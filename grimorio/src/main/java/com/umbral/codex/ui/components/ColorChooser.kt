package com.umbral.codex.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.umbral.codex.ui.theme.Codex
import com.umbral.codex.ui.theme.TitleFont

/** Conversões de cor usadas pelo seletor livre. */
object ColorMath {
    fun toHsv(argb: Long): FloatArray {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(argb.toInt(), hsv)
        return hsv
    }

    fun fromHsv(h: Float, s: Float, v: Float): Long =
        android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)).toLong() and 0xFFFFFFFFL

    fun toHex(argb: Long): String = "#%06X".format(argb and 0xFFFFFF)

    /** Aceita "#RGB", "#RRGGBB", "RRGGBB" ou "#AARRGGBB". */
    fun parseHex(text: String): Long? {
        var t = text.trim().removePrefix("#")
        if (t.length == 3) t = t.map { "$it$it" }.joinToString("")
        if (t.length == 6) t = "FF$t"
        if (t.length != 8 || !t.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        return t.toLong(16) or 0xFF000000L
    }

    /** Texto legível sobre a cor (escuro em cores claras, branco em cores escuras). */
    fun onColor(c: Color): Color = if (c.luminance() > 0.38f) Color(0xFF0E0918) else Color.White
}

/** Bolinha "outra cor": arco-íris com +, ou a cor personalizada atual quando selecionada. */
@Composable
fun CustomColorDot(current: Long?, selected: Boolean, onClick: () -> Unit) {
    val c = Codex.colors
    Box(
        Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick)
            .semantics { contentDescription = "Cor personalizada" }
            .border(2.dp, if (selected) c.text else Color.Transparent, CircleShape).padding(5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(
                if (selected && current != null) Brush.linearGradient(listOf(Color(current), Color(current)))
                else Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red))
            ),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
    }
}

/** Faixa deslizante desenhada com gradiente (matiz, saturação ou brilho). */
@Composable
private fun GradientSlider(label: String, value: Float, colors: List<Color>, onChange: (Float) -> Unit, display: String) {
    val c = Codex.colors
    Column {
        Row {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = c.muted)
            Text(display, style = MaterialTheme.typography.bodySmall, color = c.text)
        }
        Spacer(Modifier.height(4.dp))
        Canvas(
            Modifier.fillMaxWidth().height(36.dp)
                .semantics { contentDescription = label }
                .pointerInput(Unit) { detectTapGestures { o -> onChange((o.x / size.width).coerceIn(0f, 1f)) } }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ -> onChange((change.position.x / size.width).coerceIn(0f, 1f)) }
                }
        ) {
            val top = size.height * 0.25f
            val h = size.height * 0.5f
            drawRoundRect(Brush.horizontalGradient(colors), topLeft = Offset(0f, top), size = androidx.compose.ui.geometry.Size(size.width, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2))
            val x = (value * size.width).coerceIn(h / 2, size.width - h / 2)
            drawCircle(Color.White, size.height * 0.42f, Offset(x, size.height / 2))
            drawCircle(Color.Black.copy(alpha = 0.5f), size.height * 0.42f, Offset(x, size.height / 2), style = Stroke(2.dp.toPx()))
        }
    }
}

/**
 * Seletor de qualquer cor: matiz, saturação e brilho, código hexadecimal e prévia.
 * [minBrightness] impede cores tão escuras que somem no fundo preto do grimório.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorChooserDialog(
    title: String,
    initial: Long,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
    minBrightness: Float = 0.35f,
) {
    val c = Codex.colors
    val start = remember { ColorMath.toHsv(initial) }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var bri by remember { mutableFloatStateOf(start[2].coerceAtLeast(minBrightness)) }
    val argb = ColorMath.fromHsv(hue, sat, bri)
    var hex by remember { mutableStateOf(ColorMath.toHex(initial)) }
    var hexError by remember { mutableStateOf(false) }
    val color = Color(argb)

    fun setFrom(value: Long, updateHex: Boolean = true) {
        val hsv = ColorMath.toHsv(value)
        hue = hsv[0]; sat = hsv[1]; bri = hsv[2].coerceAtLeast(minBrightness)
        if (updateHex) hex = ColorMath.toHex(ColorMath.fromHsv(hue, sat, bri))
    }

    EditorDialog(title, onDismiss, { onPick(argb) }, saveLabel = "Aplicar") {
        // Prévia: painel e botão na cor escolhida
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(color).border(1.dp, c.gold.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) { Text("Aa", color = ColorMath.onColor(color), fontFamily = TitleFont, style = MaterialTheme.typography.titleLarge) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(ColorMath.toHex(argb), style = MaterialTheme.typography.titleMedium, color = color)
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(Brush.verticalGradient(listOf(color, lerpColor(color, Color.Black, 0.45f))))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) { Text("Botão de exemplo", color = ColorMath.onColor(color), style = MaterialTheme.typography.labelLarge) }
            }
        }
        GradientSlider(
            "Matiz", hue / 360f,
            listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red),
            { hue = it * 360f; hex = ColorMath.toHex(ColorMath.fromHsv(hue, sat, bri)); hexError = false }, "${hue.toInt()}°",
        )
        GradientSlider(
            "Saturação", sat,
            listOf(Color(ColorMath.fromHsv(hue, 0f, bri)), Color(ColorMath.fromHsv(hue, 1f, bri))),
            { sat = it; hex = ColorMath.toHex(ColorMath.fromHsv(hue, sat, bri)); hexError = false }, "${(sat * 100).toInt()}%",
        )
        GradientSlider(
            "Brilho", (bri - minBrightness) / (1f - minBrightness),
            listOf(Color(ColorMath.fromHsv(hue, sat, minBrightness)), Color(ColorMath.fromHsv(hue, sat, 1f))),
            { bri = minBrightness + it * (1f - minBrightness); hex = ColorMath.toHex(ColorMath.fromHsv(hue, sat, bri)); hexError = false }, "${(bri * 100).toInt()}%",
        )
        CodexTextField(
            hex,
            { t -> hex = t; val v = ColorMath.parseHex(t); hexError = v == null && t.isNotBlank(); if (v != null) setFrom(v, updateHex = false) },
            "Código da cor (ex.: #FF5500)",
            isError = hexError,
            capitalization = KeyboardCapitalization.Characters,
            supportingText = if (hexError) "Use 6 dígitos hexadecimais, ex.: #8B5CF6" else null,
        )
        Text("Cores rápidas", style = MaterialTheme.typography.bodySmall, color = c.muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            QuickColors.forEach { q -> ColorDot(Color(q), q == argb) { setFrom(q) } }
        }
        if (minBrightness > 0f) Hint("O brilho mínimo evita cores que desapareçam no fundo escuro.")
    }
}

private val QuickColors = listOf(
    0xFF9D6BFFL, 0xFFD14B6AL, 0xFFFF5500L, 0xFFE0A84AL, 0xFFF2D16BL, 0xFF8BD450L, 0xFF4FC79AL, 0xFF3CC8E0L,
    0xFF6C8CFFL, 0xFFB04E9AL, 0xFFFF77C8L, 0xFFE8E2F5L, 0xFF9A93A8L, 0xFFB8734AL,
)
