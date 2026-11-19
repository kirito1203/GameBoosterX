package com.memesoundboardx.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.memesoundboardx.BuildConfig
import com.memesoundboardx.domain.model.LibrarySyncReport
import com.memesoundboardx.domain.repository.LibrarySyncer
import com.memesoundboardx.ui.components.NeonCard
import com.memesoundboardx.ui.components.SectionTitle
import com.memesoundboardx.ui.theme.MonoLabel
import com.memesoundboardx.ui.theme.NeonColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val syncer: LibrarySyncer,
) : ViewModel() {
    val report: StateFlow<LibrarySyncReport?> = syncer.lastReport

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    fun resync() {
        if (_syncing.value) return
        viewModelScope.launch {
            _syncing.value = true
            try { syncer.syncBundled() } finally { _syncing.value = false }
        }
    }
}

/**
 * ETAPA 1: só mostra o que já funciona. Volume, sons simultâneos e foco de áudio
 * já são salvos no DataStore, mas os controles só aparecem na ETAPA 3, junto com
 * o motor de áudio que os aplica — para não exibir controles sem efeito.
 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val report by viewModel.report.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
                OutlinedButton(onClick = viewModel::resync, enabled = !syncing) {
                    Text(if (syncing) "Verificando…" else "Verificar novamente")
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
