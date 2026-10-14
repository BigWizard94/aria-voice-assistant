package com.bigwizard.aria.ai

import com.bigwizard.aria.data.model.VoiceCommand
import com.bigwizard.aria.util.KnownApps
import com.bigwizard.aria.util.SpokenNumbers

/**
 * CommandParser — Detects system-level voice commands before sending to AI.
 *
 * Every pattern is anchored to the start of the (normalised) utterance, so a
 * command word buried in a sentence never triggers an action. Previously
 * "can you recall what I said" placed a phone call ("call what I said"),
 * "display the time" played music and "what is ..." always opened a browser.
 *
 * If no command matches, the text goes to the AI engine.
 */
object CommandParser {

    private val IGNORE = RegexOption.IGNORE_CASE

    /** Wake words / politeness Vosk transcribes in front of the actual command. */
    private val LEADING_FILLER = Regex(
        """^(?:(?:hey|hi|ok|okay)\s+)?(?:aria\s+)?(?:(?:please|can you|could you|would you|will you|i want you to|i'd like you to)\s+)*""",
        IGNORE
    )
    private val TRAILING_FILLER = Regex("""\s+(?:please|for me|now)$""", IGNORE)

    // ── Pattern Groups (all anchored) ─────────────────────────────────────────

    private val STOP_PATTERN = Regex(
        """^(?:stop|cancel|never ?mind|quit|exit|goodbye|bye|shut up|stop listening|stop aria|that'?s all|that is all)$""",
        IGNORE
    )

    private val CALL_PATTERN = Regex(
        """^(?:call|phone|dial|ring|make a call to|place a call to)\s+(.+)$""", IGNORE
    )

    private val SMS_PATTERN = Regex(
        """^(?:text|message|send (?:a )?(?:text|message|sms)(?: to)?)\s+(.+?)\s*(?:\bsaying\b|\bthat says\b|\bthat\b|:)\s*(.+)$""",
        IGNORE
    )

    private val ALARM_PATTERN = Regex(
        """^(?:set (?:an |a |the |my )?alarm|wake me(?: up)?|alarm)(?:\s+(?:at|for))?\s+(.+)$""", IGNORE
    )

    private val BARE_TIMER_PATTERN = Regex("""^(?:set|start) (?:a |the |an )?timer$""", IGNORE)

    private val TIMER_PATTERNS = listOf(
        Regex("""^(?:set|start) (?:a |the |an )?timer(?:\s+for)?\s+(.+)$""", IGNORE),
        Regex("""^timer(?:\s+for)?\s+(.+)$""", IGNORE),
        Regex("""^remind me in\s+(.+)$""", IGNORE),
        // "five minute timer", "set a ten minute timer"
        Regex("""^(?:(?:set|start)\s+)?(?:a |an )?(.+?)\s+timer$""", IGNORE)
    )

    private val MUSIC_PATTERN = Regex("""^(?:play|listen to|put on)\s+(?:some\s+)?(.+)$""", IGNORE)

    private val OPEN_APP_PATTERN = Regex(
        """^(?:open|launch|start|run|go to|switch to)\s+(?:the\s+)?(.+?)(?:\s+app)?$""", IGNORE
    )

    private val SEARCH_PATTERN = Regex(
        """^(?:search(?: the web| online| google)?(?: for)?|google|look up|find)\s+(.+)$""", IGNORE
    )

    // ── Main parse function ───────────────────────────────────────────────────

    fun normalize(input: String): String {
        var s = input.replace(',', ' ').trim().replace(Regex("\\s+"), " ").trimEnd('.', '!', '?')
        s = LEADING_FILLER.replaceFirst(s, "")
        s = TRAILING_FILLER.replaceFirst(s, "")
        return s.trim()
    }

    fun parse(input: String): VoiceCommand {
        val original = input.trim()
        val text = normalize(original)
        if (text.isEmpty()) return VoiceCommand.Unknown

        if (STOP_PATTERN.matches(text)) return VoiceCommand.StopListening

        CALL_PATTERN.matchEntire(text)?.let { return VoiceCommand.PhoneCall(it.groupValues[1].trim()) }

        SMS_PATTERN.matchEntire(text)?.let {
            return VoiceCommand.SendSms(it.groupValues[1].trim(), it.groupValues[2].trim())
        }

        // Timers before alarms ("set a timer for…" must not become an alarm).
        if (BARE_TIMER_PATTERN.matches(text)) return VoiceCommand.SetTimer("")
        for ((index, pattern) in TIMER_PATTERNS.withIndex()) {
            val match = pattern.matchEntire(text) ?: continue
            val duration = match.groupValues[1].trim()
            // The loose "<x> timer" form must actually contain a duration.
            if (index == TIMER_PATTERNS.lastIndex && SpokenNumbers.parseDurationSeconds(duration) == null) continue
            return VoiceCommand.SetTimer(duration)
        }

        ALARM_PATTERN.matchEntire(text)?.let { return VoiceCommand.SetAlarm(it.groupValues[1].trim()) }

        MUSIC_PATTERN.matchEntire(text)?.let {
            val query = it.groupValues[1].trim()
            // "play youtube" / "play spotify" means open the app
            return if (KnownApps.isKnown(query)) VoiceCommand.OpenApp(query) else VoiceCommand.PlayMusic(query)
        }

        OPEN_APP_PATTERN.matchEntire(text)?.let { return VoiceCommand.OpenApp(it.groupValues[1].trim()) }

        SEARCH_PATTERN.matchEntire(text)?.let { return VoiceCommand.WebSearch(it.groupValues[1].trim()) }

        // Default: questions and everything else go to the AI.
        return VoiceCommand.AiQuery(original)
    }

    fun resolveAppPackage(appName: String): String? = KnownApps.packageFor(appName)
}
