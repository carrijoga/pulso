package dev.pulso.app.data

import androidx.room.withTransaction
import dev.pulso.app.domain.SearchRules
import dev.pulso.app.domain.TaskRules
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Result of an additive import: how many new rows were written (existing UUIDs are skipped). */
data class ImportSummary(val inbox: Int, val tasks: Int, val categories: Int) {
    val message: String get() = if (inbox + tasks + categories == 0) "Nada novo para importar (tudo já existia)."
        else "Importados: $tasks tarefas, $inbox anotações, $categories categorias. Duplicados ignorados."
}

/**
 * Portable local backup. Import is additive (same UUIDs are skipped), never destructive.
 * Version 2 adds task links/snooze, categories and reschedule history; version 1 files still import.
 */
class BackupService(
    private val database: PulsoDatabase,
    private val changeListener: TaskChangeListener,
) {
    suspend fun exportJson(): String {
        val inbox = database.inboxDao().allForBackup()
        val tasks = database.taskDao().allForBackup()
        val categories = database.categoryDao().allForBackup()
        val reschedules = database.rescheduleDao().allForBackup()
        return JSONObject()
            .put("format", FORMAT)
            .put("version", CURRENT_VERSION)
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
                    .put("snoozedUntil", nullable(x.snoozedUntil)))
            } })
            .put("categories", JSONArray().also { a -> categories.forEach { x ->
                a.put(JSONObject().put("id", x.id).put("name", x.name).put("createdAt", x.createdAt)
                    .put("updatedAt", x.updatedAt).put("deletedAt", nullable(x.deletedAt)))
            } })
            .put("reschedules", JSONArray().also { a -> reschedules.forEach { x ->
                a.put(JSONObject().put("id", x.id).put("taskId", x.taskId).put("fromDueAt", x.fromDueAt)
                    .put("toDueAt", x.toDueAt).put("changedAt", x.changedAt))
            } })
            .toString(2)
    }

    suspend fun importJson(raw: String): ImportSummary {
        val parsed = parse(raw)
        val existingInbox = database.inboxDao().allForBackup().map { it.id }.toSet()
        val result = database.withTransaction {
            database.inboxDao().importUnseen(parsed.inbox)
            val insertedTasks = database.taskDao().importUnseen(parsed.tasks)
            // Skip categories whose name already exists locally so the picker never shows duplicates.
            val localNames = database.categoryDao().active().map { SearchRules.fold(it.name) }.toMutableSet()
            val newCategories = parsed.categories.filter { it.deletedAt != null || localNames.add(SearchRules.fold(it.name)) }
            val categoryIds = database.categoryDao().allForBackup().map { it.id }.toSet()
            database.categoryDao().importUnseen(newCategories)
            database.rescheduleDao().importUnseen(parsed.reschedules)
            Triple(insertedTasks,
                parsed.inbox.count { it.id !in existingInbox },
                newCategories.count { it.id !in categoryIds && it.deletedAt == null })
        }
        val (insertedTasks, newInbox, newCategories) = result
        var newTasks = 0
        parsed.tasks.forEachIndexed { index, task ->
            if (insertedTasks[index] != -1L) {
                newTasks++
                val reminder = if (task.deletedAt != null) null else TaskRules.reminderTime(task.toModel())
                changeListener.taskChanged(task.id, reminder)
            }
        }
        return ImportSummary(newInbox, newTasks, newCategories)
    }

    private class Parsed(
        val inbox: List<InboxEntity>,
        val tasks: List<TaskEntity>,
        val categories: List<CategoryEntity>,
        val reschedules: List<RescheduleEntity>,
    )

    /** Parses and validates everything before any write, so an invalid file changes nothing. */
    private fun parse(raw: String): Parsed {
        require(raw.length <= 10_000_000) { "Arquivo muito grande." }
        try {
            val json = JSONObject(raw)
            val version = json.optInt("version", -1)
            require(json.optString("format") == FORMAT && version in 1..CURRENT_VERSION) {
                "Formato de backup não reconhecido."
            }
            val inboxArray = json.getJSONArray("inbox")
            val tasksArray = json.getJSONArray("tasks")
            val categoriesArray = json.optJSONArray("categories") ?: JSONArray()
            val reschedulesArray = json.optJSONArray("reschedules") ?: JSONArray()
            require(listOf(inboxArray, tasksArray, categoriesArray).all { it.length() <= MAX_ROWS } &&
                reschedulesArray.length() <= MAX_ROWS * 4) { "Backup excede o limite." }
            val inbox = inboxArray.objects().map { x ->
                InboxEntity(x.getString("id"), x.getString("text"), x.getString("status"),
                    x.getLong("createdAt"), x.getLong("updatedAt"), x.stringOrNull("convertedTaskId"),
                    x.longOrNull("deletedAt"))
            }
            val tasks = tasksArray.objects().map { x ->
                TaskEntity(x.getString("id"), x.getString("title"), x.getString("notes"),
                    x.getString("category"), x.getString("priority"), x.longOrNull("dueAt"),
                    x.longOrNull("completedAt"), x.getLong("createdAt"), x.getLong("updatedAt"),
                    x.longOrNull("deletedAt"), x.optString("links", ""), x.longOrNull("snoozedUntil"))
            }
            val categories = categoriesArray.objects().map { x ->
                CategoryEntity(x.getString("id"), x.getString("name"), x.getLong("createdAt"),
                    x.getLong("updatedAt"), x.longOrNull("deletedAt"))
            }
            val reschedules = reschedulesArray.objects().map { x ->
                RescheduleEntity(x.getString("id"), x.getString("taskId"), x.getLong("fromDueAt"),
                    x.getLong("toDueAt"), x.getLong("changedAt"))
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
            return Parsed(inbox, tasks, categories, reschedules)
        } catch (exception: JSONException) {
            throw IllegalArgumentException("Arquivo de backup inválido ou corrompido.", exception)
        }
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun nullable(value: Any?): Any = value ?: JSONObject.NULL
    private fun JSONObject.longOrNull(key: String): Long? = if (!has(key) || isNull(key)) null else getLong(key)
    private fun JSONObject.stringOrNull(key: String): String? = if (!has(key) || isNull(key)) null else getString(key)

    companion object {
        const val FORMAT = "pulso-backup"
        const val CURRENT_VERSION = 2
        private const val MAX_ROWS = 50_000
    }
}
