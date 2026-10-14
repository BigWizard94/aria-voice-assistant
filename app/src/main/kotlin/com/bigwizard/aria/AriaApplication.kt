package com.bigwizard.aria

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import com.bigwizard.aria.ai.AiEngine
import com.bigwizard.aria.data.local.AriaDatabase
import com.bigwizard.aria.data.local.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Aria Application class.
 * Initializes global singletons: database, preferences, AI client, notification channels.
 */
class AriaApplication : Application() {

    // Application-scoped coroutine scope — survives activity recreation
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Lazy singletons
    val database by lazy { AriaDatabase.getInstance(this) }
    val preferencesManager by lazy { PreferencesManager(this) }
    val aiEngine by lazy { AiEngine() }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        applicationScope.launch {
            try {
                preferencesManager.migrate()
            } catch (e: Exception) {
                Log.e("AriaApplication", "Preference migration failed", e)
            }
        }
    }

    private fun createNotificationChannels() {
        val notificationManager = getSystemService(NotificationManager::class.java)

        // Listening service channel
        val listeningChannel = NotificationChannel(
            CHANNEL_LISTENING,
            getString(R.string.notification_listening_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_listening_channel_desc)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }

        // Response channel
        val responseChannel = NotificationChannel(
            CHANNEL_RESPONSE,
            getString(R.string.notification_response_channel),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(R.string.notification_response_channel_desc)
        }

        // General channel
        val generalChannel = NotificationChannel(
            CHANNEL_GENERAL,
            getString(R.string.notification_general_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_general_channel_desc)
            setShowBadge(false)
        }

        notificationManager.createNotificationChannels(
            listOf(listeningChannel, responseChannel, generalChannel)
        )
    }

    companion object {
        const val CHANNEL_LISTENING = "aria_listening"
        const val CHANNEL_RESPONSE  = "aria_response"
        const val CHANNEL_GENERAL   = "aria_general"

        const val NOTIFICATION_ID_LISTENING = 1001
        const val NOTIFICATION_ID_RESPONSE  = 1002
    }
}