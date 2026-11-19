package dev.pulso.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pulso.app.domain.TaskDraft
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupServiceTest {
    private lateinit var source: PulsoDatabase
    private lateinit var target: PulsoDatabase
    private val clock = FakeClock()

    @Before fun setUp() { source = inMemoryDatabase(); target = inMemoryDatabase() }
    @After fun tearDown() { source.close(); target.close() }

    private fun task(id: String, title: String) = JSONObject().put("id", id).put("title", title).put("notes", "")
        .put("category", "Pessoal").put("priority", "NORMAL").put("dueAt", JSONObject.NULL)
        .put("completedAt", JSONObject.NULL).put("createdAt", 1).put("updatedAt", 1).put("deletedAt", JSONObject.NULL)

    @Test fun roundTripPreservesIdsStatesHistoryAndIsIdempotent() = runTest {
        val repo = LocalTaskRepository(source, { clock.millis }, RecordingListener())
        repo.capture("Arquivada")
        repo.archiveInbox(repo.observeInbox().first().single().id)
        repo.addCategory("Viagem")
        repo.createTask(TaskDraft("Passagem", category = "Viagem", dueAt = clock.millis + 10_000, links = "https://voo.example"))
        val id = repo.observeTasks().first().single().id
        repo.reschedule(id, clock.millis + 20_000)
        repo.createTask(TaskDraft("Excluída"))
        repo.deleteTask(repo.observeTasks().first().first { it.title == "Excluída" }.id)

        val json = BackupService(source, RecordingListener()).exportJson()
        val listener = RecordingListener()
        val importer = BackupService(target, listener)
        val first = importer.importJson(json)
        assertEquals(2, first.tasks)
        assertEquals(1, first.inbox)
        assertEquals(1, first.categories) // seeded defaults already exist on the target
        val second = importer.importJson(json)
        assertEquals(0, second.tasks + second.inbox + second.categories)

        val restored = LocalTaskRepository(target, { clock.millis }, RecordingListener())
        val task = restored.observeTasks().first().single()
        assertEquals(id, task.id)
        assertEquals("https://voo.example", task.links)
        assertEquals(1, task.rescheduleCount)
        assertEquals(2, target.taskDao().allForBackup().size) // tombstone kept
        assertEquals(1, restored.observeSearchableInbox().first().size)
        assertEquals(1, restored.observeCategories().first().count { it.name == "Viagem" })
        assertTrue(listener.calls.contains(id to clock.millis + 20_000))
    }

    @Test fun importsVersion1Files() = runTest {
        val v1 = JSONObject().put("format", "pulso-backup").put("version", 1)
            .put("inbox", JSONArray()).put("tasks", JSONArray().put(task("t1", "Antiga")))
        assertEquals(1, BackupService(target, RecordingListener()).importJson(v1.toString()).tasks)
        assertEquals("", target.taskDao().findById("t1")!!.links)
    }

    @Test fun invalidFilesAreRejectedBeforeAnyWrite() = runTest {
        val importer = BackupService(target, RecordingListener())
        val validInbox = JSONObject().put("id", "i1").put("text", "ok").put("status", "INBOX")
            .put("createdAt", 1).put("updatedAt", 1)
        val cases = listOf(
            "isto não é json",
            JSONObject().put("format", "outro").put("version", 1).put("inbox", JSONArray()).put("tasks", JSONArray()).toString(),
            JSONObject().put("format", "pulso-backup").put("version", 99).put("inbox", JSONArray()).put("tasks", JSONArray()).toString(),
            // Valid inbox row + invalid task: nothing may be written.
            JSONObject().put("format", "pulso-backup").put("version", 2).put("inbox", JSONArray().put(validInbox))
                .put("tasks", JSONArray().put(task("t1", "   "))).toString(),
        )
        cases.forEach { raw ->
            val failure = runCatching { importer.importJson(raw) }.exceptionOrNull()
            assertTrue("should reject: $raw", failure is IllegalArgumentException)
        }
        assertTrue(target.inboxDao().allForBackup().isEmpty())
        assertTrue(target.taskDao().allForBackup().isEmpty())
    }

    @Test fun importsThousandTasks() = runTest {
        val tasks = JSONArray()
        repeat(1000) { i -> tasks.put(task("t$i", "Tarefa $i")) }
        val raw = JSONObject().put("format", "pulso-backup").put("version", 2)
            .put("inbox", JSONArray()).put("tasks", tasks).toString()
        assertEquals(1000, BackupService(target, RecordingListener()).importJson(raw).tasks)
        assertEquals(1000, target.taskDao().allForBackup().size)
    }
}
