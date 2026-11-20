package dev.pulso.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistRulesTest {
    private fun item(id: String, checked: Boolean = false) = ChecklistItem(id, "task", "Item $id", 0, checked)

    @Test fun `item texts are trimmed and limited to 200 characters`() {
        assertEquals("arroz", ChecklistRules.validateText("  arroz  "))
        assertThrows(IllegalArgumentException::class.java) { ChecklistRules.validateText("   ") }
        assertEquals(200, ChecklistRules.validateText("a".repeat(200)).length)
        assertThrows(IllegalArgumentException::class.java) { ChecklistRules.validateText("a".repeat(201)) }
    }

    @Test fun `draft lines drop blanks and allow at most 100 items`() {
        assertEquals(listOf("arroz", "café"), ChecklistRules.validateItems(listOf(" arroz ", "", "  ", "café")))
        assertEquals(100, ChecklistRules.validateItems(List(100) { "item $it" }).size)
        assertThrows(IllegalArgumentException::class.java) { ChecklistRules.validateItems(List(101) { "item $it" }) }
    }

    @Test fun `checking the last open item is detected, other changes are not`() {
        val items = listOf(item("a", true), item("b"), item("c", true))
        assertTrue(ChecklistRules.isLastUnchecked(items, "b"))
        // "a" is still open, so checking "b" would not finish the list.
        assertFalse(ChecklistRules.isLastUnchecked(listOf(item("a"), item("b")), "b"))
        // Already checked: nothing to finish. Unknown id: nothing either.
        assertFalse(ChecklistRules.isLastUnchecked(items, "a"))
        assertFalse(ChecklistRules.isLastUnchecked(items, "x"))
        assertTrue(ChecklistRules.isLastUnchecked(listOf(item("only")), "only"))
    }

    @Test fun `placeAt moves an item, inserts a missing one and clamps the index`() {
        assertEquals(listOf("b", "a", "c"), ChecklistRules.placeAt(listOf("a", "b", "c"), "a", 1))
        assertEquals(listOf("b", "c", "a"), ChecklistRules.placeAt(listOf("a", "b", "c"), "a", 99))
        assertEquals(listOf("b", "a"), ChecklistRules.placeAt(listOf("a", "b"), "b", -5))
        // Undo of a deletion: "b" was at index 1 and comes back there.
        assertEquals(listOf("a", "b", "c"), ChecklistRules.placeAt(listOf("a", "c"), "b", 1))
    }

    @Test fun `task search also reads checklist texts`() {
        val task = TaskItem("1", "Mercado", "", "Compras", Priority.NORMAL, null, null, 0, 0,
            checklist = listOf(ChecklistItem("a", "1", "Café torrado", 0, false)))
        assertTrue(SearchRules.matchesTask("cafe torrado", task))
        assertFalse(SearchRules.matchesTask("arroz", task))
    }

    @Test fun `task drafts keep only valid checklist lines`() {
        val draft = TaskRules.validateDraft(TaskDraft("Mercado", checklist = listOf(" arroz ", " ")))
        assertEquals(listOf("arroz"), draft.checklist)
    }
}
