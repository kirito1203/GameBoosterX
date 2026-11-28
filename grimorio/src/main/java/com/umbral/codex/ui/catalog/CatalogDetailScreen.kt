package com.umbral.codex.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umbral.codex.core.Ids
import com.umbral.codex.model.Catalog
import com.umbral.codex.model.CatalogEntry
import com.umbral.codex.model.CharacterTemplate
import com.umbral.codex.model.LoreSection
import com.umbral.codex.ui.LocalAppState
import com.umbral.codex.ui.LocalRepository
import com.umbral.codex.ui.Route
import com.umbral.codex.ui.components.ArcaneButton
import com.umbral.codex.ui.components.CatalogCover
import com.umbral.codex.ui.components.CharacterCard
import com.umbral.codex.ui.components.ChoiceChips
import com.umbral.codex.ui.components.CodexTextField
import com.umbral.codex.ui.components.EditorDialog
import com.umbral.codex.ui.components.EmptyState
import com.umbral.codex.ui.components.GhostButton
import com.umbral.codex.ui.components.OrnatePanel
import com.umbral.codex.ui.components.RoundIconButton
import com.umbral.codex.ui.components.ScreenHeader
import com.umbral.codex.ui.components.SectionTitle
import com.umbral.codex.ui.components.SuggestField
import com.umbral.codex.ui.components.Tag
import com.umbral.codex.ui.theme.Codex
import com.umbral.codex.ui.toggleFavorite

private enum class CatalogTab(val label: String) {
    SETTING("Ambientação"), TRAITS("Características"), RACES("Raças"), CLASSES("Classes"),
    POWERS("Poderes"), TEMPLATES("Modelos"), CHARACTERS("Personagens"),
}

private val LoreSuggestions = listOf(
    "História do mundo", "Reinos", "Criaturas", "Maldições", "Religiões", "Magias proibidas", "Facções",
    "Personagens importantes", "Origem das criaturas", "Vampiros", "Lobisomens", "Demônios", "Anjos", "Entidades",
    "Caçadores", "Organizações", "Casas nobres", "Cavaleiros", "Guerras", "Armas", "Armaduras", "Culturas",
)

/** Estado de edição genérico para os diálogos da página do catálogo. */
private sealed interface CatalogEdit {
    data object Info : CatalogEdit
    data object Setting : CatalogEdit
    data class Lore(val section: LoreSection?) : CatalogEdit
    data class Trait(val index: Int?) : CatalogEdit
    data class Entry(val tab: CatalogTab, val entry: CatalogEntry?) : CatalogEdit
    data class Template(val template: CharacterTemplate) : CatalogEdit
}

@Composable
fun CatalogDetailScreen(id: String) {
    val vm = LocalAppState.current
    val repo = LocalRepository.current
    val catalogs by repo.catalogs.collectAsStateWithLifecycle()
    val chars by repo.characters.collectAsStateWithLifecycle()
    val cat = catalogs.firstOrNull { it.id == id }
    val c = Codex.colors
    var tab by rememberSaveable { mutableStateOf(CatalogTab.SETTING) }
    var edit by remember { mutableStateOf<CatalogEdit?>(null) }

    if (cat == null) {
        Column { ScreenHeader("Catálogo", onBack = { vm.back() }); EmptyState("Catálogo não encontrado", "Ele pode ter sido excluído.") }
        return
    }
    val accent = Color(cat.accent)
    fun save(transform: (Catalog) -> Catalog) = repo.updateCatalog(cat.id, transform)
    val members = chars.filter { it.catalogId == cat.id }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Box {
                CatalogCover(cat, Modifier.fillMaxWidth().aspectRatio(1.6f), glyphSize = 48.dp)
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(c.void.copy(alpha = 0.55f), Color.Transparent, c.void))))
                ScreenHeader("", onBack = { vm.back() }) {
                    RoundIconButton(Icons.Filled.Edit, "Editar categoria", { edit = CatalogEdit.Info }, tint = c.text, filled = true)
                }
                Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    if (cat.builtIn) Tag("Catálogo do app · editável", c.gold)
                    Spacer(Modifier.height(6.dp))
                    Text(cat.name, style = MaterialTheme.typography.headlineMedium, color = c.text)
                    if (cat.tagline.isNotBlank()) Text(cat.tagline, style = MaterialTheme.typography.bodyMedium, color = c.muted)
                }
            }
        }
        item {
            ChoiceChips(CatalogTab.entries, tab, { t ->
                when (t) {
                    CatalogTab.RACES -> "Raças (${cat.races.size})"
                    CatalogTab.CLASSES -> "Classes (${cat.classes.size})"
                    CatalogTab.POWERS -> "Poderes (${cat.powers.size})"
                    CatalogTab.TEMPLATES -> "Modelos (${cat.templates.size})"
                    CatalogTab.CHARACTERS -> "Personagens (${members.size})"
                    else -> t.label
                }
            }, { tab = it }, Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        }

        when (tab) {
            CatalogTab.SETTING -> {
                if (cat.description.isNotBlank()) item {
                    Text(cat.description, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.bodyLarge, color = c.text)
                }
                item {
                    OrnatePanel(Modifier.padding(16.dp).fillMaxWidth(), accent = accent, onClick = { edit = CatalogEdit.Setting }) {
                        SectionTitle("História e ambientação") { RoundIconButton(Icons.Filled.Edit, "Editar ambientação", { edit = CatalogEdit.Setting }, tint = c.accent, size = 36.dp) }
                        Text(cat.setting.ifBlank { "Toque para escrever a história deste universo." }, style = MaterialTheme.typography.bodyLarge, color = if (cat.setting.isBlank()) c.faint else c.text)
                    }
                }
                items(cat.lore, key = { it.id }) { s ->
                    OrnatePanel(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), accent = accent, ornaments = false, onClick = { edit = CatalogEdit.Lore(s) }) {
                        SectionTitle(s.title)
                        Text(s.body, style = MaterialTheme.typography.bodyMedium, color = c.text)
                    }
                }
                item { GhostButton("Adicionar seção (reinos, criaturas, facções…)", { edit = CatalogEdit.Lore(null) }, Modifier.padding(16.dp).fillMaxWidth(), icon = Icons.Filled.Add) }
            }
            CatalogTab.TRAITS -> {
                item { Text("Traços que definem o gênero e ajudam a criar personagens coerentes.", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = c.muted) }
                items(cat.characteristics.size) { i ->
                    OrnatePanel(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(), ornaments = false, padding = PaddingValues(14.dp), onClick = { edit = CatalogEdit.Trait(i) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("✧", color = c.gold); Spacer(Modifier.width(10.dp))
                            Text(cat.characteristics[i], style = MaterialTheme.typography.bodyLarge, color = c.text)
                        }
                    }
                }
                item { GhostButton("Adicionar característica", { edit = CatalogEdit.Trait(null) }, Modifier.padding(16.dp).fillMaxWidth(), icon = Icons.Filled.Add) }
            }
            CatalogTab.RACES, CatalogTab.CLASSES, CatalogTab.POWERS -> {
                val list = when (tab) { CatalogTab.RACES -> cat.races; CatalogTab.CLASSES -> cat.classes; else -> cat.powers }
                if (list.isEmpty()) item { EmptyState("Nada cadastrado", "Adicione ${tab.label.lowercase()} para usar como referência nas fichas.") }
                items(list, key = { it.id }) { e ->
                    OrnatePanel(Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth(), accent = accent, ornaments = false, onClick = { edit = CatalogEdit.Entry(tab, e) }) {
                        Text(e.name, style = MaterialTheme.typography.titleMedium, color = c.text)
                        if (e.description.isNotBlank()) Text(e.description, style = MaterialTheme.typography.bodyMedium, color = c.muted)
                    }
                }
                item { GhostButton("Adicionar", { edit = CatalogEdit.Entry(tab, null) }, Modifier.padding(16.dp).fillMaxWidth(), icon = Icons.Filled.Add) }
            }
            CatalogTab.TEMPLATES -> {
                item {
                    Text(
                        "Modelos são fichas-base prontas. Ao criar uma ficha com eles, atributos, recursos, poderes e itens são copiados e podem ser alterados livremente. " +
                            "Para criar um modelo, abra uma ficha e use “Salvar como modelo” em Outras informações.",
                        Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall, color = c.muted,
                    )
                }
                if (cat.templates.isEmpty()) item { EmptyState("Nenhum modelo", "Salve uma ficha como modelo para reutilizá-la.") }
                items(cat.templates, key = { it.id }) { t ->
                    OrnatePanel(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(), accent = accent) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t.name, style = MaterialTheme.typography.titleMedium, color = c.text)
                                if (t.description.isNotBlank()) Text(t.description, style = MaterialTheme.typography.bodySmall, color = c.muted)
                            }
                            RoundIconButton(Icons.Filled.Edit, "Editar modelo", { edit = CatalogEdit.Template(t) }, tint = c.muted)
                        }
                        Spacer(Modifier.height(6.dp))
                        val b = t.blueprint
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOfNotNull(b.race.takeIf { it.isNotBlank() }, b.charClass.takeIf { it.isNotBlank() }).forEach { Tag(it, accent) }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${b.attributes.size} atributos · ${b.resources.size} recursos · ${b.skills.size} perícias · ${b.spells.size} magias · ${b.abilities.size} poderes · ${b.items.size} itens",
                            style = MaterialTheme.typography.bodySmall, color = c.faint,
                        )
                        Spacer(Modifier.height(10.dp))
                        ArcaneButton("Criar ficha com este modelo", { vm.open(Route.Create(cat.id, t.id)) }, Modifier.fillMaxWidth(), icon = Icons.Filled.PersonAdd)
                    }
                }
            }
            CatalogTab.CHARACTERS -> {
                item { ArcaneButton("Nova ficha neste catálogo", { vm.open(Route.Create(cat.id)) }, Modifier.padding(16.dp).fillMaxWidth(), icon = Icons.Filled.PersonAdd) }
                if (members.isEmpty()) item { EmptyState("Nenhum personagem", "As fichas criadas neste universo aparecem aqui.") }
                items(members, key = { it.id }) { ch ->
                    CharacterCard(ch, cat, { vm.open(Route.Sheet(ch.id)) }, { vm.toggleFavorite(ch.id) }, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
            }
        }
    }

    when (val e = edit) {
        null -> {}
        CatalogEdit.Info -> CatalogEditDialog(
            cat, isNew = false, onDismiss = { edit = null },
            onSave = { updated -> repo.upsertCatalog(updated); edit = null; vm.toast("Categoria salva") },
            onDelete = { repo.deleteCatalog(cat.id); edit = null; vm.back(); vm.toast("Categoria “${cat.name}” excluída") },
        )
        CatalogEdit.Setting -> {
            var text by remember { mutableStateOf(cat.setting) }
            EditorDialog("História e ambientação", { edit = null }, { save { it.copy(setting = text) }; edit = null }) {
                CodexTextField(text, { text = it }, "Texto da ambientação", singleLine = false, minLines = 8)
            }
        }
        is CatalogEdit.Lore -> {
            var title by remember { mutableStateOf(e.section?.title ?: "") }
            var body by remember { mutableStateOf(e.section?.body ?: "") }
            EditorDialog(
                if (e.section == null) "Nova seção" else "Editar seção", { edit = null },
                {
                    val s = (e.section ?: LoreSection(Ids.new(), "")).copy(title = title.trim(), body = body)
                    save { cat2 -> cat2.copy(lore = if (e.section == null) cat2.lore + s else cat2.lore.map { if (it.id == s.id) s else it }) }
                    edit = null
                },
                saveEnabled = title.isNotBlank(),
                onDelete = e.section?.let { sec -> { save { it.copy(lore = it.lore.filterNot { l -> l.id == sec.id }) }; edit = null } },
                deleteWhat = "a seção “${e.section?.title}”",
            ) {
                SuggestField(title, { title = it }, "Título", LoreSuggestions.filterNot { s -> cat.lore.any { it.title == s } })
                CodexTextField(body, { body = it }, "Conteúdo", singleLine = false, minLines = 6)
            }
        }
        is CatalogEdit.Trait -> {
            var text by remember { mutableStateOf(e.index?.let { cat.characteristics.getOrNull(it) } ?: "") }
            EditorDialog(
                if (e.index == null) "Nova característica" else "Editar característica", { edit = null },
                {
                    save { cat2 ->
                        val l = cat2.characteristics.toMutableList()
                        if (e.index == null) l += text.trim() else if (e.index in l.indices) l[e.index] = text.trim()
                        cat2.copy(characteristics = l)
                    }
                    edit = null
                },
                saveEnabled = text.isNotBlank(),
                onDelete = e.index?.let { idx -> { save { it.copy(characteristics = it.characteristics.filterIndexed { i, _ -> i != idx }) }; edit = null } },
                deleteWhat = "esta característica",
            ) { CodexTextField(text, { text = it }, "Característica do gênero", singleLine = false, minLines = 2) }
        }
        is CatalogEdit.Entry -> {
            var name by remember { mutableStateOf(e.entry?.name ?: "") }
            var desc by remember { mutableStateOf(e.entry?.description ?: "") }
            val what = when (e.tab) { CatalogTab.RACES -> "raça"; CatalogTab.CLASSES -> "classe"; else -> "poder" }
            fun apply(transform: (List<CatalogEntry>) -> List<CatalogEntry>) = save { cat2 ->
                when (e.tab) {
                    CatalogTab.RACES -> cat2.copy(races = transform(cat2.races))
                    CatalogTab.CLASSES -> cat2.copy(classes = transform(cat2.classes))
                    else -> cat2.copy(powers = transform(cat2.powers))
                }
            }
            EditorDialog(
                if (e.entry == null) "Nova $what" else "Editar $what", { edit = null },
                {
                    val entry = (e.entry ?: CatalogEntry(Ids.new(), "")).copy(name = name.trim(), description = desc.trim())
                    apply { l -> if (e.entry == null) l + entry else l.map { if (it.id == entry.id) entry else it } }
                    edit = null
                },
                saveEnabled = name.isNotBlank(),
                onDelete = e.entry?.let { en -> { apply { l -> l.filterNot { it.id == en.id } }; edit = null } },
                deleteWhat = "“${e.entry?.name}”",
            ) {
                CodexTextField(name, { name = it }, "Nome")
                CodexTextField(desc, { desc = it }, "Descrição", singleLine = false, minLines = 4)
            }
        }
        is CatalogEdit.Template -> {
            var name by remember { mutableStateOf(e.template.name) }
            var desc by remember { mutableStateOf(e.template.description) }
            EditorDialog(
                "Editar modelo", { edit = null },
                {
                    save { cat2 -> cat2.copy(templates = cat2.templates.map { if (it.id == e.template.id) it.copy(name = name.trim(), description = desc.trim()) else it }) }
                    edit = null
                },
                saveEnabled = name.isNotBlank(),
                onDelete = { save { it.copy(templates = it.templates.filterNot { t -> t.id == e.template.id }) }; edit = null },
                deleteWhat = "o modelo “${e.template.name}”",
            ) {
                CodexTextField(name, { name = it }, "Nome do modelo")
                CodexTextField(desc, { desc = it }, "Descrição", singleLine = false, minLines = 3)
            }
        }
    }
}
