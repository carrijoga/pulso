package dev.pulso.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

enum class DueState { OVERDUE, TODAY, SOON, LATER }

/** Relative due bucket used by the redesigned task cards (rebranding session). */
enum class DueStatus { NONE, OVERDUE, TODAY, TOMORROW, THIS_WEEK, LATER }

data class DueLabel(val text: String, val state: DueState)

object TaskRules {
    const val MAX_CAPTURE = 500
    const val MAX_TITLE = 200
    const val DEFAULT_CATEGORY = "Pessoal"
    private val ptBr: Locale = Locale.forLanguageTag("pt-BR")

    fun validateCapture(text: String): String = text.trim().also {
        require(it.isNotBlank()) { "Escreva alguma coisa para salvar." }
        require(it.length <= MAX_CAPTURE) { "A anotação deve ter até $MAX_CAPTURE caracteres." }
    }

    fun validateDraft(draft: TaskDraft): TaskDraft = draft.copy(
        title = draft.title.trim().also {
            require(it.isNotBlank()) { "O título é obrigatório." }
            require(it.length <= MAX_TITLE) { "O título deve ter até $MAX_TITLE caracteres." }
        },
        notes = draft.notes.trim(),
        category = draft.category.trim().ifEmpty { DEFAULT_CATEGORY },
        links = normalizeLinks(draft.links),
        checklist = ChecklistRules.validateItems(draft.checklist),
    )

    /** One link per line; blank lines dropped, duplicates removed, order kept. */
    fun normalizeLinks(raw: String): String = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        .distinct().also { links ->
            require(links.size <= 20) { "Use até 20 links por tarefa." }
            links.forEach { require(it.length <= 2_000) { "Link muito longo." } }
        }.joinToString("\n")

    fun linkList(links: String): List<String> = links.lines().map { it.trim() }.filter { it.isNotEmpty() }

    fun isOverdue(task: TaskItem, nowMillis: Long): Boolean =
        !task.isCompleted && task.dueAt?.let { it < nowMillis } == true

    fun isDueTodayOrOverdue(task: TaskItem, nowMillis: Long, zone: ZoneId): Boolean {
        if (task.isCompleted || task.dueAt == null) return false
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val dueDay = Instant.ofEpochMilli(task.dueAt).atZone(zone).toLocalDate()
        return !dueDay.isAfter(today)
    }

    fun dueStatus(task: TaskItem, nowMillis: Long, zone: ZoneId): DueStatus {
        val due = task.dueAt ?: return DueStatus.NONE
        if (due < nowMillis) return DueStatus.OVERDUE
        val days = java.time.temporal.ChronoUnit.DAYS.between(
            Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate(), Instant.ofEpochMilli(due).atZone(zone).toLocalDate())
        return when {
            days == 0L -> DueStatus.TODAY
            days == 1L -> DueStatus.TOMORROW
            days in 2..6 -> DueStatus.THIS_WEEK
            else -> DueStatus.LATER
        }
    }

    /** Active tasks with overdue/today first (by due date), then the rest in their original order. */
    fun agenda(tasks: List<TaskItem>, nowMillis: Long, zone: ZoneId): List<TaskItem> {
        val active = tasks.filterNot { it.isCompleted }
        val due = active.filter { isDueTodayOrOverdue(it, nowMillis, zone) }.sortedBy { it.dueAt }
        return due + active.filterNot { it in due }
    }

    /** Short pt-BR label for lists and the widget: "Atrasada", "Hoje 14:00", "Amanhã", "sex.", "24/12". */
    fun dueLabel(dueAt: Long, nowMillis: Long, zone: ZoneId): DueLabel {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val due = Instant.ofEpochMilli(dueAt).atZone(zone)
        val days = java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), due.toLocalDate())
        val time = due.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
        return when {
            dueAt < nowMillis -> DueLabel("Atrasada", DueState.OVERDUE)
            days == 0L -> DueLabel("Hoje $time", DueState.TODAY)
            days == 1L -> DueLabel("Amanhã $time", DueState.SOON)
            days in 2..6 -> DueLabel(due.dayOfWeek.getDisplayName(TextStyle.SHORT, ptBr) + " $time", DueState.SOON)
            else -> DueLabel(due.toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), DueState.LATER)
        }
    }

    /** Reminders arrive a little after the due time; within this window the notification says "Vence agora". */
    const val JUST_DUE_WINDOW_MILLIS = 15 * 60_000L

    /** Due text for a reminder notification: "Vence agora · HH:mm" right at the due time, otherwise [dueLabel]. */
    fun reminderDueText(dueAt: Long, nowMillis: Long, zone: ZoneId): String {
        val late = nowMillis - dueAt
        if (late in 0 until JUST_DUE_WINDOW_MILLIS) {
            val time = Instant.ofEpochMilli(dueAt).atZone(zone).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
            return "Vence agora · $time"
        }
        return dueLabel(dueAt, nowMillis, zone).text
    }

    /** Due instant for a local date and time in the user's zone (DST gaps resolved by java.time). */
    fun dueAtFor(date: LocalDate, time: LocalTime, zone: ZoneId): Long =
        date.atTime(time).atZone(zone).toInstant().toEpochMilli()

    /** Moves a due date by whole days while keeping the local time of day. */
    fun shiftDays(dueAt: Long, days: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(dueAt).atZone(zone).plusDays(days).toInstant().toEpochMilli()

    /**
     * Target for quick reschedule actions ("Amanhã", "+7 dias"): [daysFromToday] counted from *today*
     * (not from an old overdue date), keeping the task's local time of day, or [fallbackTime] if none.
     */
    fun rescheduleFromToday(dueAt: Long?, daysFromToday: Long, nowMillis: Long, zone: ZoneId, fallbackTime: LocalTime): Long {
        val time = dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() } ?: fallbackTime
        val day = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().plusDays(daysFromToday)
        return dueAtFor(day, time, zone)
    }

    /** Instant at which the reminder should fire: an active snooze wins over the due date. */
    fun reminderTime(task: TaskItem): Long? =
        if (task.isCompleted) null else task.snoozedUntil ?: task.dueAt
}
