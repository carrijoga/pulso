package dev.pulso.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class RecurrenceRulesTest {
    private val sp = ZoneId.of("America/Sao_Paulo")
    private val ny = ZoneId.of("America/New_York")

    private fun ms(zone: ZoneId, local: String) = LocalDateTime.parse(local).atZone(zone).toInstant().toEpochMilli()
    private fun local(zone: ZoneId, millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime().toString()

    private fun next(
        rule: RecurrenceRule, zone: ZoneId, anchor: String, current: String,
        now: String = current, completed: String = now,
    ) = local(zone, RecurrenceRules.nextDueAt(rule, ms(zone, anchor), ms(zone, current), ms(zone, completed), ms(zone, now), zone))

    private val daily = RecurrenceRule(RecurrenceFrequency.DAILY)

    // --- SCHEDULE ---

    @Test fun `daily goes to the next day at the anchor time`() {
        assertEquals("2026-01-06T09:00", next(daily, sp, "2026-01-05T09:00", "2026-01-05T09:00"))
    }

    @Test fun `daily with interval 2 skips a day`() {
        val rule = daily.copy(interval = 2)
        assertEquals("2026-01-07T09:00", next(rule, sp, "2026-01-05T09:00", "2026-01-05T09:00"))
        assertEquals("2026-01-09T09:00", next(rule, sp, "2026-01-05T09:00", "2026-01-07T09:00"))
    }

    @Test fun `weekly with several days walks the marked days in order`() {
        val rule = RecurrenceRule(RecurrenceFrequency.WEEKLY, daysOfWeek = setOf(DayOfWeek.FRIDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        val anchor = "2026-01-05T09:00" // segunda
        assertEquals("2026-01-07T09:00", next(rule, sp, anchor, "2026-01-05T09:00"))
        assertEquals("2026-01-09T09:00", next(rule, sp, anchor, "2026-01-07T09:00"))
        assertEquals("2026-01-12T09:00", next(rule, sp, anchor, "2026-01-09T09:00"))
    }

    @Test fun `weekly with interval 2 skips the off week`() {
        val rule = RecurrenceRule(RecurrenceFrequency.WEEKLY, interval = 2, daysOfWeek = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY))
        val anchor = "2026-01-06T08:00" // terça
        assertEquals("2026-01-08T08:00", next(rule, sp, anchor, "2026-01-06T08:00"))
        assertEquals("2026-01-20T08:00", next(rule, sp, anchor, "2026-01-08T08:00"))
    }

    @Test fun `week starts on monday so sunday belongs to the week of the previous monday`() {
        val rule = RecurrenceRule(RecurrenceFrequency.WEEKLY, interval = 2, daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY))
        val anchor = "2026-01-05T07:00" // segunda
        assertEquals("2026-01-11T07:00", next(rule, sp, anchor, "2026-01-05T07:00")) // domingo da mesma semana
        assertEquals("2026-01-19T07:00", next(rule, sp, anchor, "2026-01-11T07:00")) // pula a semana seguinte
        assertEquals("2026-01-25T07:00", next(rule, sp, anchor, "2026-01-19T07:00"))
    }

    @Test fun `weekly ignores marked days earlier than the anchor in the first week`() {
        val rule = RecurrenceRule(RecurrenceFrequency.WEEKLY, daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        val anchor = ms(sp, "2026-01-07T09:00") // quarta
        val list = RecurrenceRules.occurrencesBetween(rule, anchor, ms(sp, "2026-01-01T00:00"), ms(sp, "2026-01-20T00:00"), sp)
        assertEquals(listOf("2026-01-07T09:00", "2026-01-12T09:00", "2026-01-14T09:00", "2026-01-19T09:00"), list.map { local(sp, it) })
    }

    @Test fun `monthly on day 31 uses the last day without moving the anchor`() {
        val rule = RecurrenceRule(RecurrenceFrequency.MONTHLY)
        val anchor = "2026-01-31T10:00"
        assertEquals("2026-02-28T10:00", next(rule, sp, anchor, "2026-01-31T10:00"))
        assertEquals("2026-03-31T10:00", next(rule, sp, anchor, "2026-02-28T10:00")) // sem deriva
        assertEquals("2026-04-30T10:00", next(rule, sp, anchor, "2026-03-31T10:00"))
        assertEquals("2028-02-29T10:00", next(rule, sp, "2028-01-31T10:00", "2028-01-31T10:00")) // bissexto
    }

    @Test fun `monthly with interval 3`() {
        val rule = RecurrenceRule(RecurrenceFrequency.MONTHLY, interval = 3)
        assertEquals("2026-04-15T12:00", next(rule, sp, "2026-01-15T12:00", "2026-01-15T12:00"))
    }

    @Test fun `yearly on feb 29 becomes feb 28 in common years and returns to 29 in leap years`() {
        val rule = RecurrenceRule(RecurrenceFrequency.YEARLY)
        val anchor = "2024-02-29T09:00"
        assertEquals("2025-02-28T09:00", next(rule, sp, anchor, "2024-02-29T09:00"))
        assertEquals("2026-02-28T09:00", next(rule, sp, anchor, "2025-02-28T09:00"))
        assertEquals("2028-02-29T09:00", next(rule, sp, anchor, "2027-02-28T09:00"))
    }

    @Test fun `next date is strictly after now and overdue occurrences do not pile up`() {
        // diária atrasada 5 dias, concluída agora (10/01 15:00): próxima é amanhã, não uma por dia perdido
        assertEquals("2026-01-11T09:00", next(daily, sp, "2026-01-05T09:00", "2026-01-05T09:00", now = "2026-01-10T15:00"))
        // antes do horário do dia: ainda cabe hoje
        assertEquals("2026-01-10T09:00", next(daily, sp, "2026-01-05T09:00", "2026-01-05T09:00", now = "2026-01-10T08:00"))
        // exatamente no horário: estritamente depois
        assertEquals("2026-01-11T09:00", next(daily, sp, "2026-01-05T09:00", "2026-01-05T09:00", now = "2026-01-10T09:00"))
    }

    @Test fun `weekly overdue for several weeks goes to the next marked day after now`() {
        val rule = RecurrenceRule(RecurrenceFrequency.WEEKLY, daysOfWeek = setOf(DayOfWeek.MONDAY))
        assertEquals("2026-02-02T09:00", next(rule, sp, "2026-01-05T09:00", "2026-01-05T09:00", now = "2026-01-28T10:00"))
    }

    @Test fun `manual reschedule returns to the series rhythm`() {
        val rule = RecurrenceRule(RecurrenceFrequency.WEEKLY, daysOfWeek = setOf(DayOfWeek.MONDAY))
        val anchor = "2026-01-05T09:00"
        // segunda 12/01 reagendada para quarta 14/01 → próxima é segunda 19/01
        assertEquals("2026-01-19T09:00", next(rule, sp, anchor, "2026-01-14T09:00", now = "2026-01-14T09:00"))
        // reagendada para antes (quinta 08/01), concluída lá → próxima segunda 12/01
        assertEquals("2026-01-12T09:00", next(rule, sp, anchor, "2026-01-08T09:00", now = "2026-01-08T09:00"))
    }

    @Test fun `current due date in the future wins over now`() {
        assertEquals("2026-01-21T09:00", next(daily, sp, "2026-01-05T09:00", "2026-01-20T09:00", now = "2026-01-10T09:00"))
    }

    // --- COMPLETION ---

    @Test fun `completion counts from the local completion date at the anchor time`() {
        val rule = RecurrenceRule(RecurrenceFrequency.MONTHLY, interval = 3, basis = RecurrenceBasis.COMPLETION)
        assertEquals("2026-05-20T08:00", next(rule, sp, "2026-01-10T08:00", "2026-02-01T08:00", completed = "2026-02-20T21:30"))
    }

    @Test fun `completion uses the local date, not the UTC date`() {
        val rule = RecurrenceRule(RecurrenceFrequency.DAILY, interval = 3, basis = RecurrenceBasis.COMPLETION)
        // 23:30 em São Paulo já é dia 21 em UTC; vale a data local (20)
        assertEquals("2026-02-23T08:00", next(rule, sp, "2026-01-10T08:00", "2026-02-20T08:00", completed = "2026-02-20T23:30"))
    }

    @Test fun `completion ignores current due and now, and clamps month end`() {
        val rule = RecurrenceRule(RecurrenceFrequency.MONTHLY, interval = 3, basis = RecurrenceBasis.COMPLETION)
        assertEquals("2027-02-28T08:00", next(rule, sp, "2026-01-10T08:00", "2030-01-01T08:00", now = "2030-01-01T08:00", completed = "2026-11-30T10:00"))
    }

    @Test fun `completion weekly and yearly`() {
        val weekly = RecurrenceRule(RecurrenceFrequency.WEEKLY, interval = 2, basis = RecurrenceBasis.COMPLETION)
        assertEquals("2026-03-17T07:00", next(weekly, sp, "2026-01-05T07:00", "2026-01-05T07:00", completed = "2026-03-03T12:00"))
        val yearly = RecurrenceRule(RecurrenceFrequency.YEARLY, basis = RecurrenceBasis.COMPLETION)
        assertEquals("2025-02-28T07:00", next(yearly, sp, "2024-01-05T07:00", "2024-01-05T07:00", completed = "2024-02-29T12:00"))
    }

    // --- DST ---

    @Test fun `daily keeps the local time across New York spring forward`() {
        val rule = daily
        assertEquals("2026-03-08T09:00", next(rule, ny, "2026-03-07T09:00", "2026-03-07T09:00"))
        // o intervalo real é de 23h, a hora local não muda
        val a = ms(ny, "2026-03-07T09:00")
        val b = RecurrenceRules.nextDueAt(rule, a, a, a, a, ny)
        assertEquals(23 * 3_600_000L, b - a)
    }

    @Test fun `daily anchored in the spring forward gap resolves forward then returns to the anchor time`() {
        val anchor = ms(ny, "2026-03-07T02:30")
        val first = RecurrenceRules.nextDueAt(daily, anchor, anchor, anchor, anchor, ny)
        assertEquals("2026-03-08T03:30", local(ny, first)) // 02:30 não existe em 08/03
        val second = RecurrenceRules.nextDueAt(daily, anchor, first, first, first, ny)
        assertEquals("2026-03-09T02:30", local(ny, second)) // sem deriva
    }

    @Test fun `daily keeps the local time across New York fall back`() {
        assertEquals("2026-11-01T01:30", next(daily, ny, "2026-10-31T01:30", "2026-10-31T01:30"))
        assertEquals("2026-11-02T01:30", next(daily, ny, "2026-10-31T01:30", "2026-11-01T01:30"))
    }

    @Test fun `monthly crosses New York DST keeping local time`() {
        val rule = RecurrenceRule(RecurrenceFrequency.MONTHLY)
        assertEquals("2026-03-15T09:00", next(rule, ny, "2026-02-15T09:00", "2026-02-15T09:00"))
    }

    @Test fun `Sao Paulo midnight DST gap of 2018 resolves forward and does not drift`() {
        val anchor = ms(sp, "2018-11-03T00:30")
        val first = RecurrenceRules.nextDueAt(daily, anchor, anchor, anchor, anchor, sp)
        assertEquals("2018-11-04T01:30", local(sp, first)) // 00:30 não existiu em 04/11/2018
        val second = RecurrenceRules.nextDueAt(daily, anchor, first, first, first, sp)
        assertEquals("2018-11-05T00:30", local(sp, second))
    }

    // --- occurrencesBetween ---

    @Test fun `occurrencesBetween lists projected dates with inclusive bounds`() {
        val anchor = ms(sp, "2026-12-05T09:00")
        val rule = RecurrenceRule(RecurrenceFrequency.MONTHLY)
        val list = RecurrenceRules.occurrencesBetween(rule, anchor, ms(sp, "2026-12-05T09:00"), ms(sp, "2027-02-05T09:00"), sp)
        assertEquals(listOf("2026-12-05T09:00", "2027-01-05T09:00", "2027-02-05T09:00"), list.map { local(sp, it) })
    }

    @Test fun `occurrencesBetween respects the limit and skips dates before from`() {
        val anchor = ms(sp, "2026-01-01T09:00")
        val list = RecurrenceRules.occurrencesBetween(daily, anchor, ms(sp, "2026-03-01T00:00"), ms(sp, "2027-01-01T00:00"), sp, limit = 3)
        assertEquals(listOf("2026-03-01T09:00", "2026-03-02T09:00", "2026-03-03T09:00"), list.map { local(sp, it) })
    }

    @Test fun `occurrencesBetween is empty for completion basis and empty ranges`() {
        val anchor = ms(sp, "2026-01-01T09:00")
        val completion = RecurrenceRule(RecurrenceFrequency.DAILY, basis = RecurrenceBasis.COMPLETION)
        assertTrue(RecurrenceRules.occurrencesBetween(completion, anchor, anchor, anchor + 10 * 86_400_000L, sp).isEmpty())
        assertTrue(RecurrenceRules.occurrencesBetween(daily, anchor, anchor + 1000, anchor, sp).isEmpty())
    }

    // --- validate ---

    @Test fun `validate accepts good rules and returns the same instance`() {
        val ok = RecurrenceRule(RecurrenceFrequency.WEEKLY, 2, setOf(DayOfWeek.MONDAY))
        assertSame(ok, RecurrenceRules.validate(ok))
        RecurrenceRules.validate(daily.copy(interval = 99))
        RecurrenceRules.validate(RecurrenceRule(RecurrenceFrequency.WEEKLY, 2, basis = RecurrenceBasis.COMPLETION))
    }

    @Test fun `validate rejects invalid rules`() {
        val bad = listOf(
            daily.copy(interval = 0),
            daily.copy(interval = 100),
            daily.copy(interval = -1),
            RecurrenceRule(RecurrenceFrequency.WEEKLY),
            daily.copy(daysOfWeek = setOf(DayOfWeek.MONDAY)),
            RecurrenceRule(RecurrenceFrequency.MONTHLY, daysOfWeek = setOf(DayOfWeek.MONDAY)),
            RecurrenceRule(RecurrenceFrequency.YEARLY, daysOfWeek = setOf(DayOfWeek.MONDAY)),
            RecurrenceRule(RecurrenceFrequency.DAILY, basis = RecurrenceBasis.COMPLETION, daysOfWeek = setOf(DayOfWeek.MONDAY)),
            RecurrenceRule(RecurrenceFrequency.WEEKLY, basis = RecurrenceBasis.COMPLETION, daysOfWeek = setOf(DayOfWeek.MONDAY)),
        )
        bad.forEach { rule -> assertThrows(rule.toString(), IllegalArgumentException::class.java) { RecurrenceRules.validate(rule) } }
    }

    @Test fun `nextDueAt and occurrencesBetween validate the rule`() {
        val a = ms(sp, "2026-01-05T09:00")
        assertThrows(IllegalArgumentException::class.java) { RecurrenceRules.nextDueAt(RecurrenceRule(RecurrenceFrequency.WEEKLY), a, a, a, a, sp) }
        assertThrows(IllegalArgumentException::class.java) { RecurrenceRules.occurrencesBetween(daily.copy(interval = 0), a, a, a + 1, sp) }
    }

    // --- describe ---

    @Test fun `describe in pt-BR`() {
        val anchor = ms(sp, "2026-03-05T09:00")
        assertEquals("Todo dia", RecurrenceRules.describe(daily, anchor, sp))
        assertEquals("A cada 3 dias", RecurrenceRules.describe(daily.copy(interval = 3), anchor, sp))
        assertEquals("A cada 2 semanas: seg, qua", RecurrenceRules.describe(
            RecurrenceRule(RecurrenceFrequency.WEEKLY, 2, setOf(DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY)), anchor, sp))
        assertEquals("Toda semana: ter, sex", RecurrenceRules.describe(
            RecurrenceRule(RecurrenceFrequency.WEEKLY, 1, setOf(DayOfWeek.FRIDAY, DayOfWeek.TUESDAY)), anchor, sp))
        assertEquals("Todo mês no dia 5", RecurrenceRules.describe(RecurrenceRule(RecurrenceFrequency.MONTHLY), anchor, sp))
        assertEquals("A cada 3 meses no dia 5", RecurrenceRules.describe(RecurrenceRule(RecurrenceFrequency.MONTHLY, 3), anchor, sp))
        assertEquals("Todo ano em 05/03", RecurrenceRules.describe(RecurrenceRule(RecurrenceFrequency.YEARLY), anchor, sp))
        assertEquals("A cada 2 anos em 05/03", RecurrenceRules.describe(RecurrenceRule(RecurrenceFrequency.YEARLY, 2), anchor, sp))
        assertEquals("3 dias após concluir", RecurrenceRules.describe(
            RecurrenceRule(RecurrenceFrequency.DAILY, 3, basis = RecurrenceBasis.COMPLETION), anchor, sp))
        assertEquals("1 mês após concluir", RecurrenceRules.describe(
            RecurrenceRule(RecurrenceFrequency.MONTHLY, basis = RecurrenceBasis.COMPLETION), anchor, sp))
        assertEquals("2 meses após concluir", RecurrenceRules.describe(
            RecurrenceRule(RecurrenceFrequency.MONTHLY, 2, basis = RecurrenceBasis.COMPLETION), anchor, sp))
    }
}
