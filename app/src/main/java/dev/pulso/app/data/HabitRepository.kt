package dev.pulso.app.data

import androidx.room.withTransaction
import dev.pulso.app.domain.DaysOfWeekMask
import dev.pulso.app.domain.Habit
import dev.pulso.app.domain.HabitCheckin
import dev.pulso.app.domain.HabitDraft
import dev.pulso.app.domain.HabitRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Habits and their daily check-ins (SPEC 009). Separate from [TaskRepository] by design (ADR 0006). */
interface HabitRepository {
    /** Non-deleted habits ordered by position; archived ones only when [includeArchived] is true. */
    fun observeHabits(includeArchived: Boolean = false): Flow<List<Habit>>

    /** Active check-ins with `from <= date <= to`. */
    fun observeCheckins(from: LocalDate, to: LocalDate): Flow<List<HabitCheckin>>

    suspend fun createHabit(draft: HabitDraft)
    suspend fun editHabit(id: String, draft: HabitDraft)

    /** Archiving pauses the habit (hidden from Hoje, history kept). Idempotent. */
    suspend fun setArchived(id: String, archived: Boolean)

    /** Tombstone only: check-ins are kept, so [restoreHabit] brings the history back. */
    suspend fun deleteHabit(id: String)
    suspend fun restoreHabit(id: String)

    /**
     * Moves the habit to [newPosition], an index in the list of non-deleted habits in their current
     * order (archived included). Rewrites the positions of every habit whose index changed.
     */
    suspend fun move(id: String, newPosition: Int)

    /**
     * Marks or unmarks the habit for [date]. Only today and the 7 previous days are accepted
     * ([HabitRules.canCheck]). Idempotent; reuses the same row (and UUID) for the same (habit, day).
     * Returns true only if the stored state changed; false for a no-op, a deleted or unknown habit,
     * or an archived habit being checked.
     */
    suspend fun setChecked(habitId: String, date: LocalDate, checked: Boolean): Boolean

    /**
     * Same as [setChecked] for the current local day, read from the clock at the moment of the call.
     * Returns the date written, or null when nothing changed.
     */
    suspend fun setCheckedToday(habitId: String, checked: Boolean): LocalDate?
}

/**
 * Room implementation. Every multi-row write runs in `withTransaction`. Dates of check-ins are
 * local `yyyy-MM-dd` values; "today" is derived from [now] in [zone] at the moment of the action.
 */
class LocalHabitRepository(
    private val database: PulsoDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : HabitRepository {
    private val habitDao get() = database.habitDao()
    private val checkinDao get() = database.habitCheckinDao()

    override fun observeHabits(includeArchived: Boolean): Flow<List<Habit>> =
        habitDao.observeActive().map { list ->
            list.filter { includeArchived || it.archivedAt == null }.map(HabitEntity::toModel)
        }

    override fun observeCheckins(from: LocalDate, to: LocalDate): Flow<List<HabitCheckin>> =
        checkinDao.observeBetween(from.toString(), to.toString()).map { list -> list.map(HabitCheckinEntity::toModel) }

    override suspend fun createHabit(draft: HabitDraft) {
        val valid = HabitRules.validateDraft(draft)
        database.withTransaction {
            val next = (habitDao.activeInOrder().maxOfOrNull { it.position } ?: -1) + 1
            val timestamp = now()
            habitDao.upsert(valid.toEntity(UUID.randomUUID().toString(), position = next, archivedAt = null,
                createdAt = timestamp, updatedAt = timestamp))
        }
    }

    override suspend fun editHabit(id: String, draft: HabitDraft) {
        val valid = HabitRules.validateDraft(draft)
        database.withTransaction {
            val original = habitDao.findById(id) ?: return@withTransaction
            if (original.deletedAt != null) return@withTransaction
            habitDao.upsert(valid.toEntity(original.id, position = original.position, archivedAt = original.archivedAt,
                createdAt = original.createdAt, updatedAt = now()))
        }
    }

    override suspend fun setArchived(id: String, archived: Boolean) {
        database.withTransaction {
            val original = habitDao.findById(id) ?: return@withTransaction
            if (original.deletedAt != null || (original.archivedAt != null) == archived) return@withTransaction
            val timestamp = now()
            habitDao.upsert(original.copy(archivedAt = if (archived) timestamp else null, updatedAt = timestamp))
        }
    }

    override suspend fun deleteHabit(id: String) {
        database.withTransaction {
            val original = habitDao.findById(id) ?: return@withTransaction
            if (original.deletedAt != null) return@withTransaction
            val timestamp = now()
            habitDao.upsert(original.copy(deletedAt = timestamp, updatedAt = timestamp))
        }
    }

    override suspend fun restoreHabit(id: String) {
        database.withTransaction {
            val original = habitDao.findById(id) ?: return@withTransaction
            if (original.deletedAt == null) return@withTransaction
            habitDao.upsert(original.copy(deletedAt = null, updatedAt = now()))
        }
    }

    override suspend fun move(id: String, newPosition: Int) {
        database.withTransaction {
            val habits = habitDao.activeInOrder()
            if (habits.none { it.id == id }) return@withTransaction
            val targets = HabitRules.positionsAfterMove(habits.map { it.id }, id, newPosition)
            val timestamp = now()
            habits.forEach { habit ->
                val target = targets.getValue(habit.id)
                if (habit.position != target) habitDao.upsert(habit.copy(position = target, updatedAt = timestamp))
            }
        }
    }

    override suspend fun setChecked(habitId: String, date: LocalDate, checked: Boolean): Boolean {
        require(HabitRules.canCheck(date, today())) { "Só dá para marcar hoje ou nos 7 dias anteriores." }
        return database.withTransaction {
            val habit = habitDao.findById(habitId) ?: return@withTransaction false
            if (habit.deletedAt != null || (checked && habit.archivedAt != null)) return@withTransaction false
            writeCheckin(habitId, date.toString(), checked)
        }
    }

    override suspend fun setCheckedToday(habitId: String, checked: Boolean): LocalDate? {
        val day = today()
        return if (setChecked(habitId, day, checked)) day else null
    }

    private fun today(): LocalDate = Instant.ofEpochMilli(now()).atZone(zone).toLocalDate()

    /**
     * Never REPLACE here (see [HabitCheckinDao]): find the row for (habit, day) including tombstones,
     * then update it in place, or insert a new row only when none exists. Returns true if it wrote.
     */
    private suspend fun writeCheckin(habitId: String, date: String, checked: Boolean): Boolean {
        val timestamp = now()
        val existing = checkinDao.findByHabitAndDate(habitId, date)
        return when {
            existing == null -> if (checked) {
                checkinDao.insert(HabitCheckinEntity(UUID.randomUUID().toString(), habitId, date,
                    createdAt = timestamp, updatedAt = timestamp))
                true
            } else false
            checked && existing.deletedAt != null -> {
                checkinDao.update(existing.copy(deletedAt = null, updatedAt = timestamp))
                true
            }
            !checked && existing.deletedAt == null -> {
                checkinDao.update(existing.copy(deletedAt = timestamp, updatedAt = timestamp))
                true
            }
            else -> false
        }
    }

    private fun HabitDraft.toEntity(id: String, position: Int, archivedAt: Long?, createdAt: Long, updatedAt: Long) =
        HabitEntity(
            id = id,
            name = name,
            notes = notes,
            scheduleType = scheduleType.name,
            daysOfWeek = DaysOfWeekMask.toMask(daysOfWeek),
            timesPerWeek = timesPerWeek,
            position = position,
            archivedAt = archivedAt,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
}
