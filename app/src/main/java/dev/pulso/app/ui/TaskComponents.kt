package dev.pulso.app.ui

import android.app.DatePickerDialog
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.draw.clip
import dev.pulso.app.domain.DueLabel
import dev.pulso.app.ui.components.PulsoCheck
import dev.pulso.app.ui.components.SectionHeader
import dev.pulso.app.ui.components.categoryColor
import dev.pulso.app.ui.theme.LocalPulsoColors
import dev.pulso.app.ui.theme.PulsoMotion
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.pulso.app.domain.Category
import dev.pulso.app.domain.DueState
import dev.pulso.app.domain.Priority
import dev.pulso.app.domain.RescheduleEntry
import dev.pulso.app.domain.TaskDraft
import dev.pulso.app.domain.TaskItem
import dev.pulso.app.domain.TaskRules
import dev.pulso.app.reminders.NotificationAccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val dateTimeFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm")
private val shortDate = DateTimeFormatter.ofPattern("dd/MM")

internal fun formatDateTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(millis).atZone(zone).format(dateTimeFormat)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskCard(task: TaskItem, onToggle: (Boolean) -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val now = System.currentTimeMillis()
    val zone = ZoneId.systemDefault()
    val due = task.dueAt?.takeIf { !task.isCompleted }?.let { TaskRules.dueLabel(it, now, zone) }
    val contentAlpha by animateFloatAsState(if (task.isCompleted) 0.55f else 1f, tween(PulsoMotion.MEDIUM), label = "taskAlpha")
    val container by animateColorAsState(
        if (due?.state == DueState.OVERDUE) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.surfaceContainerLow, tween(PulsoMotion.MEDIUM), label = "taskContainer")
    Surface(onClick = onOpen, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = container) {
        Row(Modifier.padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 6.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically) {
            PulsoCheck(task.isCompleted, onToggle,
                label = if (task.isCompleted) "Reabrir ${task.title}" else "Concluir ${task.title}")
            Column(Modifier.weight(1f).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(task.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically) {
                    if (due != null) DueBadge(due)
                    CategoryDot(task.category, contentAlpha)
                    if (task.priority == Priority.HIGH && !task.isCompleted) MetaIcon(Icons.Rounded.Flag, "Prioridade alta", null,
                        tint = MaterialTheme.colorScheme.primary)
                    if (task.snoozedUntil != null && !task.isCompleted) MetaIcon(Icons.Rounded.Snooze, "Lembrete adiado", null)
                    if (task.rescheduleCount > 0) MetaIcon(Icons.Rounded.Update,
                        "Reagendada ${task.rescheduleCount} vezes", "${task.rescheduleCount}")
                    val links = TaskRules.linkList(task.links).size
                    if (links > 0) MetaIcon(Icons.Rounded.Link, "$links links", "$links")
                    if (task.notes.isNotBlank()) MetaIcon(Icons.AutoMirrored.Rounded.Notes, "Tem observações", null)
                }
            }
        }
    }
}

@Composable
fun DueBadge(due: DueLabel) {
    val extra = LocalPulsoColors.current
    val (bg, fg) = when (due.state) {
        DueState.OVERDUE -> extra.overdueContainer to MaterialTheme.colorScheme.onErrorContainer
        DueState.TODAY -> extra.todayContainer to MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Event, contentDescription = null, tint = fg, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(due.text, style = MaterialTheme.typography.labelSmall, color = fg)
    }
}

@Composable
private fun CategoryDot(category: String, alpha: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(categoryColor(category).copy(alpha = alpha)))
        Spacer(Modifier.width(5.dp))
        Text(category, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MetaIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, text: String?,
                     tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(Modifier.semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp), tint = tint)
        if (text != null) {
            Spacer(Modifier.width(2.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Create/edit/organize dialog. Only the title is required (capture-first: metadata is optional).
 * [existing] enables edit-only features (history, delete).
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorDialog(
    initial: TaskDraft,
    title: String,
    categories: List<Category>,
    defaultDueTime: LocalTime,
    notificationAccess: NotificationAccess,
    onDismiss: () -> Unit,
    onSave: (TaskDraft, (Throwable?) -> Unit) -> Unit,
    onAddCategory: (String, (Throwable?) -> Unit) -> Unit,
    onRequestNotifications: () -> Unit,
    existing: TaskItem? = null,
    history: Flow<List<RescheduleEntry>> = flowOf(emptyList()),
    onDelete: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    var text by rememberSaveable { mutableStateOf(initial.title) }
    var notes by rememberSaveable { mutableStateOf(initial.notes) }
    var links by rememberSaveable { mutableStateOf(initial.links) }
    var category by rememberSaveable { mutableStateOf(initial.category) }
    var priority by rememberSaveable { mutableStateOf(initial.priority) }
    var dueAt by rememberSaveable { mutableStateOf(initial.dueAt) }
    var newCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val entries by history.collectAsState(initial = emptyList())

    fun pickTime(date: LocalDate, current: LocalTime) {
        TimePickerDialog(context, { _, hour, minute ->
            dueAt = TaskRules.dueAtFor(date, LocalTime.of(hour, minute), zone)
        }, current.hour, current.minute, DateFormat.is24HourFormat(context)).show()
    }

    fun save() {
        if (busy || text.isBlank()) return
        busy = true
        onSave(TaskDraft(text, notes, category, priority, dueAt, links)) { failure ->
            busy = false
            error = failure?.message
        }
        if (dueAt != null && notificationAccess == NotificationAccess.NOT_REQUESTED) onRequestNotifications()
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !busy })

    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") }
                Button(enabled = !busy && text.isNotBlank(), onClick = ::save) { Text(if (busy) "Salvando…" else "Salvar") }
            }
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding()
                .padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(text, { if (it.length <= TaskRules.MAX_TITLE) text = it }, label = { Text("Título") },
                    modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small,
                    textStyle = MaterialTheme.typography.titleMedium,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                OutlinedTextField(notes, { notes = it }, label = { Text("Observações (opcional)") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                OutlinedTextField(links, { links = it }, label = { Text("Links (um por linha)") },
                    modifier = Modifier.fillMaxWidth(), maxLines = 4,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                LinkButtons(links)

                Text("Categoria", style = MaterialTheme.typography.labelLarge)
                val names = (categories.map { it.name } + category).distinct()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    names.forEach { name ->
                        FilterChip(selected = category == name, onClick = { category = name }, label = { Text(name) })
                    }
                    FilterChip(selected = false, onClick = { newCategory = "" }, label = { Text("Nova") },
                        leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, Modifier.size(16.dp)) })
                }
                newCategory?.let { draftName ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(draftName, { newCategory = it }, label = { Text("Nova categoria") },
                            singleLine = true, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                        TextButton(enabled = draftName.isNotBlank(), onClick = {
                            onAddCategory(draftName) { failure ->
                                if (failure == null) { category = draftName.trim(); newCategory = null; error = null }
                                else error = failure.message
                            }
                        }) { Text("Criar") }
                    }
                }

                Text("Prioridade", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Priority.entries.forEachIndexed { index, option ->
                        SegmentedButton(selected = priority == option, onClick = { priority = option },
                            colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                        shape = SegmentedButtonDefaults.itemShape(index, Priority.entries.size)) { Text(option.label) }
                    }
                }

                Text("Prazo e lembrete", style = MaterialTheme.typography.labelLarge)
                val dueLocal = dueAt?.let { Instant.ofEpochMilli(it).atZone(zone) }
                val today = LocalDate.now(zone)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Hoje" to today, "Amanhã" to today.plusDays(1), "Próxima semana" to today.plusDays(7)).forEach { (label, date) ->
                        FilterChip(selected = dueLocal?.toLocalDate() == date, label = { Text(label) }, onClick = {
                            dueAt = TaskRules.dueAtFor(date, dueLocal?.toLocalTime() ?: defaultDueTime, zone)
                        })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = {
                        val start = dueLocal?.toLocalDate() ?: LocalDate.now(zone)
                        DatePickerDialog(context, { _, year, month, day ->
                            val date = LocalDate.of(year, month + 1, day)
                            dueAt = TaskRules.dueAtFor(date, dueLocal?.toLocalTime() ?: defaultDueTime, zone)
                        }, start.year, start.monthValue - 1, start.dayOfMonth).show()
                    }) { Text(dueLocal?.toLocalDate()?.format(dateFormat) ?: "Definir prazo") }
                    if (dueLocal != null) OutlinedButton(onClick = { pickTime(dueLocal.toLocalDate(), dueLocal.toLocalTime()) }) {
                        Text(dueLocal.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")))
                    }
                }
                if (dueAt != null) {
                    TextButton(onClick = { dueAt = null }) { Text("Remover prazo") }
                    if (notificationAccess != NotificationAccess.ALLOWED) {
                        Text("Notificações desativadas: o lembrete não vai aparecer.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRequestNotifications) {
                            Text(if (notificationAccess == NotificationAccess.NOT_REQUESTED) "Permitir notificações"
                                else "Abrir ajustes de notificação")
                        }
                    } else {
                        Text("O lembrete chega perto do horário (pode atrasar alguns minutos).",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                existing?.snoozedUntil?.takeIf { !existing.isCompleted && dueAt == existing.dueAt }?.let {
                    Text("Lembrete adiado para ${formatDateTime(it)}.", style = MaterialTheme.typography.bodySmall)
                }
                if (entries.isNotEmpty()) RescheduleHistory(entries, zone)
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
                if (onDelete != null) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Excluir tarefa", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
    }
    if (confirmDelete && onDelete != null) {
        AlertDialog(onDismissRequest = { confirmDelete = false },
            title = { Text("Excluir tarefa?") },
            text = { Text("\"${text.trim()}\" sai das listas. Você pode desfazer logo em seguida.") },
            confirmButton = {
                Button(onClick = { confirmDelete = false; onDelete() }) { Text("Excluir") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } })
    }
}

@Composable
private fun LinkButtons(links: String) {
    val uriHandler = LocalUriHandler.current
    val openable = TaskRules.linkList(links).mapNotNull { raw ->
        when {
            raw.startsWith("http://", true) || raw.startsWith("https://", true) -> raw
            raw.startsWith("www.", true) -> "https://$raw"
            else -> null
        }
    }.take(5)
    openable.forEach { url ->
        TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) {
            Icon(Icons.Rounded.Link, contentDescription = null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Abrir ${url.substringAfter("://").substringBefore('/')}", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RescheduleHistory(entries: List<RescheduleEntry>, zone: ZoneId) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Reagendada ${entries.size} ${if (entries.size == 1) "vez" else "vezes"}",
                style = MaterialTheme.typography.labelLarge)
            entries.take(5).forEach { entry ->
                fun day(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).format(shortDate)
                Text("${day(entry.fromDueAt)} → ${day(entry.toDueAt)} (em ${day(entry.changedAt)})",
                    style = MaterialTheme.typography.bodySmall)
            }
            if (entries.size > 5) Text("…e mais ${entries.size - 5}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun SectionHeading(title: String, modifier: Modifier = Modifier) {
    // Titles arrive as "Vencidas e para hoje (3)": the count is shown in the accent color.
    val match = Regex("^(.*?)\\s*\\((\\d+)\\)$").find(title)
    SectionHeader(match?.groupValues?.get(1) ?: title, match?.groupValues?.get(2)?.toIntOrNull(), modifier)
}

/** Quiet inline empty/info row used inside lists. */
@Composable
fun EmptyMessage(message: String) {
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
        .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Rounded.Spa, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
