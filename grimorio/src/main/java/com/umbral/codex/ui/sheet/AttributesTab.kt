package com.umbral.codex.ui.sheet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umbral.codex.core.Ids
import com.umbral.codex.core.SheetMath
import com.umbral.codex.model.Attribute
import com.umbral.codex.model.Character
import com.umbral.codex.model.ModifierRule
import com.umbral.codex.ui.components.AutoGrid
import com.umbral.codex.ui.components.CodexTextField
import com.umbral.codex.ui.components.DropdownField
import com.umbral.codex.ui.components.EditorDialog
import com.umbral.codex.ui.components.FieldRow
import com.umbral.codex.ui.components.GhostButton
import com.umbral.codex.ui.components.Hint
import com.umbral.codex.ui.components.IntField
import com.umbral.codex.ui.components.OrnatePanel
import com.umbral.codex.ui.components.RoundIconButton
import com.umbral.codex.ui.components.SectionTitle
import com.umbral.codex.ui.components.Stepper
import com.umbral.codex.ui.components.SuggestField
import com.umbral.codex.ui.theme.Codex
import com.umbral.codex.ui.theme.TitleFont
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val ExtraAttributeSuggestions = listOf(
    "Poder arcano", "Energia espiritual", "Controle sobrenatural", "Sanidade", "Corrupção", "Humanidade",
    "Essência", "Aura", "Poder demoníaco", "Resistência mágica", "Vitalidade", "Fé", "Sorte",
)

@Composable
fun AttributesTab(c: Character, ctl: SheetController) {
    val col = Codex.colors
    var editing by remember { mutableStateOf<Attribute?>(null) }
    var creating by remember { mutableStateOf(false) }
    TabColumn {
        SectionTitle("Atributos (${c.attributes.size})") {
            RoundIconButton(Icons.Filled.Add, "Novo atributo", { creating = true }, tint = col.accent, size = 36.dp)
        }
        AutoGrid(c.attributes, minItemWidth = 150.dp, maxColumns = 4) { a ->
            AttributeCircle(a, c, onEdit = { editing = a }, onRoll = {
                ctl.rollCheck(c, a.name, if (c.rules.rollUnder) a.value else SheetMath.modifier(a, c.rules))
            })
        }
        GhostButton("Criar atributo personalizado", { creating = true }, Modifier.fillMaxWidth(), icon = Icons.Filled.Add)
        Hint("Toque no círculo para rolar um teste (${c.rules.checkDie}${if (c.rules.rollUnder) ", rolar abaixo do valor" else " + modificador"}). Use o lápis para editar.")
        OrnatePanel(Modifier.fillMaxWidth()) {
            SectionTitle("Regra de modificador")
            DropdownField("Como o modificador é calculado", ModifierRule.entries, c.rules.modifierRule, { it.label }, { r -> ctl.update { it.copy(rules = it.rules.copy(modifierRule = r)) } })
            Spacer(Modifier.height(6.dp))
            Hint(c.rules.modifierRule.description)
            when (c.rules.modifierRule) {
                ModifierRule.D20 -> { Spacer(Modifier.height(8.dp)); IntField(c.rules.modifierBase, { v -> ctl.update { it.copy(rules = it.rules.copy(modifierBase = v)) } }, "Valor de referência (base)", Modifier.fillMaxWidth()) }
                ModifierRule.DIVIDED -> { Spacer(Modifier.height(8.dp)); IntField(c.rules.modifierDivisor, { v -> ctl.update { it.copy(rules = it.rules.copy(modifierDivisor = v)) } }, "Divisor", Modifier.fillMaxWidth(), allowNegative = false, min = 1) }
                else -> {}
            }
        }

    }
    if (creating) AttributeDialog(null, c, ctl) { creating = false }
    editing?.let { a -> AttributeDialog(c.attributes.firstOrNull { it.id == a.id } ?: a, c, ctl) { editing = null } }
}

/** Atributo em forma de selo circular: anel proporcional ao valor, número central e modificador. */
@Composable
private fun AttributeCircle(a: Attribute, c: Character, onEdit: () -> Unit, onRoll: () -> Unit) {
    val col = Codex.colors
    val mod = SheetMath.modifier(a, c.rules)
    val scaleMax = when {
        c.rules.rollUnder -> 100f
        c.rules.modifierRule == ModifierRule.DIRECT -> 10f
        else -> 30f
    }
    val fraction = (a.value / scaleMax).coerceIn(0f, 1f)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(col.panelHigh, col.panel)))
            .border(1.dp, col.line, RoundedCornerShape(16.dp)).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(a.name.uppercase(), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = col.gold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            RoundIconButton(Icons.Filled.Edit, "Editar ${a.name}", onEdit, size = 32.dp, tint = col.faint)
        }
        Box(Modifier.fillMaxWidth(0.82f).aspectRatio(1f).clip(RoundedCornerShape(100.dp)).clickable(onClick = onRoll), contentAlignment = Alignment.Center) {
            val accent = col.accent; val gold = col.gold; val line = col.line; val abyss = col.abyss
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2
                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.28f), abyss)), r * 0.92f)
                drawCircle(line, r * 0.92f, style = Stroke(5.dp.toPx()))
                drawArc(
                    Brush.sweepGradient(listOf(accent.copy(alpha = 0.5f), accent, gold)),
                    startAngle = -90f, sweepAngle = 360f * fraction, useCenter = false,
                    topLeft = Offset(center.x - r * 0.92f, center.y - r * 0.92f),
                    size = androidx.compose.ui.geometry.Size(r * 1.84f, r * 1.84f),
                    style = Stroke(5.dp.toPx(), cap = StrokeCap.Round),
                )
                for (k in 0 until 12) {
                    val ang = k * PI / 6
                    drawCircle(gold.copy(alpha = 0.5f), 1.3.dp.toPx(), Offset(center.x + cos(ang).toFloat() * r * 0.74f, center.y + sin(ang).toFloat() * r * 0.74f))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${a.value}", color = col.text, fontFamily = TitleFont, fontWeight = FontWeight.SemiBold, fontSize = 30.sp)
                Text(a.abbr.ifBlank { a.name.take(3).uppercase() }, color = col.muted, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (c.rules.rollUnder) "Alvo ${a.value}" else SheetMath.signed(mod),
                Modifier.clip(RoundedCornerShape(8.dp)).background(col.accent.copy(alpha = 0.18f)).border(1.dp, col.accent.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 4.dp),
                color = col.text, style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.size(4.dp))
            RoundIconButton(Icons.Filled.Casino, "Rolar ${a.name}", onRoll, size = 36.dp, tint = col.accent)
        }
        if (a.bonus != 0 || a.penalty != 0) {
            Text(
                listOfNotNull(a.bonus.takeIf { it != 0 }?.let { "bônus +$it" }, a.penalty.takeIf { it != 0 }?.let { "penal. −$it" }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = col.faint, textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun AttributeDialog(initial: Attribute?, c: Character, ctl: SheetController, onDismiss: () -> Unit) {
    var a by remember { mutableStateOf(initial ?: Attribute(Ids.new(), "", "", 10)) }
    val col = Codex.colors
    val idx = initial?.let { i -> c.attributes.indexOfFirst { it.id == i.id } } ?: -1
    EditorDialog(
        if (initial == null) "Novo atributo" else "Editar ${initial.name}", onDismiss,
        {
            val fixed = a.copy(name = a.name.trim(), abbr = a.abbr.trim().uppercase().take(5))
            ctl.update { ch -> ch.copy(attributes = if (initial == null) ch.attributes + fixed else ch.attributes.map { if (it.id == fixed.id) fixed else it }) }
            onDismiss()
        },
        saveEnabled = a.name.isNotBlank(),
        onDelete = initial?.let {
            {
                // Perícias ligadas a este atributo ficam sem atributo.
                ctl.update { ch -> ch.copy(attributes = ch.attributes.filterNot { x -> x.id == it.id }, skills = ch.skills.map { s -> if (s.attributeId == it.id) s.copy(attributeId = null) else s }) }
                onDismiss()
            }
        },
        deleteWhat = "o atributo “${initial?.name}” (perícias ligadas a ele ficarão sem atributo)",
    ) {
        SuggestField(a.name, { a = a.copy(name = it) }, "Nome", if (initial == null) ExtraAttributeSuggestions.filterNot { s -> c.attributes.any { it.name == s } } else emptyList())
        CodexTextField(a.abbr, { a = a.copy(abbr = it) }, "Abreviação (ex.: FOR, ESS)")
        Stepper(a.value, { a = a.copy(value = it) }, Modifier.fillMaxWidth(), label = "Valor")
        IntField(a.value, { a = a.copy(value = it) }, "Valor (digitar)", Modifier.fillMaxWidth())
        FieldRow {
            IntField(a.bonus, { a = a.copy(bonus = it) }, "Bônus", Modifier.weight(1f))
            IntField(a.penalty, { a = a.copy(penalty = it) }, "Penalidade", Modifier.weight(1f))
        }
        if (c.rules.modifierRule == ModifierRule.MANUAL) IntField(a.manualModifier, { a = a.copy(manualModifier = it) }, "Modificador (manual)", Modifier.fillMaxWidth())
        Text("Modificador final: ${SheetMath.signed(SheetMath.modifier(a, c.rules))}", style = MaterialTheme.typography.titleSmall, color = col.gold)
        CodexTextField(a.description, { a = a.copy(description = it) }, "Descrição", singleLine = false, minLines = 2)
        if (idx >= 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Mover para cima", { ctl.update { it.copy(attributes = it.attributes.move(idx, -1)) }; onDismiss() }, Modifier.weight(1f), icon = Icons.Filled.ArrowUpward, enabled = idx > 0, color = col.muted)
                GhostButton("Para baixo", { ctl.update { it.copy(attributes = it.attributes.move(idx, 1)) }; onDismiss() }, Modifier.weight(1f), icon = Icons.Filled.ArrowDownward, enabled = idx < c.attributes.lastIndex, color = col.muted)
            }
        }
    }
}

fun <T> List<T>.move(index: Int, delta: Int): List<T> {
    val to = index + delta
    if (index !in indices || to !in indices) return this
    return toMutableList().also { val x = it.removeAt(index); it.add(to, x) }
}
