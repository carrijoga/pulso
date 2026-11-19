package dev.pulso.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
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

@Database(
    entities = [InboxEntity::class, TaskEntity::class, CategoryEntity::class, RescheduleEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class PulsoDatabase : RoomDatabase() {
    abstract fun inboxDao(): InboxDao
    abstract fun taskDao(): TaskDao
    abstract fun categoryDao(): CategoryDao
    abstract fun rescheduleDao(): RescheduleDao

    companion object {
        val ALL_MIGRATIONS: Array<Migration> = arrayOf(Migration1To2)
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
