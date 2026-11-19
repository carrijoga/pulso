package dev.pulso.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pulso.app.domain.Habit
import dev.pulso.app.domain.HabitDraft
import dev.pulso.app.domain.HabitScheduleType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * SPEC 009 repository contract, against an in-memory database. Not run in WP2 (the emulator is
 * shared); compile-checked with `:app:compileDebugAndroidTestKotlin`.
 */
@RunWith(AndroidJUnit4::class)
class LocalHabitRepositoryTest {
    private lateinit var db: PulsoDatabase
    private lateinit var repo: LocalHabitRepository
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val clock = FakeClock(LocalDateTime.of(2026, 11, 19, 8, 0).atZone(zone).toInstant().toEpochMilli())
    private val today = LocalDate.of(2026, 11, 19)

    @Before fun setUp() {
        db = inMemoryDatabase()
        repo = LocalHabitRepository(db, { clock.millis }, zone)
    }

    @After fun tearDown() = db.close()

    private suspend fun habitNamed(name: String): Habit =
        repo.observeHabits(includeArchived = true).first().single { it.name == name }

    private suspend fun createAndGet(name: String, draft: HabitDraft = HabitDraft(name)): Habit {
        repo.createHabit(draft)
        return habitNamed(name)
    }

    @Test fun checkUncheckAndRecheckReuseTheSameRow() = runTest {
        val habit = createAndGet("Ler")
        repo.setChecked(habit.id, today, true)
        val first = db.habitCheckinDao().allForBackup().single()

        repo.setChecked(habit.id, today, false)
        val tombstone = db.habitCheckinDao().allForBackup().single()
        assertEquals(first.id, tombstone.id)
        assertTrue(tombstone.deletedAt != null)
        assertTrue(repo.observeCheckins(today, today).first().isEmpty())

        repo.setChecked(habit.id, today, true)
        val rows = db.habitCheckinDao().allForBackup()
        assertEquals(1, rows.size)
        assertEquals(first.id, rows.single().id)
        assertEquals(null, rows.single().deletedAt)
    }

    @Test fun checkingTwiceDoesNotDuplicateAndUncheckingTwiceIsIdempotent() = runTest {
        val habit = createAndGet("Ler")
        repo.setChecked(habit.id, today, true)
        repo.setChecked(habit.id, today, true)
        assertEquals(1, db.habitCheckinDao().allForBackup().size)

        repo.setChecked(habit.id, today, false)
        repo.setChecked(habit.id, today, false)
        assertEquals(1, db.habitCheckinDao().allForBackup().size)
        assertTrue(repo.observeCheckins(today, today).first().isEmpty())
    }

    @Test fun futureDatesAndDatesOlderThanSevenDaysAreRejected() = runTest {
        val habit = createAndGet("Ler")
        assertRejected { repo.setChecked(habit.id, today.plusDays(1), true) }
        assertRejected { repo.setChecked(habit.id, today.minusDays(8), true) }
        assertEquals(0, db.habitCheckinDao().allForBackup().size)

        repo.setChecked(habit.id, today.minusDays(7), true) // exactly seven days back is accepted
        assertEquals(1, db.habitCheckinDao().allForBackup().size)
    }

    @Test fun deleteKeepsCheckinsAndRestoreBringsThemBack() = runTest {
        val habit = createAndGet("Ler")
        repo.setChecked(habit.id, today, true)

        repo.deleteHabit(habit.id)
        assertTrue(repo.observeHabits(includeArchived = true).first().isEmpty())
        assertEquals(1, db.habitCheckinDao().allForBackup().count { it.deletedAt == null })

        repo.restoreHabit(habit.id)
        assertEquals(1, repo.observeHabits().first().size)
        assertEquals(1, repo.observeCheckins(today, today).first().size)
    }

    @Test fun checkingADeletedHabitWritesNothing() = runTest {
        val habit = createAndGet("Ler")
        repo.deleteHabit(habit.id)
        repo.setChecked(habit.id, today, true)
        assertEquals(0, db.habitCheckinDao().allForBackup().size)
    }

    @Test fun archivedHabitsAreHiddenByDefaultAndCannotBeChecked() = runTest {
        val habit = createAndGet("Ler")
        repo.setArchived(habit.id, true)
        assertTrue(repo.observeHabits().first().isEmpty())
        assertTrue(repo.observeHabits(includeArchived = true).first().single().archived)

        repo.setChecked(habit.id, today, true)
        assertEquals(0, db.habitCheckinDao().allForBackup().size)

        repo.setArchived(habit.id, false)
        assertEquals(1, repo.observeHabits().first().size)
    }

    @Test fun createStoresScheduleAndAppendsPosition() = runTest {
        createAndGet("Ler")
        val weekly = createAndGet("Academia", HabitDraft("Academia", scheduleType = HabitScheduleType.TIMES_PER_WEEK,
            timesPerWeek = 3))
        val days = createAndGet("Yoga", HabitDraft("Yoga", scheduleType = HabitScheduleType.DAYS_OF_WEEK,
            daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), notes = "  na sala  "))

        assertEquals(3, weekly.timesPerWeek)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), days.daysOfWeek)
        assertEquals("na sala", days.notes)
        assertEquals(listOf("Ler", "Academia", "Yoga"), repo.observeHabits().first().map { it.name })
    }

    @Test fun invalidDraftIsRejectedAndWritesNothing() = runTest {
        assertRejected { repo.createHabit(HabitDraft("   ")) }
        assertRejected { repo.createHabit(HabitDraft("Yoga", scheduleType = HabitScheduleType.DAYS_OF_WEEK)) }
        assertTrue(db.habitDao().allForBackup().isEmpty())
    }

    @Test fun editKeepsCreationDateHistoryAndPositionButChangesSchedule() = runTest {
        val habit = createAndGet("Ler")
        repo.setChecked(habit.id, today, true)
        val stored = db.habitDao().findById(habit.id)!!

        clock.advance(86_400_000L)
        repo.editHabit(habit.id, HabitDraft("Ler mais", "vinte minutos", HabitScheduleType.DAYS_OF_WEEK,
            daysOfWeek = setOf(DayOfWeek.SATURDAY)))

        val edited = db.habitDao().findById(habit.id)!!
        assertEquals("Ler mais", edited.name)
        assertEquals(stored.createdAt, edited.createdAt)
        assertEquals(stored.position, edited.position)
        assertTrue(edited.updatedAt > stored.updatedAt)
        assertEquals(1, db.habitCheckinDao().allForBackup().size)
    }

    @Test fun moveRewritesPositionsAndKeepsTheOrder() = runTest {
        val a = createAndGet("A")
        val b = createAndGet("B")
        val c = createAndGet("C")

        repo.move(c.id, 0) // [C, A, B]
        assertEquals(listOf("C", "A", "B"), repo.observeHabits().first().map { it.name })

        repo.move(a.id, 2) // [C, B, A]
        assertEquals(listOf("C", "B", "A"), repo.observeHabits().first().map { it.name })
        assertEquals(listOf(0, 1, 2), listOf(c.id, b.id, a.id).map { db.habitDao().findById(it)!!.position })
    }

    @Test fun moveWithUnknownIdIsNoopAndArchivedHabitsKeepTheirPlace() = runTest {
        val a = createAndGet("A")
        createAndGet("B")
        repo.move("unknown", 0)
        assertEquals(listOf("A", "B"), repo.observeHabits(includeArchived = true).first().map { it.name })
        repo.setArchived(a.id, true)
        repo.move(a.id, 1) // archived habits keep their place in the full order
        assertEquals(listOf("B", "A"), repo.observeHabits(includeArchived = true).first().map { it.name })
    }

    @Test fun checkinsRangeIsInclusiveAndIgnoresOtherDays() = runTest {
        val habit = createAndGet("Ler")
        repo.setChecked(habit.id, today.minusDays(2), true)
        repo.setChecked(habit.id, today.minusDays(1), true)
        repo.setChecked(habit.id, today, true)

        assertEquals(listOf(today.minusDays(1), today),
            repo.observeCheckins(today.minusDays(1), today).first().map { it.date })
    }

    private suspend fun assertRejected(block: suspend () -> Unit) {
        try {
            block()
            fail("Esperava IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }
}
