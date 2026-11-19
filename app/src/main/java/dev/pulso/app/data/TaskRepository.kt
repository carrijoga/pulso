package dev.pulso.app.data

import androidx.room.withTransaction
import dev.pulso.app.domain.Category
import dev.pulso.app.domain.CategoryRules
import dev.pulso.app.domain.InboxItem
import dev.pulso.app.domain.InboxStatus
import dev.pulso.app.domain.RescheduleEntry
import dev.pulso.app.domain.TaskDraft
import dev.pulso.app.domain.TaskItem
import dev.pulso.app.domain.TaskRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

interface TaskRepository {
    fun observeInbox(): Flow<List<InboxItem>>
    fun observeSearchableInbox(): Flow<List<InboxItem>>
    fun observeTasks(): Flow<List<TaskItem>>
    fun observeCategories(): Flow<List<Category>>
    fun observeRescheduleHistory(taskId: String): Flow<List<RescheduleEntry>>
    suspend fun capture(text: String)
    suspend fun archiveInbox(id: String)
    suspend fun restoreInbox(id: String)
    suspend fun organizeInbox(id: String, draft: TaskDraft)
    suspend fun createTask(draft: TaskDraft)
    suspend fun editTask(id: String, draft: TaskDraft)
    suspend fun setCompleted(id: String, completed: Boolean)
    suspend fun deleteTask(id: String)
    suspend fun restoreTask(id: String)
    /** Moves the due date (recording history). Clears any snooze. */
    suspend fun reschedule(id: String, newDueAt: Long)
    /** Postpones only the reminder; the due date stays as-is. */
    suspend fun snooze(id: String, untilMillis: Long)
    suspend fun addCategory(name: String)
    suspend fun renameCategory(id: String, name: String)
    suspend fun deleteCategory(id: String)
}

/** Port for side effects of task changes (reminders + widget). [reminderAt] null cancels the reminder. */
interface TaskChangeListener {
    suspend fun taskChanged(taskId: String, reminderAt: Long?)
}

class LocalTaskRepository(
    private val database: PulsoDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val changeListener: TaskChangeListener,
) : TaskRepository {
    private val inboxDao get() = database.inboxDao()
    private val taskDao get() = database.taskDao()
    private val categoryDao get() = database.categoryDao()
    private val rescheduleDao get() = database.rescheduleDao()

    override fun observeInbox(): Flow<List<InboxItem>> =
        inboxDao.observePending().map { list -> list.map(InboxEntity::toModel) }

    override fun observeSearchableInbox(): Flow<List<InboxItem>> =
        inboxDao.observeSearchable().map { list -> list.map(InboxEntity::toModel) }

    override fun observeTasks(): Flow<List<TaskItem>> =
        taskDao.observeActiveAndCompleted().map { list -> list.map(TaskWithStats::toModel) }

    override fun observeCategories(): Flow<List<Category>> =
        categoryDao.observeActive().map { list -> list.map(CategoryEntity::toModel) }

    override fun observeRescheduleHistory(taskId: String): Flow<List<RescheduleEntry>> =
        rescheduleDao.observeForTask(taskId).map { list -> list.map(RescheduleEntity::toModel) }

    override suspend fun capture(text: String) {
        val clean = TaskRules.validateCapture(text)
        val timestamp = now()
        inboxDao.upsert(InboxEntity(UUID.randomUUID().toString(), clean,
            InboxStatus.INBOX.name, timestamp, timestamp))
    }

    override suspend fun archiveInbox(id: String) = setInboxStatus(id, from = InboxStatus.INBOX, to = InboxStatus.ARCHIVED)

    override suspend fun restoreInbox(id: String) = setInboxStatus(id, from = InboxStatus.ARCHIVED, to = InboxStatus.INBOX)

    private suspend fun setInboxStatus(id: String, from: InboxStatus, to: InboxStatus) {
        val item = inboxDao.findById(id) ?: return
        if (item.status != from.name || item.deletedAt != null) return
        inboxDao.upsert(item.copy(status = to.name, updatedAt = now()))
    }

    override suspend fun organizeInbox(id: String, draft: TaskDraft) {
        val validated = TaskRules.validateDraft(draft)
        val timestamp = now()
        val taskId = UUID.randomUUID().toString()
        var converted = false
        database.withTransaction {
            val inbox = inboxDao.findById(id)
            if (inbox != null && inbox.status == InboxStatus.INBOX.name) {
                taskDao.upsert(newTask(taskId, validated, timestamp))
                inboxDao.upsert(inbox.copy(status = InboxStatus.CONVERTED.name,
                    convertedTaskId = taskId, updatedAt = timestamp))
                converted = true
            }
        }
        if (converted) changeListener.taskChanged(taskId, validated.dueAt)
    }

    override suspend fun createTask(draft: TaskDraft) {
        val valid = TaskRules.validateDraft(draft)
        val id = UUID.randomUUID().toString()
        taskDao.upsert(newTask(id, valid, now()))
        changeListener.taskChanged(id, valid.dueAt)
    }

    private fun newTask(id: String, draft: TaskDraft, timestamp: Long) = TaskEntity(
        id = id, title = draft.title, notes = draft.notes, category = draft.category,
        priority = draft.priority.name, dueAt = draft.dueAt, completedAt = null,
        createdAt = timestamp, updatedAt = timestamp, links = draft.links,
    )

    override suspend fun editTask(id: String, draft: TaskDraft) {
        val valid = TaskRules.validateDraft(draft)
        val timestamp = now()
        val updated = database.withTransaction {
            val original = taskDao.findById(id) ?: return@withTransaction null
            if (original.deletedAt != null) return@withTransaction null
            val dueChanged = original.dueAt != valid.dueAt
            if (dueChanged) recordReschedule(original, valid.dueAt, timestamp)
            original.copy(title = valid.title, notes = valid.notes, category = valid.category,
                priority = valid.priority.name, dueAt = valid.dueAt, links = valid.links,
                snoozedUntil = if (dueChanged) null else original.snoozedUntil,
                updatedAt = timestamp).also { taskDao.upsert(it) }
        } ?: return
        notify(updated)
    }

    override suspend fun reschedule(id: String, newDueAt: Long) {
        val timestamp = now()
        val updated = database.withTransaction {
            val original = taskDao.findById(id) ?: return@withTransaction null
            if (original.deletedAt != null) return@withTransaction null
            recordReschedule(original, newDueAt, timestamp)
            original.copy(dueAt = newDueAt, snoozedUntil = null, updatedAt = timestamp)
                .also { taskDao.upsert(it) }
        } ?: return
        notify(updated)
    }

    /** Only a move between two real dates counts; setting/removing a due date is not a reschedule. */
    private suspend fun recordReschedule(original: TaskEntity, newDueAt: Long?, timestamp: Long) {
        val from = original.dueAt ?: return
        if (newDueAt == null || newDueAt == from) return
        rescheduleDao.insert(RescheduleEntity(UUID.randomUUID().toString(), original.id, from, newDueAt, timestamp))
    }

    override suspend fun snooze(id: String, untilMillis: Long) {
        val original = taskDao.findById(id) ?: return
        if (original.deletedAt != null || original.completedAt != null) return
        val updated = original.copy(snoozedUntil = untilMillis, updatedAt = now())
        taskDao.upsert(updated)
        notify(updated)
    }

    override suspend fun setCompleted(id: String, completed: Boolean) {
        val original = taskDao.findById(id) ?: return
        if (original.deletedAt != null || (original.completedAt != null) == completed) return
        val timestamp = now()
        val updated = original.copy(completedAt = if (completed) timestamp else null,
            snoozedUntil = null, updatedAt = timestamp)
        taskDao.upsert(updated)
        notify(updated)
    }

    override suspend fun deleteTask(id: String) {
        val original = taskDao.findById(id) ?: return
        if (original.deletedAt != null) return
        val timestamp = now()
        taskDao.upsert(original.copy(deletedAt = timestamp, updatedAt = timestamp))
        changeListener.taskChanged(id, null)
    }

    override suspend fun restoreTask(id: String) {
        val original = taskDao.findById(id) ?: return
        if (original.deletedAt == null) return
        val updated = original.copy(deletedAt = null, updatedAt = now())
        taskDao.upsert(updated)
        notify(updated)
    }

    private suspend fun notify(task: TaskEntity) {
        changeListener.taskChanged(task.id, if (task.deletedAt != null) null else TaskRules.reminderTime(task.toModel()))
    }

    override suspend fun addCategory(name: String) {
        database.withTransaction {
            val existing = categoryDao.active().map(CategoryEntity::toModel)
            val clean = CategoryRules.validateName(name, existing)
            val timestamp = now()
            categoryDao.upsert(CategoryEntity(UUID.randomUUID().toString(), clean, timestamp, timestamp))
        }
    }

    /** Renames the category and every active task that uses it, atomically. */
    override suspend fun renameCategory(id: String, name: String) {
        database.withTransaction {
            val original = categoryDao.findById(id) ?: return@withTransaction
            if (original.deletedAt != null) return@withTransaction
            val clean = CategoryRules.validateName(name, categoryDao.active().map(CategoryEntity::toModel), ignoreId = id)
            if (clean == original.name) return@withTransaction
            val timestamp = now()
            categoryDao.upsert(original.copy(name = clean, updatedAt = timestamp))
            taskDao.renameCategory(original.name, clean, timestamp)
        }
    }

    /** Hides the category from pickers; tasks keep their category text (nothing is lost). */
    override suspend fun deleteCategory(id: String) {
        val original = categoryDao.findById(id) ?: return
        if (original.deletedAt != null) return
        val timestamp = now()
        categoryDao.upsert(original.copy(deletedAt = timestamp, updatedAt = timestamp))
    }
}
