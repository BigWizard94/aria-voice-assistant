package com.bigwizard.aria.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.util.Log
import com.bigwizard.aria.ui.screens.MainActivity

/**
 * Creates a [VoiceInteractionSession] each time the user invokes the assistant
 * (long-press home / assist gesture) while Aria is the default assistant.
 * Referenced from res/xml/interaction_service.xml (android:sessionService).
 */
class AriaSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = AriaSession(this)
}

/**
 * Lightweight session: instead of drawing its own overlay window it brings up
 * Aria's main screen and asks it to start listening, then hides itself. This
 * keeps a single UI/pipeline and avoids a second Compose window on low-RAM devices.
 */
class AriaSession(context: Context) : VoiceInteractionSession(context) {

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_ASSIST
            putExtra(MainActivity.EXTRA_START_LISTENING, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                startAssistantActivity(intent)
            } else {
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not launch Aria from assistant session", e)
        }
        hide()
    }

    companion object {
        private const val TAG = "AriaSession"
    }
}
