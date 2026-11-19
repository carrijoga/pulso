package dev.pulso.app.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

data class HabitToday(val habit: Habit, val doneToday: Boolean, val progress: WeekProgress, val streak: Int)

data class WeekProgress(val done: Int, val target: Int)

/** Input of the habit editor; [HabitRules.validateDraft] normalises it before it is written. */
data class HabitDraft(
    val name: String,
    val notes: String = "",
    val scheduleType: HabitScheduleType = HabitScheduleType.DAILY,
    val daysOfWeek: Set<DayOfWeek> = emptySet(),
    val timesPerWeek: Int = 0,
)

/** Pure habit rules (SPEC 009 / ADR 0006): no Android, no persistence, never reads the clock. */
object HabitRules {
    const val MAX_NAME = 60
    const val MAX_NOTES = 500
    const val MAX_BACKDATE_DAYS = 7L

    /** Safety net so a corrupt `createdOn` can never spin forever. */
    private const val MAX_STREAK_DAYS = 20_000L

    private val ptBr: Locale = Locale.forLanguageTag("pt-BR")

    fun validateName(name: String): String = name.trim().replace(Regex("\\s+"), " ").also {
        require(it.isNotEmpty()) { "Dê um nome ao hábito." }
        require(it.length <= MAX_NAME) { "O hábito deve ter até $MAX_NAME caracteres." }
    }

    /**
     * Trims the texts and checks the schedule. Fields that belong to another schedule are cleared,
     * so a habit never stores days for a DAILY schedule (or a weekly count for DAYS_OF_WEEK).
     */
    fun validateDraft(draft: HabitDraft): HabitDraft {
        val name = validateName(draft.name)
        val notes = draft.notes.trim()
        require(notes.length <= MAX_NOTES) { "As observações devem ter até $MAX_NOTES caracteres." }
        return when (draft.scheduleType) {
            HabitScheduleType.DAILY -> draft.copy(name = name, notes = notes, daysOfWeek = emptySet(), timesPerWeek = 0)
            HabitScheduleType.DAYS_OF_WEEK -> {
                require(draft.daysOfWeek.isNotEmpty()) { "Escolha pelo menos um dia da semana." }
                draft.copy(name = name, notes = notes, timesPerWeek = 0)
            }
            HabitScheduleType.TIMES_PER_WEEK -> {
                require(draft.timesPerWeek in 1..7) { "Escolha de 1 a 7 vezes por semana." }
                draft.copy(name = name, notes = notes, daysOfWeek = emptySet())
            }
        }
    }

    /**
     * Ids in their new order after moving [id] to [newPosition] (clamped to the list), mapped to their
     * final index. [newPosition] is the index the habit should end up at in [orderedIds].
     */
    fun positionsAfterMove(orderedIds: List<String>, id: String, newPosition: Int): Map<String, Int> {
        if (id !in orderedIds) return orderedIds.withIndex().associate { it.value to it.index }
        val rest = orderedIds.filter { it != id }.toMutableList()
        rest.add(newPosition.coerceIn(0, rest.size), id)
        return rest.withIndex().associate { it.value to it.index }
    }

    fun isScheduledOn(habit: Habit, date: LocalDate): Boolean = when (habit.scheduleType) {
        HabitScheduleType.DAILY -> true
        HabitScheduleType.DAYS_OF_WEEK -> date.dayOfWeek in habit.daysOfWeek
        HabitScheduleType.TIMES_PER_WEEK -> true
    }

    /** Local creation date of [habit] in [zone]; pass it as `createdOn` to [currentStreak]. */
    fun createdOn(habit: Habit, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(habit.createdAt).atZone(zone).toLocalDate()

    /** Monday of the week containing [date]. */
    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Only today and up to [MAX_BACKDATE_DAYS] days back; never the future. */
    fun canCheck(date: LocalDate, today: LocalDate): Boolean =
        !date.isAfter(today) && ChronoUnit.DAYS.between(date, today) <= MAX_BACKDATE_DAYS

    /** Progress of the week starting at [weekStart]. Check-ins before [createdOn] do not count. */
    fun weekProgress(habit: Habit, checkins: List<HabitCheckin>, weekStart: LocalDate, createdOn: LocalDate): WeekProgress {
        val end = weekStart.plusDays(6)
        val dates = checkins.asSequence()
            .filter { it.habitId == habit.id && it.date in weekStart..end && !it.date.isBefore(createdOn) }
            .map { it.date }.toSet()
        val done = when (habit.scheduleType) {
            HabitScheduleType.DAYS_OF_WEEK -> dates.count { it.dayOfWeek in habit.daysOfWeek }
            HabitScheduleType.DAILY, HabitScheduleType.TIMES_PER_WEEK -> dates.size
        }
        return WeekProgress(done, targetFor(habit, weekStart, createdOn))
    }

    /**
     * Goal of the week starting at [weekStart]. Only the week that contains [createdOn] is prorated,
     * counting from the creation day to Sunday; other weeks keep the full goal. A target of 0 means the
     * week is not part of the goal nor of the streak (DAYS_OF_WEEK with no marked day left).
     */
    private fun targetFor(habit: Habit, weekStart: LocalDate, createdOn: LocalDate): Int {
        val end = weekStart.plusDays(6)
        if (createdOn !in weekStart..end) {
            return when (habit.scheduleType) {
                HabitScheduleType.DAILY -> 7
                HabitScheduleType.DAYS_OF_WEEK -> habit.daysOfWeek.size
                HabitScheduleType.TIMES_PER_WEEK -> habit.timesPerWeek
            }
        }
        val daysLeft = ChronoUnit.DAYS.between(createdOn, end).toInt() + 1
        return when (habit.scheduleType) {
            HabitScheduleType.DAILY -> daysLeft
            HabitScheduleType.DAYS_OF_WEEK -> habit.daysOfWeek.count { it.value >= createdOn.dayOfWeek.value }
            HabitScheduleType.TIMES_PER_WEEK -> minOf(habit.timesPerWeek, daysLeft)
        }
    }

    fun currentStreak(habit: Habit, checkins: List<HabitCheckin>, today: LocalDate, createdOn: LocalDate): Int {
        val dates = checkins.asSequence().filter { it.habitId == habit.id }.map { it.date }.toSet()
        if (createdOn.isAfter(today)) return 0
        return if (habit.scheduleType == HabitScheduleType.TIMES_PER_WEEK) {
            weeklyStreak(habit, dates, today, createdOn)
        } else {
            dailyStreak(habit, dates, today, createdOn)
        }
    }

    private fun dailyStreak(habit: Habit, dates: Set<LocalDate>, today: LocalDate, createdOn: LocalDate): Int {
        // Today still pending does not break the streak: the day has not ended.
        var day = if (isScheduledOn(habit, today) && today !in dates) today.minusDays(1) else today
        val floor = maxOf(createdOn, today.minusDays(MAX_STREAK_DAYS))
        var count = 0
        while (!day.isBefore(floor)) {
            if (isScheduledOn(habit, day)) {
                if (day in dates) count++ else break
            }
            day = day.minusDays(1)
        }
        return count
    }

    private fun weeklyStreak(habit: Habit, dates: Set<LocalDate>, today: LocalDate, createdOn: LocalDate): Int {
        if (habit.timesPerWeek < 1) return 0
        fun metIn(start: LocalDate): Boolean {
            val from = maxOf(start, createdOn)
            val to = minOf(start.plusDays(6), today)
            val target = targetFor(habit, start, createdOn)
            return target > 0 && dates.count { it in from..to } >= target
        }
        var start = weekStart(today)
        var count = 0
        // The current week counts once it is met, and never breaks the streak while still open.
        if (metIn(start)) count++
        start = start.minusWeeks(1)
        val floor = today.minusDays(MAX_STREAK_DAYS)
        while (!start.plusDays(6).isBefore(createdOn) && !start.isBefore(floor)) {
            if (metIn(start)) count++ else break
            start = start.minusWeeks(1)
        }
        return count
    }

    /** Habits for the Today section, ordered by `position`. [zone] only converts `createdAt` to a local date. */
    fun dueToday(
        habits: List<Habit>,
        checkins: List<HabitCheckin>,
        today: LocalDate,
        zone: ZoneId,
    ): List<HabitToday> {
        val weekStart = weekStart(today)
        return habits.asSequence()
            .filter { !it.archived && isScheduledOn(it, today) }
            .sortedBy { it.position }
            .mapNotNull { habit ->
                val created = createdOn(habit, zone)
                val doneToday = checkins.any { it.habitId == habit.id && it.date == today }
                val progress = weekProgress(habit, checkins, weekStart, created)
                val visible = habit.scheduleType != HabitScheduleType.TIMES_PER_WEEK ||
                    progress.done < progress.target || doneToday
                if (!visible) return@mapNotNull null
                HabitToday(habit, doneToday, progress, currentStreak(habit, checkins, today, created))
            }
            .toList()
    }

    /**
     * Streak line for Hoje. TIMES_PER_WEEK counts weeks (feminine: "seguidas"); the other schedules
     * count days (masculine: "seguidos").
     */
    fun streakText(habit: Habit, streak: Int): String =
        if (habit.scheduleType == HabitScheduleType.TIMES_PER_WEEK) {
            if (streak == 1) "1 semana seguida" else "$streak semanas seguidas"
        } else {
            if (streak == 1) "1 dia seguido" else "$streak dias seguidos"
        }

    fun describe(habit: Habit): String = when (habit.scheduleType) {
        HabitScheduleType.DAILY -> "Todo dia"
        HabitScheduleType.TIMES_PER_WEEK -> "${habit.timesPerWeek}x por semana"
        HabitScheduleType.DAYS_OF_WEEK -> {
            val names = habit.daysOfWeek.sortedBy { it.value }.map { shortName(it) }
            val text = when (names.size) {
                0 -> "Sem dias marcados"
                1 -> names.first()
                else -> names.dropLast(1).joinToString(", ") + " e " + names.last()
            }
            text.replaceFirstChar { it.titlecase(ptBr) }
        }
    }

    private fun shortName(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "seg"
        DayOfWeek.TUESDAY -> "ter"
        DayOfWeek.WEDNESDAY -> "qua"
        DayOfWeek.THURSDAY -> "qui"
        DayOfWeek.FRIDAY -> "sex"
        DayOfWeek.SATURDAY -> "sáb"
        DayOfWeek.SUNDAY -> "dom"
    }
}
