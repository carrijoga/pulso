package dev.pulso.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.pulso.app.reminders.NotificationAccess
import dev.pulso.app.reminders.appNotificationSettingsIntent
import dev.pulso.app.reminders.notificationAccess

class NotificationPermissionState(
    val access: NotificationAccess,
    /** Asks the system dialog when possible, otherwise opens the app's notification settings. */
    val request: () -> Unit,
)

/** Re-reads the permission on every resume so the UI reflects changes made in system settings. */
@Composable
fun rememberNotificationPermission(promptShown: Boolean, onPromptShown: () -> Unit): NotificationPermissionState {
    val context = LocalContext.current
    var access by remember { mutableStateOf(notificationAccess(context, promptShown)) }
    LifecycleResumeEffect(promptShown) {
        access = notificationAccess(context, promptShown)
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onPromptShown()
        access = notificationAccess(context, askedBefore = true)
    }
    return NotificationPermissionState(access) {
        if (access == NotificationAccess.NOT_REQUESTED && Build.VERSION.SDK_INT >= 33) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            runCatching { context.startActivity(appNotificationSettingsIntent(context)) }
        }
    }
}
