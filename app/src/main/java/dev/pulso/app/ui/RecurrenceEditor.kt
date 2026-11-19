package dev.pulso.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.pulso.app.domain.DaysOfWeekMask
import dev.pulso.app.domain.RecurrenceBasis
import dev.pulso.app.domain.RecurrenceChoice
import dev.pulso.app.domain.RecurrenceFrequency
import dev.pulso.app.domain.RecurrencePresets
import dev.pulso.app.domain.RecurrenceRule
import dev.pulso.app.domain.RecurrenceRules
import dev.pulso.app.domain.TaskItem
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val PREVIEW_SPAN_MILLIS = 3L * 366 * 24 * 3_600_000
private val shortDay = DateTimeFormatter.ofPattern("dd/MM")

/** Persists the rule across rotation inside the editor (a plain data class cannot be saved as is). */
internal val RecurrenceSaver: Saver<RecurrenceRule?, String> = Saver(
    save = { rule ->
        rule?.let { r -> listOf(r.frequency.name, r.interval, DaysOfWeekMask.toMask(r.daysOfWeek), r.basis.name).joinToString(";") } ?: ""
    },
    restore = { text ->
        if (text.isEmpty()) null else text.split(";").let { p ->
            RecurrenceRule(RecurrenceFrequency.valueOf(p[0]), p[1].toInt(), DaysOfWeekMask.fromMask(p[2].toInt()),
                RecurrenceBasis.valueOf(p[3]))
        }
    },
)

/**
 * Anchor a series will have if [rule] is saved: an unchanged rule keeps the series anchor; a new or
 * changed rule starts at the current due date (SPEC 008).
 */
internal fun savedAnchor(rule: RecurrenceRule, existing: TaskItem?, dueAt: Long?, nowMillis: Long): Long =
    (if (existing?.recurrence == rule) existing.recurrenceAnchorDueAt else null) ?: dueAt ?: nowMillis

/** "Próximas: 05/12, 05/01, 05/02", or a note for rules that depend on the completion date. */
internal fun nextPreview(rule: RecurrenceRule, anchorDueAt: Long, zone: ZoneId): String {
    if (rule.basis == RecurrenceBasis.COMPLETION) return "A próxima data depende de quando você concluir."
    val next = RecurrenceRules.occurrencesBetween(rule, anchorDueAt, anchorDueAt + 1, anchorDueAt + PREVIEW_SPAN_MILLIS, zone, 3)
    if (next.isEmpty()) return "Sem próximas datas."
    return "Próximas: " + next.joinToString(", ") { Instant.ofEpochMilli(it).atZone(zone).format(shortDay) }
}

private val weekdayChips = listOf(
    DayOfWeek.MONDAY to "Seg", DayOfWeek.TUESDAY to "Ter", DayOfWeek.WEDNESDAY to "Qua", DayOfWeek.THURSDAY to "Qui",
    DayOfWeek.FRIDAY to "Sex", DayOfWeek.SATURDAY to "Sáb", DayOfWeek.SUNDAY to "Dom",
)

private val unitOptions = listOf(
    RecurrenceFrequency.DAILY to "Dias", RecurrenceFrequency.WEEKLY to "Semanas",
    RecurrenceFrequency.MONTHLY to "Meses", RecurrenceFrequency.YEARLY to "Anos",
)

private fun choiceLabel(choice: RecurrenceChoice): String = when (choice) {
    RecurrenceChoice.NONE -> "Não repetir"
    RecurrenceChoice.DAILY -> "Todo dia"
    RecurrenceChoice.WEEKLY -> "Toda semana"
    RecurrenceChoice.MONTHLY -> "Todo mês"
    RecurrenceChoice.YEARLY -> "Todo ano"
    RecurrenceChoice.CUSTOM -> "Personalizar"
}

/**
 * Repeat picker opened from the task editor. Changes are only returned on "OK", so cancelling keeps the
 * task as it was. [dueAt] is the task's current due date (the anchor of a new rule).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecurrenceDialog(
    current: RecurrenceRule?,
    existing: TaskItem?,
    dueAt: Long?,
    zone: ZoneId,
    onDismiss: () -> Unit,
    onConfirm: (RecurrenceRule?) -> Unit,
) {
    val nowMillis = System.currentTimeMillis()
    val anchor = dueAt ?: nowMillis
    val anchorDay = RecurrencePresets.anchorDay(anchor, zone)
    var rule by rememberSaveable(stateSaver = RecurrenceSaver) { mutableStateOf(current) }
    var custom by rememberSaveable { mutableStateOf(RecurrencePresets.choiceOf(current) == RecurrenceChoice.CUSTOM) }
    var intervalText by rememberSaveable { mutableStateOf((current?.interval ?: 2).toString()) }

    val picked = rule
    val candidate: RecurrenceRule? = picked?.let { if (custom) it.copy(interval = intervalText.toIntOrNull() ?: 0) else it }
    val problem: String? = candidate?.let { r -> runCatching { RecurrenceRules.validate(r) }.exceptionOrNull()?.message }
    val preview: String? = candidate?.takeIf { problem == null }?.let { r ->
        RecurrenceRules.describe(r, savedAnchor(r, existing, dueAt, nowMillis), zone) + " · " +
            nextPreview(r, savedAnchor(r, existing, dueAt, nowMillis), zone)
    }
    val selected = if (custom) RecurrenceChoice.CUSTOM else RecurrencePresets.choiceOf(picked)

    fun select(choice: RecurrenceChoice) {
        custom = choice == RecurrenceChoice.CUSTOM
        rule = RecurrencePresets.presetRule(choice, anchor, zone)
        intervalText = rule?.interval?.toString() ?: intervalText
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Repetir") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RecurrenceChoice.entries.forEach { choice ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(selected = selected == choice, role = Role.RadioButton, onClick = { select(choice) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == choice, onClick = null)
                        Text(choiceLabel(choice), modifier = Modifier.padding(start = 8.dp))
                    }
                }
                if (custom && picked != null) {
                    OutlinedTextField(intervalText, { intervalText = it.filter(Char::isDigit).take(2) },
                        label = { Text("A cada") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth())
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        unitOptions.forEachIndexed { index, (frequency, label) ->
                            SegmentedButton(selected = picked.frequency == frequency,
                                onClick = { rule = RecurrencePresets.adjust(picked, frequency, picked.basis, anchorDay) },
                                shape = SegmentedButtonDefaults.itemShape(index, unitOptions.size)) { Text(label) }
                        }
                    }
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(RecurrenceBasis.SCHEDULE to "Na data", RecurrenceBasis.COMPLETION to "Após concluir")
                            .forEachIndexed { index, (basis, label) ->
                                SegmentedButton(selected = picked.basis == basis,
                                    onClick = { rule = RecurrencePresets.adjust(picked, picked.frequency, basis, anchorDay) },
                                    shape = SegmentedButtonDefaults.itemShape(index, 2)) { Text(label) }
                            }
                    }
                }
                if (picked != null && picked.frequency == RecurrenceFrequency.WEEKLY && picked.basis == RecurrenceBasis.SCHEDULE) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        weekdayChips.forEach { (day, label) ->
                            val on = day in picked.daysOfWeek
                            FilterChip(selected = on, label = { Text(label) },
                                modifier = Modifier.heightIn(min = 48.dp),
                                onClick = {
                                    rule = picked.copy(daysOfWeek = if (on) picked.daysOfWeek - day else picked.daysOfWeek + day)
                                })
                        }
                    }
                }
                val message = problem ?: preview
                if (message != null) {
                    Text(message, style = MaterialTheme.typography.bodySmall,
                        color = if (problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = problem == null, onClick = { onConfirm(candidate) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
