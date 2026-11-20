package dev.pulso.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class SubtaskRulesTest {
    private val sp = ZoneId.of("America/Sao_Paulo")
    private val ny = ZoneId.of("America/New_York")

    private fun task(id: String, parentId: String? = null, completed: Boolean = false, total: Int = 0, done: Int = 0) =
        TaskItem(id, "T$id", "", "Pessoal", Priority.NORMAL, null, if (completed) 1L else null, 1L, 1L,
            parentId = parentId, subtaskTotal = total, subtaskDone = done)

    private val mother = SubtaskRules.Parent("m", isSubtask = false, deleted = false)

    @Test fun linkingToNoParentIsAlwaysAllowed() {
        assertNull(SubtaskRules.linkError("a", null, null, taskHasSubtasks = true, taskRepeats = true))
    }

    @Test fun linkRefusalsHaveSpecificMessages() {
        assertEquals("Uma tarefa não pode ser subtarefa de si mesma.",
            SubtaskRules.linkError("a", "a", SubtaskRules.Parent("a", false, false), false, false))
        assertEquals("A tarefa mãe não existe mais.", SubtaskRules.linkError("a", "m", null, false, false))
        assertEquals("A tarefa mãe não existe mais.",
            SubtaskRules.linkError("a", "m", mother.copy(deleted = true), false, false))
        assertEquals("Subtarefas só podem ter um nível: escolha uma tarefa principal.",
            SubtaskRules.linkError("a", "m", mother.copy(isSubtask = true), false, false))
        assertEquals("Tarefa com subtarefas não pode virar subtarefa.",
            SubtaskRules.linkError("a", "m", mother, taskHasSubtasks = true, taskRepeats = false))
        assertEquals("Tarefa repetida não pode ser subtarefa. Remova a repetição antes.",
            SubtaskRules.linkError("a", "m", mother, taskHasSubtasks = false, taskRepeats = true))
    }

    @Test fun linkToAPlainMotherIsAllowed() {
        assertNull(SubtaskRules.linkError("a", "m", mother, taskHasSubtasks = false, taskRepeats = false))
    }

    @Test fun repeatingMotherIsAllowedButSubtaskCannotRepeat() {
        // Mother repeating: no error for the link itself (SPEC 015 §7); the copies come with each occurrence.
        assertNull(SubtaskRules.repeatError(isSubtask = false))
        assertEquals("Subtarefa não pode repetir. Solte-a da tarefa mãe antes.", SubtaskRules.repeatError(isSubtask = true))
    }

    @Test fun progressLabelIsDoneOverTotal() {
        assertEquals("2/5 subtarefas", SubtaskRules.progressLabel(2, 5))
        assertEquals("0/1 subtarefas", SubtaskRules.progressLabel(0, 1))
    }

    @Test fun suggestsCompletingTheMotherOnlyForTheLastOpenSubtask() {
        val parentLastOpen = task("m", total = 2, done = 1)
        assertTrue(SubtaskRules.suggestCompletingParent(parentLastOpen, task("c", parentId = "m")))
        // Other subtasks still open: nothing suggested.
        assertFalse(SubtaskRules.suggestCompletingParent(task("m", total = 3, done = 1), task("c", parentId = "m")))
        // Mother already completed: nothing to suggest.
        assertFalse(SubtaskRules.suggestCompletingParent(task("m", completed = true, total = 1, done = 0),
            task("c", parentId = "m")))
        // Not a subtask of this mother.
        assertFalse(SubtaskRules.suggestCompletingParent(parentLastOpen, task("c", parentId = "outra")))
        // The subtask was already completed (state before the change).
        assertFalse(SubtaskRules.suggestCompletingParent(parentLastOpen, task("c", parentId = "m", completed = true)))
    }

    @Test fun groupsSubtasksUnderTheirMotherWhenBothAreListed() {
        val tasks = listOf(
            task("a", total = 2),
            task("b", parentId = "a"),
            task("c"),
            task("d", parentId = "a"),
            task("e", parentId = "fora"), // mother not in this list: stands alone
        )
        val rows = SubtaskRules.groupForList(tasks)
        assertEquals(listOf("a", "b", "d", "c", "e"), rows.map { it.task.id })
        assertEquals(listOf(false, true, true, false, false), rows.map { it.indented })
    }

    @Test fun shiftedDueKeepsDaysApartAndLocalTimeAcrossDaylightSaving() {
        // New York switches DST on 2026-03-08. Mother 09:00 on the 7th; subtask 15:00 on the same day.
        val motherDue = LocalDateTime.parse("2026-03-07T09:00").atZone(ny).toInstant().toEpochMilli()
        val childDue = LocalDateTime.parse("2026-03-07T15:00").atZone(ny).toInstant().toEpochMilli()
        val nextMother = LocalDateTime.parse("2026-03-08T09:00").atZone(ny).toInstant().toEpochMilli()
        assertEquals(LocalDateTime.parse("2026-03-08T15:00").atZone(ny).toInstant().toEpochMilli(),
            SubtaskRules.shiftedDueAt(childDue, motherDue, nextMother, ny))

        // Subtask two days after the mother, at 10:00: the copy stays two days after the next mother.
        val laterChild = LocalDateTime.parse("2026-03-09T10:00").atZone(ny).toInstant().toEpochMilli()
        assertEquals(LocalDateTime.parse("2026-03-10T10:00").atZone(ny).toInstant().toEpochMilli(),
            SubtaskRules.shiftedDueAt(laterChild, motherDue, nextMother, ny))
    }

    @Test fun shiftedDueWorksInSaoPaulo() {
        val motherDue = LocalDateTime.parse("2026-01-05T08:00").atZone(sp).toInstant().toEpochMilli()
        val childDue = LocalDateTime.parse("2026-01-05T08:00").atZone(sp).toInstant().toEpochMilli()
        val nextMother = LocalDateTime.parse("2026-01-06T08:00").atZone(sp).toInstant().toEpochMilli()
        assertEquals(nextMother, SubtaskRules.shiftedDueAt(childDue, motherDue, nextMother, sp))
    }

    private fun link(id: String, parentId: String? = null, deleted: Boolean = false) = SubtaskLink(id, parentId, deleted)

    @Test fun importAcceptsAValidTree() {
        val local = listOf(link("m"))
        val incoming = listOf(link("c", "m"), link("c2", "m"))
        assertNull(SubtaskRules.importError(local, incoming))
    }

    @Test fun importRejectsMissingParentSelfReferenceCycleAndTwoLevels() {
        assertEquals(SubtaskRules.MISSING_PARENT, SubtaskRules.importError(emptyList(), listOf(link("c", "nao-existe"))))
        assertEquals(SubtaskRules.CYCLE, SubtaskRules.importError(emptyList(), listOf(link("a", "a"))))
        assertEquals(SubtaskRules.CYCLE, SubtaskRules.importError(emptyList(), listOf(link("a", "b"), link("b", "a"))))
        assertEquals(SubtaskRules.TWO_LEVELS,
            SubtaskRules.importError(emptyList(), listOf(link("m"), link("s", "m"), link("n", "s"))))
    }

    @Test fun importRejectsLiveSubtaskUnderDeletedMotherButAcceptsDeletedChild() {
        val local = listOf(link("m", deleted = true))
        assertEquals(SubtaskRules.POINTS_TO_DELETED, SubtaskRules.importError(local, listOf(link("c", "m"))))
        assertNull(SubtaskRules.importError(local, listOf(link("c", "m", deleted = true))))
    }

    @Test fun importRejectsALiveSubtaskWhoseRepetitionIsStillLive() {
        val local = listOf(link("m"))
        assertEquals(SubtaskRules.REPEATING_SUBTASK,
            SubtaskRules.importError(local, listOf(SubtaskLink("c", "m", deleted = false, repeats = true))))
        // Deleted rows and subtasks whose series ended are history, not a live repetition.
        assertNull(SubtaskRules.importError(local, listOf(SubtaskLink("c", "m", deleted = true, repeats = true))))
        assertNull(SubtaskRules.importError(local, listOf(SubtaskLink("c", "m", deleted = false, repeats = false))))
    }

    @Test fun importUsesTheLocalRowWhenTheIdAlreadyExists() {
        // The local "m" is a subtask already; the file's "m" is ignored by the additive import, so the chain is two levels.
        val local = listOf(link("x"), link("m", "x"))
        assertEquals(SubtaskRules.TWO_LEVELS, SubtaskRules.importError(local, listOf(link("m", null), link("c", "m"))))
    }
}
