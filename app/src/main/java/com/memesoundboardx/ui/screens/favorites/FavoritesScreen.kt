package com.memesoundboardx.ui.screens.favorites

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.memesoundboardx.domain.repository.CategoryRepository
import com.memesoundboardx.domain.repository.SoundRepository
import com.memesoundboardx.domain.usecase.ToggleFavoriteUseCase
import com.memesoundboardx.ui.components.EmptyState
import com.memesoundboardx.ui.components.SoundList
import com.memesoundboardx.ui.screens.library.SoundListViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    sounds: SoundRepository,
    categories: CategoryRepository,
    toggleFavorite: ToggleFavoriteUseCase,
) : SoundListViewModel(sounds.observeFavorites(), categories, toggleFavorite)

@Composable
fun FavoritesScreen(viewModel: FavoritesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.loading && state.sounds.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.FavoriteBorder,
            title = "Nenhum favorito",
            message = "Toque no coração de qualquer som para fixá-lo aqui.",
        )
    } else {
        SoundList(state.sounds, state.categoryNames, viewModel::onToggleFavorite)
    }
}
