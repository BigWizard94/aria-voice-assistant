package com.bigwizard.aria.ai

import com.bigwizard.aria.data.model.VoiceCommand

/**
 * CommandParser — Detects system-level voice commands before sending to AI.
 *
 * Fast, regex-based pattern matching for common device actions.
 * If no command is matched, falls through to the AI engine.
 */
object CommandParser {

    // ── Pattern Groups ────────────────────────────────────────────────────────

    private val CALL_PATTERNS = listOf(
        Regex("""(?:call|phone|dial|ring)\s+(.+)""", RegexOption.IGNORE_CASE),
        Regex("""(?:make a call to|place a call to)\s+(.+)""", RegexOption.IGNORE_CASE)
    )

    private val SMS_PATTERNS = listOf(
        Regex("""(?:text|send (?:a )?(?:text|message|sms) to)\s+(.+?)\s+(?:saying|that|:)\s+(.+)""", RegexOption.IGNORE_CASE),
        Regex("""(?:message)\s+(.+?)\s+(?:saying|that|:)\s+(.+)""", RegexOption.IGNORE_CASE)
    )

    private val ALARM_PATTERNS = listOf(
        Regex("""(?:set (?:an )?alarm|wake me up|alarm)\s+(?:at|for)?\s*(.+)""", RegexOption.IGNORE_CASE),
        Regex("""(?:wake me at)\s+(.+)""", RegexOption.IGNORE_CASE)
    )

    private val TIMER_PATTERNS = listOf(
        Regex("""(?:set (?:a )?timer|start (?:a )?timer)\s+(?:for)?\s*(.+)""", RegexOption.IGNORE_CASE),
        Regex("""(?:timer)\s+(?:for)?\s*(.+)""", RegexOption.IGNORE_CASE),
        Regex("""(?:remind me in)\s+(.+)""", RegexOption.IGNORE_CASE)
    )

    private val OPEN_APP_PATTERNS = listOf(
        Regex("""(?:open|launch|start|run)\s+(.+)""", RegexOption.IGNORE_CASE),
        Regex("""(?:go to|switch to)\s+(.+)""", RegexOption.IGNORE_CASE)
    )

    private val SEARCH_PATTERNS = listOf(
        Regex("""(?:search (?:for|the web for)?|google|look up|find)\s+(.+)""", RegexOption.IGNORE_CASE),
        Regex("""(?:what is|what are|who is|who are|where is|how do|how does)\s+(.+)""", RegexOption.IGNORE_CASE)
    )

    private val MUSIC_PATTERNS = listOf(
        Regex("""(?:play|listen to|put on)\s+(.+)""", RegexOption.IGNORE_CASE),
        Regex("""(?:play some)\s+(.+)""", RegexOption.IGNORE_CASE)
    )

    private val STOP_PATTERNS = listOf(
        Regex("""^(?:stop|cancel|never mind|quit|exit|goodbye|bye|shut up)$""", RegexOption.IGNORE_CASE),
        Regex("""^(?:stop listening|stop aria|that's all|that is all)$""", RegexOption.IGNORE_CASE)
    )

    // ── App name shortcuts ────────────────────────────────────────────────────

    private val APP_SHORTCUTS = mapOf(
        "youtube"   to "com.google.android.youtube",
        "maps"      to "com.google.android.apps.maps",
        "camera"    to "android.media.action.IMAGE_CAPTURE",
        "settings"  to "android.settings.SETTINGS",
        "calculator" to "com.android.calculator2",
        "calendar"  to "com.android.calendar",
        "contacts"  to "com.android.contacts",
        "messages"  to "com.android.mms",
        "phone"     to "com.android.phone",
        "browser"   to "android.intent.action.VIEW",
        "spotify"   to "com.spotify.music",
        "whatsapp"  to "com.whatsapp",
        "telegram"  to "org.telegram.messenger",
        "instagram" to "com.instagram.android",
        "twitter"   to "com.twitter.android",
        "facebook"  to "com.facebook.katana",
        "gmail"     to "com.google.android.gm",
        "clock"     to "com.android.deskclock",
        "gallery"   to "com.android.gallery3d",
        "files"     to "com.android.documentsui"
    )

    // ── Main parse function ───────────────────────────────────────────────────

    fun parse(input: String): VoiceCommand {
        val trimmed = input.trim()

        // Stop commands
        for (pattern in STOP_PATTERNS) {
            if (pattern.matches(trimmed)) return VoiceCommand.StopListening
        }

        // Call commands
        for (pattern in CALL_PATTERNS) {
            val match = pattern.find(trimmed)
            if (match != null) {
                val contact = match.groupValues[1].trim()
                return VoiceCommand.PhoneCall(contact)
            }
        }

        // SMS commands
        for (pattern in SMS_PATTERNS) {
            val match = pattern.find(trimmed)
            if (match != null) {
                val contact = match.groupValues[1].trim()
                val message = match.groupValues[2].trim()
                return VoiceCommand.SendSms(contact, message)
            }
        }

        // Alarm commands
        for (pattern in ALARM_PATTERNS) {
            val match = pattern.find(trimmed)
            if (match != null) {
                val time = match.groupValues[1].trim()
                return VoiceCommand.SetAlarm(time)
            }
        }

        // Timer commands
        for (pattern in TIMER_PATTERNS) {
            val match = pattern.find(trimmed)
            if (match != null) {
                val duration = match.groupValues[1].trim()
                return VoiceCommand.SetTimer(duration)
            }
        }

        // Music commands (before open, to catch "play music")
        for (pattern in MUSIC_PATTERNS) {
            val match = pattern.find(trimmed)
            if (match != null) {
                val query = match.groupValues[1].trim()
                // Don't treat "play [app name]" as music if it's a known app
                if (!APP_SHORTCUTS.containsKey(query.lowercase())) {
                    return VoiceCommand.PlayMusic(query)
                }
            }
        }

        // Open app commands
        for (pattern in OPEN_APP_PATTERNS) {
            val match = pattern.find(trimmed)
            if (match != null) {
                val appName = match.groupValues[1].trim()
                return VoiceCommand.OpenApp(appName)
            }
        }

        // Search commands
        for (pattern in SEARCH_PATTERNS) {
            val match = pattern.find(trimmed)
            if (match != null) {
                val query = match.groupValues[1].trim()
                return VoiceCommand.WebSearch(query)
            }
        }

        // Default: send to AI
        return VoiceCommand.AiQuery(trimmed)
    }

    fun resolveAppPackage(appName: String): String? {
        return APP_SHORTCUTS[appName.lowercase().trim()]
    }
}