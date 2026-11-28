package com.umbral.codex.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.umbral.codex.ui.theme.Codex
import com.umbral.codex.ui.theme.TitleFont
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val PanelShape = RoundedCornerShape(14.dp)

/** Cantoneiras ornamentais douradas desenhadas nos quatro cantos. */
fun Modifier.ornateCorners(color: Color, enabled: Boolean = true, arm: Dp = 14.dp, inset: Dp = 5.dp): Modifier =
    if (!enabled) this else drawWithContent {
        drawContent()
        val a = arm.toPx(); val i = inset.toPx(); val s = 1.2.dp.toPx(); val d = 2.2.dp.toPx()
        val w = size.width; val h = size.height
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(color, Offset(x, y), Offset(x + dx * a, y), s, StrokeCap.Round)
            drawLine(color, Offset(x, y), Offset(x, y + dy * a), s, StrokeCap.Round)
            val p = Path().apply {
                moveTo(x + dx * (d * 2.2f), y + dy * (d * 2.2f) - d)
                lineTo(x + dx * (d * 2.2f) + d, y + dy * (d * 2.2f))
                lineTo(x + dx * (d * 2.2f), y + dy * (d * 2.2f) + d)
                lineTo(x + dx * (d * 2.2f) - d, y + dy * (d * 2.2f))
                close()
            }
            drawPath(p, color)
        }
        corner(i, i, 1f, 1f); corner(w - i, i, -1f, 1f); corner(i, h - i, 1f, -1f); corner(w - i, h - i, -1f, -1f)
    }

/** Painel principal do grimório: fundo grafite-violeta, borda fina, cantoneiras e brilho opcional. */
@Composable
fun OrnatePanel(
    modifier: Modifier = Modifier,
    accent: Color = Codex.colors.accent,
    glow: Boolean = false,
    ornaments: Boolean = true,
    padding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Codex.colors
    val style = Codex.style
    val glowOn = glow && style.glow
    Column(
        modifier
            .then(if (glowOn) Modifier.shadow(14.dp, PanelShape, ambientColor = accent, spotColor = accent) else Modifier)
            .clip(PanelShape)
            .background(Brush.verticalGradient(listOf(c.panelHigh, c.panel)))
            .border(BorderStroke(1.dp, Brush.verticalGradient(listOf(accent.copy(alpha = 0.45f), c.line))), PanelShape)
            .ornateCorners(c.gold.copy(alpha = 0.55f), ornaments && style.ornaments)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** Título de seção com losango dourado e linha que se desfaz. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable RowScope.() -> Unit)? = null) {
    val c = Codex.colors
    Row(modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("✦", color = c.gold, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.width(8.dp))
        Text(
            text.uppercase(), color = c.gold, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.weight(1f).height(1.dp)
                .background(Brush.horizontalGradient(listOf(c.gold.copy(alpha = 0.5f), Color.Transparent)))
        )
        if (action != null) { Spacer(Modifier.width(6.dp)); action() }
    }
}

/** Divisor decorativo com runa central. */
@Composable
fun RuneDivider(modifier: Modifier = Modifier) {
    val c = Codex.colors
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, c.goldDim))))
        Text("  ✧  ", color = c.goldDim, style = MaterialTheme.typography.labelMedium)
        Box(Modifier.weight(1f).height(1.dp).background(Brush.horizontalGradient(listOf(c.goldDim, Color.Transparent))))
    }
}

/** Círculo arcano desenhado: anéis, marcas, estrela e polígono interno. Pode girar devagar. */
@Composable
fun ArcaneSigil(
    modifier: Modifier = Modifier,
    color: Color = Codex.colors.accent,
    secondary: Color = Codex.colors.gold,
    points: Int = 7,
    animated: Boolean = true,
) {
    val rotation = if (animated && Codex.style.glow) {
        val t = rememberInfiniteTransition(label = "sigil")
        t.animateFloat(0f, 360f, infiniteRepeatable(tween(60_000, easing = LinearEasing)), label = "rot").value
    } else 0f
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val center = this.center
        drawCircle(color.copy(alpha = 0.10f), r * 0.98f)
        drawCircle(secondary.copy(alpha = 0.55f), r * 0.96f, style = Stroke(1.2.dp.toPx()))
        drawCircle(color.copy(alpha = 0.55f), r * 0.86f, style = Stroke(0.8.dp.toPx()))
        rotate(rotation, center) {
            for (k in 0 until 48) {
                val ang = (k / 48f) * 2 * PI
                val inner = if (k % 4 == 0) r * 0.86f else r * 0.90f
                drawLine(
                    secondary.copy(alpha = 0.5f),
                    Offset(center.x + cos(ang).toFloat() * inner, center.y + sin(ang).toFloat() * inner),
                    Offset(center.x + cos(ang).toFloat() * r * 0.94f, center.y + sin(ang).toFloat() * r * 0.94f),
                    0.8.dp.toPx(),
                )
            }
            star(center, r * 0.80f, points, color.copy(alpha = 0.75f), 1.dp.toPx())
        }
        rotate(-rotation * 1.5f, center) {
            polygon(center, r * 0.46f, points, secondary.copy(alpha = 0.6f), 0.9.dp.toPx())
        }
        drawCircle(color.copy(alpha = 0.35f), r * 0.34f, style = Stroke(0.8.dp.toPx()))
    }
}

private fun DrawScope.polygon(center: Offset, r: Float, n: Int, color: Color, stroke: Float) {
    val p = Path()
    for (k in 0..n) {
        val a = -PI / 2 + k * 2 * PI / n
        val x = center.x + cos(a).toFloat() * r; val y = center.y + sin(a).toFloat() * r
        if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    drawPath(p, color, style = Stroke(stroke))
}

private fun DrawScope.star(center: Offset, r: Float, n: Int, color: Color, stroke: Float) {
    // Estrela {n/2}: liga cada vértice ao que está a dois passos.
    val p = Path()
    val step = if (n % 2 == 0) n / 2 - 1 else 2
    var k = 0
    for (i in 0..n) {
        val a = -PI / 2 + k * 2 * PI / n
        val x = center.x + cos(a).toFloat() * r; val y = center.y + sin(a).toFloat() * r
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        k = (k + step) % n
    }
    drawPath(p, color, style = Stroke(stroke))
}

/** Botão principal com gradiente violeta e brilho discreto. */
@Composable
fun ArcaneButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    color: Color = Codex.colors.accent,
) {
    val c = Codex.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .heightIn(min = 48.dp)
            .then(if (Codex.style.glow && enabled) Modifier.shadow(10.dp, shape, ambientColor = color, spotColor = color) else Modifier)
            .clip(shape)
            .background(
                if (enabled) Brush.verticalGradient(listOf(color, lerpColor(color, c.accentDeep, 0.55f)))
                else Brush.verticalGradient(listOf(c.panelHighest, c.panelHighest))
            )
            .border(1.dp, c.gold.copy(alpha = if (enabled) 0.45f else 0.15f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val fg = if (enabled) ColorMath.onColor(color) else c.faint
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Botão secundário com contorno. */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = Codex.colors.accent,
    enabled: Boolean = true,
) {
    val c = Codex.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = if (enabled) 0.55f else 0.2f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val fg = if (enabled) color else c.faint
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Botão de ícone redondo com área de toque de 44dp. */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Codex.colors.muted,
    size: Dp = 44.dp,
    filled: Boolean = false,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .then(if (filled) Modifier.background(tint.copy(alpha = 0.14f)).border(1.dp, tint.copy(alpha = 0.4f), CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** Etiqueta pequena colorida. */
@Composable
fun Tag(text: String, color: Color = Codex.colors.accent, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        // Texto clareado: cores de catálogo escuras continuam legíveis no fundo preto.
        color = lerpColor(color, Color.White, 0.35f),
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Barra de progresso com brilho na ponta. */
@Composable
fun GlowBar(progress: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 10.dp) {
    val c = Codex.colors
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(450), label = "bar")
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(c.void)
            .border(1.dp, c.line, RoundedCornerShape(height / 2))
    ) {
        if (p > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(p)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(height / 2))
                    .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.55f), color)))
            )
        }
    }
}

/** Valor destacado com rótulo (CA, Iniciativa...). */
@Composable
fun StatBadge(label: String, value: String, modifier: Modifier = Modifier, color: Color = Codex.colors.accent, onClick: (() -> Unit)? = null) {
    val c = Codex.colors
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(c.abyss)
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = c.text, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label.uppercase(), color = c.muted, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2)
    }
}

/** Estado vazio com selo arcano. */
@Composable
fun EmptyState(title: String, text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val c = Codex.colors
    Column(modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ArcaneSigil(Modifier.size(84.dp), color = c.accent.copy(alpha = 0.7f), animated = false)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.text, textAlign = TextAlign.Center, fontFamily = TitleFont)
        Spacer(Modifier.height(4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = c.muted, textAlign = TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(14.dp)); action() }
    }
}

/** Ícone temático dentro de um losango/medalhão. */
@Composable
fun GlyphMedallion(icon: ImageVector, color: Color, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val c = Codex.colors
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(size * 0.78f).rotate(45f)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.linearGradient(listOf(color.copy(alpha = 0.35f), c.abyss)))
                .border(1.dp, c.gold.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
        )
        Icon(icon, null, tint = c.text, modifier = Modifier.size(size * 0.42f))
    }
}

fun lerpColor(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t,
)
