package dev.pulso.app.domain

/** Items inside a task (SPEC 013). Pure rules; persistence lives in the repository. */
object ChecklistRules {
    const val MAX_TEXT = 200
    const val MAX_ITEMS = 100

    fun validateText(text: String): String = text.trim().also {
        require(it.isNotEmpty()) { "Escreva o texto do item." }
        require(it.length <= MAX_TEXT) { "O item deve ter até $MAX_TEXT caracteres." }
    }

    /** Draft lines: blank ones dropped, every text validated, at most [MAX_ITEMS]. */
    fun validateItems(texts: List<String>): List<String> =
        texts.map { it.trim() }.filter { it.isNotEmpty() }.map(::validateText).also {
            require(it.size <= MAX_ITEMS) { "Use até $MAX_ITEMS itens por tarefa." }
        }

    /**
     * True when [itemId] is the only unchecked item: checking it would finish the list.
     * [items] is the state before the change.
     */
    fun isLastUnchecked(items: List<ChecklistItem>, itemId: String): Boolean {
        val item = items.firstOrNull { it.id == itemId } ?: return false
        return !item.checked && items.all { it.id == itemId || it.checked }
    }

    /**
     * Order after putting [id] at [index] (clamped to the list). [orderedIds] may or may not contain [id].
     * Used for move, and to put an undone deletion back at its place.
     */
    fun placeAt(orderedIds: List<String>, id: String, index: Int): List<String> {
        val rest = orderedIds.filter { it != id }
        return rest.toMutableList().apply { add(index.coerceIn(0, rest.size), id) }
    }
}
