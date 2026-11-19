package com.memesoundboardx.ui.screens.myaudio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.memesoundboardx.domain.model.Category
import com.memesoundboardx.domain.model.ImportReport
import com.memesoundboardx.domain.model.Sound
import com.memesoundboardx.domain.model.PendingItem
import com.memesoundboardx.domain.repository.AudioImporter
import com.memesoundboardx.domain.repository.LibrarySyncer
import com.memesoundboardx.domain.repository.CategoryRepository
import com.memesoundboardx.domain.repository.SoundPlayer
import com.memesoundboardx.domain.repository.SoundRepository
import com.memesoundboardx.domain.usecase.NameRules
import com.memesoundboardx.domain.usecase.NameValidation
import com.memesoundboardx.domain.usecase.RenameSoundUseCase
import com.memesoundboardx.domain.usecase.ToggleFavoriteUseCase
import com.memesoundboardx.ui.components.AppIcons
import com.memesoundboardx.ui.components.EmptyState
import com.memesoundboardx.ui.components.SoundList
import com.memesoundboardx.ui.screens.library.SoundListViewModel
import com.memesoundboardx.ui.theme.MonoLabel
import com.memesoundboardx.ui.theme.NeonColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ImportUiState {
    data object Idle : ImportUiState
    data class Running(val count: Int) : ImportUiState
    data class Done(val report: ImportReport) : ImportUiState
}

@HiltViewModel
class MyAudioViewModel @Inject constructor(
    private val sounds: SoundRepository,
    categories: CategoryRepository,
    toggleFavorite: ToggleFavoriteUseCase,
    private val renameSound: RenameSoundUseCase,
    private val importer: AudioImporter,
    private val player: SoundPlayer,
    syncer: LibrarySyncer,
) : SoundListViewModel(sounds.observeImported(), categories, toggleFavorite) {

    /** Memes conhecidos ainda sem arquivo (some da lista quando você importa o arquivo dele). */
    val pending: StateFlow<List<PendingItem>> = combine(syncer.pending, sounds.observeFulfilledPending()) { all, done ->
        all.filter { it.id !in done }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun importForPending(uri: String, item: PendingItem) {
        if (_import.value is ImportUiState.Running) return
        viewModelScope.launch {
            _import.value = ImportUiState.Running(1)
            _import.value = ImportUiState.Done(importer.importForPending(uri, item))
        }
    }

    val categoryList: StateFlow<List<Category>> = categories.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _import = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val importState: StateFlow<ImportUiState> = _import.asStateFlow()

    fun import(uris: List<String>) {
        if (uris.isEmpty() || _import.value is ImportUiState.Running) return
        viewModelScope.launch {
            _import.value = ImportUiState.Running(uris.size)
            val report = importer.import(uris)
            _import.value = ImportUiState.Done(report)
        }
    }

    fun dismissReport() { _import.value = ImportUiState.Idle }

    fun rename(sound: Sound, newName: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val error = when (renameSound(sound.id, newName)) {
                is NameValidation.Valid, NameValidation.Duplicate -> null
                NameValidation.Empty -> "Digite um nome."
                NameValidation.TooLong -> "Máximo de ${NameRules.MAX_LENGTH} caracteres."
            }
            onResult(error)
        }
    }

    fun move(sound: Sound, categoryId: String?) {
        viewModelScope.launch { sounds.setCategory(sound.id, categoryId) }
    }

    /** Para o som (se estiver tocando), remove da biblioteca e apaga a cópia interna. */
    fun delete(sound: Sound) {
        player.stop(sound.id)
        viewModelScope.launch { sounds.delete(sound.id) }
    }
}

private val IMPORT_MIME_TYPES = arrayOf("audio/*", "application/ogg")

@Composable
fun MyAudioScreen(viewModel: MyAudioViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val categories by viewModel.categoryList.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<Sound?>(null) }
    var moving by remember { mutableStateOf<Sound?>(null) }
    var deleting by remember { mutableStateOf<Sound?>(null) }
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    var pendingTarget by remember { mutableStateOf<PendingItem?>(null) }
    val pendingPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = pendingTarget
        if (uri != null && target != null) viewModel.importForPending(uri.toString(), target)
        pendingTarget = null
    }

    // Storage Access Framework: seletor de arquivos do sistema, sem permissão de armazenamento.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.import(uris.map { it.toString() })
    }

    Column(Modifier.fillMaxSize()) {
        val running = importState is ImportUiState.Running
        Button(
            onClick = { picker.launch(IMPORT_MIME_TYPES) },
            enabled = !running,
            colors = ButtonDefaults.buttonColors(containerColor = NeonColors.Purple),
            modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 52.dp),
        ) {
            if (running) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = NeonColors.Text)
                Spacer(Modifier.width(10.dp))
                Text("Importando ${(importState as ImportUiState.Running).count} arquivo(s)…")
            } else {
                Icon(AppIcons.Folder, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("Importar áudios (MP3, WAV, OGG, M4A)")
            }
        }

        Box(Modifier.weight(1f)) {
            run {
                SoundList(
                    sounds = state.sounds,
                    categoryNames = state.categoryNames,
                    onToggleFavorite = viewModel::onToggleFavorite,
                    header = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (pending.isNotEmpty()) {
                                PendingSection(pending, state.categoryNames) { item ->
                                    pendingTarget = item
                                    pendingPicker.launch(IMPORT_MIME_TYPES)
                                }
                            }
                            if (!state.loading && state.sounds.isEmpty()) {
                                Text(
                                    "Nenhum áudio importado ainda. Toque em Importar e escolha um ou vários arquivos. " +
                                        "O app guarda uma cópia, então o som continua funcionando mesmo se o original for movido. " +
                                        "O arquivo original nunca é apagado.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NeonColors.TextDim,
                                )
                            } else {
                                com.memesoundboardx.ui.components.SectionTitle("Meus áudios (${state.sounds.size})")
                            }
                        }
                    },
                    trailing = { sound ->
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Mais opções", tint = NeonColors.TextDim)
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Renomear") }, onClick = { menu = false; renaming = sound })
                                DropdownMenuItem(text = { Text("Mover para categoria") }, onClick = { menu = false; moving = sound })
                                DropdownMenuItem(
                                    text = { Text("Remover", color = NeonColors.Danger) },
                                    onClick = { menu = false; deleting = sound },
                                )
                            }
                        }
                    },
                )
            }
        }
    }

    (importState as? ImportUiState.Done)?.let { done ->
        ImportReportDialog(done.report, onDismiss = viewModel::dismissReport)
    }

    renaming?.let { sound ->
        var name by remember(sound.id) { mutableStateOf(sound.name) }
        var error by remember(sound.id) { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Renomear") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.rename(sound, name) { err -> if (err == null) renaming = null else error = err }
                }) { Text("Salvar") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancelar") } },
        )
    }

    moving?.let { sound ->
        AlertDialog(
            onDismissRequest = { moving = null },
            title = { Text("Mover \"${sound.name}\"") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    categories.forEach { cat ->
                        TextButton(
                            onClick = { viewModel.move(sound, cat.id); moving = null },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                (if (cat.id == sound.categoryId) "✓ " else "") + cat.name,
                                modifier = Modifier.fillMaxWidth(),
                                color = if (cat.id == sound.categoryId) NeonColors.Blue else NeonColors.Text,
                            )
                        }
                    }
                    TextButton(onClick = { viewModel.move(sound, null); moving = null }, modifier = Modifier.fillMaxWidth()) {
                        Text("Sem categoria", modifier = Modifier.fillMaxWidth(), color = NeonColors.TextDim)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { moving = null }) { Text("Fechar") } },
        )
    }

    deleting?.let { sound ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Remover \"${sound.name}\"?") },
            text = { Text("O som sai da biblioteca e a cópia interna é apagada. O arquivo original no seu celular não é tocado.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(sound); deleting = null }) {
                    Text("Remover", color = NeonColors.Danger)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun ImportReportDialog(report: ImportReport, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Importação concluída") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✓ ${report.imported.size} importado(s)", color = NeonColors.Cyan, style = MaterialTheme.typography.titleMedium)
                }
                if (report.duplicates.isNotEmpty()) {
                    Text("${report.duplicates.size} já estava(m) na biblioteca", color = NeonColors.TextDim)
                }
                if (report.failures.isNotEmpty()) {
                    Text("${report.failures.size} recusado(s):", color = NeonColors.Danger)
                    report.failures.take(20).forEach { f ->
                        Text("• ${f.fileName}: ${f.reason}", style = MonoLabel, color = NeonColors.TextDim)
                    }
                }
                if (report.imported.isNotEmpty()) {
                    Text(
                        "Os sons foram colocados em categorias pelo nome do arquivo. Use ⋮ → Mover para trocar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeonColors.TextDim,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

/** Memes conhecidos que não têm fonte autorizada: você escolhe o arquivo e ele entra com nome e categoria certos. */
@Composable
private fun PendingSection(items: List<PendingItem>, categoryNames: Map<String, String>, onPick: (PendingItem) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    com.memesoundboardx.ui.components.NeonCard(Modifier.fillMaxWidth(), onClick = { expanded = !expanded }) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Memes aguardando arquivo (${items.size})", style = MaterialTheme.typography.titleMedium, color = NeonColors.Warning)
            Text(
                "Sem fonte com licença que permita incluir no app. Se você tiver o arquivo, toque em Escolher arquivo. " +
                    if (expanded) "" else "Toque para ver a lista.",
                style = MaterialTheme.typography.bodySmall, color = NeonColors.TextDim,
            )
            if (expanded) {
                items.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                listOfNotNull(categoryNames[item.categoryId], item.hint, item.reason).joinToString(" · "),
                                style = MonoLabel, color = NeonColors.TextDim,
                            )
                        }
                        TextButton(onClick = { onPick(item) }) { Text("Escolher arquivo") }
                    }
                }
            }
        }
    }
}
