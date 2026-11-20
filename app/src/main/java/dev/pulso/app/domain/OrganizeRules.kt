package dev.pulso.app.domain

import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId

/** Accent- and case-insensitive matching used by global search ("racao" finds "Ração"). */
object SearchRules {
    private val marks = Regex("\\p{Mn}+")

    fun fold(text: String): String =
        marks.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase()

    /** Every whitespace-separated term must appear in at least one field. */
    fun matches(query: String, vararg fields: String): Boolean {
        val terms = fold(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return false
        val haystack = fields.joinToString("\n") { fold(it) }
        return terms.all { haystack.contains(it) }
    }

    fun matchesTask(query: String, task: TaskItem): Boolean =
        matches(query, task.title, task.notes, task.category, task.links, *task.checklist.map { it.text }.toTypedArray())

    fun matchesInbox(query: String, item: InboxItem): Boolean = matches(query, item.text)
}

object CategoryRules {
    const val MAX_NAME = 40
    val DEFAULTS = listOf("Pessoal", "Compras", "Estudos", "Casa", "Trabalho")

    fun validateName(name: String, existing: List<Category>, ignoreId: String? = null): String =
        name.trim().replace(Regex("\\s+"), " ").also { clean ->
            require(clean.isNotEmpty()) { "Dê um nome à categoria." }
            require(clean.length <= MAX_NAME) { "A categoria deve ter até $MAX_NAME caracteres." }
            require(existing.none { it.id != ignoreId && SearchRules.fold(it.name) == SearchRules.fold(clean) }) {
                "Já existe uma categoria com esse nome."
            }
        }

    /** Ready-made categories offered in Ajustes (SPEC 013 §3). Not seeded automatically. */
    val SUGGESTIONS = listOf("Manutenção", "Casamento", "Encomendas")

    /** Suggestions whose name (accent/case-insensitive) is not already a category. */
    fun suggestions(existing: List<Category>): List<String> =
        SUGGESTIONS.filter { name -> existing.none { SearchRules.fold(it.name) == SearchRules.fold(name) } }

    /** Tasks of one category for its screen: active ones grouped by state, plus the completed history. */
    fun overview(tasks: List<TaskItem>, category: String, nowMillis: Long): CategoryOverview {
        val key = SearchRules.fold(category)
        val mine = tasks.filter { SearchRules.fold(it.category) == key }
        val active = mine.filterNot { it.isCompleted }
        return CategoryOverview(
            overdue = active.filter { TaskRules.isOverdue(it, nowMillis) }.sortedBy { it.dueAt },
            scheduled = active.filter { it.dueAt != null && !TaskRules.isOverdue(it, nowMillis) }.sortedBy { it.dueAt },
            withoutDue = active.filter { it.dueAt == null },
            completed = mine.filter { it.isCompleted }.sortedByDescending { it.completedAt },
        )
    }
}

data class CategoryOverview(
    val overdue: List<TaskItem>,
    val scheduled: List<TaskItem>,
    val withoutDue: List<TaskItem>,
    val completed: List<TaskItem>,
) {
    val activeCount: Int get() = overdue.size + scheduled.size + withoutDue.size
}

data class WeeklyReview(
    val overdue: List<TaskItem>,
    val nextSevenDays: List<TaskItem>,
    val withoutDue: List<TaskItem>,
    val completedLastSevenDays: List<TaskItem>,
    /** Tasks moved at least [ReviewRules.FREQUENT_RESCHEDULES] times: candidates to rethink or split. */
    val frequentlyRescheduled: List<TaskItem>,
)

object ReviewRules {
    const val FREQUENT_RESCHEDULES = 3
    private const val WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000

    fun build(tasks: List<TaskItem>, nowMillis: Long, zone: ZoneId): WeeklyReview {
        val active = tasks.filterNot { it.isCompleted }
        val endOfWindow = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().plusDays(8)
            .atStartOfDay(zone).toInstant().toEpochMilli()
        return WeeklyReview(
            overdue = active.filter { TaskRules.isOverdue(it, nowMillis) }.sortedBy { it.dueAt },
            nextSevenDays = active.filter { val due = it.dueAt; due != null && due >= nowMillis && due < endOfWindow }
                .sortedBy { it.dueAt },
            withoutDue = active.filter { it.dueAt == null },
            completedLastSevenDays = tasks.filter { val done = it.completedAt; done != null && done >= nowMillis - WEEK_MILLIS }
                .sortedByDescending { it.completedAt },
            frequentlyRescheduled = active.filter { it.rescheduleCount >= FREQUENT_RESCHEDULES }
                .sortedByDescending { it.rescheduleCount },
        )
    }

    fun isReviewDue(lastReviewAt: Long?, nowMillis: Long): Boolean =
        lastReviewAt == null || nowMillis - lastReviewAt >= WEEK_MILLIS
}
