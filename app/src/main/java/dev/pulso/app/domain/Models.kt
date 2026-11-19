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
) {
    val isCompleted: Boolean get() = completedAt != null
}

data class TaskDraft(
    val title: String,
    val notes: String = "",
    val category: String = TaskRules.DEFAULT_CATEGORY,
    val priority: Priority = Priority.NORMAL,
    val dueAt: Long? = null,
    val links: String = "",
)

data class Category(val id: String, val name: String)

/** History entry: a task's due date moved from [fromDueAt] to [toDueAt]. */
data class RescheduleEntry(val id: String, val taskId: String, val fromDueAt: Long, val toDueAt: Long, val changedAt: Long)
