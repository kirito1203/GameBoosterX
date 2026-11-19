package com.memesoundboardx.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.memesoundboardx.BuildConfig
import com.memesoundboardx.core.audio.PlaybackPolicy
import com.memesoundboardx.core.util.Formatters
import com.memesoundboardx.data.importer.ImportedFileStore
import com.memesoundboardx.domain.model.AppSettings
import com.memesoundboardx.domain.model.LibrarySyncReport
import com.memesoundboardx.domain.repository.LibrarySyncer
import com.memesoundboardx.domain.repository.SettingsRepository
import com.memesoundboardx.domain.repository.SoundPlayer
import com.memesoundboardx.ui.components.NeonCard
import com.memesoundboardx.ui.components.SectionTitle
import com.memesoundboardx.ui.theme.MonoLabel
import com.memesoundboardx.ui.theme.NeonColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

data class Diagnostics(val loadedSamples: Int = 0, val importedBytes: Long = 0)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val syncer: LibrarySyncer,
    private val settingsRepo: SettingsRepository,
    private val player: SoundPlayer,
    private val files: ImportedFileStore,
) : ViewModel() {
    val report: StateFlow<LibrarySyncReport?> = syncer.lastReport
    val settings: StateFlow<AppSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _diag = MutableStateFlow(Diagnostics())
    val diagnostics: StateFlow<Diagnostics> = _diag.asStateFlow()

    fun refreshDiagnostics() {
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) { files.totalBytes() }
            _diag.value = Diagnostics(player.loadedSampleCount(), bytes)
        }
    }

    fun resync() {
        if (_syncing.value) return
        viewModelScope.launch {
            _syncing.value = true
            try { syncer.syncBundled() } finally { _syncing.value = false }
        }
    }

    fun setVolume(v: Float) {
        viewModelScope.launch { settingsRepo.setMasterVolume(v) }
    }

    fun setMaxSimultaneous(n: Int) {
        viewModelScope.launch { settingsRepo.setMaxSimultaneous(n) }
    }

    fun setAudioFocus(on: Boolean) {
        viewModelScope.launch { settingsRepo.setRequestAudioFocus(on) }
    }
    fun restoreHidden() {
        viewModelScope.launch {
            settingsRepo.restoreBundled()
            resync()
        }
    }

    fun freeMemory() {
        player.trimMemory()
        refreshDiagnostics()
    }
}

@Composable
fun SettingsScreen(onOpenCredits: () -> Unit = {}, viewModel: SettingsViewModel = hiltViewModel()) {
    val report by viewModel.report.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val diag by viewModel.diagnostics.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshDiagnostics() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle("Áudio")
        NeonCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                var vol by remember(settings.masterVolume) { mutableFloatStateOf(settings.masterVolume) }
                Text("Volume geral: ${(vol * 100).toInt()}%", style = MaterialTheme.typography.titleMedium)
                Slider(value = vol, onValueChange = { vol = it }, onValueChangeFinished = { viewModel.setVolume(vol) })
                Text(
                    "Relativo ao volume de mídia do celular (botões laterais).",
                    style = MaterialTheme.typography.bodySmall, color = NeonColors.TextDim,
                )

                var max by remember(settings.maxSimultaneous) { mutableFloatStateOf(settings.maxSimultaneous.toFloat()) }
                Text("Sons ao mesmo tempo: ${max.toInt()}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                Slider(
                    value = max,
                    onValueChange = { max = it },
                    onValueChangeFinished = { viewModel.setMaxSimultaneous(max.toInt()) },
                    valueRange = AppSettings.MIN_SIMULTANEOUS.toFloat()..AppSettings.MAX_SIMULTANEOUS.toFloat(),
                    steps = AppSettings.MAX_SIMULTANEOUS - AppSettings.MIN_SIMULTANEOUS - 1,
                )
                Text(
                    "Ao passar do limite, o som mais antigo é interrompido.",
                    style = MaterialTheme.typography.bodySmall, color = NeonColors.TextDim,
                )

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Abaixar outros apps ao tocar", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Desligado (recomendado): o som do Roblox continua normal. Ligado: o Android pode " +
                                "abaixar o jogo enquanto um efeito toca, e uma ligação interrompe os efeitos.",
                            style = MaterialTheme.typography.bodySmall, color = NeonColors.TextDim,
                        )
                    }
                    Switch(
                        checked = settings.requestAudioFocus,
                        onCheckedChange = viewModel::setAudioFocus,
                        colors = SwitchDefaults.colors(checkedTrackColor = NeonColors.Purple),
                    )
                }
            }
        }

        SectionTitle("Limitação: microfone do Roblox")
        NeonCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Este app NÃO transmite sons pelo microfone do Roblox.",
                    style = MaterialTheme.typography.titleMedium,
                    color = NeonColors.Warning,
                )
                Text(
                    "O Android não oferece a apps comuns nenhuma forma de criar um microfone virtual " +
                        "ou de injetar áudio na gravação de outro app. Isso só é possível com root, " +
                        "que este projeto não usa.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Os sons tocam no alto-falante ou no fone. Se o microfone captar o alto-falante, " +
                        "outras pessoas podem ouvir — mas o cancelamento de eco do chat de voz tende a " +
                        "remover esse som. Isso ainda NÃO foi comprovado em teste real no Realme C71.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NeonColors.TextDim,
                )
            }
        }

        SectionTitle("Biblioteca embutida")
        NeonCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val r = report
                if (r == null) {
                    Text("Sincronização ainda não concluída.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text("Sons válidos no manifesto: ${r.validSounds}", style = MaterialTheme.typography.bodyMedium)
                    Text("Entradas rejeitadas: ${r.rejected.size}", style = MaterialTheme.typography.bodyMedium)
                    r.rejected.take(10).forEach { Text("• $it", style = MonoLabel, color = NeonColors.Danger) }
                    if (r.rejected.size > 10) Text("…e mais ${r.rejected.size - 10}", style = MonoLabel)
                    Text(
                        "Última verificação: " + DateFormat.getDateTimeInstance().format(Date(r.finishedAt)),
                        style = MonoLabel,
                        color = NeonColors.TextDim,
                    )
                }
                Text(
                    "Sons originais (síntese) + gravações de fontes livres com licença registrada. " +
                        "Toque longo em qualquer som mostra fonte, autor e licença.",
                    style = MaterialTheme.typography.bodySmall, color = NeonColors.TextDim,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = viewModel::resync, enabled = !syncing) {
                        Text(if (syncing) "Verificando…" else "Verificar novamente")
                    }
                    OutlinedButton(onClick = onOpenCredits) { Text("Créditos") }
                }
                val hidden = settings.hiddenBundled.size
                if (hidden > 0) {
                    OutlinedButton(onClick = viewModel::restoreHidden, enabled = !syncing) {
                        Text("Restaurar $hidden som(ns) removido(s)")
                    }
                }
            }
        }

        SectionTitle("Memória e armazenamento")
        NeonCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Sons curtos na memória: ${diag.loadedSamples} de no máximo ${PlaybackPolicy.SAMPLE_CACHE_SIZE}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("Cópias de áudios importados: ${Formatters.size(diag.importedBytes)}", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = viewModel::refreshDiagnostics) { Text("Atualizar") }
                    OutlinedButton(onClick = viewModel::freeMemory) { Text("Liberar memória") }
                }
            }
        }

        SectionTitle("Sobre")
        NeonCard(Modifier.fillMaxWidth()) {
            Column {
                Text("MEME SOUNDBOARD X", style = MaterialTheme.typography.titleMedium)
                Text("Versão ${BuildConfig.VERSION_NAME}", style = MonoLabel, color = NeonColors.TextDim)
                Text("Sem root · Sem anúncios · Sem acesso à internet", style = MonoLabel, color = NeonColors.TextDim)
            }
        }
    }
}
