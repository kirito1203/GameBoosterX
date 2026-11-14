package com.gamebooster.x.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gamebooster.x.ui.theme.GbxColors

/** Painel padrão: fundo grafite com borda neon discreta. */
@Composable
fun GbxPanel(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    trailingColor: Color = GbxColors.Neon,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(GbxColors.Panel, GbxColors.Graphite)))
            .border(1.dp, GbxColors.PanelBorder, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = GbxColors.Neon,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                Text(trailing, style = MaterialTheme.typography.labelSmall, color = trailingColor, fontWeight = FontWeight.Bold)
            }
        }
        content()
    }
}

@Composable
fun StatRow(label: String, value: String, valueColor: Color = GbxColors.TextPrimary) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = GbxColors.TextSecondary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor, fontWeight = FontWeight.SemiBold)
    }
}

/** Barra horizontal animada (0..1). */
@Composable
fun NeonBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(500), label = "bar")
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(GbxColors.Black)
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.6f), color)))
        )
    }
}

/**
 * Gráfico de linha em tempo real. [minValue]/[maxValue] fixam a escala vertical;
 * com menos de 2 pontos nada é desenhado (sem dados inventados).
 */
@Composable
fun Sparkline(
    values: List<Float>,
    minValue: Float,
    maxValue: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        // Grade
        val gridColor = GbxColors.PanelBorder
        for (i in 0..2) {
            val y = size.height * i / 2f
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        if (values.size < 2) return@Canvas
        val range = (maxValue - minValue).takeIf { it > 0f } ?: 1f
        val stepX = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - ((v - minValue) / range).coerceIn(0f, 1f) * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color.copy(alpha = 0.25f), style = Stroke(width = 8f, cap = StrokeCap.Round))
        drawPath(path, color, style = Stroke(width = 3f, cap = StrokeCap.Round))
    }
}
