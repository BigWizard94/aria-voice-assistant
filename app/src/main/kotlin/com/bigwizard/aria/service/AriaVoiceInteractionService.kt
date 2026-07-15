package com.bigwizard.aria.service

import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.util.Log

/**
 * AriaVoiceInteractionService — Registers Aria as the system default assistant.
 *
 * This is what allows Aria to be selected in:
 *   Settings → Apps → Default Apps → Digital Assistant
 *
 * Once set as default, long-pressing the home button launches Aria
 * instead of Google Assistant / Gemini.
 */
class AriaVoiceInteractionService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
        Log.i(TAG, "AriaVoiceInteractionService ready — Aria is the default assistant")
    }

    override fun onShutdown() {
        super.onShutdown()
        Log.i(TAG, "AriaVoiceInteractionService shutdown")
    }

    companion object {
        private const val TAG = "AriaVoiceInteraction"

        fun isActive(): Boolean = isActiveService(
            // Check if this service is the active voice interaction service
            null, null
        )
    }
}