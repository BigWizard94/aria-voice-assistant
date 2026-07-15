package com.bigwizard.aria.ui.screens

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bigwizard.aria.AriaApplication
import com.bigwizard.aria.ai.AiEngine
import com.bigwizard.aria.data.model.*
import com.bigwizard.aria.service.AriaListenerService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * MainViewModel — UI state management for the main assistant screen.
 *
 * Connects to AriaListenerService via binding,
 * exposes reactive state flows for Compose UI.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AriaApplication

    // ── Service Binding ───────────────────────────────────────────────────────

    private var ariaService: AriaListenerService? = null
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? AriaListenerService.LocalBinder
            ariaService = localBinder?.getService()
            isBound = true

            // Observe service state
            viewModelScope.launch {
                ariaService?.assistantState?.collect { state ->
                    _assistantState.value = state
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            ariaService = null
            isBound = false
        }
    }

    // ── UI State ──────────────────────────────────────────────────────────────

    private val _assistantState = MutableStateFlow<AssistantState>(AssistantState.Idle)
    val assistantState: StateFlow<AssistantState> = _assistantState.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _connectionTestResult = MutableStateFlow<String?>(null)
    val connectionTestResult: StateFlow<String?> = _connectionTestResult.asStateFlow()

    private val _onboardingDone = MutableStateFlow(false)
    val onboardingDone: StateFlow<Boolean> = _onboardingDone.asStateFlow()

    val currentSessionId: String
        get() = ariaService?.getCurrentSessionId() ?: "default"

    // ── Initialization ────────────────────────────────────────────────────────

    init {
        observeSettings()
        observeOnboarding()
        startAndBindService()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            app.preferencesManager.appSettingsFlow.collect { settings ->
                _settings.value = settings
            }
        }
    }

    private fun observeOnboarding() {
        viewModelScope.launch {
            app.preferencesManager.onboardingDoneFlow.collect { done ->
                _onboardingDone.value = done
            }
        }
    }

    // ── Service Control ───────────────────────────────────────────────────────

    fun startAndBindService() {
        val context = getApplication<Application>()
        val intent = Intent(context, AriaListenerService::class.java)
        context.startForegroundService(intent)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        _isServiceRunning.value = true
    }

    fun stopService() {
        val context = getApplication<Application>()
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
        }
        val intent = Intent(context, AriaListenerService::class.java).apply {
            action = AriaListenerService.ACTION_STOP_SERVICE
        }
        context.startService(intent)
        _isServiceRunning.value = false
    }

    // ── Voice Control ─────────────────────────────────────────────────────────

    fun startListening() {
        ariaService?.startListening()
            ?: run {
                // Service not bound yet — send via intent
                val context = getApplication<Application>()
                val intent = Intent(context, AriaListenerService::class.java).apply {
                    action = AriaListenerService.ACTION_START_LISTENING
                }
                context.startService(intent)
            }
    }

    fun stopListening() {
        ariaService?.stopListening()
    }

    fun startNewSession() {
        ariaService?.startNewSession()
        _messages.value = emptyList()
        _partialText.value = ""
    }

    // ── Message Loading ───────────────────────────────────────────────────────

    fun loadMessages(sessionId: String) {
        viewModelScope.launch {
            app.database.messageDao()
                .getMessagesForSession(sessionId)
                .collect { msgs ->
                    _messages.value = msgs
                }
        }
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    fun saveAiConfig(config: AiConfig) {
        viewModelScope.launch {
            app.preferencesManager.saveAiConfig(config)
        }
    }

    fun saveAppSettings(settings: AppSettings) {
        viewModelScope.launch {
            app.preferencesManager.saveAppSettings(settings)
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            app.preferencesManager.setOnboardingDone(true)
        }
    }

    // ── Connection Test ───────────────────────────────────────────────────────

    fun testAiConnection(config: AiConfig) {
        viewModelScope.launch {
            _connectionTestResult.value = "Testing..."
            val aiEngine = AiEngine()
            val result = aiEngine.testConnection(config)
            _connectionTestResult.value = result.fold(
                onSuccess = { "✅ Connected! Response: $it" },
                onFailure = { "❌ Failed: ${it.message}" }
            )
        }
    }

    fun clearConnectionTestResult() {
        _connectionTestResult.value = null
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        val context = getApplication<Application>()
        if (isBound) {
            context.unbindService(serviceConnection)
            isBound = false
        }
    }
}