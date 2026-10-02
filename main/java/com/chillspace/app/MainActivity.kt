package com.chillspace.app

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var webViewFeed: WebView
    private lateinit var webViewProfile: WebView
    private lateinit var webViewReelSpace: WebView
    private lateinit var webViewMessages: WebView
    private lateinit var webViewShop: WebView
    private lateinit var webViewGame: WebView
    private lateinit var bottomNav: BottomNavigationView

    private lateinit var tts: TextToSpeech
    private lateinit var ttsBridge: TTSBridge

    // File Chooser state
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraMediaUri: Uri? = null

    // Callbacks for on-demand permissions
    private var onPermissionGranted: (() -> Unit)? = null

    // Activity result launcher for permissions
    private val requestPermissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            onPermissionGranted?.invoke()
        } else {
            // CRITICAL: Always reset the callback to null if permissions fail, 
            // otherwise the WebView will stop responding to file picker requests.
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
        }
        onPermissionGranted = null
    }


    // Activity result launcher for file picker
    private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            var results: Array<Uri>? = null

            // Check if there is any result from camera/video
            if (data == null || data.data == null) {
                cameraMediaUri?.let {
                    results = arrayOf(it)
                }
            } else {
                val uri = data.data
                if (uri != null) {
                    val mimeType = applicationContext.contentResolver.getType(uri) ?: ""
                    val isVideo = mimeType.startsWith("video") || uri.toString().contains("video", true)
                    val storedUri = copyUriToStorage(uri, isVideo)
                    results = arrayOf(storedUri)
                }
            }
            filePathCallback?.onReceiveValue(results)
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    private fun copyUriToStorage(sourceUri: Uri, isVideo: Boolean): Uri {
        return try {
            val destFile = if (isVideo) createVideoFile() else createImageFile()
            applicationContext.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Uri.fromFile(destFile)
        } catch (e: Exception) {
            sourceUri
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webViewFeed = findViewById(R.id.webViewFeed)
        webViewProfile = findViewById(R.id.webViewProfile)
        webViewReelSpace = findViewById(R.id.webViewReelSpace)
        webViewMessages = findViewById(R.id.webViewMessages)
        webViewShop = findViewById(R.id.webViewShop)
        webViewGame = findViewById(R.id.webViewGame)

        // Initialize TTS
        ttsBridge = TTSBridge()
        tts = TextToSpeech(this, ttsBridge)
        ttsBridge.tts = tts

        // Enable WebView debugging (inspect via chrome://inspect)
        WebView.setWebContentsDebuggingEnabled(true)

        // Setup each WebView with the same configuration
        val feedAsset = if (Locale.getDefault().language == "ko") "file:///android_asset/feedko.html" else "file:///android_asset/feed.html"
        setupWebView(webViewFeed, feedAsset)
        setupWebView(webViewProfile, "file:///android_asset/profile.html")
        setupWebView(webViewReelSpace, "file:///android_asset/reel.html")
        setupWebView(webViewMessages, "file:///android_asset/messages.html")
        setupWebView(webViewShop, "file:///android_asset/shop.html")
        setupWebView(webViewGame, "file:///android_asset/gaming.html")

        // Bottom navigation
        bottomNav = findViewById(R.id.bottomNavigation)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_feed -> {
                    showTab(webViewFeed)
                    true
                }
                R.id.nav_profile -> {
                    showTab(webViewProfile)
                    true
                }
                R.id.nav_reelspace -> {
                    showTab(webViewReelSpace)
                    true
                }
                R.id.nav_shop -> {
                    webViewShop.loadUrl("file:///android_asset/shop.html")
                    showTab(webViewShop)
                    true
                }
                R.id.nav_game -> {
                    showTab(webViewGame)
                    true
                }
                else -> false
            }
        }

        // --- Handle Incoming Intents (Deep Links) ---
        handleIntent(intent)

        // Back button: go back in WebView history if possible
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(enabled = true) {
                override fun handleOnBackPressed() {
                    val currentWebView = when {
                        webViewFeed.isVisible -> webViewFeed
                        webViewProfile.isVisible -> webViewProfile
                        webViewReelSpace.isVisible -> webViewReelSpace
                        webViewMessages.isVisible -> webViewMessages
                        webViewShop.isVisible -> webViewShop
                        webViewGame.isVisible -> webViewGame
                        else -> null
                    }

                    if (currentWebView?.canGoBack() == true) {
                        currentWebView.goBack()
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            },
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val appLinkData: Uri? = intent?.data
        if (appLinkData != null) {
            if (!routeInternalUrl(appLinkData)) {
                // If it's a link but not an internal one we route, default to Home
                bottomNav.selectedItemId = R.id.nav_feed
                showTab(webViewFeed)
            }
        } else {
            // Default behavior if not a deep link (e.g. cold launch)
            bottomNav.selectedItemId = R.id.nav_feed
            showTab(webViewFeed)
        }
    }

    /**
     * Routes an internal URL to the correct tab.
     * Returns true if the URL was handled and routed.
     */
    private fun routeInternalUrl(uri: Uri): Boolean {
        if ("https" == uri.scheme && ("chillspace.ai" == uri.host || "chill-space.pages.dev" == uri.host)) {
            val path = uri.path ?: "/"
            when {
                // SPECIAL CASE: K-pop is the ONLY remote game
                path.startsWith("/profile") -> {
                    showTab(webViewProfile)
                    return true
                }
                path.startsWith("/kpop") -> {
                    bottomNav.selectedItemId = R.id.nav_game
                    webViewGame.loadUrl(uri.toString())
                    showTab(webViewGame)
                    return true
                }
                path.startsWith("/rs") -> {
                    bottomNav.selectedItemId = R.id.nav_reelspace
                    webViewReelSpace.loadUrl("file:///android_asset/reel.html")
                    showTab(webViewReelSpace)
                    return true
                }
                path.startsWith("/messages") || path.startsWith("/shop") -> {
                    bottomNav.selectedItemId = R.id.nav_shop
                    webViewShop.loadUrl("file:///android_asset/shop.html")
                    showTab(webViewShop)
                    return true
                }
                path.startsWith("/jump") -> {
                    bottomNav.selectedItemId = R.id.nav_game
                    webViewGame.loadUrl("file:///android_asset/jump.html")
                    showTab(webViewGame)
                    return true
                }
                path.startsWith("/surfers") -> {
                    bottomNav.selectedItemId = R.id.nav_game
                    webViewGame.loadUrl("file:///android_asset/surfer.html")
                    showTab(webViewGame)
                    return true
                }
                path.startsWith("/numbers") -> {
                    bottomNav.selectedItemId = R.id.nav_game
                    webViewGame.loadUrl("file:///android_asset/number.html")
                    showTab(webViewGame)
                    return true
                }
                path.startsWith("/gaming") -> {
                    bottomNav.selectedItemId = R.id.nav_game
                    webViewGame.loadUrl("file:///android_asset/gaming.html")
                    showTab(webViewGame)
                    return true
                }
                path.startsWith("/runner") -> {
                    bottomNav.selectedItemId = R.id.nav_game
                    webViewGame.loadUrl("file:///android_asset/runner.html")
                    showTab(webViewGame)
                    return true
                }
                path.startsWith("/stacker") -> {
                    bottomNav.selectedItemId = R.id.nav_game
                    webViewGame.loadUrl("file:///android_asset/stacker.html")
                    showTab(webViewGame)
                    return true
                }
                path.startsWith("/flapper") -> {
                    bottomNav.selectedItemId = R.id.nav_game
                    webViewGame.loadUrl("file:///android_asset/flapper.html")
                    showTab(webViewGame)
                    return true
                }
                path.startsWith("/slidey") -> {
                    bottomNav.selectedItemId = R.id.nav_game
                    webViewGame.loadUrl("file:///android_asset/slidey.html")
                    showTab(webViewGame)
                    return true
                }
                path.startsWith("/profile") -> {
                    bottomNav.selectedItemId = R.id.nav_profile
                    showTab(webViewProfile)
                    return true
                }
                path == "/" || path == "" -> {
                    bottomNav.selectedItemId = R.id.nav_feed
                    val assetName = if (Locale.getDefault().language == "ko") "feedko.html" else "feed.html"
                    val query = if (!uri.query.isNullOrEmpty()) "?${uri.query}" else ""
                    webViewFeed.loadUrl("file:///android_asset/$assetName$query")
                    showTab(webViewFeed)
                    return true
                }
            }
        }
        return false
    }

    /**
     * Checks and requests camera permission on-demand.
     */
    private fun checkAndRequestCameraPermission(onGranted: () -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            onGranted()
        } else {
            onPermissionGranted = onGranted
            requestPermissionsLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        }
    }

    /**
     * Opens the file chooser (gallery/files) with an optional camera intent.
     * If isCaptureEnabled is true, it attempts to launch the camera/video directly.
     */
    private fun openFileChooser(params: WebChromeClient.FileChooserParams?) {
        val acceptTypes = params?.acceptTypes ?: arrayOf("")
        val isVideo = acceptTypes.any { it.contains("video", ignoreCase = true) }
        val isCaptureEnabled = params?.isCaptureEnabled ?: false

        if (isCaptureEnabled) {
            val captureIntent = if (isVideo) {
                Intent(MediaStore.ACTION_VIDEO_CAPTURE)
            } else {
                Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            }

            val mediaFile = try {
                if (isVideo) createVideoFile() else createImageFile()
            } catch (ex: Exception) {
                null
            }

            if (mediaFile != null) {
                cameraMediaUri = FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.fileprovider",
                    mediaFile
                )
                captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraMediaUri)
                // Grant URI permissions for the intent
                captureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                
                try {
                    fileChooserLauncher.launch(captureIntent)
                    return // Launched directly, skip chooser
                } catch (e: Exception) {
                    // Fallback to chooser if direct launch fails
                }
            }
        }

        // Standard Chooser (Gallery + Camera)
        val contentIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (acceptTypes.isNotEmpty() && acceptTypes[0].isNotEmpty()) acceptTypes[0] else "*/*"
            if (acceptTypes.size > 1) {
                putExtra(Intent.EXTRA_MIME_TYPES, acceptTypes)
            }
        }

        val chooserIntent = Intent(Intent.createChooser(contentIntent, "Choose File"))

        // Add Camera/Video option to the generic chooser as well
        val genericCaptureIntent = if (isVideo) {
            Intent(MediaStore.ACTION_VIDEO_CAPTURE)
        } else {
            Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        }

        val mediaFile = try {
            if (isVideo) createVideoFile() else createImageFile()
        } catch (ex: Exception) {
            null
        }

        if (mediaFile != null) {
            cameraMediaUri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                mediaFile
            )
            genericCaptureIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraMediaUri)
            genericCaptureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(genericCaptureIntent))
        }

        try {
            fileChooserLauncher.launch(chooserIntent)
        } catch (e: ActivityNotFoundException) {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
        }
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }

    private fun createVideoFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        return File.createTempFile("VIDEO_${timeStamp}_", ".mp4", storageDir)
    }

    @Suppress("SetJavaScriptEnabled")
    private fun setupWebView(webView: WebView, url: String) {
        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = true
        settings.allowContentAccess = true

        // WebChromeClient handles JS alerts, video playback, progress, and FILE CHOOSER
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                if (this@MainActivity.filePathCallback != null) {
                    this@MainActivity.filePathCallback?.onReceiveValue(null)
                }
                this@MainActivity.filePathCallback = filePathCallback

                // Determine if camera capture is preferred
                val isCaptureEnabled = fileChooserParams?.isCaptureEnabled ?: false
                
                // Check permissions on-demand
                if (isCaptureEnabled) {
                    checkAndRequestCameraPermission {
                        openFileChooser(fileChooserParams)
                    }
                } else {
                    openFileChooser(fileChooserParams)
                }
                
                return true
            }
        }

        // ---------- CUSTOM WebViewClient to handle external links ----------
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, urlString: String?): Boolean {
                if (urlString == null) return false
                val uri = Uri.parse(urlString)

                // 1. Intercept internal links for routing between tabs
                if (routeInternalUrl(uri)) {
                    return true
                }

                // 2. List of domains that should OPEN IN EXTERNAL BROWSER / APPS
                val externalDomains = listOf(
                    "wa.me", "api.whatsapp.com",   // WhatsApp
                    "instagram.com", "ig.me",              // Instagram
                    "twitter.com", "x.com",        // Twitter / X
                    "facebook.com",                // Facebook
                    "tiktok.com",                  // TikTok
                    "youtube.com", "youtu.be",     // YouTube
                    "telegram.org"                 // Telegram
                )

                val isExternal = externalDomains.any { urlString.contains(it, ignoreCase = true) }
                        || urlString.startsWith("mailto:")
                        || urlString.startsWith("sms:")
                        || urlString.startsWith("tel:")

                if (isExternal) {
                    try {
                        // Open in external app (browser or native app)
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlString))
                        startActivity(intent)
                    } catch (_: Exception) {
                        // Fallback
                    }
                    return true // WebView stops loading this link
                }

                // For ChillSpace internal links, keep them inside the WebView
                return false
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                // If the main page fails to load (e.g. offline), show fallback
                val failedUrl = request?.url?.toString() ?: ""
                if (request?.isForMainFrame == true && !isNetworkAvailable()) {
                    view?.loadUrl(getFallbackUrl(failedUrl))
                }
            }
        }

        // ---------- Add JavaScript interface for Utils ----------
        val bridge = ContactBridge(this)
        webView.addJavascriptInterface(bridge, "AndroidUtils")
        // ---------- Add JavaScript interface for TTS ----------
        webView.addJavascriptInterface(ttsBridge, "AndroidTTS")

        val initialUrl = when {
            url.startsWith("file://") -> url
            isNetworkAvailable() -> url
            else -> getFallbackUrl(url)
        }
        webView.loadUrl(initialUrl)
    }

    /**
     * Determines the appropriate local fallback URL for a given web URL.
     */
    private fun getFallbackUrl(url: String): String {
        return when {
            url.contains("/gaming") || url.contains("/kpop") || url.contains("/numbers") -> 
                "file:///android_asset/gaming.html"
            url.contains("reel.html") || url.contains("/rs") -> 
                "file:///android_asset/reel.html"
            else -> 
                "file:///android_asset/feed.html"
        }
    }

    private fun showTab(activeWebView: WebView) {
        // Stop media, TTS, and JS in background WebViews
        if (::tts.isInitialized) {
            tts.stop()
        }
        ttsBridge.activeWebView = activeWebView
        val webViews = listOf(webViewFeed, webViewProfile, webViewReelSpace, webViewMessages, webViewShop, webViewGame)
        webViews.forEach { wv ->
            if (wv == activeWebView) {
                wv.isVisible = true
                wv.onResume()
                // Resume ReelSpace if active
                if (wv == webViewReelSpace) {
                    wv.evaluateJavascript("if(window.resumeReelSpaceMedia) resumeReelSpaceMedia();", null)
                }
            } else {
                wv.isVisible = false
                wv.onPause()
                // Aggressively pause all audio/video tags in the hidden WebView
                wv.evaluateJavascript("(function(){ document.querySelectorAll('audio,video').forEach(m => { m.pause(); }); })();", null)
                // Also call the specific pause function if it exists
                if (wv == webViewReelSpace) {
                    wv.evaluateJavascript("if(window.pauseReelSpaceMedia) pauseReelSpaceMedia();", null)
                }
            }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }

    override fun onPause() {
        super.onPause()
        if (::tts.isInitialized) {
            tts.stop()
        }
        val webViews = listOf(webViewFeed, webViewProfile, webViewReelSpace, webViewMessages, webViewShop, webViewGame)
        webViews.forEach { wv ->
            try {
                wv.onPause()
                wv.pauseTimers()
                wv.evaluateJavascript("if(window.pauseAllMediaAndScripts) window.pauseAllMediaAndScripts();", null)
            } catch (e: Exception) {}
        }
    }

    override fun onResume() {
        super.onResume()
        val webViews = listOf(webViewFeed, webViewProfile, webViewReelSpace, webViewMessages, webViewShop, webViewGame)
        webViews.forEach { wv ->
            try {
                wv.resumeTimers()
                if (wv.isVisible) {
                    wv.onResume()
                    if (wv == webViewReelSpace) {
                        wv.evaluateJavascript("if(window.resumeReelSpaceMedia) resumeReelSpaceMedia();", null)
                    }
                }
            } catch (e: Exception) {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        val webViews = listOf(webViewFeed, webViewProfile, webViewReelSpace, webViewMessages, webViewShop, webViewGame)
        webViews.forEach { wv ->
            try {
                wv.loadUrl("about:blank")
                wv.clearCache(true)
                wv.destroy()
            } catch (e: Exception) {}
        }
    }

    // ---------- Utils Bridge: lets JavaScript manage files and share content ----------
    inner class ContactBridge(private val context: Context) {

        /**
         * Returns the latest media file URI from Pictures or Movies directory.
         */
        @JavascriptInterface
        fun getLatestMediaUri(isVideo: Boolean): String {
            return try {
                val dir = if (isVideo) getExternalFilesDir(Environment.DIRECTORY_MOVIES) else getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                if (dir != null) {
                    val fileDir = dir as File
                    if (fileDir.exists()) {
                        val files = fileDir.listFiles()
                        if (files != null && files.isNotEmpty()) {
                            val sorted = files.sortedByDescending { it.lastModified() }
                            return Uri.fromFile(sorted[0]).toString()
                        }
                    }
                }
                ""
            } catch (e: Exception) {
                ""
            }
        }

        /**
         * Launches the native Android system share menu for post sharing.
         */
        @JavascriptInterface
        fun shareText(text: String) {
            runOnUiThread {
                try {
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, text)
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, "Share Post")
                    context.startActivity(shareIntent)
                } catch (e: Exception) {}
            }
        }

        /**
         * Deletes a file from the app's internal/external storage.
         * Used for cleaning up ephemeral "Story" media.
         */
        @JavascriptInterface
        fun deleteFile(uriString: String): Boolean {
            return try {
                val uri = Uri.parse(uriString)
                // If it's a FileProvider URI, we need to find the actual file
                if (uri.authority == "${applicationContext.packageName}.fileprovider") {
                    // This is a bit complex since FileProvider obscures the path.
                    // For our app, we know we store them in Pictures or Movies.
                    val fileName = uri.lastPathSegment
                    if (fileName != null) {
                        val picturesDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                        val moviesDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES)

                        val fileInPictures = File(picturesDir, fileName)
                        val fileInMovies = File(moviesDir, fileName)

                        if (fileInPictures.exists()) return fileInPictures.delete()
                        if (fileInMovies.exists()) return fileInMovies.delete()
                    }
                } else if (uri.scheme == "file") {
                    val file = File(uri.path!!)
                    if (file.exists()) return file.delete()
                }
                false
            } catch (e: Exception) {
                false
            }
        }

    }
}