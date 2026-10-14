package com.bigwizard.aria.data.local

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.bigwizard.aria.data.model.AiConfig
import com.bigwizard.aria.data.model.AppSettings
import com.bigwizard.aria.data.model.AppTheme
import com.bigwizard.aria.data.model.DEFAULT_AI_BASE_URL
import com.bigwizard.aria.data.model.DEFAULT_AI_MODEL
import com.bigwizard.aria.data.model.DEFAULT_SYSTEM_PROMPT
import com.bigwizard.aria.data.model.LEGACY_AI_MODELS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.UUID

// DataStore extension on Context (file: files/datastore/aria_prefs.preferences_pb)
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "aria_prefs")

/**
 * Manages all persistent app preferences using Jetpack DataStore.
 * Fully reactive — exposes Flows for UI observation.
 *
 * The AI API key is stored encrypted with a Keystore-backed key ([SecretCipher]);
 * keys saved in plaintext by older builds are migrated on first launch.
 */
class PreferencesManager(context: Context) {

    private val dataStore = context.applicationContext.dataStore

    // ── Keys ──────────────────────────────────────────────────────────────────

    private object Keys {
        // AI Config
        val AI_BASE_URL      = stringPreferencesKey("ai_base_url")
        val AI_API_KEY       = stringPreferencesKey("ai_api_key")      // legacy plaintext
        val AI_API_KEY_ENC   = stringPreferencesKey("ai_api_key_enc")  // encrypted
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

    /** IO errors reading the file fall back to defaults instead of crashing. */
    private val safeData: Flow<Preferences> = dataStore.data.catch { exception ->
        if (exception is IOException) {
            Log.e(TAG, "Error reading preferences", exception)
            emit(emptyPreferences())
        } else {
            throw exception
        }
    }

    // Decrypting on every emission is cheap but pointless — cache the last result.
    @Volatile private var lastCipher: String? = null
    @Volatile private var lastPlain: String = ""

    private fun readApiKey(prefs: Preferences): String {
        val enc = prefs[Keys.AI_API_KEY_ENC]
        if (enc != null) {
            if (enc == lastCipher) return lastPlain
            val plain = SecretCipher.decrypt(enc) ?: ""
            lastCipher = enc
            lastPlain = plain
            return plain
        }
        return prefs[Keys.AI_API_KEY] ?: ""
    }

    // ── Flows ─────────────────────────────────────────────────────────────────

    val appSettingsFlow: Flow<AppSettings> = safeData
        .map { prefs ->
            AppSettings(
                aiConfig = AiConfig(
                    baseUrl      = prefs[Keys.AI_BASE_URL]      ?: DEFAULT_AI_BASE_URL,
                    apiKey       = readApiKey(prefs),
                    modelName    = prefs[Keys.AI_MODEL_NAME]    ?: DEFAULT_AI_MODEL,
                    systemPrompt = prefs[Keys.AI_SYSTEM_PROMPT] ?: DEFAULT_SYSTEM_PROMPT,
                    maxTokens    = prefs[Keys.AI_MAX_TOKENS]    ?: 512,
                    temperature  = prefs[Keys.AI_TEMPERATURE]   ?: 0.7f,
                    streamResponse = prefs[Keys.AI_STREAM]      ?: true
                ),
                wakeWordEnabled    = prefs[Keys.WAKE_WORD_ENABLED]  ?: false,
                wakeWord           = prefs[Keys.WAKE_WORD]          ?: "hey aria",
                voiceSpeed         = prefs[Keys.VOICE_SPEED]        ?: 1.0f,
                voicePitch         = prefs[Keys.VOICE_PITCH]        ?: 1.0f,
                theme              = prefs[Keys.THEME]
                    ?.let { name -> AppTheme.entries.firstOrNull { it.name == name } }
                    ?: AppTheme.SYSTEM,
                language           = prefs[Keys.LANGUAGE]           ?: "en-us",
                autoListen         = prefs[Keys.AUTO_LISTEN]        ?: false,
                hapticFeedback     = prefs[Keys.HAPTIC_FEEDBACK]    ?: true,
                readNotifications  = prefs[Keys.READ_NOTIFICATIONS] ?: false,
                offlineMode        = prefs[Keys.OFFLINE_MODE]       ?: false
            )
        }
        .distinctUntilChanged()

    val onboardingDoneFlow: Flow<Boolean> = safeData
        .map { prefs -> prefs[Keys.ONBOARDING_DONE] ?: false }
        .distinctUntilChanged()

    val currentSessionIdFlow: Flow<String?> = safeData
        .map { prefs -> prefs[Keys.CURRENT_SESSION_ID] }
        .distinctUntilChanged()

    // ── Suspend Writers ───────────────────────────────────────────────────────

    suspend fun saveAiConfig(config: AiConfig) {
        val encrypted = if (config.apiKey.isBlank()) null else SecretCipher.encrypt(config.apiKey)
        dataStore.edit { prefs ->
            prefs[Keys.AI_BASE_URL]      = config.baseUrl.trim()
            when {
                config.apiKey.isBlank() -> {
                    prefs.remove(Keys.AI_API_KEY_ENC)
                    prefs.remove(Keys.AI_API_KEY)
                }
                encrypted != null -> {
                    prefs[Keys.AI_API_KEY_ENC] = encrypted
                    prefs.remove(Keys.AI_API_KEY)
                }
                // Keystore unavailable on this device: keep working rather than lose the key.
                else -> prefs[Keys.AI_API_KEY] = config.apiKey.trim()
            }
            prefs[Keys.AI_MODEL_NAME]    = config.modelName.trim()
            prefs[Keys.AI_SYSTEM_PROMPT] = config.systemPrompt
            prefs[Keys.AI_MAX_TOKENS]    = config.maxTokens
            prefs[Keys.AI_TEMPERATURE]   = config.temperature
            prefs[Keys.AI_STREAM]        = config.streamResponse
        }
    }

    suspend fun saveAppSettings(settings: AppSettings) {
        dataStore.edit { prefs ->
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

    /** Saves the AI config, then marks onboarding finished (in that order). */
    suspend fun completeOnboarding(config: AiConfig) {
        saveAiConfig(config)
        setOnboardingDone(true)
    }

    suspend fun setOnboardingDone(done: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.ONBOARDING_DONE] = done }
    }

    suspend fun setCurrentSessionId(sessionId: String) {
        dataStore.edit { prefs -> prefs[Keys.CURRENT_SESSION_ID] = sessionId }
    }

    /** Returns the current conversation id, creating one atomically if none exists. */
    suspend fun getOrCreateSessionId(): String {
        var id: String? = null
        dataStore.edit { prefs ->
            id = prefs[Keys.CURRENT_SESSION_ID] ?: UUID.randomUUID().toString().also {
                prefs[Keys.CURRENT_SESSION_ID] = it
            }
        }
        return id!!
    }

    /** Starts a fresh conversation and returns its id. */
    suspend fun startNewSession(): String {
        val id = UUID.randomUUID().toString()
        setCurrentSessionId(id)
        return id
    }

    /**
     * One-time upgrades for data written by older builds:
     *  - encrypts a plaintext API key
     *  - replaces default model ids that the provider has since shut down
     */
    suspend fun migrate() {
        dataStore.edit { prefs ->
            val legacyKey = prefs[Keys.AI_API_KEY]
            if (!legacyKey.isNullOrBlank()) {
                SecretCipher.encrypt(legacyKey)?.let {
                    prefs[Keys.AI_API_KEY_ENC] = it
                    prefs.remove(Keys.AI_API_KEY)
                }
            }
            val model = prefs[Keys.AI_MODEL_NAME]
            val url = prefs[Keys.AI_BASE_URL]
            if (model != null && url != null) {
                LEGACY_AI_MODELS[url.trimEnd('/') to model]?.let { prefs[Keys.AI_MODEL_NAME] = it }
            }
        }
    }

    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }

    companion object {
        private const val TAG = "PreferencesManager"
    }
}
