package com.bigwizard.aria.service

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.RemoteException
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.util.Log
import com.bigwizard.aria.AriaApplication
import com.bigwizard.aria.stt.SpeechRecognitionEngine
import com.bigwizard.aria.stt.VoskModelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

/**
 * Offline [RecognitionService] backed by Vosk. A voice interaction service must
 * declare a recognition service (res/xml/interaction_service.xml); the system
 * also offers it to other apps through [SpeechRecognizer].
 *
 * It never downloads the model itself (a third-party caller should not trigger
 * a 40 MB download) — the model must already have been fetched by Aria's app.
 */
class AriaRecognitionService : RecognitionService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var recognizer: Recognizer? = null
    private var callback: Callback? = null
    private var speechStarted = false
    private var delivered = false

    override fun onStartListening(recognizerIntent: Intent?, listener: Callback) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            safeError(listener, SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
            return
        }
        if (speechService != null) {
            safeError(listener, SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
            return
        }
        callback = listener
        speechStarted = false
        delivered = false

        scope.launch {
            try {
                val loaded = model ?: run {
                    val language = (application as AriaApplication).preferencesManager
                        .appSettingsFlow.first().language
                    val modelName = SpeechRecognitionEngine.modelForLanguage(language)
                    if (!VoskModelManager.isInstalled(this@AriaRecognitionService, modelName)) {
                        Log.w(TAG, "Speech model not installed yet — open Aria once to download it")
                        safeError(listener, SpeechRecognizer.ERROR_SERVER)
                        callback = null
                        return@launch
                    }
                    VoskModelManager.acquire(this@AriaRecognitionService, modelName).also { model = it }
                }
                if (callback !== listener) return@launch // cancelled while loading

                val rec = Recognizer(loaded, SAMPLE_RATE)
                recognizer = rec
                speechService = SpeechService(rec, SAMPLE_RATE).also { it.startListening(voskListener) }
                listener.readyForSpeech(Bundle())
            } catch (e: Exception) {
                Log.e(TAG, "Recognition failed to start", e)
                releaseAudio()
                safeError(listener, SpeechRecognizer.ERROR_AUDIO)
                callback = null
            }
        }
    }

    override fun onStopListening(listener: Callback) {
        // Stopping makes Vosk post onFinalResult with whatever was heard.
        speechService?.stop()
    }

    override fun onCancel(listener: Callback) {
        callback = null
        releaseAudio()
    }

    override fun onDestroy() {
        callback = null
        releaseAudio()
        if (model != null) {
            model = null
            VoskModelManager.release()
        }
        scope.cancel()
        super.onDestroy()
    }

    private val voskListener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String?) {
            val cb = callback ?: return
            val text = parse(hypothesis, "partial")
            if (text.isBlank()) return
            try {
                if (!speechStarted) {
                    speechStarted = true
                    cb.beginningOfSpeech()
                }
                cb.partialResults(resultsBundle(text))
            } catch (e: RemoteException) {
                Log.w(TAG, "Client went away", e)
            }
        }

        override fun onResult(hypothesis: String?) {
            val text = parse(hypothesis, "text")
            if (text.isNotBlank()) deliver(text)
        }

        override fun onFinalResult(hypothesis: String?) {
            val text = parse(hypothesis, "text")
            if (text.isNotBlank()) deliver(text)
            else if (!delivered) callback?.let { safeError(it, SpeechRecognizer.ERROR_NO_MATCH) }
            callback = null
            releaseAudio()
        }

        override fun onError(exception: Exception?) {
            Log.e(TAG, "Vosk error", exception)
            callback?.let { safeError(it, SpeechRecognizer.ERROR_AUDIO) }
            callback = null
            releaseAudio()
        }

        override fun onTimeout() {
            callback?.let { safeError(it, SpeechRecognizer.ERROR_SPEECH_TIMEOUT) }
            callback = null
            releaseAudio()
        }
    }

    /** Delivers one final utterance and ends the session (one result per request). */
    private fun deliver(text: String) {
        val cb = callback ?: return
        if (delivered) return
        delivered = true
        try {
            cb.endOfSpeech()
            cb.results(resultsBundle(text))
        } catch (e: RemoteException) {
            Log.w(TAG, "Client went away", e)
        }
        callback = null
        releaseAudio()
    }

    private fun releaseAudio() {
        speechService?.let {
            it.stop()
            it.shutdown()
        }
        speechService = null
        recognizer?.close()
        recognizer = null
    }

    private fun resultsBundle(text: String) = Bundle().apply {
        putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text))
    }

    private fun parse(hypothesis: String?, key: String): String = try {
        if (hypothesis == null) "" else JSONObject(hypothesis).optString(key, "")
    } catch (e: Exception) {
        ""
    }

    private fun safeError(cb: Callback, error: Int) {
        try {
            cb.error(error)
        } catch (e: RemoteException) {
            Log.w(TAG, "Client went away", e)
        }
    }

    companion object {
        private const val TAG = "AriaRecognitionService"
        private const val SAMPLE_RATE = 16000.0f
    }
}
