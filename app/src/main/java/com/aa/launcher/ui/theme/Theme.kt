package com.aa.launcher.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.aa.launcher.settings.LauncherSettings
import com.aa.launcher.settings.ThemeMode

val LocalLauncherSettings = compositionLocalOf { LauncherSettings() }
val LocalIsDark = compositionLocalOf { false }

object IosColors {
    val Blue = Color(0xFF0A84FF)
    val Green = Color(0xFF34C759)
    val Red = Color(0xFFFF3B30)
    val Orange = Color(0xFFFF9500)
    val Yellow = Color(0xFFFFCC00)
    val Pink = Color(0xFFFF2D55)
    val Purple = Color(0xFFAF52DE)
    val Indigo = Color(0xFF5856D6)
    val Teal = Color(0xFF30B0C7)
    val Cyan = Color(0xFF32ADE6)
    val Mint = Color(0xFF00C7BE)
    val Gray = Color(0xFF8E8E93)
    val Gray2 = Color(0xFFAEAEB2)
    val Gray3 = Color(0xFFC7C7CC)
    val DarkGray = Color(0xFF636366)

    val accents = listOf(Blue, Purple, Pink, Red, Orange, Yellow, Green, Teal, Indigo, Gray)
}

/** ホーム画面ラベルの影 (iOS と同じく白文字 + 柔らかい影)。 */
val LabelShadow = Shadow(color = Color.Black.copy(alpha = 0.45f), blurRadius = 6f)

fun iosTypography(bold: Boolean): Typography {
    val base = Typography()
    fun TextStyle.b(normal: FontWeight) = copy(fontWeight = if (bold) FontWeight(minOf(900, normal.weight + 200)) else normal)
    return base.copy(
        displayLarge = base.displayLarge.b(FontWeight.Bold),
        headlineLarge = base.headlineLarge.copy(fontSize = 34.sp).b(FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontSize = 28.sp).b(FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontSize = 22.sp).b(FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontSize = 17.sp).b(FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontSize = 17.sp).b(FontWeight.Normal),
        bodyMedium = base.bodyMedium.copy(fontSize = 15.sp).b(FontWeight.Normal),
        bodySmall = base.bodySmall.copy(fontSize = 13.sp).b(FontWeight.Normal),
        labelLarge = base.labelLarge.copy(fontSize = 15.sp).b(FontWeight.Medium),
        labelMedium = base.labelMedium.copy(fontSize = 13.sp).b(FontWeight.Medium),
        labelSmall = base.labelSmall.copy(fontSize = 11.sp).b(FontWeight.Medium),
    )
}

@Composable
fun resolveDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun LauncherTheme(settings: LauncherSettings, content: @Composable () -> Unit) {
    val dark = resolveDark(settings.themeMode)
    val accent = Color(settings.accentColor)
    val scheme = if (dark) {
        darkColorScheme(primary = accent, background = Color.Black, surface = Color(0xFF1C1C1E))
    } else {
        lightColorScheme(primary = accent, background = Color(0xFFF2F2F7), surface = Color.White)
    }
    MaterialTheme(colorScheme = scheme, typography = iosTypography(settings.boldText)) {
        CompositionLocalProvider(
            LocalLauncherSettings provides settings,
            LocalIsDark provides dark,
            content = content,
        )
    }
}

/** 設定のアニメーション速度・視差効果を減らすを反映した長さ。 */
fun LauncherSettings.duration(base: Int): Int =
    if (reduceMotion) (base * 0.6f).toInt() else (base / animationSpeed.coerceIn(0.25f, 3f)).toInt()

fun <T> LauncherSettings.tweenSpec(base: Int = 300): AnimationSpec<T> = tween(duration(base), easing = FastOutSlowInEasing)

fun <T> LauncherSettings.springSpec(): AnimationSpec<T> =
    if (reduceMotion) tween(duration(220)) else spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow * animationSpeed.coerceIn(0.25f, 3f))
