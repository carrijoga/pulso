package dev.pulso.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class OrganizeRulesTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private fun at(day: Int, hour: Int, minute: Int = 0) =
        LocalDate.of(2026, 11, day).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
    private fun task(id: String = "1", due: Long? = null, done: Long? = null, reschedules: Int = 0) =
        TaskItem(id, "Tarefa $id", "", "Pessoal", Priority.NORMAL, due, done, 0, 0, rescheduleCount = reschedules)

    @Test fun `search ignores accents case and term order`() {
        val item = task().copy(title = "Comprar ração", notes = "Pet shop do bairro", links = "https://loja.example/racao")
        assertTrue(SearchRules.matchesTask("RACAO", item))
        assertTrue(SearchRules.matchesTask("bairro comprar", item))
        assertTrue(SearchRules.matchesTask("loja.example", item))
        assertFalse(SearchRules.matchesTask("ração gato", item))
        assertFalse(SearchRules.matchesTask("   ", item))
    }

    @Test fun `links are normalized one per line without duplicates`() {
        assertEquals("a.com\nb.com", TaskRules.normalizeLinks("  a.com \n\n b.com\na.com\n"))
        assertEquals("", TaskRules.normalizeLinks("  \n "))
        assertThrows(IllegalArgumentException::class.java) {
            TaskRules.normalizeLinks((1..21).joinToString("\n") { "l$it" })
        }
    }

    @Test fun `category names are trimmed and unique ignoring case and accents`() {
        val existing = listOf(Category("1", "Estudos"), Category("2", "Casa"))
        assertEquals("Viagem longa", CategoryRules.validateName("  Viagem   longa ", existing))
        assertThrows(IllegalArgumentException::class.java) { CategoryRules.validateName("estúdos", existing) }
        assertThrows(IllegalArgumentException::class.java) { CategoryRules.validateName("  ", existing) }
        // Renaming a category to a different casing of itself is allowed.
        assertEquals("ESTUDOS", CategoryRules.validateName("ESTUDOS", existing, ignoreId = "1"))
    }

    @Test fun `due labels are relative and pt-BR`() {
        val now = at(19, 10) // Thursday
        assertEquals(DueState.OVERDUE, TaskRules.dueLabel(at(19, 9), now, zone).state)
        assertEquals("Hoje 14:30", TaskRules.dueLabel(at(19, 14, 30), now, zone).text)
        assertEquals("Amanhã 09:00", TaskRules.dueLabel(at(20, 9), now, zone).text)
        assertTrue(TaskRules.dueLabel(at(23, 9), now, zone).text.startsWith("seg"))
        assertEquals("30/11/2026", TaskRules.dueLabel(at(30, 9), now, zone).text)
    }

    @Test fun `snooze overrides the reminder but not the due date`() {
        val item = task(due = at(19, 9)).copy(snoozedUntil = at(19, 10))
        assertEquals(at(19, 10), TaskRules.reminderTime(item))
        assertEquals(at(19, 9), TaskRules.reminderTime(item.copy(snoozedUntil = null)))
        assertNull(TaskRules.reminderTime(item.copy(completedAt = at(19, 9))))
    }

    @Test fun `quick reschedule counts from today and keeps time of day`() {
        val now = at(19, 15)
        val overdue = at(10, 18, 30)
        val tomorrow = TaskRules.rescheduleFromToday(overdue, 1, now, zone, LocalTime.of(9, 0))
        assertEquals(at(20, 18, 30), tomorrow)
        assertEquals(at(26, 9), TaskRules.rescheduleFromToday(null, 7, now, zone, LocalTime.of(9, 0)))
    }

    @Test fun `due time survives DST transitions`() {
        // Brazil has no DST since 2019; use a zone that still has it to exercise the gap.
        val ny = ZoneId.of("America/New_York")
        val due = TaskRules.dueAtFor(LocalDate.of(2026, 3, 7), LocalTime.of(9, 0), ny)
        val shifted = TaskRules.shiftDays(due, 1, ny)
        assertEquals(LocalTime.of(9, 0), Instant.ofEpochMilli(shifted).atZone(ny).toLocalTime())
    }

    @Test fun `weekly review buckets tasks`() {
        val now = at(19, 12)
        val tasks = listOf(
            task("late", due = at(17, 9), reschedules = 3),
            task("soon", due = at(22, 9)),
            task("far", due = at(30, 9)),
            task("open"),
            task("done", due = at(18, 9), done = at(18, 10)),
            task("oldDone", done = at(5, 10)),
        )
        val review = ReviewRules.build(tasks, now, zone)
        assertEquals(listOf("late"), review.overdue.map { it.id })
        assertEquals(listOf("soon"), review.nextSevenDays.map { it.id })
        assertEquals(listOf("open"), review.withoutDue.map { it.id })
        assertEquals(listOf("done"), review.completedLastSevenDays.map { it.id })
        assertEquals(listOf("late"), review.frequentlyRescheduled.map { it.id })
    }

    @Test fun `review is due weekly`() {
        val now = at(19, 12)
        assertTrue(ReviewRules.isReviewDue(null, now))
        assertFalse(ReviewRules.isReviewDue(at(15, 12), now))
        assertTrue(ReviewRules.isReviewDue(at(12, 11), now))
    }
}
