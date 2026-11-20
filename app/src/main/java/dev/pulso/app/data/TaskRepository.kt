package dev.pulso.app.data

import androidx.room.withTransaction
import dev.pulso.app.domain.Category
import dev.pulso.app.domain.CategoryRules
import dev.pulso.app.domain.ChecklistItem
import dev.pulso.app.domain.ChecklistRules
import dev.pulso.app.domain.DaysOfWeekMask
import dev.pulso.app.domain.InboxItem
import dev.pulso.app.domain.InboxStatus
import dev.pulso.app.domain.RecurrenceRule
import dev.pulso.app.domain.RecurrenceRules
import dev.pulso.app.domain.RescheduleEntry
import dev.pulso.app.domain.SubtaskRules
import dev.pulso.app.domain.TaskDraft
import dev.pulso.app.domain.TaskItem
import dev.pulso.app.domain.TaskRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.ZoneId
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
    /**
     * Completes or reopens. Completing the open occurrence of a series creates the next one (SPEC 008)
     * and returns it; completing twice creates nothing. Reopening removes the untouched next occurrence.
     * [completeSubtasks] (SPEC 015) also completes the open subtasks in the same transaction; reopening the
     * mother reopens the subtasks completed with it.
     */
    suspend fun setCompleted(id: String, completed: Boolean, completeSubtasks: Boolean = false): TaskItem?
    /** "Excluir só esta": deletes this occurrence; an open occurrence of a series generates the next one. */
    suspend fun deleteTask(id: String)
    /** "Encerrar repetição": deletes this open occurrence and ends its series. Completed ones are refused. */
    suspend fun endRecurrence(id: String)
    /** Undo of [endRecurrence]: restores the occurrence and the series that the same end tombstoned. */
    suspend fun restoreEndedRecurrence(id: String)
    /** Undo of [deleteTask]: restores it and removes the untouched next occurrence it generated. */
    suspend fun restoreTask(id: String)
    /** Moves the due date (recording history). Clears any snooze. */
    suspend fun reschedule(id: String, newDueAt: Long)
    /** Postpones only the reminder; the due date stays as-is. */
    suspend fun snooze(id: String, untilMillis: Long)
    suspend fun addCategory(name: String)
    suspend fun renameCategory(id: String, name: String)
    suspend fun deleteCategory(id: String)

    /** Active checklist items of a task, in order (SPEC 013). */
    fun observeChecklist(taskId: String): Flow<List<ChecklistItem>>
    /** Adds an item at the end (up to [ChecklistRules.MAX_ITEMS]). Touches the task in the same transaction. */
    suspend fun addChecklistItem(taskId: String, text: String)
    /** Checks or unchecks. Returns true only when the stored state changed (false: same state, or item gone). */
    suspend fun setChecklistItemChecked(itemId: String, checked: Boolean): Boolean
    suspend fun editChecklistItem(itemId: String, text: String)
    /** Tombstone; [restoreChecklistItem] puts it back at its place. */
    suspend fun deleteChecklistItem(itemId: String)
    suspend fun restoreChecklistItem(itemId: String)
    /** Moves the item to [newIndex] among the active items of its task (clamped). */
    suspend fun moveChecklistItem(itemId: String, newIndex: Int)
}

/** Port for side effects of task changes (reminders + widget). [reminderAt] null cancels the reminder. */
interface TaskChangeListener {
    suspend fun taskChanged(taskId: String, reminderAt: Long?)
}

class LocalTaskRepository(
    private val database: PulsoDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val changeListener: TaskChangeListener,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : TaskRepository {
    private val inboxDao get() = database.inboxDao()
    private val taskDao get() = database.taskDao()
    private val categoryDao get() = database.categoryDao()
    private val rescheduleDao get() = database.rescheduleDao()
    private val seriesDao get() = database.seriesDao()
    private val checklistDao get() = database.checklistDao()

    override fun observeInbox(): Flow<List<InboxItem>> =
        inboxDao.observePending().map { list -> list.map(InboxEntity::toModel) }

    override fun observeSearchableInbox(): Flow<List<InboxItem>> =
        inboxDao.observeSearchable().map { list -> list.map(InboxEntity::toModel) }

    override fun observeTasks(): Flow<List<TaskItem>> = combine(
        taskDao.observeActiveAndCompleted(), seriesDao.observeActive(), checklistDao.observeActive(),
    ) { tasks, series, items ->
        val bySeries = series.associateBy(SeriesEntity::id)
        val byTask = items.groupBy(ChecklistItemEntity::taskId)
        // observeActiveAndCompleted returns live rows only: a mother's title and its subtasks come from this list.
        val byId = tasks.associateBy { it.task.id }
        val subtasksOf = tasks.filter { it.task.parentId != null }.groupBy { it.task.parentId!! }
        tasks.map { row ->
            val model = row.toModel().copy(checklist = byTask[row.task.id].orEmpty().map(ChecklistItemEntity::toModel))
            // A task whose series ended keeps its seriesId for history but shows no repetition.
            val seriesRow = row.task.seriesId?.let { bySeries[it] }
            val withSeries = if (seriesRow == null) model
                else model.copy(recurrence = seriesRow.toRule(), recurrenceAnchorDueAt = seriesRow.anchorDueAt)
            val children = subtasksOf[row.task.id].orEmpty()
            withSeries.copy(
                parentTitle = row.task.parentId?.let { byId[it]?.task?.title },
                subtaskTotal = children.size,
                subtaskDone = children.count { it.task.completedAt != null },
            )
        }
    }

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
                checkSubtaskRules(taskId, validated.parentId, repeats = validated.recurrence != null)
                val seriesId = createSeries(validated, timestamp)
                taskDao.upsert(newTask(taskId, validated, timestamp, seriesId))
                insertChecklist(taskId, validated.checklist, timestamp)
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
        val timestamp = now()
        database.withTransaction {
            checkSubtaskRules(id, valid.parentId, repeats = valid.recurrence != null)
            val seriesId = createSeries(valid, timestamp)
            taskDao.upsert(newTask(id, valid, timestamp, seriesId))
            insertChecklist(id, valid.checklist, timestamp)
        }
        changeListener.taskChanged(id, valid.dueAt)
    }

    /** Initial checklist of a new task, unchecked, in the given order. Must run in a transaction. */
    private suspend fun insertChecklist(taskId: String, texts: List<String>, at: Long) {
        texts.forEachIndexed { index, text ->
            checklistDao.upsert(ChecklistItemEntity(UUID.randomUUID().toString(), taskId, text, index, null, at, at))
        }
    }

    private fun newTask(id: String, draft: TaskDraft, timestamp: Long, seriesId: String? = null) = TaskEntity(
        id = id, title = draft.title, notes = draft.notes, category = draft.category,
        priority = draft.priority.name, dueAt = draft.dueAt, completedAt = null,
        createdAt = timestamp, updatedAt = timestamp, links = draft.links, seriesId = seriesId,
        parentId = draft.parentId,
    )

    override suspend fun editTask(id: String, draft: TaskDraft) {
        val valid = TaskRules.validateDraft(draft)
        val rule = checkedRule(valid)
        val timestamp = now()
        val updated = database.withTransaction {
            val original = taskDao.findById(id) ?: return@withTransaction null
            if (original.deletedAt != null) return@withTransaction null
            checkSubtaskRules(id, valid.parentId, repeats = rule != null)
            val dueChanged = original.dueAt != valid.dueAt
            if (dueChanged) recordReschedule(original, valid.dueAt, timestamp)
            val seriesId = syncSeries(original, valid, rule, timestamp)
            original.copy(title = valid.title, notes = valid.notes, category = valid.category,
                priority = valid.priority.name, dueAt = valid.dueAt, links = valid.links,
                snoozedUntil = if (dueChanged) null else original.snoozedUntil,
                seriesId = seriesId, parentId = valid.parentId, updatedAt = timestamp).also { taskDao.upsert(it) }
        } ?: return
        notify(updated)
    }

    /**
     * SPEC 015 rules for the link and for repetition, checked before the write in the same transaction.
     * [taskId] is the task being written; its live subtasks decide nothing for the link, only for the
     * link refusal "tarefa com subtarefas não pode virar subtarefa".
     */
    private suspend fun checkSubtaskRules(taskId: String, parentId: String?, repeats: Boolean) {
        val hasSubtasks = taskDao.countLiveChildren(taskId) > 0
        val parent = parentId?.let { taskDao.findById(it) }?.let { row ->
            SubtaskRules.Parent(row.id, isSubtask = row.parentId != null, deleted = row.deletedAt != null)
        }
        SubtaskRules.linkError(taskId, parentId, parent, hasSubtasks, taskRepeats = repeats)?.let(::refuse)
        if (repeats) SubtaskRules.repeatError(isSubtask = parentId != null)?.let(::refuse)
    }

    private fun refuse(message: String): Nothing = throw IllegalArgumentException(message)

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

    override suspend fun setCompleted(id: String, completed: Boolean, completeSubtasks: Boolean): TaskItem? {
        val timestamp = now()
        val mutation = database.withTransaction {
            val original = taskDao.findById(id)
            if (original == null || original.deletedAt != null || (original.completedAt != null) == completed) {
                return@withTransaction Mutation.NONE
            }
            if (completed) {
                val done = original.copy(completedAt = timestamp, snoozedUntil = null, updatedAt = timestamp)
                taskDao.upsert(done)
                // Copies of the subtasks are made before the originals are completed (copies always start open).
                val spawned = spawnNextOccurrence(done, timestamp)
                val subtasks = if (completeSubtasks) completeOpenChildren(id, timestamp) else emptyList()
                Mutation(listOf(done) + spawned.changed() + subtasks, spawned?.next)
            } else {
                val reopened = original.copy(completedAt = null, snoozedUntil = null, updatedAt = timestamp)
                taskDao.upsert(reopened)
                val removed = tombstoneUntouchedNext(reopened, generatedAt = original.completedAt, at = timestamp)
                val subtasks = reopenChildrenCompletedWith(id, original.completedAt, timestamp)
                Mutation(listOf(reopened) + removed + subtasks)
            }
        }
        mutation.changed.forEach { notify(it) }
        return mutation.next?.toModel()
    }

    override suspend fun deleteTask(id: String) {
        val timestamp = now()
        val mutation = database.withTransaction {
            val original = taskDao.findById(id)
            if (original == null || original.deletedAt != null) return@withTransaction Mutation.NONE
            val tombstone = original.copy(deletedAt = timestamp, updatedAt = timestamp)
            taskDao.upsert(tombstone)
            // Only an open occurrence generates a successor; deleting old history must not create one.
            // The successor copies the subtasks while they are still live: they are removed below.
            val spawned = if (original.completedAt == null) spawnNextOccurrence(tombstone, timestamp) else null
            // SPEC 015: the subtasks go with their mother in the same transaction; one undo restores them.
            val subtasks = taskDao.liveChildren(id).map { child ->
                child.copy(deletedAt = timestamp, updatedAt = timestamp).also { taskDao.upsert(it) }
            }
            Mutation(listOf(tombstone) + spawned.changed() + subtasks, spawned?.next)
        }
        mutation.changed.forEach { notify(it) }
    }

    override suspend fun endRecurrence(id: String) {
        val timestamp = now()
        val mutation = database.withTransaction {
            val original = taskDao.findById(id)
            if (original == null || original.deletedAt != null) return@withTransaction Mutation.NONE
            // Only an open occurrence offers "Encerrar"; ending from history would silently drop the next one.
            require(original.completedAt == null) { "Só é possível encerrar a repetição de uma tarefa aberta." }
            original.seriesId?.let { seriesDao.findById(it) }?.takeIf { it.deletedAt == null }?.let { series ->
                seriesDao.upsert(series.copy(deletedAt = timestamp, updatedAt = timestamp))
            }
            val tombstone = original.copy(deletedAt = timestamp, updatedAt = timestamp)
            taskDao.upsert(tombstone)
            val subtasks = taskDao.liveChildren(id).map { child ->
                child.copy(deletedAt = timestamp, updatedAt = timestamp).also { taskDao.upsert(it) }
            }
            Mutation(listOf(tombstone) + subtasks)
        }
        mutation.changed.forEach { notify(it) }
    }

    override suspend fun restoreEndedRecurrence(id: String) {
        val timestamp = now()
        val mutation = database.withTransaction {
            val original = taskDao.findById(id)
            if (original == null || original.deletedAt == null) return@withTransaction Mutation.NONE
            // Only the series tombstoned by the same end (same instant) is revived.
            val endedAt = requireNotNull(original.deletedAt)
            original.seriesId?.let { seriesDao.findById(it) }?.takeIf { it.deletedAt == endedAt }?.let { series ->
                seriesDao.upsert(series.copy(deletedAt = null, updatedAt = timestamp))
            }
            val restored = original.copy(deletedAt = null, updatedAt = timestamp)
            taskDao.upsert(restored)
            val subtasks = taskDao.childrenDeletedAt(id, endedAt).map { child ->
                child.copy(deletedAt = null, updatedAt = timestamp).also { taskDao.upsert(it) }
            }
            Mutation(listOf(restored) + subtasks)
        }
        mutation.changed.forEach { notify(it) }
    }

    override suspend fun restoreTask(id: String) {
        val timestamp = now()
        val mutation = database.withTransaction {
            val original = taskDao.findById(id)
            if (original == null || original.deletedAt == null) return@withTransaction Mutation.NONE
            val restored = original.copy(deletedAt = null, updatedAt = timestamp)
            taskDao.upsert(restored)
            val removed = tombstoneUntouchedNext(restored, generatedAt = original.deletedAt, at = timestamp)
            // Only the subtasks removed with this mother (same instant); ones deleted earlier stay deleted.
            val subtasks = taskDao.childrenDeletedAt(id, requireNotNull(original.deletedAt)).map { child ->
                child.copy(deletedAt = null, updatedAt = timestamp).also { taskDao.upsert(it) }
            }
            Mutation(listOf(restored) + removed + subtasks)
        }
        mutation.changed.forEach { notify(it) }
    }

    /** The next occurrence and copies of the subtasks made for it (SPEC 015 §7). */
    private class Spawn(val next: TaskEntity, val subtasks: List<TaskEntity>)

    private fun Spawn?.changed(): List<TaskEntity> = this?.let { listOf(it.next) + it.subtasks }.orEmpty()

    /**
     * Creates the next occurrence of [finished]'s series, unless another occurrence of that series is
     * still open (idempotency: widget + notification may both complete the same occurrence).
     * Copies title, notes, category, priority, links and the checklist (unchecked), and the open copies of
     * its live subtasks (SPEC 015 §7). Must run in a transaction.
     */
    private suspend fun spawnNextOccurrence(finished: TaskEntity, at: Long): Spawn? {
        val seriesId = finished.seriesId ?: return null
        val series = seriesDao.findById(seriesId)?.takeIf { it.deletedAt == null } ?: return null
        if (taskDao.findOpenBySeries(seriesId).any { it.id != finished.id }) return null
        val currentDue = finished.dueAt ?: at
        val nextDue = RecurrenceRules.nextDueAt(series.toRule(), series.anchorDueAt,
            currentDueAt = currentDue, completedAt = at, nowMillis = at, zone = zone())
        val next = finished.copy(id = UUID.randomUUID().toString(), dueAt = nextDue, completedAt = null,
            snoozedUntil = null, createdAt = at, updatedAt = at, deletedAt = null)
        taskDao.upsert(next)
        checklistDao.activeForTask(finished.id).forEach { item ->
            checklistDao.upsert(item.copy(id = UUID.randomUUID().toString(), taskId = next.id,
                checkedAt = null, createdAt = at, updatedAt = at, deletedAt = null))
        }
        val subtasks = taskDao.liveChildren(finished.id).map { child ->
            copySubtask(child, next, oldMotherDue = currentDue, nextMotherDue = nextDue, at = at)
        }
        return Spawn(next, subtasks)
    }

    /**
     * Open copy of a subtask for the next occurrence: new id, same content, due date shifted with the mother
     * (same local days apart and time), unchecked checklist copy. Subtasks never repeat, so no series.
     */
    private suspend fun copySubtask(child: TaskEntity, nextMother: TaskEntity, oldMotherDue: Long,
                                    nextMotherDue: Long, at: Long): TaskEntity {
        val copy = child.copy(id = UUID.randomUUID().toString(), parentId = nextMother.id,
            dueAt = child.dueAt?.let { SubtaskRules.shiftedDueAt(it, oldMotherDue, nextMotherDue, zone()) },
            completedAt = null, snoozedUntil = null, seriesId = null,
            createdAt = at, updatedAt = at, deletedAt = null)
        taskDao.upsert(copy)
        checklistDao.activeForTask(child.id).forEach { item ->
            checklistDao.upsert(item.copy(id = UUID.randomUUID().toString(), taskId = copy.id,
                checkedAt = null, createdAt = at, updatedAt = at, deletedAt = null))
        }
        return copy
    }

    /**
     * Undo (SPEC 008): removes the next occurrence created at [generatedAt] that nobody edited since
     * (createdAt == updatedAt), together with its untouched subtask copies (SPEC 015 §7). If the user edited
     * the occurrence or any copy, nothing is removed, so nothing the user wrote is lost.
     * Returns the rows tombstoned (empty when nothing was removed). Must run in a transaction.
     */
    private suspend fun tombstoneUntouchedNext(occurrence: TaskEntity, generatedAt: Long?, at: Long): List<TaskEntity> {
        val seriesId = occurrence.seriesId ?: return emptyList()
        if (generatedAt == null) return emptyList()
        val untouched = taskDao.findOpenBySeries(seriesId).firstOrNull {
            it.id != occurrence.id && it.createdAt == generatedAt && it.updatedAt == generatedAt
        } ?: return emptyList()
        val copies = taskDao.liveChildren(untouched.id)
        if (copies.any { it.createdAt != generatedAt || it.updatedAt != generatedAt }) return emptyList()
        return (listOf(untouched) + copies).map { row ->
            row.copy(deletedAt = at, updatedAt = at).also { taskDao.upsert(it) }
        }
    }

    /** SPEC 015: completes the open subtasks of [parentId] at [at]. Must run in a transaction. */
    private suspend fun completeOpenChildren(parentId: String, at: Long): List<TaskEntity> =
        taskDao.openChildren(parentId).map { child ->
            child.copy(completedAt = at, snoozedUntil = null, updatedAt = at).also { taskDao.upsert(it) }
        }

    /** SPEC 015: undo of "Concluir também as subtarefas": reopens those completed at the mother's instant. */
    private suspend fun reopenChildrenCompletedWith(parentId: String, completedAt: Long?, at: Long): List<TaskEntity> {
        if (completedAt == null) return emptyList()
        return taskDao.childrenCompletedAt(parentId, completedAt).map { child ->
            child.copy(completedAt = null, snoozedUntil = null, updatedAt = at).also { taskDao.upsert(it) }
        }
    }

    /**
     * Validates a repetition: the rule must be valid and the task needs a due date (the first occurrence).
     * Returns null for a non-repeating draft.
     */
    private fun checkedRule(draft: TaskDraft): RecurrenceRule? {
        val rule = draft.recurrence?.let(RecurrenceRules::validate) ?: return null
        require(draft.dueAt != null) { "A tarefa repetida precisa de prazo." }
        return rule
    }

    /** Creates a series anchored at the draft's due date when the draft repeats. Returns its id. */
    private suspend fun createSeries(draft: TaskDraft, at: Long): String? {
        val rule = checkedRule(draft) ?: return null
        return insertSeries(rule, requireNotNull(draft.dueAt), at).id
    }

    /**
     * Brings the task's series in line with the editor (SPEC 008) and returns the seriesId the task keeps.
     * Unchanged rule: nothing. Changed rule: the series restarts at the current due date. "Não repetir":
     * ends the series but keeps the link for history. Editing rules of a completed occurrence is refused.
     */
    private suspend fun syncSeries(original: TaskEntity, draft: TaskDraft, rule: RecurrenceRule?, at: Long): String? {
        val current = original.seriesId?.let { seriesDao.findById(it) }?.takeIf { it.deletedAt == null }
        if (current == null) {
            if (rule == null) return original.seriesId
            require(original.completedAt == null) { "Reabra a tarefa para mudar a repetição." }
            return createSeries(draft, at)
        }
        if (current.toRule() == rule) return current.id
        require(original.completedAt == null) { "Reabra a tarefa para mudar a repetição." }
        if (rule == null) {
            seriesDao.upsert(current.copy(deletedAt = at, updatedAt = at))
            return original.seriesId
        }
        seriesDao.upsert(seriesEntity(current.id, rule, requireNotNull(draft.dueAt), current.createdAt, at))
        return current.id
    }

    private suspend fun insertSeries(rule: RecurrenceRule, anchorDueAt: Long, at: Long): SeriesEntity =
        seriesEntity(UUID.randomUUID().toString(), rule, anchorDueAt, at, at).also { seriesDao.upsert(it) }

    private fun seriesEntity(id: String, rule: RecurrenceRule, anchorDueAt: Long, createdAt: Long, at: Long) = SeriesEntity(
        id = id, frequency = rule.frequency.name, repeatInterval = rule.interval,
        daysOfWeek = DaysOfWeekMask.toMask(rule.daysOfWeek), basis = rule.basis.name,
        anchorDueAt = anchorDueAt, createdAt = createdAt, updatedAt = at,
    )

    override fun observeChecklist(taskId: String): Flow<List<ChecklistItem>> =
        checklistDao.observeForTask(taskId).map { list -> list.map(ChecklistItemEntity::toModel) }

    override suspend fun addChecklistItem(taskId: String, text: String) {
        val clean = ChecklistRules.validateText(text)
        val at = now()
        val task = database.withTransaction {
            val touched = touchTask(taskId, at) ?: return@withTransaction null
            val active = checklistDao.activeForTask(taskId)
            require(active.size < ChecklistRules.MAX_ITEMS) { "Use até ${ChecklistRules.MAX_ITEMS} itens por tarefa." }
            checklistDao.upsert(ChecklistItemEntity(UUID.randomUUID().toString(), taskId, clean,
                position = (active.maxOfOrNull { it.position } ?: -1) + 1, createdAt = at, updatedAt = at))
            touched
        } ?: return
        notify(task)
    }

    override suspend fun setChecklistItemChecked(itemId: String, checked: Boolean): Boolean {
        val at = now()
        val task = database.withTransaction {
            val item = activeItem(itemId) ?: return@withTransaction null
            if ((item.checkedAt != null) == checked) return@withTransaction null
            val touched = touchTask(item.taskId, at) ?: return@withTransaction null
            checklistDao.upsert(item.copy(checkedAt = if (checked) at else null, updatedAt = at))
            touched
        } ?: return false
        notify(task)
        return true
    }

    override suspend fun editChecklistItem(itemId: String, text: String) {
        val clean = ChecklistRules.validateText(text)
        val at = now()
        val task = database.withTransaction {
            val item = activeItem(itemId) ?: return@withTransaction null
            val touched = touchTask(item.taskId, at) ?: return@withTransaction null
            checklistDao.upsert(item.copy(text = clean, updatedAt = at))
            touched
        } ?: return
        notify(task)
    }

    override suspend fun deleteChecklistItem(itemId: String) {
        val at = now()
        val task = database.withTransaction {
            val item = activeItem(itemId) ?: return@withTransaction null
            val touched = touchTask(item.taskId, at) ?: return@withTransaction null
            checklistDao.upsert(item.copy(deletedAt = at, updatedAt = at))
            touched
        } ?: return
        notify(task)
    }

    override suspend fun restoreChecklistItem(itemId: String) {
        val at = now()
        val task = database.withTransaction {
            val item = checklistDao.findById(itemId)?.takeIf { it.deletedAt != null } ?: return@withTransaction null
            val touched = touchTask(item.taskId, at) ?: return@withTransaction null
            // The same limit as add: an undo must not push a task past MAX_ITEMS.
            val others = checklistDao.activeForTask(item.taskId).filter { it.id != itemId }
            require(others.size < ChecklistRules.MAX_ITEMS) { "Use até ${ChecklistRules.MAX_ITEMS} itens por tarefa." }
            val restored = item.copy(deletedAt = null, updatedAt = at)
            checklistDao.upsert(restored)
            // Back at its old index: the active items keep their order, the restored one is placed by its position.
            renumber(ChecklistRules.placeAt(others.map { it.id }, itemId, item.position), others + restored, at)
            touched
        } ?: return
        notify(task)
    }

    override suspend fun moveChecklistItem(itemId: String, newIndex: Int) {
        val at = now()
        val task = database.withTransaction {
            val item = activeItem(itemId) ?: return@withTransaction null
            val touched = touchTask(item.taskId, at) ?: return@withTransaction null
            val items = checklistDao.activeForTask(item.taskId)
            renumber(ChecklistRules.placeAt(items.map { it.id }, itemId, newIndex), items, at)
            touched
        } ?: return
        notify(task)
    }

    private suspend fun activeItem(itemId: String): ChecklistItemEntity? =
        checklistDao.findById(itemId)?.takeIf { it.deletedAt == null }

    /** Sets `updatedAt` on a live task (every checklist write is a task change, SPEC 013). Null if it is gone. */
    private suspend fun touchTask(taskId: String, at: Long): TaskEntity? {
        val task = taskDao.findById(taskId)?.takeIf { it.deletedAt == null } ?: return null
        return task.copy(updatedAt = at).also { taskDao.upsert(it) }
    }

    /** Writes `position = index` for items whose index changed; [order] lists every item id in the new order. */
    private suspend fun renumber(order: List<String>, items: List<ChecklistItemEntity>, at: Long) {
        val byId = items.associateBy { it.id }
        order.forEachIndexed { index, id ->
            val item = byId.getValue(id)
            if (item.position != index) checklistDao.upsert(item.copy(position = index, updatedAt = at))
        }
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

/** Rows written by one transaction, returned out of it so change notifications run after commit. */
private class Mutation(val changed: List<TaskEntity>, val next: TaskEntity? = null) {
    companion object {
        val NONE = Mutation(emptyList())
    }
}
