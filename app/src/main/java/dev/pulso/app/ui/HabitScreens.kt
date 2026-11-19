package dev.pulso.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.pulso.app.PulsoApplication
import dev.pulso.app.domain.DaysOfWeekMask
import dev.pulso.app.domain.Habit
import dev.pulso.app.domain.HabitDraft
import dev.pulso.app.domain.HabitRules
import dev.pulso.app.domain.HabitScheduleType
import dev.pulso.app.domain.HabitToday
import dev.pulso.app.ui.components.EmptyState
import dev.pulso.app.ui.components.PulsoCheck
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/** The habit ViewModel, scoped to the activity like [PulsoViewModel] (see [HabitViewModelFactory]). */
@Composable
fun habitViewModel(): HabitViewModel {
    val application = LocalContext.current.applicationContext as PulsoApplication
    return viewModel(factory = HabitViewModelFactory(application.container.habits))
}

/**
 * Habits section of Hoje (SPEC 009): only shown when some habit is due today. Each row has a 48dp
 * checkbox; the snackbar offers "Desfazer" through the ViewModel messages.
 */
internal fun LazyListScope.habitTodaySection(
    state: HabitUiState,
    today: LocalDate,
    zone: ZoneId,
    onCheck: (Habit, Boolean) -> Unit,
    onManage: () -> Unit,
) {
    val due = HabitRules.dueToday(state.habits, state.checkins, today, zone)
    if (due.isEmpty()) return
    item(key = "habits-heading") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionHeading("Hábitos (${due.size})", Modifier.weight(1f))
            TextButton(onClick = onManage) { Text("Gerenciar") }
        }
    }
    items(due, key = { "habit-${it.habit.id}" }) { item ->
        HabitTodayRow(item, onCheck = { checked -> onCheck(item.habit, checked) }, Modifier.animateItem())
    }
}

@Composable
private fun HabitTodayRow(item: HabitToday, onCheck: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val name = item.habit.name
    // Streaks of TIMES_PER_WEEK count weeks; the other schedules count days.
    val streakUnit = if (item.habit.scheduleType == HabitScheduleType.TIMES_PER_WEEK) "semanas" else "dias"
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            PulsoCheck(
                checked = item.doneToday,
                onCheckedChange = onCheck,
                label = if (item.doneToday) "Desmarcar $name como feito hoje" else "Marcar $name como feito hoje",
            )
            Column(Modifier.weight(1f).padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${item.progress.done}/${item.progress.target} nesta semana",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.streak >= 2) Text("${item.streak} $streakUnit seguidas",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** Overlay "Hábitos" (SPEC 009): create, edit, reorder, archive/restore and delete. */
@Composable
fun HabitManagerScreen(state: HabitUiState, vm: HabitViewModel) {
    // "new" or the id of the habit being edited; stored as text so it survives rotation.
    var editorKey by rememberSaveable { mutableStateOf<String?>(null) }
    val habits = state.habits
    val active = habits.filterNot { it.archived }
    val archived = habits.filter { it.archived }
    val editing = editorKey?.takeIf { it != "new" }?.let { id -> habits.firstOrNull { it.id == id } }
    LaunchedEffect(state.loaded, editing == null, editorKey) {
        // The habit disappeared (deleted elsewhere): close its editor.
        if (state.loaded && editorKey != null && editorKey != "new" && editing == null) editorKey = null
    }

    LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "title") { ScreenTitle("Hábitos", "Rotinas que se marcam com um toque por dia.") }
        item(key = "new") {
            FilledTonalButton(onClick = { editorKey = "new" }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Novo hábito")
            }
        }
        if (state.loaded && habits.isEmpty()) item(key = "empty") {
            EmptyState(Icons.Rounded.Repeat, "Nenhum hábito ainda",
                "Beber água, ler 20 minutos, treinar três vezes por semana. Você marca com um toque.")
        }
        if (active.isNotEmpty()) item(key = "h-active") { SectionHeading("Ativos (${active.size})") }
        else if (archived.isNotEmpty()) item(key = "h-none-active") {
            Text("Nenhum hábito ativo. Os arquivados estão abaixo.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        itemsIndexed(active, key = { _, habit -> habit.id }) { index, habit ->
            HabitManageRow(
                habit = habit,
                canMoveUp = index > 0,
                canMoveDown = index < active.lastIndex,
                // Positions are indexes in the full list (archived included): see HabitRepository.move.
                onMoveUp = { vm.move(habit.id, habits.indexOf(active[index - 1])) },
                onMoveDown = { vm.move(habit.id, habits.indexOf(active[index + 1])) },
                onEdit = { editorKey = habit.id },
                modifier = Modifier.animateItem(),
            )
        }
        if (archived.isNotEmpty()) {
            item(key = "h-archived") { SectionHeading("Arquivados (${archived.size})") }
            items(archived, key = { "archived-${it.id}" }) { habit ->
                HabitArchivedRow(habit, onRestore = { vm.setArchived(habit, false) },
                    onEdit = { editorKey = habit.id }, modifier = Modifier.animateItem())
            }
        }
    }

    val key = editorKey
    if (key != null && (key == "new" || editing != null)) {
        HabitEditorSheet(
            habit = editing,
            onDismiss = { editorKey = null },
            onSave = { draft, done ->
                val onDone: (Throwable?) -> Unit = { failure -> if (failure == null) editorKey = null; done(failure) }
                if (editing == null) vm.create(draft, onDone) else vm.edit(editing.id, draft, onDone)
            },
            onToggleArchived = editing?.let { habit -> { vm.setArchived(habit, !habit.archived); editorKey = null } },
            onDelete = editing?.let { habit -> { vm.delete(habit); editorKey = null } },
        )
    }
}

@Composable
private fun HabitManageRow(
    habit: Habit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(habit.name, style = MaterialTheme.typography.titleMedium)
            Text(HabitRules.describe(habit), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Subir ${habit.name}")
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Descer ${habit.name}")
                }
                TextButton(onClick = onEdit, modifier = Modifier.semantics { contentDescription = "Editar ${habit.name}" }) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text("Editar")
                }
            }
        }
    }
}

@Composable
private fun HabitArchivedRow(habit: Habit, onRestore: () -> Unit, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(start = 18.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(habit.name, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(HabitRules.describe(habit), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onEdit, modifier = Modifier.semantics { contentDescription = "Editar ${habit.name}" }) {
                    Text("Editar")
                }
                TextButton(onClick = onRestore, modifier = Modifier.semantics { contentDescription = "Reativar ${habit.name}" }) {
                    Icon(Icons.Rounded.Unarchive, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text("Reativar")
                }
            }
        }
    }
}

/**
 * Create/edit sheet. Only the name is required; the schedule picks which fields apply.
 * Errors from [HabitRules.validateDraft] come back through [onSave] and are shown here.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun HabitEditorSheet(
    habit: Habit?,
    onDismiss: () -> Unit,
    onSave: (HabitDraft, (Throwable?) -> Unit) -> Unit,
    onToggleArchived: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    var name by rememberSaveable(habit?.id) { mutableStateOf(habit?.name ?: "") }
    var notes by rememberSaveable(habit?.id) { mutableStateOf(habit?.notes ?: "") }
    var scheduleType by rememberSaveable(habit?.id) { mutableStateOf(habit?.scheduleType ?: HabitScheduleType.DAILY) }
    var daysMask by rememberSaveable(habit?.id) { mutableIntStateOf(DaysOfWeekMask.toMask(habit?.daysOfWeek ?: emptySet())) }
    var times by rememberSaveable(habit?.id) { mutableIntStateOf(habit?.timesPerWeek?.takeIf { it > 0 } ?: 3) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun save() {
        if (busy || name.isBlank()) return
        busy = true
        val draft = HabitDraft(name, notes, scheduleType, DaysOfWeekMask.fromMask(daysMask), times)
        onSave(draft) { failure ->
            busy = false
            error = failure?.message
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !busy })

    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (habit == null) "Novo hábito" else "Editar hábito",
                style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") }
            Button(enabled = !busy && name.isNotBlank(), onClick = ::save) { Text(if (busy) "Salvando…" else "Salvar") }
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding()
            .padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { if (it.length <= HabitRules.MAX_NAME) name = it }, label = { Text("Nome") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small,
                textStyle = MaterialTheme.typography.titleMedium,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            OutlinedTextField(notes, { if (it.length <= HabitRules.MAX_NOTES) notes = it },
                label = { Text("Observações (opcional)") }, modifier = Modifier.fillMaxWidth(),
                minLines = 2, maxLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))

            Text("Quando", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HabitScheduleType.entries.forEach { option ->
                    FilterChip(selected = scheduleType == option, onClick = { scheduleType = option },
                        label = { Text(scheduleLabel(option)) },
                        modifier = Modifier.minimumInteractiveComponentSize())
                }
            }
            when (scheduleType) {
                HabitScheduleType.DAILY -> Unit
                HabitScheduleType.DAYS_OF_WEEK -> {
                    Text("Dias da semana", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        DayOfWeek.entries.forEach { day ->
                            val bit = 1 shl (day.value - 1)
                            val selected = daysMask and bit != 0
                            FilterChip(selected = selected, onClick = { daysMask = daysMask xor bit },
                                label = { Text(shortDayName(day)) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                                    .semantics { contentDescription = fullDayName(day) })
                        }
                    }
                }
                HabitScheduleType.TIMES_PER_WEEK -> {
                    Text("Vezes por semana", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        (1..7).forEach { n ->
                            FilterChip(selected = times == n, onClick = { times = n },
                                label = { Text("$n") },
                                modifier = Modifier.minimumInteractiveComponentSize()
                                    .semantics { contentDescription = if (n == 1) "1 vez por semana" else "$n vezes por semana" })
                        }
                    }
                }
            }
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)

            if (onToggleArchived != null || onDelete != null) {
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (onToggleArchived != null) TextButton(enabled = !busy, onClick = onToggleArchived) {
                        Icon(if (habit?.archived == true) Icons.Rounded.Unarchive else Icons.Rounded.Archive,
                            contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                        Text(if (habit?.archived == true) "Reativar" else "Arquivar")
                    }
                    Spacer(Modifier.weight(1f))
                    if (onDelete != null) TextButton(enabled = !busy, onClick = { confirmDelete = true }) {
                        Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                        Text("Excluir", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (confirmDelete && onDelete != null && habit != null) {
        AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Excluir \"${habit.name}\"?") },
            text = { Text("O histórico de marcações fica guardado. Você pode desfazer logo em seguida.") },
            confirmButton = { Button(onClick = { confirmDelete = false; onDelete() }) { Text("Excluir") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } })
    }
}

private fun scheduleLabel(type: HabitScheduleType): String = when (type) {
    HabitScheduleType.DAILY -> "Todo dia"
    HabitScheduleType.DAYS_OF_WEEK -> "Dias da semana"
    HabitScheduleType.TIMES_PER_WEEK -> "Vezes na semana"
}

private fun shortDayName(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "Seg"
    DayOfWeek.TUESDAY -> "Ter"
    DayOfWeek.WEDNESDAY -> "Qua"
    DayOfWeek.THURSDAY -> "Qui"
    DayOfWeek.FRIDAY -> "Sex"
    DayOfWeek.SATURDAY -> "Sáb"
    DayOfWeek.SUNDAY -> "Dom"
}

private fun fullDayName(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "segunda-feira"
    DayOfWeek.TUESDAY -> "terça-feira"
    DayOfWeek.WEDNESDAY -> "quarta-feira"
    DayOfWeek.THURSDAY -> "quinta-feira"
    DayOfWeek.FRIDAY -> "sexta-feira"
    DayOfWeek.SATURDAY -> "sábado"
    DayOfWeek.SUNDAY -> "domingo"
}
