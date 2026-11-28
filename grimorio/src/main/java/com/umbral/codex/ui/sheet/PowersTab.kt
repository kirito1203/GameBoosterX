package com.umbral.codex.ui.sheet

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.umbral.codex.core.Dice
import com.umbral.codex.core.Ids
import com.umbral.codex.model.Ability
import com.umbral.codex.model.AbilityCategories
import com.umbral.codex.model.Character
import com.umbral.codex.model.Trait
import com.umbral.codex.model.TraitTypes
import com.umbral.codex.ui.components.ChoiceChips
import com.umbral.codex.ui.components.CodexTextField
import com.umbral.codex.ui.components.DetailLine
import com.umbral.codex.ui.components.DropdownField
import com.umbral.codex.ui.components.EditorDialog
import com.umbral.codex.ui.components.EmptyState
import com.umbral.codex.ui.components.FieldRow
import com.umbral.codex.ui.components.FilterPill
import com.umbral.codex.ui.components.GhostButton
import com.umbral.codex.ui.components.IconPicker
import com.umbral.codex.ui.components.IntField
import com.umbral.codex.ui.components.OrnatePanel
import com.umbral.codex.ui.components.RoundIconButton
import com.umbral.codex.ui.components.SectionTitle
import com.umbral.codex.ui.components.SuggestField
import com.umbral.codex.ui.components.SwitchRow
import com.umbral.codex.ui.components.Tag
import com.umbral.codex.ui.components.rememberImagePicker
import com.umbral.codex.ui.theme.Codex

val TraitSuggestions = listOf(
    "Visão noturna", "Regeneração", "Imortalidade", "Resistência a venenos", "Resistência a magia", "Sentidos aguçados",
    "Imunidade a doenças", "Transformação", "Resistência sobrenatural", "Afinidade elemental", "Aura mágica", "Sangue ancestral",
)

fun Character.allAbilityCategories(): List<String> =
    (AbilityCategories.defaults + abilityCategories + abilities.map { it.category }).filter { it.isNotBlank() }.distinct()

@Composable
fun PowersTab(c: Character, ctl: SheetController) {
    var segment by rememberSaveable { mutableStateOf(0) }
    TabColumn {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterPill("Habilidades e poderes (${c.abilities.size})", segment == 0, { segment = 0 })
            FilterPill("Características (${c.traits.size})", segment == 1, { segment = 1 })
        }
        if (segment == 0) AbilitiesSection(c, ctl) else TraitsSection(c, ctl)
    }
}

// ---------------- Habilidades ----------------

@Composable
private fun AbilitiesSection(c: Character, ctl: SheetController) {
    val col = Codex.colors
    var onlyFav by rememberSaveable { mutableStateOf(false) }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<Ability?>(null) }
    var creating by remember { mutableStateOf(false) }
    val list = c.abilities.filter {
        (!onlyFav || it.favorite) && (category == null || it.category == category) && (query.isBlank() || it.name.contains(query.trim(), true))
    }
    val used = c.abilities.map { it.category }.distinct()

    OrnatePanel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Talentos, habilidades e poderes", style = MaterialTheme.typography.titleMedium, color = col.text)
                Text("Ativas, passivas, técnicas, raciais, transformações, sobrenaturais, demoníacos, divinos e de combate.", style = MaterialTheme.typography.bodySmall, color = col.muted)
            }
            RoundIconButton(Icons.Filled.Add, "Nova habilidade", { creating = true }, tint = col.accent, filled = true)
        }
        Spacer(Modifier.height(8.dp))
        CodexTextField(query, { query = it }, "Pesquisar")
        Spacer(Modifier.height(8.dp))
        FieldRow {
            DropdownField("Categoria", listOf<String?>(null) + used, category, { it ?: "Todas" }, { category = it }, Modifier.weight(1f))
        }
        SwitchRow("Somente favoritas", onlyFav, { onlyFav = it })
    }
    if (list.isEmpty()) EmptyState(if (c.abilities.isEmpty()) "Nenhuma habilidade" else "Nada encontrado", "Cadastre poderes com custo, recarga, requisitos e dados.")
    list.groupBy { it.category }.toSortedMap(compareBy { cat -> c.allAbilityCategories().indexOf(cat).let { if (it < 0) 999 else it } }).forEach { (cat, items) ->
        SectionTitle("$cat (${items.size})")
        items.forEach { a -> AbilityCard(a, ctl) { editing = a } }
    }
    GhostButton("Adicionar habilidade ou poder", { creating = true }, Modifier.fillMaxWidth(), icon = Icons.Filled.Add)
    if (creating) AbilityDialog(null, c, ctl) { creating = false }
    editing?.let { a -> AbilityDialog(c.abilities.firstOrNull { it.id == a.id } ?: a, c, ctl) { editing = null } }
}

@Composable
private fun AbilityCard(a: Ability, ctl: SheetController, onEdit: () -> Unit) {
    val col = Codex.colors
    var expanded by rememberSaveable(a.id) { mutableStateOf(false) }
    OrnatePanel(Modifier.fillMaxWidth().animateContentSize(), ornaments = expanded, padding = PaddingValues(12.dp), onClick = { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EntityIcon(a.imageId, a.icon, col.accent)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(a.name, style = MaterialTheme.typography.titleMedium, color = col.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
                    if (a.cost.isNotBlank()) Tag(a.cost, col.accent)
                    if (a.cooldown.isNotBlank()) Tag("⟳ ${a.cooldown}", col.muted)
                    if (a.levelRequired > 0) Tag("Nv. ${a.levelRequired}", col.gold)
                }
            }
            RoundIconButton(
                if (a.favorite) Icons.Filled.Star else Icons.Filled.StarBorder, if (a.favorite) "Desfavoritar" else "Favoritar",
                { ctl.update { ch -> ch.copy(abilities = ch.abilities.map { if (it.id == a.id) it.copy(favorite = !it.favorite) else it }) } },
                tint = if (a.favorite) col.gold else col.faint,
            )
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = col.muted)
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            if (a.description.isNotBlank()) Text(a.description, style = MaterialTheme.typography.bodyMedium, color = col.text)
            DetailLine("Efeito", a.effect)
            DetailLine("Custo", a.cost)
            DetailLine("Recarga", a.cooldown)
            DetailLine("Requisitos", a.requirements)
            DetailLine("Nível necessário", if (a.levelRequired > 0) "${a.levelRequired}" else "")
            DetailLine("Bônus", a.bonus)
            DetailLine("Penalidades", a.penalty)
            DetailLine("Dados", a.dice)
            DetailLine("Observações", a.notes)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (a.dice.isNotBlank()) GhostButton("Usar (${a.dice})", { ctl.roll(a.name, a.dice) }, Modifier.weight(1f), icon = Icons.Filled.Bolt)
                else GhostButton("Usar", { ctl.vm.toast("${a.name} usada") }, Modifier.weight(1f), icon = Icons.Filled.Bolt)
                RoundIconButton(Icons.Filled.Edit, "Editar", onEdit, tint = col.muted, filled = true)
            }
        }
    }
}

@Composable
private fun AbilityDialog(initial: Ability?, c: Character, ctl: SheetController, onDismiss: () -> Unit) {
    var a by remember { mutableStateOf(initial ?: Ability(Ids.new(), "")) }
    val col = Codex.colors
    val pick = rememberImagePicker { id -> if (id != null) a = a.copy(imageId = id) }
    val diceError = a.dice.isNotBlank() && Dice.tryParse(a.dice) == null
    EditorDialog(
        if (initial == null) "Nova habilidade" else "Editar habilidade", onDismiss,
        {
            val fixed = a.copy(name = a.name.trim(), category = a.category.trim().ifBlank { AbilityCategories.ACTIVE })
            ctl.update { ch ->
                val cats = if (fixed.category !in AbilityCategories.defaults && fixed.category !in ch.abilityCategories) ch.abilityCategories + fixed.category else ch.abilityCategories
                ch.copy(
                    abilities = if (initial == null) ch.abilities + fixed else ch.abilities.map { if (it.id == fixed.id) fixed else it },
                    abilityCategories = cats,
                )
            }
            onDismiss()
        },
        saveEnabled = a.name.isNotBlank() && !diceError,
        onDelete = initial?.let { { ctl.update { ch -> ch.copy(abilities = ch.abilities.filterNot { it.id == initial.id }) }; onDismiss() } },
        deleteWhat = "“${initial?.name}”",
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EntityIcon(a.imageId, a.icon, col.accent, 56.dp)
            Spacer(Modifier.width(10.dp))
            GhostButton("Imagem", pick, Modifier.weight(1f), icon = Icons.Filled.AddPhotoAlternate)
            if (a.imageId != null) RoundIconButton(Icons.Filled.HideImage, "Remover imagem", { a = a.copy(imageId = null) })
        }
        CodexTextField(a.name, { a = a.copy(name = it) }, "Nome")
        SuggestField(a.category, { a = a.copy(category = it) }, "Categoria (escolha ou crie)", c.allAbilityCategories())
        CodexTextField(a.description, { a = a.copy(description = it) }, "Descrição", singleLine = false, minLines = 3)
        CodexTextField(a.effect, { a = a.copy(effect = it) }, "Efeito", singleLine = false, minLines = 2)
        FieldRow {
            CodexTextField(a.cost, { a = a.copy(cost = it) }, "Custo", Modifier.weight(1f), placeholder = "2 Mana")
            CodexTextField(a.cooldown, { a = a.copy(cooldown = it) }, "Recarga", Modifier.weight(1f), placeholder = "1 por cena")
        }
        FieldRow {
            IntField(a.levelRequired, { a = a.copy(levelRequired = it) }, "Nível necessário", Modifier.weight(1f), allowNegative = false, min = 0)
            CodexTextField(a.dice, { a = a.copy(dice = it) }, "Dados", Modifier.weight(1f), placeholder = "1d10+2", isError = diceError)
        }
        CodexTextField(a.requirements, { a = a.copy(requirements = it) }, "Requisitos")
        FieldRow {
            CodexTextField(a.bonus, { a = a.copy(bonus = it) }, "Bônus", Modifier.weight(1f))
            CodexTextField(a.penalty, { a = a.copy(penalty = it) }, "Penalidades", Modifier.weight(1f))
        }
        CodexTextField(a.notes, { a = a.copy(notes = it) }, "Observações", singleLine = false, minLines = 2)
        SwitchRow("Favorita", a.favorite, { a = a.copy(favorite = it) })
        IconPicker(a.icon, { a = a.copy(icon = it) })
    }
}

// ---------------- Características ----------------

@Composable
private fun TraitsSection(c: Character, ctl: SheetController) {
    val col = Codex.colors
    var editing by remember { mutableStateOf<Trait?>(null) }
    var creating by remember { mutableStateOf(false) }
    var type by rememberSaveable { mutableStateOf<String?>(null) }
    val types = c.traits.map { it.type }.distinct()
    OrnatePanel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Características naturais e sobrenaturais", style = MaterialTheme.typography.titleMedium, color = col.text)
                Text("Visão noturna, regeneração, resistências, maldições, sangue ancestral…", style = MaterialTheme.typography.bodySmall, color = col.muted)
            }
            RoundIconButton(Icons.Filled.Add, "Nova característica", { creating = true }, tint = col.accent, filled = true)
        }
        if (types.size > 1) { Spacer(Modifier.height(8.dp)); ChoiceChips(listOf<String?>(null) + types, type, { it ?: "Todas" }, { type = it }) }
    }
    val list = c.traits.filter { type == null || it.type == type }
    if (list.isEmpty()) EmptyState("Nenhuma característica", "Registre traços raciais, dons e maldições do personagem.")
    list.forEach { t ->
        OrnatePanel(Modifier.fillMaxWidth(), ornaments = false, padding = PaddingValues(14.dp), onClick = { editing = t }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = col.text)
                Tag(t.type, if (t.type == "Maldição") col.danger else if (t.type == "Sobrenatural") col.accent else col.gold)
            }
            if (t.description.isNotBlank()) Text(t.description, style = MaterialTheme.typography.bodyMedium, color = col.text, modifier = Modifier.padding(top = 4.dp))
            DetailLine("Efeito", t.effect)
            DetailLine("Requisitos", t.requirements)
            DetailLine("Limitações", t.limitations)
            DetailLine("Observações", t.notes)
        }
    }
    GhostButton("Adicionar característica", { creating = true }, Modifier.fillMaxWidth(), icon = Icons.Filled.Add)
    if (creating) TraitDialog(null, c, ctl) { creating = false }
    editing?.let { t -> TraitDialog(c.traits.firstOrNull { it.id == t.id } ?: t, c, ctl) { editing = null } }
}

@Composable
private fun TraitDialog(initial: Trait?, c: Character, ctl: SheetController, onDismiss: () -> Unit) {
    var t by remember { mutableStateOf(initial ?: Trait(Ids.new(), "")) }
    EditorDialog(
        if (initial == null) "Nova característica" else "Editar característica", onDismiss,
        {
            val fixed = t.copy(name = t.name.trim(), type = t.type.trim().ifBlank { "Natural" })
            ctl.update { ch -> ch.copy(traits = if (initial == null) ch.traits + fixed else ch.traits.map { if (it.id == fixed.id) fixed else it }) }
            onDismiss()
        },
        saveEnabled = t.name.isNotBlank(),
        onDelete = initial?.let { { ctl.update { ch -> ch.copy(traits = ch.traits.filterNot { it.id == initial.id }) }; onDismiss() } },
        deleteWhat = "“${initial?.name}”",
    ) {
        SuggestField(t.name, { t = t.copy(name = it) }, "Nome", if (initial == null) TraitSuggestions.filterNot { s -> c.traits.any { it.name == s } } else emptyList())
        SuggestField(t.type, { t = t.copy(type = it) }, "Tipo", (TraitTypes.defaults + c.traits.map { it.type }).distinct())
        CodexTextField(t.description, { t = t.copy(description = it) }, "Descrição", singleLine = false, minLines = 3)
        CodexTextField(t.effect, { t = t.copy(effect = it) }, "Efeito", singleLine = false, minLines = 2)
        CodexTextField(t.requirements, { t = t.copy(requirements = it) }, "Requisitos")
        CodexTextField(t.limitations, { t = t.copy(limitations = it) }, "Limitações", singleLine = false, minLines = 2)
        CodexTextField(t.notes, { t = t.copy(notes = it) }, "Observações", singleLine = false, minLines = 2)
    }
}
