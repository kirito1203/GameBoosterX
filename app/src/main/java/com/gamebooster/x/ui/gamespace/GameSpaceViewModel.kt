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
    val sidebarApps: Set<String> = emptySet(),
    val launchableApps: List<Pair<String, String>> = emptyList(),
    /** null = não verificado (Shizuku parado). */
    val freeformEnabled: Boolean? = null,
    val freeformMessage: String? = null,
)

private data class FreeformState(val enabled: Boolean? = null, val message: String? = null)

class GameSpaceViewModel(private val container: AppContainer) : ViewModel() {

    private val permissions = MutableStateFlow(readPermissions())
    private val launchable = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    private val freeform = MutableStateFlow(FreeformState())

    private val base = combine(
        GameSpaceService.running,
        container.settings.gameSpaceSettings,
        permissions,
        container.sessions.sessions,
        container.settings.sidebarApps,
    ) { running, settings, perms, sessions, sidebar ->
        GameSpaceUiState(
            serviceRunning = running,
            settings = settings,
            permissions = perms,
            stats = GameStatsAggregator.aggregate(sessions),
            recent = sessions.sortedByDescending { it.startMs }.take(10),
            sidebarApps = sidebar,
        )
    }

    val state: StateFlow<GameSpaceUiState> = combine(base, launchable, freeform) { s, apps, ff ->
        s.copy(launchableApps = apps, freeformEnabled = ff.enabled, freeformMessage = ff.message)
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

    init {
        viewModelScope.launch { launchable.value = container.games.launchableApps() }
    }

    /** Lê as opções do desenvolvedor "janelas de formato livre" (via Shizuku). */
    fun checkFreeform() {
        viewModelScope.launch {
            if (!container.shizuku.isReady) {
                freeform.value = FreeformState(null, null)
                return@launch
            }
            val r = container.shizuku.run("settings get global enable_freeform_support")
            freeform.value = freeform.value.copy(enabled = r.output.trim() == "1")
        }
    }

    /**
     * Liga/desliga as opções do desenvolvedor que permitem abrir apps em janela flutuante
     * ("Ativar janelas de formato livre" e "Forçar atividades redimensionáveis"). Reversível.
     */
    fun setFreeform(enable: Boolean) {
        viewModelScope.launch {
            val v = if (enable) "1" else "0"
            val results = listOf(
                container.shizuku.run("settings put global enable_freeform_support $v"),
                container.shizuku.run("settings put global force_resizable_activities $v"),
            )
            val ok = results.all { it.ok }
            freeform.value = FreeformState(
                enabled = if (ok) enable else freeform.value.enabled,
                message = when {
                    !ok -> "O sistema recusou: ${results.firstOrNull { !it.ok }?.output?.take(120)}"
                    enable -> "Ativado. Se ainda abrir em tela cheia, reinicie o celular e inicie o Shizuku de novo."
                    else -> "Desativado."
                },
            )
        }
    }

    fun toggleSidebarApp(packageName: String) {
        viewModelScope.launch { container.settings.toggleSidebarApp(packageName) }
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
