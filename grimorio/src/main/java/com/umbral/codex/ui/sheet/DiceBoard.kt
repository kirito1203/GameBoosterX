package com.umbral.codex.ui.sheet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.umbral.codex.ui.components.DieMesh
import com.umbral.codex.ui.components.drawDie3D
import com.umbral.codex.ui.components.easeOutCubic
import com.umbral.codex.ui.components.restingRotation
import com.umbral.codex.ui.components.tumbleRotation
import com.umbral.codex.ui.theme.Codex
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Um dado na mesa. [value] nulo = ainda não rolado; [dim] = descartado (vantagem/desvantagem, kh/kl). */
data class BoardDie(val sides: Int, val value: Int?, val dim: Boolean = false, val seed: Int)

/** Posição e raio de cada dado na mesa: grade que se adapta à quantidade, com leve desalinhamento natural. */
fun boardLayout(dice: List<BoardDie>, w: Float, h: Float): List<Pair<Offset, Float>> {
    val n = dice.size
    if (n == 0) return emptyList()
    val cols = ceil(sqrt(n * w / h)).toInt().coerceIn(1, n)
    val rows = ceil(n / cols.toFloat()).toInt()
    val cellW = w / cols; val cellH = h / rows
    val base = if (n == 1) min(w, h) * 0.27f else min(cellW, cellH) * 0.34f
    return dice.mapIndexed { i, d ->
        val row = i / cols; val col = i % cols
        val inRow = if (row == rows - 1) n - row * cols else cols
        val shift = (cols - inRow) * cellW / 2
        val rnd = Random(d.seed)
        val jx = (rnd.nextFloat() - 0.5f) * cellW * 0.12f
        val jy = (rnd.nextFloat() - 0.5f) * cellH * 0.10f
        val r = base * (if (d.dim) 0.78f else 1f)
        Offset(shift + (col + 0.5f) * cellW + jx, (row + 0.5f) * cellH + jy) to r
    }
}

@Composable
fun DiceBoard(
    dice: List<BoardDie>,
    rollKey: Int,
    animate: Boolean,
    light: Boolean,
    onTap: () -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Codex.colors
    val accent = c.accent
    val progress = remember { Animatable(1f) }
    LaunchedEffect(rollKey) {
        if (rollKey > 0 && animate) { progress.snapTo(0f); progress.animateTo(1f, tween(1250, easing = LinearEasing)) } else progress.snapTo(1f)
    }
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .clip(shape)
            .semantics { contentDescription = "Mesa de dados" }
            .pointerInput(dice) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { pos ->
                        val layout = boardLayout(dice, size.width.toFloat(), size.height.toFloat())
                        val hit = layout.withIndex().minByOrNull { (_, p) -> (p.first - pos).getDistance() }
                        if (hit != null && (hit.value.first - pos).getDistance() < hit.value.second * 1.4f) onRemove(hit.index)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            // Tabuleiro: fundo escuro, faixa de "mesa" e foco de luz na cor do app
            drawRect(Brush.verticalGradient(listOf(Color(0xFF0B080D), Color(0xFF060407))))
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.06f), Color.Transparent), startY = h * 0.30f, endY = h * 0.75f),
                topLeft = Offset(0f, h * 0.30f), size = Size(w, h * 0.45f),
            )
            drawLine(accent.copy(alpha = 0.10f), Offset(0f, h * 0.36f), Offset(w, h * 0.36f), 1.dp.toPx())
            drawCircle(
                Brush.radialGradient(listOf(accent.copy(alpha = if (light) 0.26f else 0.08f), Color.Transparent), Offset(w / 2, h * 0.48f), max(w, h) * 0.6f),
                max(w, h) * 0.6f, Offset(w / 2, h * 0.48f),
            )
            // Vinheta nas bordas
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), Offset(w / 2, h / 2), max(w, h) * 0.75f))

            val t = progress.value
            val layout = boardLayout(dice, w, h)
            dice.forEachIndexed { i, d ->
                val (target, r) = layout[i]
                val mesh = DieMesh.of(d.sides)
                val rolled = d.value != null
                val rnd = Random(d.seed + rollKey * 7919)
                // Arremesso: entra pela lateral, quica e desacelera até o lugar
                val e = easeOutCubic(t)
                val from = Offset((rnd.nextFloat() - 0.5f) * w * 1.1f, h * (0.35f + rnd.nextFloat() * 0.3f))
                val hop = if (rolled && t < 1f) abs(sin(t * PI.toFloat() * 3.2f)) * (1 - t).pow(1.6f) * r * 1.3f else 0f
                val pos = if (rolled && t < 1f) Offset(target.x + from.x * (1 - e), target.y + from.y * (1 - e) - hop) else target

                // Sombra no chão (encolhe quando o dado está no ar)
                val shadowScale = 1f - (hop / (r * 3f)).coerceIn(0f, 0.6f)
                val groundY = (if (rolled && t < 1f) target.y + from.y * (1 - e) else target.y) + r * 0.92f
                drawOval(
                    Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent), Offset(pos.x, groundY), r * 1.15f * shadowScale),
                    topLeft = Offset(pos.x - r * 1.15f * shadowScale, groundY - r * 0.32f * shadowScale),
                    size = Size(r * 2.3f * shadowScale, r * 0.64f * shadowScale),
                )

                val face = mesh.faceFor(d.value ?: mesh.labels.mapNotNull { it.toIntOrNull() }.maxOrNull() ?: 1)
                val rest = restingRotation(mesh, face, d.seed)
                val rot = if (rolled) tumbleRotation(rest, d.seed + rollKey, t) else rest
                val settled = !rolled || t >= 1f
                drawDie3D(
                    mesh, rot, pos, r, accent,
                    alpha = if (d.dim) 0.38f else 1f,
                    highlightFace = if (rolled && settled) face else -1,
                    valueLabel = if (d.sides == 100 && d.value != null && settled) (if (d.value == 100) "100" else d.value.toString()) else null,
                )
                if (d.dim && settled) {
                    // Risco diagonal discreto marca dados descartados
                    drawLine(c.muted.copy(alpha = 0.6f), Offset(pos.x - r * 0.7f, pos.y + r * 0.7f), Offset(pos.x + r * 0.7f, pos.y - r * 0.7f), 1.5.dp.toPx())
                }
            }
            drawRoundRect(accent.copy(alpha = 0.35f), style = Stroke(1.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()))
        }
        if (dice.isEmpty()) {
            Text(
                "Mesa vazia\nToque num dado abaixo para colocá-lo aqui",
                style = MaterialTheme.typography.bodyMedium, color = c.muted, textAlign = TextAlign.Center,
            )
        }
    }
}
