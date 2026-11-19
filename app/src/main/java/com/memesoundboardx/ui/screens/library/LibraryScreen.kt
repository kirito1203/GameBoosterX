package com.memesoundboardx.ui.screens.library

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.memesoundboardx.domain.model.Sound
import com.memesoundboardx.domain.repository.CategoryRepository
import com.memesoundboardx.domain.repository.SoundRepository
import com.memesoundboardx.domain.usecase.ToggleFavoriteUseCase
import com.memesoundboardx.ui.components.EmptyState
import com.memesoundboardx.ui.components.SoundList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Estado comum das telas que listam sons. */
data class SoundListUiState(
    val loading: Boolean = true,
    val sounds: List<Sound> = emptyList(),
    val categoryNames: Map<String, String> = emptyMap(),
)

fun CategoryRepository.categoryNames(): Flow<Map<String, String>> =
    observeCategories().map { list -> list.associate { it.id to it.name } }

/** Base para ViewModels que exibem uma lista de sons com favoritos. */
abstract class SoundListViewModel(
    soundsFlow: Flow<List<Sound>>,
    categories: CategoryRepository,
    private val toggleFavorite: ToggleFavoriteUseCase,
) : ViewModel() {
    val state: StateFlow<SoundListUiState> = combine(soundsFlow, categories.categoryNames()) { s, names ->
        SoundListUiState(loading = false, sounds = s, categoryNames = names)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SoundListUiState())

    fun onToggleFavorite(sound: Sound) {
        viewModelScope.launch { toggleFavorite(sound) }
    }
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    sounds: SoundRepository,
    categories: CategoryRepository,
    toggleFavorite: ToggleFavoriteUseCase,
) : SoundListViewModel(sounds.observeAll(), categories, toggleFavorite)

@Composable
fun LibraryScreen(viewModel: LibraryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.loading && state.sounds.isEmpty()) {
        EmptyState(
            icon = Icons.AutoMirrored.Filled.List,
            title = "Biblioteca vazia",
            message = "Nenhum som embutido ainda. A biblioteca está pronta para receber mais de 350 efeitos: " +
                "sons licenciados entram pelo manifesto e os seus pela tela Meus áudios (ETAPA 5).",
        )
    } else {
        SoundList(state.sounds, state.categoryNames, viewModel::onToggleFavorite)
    }
}
