package dev.pulso.app.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Pure recurrence rules (SPEC 008 / ADR 0005): no Android, no persistence. */
object RecurrenceRules {
    const val MIN_INTERVAL = 1
    const val MAX_INTERVAL = 99

    /** Safety net so a corrupt anchor/rule can never spin forever. */
    private const val MAX_STEPS = 200_000

    private val ptBr: Locale = Locale.forLanguageTag("pt-BR")

    /**
     * Throws [IllegalArgumentException] (pt-BR message) for invalid rules; returns [rule] unchanged otherwise.
     * `WEEKLY` + `SCHEDULE` needs at least one weekday; every other combination must have none
     * (so `WEEKLY` + `COMPLETION` means "N weeks after completing", with no weekdays).
     */
    fun validate(rule: RecurrenceRule): RecurrenceRule {
        require(rule.interval in MIN_INTERVAL..MAX_INTERVAL) { "O intervalo deve ficar entre $MIN_INTERVAL e $MAX_INTERVAL." }
        if (rule.basis == RecurrenceBasis.COMPLETION) {
            require(rule.daysOfWeek.isEmpty()) { "A repetição após concluir não usa dias da semana." }
        } else if (rule.frequency == RecurrenceFrequency.WEEKLY) {
            require(rule.daysOfWeek.isNotEmpty()) { "Escolha ao menos um dia da semana." }
        } else {
            require(rule.daysOfWeek.isEmpty()) { "Dias da semana só valem para repetição semanal." }
        }
        return rule
    }

    /**
     * Due date of the next occurrence after completing one.
     * SCHEDULE: first date of the anchor-generated sequence strictly after max([currentDueAt], [nowMillis]).
     * COMPLETION: local date of [completedAt] + interval units, at the anchor's local time.
     */
    fun nextDueAt(
        rule: RecurrenceRule, anchorDueAt: Long, currentDueAt: Long,
        completedAt: Long, nowMillis: Long, zone: ZoneId,
    ): Long {
        validate(rule)
        val time = localTime(anchorDueAt, zone)
        if (rule.basis == RecurrenceBasis.COMPLETION) {
            val day = Instant.ofEpochMilli(completedAt).atZone(zone).toLocalDate()
            return TaskRules.dueAtFor(plusUnits(day, rule.frequency, rule.interval.toLong()), time, zone)
        }
        val threshold = maxOf(currentDueAt, nowMillis)
        return scheduleSequence(rule, anchorDueAt, zone).take(MAX_STEPS).firstOrNull { it > threshold }
            ?: error("Não foi possível calcular a próxima data da repetição.")
    }

    /**
     * Projected SCHEDULE occurrences in [[fromMillis], [toMillis]] (both inclusive), ascending, at most [limit].
     * `COMPLETION` series cannot be projected (the next date depends on when the user completes) and return empty.
     */
    fun occurrencesBetween(
        rule: RecurrenceRule, anchorDueAt: Long, fromMillis: Long, toMillis: Long,
        zone: ZoneId, limit: Int = 50,
    ): List<Long> {
        validate(rule)
        if (rule.basis == RecurrenceBasis.COMPLETION || limit <= 0 || toMillis < fromMillis) return emptyList()
        return scheduleSequence(rule, anchorDueAt, zone).take(MAX_STEPS)
            .takeWhile { it <= toMillis }
            .filter { it >= fromMillis }
            .take(limit)
            .toList()
    }

    /** pt-BR summary: "Todo dia", "A cada 2 semanas: seg, qua", "Todo mês no dia 5", "Todo ano em 12/03", "3 dias após concluir". */
    fun describe(rule: RecurrenceRule, anchorDueAt: Long, zone: ZoneId): String {
        val n = rule.interval
        val anchorDate = Instant.ofEpochMilli(anchorDueAt).atZone(zone).toLocalDate()
        if (rule.basis == RecurrenceBasis.COMPLETION) {
            val unit = when (rule.frequency) {
                RecurrenceFrequency.DAILY -> if (n == 1) "dia" else "dias"
                RecurrenceFrequency.WEEKLY -> if (n == 1) "semana" else "semanas"
                RecurrenceFrequency.MONTHLY -> if (n == 1) "mês" else "meses"
                RecurrenceFrequency.YEARLY -> if (n == 1) "ano" else "anos"
            }
            return "$n $unit após concluir"
        }
        return when (rule.frequency) {
            RecurrenceFrequency.DAILY -> if (n == 1) "Todo dia" else "A cada $n dias"
            RecurrenceFrequency.WEEKLY -> {
                val head = if (n == 1) "Toda semana" else "A cada $n semanas"
                val days = rule.daysOfWeek.sortedBy { it.value }
                    .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, ptBr).trimEnd('.') }
                if (days.isEmpty()) head else "$head: $days"
            }
            RecurrenceFrequency.MONTHLY ->
                (if (n == 1) "Todo mês" else "A cada $n meses") + " no dia ${anchorDate.dayOfMonth}"
            RecurrenceFrequency.YEARLY ->
                (if (n == 1) "Todo ano" else "A cada $n anos") + " em " + anchorDate.format(DateTimeFormatter.ofPattern("dd/MM"))
        }
    }

    private fun localTime(anchorDueAt: Long, zone: ZoneId): LocalTime =
        Instant.ofEpochMilli(anchorDueAt).atZone(zone).toLocalTime()

    private fun plusUnits(day: LocalDate, frequency: RecurrenceFrequency, n: Long): LocalDate = when (frequency) {
        RecurrenceFrequency.DAILY -> day.plusDays(n)
        RecurrenceFrequency.WEEKLY -> day.plusWeeks(n)
        RecurrenceFrequency.MONTHLY -> day.plusMonths(n) // clamps to the last day of the month
        RecurrenceFrequency.YEARLY -> day.plusYears(n)   // 29/02 -> 28/02 in non-leap years
    }

    /**
     * Ascending SCHEDULE sequence, always computed from the anchor (k-th element from the anchor date,
     * never from the previous element), so month-end clamping and DST gaps never accumulate drift.
     */
    private fun scheduleSequence(rule: RecurrenceRule, anchorDueAt: Long, zone: ZoneId): Sequence<Long> {
        val time = localTime(anchorDueAt, zone)
        val anchorDate = Instant.ofEpochMilli(anchorDueAt).atZone(zone).toLocalDate()
        val step = rule.interval.toLong()
        fun at(date: LocalDate) = TaskRules.dueAtFor(date, time, zone)
        return when (rule.frequency) {
            RecurrenceFrequency.WEEKLY -> {
                val firstWeek = anchorDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val days = rule.daysOfWeek.sortedBy { it.value }
                generateSequence(0L) { it + 1 }.flatMap { w ->
                    val monday = firstWeek.plusWeeks(w * step)
                    days.asSequence().map { monday.plusDays((it.value - 1).toLong()) }
                        .filter { !it.isBefore(anchorDate) }
                        .map(::at)
                }
            }
            else -> generateSequence(0L) { it + 1 }.map { k -> at(plusUnits(anchorDate, rule.frequency, k * step)) }
        }
    }
}
