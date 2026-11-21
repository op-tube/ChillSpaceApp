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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var tts: TextToSpeech
    private lateinit var ttsBridge: TTSBridge

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraMediaUri: Uri? = null
    private var onPermissionGranted: (() -> Unit)? = null

    private val LAUNCHER_URL = "file:///android_asset/index.html"

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.values.all { it }) {
                onPermissionGranted?.invoke()
            } else {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = null
            }
            onPermissionGranted = null
        }

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                val results: Array<Uri>? = when {
                    data?.data == null -> cameraMediaUri?.let { arrayOf(it) }
                    else -> {
                        val uri = data.data!!
                        val mime = applicationContext.contentResolver.getType(uri) ?: ""
                        val isVideo = mime.startsWith("video") || uri.toString().contains("video", true)
                        arrayOf(copyUriToStorage(uri, isVideo))
                    }
                }
                filePathCallback?.onReceiveValue(results)
            } else {
                filePathCallback?.onReceiveValue(null)
            }
            filePathCallback = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)

        ttsBridge = TTSBridge()
        tts = TextToSpeech(this, ttsBridge)
        ttsBridge.tts = tts

        WebView.setWebContentsDebuggingEnabled(true)

        setupWebView(webView)

        val initialUrl = routeInternalUrl(intent?.data) ?: LAUNCHER_URL
        webView.loadUrl(initialUrl)

        // Back: walk WebView history; if at the root, fall back to launcher; then exit
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else if (!isOnLauncher()) {
                    webView.loadUrl(LAUNCHER_URL)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val url = routeInternalUrl(intent.data) ?: return
        webView.loadUrl(url)
    }

    private fun isOnLauncher(): Boolean {
        return webView.url?.endsWith("index.html") == true
    }

    /**
     * Returns a local asset URL, remote URL, or null if the URI is not a ChillSpace link.
     * Note: /gaming now resolves to index.html (gaming.html is retired).
     *       /messages now resolves to messages.html (fixing the old shop.html bug).
     */
    private fun routeInternalUrl(uri: Uri?): String? {
        if (uri == null) return null
        if (uri.scheme != "https") return null
        if (uri.host != "chillspace.ai" && uri.host != "chill-space.pages.dev") return null

        val path = uri.path ?: "/"
        return when {
            path.startsWith("/profile") -> "file:///android_asset/profile.html"
            path.startsWith("/kpop")    -> uri.toString()          // remote game
            path.startsWith("/rs")      -> "file:///android_asset/reel.html"
            path.startsWith("/messages") -> "file:///android_asset/messages.html"
            path.startsWith("/shop")    -> "file:///android_asset/shop.html"
            path.startsWith("/jump")    -> "file:///android_asset/jump.html"
            path.startsWith("/surfers") -> "file:///android_asset/surfer.html"
            path.startsWith("/numbers") -> "file:///android_asset/number.html"
            path.startsWith("/runner")  -> "file:///android_asset/runner.html"
            path.startsWith("/stacker") -> "file:///android_asset/stacker.html"
            path.startsWith("/flapper") -> "file:///android_asset/flapper.html"
            path.startsWith("/slidey")  -> "file:///android_asset/slidey.html"
            path.startsWith("/gaming")  -> "file:///android_asset/index.html"   // retired gaming.html
            path == "/" || path.isEmpty() -> {
                val q = if (!uri.query.isNullOrEmpty()) "?${uri.query}" else ""
                "file:///android_asset/feed.html$q"
            }
            else -> null
        }
    }

    private fun copyUriToStorage(sourceUri: Uri, isVideo: Boolean): Uri {
        return try {
            val destFile = if (isVideo) createVideoFile() else createImageFile()
            applicationContext.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            Uri.fromFile(destFile)
        } catch (e: Exception) { sourceUri }
    }

    private fun checkAndRequestCameraPermission(onGranted: () -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) onGranted()
        else {
            onPermissionGranted = onGranted
            requestPermissionsLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        }
    }

    private fun openFileChooser(params: WebChromeClient.FileChooserParams?) {
        val acceptTypes = params?.acceptTypes ?: arrayOf("")
        val isVideo = acceptTypes.any { it.contains("video", ignoreCase = true) }
        val captureEnabled = params?.isCaptureEnabled ?: false

        if (captureEnabled) {
            val captureIntent = if (isVideo) Intent(MediaStore.ACTION_VIDEO_CAPTURE)
                                else Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            val mediaFile = try { if (isVideo) createVideoFile() else createImageFile() } catch (e: Exception) { null }
            if (mediaFile != null) {
                cameraMediaUri = FileProvider.getUriForFile(
                    this, "${applicationContext.packageName}.fileprovider", mediaFile)
                captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraMediaUri)
                captureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                try { fileChooserLauncher.launch(captureIntent); return } catch (e: Exception) {}
            }
        }

        val contentIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (acceptTypes.isNotEmpty() && acceptTypes[0].isNotEmpty()) acceptTypes[0] else "*/*"
            if (acceptTypes.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, acceptTypes)
        }
        val chooserIntent = Intent.createChooser(contentIntent, "Choose File")

        val captureIntent = if (isVideo) Intent(MediaStore.ACTION_VIDEO_CAPTURE)
                            else Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val mediaFile = try { if (isVideo) createVideoFile() else createImageFile() } catch (e: Exception) { null }
        if (mediaFile != null) {
            cameraMediaUri = FileProvider.getUriForFile(
                this, "${applicationContext.packageName}.fileprovider", mediaFile)
            captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraMediaUri)
            captureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(captureIntent))
        }

        try { fileChooserLauncher.launch(chooserIntent) }
        catch (e: ActivityNotFoundException) {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
        }
    }

    private fun createImageFile(): File {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return File.createTempFile("JPEG_${ts}_", ".jpg", getExternalFilesDir(Environment.DIRECTORY_PICTURES))
    }

    private fun createVideoFile(): File {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return File.createTempFile("VIDEO_${ts}_", ".mp4", getExternalFilesDir(Environment.DIRECTORY_MOVIES))
    }

    @Suppress("SetJavaScriptEnabled")
    private fun setupWebView(webView: WebView) {
        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = true
        settings.allowContentAccess = true

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?, callback: ValueCallback<Array<Uri>>?, params: FileChooserParams?
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                if (params?.isCaptureEnabled == true) {
                    checkAndRequestCameraPermission { openFileChooser(params) }
                } else {
                    openFileChooser(params)
                }
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, urlString: String?): Boolean {
                if (urlString == null) return false
                val uri = Uri.parse(urlString)

                // Substitute an internal URL for ChillSpace https links
                val internal = routeInternalUrl(uri)
                if (internal != null) {
                    view?.loadUrl(internal)
                    return true
                }

                val externalDomains = listOf(
                    "wa.me", "api.whatsapp.com", "instagram.com", "ig.me",
                    "twitter.com", "x.com", "facebook.com", "tiktok.com",
                    "youtube.com", "youtu.be", "telegram.org"
                )
                val isExternal = externalDomains.any { urlString.contains(it, true) }
                        || urlString.startsWith("mailto:")
                        || urlString.startsWith("sms:")
                        || urlString.startsWith("tel:")

                if (isExternal) {
                    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(urlString))) } catch (_: Exception) {}
                    return true
                }
                return false
            }

            override fun onReceivedError(
                view: WebView?, request: WebResourceRequest?, error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true && !isNetworkAvailable()) {
                    val failed = request.url?.toString() ?: ""
                    view?.loadUrl(getFallbackUrl(failed))
                }
            }
        }

        webView.addJavascriptInterface(ContactBridge(this), "AndroidUtils")
        webView.addJavascriptInterface(ttsBridge, "AndroidTTS")
    }

    private fun getFallbackUrl(url: String): String = when {
        url.contains("/gaming") || url.contains("/kpop") || url.contains("/numbers")
                -> "file:///android_asset/index.html"    // gaming.html retired → launcher
        url.contains("reel.html") || url.contains("/rs")
                -> "file:///android_asset/reel.html"
        else -> "file:///android_asset/feed.html"
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    override fun onPause() {
        super.onPause()
        if (::tts.isInitialized) tts.stop()
        try {
            webView.onPause()
            webView.pauseTimers()
            webView.evaluateJavascript(
                "if(window.pauseAllMediaAndScripts) window.pauseAllMediaAndScripts();", null)
        } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        try {
            webView.resumeTimers()
            webView.onResume()
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::tts.isInitialized) { tts.stop(); tts.shutdown() }
        try {
            webView.loadUrl("about:blank")
            webView.clearCache(true)
            webView.destroy()
        } catch (_: Exception) {}
    }

    // ============ Bridge ============
    inner class ContactBridge(private val context: Context) {

        @JavascriptInterface
        fun loadAsset(fileName: String): String = try {
            context.assets.open(fileName).bufferedReader().use { it.readText() }
        } catch (e: Exception) { "" }

        @JavascriptInterface
        fun getLatestMediaUri(isVideo: Boolean): String {
            return try {
                val dir = if (isVideo) getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                          else getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                if (dir != null && dir.exists()) {
                    val sorted = dir.listFiles()?.sortedByDescending { it.lastModified() }
                    if (!sorted.isNullOrEmpty()) return Uri.fromFile(sorted[0]).toString()
                }
                ""
            } catch (e: Exception) { "" }
        }

        @JavascriptInterface
        fun shareText(text: String) {
            runOnUiThread {
                try {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, text)
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(send, "Share Post"))
                } catch (_: Exception) {}
            }
        }

        @JavascriptInterface
        fun deleteFile(uriString: String): Boolean {
            return try {
                val uri = Uri.parse(uriString)
                if (uri.authority == "${applicationContext.packageName}.fileprovider") {
                    val name = uri.lastPathSegment
                    if (name != null) {
                        val p = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), name)
                        val v = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), name)
                        if (p.exists()) return p.delete()
                        if (v.exists()) return v.delete()
                    }
                } else if (uri.scheme == "file") {
                    val f = File(uri.path!!)
                    if (f.exists()) return f.delete()
                }
                false
            } catch (e: Exception) { false }
        }

        /** New: called from the launcher tile for Feed Lite. */
        @JavascriptInterface
        fun openLiteFeed() {
            runOnUiThread {
                try {
                    startActivity(Intent(this@MainActivity, LiteFeedActivity::class.java))
                } catch (_: Exception) {}
            }
        }
    }
}