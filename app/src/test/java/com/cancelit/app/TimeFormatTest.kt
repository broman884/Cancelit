package com.cancelit.app

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {

    @Test
    fun shortDuration_listsEachUnit() {
        assertEquals("5 min", shortDuration(5 * 60_000L))
        assertEquals("1 h 30 min", shortDuration(90 * 60_000L))
        assertEquals("45 sec", shortDuration(45_000L))
        assertEquals("0 sec", shortDuration(0L))
    }

    @Test
    fun describeDuration_usesWordsAndPlurals() {
        assertEquals("1 hour", describeDuration(60 * 60_000L))
        assertEquals("2 minutes", describeDuration(2 * 60_000L))
        assertEquals("1 hour 1 minute 1 second", describeDuration(61 * 60_000L + 1_000L))
        assertEquals("0 seconds", describeDuration(-5L))
    }

    @Test
    fun formatDuration_showsTheTwoLargestUnits() {
        assertEquals("1h 02m", formatDuration(3_725_000L))
        assertEquals("4m 32s", formatDuration(272_000L))
        assertEquals("12s", formatDuration(12_000L))
        assertEquals("0s", formatDuration(-5L))
    }

    @Test
    fun formatClock_roundsCountdownsUp() {
        assertEquals("04:32", formatClock(271_001L))
        assertEquals("00:01", formatClock(1L))
        assertEquals("00:00", formatClock(0L))
        assertEquals("1:04:32", formatClock(3_872_000L))
    }

    @Test
    fun formatClock_canFloorAndPadHours() {
        assertEquals("04:31", formatClock(271_999L, roundUp = false))
        assertEquals("00:05:00", formatClock(300_000L, fixedHours = true))
    }
}
