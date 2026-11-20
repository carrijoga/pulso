package dev.pulso.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pulso.app.domain.RecurrenceFrequency
import dev.pulso.app.domain.RecurrenceRule
import dev.pulso.app.domain.TaskDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * SPEC 015 subtasks at the repository level (ADR 0009). Compiled in the WP6 session; execution on the
 * emulator is pending.
 */
@RunWith(AndroidJUnit4::class)
class SubtaskRepositoryTest {
    private lateinit var db: PulsoDatabase
    private lateinit var listener: RecordingListener
    private lateinit var clock: FakeClock
    private lateinit var repo: LocalTaskRepository
    private val sp = ZoneId.of("America/Sao_Paulo")
    private val daily = RecurrenceRule(RecurrenceFrequency.DAILY)

    private fun at(local: String): Long = LocalDateTime.parse(local).atZone(sp).toInstant().toEpochMilli()

    @Before fun setUp() {
        db = inMemoryDatabase()
        listener = RecordingListener()
        clock = FakeClock(at("2026-01-05T08:00"))
        repo = LocalTaskRepository(db, { clock.millis }, listener) { sp }
    }

    @After fun tearDown() = db.close()

    private suspend fun all() = repo.observeTasks().first()
    private suspend fun idOf(title: String): String = all().single { it.title == title }.id

    /** The exception a refused write throws, or null. */
    private suspend fun failureOf(block: suspend () -> Unit): Throwable? = runCatching { block() }.exceptionOrNull()

    @Test fun completingMotherWithoutSubtasksLeavesThemOpen() = runTest {
        repo.createTask(TaskDraft("Casamento"))
        val mother = idOf("Casamento")
        repo.createTask(TaskDraft("Buffet", parentId = mother))
        repo.setCompleted(mother, true)
        assertTrue(all().single { it.title == "Casamento" }.isCompleted)
        assertFalse(all().single { it.title == "Buffet" }.isCompleted)
    }

    @Test fun completingMotherWithSubtasksCompletesThemTogetherAndUndoReopensOnlyThose() = runTest {
        repo.createTask(TaskDraft("Casamento"))
        val mother = idOf("Casamento")
        repo.createTask(TaskDraft("Flores"))
        repo.createTask(TaskDraft("Buffet", parentId = mother))
        repo.createTask(TaskDraft("Convites", parentId = mother))
        val flores = idOf("Flores")
        repo.editTask(flores, TaskDraft("Flores", parentId = mother))
        clock.advance(1_000)
        repo.setCompleted(flores, true) // completed on its own, before the mother
        clock.advance(1_000)
        repo.setCompleted(mother, true, completeSubtasks = true)
        assertTrue(all().single { it.title == "Buffet" }.isCompleted)
        assertTrue(all().single { it.title == "Convites" }.isCompleted)

        repo.setCompleted(mother, false) // undo of the mother
        assertFalse(all().single { it.title == "Casamento" }.isCompleted)
        assertFalse(all().single { it.title == "Buffet" }.isCompleted)
        assertFalse(all().single { it.title == "Convites" }.isCompleted)
        assertTrue("completed on its own stays completed", all().single { it.title == "Flores" }.isCompleted)
    }

    @Test fun progressAndMotherTitleComeFromTheSameList() = runTest {
        repo.createTask(TaskDraft("Casamento"))
        val mother = idOf("Casamento")
        repo.createTask(TaskDraft("Buffet", parentId = mother))
        repo.createTask(TaskDraft("Convites", parentId = mother))
        repo.setCompleted(idOf("Buffet"), true)
        val rows = all()
        val parent = rows.single { it.title == "Casamento" }
        assertEquals(2, parent.subtaskTotal)
        assertEquals(1, parent.subtaskDone)
        assertEquals("Casamento", rows.single { it.title == "Convites" }.parentTitle)
    }

    @Test fun subtaskOfASubtaskIsRefusedWithAClearMessage() = runTest {
        repo.createTask(TaskDraft("Casamento"))
        val mother = idOf("Casamento")
        repo.createTask(TaskDraft("Buffet", parentId = mother))
        val child = idOf("Buffet")
        val failure = failureOf { repo.createTask(TaskDraft("Bolo", parentId = child)) }
        assertTrue(failure is IllegalArgumentException)
        assertEquals("Subtarefas só podem ter um nível: escolha uma tarefa principal.", failure!!.message)
    }

    @Test fun taskWithSubtasksCannotBecomeASubtask() = runTest {
        repo.createTask(TaskDraft("Casamento"))
        repo.createTask(TaskDraft("Outra"))
        val mother = idOf("Casamento")
        repo.createTask(TaskDraft("Buffet", parentId = mother))
        val failure = failureOf { repo.editTask(mother, TaskDraft("Casamento", parentId = idOf("Outra"))) }
        assertEquals("Tarefa com subtarefas não pode virar subtarefa.", failure?.message)
    }

    @Test fun subtaskCannotRepeatAndCannotBecomeRepeating() = runTest {
        repo.createTask(TaskDraft("Casamento"))
        val mother = idOf("Casamento")
        val created = failureOf {
            repo.createTask(TaskDraft("Bolo", dueAt = at("2026-01-06T10:00"), parentId = mother, recurrence = daily))
        }
        assertEquals("Tarefa repetida não pode ser subtarefa. Remova a repetição antes.", created?.message)

        repo.createTask(TaskDraft("Buffet", parentId = mother))
        val child = idOf("Buffet")
        val edited = failureOf {
            repo.editTask(child, TaskDraft("Buffet", dueAt = at("2026-01-06T10:00"), parentId = mother, recurrence = daily))
        }
        assertEquals("Tarefa repetida não pode ser subtarefa. Remova a repetição antes.", edited?.message)
        assertNull(all().single { it.title == "Buffet" }.recurrence)
    }

    @Test fun repeatingMotherAcceptsSubtasksAndEachNextOccurrenceGetsOpenCopies() = runTest {
        repo.createTask(TaskDraft("Trocar filtro", dueAt = at("2026-01-05T09:00"), recurrence = daily))
        val mother = idOf("Trocar filtro")
        // Subtask two days after the mother, at 10:00 local, with its own category, link and checklist.
        repo.createTask(TaskDraft("Comprar filtro", dueAt = at("2026-01-07T10:00"), category = "Casa",
            links = "https://loja.example", checklist = listOf("medir"), parentId = mother))
        val original = idOf("Comprar filtro")

        val next = repo.setCompleted(mother, true)
        assertNotNull(next)
        val copies = all().filter { it.parentId == next!!.id }
        assertEquals(1, copies.size)
        val copy = copies.single()
        assertNotEquals(original, copy.id)
        assertFalse(copy.isCompleted)
        assertEquals("Comprar filtro", copy.title)
        assertEquals("Casa", copy.category)
        assertEquals("https://loja.example", copy.links)
        // Next mother is 2026-01-06 09:00, so the copy keeps "two days after" at 10:00: 2026-01-08 10:00.
        assertEquals(at("2026-01-08T10:00"), copy.dueAt)
        assertEquals(listOf("medir"), copy.checklist.map { it.text })
        assertFalse(copy.checklist.single().checked)
        assertTrue(listener.calls.any { it.first == copy.id })
        // The original subtask stays with the finished occurrence.
        assertEquals(mother, all().single { it.id == original }.parentId)
    }

    @Test fun undoingTheCompletionRemovesTheUntouchedNextOccurrenceAndItsCopies() = runTest {
        repo.createTask(TaskDraft("Trocar filtro", dueAt = at("2026-01-05T09:00"), recurrence = daily))
        val mother = idOf("Trocar filtro")
        repo.createTask(TaskDraft("Comprar filtro", dueAt = at("2026-01-07T10:00"), parentId = mother))
        val next = repo.setCompleted(mother, true)!!.id
        repo.setCompleted(mother, false)
        assertNull(all().firstOrNull { it.id == next })
        assertTrue(all().none { it.parentId == next })
        assertFalse(all().single { it.id == mother }.isCompleted)
        assertNotNull(all().single { it.title == "Comprar filtro" }) // the original subtask is untouched
    }

    @Test fun undoKeepsTheNextOccurrenceWhenACopyWasEdited() = runTest {
        repo.createTask(TaskDraft("Trocar filtro", dueAt = at("2026-01-05T09:00"), recurrence = daily))
        val mother = idOf("Trocar filtro")
        repo.createTask(TaskDraft("Comprar filtro", dueAt = at("2026-01-07T10:00"), parentId = mother))
        val next = repo.setCompleted(mother, true)!!.id
        val copy = all().single { it.parentId == next }
        clock.advance(60_000)
        repo.editTask(copy.id, TaskDraft("Comprar filtro novo", dueAt = copy.dueAt, parentId = next))

        repo.setCompleted(mother, false)
        // Nothing the user wrote is removed: the next occurrence and its edited copy stay.
        assertNotNull(all().firstOrNull { it.id == next })
        assertEquals("Comprar filtro novo", all().single { it.id == copy.id }.title)
    }

    @Test fun deletingTheMotherRemovesSubtasksAndUndoRestoresOnlyThoseRemovedWithIt() = runTest {
        repo.createTask(TaskDraft("Casamento"))
        val mother = idOf("Casamento")
        repo.createTask(TaskDraft("Buffet", parentId = mother))
        repo.createTask(TaskDraft("Convites", parentId = mother))
        repo.createTask(TaskDraft("Flores", parentId = mother))
        clock.advance(1_000)
        repo.deleteTask(idOf("Flores")) // deleted on its own, before the mother
        clock.advance(1_000)
        repo.deleteTask(mother)
        assertTrue(all().isEmpty())

        repo.restoreTask(mother)
        assertEquals(setOf("Casamento", "Buffet", "Convites"), all().map { it.title }.toSet())
        assertNotNull(db.taskDao().allForBackup().single { it.title == "Flores" }.deletedAt)
    }

    @Test fun endingTheRepetitionOfAMotherRemovesSubtasksAndUndoRestoresThem() = runTest {
        repo.createTask(TaskDraft("Trocar filtro", dueAt = at("2026-01-05T09:00"), recurrence = daily))
        val mother = idOf("Trocar filtro")
        repo.createTask(TaskDraft("Comprar filtro", dueAt = at("2026-01-07T10:00"), parentId = mother))
        repo.endRecurrence(mother)
        assertTrue(all().isEmpty())

        repo.restoreEndedRecurrence(mother)
        assertEquals(setOf("Trocar filtro", "Comprar filtro"), all().map { it.title }.toSet())
        assertNotNull(all().single { it.title == "Trocar filtro" }.recurrence)
    }

    @Test fun subtaskWithoutDueDateIsCopiedWithoutDueDate() = runTest {
        repo.createTask(TaskDraft("Trocar filtro", dueAt = at("2026-01-05T09:00"), recurrence = daily))
        val mother = idOf("Trocar filtro")
        repo.createTask(TaskDraft("Pesquisar modelo", parentId = mother))
        val next = repo.setCompleted(mother, true)!!.id
        val copy = all().single { it.parentId == next }
        assertEquals("Pesquisar modelo", copy.title)
        assertNull(copy.dueAt)
        assertFalse(copy.isCompleted)
    }

    @Test fun detachingASubtaskMakesItIndependent() = runTest {
        repo.createTask(TaskDraft("Casamento"))
        val mother = idOf("Casamento")
        repo.createTask(TaskDraft("Buffet", dueAt = at("2026-01-09T12:00"), parentId = mother))
        val child = all().single { it.title == "Buffet" }
        repo.editTask(child.id, TaskDraft("Buffet", dueAt = child.dueAt, parentId = null))
        val after = all().single { it.title == "Buffet" }
        assertNull(after.parentId)
        assertEquals(at("2026-01-09T12:00"), after.dueAt)
        assertEquals(0, all().single { it.title == "Casamento" }.subtaskTotal)
    }
}
