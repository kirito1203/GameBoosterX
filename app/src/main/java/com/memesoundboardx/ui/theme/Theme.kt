package com.memesoundboardx.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object NeonColors {
    val BgDeep = Color(0xFF07080F)
    val Surface = Color(0xFF10121E)
    val SurfaceHigh = Color(0xFF181B2C)
    val Outline = Color(0xFF2A2F4A)
    val Blue = Color(0xFF2E8BFF)
    val Purple = Color(0xFF9B4DFF)
    val Cyan = Color(0xFF22D3EE)
    val Text = Color(0xFFE8EAFF)
    val TextDim = Color(0xFF9AA0C3)
    val Danger = Color(0xFFFF4D6D)
    val Warning = Color(0xFFFFB547)

    val NeonGradient = Brush.linearGradient(listOf(Blue, Purple))
}

private val DarkScheme = darkColorScheme(
    primary = NeonColors.Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF13305C),
    onPrimaryContainer = NeonColors.Text,
    secondary = NeonColors.Purple,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF34195C),
    onSecondaryContainer = NeonColors.Text,
    tertiary = NeonColors.Cyan,
    background = NeonColors.BgDeep,
    onBackground = NeonColors.Text,
    surface = NeonColors.Surface,
    onSurface = NeonColors.Text,
    surfaceVariant = NeonColors.SurfaceHigh,
    onSurfaceVariant = NeonColors.TextDim,
    surfaceContainer = NeonColors.Surface,
    surfaceContainerHigh = NeonColors.SurfaceHigh,
    outline = NeonColors.Outline,
    error = NeonColors.Danger,
)

private val AppTypography = Typography().let { base ->
    base.copy(
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 1.5.sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
    )
}

val MonoLabel = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, letterSpacing = 0.5.sp)

@Composable
fun MemeSoundboardTheme(content: @Composable () -> Unit) {
    // Sempre escuro: é a identidade visual do app e economiza bateria em telas OLED/LCD escuras.
    MaterialTheme(
        colorScheme = DarkScheme,
        typography = AppTypography,
        content = content,
    )
}
