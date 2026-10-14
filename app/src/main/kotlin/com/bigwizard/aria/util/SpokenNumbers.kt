package com.bigwizard.aria.util

/**
 * Parses numbers, clock times and durations the way an offline recogniser
 * (Vosk) transcribes them: lowercase words with no digits or punctuation,
 * e.g. "set an alarm for seven thirty a m" or "timer for twenty five minutes".
 *
 * Pure Kotlin (no Android types) so it is unit-tested on the JVM.
 */
object SpokenNumbers {

    private val UNITS = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14,
        "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18,
        "nineteen" to 19
    )
    private val TENS = mapOf(
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
        "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90
    )

    /**
     * Replaces spelled-out numbers (0–999) with digits:
     * "twenty five minutes" → "25 minutes", "seven oh five" → "7 05".
     */
    fun digitize(text: String): String {
        val tokens = text.lowercase().replace('-', ' ').split(Regex("\\s+")).filter { it.isNotEmpty() }
        val out = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            // "oh five" → "05" (as in "seven oh five")
            if ((t == "oh" || t == "o") && i + 1 < tokens.size && UNITS[tokens[i + 1]]?.let { it in 1..9 } == true) {
                out += "0" + UNITS.getValue(tokens[i + 1])
                i += 2
                continue
            }
            var value: Int? = null
            var j = i
            if (UNITS.containsKey(tokens[j]) || TENS.containsKey(tokens[j])) {
                var v: Int
                if (TENS.containsKey(tokens[j])) {
                    v = TENS.getValue(tokens[j]); j++
                    if (j < tokens.size && UNITS[tokens[j]]?.let { it in 1..9 } == true) { v += UNITS.getValue(tokens[j]); j++ }
                } else {
                    v = UNITS.getValue(tokens[j]); j++
                }
                if (j < tokens.size && tokens[j] == "hundred") {
                    v *= 100; j++
                    if (j < tokens.size && tokens[j] == "and") j++
                    if (j < tokens.size && TENS.containsKey(tokens[j])) {
                        v += TENS.getValue(tokens[j]); j++
                        if (j < tokens.size && UNITS[tokens[j]]?.let { it in 1..9 } == true) { v += UNITS.getValue(tokens[j]); j++ }
                    } else if (j < tokens.size && UNITS.containsKey(tokens[j])) {
                        v += UNITS.getValue(tokens[j]); j++
                    }
                }
                value = v
            }
            if (value != null) {
                out += value.toString()
                i = j
            } else {
                out += t
                i++
            }
        }
        return out.joinToString(" ")
    }

    /** Parses a spoken or typed clock time into (hour 0–23, minute). Null if no time is found. */
    fun parseClockTime(raw: String): Pair<Int, Int>? {
        var s = " " + digitize(
            raw.lowercase()
                .replace("a.m.", " am ").replace("p.m.", " pm ")
                .replace("o'clock", " ").replace("oclock", " ")
        ) + " "
        s = s.replace(Regex("\\ba m\\b"), "am").replace(Regex("\\bp m\\b"), "pm")

        val pm = Regex("\\b(pm|in the (afternoon|evening)|at night|tonight)\\b").containsMatchIn(s)
        val am = Regex("\\b(am|in the morning)\\b").containsMatchIn(s)

        if (Regex("\\bnoon\\b").containsMatchIn(s)) return 12 to 0
        if (Regex("\\bmidnight\\b").containsMatchIn(s)) return 0 to 0

        fun applyMeridiem(h: Int): Int = when {
            pm && h in 1..11 -> h + 12
            am && h == 12 -> 0
            else -> h
        }

        Regex("\\bhalf past (\\d{1,2})\\b").find(s)?.let {
            val h = it.groupValues[1].toInt(); if (h in 0..23) return applyMeridiem(h) to 30
        }
        Regex("\\bquarter past (\\d{1,2})\\b").find(s)?.let {
            val h = it.groupValues[1].toInt(); if (h in 0..23) return applyMeridiem(h) to 15
        }
        Regex("\\bquarter to (\\d{1,2})\\b").find(s)?.let {
            val h = it.groupValues[1].toInt(); if (h in 1..24) return applyMeridiem(h - 1) to 45
        }

        // "7:30", "7.30", "7 30", "7 05", or "730"
        Regex("\\b(\\d{1,2})(?:[:.\\s](\\d{2}))?\\b").find(s)?.let { m ->
            val hour = m.groupValues[1].toInt()
            val minute = m.groupValues[2].toIntOrNull() ?: 0
            if (hour in 0..23 && minute in 0..59) return applyMeridiem(hour) to minute
        }
        Regex("\\b(\\d{3,4})\\b").find(s)?.let {
            val n = it.groupValues[1].toInt()
            val hour = n / 100; val minute = n % 100
            if (hour in 0..23 && minute in 0..59) return applyMeridiem(hour) to minute
        }
        return null
    }

    /** Parses a spoken duration into seconds ("an hour and a half", "25 minutes"). Null if none found. */
    fun parseDurationSeconds(raw: String): Int? {
        var s = " " + digitize(raw.lowercase()) + " "
        var total = 0
        var found = false

        // "half an hour" / "half a minute" first, before "an hour" becomes "1 hour".
        Regex("\\bhalf (?:an|a) hour\\b").let { if (it.containsMatchIn(s)) { total += 1800; found = true; s = it.replace(s, " ") } }
        Regex("\\bhalf (?:an|a) minute\\b").let { if (it.containsMatchIn(s)) { total += 30; found = true; s = it.replace(s, " ") } }
        s = s.replace(Regex("\\b(?:a|an)\\s+(hour|minute|second)"), "1 $1")

        val units = listOf(
            Regex("(\\d+)\\s*(?:hours?|hrs?)\\b") to 3600,
            Regex("(\\d+)\\s*(?:minutes?|mins?)\\b") to 60,
            Regex("(\\d+)\\s*(?:seconds?|secs?)\\b") to 1
        )
        for ((regex, factor) in units) {
            regex.find(s)?.let { m ->
                var amount = m.groupValues[1].toInt() * factor
                // "... and a half"
                val after = s.substring(m.range.last + 1)
                if (Regex("^\\s*and a half\\b").containsMatchIn(after)) amount += factor / 2
                total += amount
                found = true
            }
        }
        if (!found) {
            // A bare number: assume minutes ("timer for 10")
            Regex("^\\s*(\\d+)\\s*$").find(s)?.let { total = it.groupValues[1].toInt() * 60; found = true }
        }
        return if (found && total > 0) total else null
    }

    /** Formats seconds for speech: 5400 → "1 hour 30 minutes". */
    fun describeDuration(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        val parts = mutableListOf<String>()
        if (h > 0) parts += "$h hour" + if (h == 1) "" else "s"
        if (m > 0) parts += "$m minute" + if (m == 1) "" else "s"
        if (s > 0) parts += "$s second" + if (s == 1) "" else "s"
        return parts.joinToString(" ").ifEmpty { "0 seconds" }
    }
}
