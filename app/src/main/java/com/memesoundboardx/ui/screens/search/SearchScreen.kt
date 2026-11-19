package com.memesoundboardx.ui.screens.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.memesoundboardx.domain.model.Sound
import com.memesoundboardx.domain.repository.CategoryRepository
import com.memesoundboardx.domain.usecase.SearchSoundsUseCase
import com.memesoundboardx.domain.usecase.ToggleFavoriteUseCase
import com.memesoundboardx.ui.components.EmptyState
import com.memesoundboardx.ui.components.SoundList
import com.memesoundboardx.ui.screens.library.categoryNames
import com.memesoundboardx.ui.theme.NeonColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val search: SearchSoundsUseCase,
    categories: CategoryRepository,
    private val toggleFavorite: ToggleFavoriteUseCase,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Debounce de 200 ms: não consulta o banco a cada tecla. */
    val results: StateFlow<List<Sound>> = _query
        .debounce(200)
        .distinctUntilChanged()
        .flatMapLatest { search(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categoryNames: StateFlow<Map<String, String>> = categories.categoryNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun onQueryChange(q: String) { _query.value = q }

    fun onToggleFavorite(sound: Sound) {
        viewModelScope.launch { toggleFavorite(sound) }
    }
}

@Composable
fun SearchScreen(viewModel: SearchViewModel = hiltViewModel()) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val names by viewModel.categoryNames.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            singleLine = true,
            placeholder = { Text("Buscar por nome ou tag (acentos são ignorados)") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Limpar")
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NeonColors.Blue,
                unfocusedBorderColor = NeonColors.Outline,
            ),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        when {
            query.isBlank() -> EmptyState(Icons.Filled.Search, "Pesquisar sons", "Digite parte do nome ou de uma tag.")
            results.isEmpty() -> EmptyState(Icons.Filled.Search, "Nada encontrado", "Nenhum som corresponde a \"$query\".")
            else -> SoundList(results, names, viewModel::onToggleFavorite)
        }
    }
}
