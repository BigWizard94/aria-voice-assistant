package com.bigwizard.aria.stt

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import org.json.JSONObject
import java.io.IOException

/**
 * SpeechRecognitionEngine — Offline STT via Vosk.
 *
 * Features:
 *  - 100% offline, no internet required for speech recognition
 *  - Lightweight small model (~50MB) works on Android Go / 1GB RAM devices
 *  - Emits partial results for real-time UI feedback
 *  - Emits final results for command processing
 *  - Automatic model loading from assets
 */
class SpeechRecognitionEngine(private val context: Context) {

    // ── State ─────────────────────────────────────────────────────────────────

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var isModelLoaded = false

    private val _sttState = MutableStateFlow<SttState>(SttState.Idle)
    val sttState: StateFlow<SttState> = _sttState.asStateFlow()

    private val _partialResult = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val partialResult: SharedFlow<String> = _partialResult.asSharedFlow()

    private val _finalResult = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val finalResult: SharedFlow<String> = _finalResult.asSharedFlow()

    // ── Model Loading ─────────────────────────────────────────────────────────

    /**
     * Load the Vosk model from assets.
     * Call once at app startup. Model stays in memory for fast response.
     */
    fun loadModel(
        modelName: String = MODEL_SMALL_EN,
        onComplete: (Boolean) -> Unit = {}
    ) {
        if (isModelLoaded) {
            onComplete(true)
            return
        }

        _sttState.value = SttState.LoadingModel

        StorageService.unpack(
            context,
            modelName,
            "model",
            { loadedModel ->
                model = loadedModel
                isModelLoaded = true
                _sttState.value = SttState.Ready
                Log.i(TAG, "Vosk model loaded: $modelName")
                onComplete(true)
            },
            { exception ->
                Log.e(TAG, "Failed to load Vosk model", exception)
                _sttState.value = SttState.Error("Failed to load speech model: ${exception.message}")
                onComplete(false)
            }
        )
    }

    // ── Listening Control ─────────────────────────────────────────────────────

    fun startListening() {
        if (!isModelLoaded || model == null) {
            _sttState.value = SttState.Error("Speech model not loaded yet")
            return
        }

        if (_sttState.value is SttState.Listening) {
            Log.w(TAG, "Already listening")
            return
        }

        try {
            val recognizer = Recognizer(model, SAMPLE_RATE)
            speechService = SpeechService(recognizer, SAMPLE_RATE)
            speechService?.startListening(recognitionListener)
            _sttState.value = SttState.Listening
            Log.i(TAG, "Started listening")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start listening", e)
            _sttState.value = SttState.Error("Microphone error: ${e.message}")
        }
    }

    fun stopListening() {
        speechService?.stop()
        speechService = null
        if (_sttState.value is SttState.Listening) {
            _sttState.value = SttState.Ready
        }
        Log.i(TAG, "Stopped listening")
    }

    fun pauseListening() {
        speechService?.setPause(true)
    }

    fun resumeListening() {
        speechService?.setPause(false)
    }

    // ── Recognition Listener ──────────────────────────────────────────────────

    private val recognitionListener = object : RecognitionListener {

        override fun onPartialResult(hypothesis: String?) {
            hypothesis ?: return
            try {
                val partial = JSONObject(hypothesis).optString("partial", "")
                if (partial.isNotBlank()) {
                    _partialResult.tryEmit(partial)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse partial result: $hypothesis")
            }
        }

        override fun onResult(hypothesis: String?) {
            hypothesis ?: return
            try {
                val text = JSONObject(hypothesis).optString("text", "")
                if (text.isNotBlank()) {
                    Log.i(TAG, "Final STT result: $text")
                    _finalResult.tryEmit(text)
                    _sttState.value = SttState.Result(text)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse final result: $hypothesis")
            }
        }

        override fun onFinalResult(hypothesis: String?) {
            onResult(hypothesis)
        }

        override fun onError(exception: Exception?) {
            Log.e(TAG, "STT error", exception)
            _sttState.value = SttState.Error(exception?.message ?: "Speech recognition error")
        }

        override fun onTimeout() {
            Log.i(TAG, "STT timeout — no speech detected")
            _sttState.value = SttState.Timeout
        }
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    fun destroy() {
        stopListening()
        model?.close()
        model = null
        isModelLoaded = false
        _sttState.value = SttState.Idle
        Log.i(TAG, "SpeechRecognitionEngine destroyed")
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    companion object {
        private const val TAG = "SpeechRecognitionEngine"
        private const val SAMPLE_RATE = 16000.0f

        // Model names (must match folder name in assets/)
        const val MODEL_SMALL_EN  = "vosk-model-small-en-us-0.15"   // ~50MB — recommended
        const val MODEL_SMALL_ES  = "vosk-model-small-es-0.42"       // Spanish
        const val MODEL_SMALL_FR  = "vosk-model-small-fr-0.22"       // French
        const val MODEL_SMALL_DE  = "vosk-model-small-de-0.15"       // German
        const val MODEL_SMALL_PT  = "vosk-model-small-pt-0.3"        // Portuguese
        const val MODEL_SMALL_ZH  = "vosk-model-small-cn-0.22"       // Chinese
        const val MODEL_SMALL_RU  = "vosk-model-small-ru-0.22"       // Russian
        const val MODEL_SMALL_AR  = "vosk-model-ar-mgb2-0.4"         // Arabic
        const val MODEL_SMALL_HI  = "vosk-model-small-hi-0.22"       // Hindi
        const val MODEL_SMALL_JA  = "vosk-model-small-ja-0.22"       // Japanese
    }
}

// ── STT State ─────────────────────────────────────────────────────────────────

sealed class SttState {
    object Idle         : SttState()
    object LoadingModel : SttState()
    object Ready        : SttState()
    object Listening    : SttState()
    object Timeout      : SttState()
    data class Result(val text: String) : SttState()
    data class Error(val message: String) : SttState()
}