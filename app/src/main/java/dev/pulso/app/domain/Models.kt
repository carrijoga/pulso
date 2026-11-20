package dev.pulso.app.domain

enum class Priority(val label: String) { LOW("Baixa"), NORMAL("Normal"), HIGH("Alta") }
enum class InboxStatus { INBOX, CONVERTED, ARCHIVED }

data class InboxItem(
    val id: String,
    val text: String,
    val status: InboxStatus,
    val createdAt: Long,
    val updatedAt: Long,
    val convertedTaskId: String? = null,
)

data class TaskItem(
    val id: String,
    val title: String,
    val notes: String,
    val category: String,
    val priority: Priority,
    val dueAt: Long?,
    val completedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    /** One URL/reference per line. */
    val links: String = "",
    /** When set, the reminder fires at this instant instead of [dueAt]; the due date is unchanged. */
    val snoozedUntil: Long? = null,
    /** How many times the due date was moved to another date (see [RescheduleEntry]). */
    val rescheduleCount: Int = 0,
    /** v3: series this task belongs to; null = standalone. */
    val seriesId: String? = null,
    /** v3: rule of [seriesId], filled by the repository (SPEC 008). */
    val recurrence: RecurrenceRule? = null,
    /** v3: first occurrence of [seriesId]; the monthly/yearly label uses its day (not the reschedules). */
    val recurrenceAnchorDueAt: Long? = null,
    /** v3 (SPEC 013): active checklist items in order; filled by the repository. */
    val checklist: List<ChecklistItem> = emptyList(),
    /** v4 (SPEC 015): mother task when this is a subtask; null = independent. */
    val parentId: String? = null,
    /** v4: title of the mother, filled by the repository when the mother is live. */
    val parentTitle: String? = null,
    /** v4: live subtasks of this task and how many are completed; filled by the repository. */
    val subtaskTotal: Int = 0,
    val subtaskDone: Int = 0,
) {
    val isCompleted: Boolean get() = completedAt != null
    val openSubtasks: Int get() = subtaskTotal - subtaskDone
}

data class TaskDraft(
    val title: String,
    val notes: String = "",
    val category: String = TaskRules.DEFAULT_CATEGORY,
    val priority: Priority = Priority.NORMAL,
    val dueAt: Long? = null,
    val links: String = "",
    val recurrence: RecurrenceRule? = null,
    /** Initial checklist texts. Used only when creating a task; later edits go through the checklist methods. */
    val checklist: List<String> = emptyList(),
    /** v4 (SPEC 015): mother task, or null for an independent task. */
    val parentId: String? = null,
)

data class Category(val id: String, val name: String)

/** History entry: a task's due date moved from [fromDueAt] to [toDueAt]. */
data class RescheduleEntry(val id: String, val taskId: String, val fromDueAt: Long, val toDueAt: Long, val changedAt: Long)
