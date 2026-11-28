package com.umbral.codex.ui.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umbral.codex.core.Dice
import com.umbral.codex.core.Ids
import com.umbral.codex.core.SheetMath
import com.umbral.codex.model.Catalog
import com.umbral.codex.model.Character
import com.umbral.codex.model.CustomField
import com.umbral.codex.model.ProficiencyRule
import com.umbral.codex.model.SheetSection
import com.umbral.codex.model.XpCurve
import com.umbral.codex.ui.LocalRepository
import com.umbral.codex.ui.components.ArcaneButton
import com.umbral.codex.ui.components.CodexTextField
import com.umbral.codex.ui.components.DropdownField
import com.umbral.codex.ui.components.EditorDialog
import com.umbral.codex.ui.components.FieldRow
import com.umbral.codex.ui.components.FilterPill
import com.umbral.codex.ui.components.GhostButton
import com.umbral.codex.ui.components.Hint
import com.umbral.codex.ui.components.IntField
import com.umbral.codex.ui.components.LongField
import com.umbral.codex.ui.components.OrnatePanel
import com.umbral.codex.ui.components.RoundIconButton
import com.umbral.codex.ui.components.SectionTitle
import com.umbral.codex.ui.components.SwitchRow
import com.umbral.codex.ui.components.formatDate
import com.umbral.codex.ui.duplicateCharacter
import com.umbral.codex.ui.saveAsTemplate
import com.umbral.codex.ui.settings.rememberCharacterExporter
import com.umbral.codex.ui.theme.Codex

@Composable
fun OtherTab(c: Character, ctl: SheetController) {
    val col = Codex.colors
    var editingField by remember { mutableStateOf<CustomField?>(null) }
    var creatingField by remember { mutableStateOf(false) }
    var saveTemplate by remember { mutableStateOf(false) }
    var config by rememberSaveable { mutableStateOf(0) }
    val export = rememberCharacterExporter()

    TabColumn {
        OrnatePanel(Modifier.fillMaxWidth(), ornaments = false) {
            SectionTitle("Anotações")
            CodexTextField(c.notes, { v -> ctl.update { it.copy(notes = v) } }, "Notas gerais, regras da mesa, lembretes", singleLine = false, minLines = 5)
            Spacer(Modifier.height(10.dp))
            CodexTextField(c.languages, { v -> ctl.update { it.copy(languages = v) } }, "Idiomas e proficiências", singleLine = false, minLines = 2)
        }

        OrnatePanel(Modifier.fillMaxWidth()) {
            SectionTitle("Campos personalizados") { RoundIconButton(Icons.Filled.Add, "Novo campo", { creatingField = true }, tint = col.accent, size = 36.dp) }
            if (c.customFields.isEmpty()) Hint("Crie campos próprios do seu sistema: Clã, Geração, Patrono, Signo, Pecado…")
            c.customFields.forEach { f ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(f.name, style = MaterialTheme.typography.labelLarge, color = col.gold)
                        Text(f.value.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge, color = col.text)
                    }
                    RoundIconButton(Icons.Filled.Edit, "Editar ${f.name}", { editingField = f }, tint = col.faint)
                }
            }
        }

        OrnatePanel(Modifier.fillMaxWidth()) {
            SectionTitle("Configurar ficha")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill("Seções", config == 0, { config = 0 })
                FilterPill("Regras", config == 1, { config = 1 })
                FilterPill("Nomes", config == 2, { config = 2 })
            }
            Spacer(Modifier.height(8.dp))
            when (config) {
                0 -> SectionsConfig(c, ctl)
                1 -> RulesConfig(c, ctl)
                else -> LabelsConfig(c, ctl)
            }
        }

        OrnatePanel(Modifier.fillMaxWidth()) {
            SectionTitle("Ficha")
            Text("Criada em ${formatDate(c.createdAt)} · editada em ${formatDate(c.updatedAt)}", style = MaterialTheme.typography.bodySmall, color = col.faint)
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ArcaneButton("Salvar como modelo", { saveTemplate = true }, Modifier.fillMaxWidth(), icon = Icons.Filled.BookmarkAdd)
                GhostButton("Duplicar ficha", { ctl.vm.duplicateCharacter(c.id) }, Modifier.fillMaxWidth(), icon = Icons.Filled.ContentCopy)
                GhostButton("Exportar ficha (.json)", { export(c.id) }, Modifier.fillMaxWidth(), icon = Icons.Filled.FileUpload)
            }
            if (c.isDemo) {
                Spacer(Modifier.height(8.dp))
                SwitchRow("Personagem de exemplo", c.isDemo, { v -> ctl.update { it.copy(isDemo = v) } }, description = "Desmarque para tornar esta ficha sua (ela não será removida com os exemplos).")
            }
        }
    }

    if (creatingField) FieldDialog(null, ctl) { creatingField = false }
    editingField?.let { f -> FieldDialog(f, ctl) { editingField = null } }
    if (saveTemplate) TemplateDialog(c, ctl) { saveTemplate = false }
}

@Composable
private fun SectionsConfig(c: Character, ctl: SheetController) {
    Hint("Escolha quais abas aparecem nesta ficha. Ficha e Outras informações são sempre exibidas.")
    SheetSection.entries.filter { it.canHide }.forEach { s ->
        SwitchRow(s.label, s.key !in c.hiddenSections, { visible ->
            ctl.update { it.copy(hiddenSections = if (visible) it.hiddenSections - s.key else it.hiddenSections + s.key) }
        })
    }
}

@Composable
private fun RulesConfig(c: Character, ctl: SheetController) {
    val col = Codex.colors
    val r = c.rules
    fun set(t: (com.umbral.codex.model.RuleSet) -> com.umbral.codex.model.RuleSet) = ctl.update { SheetMath.syncDerived(it.copy(rules = t(it.rules))) }
    var die by remember { mutableStateOf(r.checkDie) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Hint("A regra de modificador dos atributos fica na aba Atributos.")
        Row(verticalAlignment = Alignment.CenterVertically) {
            CodexTextField(die, { v -> die = v; if (Dice.tryParse(v) != null) set { it.copy(checkDie = v.trim()) } }, "Dado de teste", Modifier.weight(1f), isError = Dice.tryParse(die) == null)
        }
        SwitchRow("Rolar abaixo do valor (d100)", r.rollUnder, { v -> set { it.copy(rollUnder = v) } }, description = "Testes comparam o dado com o valor da perícia, como em sistemas percentuais.")
        DropdownField("Bônus de proficiência", ProficiencyRule.entries, r.proficiencyRule, { it.label }, { v -> set { it.copy(proficiencyRule = v) } })
        IntField(r.trainedBonus, { v -> set { it.copy(trainedBonus = v) } }, "Bônus de perícia treinada", Modifier.fillMaxWidth())
        DropdownField("Curva de experiência", XpCurve.entries, r.xpCurve, { it.label }, { v -> set { it.copy(xpCurve = v) } })
        FieldRow {
            if (r.xpCurve == XpCurve.LINEAR || r.xpCurve == XpCurve.PROGRESSIVE) LongField(r.xpBase, { v -> set { it.copy(xpBase = v.coerceAtLeast(1)) } }, "Base de XP", Modifier.weight(1f))
            IntField(r.maxLevel, { v -> set { it.copy(maxLevel = v.coerceAtLeast(1)) } }, "Nível máximo", Modifier.weight(1f), allowNegative = false, min = 1)
        }
        SwitchRow("Manter XP excedente ao subir de nível", r.carryOverXp, { v -> set { it.copy(carryOverXp = v) } })
        Text("Próximo nível exige ${c.xpToNext} XP", style = MaterialTheme.typography.bodySmall, color = col.gold)
    }
}

@Composable
private fun LabelsConfig(c: Character, ctl: SheetController) {
    Hint("Renomeie campos para combinar com o seu sistema (ex.: Classe → Ocupação, Raça → Linhagem). Deixe vazio para o nome padrão.")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RenameableFields.forEach { (key, default) ->
            CodexTextField(
                c.fieldLabels[key] ?: "",
                { v -> ctl.update { it.copy(fieldLabels = if (v.isBlank()) it.fieldLabels - key else it.fieldLabels + (key to v)) } },
                default, placeholder = default,
            )
        }
    }
}

@Composable
private fun FieldDialog(initial: CustomField?, ctl: SheetController, onDismiss: () -> Unit) {
    var f by remember { mutableStateOf(initial ?: CustomField(Ids.new(), "")) }
    EditorDialog(
        if (initial == null) "Novo campo" else "Editar campo", onDismiss,
        {
            val fixed = f.copy(name = f.name.trim())
            ctl.update { ch -> ch.copy(customFields = if (initial == null) ch.customFields + fixed else ch.customFields.map { if (it.id == fixed.id) fixed else it }) }
            onDismiss()
        },
        saveEnabled = f.name.isNotBlank(),
        onDelete = initial?.let { { ctl.update { ch -> ch.copy(customFields = ch.customFields.filterNot { it.id == initial.id }) }; onDismiss() } },
        deleteWhat = "o campo “${initial?.name}”",
    ) {
        CodexTextField(f.name, { f = f.copy(name = it) }, "Nome do campo")
        CodexTextField(f.value, { f = f.copy(value = it) }, "Valor", singleLine = false, minLines = 2)
    }
}

@Composable
private fun TemplateDialog(c: Character, ctl: SheetController, onDismiss: () -> Unit) {
    val repo = LocalRepository.current
    val catalogs by repo.catalogs.collectAsStateWithLifecycle()
    var catalog by remember { mutableStateOf(catalogs.firstOrNull { it.id == c.catalogId } ?: catalogs.firstOrNull()) }
    var name by remember { mutableStateOf(listOf(c.race, c.charClass).filter { it.isNotBlank() }.joinToString(" ").ifBlank { c.name }) }
    var desc by remember { mutableStateOf("") }
    EditorDialog("Salvar como modelo", onDismiss, {
        catalog?.let { ctl.vm.saveAsTemplate(c.id, it.id, name, desc) }
        onDismiss()
    }, saveEnabled = name.isNotBlank() && catalog != null) {
        Hint("O modelo guarda atributos, recursos, perícias, magias, poderes, características, itens, moedas, regras e configurações desta ficha. Nome e história ficam de fora.")
        DropdownField("Catálogo", catalogs.sortedBy { it.order }, catalog, { it?.name ?: "—" }, { v: Catalog? -> catalog = v })
        CodexTextField(name, { name = it }, "Nome do modelo")
        CodexTextField(desc, { desc = it }, "Descrição", singleLine = false, minLines = 2)
    }
}
