package com.gamebooster.x.ui.gamespace

import android.Manifest
import android.content.ActivityNotFoundException
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gamebooster.x.core.GameStatsAggregator
import com.gamebooster.x.core.InstalledGame
import com.gamebooster.x.data.SettingsRepository
import com.gamebooster.x.gamespace.GameSpaceService
import com.gamebooster.x.gamespace.GsPermission
import com.gamebooster.x.ui.components.GbxPanel
import com.gamebooster.x.ui.components.StatRow
import com.gamebooster.x.ui.theme.GbxColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GameSpaceRoute(
    games: List<InstalledGame>,
    viewModel: GameSpaceViewModel = viewModel(factory = GameSpaceViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val status by GameSpaceService.status.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPermissions()
        viewModel.checkFreeform()
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refreshPermissions() }

    fun open(p: GsPermission) {
        if (p == GsPermission.POST_NOTIFICATIONS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        try {
            context.startActivity(viewModel.permissionIntent(p))
        } catch (_: ActivityNotFoundException) {
            context.startActivity(viewModel.appDetailsIntent())
        }
    }

    val names = games.associate { it.packageName to it.name }
    val essentialGranted = state.permissions[GsPermission.USAGE] == true

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(GbxColors.Black).statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("GAME SPACE", style = MaterialTheme.typography.headlineSmall, color = GbxColors.ElectricBlue)
            Text(
                "Detecta quando um jogo abre e ativa as funções abaixo só durante a partida.",
                style = MaterialTheme.typography.bodySmall, color = GbxColors.TextSecondary,
            )
        }

        item {
            GbxPanel(
                title = "Serviço",
                trailing = if (state.serviceRunning) "ATIVO" else "DESLIGADO",
                trailingColor = if (state.serviceRunning) GbxColors.Ok else GbxColors.TextSecondary,
            ) {
                if (!essentialGranted) {
                    Text(
                        "Conceda \"Acesso ao uso\" abaixo para o Game Space saber quando um jogo abre.",
                        style = MaterialTheme.typography.bodySmall, color = GbxColors.Warn,
                    )
                }
                OutlinedButton(
                    onClick = { GameSpaceService.preview(context) },
                    enabled = state.permissions[GsPermission.OVERLAY] == true,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("👁 Testar barra agora (15 s)", color = GbxColors.Neon) }
                if (state.permissions[GsPermission.OVERLAY] != true) {
                    Text(
                        "Para a barra aparecer, conceda \"Sobrepor a outros apps\" em Permissões.",
                        style = MaterialTheme.typography.bodySmall, color = GbxColors.Warn,
                    )
                }
                if (state.serviceRunning) {
                    Text("Diagnóstico (atualiza a cada 2 s)", style = MaterialTheme.typography.labelSmall, color = GbxColors.TextSecondary)
                    StatRow("App em primeiro plano", status.foregroundPackage ?: "nenhum detectado")
                    StatRow("Partida em andamento", status.sessionGame ?: "não")
                    StatRow("Jogos reconhecidos", status.gamesKnown.toString())
                    StatRow("Barra na tela", if (status.barVisible) "sim" else "não")
                    StatRow("FPS lido", status.lastFps?.toString() ?: "—")
                    StatRow("Fonte de FPS", status.fpsSource ?: "—")
                    status.fpsProblem?.let { StatRow("Problema do FPS", it) }
                    Text(
                        "Dica: com o Game Space ligado, abra um jogo, volte aqui pela tela de recentes e veja se " +
                            "\"App em primeiro plano\" mostrou o jogo.",
                        style = MaterialTheme.typography.bodySmall, color = GbxColors.TextSecondary,
                    )
                    OutlinedButton(onClick = { GameSpaceService.stop(context) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Desligar Game Space", color = GbxColors.Neon)
                    }
                } else {
                    Button(
                        onClick = { GameSpaceService.start(context) },
                        enabled = essentialGranted,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = GbxColors.ElectricBlue),
                    ) { Text("🎮 LIGAR GAME SPACE", fontWeight = FontWeight.Bold) }
                }
            }
        }

        item {
            GbxPanel(title = "Durante o jogo") {
                ToggleRow("Barra lateral", "Alça na borda com FPS; abre status, captura, gravação e atalhos", state.settings.overlay) { v ->
                    viewModel.update { it.copy(overlay = v) }
                }
                ToggleRow("Contador de FPS", "FPS real via Shizuku. Pode causar leves travadas em celulares mais simples; ligue só para medir.", state.settings.fpsCounter) { v ->
                    viewModel.update { it.copy(fpsCounter = v) }
                }
                ToggleRow("Não perturbe", "Silencia notificações e chamadas (alarmes continuam)", state.settings.doNotDisturb) { v ->
                    viewModel.update { it.copy(doNotDisturb = v) }
                }
                ToggleRow("Travar brilho", "Desliga o brilho automático durante a partida", state.settings.lockBrightness) { v ->
                    viewModel.update { it.copy(lockBrightness = v) }
                }
                ToggleRow("Notificações discretas", "Mostra notificações como texto pequeno no painel", state.settings.notificationTicker) { v ->
                    viewModel.update { it.copy(notificationTicker = v) }
                }
                Text(
                    "Captura e gravação de tela usam o Shizuku. Tudo é desfeito ao sair do jogo.",
                    style = MaterialTheme.typography.bodySmall, color = GbxColors.TextSecondary,
                )
            }
        }

        item {
            var expanded by remember { mutableStateOf(false) }
            GbxPanel(
                title = "Barra lateral · atalhos de apps",
                trailing = "${state.sidebarApps.size}/${SettingsRepository.MAX_SIDEBAR_APPS}",
            ) {
                Text(
                    "Na partida, toque na alça da borda direita (ou deslize para dentro) para abrir a barra. " +
                        "Toque num app para tentar abrir em janela flutuante; segure para abrir em tela cheia.",
                    style = MaterialTheme.typography.bodySmall, color = GbxColors.TextSecondary,
                )
                Text(
                    "Janela flutuante usa o Shizuku e depende do realme UI aceitar. Se abrir em tela cheia, " +
                        "use a Barra lateral inteligente da realme para janelas.",
                    style = MaterialTheme.typography.bodySmall, color = GbxColors.TextSecondary,
                )
                val selected = state.launchableApps.filter { it.first in state.sidebarApps }
                if (selected.isNotEmpty()) {
                    Text(selected.joinToString(" · ") { it.second }, color = GbxColors.Neon)
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Fechar lista" else "Escolher apps", color = GbxColors.ElectricBlue)
                }
                if (expanded) {
                    val full = state.sidebarApps.size >= SettingsRepository.MAX_SIDEBAR_APPS
                    state.launchableApps.forEach { (pkg, label) ->
                        val checked = pkg in state.sidebarApps
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { viewModel.toggleSidebarApp(pkg) },
                                enabled = checked || !full,
                            )
                            Text(label, color = GbxColors.TextPrimary)
                        }
                    }
                }
            }
        }

        item {
            GbxPanel(
                title = "Janelas flutuantes",
                trailing = when (state.freeformEnabled) {
                    true -> "ATIVADAS"
                    false -> "DESATIVADAS"
                    null -> "SHIZUKU PARADO"
                },
                trailingColor = if (state.freeformEnabled == true) GbxColors.Ok else GbxColors.TextSecondary,
            ) {
                Text(
                    "Para os atalhos abrirem num quadro por cima do jogo (ex.: WhatsApp), o Android precisa das " +
                        "opções do desenvolvedor \"janelas de formato livre\". O realme UI pode ainda assim recusar — " +
                        "nesse caso use a Barra lateral inteligente da realme, que tem janela flutuante própria.",
                    style = MaterialTheme.typography.bodySmall, color = GbxColors.TextSecondary,
                )
                if (state.freeformEnabled == false) {
                    Button(
                        onClick = { viewModel.setFreeform(true) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = GbxColors.ElectricBlue),
                    ) { Text("Ativar janelas flutuantes") }
                }
                if (state.freeformEnabled == true) {
                    OutlinedButton(onClick = { viewModel.setFreeform(false) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Desativar (voltar ao normal)", color = GbxColors.Neon)
                    }
                }
                state.freeformMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = GbxColors.TextPrimary) }
            }
        }

        item {
            GbxPanel(title = "Permissões") {
                GsPermission.entries.forEach { p ->
                    val granted = state.permissions[p] == true
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                (if (granted) "✔ " else "✖ ") + p.label,
                                color = if (granted) GbxColors.Ok else GbxColors.TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(p.purpose, style = MaterialTheme.typography.bodySmall, color = GbxColors.TextSecondary)
                        }
                        if (!granted) {
                            TextButton(onClick = { open(p) }) { Text("Conceder", color = GbxColors.ElectricBlue) }
                        }
                    }
                }
                Text(
                    "Se o Android mostrar \"Configuração restrita\" (comum em apps instalados por APK): abra " +
                        "Informações do app → ⋮ (canto superior) → Permitir configurações restritas, e tente de novo.",
                    style = MaterialTheme.typography.bodySmall, color = GbxColors.TextSecondary,
                )
                TextButton(onClick = { context.startActivity(viewModel.appDetailsIntent()) }) {
                    Text("Abrir Informações do app", color = GbxColors.ElectricBlue)
                }
            }
        }

        item {
            GbxPanel(title = "Estatísticas por jogo") {
                if (state.stats.isEmpty()) {
                    Text(
                        "Nenhuma partida registrada ainda. Ligue o Game Space e jogue por pelo menos 30 s.",
                        style = MaterialTheme.typography.bodySmall, color = GbxColors.TextSecondary,
                    )
                } else {
                    state.stats.values.sortedByDescending { it.totalPlayMs }.forEach { s ->
                        Text(names[s.packageName] ?: s.packageName, color = GbxColors.Neon, fontWeight = FontWeight.SemiBold)
                        StatRow("Partidas", s.sessions.toString())
                        StatRow("Tempo total", GameStatsAggregator.formatDuration(s.totalPlayMs))
                        StatRow("FPS médio (última)", s.lastAverageFps?.let { "%.0f".format(it) } ?: "sem medição")
                        StatRow("Melhor FPS médio", s.bestAverageFps?.let { "%.0f".format(it) } ?: "sem medição")
                        StatRow("Temperatura máxima", s.maxBatteryCelsius?.let { "%.1f °C".format(it) } ?: "—")
                    }
                }
            }
        }

        if (state.recent.isNotEmpty()) {
            item {
                GbxPanel(title = "Últimas partidas") {
                    val fmt = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                    state.recent.forEach { r ->
                        Text(
                            "${fmt.format(Date(r.startMs))} · ${names[r.packageName] ?: r.packageName} · " +
                                GameStatsAggregator.formatDuration(r.durationMs) +
                                (r.averageFps?.let { " · %.0f FPS (1%% low %.0f)".format(it, r.onePercentLowFps ?: 0f) } ?: ""),
                            style = MaterialTheme.typography.bodySmall, color = GbxColors.TextPrimary,
                        )
                    }
                    TextButton(onClick = viewModel::clearHistory) { Text("Apagar histórico", color = GbxColors.Danger) }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = GbxColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = GbxColors.TextSecondary)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
