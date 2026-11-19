package dev.pulso.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TaskRulesTest {
    @Test fun `capture trims whitespace`() {
        assertEquals("Comprar ração", TaskRules.validateCapture("  Comprar ração  "))
    }

    @Test fun `capture refuses empty text`() {
        assertThrows(IllegalArgumentException::class.java) { TaskRules.validateCapture("   ") }
    }

    @Test fun `draft applies default category`() {
        val clean = TaskRules.validateDraft(TaskDraft(title = "  Revisar xUnit ", category = "  "))
        assertEquals("Revisar xUnit", clean.title)
        assertEquals("Pessoal", clean.category)
    }

    @Test fun `today handles overdue in Sao Paulo`() {
        val zone = ZoneId.of("America/Sao_Paulo")
        val now = LocalDate.of(2026, 11, 19).atTime(14, 0).atZone(zone).toInstant().toEpochMilli()
        val past = LocalDate.of(2026, 11, 18).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val future = LocalDate.of(2026, 11, 20).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val item = TaskItem("1", "Prazo", "", "Pessoal", Priority.NORMAL, past, null, now, now)
        assertTrue(TaskRules.isDueTodayOrOverdue(item, now, zone))
        assertTrue(TaskRules.isOverdue(item, now))
        assertFalse(TaskRules.isDueTodayOrOverdue(item.copy(dueAt = future), now, zone))
        assertFalse(TaskRules.isDueTodayOrOverdue(item.copy(completedAt = now), now, zone))
    }

    @Test fun `due status buckets relative days`() {
        val zone = ZoneId.of("America/Sao_Paulo")
        fun at(day: Int, hour: Int) = LocalDate.of(2026, 11, day).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
        val now = at(19, 8)
        val base = TaskItem("1", "Prazo", "", "Pessoal", Priority.NORMAL, null, null, now, now)
        assertEquals(DueStatus.NONE, TaskRules.dueStatus(base, now, zone))
        assertEquals(DueStatus.OVERDUE, TaskRules.dueStatus(base.copy(dueAt = at(18, 9)), now, zone))
        assertEquals(DueStatus.TODAY, TaskRules.dueStatus(base.copy(dueAt = at(19, 9)), now, zone))
        assertEquals(DueStatus.TOMORROW, TaskRules.dueStatus(base.copy(dueAt = at(20, 9)), now, zone))
        assertEquals(DueStatus.THIS_WEEK, TaskRules.dueStatus(base.copy(dueAt = at(24, 9)), now, zone))
        assertEquals(DueStatus.LATER, TaskRules.dueStatus(base.copy(dueAt = at(28, 9)), now, zone))
    }

    @Test fun `agenda puts due tasks first and hides completed`() {
        val zone = ZoneId.of("America/Sao_Paulo")
        fun at(day: Int) = LocalDate.of(2026, 11, day).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val now = at(19) + 1
        fun task(id: String, due: Long?, done: Long? = null) =
            TaskItem(id, id, "", "Pessoal", Priority.NORMAL, due, done, now, now)
        val list = listOf(task("a", null), task("b", at(25)), task("c", at(19)), task("d", at(17)), task("e", at(18), done = now))
        assertEquals(listOf("d", "c", "a", "b"), TaskRules.agenda(list, now, zone).map { it.id })
    }
}
