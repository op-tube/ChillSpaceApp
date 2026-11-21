package com.chillspace.app

import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.webkit.JavascriptInterface
import android.webkit.WebView
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import org.json.JSONObject

/** Register with webView.addJavascriptInterface(bridge, "AndroidTTS"). */
class TTSBridge : TextToSpeech.OnInitListener {
    var tts: TextToSpeech? = null
    @Volatile var activeWebView: WebView? = null
    @Volatile private var isReady = false
    private var initializationFinished = false
    private var pending: Request? = null
    private val nextPitch = AtomicReference<Float?>(null)
    private var lastRandomPitch: Float? = null
    private val main = Handler(Looper.getMainLooper())
    private val generation = AtomicLong(0)

    private data class Request(val generation: Long, val id: String, val webView: WebView?,
        val text: String, val language: String, val pitch: Float, val clientToken: String?)
    @Volatile private var current: Request? = null

    override fun onInit(status: Int) {
        onMain {
            val engine = tts
            initializationFinished = true
            isReady = status == TextToSpeech.SUCCESS && engine != null
            if (isReady && engine != null) {
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        callback(utteranceId, "onTTSStart")
                    }

                    // Android API 26+, when the selected speech engine supplies word ranges.
                    override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                        callback(utteranceId, "onTTSRange", "$start,$end")
                    }

                    override fun onDone(utteranceId: String?) {
                        callback(utteranceId, "onTTSDone", finished = true)
                    }

                    @Deprecated("Required legacy callback")
                    override fun onError(utteranceId: String?) {
                        callback(utteranceId, "onTTSError", finished = true)
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        callback(utteranceId, "onTTSError", finished = true)
                    }
                })
            }
            val waiting = pending
            pending = null
            if (waiting != null && generation.get() == waiting.generation) startRequest(waiting)
        }
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else main.post { action() }
    }

    private fun callback(id: String?, event: String, args: String = "", finished: Boolean = false) {
        val request = current ?: return
        if (id != request.id) return
        val token = request.clientToken?.let { JSONObject.quote(it) } ?: "undefined"
        val parameters = if (args.isEmpty()) token else "$args,$token"
        val script = "if(window.$event) window.$event($parameters);"
        main.post {
            if (generation.get() == request.generation && current === request) {
                // Capture the originating WebView; never deliver an old callback to a new one.
                if (activeWebView === request.webView) request.webView?.evaluateJavascript(script, null)
                if (finished && current === request) current = null
            }
        }
    }

    /** Preserves the original two-argument JavaScript API. */
    @JavascriptInterface
    fun speak(text: String, language: String) {
        enqueue(text, language, nextPitch.getAndSet(null) ?: randomPitch())
    }

    @JavascriptInterface
    fun setPitch(pitch: Double) {
        nextPitch.set(normalizePitch(pitch))
    }

    /** Sets pitch and starts speech together, without a separate setPitch call. */
    @JavascriptInterface
    fun speakWithPitch(text: String, language: String, pitch: Double) {
        nextPitch.set(null)
        enqueue(text, language, normalizePitch(pitch))
    }

    /** Token-aware API used by ReelSpace for cancellation-safe start/range/end events. */
    @JavascriptInterface
    fun speakReel(text: String, language: String, pitch: Double, token: String) {
        nextPitch.set(null)
        enqueue(text, language, normalizePitch(pitch), token)
    }

    @Synchronized
    private fun randomPitch(): Float {
        val previous = lastRandomPitch
        val pitch = listOf(0.65f, 0.8f, 1.0f, 1.25f, 1.5f)
            .filter { previous == null || abs(it - previous) >= 0.25f }.random()
        lastRandomPitch = pitch
        return pitch
    }

    private fun normalizePitch(pitch: Double): Float =
        if (pitch.isNaN() || pitch.isInfinite()) 1.0f else pitch.coerceIn(0.5, 2.0).toFloat()

    private fun enqueue(text: String, language: String, pitch: Float, token: String? = null) {
        val serial = generation.incrementAndGet()
        val request = Request(serial, "ChillSpaceTTS-$serial", activeWebView, text, language, pitch, token)
        current = null
        onMain {
            if (generation.get() != serial) return@onMain
            current = request
            pending = null
            if (!initializationFinished) {
                pending = request // First tap can arrive while the native engine initializes.
            } else {
                startRequest(request)
            }
        }
    }

    // Runs on the main thread, after the progress listener is installed.
    private fun startRequest(request: Request) {
        if (generation.get() != request.generation || current !== request) return
        val engine = tts
        if (!isReady || engine == null || request.text.isBlank()) {
            callback(request.id, "onTTSError", finished = true)
            return
        }
        engine.stop()
        val locale = Locale.forLanguageTag(request.language.replace('_', '-'))
            .takeIf { it.language.isNotBlank() } ?: Locale.getDefault()
        val languageResult = engine.setLanguage(locale)
        if (languageResult == TextToSpeech.LANG_MISSING_DATA || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            callback(request.id, "onTTSError", finished = true)
            return
        }
        if (engine.setPitch(request.pitch) == TextToSpeech.ERROR ||
            engine.speak(request.text, TextToSpeech.QUEUE_FLUSH, null, request.id) == TextToSpeech.ERROR) {
            callback(request.id, "onTTSError", finished = true)
        }
    }

    /** Allows the page to recover if a speech engine drops its completion callback. */
    @JavascriptInterface
    fun isSpeaking(): Boolean = isReady && tts?.isSpeaking == true

    @JavascriptInterface
    fun stop() {
        val serial = generation.incrementAndGet()
        current = null
        onMain { if (generation.get() == serial) { pending = null; tts?.stop() } }
    }

    /** Call from the owning Activity's onDestroy; not exposed to JavaScript. */
    fun shutdown() {
        val serial = generation.incrementAndGet()
        current = null
        isReady = false
        onMain {
            if (generation.get() == serial) {
                tts?.stop()
                pending = null
                tts?.shutdown()
                tts = null
                activeWebView = null
            }
        }
    }
}
