package com.umbral.codex.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.umbral.codex.model.AccentTheme
import com.umbral.codex.model.AppSettings

/** Paleta do grimório. O destaque muda conforme o tema escolhido em Configurações. */
@Immutable
data class CodexPalette(
    val void: Color = Color(0xFF07060B),
    val abyss: Color = Color(0xFF0D0A14),
    val panel: Color = Color(0xFF14101E),
    val panelHigh: Color = Color(0xFF1C1629),
    val panelHighest: Color = Color(0xFF261E37),
    val line: Color = Color(0xFF2E2442),
    val accent: Color = Color(0xFF9D6BFF),
    val accentDeep: Color = Color(0xFF4C1D95),
    val gold: Color = Color(0xFFC9A55C),
    val goldDim: Color = Color(0xFF7D6638),
    val text: Color = Color(0xFFECE6F6),
    val muted: Color = Color(0xFF9C92B4),
    val faint: Color = Color(0xFF675E7C),
    val danger: Color = Color(0xFFD14B6A),
    val success: Color = Color(0xFF5FB98A),
    val warning: Color = Color(0xFFE0A84A),
)

@Immutable
data class CodexStyle(val glow: Boolean = true, val ornaments: Boolean = true, val diceAnimation: Boolean = true, val haptics: Boolean = true)

val LocalPalette = staticCompositionLocalOf { CodexPalette() }
val LocalCodexStyle = staticCompositionLocalOf { CodexStyle() }

object Codex {
    val colors: CodexPalette @Composable get() = LocalPalette.current
    val style: CodexStyle @Composable get() = LocalCodexStyle.current
}

/** Serifada (Noto Serif no Android) para títulos; sans legível para dados da ficha. */
val TitleFont = FontFamily.Serif
val BodyFont = FontFamily.SansSerif

private fun typography(p: CodexPalette) = Typography(
    displaySmall = TextStyle(fontFamily = TitleFont, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, letterSpacing = 1.sp, color = p.text),
    headlineMedium = TextStyle(fontFamily = TitleFont, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, letterSpacing = 0.5.sp, color = p.text),
    headlineSmall = TextStyle(fontFamily = TitleFont, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = p.text),
    titleLarge = TextStyle(fontFamily = TitleFont, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = p.text),
    titleMedium = TextStyle(fontFamily = TitleFont, fontWeight = FontWeight.Medium, fontSize = 17.sp, color = p.text),
    titleSmall = TextStyle(fontFamily = TitleFont, fontWeight = FontWeight.Medium, fontSize = 15.sp, letterSpacing = 0.4.sp, color = p.text),
    bodyLarge = TextStyle(fontFamily = BodyFont, fontSize = 16.sp, lineHeight = 23.sp, color = p.text),
    bodyMedium = TextStyle(fontFamily = BodyFont, fontSize = 14.sp, lineHeight = 20.sp, color = p.text),
    bodySmall = TextStyle(fontFamily = BodyFont, fontSize = 12.sp, lineHeight = 16.sp, color = p.muted),
    labelLarge = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.4.sp),
    labelMedium = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.6.sp),
    labelSmall = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.2.sp),
)

private fun scheme(p: CodexPalette): ColorScheme = darkColorScheme(
    primary = p.accent,
    onPrimary = Color(0xFF0B0712),
    primaryContainer = p.accentDeep,
    onPrimaryContainer = p.text,
    secondary = p.gold,
    onSecondary = Color(0xFF1A1206),
    tertiary = p.gold,
    background = p.void,
    onBackground = p.text,
    surface = p.panel,
    onSurface = p.text,
    surfaceVariant = p.panelHigh,
    onSurfaceVariant = p.muted,
    surfaceContainer = p.panel,
    surfaceContainerHigh = p.panelHigh,
    surfaceContainerHighest = p.panelHighest,
    surfaceContainerLow = p.abyss,
    surfaceContainerLowest = p.void,
    outline = p.line,
    outlineVariant = p.line,
    error = p.danger,
    onError = Color.White,
)

fun paletteFor(accent: AccentTheme) = CodexPalette(accent = Color(accent.primary), accentDeep = Color(accent.deep))

@Composable
fun CodexTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val palette = paletteFor(settings.accent)
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalCodexStyle provides CodexStyle(settings.glowEffects, settings.ornaments, settings.diceAnimation, settings.haptics),
        LocalDensity provides Density(density.density, density.fontScale * settings.textScale.factor),
    ) {
        MaterialTheme(colorScheme = scheme(palette), typography = typography(palette), content = content)
    }
}
