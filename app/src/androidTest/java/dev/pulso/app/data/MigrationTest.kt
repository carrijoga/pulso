package dev.pulso.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PulsoDatabase::class.java)

    @Test fun migrate1To2KeepsEveryRowAndSeedsUsedCategories() {
        helper.createDatabase(DB, 1).apply {
            execSQL("INSERT INTO inbox_items (id, text, status, createdAt, updatedAt, convertedTaskId, deletedAt) " +
                "VALUES ('i1', 'Ideia', 'INBOX', 1, 1, NULL, NULL)")
            execSQL("INSERT INTO task_items (id, title, notes, category, priority, dueAt, completedAt, createdAt, updatedAt, deletedAt) " +
                "VALUES ('t1', 'Bolo', '', 'Casamento', 'HIGH', 100, NULL, 1, 1, NULL)")
            execSQL("INSERT INTO task_items (id, title, notes, category, priority, dueAt, completedAt, createdAt, updatedAt, deletedAt) " +
                "VALUES ('t2', 'Velha', 'n', 'Lixo', 'LOW', NULL, 5, 1, 1, 9)")
            close()
        }
        // Validates the migrated schema against the v2 entities (exported 2.json).
        val db = helper.runMigrationsAndValidate(DB, 2, true, Migration1To2)

        db.query("SELECT id, category, dueAt, links, snoozedUntil FROM task_items ORDER BY id").use { c ->
            assertEquals(2, c.count)
            c.moveToFirst()
            assertEquals("t1", c.getString(0))
            assertEquals("Casamento", c.getString(1))
            assertEquals(100L, c.getLong(2))
            assertEquals("", c.getString(3))
            assertTrue(c.isNull(4))
        }
        db.query("SELECT COUNT(*) FROM inbox_items").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        val names = mutableListOf<String>()
        db.query("SELECT name FROM categories").use { c -> while (c.moveToNext()) names += c.getString(0) }
        // Defaults + names used by active tasks; deleted tasks' categories are not resurrected.
        assertEquals(setOf("Pessoal", "Compras", "Estudos", "Casa", "Trabalho", "Casamento"), names.toSet())
        assertEquals(names.size, names.toSet().size)
    }

    private fun insertV2Edge(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO inbox_items (id, text, status, createdAt, updatedAt, convertedTaskId, deletedAt) " +
            "VALUES ('i1', 'Ideia', 'INBOX', 1, 1, NULL, NULL)")
        db.execSQL("INSERT INTO inbox_items (id, text, status, createdAt, updatedAt, convertedTaskId, deletedAt) " +
            "VALUES ('i2', 'Virou tarefa', 'CONVERTED', 1, 2, 't1', NULL)")
        // Edge: null dueAt/snoozedUntil, empty links.
        db.execSQL("INSERT INTO task_items (id, title, notes, category, priority, dueAt, completedAt, createdAt, updatedAt, deletedAt, links, snoozedUntil) " +
            "VALUES ('t1', 'Café da manhã', '', 'Açúcar', 'NORMAL', NULL, NULL, 1, 2, NULL, '', NULL)")
        // Edge: tombstone, with dates and snooze.
        db.execSQL("INSERT INTO task_items (id, title, notes, category, priority, dueAt, completedAt, createdAt, updatedAt, deletedAt, links, snoozedUntil) " +
            "VALUES ('t2', 'Velha', 'n', 'Casa', 'LOW', 100, 5, 1, 1, 9, 'https://x.test', 200)")
        db.execSQL("INSERT INTO categories (id, name, createdAt, updatedAt, deletedAt) VALUES ('c1', 'Ação', 1, 1, NULL)")
        db.execSQL("INSERT INTO task_reschedules (id, taskId, fromDueAt, toDueAt, changedAt) VALUES ('r1', 't2', 1, 2, 3)")
    }

    private fun assertV2DataIntact(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.query("SELECT id, title, category, dueAt, completedAt, deletedAt, links, snoozedUntil, seriesId FROM task_items ORDER BY id").use { c ->
            assertEquals(2, c.count)
            c.moveToFirst()
            assertEquals("Café da manhã", c.getString(1))
            assertEquals("Açúcar", c.getString(2))
            assertTrue(c.isNull(3)); assertTrue(c.isNull(4)); assertTrue(c.isNull(5))
            assertEquals("", c.getString(6)); assertTrue(c.isNull(7)); assertTrue(c.isNull(8))
            c.moveToNext()
            assertEquals(9L, c.getLong(5)); assertEquals(200L, c.getLong(7)); assertTrue(c.isNull(8))
        }
        db.query("SELECT status, convertedTaskId FROM inbox_items WHERE id = 'i2'").use { c ->
            c.moveToFirst(); assertEquals("CONVERTED", c.getString(0)); assertEquals("t1", c.getString(1))
        }
        db.query("SELECT COUNT(*) FROM categories WHERE name = 'Ação'").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        db.query("SELECT COUNT(*) FROM task_reschedules").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        listOf("task_series", "habits", "habit_checkins", "checklist_items").forEach { table ->
            db.query("SELECT COUNT(*) FROM $table").use { c -> c.moveToFirst(); assertEquals("$table starts empty", 0, c.getInt(0)) }
        }
    }

    @Test fun migrate2To3KeepsEveryRowIncludingEdgeCases() {
        helper.createDatabase(DB, 2).apply { insertV2Edge(this); close() }
        val db = helper.runMigrationsAndValidate(DB, 3, true, Migration2To3)
        assertV2DataIntact(db)
    }

    @Test fun migrate1To3ChainedKeepsData() {
        helper.createDatabase(DB, 1).apply {
            execSQL("INSERT INTO task_items (id, title, notes, category, priority, dueAt, completedAt, createdAt, updatedAt, deletedAt) " +
                "VALUES ('t1', 'Bolo', '', 'Casamento', 'HIGH', 100, NULL, 1, 1, NULL)")
            close()
        }
        val db = helper.runMigrationsAndValidate(DB, 3, true, Migration1To2, Migration2To3)
        db.query("SELECT title, dueAt, links, seriesId FROM task_items").use { c ->
            assertEquals(1, c.count); c.moveToFirst()
            assertEquals("Bolo", c.getString(0)); assertEquals(100L, c.getLong(1))
            assertEquals("", c.getString(2)); assertTrue(c.isNull(3))
        }
    }

    @Test fun everyExportedVersionMigratesToLatestWithAllMigrations() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val versions = context.assets.list("dev.pulso.app.data.PulsoDatabase")!!
            .mapNotNull { it.removeSuffix(".json").toIntOrNull() }.sorted()
        val latest = versions.last()
        assertEquals((1..latest).toList(), versions)
        for (from in versions.dropLast(1)) {
            val name = "all-versions-$from.db"
            helper.createDatabase(name, from).apply {
                execSQL("INSERT INTO task_items (id, title, notes, category, priority, dueAt, completedAt, createdAt, updatedAt, deletedAt) " +
                    "VALUES ('t1', 'Tarefa', '', 'Pessoal', 'NORMAL', NULL, NULL, 1, 1, NULL)")
                close()
            }
            val db = helper.runMigrationsAndValidate(name, latest, true, *PulsoDatabase.ALL_MIGRATIONS)
            db.query("SELECT COUNT(*) FROM task_items").use { c -> c.moveToFirst(); assertEquals("from v$from", 1, c.getInt(0)) }
        }
    }

    @Test fun v3UniqueIndexRejectsDuplicateCheckinDay() {
        helper.createDatabase(DB, 2).close()
        val db = helper.runMigrationsAndValidate(DB, 3, true, Migration2To3)
        db.execSQL("INSERT INTO habit_checkins (id, habitId, date, createdAt, updatedAt) VALUES ('a', 'h', '2026-01-01', 1, 1)")
        val failure = runCatching {
            db.execSQL("INSERT INTO habit_checkins (id, habitId, date, createdAt, updatedAt) VALUES ('b', 'h', '2026-01-01', 1, 1)")
        }.exceptionOrNull()
        assertTrue(failure != null)
    }

    private companion object { const val DB = "migration-test.db" }
}
