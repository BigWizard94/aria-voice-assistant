package com.bigwizard.aria.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.bigwizard.aria.service.AriaListenerService

/**
 * BootReceiver — Restarts Aria's listener service after device reboot.
 * Ensures Aria is always available without manual app launch.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {

            Log.i(TAG, "Device booted — starting Aria listener service")

            val serviceIntent = Intent(context, AriaListenerService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}