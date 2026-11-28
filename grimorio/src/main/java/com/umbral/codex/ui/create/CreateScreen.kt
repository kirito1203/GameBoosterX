package com.umbral.codex.ui.create

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umbral.codex.core.SystemPreset
import com.umbral.codex.model.Catalog
import com.umbral.codex.ui.LocalAppState
import com.umbral.codex.ui.LocalRepository
import com.umbral.codex.ui.components.ArcaneButton
import com.umbral.codex.ui.components.CodexIcons
import com.umbral.codex.ui.components.CodexTextField
import com.umbral.codex.ui.components.DropdownField
import com.umbral.codex.ui.components.EmptyState
import com.umbral.codex.ui.components.FilterPill
import com.umbral.codex.ui.components.GlyphMedallion
import com.umbral.codex.ui.components.Hint
import com.umbral.codex.ui.components.OrnatePanel
import com.umbral.codex.ui.components.PanelShape
import com.umbral.codex.ui.components.ScreenHeader
import com.umbral.codex.ui.components.SectionTitle
import com.umbral.codex.ui.createCharacter
import com.umbral.codex.ui.createFromTemplate
import com.umbral.codex.ui.theme.Codex

@Composable
fun CreateScreen(initialCatalogId: String?, initialTemplateId: String?) {
    val vm = LocalAppState.current
    val repo = LocalRepository.current
    val catalogs by repo.catalogs.collectAsStateWithLifecycle()
    val settings by repo.settings.collectAsStateWithLifecycle()
    val c = Codex.colors

    var name by rememberSaveable { mutableStateOf("") }
    var catalogId by rememberSaveable { mutableStateOf(initialCatalogId ?: settings.defaultCatalogId) }
    var useTemplate by rememberSaveable { mutableStateOf(initialTemplateId != null) }
    var preset by rememberSaveable { mutableStateOf(SystemPreset.CLASSIC) }
    var templateId by rememberSaveable { mutableStateOf(initialTemplateId) }

    val sorted = catalogs.sortedBy { it.order }
    val catalog = sorted.firstOrNull { it.id == catalogId }
    val templates: List<Pair<Catalog, com.umbral.codex.model.CharacterTemplate>> =
        (if (catalog != null) listOf(catalog) else sorted).flatMap { cat -> cat.templates.map { cat to it } }
    val template = templates.firstOrNull { it.second.id == templateId }
    androidx.compose.runtime.LaunchedEffect(useTemplate, catalogId) {
        if (useTemplate && template == null) templateId = templates.firstOrNull()?.second?.id
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Criar ficha", "Do zero ou a partir de um modelo", onBack = if (vm.stack.size > 1) ({ vm.back() }) else null)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OrnatePanel(Modifier.fillMaxWidth(), glow = true) {
                CodexTextField(name, { name = it }, "Nome do personagem", placeholder = "Ex.: Lysandra Vael")
                Spacer(Modifier.height(10.dp))
                DropdownField(
                    "Universo / categoria de RPG",
                    listOf<Catalog?>(null) + sorted,
                    catalog,
                    { it?.name ?: "Nenhum (ficha avulsa)" },
                    { catalogId = it?.id; if (template != null && it != null && template.first.id != it.id) templateId = null },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill("Do zero", !useTemplate, { useTemplate = false })
                FilterPill("Usar modelo", useTemplate, { useTemplate = true })
            }

            if (!useTemplate) {
                SectionTitle("Sistema de regras inicial")
                Hint("O sistema só define o ponto de partida. Atributos, recursos, perícias e fórmulas continuam editáveis na ficha.")
                SystemPreset.entries.forEach { p ->
                    SelectCard(p.label, p.description, p == preset, icon = when (p) {
                        SystemPreset.CLASSIC -> "shield"; SystemPreset.SUPERNATURAL -> "nights_stay"
                        SystemPreset.INVESTIGATION -> "visibility"; SystemPreset.FREEFORM -> "history_edu"
                    }) { preset = p }
                }
            } else {
                SectionTitle(if (catalog != null) "Modelos de ${catalog.name}" else "Todos os modelos")
                if (templates.isEmpty()) {
                    EmptyState("Nenhum modelo neste catálogo", "Crie a ficha do zero e depois use “Salvar como modelo”.")
                }
                templates.forEach { (cat, t) ->
                    SelectCard(
                        t.name,
                        buildString {
                            if (catalog == null) append(cat.name).append(" · ")
                            append(t.description.ifBlank { listOf(t.blueprint.race, t.blueprint.charClass).filter { it.isNotBlank() }.joinToString(" · ") })
                        },
                        t.id == templateId,
                        icon = cat.glyph,
                        accent = Color(cat.accent),
                    ) { templateId = t.id; if (catalogId == null) catalogId = cat.id }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Column(Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding()) {
            ArcaneButton(
                "Criar ficha",
                {
                    if (useTemplate && template != null) vm.createFromTemplate(name, catalogId ?: template.first.id, template.second)
                    else vm.createCharacter(name, catalogId, preset)
                },
                Modifier.fillMaxWidth(),
                icon = Icons.Filled.AutoFixHigh,
                enabled = !useTemplate || template != null,
            )
            if (name.isBlank()) Text("Sem nome? Tudo bem — você pode nomear depois.", style = MaterialTheme.typography.bodySmall, color = c.faint, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun SelectCard(title: String, description: String, selected: Boolean, icon: String, accent: Color = Codex.colors.accent, onClick: () -> Unit) {
    val c = Codex.colors
    OrnatePanel(
        Modifier.fillMaxWidth().then(if (selected) Modifier.border(1.5.dp, c.gold, PanelShape) else Modifier),
        accent = accent, glow = selected, ornaments = selected, onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphMedallion(CodexIcons.of(icon), accent)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = c.text)
                if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
            Icon(
                if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null,
                tint = if (selected) c.gold else c.faint, modifier = Modifier.size(24.dp),
            )
        }
    }
}
