package dev.pulso.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pulso.app.domain.InboxStatus
import dev.pulso.app.domain.Priority
import dev.pulso.app.domain.TaskDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalTaskRepositoryTest {
    private lateinit var db: PulsoDatabase
    private lateinit var listener: RecordingListener
    private val clock = FakeClock()
    private lateinit var repo: LocalTaskRepository
    private val hour = 3_600_000L

    @Before fun setUp() {
        db = inMemoryDatabase()
        listener = RecordingListener()
        repo = LocalTaskRepository(db, { clock.millis }, listener)
    }

    @After fun tearDown() = db.close()

    @Test fun captureThenOrganizeIsTransactionalAndKeepsOrigin() = runTest {
        repo.capture("  Comprar ração  ")
        val inbox = repo.observeInbox().first().single()
        assertEquals("Comprar ração", inbox.text)

        repo.organizeInbox(inbox.id, TaskDraft("Comprar ração", category = "Compras", priority = Priority.HIGH))
        repo.organizeInbox(inbox.id, TaskDraft("De novo")) // second conversion must be a no-op

        val tasks = repo.observeTasks().first()
        assertEquals(1, tasks.size)
        val origin = db.inboxDao().findById(inbox.id)!!
        assertEquals(InboxStatus.CONVERTED.name, origin.status)
        assertEquals(tasks.single().id, origin.convertedTaskId)
        assertTrue(repo.observeInbox().first().isEmpty())
    }

    @Test fun archiveAndRestoreNeverDeleteCaptures() = runTest {
        repo.capture("Ideia")
        val id = repo.observeInbox().first().single().id
        repo.archiveInbox(id)
        assertTrue(repo.observeInbox().first().isEmpty())
        assertEquals(InboxStatus.ARCHIVED, repo.observeSearchableInbox().first().single().status)
        repo.restoreInbox(id)
        assertEquals(id, repo.observeInbox().first().single().id)
    }

    @Test fun editRecordsRescheduleOnlyBetweenRealDates() = runTest {
        repo.createTask(TaskDraft("Pagar luz"))
        val id = repo.observeTasks().first().single().id
        repo.editTask(id, TaskDraft("Pagar luz", dueAt = clock.millis + hour)) // first due date: not a reschedule
        clock.advance(1_000)
        repo.editTask(id, TaskDraft("Pagar luz", dueAt = clock.millis + 2 * hour,
            links = "https://a.example\n\nhttps://a.example"))
        repo.reschedule(id, clock.millis + 3 * hour)
        repo.editTask(id, TaskDraft("Pagar luz", dueAt = null)) // removing: not a reschedule

        val task = repo.observeTasks().first().single()
        assertEquals(2, task.rescheduleCount)
        assertEquals("", task.links) // last edit sent no links
        assertEquals(2, repo.observeRescheduleHistory(id).first().size)
        assertEquals(id to null, listener.calls.last())
    }

    @Test fun linksAreNormalizedOnSave() = runTest {
        repo.createTask(TaskDraft("Ler", links = " https://a.example \n\nhttps://a.example\nwww.b.example"))
        assertEquals("https://a.example\nwww.b.example", repo.observeTasks().first().single().links)
    }

    @Test fun snoozeMovesReminderAndCompletionClearsIt() = runTest {
        val due = clock.millis + hour
        repo.createTask(TaskDraft("Ligar", dueAt = due))
        val id = repo.observeTasks().first().single().id
        assertEquals(id to due, listener.calls.last())

        repo.snooze(id, due + hour)
        val snoozed = repo.observeTasks().first().single()
        assertEquals(due, snoozed.dueAt)
        assertEquals(due + hour, snoozed.snoozedUntil)
        assertEquals(id to due + hour, listener.calls.last())

        repo.setCompleted(id, true)
        assertNull(repo.observeTasks().first().single().snoozedUntil)
        assertEquals(id to null, listener.calls.last())

        repo.setCompleted(id, false)
        assertEquals(id to due, listener.calls.last())
    }

    @Test fun deleteIsTombstoneAndRestorable() = runTest {
        repo.createTask(TaskDraft("Temporária"))
        val id = repo.observeTasks().first().single().id
        repo.deleteTask(id)
        assertTrue(repo.observeTasks().first().isEmpty())
        assertNotNull(db.taskDao().findById(id)!!.deletedAt)
        repo.restoreTask(id)
        assertEquals(id, repo.observeTasks().first().single().id)
    }

    @Test fun categoriesAreSeededUniqueAndRenamePropagatesToTasks() = runTest {
        val seeded = repo.observeCategories().first().map { it.name }
        assertTrue(seeded.containsAll(listOf("Pessoal", "Compras", "Estudos", "Casa", "Trabalho")))

        repo.addCategory("Casamento")
        assertTrue(runCatching { repo.addCategory("casamento") }.isFailure)

        repo.createTask(TaskDraft("Provar bolo", category = "Casamento"))
        val category = repo.observeCategories().first().single { it.name == "Casamento" }
        repo.renameCategory(category.id, "Casório")
        assertEquals("Casório", repo.observeTasks().first().single().category)

        repo.deleteCategory(category.id)
        assertTrue(repo.observeCategories().first().none { it.id == category.id })
        assertEquals("Casório", repo.observeTasks().first().single().category) // tasks keep their text
    }
}
