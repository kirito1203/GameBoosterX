package com.memesoundboardx.ui.screens.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.memesoundboardx.domain.model.Category
import com.memesoundboardx.domain.repository.CategoryRepository
import com.memesoundboardx.domain.repository.SoundRepository
import com.memesoundboardx.domain.usecase.CreateCategoryUseCase
import com.memesoundboardx.domain.usecase.NameRules
import com.memesoundboardx.domain.usecase.NameValidation
import com.memesoundboardx.domain.usecase.ToggleFavoriteUseCase
import com.memesoundboardx.ui.components.EmptyState
import com.memesoundboardx.ui.components.NeonCard
import com.memesoundboardx.ui.components.SectionTitle
import com.memesoundboardx.ui.components.SoundList
import com.memesoundboardx.ui.navigation.Routes
import com.memesoundboardx.ui.screens.library.SoundListViewModel
import com.memesoundboardx.ui.theme.MonoLabel
import com.memesoundboardx.ui.theme.NeonColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val categories: CategoryRepository,
    private val createCategory: CreateCategoryUseCase,
) : ViewModel() {

    val categoryList: StateFlow<List<Category>?> = categories.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _createError = MutableStateFlow<String?>(null)
    val createError: StateFlow<String?> = _createError.asStateFlow()

    fun create(name: String, onCreated: () -> Unit) {
        viewModelScope.launch {
            when (val r = createCategory(name)) {
                is CreateCategoryUseCase.Result.Created -> { _createError.value = null; onCreated() }
                is CreateCategoryUseCase.Result.Invalid -> _createError.value = when (r.reason) {
                    NameValidation.Empty -> "Digite um nome."
                    NameValidation.TooLong -> "Máximo de ${NameRules.MAX_LENGTH} caracteres."
                    NameValidation.Duplicate -> "Já existe uma categoria com esse nome."
                    is NameValidation.Valid -> null
                }
            }
        }
    }

    fun clearError() { _createError.value = null }

    fun delete(category: Category) {
        if (!category.isCustom) return
        viewModelScope.launch { categories.deleteCustom(category.id) }
    }
}

@Composable
fun CategoriesScreen(
    onOpenCategory: (String) -> Unit,
    viewModel: CategoriesViewModel = hiltViewModel(),
) {
    val list by viewModel.categoryList.collectAsStateWithLifecycle()
    val createError by viewModel.createError.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Category?>(null) }

    Box(Modifier.fillMaxSize()) {
        val cats = list
        if (cats != null && cats.isEmpty()) {
            EmptyState(Icons.Filled.Menu, "Sem categorias", "As categorias padrão são criadas ao abrir o app.")
        } else if (cats != null) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val builtIn = cats.filter { !it.isCustom }
                val custom = cats.filter { it.isCustom }
                if (builtIn.isNotEmpty()) item { SectionTitle("Padrão") }
                items(builtIn, key = { it.id }) { CategoryCard(it, onOpenCategory, onDelete = null) }
                if (custom.isNotEmpty()) item { SectionTitle("Minhas categorias") }
                items(custom, key = { it.id }) { CategoryCard(it, onOpenCategory, onDelete = { toDelete = it }) }
            }
        }

        ExtendedFloatingActionButton(
            onClick = { viewModel.clearError(); showCreate = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Nova categoria") },
            containerColor = NeonColors.Purple,
            contentColor = NeonColors.Text,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Nova categoria") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; viewModel.clearError() },
                    singleLine = true,
                    label = { Text("Nome") },
                    isError = createError != null,
                    supportingText = { createError?.let { Text(it) } },
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.create(name) { showCreate = false } }) { Text("Criar") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancelar") } },
        )
    }

    toDelete?.let { cat ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Excluir \"${cat.name}\"?") },
            text = { Text("Os ${cat.soundCount} sons desta categoria continuam na biblioteca, sem categoria.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(cat); toDelete = null }) {
                    Text("Excluir", color = NeonColors.Danger)
                }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun CategoryCard(category: Category, onOpen: (String) -> Unit, onDelete: (() -> Unit)?) {
    NeonCard(Modifier.fillMaxWidth(), onClick = { onOpen(category.id) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(category.name, style = MaterialTheme.typography.titleMedium)
                val target = category.targetCount
                if (target != null) {
                    Text("${category.soundCount} / $target sons", style = MonoLabel, color = NeonColors.TextDim)
                    LinearProgressIndicator(
                        progress = { (category.soundCount.toFloat() / target).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        color = NeonColors.Blue,
                        trackColor = NeonColors.Outline,
                    )
                } else {
                    Text("${category.soundCount} sons", style = MonoLabel, color = NeonColors.TextDim)
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Excluir categoria", tint = NeonColors.TextDim)
                }
            }
        }
    }
}

@HiltViewModel
class CategoryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    sounds: SoundRepository,
    categories: CategoryRepository,
    toggleFavorite: ToggleFavoriteUseCase,
) : SoundListViewModel(
    sounds.observeByCategory(checkNotNull(savedStateHandle.get<String>(Routes.ARG_CATEGORY_ID))),
    categories,
    toggleFavorite,
) {
    val categoryId: String = checkNotNull(savedStateHandle.get<String>(Routes.ARG_CATEGORY_ID))
}

@Composable
fun CategoryDetailScreen(viewModel: CategoryDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val title = state.categoryNames[viewModel.categoryId] ?: ""
    if (!state.loading && state.sounds.isEmpty()) {
        EmptyState(Icons.Filled.Menu, title.ifEmpty { "Categoria" }, "Nenhum som nesta categoria ainda.")
    } else {
        SoundList(
            sounds = state.sounds,
            categoryNames = state.categoryNames,
            onToggleFavorite = viewModel::onToggleFavorite,
            header = { SectionTitle(title) },
        )
    }
}
