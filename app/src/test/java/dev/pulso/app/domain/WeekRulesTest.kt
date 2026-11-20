package dev.pulso.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class WeekRulesTest {
    private val sp = ZoneId.of("America/Sao_Paulo")
    private val lisbon = ZoneId.of("Europe/Lisbon")
    private val ny = ZoneId.of("America/New_York")

    // 2026-11-19 is a Thursday; the current week starts Monday 2026-11-16.
    private val monday = LocalDate.of(2026, 11, 16)
    private val now = at("2026-11-19", "10:00")
    private val daily = RecurrenceRule(RecurrenceFrequency.DAILY)

    private fun at(date: String, time: String, zone: ZoneId = sp): Long =
        LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()

    private fun task(
        id: String,
        due: Long?,
        completedAt: Long? = null,
        seriesId: String? = null,
        rule: RecurrenceRule? = null,
        anchor: Long? = null,
    ) = TaskItem(
        id = id, title = "T$id", notes = "", category = "Pessoal", priority = Priority.NORMAL,
        dueAt = due, completedAt = completedAt, createdAt = 0L, updatedAt = 0L,
        seriesId = seriesId, recurrence = rule, recurrenceAnchorDueAt = anchor,
    )

    private fun habit(
        id: String = "h1",
        type: HabitScheduleType = HabitScheduleType.DAILY,
        days: Set<DayOfWeek> = emptySet(),
        createdAt: Long = at("2026-01-01", "12:00"),
        archived: Boolean = false,
    ) = Habit(id, "Água", "", type, days, 0, null, 0, archived, createdAt)

    private fun build(
        tasks: List<TaskItem> = emptyList(),
        habits: List<Habit> = emptyList(),
        checkins: List<HabitCheckin> = emptyList(),
        weekStart: LocalDate = monday,
        nowMillis: Long = now,
        zone: ZoneId = sp,
    ) = WeekRules.build(tasks, WeekRules.seriesOf(tasks), habits, checkins, weekStart, nowMillis, zone)

    private fun WeekView.day(date: LocalDate) = days.first { it.date == date }

    @Test
    fun weekStartIsMondayAndWeekHasSevenDays() {
        assertEquals(monday, WeekRules.weekStart(LocalDate.of(2026, 11, 18)))
        assertEquals(monday, WeekRules.weekStart(LocalDate.of(2026, 11, 22)))
        assertEquals(monday, WeekRules.weekStart(monday))
        val days = WeekRules.daysOf(monday)
        assertEquals(7, days.size)
        assertEquals(DayOfWeek.MONDAY, days.first().dayOfWeek)
        assertEquals(LocalDate.of(2026, 11, 22), days.last())
    }

    @Test
    fun dueTimeLandsOnTheLocalDayNotTheUtcDay() {
        // 23:30 in São Paulo is 02:30 UTC of the next day: it must stay on the 18th.
        val late = task("a", at("2026-11-18", "23:30"))
        // 00:30 in São Paulo of the 19th is 03:30 UTC of the 19th.
        val early = task("b", at("2026-11-19", "00:30"))
        // Seen from the 17th, so the 18th and 19th are both upcoming and not overdue.
        val week = build(listOf(late, early), nowMillis = at("2026-11-17", "10:00"))
        assertEquals(listOf("a"), week.day(LocalDate.of(2026, 11, 18)).active.map { it.id })
        assertEquals(listOf("b"), week.day(LocalDate.of(2026, 11, 19)).active.map { it.id })
        assertTrue(week.day(LocalDate.of(2026, 11, 17)).isToday)
        assertFalse(week.day(LocalDate.of(2026, 11, 19)).isToday)
    }

    @Test
    fun overdueIsListedOnlyInTheCurrentWeek() {
        val yesterday = task("a", at("2026-11-18", "10:00"))
        val current = build(listOf(yesterday))
        assertEquals(listOf("a"), current.overdue.map { it.id })
        assertTrue("overdue must not repeat in its day column", current.day(LocalDate.of(2026, 11, 18)).active.isEmpty())

        val tuesday = task("c", at("2026-11-10", "10:00"))
        val previous = build(listOf(tuesday), weekStart = LocalDate.of(2026, 11, 9))
        assertTrue(previous.overdue.isEmpty())
        assertEquals(listOf("c"), previous.day(LocalDate.of(2026, 11, 10)).active.map { it.id })
    }

    @Test
    fun completedTasksAppearOnTheirDueDayAndTasksWithoutDueAreCounted() {
        val done = task("d", at("2026-11-17", "09:00"), completedAt = at("2026-11-18", "08:00"))
        val open = task("o", null)
        val another = task("n", null)
        val week = build(listOf(done, open, another, task("x", null, completedAt = at("2026-11-18", "08:00"))))
        assertEquals(listOf("d"), week.day(LocalDate.of(2026, 11, 17)).completed.map { it.id })
        assertTrue(week.day(LocalDate.of(2026, 11, 17)).active.isEmpty())
        assertEquals(2, week.withoutDueCount)
    }

    @Test
    fun dailySeriesProjectsFutureDaysWithoutTheActiveDay() {
        val anchor = at("2026-11-16", "09:00")
        val active = task("a", at("2026-11-19", "09:00"), seriesId = "s", rule = daily, anchor = anchor)
        val week = build(listOf(active))
        assertTrue(week.day(LocalDate.of(2026, 11, 19)).projected.isEmpty())
        assertTrue(week.day(LocalDate.of(2026, 11, 18)).projected.isEmpty())
        val friday = week.day(LocalDate.of(2026, 11, 20)).projected.single()
        assertEquals("s", friday.seriesId)
        assertEquals("a", friday.activeTaskId)
        assertEquals("Ta", friday.title)
        assertEquals(at("2026-11-20", "09:00"), friday.dueAt)
        assertEquals(3, (20..22).count { week.day(LocalDate.of(2026, 11, it)).projected.isNotEmpty() })
    }

    @Test
    fun weeklySeriesProjectsOnlyItsWeekdaysAfterTheActiveOccurrence() {
        val rule = RecurrenceRule(RecurrenceFrequency.WEEKLY, daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        val anchor = at("2026-11-16", "09:00")
        val active = task("a", anchor, seriesId = "s", rule = rule, anchor = anchor)
        val week = build(listOf(active))
        assertTrue(week.day(monday).projected.isEmpty())
        // Wednesday is already past when "now" is Thursday 10:00.
        assertTrue(week.day(LocalDate.of(2026, 11, 18)).projected.isEmpty())
        assertEquals(1, week.day(LocalDate.of(2026, 11, 20)).projected.size)
        assertTrue(week.day(LocalDate.of(2026, 11, 21)).projected.isEmpty())
    }

    @Test
    fun overdueOpenOccurrenceProjectsOnlyFromNowOn() {
        // Daily 09:00; the open occurrence is Monday (overdue); now is Thursday 10:00.
        val anchor = at("2026-11-16", "09:00")
        val overdue = task("a", at("2026-11-16", "09:00"), seriesId = "s", rule = daily, anchor = anchor)
        val week = build(listOf(overdue))
        assertTrue(week.days.filter { it.date < LocalDate.of(2026, 11, 20) }.all { it.projected.isEmpty() })
        assertEquals(at("2026-11-20", "09:00"), week.day(LocalDate.of(2026, 11, 20)).projected.single().dueAt)
        // Thursday 09:00 is already past at 10:00: no projection for it either.
        assertTrue(week.day(LocalDate.of(2026, 11, 19)).projected.isEmpty())
        assertTrue("projections never land before today", week.days.none { it.date < LocalDate.of(2026, 11, 19) && it.projected.isNotEmpty() })
    }

    @Test
    fun completionSeriesIsNotProjected() {
        val rule = RecurrenceRule(RecurrenceFrequency.DAILY, basis = RecurrenceBasis.COMPLETION)
        val active = task("a", at("2026-11-19", "09:00"), seriesId = "s", rule = rule, anchor = at("2026-11-16", "09:00"))
        val week = build(listOf(active))
        assertTrue(week.days.all { it.projected.isEmpty() })
    }

    @Test
    fun endedOrCompletedSeriesProducesNoProjection() {
        val anchor = at("2026-11-16", "09:00")
        val completedOnly = task("a", at("2026-11-19", "09:00"), completedAt = now, seriesId = "s", rule = daily, anchor = anchor)
        assertTrue(WeekRules.seriesOf(listOf(completedOnly)).isEmpty())
        assertTrue(build(listOf(completedOnly)).days.all { it.projected.isEmpty() })
    }

    @Test
    fun dstKeepsTheLocalTimeOfProjectedOccurrences() {
        // New York switches to DST on 2026-03-08: the projected 09:00 must stay 09:00 local.
        val anchor = at("2026-03-07", "09:00", ny)
        val active = task("a", anchor, seriesId = "s", rule = daily, anchor = anchor)
        val week = build(listOf(active), weekStart = LocalDate.of(2026, 3, 2), nowMillis = at("2026-03-07", "10:00", ny), zone = ny)
        val projected = week.day(LocalDate.of(2026, 3, 8)).projected.single().dueAt
        val local = Instant.ofEpochMilli(projected).atZone(ny)
        assertEquals(LocalDate.of(2026, 3, 8), local.toLocalDate())
        assertEquals(LocalTime.of(9, 0), local.toLocalTime())
    }

    @Test
    fun gapTimeOfDstStaysOnTheSameDay() {
        // Lisbon skips 01:00-02:00 on 2026-03-29; the task still lands on that day.
        val week = build(listOf(task("g", at("2026-03-29", "01:30", lisbon))),
            weekStart = LocalDate.of(2026, 3, 23), nowMillis = at("2026-03-25", "10:00", lisbon), zone = lisbon)
        assertEquals(listOf("g"), week.day(LocalDate.of(2026, 3, 29)).active.map { it.id })
    }

    @Test
    fun moveToDayKeepsTheLocalHour() {
        val dueAt = at("2026-11-18", "23:30")
        val moved = WeekRules.moveToDay(dueAt, LocalDate.of(2026, 11, 20), LocalTime.of(8, 0), sp)
        assertEquals(at("2026-11-20", "23:30"), moved)

        val nyDue = at("2026-03-07", "09:00", ny)
        val movedAcrossDst = WeekRules.moveToDay(nyDue, LocalDate.of(2026, 3, 8), LocalTime.of(8, 0), ny)
        assertEquals(LocalTime.of(9, 0), Instant.ofEpochMilli(movedAcrossDst).atZone(ny).toLocalTime())

        assertEquals(at("2026-11-20", "08:00"), WeekRules.moveToDay(null, LocalDate.of(2026, 11, 20), LocalTime.of(8, 0), sp))
    }

    @Test
    fun habitMarksAreEditableOnlyWithinSevenDaysAndNotInTheFuture() {
        val previous = build(habits = listOf(habit()), weekStart = LocalDate.of(2026, 11, 9))
        assertFalse(previous.day(LocalDate.of(2026, 11, 11)).habits.single().editable) // 8 days back
        assertTrue(previous.day(LocalDate.of(2026, 11, 12)).habits.single().editable)  // 7 days back

        val current = build(habits = listOf(habit()))
        assertTrue(current.day(LocalDate.of(2026, 11, 19)).habits.single().editable)  // today
        assertFalse(current.day(LocalDate.of(2026, 11, 20)).habits.single().editable) // tomorrow
    }

    @Test
    fun habitMarksFollowScheduleCreationDayAndCheckins() {
        val weekly = habit(type = HabitScheduleType.DAYS_OF_WEEK, days = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))
        val checkin = HabitCheckin("c1", "h1", LocalDate.of(2026, 11, 19))
        val week = build(habits = listOf(weekly), checkins = listOf(checkin))
        assertEquals(listOf(monday), week.days.filter { it.habits.isNotEmpty() }.map { it.date }.take(1))
        assertTrue(week.day(LocalDate.of(2026, 11, 19)).habits.single().done)

        val createdThursday = habit(createdAt = at("2026-11-19", "12:00"))
        val later = build(habits = listOf(createdThursday))
        assertTrue(later.day(LocalDate.of(2026, 11, 18)).habits.isEmpty())
        assertEquals(1, later.day(LocalDate.of(2026, 11, 19)).habits.size)
    }

    @Test
    fun archivedHabitsAreNotShown() {
        val week = build(habits = listOf(habit(archived = true)))
        assertTrue(week.days.all { it.habits.isEmpty() })
    }
}
