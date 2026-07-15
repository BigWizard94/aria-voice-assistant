package com.bigwizard.aria.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.util.Log
import java.util.regex.Pattern

/**
 * CommandExecutor — Executes system-level voice commands.
 *
 * Handles: calls, SMS, alarms, timers, app launching, web search, music.
 * All actions use standard Android Intents — no root required.
 */
class CommandExecutor(private val context: Context) {

    // ── Phone Call ────────────────────────────────────────────────────────────

    fun makeCall(contactName: String) {
        try {
            val number = resolveContactNumber(contactName) ?: contactName
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:${Uri.encode(number)}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.i(TAG, "Calling: $contactName → $number")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to make call to $contactName", e)
            // Fallback: open dialer
            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:${Uri.encode(contactName)}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(dialIntent)
        }
    }

    // ── SMS ───────────────────────────────────────────────────────────────────

    fun sendSms(contactName: String, message: String) {
        try {
            val number = resolveContactNumber(contactName) ?: contactName
            // Use Intent to open SMS app (safer than direct SmsManager for UX)
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:${Uri.encode(number)}")
                putExtra("sms_body", message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.i(TAG, "Opening SMS to: $contactName with message: $message")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS to $contactName", e)
        }
    }

    // ── Alarm ─────────────────────────────────────────────────────────────────

    fun setAlarm(timeString: String) {
        try {
            val (hour, minute) = parseTime(timeString)
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, "Aria Alarm")
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.i(TAG, "Alarm set for $hour:$minute")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set alarm for $timeString", e)
            // Fallback: open clock app
            openApp("clock")
        }
    }

    // ── Timer ─────────────────────────────────────────────────────────────────

    fun setTimer(durationString: String) {
        try {
            val seconds = parseDurationToSeconds(durationString)
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, "Aria Timer")
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.i(TAG, "Timer set for $seconds seconds ($durationString)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set timer for $durationString", e)
        }
    }

    // ── Open App ──────────────────────────────────────────────────────────────

    fun openApp(appName: String) {
        try {
            val packageName = resolveAppPackage(appName)
            if (packageName != null) {
                val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                    Log.i(TAG, "Opened app: $appName ($packageName)")
                    return
                }
            }

            // Fallback: search Play Store
            val storeIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("market://search?q=${Uri.encode(appName)}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(storeIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app: $appName", e)
        }
    }

    // ── Web Search ────────────────────────────────────────────────────────────

    fun webSearch(query: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.i(TAG, "Web search: $query")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to search for: $query", e)
        }
    }

    // ── Music ─────────────────────────────────────────────────────────────────

    fun playMusic(query: String) {
        try {
            // Try Spotify first
            val spotifyIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("spotify:search:${Uri.encode(query)}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (context.packageManager.resolveActivity(spotifyIntent, 0) != null) {
                context.startActivity(spotifyIntent)
                return
            }

            // Try YouTube Music
            val ytMusicIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://music.youtube.com/search?q=${Uri.encode(query)}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(ytMusicIntent)
            Log.i(TAG, "Playing music: $query")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play music: $query", e)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun resolveContactNumber(name: String): String? {
        return try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                ),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$name%"),
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    it.getString(it.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    ))
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not resolve contact: $name", e)
            null
        }
    }

    private fun parseTime(timeString: String): Pair<Int, Int> {
        // Handle "7:30", "7:30 AM", "7 30", "seven thirty", etc.
        val pattern = Pattern.compile("""(\d{1,2})[:.]?(\d{2})?\s*(am|pm)?""", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(timeString)

        return if (matcher.find()) {
            var hour = matcher.group(1)?.toIntOrNull() ?: 7
            val minute = matcher.group(2)?.toIntOrNull() ?: 0
            val amPm = matcher.group(3)?.lowercase()

            if (amPm == "pm" && hour < 12) hour += 12
            if (amPm == "am" && hour == 12) hour = 0

            Pair(hour, minute)
        } else {
            Pair(7, 0) // Default 7:00 AM
        }
    }

    private fun parseDurationToSeconds(durationString: String): Int {
        var totalSeconds = 0
        val lower = durationString.lowercase()

        // Hours
        val hoursMatch = Regex("""(\d+)\s*(?:hour|hr)s?""").find(lower)
        hoursMatch?.groupValues?.get(1)?.toIntOrNull()?.let { totalSeconds += it * 3600 }

        // Minutes
        val minutesMatch = Regex("""(\d+)\s*(?:minute|min)s?""").find(lower)
        minutesMatch?.groupValues?.get(1)?.toIntOrNull()?.let { totalSeconds += it * 60 }

        // Seconds
        val secondsMatch = Regex("""(\d+)\s*(?:second|sec)s?""").find(lower)
        secondsMatch?.groupValues?.get(1)?.toIntOrNull()?.let { totalSeconds += it }

        // Plain number (assume minutes)
        if (totalSeconds == 0) {
            val plainNumber = Regex("""^(\d+)$""").find(lower.trim())
            plainNumber?.groupValues?.get(1)?.toIntOrNull()?.let { totalSeconds = it * 60 }
        }

        return if (totalSeconds > 0) totalSeconds else 60 // Default 1 minute
    }

    private fun resolveAppPackage(appName: String): String? {
        val knownApps = mapOf(
            "youtube"    to "com.google.android.youtube",
            "maps"       to "com.google.android.apps.maps",
            "calculator" to "com.android.calculator2",
            "calendar"   to "com.android.calendar",
            "contacts"   to "com.android.contacts",
            "clock"      to "com.android.deskclock",
            "camera"     to "com.android.camera2",
            "spotify"    to "com.spotify.music",
            "whatsapp"   to "com.whatsapp",
            "telegram"   to "org.telegram.messenger",
            "instagram"  to "com.instagram.android",
            "twitter"    to "com.twitter.android",
            "facebook"   to "com.facebook.katana",
            "gmail"      to "com.google.android.gm",
            "chrome"     to "com.android.chrome",
            "firefox"    to "org.mozilla.firefox",
            "netflix"    to "com.netflix.mediaclient",
            "tiktok"     to "com.zhiliaoapp.musically",
            "snapchat"   to "com.snapchat.android",
            "reddit"     to "com.reddit.frontpage",
            "discord"    to "com.discord",
            "files"      to "com.android.documentsui",
            "settings"   to "com.android.settings",
            "photos"     to "com.google.android.apps.photos",
            "drive"      to "com.google.android.apps.docs",
            "docs"       to "com.google.android.apps.docs.editors.docs",
            "sheets"     to "com.google.android.apps.docs.editors.sheets"
        )
        return knownApps[appName.lowercase().trim()]
    }

    companion object {
        private const val TAG = "CommandExecutor"
    }
}