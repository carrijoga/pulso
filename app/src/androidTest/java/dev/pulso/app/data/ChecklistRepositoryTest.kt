package dev.pulso.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pulso.app.domain.RecurrenceFrequency
import dev.pulso.app.domain.RecurrenceRule
import dev.pulso.app.domain.TaskDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.time.ZoneId

/** SPEC 013 checklist repository behaviour. Compiled in the WP5 session; execution is pending on the emulator. */
@RunWith(AndroidJUnit4::class)
class ChecklistRepositoryTest {
    private lateinit var db: PulsoDatabase
    private lateinit var listener: RecordingListener
    private lateinit var clock: FakeClock
    private lateinit var repo: LocalTaskRepository
    private val sp = ZoneId.of("America/Sao_Paulo")

    @Before fun setUp() {
        db = inMemoryDatabase()
        listener = RecordingListener()
        clock = FakeClock(LocalDateTime.parse("2026-01-05T08:00").atZone(sp).toInstant().toEpochMilli())
        repo = LocalTaskRepository(db, { clock.millis }, listener) { sp }
    }

    @After fun tearDown() = db.close()

    private suspend fun onlyTask() = repo.observeTasks().first().single()

    @Test fun addEditCheckMoveDeleteAndRestoreKeepOrderAndTouchTheTask() = runTest {
        repo.createTask(TaskDraft("Mercado", checklist = listOf("arroz", "café")))
        val task = onlyTask()
        assertEquals(listOf("arroz", "café"), task.checklist.map { it.text })

        clock.advance(1_000)
        repo.addChecklistItem(task.id, "  leite ")
        val afterAdd = onlyTask()
        assertEquals(listOf("arroz", "café", "leite"), afterAdd.checklist.map { it.text })
        assertEquals(clock.millis, afterAdd.updatedAt)

        repo.setChecklistItemChecked(afterAdd.checklist[0].id, true)
        assertTrue(onlyTask().checklist[0].checked)

        repo.moveChecklistItem(afterAdd.checklist[2].id, 0)
        assertEquals(listOf("leite", "arroz", "café"), onlyTask().checklist.map { it.text })

        val cafe = onlyTask().checklist.first { it.text == "café" }.id
        repo.deleteChecklistItem(cafe)
        assertEquals(listOf("leite", "arroz"), onlyTask().checklist.map { it.text })
        repo.restoreChecklistItem(cafe)
        assertEquals(listOf("leite", "arroz", "café"), onlyTask().checklist.map { it.text })

        repo.editChecklistItem(cafe, "café moído")
        assertEquals("café moído", onlyTask().checklist[2].text)
        assertTrue(listener.calls.isNotEmpty())
    }

    @Test fun checkingTheSameStateTwiceChangesNothing() = runTest {
        repo.createTask(TaskDraft("Mercado", checklist = listOf("arroz")))
        val task = onlyTask()
        val id = task.checklist.single().id
        repo.setChecklistItemChecked(id, true)
        val changedAt = onlyTask().updatedAt
        val calls = listener.calls.size
        clock.advance(500)
        repo.setChecklistItemChecked(id, true)
        assertEquals(calls, listener.calls.size)
        assertEquals(changedAt, onlyTask().updatedAt)
    }

    @Test fun limitAndBlankTextAreRefusedWithoutWriting() = runTest {
        repo.createTask(TaskDraft("Mercado", checklist = List(100) { "item $it" }))
        val id = onlyTask().id
        var refusedFull = false
        try { repo.addChecklistItem(id, "extra") } catch (_: IllegalArgumentException) { refusedFull = true }
        assertTrue(refusedFull)
        var refusedBlank = false
        try { repo.addChecklistItem(id, "   ") } catch (_: IllegalArgumentException) { refusedBlank = true }
        assertTrue(refusedBlank)
        assertEquals(100, onlyTask().checklist.size)
    }

    @Test fun itemsOfDeletedTaskAreNotWritten() = runTest {
        repo.createTask(TaskDraft("Mercado"))
        val id = onlyTask().id
        repo.deleteTask(id)
        repo.addChecklistItem(id, "arroz")
        assertEquals(0, db.checklistDao().allForBackup().size)
    }

    @Test fun nextOccurrenceCopiesTheChecklistWithEveryItemUnchecked() = runTest {
        repo.createTask(TaskDraft("Trocar filtro", dueAt = clock.millis,
            recurrence = RecurrenceRule(RecurrenceFrequency.DAILY), checklist = listOf("filtro", "parafuso")))
        val first = onlyTask()
        repo.setChecklistItemChecked(first.checklist[0].id, true)
        repo.setCompleted(first.id, true)
        val next = repo.observeTasks().first().single { !it.isCompleted }
        assertEquals(listOf("filtro", "parafuso"), next.checklist.map { it.text })
        assertEquals(listOf(false, false), next.checklist.map { it.checked })
    }
}
