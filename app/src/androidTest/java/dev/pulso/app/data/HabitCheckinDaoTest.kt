package dev.pulso.app.data

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** SPEC 009 / ADR 0006: one row per (habitId, date); the write pattern must never REPLACE. */
@RunWith(AndroidJUnit4::class)
class HabitCheckinDaoTest {
    private lateinit var db: PulsoDatabase
    private val now = 1_763_550_000_000L

    @Before fun setUp() { db = inMemoryDatabase() }
    @After fun tearDown() { db.close() }

    private fun checkin(id: String, date: String, deletedAt: Long? = null) =
        HabitCheckinEntity(id, "h1", date, now, now, deletedAt)

    @Test fun tombstoneAndRestoreReuseTheSameRowAndUuid() = runTest {
        val dao = db.habitCheckinDao()
        dao.insert(checkin("c1", "2026-01-05"))

        // Uncheck: tombstone the existing row found by (habitId, date).
        val active = dao.findByHabitAndDate("h1", "2026-01-05")!!
        dao.update(active.copy(deletedAt = now + 1, updatedAt = now + 1))
        assertTrue(dao.observeBetween("2026-01-05", "2026-01-05").first().isEmpty())

        // Check again: restore the same row, not a new one.
        val tombstoned = dao.findByHabitAndDate("h1", "2026-01-05")!!
        assertEquals("c1", tombstoned.id)
        dao.update(tombstoned.copy(deletedAt = null, updatedAt = now + 2))

        val rows = dao.allForBackup()
        assertEquals(1, rows.size)
        assertEquals("c1", rows.single().id)
        assertNull(rows.single().deletedAt)
        assertEquals(1, dao.observeBetween("2026-01-05", "2026-01-05").first().size)
    }

    @Test fun insertOfAnotherUuidOnSameDayThrowsAndKeepsExistingRow() = runTest {
        val dao = db.habitCheckinDao()
        dao.insert(checkin("c1", "2026-01-05"))

        val failure = runCatching { dao.insert(checkin("c2", "2026-01-05")) }.exceptionOrNull()
        assertTrue("expected a unique constraint failure, got $failure", failure is SQLiteConstraintException)

        val rows = dao.allForBackup()
        assertEquals(1, rows.size)
        assertEquals("c1", rows.single().id)
    }

    @Test fun insertOfAnotherUuidOverATombstoneThrowsAndKeepsTombstone() = runTest {
        val dao = db.habitCheckinDao()
        dao.insert(checkin("c1", "2026-01-05", deletedAt = now))

        val failure = runCatching { dao.insert(checkin("c2", "2026-01-05")) }.exceptionOrNull()
        assertTrue(failure is SQLiteConstraintException)

        val row = dao.findByHabitAndDate("h1", "2026-01-05")!!
        assertEquals("c1", row.id)
        assertNotNull(row.deletedAt) // the tombstone was not replaced
    }

    @Test fun observeBetweenIncludesBoundsAndIgnoresTombstones() = runTest {
        val dao = db.habitCheckinDao()
        dao.insert(checkin("before", "2026-01-04"))
        dao.insert(checkin("lower", "2026-01-05"))
        dao.insert(checkin("tombstoned", "2026-01-07", deletedAt = now))
        dao.insert(checkin("upper", "2026-01-10"))
        dao.insert(checkin("after", "2026-01-11"))

        val ids = dao.observeBetween("2026-01-05", "2026-01-10").first().map { it.id }
        assertEquals(listOf("lower", "upper"), ids)
    }

    @Test fun findByHabitAndDateReturnsTombstonedRow() = runTest {
        val dao = db.habitCheckinDao()
        dao.insert(checkin("c1", "2026-01-05", deletedAt = now))

        val row = dao.findByHabitAndDate("h1", "2026-01-05")
        assertNotNull(row)
        assertEquals("c1", row!!.id)
        assertNotNull(row.deletedAt)
        assertNull(dao.findByHabitAndDate("h1", "2026-01-06"))
    }
}
