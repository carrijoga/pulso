package dev.pulso.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.pulso.app.domain.CategoryRules
import dev.pulso.app.domain.SearchRules
import dev.pulso.app.domain.TaskItem
import dev.pulso.app.ui.components.EmptyState

/** Overlay for one category (SPEC 013 §1): active tasks by state, completed ones collapsed. */
@Composable
fun CategoryScreen(category: String, state: PulsoUiState, actions: ScreenActions, onNewTask: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var showCompleted by rememberSaveable { mutableStateOf(false) }
    val overview = CategoryRules.overview(state.tasks, category, System.currentTimeMillis())
    fun shown(tasks: List<TaskItem>) = tasks.filter { query.isBlank() || SearchRules.matchesTask(query, it) }
    val overdue = shown(overview.overdue)
    val scheduled = shown(overview.scheduled)
    val withoutDue = shown(overview.withoutDue)
    val completed = shown(overview.completed)
    val anyActive = overdue.isNotEmpty() || scheduled.isNotEmpty() || withoutDue.isNotEmpty()

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "summary") {
            Text("${overview.activeCount} ativas · ${overview.overdue.size} atrasadas",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item(key = "new") {
            FilledTonalButton(onClick = { onNewTask(category) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Nova tarefa")
            }
        }
        item(key = "filter") {
            TextField(query, { query = it }, singleLine = true, placeholder = { Text("Filtrar em $category") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) TextButton(onClick = { query = "" }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "Limpar filtro")
                    }
                },
                shape = CircleShape, modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
        }
        taskSection("Atrasadas", overdue, actions)
        taskSection("Com prazo", scheduled, actions)
        taskSection("Sem prazo", withoutDue, actions)
        if (overview.completed.isNotEmpty()) {
            item(key = "completedToggle") {
                TextButton(onClick = { showCompleted = !showCompleted }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (showCompleted) "Ocultar concluídas" else "Mostrar concluídas (${overview.completed.size})")
                }
            }
            if (showCompleted) taskSection("Concluídas", completed, actions)
        }
        if (state.loaded && !anyActive && (!showCompleted || completed.isEmpty())) item(key = "empty") {
            EmptyState(Icons.Rounded.TaskAlt, "Nada aqui",
                if (query.isBlank()) "Nenhuma tarefa ativa em $category." else "Nada encontrado para \"${query.trim()}\".")
        }
    }
}

private fun LazyListScope.taskSection(title: String, tasks: List<TaskItem>, actions: ScreenActions) {
    if (tasks.isEmpty()) return
    item(key = "h-$title") { SectionHeading("$title (${tasks.size})") }
    items(tasks, key = { "$title-${it.id}" }) { task ->
        TaskCard(task, { done -> actions.toggleTask(task, done) }, { actions.openTask(task) }, Modifier.animateItem())
    }
}
