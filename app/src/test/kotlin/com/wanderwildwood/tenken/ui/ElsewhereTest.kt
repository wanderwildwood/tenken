package com.wanderwildwood.tenken.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class ElsewhereTest {
    private val zone = ZoneId.of("America/New_York")

    private fun at(year: Int, month: Int, day: Int, hour: Int) =
        ZonedDateTime.of(year, month, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()

    @Test fun tomorrowIsTheSameTimeOnAnOrdinaryDay() {
        assertEquals(at(2026, 6, 11, 20), Elsewhere.reminderStart(at(2026, 6, 10, 20), zone))
    }

    @Test fun tomorrowKeepsTheTimeWhenTheClocksGoForward() {
        val start = Elsewhere.reminderStart(at(2026, 3, 7, 20), zone)
        assertEquals(at(2026, 3, 8, 20), start)
        assertEquals(20, Instant.ofEpochMilli(start).atZone(zone).hour)
    }

    @Test fun tomorrowKeepsTheTimeWhenTheClocksGoBack() {
        assertEquals(at(2026, 11, 1, 20), Elsewhere.reminderStart(at(2026, 10, 31, 20), zone))
    }
}
