package dev.pulso.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * A row's link to its mother, as the backup validation sees it (no Room types here). [repeats] is true when
 * the row's series is still live: a subtask whose repetition ended keeps its seriesId as history and is valid.
 */
data class SubtaskLink(val id: String, val parentId: String?, val deleted: Boolean, val repeats: Boolean = false)

/** One line of a task list: a task, and whether it is shown under its mother. */
data class ListRow(val task: TaskItem, val indented: Boolean)

/**
 * Subtasks (SPEC 015, ADR 0009): one level only. Mother and subtasks keep their own due date, reminder,
 * category and completion. Pure rules; the repository applies them inside its transactions.
 */
object SubtaskRules {
    const val MISSING_PARENT = "Backup inconsistente: referência ausente."
    const val CYCLE = "Backup inconsistente: ciclo entre subtarefas."
    const val TWO_LEVELS = "Backup inconsistente: subtarefa com mais de um nível."
    const val POINTS_TO_DELETED = "Backup inconsistente: subtarefa aponta para tarefa excluída."
    const val REPEATING_SUBTASK = "Backup inconsistente: subtarefa com repetição ativa."

    /** The mother as the rules see it. */
    data class Parent(val id: String, val isSubtask: Boolean, val deleted: Boolean)

    /**
     * Why [taskId] cannot be linked to [parentId] (null when the link is allowed, or when [parentId] is null).
     * A repeating mother is allowed (SPEC 015 §7); a repeating task is not (it would become a repeating subtask).
     * [parent] is the row with that id, or null when it does not exist.
     */
    fun linkError(taskId: String, parentId: String?, parent: Parent?, taskHasSubtasks: Boolean, taskRepeats: Boolean): String? {
        if (parentId == null) return null
        if (parentId == taskId) return "Uma tarefa não pode ser subtarefa de si mesma."
        if (parent == null || parent.deleted) return "A tarefa mãe não existe mais."
        if (parent.isSubtask) return "Subtarefas só podem ter um nível: escolha uma tarefa principal."
        if (taskHasSubtasks) return "Tarefa com subtarefas não pode virar subtarefa."
        if (taskRepeats) return "Tarefa repetida não pode ser subtarefa. Remova a repetição antes."
        return null
    }

    /**
     * A subtask cannot repeat (SPEC 015 §7). A repeating mother may have subtasks: each new occurrence
     * gets copies of them.
     */
    fun repeatError(isSubtask: Boolean): String? =
        if (isSubtask) "Subtarefa não pode repetir. Solte-a da tarefa mãe antes." else null

    /**
     * Due date of a subtask copied into the next occurrence of its repeating mother: keeps the same local
     * days-apart and the same local time of day as the subtask had relative to the finished mother.
     */
    fun shiftedDueAt(childDueAt: Long, finishedMotherDueAt: Long, nextMotherDueAt: Long, zone: ZoneId): Long {
        val offsetDays = ChronoUnit.DAYS.between(
            Instant.ofEpochMilli(finishedMotherDueAt).atZone(zone).toLocalDate(),
            Instant.ofEpochMilli(childDueAt).atZone(zone).toLocalDate(),
        )
        val child = Instant.ofEpochMilli(childDueAt).atZone(zone)
        val day: LocalDate = Instant.ofEpochMilli(nextMotherDueAt).atZone(zone).toLocalDate().plusDays(offsetDays)
        return TaskRules.dueAtFor(day, child.toLocalTime(), zone)
    }

    /** "2/5 subtarefas" for the card indicator. */
    fun progressLabel(done: Int, total: Int): String = "$done/$total subtarefas"

    /**
     * True when completing [child] was the last open subtask of an open [parent]: the app then suggests
     * completing the mother. Counts are the state before the change.
     */
    fun suggestCompletingParent(parent: TaskItem, child: TaskItem): Boolean =
        child.parentId == parent.id && !child.isCompleted && !parent.isCompleted && parent.openSubtasks == 1

    /**
     * Order of a task list (SPEC 015): a mother that is in the list is followed by its subtasks, indented.
     * A subtask whose mother is not in the list stands alone, as a normal task (with the mother's label).
     */
    fun groupForList(tasks: List<TaskItem>): List<ListRow> {
        val ids = tasks.map { it.id }.toSet()
        val childrenOf = tasks.filter { it.parentId in ids }.groupBy { it.parentId!! }
        val rows = mutableListOf<ListRow>()
        fun place(task: TaskItem, indented: Boolean) {
            rows += ListRow(task, indented)
            childrenOf[task.id].orEmpty().forEach { place(it, indented = true) }
        }
        tasks.filter { it.parentId == null || it.parentId !in ids }.forEach { place(it, indented = false) }
        return rows
    }

    /**
     * Backup validation (tudo ou nada). [local] are the rows already in the database; [incoming] the rows of the
     * file. An incoming id that already exists locally is ignored by the additive import, so the local row
     * is what the link must resolve to. Returns the message to reject with, or null.
     */
    fun importError(local: List<SubtaskLink>, incoming: List<SubtaskLink>): String? {
        val localIds = local.map { it.id }.toSet()
        val fresh = incoming.filter { it.id !in localIds }
        val effective = (local + fresh).associateBy { it.id }
        for (row in fresh) {
            val parentId = row.parentId ?: continue
            if (!row.deleted && row.repeats) return REPEATING_SUBTASK
            val parent = effective[parentId] ?: return MISSING_PARENT
            when {
                parentId == row.id || parent.parentId == row.id -> return CYCLE
                parent.parentId != null -> return TWO_LEVELS
                !row.deleted && parent.deleted -> return POINTS_TO_DELETED
            }
        }
        return null
    }
}
