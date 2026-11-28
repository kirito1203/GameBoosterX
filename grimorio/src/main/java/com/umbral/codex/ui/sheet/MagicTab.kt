package com.umbral.codex.ui.sheet

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Casino
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.umbral.codex.core.Dice
import com.umbral.codex.core.Ids
import com.umbral.codex.model.Character
import com.umbral.codex.model.Resource
import com.umbral.codex.model.Spell
import com.umbral.codex.ui.components.ChoiceChips
import com.umbral.codex.ui.components.CodexIcons
import com.umbral.codex.ui.components.CodexTextField
import com.umbral.codex.ui.components.DetailLine
import com.umbral.codex.ui.components.DropdownField
import com.umbral.codex.ui.components.EditorDialog
import com.umbral.codex.ui.components.EmptyState
import com.umbral.codex.ui.components.FieldRow
import com.umbral.codex.ui.components.GhostButton
import com.umbral.codex.ui.components.GlyphMedallion
import com.umbral.codex.ui.components.Hint
import com.umbral.codex.ui.components.IconPicker
import com.umbral.codex.ui.components.IntField
import com.umbral.codex.ui.components.OrnatePanel
import com.umbral.codex.ui.components.RoundIconButton
import com.umbral.codex.ui.components.SuggestField
import com.umbral.codex.ui.components.SwitchRow
import com.umbral.codex.ui.components.Tag
import com.umbral.codex.ui.components.rememberImage
import com.umbral.codex.ui.components.rememberImagePicker
import com.umbral.codex.ui.theme.Codex

val SpellSchools = listOf("Abjuração", "Adivinhação", "Conjuração", "Encantamento", "Evocação", "Ilusão", "Necromancia", "Transmutação", "Hemomancia", "Elemental")
val SpellTypes = listOf("Arcana", "Divina", "Natureza", "Ritual", "Sobrenatural", "Demoníaca", "Psíquica", "Profana")

/** Imagem do usuário ou ícone temático num medalhão. */
@Composable
fun EntityIcon(imageId: String?, icon: String, color: Color, size: Dp = 46.dp) {
    val img = rememberImage(imageId)
    if (img != null) {
        Image(img, null, Modifier.size(size).clip(RoundedCornerShape(10.dp)).border(1.dp, Codex.colors.gold.copy(alpha = 0.5f), RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
    } else GlyphMedallion(CodexIcons.of(icon), color, size = size)
}

private enum class SpellFilter(val label: String) { ALL("Todas"), FAV("Favoritas"), AVAILABLE("Disponíveis"), UNAVAILABLE("Indisponíveis") }

/** Encontra o recurso que paga um custo (ex.: "Mana", "Energia"). */
fun Character.findResource(vararg names: String): Resource? =
    names.firstNotNullOfOrNull { n -> resources.firstOrNull { it.name.equals(n, true) } }
        ?: names.firstNotNullOfOrNull { n -> resources.firstOrNull { it.name.contains(n, true) } }

@Composable
fun MagicTab(c: Character, ctl: SheetController) {
    val col = Codex.colors
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(SpellFilter.ALL) }
    var level by rememberSaveable { mutableStateOf<Int?>(null) }
    var type by rememberSaveable { mutableStateOf<String?>(null) }
    var school by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Spell?>(null) }
    var creating by remember { mutableStateOf(false) }

    val visible = c.spells.filter { s ->
        (query.isBlank() || s.name.contains(query.trim(), true)) &&
            when (filter) { SpellFilter.ALL -> true; SpellFilter.FAV -> s.favorite; SpellFilter.AVAILABLE -> s.available; SpellFilter.UNAVAILABLE -> !s.available } &&
            (level == null || s.level == level) && (type == null || s.type == type) && (school == null || s.school == school)
    }.sortedWith(compareBy({ it.level }, { it.name.lowercase() }))
    val mana = c.findResource("Mana")

    TabColumn {
        OrnatePanel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Grimório", style = MaterialTheme.typography.titleLarge, color = col.text)
                    Text("${c.spells.size} magias${mana?.let { " · ${it.name} ${it.current}/${it.max}" } ?: ""}", style = MaterialTheme.typography.bodySmall, color = col.muted)
                }
                RoundIconButton(Icons.Filled.Add, "Nova magia", { creating = true }, tint = col.accent, filled = true)
            }
            Spacer(Modifier.height(8.dp))
            CodexTextField(query, { query = it }, "Pesquisar magia pelo nome")
            Spacer(Modifier.height(8.dp))
            ChoiceChips(SpellFilter.entries, filter, { it.label }, { filter = it })
            Spacer(Modifier.height(8.dp))
            FieldRow {
                DropdownField("Nível", listOf<Int?>(null) + c.spells.map { it.level }.distinct().sorted(), level, { it?.let { l -> if (l == 0) "Truque (0)" else "Nível $l" } ?: "Todos" }, { level = it }, Modifier.weight(1f))
                DropdownField("Tipo", listOf<String?>(null) + c.spells.map { it.type }.filter { it.isNotBlank() }.distinct().sorted(), type, { it ?: "Todos" }, { type = it }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            DropdownField("Escola", listOf<String?>(null) + c.spells.map { it.school }.filter { it.isNotBlank() }.distinct().sorted(), school, { it ?: "Todas" }, { school = it })
        }
        if (visible.isEmpty()) EmptyState(
            if (c.spells.isEmpty()) "Grimório vazio" else "Nenhuma magia encontrada",
            if (c.spells.isEmpty()) "Crie magias com custo, dano rolável, alcance e efeitos." else "Ajuste a pesquisa ou os filtros.",
        )
        visible.forEach { s -> SpellCard(s, c, ctl, onEdit = { editing = s }) }
        GhostButton("Criar magia", { creating = true }, Modifier.fillMaxWidth(), icon = Icons.Filled.Add)
        Hint("Para habilidades, técnicas e poderes que não são magias, use a aba Poderes.")
    }
    if (creating) SpellDialog(null, ctl) { creating = false }
    editing?.let { s -> SpellDialog(c.spells.firstOrNull { it.id == s.id } ?: s, ctl) { editing = null } }
}

@Composable
private fun SpellCard(s: Spell, c: Character, ctl: SheetController, onEdit: () -> Unit) {
    val col = Codex.colors
    var expanded by rememberSaveable(s.id) { mutableStateOf(false) }
    val accent = if (s.available) col.accent else col.faint
    OrnatePanel(Modifier.fillMaxWidth().animateContentSize(), accent = accent, ornaments = expanded, padding = PaddingValues(12.dp), onClick = { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EntityIcon(s.imageId, s.icon, accent)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(s.name, style = MaterialTheme.typography.titleMedium, color = if (s.available) col.text else col.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(if (s.level == 0) "Truque" else "Nível ${s.level}", s.school, s.type).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = col.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    if (s.manaCost > 0) Tag("${s.manaCost} mana", Color(0xFF5B7CF0))
                    if (s.energyCost > 0) Tag("${s.energyCost} energia", col.warning)
                    if (s.damage.isNotBlank()) Tag(s.damage, col.danger)
                    if (!s.available) Tag("Indisponível", col.faint)
                }
            }
            RoundIconButton(
                if (s.favorite) Icons.Filled.Star else Icons.Filled.StarBorder, if (s.favorite) "Desfavoritar" else "Favoritar",
                { ctl.update { ch -> ch.copy(spells = ch.spells.map { if (it.id == s.id) it.copy(favorite = !it.favorite) else it }) } },
                tint = if (s.favorite) col.gold else col.faint,
            )
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = col.muted)
        }
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            if (s.description.isNotBlank()) Text(s.description, style = MaterialTheme.typography.bodyMedium, color = col.text)
            Spacer(Modifier.height(6.dp))
            DetailLine("Tempo de conjuração", s.castTime)
            DetailLine("Alcance", s.range)
            DetailLine("Duração", s.duration)
            DetailLine("Dano", s.damage)
            DetailLine("Efeito", s.effect)
            DetailLine("Requisitos", s.requirements)
            DetailLine("Observações", s.notes)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Conjurar", { cast(s, c, ctl) }, Modifier.weight(1f), icon = Icons.Filled.AutoFixHigh, enabled = s.available)
                if (s.damage.isNotBlank()) GhostButton("Dano", { ctl.roll("${s.name} — dano", s.damage) }, Modifier.weight(1f), icon = Icons.Filled.Casino, color = col.danger)
                RoundIconButton(Icons.Filled.Edit, "Editar magia", onEdit, tint = col.muted, filled = true)
            }
        }
    }
}

/** Conjura: desconta mana/energia dos recursos correspondentes e rola o dano, se houver. */
private fun cast(s: Spell, c: Character, ctl: SheetController) {
    val mana = if (s.manaCost > 0) c.findResource("Mana", "Pontos de magia") else null
    val energy = if (s.energyCost > 0) c.findResource("Energia", "Energia espiritual", "Vigor") else null
    if (s.manaCost > 0 && mana == null) { ctl.vm.toast("Crie um recurso “Mana” para pagar o custo"); return }
    if (s.energyCost > 0 && energy == null) { ctl.vm.toast("Crie um recurso “Energia” para pagar o custo"); return }
    if (mana != null && mana.current - s.manaCost < mana.min) { ctl.vm.toast("${mana.name} insuficiente (${mana.current}/${s.manaCost})"); return }
    if (energy != null && energy.current - s.energyCost < energy.min) { ctl.vm.toast("${energy.name} insuficiente (${energy.current}/${s.energyCost})"); return }
    ctl.update { ch ->
        ch.copy(resources = ch.resources.map {
            when (it.id) {
                mana?.id -> it.copy(current = it.current - s.manaCost)
                energy?.id -> it.copy(current = it.current - s.energyCost)
                else -> it
            }
        })
    }
    val paid = listOfNotNull(mana?.let { "−${s.manaCost} ${it.name}" }, energy?.let { "−${s.energyCost} ${it.name}" }).joinToString(", ")
    if (s.damage.isNotBlank() && Dice.tryParse(s.damage) != null) ctl.roll("${s.name} conjurada${if (paid.isNotBlank()) " ($paid)" else ""}", s.damage)
    else ctl.vm.toast("${s.name} conjurada${if (paid.isNotBlank()) " · $paid" else ""}")
}

@Composable
private fun SpellDialog(initial: Spell?, ctl: SheetController, onDismiss: () -> Unit) {
    var s by remember { mutableStateOf(initial ?: Spell(Ids.new(), "")) }
    val col = Codex.colors
    val pick = rememberImagePicker { id -> if (id != null) s = s.copy(imageId = id) }
    val damageError = s.damage.isNotBlank() && Dice.tryParse(s.damage) == null
    EditorDialog(
        if (initial == null) "Nova magia" else "Editar magia", onDismiss,
        {
            val fixed = s.copy(name = s.name.trim(), damage = s.damage.trim())
            ctl.update { ch -> ch.copy(spells = if (initial == null) ch.spells + fixed else ch.spells.map { if (it.id == fixed.id) fixed else it }) }
            onDismiss()
        },
        saveEnabled = s.name.isNotBlank() && !damageError,
        onDelete = initial?.let { { ctl.update { ch -> ch.copy(spells = ch.spells.filterNot { it.id == initial.id }) }; onDismiss() } },
        deleteWhat = "a magia “${initial?.name}”",
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EntityIcon(s.imageId, s.icon, col.accent, 56.dp)
            Spacer(Modifier.width(10.dp))
            GhostButton("Imagem", pick, Modifier.weight(1f), icon = Icons.Filled.AddPhotoAlternate)
            if (s.imageId != null) { Spacer(Modifier.width(6.dp)); RoundIconButton(Icons.Filled.HideImage, "Remover imagem", { s = s.copy(imageId = null) }) }
        }
        CodexTextField(s.name, { s = s.copy(name = it) }, "Nome")
        CodexTextField(s.description, { s = s.copy(description = it) }, "Descrição", singleLine = false, minLines = 3)
        FieldRow {
            IntField(s.level, { s = s.copy(level = it) }, "Nível", Modifier.weight(1f), allowNegative = false, min = 0)
            IntField(s.manaCost, { s = s.copy(manaCost = it) }, "Custo de mana", Modifier.weight(1f), allowNegative = false, min = 0)
            IntField(s.energyCost, { s = s.copy(energyCost = it) }, "Custo de energia", Modifier.weight(1f), allowNegative = false, min = 0)
        }
        SuggestField(s.school, { s = s.copy(school = it) }, "Escola de magia", SpellSchools)
        SuggestField(s.type, { s = s.copy(type = it) }, "Tipo de magia", SpellTypes)
        FieldRow {
            CodexTextField(s.castTime, { s = s.copy(castTime = it) }, "Conjuração", Modifier.weight(1f), placeholder = "1 ação")
            CodexTextField(s.range, { s = s.copy(range = it) }, "Alcance", Modifier.weight(1f), placeholder = "18 m")
        }
        FieldRow {
            CodexTextField(s.duration, { s = s.copy(duration = it) }, "Duração", Modifier.weight(1f))
            CodexTextField(s.damage, { s = s.copy(damage = it) }, "Dano (dados)", Modifier.weight(1f), placeholder = "2d8+3", isError = damageError)
        }
        if (damageError) Text("Use o formato de dados, ex.: 2d8+3", color = col.danger, style = MaterialTheme.typography.bodySmall)
        CodexTextField(s.effect, { s = s.copy(effect = it) }, "Efeito", singleLine = false, minLines = 2)
        CodexTextField(s.requirements, { s = s.copy(requirements = it) }, "Requisitos")
        CodexTextField(s.notes, { s = s.copy(notes = it) }, "Observações", singleLine = false, minLines = 2)
        SwitchRow("Disponível", s.available, { s = s.copy(available = it) }, description = "Desmarque para magias ainda não aprendidas ou bloqueadas.")
        SwitchRow("Favorita", s.favorite, { s = s.copy(favorite = it) })
        IconPicker(s.icon, { s = s.copy(icon = it) })
    }
}
