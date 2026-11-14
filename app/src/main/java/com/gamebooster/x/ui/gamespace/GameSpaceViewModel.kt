package com.gamebooster.x.ui.gamespace

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gamebooster.x.AppContainer
import com.gamebooster.x.GameBoosterApp
import com.gamebooster.x.core.GameSessionRecord
import com.gamebooster.x.core.GameSpaceSettings
import com.gamebooster.x.core.GameStats
import com.gamebooster.x.core.GameStatsAggregator
import com.gamebooster.x.gamespace.GameSpaceService
import com.gamebooster.x.gamespace.GsPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GameSpaceUiState(
    val serviceRunning: Boolean = false,
    val settings: GameSpaceSettings = GameSpaceSettings(),
    val permissions: Map<GsPermission, Boolean> = emptyMap(),
    val stats: Map<String, GameStats> = emptyMap(),
    val recent: List<GameSessionRecord> = emptyList(),
)

class GameSpaceViewModel(private val container: AppContainer) : ViewModel() {

    private val permissions = MutableStateFlow(readPermissions())

    val state: StateFlow<GameSpaceUiState> = combine(
        GameSpaceService.running,
        container.settings.gameSpaceSettings,
        permissions,
        container.sessions.sessions,
    ) { running, settings, perms, sessions ->
        GameSpaceUiState(
            serviceRunning = running,
            settings = settings,
            permissions = perms,
            stats = GameStatsAggregator.aggregate(sessions),
            recent = sessions.sortedByDescending { it.startMs }.take(10),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GameSpaceUiState(permissions = permissions.value))

    private fun readPermissions(): Map<GsPermission, Boolean> =
        GsPermission.entries.associateWith { runCatching { container.controls.isGranted(it) }.getOrDefault(false) }

    /** Chamado ao voltar das Configurações do Android. */
    fun refreshPermissions() {
        permissions.value = readPermissions()
        container.shizuku.refresh()
    }

    fun permissionIntent(p: GsPermission): Intent = container.controls.settingsIntent(p)

    fun appDetailsIntent(): Intent = container.controls.appDetailsIntent()

    fun update(transform: (GameSpaceSettings) -> GameSpaceSettings) {
        viewModelScope.launch { container.settings.setGameSpaceSettings(transform(state.value.settings)) }
    }

    fun clearHistory() {
        viewModelScope.launch { container.sessions.clear() }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as GameBoosterApp
                GameSpaceViewModel(app.container)
            }
        }
    }
}
