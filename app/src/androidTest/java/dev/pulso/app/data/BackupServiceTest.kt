package dev.pulso.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
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

    private fun fixture(name: String): String = InstrumentationRegistry.getInstrumentation().context
        .assets.open("backups/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun now() = 1_700_000_000_000L
    private fun seed(db: PulsoDatabase) = runBlocking {
        db.seriesDao().upsert(SeriesEntity("s1", "WEEKLY", 2, 21, "SCHEDULE", now(), now(), now()))
        db.taskDao().upsert(TaskEntity("t1", "Recorrente", "", "Casa", "NORMAL", now(), null, now(), now(), seriesId = "s1"))
        db.habitDao().upsert(HabitEntity("h1", "Meditar", scheduleType = "DAYS_OF_WEEK", daysOfWeek = 5, createdAt = now(), updatedAt = now()))
        db.habitCheckinDao().upsert(HabitCheckinEntity("c1", "h1", "2026-01-05", now(), now()))
        db.habitCheckinDao().upsert(HabitCheckinEntity("c2", "h1", "2026-01-06", now(), now(), deletedAt = now()))
        db.checklistDao().upsert(ChecklistItemEntity("k1", "t1", "Item", 0, now(), now(), now()))
    }

    @Test fun v3RoundTripCarriesSeriesHabitsCheckinsAndChecklistAndIsIdempotent() = runTest {
        seed(source)
        val json = BackupService(source, RecordingListener(), appVersionName = "9.9").exportJson()
        val header = JSONObject(json)
        assertEquals(3, header.getInt("version")); assertEquals(3, header.getInt("schemaVersion"))
        assertEquals("9.9", header.getString("appVersionName")); assertTrue(header.has("exportedAt"))

        val importer = BackupService(target, RecordingListener())
        val first = importer.importJson(json)
        assertEquals(1, first.habits); assertEquals(1, first.series); assertEquals(2, first.checkins); assertEquals(1, first.checklistItems)
        assertEquals("s1", target.taskDao().findById("t1")!!.seriesId)
        assertEquals(5, target.habitDao().findById("h1")!!.daysOfWeek)
        assertEquals(2, target.habitCheckinDao().allForBackup().size) // tombstone kept
        assertEquals("Item", target.checklistDao().findById("k1")!!.text)
        val second = importer.importJson(json)
        assertEquals(0, second.tasks + second.habits + second.series + second.checkins + second.checklistItems)
        assertEquals(2, target.habitCheckinDao().allForBackup().size)
    }

    @Test fun importsFixturesV1V2AndV3IntoAV3Database() = runTest {
        val importer = BackupService(target, RecordingListener())
        assertEquals(1, importer.importJson(fixture("backup-v1.json")).tasks)
        val v2 = importer.importJson(fixture("backup-v2.json"))
        assertEquals(2, v2.tasks); assertEquals(2, v2.inbox); assertEquals(1, v2.categories)
        assertNull(target.taskDao().findById("22222222-0000-4000-8000-0000000000a1")!!.seriesId)
        val v3 = importer.importJson(fixture("backup-v3.json"))
        assertEquals(2, v3.tasks); assertEquals(2, v3.habits); assertEquals(1, v3.series)
        assertEquals(2, v3.checkins); assertEquals(2, v3.checklistItems)
        assertEquals(5, target.taskDao().allForBackup().size)
    }

    @Test fun versionNewerThanSupportedIsRejectedWithSpecificMessageAndWritesNothing() = runTest {
        val raw = JSONObject(fixture("backup-v3.json")).put("version", 4).toString()
        val failure = runCatching { BackupService(target, RecordingListener()).importJson(raw) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure!!.message!!.contains("versão mais nova"))
        assertTrue(target.taskDao().allForBackup().isEmpty() && target.habitDao().allForBackup().isEmpty())
    }

    @Test fun missingReferencesAreRejectedWithoutWriting() = runTest {
        val base = JSONObject(fixture("backup-v3.json"))
        val cases = listOf(
            JSONObject(base.toString()).also { it.getJSONArray("series").remove(0) },
            JSONObject(base.toString()).also { it.getJSONArray("habits").remove(0) },
            JSONObject(base.toString()).also { it.getJSONArray("checklistItems").getJSONObject(0).put("taskId", "nao-existe") },
        )
        cases.forEach { json ->
            val failure = runCatching { BackupService(target, RecordingListener()).importJson(json.toString()) }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
            assertTrue(failure!!.message!!.contains("referência ausente"))
        }
        assertTrue(target.taskDao().allForBackup().isEmpty())
        assertTrue(target.seriesDao().allForBackup().isEmpty())
        assertTrue(target.habitDao().allForBackup().isEmpty())
        assertTrue(target.habitCheckinDao().allForBackup().isEmpty())
        assertTrue(target.checklistDao().allForBackup().isEmpty())
    }

    @Test fun invalidV3FieldsAreRejected() = runTest {
        fun mutate(path: String, edit: (JSONObject) -> Unit): String {
            val json = JSONObject(fixture("backup-v3.json")); edit(json.getJSONArray(path).getJSONObject(0)); return json.toString()
        }
        val cases = listOf(
            mutate("series") { it.put("frequency", "HOURLY") },
            mutate("series") { it.put("repeatInterval", 100) },
            mutate("series") { it.put("daysOfWeek", 0) }, // WEEKLY needs a weekday
            mutate("series") { it.put("daysOfWeek", 128) },
            mutate("habits") { it.put("scheduleType", "TIMES_PER_WEEK").put("timesPerWeek", 8) },
            mutate("habits") { it.put("name", "x".repeat(61)) },
            mutate("habitCheckins") { it.put("date", "14/11/2023") },
            mutate("checklistItems") { it.put("text", "x".repeat(201)) },
        )
        cases.forEach { raw ->
            assertTrue("should reject: $raw", runCatching { BackupService(target, RecordingListener()).importJson(raw) }.exceptionOrNull() is IllegalArgumentException)
        }
        assertTrue(target.habitDao().allForBackup().isEmpty() && target.seriesDao().allForBackup().isEmpty())
    }

    @Test fun duplicateCheckinForSameHabitAndDayWithDifferentUuidKeepsLocalRow() = runTest {
        target.habitDao().upsert(HabitEntity("h1", "Meditar", scheduleType = "DAILY", createdAt = 1, updatedAt = 1))
        target.habitCheckinDao().upsert(HabitCheckinEntity("local", "h1", "2026-01-05", 1, 1))
        val json = JSONObject(fixture("backup-v3.json"))
        json.getJSONArray("habits").getJSONObject(0).put("id", "h1")
        json.getJSONArray("habitCheckins").getJSONObject(0).put("habitId", "h1").put("date", "2026-01-05").put("id", "remote")
        json.getJSONArray("habitCheckins").remove(1)
        val summary = BackupService(target, RecordingListener()).importJson(json.toString())
        assertEquals(0, summary.checkins)
        val rows = target.habitCheckinDao().allForBackup()
        assertEquals(1, rows.size); assertEquals("local", rows.single().id)
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
