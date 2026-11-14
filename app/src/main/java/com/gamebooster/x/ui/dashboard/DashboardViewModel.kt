package com.gamebooster.x.ui.dashboard

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gamebooster.x.AppContainer
import com.gamebooster.x.GameBoosterApp
import com.gamebooster.x.core.Advice
import com.gamebooster.x.core.BoostReport
import com.gamebooster.x.core.DashboardAdvisor
import com.gamebooster.x.core.DeviceIdentity
import com.gamebooster.x.core.DeviceProfile
import com.gamebooster.x.core.DeviceProfileResolver
import com.gamebooster.x.core.HardwareSnapshot
import com.gamebooster.x.core.InstalledGame
import com.gamebooster.x.core.MemoryAnalyzer
import com.gamebooster.x.core.MemoryAssessment
import com.gamebooster.x.core.ModePolicies
import com.gamebooster.x.core.ModePolicy
import com.gamebooster.x.core.PerformanceMode
import com.gamebooster.x.core.SampleHistory
import com.gamebooster.x.core.ThermalAnalyzer
import com.gamebooster.x.core.ThermalAssessment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BoostState(
    val running: Boolean = false,
    val last: BoostReport? = null,
    val error: String? = null,
)

data class MonitorState(
    val snapshot: HardwareSnapshot? = null,
    val memoryHistory: SampleHistory = SampleHistory(HISTORY_SIZE),
    val temperatureHistory: SampleHistory = SampleHistory(HISTORY_SIZE),
    val error: String? = null,
) {
    companion object {
        const val HISTORY_SIZE = 60
    }
}

data class DashboardUiState(
    val identity: DeviceIdentity? = null,
    val profile: DeviceProfile? = null,
    val mode: PerformanceMode = PerformanceMode.DEFAULT,
    val policy: ModePolicy = ModePolicies.of(PerformanceMode.DEFAULT),
    val monitor: MonitorState = MonitorState(),
    val thermal: ThermalAssessment? = null,
    val memory: MemoryAssessment? = null,
    val advice: List<Advice> = emptyList(),
    val games: List<InstalledGame> = emptyList(),
    val gamesLoaded: Boolean = false,
    val boost: BoostState = BoostState(),
)

class DashboardViewModel(private val container: AppContainer) : ViewModel() {

    private val device = MutableStateFlow<Pair<DeviceIdentity, DeviceProfile>?>(null)
    private val games = MutableStateFlow<List<InstalledGame>?>(null)
    private val boost = MutableStateFlow(BoostState())
    private val launches = Channel<Intent>(Channel.BUFFERED)

    /** Intents de jogos a abrir; a tela as consome e chama startActivity. */
    val launchRequests: Flow<Intent> = launches.receiveAsFlow()

    private val mode: Flow<PerformanceMode> = container.settings.performanceMode.distinctUntilChanged()

    /**
     * Monitor contínuo. O intervalo de leitura vem do modo ativo; trocar de modo
     * reinicia a coleta com o novo intervalo sem perder o histórico.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val monitor: Flow<MonitorState> = mode
        .flatMapLatest { m -> container.hardware.snapshots(ModePolicies.of(m).sampleIntervalMs) }
        .scan(MonitorState()) { acc, snap ->
            acc.copy(
                snapshot = snap,
                memoryHistory = acc.memoryHistory.add(snap.memory.usedPercent.toFloat()),
                temperatureHistory = snap.thermal.batteryCelsius
                    ?.let { acc.temperatureHistory.add(it) } ?: acc.temperatureHistory,
                error = null,
            )
        }
        .catch { e -> emit(MonitorState(error = "Falha ao ler hardware: ${e.message}")) }

    val state: StateFlow<DashboardUiState> =
        combine(device, games, boost, monitor, mode) { dev, gameList, boostState, mon, m ->
            val policy = ModePolicies.of(m)
            val snap = mon.snapshot
            val thermal = snap?.let { ThermalAnalyzer.assess(it.thermal, policy) }
            val memory = snap?.let { MemoryAnalyzer.assess(it.memory, policy) }
            val profile = dev?.second
            val advice = if (thermal != null && memory != null && profile != null) {
                DashboardAdvisor.advise(thermal, memory, policy, profile, snap?.display)
            } else emptyList()
            DashboardUiState(
                identity = dev?.first,
                profile = profile,
                mode = m,
                policy = policy,
                monitor = mon,
                thermal = thermal,
                memory = memory,
                advice = advice,
                games = gameList.orEmpty(),
                gamesLoaded = gameList != null,
                boost = boostState,
            )
        }.stateIn(
            scope = viewModelScope,
            // Para de ler o hardware 5 s depois que a tela sai de vista (economia de bateria).
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DashboardUiState(),
        )

    init {
        viewModelScope.launch {
            val identity = withContext(Dispatchers.IO) { container.hardware.identity() }
            device.value = identity to DeviceProfileResolver.resolve(identity)
        }
        refreshGames()
    }

    fun refreshGames() {
        viewModelScope.launch {
            games.value = runCatching { container.games.scan() }.getOrDefault(emptyList())
        }
    }

    fun setMode(newMode: PerformanceMode) {
        viewModelScope.launch { container.settings.setPerformanceMode(newMode) }
    }

    /** Otimiza; se [launchPackage] for informado, abre o jogo em seguida e o preserva na limpeza. */
    fun boost(launchPackage: String? = null) {
        if (boost.value.running) return
        viewModelScope.launch {
            boost.update { it.copy(running = true, error = null) }
            try {
                val keep = launchPackage?.let { setOf(it) } ?: emptySet()
                val report = container.cleaner.boost(keep)
                boost.update { BoostState(running = false, last = report) }
                if (launchPackage != null) {
                    val intent = container.games.launchIntent(launchPackage)
                    if (intent != null) {
                        launches.send(intent)
                    } else {
                        boost.update { it.copy(error = "Não foi possível abrir o jogo (sem atividade de início).") }
                    }
                }
            } catch (e: Exception) {
                boost.update { it.copy(running = false, error = "Falha na otimização: ${e.message}") }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as GameBoosterApp
                DashboardViewModel(app.container)
            }
        }
    }
}
