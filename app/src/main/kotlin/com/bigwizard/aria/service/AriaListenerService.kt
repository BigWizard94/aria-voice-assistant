package com.bigwizard.aria.service

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.bigwizard.aria.AriaApplication
import com.bigwizard.aria.R
import com.bigwizard.aria.ai.AiEngine
import com.bigwizard.aria.ai.AiHttpException
import com.bigwizard.aria.ai.CommandParser
import com.bigwizard.aria.data.model.AppSettings
import com.bigwizard.aria.data.model.AssistantState
import com.bigwizard.aria.data.model.ChatMessage
import com.bigwizard.aria.data.model.Message
import com.bigwizard.aria.data.model.VoiceCommand
import com.bigwizard.aria.stt.SpeechRecognitionEngine
import com.bigwizard.aria.stt.SttState
import com.bigwizard.aria.tts.TextToSpeechEngine
import com.bigwizard.aria.ui.screens.MainActivity
import com.bigwizard.aria.util.CommandExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * AriaListenerService — The central service.
 *
 * Orchestrates the full voice pipeline:
 *   Microphone → Vosk STT → CommandParser → (CommandExecutor | AiEngine) → TTS
 *
 * It is started with startService() + bound by the UI, and promotes itself to
 * a *microphone* foreground service while the app is visible and RECORD_AUDIO
 * is granted. On Android 14 a microphone FGS may only be started from the
 * foreground and with the permission already granted — starting it any other
 * way (e.g. at boot, or before the permission dialog) throws and crashes.
 */
class AriaListenerService : Service() {

    // ── Engines ───────────────────────────────────────────────────────────────

    private lateinit var sttEngine: SpeechRecognitionEngine
    private lateinit var ttsEngine: TextToSpeechEngine
    private lateinit var commandExecutor: CommandExecutor
    private val aiEngine: AiEngine get() = (application as AriaApplication).aiEngine

    // ── Coroutines ────────────────────────────────────────────────────────────

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var aiJob: Job? = null

    // ── State ─────────────────────────────────────────────────────────────────

    private val _assistantState = MutableStateFlow<AssistantState>(AssistantState.Idle)
    val assistantState: StateFlow<AssistantState> = _assistantState.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val conversationHistory = ArrayDeque<ChatMessage>()
    private var currentSessionId: String? = null
    private var currentSettings = AppSettings()
    private var listenWhenReady = false
    private var isForeground = false

    // ── Binder ────────────────────────────────────────────────────────────────

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): AriaListenerService = this@AriaListenerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "AriaListenerService created")

        sttEngine = SpeechRecognitionEngine(this)
        ttsEngine = TextToSpeechEngine(this)
        commandExecutor = CommandExecutor(this)

        observeSettings()
        observeSession()
        observeStt()
        loadSttModel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_LISTENING -> { ensureForeground(); startListening() }
            ACTION_STOP_LISTENING  -> stopListening()
            ACTION_STOP_SERVICE    -> {
                stopListening()
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                isForeground = false
                stopSelf()
            }
            else -> ensureForeground()
        }
        // Never let the system restart us from the background: a restarted
        // service cannot legally re-enter the microphone foreground state.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        sttEngine.destroy()
        ttsEngine.destroy()
        Log.i(TAG, "AriaListenerService destroyed")
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Android Go: give the ~50 MB speech model back when the UI is gone and we're idle.
        @Suppress("DEPRECATION")
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND &&
            _assistantState.value is AssistantState.Idle
        ) {
            sttEngine.unloadModel()
        }
    }

    /**
     * Enters the microphone foreground state. Only call while the app is in the
     * foreground (from the UI or an assistant invocation).
     */
    fun ensureForeground(): Boolean {
        if (isForeground) return true
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "RECORD_AUDIO not granted — staying a background service")
            return false
        }
        return try {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                0
            }
            ServiceCompat.startForeground(
                this,
                AriaApplication.NOTIFICATION_ID_LISTENING,
                buildNotification(getString(R.string.notification_listening_text)),
                type
            )
            isForeground = true
            true
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException / SecurityException
            Log.e(TAG, "Could not enter foreground", e)
            false
        }
    }

    // ── Observers ─────────────────────────────────────────────────────────────

    private fun observeSettings() {
        val prefs = (application as AriaApplication).preferencesManager
        serviceScope.launch {
            prefs.appSettingsFlow.collect { settings -> currentSettings = settings }
        }
    }

    private fun observeSession() {
        val prefs = (application as AriaApplication).preferencesManager
        serviceScope.launch {
            prefs.getOrCreateSessionId()
            prefs.currentSessionIdFlow.collect { id ->
                if (id != null && id != currentSessionId) {
                    if (currentSessionId != null) Log.i(TAG, "New session started: $id")
                    currentSessionId = id
                    conversationHistory.clear()
                }
            }
        }
    }

    private fun observeStt() {
        serviceScope.launch {
            sttEngine.finalResult.collect { text ->
                if (text.isNotBlank()) {
                    Log.i(TAG, "Processing voice input")
                    handleUserText(text)
                }
            }
        }
        serviceScope.launch {
            sttEngine.partialResult.collect { partial -> _partialText.value = partial }
        }
        serviceScope.launch {
            sttEngine.sttState.collect { state ->
                when (state) {
                    is SttState.LoadingModel -> setState(
                        AssistantState.Preparing(getString(R.string.state_loading_model)),
                        getString(R.string.state_loading_model)
                    )
                    is SttState.Downloading -> {
                        val label = state.progress?.let {
                            getString(R.string.state_downloading_model_pct, (it * 100).toInt())
                        } ?: getString(R.string.state_unpacking_model)
                        setState(AssistantState.Preparing(label, state.progress), label)
                    }
                    is SttState.Ready -> if (_assistantState.value is AssistantState.Preparing ||
                        _assistantState.value is AssistantState.Listening
                    ) {
                        setState(AssistantState.Idle, getString(R.string.notification_listening_text))
                    }
                    is SttState.Listening -> setState(AssistantState.Listening, getString(R.string.state_listening))
                    is SttState.Timeout -> {
                        _partialText.value = ""
                        setState(AssistantState.Idle, getString(R.string.notification_listening_text))
                    }
                    is SttState.Error -> {
                        _partialText.value = ""
                        listenWhenReady = false
                        setState(AssistantState.Error(state.message), state.message)
                    }
                    is SttState.Idle, is SttState.Result -> Unit
                }
            }
        }
    }

    // ── Model Loading ─────────────────────────────────────────────────────────

    private fun loadSttModel() {
        val modelName = SpeechRecognitionEngine.modelForLanguage(currentSettings.language)
        sttEngine.loadModel(modelName) { success ->
            if (success && listenWhenReady) {
                listenWhenReady = false
                startListening()
            }
        }
    }

    // ── Voice Pipeline ────────────────────────────────────────────────────────

    fun startListening() {
        if (_assistantState.value is AssistantState.Speaking) ttsEngine.stop()
        if (!sttEngine.isModelLoaded) {
            // First run (download) or model unloaded for memory: listen as soon as it's ready.
            listenWhenReady = true
            if (!sttEngine.isLoading) loadSttModel()
            return
        }
        ensureForeground()
        _partialText.value = ""
        sttEngine.startListening()
    }

    fun stopListening() {
        listenWhenReady = false
        sttEngine.stopListening()
        _partialText.value = ""
        if (_assistantState.value is AssistantState.Speaking) ttsEngine.stop()
        if (_assistantState.value !is AssistantState.Processing) {
            setState(AssistantState.Idle, getString(R.string.notification_listening_text))
        }
    }

    /** Typed or tapped input (e.g. a suggestion chip) goes through the same pipeline. */
    fun submitText(text: String) {
        if (text.isBlank()) return
        if (_assistantState.value is AssistantState.Speaking) ttsEngine.stop()
        sttEngine.stopListening()
        handleUserText(text)
    }

    private fun handleUserText(text: String) {
        sttEngine.stopListening()
        _partialText.value = ""
        setState(AssistantState.Processing, getString(R.string.state_processing))

        aiJob?.cancel()
        aiJob = serviceScope.launch {
            saveMessage(role = "user", content = text)

            val reply: String? = when (val command = CommandParser.parse(text)) {
                is VoiceCommand.StopListening -> getString(R.string.cmd_goodbye)
                is VoiceCommand.PhoneCall -> commandExecutor.makeCall(command.contact)
                is VoiceCommand.SendSms -> commandExecutor.sendSms(command.contact, command.message)
                is VoiceCommand.SetAlarm -> commandExecutor.setAlarm(command.time)
                is VoiceCommand.SetTimer -> commandExecutor.setTimer(command.duration)
                is VoiceCommand.OpenApp -> commandExecutor.openApp(command.appName)
                is VoiceCommand.WebSearch -> commandExecutor.webSearch(command.query)
                is VoiceCommand.PlayMusic -> commandExecutor.playMusic(command.query)
                is VoiceCommand.AiQuery, is VoiceCommand.Unknown -> {
                    queryAi(text)
                    null // queryAi saves and speaks its own reply
                }
            }
            if (reply != null) {
                saveMessage(role = "assistant", content = reply)
                speak(reply)
            }
        }
    }

    // ── AI Query ──────────────────────────────────────────────────────────────

    private suspend fun queryAi(userText: String) {
        val config = currentSettings.aiConfig

        if (config.apiKey.isBlank() && AiEngine.requiresApiKey(config.baseUrl)) {
            val errorMsg = getString(R.string.error_no_api_key)
            saveMessage(role = "assistant", content = errorMsg, isError = true)
            speak(errorMsg)
            return
        }

        // Keep the last 10 exchanges for context (bounded for low-RAM devices)
        conversationHistory.addLast(ChatMessage(role = "user", content = userText))
        while (conversationHistory.size > MAX_HISTORY) conversationHistory.removeFirst()

        val result = aiEngine.complete(config, conversationHistory.toList())

        result.fold(
            onSuccess = { response ->
                conversationHistory.addLast(ChatMessage(role = "assistant", content = response))
                saveMessage(role = "assistant", content = response)
                speak(response)
            },
            onFailure = { error ->
                // Drop the unanswered question so the next request stays well-formed.
                conversationHistory.removeLastOrNull()
                val errorMsg = describeAiError(error)
                Log.e(TAG, "AI query failed: ${error.message}")
                saveMessage(role = "assistant", content = errorMsg, isError = true)
                speak(errorMsg)
            }
        )
    }

    private fun describeAiError(error: Throwable): String = when {
        error is AiHttpException && (error.code == 401 || error.code == 403) ->
            getString(R.string.error_api_invalid_key)
        error is AiHttpException && error.code == 429 ->
            getString(R.string.error_rate_limited)
        error is AiHttpException && (error.code == 404 ||
            (error.code == 400 && error.serverMessage.contains("model", ignoreCase = true))) ->
            getString(R.string.error_model_unavailable, currentSettings.aiConfig.modelName)
        error is SocketTimeoutException -> getString(R.string.error_timeout)
        error is UnknownHostException -> getString(R.string.error_no_internet)
        error is ConnectException -> getString(R.string.error_cannot_reach_server)
        error is IllegalArgumentException -> getString(R.string.error_bad_url)
        else -> getString(R.string.error_ai_generic)
    }

    // ── TTS ───────────────────────────────────────────────────────────────────

    private fun speak(text: String) {
        val speaking = AssistantState.Speaking(text)
        setState(speaking, getString(R.string.state_speaking))

        ttsEngine.speak(
            text  = text,
            speed = currentSettings.voiceSpeed,
            pitch = currentSettings.voicePitch,
            onDone = {
                // Called on a TTS binder thread.
                serviceScope.launch {
                    // Only reset if nothing newer (e.g. the user tapped the mic) took over.
                    if (_assistantState.value === speaking) {
                        setState(AssistantState.Idle, getString(R.string.notification_listening_text))
                        if (currentSettings.autoListen) startListening()
                    }
                }
            }
        )
    }

    // ── Database ──────────────────────────────────────────────────────────────

    private suspend fun saveMessage(role: String, content: String, isError: Boolean = false) {
        val app = application as AriaApplication
        val sessionId = currentSessionId ?: app.preferencesManager.getOrCreateSessionId()
        val message = Message(
            sessionId = sessionId,
            role      = role,
            content   = content,
            isError   = isError
        )
        try {
            withContext(Dispatchers.IO) { app.database.messageDao().insertMessage(message) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save message", e)
        }
    }

    // ── State + Notification ──────────────────────────────────────────────────

    private fun setState(state: AssistantState, notificationText: String) {
        _assistantState.value = state
        updateNotification(notificationText)
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, AriaListenerService::class.java).setAction(ACTION_STOP_SERVICE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, AriaApplication.CHANNEL_LISTENING)
            .setContentTitle(getString(R.string.notification_listening_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_aria_notification)
            .setColor(ContextCompat.getColor(this, R.color.notification_icon_bg))
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.notification_action_stop), stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        if (!isForeground) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            nm.notify(AriaApplication.NOTIFICATION_ID_LISTENING, buildNotification(text))
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS denied on Android 13+: the FGS still runs, just without updates.
            Log.w(TAG, "Cannot update notification", e)
        }
    }

    // ── Companion ─────────────────────────────────────────────────────────────

    companion object {
        private const val TAG = "AriaListenerService"
        private const val MAX_HISTORY = 20

        const val ACTION_START_LISTENING = "com.bigwizard.aria.START_LISTENING"
        const val ACTION_STOP_LISTENING  = "com.bigwizard.aria.STOP_LISTENING"
        const val ACTION_STOP_SERVICE    = "com.bigwizard.aria.STOP_SERVICE"
    }
}
