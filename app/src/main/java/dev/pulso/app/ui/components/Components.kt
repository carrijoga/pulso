package dev.pulso.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.pulso.app.domain.DueStatus
import dev.pulso.app.domain.Priority
import dev.pulso.app.domain.TaskItem
import dev.pulso.app.domain.TaskRules
import dev.pulso.app.ui.theme.LocalPulsoColors
import dev.pulso.app.ui.theme.OverlineStyle
import dev.pulso.app.ui.theme.PulsoMotion
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

val PtBr: Locale = Locale.forLanguageTag("pt-BR")

/** Same geometry as res/drawable/ic_launcher_foreground.xml (108-unit viewport). */
private val PulsePoints = listOf(
    28f to 56f, 39f to 56f, 45f to 45f, 52f to 64f, 58f to 55f, 63f to 61f, 80f to 43f,
)

/**
 * The Pulso mark: a pulse line resolving into a check. When [animate] is true the line
 * draws itself once, which is the app's signature motion.
 */
@Composable
fun PulseMark(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    background: Color = dev.pulso.app.ui.theme.PulsoColors.Brasa,
    stroke: Color = Color.White,
    animate: Boolean = false,
) {
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(animate) {
        if (animate) progress.animateTo(1f, tween(900, delayMillis = 150, easing = PulsoMotion.Emphasized))
    }
    Canvas(modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(background)) {
        // Content box: the 52x21 pulse is scaled to ~70% of the tile width.
        val scale = this.size.width * 0.70f / 52f
        val ox = this.size.width / 2f - 54f * scale
        val oy = this.size.height / 2f - 53.5f * scale
        val full = Path().apply {
            PulsePoints.forEachIndexed { i, (x, y) ->
                val p = Offset(ox + x * scale, oy + y * scale)
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
        }
        val path = if (progress.value >= 1f) full else Path().also { out ->
            val measure = PathMeasure().apply { setPath(full, false) }
            measure.getSegment(0f, measure.length * progress.value, out, true)
        }
        drawPath(path, stroke, style = Stroke(width = 7f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Round, springy completion toggle with a 48dp touch target. */
@Composable
fun PulsoCheck(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String, modifier: Modifier = Modifier) {
    val extra = LocalPulsoColors.current
    val haptics = LocalHapticFeedback.current
    val fill by animateColorAsState(if (checked) extra.done else Color.Transparent, tween(PulsoMotion.SHORT), label = "checkFill")
    val ring by animateColorAsState(
        if (checked) extra.done else MaterialTheme.colorScheme.outline, tween(PulsoMotion.SHORT), label = "checkRing")
    val pop by animateFloatAsState(if (checked) 1f else 0.92f, PulsoMotion.bouncy(), label = "checkPop")
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                interactionSource = null,
                indication = ripple(bounded = false, radius = 24.dp),
                onValueChange = {
                    haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                    onCheckedChange(it)
                },
            )
            .semantics(label),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(26.dp).scale(pop).clip(CircleShape).background(fill).border(2.dp, ring, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(checked, enter = scaleIn(PulsoMotion.bouncy()) + fadeIn(), exit = scaleOut() + fadeOut()) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = extra.onDone, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun Modifier.semantics(label: String) =
    this.semantics(mergeDescendants = false) { contentDescription = label }

@Composable
fun TaskCard(
    task: TaskItem,
    nowMillis: Long,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val zone = ZoneId.systemDefault()
    val status = TaskRules.dueStatus(task, nowMillis, zone)
    val contentAlpha by animateFloatAsState(if (task.isCompleted) 0.55f else 1f, tween(PulsoMotion.MEDIUM), label = "taskAlpha")
    Surface(
        onClick = { onClick?.invoke() },
        enabled = onClick != null,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp).animateContentSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PulsoCheck(task.isCompleted, onToggle,
                label = if (task.isCompleted) "Reabrir ${task.title}" else "Concluir ${task.title}")
            Column(Modifier.weight(1f).padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!task.isCompleted && status != DueStatus.NONE) DueChip(status, task.dueAt!!, zone)
                    CategoryLabel(task.category)
                    if (task.priority == Priority.HIGH && !task.isCompleted) {
                        Icon(Icons.Rounded.Flag, contentDescription = "Prioridade alta",
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                    if (task.notes.isNotBlank()) {
                        Icon(Icons.Rounded.Notes, contentDescription = "Tem observações",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryLabel(category: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(categoryColor(category)))
        Spacer(Modifier.width(5.dp))
        Text(category, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Stable, theme-friendly hue per free-text category. */
@Composable
fun categoryColor(category: String): Color {
    val c = MaterialTheme.colorScheme
    val palette = listOf(c.primary, c.secondary, c.tertiary, Color(0xFF6B67C7), Color(0xFF3F7FB5), Color(0xFFB0558C))
    return palette[(category.lowercase().hashCode() and 0x7fffffff) % palette.size]
}

@Composable
fun DueChip(status: DueStatus, dueAt: Long, zone: ZoneId) {
    val extra = LocalPulsoColors.current
    val (bg, fg) = when (status) {
        DueStatus.OVERDUE -> extra.overdueContainer to MaterialTheme.colorScheme.onErrorContainer
        DueStatus.TODAY -> extra.todayContainer to MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Event, contentDescription = null, tint = fg, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(dueLabel(status, dueAt, zone), style = MaterialTheme.typography.labelSmall, color = fg)
    }
}

fun dueLabel(status: DueStatus, dueAt: Long, zone: ZoneId): String {
    val date = Instant.ofEpochMilli(dueAt).atZone(zone).toLocalDate()
    return when (status) {
        DueStatus.NONE -> ""
        DueStatus.OVERDUE -> "Atrasada · " + date.format(DateTimeFormatter.ofPattern("d MMM", PtBr)).trimEnd('.')
        DueStatus.TODAY -> "Hoje"
        DueStatus.TOMORROW -> "Amanhã"
        DueStatus.THIS_WEEK -> date.format(DateTimeFormatter.ofPattern("EEEE", PtBr)).replaceFirstChar { it.titlecase(PtBr) }
        DueStatus.LATER -> date.format(DateTimeFormatter.ofPattern("d MMM", PtBr)).trimEnd('.')
    }
}

fun relativeTime(epochMillis: Long, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val minutes = (nowMillis - epochMillis) / 60_000
    val days = ChronoUnit.DAYS.between(
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate(), Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate())
    return when {
        minutes < 1 -> "agora"
        minutes < 60 -> "há $minutes min"
        days == 0L -> "há ${minutes / 60} h"
        days == 1L -> "ontem"
        else -> Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
            .format(DateTimeFormatter.ofPattern("d MMM", PtBr)).trimEnd('.')
    }
}

@Composable
fun SectionHeader(title: String, count: Int? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = 18.dp, bottom = 4.dp, start = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(PtBr), style = OverlineStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text("$count", style = OverlineStyle, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier) {
    val pop = remember { Animatable(0.85f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, PulsoMotion.bouncy()) }
    Column(
        modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(72.dp).scale(pop.value).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(4.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
