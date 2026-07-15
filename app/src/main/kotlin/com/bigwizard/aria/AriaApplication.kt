package com.bigwizard.aria

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.bigwizard.aria.data.local.AriaDatabase
import com.bigwizard.aria.data.local.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Aria Application class.
 * Initializes global singletons: database, preferences, notification channels.
 */
class AriaApplication : Application() {

    // Application-scoped coroutine scope — survives activity recreation
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Lazy singletons
    val database by lazy { AriaDatabase.getInstance(this) }
    val preferencesManager by lazy { PreferencesManager(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Listening service channel
            val listeningChannel = NotificationChannel(
                CHANNEL_LISTENING,
                "Aria Listening",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shown while Aria is actively listening"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }

            // Response channel
            val responseChannel = NotificationChannel(
                CHANNEL_RESPONSE,
                "Aria Responses",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Aria AI responses and alerts"
            }

            // General channel
            val generalChannel = NotificationChannel(
                CHANNEL_GENERAL,
                "Aria General",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "General Aria notifications"
                setShowBadge(false)
            }

            notificationManager.createNotificationChannels(
                listOf(listeningChannel, responseChannel, generalChannel)
            )
        }
    }

    companion object {
        lateinit var instance: AriaApplication
            private set

        const val CHANNEL_LISTENING = "aria_listening"
        const val CHANNEL_RESPONSE  = "aria_response"
        const val CHANNEL_GENERAL   = "aria_general"

        const val NOTIFICATION_ID_LISTENING = 1001
        const val NOTIFICATION_ID_RESPONSE  = 1002
    }
}