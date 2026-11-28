package com.umbral.codex.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umbral.codex.model.Catalog
import com.umbral.codex.ui.theme.Codex
import com.umbral.codex.ui.theme.TitleFont
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Capa do catálogo: a imagem escolhida pelo usuário ou uma arte arcana gerada a partir da
 * cor e do ícone do catálogo (cada catálogo tem estrelas e runas próprias, semeadas pelo id).
 */
@Composable
fun CatalogCover(catalog: Catalog, modifier: Modifier = Modifier, glyphSize: Dp = 54.dp) {
    val c = Codex.colors
    val accent = Color(catalog.accent)
    val img = rememberImage(catalog.coverImageId)
    Box(modifier.background(c.void), contentAlignment = Alignment.Center) {
        if (img != null) {
            Image(img, catalog.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, c.void.copy(alpha = 0.85f)))))
        } else {
            val seed = remember(catalog.id) { catalog.id.hashCode() }
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.radialGradient(listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0.12f), c.void), center = Offset(size.width * 0.5f, size.height * 0.35f), radius = size.maxDimension * 0.75f))
                val rnd = Random(seed)
                repeat(46) {
                    val x = rnd.nextFloat() * size.width; val y = rnd.nextFloat() * size.height
                    drawCircle(Color.White.copy(alpha = 0.15f + rnd.nextFloat() * 0.45f), radius = 0.6f + rnd.nextFloat() * 1.6f, center = Offset(x, y))
                }
                // Montanhas/ruínas em silhueta
                val p = Path().apply {
                    moveTo(0f, size.height)
                    var x = 0f
                    lineTo(0f, size.height * 0.78f)
                    while (x < size.width) {
                        x += size.width / (6 + rnd.nextInt(5))
                        lineTo(x, size.height * (0.62f + rnd.nextFloat() * 0.22f))
                    }
                    lineTo(size.width, size.height); close()
                }
                drawPath(p, Brush.verticalGradient(listOf(c.abyss.copy(alpha = 0.9f), c.void)))
                val r = size.minDimension * 0.36f
                val center = Offset(size.width / 2, size.height * 0.42f)
                drawCircle(c.gold.copy(alpha = 0.35f), r, center, style = Stroke(1.dp.toPx()))
                drawCircle(accent.copy(alpha = 0.5f), r * 0.82f, center, style = Stroke(0.8.dp.toPx()))
                val n = 5 + (seed.mod(4))
                val star = Path()
                var k = 0
                for (i in 0..n) {
                    val a = -PI / 2 + k * 2 * PI / n
                    val px = center.x + cos(a).toFloat() * r * 0.82f; val py = center.y + sin(a).toFloat() * r * 0.82f
                    if (i == 0) star.moveTo(px, py) else star.lineTo(px, py)
                    k = (k + 2) % n
                }
                drawPath(star, c.gold.copy(alpha = 0.25f), style = Stroke(0.8.dp.toPx()))
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GlyphMedallion(CodexIcons.of(catalog.glyph), accent, Modifier.size(glyphSize * 1.3f), size = glyphSize * 1.3f)
            }
        }
    }
}

/** Selo com a letra do dado, desenhado conforme o número de faces. */
@Composable
fun DieGlyph(sides: Int, modifier: Modifier = Modifier, color: Color = Codex.colors.accent, label: String = "d$sides", selected: Boolean = false, value: String? = null) {
    val c = Codex.colors
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val cx = w / 2; val cy = h / 2; val r = size.minDimension / 2 * 0.92f
            fun poly(n: Int, rot: Double = -PI / 2, rr: Float = r): Path = Path().apply {
                for (i in 0 until n) {
                    val a = rot + i * 2 * PI / n
                    val x = cx + cos(a).toFloat() * rr; val y = cy + sin(a).toFloat() * rr
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            val shape = when (sides) {
                4 -> poly(3)
                6 -> poly(4, -PI / 4)
                8 -> poly(4)
                10 -> Path().apply {
                    moveTo(cx, cy - r); lineTo(cx + r * 0.9f, cy - r * 0.1f); lineTo(cx, cy + r); lineTo(cx - r * 0.9f, cy - r * 0.1f); close()
                }
                12 -> poly(5)
                20 -> poly(6)
                else -> null
            }
            val fill = Brush.linearGradient(listOf(color.copy(alpha = if (selected) 0.55f else 0.25f), c.abyss), Offset(0f, 0f), Offset(w, h))
            val stroke = Stroke((if (selected) 2.dp else 1.2.dp).toPx())
            val edge = if (selected) c.gold else color.copy(alpha = 0.8f)
            if (shape != null) {
                drawPath(shape, fill); drawPath(shape, edge, style = stroke)
                if (sides == 20) drawPath(poly(3, -PI / 2, r * 0.62f), edge.copy(alpha = 0.6f), style = Stroke(0.8.dp.toPx()))
                if (sides == 8) drawLine(edge.copy(alpha = 0.5f), Offset(cx - r, cy), Offset(cx + r, cy), 0.8.dp.toPx())
                if (sides == 12) drawPath(poly(5, PI / 2, r * 0.5f), edge.copy(alpha = 0.5f), style = Stroke(0.8.dp.toPx()))
            } else {
                drawCircle(fill, r); drawCircle(edge, r, style = stroke)
                drawCircle(edge.copy(alpha = 0.5f), r * 0.7f, style = Stroke(0.8.dp.toPx()))
            }
        }
        Text(
            value ?: label,
            color = c.text,
            fontFamily = TitleFont,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (value != null) 20.sp else 13.sp,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun AccentIcon(name: String?, color: Color, modifier: Modifier = Modifier) {
    Icon(CodexIcons.of(name), null, tint = color, modifier = modifier)
}
