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

    private companion object { const val DB = "migration-test.db" }
}
