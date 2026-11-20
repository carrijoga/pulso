package dev.pulso.app.ui

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AssistChip
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.pulso.app.domain.Category
import dev.pulso.app.domain.CategoryRules
import dev.pulso.app.data.ThemeMode
import dev.pulso.app.reminders.NotificationAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Sub-pages of the Ajustes index; [PulsoScreens] maps each one to an overlay. */
enum class SettingsPage { APPEARANCE, REMINDERS, CATEGORIES, BACKUP, PRIVACY, ABOUT }

private class SettingsRowData(
    val icon: ImageVector, val label: String, val value: String, val onClick: (() -> Unit)? = null,
)

private val OuterCorner = 20.dp
private val InnerCorner = 6.dp

@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty() }
}

@Composable
fun SettingsScreen(
    state: PulsoUiState,
    notifications: NotificationPermissionState,
    onOpen: (SettingsPage) -> Unit,
) {
    val reminders = if (notifications.access == NotificationAccess.ALLOWED)
        state.settings.defaultDueTime.format(DateTimeFormatter.ofPattern("HH:mm")) else "Desativados"
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenTitle("Ajustes", "Do seu jeito, sem sair do aparelho.")
        SettingsGroup("App", listOf(
            SettingsRowData(Icons.Rounded.Palette, "Aparência", state.settings.appearance.themeMode.label) { onOpen(SettingsPage.APPEARANCE) },
            SettingsRowData(Icons.Rounded.NotificationsActive, "Lembretes", reminders) { onOpen(SettingsPage.REMINDERS) },
            SettingsRowData(Icons.AutoMirrored.Rounded.Label, "Categorias", "${state.categories.size}") { onOpen(SettingsPage.CATEGORIES) },
            SettingsRowData(Icons.Rounded.Widgets, "Widgets", "Em breve"), // planned: SPEC 014
        ))
        SettingsGroup("Dados", listOf(
            SettingsRowData(Icons.Rounded.Backup, "Backup", "") { onOpen(SettingsPage.BACKUP) },
            SettingsRowData(Icons.Rounded.Lock, "Privacidade", "") { onOpen(SettingsPage.PRIVACY) },
            SettingsRowData(Icons.Rounded.Sync, "Sincronização", "Em breve"), // planned: SPEC 004
        ))
        SettingsGroup("Sobre", listOf(
            SettingsRowData(Icons.Rounded.Info, "Sobre o Pulso", appVersion()) { onOpen(SettingsPage.ABOUT) },
        ))
        Spacer(Modifier.height(104.dp))
    }
}

@Composable
private fun SettingsGroup(title: String, rows: List<SettingsRowData>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, bottom = 4.dp))
        rows.forEachIndexed { index, row ->
            val top = if (index == 0) OuterCorner else InnerCorner
            val bottom = if (index == rows.lastIndex) OuterCorner else InnerCorner
            SettingsRow(row, RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom))
        }
    }
}

/** A row without [SettingsRowData.onClick] is a planned placeholder: dimmed, no chevron, not clickable. */
@Composable
private fun SettingsRow(row: SettingsRowData, shape: Shape) {
    val description = if (row.value.isEmpty()) row.label else "${row.label}, ${row.value.replaceFirstChar { it.lowercase() }}"
    val interaction = if (row.onClick != null) Modifier.clickable(role = Role.Button, onClick = row.onClick) else Modifier.alpha(0.5f)
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()
        .clip(shape).then(interaction).semantics(mergeDescendants = true) { contentDescription = description }) {
        Row(Modifier.heightIn(min = 56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(row.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Text(row.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            if (row.value.isNotEmpty()) Text(row.value, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (row.onClick != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SubPage(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
}

@Composable
fun AppearanceSettings(state: PulsoUiState, vm: PulsoViewModel) = SubPage {
    Text("Tema")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        ThemeMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(selected = state.settings.appearance.themeMode == mode, onClick = { vm.themeMode(mode) },
                colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                icon = { SegmentedButtonDefaults.Icon(state.settings.appearance.themeMode == mode) {
                    Icon(when (mode) { ThemeMode.SYSTEM -> Icons.Rounded.BrightnessAuto
                        ThemeMode.LIGHT -> Icons.Rounded.LightMode; ThemeMode.DARK -> Icons.Rounded.DarkMode },
                        contentDescription = null, modifier = Modifier.size(SegmentedButtonDefaults.IconSize))
                } }) { Text(mode.label) }
        }
    }
    // The whole row toggles, so TalkBack reads the label together with the switch state.
    if (android.os.Build.VERSION.SDK_INT >= 31) Row(Modifier.fillMaxWidth().toggleable(
        value = state.settings.appearance.dynamicColor, role = Role.Switch, onValueChange = vm::dynamicColor),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Cores do papel de parede")
            Text("Usa as cores do sistema (Material You) em vez das cores do Pulso.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.material3.Switch(checked = state.settings.appearance.dynamicColor, onCheckedChange = null)
    }
}

@Composable
fun ReminderSettings(state: PulsoUiState, vm: PulsoViewModel, notifications: NotificationPermissionState) = SubPage {
    val context = LocalContext.current
    NotificationStatus(notifications)
    val time = state.settings.defaultDueTime
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text("Horário padrão do prazo")
            Text("Sugerido ao escolher uma data e no \"Amanhã\" das notificações.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val timeText = time.format(DateTimeFormatter.ofPattern("HH:mm"))
        OutlinedButton(modifier = Modifier.semantics { contentDescription = "Horário padrão do prazo, $timeText. Alterar" },
            onClick = {
            TimePickerDialog(context, { _, hour, minute -> vm.defaultDueTime(LocalTime.of(hour, minute)) },
                time.hour, time.minute, DateFormat.is24HourFormat(context)).show()
        }) { Text(timeText) }
    }
    Text("Lembretes usam o WorkManager do Android: chegam perto do horário, podendo atrasar alguns minutos " +
        "(mais em economia de bateria). Não são despertadores.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun CategorySettings(state: PulsoUiState, vm: PulsoViewModel, onOpenCategory: (String) -> Unit) = SubPage {
    CategoryManager(state.categories, vm, onOpenCategory)
}

// The launchers live here so they are registered only while the Backup page is shown.
@Composable
fun BackupSettings(vm: PulsoViewModel) = SubPage {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var feedback by remember { mutableStateOf<String?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val contents = vm.exportBackup()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(contents) }
                        ?: error("Não foi possível abrir o arquivo para gravação.")
                }
            }.onSuccess { feedback = "Backup exportado. Guarde o arquivo em local seguro: ele não é criptografado." }
                .onFailure { feedback = it.message ?: "Falha ao exportar backup." }
        }
    }
    val load = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("Não foi possível ler o arquivo.")
                }
            }.onSuccess { contents ->
                vm.importBackup(contents) { result ->
                    feedback = result.fold({ it.message }, { it.message ?: "Falha ao importar backup." })
                }
            }.onFailure { feedback = it.message }
        }
    }
    Button(onClick = { save.launch("pulso-backup-v4-${java.time.LocalDate.now()}.json") }, modifier = Modifier.fillMaxWidth()) {
        Text("Exportar backup JSON")
    }
    OutlinedButton(onClick = { load.launch(arrayOf("application/json", "text/plain", "*/*")) },
        modifier = Modifier.fillMaxWidth()) { Text("Importar backup (mesclar)") }
    Text("Importar nunca apaga nem substitui seus dados: só adiciona o que ainda não existe.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (feedback != null) Text(feedback!!, color = MaterialTheme.colorScheme.primary)
}

@Composable
fun PrivacySettings() = SubPage {
    Text("Ditar anotação: $VOICE_PRIVACY_NOTICE",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun AboutSettings() = SubPage {
    val version = appVersion()
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        dev.pulso.app.ui.components.PulseMark(size = 36.dp)
        Column {
            Text("Pulso", style = MaterialTheme.typography.titleSmall)
            Text("Capture agora. Organize depois. Funciona offline e não envia dados para servidores.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (version.isNotEmpty()) Text("Versão $version", style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun NotificationStatus(notifications: NotificationPermissionState) {
    val allowed = notifications.access == NotificationAccess.ALLOWED
    Card(shape = MaterialTheme.shapes.small, colors = CardDefaults.cardColors(containerColor =
        if (allowed) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(if (allowed) Icons.Rounded.Notifications else Icons.Rounded.NotificationsOff, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(when (notifications.access) {
                    NotificationAccess.ALLOWED -> "Notificações permitidas"
                    NotificationAccess.NOT_REQUESTED -> "Notificações ainda não permitidas"
                    NotificationAccess.BLOCKED -> "Notificações bloqueadas"
                }, style = MaterialTheme.typography.titleSmall)
                Text(when (notifications.access) {
                    NotificationAccess.ALLOWED -> "Você vai receber lembretes de prazo."
                    NotificationAccess.NOT_REQUESTED -> "Sem permissão, os lembretes não aparecem."
                    NotificationAccess.BLOCKED -> "Ative nos ajustes do Android para receber lembretes."
                }, style = MaterialTheme.typography.bodySmall)
            }
            if (!allowed) TextButton(onClick = notifications.request) {
                Text(if (notifications.access == NotificationAccess.NOT_REQUESTED) "Permitir" else "Abrir ajustes")
            }
        }
    }
}

@Composable
private fun CategoryManager(categories: List<Category>, vm: PulsoViewModel, onOpen: (String) -> Unit) {
    var newName by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<Category?>(null) }
    var deleting by remember { mutableStateOf<Category?>(null) }
    categories.forEachIndexed { index, category ->
        if (index > 0) HorizontalDivider()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClickLabel = "Abrir ${category.name}") { onOpen(category.name) },
                contentAlignment = Alignment.CenterStart) { Text(category.name) }
            IconButton(onClick = { renaming = category }) { Icon(Icons.Rounded.Edit, "Renomear ${category.name}") }
            IconButton(onClick = { deleting = category }) { Icon(Icons.Rounded.Delete, "Remover ${category.name}") }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(newName, { newName = it }, label = { Text("Nova categoria") }, singleLine = true,
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
        Button(enabled = newName.isNotBlank(), onClick = {
            vm.addCategory(newName) { failure -> if (failure == null) { newName = ""; error = null } else error = failure.message }
        }) { Text("Adicionar") }
    }
    val suggestions = CategoryRules.suggestions(categories)
    if (suggestions.isNotEmpty()) {
        Text("Sugestões", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEach { name ->
                AssistChip(onClick = { vm.addCategory(name) { failure -> error = failure?.message } },
                    label = { Text(name) }, leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)) },
                    modifier = Modifier.heightIn(min = 48.dp))
            }
        }
    }
    if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)

    renaming?.let { category ->
        var name by remember(category.id) { mutableStateOf(category.name) }
        var renameError by remember(category.id) { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest = { renaming = null }, title = { Text("Renomear categoria") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Nome") })
                    Text("As tarefas desta categoria também serão atualizadas.", style = MaterialTheme.typography.bodySmall)
                    if (renameError != null) Text(renameError!!, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                Button(enabled = name.isNotBlank(), onClick = {
                    vm.renameCategory(category.id, name) { failure ->
                        if (failure == null) renaming = null else renameError = failure.message
                    }
                }) { Text("Salvar") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancelar") } })
    }
    deleting?.let { category ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Remover \"${category.name}\"?") },
            text = { Text("Ela sai da lista de escolha. Tarefas que já usam essa categoria continuam com o nome.") },
            confirmButton = { Button(onClick = { vm.deleteCategory(category); deleting = null }) { Text("Remover") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } })
    }
}
