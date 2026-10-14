package com.bigwizard.aria.util

import android.Manifest
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.util.Log
import com.bigwizard.aria.R

/**
 * CommandExecutor — Executes system-level voice commands.
 *
 * Handles: calls, SMS, alarms, timers, app launching, web search, music.
 * All actions use standard Android Intents — no root required.
 *
 * Every action returns the sentence Aria should speak, so the reply always
 * matches what actually happened (e.g. "I couldn't find Mom in your contacts"
 * instead of announcing a call that never starts).
 */
class CommandExecutor(context: Context) {

    private val context = context.applicationContext

    // ── Phone Call ────────────────────────────────────────────────────────────

    fun makeCall(contactName: String): String {
        val number = resolveNumber(contactName)
            ?: return context.getString(R.string.cmd_contact_not_found, contactName)

        val canCall = hasPermission(Manifest.permission.CALL_PHONE)
        val intent = Intent(if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL).apply {
            data = Uri.fromParts("tel", number, null)
        }
        return if (launch(intent)) {
            Log.i(TAG, "Calling: $contactName")
            context.getString(if (canCall) R.string.cmd_calling else R.string.cmd_dialing, contactName)
        } else {
            context.getString(R.string.cmd_no_phone_app)
        }
    }

    // ── SMS ───────────────────────────────────────────────────────────────────

    fun sendSms(contactName: String, message: String): String {
        val number = resolveNumber(contactName)
            ?: return context.getString(R.string.cmd_contact_not_found, contactName)
        // Opens the user's SMS app pre-filled; the user confirms the send.
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.fromParts("smsto", number, null)
            putExtra("sms_body", message)
        }
        return if (launch(intent)) {
            context.getString(R.string.cmd_messaging, contactName)
        } else {
            context.getString(R.string.cmd_no_sms_app)
        }
    }

    // ── Alarm ─────────────────────────────────────────────────────────────────

    fun setAlarm(timeString: String): String {
        val (hour, minute) = SpokenNumbers.parseClockTime(timeString)
            ?: return context.getString(R.string.cmd_alarm_time_unclear)
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, context.getString(R.string.alarm_label))
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
        return if (launch(intent)) {
            Log.i(TAG, "Alarm set for $hour:$minute")
            context.getString(R.string.cmd_alarm_set, formatTime(hour, minute))
        } else {
            context.getString(R.string.cmd_no_clock_app)
        }
    }

    // ── Timer ─────────────────────────────────────────────────────────────────

    fun setTimer(durationString: String): String {
        val seconds = SpokenNumbers.parseDurationSeconds(durationString)
            ?: return context.getString(R.string.cmd_timer_duration_unclear)
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, context.getString(R.string.timer_label))
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        }
        return if (launch(intent)) {
            Log.i(TAG, "Timer set for $seconds seconds")
            context.getString(R.string.cmd_timer_set, SpokenNumbers.describeDuration(seconds))
        } else {
            context.getString(R.string.cmd_no_clock_app)
        }
    }

    // ── Open App ──────────────────────────────────────────────────────────────

    fun openApp(appName: String): String {
        val launchIntent = findLaunchIntent(appName)
        if (launchIntent != null && launch(launchIntent)) {
            Log.i(TAG, "Opened app: $appName")
            return context.getString(R.string.cmd_opening, appName)
        }
        // Not installed: offer it in the app store (Android Go devices may not have one).
        val storeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=${Uri.encode(appName)}"))
        return if (launch(storeIntent)) {
            context.getString(R.string.cmd_app_not_found_store, appName)
        } else {
            context.getString(R.string.cmd_app_not_found, appName)
        }
    }

    // ── Web Search ────────────────────────────────────────────────────────────

    fun webSearch(query: String): String {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}"))
        return if (launch(intent)) {
            context.getString(R.string.cmd_searching, query)
        } else {
            context.getString(R.string.cmd_no_browser)
        }
    }

    // ── Music ─────────────────────────────────────────────────────────────────

    fun playMusic(query: String): String {
        // Standard "play from search" intent — handled by Spotify, YouTube Music, etc.
        val playFromSearch = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            putExtra(SearchManager.QUERY, query)
        }
        val spotify = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:${Uri.encode(query)}"))
        val ytMusic = Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/search?q=${Uri.encode(query)}"))

        return if (launch(playFromSearch) || launch(spotify) || launch(ytMusic)) {
            Log.i(TAG, "Playing music: $query")
            context.getString(R.string.cmd_playing, query)
        } else {
            context.getString(R.string.cmd_no_music_app)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Starts an activity from a non-activity context; false if nothing can handle it. */
    private fun launch(intent: Intent): Boolean = try {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        Log.w(TAG, "Not allowed to start ${intent.action}", e)
        false
    }

    private fun hasPermission(permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /** A spoken phone number ("five five five one two three four") or a contact name → number. */
    private fun resolveNumber(spoken: String): String? {
        val digitized = SpokenNumbers.digitize(spoken)
        if (Regex("^[0-9+ ]+$").matches(digitized)) {
            val digits = digitized.replace(" ", "")
            if (digits.length >= 3) return digits
        }
        return resolveContactNumber(spoken)
    }

    private fun resolveContactNumber(name: String): String? {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) return null
        return try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY
                ),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$name%"),
                "${ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY} DESC"
            )?.use { cursor ->
                val numberCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val nameCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                var firstMatch: String? = null
                while (cursor.moveToNext()) {
                    val number = cursor.getString(numberCol) ?: continue
                    // Prefer an exact name match ("Mom") over a partial one ("Tom Moment").
                    if (cursor.getString(nameCol).equals(name, ignoreCase = true)) return@use number
                    if (firstMatch == null) firstMatch = number
                }
                firstMatch
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not resolve contact: $name", e)
            null
        }
    }

    /**
     * Finds an installed app by its launcher label ("open calculator" works on
     * any OEM), then by the known-package table. Needs the MAIN/LAUNCHER
     * <queries> entry in the manifest on Android 11+.
     */
    private fun findLaunchIntent(appName: String): Intent? {
        val pm = context.packageManager
        val wanted = appName.lowercase().trim()
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = try {
            pm.queryIntentActivities(launcherIntent, 0)
        } catch (e: Exception) {
            emptyList()
        }
        val labelled = activities.map { it to it.loadLabel(pm).toString().lowercase() }
        val match = labelled.firstOrNull { it.second == wanted }
            ?: labelled.firstOrNull { it.second.startsWith(wanted) }
            ?: labelled.firstOrNull { it.second.contains(wanted) }
        match?.first?.activityInfo?.let { info ->
            return Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(info.packageName, info.name)
        }
        return KnownApps.packageFor(wanted)?.let { pm.getLaunchIntentForPackage(it) }
    }

    private fun formatTime(hour: Int, minute: Int): String {
        val h12 = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        val suffix = if (hour < 12) "AM" else "PM"
        return if (minute == 0) "$h12 $suffix" else "$h12:${minute.toString().padStart(2, '0')} $suffix"
    }

    companion object {
        private const val TAG = "CommandExecutor"
    }
}
