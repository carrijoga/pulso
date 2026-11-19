package dev.pulso.app.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.pulso.app.MainActivity
import dev.pulso.app.PulsoApplication
import dev.pulso.app.R
import dev.pulso.app.data.TaskChangeListener
import dev.pulso.app.data.toModel
import dev.pulso.app.domain.TaskRules
import dev.pulso.app.widget.TodayWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

private const val CHANNEL_ID = "task_reminders"
private const val KEY_TASK_ID = "taskId"
private const val EARLY_TOLERANCE_MILLIS = 60_000L

fun createNotificationChannel(context: Context) {
    val channel = NotificationChannel(CHANNEL_ID, "Lembretes de tarefas", NotificationManager.IMPORTANCE_DEFAULT)
        .apply { description = "Avisos aproximados no prazo das tarefas" }
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}

/** What the user sees about reminders; drives the Settings card and editor hints. */
enum class NotificationAccess { ALLOWED, NOT_REQUESTED, BLOCKED }

fun notificationAccess(context: Context, askedBefore: Boolean): NotificationAccess {
    val manager = NotificationManagerCompat.from(context)
    val channelBlocked = manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE
    if (manager.areNotificationsEnabled() && !channelBlocked) return NotificationAccess.ALLOWED
    val runtimeGranted = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
        context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    return if (!runtimeGranted && !askedBefore) NotificationAccess.NOT_REQUESTED else NotificationAccess.BLOCKED
}

fun appNotificationSettingsIntent(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** WorkManager is inexact: this is a reminder near the due time, not an alarm clock. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val database = (applicationContext as PulsoApplication).container.database
        val entity = database.taskDao().findById(id) ?: return Result.success()
        if (entity.deletedAt != null) return Result.success()
        val task = entity.toModel()
        val reminderAt = TaskRules.reminderTime(task) ?: return Result.success()
        // A stale work request (task moved later meanwhile) must not notify early.
        if (reminderAt > System.currentTimeMillis() + EARLY_TOLERANCE_MILLIS) return Result.success()
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) return Result.success()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return Result.success()

        val context = applicationContext
        val open = PendingIntent.getActivity(context, id.hashCode(),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val due = task.dueAt?.let { TaskRules.reminderDueText(it, System.currentTimeMillis(), ZoneId.systemDefault()) }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pulso)
            .setContentTitle(task.title)
            .setContentText(listOfNotNull(due, task.category).joinToString(" · "))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, "Concluir", ReminderActionReceiver.pending(context, id, ReminderActionReceiver.ACTION_COMPLETE))
            .addAction(0, "Adiar 1h", ReminderActionReceiver.pending(context, id, ReminderActionReceiver.ACTION_SNOOZE_HOUR))
            .addAction(0, "Amanhã", ReminderActionReceiver.pending(context, id, ReminderActionReceiver.ACTION_SNOOZE_TOMORROW))
            .build()
        NotificationManagerCompat.from(context).notify(id.hashCode(), notification)
        return Result.success()
    }
}

/** Notification actions. Runs the repository write off the main thread with goAsync(). */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(KEY_TASK_ID) ?: return
        val action = intent.action ?: return
        val pending = goAsync()
        val container = (context.applicationContext as PulsoApplication).container
        val repository = container.repository
        scope.launch {
            try {
                when (action) {
                    ACTION_COMPLETE -> repository.setCompleted(taskId, true)
                    ACTION_SNOOZE_HOUR -> repository.snooze(taskId, System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1))
                    ACTION_SNOOZE_TOMORROW -> repository.snooze(taskId,
                        tomorrowAt(container.preferences.settings.first().defaultDueTime))
                }
                NotificationManagerCompat.from(context).cancel(taskId.hashCode())
            } catch (exception: Exception) {
                Log.e("Pulso", "Reminder action failed", exception)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE = "dev.pulso.app.action.COMPLETE"
        const val ACTION_SNOOZE_HOUR = "dev.pulso.app.action.SNOOZE_HOUR"
        const val ACTION_SNOOZE_TOMORROW = "dev.pulso.app.action.SNOOZE_TOMORROW"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun pending(context: Context, taskId: String, action: String): PendingIntent =
            PendingIntent.getBroadcast(context, (taskId + action).hashCode(),
                Intent(context, ReminderActionReceiver::class.java).setAction(action).putExtra(KEY_TASK_ID, taskId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        private fun tomorrowAt(time: LocalTime): Long =
            ZonedDateTime.now().toLocalDate().plusDays(1).atTime(time).atZone(ZoneId.systemDefault())
                .toInstant().toEpochMilli()
    }
}

class AndroidTaskChangeListener(private val context: Context) : TaskChangeListener {
    override suspend fun taskChanged(taskId: String, reminderAt: Long?) {
        // Side effects must never make a successful Room write look like a failed task save.
        try {
            val manager = WorkManager.getInstance(context)
            val workName = "task_reminder_$taskId"
            val now = System.currentTimeMillis()
            if (reminderAt == null || reminderAt <= now) {
                manager.cancelUniqueWork(workName)
                if (reminderAt == null) NotificationManagerCompat.from(context).cancel(taskId.hashCode())
            } else {
                val work = OneTimeWorkRequestBuilder<ReminderWorker>()
                    .setInitialDelay(reminderAt - now, TimeUnit.MILLISECONDS)
                    .setInputData(Data.Builder().putString(KEY_TASK_ID, taskId).build())
                    .build()
                manager.enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, work)
            }
        } catch (exception: Exception) {
            Log.e("Pulso", "Failed to schedule local reminder", exception)
        }
        try {
            TodayWidget().updateAll(context)
        } catch (exception: Exception) {
            Log.e("Pulso", "Failed to refresh widget", exception)
        }
    }
}
