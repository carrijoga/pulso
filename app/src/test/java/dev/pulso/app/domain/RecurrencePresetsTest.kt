package dev.pulso.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

class RecurrencePresetsTest {
    private val sp = ZoneId.of("America/Sao_Paulo")
    private val ny = ZoneId.of("America/New_York")

    private fun ms(zone: ZoneId, local: String) = LocalDateTime.parse(local).atZone(zone).toInstant().toEpochMilli()

    @Test fun `choiceOf maps picker options back from rules`() {
        assertEquals(RecurrenceChoice.NONE, RecurrencePresets.choiceOf(null))
        assertEquals(RecurrenceChoice.DAILY, RecurrencePresets.choiceOf(RecurrenceRule(RecurrenceFrequency.DAILY)))
        assertEquals(RecurrenceChoice.WEEKLY, RecurrencePresets.choiceOf(
            RecurrenceRule(RecurrenceFrequency.WEEKLY, daysOfWeek = setOf(DayOfWeek.FRIDAY))))
        assertEquals(RecurrenceChoice.MONTHLY, RecurrencePresets.choiceOf(RecurrenceRule(RecurrenceFrequency.MONTHLY)))
        assertEquals(RecurrenceChoice.YEARLY, RecurrencePresets.choiceOf(RecurrenceRule(RecurrenceFrequency.YEARLY)))
    }

    @Test fun `interval other than one and completion basis are custom`() {
        assertEquals(RecurrenceChoice.CUSTOM, RecurrencePresets.choiceOf(RecurrenceRule(RecurrenceFrequency.DAILY, interval = 2)))
        assertEquals(RecurrenceChoice.CUSTOM, RecurrencePresets.choiceOf(
            RecurrenceRule(RecurrenceFrequency.MONTHLY, basis = RecurrenceBasis.COMPLETION)))
    }

    @Test fun `weekly preset starts on the anchor weekday in the user's zone, not in UTC`() {
        // 2026-01-07 is a Wednesday. 23:30 in Sao Paulo is already Thursday 02:30 UTC.
        val anchor = ms(sp, "2026-01-07T23:30")
        val rule = RecurrencePresets.presetRule(RecurrenceChoice.WEEKLY, anchor, sp)
        assertEquals(setOf(DayOfWeek.WEDNESDAY), rule?.daysOfWeek)
        assertTrue(RecurrenceRules.validate(rule!!).daysOfWeek.isNotEmpty())
    }

    @Test fun `presets for none, daily, monthly, yearly and custom`() {
        val anchor = ms(ny, "2026-03-08T23:30") // Sunday after the New York spring-forward
        assertNull(RecurrencePresets.presetRule(RecurrenceChoice.NONE, anchor, ny))
        assertEquals(RecurrenceRule(RecurrenceFrequency.DAILY), RecurrencePresets.presetRule(RecurrenceChoice.DAILY, anchor, ny))
        assertEquals(RecurrenceRule(RecurrenceFrequency.MONTHLY), RecurrencePresets.presetRule(RecurrenceChoice.MONTHLY, anchor, ny))
        assertEquals(RecurrenceRule(RecurrenceFrequency.YEARLY), RecurrencePresets.presetRule(RecurrenceChoice.YEARLY, anchor, ny))
        assertEquals(RecurrenceRule(RecurrenceFrequency.DAILY, interval = 2),
            RecurrencePresets.presetRule(RecurrenceChoice.CUSTOM, anchor, ny))
    }

    @Test fun `anchorDay uses the zone across the New York DST change`() {
        assertEquals(DayOfWeek.SUNDAY, RecurrencePresets.anchorDay(ms(ny, "2026-03-08T23:30"), ny))
        assertEquals(DayOfWeek.MONDAY, RecurrencePresets.anchorDay(ms(ny, "2026-03-09T00:30"), ny))
    }

    @Test fun `adjust keeps weekdays only for a weekly calendar schedule`() {
        val daily = RecurrenceRule(RecurrenceFrequency.DAILY)
        val weekly = RecurrencePresets.adjust(daily, RecurrenceFrequency.WEEKLY, RecurrenceBasis.SCHEDULE, DayOfWeek.WEDNESDAY)
        assertEquals(setOf(DayOfWeek.WEDNESDAY), weekly.daysOfWeek) // empty weekly starts on the anchor day

        val edited = weekly.copy(daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
        val kept = RecurrencePresets.adjust(edited, RecurrenceFrequency.WEEKLY, RecurrenceBasis.SCHEDULE, DayOfWeek.WEDNESDAY)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), kept.daysOfWeek)

        val afterCompletion = RecurrencePresets.adjust(edited, RecurrenceFrequency.WEEKLY, RecurrenceBasis.COMPLETION, DayOfWeek.WEDNESDAY)
        assertTrue(afterCompletion.daysOfWeek.isEmpty())
        assertEquals(RecurrenceBasis.COMPLETION, afterCompletion.basis)

        val backToSchedule = RecurrencePresets.adjust(afterCompletion, RecurrenceFrequency.WEEKLY, RecurrenceBasis.SCHEDULE, DayOfWeek.THURSDAY)
        assertEquals(setOf(DayOfWeek.THURSDAY), backToSchedule.daysOfWeek)

        val monthly = RecurrencePresets.adjust(edited, RecurrenceFrequency.MONTHLY, RecurrenceBasis.SCHEDULE, DayOfWeek.WEDNESDAY)
        assertTrue(monthly.daysOfWeek.isEmpty())
        RecurrenceRules.validate(monthly) // must not throw
        RecurrenceRules.validate(backToSchedule)
    }
}
