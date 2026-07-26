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
    val baseUrl: String = "https://api.groq.com/openai/v1",
    val apiKey: String = "",
    val modelName: String = "llama3-8b-8192",
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

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val max_tokens: Int = 512,
    val temperature: Float = 0.7f,
    val stream: Boolean = false
)

data class ChatResponse(
    val id: String,
    val choices: List<Choice>,
    val usage: Usage?
)

data class Choice(
    val index: Int,
    val message: ChatMessage,
    val finish_reason: String?
)

data class Usage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int
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

const val DEFAULT_SYSTEM_PROMPT = "You are Aria, a helpful, private, and open-source voice assistant for Android. " +
    "You prioritize user privacy and work offline when possible. " +
    "Keep responses concise and natural for voice interaction. " +
    "Be friendly, knowledgeable, and respectful."
