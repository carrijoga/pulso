package dev.pulso.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider
import dev.pulso.app.CaptureActivity
import dev.pulso.app.MainActivity
import dev.pulso.app.PulsoApplication
import dev.pulso.app.R
import dev.pulso.app.domain.DueState
import dev.pulso.app.domain.TaskItem
import dev.pulso.app.domain.TaskRules
import kotlinx.coroutines.flow.first
import java.time.ZoneId

private val TASK_ID = ActionParameters.Key<String>("task_id")

private val COMPACT = DpSize(110.dp, 110.dp)
/** From this width the list layout fits; below it only count + capture. */
private val LIST = DpSize(250.dp, 110.dp)
/** Rows in the scrollable list; the rest is one tap away in the app. */
private const val MAX_LIST_ITEMS = 30

// Mirrors res/values*/colors.xml widget_* (resource-based ColorProvider is restricted API in Glance).
private val onSurface = ColorProvider(day = Color(0xFF231917), night = Color(0xFFF1DFDB))
private val onSurfaceVariant = ColorProvider(day = Color(0xFF6E5752), night = Color(0xFFD8C2BD))
private val overdueColor = ColorProvider(day = Color(0xFFBA1A1A), night = Color(0xFFFFB4AB))
private val todayColor = ColorProvider(day = Color(0xFF8A5100), night = Color(0xFFFFB960))

class TodayWidget : GlanceAppWidget() {
    /**
     * Narrow: count + capture. Wider: header + a scrollable list that fills whatever height the launcher
     * gives. Launchers report heights unreliably (the Moto G84 launcher reported 113dp for a 155dp widget),
     * so the list never assumes a row count and never clips a row.
     */
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(COMPACT, LIST))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val source = (context.applicationContext as PulsoApplication).container.repository.observeTasks()
        val initial = source.first()
        provideContent {
            // Collected inside the session: an update while the session is alive (e.g. right after
            // completing from the widget) recomposes with fresh data instead of the first snapshot.
            val tasks by source.collectAsState(initial)
            val now = System.currentTimeMillis()
            val zone = ZoneId.systemDefault()
            // Overdue/today first, then the rest: same ordering as the app's Hoje screen.
            WidgetContent(context, TaskRules.agenda(tasks, now, zone), now, zone)
        }
    }
}

@Composable
private fun WidgetContent(context: Context, active: List<TaskItem>, now: Long, zone: ZoneId) {
    val size = LocalSize.current
    val compact = size.width < LIST.width
    val dueCount = active.count { TaskRules.isDueTodayOrOverdue(it, now, zone) }
    Column(
        GlanceModifier.fillMaxSize().appWidgetBackground().background(ImageProvider(R.drawable.widget_background))
            .cornerRadius(28.dp).padding(14.dp)
    ) {
        Header(context, active.size, dueCount, compact = compact)
        if (!compact) {
            Spacer(GlanceModifier.height(8.dp))
            if (active.isEmpty()) {
                Row(GlanceModifier.fillMaxWidth().background(ImageProvider(R.drawable.widget_row)).cornerRadius(18.dp)
                    .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(ImageProvider(R.drawable.widget_ic_done_all), contentDescription = null,
                        modifier = GlanceModifier.size(32.dp))
                    Column(GlanceModifier.padding(start = 10.dp)) {
                        Text("Tudo em dia", style = TextStyle(color = onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold))
                        Text("Toque em + para capturar.", style = TextStyle(color = onSurfaceVariant, fontSize = 12.sp))
                    }
                }
            } else {
                val shown = active.take(MAX_LIST_ITEMS)
                LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    items(shown, itemId = { it.id.hashCode().toLong() }) { task ->
                        Column {
                            TaskRow(context, task, now, zone)
                            Spacer(GlanceModifier.height(6.dp))
                        }
                    }
                    if (active.size > shown.size) {
                        item {
                            Text("+${active.size - shown.size} no app",
                                style = TextStyle(color = onSurfaceVariant, fontSize = 12.sp),
                                modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(context: Context, pending: Int, due: Int, compact: Boolean) {
    val summary = when {
        pending == 0 -> "Nada pendente"
        due > 0 -> "$due para hoje · $pending ${if (pending == 1) "pendente" else "pendentes"}"
        else -> "$pending ${if (pending == 1) "pendente" else "pendentes"}"
    }
    val open = actionStartActivity<MainActivity>()
    if (compact) {
        // Whole card opens the app (bigger target than the number); the + keeps its own action.
        Column(GlanceModifier.fillMaxSize().clickable(open), horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically) {
            Text("$pending",
                style = TextStyle(color = onSurface, fontSize = 28.sp, fontWeight = FontWeight.Bold))
            Text(if (due > 0) "$due hoje" else if (pending == 1) "pendente" else "pendentes",
                style = TextStyle(color = onSurfaceVariant, fontSize = 12.sp))
            Spacer(GlanceModifier.height(6.dp))
            AddButton(context)
        }
        return
    }
    // Whole header row (48dp tall, set by the + button) opens the app; the + keeps its own action.
    Row(GlanceModifier.fillMaxWidth().clickable(open), verticalAlignment = Alignment.CenterVertically) {
        Image(ImageProvider(R.drawable.ic_pulso_mark), contentDescription = null,
            modifier = GlanceModifier.size(28.dp))
        Column(GlanceModifier.defaultWeight().padding(start = 10.dp)) {
            Text("Hoje", style = TextStyle(color = onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold))
            Text(summary, style = TextStyle(color = onSurfaceVariant, fontSize = 12.sp), maxLines = 1)
        }
        AddButton(context)
    }
}

@Composable
private fun AddButton(context: Context) {
    Box(
        GlanceModifier.size(48.dp).background(ImageProvider(R.drawable.widget_add_button)).cornerRadius(24.dp)
            .clickable(actionStartActivity(Intent(context, CaptureActivity::class.java)))
            .semantics { contentDescription = "Capturar anotação" },
        contentAlignment = Alignment.Center,
    ) {
        Image(ImageProvider(R.drawable.widget_ic_add), contentDescription = null, modifier = GlanceModifier.size(24.dp))
    }
}

@Composable
private fun TaskRow(context: Context, task: TaskItem, now: Long, zone: ZoneId) {
    val due = task.dueAt?.let { TaskRules.dueLabel(it, now, zone) }
    val dueColor = when (due?.state) {
        DueState.OVERDUE -> overdueColor
        DueState.TODAY -> todayColor
        else -> onSurfaceVariant
    }
    Row(
        // The whole 48dp row opens the app (the title alone was a 16dp target); the circle keeps its own action.
        GlanceModifier.fillMaxWidth().height(48.dp).background(ImageProvider(R.drawable.widget_row))
            .cornerRadius(18.dp).padding(end = 12.dp).clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Completing is its own 48dp target, separate from opening the app, to avoid accidental completion.
        Box(
            GlanceModifier.size(48.dp)
                .clickable(actionRunCallback<CompleteTaskAction>(actionParametersOf(TASK_ID to task.id)))
                .semantics { contentDescription = "Concluir ${task.title}" },
            contentAlignment = Alignment.Center,
        ) {
            Image(ImageProvider(R.drawable.widget_ic_circle), contentDescription = null, modifier = GlanceModifier.size(20.dp))
        }
        // A subtask shows its mother in parentheses (SPEC 015); the row height and tap targets do not change.
        Text(task.parentTitle?.let { "${task.title} (${it})" } ?: task.title, maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(color = onSurface, fontSize = 14.sp))
        if (due != null) {
            Spacer(GlanceModifier.width(8.dp))
            Text(due.text, maxLines = 1, style = TextStyle(color = dueColor, fontSize = 12.sp, fontWeight = FontWeight.Bold))
        }
    }
}

class CompleteTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val taskId = parameters[TASK_ID] ?: return
        // The repository's change listener refreshes every widget instance afterwards.
        (context.applicationContext as PulsoApplication).container.repository.setCompleted(taskId, true)
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
