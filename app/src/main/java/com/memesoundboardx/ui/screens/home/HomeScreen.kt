package com.memesoundboardx.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.memesoundboardx.domain.model.LibraryStats
import com.memesoundboardx.domain.repository.CategoryRepository
import com.memesoundboardx.domain.repository.SoundRepository
import com.memesoundboardx.ui.components.NeonCard
import com.memesoundboardx.ui.components.NoticeBanner
import com.memesoundboardx.ui.components.SectionTitle
import com.memesoundboardx.ui.components.StatTile
import com.memesoundboardx.ui.navigation.Routes
import com.memesoundboardx.ui.theme.MonoLabel
import com.memesoundboardx.ui.theme.NeonColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeUiState(
    val stats: LibraryStats = LibraryStats(0, 0, 0, 0),
    val categoryCount: Int = 0,
    val libraryTarget: Int = 0,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    sounds: SoundRepository,
    categories: CategoryRepository,
) : ViewModel() {
    val state: StateFlow<HomeUiState> = combine(
        sounds.observeStats(),
        categories.observeCategories(),
    ) { stats, cats ->
        HomeUiState(
            stats = stats,
            categoryCount = cats.size,
            libraryTarget = cats.sumOf { it.targetCount ?: 0 },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}

@Composable
fun HomeScreen(
    onOpenMyAudio: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTab: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "MEME SOUNDBOARD X",
            style = MaterialTheme.typography.headlineLarge,
            color = NeonColors.Blue,
        )
        Text("versão 0.1.0 · ETAPA 1 (estrutura)", style = MonoLabel, color = NeonColors.TextDim)

        NoticeBanner(
            title = "Reprodução ainda não disponível",
            message = "Esta versão contém a estrutura, o banco de dados e as telas. " +
                "O motor de áudio entra na ETAPA 3 e a importação na ETAPA 5.",
        )

        NoticeBanner(
            title = "Microfone do Roblox: sem suporte direto",
            message = "O Android não permite que um app injete áudio no microfone de outro sem root. " +
                "Toque para ver os detalhes.",
            onClick = onOpenSettings,
        )

        SectionTitle("Biblioteca")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile("sons", state.stats.total.toString(), Modifier.weight(1f))
            StatTile("favoritos", state.stats.favorites.toString(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile("importados", state.stats.imported.toString(), Modifier.weight(1f))
            StatTile("categorias", state.categoryCount.toString(), Modifier.weight(1f))
        }
        Text(
            "Capacidade planejada: ${state.libraryTarget} efeitos nas categorias padrão. " +
                "Embutidos no APK hoje: ${state.stats.bundled}.",
            style = MaterialTheme.typography.bodySmall,
            color = NeonColors.TextDim,
        )

        SectionTitle("Atalhos")
        NeonCard(Modifier.fillMaxWidth(), onClick = onOpenMyAudio) {
            Column {
                Text("Meus áudios", style = MaterialTheme.typography.titleMedium)
                Text("Sons importados do seu celular", style = MaterialTheme.typography.bodySmall, color = NeonColors.TextDim)
            }
        }
        NeonCard(Modifier.fillMaxWidth(), onClick = { onOpenTab(Routes.CATEGORIES) }) {
            Column {
                Text("Categorias", style = MaterialTheme.typography.titleMedium)
                Text("Padrão e personalizadas", style = MaterialTheme.typography.bodySmall, color = NeonColors.TextDim)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
