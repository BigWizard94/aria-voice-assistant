package com.bigwizard.aria

import com.bigwizard.aria.ai.AiEngine
import com.bigwizard.aria.data.model.ChatRequest
import com.bigwizard.aria.data.model.ChatResponse
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiEngineTest {

    private val gson = Gson()

    @Test fun `parses an OpenAI style response`() {
        val json = """{"id":"x","object":"chat.completion","choices":[{"index":0,
            "message":{"role":"assistant","content":"Hello!"},"finish_reason":"stop"}],
            "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}"""
        val r = gson.fromJson(json, ChatResponse::class.java)
        assertEquals("Hello!", r.choices!!.first().message!!.content)
    }

    @Test fun `tolerates missing fields and null content`() {
        val r = gson.fromJson("""{"choices":[{"message":{"role":"assistant","content":null}}]}""", ChatResponse::class.java)
        assertNull(r.choices!!.first().message!!.content)
        assertNull(r.id)
    }

    @Test fun `null reasoning fields are not sent`() {
        val body = gson.toJson(ChatRequest(model = "m", messages = emptyList()))
        assertFalse(body.contains("reasoning_effort"))
        assertFalse(body.contains("include_reasoning"))
    }

    @Test fun `strips inline think blocks`() {
        assertEquals("Answer", AiEngine.stripThinking("<think>hmm\nlet me see</think>Answer").trim())
    }

    @Test fun `presets have current default models`() {
        val groq = AiEngine.PRESET_ENDPOINTS.first { it.baseUrl.contains("groq") }
        assertEquals("openai/gpt-oss-20b", groq.defaultModel)
        assertTrue(AiEngine.PRESET_ENDPOINTS.none { it.defaultModel == "llama3-8b-8192" })
        assertTrue(AiEngine.requiresApiKey(groq.baseUrl))
        assertFalse(AiEngine.requiresApiKey("http://192.168.1.20:11434/v1"))
    }
}
