package com.umbral.codex.ui.sheet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umbral.codex.core.Dice
import com.umbral.codex.core.DiceParseException
import com.umbral.codex.core.Ids
import com.umbral.codex.core.RollMode
import com.umbral.codex.core.RollOutcome
import com.umbral.codex.core.SheetMath
import com.umbral.codex.data.CodexRepository
import com.umbral.codex.model.AppSettings
import com.umbral.codex.model.Character
import com.umbral.codex.model.RollRecord
import com.umbral.codex.ui.AppViewModel
import com.umbral.codex.ui.components.PanelShape
import com.umbral.codex.ui.theme.Codex
import com.umbral.codex.ui.theme.TitleFont
import kotlinx.coroutines.delay

/** Rótulo de campo com nome personalizado pelo usuário (ex.: "Classe" → "Ocupação"). */
fun Character.label(key: String, default: String): String = fieldLabels[key]?.takeIf { it.isNotBlank() } ?: default

/** Campos básicos cujos nomes podem ser trocados em Outras informações. */
val RenameableFields = listOf(
    "age" to "Idade", "gender" to "Gênero", "race" to "Raça", "charClass" to "Classe", "subclass" to "Subclasse",
    "background" to "Antecedente", "alignment" to "Alinhamento", "title" to "Título", "faction" to "Organização / facção",
    "armorClass" to "Classe de armadura", "initiative" to "Iniciativa", "speed" to "Deslocamento",
    "proficiency" to "Bônus de proficiência", "xp" to "Experiência",
)

/** Liga a ficha aberta ao repositório e ao sistema de rolagem. */
class SheetController(
    val id: String,
    val repo: CodexRepository,
    val vm: AppViewModel,
    val settings: AppSettings,
    private val popup: MutableState<RollRecord?>,
    private val haptics: HapticFeedback,
) {
    fun update(transform: (Character) -> Character) = repo.update(id, transform = transform)

    /** Rola uma expressão livre e registra no histórico da ficha. */
    fun roll(label: String, expression: String, mode: RollMode = RollMode.NORMAL, showPopup: Boolean = settings.showRollPopup): RollOutcome? {
        val outcome = try {
            Dice.roll(expression, mode = mode)
        } catch (e: DiceParseException) {
            vm.toast(e.message ?: "Expressão inválida"); return null
        }
        record(label, outcome, null, showPopup)
        return outcome
    }

    /**
     * Teste de perícia/atributo usando o dado configurado na ficha. Em sistemas "rolar abaixo"
     * (d100), o resultado é comparado com o valor-alvo em vez de somar o modificador.
     */
    fun rollCheck(c: Character, label: String, value: Int, mode: RollMode = RollMode.NORMAL) {
        val die = Dice.tryParse(c.rules.checkDie) ?: Dice.parse("1d20")
        if (c.rules.rollUnder) {
            val o = Dice.roll(die, mode = mode)
            val verdict = when {
                o.total == 1 -> "Sucesso crítico"
                o.total <= value / 5 -> "Sucesso extremo"
                o.total <= value / 2 -> "Sucesso bom"
                o.total <= value -> "Sucesso"
                o.total >= 96 && die.maxTotal == 100 -> "Desastre"
                else -> "Falha"
            }
            record("$label (alvo $value)", o, verdict, settings.showRollPopup)
        } else {
            val o = Dice.roll(die.withModifier(value), mode = mode)
            record("$label ${SheetMath.signed(value)}", o, null, settings.showRollPopup)
        }
    }

    private fun record(label: String, o: RollOutcome, verdict: String?, showPopup: Boolean) {
        val modeTxt = if (o.mode != RollMode.NORMAL) " · ${o.mode.label}" else ""
        val rec = RollRecord(
            id = Ids.new(),
            label = label + modeTxt,
            expression = o.expression.toString(),
            detail = listOfNotNull(o.detail(), verdict).joinToString("  ·  "),
            total = o.total,
            critical = o.isCritical || verdict?.startsWith("Sucesso crítico") == true || verdict == "Sucesso extremo",
            fumble = o.isFumble || verdict == "Desastre",
            timestamp = System.currentTimeMillis(),
        )
        repo.update(id, touch = false) { it.copy(rollHistory = (listOf(rec) + it.rollHistory).take(100)) }
        if (settings.haptics) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        if (showPopup) popup.value = rec
    }
}

/** Resultado em destaque que some sozinho (ou ao tocar). */
@Composable
fun RollPopup(state: MutableState<RollRecord?>, modifier: Modifier = Modifier) {
    val c = Codex.colors
    val rec = state.value
    LaunchedEffect(rec?.id) { if (rec != null) { delay(4500); if (state.value?.id == rec.id) state.value = null } }
    AnimatedVisibility(rec != null, modifier, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
        val r = rec ?: return@AnimatedVisibility
        val color = when { r.critical -> c.gold; r.fumble -> c.danger; else -> c.accent }
        Row(
            Modifier.padding(16.dp).widthIn(max = 560.dp).fillMaxWidth()
                .shadow(16.dp, PanelShape, ambientColor = color, spotColor = color)
                .background(c.panelHigh, PanelShape).border(1.dp, color.copy(alpha = 0.7f), PanelShape)
                .clickable { state.value = null }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(r.label, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(r.detail, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (r.critical) Text("✦ Crítico!", color = c.gold, style = MaterialTheme.typography.labelLarge)
                if (r.fumble) Text("Falha crítica", color = c.danger, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.width(12.dp))
            Text("${r.total}", color = color, fontFamily = TitleFont, fontWeight = FontWeight.Bold, fontSize = 38.sp)
        }
    }
}
