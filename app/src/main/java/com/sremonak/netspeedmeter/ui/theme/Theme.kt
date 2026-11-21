package com.sremonak.netspeedmeter.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.sremonak.netspeedmeter.core.AppSettings
import com.sremonak.netspeedmeter.core.ThemeMode

/** Semantic colours used across charts and cards. */
object Brand {
    val Download = Color(0xFF22D3EE)
    val Upload = Color(0xFFA78BFA)
    val Mobile = Color(0xFFF59E0B)
    val Wifi = Color(0xFF34D399)
    val Danger = Color(0xFFF87171)
    val GradientStart = Color(0xFF06B6D4)
    val GradientMid = Color(0xFF3B82F6)
    val GradientEnd = Color(0xFF8B5CF6)
}

private val DarkColors = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF00131F),
    primaryContainer = Color(0xFF0C4A6E),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFFA78BFA),
    onSecondary = Color(0xFF1E1037),
    secondaryContainer = Color(0xFF2E1F5E),
    onSecondaryContainer = Color(0xFFEDE9FE),
    tertiary = Color(0xFF34D399),
    background = Color(0xFF070B17),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF070B17),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceContainerLowest = Color(0xFF05080F),
    surfaceContainerLow = Color(0xFF0C1222),
    surfaceContainer = Color(0xFF111827),
    surfaceContainerHigh = Color(0xFF172033),
    surfaceContainerHighest = Color(0xFF1E293B),
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF1E293B),
    error = Color(0xFFF87171),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0C4A6E),
    secondary = Color(0xFF7C3AED),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE9FE),
    onSecondaryContainer = Color(0xFF2E1065),
    tertiary = Color(0xFF059669),
    background = Color(0xFFF5F7FB),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFF5F7FB),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF64748B),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEEF2F7),
    surfaceContainerHighest = Color(0xFFE2E8F0),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626),
)

private val AppTypography = Typography().let { t ->
    val tnum = "tnum"
    t.copy(
        displayLarge = t.displayLarge.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = tnum, letterSpacing = (-1).sp),
        displayMedium = t.displayMedium.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = tnum, letterSpacing = (-1).sp),
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = tnum),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = tnum),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = tnum),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = tnum),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = tnum),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = tnum),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = tnum),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.copy(fontFeatureSettings = tnum),
        bodyMedium = t.bodyMedium.copy(fontFeatureSettings = tnum),
    )
}

val NumberStyle = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun NetSpeedTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    var scheme = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    if (dark && settings.pureBlack) {
        scheme = scheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF070707),
            surfaceContainer = Color(0xFF0D0D0F),
            surfaceContainerHigh = Color(0xFF151518),
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}
