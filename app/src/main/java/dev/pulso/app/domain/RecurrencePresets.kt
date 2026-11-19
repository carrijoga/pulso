package dev.pulso.app.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/** Options of the recurrence picker (SPEC 008, UI). Pure: no Android. */
enum class RecurrenceChoice { NONE, DAILY, WEEKLY, MONTHLY, YEARLY, CUSTOM }

/** Maps the picker's options to rules and back. Weekdays exist only for weekly calendar schedules. */
object RecurrencePresets {
    fun choiceOf(rule: RecurrenceRule?): RecurrenceChoice = when {
        rule == null -> RecurrenceChoice.NONE
        rule.basis == RecurrenceBasis.COMPLETION || rule.interval != 1 -> RecurrenceChoice.CUSTOM
        rule.frequency == RecurrenceFrequency.DAILY -> RecurrenceChoice.DAILY
        rule.frequency == RecurrenceFrequency.WEEKLY -> RecurrenceChoice.WEEKLY
        rule.frequency == RecurrenceFrequency.MONTHLY -> RecurrenceChoice.MONTHLY
        else -> RecurrenceChoice.YEARLY
    }

    /** Weekday of the anchor in the user's zone (a weekly rule starts on it). */
    fun anchorDay(anchorDueAt: Long, zone: ZoneId): DayOfWeek =
        Instant.ofEpochMilli(anchorDueAt).atZone(zone).dayOfWeek

    /** Starting rule for a picker option; [CUSTOM] starts at "a cada 2 dias" and is then edited. */
    fun presetRule(choice: RecurrenceChoice, anchorDueAt: Long, zone: ZoneId): RecurrenceRule? = when (choice) {
        RecurrenceChoice.NONE -> null
        RecurrenceChoice.DAILY -> RecurrenceRule(RecurrenceFrequency.DAILY)
        RecurrenceChoice.WEEKLY -> RecurrenceRule(RecurrenceFrequency.WEEKLY,
            daysOfWeek = setOf(anchorDay(anchorDueAt, zone)))
        RecurrenceChoice.MONTHLY -> RecurrenceRule(RecurrenceFrequency.MONTHLY)
        RecurrenceChoice.YEARLY -> RecurrenceRule(RecurrenceFrequency.YEARLY)
        RecurrenceChoice.CUSTOM -> RecurrenceRule(RecurrenceFrequency.DAILY, interval = 2)
    }

    /**
     * Applies a change of unit or base in the custom editor. Weekdays are kept only for a weekly
     * calendar schedule; when that schedule has none yet, it starts on [anchorDay].
     */
    fun adjust(rule: RecurrenceRule, frequency: RecurrenceFrequency, basis: RecurrenceBasis, anchorDay: DayOfWeek): RecurrenceRule {
        val weekly = frequency == RecurrenceFrequency.WEEKLY && basis == RecurrenceBasis.SCHEDULE
        val days: Set<DayOfWeek> = when {
            !weekly -> emptySet()
            rule.daysOfWeek.isEmpty() -> setOf(anchorDay)
            else -> rule.daysOfWeek
        }
        return rule.copy(frequency = frequency, basis = basis, daysOfWeek = days)
    }
}
