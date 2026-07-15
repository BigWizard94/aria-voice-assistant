package com.bigwizard.aria.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.bigwizard.aria.AriaApplication
import com.bigwizard.aria.R
import com.bigwizard.aria.ai.AiEngine
import com.bigwizard.aria.ai.CommandParser
import com.bigwizard.aria.data.model.*
import com.bigwizard.aria.stt.SpeechRecognitionEngine
import com.bigwizard.aria.stt.SttState
import com.bigwizard.aria.tts.TextToSpeechEngine
import com.bigwizard.aria.ui.screens.MainActivity
import com.bigwizard.aria.util.CommandExecutor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

/**
 * AriaListenerService — The central foreground service.
 *
 * Orchestrates the full voice pipeline:
 *   Microphone → Vosk STT → CommandParser → AiEngine → TTS → Speaker
 *
 * Runs as a foreground service so it stays alive even when the app
 * is in the background — essential for a true assistant experience.
 */
class AriaListenerService : Service() {

    // ── Engines ───────────────────────────────────────────────────────────────

    private lateinit var sttEngine: SpeechRecognitionEngine
    private lateinit var ttsEngine: TextToSpeechEngine
    private lateinit var aiEngine: AiEngine
    private lateinit var commandExecutor: CommandExecutor

    // ── Coroutines ────────────────────────────────────────────────────────────

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ── State ─────────────────────────────────────────────────────────────────

    private val _assistantState = MutableStateFlow<AssistantState>(AssistantState.Idle)
    val assistantState: StateFlow<AssistantState> = _assistantState.asStateFlow()

    private var conversationHistory = mutableListOf<ChatMessage>()
    private var currentSessionId = UUID.randomUUID().toString()
    private var currentSettings = AppSettings()

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
        aiEngine  = AiEngine()
        commandExecutor = CommandExecutor(this)

        startForeground(
            AriaApplication.NOTIFICATION_ID_LISTENING,
            buildNotification("Aria is ready")
        )

        observeSettings()
        observeSttResults()
        loadSttModel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_LISTENING -> startListening()
            ACTION_STOP_LISTENING  -> stopListening()
            ACTION_STOP_SERVICE    -> stopSelf()
        }
        return START_STICKY // Restart if killed by system
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        sttEngine.destroy()
        ttsEngine.destroy()
        Log.i(TAG, "AriaListenerService destroyed")
    }

    // ── Settings Observer ─────────────────────────────────────────────────────

    private fun observeSettings() {
        val app = application as AriaApplication
        serviceScope.launch {
            app.preferencesManager.appSettingsFlow.collect { settings ->
                currentSettings = settings
            }
        }
    }

    // ── Model Loading ─────────────────────────────────────────────────────────

    private fun loadSttModel() {
        _assistantState.value = AssistantState.Processing
        sttEngine.loadModel { success ->
            if (success) {
                _assistantState.value = AssistantState.Idle
                updateNotification("Aria is ready")
                Log.i(TAG, "STT model loaded successfully")
            } else {
                _assistantState.value = AssistantState.Error("Failed to load speech model")
                updateNotification("Speech model failed to load")
            }
        }
    }

    // ── STT Result Observer ───────────────────────────────────────────────────

    private fun observeSttResults() {
        // Observe final results
        serviceScope.launch {
            sttEngine.finalResult.collect { text ->
                if (text.isNotBlank()) {
                    Log.i(TAG, "Processing voice input: $text")
                    processVoiceInput(text)
                }
            }
        }

        // Observe STT state changes
        serviceScope.launch {
            sttEngine.sttState.collect { state ->
                when (state) {
                    is SttState.Listening -> {
                        _assistantState.value = AssistantState.Listening
                        updateNotification("Listening...")
                    }
                    is SttState.Timeout -> {
                        _assistantState.value = AssistantState.Idle
                        updateNotification("Aria is ready")
                    }
                    is SttState.Error -> {
                        _assistantState.value = AssistantState.Error(state.message)
                    }
                    else -> {}
                }
            }
        }
    }

    // ── Voice Pipeline ────────────────────────────────────────────────────────

    fun startListening() {
        if (_assistantState.value is AssistantState.Speaking) {
            ttsEngine.stop()
        }
        sttEngine.startListening()
        broadcastState(AssistantState.Listening)
    }

    fun stopListening() {
        sttEngine.stopListening()
        broadcastState(AssistantState.Idle)
    }

    private fun processVoiceInput(text: String) {
        sttEngine.stopListening()
        _assistantState.value = AssistantState.Processing
        updateNotification("Thinking...")

        serviceScope.launch {
            // Save user message to DB
            saveMessage(role = "user", content = text)

            // Parse for system commands first
            val command = CommandParser.parse(text)

            when (command) {
                is VoiceCommand.StopListening -> {
                    ttsEngine.speak("Goodbye!", speed = currentSettings.voiceSpeed)
                    _assistantState.value = AssistantState.Idle
                    updateNotification("Aria is ready")
                }

                is VoiceCommand.PhoneCall -> {
                    val response = "Calling ${command.contact}"
                    speak(response)
                    commandExecutor.makeCall(command.contact)
                }

                is VoiceCommand.SendSms -> {
                    val response = "Sending message to ${command.contact}"
                    speak(response)
                    commandExecutor.sendSms(command.contact, command.message)
                }

                is VoiceCommand.SetAlarm -> {
                    val response = "Setting alarm for ${command.time}"
                    speak(response)
                    commandExecutor.setAlarm(command.time)
                }

                is VoiceCommand.SetTimer -> {
                    val response = "Timer set for ${command.duration}"
                    speak(response)
                    commandExecutor.setTimer(command.duration)
                }

                is VoiceCommand.OpenApp -> {
                    val response = "Opening ${command.appName}"
                    speak(response)
                    commandExecutor.openApp(command.appName)
                }

                is VoiceCommand.WebSearch -> {
                    val response = "Searching for ${command.query}"
                    speak(response)
                    commandExecutor.webSearch(command.query)
                }

                is VoiceCommand.PlayMusic -> {
                    val response = "Playing ${command.query}"
                    speak(response)
                    commandExecutor.playMusic(command.query)
                }

                is VoiceCommand.AiQuery, is VoiceCommand.Unknown -> {
                    // Send to AI engine
                    queryAi(text)
                }
            }
        }
    }

    // ── AI Query ──────────────────────────────────────────────────────────────

    private suspend fun queryAi(userText: String) {
        val config = currentSettings.aiConfig

        if (config.apiKey.isBlank() && !isLocalEndpoint(config.baseUrl)) {
            val errorMsg = "Please set up your AI API key in settings to use Aria's brain."
            speak(errorMsg)
            saveMessage(role = "assistant", content = errorMsg)
            return
        }

        // Add to conversation history (keep last 10 exchanges for context)
        conversationHistory.add(ChatMessage(role = "user", content = userText))
        if (conversationHistory.size > 20) {
            conversationHistory = conversationHistory.takeLast(20).toMutableList()
        }

        val result = aiEngine.complete(config, conversationHistory)

        result.fold(
            onSuccess = { response ->
                conversationHistory.add(ChatMessage(role = "assistant", content = response))
                saveMessage(role = "assistant", content = response)
                speak(response)
            },
            onFailure = { error ->
                val errorMsg = when {
                    error.message?.contains("401") == true ->
                        "Invalid API key. Please check your settings."
                    error.message?.contains("429") == true ->
                        "Too many requests. Please wait a moment."
                    error.message?.contains("timeout") == true ->
                        "Request timed out. Please try again."
                    error.message?.contains("Unable to resolve host") == true ->
                        "No internet connection. Check your network."
                    else -> "I had trouble connecting. Please try again."
                }
                Log.e(TAG, "AI query failed: ${error.message}")
                speak(errorMsg)
                saveMessage(role = "assistant", content = errorMsg, isError = true)
            }
        )
    }

    // ── TTS ───────────────────────────────────────────────────────────────────

    private fun speak(text: String) {
        _assistantState.value = AssistantState.Speaking(text)
        updateNotification("Speaking...")
        broadcastState(AssistantState.Speaking(text))

        ttsEngine.speak(
            text  = text,
            speed = currentSettings.voiceSpeed,
            pitch = currentSettings.voicePitch,
            onDone = {
                _assistantState.value = AssistantState.Idle
                updateNotification("Aria is ready")
                broadcastState(AssistantState.Idle)
            }
        )
    }

    // ── Database ──────────────────────────────────────────────────────────────

    private suspend fun saveMessage(role: String, content: String, isError: Boolean = false) {
        val app = application as AriaApplication
        val message = Message(
            sessionId = currentSessionId,
            role      = role,
            content   = content,
            isError   = isError
        )
        withContext(Dispatchers.IO) {
            app.database.messageDao().insertMessage(message)
        }
    }

    // ── Session Management ────────────────────────────────────────────────────

    fun startNewSession() {
        currentSessionId = UUID.randomUUID().toString()
        conversationHistory.clear()
        Log.i(TAG, "New session started: $currentSessionId")
    }

    fun getCurrentSessionId(): String = currentSessionId

    // ── Notification ──────────────────────────────────────────────────────────

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, AriaApplication.CHANNEL_LISTENING)
            .setContentTitle("Aria Voice Assistant")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_aria_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(AriaApplication.NOTIFICATION_ID_LISTENING, buildNotification(text))
    }

    // ── Broadcast ─────────────────────────────────────────────────────────────

    private fun broadcastState(state: AssistantState) {
        val intent = Intent(ACTION_STATE_CHANGED).apply {
            putExtra(EXTRA_STATE, state.javaClass.simpleName)
            if (state is AssistantState.Speaking) {
                putExtra(EXTRA_TEXT, state.text)
            }
        }
        sendBroadcast(intent)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun isLocalEndpoint(url: String): Boolean {
        return url.contains("localhost") || url.contains("127.0.0.1") || url.contains("10.0.2.2")
    }

    // ── Companion ─────────────────────────────────────────────────────────────

    companion object {
        private const val TAG = "AriaListenerService"

        const val ACTION_START_LISTENING = "com.bigwizard.aria.START_LISTENING"
        const val ACTION_STOP_LISTENING  = "com.bigwizard.aria.STOP_LISTENING"
        const val ACTION_STOP_SERVICE    = "com.bigwizard.aria.STOP_SERVICE"
        const val ACTION_STATE_CHANGED   = "com.bigwizard.aria.STATE_CHANGED"
        const val EXTRA_STATE            = "extra_state"
        const val EXTRA_TEXT             = "extra_text"
    }
}