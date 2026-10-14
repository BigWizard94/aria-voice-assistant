package com.bigwizard.aria

import com.bigwizard.aria.util.SpokenNumbers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpokenNumbersTest {

    @Test fun digitize() {
        assertEquals("25 minutes", SpokenNumbers.digitize("twenty five minutes"))
        assertEquals("7 30 am", SpokenNumbers.digitize("seven thirty am"))
        assertEquals("7 05", SpokenNumbers.digitize("seven oh five"))
        assertEquals("150", SpokenNumbers.digitize("one hundred and fifty"))
        assertEquals("call mom", SpokenNumbers.digitize("call mom"))
    }

    @Test fun clockTimes() {
        assertEquals(7 to 30, SpokenNumbers.parseClockTime("seven thirty a m"))
        assertEquals(19 to 30, SpokenNumbers.parseClockTime("seven thirty p m"))
        assertEquals(7 to 30, SpokenNumbers.parseClockTime("7:30 AM"))
        assertEquals(18 to 0, SpokenNumbers.parseClockTime("six pm"))
        assertEquals(6 to 0, SpokenNumbers.parseClockTime("6 o'clock"))
        assertEquals(0 to 0, SpokenNumbers.parseClockTime("twelve am"))
        assertEquals(12 to 0, SpokenNumbers.parseClockTime("noon"))
        assertEquals(7 to 5, SpokenNumbers.parseClockTime("seven oh five"))
        assertEquals(7 to 30, SpokenNumbers.parseClockTime("730"))
        assertEquals(8 to 30, SpokenNumbers.parseClockTime("half past eight"))
        assertEquals(6 to 45, SpokenNumbers.parseClockTime("quarter to seven"))
        assertEquals(21 to 0, SpokenNumbers.parseClockTime("nine tonight"))
        assertNull(SpokenNumbers.parseClockTime("whenever"))
    }

    @Test fun durations() {
        assertEquals(300, SpokenNumbers.parseDurationSeconds("five minutes"))
        assertEquals(1500, SpokenNumbers.parseDurationSeconds("twenty five minutes"))
        assertEquals(3600, SpokenNumbers.parseDurationSeconds("an hour"))
        assertEquals(1800, SpokenNumbers.parseDurationSeconds("half an hour"))
        assertEquals(5400, SpokenNumbers.parseDurationSeconds("an hour and a half"))
        assertEquals(90, SpokenNumbers.parseDurationSeconds("1 minute 30 seconds"))
        assertEquals(600, SpokenNumbers.parseDurationSeconds("10"))
        assertEquals(300, SpokenNumbers.parseDurationSeconds("five minute"))
        assertNull(SpokenNumbers.parseDurationSeconds(""))
        assertNull(SpokenNumbers.parseDurationSeconds("a while"))
    }

    @Test fun describe() {
        assertEquals("1 hour 30 minutes", SpokenNumbers.describeDuration(5400))
        assertEquals("5 minutes", SpokenNumbers.describeDuration(300))
        assertEquals("1 second", SpokenNumbers.describeDuration(1))
    }
}
