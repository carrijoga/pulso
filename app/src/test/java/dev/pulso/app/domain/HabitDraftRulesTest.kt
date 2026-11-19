package dev.pulso.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Editor validation and move ordering for SPEC 009 (WP2a additions). */
class HabitDraftRulesTest {
    private val sp = ZoneId.of("America/Sao_Paulo")
    private val lisbon = ZoneId.of("Europe/Lisbon")
    private val newYork = ZoneId.of("America/New_York")

    @Test fun `daily draft clears days and weekly count and trims texts`() {
        val valid = HabitRules.validateDraft(HabitDraft("  Beber água  ", "  dois litros  ", HabitScheduleType.DAILY,
            daysOfWeek = setOf(DayOfWeek.MONDAY), timesPerWeek = 4))
        assertEquals("Beber água", valid.name)
        assertEquals("dois litros", valid.notes)
        assertEquals(emptySet<DayOfWeek>(), valid.daysOfWeek)
        assertEquals(0, valid.timesPerWeek)
    }

    @Test fun `days of week draft needs at least one day and drops the weekly count`() {
        assertThrows(IllegalArgumentException::class.java) {
            HabitRules.validateDraft(HabitDraft("Ler", scheduleType = HabitScheduleType.DAYS_OF_WEEK))
        }
        val valid = HabitRules.validateDraft(HabitDraft("Ler", scheduleType = HabitScheduleType.DAYS_OF_WEEK,
            daysOfWeek = setOf(DayOfWeek.SATURDAY), timesPerWeek = 3))
        assertEquals(setOf(DayOfWeek.SATURDAY), valid.daysOfWeek)
        assertEquals(0, valid.timesPerWeek)
    }

    @Test fun `times per week accepts 1 to 7 and clears the days`() {
        for (n in 1..7) {
            val valid = HabitRules.validateDraft(HabitDraft("Treinar", scheduleType = HabitScheduleType.TIMES_PER_WEEK,
                daysOfWeek = setOf(DayOfWeek.FRIDAY), timesPerWeek = n))
            assertEquals(n, valid.timesPerWeek)
            assertEquals(emptySet<DayOfWeek>(), valid.daysOfWeek)
        }
        listOf(0, 8).forEach { n ->
            assertThrows(IllegalArgumentException::class.java) {
                HabitRules.validateDraft(HabitDraft("Treinar", scheduleType = HabitScheduleType.TIMES_PER_WEEK, timesPerWeek = n))
            }
        }
    }

    @Test fun `blank name and too long notes are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { HabitRules.validateDraft(HabitDraft("   ")) }
        assertThrows(IllegalArgumentException::class.java) {
            HabitRules.validateDraft(HabitDraft("x".repeat(HabitRules.MAX_NAME + 1)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            HabitRules.validateDraft(HabitDraft("Ler", notes = "x".repeat(HabitRules.MAX_NOTES + 1)))
        }
        assertEquals(HabitRules.MAX_NOTES, HabitRules.validateDraft(HabitDraft("Ler", notes = "x".repeat(HabitRules.MAX_NOTES)))
            .notes.length)
    }

    @Test fun `move up puts the habit before its neighbour`() {
        // [A, B, C]: move C up to index 1 (where B is) gives [A, C, B].
        val result = HabitRules.positionsAfterMove(listOf("A", "B", "C"), "C", 1)
        assertEquals(mapOf("A" to 0, "C" to 1, "B" to 2), result)
    }

    @Test fun `move down puts the habit after its neighbour`() {
        // [A, B, C]: move A down past B (B is at index 1) gives [B, A, C].
        val result = HabitRules.positionsAfterMove(listOf("A", "B", "C"), "A", 1)
        assertEquals(mapOf("B" to 0, "A" to 1, "C" to 2), result)
    }

    @Test fun `move keeps every other order and clamps out of range targets`() {
        assertEquals(mapOf("B" to 0, "C" to 1, "A" to 2), HabitRules.positionsAfterMove(listOf("A", "B", "C"), "A", 99))
        assertEquals(mapOf("C" to 0, "A" to 1, "B" to 2), HabitRules.positionsAfterMove(listOf("A", "B", "C"), "C", -5))
        assertEquals(mapOf("A" to 0, "B" to 1, "C" to 2), HabitRules.positionsAfterMove(listOf("A", "B", "C"), "B", 1))
    }

    @Test fun `unknown id leaves the order unchanged`() {
        assertEquals(mapOf("A" to 0, "B" to 1), HabitRules.positionsAfterMove(listOf("A", "B"), "Z", 0))
    }

    @Test fun `streak text agrees with the unit of the schedule`() {
        fun habit(type: HabitScheduleType) =
            Habit("h1", "Ler", "", type, emptySet(), if (type == HabitScheduleType.TIMES_PER_WEEK) 3 else 0, null, 0, false, 0L)

        val daily = habit(HabitScheduleType.DAILY)
        assertEquals("2 dias seguidos", HabitRules.streakText(daily, 2))
        assertEquals("15 dias seguidos", HabitRules.streakText(daily, 15))
        assertEquals("1 dia seguido", HabitRules.streakText(daily, 1))
        assertEquals("2 dias seguidos", HabitRules.streakText(habit(HabitScheduleType.DAYS_OF_WEEK), 2))

        val weekly = habit(HabitScheduleType.TIMES_PER_WEEK)
        assertEquals("3 semanas seguidas", HabitRules.streakText(weekly, 3))
        assertEquals("1 semana seguida", HabitRules.streakText(weekly, 1))
    }

    @Test fun `creation date is the local day of the zone, across a DST change`() {
        // New York ends DST on 2026-11-01 at 02:00; 23:30 that night is already 04:30 UTC of the next day.
        val createdAt = LocalDateTime.of(2026, 11, 1, 23, 30).atZone(newYork).toInstant().toEpochMilli()
        val habit = Habit("h1", "Ler", "", HabitScheduleType.DAILY, emptySet(), 0, null, 0, false, createdAt)
        assertEquals(LocalDate.of(2026, 11, 1), HabitRules.createdOn(habit, newYork))
        assertEquals(LocalDate.of(2026, 11, 2), HabitRules.createdOn(habit, ZoneId.of("UTC")))
        // Sao Paulo has no DST since 2019: the same instant is still 01:30 of the 2nd there.
        assertEquals(LocalDate.of(2026, 11, 2), HabitRules.createdOn(habit, sp))
    }

    @Test fun `creation date across Lisbon's end of summer time`() {
        // Lisbon returns to WET on 2026-10-25 at 01:00 UTC; 00:30 local that day is still WEST.
        val createdAt = LocalDateTime.of(2026, 10, 25, 0, 30).atZone(lisbon).toInstant().toEpochMilli()
        val habit = Habit("h1", "Ler", "", HabitScheduleType.DAILY, emptySet(), 0, null, 0, false, createdAt)
        assertEquals(LocalDate.of(2026, 10, 25), HabitRules.createdOn(habit, lisbon))
    }
}
