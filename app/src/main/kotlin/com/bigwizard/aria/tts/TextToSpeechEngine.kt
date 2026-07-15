package com.bigwizard.aria.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/**
 * TextToSpeechEngine — Android native TTS wrapper.
 *
 * Uses the built-in Android TTS engine — zero extra APK size,
 * works on every Android device including Android Go.
 *
 * Features:
 *  - Adjustable speed and pitch
 *  - Language/locale support
 *  - Utterance callbacks (started, done, error)
 *  - Queue management (interrupt or enqueue)
 *  - Sentence chunking for long responses
 */
class TextToSpeechEngine(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isReady = false

    private val _ttsState = MutableStateFlow<TtsState>(TtsState.Idle)
    val ttsState: StateFlow<TtsState> = _ttsState.asStateFlow()

    private var onSpeakingDone: (() -> Unit)? = null

    // ── Initialization ────────────────────────────────────────────────────────

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "TTS language not supported, falling back to default")
                tts?.setLanguage(Locale.getDefault())
            }
            setupUtteranceListener()
            isReady = true
            _ttsState.value = TtsState.Ready
            Log.i(TAG, "TTS engine initialized")
        } else {
            Log.e(TAG, "TTS initialization failed with status: $status")
            _ttsState.value = TtsState.Error("TTS engine failed to initialize")
        }
    }

    // ── Utterance Listener ────────────────────────────────────────────────────

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _ttsState.value = TtsState.Speaking
            }

            override fun onDone(utteranceId: String?) {
                _ttsState.value = TtsState.Ready
                onSpeakingDone?.invoke()
                onSpeakingDone = null
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.e(TAG, "TTS error for utterance: $utteranceId")
                _ttsState.value = TtsState.Error("Speech output error")
                onSpeakingDone?.invoke()
                onSpeakingDone = null
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e(TAG, "TTS error $errorCode for utterance: $utteranceId")
                _ttsState.value = TtsState.Error("Speech output error: $errorCode")
                onSpeakingDone?.invoke()
                onSpeakingDone = null
            }
        })
    }

    // ── Speak ─────────────────────────────────────────────────────────────────

    /**
     * Speak text aloud. Interrupts any current speech by default.
     */
    fun speak(
        text: String,
        speed: Float = 1.0f,
        pitch: Float = 1.0f,
        interrupt: Boolean = true,
        onDone: (() -> Unit)? = null
    ) {
        if (!isReady || tts == null) {
            Log.w(TAG, "TTS not ready, cannot speak: $text")
            onDone?.invoke()
            return
        }

        onSpeakingDone = onDone

        tts?.setSpeechRate(speed.coerceIn(0.1f, 4.0f))
        tts?.setPitch(pitch.coerceIn(0.1f, 2.0f))

        val queueMode = if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val utteranceId = UUID.randomUUID().toString()

        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }

        // Chunk long text to avoid TTS limits (max ~4000 chars per utterance)
        val chunks = chunkText(text)
        chunks.forEachIndexed { index, chunk ->
            val chunkMode = if (index == 0) queueMode else TextToSpeech.QUEUE_ADD
            val chunkId = if (index == chunks.lastIndex) utteranceId else UUID.randomUUID().toString()
            tts?.speak(chunk, chunkMode, params, chunkId)
        }

        Log.d(TAG, "Speaking: ${text.take(80)}...")
    }

    /**
     * Speak a streaming response — word by word as it arrives.
     * Call this repeatedly with new tokens; it enqueues them.
     */
    fun speakToken(token: String, speed: Float = 1.0f, pitch: Float = 1.0f) {
        if (!isReady || tts == null) return
        tts?.setSpeechRate(speed)
        tts?.setPitch(pitch)
        tts?.speak(token, TextToSpeech.QUEUE_ADD, null, UUID.randomUUID().toString())
    }

    fun stop() {
        tts?.stop()
        _ttsState.value = TtsState.Ready
        onSpeakingDone = null
    }

    fun isSpeaking(): Boolean = tts?.isSpeaking == true

    // ── Language ──────────────────────────────────────────────────────────────

    fun setLanguage(locale: Locale): Boolean {
        val result = tts?.setLanguage(locale)
        return result != TextToSpeech.LANG_MISSING_DATA &&
               result != TextToSpeech.LANG_NOT_SUPPORTED
    }

    fun setLanguageFromCode(languageCode: String): Boolean {
        val locale = when (languageCode.lowercase()) {
            "en-us", "en" -> Locale.US
            "en-gb"       -> Locale.UK
            "es"          -> Locale("es", "ES")
            "fr"          -> Locale.FRANCE
            "de"          -> Locale.GERMANY
            "it"          -> Locale.ITALY
            "pt"          -> Locale("pt", "BR")
            "zh"          -> Locale.CHINESE
            "ja"          -> Locale.JAPANESE
            "ko"          -> Locale.KOREAN
            "ru"          -> Locale("ru", "RU")
            "ar"          -> Locale("ar", "SA")
            "hi"          -> Locale("hi", "IN")
            else          -> Locale.getDefault()
        }
        return setLanguage(locale)
    }

    fun getAvailableLanguages(): Set<Locale> {
        return tts?.availableLanguages ?: emptySet()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun chunkText(text: String, maxLength: Int = 3000): List<String> {
        if (text.length <= maxLength) return listOf(text)

        val chunks = mutableListOf<String>()
        var remaining = text

        while (remaining.length > maxLength) {
            // Try to break at sentence boundary
            val breakPoint = remaining.lastIndexOf('.', maxLength)
                .takeIf { it > maxLength / 2 }
                ?: remaining.lastIndexOf(' ', maxLength)
                    .takeIf { it > 0 }
                ?: maxLength

            chunks.add(remaining.substring(0, breakPoint + 1).trim())
            remaining = remaining.substring(breakPoint + 1).trim()
        }

        if (remaining.isNotBlank()) chunks.add(remaining)
        return chunks
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        _ttsState.value = TtsState.Idle
        Log.i(TAG, "TTS engine destroyed")
    }

    companion object {
        private const val TAG = "TextToSpeechEngine"
    }
}

// ── TTS State ─────────────────────────────────────────────────────────────────

sealed class TtsState {
    object Idle    : TtsState()
    object Ready   : TtsState()
    object Speaking : TtsState()
    data class Error(val message: String) : TtsState()
}