package dev.pulso.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.pulso.app.domain.ChecklistRules
import dev.pulso.app.domain.TaskItem

/** One line of the checklist editor: a saved item, or a line of a new task's draft. */
data class ChecklistRow(val id: String, val text: String, val checked: Boolean)

/** Where checklist edits go: a saved task writes through the ViewModel, a new task edits its draft. */
interface ChecklistHandler {
    /** [done] runs once the item is stored (or refused), so the caller can wait for it. */
    fun add(text: String, done: (Throwable?) -> Unit)
    fun setChecked(id: String, checked: Boolean)
    fun edit(id: String, text: String, done: (Throwable?) -> Unit)
    fun move(id: String, index: Int)
    fun delete(id: String)
}

/** Draft checklist of a task being created. Ids are line indexes; nothing is written until the task is saved. */
class DraftChecklist(private val lines: MutableList<String>) : ChecklistHandler {
    override fun add(text: String, done: (Throwable?) -> Unit) {
        val result = runCatching {
            require(lines.size < ChecklistRules.MAX_ITEMS) { "Use até ${ChecklistRules.MAX_ITEMS} itens por tarefa." }
            ChecklistRules.validateText(text)
        }
        result.onSuccess { lines += it }
        done(result.exceptionOrNull())
    }

    override fun setChecked(id: String, checked: Boolean) = Unit // drafts have no checkboxes

    override fun edit(id: String, text: String, done: (Throwable?) -> Unit) {
        val result = runCatching { ChecklistRules.validateText(text) }
        result.onSuccess { lines[id.toInt()] = it }
        done(result.exceptionOrNull())
    }

    override fun move(id: String, index: Int) {
        val line = lines.removeAt(id.toInt())
        lines.add(index.coerceIn(0, lines.size), line)
    }

    override fun delete(id: String) {
        lines.removeAt(id.toInt())
    }
}

/** Keeps the draft checklist through rotation, like the other editor fields. */
val DraftChecklistSaver = listSaver<SnapshotStateList<String>, String>(
    save = { it.toList() },
    restore = { it.toMutableStateList() },
)

/** Writes a saved task's checklist through the ViewModel. [task] is the state the editor was opened with. */
fun PulsoViewModel.checklistHandler(task: TaskItem): ChecklistHandler = object : ChecklistHandler {
    override fun add(text: String, done: (Throwable?) -> Unit) = addChecklistItem(task.id, text, done)
    override fun setChecked(id: String, checked: Boolean) = setChecklistItemChecked(task, id, checked)
    override fun edit(id: String, text: String, done: (Throwable?) -> Unit) = editChecklistItem(id, text, done)
    override fun move(id: String, index: Int) = moveChecklistItem(id, index)
    override fun delete(id: String) = deleteChecklistItem(id)
}

/**
 * Checklist block of the task editor (SPEC 013 §4): add (keeps focus), check, edit, move and delete.
 * [pending] is the text typed in "Adicionar item"; it is hoisted so the editor can add it on Salvar.
 */
@Composable
fun ChecklistSection(
    rows: List<ChecklistRow>,
    checkable: Boolean,
    handler: ChecklistHandler,
    pending: String,
    onPendingChange: (String) -> Unit,
) {
    var error by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<ChecklistRow?>(null) }
    val focus = remember { FocusRequester() }
    val checkedCount = rows.count { it.checked }
    Text(
        when {
            rows.isEmpty() -> "Itens"
            checkable -> "Itens ($checkedCount de ${rows.size} marcados)"
            else -> "Itens (${rows.size})"
        },
        style = MaterialTheme.typography.labelLarge,
    )
    rows.forEach { row ->
        val textColor = if (row.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
        val decoration = if (row.checked) TextDecoration.LineThrough else null
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            // One node for TalkBack: the checkbox state and the text are read together as "arroz, marcado".
            val toggle = if (checkable) Modifier.weight(1f).heightIn(min = 48.dp).toggleable(
                value = row.checked, role = Role.Checkbox, onValueChange = { handler.setChecked(row.id, it) })
            else Modifier.weight(1f).heightIn(min = 48.dp)
            Row(toggle, verticalAlignment = Alignment.CenterVertically) {
                if (checkable) {
                    Checkbox(row.checked, null) // visual only: the row carries the state and the click
                    Spacer(Modifier.width(8.dp))
                }
                Text(row.text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                    color = textColor, textDecoration = decoration)
            }
            IconButton(onClick = { editing = row }) {
                Icon(Icons.Rounded.Edit, contentDescription = "Editar ${row.text}")
            }
        }
    }
    fun submit() {
        if (pending.isBlank()) return
        handler.add(pending) { failure ->
            if (failure == null) { onPendingChange(""); error = null } else error = failure.message
        }
        focus.requestFocus()
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(pending, { if (it.length <= ChecklistRules.MAX_TEXT) onPendingChange(it) },
            label = { Text("Adicionar item") }, singleLine = true,
            modifier = Modifier.weight(1f).focusRequester(focus),
            // Done adds the item and keeps the field focused, so a list can be typed line by line.
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }))
        IconButton(onClick = ::submit, enabled = pending.isNotBlank()) {
            Icon(Icons.Rounded.Add, contentDescription = "Adicionar item")
        }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

    editing?.let { row ->
        val index = rows.indexOfFirst { it.id == row.id }
        var text by remember(row.id) { mutableStateOf(row.text) }
        var editError by remember(row.id) { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest = { editing = null }, title = { Text("Item da lista") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("Texto") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                    editError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled = index > 0, onClick = { handler.move(row.id, index - 1); editing = null }) {
                            Text("Subir")
                        }
                        OutlinedButton(enabled = index in 0 until rows.lastIndex,
                            onClick = { handler.move(row.id, index + 1); editing = null }) { Text("Descer") }
                    }
                    TextButton(onClick = { handler.delete(row.id); editing = null }) {
                        Text("Excluir item", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(enabled = text.isNotBlank(), onClick = {
                    handler.edit(row.id, text) { failure -> if (failure == null) editing = null else editError = failure.message }
                }) { Text("Salvar") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancelar") } })
    }
}
