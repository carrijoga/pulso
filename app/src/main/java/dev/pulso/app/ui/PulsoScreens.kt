package dev.pulso.app.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pulso.app.CaptureActivity
import dev.pulso.app.domain.InboxItem
import dev.pulso.app.domain.ReviewRules
import dev.pulso.app.domain.SearchRules
import dev.pulso.app.domain.TaskDraft
import dev.pulso.app.domain.TaskItem
import dev.pulso.app.domain.TaskRules
import dev.pulso.app.ui.components.EmptyState
import dev.pulso.app.ui.components.PtBr
import dev.pulso.app.ui.components.PulseMark
import dev.pulso.app.ui.components.relativeTime
import dev.pulso.app.ui.theme.LocalPulsoColors
import dev.pulso.app.ui.theme.PulsoMotion
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class Tab(val label: String, val selectedIcon: ImageVector, val icon: ImageVector) {
    TODAY("Hoje", Icons.Rounded.Home, Icons.Outlined.Home),
    INBOX("Entrada", Icons.Rounded.Inbox, Icons.Outlined.Inbox),
    TASKS("Tarefas", Icons.Rounded.TaskAlt, Icons.Outlined.TaskAlt),
    SETTINGS("Ajustes", Icons.Rounded.Settings, Icons.Outlined.Settings),
}
private enum class Overlay(val title: String) { SEARCH("Buscar"), REVIEW("Revisão semanal") }

/** Which editor sheet is open. Stored as ids so it survives rotation and follows Room updates. */
private sealed interface Editor {
    data object NewTask : Editor
    data class Edit(val taskId: String) : Editor
    data class Organize(val inboxId: String) : Editor
}

/** Actions screens can trigger; keeps each screen independent of the scaffold. */
class ScreenActions(
    val openTask: (TaskItem) -> Unit,
    val toggleTask: (TaskItem, Boolean) -> Unit,
    val organizeInbox: (InboxItem) -> Unit,
)

/** Horizontal gutter shared by every screen. */
internal val ScreenPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 104.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulsoScreens(vm: PulsoViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    var overlay by rememberSaveable { mutableStateOf<Overlay?>(null) }
    var editorKey by rememberSaveable { mutableStateOf<String?>(null) }
    val editor: Editor? = editorKey?.let { key ->
        when {
            key == "new" -> Editor.NewTask
            key.startsWith("edit:") -> Editor.Edit(key.removePrefix("edit:"))
            key.startsWith("organize:") -> Editor.Organize(key.removePrefix("organize:"))
            else -> null
        }
    }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) {
        vm.messages.collect { message ->
            val result = snackbar.showSnackbar(message.text, actionLabel = message.undoLabel,
                withDismissAction = message.undoLabel == null,
                duration = if (message.undo != null) SnackbarDuration.Long else SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) message.undo?.invoke()
        }
    }
    val notifications = rememberNotificationPermission(state.settings.notificationPromptShown, vm::notificationPromptShown)
    val actions = ScreenActions(
        openTask = { editorKey = "edit:${it.id}" },
        toggleTask = { task, done -> vm.complete(task.id, done) },
        organizeInbox = { editorKey = "organize:${it.id}" },
    )
    BackHandler(enabled = overlay != null) { overlay = null }
    BackHandler(enabled = overlay == null && tab != Tab.TODAY) { tab = Tab.TODAY }

    // One list state per tab so scroll position survives switching, and drives the FAB.
    val listStates = Tab.entries.associateWith { rememberLazyListState() }
    val currentList = listStates.getValue(tab)
    val fabExpanded by remember(currentList) { derivedStateOf { currentList.firstVisibleItemIndex == 0 } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                navigationIcon = {
                    if (overlay != null) IconButton(onClick = { overlay = null }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar")
                    }
                },
                title = {
                    AnimatedContent(overlay, transitionSpec = { fadeIn(tween(PulsoMotion.SHORT)) togetherWith fadeOut(tween(PulsoMotion.SHORT)) },
                        label = "topTitle") { current ->
                        if (current != null) Text(current.title, style = MaterialTheme.typography.titleLarge)
                        else Row(verticalAlignment = Alignment.CenterVertically) {
                            PulseMark(size = 30.dp, animate = true)
                            Spacer(Modifier.width(10.dp))
                            Text("Pulso", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                actions = {
                    if (overlay == null) IconButton(onClick = { overlay = Overlay.SEARCH }) {
                        Icon(Icons.Rounded.Search, contentDescription = "Buscar em tudo")
                    }
                })
        },
        floatingActionButton = {
            AnimatedVisibility(overlay == null && tab != Tab.SETTINGS,
                enter = scaleIn(PulsoMotion.bouncy()) + fadeIn(), exit = scaleOut() + fadeOut()) {
                val newTask = tab == Tab.TASKS
                val fabLabel = if (newTask) "Nova tarefa" else "Capturar"
                ExtendedFloatingActionButton(
                    // The FAB's inner text is not exposed to accessibility (seen with TalkBack on the Moto G84),
                    // so the label goes on the button itself.
                    modifier = Modifier.semantics { contentDescription = fabLabel },
                    onClick = {
                        if (newTask) editorKey = "new"
                        else context.startActivity(Intent(context, CaptureActivity::class.java))
                    },
                    expanded = fabExpanded,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(if (newTask) Icons.Rounded.Add else Icons.Rounded.EditNote, contentDescription = null) },
                    text = { Text(fabLabel) })
            }
        },
        bottomBar = {
            AnimatedVisibility(overlay == null, enter = slideInVertically { it } + fadeIn(), exit = fadeOut()) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Tab.entries.forEach { item ->
                        val selected = tab == item
                        NavigationBarItem(selected = selected, onClick = { tab = item },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary),
                            icon = {
                                BadgedBox(badge = {
                                    if (item == Tab.INBOX && state.inbox.isNotEmpty()) Badge { Text("${state.inbox.size}") }
                                }) { Icon(if (selected) item.selectedIcon else item.icon, contentDescription = null) }
                            },
                            label = { Text(item.label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) })
                    }
                }
            }
        }
    ) { contentPadding ->
        Box(Modifier.fillMaxSize().padding(contentPadding)) {
            AnimatedContent(
                targetState = overlay?.name ?: tab.name,
                transitionSpec = {
                    (fadeIn(tween(PulsoMotion.MEDIUM, delayMillis = 60)) +
                        slideInVertically(tween(PulsoMotion.MEDIUM, easing = PulsoMotion.Emphasized)) { it / 24 }) togetherWith
                        fadeOut(tween(PulsoMotion.SHORT))
                },
                label = "screen",
            ) { key ->
                when (key) {
                    Overlay.SEARCH.name -> Box(Modifier.padding(horizontal = 16.dp)) {
                        SearchScreen(state, actions, onRestoreInbox = vm::restoreInbox)
                    }
                    Overlay.REVIEW.name -> Box(Modifier.padding(horizontal = 16.dp)) {
                        ReviewScreen(state, actions,
                            onReschedule = vm::reschedule, onClearDue = vm::clearDue,
                            onOpenInbox = { overlay = null; tab = Tab.INBOX },
                            onFinish = { vm.markReviewDone(); overlay = null })
                    }
                    Tab.TODAY.name -> TodayScreen(state, actions, listStates.getValue(Tab.TODAY),
                        onReview = { overlay = Overlay.REVIEW },
                        onOpenInbox = { tab = Tab.INBOX }, onOpenTasks = { tab = Tab.TASKS })
                    Tab.INBOX.name -> InboxScreen(state.inbox, vm, actions, listStates.getValue(Tab.INBOX))
                    Tab.TASKS.name -> TaskScreen(state, actions, listStates.getValue(Tab.TASKS))
                    else -> Column(Modifier.padding(horizontal = 16.dp)) { SettingsScreen(state, vm, notifications) }
                }
            }
        }
    }

    val dismiss = { editorKey = null }
    val common = @Composable { initial: TaskDraft, title: String, existing: TaskItem?,
                               onSave: (TaskDraft, (Throwable?) -> Unit) -> Unit ->
        TaskEditorDialog(initial = initial, title = title, categories = state.categories,
            defaultDueTime = state.settings.defaultDueTime, notificationAccess = notifications.access,
            onDismiss = dismiss, onSave = onSave, onAddCategory = vm::addCategory,
            onRequestNotifications = notifications.request, existing = existing,
            history = if (existing != null) remember(existing.id) { vm.rescheduleHistory(existing.id) }
                else remember { kotlinx.coroutines.flow.flowOf(emptyList()) },
            onDelete = existing?.let { task -> { dismiss(); vm.delete(task.id) } },
            onEndSeries = existing?.takeIf { it.recurrence != null }?.let { task -> { dismiss(); vm.endRecurrence(task.id) } })
    }
    when (editor) {
        Editor.NewTask -> common(TaskDraft(title = ""), "Nova tarefa", null) { draft, done ->
            vm.create(draft) { failure -> if (failure == null) dismiss(); done(failure) }
        }
        is Editor.Edit -> {
            val task = state.tasks.firstOrNull { it.id == editor.taskId }
            if (task == null) { if (state.loaded) LaunchedEffect(Unit) { dismiss() } }
            else common(task.toDraft(), "Editar tarefa", task) { draft, done ->
                vm.edit(task.id, draft) { failure -> if (failure == null) dismiss(); done(failure) }
            }
        }
        is Editor.Organize -> {
            val inbox = state.inbox.firstOrNull { it.id == editor.inboxId }
            if (inbox == null) { if (state.loaded) LaunchedEffect(Unit) { dismiss() } }
            else common(TaskDraft(title = inbox.text.take(TaskRules.MAX_TITLE),
                notes = if (inbox.text.length > TaskRules.MAX_TITLE) inbox.text else ""),
                "Organizar anotação", null) { draft, done ->
                vm.organize(inbox.id, draft) { failure -> if (failure == null) dismiss(); done(failure) }
            }
        }
        null -> Unit
    }
}

@Composable
private fun TodayScreen(
    state: PulsoUiState,
    actions: ScreenActions,
    listState: LazyListState,
    onReview: () -> Unit,
    onOpenInbox: () -> Unit,
    onOpenTasks: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val zone = ZoneId.systemDefault()
    val active = state.tasks.filterNot { it.isCompleted }
    val due = active.filter { TaskRules.isDueTodayOrOverdue(it, now, zone) }.sortedBy { it.dueAt }
    val upcoming = active.filterNot { it in due }
    val today = LocalDate.now(zone)
    val doneToday = state.tasks.count { task ->
        task.completedAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == today } == true
    }
    LazyColumn(state = listState, contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "hero") { TodayHero(due.size, doneToday, state.inbox.size) }
        if (state.loaded && ReviewRules.isReviewDue(state.settings.lastReviewAt, now) && state.tasks.isNotEmpty()) item(key = "review") {
            PromptCard(Icons.Rounded.AutoAwesome, "Hora da revisão semanal",
                "Alguns minutos para reagendar atrasadas e esvaziar a entrada.", "Revisar", onReview,
                Modifier.animateItem())
        }
        if (state.inbox.isNotEmpty()) item(key = "inbox") {
            PromptCard(Icons.Rounded.Inbox,
                "${state.inbox.size} ${if (state.inbox.size == 1) "anotação espera" else "anotações esperam"} na entrada",
                "Organize quando tiver um tempinho.", "Ver", onOpenInbox, Modifier.animateItem())
        }
        item(key = "h-due") { SectionHeading("Vencidas e para hoje (${due.size})") }
        if (state.loaded && due.isEmpty()) item(key = "e-due") { EmptyMessage("Nada vence hoje. Respire.") }
        items(due, key = { it.id }) {
            TaskCard(it, { done -> actions.toggleTask(it, done) }, { actions.openTask(it) }, Modifier.animateItem())
        }
        item(key = "h-next") { SectionHeading("Próximas") }
        if (state.loaded && upcoming.isEmpty()) item(key = "e-next") { EmptyMessage("Nenhuma outra tarefa pendente.") }
        items(upcoming.take(5), key = { it.id }) {
            TaskCard(it, { done -> actions.toggleTask(it, done) }, { actions.openTask(it) }, Modifier.animateItem())
        }
        item(key = "more") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (upcoming.size > 5) TextButton(onClick = onOpenTasks) { Text("Ver todas as ${active.size}") } else Spacer(Modifier)
                TextButton(onClick = onReview) { Text("Revisão semanal") }
            }
        }
    }
}

/** Branded summary card: greeting, date and a gentle progress line (no streaks, no guilt). */
@Composable
private fun TodayHero(dueCount: Int, doneToday: Int, inboxCount: Int) {
    val extra = LocalPulsoColors.current
    val hour = LocalTime.now().hour
    val greeting = when (hour) { in 5..11 -> "Bom dia"; in 12..17 -> "Boa tarde"; else -> "Boa noite" }
    val date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", PtBr)).replaceFirstChar { it.titlecase(PtBr) }
    val total = dueCount + doneToday
    val progress by animateFloatAsState(if (total == 0) 0f else doneToday.toFloat() / total,
        tween(PulsoMotion.LONG, easing = PulsoMotion.Emphasized), label = "todayProgress")
    val onHero = if (extra.heroStart == MaterialTheme.colorScheme.primary) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onPrimaryContainer
    Box(Modifier.fillMaxWidth().padding(top = 4.dp).clip(MaterialTheme.shapes.large)
        .background(Brush.linearGradient(listOf(extra.heroStart, extra.heroEnd)))) {
        Icon(Icons.Rounded.WbSunny, contentDescription = null, tint = onHero.copy(alpha = 0.10f),
            modifier = Modifier.size(150.dp).align(Alignment.TopEnd).offset(x = 36.dp, y = (-36).dp))
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(date, style = MaterialTheme.typography.labelLarge, color = onHero.copy(alpha = 0.85f))
            Text(greeting, style = MaterialTheme.typography.headlineMedium, color = onHero)
            Spacer(Modifier.height(6.dp))
            Text(when {
                dueCount == 0 && doneToday > 0 -> "Tudo de hoje concluído. Bom trabalho."
                dueCount == 0 -> "Nada urgente por aqui."
                dueCount == 1 -> "1 tarefa para hoje."
                else -> "$dueCount tarefas para hoje."
            }, style = MaterialTheme.typography.bodyLarge, color = onHero)
            if (total > 0) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = onHero, trackColor = onHero.copy(alpha = 0.22f), drawStopIndicator = {})
                Text("$doneToday de $total concluídas hoje", style = MaterialTheme.typography.labelMedium,
                    color = onHero.copy(alpha = 0.85f))
            }
            if (inboxCount > 0) Text("$inboxCount na entrada", style = MaterialTheme.typography.labelMedium,
                color = onHero.copy(alpha = 0.85f))
        }
    }
}

@Composable
private fun PromptCard(icon: ImageVector, title: String, body: String, action: String, onClick: () -> Unit,
                       modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
internal fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(top = 4.dp, bottom = 4.dp, start = 4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InboxScreen(items: List<InboxItem>, vm: PulsoViewModel, actions: ScreenActions, listState: LazyListState) {
    var text by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    fun submit() {
        if (text.isBlank() || busy) return
        busy = true
        vm.capture(text) { failure ->
            busy = false
            if (failure == null) { text = ""; error = null } else error = failure.message
        }
    }
    val settings = vm.state.collectAsStateWithLifecycle().value.settings
    val voice = rememberVoiceCapture(settings.voiceNoticeShown, vm::voiceNoticeShown,
        onText = { heard -> text = appendDictation(text, heard); error = null },
        onError = { error = it })
    VoiceNoticeDialog(voice)
    val now = System.currentTimeMillis()
    LazyColumn(state = listState, contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "title") { ScreenTitle("Caixa de entrada", "Sem pressão para organizar agora.") }
        item(key = "composer") {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(Modifier.padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextField(value = text, onValueChange = { if (it.length <= TaskRules.MAX_CAPTURE) text = it },
                        placeholder = { Text("O que você precisa lembrar?") },
                        modifier = Modifier.weight(1f), minLines = 1, maxLines = 5,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                    VoiceCaptureButton(voice, enabled = !busy)
                    AnimatedVisibility(text.isNotBlank(), enter = scaleIn(PulsoMotion.bouncy()) + fadeIn(), exit = scaleOut() + fadeOut()) {
                        FilledIconButton(onClick = ::submit, enabled = !busy) {
                            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Salvar na entrada")
                        }
                    }
                }
            }
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 16.dp, top = 6.dp))
        }
        item(key = "count") {
            SectionHeading(when (items.size) { 0 -> "Nada para revisar"; else -> "Para organizar (${items.size})" })
        }
        if (items.isEmpty() && vm.state.value.loaded) item(key = "empty") {
            EmptyState(Icons.Rounded.Inbox, "Entrada vazia", "Capture qualquer ideia aqui ou pelo widget. Você organiza depois.")
        }
        items(items, key = { it.id }) { item ->
            Surface(Modifier.fillMaxWidth().animateItem(), shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(start = 18.dp, end = 12.dp, top = 16.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.text, style = MaterialTheme.typography.bodyLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(relativeTime(item.createdAt, now), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.archive(item.id) }) {
                            Icon(Icons.Rounded.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Arquivar")
                        }
                        FilledTonalButton(onClick = { actions.organizeInbox(item) }) { Text("Organizar") }
                    }
                }
            }
        }
    }
}

private enum class TaskFilter(val label: String) {
    ACTIVE("Ativas"), OVERDUE("Atrasadas"), NO_DUE("Sem prazo"), COMPLETED("Concluídas")
}

@Composable
private fun TaskScreen(state: PulsoUiState, actions: ScreenActions, listState: LazyListState) {
    var filter by rememberSaveable { mutableStateOf(TaskFilter.ACTIVE) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val now = System.currentTimeMillis()
    val usedCategories = state.tasks.map { it.category }.distinct().sortedBy { SearchRules.fold(it) }
    fun matches(task: TaskItem, option: TaskFilter) = when (option) {
        TaskFilter.ACTIVE -> !task.isCompleted
        TaskFilter.OVERDUE -> TaskRules.isOverdue(task, now)
        TaskFilter.NO_DUE -> !task.isCompleted && task.dueAt == null
        TaskFilter.COMPLETED -> task.isCompleted
    }
    val results = state.tasks.filter { task ->
        matches(task, filter) && (category == null || task.category == category) &&
            (query.isBlank() || SearchRules.matchesTask(query, task))
    }.let { list -> if (filter == TaskFilter.COMPLETED) list.sortedByDescending { it.completedAt } else list }
    LazyColumn(state = listState, contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "title") { ScreenTitle("Tarefas") }
        item(key = "filter") {
            TextField(query, { query = it }, singleLine = true, placeholder = { Text("Filtrar tarefas") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Limpar filtro") }
                },
                shape = CircleShape, modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
        }
        item(key = "chips") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(TaskFilter.entries) { option ->
                    val count = state.tasks.count { matches(it, option) }
                    FilterChip(selected = filter == option, onClick = { filter = option },
                        label = { Text("${option.label} · $count") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer))
                }
            }
        }
        if (usedCategories.size > 1) item(key = "categories") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = category == null, onClick = { category = null }, label = { Text("Todas") }) }
                items(usedCategories) { name ->
                    FilterChip(selected = category == name, onClick = { category = if (category == name) null else name },
                        label = { Text(name) })
                }
            }
        }
        if (state.loaded && results.isEmpty()) item(key = "empty") {
            EmptyState(Icons.Rounded.TaskAlt, "Nada por aqui",
                if (query.isBlank()) "Nenhuma tarefa nessa visualização." else "Nada encontrado para \"${query.trim()}\".")
        }
        items(results, key = { it.id }) { task ->
            TaskCard(task, { done -> actions.toggleTask(task, done) }, { actions.openTask(task) }, Modifier.animateItem())
        }
    }
}
