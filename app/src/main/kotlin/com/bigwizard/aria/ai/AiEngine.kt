package com.bigwizard.aria.ai

import android.util.Log
import com.bigwizard.aria.data.model.*
import com.bigwizard.aria.data.remote.Http
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
 *
 * Stateless and thread-safe: share one instance.
 */
class AiEngine {

    private val gson = Gson()
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    // Shares the app-wide connection pool; LLM replies need a longer read timeout.
    private val client: OkHttpClient = Http.client.newBuilder()
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // ── Standard (non-streaming) completion ───────────────────────────────────

    suspend fun complete(
        config: AiConfig,
        messages: List<ChatMessage>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest(config, messages, stream = false)
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.e(TAG, "AI API error ${response.code}: ${body.take(300)}")
                    return@withContext Result.failure(AiHttpException(response.code, parseErrorMessage(body)))
                }
                val chatResponse = gson.fromJson(body, ChatResponse::class.java)
                    ?: return@withContext Result.failure(IOException("Empty response body"))
                val content = chatResponse.choices?.firstOrNull()?.message?.content
                    ?.let(::stripThinking)
                    ?.trim()
                if (content.isNullOrEmpty()) {
                    return@withContext Result.failure(IOException("The AI returned an empty reply"))
                }
                Log.d(TAG, "AI response: ${content.take(100)}")
                Result.success(content)
            }
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
        val request = buildRequest(config, messages, stream = true)
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw AiHttpException(response.code, parseErrorMessage(response.body?.string().orEmpty()))
            }
            val reader = response.body?.charStream()?.buffered()
                ?: throw IOException("Empty response body")
            reader.useLines { lines ->
                for (line in lines) {
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    val delta = try {
                        JsonParser.parseString(data).asJsonObject
                            .getAsJsonArray("choices")
                            ?.firstOrNull()?.asJsonObject
                            ?.getAsJsonObject("delta")
                            ?.get("content")
                            ?.takeIf { !it.isJsonNull }
                            ?.asString
                    } catch (e: Exception) {
                        null // skip malformed SSE lines / keep-alives
                    }
                    if (!delta.isNullOrEmpty()) emit(delta)
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

    private fun buildRequest(config: AiConfig, messages: List<ChatMessage>, stream: Boolean): Request {
        val baseUrl = config.baseUrl.trim().trimEnd('/')
        require(baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
            "Base URL must start with http:// or https://"
        }
        val isGptOss = config.modelName.contains("gpt-oss", ignoreCase = true)
        val body = gson.toJson(
            ChatRequest(
                model       = config.modelName.trim(),
                messages    = buildMessageList(config.systemPrompt, messages),
                max_tokens  = config.maxTokens,
                temperature = config.temperature,
                stream      = stream,
                // gpt-oss is a reasoning model: keep reasoning short so a voice reply
                // fits in max_tokens, and don't send the hidden reasoning back.
                reasoning_effort  = if (isGptOss) "low" else null,
                include_reasoning = if (isGptOss && isGroq(baseUrl)) false else null
            )
        ).toRequestBody(mediaType)

        return Request.Builder()
            .url("$baseUrl/chat/completions")
            .apply {
                // Local servers (Ollama / LM Studio) need no key; don't send an empty bearer.
                if (config.apiKey.isNotBlank()) header("Authorization", "Bearer ${config.apiKey.trim()}")
            }
            .header("Accept", if (stream) "text/event-stream" else "application/json")
            .header("User-Agent", "Aria-Voice-Assistant/1.0")
            .post(body)
            .build()
    }

    private fun buildMessageList(
        systemPrompt: String,
        messages: List<ChatMessage>
    ): List<ChatMessage> {
        val list = ArrayList<ChatMessage>(messages.size + 1)
        if (systemPrompt.isNotBlank()) {
            list.add(ChatMessage(role = "system", content = systemPrompt))
        }
        list.addAll(messages)
        return list
    }

    private fun parseErrorMessage(errorBody: String): String {
        return try {
            val json = JsonParser.parseString(errorBody).asJsonObject
            val error = json.get("error")
            when {
                error == null || error.isJsonNull -> errorBody.take(200)
                error.isJsonObject -> error.asJsonObject.get("message")?.asString ?: errorBody.take(200)
                else -> error.asString // Ollama: {"error": "model not found"}
            }
        } catch (e: Exception) {
            errorBody.take(200)
        }
    }

    companion object {
        private const val TAG = "AiEngine"

        private val THINK_BLOCK = Regex("""(?s)<think>.*?</think>""")

        /** Some local reasoning models inline their chain of thought; never read it aloud. */
        fun stripThinking(text: String): String = text.replace(THINK_BLOCK, "")

        private fun isGroq(baseUrl: String) = baseUrl.contains("api.groq.com")

        /** Hosted providers that always need a key (local / self-hosted ones may not). */
        private val KEYED_HOSTS = listOf(
            "api.groq.com", "openrouter.ai", "api.openai.com", "api.together.xyz"
        )

        fun requiresApiKey(baseUrl: String): Boolean = KEYED_HOSTS.any { baseUrl.contains(it) }

        const val CUSTOM_PRESET_NAME = "Custom Endpoint"

        // Popular free/cheap endpoints for easy setup. Default model ids verified Oct 2026.
        val PRESET_ENDPOINTS = listOf(
            EndpointPreset(
                name        = "Groq (Free & Ultra-Fast)",
                baseUrl     = DEFAULT_AI_BASE_URL,
                defaultModel = DEFAULT_AI_MODEL,
                description = "Free tier, fastest inference, great for voice",
                keyHint     = "Get a free key at console.groq.com"
            ),
            EndpointPreset(
                name        = "OpenRouter (100+ Models)",
                baseUrl     = "https://openrouter.ai/api/v1",
                defaultModel = "openrouter/free",
                description = "Access to many free and paid models",
                keyHint     = "Get a key at openrouter.ai/keys"
            ),
            EndpointPreset(
                name        = "Ollama (Fully Local)",
                baseUrl     = "http://localhost:11434/v1",
                defaultModel = "llama3.2",
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
                description = "Official OpenAI API",
                keyHint     = "Get a key at platform.openai.com"
            ),
            EndpointPreset(
                name        = "Together AI",
                baseUrl     = "https://api.together.xyz/v1",
                defaultModel = "Qwen/Qwen3.5-9B",
                description = "Fast inference, many open models",
                keyHint     = "Get a key at api.together.xyz"
            ),
            EndpointPreset(
                name        = CUSTOM_PRESET_NAME,
                baseUrl     = "",
                defaultModel = "",
                description = "Enter your own OpenAI-compatible URL"
            )
        )
    }
}

/** Non-2xx reply from the AI endpoint, with the server's own message. */
class AiHttpException(val code: Int, val serverMessage: String) :
    IOException("API error $code: $serverMessage")

data class EndpointPreset(
    val name: String,
    val baseUrl: String,
    val defaultModel: String,
    val description: String,
    val keyHint: String? = null
) {
    val isLocal: Boolean get() = baseUrl.startsWith("http://")
    val isCustom: Boolean get() = name == AiEngine.CUSTOM_PRESET_NAME
}
