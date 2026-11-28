package com.umbral.codex.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GroupRemove
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.umbral.codex.data.BackupCodec
import com.umbral.codex.data.ImportException
import com.umbral.codex.data.ImportPayload
import com.umbral.codex.data.RestoreMode
import com.umbral.codex.model.AccentTheme
import com.umbral.codex.model.Catalog
import com.umbral.codex.model.TextScale
import com.umbral.codex.ui.AppViewModel
import com.umbral.codex.ui.LocalAppState
import com.umbral.codex.ui.LocalRepository
import com.umbral.codex.ui.components.ArcaneButton
import com.umbral.codex.ui.components.ChoiceChips
import com.umbral.codex.ui.components.CodexTextField
import com.umbral.codex.ui.components.ColorChooserDialog
import com.umbral.codex.ui.components.ColorDot
import com.umbral.codex.ui.components.ColorMath
import com.umbral.codex.ui.components.CustomColorDot
import com.umbral.codex.ui.components.ConfirmDialog
import com.umbral.codex.ui.components.DropdownField
import com.umbral.codex.ui.components.EditorDialog
import com.umbral.codex.ui.components.GhostButton
import com.umbral.codex.ui.components.Hint
import com.umbral.codex.ui.components.OrnatePanel
import com.umbral.codex.ui.components.ScreenHeader
import com.umbral.codex.ui.components.SectionTitle
import com.umbral.codex.ui.components.SwitchRow
import com.umbral.codex.ui.components.formatDate
import com.umbral.codex.ui.loadDemo
import com.umbral.codex.ui.removeDemo
import com.umbral.codex.ui.theme.Codex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Exporta uma ficha para um arquivo .json escolhido pelo usuário. */
@Composable
fun rememberCharacterExporter(): (String) -> Unit {
    val vm = LocalAppState.current
    val repo = LocalRepository.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val id = pending ?: return@rememberLauncherForActivityResult
        pending = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val json = repo.exportCharacter(id) ?: error("Ficha não encontrada")
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray()) } }
                vm.toast("Ficha exportada")
            } catch (e: Exception) {
                vm.toast("Falha ao exportar: ${e.message}")
            }
        }
    }
    return { id ->
        pending = id
        val name = repo.character(id)?.name?.ifBlank { "ficha" } ?: "ficha"
        launcher.launch("${safeFileName(name)}.codice.json")
    }
}

fun safeFileName(s: String) = s.replace(Regex("[^\\p{L}\\p{N}_ -]"), "").trim().replace(' ', '_').ifBlank { "ficha" }.take(40)

/** Seletor de arquivo para importar ficha única ou backup completo. */
@Composable
fun rememberImporter(vm: AppViewModel): () -> Unit {
    val repo = vm.repository
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingFull by remember { mutableStateOf<Pair<String, ImportPayload.Full>?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val text = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }
                when (val p = withContext(Dispatchers.Default) { BackupCodec.decode(text) }) {
                    is ImportPayload.Single -> {
                        repo.import(text)
                        vm.toast("Ficha “${p.export.character.name.ifBlank { "Sem nome" }}” importada")
                    }
                    is ImportPayload.Full -> pendingFull = text to p
                }
            } catch (e: ImportException) {
                vm.toast(e.message ?: "Arquivo inválido")
            } catch (e: Exception) {
                vm.toast("Não foi possível ler o arquivo: ${e.message}")
            }
        }
    }
    pendingFull?.let { (text, p) ->
        var mode by remember { mutableStateOf(RestoreMode.MERGE) }
        EditorDialog(
            "Restaurar backup",
            onDismiss = { pendingFull = null },
            onSave = {
                pendingFull = null
                scope.launch {
                    try {
                        val s = repo.import(text, mode)
                        vm.toast("Backup restaurado: ${s.characters} fichas, ${s.catalogs} catálogos, ${s.images} imagens")
                    } catch (e: Exception) {
                        vm.toast("Falha ao restaurar: ${e.message}")
                    }
                }
            },
            saveLabel = "Restaurar",
        ) {
            val b = p.backup
            Text("Backup de ${formatDate(b.exportedAt)}", style = MaterialTheme.typography.titleSmall, color = Codex.colors.text)
            Text("${b.characters.size} fichas · ${b.catalogs.size} catálogos · ${b.images.size} imagens", style = MaterialTheme.typography.bodyMedium, color = Codex.colors.muted)
            ChoiceChips(RestoreMode.entries, mode, { if (it == RestoreMode.MERGE) "Mesclar" else "Substituir tudo" }, { mode = it })
            Hint(
                if (mode == RestoreMode.MERGE) "Mantém suas fichas atuais. Fichas do backup com o mesmo id são atualizadas pela versão do arquivo."
                else "Apaga as fichas atuais e deixa o app exatamente como no backup (incluindo configurações)."
            )
        }
    }
    return { launcher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen() {
    val vm = LocalAppState.current
    val repo = LocalRepository.current
    val settings by repo.settings.collectAsStateWithLifecycle()
    val chars by repo.characters.collectAsStateWithLifecycle()
    val catalogs by repo.catalogs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = Codex.colors
    var confirm by remember { mutableStateOf<String?>(null) }
    var wipeText by remember { mutableStateOf<String?>(null) }
    var choosingColor by remember { mutableStateOf(false) }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val json = repo.exportBackup()
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray()) } }
                repo.updateSettings { it.copy(lastBackupAt = System.currentTimeMillis()) }
                vm.toast("Backup salvo com ${chars.size} fichas")
            } catch (e: Exception) {
                vm.toast("Falha no backup: ${e.message}")
            }
        }
    }
    val importer = rememberImporter(vm)

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Ajustes", "Aparência, preferências e dados")
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OrnatePanel(Modifier.fillMaxWidth()) {
                SectionTitle("Aparência")
                Text("Cor arcana", style = MaterialTheme.typography.bodySmall, color = c.muted)
                FlowRow(verticalArrangement = Arrangement.Center) {
                    AccentTheme.entries.forEach { a ->
                        Column(Modifier.padding(end = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            ColorDot(Color(a.primary), settings.customAccent == null && settings.accent == a) {
                                repo.updateSettings { it.copy(accent = a, customAccent = null) }
                            }
                        }
                    }
                    CustomColorDot(settings.customAccent, settings.customAccent != null) { choosingColor = true }
                }
                Text(
                    settings.customAccent?.let { "Cor personalizada ${ColorMath.toHex(it)}" } ?: settings.accent.label,
                    style = MaterialTheme.typography.labelLarge, color = c.accent,
                )
                Text("Toque no círculo colorido com + para escolher qualquer cor.", style = MaterialTheme.typography.bodySmall, color = c.faint)
                Spacer(Modifier.height(8.dp))
                SwitchRow("Brilhos mágicos", settings.glowEffects, { v -> repo.updateSettings { it.copy(glowEffects = v) } }, description = "Halo luminoso suave em painéis e botões e selos animados.")
                SwitchRow("Ornamentos dourados", settings.ornaments, { v -> repo.updateSettings { it.copy(ornaments = v) } }, description = "Cantoneiras decorativas nos painéis.")
                Text("Tamanho do texto", style = MaterialTheme.typography.bodySmall, color = c.muted)
                Spacer(Modifier.height(4.dp))
                ChoiceChips(TextScale.entries, settings.textScale, { it.label }, { s -> repo.updateSettings { it.copy(textScale = s) } })
            }

            OrnatePanel(Modifier.fillMaxWidth()) {
                SectionTitle("Preferências de interface")
                SwitchRow("Animação dos dados", settings.diceAnimation, { v -> repo.updateSettings { it.copy(diceAnimation = v) } })
                SwitchRow("Vibração ao rolar", settings.haptics, { v -> repo.updateSettings { it.copy(haptics = v) } })
                SwitchRow("Destacar resultado de rolagens rápidas", settings.showRollPopup, { v -> repo.updateSettings { it.copy(showRollPopup = v) } }, description = "Ao tocar em perícias, atributos ou magias.")
                SwitchRow("Biblioteca compacta", settings.compactLibrary, { v -> repo.updateSettings { it.copy(compactLibrary = v) } })
                Spacer(Modifier.height(6.dp))
                DropdownField(
                    "Categoria padrão de novas fichas",
                    listOf<Catalog?>(null) + catalogs.sortedBy { it.order },
                    catalogs.firstOrNull { it.id == settings.defaultCatalogId },
                    { it?.name ?: "Nenhuma" },
                    { cat -> repo.updateSettings { it.copy(defaultCatalogId = cat?.id) } },
                )
            }

            OrnatePanel(Modifier.fillMaxWidth()) {
                SectionTitle("Backup e restauração")
                Text(
                    "O backup é um arquivo .json com todas as fichas, catálogos, imagens e ajustes. Guarde-o no Drive, envie por mensagem ou copie para outro aparelho e restaure lá.",
                    style = MaterialTheme.typography.bodyMedium, color = c.muted,
                )
                Text("Último backup: ${formatDate(settings.lastBackupAt)}", style = MaterialTheme.typography.bodySmall, color = c.faint, modifier = Modifier.padding(vertical = 6.dp))
                ArcaneButton("Exportar backup completo", {
                    val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
                    backupLauncher.launch("codice-umbral-backup_$stamp.json")
                }, Modifier.fillMaxWidth(), icon = Icons.Filled.Backup)
                Spacer(Modifier.height(8.dp))
                GhostButton("Importar backup ou ficha (.json)", importer, Modifier.fillMaxWidth(), icon = Icons.Filled.FileDownload)
                Spacer(Modifier.height(4.dp))
                Hint("Para exportar uma única ficha, use o menu ⋮ na Biblioteca ou dentro da ficha.")
            }

            OrnatePanel(Modifier.fillMaxWidth()) {
                SectionTitle("Gerenciamento de dados")
                Text("${chars.size} fichas · ${catalogs.size} catálogos · ${chars.count { it.isDemo }} exemplos", style = MaterialTheme.typography.bodyMedium, color = c.text)
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Adicionar personagens de exemplo (fictícios)", { vm.loadDemo() }, Modifier.fillMaxWidth(), icon = Icons.Filled.Group)
                    GhostButton("Remover personagens de exemplo", { confirm = "demo" }, Modifier.fillMaxWidth(), icon = Icons.Filled.GroupRemove, color = c.warning)
                    GhostButton("Restaurar catálogos originais", { confirm = "catalogs" }, Modifier.fillMaxWidth(), icon = Icons.Filled.Restore)
                    GhostButton("Limpar imagens não usadas", {
                        scope.launch { repo.cleanupImages(); vm.toast("Imagens órfãs removidas") }
                    }, Modifier.fillMaxWidth(), icon = Icons.Filled.CleaningServices, color = c.muted)
                    GhostButton("Apagar todos os dados", { wipeText = "" }, Modifier.fillMaxWidth(), icon = Icons.Filled.DeleteForever, color = c.danger)
                }
                SwitchRow("Oferecer exemplos na tela inicial", settings.offerDemo, { v -> repo.updateSettings { it.copy(offerDemo = v) } })
            }

            OrnatePanel(Modifier.fillMaxWidth()) {
                SectionTitle("Geral")
                Text("Códice Umbral 1.2.0", style = MaterialTheme.typography.titleMedium, color = c.text)
                Text(
                    "Grimório digital de fichas de RPG para fantasia, dark fantasy, sobrenatural, paranormal, horror e ocultismo. " +
                        "Funciona sem internet e sem conta: os dados ficam no armazenamento privado deste aparelho, com salvamento automático.",
                    style = MaterialTheme.typography.bodySmall, color = c.muted,
                )
                Spacer(Modifier.height(6.dp))
                Row { Text("Personagens e textos de exemplo são fictícios.", style = MaterialTheme.typography.bodySmall, color = c.faint); Spacer(Modifier.width(4.dp)) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    when (confirm) {
        "demo" -> ConfirmDialog("Remover exemplos?", "As fichas marcadas como “Exemplo” serão excluídas. Suas fichas não serão afetadas.", "Remover",
            { vm.removeDemo(); confirm = null }, { confirm = null }, destructive = true)
        "catalogs" -> ConfirmDialog("Restaurar catálogos?", "Os 8 catálogos originais voltam ao conteúdo padrão (edições neles serão perdidas). Categorias criadas por você são mantidas.", "Restaurar",
            { repo.restoreBuiltInCatalogs(); vm.toast("Catálogos originais restaurados"); confirm = null }, { confirm = null })
    }
    if (choosingColor) {
        ColorChooserDialog(
            "Cor do aplicativo", settings.customAccent ?: settings.accent.primary,
            onDismiss = { choosingColor = false },
            onPick = { argb -> repo.updateSettings { it.copy(customAccent = argb) }; choosingColor = false; vm.toast("Cor aplicada: ${ColorMath.toHex(argb)}") },
        )
    }
    wipeText?.let { typed ->
        EditorDialog(
            "Apagar todos os dados",
            onDismiss = { wipeText = null },
            onSave = { repo.deleteAll(); wipeText = null; vm.toast("Todos os dados foram apagados") },
            saveEnabled = typed.trim().equals("APAGAR", ignoreCase = true),
            saveLabel = "Apagar tudo",
        ) {
            Text("Todas as ${chars.size} fichas, imagens, catálogos editados e ajustes serão removidos deste aparelho. Faça um backup antes.", color = c.danger, style = MaterialTheme.typography.bodyMedium)
            CodexTextField(typed, { wipeText = it }, "Digite APAGAR para confirmar")
        }
    }
}
