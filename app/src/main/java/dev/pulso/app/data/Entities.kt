package dev.pulso.app.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.pulso.app.domain.Category
import dev.pulso.app.domain.InboxItem
import dev.pulso.app.domain.InboxStatus
import dev.pulso.app.domain.Priority
import dev.pulso.app.domain.RescheduleEntry
import dev.pulso.app.domain.TaskItem

@Entity(tableName = "inbox_items")
data class InboxEntity(
    @PrimaryKey val id: String,
    val text: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val convertedTaskId: String? = null,
    val deletedAt: Long? = null,
)

@Entity(tableName = "task_items")
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val notes: String,
    val category: String,
    val priority: String,
    val dueAt: Long?,
    val completedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    /** v2: one link per line. */
    @ColumnInfo(defaultValue = "''") val links: String = "",
    /** v2: reminder postponed to this instant; cleared when the due date changes or the task completes. */
    val snoozedUntil: Long? = null,
)

/** v2: user-managed category list. Tasks keep the category *name* (text) for backup compatibility. */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** v2: append-only history of due-date changes, used by the weekly review. */
@Entity(tableName = "task_reschedules", indices = [Index("taskId")])
data class RescheduleEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val fromDueAt: Long,
    val toDueAt: Long,
    val changedAt: Long,
)

data class TaskWithStats(
    @Embedded val task: TaskEntity,
    val rescheduleCount: Int,
)

fun InboxEntity.toModel(): InboxItem = InboxItem(
    id, text, InboxStatus.valueOf(status), createdAt, updatedAt, convertedTaskId
)

fun TaskEntity.toModel(rescheduleCount: Int = 0): TaskItem = TaskItem(
    id, title, notes, category, Priority.valueOf(priority), dueAt, completedAt, createdAt, updatedAt,
    links, snoozedUntil, rescheduleCount,
)

fun TaskWithStats.toModel(): TaskItem = task.toModel(rescheduleCount)

fun CategoryEntity.toModel(): Category = Category(id, name)

fun RescheduleEntity.toModel(): RescheduleEntry = RescheduleEntry(id, taskId, fromDueAt, toDueAt, changedAt)
