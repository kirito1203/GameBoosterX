package com.umbral.codex.ui.sheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umbral.codex.core.Ids
import com.umbral.codex.core.ResourceColors
import com.umbral.codex.core.SheetMath
import com.umbral.codex.model.Catalog
import com.umbral.codex.model.Character
import com.umbral.codex.model.ProficiencyRule
import com.umbral.codex.model.Resource
import com.umbral.codex.model.ResourceDisplay
import com.umbral.codex.model.XpCurve
import com.umbral.codex.ui.LocalRepository
import com.umbral.codex.ui.components.ArcaneButton
import com.umbral.codex.ui.components.AutoGrid
import com.umbral.codex.ui.components.ChoiceChips
import com.umbral.codex.ui.components.CodexTextField
import com.umbral.codex.ui.components.ColorPicker
import com.umbral.codex.ui.components.DropdownField
import com.umbral.codex.ui.components.EditorDialog
import com.umbral.codex.ui.components.FieldRow
import com.umbral.codex.ui.components.GhostButton
import com.umbral.codex.ui.components.GlowBar
import com.umbral.codex.ui.components.Hint
import com.umbral.codex.ui.components.IntField
import com.umbral.codex.ui.components.LongField
import com.umbral.codex.ui.components.OrnatePanel
import com.umbral.codex.ui.components.Portrait
import com.umbral.codex.ui.components.RoundIconButton
import com.umbral.codex.ui.components.SectionTitle
import com.umbral.codex.ui.components.StatBadge
import com.umbral.codex.ui.components.SuggestField
import com.umbral.codex.ui.components.rememberImagePicker
import com.umbral.codex.ui.theme.Codex
import com.umbral.codex.ui.theme.TitleFont

val Alignments = listOf(
    "Leal e Bom", "Neutro e Bom", "Caótico e Bom", "Leal e Neutro", "Neutro",
    "Caótico e Neutro", "Leal e Mau", "Neutro e Mau", "Caótico e Mau",
)

/** Coluna rolável padrão das abas da ficha. */
@Composable
fun TabColumn(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        content()
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
fun SheetTab(c: Character, ctl: SheetController) {
    val repo = LocalRepository.current
    val catalogs by repo.catalogs.collectAsStateWithLifecycle()
    val catalog = catalogs.firstOrNull { it.id == c.catalogId }
    TabColumn {
        IdentityPanel(c, ctl, catalog, catalogs)
        ProgressionPanel(c, ctl)
        ResourcesPanel(c, ctl)
        AttributeSummary(c, ctl)
    }
}

@Composable
private fun IdentityPanel(c: Character, ctl: SheetController, catalog: Catalog?, catalogs: List<Catalog>) {
    val col = Codex.colors
    val pick = rememberImagePicker { id -> if (id != null) ctl.update { it.copy(portraitId = id) } else ctl.vm.toast("Não foi possível carregar a imagem") }
    OrnatePanel(Modifier.fillMaxWidth(), accent = catalog?.let { Color(it.accent) } ?: col.accent, glow = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                Portrait(c.portraitId, c.name, Modifier.size(104.dp).clickable(onClick = pick), RoundedCornerShape(18.dp))
                RoundIconButton(Icons.Filled.AddPhotoAlternate, "Trocar retrato", pick, Modifier.align(Alignment.BottomEnd), tint = col.gold, size = 36.dp, filled = true)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                CodexTextField(c.name, { v -> ctl.update { it.copy(name = v) } }, "Nome do personagem", capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words)
                Spacer(Modifier.height(6.dp))
                CodexTextField(c.title, { v -> ctl.update { it.copy(title = v) } }, c.label("title", "Título"))
            }
        }
        if (c.portraitId != null) {
            Text("Remover retrato", Modifier.clip(RoundedCornerShape(6.dp)).clickable { ctl.update { it.copy(portraitId = null) } }.padding(6.dp), color = col.faint, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(10.dp))
        SectionTitle("Informações básicas")
        Spacer(Modifier.height(6.dp))
        DropdownField(
            "Universo de RPG", listOf<Catalog?>(null) + catalogs.sortedBy { it.order }, catalog, { it?.name ?: "Nenhum" },
            { cat -> ctl.update { it.copy(catalogId = cat?.id) } },
        )
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FieldRow {
                CodexTextField(c.age, { v -> ctl.update { it.copy(age = v) } }, c.label("age", "Idade"), Modifier.weight(1f))
                CodexTextField(c.gender, { v -> ctl.update { it.copy(gender = v) } }, c.label("gender", "Gênero"), Modifier.weight(1f))
            }
            SuggestField(c.race, { v -> ctl.update { it.copy(race = v) } }, c.label("race", "Raça"), catalog?.races?.map { it.name }.orEmpty())
            SuggestField(c.charClass, { v -> ctl.update { it.copy(charClass = v) } }, c.label("charClass", "Classe"), catalog?.classes?.map { it.name }.orEmpty())
            FieldRow {
                CodexTextField(c.subclass, { v -> ctl.update { it.copy(subclass = v) } }, c.label("subclass", "Subclasse"), Modifier.weight(1f))
                CodexTextField(c.background, { v -> ctl.update { it.copy(background = v) } }, c.label("background", "Antecedente"), Modifier.weight(1f))
            }
            SuggestField(c.alignment, { v -> ctl.update { it.copy(alignment = v) } }, c.label("alignment", "Alinhamento"), Alignments)
            CodexTextField(c.faction, { v -> ctl.update { it.copy(faction = v) } }, c.label("faction", "Organização / facção"))
        }
    }
}

@Composable
private fun ProgressionPanel(c: Character, ctl: SheetController) {
    val col = Codex.colors
    var addXp by remember { mutableStateOf(false) }
    val canLevel = SheetMath.canLevelUp(c)
    OrnatePanel(Modifier.fillMaxWidth()) {
        SectionTitle("Progressão")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("NÍVEL", style = MaterialTheme.typography.labelSmall, color = col.gold)
                Text("${c.level}", style = MaterialTheme.typography.displaySmall, color = col.text, fontFamily = TitleFont)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row {
                    Text(c.label("xp", "Experiência"), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = col.muted)
                    Text("${c.xp} / ${c.xpToNext}", style = MaterialTheme.typography.labelLarge, color = col.text)
                }
                Spacer(Modifier.height(6.dp))
                GlowBar(SheetMath.xpProgress(c), col.gold, height = 12.dp)
                if (canLevel) Text("✦ Pronto para subir de nível!", style = MaterialTheme.typography.labelMedium, color = col.gold, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("Experiência", { addXp = true }, Modifier.weight(1f), icon = Icons.Filled.Add, color = col.gold)
            if (canLevel) ArcaneButton("Subir de nível", { levelUp(c, ctl) }, Modifier.weight(1f), icon = Icons.Filled.KeyboardDoubleArrowUp)
            else GhostButton("Subir de nível", { levelUp(c, ctl) }, Modifier.weight(1f), icon = Icons.Filled.KeyboardDoubleArrowUp, enabled = c.level < c.rules.maxLevel)
        }
        Spacer(Modifier.height(12.dp))
        AutoGrid(listOf("level", "xp", "next", "prof"), minItemWidth = 130.dp) { key ->
            when (key) {
                "level" -> IntField(c.level, { v -> ctl.update { SheetMath.syncDerived(it.copy(level = v)) } }, "Nível", min = 1, max = 999, allowNegative = false)
                "xp" -> LongField(c.xp, { v -> ctl.update { it.copy(xp = v) } }, "XP atual")
                "next" -> LongField(c.xpToNext, { v -> ctl.update { it.copy(xpToNext = v, rules = if (it.rules.xpCurve != XpCurve.MANUAL) it.rules.copy(xpCurve = XpCurve.MANUAL) else it.rules) } }, "XP p/ próximo")
                else -> if (c.rules.proficiencyRule == ProficiencyRule.MANUAL)
                    IntField(c.proficiencyBonus, { v -> ctl.update { it.copy(proficiencyBonus = v) } }, c.label("proficiency", "Proficiência"))
                else StatBadge(c.label("proficiency", "Proficiência") + " (auto)", SheetMath.signed(SheetMath.proficiency(c)), Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.height(12.dp))
        SectionTitle("Combate")
        AutoGrid(listOf("ca", "init", "speed"), minItemWidth = 100.dp) { key ->
            when (key) {
                "ca" -> IntField(c.armorClass, { v -> ctl.update { it.copy(armorClass = v) } }, c.label("armorClass", "Classe de armadura"))
                "init" -> Row(verticalAlignment = Alignment.CenterVertically) {
                    IntField(c.initiative, { v -> ctl.update { it.copy(initiative = v) } }, c.label("initiative", "Iniciativa"), Modifier.weight(1f))
                    RoundIconButton(Icons.Filled.Casino, "Rolar iniciativa", { ctl.roll("Iniciativa", "1d20${if (c.initiative >= 0) "+" else ""}${c.initiative}") }, tint = col.accent, size = 40.dp)
                }
                else -> CodexTextField(c.speed, { v -> ctl.update { it.copy(speed = v) } }, c.label("speed", "Deslocamento"))
            }
        }
    }
    if (addXp) XpDialog(c, ctl) { addXp = false }
}

private fun levelUp(c: Character, ctl: SheetController) {
    if (c.level >= c.rules.maxLevel) { ctl.vm.toast("Nível máximo (${c.rules.maxLevel}) alcançado"); return }
    ctl.update { SheetMath.levelUp(it) }
    ctl.vm.toast("${c.name.ifBlank { "Personagem" }} alcançou o nível ${c.level + 1}!")
}

@Composable
private fun XpDialog(c: Character, ctl: SheetController, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf(0L) }
    var remove by remember { mutableStateOf(false) }
    EditorDialog("Experiência", onDismiss, {
        val delta = if (remove) -amount else amount
        ctl.update { SheetMath.addXp(it, delta) }
        ctl.vm.toast(if (remove) "−$amount XP" else "+$amount XP")
        onDismiss()
    }, saveEnabled = amount > 0, saveLabel = if (remove) "Remover" else "Adicionar") {
        ChoiceChips(listOf(false, true), remove, { if (it) "Remover" else "Adicionar" }, { remove = it })
        LongField(amount, { amount = it }, "Quantidade de XP", Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(50L, 100L, 300L, 1000L).forEach { q -> GhostButton("+$q", { amount += q }, Modifier.weight(1f)) }
        }
        Hint("Atual: ${c.xp} / ${c.xpToNext}. O nível não sobe sozinho: use “Subir de nível” quando quiser.")
    }
}

// ---------------- Recursos ----------------

@Composable
private fun ResourcesPanel(c: Character, ctl: SheetController) {
    var editing by remember { mutableStateOf<Resource?>(null) }
    var creating by remember { mutableStateOf(false) }
    OrnatePanel(Modifier.fillMaxWidth()) {
        SectionTitle("Status e recursos") {
            RoundIconButton(Icons.Filled.Add, "Novo recurso", { creating = true }, tint = Codex.colors.accent, size = 36.dp)
        }
        if (c.resources.isEmpty()) Hint("Nenhum recurso. Adicione Vida, Mana, Sanidade ou o que o seu sistema usar.")
        AutoGrid(c.resources, minItemWidth = 260.dp, maxColumns = 2) { r ->
            ResourceCard(r, onChange = { v -> ctl.update { ch -> ch.copy(resources = ch.resources.map { if (it.id == r.id) it.copy(current = v) else it }) } }, onEdit = { editing = r })
        }
    }
    if (creating) ResourceDialog(null, c, ctl) { creating = false }
    editing?.let { r -> ResourceDialog(r, c, ctl) { editing = null } }
}

@Composable
fun ResourceCard(r: Resource, onChange: (Int) -> Unit, onEdit: () -> Unit) {
    val col = Codex.colors
    val color = Color(r.color)
    val range = (r.max - r.min).coerceAtLeast(1)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).padding(vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.name, Modifier.weight(1f).clickable(onClick = onEdit), style = MaterialTheme.typography.titleSmall, color = col.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (r.display != ResourceDisplay.BAR) {
                Text(
                    "${r.current}", style = MaterialTheme.typography.titleLarge, color = color, fontFamily = TitleFont,
                    modifier = Modifier.clickable(onClick = onEdit),
                )
                Text(" / ${r.max}", style = MaterialTheme.typography.titleSmall, color = col.muted)
            }
            RoundIconButton(Icons.Filled.Edit, "Editar ${r.name}", onEdit, size = 36.dp, tint = col.faint)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(Icons.Filled.Remove, "Diminuir ${r.name}", { onChange((r.current - r.step).coerceIn(r.min, r.max)) }, tint = color, size = 40.dp, filled = true)
            Spacer(Modifier.width(8.dp))
            if (r.display != ResourceDisplay.NUMBER) {
                Column(Modifier.weight(1f)) {
                    GlowBar((r.current - r.min).toFloat() / range, color, height = 14.dp)
                    if (r.display == ResourceDisplay.BAR) Text("${r.current} / ${r.max}", style = MaterialTheme.typography.labelSmall, color = col.muted, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            } else {
                Text("mín. ${r.min} · máx. ${r.max}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = col.faint, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.width(8.dp))
            RoundIconButton(Icons.Filled.Add, "Aumentar ${r.name}", { onChange((r.current + r.step).coerceIn(r.min, r.max)) }, tint = color, size = 40.dp, filled = true)
        }
    }
}

private val ResourceSuggestions = listOf(
    "Vida" to ResourceColors.LIFE, "Mana" to ResourceColors.MANA, "Vigor" to ResourceColors.STAMINA,
    "Energia" to ResourceColors.ENERGY, "Energia espiritual" to ResourceColors.SPIRIT, "Sanidade" to ResourceColors.SANITY,
    "Corrupção" to ResourceColors.CORRUPTION, "Sangue" to ResourceColors.BLOOD, "Fúria" to 0xFFD9534FL, "Fé" to 0xFFE8D58AL,
)

@Composable
private fun ResourceDialog(initial: Resource?, c: Character, ctl: SheetController, onDismiss: () -> Unit) {
    var r by remember { mutableStateOf(initial ?: Resource(Ids.new(), "", 10, 10, 0, ResourceColors.SPIRIT)) }
    val valid = r.name.isNotBlank() && r.max >= r.min
    EditorDialog(
        if (initial == null) "Novo recurso" else "Editar ${initial.name}", onDismiss,
        {
            val fixed = r.copy(name = r.name.trim(), current = r.current.coerceIn(r.min, r.max), step = r.step.coerceAtLeast(1))
            ctl.update { ch -> ch.copy(resources = if (initial == null) ch.resources + fixed else ch.resources.map { if (it.id == fixed.id) fixed else it }) }
            onDismiss()
        },
        saveEnabled = valid,
        onDelete = initial?.let { { ctl.update { ch -> ch.copy(resources = ch.resources.filterNot { it.id == initial.id }) }; onDismiss() } },
        deleteWhat = "o recurso “${initial?.name}”",
    ) {
        SuggestField(r.name, { v ->
            val sug = ResourceSuggestions.firstOrNull { it.first == v }
            r = r.copy(name = v, color = sug?.second ?: r.color)
        }, "Nome", ResourceSuggestions.map { it.first }.filterNot { s -> c.resources.any { it.name == s } })
        FieldRow {
            IntField(r.current, { r = r.copy(current = it) }, "Atual", Modifier.weight(1f))
            IntField(r.max, { r = r.copy(max = it) }, "Máximo", Modifier.weight(1f))
        }
        FieldRow {
            IntField(r.min, { r = r.copy(min = it) }, "Mínimo", Modifier.weight(1f))
            IntField(r.step, { r = r.copy(step = it) }, "Passo (+/−)", Modifier.weight(1f), allowNegative = false, min = 1)
        }
        if (r.max < r.min) Text("O máximo deve ser maior ou igual ao mínimo.", color = Codex.colors.danger, style = MaterialTheme.typography.bodySmall)
        Text("Exibição", style = MaterialTheme.typography.bodySmall, color = Codex.colors.muted)
        ChoiceChips(ResourceDisplay.entries, r.display, { it.label }, { r = r.copy(display = it) })
        ColorPicker(ResourceColors.palette, r.color, { r = r.copy(color = it) })
        if (initial != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Encher", { r = r.copy(current = r.max) }, Modifier.weight(1f))
                GhostButton("Zerar", { r = r.copy(current = r.min) }, Modifier.weight(1f), color = Codex.colors.muted)
            }
        }
    }
}

// ---------------- Atributos (resumo) ----------------

@Composable
private fun AttributeSummary(c: Character, ctl: SheetController) {
    val col = Codex.colors
    if (c.attributes.isEmpty()) return
    OrnatePanel(Modifier.fillMaxWidth(), padding = PaddingValues(12.dp)) {
        SectionTitle("Atributos")
        Hint("Toque para testar. Edite na aba Atributos.")
        Spacer(Modifier.height(8.dp))
        AutoGrid(c.attributes, minItemWidth = 92.dp) { a ->
            val mod = SheetMath.modifier(a, c.rules)
            StatBadge(
                a.abbr.ifBlank { a.name.take(3) },
                if (c.rules.rollUnder) "${a.value}" else SheetMath.signed(mod),
                Modifier.fillMaxWidth(), col.accent,
                onClick = { ctl.rollCheck(c, a.name, if (c.rules.rollUnder) a.value else mod) },
            )
        }
    }
}
