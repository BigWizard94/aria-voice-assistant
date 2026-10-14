package com.bigwizard.aria.service

import android.content.ComponentName
import android.content.Context
import android.service.voice.VoiceInteractionService
import android.util.Log

/**
 * AriaVoiceInteractionService — Registers Aria as the system default assistant.
 *
 * This is what allows Aria to be selected in:
 *   Settings → Apps → Default Apps → Digital Assistant
 *
 * The system binds this service while Aria is the default assistant. Each
 * invocation (long-press home, assist gesture) creates a session through
 * [AriaSessionService] → [AriaSession], which opens Aria and starts listening.
 * Speech recognition for the assistant role is provided by [AriaRecognitionService].
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

        /** True when the user has selected Aria as the default digital assistant. */
        fun isDefaultAssistant(context: Context): Boolean = try {
            isActiveService(
                context,
                ComponentName(context, AriaVoiceInteractionService::class.java)
            )
        } catch (e: Exception) {
            false
        }
    }
}
