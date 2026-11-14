package com.gamebooster.x.ui.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.gamebooster.x.core.GameStats
import com.gamebooster.x.core.GameStatsAggregator
import com.gamebooster.x.core.InstalledGame
import com.gamebooster.x.core.ShizukuStatus
import com.gamebooster.x.ui.theme.GbxColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Biblioteca de jogos estilo Game Space: um toque para jogar, com ou sem Turbo. */
@Composable
fun LibraryScreen(
    games: List<InstalledGame>,
    gamesLoaded: Boolean,
    stats: Map<String, GameStats>,
    shizukuStatus: ShizukuStatus,
    turboRunning: Boolean,
    turboMessage: String?,
    onPlay: (String) -> Unit,
    onTurbo: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().background(GbxColors.Black).statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text("MEUS JOGOS", style = MaterialTheme.typography.headlineSmall, color = GbxColors.ElectricBlue)
                Text(
                    if (shizukuStatus == ShizukuStatus.READY) "Turbo disponível (Shizuku pronto)"
                    else "Turbo indisponível: ${shizukuStatus.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (shizukuStatus == ShizukuStatus.READY) GbxColors.Ok else GbxColors.TextSecondary,
                )
                turboMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = GbxColors.TextPrimary) }
            }
        }
        if (gamesLoaded && games.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Nenhum jogo encontrado. Apps que se declaram jogos aparecem aqui automaticamente.",
                    style = MaterialTheme.typography.bodySmall, color = GbxColors.TextSecondary,
                )
            }
        }
        games.forEach { game ->
            item(key = game.packageName) {
                GameCard(
                    game = game,
                    stats = stats[game.packageName],
                    turboEnabled = shizukuStatus == ShizukuStatus.READY && !turboRunning,
                    onPlay = { onPlay(game.packageName) },
                    onTurbo = { onTurbo(game.packageName) },
                )
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            TextButton(onClick = onRefresh) { Text("Procurar jogos novamente", color = GbxColors.ElectricBlue) }
        }
    }
}

@Composable
private fun GameCard(
    game: InstalledGame,
    stats: GameStats?,
    turboEnabled: Boolean,
    onPlay: () -> Unit,
    onTurbo: () -> Unit,
) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(initialValue = null, game.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(game.packageName).toBitmap(144, 144).asImageBitmap()
            }.getOrNull()
        }
    }
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GbxColors.Panel)
            .border(1.dp, GbxColors.PanelBorder, shape)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(GbxColors.Graphite)) {
            icon?.let { Image(bitmap = it, contentDescription = game.name, modifier = Modifier.fillMaxSize()) }
        }
        Text(
            game.name,
            color = GbxColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            stats?.let {
                GameStatsAggregator.formatDuration(it.totalPlayMs) +
                    (it.lastAverageFps?.let { f -> " · %.0f FPS".format(f) } ?: "")
            } ?: "Sem partidas",
            style = MaterialTheme.typography.bodySmall,
            color = GbxColors.TextSecondary,
        )
        Button(
            onClick = onTurbo,
            enabled = turboEnabled,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = GbxColors.ElectricBlue),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
        ) { Text("🚀 Turbo", fontWeight = FontWeight.Bold) }
        OutlinedButton(
            onClick = onPlay,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
        ) { Text("Jogar", color = GbxColors.Neon) }
    }
}
