package com.mufradati

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.compose.runtime.*
import java.util.Locale

class Speech(context: Context) {
    var ready by mutableStateOf(false)
        private set

    var voices by mutableStateOf<List<Voice>>(emptyList())
        private set

    private var engine: TextToSpeech? = null

    init {
        engine =
            TextToSpeech(context.applicationContext) { status ->
                val tts = engine
                if (status == TextToSpeech.SUCCESS && tts != null) {
                    val result = tts.setLanguage(Locale.US)
                    voices =
                        tts.voices
                            .orEmpty()
                            .filter {
                                it.locale.language == "en" && !it.isNetworkConnectionRequired
                            }
                            .sortedBy { it.name }
                    ready = result >= TextToSpeech.LANG_AVAILABLE && voices.isNotEmpty()
                }
            }
    }

    fun speak(text: String, s: Settings, onError: () -> Unit) {
        val tts = engine
        if (!ready || tts == null) {
            onError()
            return
        }
        tts.voice = voices.firstOrNull { it.name == s.voice } ?: voices.first()
        tts.setSpeechRate(s.rate)
        tts.setPitch(s.pitch)
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mufradati") == TextToSpeech.ERROR)
            onError()
    }

    fun stop() {
        engine?.stop()
    }

    fun close() {
        engine?.stop()
        engine?.shutdown()
    }
}
