package com.memesoundboardx.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memesoundboardx.core.util.Formatters
import com.memesoundboardx.domain.model.Sound
import com.memesoundboardx.domain.model.SoundSource
import com.memesoundboardx.domain.repository.SoundPlayer
import com.memesoundboardx.domain.repository.SoundRepository
import com.memesoundboardx.ui.theme.MonoLabel
import com.memesoundboardx.ui.theme.NeonColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SoundActionsViewModel @Inject constructor(
    private val sounds: SoundRepository,
    private val player: SoundPlayer,
    categories: com.memesoundboardx.domain.repository.CategoryRepository,
) : ViewModel() {
    val categoryNames: StateFlow<Map<String, String>> = categories.observeCategories()
        .map { l -> l.associate { it.id to it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun toggleFavorite(sound: Sound) {
        viewModelScope.launch { sounds.setFavorite(sound.id, !sound.isFavorite) }
    }

    fun remove(sound: Sound) {
        player.stop(sound.id)
        viewModelScope.launch { sounds.delete(sound.id) }
    }
}

/** Detalhes de um som: origem, autor, licença e link (atribuição exigida por CC BY/BY-SA). */
@Composable
fun SoundDetailsDialog(
    sound: Sound,
    categoryName: String?,
    onToggleFavorite: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val uri = LocalUriHandler.current
    var confirm by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(sound.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Line("Categoria", categoryName ?: "Sem categoria")
                Line("Duração", Formatters.duration(sound.durationMs))
                if (sound.sizeBytes != null) Line("Tamanho", Formatters.size(sound.sizeBytes))
                Line("Origem", if (sound.source == SoundSource.IMPORTED) "Importado por você" else "Embutido no app")
                Line("Licença", sound.license.label)
                sound.author?.let { Line("Autor", it) }
                sound.sourceUrl?.let { url ->
                    Text("Fonte", style = MonoLabel, color = NeonColors.TextDim)
                    Text(
                        url,
                        style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.Underline),
                        color = NeonColors.Blue,
                        modifier = Modifier.clickable { runCatching { uri.openUri(url) } },
                    )
                }
                if (confirm) {
                    Text(
                        if (sound.source == SoundSource.IMPORTED) {
                            "Remover da biblioteca? A cópia interna é apagada; o arquivo original no celular continua."
                        } else {
                            "Remover da biblioteca? Ele some das listas. Dá para restaurar em Configurações."
                        },
                        color = NeonColors.Warning,
                    )
                }
            }
        },
        confirmButton = {
            if (confirm) {
                TextButton(onClick = { onRemove(); onDismiss() }) { Text("Remover", color = NeonColors.Danger) }
            } else {
                TextButton(onClick = onToggleFavorite) {
                    Text(if (sound.isFavorite) "Desfavoritar" else "Favoritar")
                }
            }
        },
        dismissButton = {
            if (confirm) {
                TextButton(onClick = { confirm = false }) { Text("Cancelar") }
            } else {
                TextButton(onClick = { confirm = true }) { Text("Remover…", color = NeonColors.Danger) }
            }
        },
    )
}

@Composable
private fun Line(label: String, value: String) {
    Column {
        Text(label, style = MonoLabel, color = NeonColors.TextDim)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
