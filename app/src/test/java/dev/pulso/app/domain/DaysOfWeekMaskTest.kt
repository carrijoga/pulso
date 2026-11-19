package dev.pulso.app.domain

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Test

class DaysOfWeekMaskTest {
    @Test fun mondayIsBit0AndSundayIsBit6() {
        assertEquals(1, DaysOfWeekMask.toMask(setOf(DayOfWeek.MONDAY)))
        assertEquals(64, DaysOfWeekMask.toMask(setOf(DayOfWeek.SUNDAY)))
        assertEquals(0b0010101, DaysOfWeekMask.toMask(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)))
    }

    @Test fun roundTripForEveryMask() {
        for (mask in 0..DaysOfWeekMask.MAX) {
            assertEquals(mask, DaysOfWeekMask.toMask(DaysOfWeekMask.fromMask(mask)))
        }
    }

    @Test fun limits() {
        assertEquals(0, DaysOfWeekMask.toMask(emptySet()))
        assertEquals(emptySet<DayOfWeek>(), DaysOfWeekMask.fromMask(0))
        assertEquals(DayOfWeek.values().toSet(), DaysOfWeekMask.fromMask(127))
        assertEquals(127, DaysOfWeekMask.toMask(DayOfWeek.values().toSet()))
        // Bits above the seventh are ignored.
        assertEquals(DayOfWeek.values().toSet(), DaysOfWeekMask.fromMask(255))
    }
}
