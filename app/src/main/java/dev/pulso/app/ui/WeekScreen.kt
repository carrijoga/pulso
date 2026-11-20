package dev.pulso.app.ui

import android.app.DatePickerDialog
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.pulso.app.domain.DayView
import dev.pulso.app.domain.HabitDayMark
import dev.pulso.app.domain.ProjectedOccurrence
import dev.pulso.app.domain.TaskItem
import dev.pulso.app.domain.TaskRules
import dev.pulso.app.domain.WeekRules
import dev.pulso.app.ui.components.PtBr
import dev.pulso.app.ui.components.PulsoCheck
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dayShort = DateTimeFormatter.ofPattern("EEE d", PtBr)
private val dayLong = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", PtBr)
private val monthShort = DateTimeFormatter.ofPattern("d MMM", PtBr)
private val clock = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Tab "Semana" (SPEC 010): seven days from Monday, overdue tasks on top of the current week, future
 * occurrences of repeating tasks ("Previsto", never stored) and a habit mark per scheduled day.
 * Only reads the state; writes go through the task and habit ViewModels.
 */
@Composable
fun WeekScreen(
    state: PulsoUiState,
    habits: HabitUiState,
    habitVm: HabitViewModel,
    actions: ScreenActions,
    listState: LazyListState,
    focusDay: LocalDate,
    onFocusDay: (LocalDate) -> Unit,
    onReschedule: (String, Long) -> Unit,
    onOpenTasks: () -> Unit,
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    // "Hoje" must not go stale while the app stays open across midnight: re-read it on every resume.
    var today by remember { mutableStateOf(LocalDate.now(zone)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) today = LocalDate.now(zone)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val now = System.currentTimeMillis()
    val fallbackTime = state.settings.defaultDueTime
    val weekStart = WeekRules.weekStart(focusDay)
    val week = WeekRules.build(state.tasks, WeekRules.seriesOf(state.tasks), habits.habits, habits.checkins,
        weekStart, now, zone)
    val habitById = habits.habits.associateBy { it.id }
    val tasksById = state.tasks.associateBy { it.id }
    var showDone by rememberSaveable { mutableStateOf(true) }
    // Id of the task whose "Mover para…" dialog is open; kept as text so it survives rotation.
    var movingId by rememberSaveable { mutableStateOf<String?>(null) }

    fun moveTo(taskId: String, day: LocalDate) {
        val task = tasksById[taskId] ?: return
        onReschedule(taskId, WeekRules.moveToDay(task.dueAt, day, fallbackTime, zone))
    }

    LazyColumn(state = listState, contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "title") { ScreenTitle("Semana", "O que vence em cada dia e os hábitos da semana.") }
        item(key = "nav") {
            WeekNav(
                weekStart = weekStart,
                onPrevious = { onFocusDay(focusDay.minusWeeks(1)) },
                onNext = { onFocusDay(focusDay.plusWeeks(1)) },
                onToday = { onFocusDay(today) },
            )
        }
        item(key = "show-done") {
            FilterChip(selected = showDone, onClick = { showDone = !showDone },
                label = { Text("Mostrar concluídas") }, modifier = Modifier.minimumInteractiveComponentSize())
        }
        if (week.overdue.isNotEmpty()) {
            item(key = "h-overdue") { SectionHeading("Atrasadas (${week.overdue.size})") }
            items(week.overdue, key = { "o-${it.id}" }) { task ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TaskCard(task, { done -> actions.toggleTask(task, done) }, { actions.openTask(task) })
                    Row(Modifier.padding(start = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Keeps the task's time (TaskRules.rescheduleFromToday), so a time already past reads as overdue again.
                        TextButton(
                            onClick = { onReschedule(task.id, TaskRules.rescheduleFromToday(task.dueAt, 0L, now, zone, fallbackTime)) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                                .semantics { contentDescription = "Reagendar ${task.title} para hoje" },
                        ) { Text("Hoje") }
                        TextButton(
                            onClick = { onReschedule(task.id, TaskRules.rescheduleFromToday(task.dueAt, 1L, now, zone, fallbackTime)) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                                .semantics { contentDescription = "Reagendar ${task.title} para amanhã" },
                        ) { Text("Amanhã") }
                    }
                }
            }
        }
        week.days.forEach { day ->
            item(key = "day-${day.date}") {
                DayHeader(day, focused = day.date == focusDay, onFocus = { onFocusDay(day.date) })
            }
            if (day.habits.isNotEmpty()) item(key = "habits-${day.date}") {
                HabitMarks(day.habits, day.date) { mark, checked ->
                    habitById[mark.habitId]?.let { habitVm.setChecked(it, day.date, checked) }
                }
            }
            items(day.active, key = { "a-${day.date}-${it.id}" }) { task ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    TaskCard(task, { done -> actions.toggleTask(task, done) }, { actions.openTask(task) })
                    TextButton(
                        onClick = { movingId = task.id },
                        modifier = Modifier.padding(start = 8.dp).minimumInteractiveComponentSize()
                            .semantics { contentDescription = "Mover ${task.title} para outro dia" },
                    ) { Text("Mover para…") }
                }
            }
            if (showDone) items(day.completed, key = { "c-${day.date}-${it.id}" }) { task ->
                TaskCard(task, { done -> actions.toggleTask(task, done) }, { actions.openTask(task) })
            }
            items(day.projected, key = { "p-${day.date}-${it.seriesId}" }) { projection ->
                ProjectedRow(projection, zone) { tasksById[projection.activeTaskId]?.let(actions.openTask) }
            }
            if (day.active.isEmpty() && day.completed.isEmpty() && day.projected.isEmpty()) {
                item(key = "free-${day.date}") {
                    Text("Livre", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
        if (week.withoutDueCount > 0) item(key = "no-due") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val n = week.withoutDueCount
                Text(if (n == 1) "1 tarefa sem prazo" else "$n tarefas sem prazo", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = onOpenTasks) { Text("Ver em Tarefas") }
            }
        }
    }

    tasksById[movingId.orEmpty()]?.let { task ->
        MoveTaskDialog(
            task = task,
            weekDays = WeekRules.daysOf(weekStart),
            onDismiss = { movingId = null },
            onPick = { day -> movingId = null; moveTo(task.id, day) },
            onOtherDate = {
                movingId = null
                pickDate(context, task.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: today) {
                    moveTo(task.id, it)
                }
            },
        )
    }
}

@Composable
private fun WeekNav(weekStart: LocalDate, onPrevious: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(rangeLabel(weekStart), style = MaterialTheme.typography.titleMedium, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Semana anterior")
        }
        TextButton(onClick = onToday, modifier = Modifier.minimumInteractiveComponentSize()) { Text("Hoje") }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Próxima semana")
        }
    }
}

/** Header of one day; tapping it makes that day the focus of the "+" button (the last day touched). */
@Composable
private fun DayHeader(day: DayView, focused: Boolean, onFocus: () -> Unit) {
    val count = day.active.size + day.completed.size + day.projected.size
    val summary = when (count) { 0 -> "livre"; 1 -> "1 tarefa"; else -> "$count tarefas" }
    val longName = day.date.format(dayLong).replaceFirstChar { it.titlecase(PtBr) }
    Row(
        Modifier.fillMaxWidth()
            .clickable(role = Role.Button, onClick = onFocus)
            .semantics { contentDescription = "$longName, $summary" }
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(day.date.format(dayShort), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            color = if (focused || day.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        if (day.isToday) {
            Spacer(Modifier.width(8.dp))
            Text("Hoje", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.weight(1f))
        Text(summary, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One compact marker per scheduled habit. Past days inside the 7-day window are tappable; others are read-only. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HabitMarks(marks: List<HabitDayMark>, date: LocalDate, onCheck: (HabitDayMark, Boolean) -> Unit) {
    val dateText = date.format(dayLong)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        marks.forEach { mark ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (mark.editable) {
                    PulsoCheck(mark.done, { checked -> onCheck(mark, checked) },
                        label = if (mark.done) "Desmarcar ${mark.name} em $dateText" else "Marcar ${mark.name} em $dateText")
                } else {
                    Box(Modifier.size(48.dp).semantics {
                        contentDescription = "${mark.name} em $dateText: ${if (mark.done) "feito" else "não feito"}, fora do prazo de marcação"
                    }, contentAlignment = Alignment.Center) {
                        Icon(if (mark.done) Icons.Rounded.Check else Icons.Rounded.Remove, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                }
                Text(mark.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Ghost row for a projected occurrence: no checkbox; opens the open occurrence of its series. */
@Composable
private fun ProjectedRow(item: ProjectedOccurrence, zone: ZoneId, onOpen: () -> Unit) {
    val time = Instant.ofEpochMilli(item.dueAt).atZone(zone).format(clock)
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Repeat, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(item.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Previsto · $time", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MoveTaskDialog(
    task: TaskItem,
    weekDays: List<LocalDate>,
    onPick: (LocalDate) -> Unit,
    onOtherDate: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mover \"${task.title}\" para…") },
        text = {
            Column {
                weekDays.forEach { day ->
                    TextButton(onClick = { onPick(day) }, modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()) {
                        Text(day.format(dayLong).replaceFirstChar { it.titlecase(PtBr) })
                    }
                }
                TextButton(onClick = onOtherDate, modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize()) {
                    Text("Escolher data")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private fun pickDate(context: Context, initial: LocalDate, onPicked: (LocalDate) -> Unit) {
    DatePickerDialog(context, { _, year, month, day -> onPicked(LocalDate.of(year, month + 1, day)) },
        initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
}

/** "17 – 23 nov." or "29 nov. – 5 dez." */
private fun rangeLabel(weekStart: LocalDate): String {
    val end = weekStart.plusDays(6)
    return if (weekStart.month == end.month) "${weekStart.dayOfMonth} – ${end.format(monthShort)}"
    else "${weekStart.format(monthShort)} – ${end.format(monthShort)}"
}
