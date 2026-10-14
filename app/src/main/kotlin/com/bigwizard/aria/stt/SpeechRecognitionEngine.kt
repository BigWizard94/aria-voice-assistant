package com.bigwizard.aria.stt

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.IOException
import java.net.UnknownHostException

/**
 * Offline speech-to-text engine backed by Vosk.
 *
 * - Downloads the model on first run (with progress) via [VoskModelManager].
 * - Shares one loaded model per process.
 * - Releases the AudioRecord and native Recognizer after every utterance
 *   (previously both leaked, so the mic failed after a few dozen uses).
 *
 * All public methods must be called on the main thread.
 */
class SpeechRecognitionEngine(context: Context) {

    companion object {
        private const val TAG = "SpeechRecognitionEngine"
        private const val SAMPLE_RATE = 16000.0f

        const val MODEL_SMALL_EN = "vosk-model-small-en-us-0.15"
        const val MODEL_SMALL_FR = "vosk-model-small-fr-0.22"
        const val MODEL_SMALL_DE = "vosk-model-small-de-0.15"
        const val MODEL_SMALL_ES = "vosk-model-small-es-0.42"
        const val MODEL_SMALL_PT = "vosk-model-small-pt-0.3"
        const val MODEL_SMALL_RU = "vosk-model-small-ru-0.22"
        const val MODEL_SMALL_ZH = "vosk-model-small-cn-0.22"
        const val MODEL_SMALL_JA = "vosk-model-small-ja-0.22"
        const val MODEL_SMALL_HI = "vosk-model-small-hi-0.22"

        /** Note: not a "small" model — ~330 MB download, too big for most Go devices. */
        const val MODEL_AR = "vosk-model-ar-mgb2-0.4"

        /** Maps the app's language setting to a Vosk model. */
        fun modelForLanguage(language: String): String = when (language.lowercase().substringBefore('-')) {
            "fr" -> MODEL_SMALL_FR
            "de" -> MODEL_SMALL_DE
            "es" -> MODEL_SMALL_ES
            "pt" -> MODEL_SMALL_PT
            "ru" -> MODEL_SMALL_RU
            "zh" -> MODEL_SMALL_ZH
            "ja" -> MODEL_SMALL_JA
            "hi" -> MODEL_SMALL_HI
            "ar" -> MODEL_AR
            else -> MODEL_SMALL_EN
        }
    }

    private val appContext = context.applicationContext
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _sttState = MutableStateFlow<SttState>(SttState.Idle)
    val sttState: StateFlow<SttState> = _sttState.asStateFlow()

    private val _partialResult = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val partialResult: SharedFlow<String> = _partialResult.asSharedFlow()

    private val _finalResult = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val finalResult: SharedFlow<String> = _finalResult.asSharedFlow()

    private var model: Model? = null
    private var loadJob: Job? = null
    private var speechService: SpeechService? = null
    private var recognizer: Recognizer? = null

    val isModelLoaded: Boolean get() = model != null
    val isLoading: Boolean get() = loadJob?.isActive == true

    private val recognitionListener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String?) {
            val partial = parse(hypothesis, "partial")
            if (partial.isNotBlank()) _partialResult.tryEmit(partial)
        }

        override fun onResult(hypothesis: String?) {
            val text = parse(hypothesis, "text")
            if (text.isNotBlank()) {
                Log.i(TAG, "Final STT result: $text")
                _sttState.value = SttState.Result(text)
                _finalResult.tryEmit(text)
            }
        }

        override fun onFinalResult(hypothesis: String?) = onResult(hypothesis)

        override fun onError(exception: Exception?) {
            Log.e(TAG, "STT error", exception)
            releaseAudio()
            _sttState.value = SttState.Error(exception?.message ?: "Speech recognition error")
        }

        override fun onTimeout() {
            Log.i(TAG, "STT timeout — no speech detected")
            releaseAudio()
            _sttState.value = SttState.Timeout
        }
    }

    private fun parse(hypothesis: String?, key: String): String {
        hypothesis ?: return ""
        return try {
            JSONObject(hypothesis).optString(key, "")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse STT JSON: $hypothesis")
            ""
        }
    }

    /**
     * Loads (downloading first if needed) the Vosk model.
     * Safe to call repeatedly — concurrent calls share one load.
     */
    fun loadModel(modelName: String = MODEL_SMALL_EN, onComplete: (Boolean) -> Unit = {}) {
        if (model != null) {
            onComplete(true)
            return
        }
        if (loadJob?.isActive == true) return

        _sttState.value = SttState.LoadingModel
        loadJob = engineScope.launch {
            try {
                val loaded = VoskModelManager.acquire(appContext, modelName) { progress ->
                    _sttState.value = SttState.Downloading(progress)
                }
                model = loaded
                _sttState.value = SttState.Ready
                Log.i(TAG, "Vosk model loaded: $modelName")
                onComplete(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Throwable: the native loader can also throw UnsatisfiedLinkError / OOM.
                Log.e(TAG, "Failed to load Vosk model", e)
                val reason = when (e) {
                    is UnknownHostException -> "No internet connection to download the speech model"
                    is OutOfMemoryError -> "Not enough memory to load the speech model"
                    else -> e.message ?: e.javaClass.simpleName
                }
                _sttState.value = SttState.Error("Speech model unavailable: $reason")
                onComplete(false)
            }
        }
    }

    fun startListening() {
        val loadedModel = model
        if (loadedModel == null) {
            _sttState.value = SttState.Error("Speech model not loaded yet")
            return
        }
        if (speechService != null) {
            Log.w(TAG, "Already listening")
            return
        }
        try {
            val rec = Recognizer(loadedModel, SAMPLE_RATE)
            recognizer = rec
            val service = SpeechService(rec, SAMPLE_RATE)
            speechService = service
            service.startListening(recognitionListener)
            _sttState.value = SttState.Listening
            Log.i(TAG, "Started listening")
        } catch (e: Exception) {
            // IOException: AudioRecord failed (mic busy); SecurityException: permission revoked.
            Log.e(TAG, "Failed to start listening", e)
            releaseAudio()
            _sttState.value = SttState.Error(
                if (e is SecurityException) "Microphone permission is required" else "Microphone error: ${e.message}"
            )
        }
    }

    fun stopListening() {
        releaseAudio()
        if (_sttState.value is SttState.Listening) _sttState.value = SttState.Ready
    }

    fun pauseListening() {
        speechService?.setPause(true)
    }

    fun resumeListening() {
        speechService?.setPause(false)
    }

    /** Stops recording and frees AudioRecord + native recognizer. */
    private fun releaseAudio() {
        speechService?.let {
            it.stop()      // joins the recognizer thread
            it.shutdown()  // releases the AudioRecord
        }
        speechService = null
        recognizer?.close()
        recognizer = null
    }

    /** Frees the model to reclaim RAM (it reloads on the next [loadModel]). */
    fun unloadModel() {
        if (speechService != null || model == null) return
        model = null
        VoskModelManager.release()
        _sttState.value = SttState.Idle
        Log.i(TAG, "Vosk model unloaded to save memory")
    }

    fun destroy() {
        loadJob?.cancel()
        releaseAudio()
        if (model != null) {
            model = null
            VoskModelManager.release()
        }
        engineScope.cancel()
        _sttState.value = SttState.Idle
        Log.i(TAG, "SpeechRecognitionEngine destroyed")
    }
}
