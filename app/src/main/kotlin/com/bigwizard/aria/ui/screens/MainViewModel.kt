package com.bigwizard.aria.ui.screens

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bigwizard.aria.AriaApplication
import com.bigwizard.aria.R
import com.bigwizard.aria.data.model.*
import com.bigwizard.aria.service.AriaListenerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * MainViewModel — UI state for the main, settings and history screens.
 *
 * The service is only started/bound by [connectToService], which the activity
 * calls from the foreground after the permission prompt — never from init, so
 * Settings/History (which share this ViewModel class) don't spin it up and the
 * microphone foreground service is never started without RECORD_AUDIO.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AriaApplication
    private val prefs = app.preferencesManager

    // ── Service Binding ───────────────────────────────────────────────────────

    private var ariaService: AriaListenerService? = null
    private var isBound = false
    private var serviceObservers: Job? = null
    private var pendingListen = false
    private var pendingText: String? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as? AriaListenerService.LocalBinder)?.getService() ?: return
            ariaService = service

            serviceObservers?.cancel()
            serviceObservers = viewModelScope.launch {
                launch { service.assistantState.collect { _assistantState.value = it } }
                launch { service.partialText.collect { _partialText.value = it } }
            }

            if (pendingListen) {
                pendingListen = false
                service.ensureForeground()
                service.startListening()
            }
            pendingText?.let {
                pendingText = null
                service.submitText(it)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // Service process crashed/killed; binding stays and reconnects automatically.
            serviceObservers?.cancel()
            ariaService = null
        }
    }

    // ── UI State ──────────────────────────────────────────────────────────────

    private val _assistantState = MutableStateFlow<AssistantState>(AssistantState.Idle)
    val assistantState: StateFlow<AssistantState> = _assistantState.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    val settings: StateFlow<AppSettings> = prefs.appSettingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    /** null while DataStore is still loading — the splash screen stays up meanwhile. */
    val onboardingDone: StateFlow<Boolean?> = prefs.onboardingDoneFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val currentSessionId: StateFlow<String?> = prefs.currentSessionIdFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Messages of the current conversation (follows "new conversation" automatically). */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<Message>> = currentSessionId
        .filterNotNull()
        .flatMapLatest { app.database.messageDao().getMessagesForSession(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** All stored messages, for the history screen. */
    val history: StateFlow<List<Message>> = app.database.messageDao().getAllMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _connectionTestResult = MutableStateFlow<ConnectionTestResult?>(null)
    val connectionTestResult: StateFlow<ConnectionTestResult?> = _connectionTestResult.asStateFlow()

    init {
        viewModelScope.launch { prefs.getOrCreateSessionId() }
    }

    // ── Service Control ───────────────────────────────────────────────────────

    /** Starts + binds the listener service. Call only while an activity is in the foreground. */
    fun connectToService() {
        val context = getApplication<Application>()
        val intent = Intent(context, AriaListenerService::class.java)
        try {
            // Plain startService from the foreground; the service promotes itself
            // to a microphone FGS only once RECORD_AUDIO is granted.
            context.startService(intent)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "App not in foreground; binding only", e)
        }
        if (!isBound) {
            isBound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    fun disconnectFromService() {
        if (isBound) {
            getApplication<Application>().unbindService(serviceConnection)
            isBound = false
        }
        serviceObservers?.cancel()
        ariaService = null
    }

    fun stopService() {
        val context = getApplication<Application>()
        disconnectFromService()
        context.startService(
            Intent(context, AriaListenerService::class.java).setAction(AriaListenerService.ACTION_STOP_SERVICE)
        )
    }

    // ── Voice Control ─────────────────────────────────────────────────────────

    fun startListening() {
        val service = ariaService
        if (service != null) {
            service.ensureForeground()
            service.startListening()
        } else {
            pendingListen = true
            connectToService()
        }
    }

    fun stopListening() {
        pendingListen = false
        ariaService?.stopListening()
    }

    /** Sends typed/tapped text (suggestion chips) through the assistant pipeline. */
    fun sendText(text: String) {
        val service = ariaService
        if (service != null) service.submitText(text) else {
            pendingText = text
            connectToService()
        }
    }

    fun reportMicPermissionDenied() {
        pendingListen = false
        _assistantState.value = AssistantState.Error(
            getApplication<Application>().getString(R.string.permission_mic_denied)
        )
    }

    fun startNewSession() {
        viewModelScope.launch { prefs.startNewSession() }
        _partialText.value = ""
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            app.database.messageDao().deleteAllMessages()
            prefs.startNewSession()
        }
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    fun saveAiConfig(config: AiConfig) {
        viewModelScope.launch { prefs.saveAiConfig(config) }
    }

    fun saveAppSettings(settings: AppSettings) {
        viewModelScope.launch { prefs.saveAppSettings(settings) }
    }

    fun completeOnboarding(config: AiConfig) {
        viewModelScope.launch { prefs.completeOnboarding(config) }
    }

    // ── Connection Test ───────────────────────────────────────────────────────

    fun testAiConnection(config: AiConfig) {
        viewModelScope.launch {
            _connectionTestResult.value = ConnectionTestResult.Testing
            val result = app.aiEngine.testConnection(config)
            _connectionTestResult.value = result.fold(
                onSuccess = { ConnectionTestResult.Success(it) },
                onFailure = { ConnectionTestResult.Failure(it.message ?: it.javaClass.simpleName) }
            )
        }
    }

    fun clearConnectionTestResult() {
        _connectionTestResult.value = null
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    override fun onCleared() {
        disconnectFromService()
        super.onCleared()
    }

    companion object {
        private const val TAG = "MainViewModel"
    }
}

sealed class ConnectionTestResult {
    object Testing : ConnectionTestResult()
    data class Success(val reply: String) : ConnectionTestResult()
    data class Failure(val message: String) : ConnectionTestResult()
}
