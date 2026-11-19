package dev.pulso.app.domain

import java.time.DayOfWeek
import java.time.LocalDate

enum class RecurrenceFrequency { DAILY, WEEKLY, MONTHLY, YEARLY }
enum class RecurrenceBasis { SCHEDULE, COMPLETION }

data class RecurrenceRule(
    val frequency: RecurrenceFrequency,
    val interval: Int = 1,
    val daysOfWeek: Set<DayOfWeek> = emptySet(),
    val basis: RecurrenceBasis = RecurrenceBasis.SCHEDULE,
)

data class TaskSeries(val id: String, val rule: RecurrenceRule, val anchorDueAt: Long)

enum class HabitScheduleType { DAILY, DAYS_OF_WEEK, TIMES_PER_WEEK }

data class Habit(
    val id: String,
    val name: String,
    val notes: String,
    val scheduleType: HabitScheduleType,
    val daysOfWeek: Set<DayOfWeek>,
    val timesPerWeek: Int,
    val reminderMinutes: Int?,
    val position: Int,
    val archived: Boolean,
    /** Streaks do not count days before the local creation date. */
    val createdAt: Long,
)

data class HabitCheckin(val id: String, val habitId: String, val date: LocalDate)

data class ChecklistItem(val id: String, val taskId: String, val text: String, val position: Int, val checked: Boolean)

/** Bitmask for weekdays: Monday = bit 0 ... Sunday = bit 6 (`DayOfWeek.value - 1`). */
object DaysOfWeekMask {
    const val MAX = 127

    fun toMask(days: Set<DayOfWeek>): Int = days.fold(0) { acc, day -> acc or (1 shl (day.value - 1)) }

    fun fromMask(mask: Int): Set<DayOfWeek> =
        DayOfWeek.values().filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()
}
