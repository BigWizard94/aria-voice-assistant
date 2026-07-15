package com.bigwizard.aria.ai

import android.util.Log
import com.bigwizard.aria.data.model.*
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * AiEngine — Universal OpenAI-compatible REST client.
 *
 * Works with ANY OpenAI-compatible endpoint:
 *   • Groq (free tier, ultra-fast)          → api.groq.com/openai/v1
 *   • OpenRouter (100+ models)              → openrouter.ai/api/v1
 *   • Ollama (fully local)                  → localhost:11434/v1
 *   • LM Studio (local)                     → localhost:1234/v1
 *   • OpenAI                                → api.openai.com/v1
 *   • Together AI                           → api.together.xyz/v1
 *   • Any other compatible endpoint         → your custom URL
 */
class AiEngine {

    private val gson = Gson()
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // ── Standard (non-streaming) completion ───────────────────────────────────

    suspend fun complete(
        config: AiConfig,
        messages: List<ChatMessage>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val allMessages = buildMessageList(config.systemPrompt, messages)
            val requestBody = gson.toJson(
                ChatRequest(
                    model       = config.modelName,
                    messages    = allMessages,
                    max_tokens  = config.maxTokens,
                    temperature = config.temperature,
                    stream      = false
                )
            ).toRequestBody(mediaType)

            val request = Request.Builder()
                .url("${config.baseUrl.trimEnd('/')}/chat/completions")
                .header("Authorization", "Bearer ${config.apiKey}")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "Aria-Voice-Assistant/1.0")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "AI API error ${response.code}: $errorBody")
                return@withContext Result.failure(
                    IOException("API error ${response.code}: ${parseErrorMessage(errorBody)}")
                )
            }

            val responseBody = response.body?.string()
                ?: return@withContext Result.failure(IOException("Empty response body"))

            val chatResponse = gson.fromJson(responseBody, ChatResponse::class.java)
            val content = chatResponse.choices.firstOrNull()?.message?.content?.trim()
                ?: return@withContext Result.failure(IOException("No content in response"))

            Log.d(TAG, "AI response: ${content.take(100)}...")
            Result.success(content)

        } catch (e: Exception) {
            Log.e(TAG, "AI completion failed", e)
            Result.failure(e)
        }
    }

    // ── Streaming completion ───────────────────────────────────────────────────

    fun completeStreaming(
        config: AiConfig,
        messages: List<ChatMessage>
    ): Flow<String> = flow {
        val allMessages = buildMessageList(config.systemPrompt, messages)
        val requestBody = gson.toJson(
            ChatRequest(
                model       = config.modelName,
                messages    = allMessages,
                max_tokens  = config.maxTokens,
                temperature = config.temperature,
                stream      = true
            )
        ).toRequestBody(mediaType)

        val request = Request.Builder()
            .url("${config.baseUrl.trimEnd('/')}/chat/completions")
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .header("User-Agent", "Aria-Voice-Assistant/1.0")
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw IOException("API error ${response.code}: ${parseErrorMessage(errorBody)}")
        }

        val reader: BufferedReader = response.body?.charStream()?.buffered()
            ?: throw IOException("Empty response body")

        reader.use { br ->
            br.lineSequence().forEach { line ->
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") return@forEach
                    try {
                        val json = JsonParser.parseString(data).asJsonObject
                        val delta = json
                            .getAsJsonArray("choices")
                            ?.get(0)?.asJsonObject
                            ?.getAsJsonObject("delta")
                            ?.get("content")?.asString
                        if (!delta.isNullOrEmpty()) {
                            emit(delta)
                        }
                    } catch (e: Exception) {
                        // Skip malformed SSE lines
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    // ── Connection test ───────────────────────────────────────────────────────

    suspend fun testConnection(config: AiConfig): Result<String> {
        return complete(
            config = config,
            messages = listOf(
                ChatMessage(role = "user", content = "Say 'Aria is online' and nothing else.")
            )
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildMessageList(
        systemPrompt: String,
        messages: List<ChatMessage>
    ): List<ChatMessage> {
        val list = mutableListOf<ChatMessage>()
        if (systemPrompt.isNotBlank()) {
            list.add(ChatMessage(role = "system", content = systemPrompt))
        }
        list.addAll(messages)
        return list
    }

    private fun parseErrorMessage(errorBody: String): String {
        return try {
            val json = JsonParser.parseString(errorBody).asJsonObject
            json.getAsJsonObject("error")?.get("message")?.asString ?: errorBody
        } catch (e: Exception) {
            errorBody.take(200)
        }
    }

    companion object {
        private const val TAG = "AiEngine"

        // Popular free/cheap endpoints for easy setup
        val PRESET_ENDPOINTS = listOf(
            EndpointPreset(
                name        = "Groq (Free & Ultra-Fast)",
                baseUrl     = "https://api.groq.com/openai/v1",
                defaultModel = "llama3-8b-8192",
                description = "Free tier, fastest inference, great for voice"
            ),
            EndpointPreset(
                name        = "OpenRouter (100+ Models)",
                baseUrl     = "https://openrouter.ai/api/v1",
                defaultModel = "mistralai/mistral-7b-instruct:free",
                description = "Access to many free and paid models"
            ),
            EndpointPreset(
                name        = "Ollama (Fully Local)",
                baseUrl     = "http://localhost:11434/v1",
                defaultModel = "llama3",
                description = "100% offline, runs on your device/PC"
            ),
            EndpointPreset(
                name        = "LM Studio (Local)",
                baseUrl     = "http://localhost:1234/v1",
                defaultModel = "local-model",
                description = "Local LLM via LM Studio"
            ),
            EndpointPreset(
                name        = "OpenAI",
                baseUrl     = "https://api.openai.com/v1",
                defaultModel = "gpt-4o-mini",
                description = "Official OpenAI API"
            ),
            EndpointPreset(
                name        = "Together AI",
                baseUrl     = "https://api.together.xyz/v1",
                defaultModel = "meta-llama/Llama-3-8b-chat-hf",
                description = "Fast inference, many open models"
            ),
            EndpointPreset(
                name        = "Custom Endpoint",
                baseUrl     = "",
                defaultModel = "",
                description = "Enter your own OpenAI-compatible URL"
            )
        )
    }
}

data class EndpointPreset(
    val name: String,
    val baseUrl: String,
    val defaultModel: String,
    val description: String
)