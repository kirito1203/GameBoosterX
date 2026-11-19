package com.memesoundboardx.ui.screens.myaudio

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.memesoundboardx.domain.model.Sound
import com.memesoundboardx.domain.repository.CategoryRepository
import com.memesoundboardx.domain.repository.SoundRepository
import com.memesoundboardx.domain.usecase.NameRules
import com.memesoundboardx.domain.usecase.NameValidation
import com.memesoundboardx.domain.usecase.RenameSoundUseCase
import com.memesoundboardx.domain.usecase.ToggleFavoriteUseCase
import com.memesoundboardx.ui.components.EmptyState
import com.memesoundboardx.ui.components.SoundList
import com.memesoundboardx.ui.screens.library.SoundListViewModel
import com.memesoundboardx.ui.theme.NeonColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MyAudioViewModel @Inject constructor(
    private val sounds: SoundRepository,
    categories: CategoryRepository,
    toggleFavorite: ToggleFavoriteUseCase,
    private val renameSound: RenameSoundUseCase,
) : SoundListViewModel(sounds.observeImported(), categories, toggleFavorite) {

    fun rename(sound: Sound, newName: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val error = when (renameSound(sound.id, newName)) {
                is NameValidation.Valid -> null
                NameValidation.Empty -> "Digite um nome."
                NameValidation.TooLong -> "Máximo de ${NameRules.MAX_LENGTH} caracteres."
                NameValidation.Duplicate -> null
            }
            onResult(error)
        }
    }

    /** Remove da biblioteca. O arquivo original no celular não é apagado. */
    fun delete(sound: Sound) {
        viewModelScope.launch { sounds.delete(sound.id) }
    }
}

@Composable
fun MyAudioScreen(viewModel: MyAudioViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<Sound?>(null) }
    var deleting by remember { mutableStateOf<Sound?>(null) }

    Column(Modifier.fillMaxSize()) {
        // Botão desativado de propósito: a importação via SAF é implementada na ETAPA 5.
        Button(
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) { Text("Importar áudios — disponível na ETAPA 5") }

        Box(Modifier.weight(1f)) {
            if (!state.loading && state.sounds.isEmpty()) {
                EmptyState(
                    Icons.Filled.AccountCircle,
                    "Nenhum áudio importado",
                    "Formatos planejados: MP3, WAV, OGG, M4A e AAC. Os arquivos originais nunca são apagados pelo app.",
                )
            } else {
                SoundList(
                    sounds = state.sounds,
                    categoryNames = state.categoryNames,
                    onToggleFavorite = viewModel::onToggleFavorite,
                    trailing = { sound ->
                        IconButton(onClick = { renaming = sound }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Renomear", tint = NeonColors.TextDim)
                        }
                        IconButton(onClick = { deleting = sound }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Excluir", tint = NeonColors.TextDim)
                        }
                    },
                )
            }
        }
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

    deleting?.let { sound ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Remover \"${sound.name}\"?") },
            text = { Text("O som sai da biblioteca. O arquivo original no seu celular não é apagado.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(sound); deleting = null }) {
                    Text("Remover", color = NeonColors.Danger)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } },
        )
    }
}
