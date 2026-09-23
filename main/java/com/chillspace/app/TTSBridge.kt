package com.chillspace.app

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.webkit.JavascriptInterface
import android.webkit.WebView
import java.util.Locale

/**
 * Bridge class to expose Android Text-to-Speech functionality to JavaScript.
 */
class TTSBridge : TextToSpeech.OnInitListener {

    var tts: TextToSpeech? = null
    var activeWebView: WebView? = null
    private var isReady = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isReady = true
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    activeWebView?.post {
                        activeWebView?.evaluateJavascript("if(window.onTTSDone) window.onTTSDone();", null)
                    }
                }
                override fun onError(utteranceId: String?) {}
            })
        }
    }

    /**
     * Speaks the given text in the specified language.
     * Called from JavaScript as: AndroidTTS.speak("Hello", "en")
     */
    @JavascriptInterface
    fun speak(text: String, language: String) {
        if (!isReady) return

        val locale = try {
            Locale.forLanguageTag(language)
        } catch (e: Exception) {
            Locale.getDefault()
        }

        tts?.language = locale
        // QUEUE_FLUSH means it will stop any current speech and start this one immediately
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ChillSpaceTTS")
    }

    /**
     * Stops any ongoing speech immediately.
     * Called from JavaScript as: AndroidTTS.stop()
     */
    @JavascriptInterface
    fun stop() {
        if (isReady) {
            tts?.stop()
        }
    }
}
