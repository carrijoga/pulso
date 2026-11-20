package dev.pulso.app.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** A projected (never stored) future occurrence of a repeating task, shown as "Previsto" (SPEC 010). */
data class ProjectedOccurrence(val seriesId: String, val activeTaskId: String, val title: String, val dueAt: Long)

data class HabitDayMark(val habitId: String, val name: String, val done: Boolean, val editable: Boolean)

data class DayView(
    val date: LocalDate,
    val isToday: Boolean,
    /** Open tasks due on this day, by time. In the current week, days before today are not listed here. */
    val active: List<TaskItem>,
    /** Completed tasks whose due date is this day. */
    val completed: List<TaskItem>,
    val projected: List<ProjectedOccurrence>,
    val habits: List<HabitDayMark>,
)

data class WeekView(val overdue: List<TaskItem>, val days: List<DayView>, val withoutDueCount: Int)

/** Pure rules of the weekly calendar (SPEC 010): no Android, no persistence; the clock is passed in. */
object WeekRules {
    /** Monday of the week containing [date]. */
    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun daysOf(weekStart: LocalDate): List<LocalDate> = (0L..6L).map { weekStart.plusDays(it) }

    /** Moves a due date to [day] keeping its local time ([fallbackTime] when there is none yet). */
    fun moveToDay(dueAt: Long?, day: LocalDate, fallbackTime: LocalTime, zone: ZoneId): Long {
        val time = dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() } ?: fallbackTime
        return TaskRules.dueAtFor(day, time, zone)
    }

    /**
     * Repeating series that still have an open occurrence, read from the tasks (`TaskItem.recurrence`
     * is filled by the repository). Ended series are not active, so they produce nothing.
     */
    fun seriesOf(tasks: List<TaskItem>): List<TaskSeries> = tasks.asSequence()
        .filter { !it.isCompleted }
        .mapNotNull { task ->
            val id = task.seriesId ?: return@mapNotNull null
            val rule = task.recurrence ?: return@mapNotNull null
            val anchor = task.recurrenceAnchorDueAt ?: return@mapNotNull null
            TaskSeries(id, rule, anchor)
        }
        .distinctBy { it.id }
        .toList()

    /**
     * Builds the week starting at [weekStart]. Overdue tasks (open, due before today) are listed only when
     * [weekStart] is the current week. Projections are the future occurrences of each series after its open
     * occurrence, so nothing is duplicated; `COMPLETION` series have no projection. Habit marks show from the
     * creation day on, for the days the habit is scheduled; they are editable only inside [HabitRules.canCheck].
     */
    fun build(
        tasks: List<TaskItem>,
        series: List<TaskSeries>,
        habits: List<Habit>,
        checkins: List<HabitCheckin>,
        weekStart: LocalDate,
        nowMillis: Long,
        zone: ZoneId,
    ): WeekView {
        fun dayOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val today = dayOf(nowMillis)
        val isCurrentWeek = weekStart(today) == weekStart
        val days = daysOf(weekStart)
        val byDueDay: Map<LocalDate, List<TaskItem>> = tasks
            .mapNotNull { task -> task.dueAt?.let { dayOf(it) to task } }
            .groupBy({ it.first }, { it.second })
        val open = tasks.filter { !it.isCompleted }
        val overdue = if (isCurrentWeek) {
            open.filter { task -> task.dueAt?.let { dayOf(it) < today } == true }.sortedBy { it.dueAt }
        } else emptyList()
        val projections = projections(series, open, weekStart, zone)
        val doneKeys = checkins.map { it.habitId to it.date }.toSet()
        val scheduled = habits.filter { !it.archived }.sortedBy { it.position }

        val views = days.map { date ->
            val due = byDueDay[date].orEmpty()
            val hidePast = isCurrentWeek && date < today
            DayView(
                date = date,
                isToday = date == today,
                active = due.filter { !it.isCompleted && !hidePast }.sortedBy { it.dueAt },
                completed = due.filter { it.isCompleted }.sortedBy { it.dueAt },
                projected = projections[date].orEmpty(),
                habits = scheduled.filter { !date.isBefore(HabitRules.createdOn(it, zone)) && HabitRules.isScheduledOn(it, date) }
                    .map { HabitDayMark(it.id, it.name, (it.id to date) in doneKeys, HabitRules.canCheck(date, today)) },
            )
        }
        return WeekView(overdue, views, tasks.count { !it.isCompleted && it.dueAt == null })
    }

    /** Future occurrences inside the week, strictly after each series' open occurrence (never the same day). */
    private fun projections(
        series: List<TaskSeries>, open: List<TaskItem>, weekStart: LocalDate, zone: ZoneId,
    ): Map<LocalDate, List<ProjectedOccurrence>> {
        val weekEnd = weekStart.plusDays(6)
        val result = mutableMapOf<LocalDate, MutableList<ProjectedOccurrence>>()
        for (item in series) {
            if (item.rule.basis == RecurrenceBasis.COMPLETION) continue
            val active = open.filter { it.seriesId == item.id }.minByOrNull { it.dueAt ?: Long.MAX_VALUE } ?: continue
            val activeDueAt = active.dueAt ?: continue
            val activeDay = Instant.ofEpochMilli(activeDueAt).atZone(zone).toLocalDate()
            val from = maxOf(weekStart, activeDay.plusDays(1))
            if (from > weekEnd) continue
            val fromMillis = from.atStartOfDay(zone).toInstant().toEpochMilli()
            val toMillis = weekEnd.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
            RecurrenceRules.occurrencesBetween(item.rule, item.anchorDueAt, fromMillis, toMillis, zone).forEach { due ->
                val day = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
                if (day in from..weekEnd) {
                    result.getOrPut(day) { mutableListOf() }.add(ProjectedOccurrence(item.id, active.id, active.title, due))
                }
            }
        }
        return result
    }
}
