package com.umbral.codex.ui.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.umbral.codex.core.Ids
import com.umbral.codex.core.RollMode
import com.umbral.codex.core.SheetMath
import com.umbral.codex.model.Attribute
import com.umbral.codex.model.Character
import com.umbral.codex.model.Proficiency
import com.umbral.codex.model.Skill
import com.umbral.codex.ui.components.ChoiceChips
import com.umbral.codex.ui.components.CodexTextField
import com.umbral.codex.ui.components.DropdownField
import com.umbral.codex.ui.components.EditorDialog
import com.umbral.codex.ui.components.EmptyState
import com.umbral.codex.ui.components.FieldRow
import com.umbral.codex.ui.components.GhostButton
import com.umbral.codex.ui.components.Hint
import com.umbral.codex.ui.components.IntField
import com.umbral.codex.ui.components.OrnatePanel
import com.umbral.codex.ui.components.RoundIconButton
import com.umbral.codex.ui.components.SwitchRow
import com.umbral.codex.ui.theme.Codex

@Composable
fun SkillsTab(c: Character, ctl: SheetController) {
    val col = Codex.colors
    var query by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(RollMode.NORMAL) }
    var onlyTrained by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Skill?>(null) }
    var creating by remember { mutableStateOf(false) }
    val attrs = c.attributes.associateBy { it.id }
    val visible = c.skills
        .filter { query.isBlank() || it.name.contains(query.trim(), true) }
        .filter { !onlyTrained || it.trained || it.proficiency != Proficiency.NONE }

    TabColumn {
        OrnatePanel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Perícias", style = MaterialTheme.typography.titleLarge, color = col.text)
                    Text("Proficiência ${SheetMath.signed(SheetMath.proficiency(c))} · teste ${c.rules.checkDie}${if (c.rules.rollUnder) " (rolar abaixo)" else ""}", style = MaterialTheme.typography.bodySmall, color = col.muted)
                }
                RoundIconButton(Icons.Filled.Add, "Nova perícia", { creating = true }, tint = col.accent, filled = true)
            }
            Spacer(Modifier.size(8.dp))
            CodexTextField(query, { query = it }, "Pesquisar perícia")
            Spacer(Modifier.size(8.dp))
            ChoiceChips(RollMode.entries, mode, { it.label }, { mode = it })
            SwitchRow("Somente treinadas/proficientes", onlyTrained, { onlyTrained = it })
        }
        Hint("Toque no nome para rolar. Toque no círculo para alternar a proficiência (vazio → proficiente → especialista → metade).")
        if (visible.isEmpty()) EmptyState("Nenhuma perícia", if (c.skills.isEmpty()) "Adicione as perícias do seu sistema." else "Nada corresponde ao filtro.")
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            visible.forEach { s ->
                SkillRow(
                    s, attrs[s.attributeId], c,
                    onRoll = { ctl.rollCheck(c, s.name, SheetMath.skillTotal(s, c), mode) },
                    onCycle = {
                        val next = when (s.proficiency) {
                            Proficiency.NONE -> Proficiency.PROFICIENT
                            Proficiency.PROFICIENT -> Proficiency.EXPERT
                            Proficiency.EXPERT -> Proficiency.HALF
                            Proficiency.HALF -> Proficiency.NONE
                        }
                        ctl.update { ch -> ch.copy(skills = ch.skills.map { if (it.id == s.id) it.copy(proficiency = next) else it }) }
                    },
                    onEdit = { editing = s },
                )
            }
        }
        GhostButton("Adicionar perícia", { creating = true }, Modifier.fillMaxWidth(), icon = Icons.Filled.Add)
    }
    if (creating) SkillDialog(null, c, ctl) { creating = false }
    editing?.let { s -> SkillDialog(c.skills.firstOrNull { it.id == s.id } ?: s, c, ctl) { editing = null } }
}

@Composable
private fun SkillRow(s: Skill, attr: Attribute?, c: Character, onRoll: () -> Unit, onCycle: () -> Unit, onEdit: () -> Unit) {
    val col = Codex.colors
    val total = SheetMath.skillTotal(s, c)
    val profColor = when (s.proficiency) {
        Proficiency.NONE -> col.faint
        Proficiency.HALF -> col.muted
        Proficiency.PROFICIENT -> col.accent
        Proficiency.EXPERT -> col.gold
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(col.panel).border(1.dp, col.line, RoundedCornerShape(12.dp))
            .heightIn(min = 56.dp).padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onCycle), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(18.dp).clip(CircleShape)
                    .background(if (s.proficiency == Proficiency.NONE) col.abyss else profColor.copy(alpha = if (s.proficiency == Proficiency.HALF) 0.4f else 1f))
                    .border(1.5.dp, profColor, CircleShape)
            ) {
                if (s.proficiency == Proficiency.EXPERT) Box(Modifier.align(Alignment.Center).size(7.dp).clip(CircleShape).background(col.void))
            }
        }
        Column(Modifier.weight(1f).clickable(onClick = onRoll).padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.name, style = MaterialTheme.typography.bodyLarge, color = col.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (s.trained) { Spacer(Modifier.width(6.dp)); Text("treinada", style = MaterialTheme.typography.labelSmall, color = col.gold) }
            }
            Text(
                listOfNotNull(attr?.let { it.abbr.ifBlank { it.name } }, s.proficiency.takeIf { it != Proficiency.NONE }?.label).joinToString(" · ").ifBlank { "Sem atributo" },
                style = MaterialTheme.typography.bodySmall, color = col.muted, maxLines = 1,
            )
        }
        Text(
            if (c.rules.rollUnder) "$total" else SheetMath.signed(total),
            Modifier.widthIn(min = 52.dp).clip(RoundedCornerShape(10.dp)).background(col.accent.copy(alpha = 0.14f)).clickable(onClick = onRoll).padding(horizontal = 10.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleMedium, color = col.text, textAlign = TextAlign.Center,
        )
        RoundIconButton(Icons.Filled.Edit, "Editar ${s.name}", onEdit, size = 40.dp, tint = col.faint)
    }
}

@Composable
private fun SkillDialog(initial: Skill?, c: Character, ctl: SheetController, onDismiss: () -> Unit) {
    var s by remember { mutableStateOf(initial ?: Skill(Ids.new(), "", c.attributes.firstOrNull()?.id)) }
    val col = Codex.colors
    EditorDialog(
        if (initial == null) "Nova perícia" else "Editar ${initial.name}", onDismiss,
        {
            val fixed = s.copy(name = s.name.trim())
            ctl.update { ch -> ch.copy(skills = if (initial == null) ch.skills + fixed else ch.skills.map { if (it.id == fixed.id) fixed else it }) }
            onDismiss()
        },
        saveEnabled = s.name.isNotBlank(),
        onDelete = initial?.let { { ctl.update { ch -> ch.copy(skills = ch.skills.filterNot { it.id == initial.id }) }; onDismiss() } },
        deleteWhat = "a perícia “${initial?.name}”",
    ) {
        CodexTextField(s.name, { s = s.copy(name = it) }, "Nome")
        DropdownField("Atributo relacionado", listOf<Attribute?>(null) + c.attributes, c.attributes.firstOrNull { it.id == s.attributeId }, { it?.name ?: "Nenhum" }, { s = s.copy(attributeId = it?.id) })
        Text("Proficiência", style = MaterialTheme.typography.bodySmall, color = col.muted)
        ChoiceChips(Proficiency.entries, s.proficiency, { it.label }, { s = s.copy(proficiency = it) })
        SwitchRow("Treinada", s.trained, { s = s.copy(trained = it) }, description = if (c.rules.trainedBonus != 0) "Soma +${c.rules.trainedBonus} (configurável em Outras informações)" else "Marcação livre; bônus de treino configurável em Outras informações.")
        FieldRow {
            IntField(s.base, { s = s.copy(base = it) }, "Valor base", Modifier.weight(1f))
            IntField(s.bonus, { s = s.copy(bonus = it) }, "Bônus", Modifier.weight(1f))
            IntField(s.penalty, { s = s.copy(penalty = it) }, "Penalidade", Modifier.weight(1f))
        }
        Text("Resultado total: ${SheetMath.skillTotal(s, c).let { if (c.rules.rollUnder) "$it" else SheetMath.signed(it) }}", style = MaterialTheme.typography.titleSmall, color = col.gold)
        CodexTextField(s.description, { s = s.copy(description = it) }, "Descrição", singleLine = false, minLines = 2)
    }
}
