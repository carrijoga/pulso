package dev.pulso.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.pulso.app.domain.InboxStatus
import dev.pulso.app.domain.ReviewRules
import dev.pulso.app.domain.SearchRules
import dev.pulso.app.domain.TaskItem
import dev.pulso.app.domain.TaskRules
import java.time.ZoneId

/** Searches tasks (title, notes, category, links) and pending/archived captures. Accent-insensitive. */
@Composable
fun SearchScreen(state: PulsoUiState, actions: ScreenActions, onRestoreInbox: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val tasks = if (query.isBlank()) emptyList() else state.tasks.filter { SearchRules.matchesTask(query, it) }
        .sortedBy { it.isCompleted }
    val inbox = if (query.isBlank()) emptyList() else state.searchableInbox.filter { SearchRules.matchesInbox(query, it) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(query, { query = it }, singleLine = true,
                label = { Text("Buscar em tarefas, observações, links e entrada") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().focusRequester(focus))
        }
        if (query.isBlank()) item {
            Text("Dica: a busca ignora acentos e maiúsculas. \"racao\" encontra \"Ração\".",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (tasks.isEmpty() && inbox.isEmpty()) item { EmptyMessage("Nada encontrado para \"${query.trim()}\".") }
        if (tasks.isNotEmpty()) {
            item { SectionHeading("Tarefas (${tasks.size})") }
            items(tasks, key = { "t-" + it.id }) { task ->
                TaskCard(task, { done -> actions.toggleTask(task, done) }, { actions.openTask(task) })
            }
        }
        if (inbox.isNotEmpty()) {
            item { SectionHeading("Entrada (${inbox.size})") }
            items(inbox, key = { "i-" + it.id }) { item ->
                androidx.compose.material3.Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(item.text, style = MaterialTheme.typography.bodyLarge)
                        val archived = item.status == InboxStatus.ARCHIVED
                        Text(if (archived) "Arquivada · ${formatDateTime(item.createdAt)}" else formatDateTime(item.createdAt),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (archived) TextButton(onClick = { onRestoreInbox(item.id) }) { Text("Devolver para a entrada") }
                        else Button(onClick = { actions.organizeInbox(item) }) { Text("Organizar") }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Guided weekly review: empty the inbox, decide on overdue tasks, look at the coming week. */
@Composable
fun ReviewScreen(
    state: PulsoUiState,
    actions: ScreenActions,
    onReschedule: (String, Long) -> Unit,
    onClearDue: (TaskItem) -> Unit,
    onOpenInbox: () -> Unit,
    onFinish: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val zone = ZoneId.systemDefault()
    val review = ReviewRules.build(state.tasks, now, zone)
    val fallback = state.settings.defaultDueTime
    fun move(task: TaskItem, days: Long) =
        onReschedule(task.id, TaskRules.rescheduleFromToday(task.dueAt, days, now, zone, fallback))

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Spacer(Modifier.height(8.dp))
            Text("Revise sua semana", style = MaterialTheme.typography.headlineMedium)
            Text(state.settings.lastReviewAt?.let { "Última revisão: ${formatDateTime(it)}" } ?: "Primeira revisão.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { SectionHeading("1. Entrada (${state.inbox.size})") }
        item {
            if (state.inbox.isEmpty()) EmptyMessage("Entrada vazia. Ótimo!")
            else androidx.compose.material3.Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Organize ou arquive o que ficou da semana.")
                    Button(onClick = onOpenInbox) { Text("Ir para a entrada") }
                }
            }
        }
        item { SectionHeading("2. Atrasadas (${review.overdue.size})") }
        if (review.overdue.isEmpty()) item { EmptyMessage("Nenhuma tarefa atrasada.") }
        items(review.overdue, key = { "o-" + it.id }) { task ->
            ReviewTaskCard(task, actions) {
                OutlinedButton(onClick = { move(task, 1) }) { Text("Amanhã") }
                OutlinedButton(onClick = { move(task, 7) }) { Text("+7 dias") }
                // A repeating task needs its due date (the first occurrence); the editor explains why.
                if (task.recurrence == null) TextButton(onClick = { onClearDue(task) }) { Text("Sem prazo") }
            }
        }
        if (review.frequentlyRescheduled.isNotEmpty()) {
            item { SectionHeading("3. Muito reagendadas (${review.frequentlyRescheduled.size})") }
            item {
                Text("Adiadas ${ReviewRules.FREQUENT_RESCHEDULES} vezes ou mais. Talvez valha dividir em passos menores, tirar o prazo ou concluir de vez.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(review.frequentlyRescheduled, key = { "f-" + it.id }) { task ->
                ReviewTaskCard(task, actions) {
                    if (task.recurrence == null) TextButton(onClick = { onClearDue(task) }) { Text("Tirar prazo") }
                    TextButton(onClick = { actions.openTask(task) }) { Text("Editar") }
                }
            }
        }
        item { SectionHeading("Próximos 7 dias (${review.nextSevenDays.size})") }
        if (review.nextSevenDays.isEmpty()) item { EmptyMessage("Nada com prazo na próxima semana.") }
        items(review.nextSevenDays, key = { "n-" + it.id }) { task ->
            TaskCard(task, { done -> actions.toggleTask(task, done) }, { actions.openTask(task) })
        }
        item {
            SectionHeading("Sem prazo (${review.withoutDue.size})")
            if (review.withoutDue.isNotEmpty()) Text(review.withoutDue.take(5).joinToString(" · ") { it.title } +
                if (review.withoutDue.size > 5) " …" else "", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            SectionHeading("Concluídas nos últimos 7 dias (${review.completedLastSevenDays.size})")
            review.completedLastSevenDays.take(10).forEach { Text("✓ ${it.title}", style = MaterialTheme.typography.bodySmall) }
        }
        item {
            Spacer(Modifier.height(8.dp))
            Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text("Concluir revisão") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ReviewTaskCard(task: TaskItem, actions: ScreenActions, buttons: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TaskCard(task, { done -> actions.toggleTask(task, done) }, { actions.openTask(task) })
        Row(Modifier.padding(start = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { buttons() }
        if (task.rescheduleCount > 0) Text("Reagendada ${task.rescheduleCount}×",
            Modifier.padding(start = 12.dp), style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
