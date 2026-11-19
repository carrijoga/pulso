package dev.pulso.app.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.pulso.app.domain.Category
import dev.pulso.app.domain.ChecklistItem
import dev.pulso.app.domain.DaysOfWeekMask
import dev.pulso.app.domain.Habit
import dev.pulso.app.domain.HabitCheckin
import dev.pulso.app.domain.HabitScheduleType
import dev.pulso.app.domain.RecurrenceBasis
import dev.pulso.app.domain.RecurrenceFrequency
import dev.pulso.app.domain.RecurrenceRule
import dev.pulso.app.domain.TaskSeries
import java.time.LocalDate
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

@Entity(tableName = "task_items", indices = [Index("seriesId")])
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
    /** v3: owning recurring series; null = standalone task. No FOREIGN KEY (ADR 0008). */
    val seriesId: String? = null,
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

/** v3 (SPEC 008). */
@Entity(tableName = "task_series")
data class SeriesEntity(
    @PrimaryKey val id: String,
    val frequency: String,
    val repeatInterval: Int,
    val daysOfWeek: Int,
    val basis: String,
    val anchorDueAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** v3 (SPEC 009). */
@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(defaultValue = "''") val notes: String = "",
    val scheduleType: String,
    @ColumnInfo(defaultValue = "0") val daysOfWeek: Int = 0,
    @ColumnInfo(defaultValue = "0") val timesPerWeek: Int = 0,
    val reminderMinutes: Int? = null,
    @ColumnInfo(defaultValue = "0") val position: Int = 0,
    val archivedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** v3 (SPEC 009): one row per habit and local day; unchecking is a tombstone. */
@Entity(
    tableName = "habit_checkins",
    indices = [Index("habitId"), Index("habitId", "date", unique = true)],
)
data class HabitCheckinEntity(
    @PrimaryKey val id: String,
    val habitId: String,
    val date: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** v3 (SPEC 013). */
@Entity(tableName = "checklist_items", indices = [Index("taskId")])
data class ChecklistItemEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val text: String,
    val position: Int,
    val checkedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
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
    links, snoozedUntil, rescheduleCount, seriesId,
)

fun TaskWithStats.toModel(): TaskItem = task.toModel(rescheduleCount)

fun CategoryEntity.toModel(): Category = Category(id, name)

fun RescheduleEntity.toModel(): RescheduleEntry = RescheduleEntry(id, taskId, fromDueAt, toDueAt, changedAt)

fun SeriesEntity.toRule(): RecurrenceRule = RecurrenceRule(
    RecurrenceFrequency.valueOf(frequency), repeatInterval, DaysOfWeekMask.fromMask(daysOfWeek),
    RecurrenceBasis.valueOf(basis),
)

fun SeriesEntity.toModel(): TaskSeries = TaskSeries(id, toRule(), anchorDueAt)

fun HabitEntity.toModel(): Habit = Habit(
    id, name, notes, HabitScheduleType.valueOf(scheduleType), DaysOfWeekMask.fromMask(daysOfWeek),
    timesPerWeek, reminderMinutes, position, archivedAt != null, createdAt,
)

fun HabitCheckinEntity.toModel(): HabitCheckin = HabitCheckin(id, habitId, LocalDate.parse(date))

fun ChecklistItemEntity.toModel(): ChecklistItem = ChecklistItem(id, taskId, text, position, checkedAt != null)
