package com.umbral.codex.ui.sheet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umbral.codex.core.Dice
import com.umbral.codex.core.DiceParseException
import com.umbral.codex.core.RollMode
import com.umbral.codex.core.SheetMath
import com.umbral.codex.model.Character
import com.umbral.codex.model.RollRecord
import com.umbral.codex.ui.components.ArcaneButton
import com.umbral.codex.ui.components.AutoGrid
import com.umbral.codex.ui.components.ChoiceChips
import com.umbral.codex.ui.components.CodexTextField
import com.umbral.codex.ui.components.ConfirmDialog
import com.umbral.codex.ui.components.DieGlyph
import com.umbral.codex.ui.components.GhostButton
import com.umbral.codex.ui.components.Hint
import com.umbral.codex.ui.components.OrnatePanel
import com.umbral.codex.ui.components.RoundIconButton
import com.umbral.codex.ui.components.SectionTitle
import com.umbral.codex.ui.components.Stepper
import com.umbral.codex.ui.components.formatDate
import com.umbral.codex.ui.theme.Codex
import com.umbral.codex.ui.theme.TitleFont
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun DiceTab(c: Character, ctl: SheetController) {
    val col = Codex.colors
    val scope = rememberCoroutineScope()
    var sides by rememberSaveable { mutableIntStateOf(20) }
    var count by rememberSaveable { mutableIntStateOf(1) }
    var modifier by rememberSaveable { mutableIntStateOf(0) }
    var mode by rememberSaveable { mutableStateOf(RollMode.NORMAL) }
    var custom by rememberSaveable { mutableStateOf("") }
    var shown by remember { mutableStateOf<RollRecord?>(null) }
    var rolling by remember { mutableStateOf(false) }
    var flicker by remember { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    val rotation = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    val animate = Codex.style.diceAnimation

    val builtExpression = Dice.build(count, sides, modifier).toString()
    val customError = custom.isNotBlank() && Dice.tryParse(custom) == null

    fun doRoll(expression: String, label: String) {
        if (rolling) return
        val parsed = try { Dice.parse(expression) } catch (e: DiceParseException) { ctl.vm.toast(e.message ?: "Expressão inválida"); return }
        scope.launch {
            if (animate) {
                rolling = true
                val spin = launch { rotation.snapTo(0f); rotation.animateTo(720f, tween(700, easing = FastOutSlowInEasing)) }
                val pulse = launch { scale.snapTo(0.8f); scale.animateTo(1.12f, tween(500)); scale.animateTo(1f, tween(200)) }
                repeat(10) { flicker = Random.nextInt(parsed.minTotal, parsed.maxTotal + 1); delay(60) }
                spin.join(); pulse.join()
                rolling = false
            }
            ctl.roll(label, parsed.toString(), mode, showPopup = false)
            shown = ctl.repo.character(c.id)?.rollHistory?.firstOrNull()
        }
    }

    TabColumn {
        OrnatePanel(Modifier.fillMaxWidth(), glow = true) {
            SectionTitle("Mesa de dados")
            // Resultado em destaque
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                val r = shown
                val color = when { r?.critical == true -> col.gold; r?.fumble == true -> col.danger; else -> col.accent }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    DieGlyph(
                        sides, Modifier.size(132.dp).rotate(rotation.value).scale(scale.value), color = color, selected = true,
                        value = when { rolling -> "$flicker"; r != null -> "${r.total}"; else -> "?" },
                    )
                    Spacer(Modifier.height(8.dp))
                    if (r != null && !rolling) {
                        Text(r.label, style = MaterialTheme.typography.titleSmall, color = col.text, textAlign = TextAlign.Center)
                        Text(r.detail, style = MaterialTheme.typography.bodySmall, color = col.muted, textAlign = TextAlign.Center)
                        if (r.critical) Text("✦ Crítico!", color = col.gold, style = MaterialTheme.typography.titleSmall)
                        if (r.fumble) Text("Falha crítica", color = col.danger, style = MaterialTheme.typography.titleSmall)
                    } else if (!rolling) Text("Escolha o dado e role", style = MaterialTheme.typography.bodyMedium, color = col.muted)
                }
            }
            // Seletor de dados
            AutoGrid(Dice.STANDARD, minItemWidth = 64.dp, maxColumns = 7, spacing = 6.dp) { s ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { sides = s }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    DieGlyph(s, Modifier.fillMaxWidth(0.9f).aspectRatio(1f), selected = s == sides, label = "D$s")
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stepper(count, { count = it }, min = 1, max = 100, label = "Quantidade")
                Stepper(modifier, { modifier = it }, min = -99, max = 99, label = "Modificador", format = { SheetMath.signed(it) })
            }
            Spacer(Modifier.height(10.dp))
            ChoiceChips(RollMode.entries, mode, { it.label }, { mode = it })
            Spacer(Modifier.height(12.dp))
            ArcaneButton("Rolar $builtExpression", { doRoll(builtExpression, builtExpression) }, Modifier.fillMaxWidth(), icon = Icons.Filled.Casino, enabled = !rolling)
        }

        OrnatePanel(Modifier.fillMaxWidth()) {
            SectionTitle("Expressão livre")
            Row(verticalAlignment = Alignment.CenterVertically) {
                CodexTextField(custom, { custom = it }, "Ex.: 1d20+5, 3d6+2, 4d6kh3, 1d100", Modifier.weight(1f), isError = customError)
                Spacer(Modifier.width(8.dp))
                RoundIconButton(Icons.Filled.Casino, "Rolar expressão", { if (custom.isNotBlank()) doRoll(custom, custom.trim()) }, tint = col.accent, filled = true, size = 52.dp)
            }
            if (customError) Text("Formato: NdX ± N. Use kh/kl para manter maiores/menores (4d6kh3).", style = MaterialTheme.typography.bodySmall, color = col.danger)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("1d20", "2d6", "3d6", "4d6kh3", "1d100").forEach { e -> GhostButton(e, { custom = e; doRoll(e, e) }, Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(4.dp))
            Hint("Atalhos rápidos também aparecem em perícias, atributos, magias, poderes e itens.")
        }

        OrnatePanel(Modifier.fillMaxWidth()) {
            SectionTitle("Histórico (${c.rollHistory.size})") {
                if (c.rollHistory.isNotEmpty()) RoundIconButton(Icons.Filled.DeleteSweep, "Limpar histórico", { confirmClear = true }, tint = col.danger, size = 36.dp)
            }
            if (c.rollHistory.isEmpty()) Hint("As rolagens desta ficha ficam salvas aqui (até 100).")
            c.rollHistory.forEach { r -> HistoryRow(r) { doRoll(r.expression, r.label.substringBefore(" · ")) } }
        }
    }
    if (confirmClear) ConfirmDialog("Limpar resultados?", "O histórico de rolagens desta ficha será apagado.", "Limpar",
        { ctl.update { it.copy(rollHistory = emptyList()) }; shown = null; confirmClear = false }, { confirmClear = false }, destructive = true)
}

@Composable
private fun HistoryRow(r: RollRecord, onReroll: () -> Unit) {
    val col = Codex.colors
    val color = when { r.critical -> col.gold; r.fumble -> col.danger; else -> col.text }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp)).background(col.abyss)
            .border(1.dp, col.line, RoundedCornerShape(10.dp)).clickable(onClick = onReroll).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(r.label, style = MaterialTheme.typography.bodyMedium, color = col.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(r.detail, style = MaterialTheme.typography.bodySmall, color = col.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(formatDate(r.timestamp), style = MaterialTheme.typography.bodySmall, color = col.faint)
        }
        Spacer(Modifier.width(8.dp))
        Text("${r.total}", color = color, fontFamily = TitleFont, fontWeight = FontWeight.Bold, fontSize = 26.sp)
    }
}
