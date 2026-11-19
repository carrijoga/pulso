package dev.pulso.app.ui.theme

import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.enableEdgeToEdge
import dev.pulso.app.data.AppearanceSettings
import dev.pulso.app.data.ThemeMode

// ---- Brand palette "Brasa & Tinta" (mirrored in res/values*/colors.xml) ----
object PulsoColors {
    val Brasa = Color(0xFFE5533D)
    val BrasaLight = Color(0xFFFF7A5C)
    val BrasaDeep = Color(0xFFC2362A)
    val Ink = Color(0xFF231917)
}

private val LightScheme = lightColorScheme(
    primary = Color(0xFFC63F2B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD3),
    onPrimaryContainer = Color(0xFF3F0700),
    secondary = Color(0xFF2F6B63),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB9EDE3),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFF8A5100),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDB8),
    onTertiaryContainer = Color(0xFF2C1600),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFFBFA),
    onBackground = Color(0xFF231917),
    surface = Color(0xFFFFFBFA),
    onSurface = Color(0xFF231917),
    surfaceVariant = Color(0xFFF5DDD8),
    onSurfaceVariant = Color(0xFF5A4440),
    surfaceTint = Color(0xFFC63F2B),
    outline = Color(0xFF8E716C),
    outlineVariant = Color(0xFFE2CDC8),
    inverseSurface = Color(0xFF392E2C),
    inverseOnSurface = Color(0xFFFFEDE9),
    inversePrimary = Color(0xFFFFB4A6),
    surfaceBright = Color(0xFFFFFBFA),
    surfaceDim = Color(0xFFE8D6D2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCF3F0),
    surfaceContainer = Color(0xFFF7ECE8),
    surfaceContainerHigh = Color(0xFFF1E5E1),
    surfaceContainerHighest = Color(0xFFEBDFDB),
    scrim = Color(0xFF000000),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFFB4A6),
    onPrimary = Color(0xFF5F1407),
    primaryContainer = Color(0xFF7F2A1B),
    onPrimaryContainer = Color(0xFFFFDAD3),
    secondary = Color(0xFF9DD1C7),
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF155147),
    onSecondaryContainer = Color(0xFFB9EDE3),
    tertiary = Color(0xFFFFB960),
    onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF693C00),
    onTertiaryContainer = Color(0xFFFFDDB8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF16110F),
    onBackground = Color(0xFFF1DFDB),
    surface = Color(0xFF16110F),
    onSurface = Color(0xFFF1DFDB),
    surfaceVariant = Color(0xFF534340),
    onSurfaceVariant = Color(0xFFD8C2BD),
    surfaceTint = Color(0xFFFFB4A6),
    outline = Color(0xFFA08C88),
    outlineVariant = Color(0xFF534340),
    inverseSurface = Color(0xFFF1DFDB),
    inverseOnSurface = Color(0xFF392E2C),
    inversePrimary = Color(0xFFC63F2B),
    surfaceBright = Color(0xFF3E3634),
    surfaceDim = Color(0xFF16110F),
    surfaceContainerLowest = Color(0xFF110C0B),
    surfaceContainerLow = Color(0xFF1F1A18),
    surfaceContainer = Color(0xFF241E1C),
    surfaceContainerHigh = Color(0xFF2F2826),
    surfaceContainerHighest = Color(0xFF3A3330),
    scrim = Color(0xFF000000),
)

/** Semantic colors that Material's scheme does not name directly. */
@Immutable
data class PulsoExtraColors(
    val done: Color,
    val onDone: Color,
    val today: Color,
    val todayContainer: Color,
    val overdue: Color,
    val overdueContainer: Color,
    val heroStart: Color,
    val heroEnd: Color,
)

val LocalPulsoColors = staticCompositionLocalOf {
    PulsoExtraColors(Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified,
        Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified)
}

private fun extraColors(scheme: ColorScheme, dark: Boolean) = PulsoExtraColors(
    done = scheme.secondary,
    onDone = scheme.onSecondary,
    today = scheme.tertiary,
    todayContainer = scheme.tertiaryContainer,
    overdue = scheme.error,
    overdueContainer = scheme.errorContainer,
    heroStart = if (dark) scheme.primaryContainer else scheme.primary,
    heroEnd = if (dark) Color(0xFF4A1A10) else PulsoColors.BrasaDeep,
)

private val Base = Typography()
private val Display = FontFamily.Default

val PulsoTypography = Typography(
    displaySmall = Base.displaySmall.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge.copy(letterSpacing = 0.15.sp, lineHeight = 24.sp),
    bodyMedium = Base.bodyMedium,
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = Base.labelMedium.copy(fontWeight = FontWeight.Medium),
    labelSmall = Base.labelSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
)

/** Small caps-like overline for section headers. */
val OverlineStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)

val PulsoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/** Shared motion tokens so every screen feels like the same app. */
object PulsoMotion {
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    const val SHORT = 180
    const val MEDIUM = 320
    const val LONG = 520
    fun <T> bouncy() = spring<T>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
    fun <T> gentle() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
}

@Composable
fun AppearanceSettings.isDark(): Boolean = when (themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun PulsoTheme(appearance: AppearanceSettings, content: @Composable () -> Unit) {
    val dark = appearance.isDark()
    val context = LocalContext.current
    val scheme = when {
        appearance.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkScheme
        else -> LightScheme
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalPulsoColors provides extraColors(scheme, dark)) {
        MaterialTheme(colorScheme = scheme, typography = PulsoTypography, shapes = PulsoShapes, content = content)
    }
}

/** Keeps status/navigation bar icons readable when the in-app theme differs from the system's. */
@Composable
fun SyncSystemBars(appearance: AppearanceSettings) {
    val dark = appearance.isDark()
    val activity = androidx.activity.compose.LocalActivity.current as? androidx.activity.ComponentActivity ?: return
    androidx.compose.runtime.LaunchedEffect(dark) {
        val style = androidx.activity.SystemBarStyle.auto(
            android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}
