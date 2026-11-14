package com.gamebooster.x.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object GbxColors {
    val Black = Color(0xFF05070A)
    val Graphite = Color(0xFF11151B)
    val Panel = Color(0xFF161B22)
    val PanelBorder = Color(0xFF243040)
    val ElectricBlue = Color(0xFF2F7BFF)
    val Neon = Color(0xFF00B8FF)
    val TextPrimary = Color(0xFFE6EDF3)
    val TextSecondary = Color(0xFF8B98A9)
    val Ok = Color(0xFF2BD99F)
    val Warn = Color(0xFFFFB020)
    val Danger = Color(0xFFFF4D5E)
}

private val scheme = darkColorScheme(
    primary = GbxColors.ElectricBlue,
    onPrimary = Color.White,
    secondary = GbxColors.Neon,
    onSecondary = GbxColors.Black,
    background = GbxColors.Black,
    onBackground = GbxColors.TextPrimary,
    surface = GbxColors.Panel,
    onSurface = GbxColors.TextPrimary,
    surfaceVariant = GbxColors.Graphite,
    onSurfaceVariant = GbxColors.TextSecondary,
    outline = GbxColors.PanelBorder,
    error = GbxColors.Danger,
)

private val typography = Typography(
    headlineSmall = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 2.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 1.sp),
)

@Composable
fun GameBoosterTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
