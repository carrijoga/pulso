package dev.pulso.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalTime

private val Context.dataStore by preferencesDataStore(name = "pulso_settings")

enum class ThemeMode(val label: String) { SYSTEM("Sistema"), LIGHT("Claro"), DARK("Escuro") }

/** Consumed by ui/theme/Theme.kt. [dynamicColor] = Material You wallpaper colors (Android 12+) instead of the brand palette. */
data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
)

data class AppSettings(
    val appearance: AppearanceSettings = AppearanceSettings(),
    /** Time pre-filled when the user picks a due date. */
    val defaultDueTime: LocalTime = LocalTime.of(9, 0),
    val lastReviewAt: Long? = null,
    /** Whether the Android 13+ notification prompt was already shown once (needed to tell "blocked" apart). */
    val notificationPromptShown: Boolean = false,
    /** Whether the one-time voice privacy notice (SPEC 011) was already confirmed. */
    val voiceNoticeShown: Boolean = false,
)

class Preferences(private val context: Context) {
    private val legacyDarkThemeKey = booleanPreferencesKey("dark_theme")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val dynamicColorKey = booleanPreferencesKey("dynamic_color")
    private val dueMinuteKey = intPreferencesKey("default_due_minute_of_day")
    private val lastReviewKey = longPreferencesKey("last_review_at")
    private val promptShownKey = booleanPreferencesKey("notification_prompt_shown")
    private val voiceNoticeKey = booleanPreferencesKey("voice_notice_shown")

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val mode = prefs[themeModeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            // Users of v0.1 chose a boolean; keep their choice instead of resetting it.
            ?: when (prefs[legacyDarkThemeKey]) { true -> ThemeMode.DARK; false -> ThemeMode.LIGHT; null -> ThemeMode.SYSTEM }
        val minute = (prefs[dueMinuteKey] ?: (9 * 60)).coerceIn(0, 24 * 60 - 1)
        AppSettings(AppearanceSettings(mode, prefs[dynamicColorKey] ?: false), LocalTime.of(minute / 60, minute % 60), prefs[lastReviewKey],
            prefs[promptShownKey] ?: false, prefs[voiceNoticeKey] ?: false)
    }

    suspend fun markVoiceNoticeShown() {
        context.dataStore.edit { it[voiceNoticeKey] = true }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[themeModeKey] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[dynamicColorKey] = enabled }
    }

    suspend fun setDefaultDueTime(time: LocalTime) {
        context.dataStore.edit { it[dueMinuteKey] = time.hour * 60 + time.minute }
    }

    suspend fun markNotificationPromptShown() {
        context.dataStore.edit { it[promptShownKey] = true }
    }

    suspend fun markReviewDone(at: Long) {
        context.dataStore.edit { it[lastReviewKey] = at }
    }
}
