package dev.pulso.app.data

import androidx.room.withTransaction
import dev.pulso.app.domain.DaysOfWeekMask
import dev.pulso.app.domain.HabitScheduleType
import dev.pulso.app.domain.RecurrenceBasis
import dev.pulso.app.domain.RecurrenceFrequency
import dev.pulso.app.domain.SearchRules
import dev.pulso.app.domain.SubtaskLink
import dev.pulso.app.domain.SubtaskRules
import dev.pulso.app.domain.TaskRules
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.DateTimeException
import java.time.LocalDate

/** Result of an additive import: how many new rows were written (existing UUIDs are skipped). */
data class ImportSummary(
    val inbox: Int,
    val tasks: Int,
    val categories: Int,
    val habits: Int = 0,
    val series: Int = 0,
    val checkins: Int = 0,
    val checklistItems: Int = 0,
) {
    private val total: Int get() = inbox + tasks + categories + habits + series + checkins + checklistItems
    val message: String get() = if (total == 0) "Nada novo para importar (tudo já existia)."
        else "Importados: $tasks tarefas, $inbox anotações, $categories categorias, $habits hábitos. Duplicados ignorados."
}

/**
 * Portable local backup. Import is additive (same UUIDs are skipped), never destructive.
 * Version 2 adds task links/snooze, categories and reschedule history; version 3 adds recurrence series,
 * habits, check-ins and checklist items; version 4 adds the subtask link `parentId` (SPEC 015).
 * Versions 1 to 3 still import (no `parentId`, so every task is independent).
 */
class BackupService(
    private val database: PulsoDatabase,
    private val changeListener: TaskChangeListener,
    private val appVersionName: String = "",
) {
    suspend fun exportJson(): String {
        val inbox = database.inboxDao().allForBackup()
        val tasks = database.taskDao().allForBackup()
        val categories = database.categoryDao().allForBackup()
        val reschedules = database.rescheduleDao().allForBackup()
        val series = database.seriesDao().allForBackup()
        val habits = database.habitDao().allForBackup()
        val checkins = database.habitCheckinDao().allForBackup()
        val checklist = database.checklistDao().allForBackup()
        return JSONObject()
            .put("format", FORMAT)
            .put("version", CURRENT_VERSION)
            .put("exportedAt", System.currentTimeMillis())
            .put("appVersionName", appVersionName)
            .put("schemaVersion", CURRENT_VERSION)
            .put("inbox", JSONArray().also { a -> inbox.forEach { x ->
                a.put(JSONObject().put("id", x.id).put("text", x.text).put("status", x.status)
                    .put("createdAt", x.createdAt).put("updatedAt", x.updatedAt)
                    .put("convertedTaskId", nullable(x.convertedTaskId)).put("deletedAt", nullable(x.deletedAt)))
            } })
            .put("tasks", JSONArray().also { a -> tasks.forEach { x ->
                a.put(JSONObject().put("id", x.id).put("title", x.title).put("notes", x.notes)
                    .put("category", x.category).put("priority", x.priority)
                    .put("dueAt", nullable(x.dueAt)).put("completedAt", nullable(x.completedAt))
                    .put("createdAt", x.createdAt).put("updatedAt", x.updatedAt)
                    .put("deletedAt", nullable(x.deletedAt)).put("links", x.links)
                    .put("snoozedUntil", nullable(x.snoozedUntil)).put("seriesId", nullable(x.seriesId))
                    .put("parentId", nullable(x.parentId)))
            } })
            .put("categories", JSONArray().also { a -> categories.forEach { x ->
                a.put(JSONObject().put("id", x.id).put("name", x.name).put("createdAt", x.createdAt)
                    .put("updatedAt", x.updatedAt).put("deletedAt", nullable(x.deletedAt)))
            } })
            .put("reschedules", JSONArray().also { a -> reschedules.forEach { x ->
                a.put(JSONObject().put("id", x.id).put("taskId", x.taskId).put("fromDueAt", x.fromDueAt)
                    .put("toDueAt", x.toDueAt).put("changedAt", x.changedAt))
            } })
            .put("series", JSONArray().also { a -> series.forEach { x ->
                a.put(JSONObject().put("id", x.id).put("frequency", x.frequency).put("repeatInterval", x.repeatInterval)
                    .put("daysOfWeek", x.daysOfWeek).put("basis", x.basis).put("anchorDueAt", x.anchorDueAt)
                    .put("createdAt", x.createdAt).put("updatedAt", x.updatedAt).put("deletedAt", nullable(x.deletedAt)))
            } })
            .put("habits", JSONArray().also { a -> habits.forEach { x ->
                a.put(JSONObject().put("id", x.id).put("name", x.name).put("notes", x.notes)
                    .put("scheduleType", x.scheduleType).put("daysOfWeek", x.daysOfWeek)
                    .put("timesPerWeek", x.timesPerWeek).put("reminderMinutes", nullable(x.reminderMinutes))
                    .put("position", x.position).put("archivedAt", nullable(x.archivedAt))
                    .put("createdAt", x.createdAt).put("updatedAt", x.updatedAt).put("deletedAt", nullable(x.deletedAt)))
            } })
            .put("habitCheckins", JSONArray().also { a -> checkins.forEach { x ->
                a.put(JSONObject().put("id", x.id).put("habitId", x.habitId).put("date", x.date)
                    .put("createdAt", x.createdAt).put("updatedAt", x.updatedAt).put("deletedAt", nullable(x.deletedAt)))
            } })
            .put("checklistItems", JSONArray().also { a -> checklist.forEach { x ->
                a.put(JSONObject().put("id", x.id).put("taskId", x.taskId).put("text", x.text)
                    .put("position", x.position).put("checkedAt", nullable(x.checkedAt))
                    .put("createdAt", x.createdAt).put("updatedAt", x.updatedAt).put("deletedAt", nullable(x.deletedAt)))
            } })
            .toString(2)
    }

    suspend fun importJson(raw: String): ImportSummary {
        val parsed = parse(raw)
        val existingInbox = database.inboxDao().allForBackup().map { it.id }.toSet()
        val existingSeries = database.seriesDao().allForBackup().map { it.id }.toSet()
        val existingHabits = database.habitDao().allForBackup().map { it.id }.toSet()
        val existingCheckins = database.habitCheckinDao().allForBackup()
        val existingChecklist = database.checklistDao().allForBackup().map { it.id }.toSet()
        val result = database.withTransaction {
            // References must resolve inside the file or the local database; throwing here rolls everything back.
            val seriesIds = existingSeries + parsed.series.map { it.id }
            val habitIds = existingHabits + parsed.habits.map { it.id }
            val localTasks = database.taskDao().allForBackup()
            // Series that stay live after the import: local rows win over the file's (additive import).
            val localSeries = database.seriesDao().allForBackup()
            val localSeriesIds = localSeries.map { it.id }.toSet()
            val liveSeriesIds = localSeries.filter { it.deletedAt == null }.map { it.id }.toSet() +
                parsed.series.filter { it.deletedAt == null && it.id !in localSeriesIds }.map { it.id }
            val taskIds = localTasks.map { it.id }.toSet() + parsed.tasks.map { it.id }
            require(parsed.tasks.all { it.seriesId == null || it.seriesId in seriesIds } &&
                parsed.checkins.all { it.habitId in habitIds } &&
                parsed.checklist.all { it.taskId in taskIds }) { "Backup inconsistente: referência ausente." }
            // SPEC 015: one level, no cycles, parents that exist, no live subtask under a deleted mother.
            SubtaskRules.importError(
                local = localTasks.map { SubtaskLink(it.id, it.parentId, it.deletedAt != null) },
                incoming = parsed.tasks.map {
                    SubtaskLink(it.id, it.parentId, it.deletedAt != null, repeats = it.seriesId in liveSeriesIds)
                },
            )?.let { throw IllegalArgumentException(it) }

            database.inboxDao().importUnseen(parsed.inbox)
            database.seriesDao().importUnseen(parsed.series)
            val insertedTasks = database.taskDao().importUnseen(parsed.tasks)
            // Skip categories whose name already exists locally so the picker never shows duplicates.
            val localNames = database.categoryDao().active().map { SearchRules.fold(it.name) }.toMutableSet()
            val newCategories = parsed.categories.filter { it.deletedAt != null || localNames.add(SearchRules.fold(it.name)) }
            val categoryIds = database.categoryDao().allForBackup().map { it.id }.toSet()
            database.categoryDao().importUnseen(newCategories)
            database.rescheduleDao().importUnseen(parsed.reschedules)
            database.habitDao().importUnseen(parsed.habits)
            database.habitCheckinDao().importUnseen(parsed.checkins)
            database.checklistDao().importUnseen(parsed.checklist)
            ImportResult(
                insertedTasks,
                parsed.inbox.count { it.id !in existingInbox },
                newCategories.count { it.id !in categoryIds && it.deletedAt == null },
            )
        }
        var newTasks = 0
        parsed.tasks.forEachIndexed { index, task ->
            if (result.insertedTasks[index] != -1L) {
                newTasks++
                val reminder = if (task.deletedAt != null) null else TaskRules.reminderTime(task.toModel())
                changeListener.taskChanged(task.id, reminder)
            }
        }
        val knownCheckinIds = existingCheckins.map { it.id }.toSet()
        val knownCheckinDays = existingCheckins.map { it.habitId to it.date }.toSet()
        return ImportSummary(
            inbox = result.newInbox,
            tasks = newTasks,
            categories = result.newCategories,
            habits = parsed.habits.count { it.id !in existingHabits },
            series = parsed.series.count { it.id !in existingSeries },
            checkins = parsed.checkins.filter { it.id !in knownCheckinIds && (it.habitId to it.date) !in knownCheckinDays }
                .distinctBy { it.habitId to it.date }.size,
            checklistItems = parsed.checklist.count { it.id !in existingChecklist },
        )
    }

    private class ImportResult(val insertedTasks: List<Long>, val newInbox: Int, val newCategories: Int)

    private class Parsed(
        val inbox: List<InboxEntity>,
        val tasks: List<TaskEntity>,
        val categories: List<CategoryEntity>,
        val reschedules: List<RescheduleEntity>,
        val series: List<SeriesEntity>,
        val habits: List<HabitEntity>,
        val checkins: List<HabitCheckinEntity>,
        val checklist: List<ChecklistItemEntity>,
    )

    /** Parses and validates everything before any write, so an invalid file changes nothing. */
    private fun parse(raw: String): Parsed {
        require(raw.length <= 10_000_000) { "Arquivo muito grande." }
        try {
            val json = JSONObject(raw)
            val version = json.optInt("version", -1)
            require(json.optString("format") == FORMAT && version >= 1) { "Formato de backup não reconhecido." }
            require(version <= CURRENT_VERSION) {
                "Este backup foi criado por uma versão mais nova do Pulso. Atualize o app para importar."
            }
            val inboxArray = json.getJSONArray("inbox")
            val tasksArray = json.getJSONArray("tasks")
            val categoriesArray = json.optJSONArray("categories") ?: JSONArray()
            val reschedulesArray = json.optJSONArray("reschedules") ?: JSONArray()
            val seriesArray = json.optJSONArray("series") ?: JSONArray()
            val habitsArray = json.optJSONArray("habits") ?: JSONArray()
            val checkinsArray = json.optJSONArray("habitCheckins") ?: JSONArray()
            val checklistArray = json.optJSONArray("checklistItems") ?: JSONArray()
            require(listOf(inboxArray, tasksArray, categoriesArray, seriesArray, habitsArray).all { it.length() <= MAX_ROWS } &&
                listOf(reschedulesArray, checkinsArray, checklistArray).all { it.length() <= MAX_ROWS * 4 }) {
                "Backup excede o limite."
            }
            val inbox = inboxArray.objects().map { x ->
                InboxEntity(x.getString("id"), x.getString("text"), x.getString("status"),
                    x.getLong("createdAt"), x.getLong("updatedAt"), x.stringOrNull("convertedTaskId"),
                    x.longOrNull("deletedAt"))
            }
            val tasks = tasksArray.objects().map { x ->
                TaskEntity(x.getString("id"), x.getString("title"), x.getString("notes"),
                    x.getString("category"), x.getString("priority"), x.longOrNull("dueAt"),
                    x.longOrNull("completedAt"), x.getLong("createdAt"), x.getLong("updatedAt"),
                    x.longOrNull("deletedAt"), x.optString("links", ""), x.longOrNull("snoozedUntil"),
                    x.stringOrNull("seriesId"), parentId = x.stringOrNull("parentId"))
            }
            val categories = categoriesArray.objects().map { x ->
                CategoryEntity(x.getString("id"), x.getString("name"), x.getLong("createdAt"),
                    x.getLong("updatedAt"), x.longOrNull("deletedAt"))
            }
            val reschedules = reschedulesArray.objects().map { x ->
                RescheduleEntity(x.getString("id"), x.getString("taskId"), x.getLong("fromDueAt"),
                    x.getLong("toDueAt"), x.getLong("changedAt"))
            }
            val series = seriesArray.objects().map { x ->
                SeriesEntity(x.getString("id"), x.getString("frequency"), x.getInt("repeatInterval"),
                    x.getInt("daysOfWeek"), x.getString("basis"), x.getLong("anchorDueAt"),
                    x.getLong("createdAt"), x.getLong("updatedAt"), x.longOrNull("deletedAt"))
            }
            val habits = habitsArray.objects().map { x ->
                HabitEntity(x.getString("id"), x.getString("name"), x.optString("notes", ""),
                    x.getString("scheduleType"), x.optInt("daysOfWeek", 0), x.optInt("timesPerWeek", 0),
                    x.intOrNull("reminderMinutes"), x.optInt("position", 0), x.longOrNull("archivedAt"),
                    x.getLong("createdAt"), x.getLong("updatedAt"), x.longOrNull("deletedAt"))
            }
            val checkins = checkinsArray.objects().map { x ->
                HabitCheckinEntity(x.getString("id"), x.getString("habitId"), x.getString("date"),
                    x.getLong("createdAt"), x.getLong("updatedAt"), x.longOrNull("deletedAt"))
            }
            val checklist = checklistArray.objects().map { x ->
                ChecklistItemEntity(x.getString("id"), x.getString("taskId"), x.getString("text"),
                    x.getInt("position"), x.longOrNull("checkedAt"), x.getLong("createdAt"),
                    x.getLong("updatedAt"), x.longOrNull("deletedAt"))
            }
            inbox.forEach {
                require(it.id.isNotBlank() && it.text.isNotBlank()) { "Backup contém anotação vazia." }
                require(it.status in setOf("INBOX", "CONVERTED", "ARCHIVED")) { "Status de entrada inválido." }
            }
            tasks.forEach {
                require(it.id.isNotBlank() && it.title.isNotBlank() && it.title.length <= TaskRules.MAX_TITLE) {
                    "Backup contém tarefa sem título válido."
                }
                require(it.priority in setOf("LOW", "NORMAL", "HIGH")) { "Prioridade inválida." }
            }
            categories.forEach { require(it.id.isNotBlank() && it.name.isNotBlank()) { "Categoria inválida." } }
            series.forEach {
                val frequency = enumOrNull<RecurrenceFrequency>(it.frequency)
                require(it.id.isNotBlank() && frequency != null && enumOrNull<RecurrenceBasis>(it.basis) != null) {
                    "Série de recorrência inválida."
                }
                require(it.repeatInterval in 1..99 && it.daysOfWeek in 0..DaysOfWeekMask.MAX &&
                    (it.daysOfWeek != 0) == (frequency == RecurrenceFrequency.WEEKLY)) { "Série de recorrência inválida." }
            }
            habits.forEach {
                val type = enumOrNull<HabitScheduleType>(it.scheduleType)
                require(it.id.isNotBlank() && type != null && it.name.isNotBlank() && it.name.length <= MAX_HABIT_NAME) {
                    "Hábito inválido."
                }
                require(it.daysOfWeek in 0..DaysOfWeekMask.MAX &&
                    (it.daysOfWeek != 0) == (type == HabitScheduleType.DAYS_OF_WEEK) &&
                    (if (type == HabitScheduleType.TIMES_PER_WEEK) it.timesPerWeek in 1..7 else it.timesPerWeek == 0) &&
                    (it.reminderMinutes == null || it.reminderMinutes in 0..1439)) { "Hábito inválido." }
            }
            checkins.forEach {
                require(it.id.isNotBlank() && it.habitId.isNotBlank()) { "Check-in inválido." }
                try { LocalDate.parse(it.date) } catch (_: DateTimeException) { throw IllegalArgumentException("Check-in com data inválida.") }
            }
            checklist.forEach {
                require(it.id.isNotBlank() && it.taskId.isNotBlank() && it.text.isNotBlank() && it.text.length <= MAX_CHECKLIST_TEXT) {
                    "Item de checklist inválido."
                }
            }
            return Parsed(inbox, tasks, categories, reschedules, series, habits, checkins, checklist)
        } catch (exception: JSONException) {
            throw IllegalArgumentException("Arquivo de backup inválido ou corrompido.", exception)
        }
    }

    private inline fun <reified E : Enum<E>> enumOrNull(name: String): E? = enumValues<E>().firstOrNull { it.name == name }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun nullable(value: Any?): Any = value ?: JSONObject.NULL
    private fun JSONObject.longOrNull(key: String): Long? = if (!has(key) || isNull(key)) null else getLong(key)
    private fun JSONObject.intOrNull(key: String): Int? = if (!has(key) || isNull(key)) null else getInt(key)
    private fun JSONObject.stringOrNull(key: String): String? = if (!has(key) || isNull(key)) null else getString(key)

    companion object {
        const val FORMAT = "pulso-backup"
        const val CURRENT_VERSION = 4
        private const val MAX_ROWS = 50_000
        private const val MAX_HABIT_NAME = 60
        private const val MAX_CHECKLIST_TEXT = 200
    }
}
