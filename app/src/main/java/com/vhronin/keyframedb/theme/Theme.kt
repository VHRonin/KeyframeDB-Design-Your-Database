package com.vhronin.keyframedb.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily


object KeyframeDBColors {
    val Background = Color(0xFFF5F6FB)
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF14151A)
    val InkMuted = Color(0xFF6B6E7A)
    val InkFaint = Color(0xFFA2A5B3)
    val Line = Color(0xFFE5E7F0)
    val Accent = Color(0xFF5B5FEF)
    val AccentSoft = Color(0xFFECEDFD)
    val Accent2 = Color(0xFF06B88A)
    val Accent2Soft = Color(0xFFE2F8F1)

    // Цвета для тёмной темы
    val BackgroundDark = Color(0xFF14151A)
    val SurfaceDark = Color(0xFF1E2028)
    val InkDark = Color(0xFFE5E7F0)
    val InkMutedDark = Color(0xFFA2A5B3)
    val InkFaintDark = Color(0xFF6B6E7A)
    val LineDark = Color(0xFF2D303D)
    val AccentDark = Color(0xFF5B5FEF)
    val AccentSoftDark = Color(0xFF2A2E6D)
    val Accent2Dark = Color(0xFF06B88A)
    val Accent2SoftDark = Color(0xFF14473C)
}

// ---- Токены, которых нет в стандартной Material3 ColorScheme.
// "Line" сюда не включаем — он уже корректно переключается через
// MaterialTheme.colorScheme.outline (см. LightColorScheme/DarkColorScheme ниже). ----
data class KeyframeDBExtendedColors(
    val inkMuted: Color,
    val inkFaint: Color,
    val accentSoft: Color,
    val accent2: Color,
    val accent2Soft: Color
)

private val LightExtendedColors = KeyframeDBExtendedColors(
    inkMuted = KeyframeDBColors.InkMuted,
    inkFaint = KeyframeDBColors.InkFaint,
    accentSoft = KeyframeDBColors.AccentSoft,
    accent2 = KeyframeDBColors.Accent2,
    accent2Soft = KeyframeDBColors.Accent2Soft
)

private val DarkExtendedColors = KeyframeDBExtendedColors(
    inkMuted = KeyframeDBColors.InkMutedDark,
    inkFaint = KeyframeDBColors.InkFaintDark,
    accentSoft = KeyframeDBColors.AccentSoftDark,
    accent2 = KeyframeDBColors.Accent2Dark,
    accent2Soft = KeyframeDBColors.Accent2SoftDark
)

val LocalKeyframeDBExtendedColors = staticCompositionLocalOf { LightExtendedColors }

object KeyframeDBExtendedTheme {
    val colors: KeyframeDBExtendedColors
        @Composable get() = LocalKeyframeDBExtendedColors.current
}


// TODO: подключить настоящие шрифты (Space Grotesk для заголовков, Inter для UI)
// через res/font — пока системные, чтобы не блокировать вёрстку экрана.
val DisplayFontFamily = FontFamily.Default
val BodyFontFamily = FontFamily.Default
private val DarkColorScheme = darkColorScheme(
    primary = KeyframeDBColors.AccentDark,
    onPrimary = KeyframeDBColors.SurfaceDark,
    background = KeyframeDBColors.BackgroundDark,
    surface = KeyframeDBColors.SurfaceDark,
    onBackground = KeyframeDBColors.InkDark,
    onSurface = KeyframeDBColors.InkDark,
    outline = KeyframeDBColors.LineDark
)

private val LightColorScheme = lightColorScheme(
    primary = KeyframeDBColors.Accent,
    onPrimary = KeyframeDBColors.Surface,
    background = KeyframeDBColors.Background,
    surface = KeyframeDBColors.Surface,
    onBackground = KeyframeDBColors.Ink,
    onSurface = KeyframeDBColors.Ink,
    outline = KeyframeDBColors.Line
)


@Composable
fun KeyframeDBTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalKeyframeDBExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = KeyframeDBTypography,
            content = content
        )
    }
}