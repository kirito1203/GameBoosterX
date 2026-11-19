package com.memesoundboardx.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.memesoundboardx.domain.model.PlayerState
import com.memesoundboardx.domain.model.Sound
import com.memesoundboardx.domain.repository.SettingsRepository
import com.memesoundboardx.domain.repository.SoundPlayer
import com.memesoundboardx.ui.theme.MonoLabel
import com.memesoundboardx.ui.theme.NeonColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val player: SoundPlayer,
    private val settings: SettingsRepository,
) : ViewModel() {
    val state: StateFlow<PlayerState> = player.state

    fun toggle(sound: Sound) = player.toggle(sound)
    fun stop(sound: Sound) = player.stop(sound.id)
    fun stopAll() = player.stopAll()
    fun setRepeat(enabled: Boolean) = player.setRepeat(enabled)
    fun preload(sounds: List<Sound>) = player.preload(sounds)
    fun clearError() = player.clearError()
    fun setVolume(v: Float) {
        viewModelScope.launch { settings.setMasterVolume(v) }
    }
}

/** O que as listas de sons precisam saber/fazer com o player, sem depender do ViewModel. */
class PlayerUi(
    val state: PlayerState,
    val onToggle: (Sound) -> Unit,
    val onStop: (Sound) -> Unit,
)

val LocalPlayerUi = compositionLocalOf { PlayerUi(PlayerState(), {}, {}) }

/** Barra fixa acima das abas: volume geral, repetição e "parar tudo". */
@Composable
fun PlayerBar(
    state: PlayerState,
    onVolumeCommit: (Float) -> Unit,
    onToggleRepeat: () -> Unit,
    onStopAll: () -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var local by remember { mutableFloatStateOf(state.volume) }
    val shown = if (dragging) local else state.volume
    val activeCount = state.active.size

    Row(
        Modifier
            .fillMaxWidth()
            .background(NeonColors.SurfaceHigh)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.VolumeUp, contentDescription = "Volume", tint = NeonColors.TextDim, modifier = Modifier.padding(start = 4.dp).size(20.dp))
        Slider(
            value = shown,
            onValueChange = { dragging = true; local = it },
            onValueChangeFinished = { onVolumeCommit(local); dragging = false },
            modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
            colors = SliderDefaults.colors(
                thumbColor = NeonColors.Blue,
                activeTrackColor = NeonColors.Blue,
                inactiveTrackColor = NeonColors.Outline,
            ),
        )
        Text("${(shown * 100).toInt()}%", style = MonoLabel, color = NeonColors.TextDim, modifier = Modifier.width(36.dp))
        IconButton(
            onClick = onToggleRepeat,
            colors = IconButtonDefaults.iconButtonColors(contentColor = if (state.repeat) NeonColors.Purple else NeonColors.TextDim),
        ) {
            Icon(AppIcons.Repeat, contentDescription = if (state.repeat) "Repetição ligada" else "Repetição desligada")
        }
        AnimatedVisibility(visible = activeCount > 0) {
            FilledTonalButton(onClick = onStopAll, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)) {
                Icon(AppIcons.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Parar ($activeCount)")
            }
        }
    }
}
