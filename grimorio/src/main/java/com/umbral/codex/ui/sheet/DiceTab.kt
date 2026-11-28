package com.umbral.codex.ui.sheet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.LightMode
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umbral.codex.core.Dice
import com.umbral.codex.core.DiceExpression
import com.umbral.codex.core.DiceParseException
import com.umbral.codex.core.DiceTerm
import com.umbral.codex.core.Ids
import com.umbral.codex.core.RollMode
import com.umbral.codex.core.RollOutcome
import com.umbral.codex.core.SheetMath
import com.umbral.codex.model.Character
import com.umbral.codex.model.RollRecord
import com.umbral.codex.model.SavedRoll
import com.umbral.codex.ui.components.ArcaneButton
import com.umbral.codex.ui.components.CodexTextField
import com.umbral.codex.ui.components.ConfirmDialog
import com.umbral.codex.ui.components.DieGlyph
import com.umbral.codex.ui.components.EditorDialog
import com.umbral.codex.ui.components.GhostButton
import com.umbral.codex.ui.components.Hint
import com.umbral.codex.ui.components.OrnatePanel
import com.umbral.codex.ui.components.RoundIconButton
import com.umbral.codex.ui.components.SectionTitle
import com.umbral.codex.ui.components.formatDate
import com.umbral.codex.ui.theme.Codex
import com.umbral.codex.ui.theme.TitleFont
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Dados do seletor, na ordem da referência (o d100 fica no botão %). */
private val SelectorDice = listOf(20, 12, 10, 8, 6, 4, 2)
private val PoolOrder = listOf(20, 12, 10, 8, 6, 4, 2, 100)
private const val MAX_POOL = 20

/** Expressão da mesa: dados agrupados por tipo + modificador, ex.: "2d20+1d6+3". */
fun poolExpression(pool: List<Int>, mod: Int): String {
    val terms = PoolOrder.mapNotNull { s -> pool.count { it == s }.takeIf { it > 0 }?.let { DiceTerm(it, s) } }
    return DiceExpression(terms, mod).toString()
}

/** Converte uma expressão simples (só somas de dados) em mesa + modificador; null se não couber na mesa. */
fun expressionToPool(e: DiceExpression): Pair<List<Int>, Int>? {
    if (e.terms.any { it.negative || it.sides !in PoolOrder }) return null
    val pool = e.terms.flatMap { t -> List(t.count) { t.sides } }
    return if (pool.size in 1..MAX_POOL) pool to e.constant else null
}

/** Dados da mesa a partir do resultado: mantidos acesos; descartados (kh/kl, vantagem) apagados. */
private fun boardFrom(o: RollOutcome, seedBase: Int): List<BoardDie> {
    var n = 0
    fun from(out: RollOutcome, dimAll: Boolean) = out.results.flatMap { r ->
        r.rolls.mapIndexed { i, v -> BoardDie(r.term.sides, v, dim = dimAll || i !in r.keptIndices, seed = seedBase + (n++) * 97) }
    }
    return (from(o, false) + (o.discarded?.let { from(it, true) } ?: emptyList())).take(MAX_POOL * 2)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DiceTab(c: Character, ctl: SheetController) {
    val col = Codex.colors
    val scope = rememberCoroutineScope()
    var pool by rememberSaveable { mutableStateOf(listOf(20)) }
    var mod by rememberSaveable { mutableIntStateOf(0) }
    var mode by rememberSaveable { mutableStateOf(RollMode.NORMAL) }
    var light by rememberSaveable { mutableStateOf(true) }
    var rolledDice by remember { mutableStateOf<List<BoardDie>?>(null) }
    var rollKey by remember { mutableIntStateOf(0) }
    var rolling by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf<RollRecord?>(null) }
    var custom by rememberSaveable { mutableStateOf("") }
    var showHistory by remember { mutableStateOf(false) }
    var showSaved by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val animate = Codex.style.diceAnimation
    val seedBase = remember { Random.nextInt(1, 1_000_000) }

    val expression = poolExpression(pool, mod)
    val boardDice = rolledDice ?: pool.mapIndexed { i, s -> BoardDie(s, null, seed = seedBase + i * 131 + s) }

    fun changePool(newPool: List<Int>) { pool = newPool; rolledDice = null; shown = null }

    /** Rola a mesa (ou uma expressão livre), anima e só então registra e mostra o resultado. */
    fun roll(expr: String = expression, label: String = expr) {
        if (rolling) return
        val parsed = try { Dice.parse(expr) } catch (e: DiceParseException) { ctl.vm.toast(e.message ?: "Expressão inválida"); return }
        if (parsed.terms.isEmpty()) { ctl.vm.toast("Coloque pelo menos um dado na mesa"); return }
        expressionToPool(parsed)?.let { (p, m) -> pool = p; mod = m }
        // Vantagem/desvantagem dobra só os d20 (mantém o maior/menor de cada par), como na mesa.
        val d20s = parsed.terms.filter { it.sides == 20 && !it.negative && it.keepHigh == null && it.keepLow == null }
        val (effective, effMode) = if (mode != RollMode.NORMAL && d20s.isNotEmpty()) {
            parsed.copy(terms = parsed.terms.map { t ->
                if (t in d20s) t.copy(
                    count = t.count * 2,
                    keepHigh = if (mode == RollMode.ADVANTAGE) t.count else null,
                    keepLow = if (mode == RollMode.DISADVANTAGE) t.count else null,
                ) else t
            }) to RollMode.NORMAL
        } else parsed to mode
        val outcome = Dice.roll(effective, mode = effMode)
        val fullLabel = if (mode != RollMode.NORMAL && effMode == RollMode.NORMAL) "$label · ${mode.label}" else label
        rolledDice = boardFrom(outcome, seedBase + rollKey * 13)
        shown = null; rolling = true; rollKey++
        scope.launch {
            if (animate) delay(1300)
            shown = ctl.recordOutcome(fullLabel, outcome)
            rolling = false
        }
    }

    TabColumn {
        // ---- Barra de ferramentas
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(if (light) Icons.Filled.LightMode else Icons.Outlined.LightMode, if (light) "Apagar luz da mesa" else "Acender luz da mesa", { light = !light }, tint = if (light) col.accent else col.muted)
            RoundIconButton(Icons.Filled.History, "Histórico de rolagens", { showHistory = true }, tint = col.muted)
            RoundIconButton(if (c.savedRolls.isEmpty()) Icons.Filled.BookmarkBorder else Icons.Filled.Bookmark, "Rolagens salvas", { showSaved = true }, tint = col.muted)
            ModeBadge("ADV", "Vantagem", mode == RollMode.ADVANTAGE) { mode = if (mode == RollMode.ADVANTAGE) RollMode.NORMAL else RollMode.ADVANTAGE; rolledDice = null; shown = null }
            ModeBadge("DIS", "Desvantagem", mode == RollMode.DISADVANTAGE) { mode = if (mode == RollMode.DISADVANTAGE) RollMode.NORMAL else RollMode.DISADVANTAGE; rolledDice = null; shown = null }
            ModeBadge("%", "Dado percentual d100", pool == listOf(100), diamond = true) { changePool(listOf(100)) }
        }

        // ---- Mesa 3D
        DiceBoard(
            dice = boardDice,
            rollKey = rollKey,
            animate = animate,
            light = light,
            onTap = { if (pool.isNotEmpty()) roll() },
            onRemove = { i ->
                val s = boardDice.getOrNull(i)?.sides ?: return@DiceBoard
                val idx = pool.lastIndexOf(s)
                if (idx >= 0) changePool(pool.toMutableList().also { it.removeAt(idx) })
            },
            modifier = Modifier.fillMaxWidth().aspectRatio(0.98f),
        )

        // ---- Resultado / instruções
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            val r = shown
            when {
                rolling -> Text("Rolando…", style = MaterialTheme.typography.titleMedium, color = col.muted)
                r != null -> {
                    val color = when { r.critical -> col.gold; r.fumble -> col.danger; else -> col.text }
                    Text("${r.total}", color = color, fontFamily = TitleFont, fontWeight = FontWeight.Bold, fontSize = 46.sp)
                    Text(r.label, style = MaterialTheme.typography.titleSmall, color = col.text, textAlign = TextAlign.Center)
                    Text(r.detail, style = MaterialTheme.typography.bodySmall, color = col.muted, textAlign = TextAlign.Center)
                    if (r.critical) Text("✦ Crítico!", color = col.gold, style = MaterialTheme.typography.titleSmall)
                    if (r.fumble) Text("Falha crítica", color = col.danger, style = MaterialTheme.typography.titleSmall)
                }
                else -> {
                    Text(
                        if (pool.isEmpty()) "Mesa vazia" else expression + if (mode != RollMode.NORMAL) " · ${mode.label}" else "",
                        style = MaterialTheme.typography.titleMedium, color = col.text, fontFamily = TitleFont,
                    )
                    Text(
                        "Toque no tabuleiro ou em Rolar · segure um dado para tirá-lo",
                        style = MaterialTheme.typography.bodyMedium, color = col.muted, textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // ---- Limpar · Modificador · Rolar
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            GhostButton("Limpar", { changePool(emptyList()); mod = 0 }, color = col.muted)
            Row(
                Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, col.accent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(start = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("MOD", style = MaterialTheme.typography.labelSmall, color = col.muted)
                Spacer(Modifier.weight(1f))
                RoundIconButton(Icons.Filled.Remove, "Diminuir modificador", { mod = (mod - 1).coerceAtLeast(-99); rolledDice = null; shown = null }, tint = col.text, size = 40.dp)
                Text(SheetMath.signed(mod).let { if (mod == 0) "0" else it }, Modifier.width(34.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium, color = col.text)
                RoundIconButton(Icons.Filled.Add, "Aumentar modificador", { mod = (mod + 1).coerceAtMost(99); rolledDice = null; shown = null }, tint = col.text, size = 40.dp)
            }
            ArcaneButton("Rolar", { roll() }, enabled = pool.isNotEmpty() && !rolling, icon = Icons.Filled.Casino)
        }

        // ---- Seletor de dados: toque adiciona, segurar remove
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            SelectorDice.forEach { s ->
                val count = pool.count { it == s }
                Column(
                    Modifier.weight(1f)
                        .semantics { contentDescription = "Adicionar d$s" }
                        .clip(RoundedCornerShape(10.dp))
                        .combinedClickable(
                            onClick = { if (pool.size >= MAX_POOL) ctl.vm.toast("Máximo de $MAX_POOL dados na mesa") else changePool(pool + s) },
                            onLongClick = { val i = pool.lastIndexOf(s); if (i >= 0) changePool(pool.toMutableList().also { it.removeAt(i) }) },
                        )
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(if (count > 0) "$count" else " ", style = MaterialTheme.typography.labelLarge, color = col.text)
                    DieGlyph(s, Modifier.fillMaxWidth(0.95f).aspectRatio(1f), selected = count > 0, label = "$s")
                }
            }
        }
        Hint("Toque num dado para colocá-lo na mesa; segure para tirar. O % coloca um d100.")

        // ---- Expressão livre
        OrnatePanel(Modifier.fillMaxWidth()) {
            SectionTitle("Expressão livre")
            Row(verticalAlignment = Alignment.CenterVertically) {
                val customError = custom.isNotBlank() && Dice.tryParse(custom) == null
                CodexTextField(custom, { custom = it }, "Ex.: 1d20+5, 3d6+2, 4d6kh3", Modifier.weight(1f), isError = customError)
                Spacer(Modifier.width(8.dp))
                RoundIconButton(Icons.Filled.Casino, "Rolar expressão", { if (custom.isNotBlank()) roll(custom.trim()) }, tint = col.accent, filled = true, size = 52.dp)
            }
            Hint("Somas de dados vão para a mesa. Use kh/kl para manter maiores/menores (4d6kh3).")
        }

        // ---- Histórico recente
        OrnatePanel(Modifier.fillMaxWidth()) {
            SectionTitle("Histórico (${c.rollHistory.size})") {
                if (c.rollHistory.isNotEmpty()) RoundIconButton(Icons.Filled.DeleteSweep, "Limpar histórico", { confirmClear = true }, tint = col.danger, size = 36.dp)
            }
            if (c.rollHistory.isEmpty()) Hint("As rolagens desta ficha ficam salvas aqui (até 100).")
            c.rollHistory.take(8).forEach { r -> HistoryRow(r) { roll(r.expression, r.label.substringBefore(" · ")) } }
            if (c.rollHistory.size > 8) GhostButton("Ver todo o histórico", { showHistory = true }, Modifier.fillMaxWidth().padding(top = 6.dp))
        }
    }

    if (showHistory) {
        EditorDialog("Histórico de rolagens", { showHistory = false }, { showHistory = false }, saveLabel = "Fechar") {
            if (c.rollHistory.isEmpty()) Hint("Nenhuma rolagem ainda.")
            c.rollHistory.forEach { r -> HistoryRow(r) { showHistory = false; roll(r.expression, r.label.substringBefore(" · ")) } }
        }
    }
    if (showSaved) SavedRollsDialog(c, ctl, expression, onRoll = { s -> showSaved = false; roll(s.expression, s.name) }, onDismiss = { showSaved = false })
    if (confirmClear) ConfirmDialog("Limpar resultados?", "O histórico de rolagens desta ficha será apagado.", "Limpar",
        { ctl.update { it.copy(rollHistory = emptyList()) }; shown = null; confirmClear = false }, { confirmClear = false }, destructive = true)
}

/** Botões ADV / DIS / % desenhados como selos (hexágono ou losango). */
@Composable
private fun ModeBadge(text: String, description: String, selected: Boolean, diamond: Boolean = false, onClick: () -> Unit) {
    val col = Codex.colors
    val tint = if (selected) col.accent else col.muted
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).semantics { contentDescription = description }.clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(38.dp)) {
            val r = size.minDimension / 2; val cx = size.width / 2; val cy = size.height / 2
            val n = if (diamond) 4 else 6
            val p = Path()
            for (i in 0 until n) {
                val a = -PI / 2 + i * 2 * PI / n
                val x = cx + cos(a).toFloat() * r; val y = cy + sin(a).toFloat() * r
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            p.close()
            if (selected) drawPath(p, tint.copy(alpha = 0.25f))
            drawPath(p, tint, style = Stroke(1.5.dp.toPx()))
            if (!diamond) drawLine(tint.copy(alpha = 0.5f), Offset(cx - r * 0.25f, cy - r * 0.45f), Offset(cx + r * 0.25f, cy - r * 0.45f), 1.dp.toPx())
        }
        Text(text, color = if (selected) col.text else col.muted, fontSize = if (text.length > 1) 10.sp else 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SavedRollsDialog(c: Character, ctl: SheetController, current: String, onRoll: (SavedRoll) -> Unit, onDismiss: () -> Unit) {
    val col = Codex.colors
    var name by remember { mutableStateOf("") }
    var expr by remember { mutableStateOf(current.takeIf { it != "0" } ?: "1d20") }
    val valid = name.isNotBlank() && Dice.tryParse(expr) != null
    EditorDialog("Rolagens salvas", onDismiss, onDismiss, saveLabel = "Fechar") {
        if (c.savedRolls.isEmpty()) Hint("Guarde rolagens que você usa sempre, como “Ataque com espada · 1d20+5”.")
        c.savedRolls.forEach { s ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(col.abyss).clickable { onRoll(s) }.padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(s.name, style = MaterialTheme.typography.bodyLarge, color = col.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(s.expression, style = MaterialTheme.typography.bodySmall, color = col.accent)
                }
                RoundIconButton(Icons.Filled.Delete, "Apagar ${s.name}", { ctl.update { ch -> ch.copy(savedRolls = ch.savedRolls.filterNot { it.id == s.id }) } }, tint = col.faint)
            }
        }
        SectionTitle("Salvar nova")
        CodexTextField(name, { name = it }, "Nome", placeholder = "Ataque com espada")
        CodexTextField(expr, { expr = it }, "Expressão", isError = expr.isNotBlank() && Dice.tryParse(expr) == null)
        GhostButton("Salvar rolagem", {
            ctl.update { ch -> ch.copy(savedRolls = ch.savedRolls + SavedRoll(Ids.new(), name.trim(), Dice.parse(expr).toString())) }
            ctl.vm.toast("Rolagem “${name.trim()}” salva"); name = ""
        }, Modifier.fillMaxWidth(), icon = Icons.Filled.BookmarkBorder, enabled = valid)
    }
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
