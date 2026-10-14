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
class TextToSpeechEngine(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    @Volatile private var isReady = false
    @Volatile private var initFailed = false

    /** Utterance whose completion fires [onSpeakingDone] (the last chunk). */
    @Volatile private var finalUtteranceId: String? = null

    /** Speech requested before the engine finished binding (first launch). */
    private var pendingSpeech: (() -> Unit)? = null

    private val _ttsState = MutableStateFlow<TtsState>(TtsState.Idle)
    val ttsState: StateFlow<TtsState> = _ttsState.asStateFlow()

    @Volatile private var onSpeakingDone: (() -> Unit)? = null

    // ── Initialization ────────────────────────────────────────────────────────

    init {
        // Application context: the engine outlives activities and must not leak them.
        tts = TextToSpeech(context.applicationContext, this)
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
            pendingSpeech?.invoke()
            pendingSpeech = null
        } else {
            Log.e(TAG, "TTS initialization failed with status: $status")
            initFailed = true
            _ttsState.value = TtsState.Error("TTS engine failed to initialize")
            pendingSpeech = null
            finishUtterance()
        }
    }

    // ── Utterance Listener ────────────────────────────────────────────────────

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _ttsState.value = TtsState.Speaking
            }

            override fun onDone(utteranceId: String?) {
                // Long replies are split into chunks: only the last one ends the turn.
                if (utteranceId != null && utteranceId == finalUtteranceId) {
                    _ttsState.value = TtsState.Ready
                    finishUtterance()
                }
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                if (utteranceId != null && utteranceId == finalUtteranceId) {
                    _ttsState.value = TtsState.Ready
                    finishUtterance()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                Log.e(TAG, "TTS error for utterance: $utteranceId")
                _ttsState.value = TtsState.Error("Speech output error")
                finishUtterance()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e(TAG, "TTS error $errorCode for utterance: $utteranceId")
                _ttsState.value = TtsState.Error("Speech output error: $errorCode")
                finishUtterance()
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
        if (initFailed || tts == null) {
            Log.w(TAG, "TTS unavailable, cannot speak: ${text.take(80)}")
            onDone?.invoke()
            return
        }
        if (!isReady) {
            // Engine still binding (common on the very first reply) — speak once ready.
            pendingSpeech = { speak(text, speed, pitch, interrupt, onDone) }
            return
        }

        // A new utterance that interrupts the old one ends the old turn first.
        if (interrupt) finishUtterance()
        onSpeakingDone = onDone

        tts?.setSpeechRate(speed.coerceIn(0.1f, 4.0f))
        tts?.setPitch(pitch.coerceIn(0.1f, 2.0f))

        val queueMode = if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD

        // Chunk long text to stay under TextToSpeech.getMaxSpeechInputLength() (~4000 chars)
        val chunks = chunkText(text)
        val lastId = UUID.randomUUID().toString()
        finalUtteranceId = lastId
        chunks.forEachIndexed { index, chunk ->
            val chunkMode = if (index == 0) queueMode else TextToSpeech.QUEUE_ADD
            val chunkId = if (index == chunks.lastIndex) lastId else UUID.randomUUID().toString()
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, chunkId)
            }
            if (tts?.speak(chunk, chunkMode, params, chunkId) == TextToSpeech.ERROR) {
                Log.e(TAG, "TTS speak() rejected chunk $index")
                finishUtterance()
                return
            }
        }

        Log.d(TAG, "Speaking: ${text.take(80)}")
    }

    /**
     * Speak a streaming response — word by word as it arrives.
     * Call this repeatedly with new tokens; it enqueues them.
     */
    fun speakToken(token: String, speed: Float = 1.0f, pitch: Float = 1.0f) {
        if (!isReady || tts == null) return
        tts?.setSpeechRate(speed.coerceIn(0.1f, 4.0f))
        tts?.setPitch(pitch.coerceIn(0.1f, 2.0f))
        tts?.speak(token, TextToSpeech.QUEUE_ADD, null, UUID.randomUUID().toString())
    }

    /** Stops speech. The pending done-callback is dropped (caller owns the state change). */
    fun stop() {
        pendingSpeech = null
        onSpeakingDone = null
        finalUtteranceId = null
        tts?.stop()
        if (isReady) _ttsState.value = TtsState.Ready
    }

    private fun finishUtterance() {
        val callback = onSpeakingDone
        onSpeakingDone = null
        callback?.invoke()
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
        pendingSpeech = null
        onSpeakingDone = null
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