package com.bigwizard.aria.stt

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * Offline speech-to-text engine backed by Vosk.
 *
 * FIX (Aug 2026): the previous implementation used Vosk's
 * StorageService.unpack(), which copies the model out of the app's
 * `assets/` folder. No model was ever bundled into assets (nothing
 * in the repo, nothing in CI), so unpack() threw an IOException on
 * every single launch, which surfaced in the UI as a permanent red
 * error orb before the user ever tapped anything.
 *
 * This version checks internal storage for an already-downloaded
 * model first, and if it's missing, downloads + unzips it from
 * Alphacephei's public Vosk model repo before loading it. No assets
 * bundling required, no APK size hit, and it plays nicer with
 * F-Droid's reproducible-build process than fetching the model at
 * CI/build time would.
 */
class SpeechRecognitionEngine(private val context: Context) {

    companion object {
        private const val TAG = "SpeechRecognitionEngine"
        private const val SAMPLE_RATE = 16000.0f
        private const val MODEL_BASE_URL = "https://alphacephei.com/vosk/models"

        const val MODEL_SMALL_EN = "vosk-model-small-en-us-0.15"
        const val MODEL_SMALL_FR = "vosk-model-small-fr-0.22"
        const val MODEL_SMALL_DE = "vosk-model-small-de-0.15"
        const val MODEL_SMALL_ES = "vosk-model-small-es-0.42"
        const val MODEL_SMALL_PT = "vosk-model-small-pt-0.3"
        const val MODEL_SMALL_RU = "vosk-model-small-ru-0.22"
        const val MODEL_SMALL_ZH = "vosk-model-small-cn-0.22"
        const val MODEL_SMALL_JA = "vosk-model-small-ja-0.22"
        const val MODEL_SMALL_HI = "vosk-model-small-hi-0.22"
        const val MODEL_SMALL_AR = "vosk-model-ar-mgb2-0.4"
    }

    // Own scope so downloads/loads survive recomposition but die with the service.
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient()

    private val _sttState = MutableStateFlow<SttState>(SttState.Idle)
    val sttState: StateFlow<SttState> = _sttState.asStateFlow()

    private val _partialResult = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val partialResult: SharedFlow<String> = _partialResult.asSharedFlow()

    private val _finalResult = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val finalResult: SharedFlow<String> = _finalResult.asSharedFlow()

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var isModelLoaded = false

    private val recognitionListener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String?) {
            hypothesis ?: return
            try {
                val partial = JSONObject(hypothesis).optString("partial", "")
                if (partial.isNotBlank()) _partialResult.tryEmit(partial)
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

        override fun onFinalResult(hypothesis: String?) = onResult(hypothesis)

        override fun onError(exception: Exception?) {
            Log.e(TAG, "STT error", exception)
            _sttState.value = SttState.Error(exception?.message ?: "Speech recognition error")
        }

        override fun onTimeout() {
            Log.i(TAG, "STT timeout — no speech detected")
            _sttState.value = SttState.Timeout
        }
    }

    /**
     * Loads (or downloads-then-loads) the Vosk model. Safe to call
     * repeatedly — a no-op once a model is already loaded.
     */
    fun loadModel(modelName: String = MODEL_SMALL_EN, onComplete: (Boolean) -> Unit = {}) {
        if (isModelLoaded) {
            onComplete(true)
            return
        }
        _sttState.value = SttState.LoadingModel
        val modelDir = File(context.filesDir, modelName)

        engineScope.launch {
            try {
                if (!modelDir.exists() || !File(modelDir, "uuid").exists()) {
                    Log.i(TAG, "Model not found on disk, downloading $modelName...")
                    downloadAndUnpackModel(modelName, modelDir)
                }
                val loadedModel = Model(modelDir.absolutePath)
                model = loadedModel
                isModelLoaded = true
                withContext(Dispatchers.Main) {
                    _sttState.value = SttState.Ready
                    Log.i(TAG, "Vosk model loaded: $modelName")
                    onComplete(true)
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to load Vosk model", e)
                withContext(Dispatchers.Main) {
                    _sttState.value = SttState.Error("Failed to load speech model: ${e.message}")
                    onComplete(false)
                }
            }
        }
    }

    private fun downloadAndUnpackModel(modelName: String, modelDir: File) {
        val zipFile = File(context.cacheDir, "$modelName.zip")
        val request = Request.Builder()
            .url("$MODEL_BASE_URL/$modelName.zip")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Model download failed: HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("Empty response body for model download")
            FileOutputStream(zipFile).use { output ->
                body.byteStream().copyTo(output)
            }
        }

        if (modelDir.exists()) modelDir.deleteRecursively()
        unzip(zipFile, context.filesDir)
        zipFile.delete()

        if (!File(modelDir, "uuid").exists()) {
            throw IOException("Downloaded model archive did not produce expected folder: ${modelDir.name}")
        }
    }

    private fun unzip(zipFile: File, targetDir: File) {
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            val buffer = ByteArray(8192)
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos ->
                        var len: Int
                        while (zis.read(buffer).also { len = it } > 0) {
                            fos.write(buffer, 0, len)
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    fun startListening() {
        val loadedModel = model
        if (!isModelLoaded || loadedModel == null) {
            _sttState.value = SttState.Error("Speech model not loaded yet")
            return
        }
        if (_sttState.value is SttState.Listening) {
            Log.w(TAG, "Already listening")
            return
        }
        try {
            val recognizer = Recognizer(loadedModel, SAMPLE_RATE)
            speechService = SpeechService(recognizer, SAMPLE_RATE).also {
                it.startListening(recognitionListener)
            }
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

    fun destroy() {
        stopListening()
        model?.close()
        model = null
        isModelLoaded = false
        _sttState.value = SttState.Idle
        Log.i(TAG, "SpeechRecognitionEngine destroyed")
    }
}
