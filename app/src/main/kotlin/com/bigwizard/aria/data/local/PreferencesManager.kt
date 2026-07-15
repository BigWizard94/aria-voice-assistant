package com.bigwizard.aria.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.bigwizard.aria.data.model.AiConfig
import com.bigwizard.aria.data.model.AppSettings
import com.bigwizard.aria.data.model.AppTheme
import com.bigwizard.aria.data.model.DEFAULT_SYSTEM_PROMPT
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

// DataStore extension on Context
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "aria_prefs")

/**
 * Manages all persistent app preferences using Jetpack DataStore.
 * Fully reactive — exposes Flows for UI observation.
 */
class PreferencesManager(private val context: Context) {

    // ── Keys ──────────────────────────────────────────────────────────────────

    private object Keys {
        // AI Config
        val AI_BASE_URL      = stringPreferencesKey("ai_base_url")
        val AI_API_KEY       = stringPreferencesKey("ai_api_key")
        val AI_MODEL_NAME    = stringPreferencesKey("ai_model_name")
        val AI_SYSTEM_PROMPT = stringPreferencesKey("ai_system_prompt")
        val AI_MAX_TOKENS    = intPreferencesKey("ai_max_tokens")
        val AI_TEMPERATURE   = floatPreferencesKey("ai_temperature")
        val AI_STREAM        = booleanPreferencesKey("ai_stream")

        // App Settings
        val WAKE_WORD_ENABLED  = booleanPreferencesKey("wake_word_enabled")
        val WAKE_WORD          = stringPreferencesKey("wake_word")
        val VOICE_SPEED        = floatPreferencesKey("voice_speed")
        val VOICE_PITCH        = floatPreferencesKey("voice_pitch")
        val THEME              = stringPreferencesKey("theme")
        val LANGUAGE           = stringPreferencesKey("language")
        val AUTO_LISTEN        = booleanPreferencesKey("auto_listen")
        val HAPTIC_FEEDBACK    = booleanPreferencesKey("haptic_feedback")
        val READ_NOTIFICATIONS = booleanPreferencesKey("read_notifications")
        val OFFLINE_MODE       = booleanPreferencesKey("offline_mode")

        // Session
        val CURRENT_SESSION_ID = stringPreferencesKey("current_session_id")
        val ONBOARDING_DONE    = booleanPreferencesKey("onboarding_done")
    }

    // ── Flows ─────────────────────────────────────────────────────────────────

    val appSettingsFlow: Flow<AppSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences())
            else throw exception
        }
        .map { prefs ->
            AppSettings(
                aiConfig = AiConfig(
                    baseUrl      = prefs[Keys.AI_BASE_URL]      ?: "https://api.groq.com/openai/v1",
                    apiKey       = prefs[Keys.AI_API_KEY]       ?: "",
                    modelName    = prefs[Keys.AI_MODEL_NAME]    ?: "llama3-8b-8192",
                    systemPrompt = prefs[Keys.AI_SYSTEM_PROMPT] ?: DEFAULT_SYSTEM_PROMPT,
                    maxTokens    = prefs[Keys.AI_MAX_TOKENS]    ?: 512,
                    temperature  = prefs[Keys.AI_TEMPERATURE]   ?: 0.7f,
                    streamResponse = prefs[Keys.AI_STREAM]      ?: true
                ),
                wakeWordEnabled    = prefs[Keys.WAKE_WORD_ENABLED]  ?: false,
                wakeWord           = prefs[Keys.WAKE_WORD]          ?: "hey aria",
                voiceSpeed         = prefs[Keys.VOICE_SPEED]        ?: 1.0f,
                voicePitch         = prefs[Keys.VOICE_PITCH]        ?: 1.0f,
                theme              = AppTheme.valueOf(prefs[Keys.THEME] ?: AppTheme.SYSTEM.name),
                language           = prefs[Keys.LANGUAGE]           ?: "en-us",
                autoListen         = prefs[Keys.AUTO_LISTEN]        ?: false,
                hapticFeedback     = prefs[Keys.HAPTIC_FEEDBACK]    ?: true,
                readNotifications  = prefs[Keys.READ_NOTIFICATIONS] ?: false,
                offlineMode        = prefs[Keys.OFFLINE_MODE]       ?: false
            )
        }

    val onboardingDoneFlow: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[Keys.ONBOARDING_DONE] ?: false }

    val currentSessionIdFlow: Flow<String?> = context.dataStore.data
        .map { prefs -> prefs[Keys.CURRENT_SESSION_ID] }

    // ── Suspend Writers ───────────────────────────────────────────────────────

    suspend fun saveAiConfig(config: AiConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AI_BASE_URL]      = config.baseUrl
            prefs[Keys.AI_API_KEY]       = config.apiKey
            prefs[Keys.AI_MODEL_NAME]    = config.modelName
            prefs[Keys.AI_SYSTEM_PROMPT] = config.systemPrompt
            prefs[Keys.AI_MAX_TOKENS]    = config.maxTokens
            prefs[Keys.AI_TEMPERATURE]   = config.temperature
            prefs[Keys.AI_STREAM]        = config.streamResponse
        }
    }

    suspend fun saveAppSettings(settings: AppSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WAKE_WORD_ENABLED]  = settings.wakeWordEnabled
            prefs[Keys.WAKE_WORD]          = settings.wakeWord
            prefs[Keys.VOICE_SPEED]        = settings.voiceSpeed
            prefs[Keys.VOICE_PITCH]        = settings.voicePitch
            prefs[Keys.THEME]              = settings.theme.name
            prefs[Keys.LANGUAGE]           = settings.language
            prefs[Keys.AUTO_LISTEN]        = settings.autoListen
            prefs[Keys.HAPTIC_FEEDBACK]    = settings.hapticFeedback
            prefs[Keys.READ_NOTIFICATIONS] = settings.readNotifications
            prefs[Keys.OFFLINE_MODE]       = settings.offlineMode
        }
    }

    suspend fun setOnboardingDone(done: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_DONE] = done
        }
    }

    suspend fun setCurrentSessionId(sessionId: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CURRENT_SESSION_ID] = sessionId
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}