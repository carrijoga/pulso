package dev.pulso.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.pulso.app.domain.CategoryRules
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface InboxDao {
    @Query("SELECT * FROM inbox_items WHERE status = 'INBOX' AND deletedAt IS NULL ORDER BY createdAt DESC")
    fun observePending(): Flow<List<InboxEntity>>

    /** Pending and archived captures (converted ones are represented by their task). */
    @Query("SELECT * FROM inbox_items WHERE status IN ('INBOX', 'ARCHIVED') AND deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeSearchable(): Flow<List<InboxEntity>>

    @Query("SELECT * FROM inbox_items WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): InboxEntity?

    @Query("SELECT * FROM inbox_items")
    suspend fun allForBackup(): List<InboxEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: InboxEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun importUnseen(items: List<InboxEntity>)
}

@Dao
interface TaskDao {
    @Query("""SELECT t.*, (SELECT COUNT(*) FROM task_reschedules r WHERE r.taskId = t.id) AS rescheduleCount
        FROM task_items t WHERE t.deletedAt IS NULL
        ORDER BY t.completedAt IS NOT NULL ASC, t.dueAt IS NULL ASC, t.dueAt ASC, t.createdAt DESC""")
    fun observeActiveAndCompleted(): Flow<List<TaskWithStats>>

    @Query("SELECT * FROM task_items WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): TaskEntity?

    /** Open (not completed, not deleted) occurrences of a series; SPEC 008 keeps at most one. */
    @Query("SELECT * FROM task_items WHERE seriesId = :seriesId AND deletedAt IS NULL AND completedAt IS NULL")
    suspend fun findOpenBySeries(seriesId: String): List<TaskEntity>

    @Query("SELECT * FROM task_items")
    suspend fun allForBackup(): List<TaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun importUnseen(items: List<TaskEntity>): List<Long>

    @Query("UPDATE task_items SET category = :newName, updatedAt = :now WHERE category = :oldName AND deletedAt IS NULL")
    suspend fun renameCategory(oldName: String, newName: String, now: Long)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun observeActive(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE deletedAt IS NULL")
    suspend fun active(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): CategoryEntity?

    @Query("SELECT * FROM categories")
    suspend fun allForBackup(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun importUnseen(items: List<CategoryEntity>)
}

@Dao
interface RescheduleDao {
    @Query("SELECT * FROM task_reschedules WHERE taskId = :taskId ORDER BY changedAt DESC")
    fun observeForTask(taskId: String): Flow<List<RescheduleEntity>>

    @Query("SELECT * FROM task_reschedules")
    suspend fun allForBackup(): List<RescheduleEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: RescheduleEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun importUnseen(items: List<RescheduleEntity>)
}

@Dao
interface SeriesDao {
    @Query("SELECT * FROM task_series WHERE deletedAt IS NULL")
    fun observeActive(): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM task_series WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): SeriesEntity?

    @Query("SELECT * FROM task_series")
    suspend fun allForBackup(): List<SeriesEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SeriesEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun importUnseen(items: List<SeriesEntity>)
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE deletedAt IS NULL ORDER BY position, createdAt")
    fun observeActive(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): HabitEntity?

    @Query("SELECT * FROM habits")
    suspend fun allForBackup(): List<HabitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: HabitEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun importUnseen(items: List<HabitEntity>)
}

/**
 * Check-ins of habits, one row per (habitId, date), enforced by a UNIQUE index.
 *
 * Never use `REPLACE` here: on SQLite, REPLACE resolves a unique-index conflict by DELETING the
 * existing row, which would silently destroy a tombstoned check-in (data loss, see AGENTS.md).
 *
 * Required write pattern (WP2 `setChecked`):
 * 1. `findByHabitAndDate(habitId, date)`, which also returns tombstoned rows;
 * 2. if it exists, `update(copy)` to restore (`deletedAt = null`) or to apply a tombstone
 *    (`deletedAt = now`), keeping the same `id`;
 * 3. if it does not exist, `insert(new)` with a new UUID.
 * A plain `insert` of a second UUID for the same (habitId, date) throws instead of replacing.
 */
@Dao
interface HabitCheckinDao {
    @Query("SELECT * FROM habit_checkins WHERE deletedAt IS NULL")
    fun observeActive(): Flow<List<HabitCheckinEntity>>

    /** Active check-ins with `from <= date <= to` (inclusive, ISO yyyy-MM-dd text comparison). */
    @Query("SELECT * FROM habit_checkins WHERE deletedAt IS NULL AND date BETWEEN :from AND :to ORDER BY date")
    fun observeBetween(from: String, to: String): Flow<List<HabitCheckinEntity>>

    @Query("SELECT * FROM habit_checkins WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): HabitCheckinEntity?

    /** Returns the row for this habit and day even when it is tombstoned (`deletedAt` set). */
    @Query("SELECT * FROM habit_checkins WHERE habitId = :habitId AND date = :date LIMIT 1")
    suspend fun findByHabitAndDate(habitId: String, date: String): HabitCheckinEntity?

    @Query("SELECT * FROM habit_checkins")
    suspend fun allForBackup(): List<HabitCheckinEntity>

    /** ABORT: a conflicting (habitId, date) with another UUID fails and leaves the existing row intact. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: HabitCheckinEntity)

    /** Updates the row with the same `id`; use it to restore or tombstone (see the class KDoc). */
    @Update
    suspend fun update(item: HabitCheckinEntity)

    /** IGNORE also skips a duplicate (habitId, date) under another UUID: the local row wins. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun importUnseen(items: List<HabitCheckinEntity>)
}

@Dao
interface ChecklistDao {
    @Query("SELECT * FROM checklist_items WHERE taskId = :taskId AND deletedAt IS NULL ORDER BY position")
    fun observeForTask(taskId: String): Flow<List<ChecklistItemEntity>>

    /** Active items of a task, used to copy the checklist into the next occurrence (SPEC 008). */
    @Query("SELECT * FROM checklist_items WHERE taskId = :taskId AND deletedAt IS NULL ORDER BY position")
    suspend fun activeForTask(taskId: String): List<ChecklistItemEntity>

    @Query("SELECT * FROM checklist_items WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ChecklistItemEntity?

    @Query("SELECT * FROM checklist_items")
    suspend fun allForBackup(): List<ChecklistItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ChecklistItemEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun importUnseen(items: List<ChecklistItemEntity>)
}

@Database(
    entities = [
        InboxEntity::class, TaskEntity::class, CategoryEntity::class, RescheduleEntity::class,
        SeriesEntity::class, HabitEntity::class, HabitCheckinEntity::class, ChecklistItemEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class PulsoDatabase : RoomDatabase() {
    abstract fun inboxDao(): InboxDao
    abstract fun taskDao(): TaskDao
    abstract fun categoryDao(): CategoryDao
    abstract fun rescheduleDao(): RescheduleDao
    abstract fun seriesDao(): SeriesDao
    abstract fun habitDao(): HabitDao
    abstract fun habitCheckinDao(): HabitCheckinDao
    abstract fun checklistDao(): ChecklistDao

    companion object {
        val ALL_MIGRATIONS: Array<Migration> = arrayOf(Migration1To2, Migration2To3)
    }
}

/**
 * v1 -> v2: adds task links and snooze, the category list and reschedule history.
 * Purely additive: existing rows are kept untouched; categories are seeded with the defaults
 * plus every category name already used by a task.
 */
object Migration1To2 : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE task_items ADD COLUMN links TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE task_items ADD COLUMN snoozedUntil INTEGER")
        db.execSQL("""CREATE TABLE IF NOT EXISTS categories (id TEXT NOT NULL, name TEXT NOT NULL,
            createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, deletedAt INTEGER, PRIMARY KEY(id))""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS task_reschedules (id TEXT NOT NULL, taskId TEXT NOT NULL,
            fromDueAt INTEGER NOT NULL, toDueAt INTEGER NOT NULL, changedAt INTEGER NOT NULL, PRIMARY KEY(id))""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_task_reschedules_taskId ON task_reschedules (taskId)")
        val used = mutableListOf<String>()
        db.query("SELECT DISTINCT category FROM task_items WHERE deletedAt IS NULL").use { cursor ->
            while (cursor.moveToNext()) used += cursor.getString(0)
        }
        seedCategories(db, CategoryRules.DEFAULTS + used)
    }
}

/** v2 -> v3: recurrence series, habits, check-ins and checklist. Purely additive (SPEC 012). */
object Migration2To3 : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE task_items ADD COLUMN seriesId TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_task_items_seriesId ON task_items (seriesId)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS task_series (id TEXT NOT NULL, frequency TEXT NOT NULL,
            repeatInterval INTEGER NOT NULL, daysOfWeek INTEGER NOT NULL, basis TEXT NOT NULL,
            anchorDueAt INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
            deletedAt INTEGER, PRIMARY KEY(id))""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS habits (id TEXT NOT NULL, name TEXT NOT NULL,
            notes TEXT NOT NULL DEFAULT '', scheduleType TEXT NOT NULL, daysOfWeek INTEGER NOT NULL DEFAULT 0,
            timesPerWeek INTEGER NOT NULL DEFAULT 0, reminderMinutes INTEGER, position INTEGER NOT NULL DEFAULT 0,
            archivedAt INTEGER, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, deletedAt INTEGER,
            PRIMARY KEY(id))""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS habit_checkins (id TEXT NOT NULL, habitId TEXT NOT NULL,
            date TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, deletedAt INTEGER,
            PRIMARY KEY(id))""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_habit_checkins_habitId ON habit_checkins (habitId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_habit_checkins_habitId_date ON habit_checkins (habitId, date)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS checklist_items (id TEXT NOT NULL, taskId TEXT NOT NULL,
            text TEXT NOT NULL, position INTEGER NOT NULL, checkedAt INTEGER, createdAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL, deletedAt INTEGER, PRIMARY KEY(id))""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_checklist_items_taskId ON checklist_items (taskId)")
    }
}

/** Inserts each distinct (case/accent-insensitive) non-blank name once. */
internal fun seedCategories(db: SupportSQLiteDatabase, names: List<String>, now: Long = System.currentTimeMillis()) {
    val seen = mutableSetOf<String>()
    names.map { it.trim() }.filter { it.isNotEmpty() && it.length <= CategoryRules.MAX_NAME }.forEach { name ->
        if (seen.add(dev.pulso.app.domain.SearchRules.fold(name))) {
            db.execSQL("INSERT INTO categories (id, name, createdAt, updatedAt, deletedAt) VALUES (?, ?, ?, ?, NULL)",
                arrayOf<Any>(UUID.randomUUID().toString(), name, now, now))
        }
    }
}

/** Fresh installs start with the default categories. */
object SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        seedCategories(db, CategoryRules.DEFAULTS)
    }
}
