package com.bigwizard.aria.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// ── Message ────────────────────────────────────────────────────────────────

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val role: String,          // "user" | "assistant" | "system"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false
)

// ── Conversation Session ──────────────────────────────────────────────────────

@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey
    val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messageCount: Int = 0
)

// ── AI Settings ───────────────────────────────────────────────────────────────

data class AiConfig(
    val baseUrl: String = DEFAULT_AI_BASE_URL,
    val apiKey: String = "",
    val modelName: String = DEFAULT_AI_MODEL,
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val maxTokens: Int = 512,
    val temperature: Float = 0.7f,
    val streamResponse: Boolean = true
)

// ── App Settings ──────────────────────────────────────────────────────────────

data class AppSettings(
    val aiConfig: AiConfig = AiConfig(),
    val wakeWordEnabled: Boolean = false,
    val wakeWord: String = "hey aria",
    val voiceSpeed: Float = 1.0f,
    val voicePitch: Float = 1.0f,
    val theme: AppTheme = AppTheme.SYSTEM,
    val language: String = "en-us",
    val autoListen: Boolean = false,
    val hapticFeedback: Boolean = true,
    val readNotifications: Boolean = false,
    val offlineMode: Boolean = false
)

enum class AppTheme { LIGHT, DARK, SYSTEM }

// ── UI State ──────────────────────────────────────────────────────────────────

sealed class AssistantState {
    object Idle : AssistantState()

    /** Getting ready to listen (loading / first-run download of the speech model). */
    data class Preparing(val message: String, val progress: Float? = null) : AssistantState()

    object Listening : AssistantState()
    object Processing : AssistantState()
    data class Speaking(val text: String) : AssistantState()
    data class Error(val message: String) : AssistantState()
}

// ── AI API Models ─────────────────────────────────────────────────────────────

data class ChatMessage(
    val role: String,
    val content: String
)

// Field names match the OpenAI wire format. Gson omits null fields, so the
// optional reasoning controls are only sent to models that understand them.
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val max_tokens: Int = 512,
    val temperature: Float = 0.7f,
    val stream: Boolean = false,
    val reasoning_effort: String? = null,
    val include_reasoning: Boolean? = null
)

// Response fields are nullable: Gson ignores Kotlin nullability, and servers
// (Ollama, LM Studio, proxies) omit fields freely.
data class ChatResponse(
    val id: String?,
    val choices: List<Choice>?,
    val usage: Usage?
)

data class Choice(
    val index: Int?,
    val message: ResponseMessage?,
    val finish_reason: String?
)

data class ResponseMessage(
    val role: String?,
    val content: String?
)

data class Usage(
    val prompt_tokens: Int?,
    val completion_tokens: Int?,
    val total_tokens: Int?
)

// ── Command Recognition ───────────────────────────────────────────────────────

sealed class VoiceCommand {
    data class PhoneCall(val contact: String) : VoiceCommand()
    data class SendSms(val contact: String, val message: String) : VoiceCommand()
    data class SetAlarm(val time: String) : VoiceCommand()
    data class SetTimer(val duration: String) : VoiceCommand()
    data class OpenApp(val appName: String) : VoiceCommand()
    data class WebSearch(val query: String) : VoiceCommand()
    data class PlayMusic(val query: String) : VoiceCommand()
    data class AiQuery(val text: String) : VoiceCommand()
    object StopListening : VoiceCommand()
    object Unknown : VoiceCommand()
}

// ── Constants ──────────────────────────────────────────────────────────────────

const val DEFAULT_AI_BASE_URL = "https://api.groq.com/openai/v1"

/**
 * Groq retired llama3-8b-8192 (Aug 2025) and llama-3.1-8b-instant (Aug 2026)
 * for free/developer tiers; openai/gpt-oss-20b is Groq's recommended replacement.
 * https://console.groq.com/docs/deprecations
 */
const val DEFAULT_AI_MODEL = "openai/gpt-oss-20b"

/** (baseUrl, old default model) → replacement, applied once to saved settings. */
val LEGACY_AI_MODELS: Map<Pair<String, String>, String> = mapOf(
    ("https://api.groq.com/openai/v1" to "llama3-8b-8192") to DEFAULT_AI_MODEL,
    ("https://api.groq.com/openai/v1" to "llama3-70b-8192") to DEFAULT_AI_MODEL,
    ("https://api.groq.com/openai/v1" to "llama-3.1-8b-instant") to DEFAULT_AI_MODEL,
    ("https://openrouter.ai/api/v1" to "mistralai/mistral-7b-instruct:free") to "openrouter/free",
    ("https://api.together.xyz/v1" to "meta-llama/Llama-3-8b-chat-hf") to "Qwen/Qwen3.5-9B"
)

const val DEFAULT_SYSTEM_PROMPT = "You are Aria, a helpful, private, and open-source voice assistant for Android. " +
    "You prioritize user privacy and work offline when possible. " +
    "Keep responses concise and natural for voice interaction. " +
    "Be friendly, knowledgeable, and respectful."
