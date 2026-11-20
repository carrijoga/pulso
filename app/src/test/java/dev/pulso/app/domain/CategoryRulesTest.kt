package dev.pulso.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CategoryRulesTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val now = at(20, 10)

    private fun at(day: Int, hour: Int) = LocalDate.of(2026, 11, day).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private fun task(id: String, category: String, due: Long? = null, done: Long? = null) =
        TaskItem(id, "Tarefa $id", "", category, Priority.NORMAL, due, done, 0, 0)

    @Test fun `overview groups one category by state, ignoring accents and case`() {
        val tasks = listOf(
            task("1", "Estúdos", due = at(18, 9)),
            task("2", "estudos", due = at(25, 9)),
            task("3", "ESTUDOS"),
            task("4", "Casa", due = at(18, 9)),
            task("5", "Estudos", due = at(30, 9), done = at(19, 9)),
            task("6", "Estudos", done = at(20, 8)),
        )
        val overview = CategoryRules.overview(tasks, "estudos", now)
        assertEquals(listOf("1"), overview.overdue.map { it.id })
        assertEquals(listOf("2"), overview.scheduled.map { it.id })
        assertEquals(listOf("3"), overview.withoutDue.map { it.id })
        // Newest completion first; the completed task with a due date still counts as history only.
        assertEquals(listOf("6", "5"), overview.completed.map { it.id })
        assertEquals(3, overview.activeCount)
    }

    @Test fun `overview of an empty category is empty`() {
        val overview = CategoryRules.overview(listOf(task("1", "Casa")), "Compras", now)
        assertEquals(0, overview.activeCount)
        assertEquals(emptyList<TaskItem>(), overview.completed)
    }

    @Test fun `suggestions skip categories that already exist`() {
        val existing = listOf(Category("1", "manutenção"), Category("2", "Casa"))
        assertEquals(listOf("Casamento", "Encomendas"), CategoryRules.suggestions(existing))
        assertEquals(CategoryRules.SUGGESTIONS, CategoryRules.suggestions(emptyList()))
    }
}
