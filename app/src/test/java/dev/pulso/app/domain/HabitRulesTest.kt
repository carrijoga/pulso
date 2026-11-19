package dev.pulso.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class HabitRulesTest {
    private val sp = ZoneId.of("America/Sao_Paulo")
    private val tokyo = ZoneId.of("Asia/Tokyo")

    // 2026-11-19 is a Thursday; its week starts Monday 2026-11-16.
    private val today = LocalDate.of(2026, 11, 19)
    private val monday = LocalDate.of(2026, 11, 16)
    private val friday = LocalDate.of(2026, 11, 20)
    private val saturday = LocalDate.of(2026, 11, 21)
    private val sunday = LocalDate.of(2026, 11, 22)
    private val nextMonday = LocalDate.of(2026, 11, 23)
    private val longAgo = LocalDate.of(2026, 1, 1)

    private fun d(s: String) = LocalDate.parse(s)
    private fun at(date: LocalDate, zone: ZoneId = sp) = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun habit(
        type: HabitScheduleType = HabitScheduleType.DAILY,
        days: Set<DayOfWeek> = emptySet(),
        times: Int = 0,
        created: LocalDate = longAgo,
        archived: Boolean = false,
        position: Int = 0,
        id: String = "h1",
    ) = Habit(id, "Água", "", type, days, times, null, position, archived, at(created))

    private fun ck(vararg dates: LocalDate, habitId: String = "h1") =
        dates.mapIndexed { i, date -> HabitCheckin("c$habitId$i", habitId, date) }

    private fun streak(h: Habit, checkins: List<HabitCheckin>, now: LocalDate = today, created: LocalDate = longAgo) =
        HabitRules.currentStreak(h, checkins, now, created)

    private fun progress(h: Habit, checkins: List<HabitCheckin>, week: LocalDate, created: LocalDate = longAgo) =
        HabitRules.weekProgress(h, checkins, week, created)

    private val mwf = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)

    // --- name / describe ---

    @Test fun `validateName trims collapses spaces and enforces limits`() {
        assertEquals("Ler 20 min", HabitRules.validateName("  Ler   20 min "))
        assertThrows(IllegalArgumentException::class.java) { HabitRules.validateName("   ") }
        assertEquals(60, HabitRules.validateName("a".repeat(60)).length)
        assertThrows(IllegalArgumentException::class.java) { HabitRules.validateName("a".repeat(61)) }
    }

    @Test fun `describe uses pt-BR`() {
        assertEquals("Todo dia", HabitRules.describe(habit()))
        assertEquals("Seg, qua e sex", HabitRules.describe(habit(HabitScheduleType.DAYS_OF_WEEK, days = mwf)))
        assertEquals("Sáb", HabitRules.describe(habit(HabitScheduleType.DAYS_OF_WEEK, days = setOf(DayOfWeek.SATURDAY))))
        assertEquals("Ter e qui", HabitRules.describe(habit(HabitScheduleType.DAYS_OF_WEEK, days = setOf(DayOfWeek.THURSDAY, DayOfWeek.TUESDAY))))
        assertEquals("3x por semana", HabitRules.describe(habit(HabitScheduleType.TIMES_PER_WEEK, times = 3)))
    }

    // --- schedule ---

    @Test fun `isScheduledOn covers the three schedules`() {
        assertTrue(HabitRules.isScheduledOn(habit(), today))
        val dow = habit(HabitScheduleType.DAYS_OF_WEEK, days = mwf)
        assertTrue(HabitRules.isScheduledOn(dow, monday))
        assertFalse(HabitRules.isScheduledOn(dow, today)) // Thursday
        assertTrue(HabitRules.isScheduledOn(habit(HabitScheduleType.TIMES_PER_WEEK, times = 3), today))
    }

    // --- canCheck ---

    @Test fun `canCheck accepts today and up to 7 days back only`() {
        assertTrue(HabitRules.canCheck(today, today))
        assertTrue(HabitRules.canCheck(today.minusDays(7), today))
        assertFalse(HabitRules.canCheck(today.minusDays(8), today))
        assertFalse(HabitRules.canCheck(today.plusDays(1), today))
    }

    // --- weekProgress ---

    @Test fun `weekProgress daily targets 7`() {
        val p = progress(habit(), ck(monday, monday.plusDays(1), monday.minusDays(1), monday.plusDays(7)), monday)
        assertEquals(WeekProgress(2, 7), p)
    }

    @Test fun `weekProgress days of week counts only marked days`() {
        val h = habit(HabitScheduleType.DAYS_OF_WEEK, days = mwf)
        // Mon, Tue (extra, not scheduled), Wed
        val p = progress(h, ck(monday, monday.plusDays(1), monday.plusDays(2)), monday)
        assertEquals(WeekProgress(2, 3), p)
    }

    @Test fun `weekProgress times per week met and not met`() {
        val h = habit(HabitScheduleType.TIMES_PER_WEEK, times = 3)
        assertEquals(WeekProgress(2, 3), progress(h, ck(monday, monday.plusDays(1)), monday))
        assertEquals(WeekProgress(3, 3), progress(h, ck(monday, monday.plusDays(1), monday.plusDays(3)), monday))
    }

    @Test fun `weekProgress ignores other habits and duplicate dates`() {
        val h = habit()
        val all = ck(monday, monday) + ck(monday.plusDays(1), habitId = "other")
        assertEquals(WeekProgress(1, 7), progress(h, all, monday))
    }

    @Test fun `week turns over on monday`() {
        val h = habit(HabitScheduleType.TIMES_PER_WEEK, times = 2)
        val previousSunday = monday.minusDays(1)
        assertEquals(WeekProgress(0, 2), progress(h, ck(previousSunday), monday))
        assertEquals(WeekProgress(1, 2), progress(h, ck(previousSunday), HabitRules.weekStart(previousSunday)))
        assertEquals(monday, HabitRules.weekStart(monday))
        assertEquals(monday, HabitRules.weekStart(monday.plusDays(6)))
        assertEquals(monday.plusWeeks(1), HabitRules.weekStart(monday.plusDays(7)))
    }

    // --- proportional goal in the creation week ---

    @Test fun `times per week created friday keeps the 3x goal (fri, sat, sun)`() {
        val h = habit(HabitScheduleType.TIMES_PER_WEEK, times = 3, created = friday)
        assertEquals(WeekProgress(0, 3), progress(h, emptyList(), monday, created = friday))
        assertEquals(WeekProgress(3, 3), progress(h, ck(friday, saturday, sunday), monday, created = friday))
    }

    @Test fun `times per week created saturday targets the 2 remaining days`() {
        val h = habit(HabitScheduleType.TIMES_PER_WEEK, times = 3, created = saturday)
        assertEquals(WeekProgress(1, 2), progress(h, ck(saturday), monday, created = saturday))
        assertEquals(WeekProgress(2, 2), progress(h, ck(saturday, sunday), monday, created = saturday))
    }

    @Test fun `times per week created saturday with sat and sun done counts one week of streak`() {
        val h = habit(HabitScheduleType.TIMES_PER_WEEK, times = 3, created = saturday)
        // Check-in before creation (friday) is ignored; sat + sun meet the prorated goal of 2.
        assertEquals(1, streak(h, ck(friday, saturday, sunday), now = sunday, created = saturday))
        assertEquals(0, streak(h, ck(friday, saturday), now = sunday, created = saturday))
    }

    @Test fun `daily created thursday targets the 4 remaining days`() {
        val h = habit(created = today)
        assertEquals(WeekProgress(2, 4), progress(h, ck(today, friday), monday, created = today))
    }

    @Test fun `days of week seg qua sex created thursday targets only friday`() {
        val h = habit(HabitScheduleType.DAYS_OF_WEEK, days = mwf, created = today)
        assertEquals(WeekProgress(1, 1), progress(h, ck(friday), monday, created = today))
        // Monday and Wednesday are before creation: they neither count nor raise the goal.
        assertEquals(WeekProgress(1, 1), progress(h, ck(monday, friday), monday, created = today))
    }

    @Test fun `days of week created saturday with no marked day left ignores the week`() {
        val h = habit(HabitScheduleType.DAYS_OF_WEEK, days = mwf, created = saturday)
        assertEquals(WeekProgress(0, 0), progress(h, emptyList(), monday, created = saturday))
        // Friday check-in is before creation: no progress and no streak.
        assertEquals(WeekProgress(0, 0), progress(h, ck(friday), monday, created = saturday))
        assertEquals(0, streak(h, ck(friday), now = sunday, created = saturday))
    }

    @Test fun `next week after creation keeps the full goal`() {
        val daily = habit(created = today)
        assertEquals(WeekProgress(0, 7), progress(daily, emptyList(), nextMonday, created = today))
        val threeX = habit(HabitScheduleType.TIMES_PER_WEEK, times = 3, created = saturday)
        assertEquals(WeekProgress(0, 3), progress(threeX, emptyList(), nextMonday, created = saturday))
        val mwfHabit = habit(HabitScheduleType.DAYS_OF_WEEK, days = mwf, created = saturday)
        assertEquals(WeekProgress(0, 3), progress(mwfHabit, emptyList(), nextMonday, created = saturday))
    }

    @Test fun `habit created on sunday targets only sunday`() {
        assertEquals(WeekProgress(1, 1), progress(habit(created = sunday), ck(sunday), monday, created = sunday))
        val threeX = habit(HabitScheduleType.TIMES_PER_WEEK, times = 3, created = sunday)
        assertEquals(WeekProgress(0, 1), progress(threeX, emptyList(), monday, created = sunday))
        val mwfHabit = habit(HabitScheduleType.DAYS_OF_WEEK, days = mwf, created = sunday)
        assertEquals(WeekProgress(0, 0), progress(mwfHabit, emptyList(), monday, created = sunday))
        assertEquals(1, streak(habit(created = sunday), ck(sunday), now = sunday, created = sunday))
    }

    // --- daily / days-of-week streak ---

    @Test fun `daily streak counts consecutive days including today`() {
        assertEquals(3, streak(habit(), ck(today, today.minusDays(1), today.minusDays(2))))
    }

    @Test fun `pending today does not break the streak`() {
        assertEquals(2, streak(habit(), ck(today.minusDays(1), today.minusDays(2))))
    }

    @Test fun `a missed past day breaks the streak`() {
        assertEquals(1, streak(habit(), ck(today, today.minusDays(2))))
        assertEquals(0, streak(habit(), ck(today.minusDays(2))))
    }

    @Test fun `unscheduled days are skipped`() {
        val h = habit(HabitScheduleType.DAYS_OF_WEEK, days = mwf)
        // Thursday today (not scheduled). Wed and Mon done, Fri of last week done; Sat/Sun/Tue skipped.
        val checkins = ck(d("2026-11-18"), monday, d("2026-11-13"))
        assertEquals(3, streak(h, checkins))
    }

    @Test fun `scheduled today pending starts from the previous scheduled day`() {
        val h = habit(HabitScheduleType.DAYS_OF_WEEK, days = mwf)
        val wednesday = d("2026-11-18")
        assertEquals(2, streak(h, ck(monday, d("2026-11-13")), now = wednesday))
        assertEquals(0, streak(h, ck(d("2026-11-13")), now = wednesday)) // Monday missed
    }

    @Test fun `extra check-in off schedule is accepted and breaks nothing`() {
        val h = habit(HabitScheduleType.DAYS_OF_WEEK, days = mwf)
        val extra = ck(d("2026-11-18"), d("2026-11-17"), monday) // Tue is extra
        assertEquals(2, streak(h, extra))
        // Extra does not add to the streak itself.
        assertEquals(0, streak(h, ck(d("2026-11-17"))))
    }

    @Test fun `nothing counts before the creation date`() {
        val created = today.minusDays(1)
        // Check-ins exist before creation (e.g. imported); they are ignored.
        assertEquals(2, streak(habit(created = created), ck(today, today.minusDays(1), today.minusDays(2), today.minusDays(3)), created = created))
    }

    @Test fun `habit created today with nothing done has no streak`() {
        assertEquals(0, streak(habit(created = today), emptyList(), created = today))
    }

    @Test fun `creation date after today yields zero`() {
        assertEquals(0, streak(habit(), ck(today), created = today.plusDays(1)))
    }

    // --- times per week streak ---

    @Test fun `weekly streak counts met weeks and ignores an open current week`() {
        val h = habit(HabitScheduleType.TIMES_PER_WEEK, times = 2)
        val lastWeek = monday.minusWeeks(1)
        val before = monday.minusWeeks(2)
        val checkins = ck(lastWeek, lastWeek.plusDays(2), before, before.plusDays(1), monday) // current week: 1/2
        assertEquals(2, streak(h, checkins))
    }

    @Test fun `weekly streak includes the current week once met`() {
        val h = habit(HabitScheduleType.TIMES_PER_WEEK, times = 2)
        val lastWeek = monday.minusWeeks(1)
        assertEquals(2, streak(h, ck(lastWeek, lastWeek.plusDays(1), monday, monday.plusDays(1))))
    }

    @Test fun `weekly streak breaks on a closed week below target`() {
        val h = habit(HabitScheduleType.TIMES_PER_WEEK, times = 2)
        val twoAgo = monday.minusWeeks(2)
        // last week only 1/2 -> broken; the older met week no longer counts
        assertEquals(0, streak(h, ck(monday.minusWeeks(1), twoAgo, twoAgo.plusDays(1))))
    }

    @Test fun `weekly streak ignores check-ins before creation`() {
        val h = habit(HabitScheduleType.TIMES_PER_WEEK, times = 2, created = today)
        val lastWeek = monday.minusWeeks(1)
        assertEquals(0, streak(h, ck(lastWeek, lastWeek.plusDays(1)), created = today))
    }

    @Test fun `extra check-ins above weekly target still count as met`() {
        val h = habit(HabitScheduleType.TIMES_PER_WEEK, times = 1)
        val lastWeek = monday.minusWeeks(1)
        assertEquals(1, streak(h, ck(lastWeek, lastWeek.plusDays(1), lastWeek.plusDays(2))))
    }

    // --- dueToday ---

    @Test fun `dueToday filters archived and unscheduled and orders by position`() {
        val a = habit(id = "a", position = 2)
        val b = habit(id = "b", position = 1)
        val archived = habit(id = "c", archived = true)
        val notToday = habit(HabitScheduleType.DAYS_OF_WEEK, days = mwf, id = "d")
        val result = HabitRules.dueToday(listOf(a, archived, notToday, b), emptyList(), today, sp)
        assertEquals(listOf("b", "a"), result.map { it.habit.id })
    }

    @Test fun `dueToday reports doneToday progress and streak`() {
        val h = habit()
        val r = HabitRules.dueToday(listOf(h), ck(today, today.minusDays(1)), today, sp).single()
        assertTrue(r.doneToday)
        assertEquals(WeekProgress(2, 7), r.progress)
        assertEquals(2, r.streak)
    }

    @Test fun `times per week hides once the goal is met unless done today`() {
        val h = habit(HabitScheduleType.TIMES_PER_WEEK, times = 2)
        val metEarlier = ck(monday, monday.plusDays(1))
        assertTrue(HabitRules.dueToday(listOf(h), metEarlier, today, sp).isEmpty())
        val metToday = ck(monday, today)
        assertEquals(true, HabitRules.dueToday(listOf(h), metToday, today, sp).single().doneToday)
        val notMet = ck(monday)
        assertEquals(1, HabitRules.dueToday(listOf(h), notMet, today, sp).size)
    }

    @Test fun `dueToday uses the prorated goal of the creation week for times per week`() {
        // Created Saturday, goal 2 (sat + sun): one check-in leaves it visible on Sunday.
        val h = habit(HabitScheduleType.TIMES_PER_WEEK, times = 3, created = saturday)
        val r = HabitRules.dueToday(listOf(h), ck(saturday), sunday, sp).single()
        assertEquals(WeekProgress(1, 2), r.progress)
        // Goal met on Sunday and done today: still shown, marked as done.
        val met = HabitRules.dueToday(listOf(h), ck(saturday, sunday), sunday, sp).single()
        assertEquals(WeekProgress(2, 2), met.progress)
        assertTrue(met.doneToday)
        // Friday is before creation: it does not count toward the goal.
        assertEquals(WeekProgress(1, 2), HabitRules.dueToday(listOf(h), ck(saturday, friday), sunday, sp).single().progress)
    }

    @Test fun `dueToday uses the creation date in the given zone`() {
        // Created 2026-11-19 12:00 in Tokyo; in Sao Paulo that instant is still 2026-11-19 00:00, same day.
        val h = habit(created = today).copy(createdAt = LocalDateTime.of(2026, 11, 19, 2, 0).atZone(tokyo).toInstant().toEpochMilli())
        // In Tokyo it is the 19th; in Sao Paulo it is the 18th at 14:00, so a check-in on the 18th counts there.
        val onThe18th = ck(d("2026-11-18"))
        assertEquals(0, HabitRules.dueToday(listOf(h), onThe18th, today, tokyo).single().streak)
        assertEquals(1, HabitRules.dueToday(listOf(h), onThe18th, today, sp).single().streak)
        assertEquals(d("2026-11-19"), HabitRules.createdOn(h, tokyo))
        assertEquals(d("2026-11-18"), HabitRules.createdOn(h, sp))
    }

    // --- dates are independent of time zone ---

    @Test fun `stored check-in dates do not depend on the zone`() {
        val h = habit()
        val checkins = ck(today, today.minusDays(1))
        // Same LocalDate inputs give the same result regardless of the device zone used for createdAt.
        val inSp = HabitRules.dueToday(listOf(h), checkins, today, sp).single()
        val inTokyo = HabitRules.dueToday(listOf(h), checkins, today, tokyo).single()
        assertEquals(inSp.streak, inTokyo.streak)
        assertEquals(inSp.progress, inTokyo.progress)
        assertEquals(today, checkins.first().date)
    }
}
