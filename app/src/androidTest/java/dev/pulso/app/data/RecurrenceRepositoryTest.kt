package dev.pulso.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pulso.app.domain.InboxStatus
import dev.pulso.app.domain.Priority
import dev.pulso.app.domain.RecurrenceFrequency
import dev.pulso.app.domain.RecurrenceRule
import dev.pulso.app.domain.TaskDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

/** SPEC 008 repository behaviour. Runs on the emulator; compiled here, executed by the owner. */
@RunWith(AndroidJUnit4::class)
class RecurrenceRepositoryTest {
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

    private suspend fun openIds(): Set<String> =
        repo.observeTasks().first().filter { !it.isCompleted }.map { it.id }.toSet()

    @Test fun createRepeatingTaskStoresSeriesAndAnchor() = runTest {
        val due = at("2026-01-05T09:00")
        repo.createTask(TaskDraft("Pagar aluguel", dueAt = due, recurrence = RecurrenceRule(RecurrenceFrequency.MONTHLY)))
        val task = repo.observeTasks().first().single()
        assertNotNull(task.seriesId)
        assertEquals(RecurrenceRule(RecurrenceFrequency.MONTHLY), task.recurrence)
        assertEquals(due, task.recurrenceAnchorDueAt)
        assertEquals(task.id to due, listener.calls.last())
    }

    @Test fun completingCreatesExactlyOneNextOccurrenceAndIsIdempotent() = runTest {
        repo.createTask(TaskDraft("Trocar filtro", notes = "Modelo X", category = "Casa", priority = Priority.HIGH,
            dueAt = at("2026-01-05T09:00"), links = "https://a.example", recurrence = daily))
        val first = repo.observeTasks().first().single()

        clock.millis = at("2026-01-05T10:00")
        val next = repo.setCompleted(first.id, true)
        assertNotNull(next)
        assertEquals(at("2026-01-06T09:00"), next!!.dueAt)
        assertNull(repo.setCompleted(first.id, true)) // widget + notification completing twice
        assertNull(repo.setCompleted(first.id, true))

        val open = repo.observeTasks().first().filter { !it.isCompleted }
        assertEquals(1, open.size)
        val copy = open.single()
        assertEquals(next.id, copy.id)
        assertEquals("Trocar filtro", copy.title)
        assertEquals("Modelo X", copy.notes)
        assertEquals("Casa", copy.category)
        assertEquals(Priority.HIGH, copy.priority)
        assertEquals("https://a.example", copy.links)
        assertEquals(first.seriesId, copy.seriesId)
        assertEquals(listOf(first.id to null, next.id to next.dueAt), listener.calls.takeLast(2))
    }

    @Test fun completingCopiesChecklistUnchecked() = runTest {
        repo.createTask(TaskDraft("Manutenção", dueAt = at("2026-01-05T09:00"), recurrence = daily))
        val id = repo.observeTasks().first().single().id
        db.checklistDao().upsert(ChecklistItemEntity(id = "check-1", taskId = id, text = "Comprar filtro", position = 0,
            checkedAt = clock.millis, createdAt = clock.millis, updatedAt = clock.millis))

        clock.millis = at("2026-01-05T10:00")
        val next = repo.setCompleted(id, true)!!
        val copied = db.checklistDao().activeForTask(next.id)
        assertEquals(listOf("Comprar filtro"), copied.map { it.text })
        assertNull(copied.single().checkedAt)
        assertEquals(1, db.checklistDao().activeForTask(id).size) // the completed occurrence keeps its own items
    }

    @Test fun reopeningRemovesUntouchedNextButKeepsEditedOne() = runTest {
        repo.createTask(TaskDraft("Trocar filtro", dueAt = at("2026-01-05T09:00"), recurrence = daily))
        val id = repo.observeTasks().first().single().id

        clock.millis = at("2026-01-05T10:00")
        repo.setCompleted(id, true)
        repo.setCompleted(id, false) // undo
        assertEquals(setOf(id), openIds())

        clock.millis = at("2026-01-05T11:00")
        val next = repo.setCompleted(id, true)!!
        clock.millis = at("2026-01-05T11:30")
        repo.editTask(next.id, TaskDraft("Trocar filtro (modelo novo)", dueAt = next.dueAt, recurrence = daily))
        repo.setCompleted(id, false)
        assertEquals(setOf(id, next.id), openIds()) // edited next occurrence is never deleted
    }

    @Test fun endRecurrenceDeletesOccurrenceAndSeries() = runTest {
        repo.createTask(TaskDraft("Ler", dueAt = at("2026-01-05T09:00"), recurrence = daily))
        val id = repo.observeTasks().first().single().id
        repo.endRecurrence(id)

        assertTrue(repo.observeTasks().first().isEmpty())
        val task = db.taskDao().findById(id)!!
        assertNotNull(task.deletedAt)
        assertNotNull(db.seriesDao().findById(task.seriesId!!)!!.deletedAt)
        assertEquals(id to null, listener.calls.last())
    }

    @Test fun undoOfEndRecurrenceRestoresTaskAndSeries() = runTest {
        repo.createTask(TaskDraft("Ler", dueAt = at("2026-01-05T09:00"), recurrence = daily))
        val id = repo.observeTasks().first().single().id
        repo.endRecurrence(id)
        assertTrue(repo.observeTasks().first().isEmpty())

        repo.restoreEndedRecurrence(id) // "Desfazer"
        val restored = repo.observeTasks().first().single()
        assertEquals(id, restored.id)
        assertEquals(daily, restored.recurrence)
        assertNull(db.seriesDao().findById(restored.seriesId!!)!!.deletedAt)
        assertEquals(setOf(id), openIds())
    }

    @Test fun endRecurrenceIsRefusedForCompletedOccurrence() = runTest {
        repo.createTask(TaskDraft("Pagar", dueAt = at("2026-01-05T09:00"), recurrence = daily))
        val first = repo.observeTasks().first().single().id
        clock.millis = at("2026-01-05T10:00")
        val next = repo.setCompleted(first, true)!!.id

        assertTrue(runCatching { repo.endRecurrence(first) }.isFailure)
        assertEquals(setOf(next), openIds()) // the next occurrence and the series survive
        assertNull(db.seriesDao().findById(db.taskDao().findById(next)!!.seriesId!!)!!.deletedAt)
    }

    @Test fun repetitionCannotStartOnCompletedPlainTask() = runTest {
        repo.createTask(TaskDraft("Avulsa", dueAt = at("2026-01-05T09:00")))
        val id = repo.observeTasks().first().single().id
        clock.millis = at("2026-01-05T10:00")
        repo.setCompleted(id, true)

        assertTrue(runCatching { repo.editTask(id, TaskDraft("Avulsa", dueAt = at("2026-01-05T09:00"), recurrence = daily)) }
            .isFailure)
        assertNull(repo.observeTasks().first().single().recurrence)
        assertTrue(db.seriesDao().allForBackup().isEmpty())
    }

    @Test fun deleteOccurrenceCreatesNextAndUndoRemovesIt() = runTest {
        repo.createTask(TaskDraft("Pagar", dueAt = at("2026-01-05T09:00"), recurrence = daily))
        val id = repo.observeTasks().first().single().id

        clock.millis = at("2026-01-05T10:00")
        repo.deleteTask(id) // "Excluir só esta"
        val open = openIds()
        assertEquals(1, open.size)
        assertNotEquals(id, open.single())

        repo.restoreTask(id) // "Desfazer"
        assertEquals(setOf(id), openIds())
    }

    @Test fun deletingCompletedHistoryDoesNotCreateAnOccurrence() = runTest {
        repo.createTask(TaskDraft("Pagar", dueAt = at("2026-01-05T09:00"), recurrence = daily))
        val first = repo.observeTasks().first().single().id
        clock.millis = at("2026-01-05T10:00")
        val second = repo.setCompleted(first, true)!!.id
        clock.millis = at("2026-01-05T11:00")
        val third = repo.setCompleted(second, true)!!.id

        clock.millis = at("2026-01-05T12:00")
        repo.deleteTask(first) // old history: must not spawn a new occurrence
        assertEquals(setOf(third), openIds())
    }

    @Test fun removingRepetitionEndsSeriesAndKeepsPlainTask() = runTest {
        repo.createTask(TaskDraft("Ler", dueAt = at("2026-01-05T09:00"), recurrence = daily))
        val task = repo.observeTasks().first().single()
        repo.editTask(task.id, TaskDraft("Ler", dueAt = at("2026-01-05T09:00"), recurrence = null))

        val plain = repo.observeTasks().first().single()
        assertNull(plain.recurrence)
        assertNotNull(plain.seriesId) // link kept for history
        assertNull(repo.setCompleted(plain.id, true)) // ended series: completing creates nothing
        assertTrue(openIds().isEmpty())
    }

    @Test fun changingRuleRestartsSeriesAtCurrentDueDate() = runTest {
        repo.createTask(TaskDraft("Academia", dueAt = at("2026-01-05T09:00"), recurrence = daily))
        val id = repo.observeTasks().first().single().id
        clock.millis = at("2026-01-05T10:00")
        val next = repo.setCompleted(id, true)!!

        clock.millis = at("2026-01-06T10:00")
        val friday = RecurrenceRule(RecurrenceFrequency.WEEKLY, daysOfWeek = setOf(DayOfWeek.FRIDAY))
        repo.editTask(next.id, TaskDraft("Academia", dueAt = at("2026-01-09T09:00"), recurrence = friday))
        val edited = repo.observeTasks().first().single { it.id == next.id }
        assertEquals(friday, edited.recurrence)
        assertEquals(at("2026-01-09T09:00"), edited.recurrenceAnchorDueAt)

        clock.millis = at("2026-01-09T10:00")
        val after = repo.setCompleted(next.id, true)!!
        assertEquals(at("2026-01-16T09:00"), after.dueAt)
    }

    @Test fun repeatingTaskWithoutDueDateIsRefused() = runTest {
        assertTrue(runCatching { repo.createTask(TaskDraft("Sem prazo", recurrence = daily)) }.isFailure)
        assertTrue(repo.observeTasks().first().isEmpty())
        assertTrue(db.seriesDao().allForBackup().isEmpty())
    }

    @Test fun completedOccurrenceCannotChangeItsRule() = runTest {
        repo.createTask(TaskDraft("Pagar", dueAt = at("2026-01-05T09:00"), recurrence = daily))
        val id = repo.observeTasks().first().single().id
        clock.millis = at("2026-01-05T10:00")
        repo.setCompleted(id, true)
        val monthly = RecurrenceRule(RecurrenceFrequency.MONTHLY)
        assertTrue(runCatching { repo.editTask(id, TaskDraft("Pagar", dueAt = at("2026-01-05T09:00"), recurrence = monthly)) }
            .isFailure)
    }

    @Test fun organizingRepeatingCaptureConvertsOnceAndCreatesOneSeries() = runTest {
        repo.capture("Pagar luz")
        val inbox = repo.observeInbox().first().single()
        val draft = TaskDraft("Pagar luz", dueAt = at("2026-01-05T09:00"),
            recurrence = RecurrenceRule(RecurrenceFrequency.MONTHLY))
        repo.organizeInbox(inbox.id, draft)
        repo.organizeInbox(inbox.id, draft) // second conversion is a no-op

        val tasks = repo.observeTasks().first()
        assertEquals(1, tasks.size)
        assertEquals(RecurrenceRule(RecurrenceFrequency.MONTHLY), tasks.single().recurrence)
        val origin = db.inboxDao().findById(inbox.id)!!
        assertEquals(InboxStatus.CONVERTED.name, origin.status)
        assertEquals(tasks.single().id, origin.convertedTaskId)
        assertEquals(1, db.seriesDao().allForBackup().size)
    }
}
