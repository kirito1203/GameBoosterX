package com.gamebooster.x.ui.dashboard

import android.content.ActivityNotFoundException
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gamebooster.x.core.AdviceSeverity
import com.gamebooster.x.core.Formatters
import com.gamebooster.x.core.MemoryPressure
import com.gamebooster.x.core.PerformanceMode
import com.gamebooster.x.core.ShizukuStatus
import com.gamebooster.x.core.TurboSettings
import com.gamebooster.x.core.ThermalLevel
import com.gamebooster.x.ui.components.GbxPanel
import com.gamebooster.x.ui.components.NeonBar
import com.gamebooster.x.ui.components.Sparkline
import com.gamebooster.x.ui.components.StatRow
import com.gamebooster.x.ui.theme.GbxColors

/** Abre as intents pedidas pelo ViewModel (jogos, Shizuku). Chamado uma vez na atividade. */
@Composable
fun LaunchRequestsEffect(viewModel: DashboardViewModel) {
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.launchRequests.collect { intent ->
            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                // O app foi desinstalado entre a varredura e o toque; atualiza a lista.
                viewModel.refreshGames()
            }
        }
    }
}

@Composable
fun DashboardRoute(viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Comportamento real do modo: manter a tela ligada enquanto o app estiver aberto.
    val view = LocalView.current
    DisposableEffect(state.policy.keepScreenOn) {
        view.keepScreenOn = state.policy.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    DashboardScreen(
        state = state,
        onModeSelected = viewModel::setMode,
        onBoost = { viewModel.boost() },
        onBoostAndPlay = { pkg -> viewModel.boost(launchPackage = pkg) },
        onRefreshGames = viewModel::refreshGames,
        turboActions = TurboActions(
            onSelectGame = viewModel::selectTurboGame,
            onSettings = viewModel::setTurboSettings,
            onApplyAndPlay = { viewModel.applyTurboAndPlay() },
            onRestore = viewModel::restoreTurbo,
            onRequestPermission = viewModel::requestShizukuPermission,
            onOpenShizuku = viewModel::openShizuku,
        ),
    )
}

class TurboActions(
    val onSelectGame: (String) -> Unit,
    val onSettings: (TurboSettings) -> Unit,
    val onApplyAndPlay: () -> Unit,
    val onRestore: () -> Unit,
    val onRequestPermission: () -> Unit,
    val onOpenShizuku: () -> Unit,
)

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    onModeSelected: (PerformanceMode) -> Unit,
    onBoost: () -> Unit,
    onBoostAndPlay: (String) -> Unit,
    onRefreshGames: () -> Unit,
    turboActions: TurboActions,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GbxColors.Black)
            .statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Header(state) }
        item { ModePanel(state, onModeSelected) }
        item { TurboPanel(state, turboActions) }
        item { BoostPanel(state, onBoost) }
        item { MemoryPanel(state) }
        item { ThermalPanel(state) }
        item { CpuPanel(state) }
        item { FpsDisplayPanel(state) }
        item { GamesPanel(state, onBoostAndPlay, onRefreshGames) }
        item { AdvicePanel(state) }
        item { DevicePanel(state) }
        state.monitor.error?.let { err ->
            item { Text(err, color = GbxColors.Danger, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun Header(state: DashboardUiState) {
    Column {
        Text("GAME BOOSTER X", style = MaterialTheme.typography.headlineSmall, color = GbxColors.ElectricBlue)
        val profile = state.profile
        val subtitle = when {
            profile == null -> "Identificando aparelho…"
            profile.isRealmeC71 -> "${profile.displayName} · perfil Realme C71"
            else -> "${profile.displayName} · perfil genérico (${profile.tier.label})"
        }
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = GbxColors.TextSecondary)
    }
}

@OptIn(ExperimentalMaterial3Api::class) // FilterChip foi experimental em versões anteriores do Material 3
@Composable
private fun ModePanel(state: DashboardUiState, onModeSelected: (PerformanceMode) -> Unit) {
    GbxPanel(title = "Alertas e monitoramento", trailing = state.mode.label.uppercase()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PerformanceMode.entries.forEach { mode ->
                FilterChip(
                    selected = state.mode == mode,
                    onClick = { onModeSelected(mode) },
                    label = { Text(mode.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GbxColors.ElectricBlue,
                        selectedLabelColor = Color.White,
                        labelColor = GbxColors.TextSecondary,
                    ),
                )
            }
        }
        Text(state.mode.tagline, style = MaterialTheme.typography.bodyMedium, color = GbxColors.TextPrimary)
        Text(
            "Estes modos ajustam só o Game Booster X (leituras e alertas). Para o jogo rodar melhor, use o Turbo.",
            style = MaterialTheme.typography.bodySmall, color = GbxColors.Warn,
        )
        Text(state.policy.description, style = MaterialTheme.typography.bodySmall, color = GbxColors.TextSecondary)
    }
}

private fun scaleLabel(scale: Float): String = if (scale >= 1f) "100%" else "${(scale * 100).toInt()}%"

private fun fpsLabel(fps: Int): String = if (fps == 0) "Livre" else "$fps"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TurboPanel(state: DashboardUiState, actions: TurboActions) {
    val turbo = state.turbo
    val ready = turbo.status == ShizukuStatus.READY
    GbxPanel(
        title = "Modo Turbo · Shizuku",
        trailing = turbo.status.label.uppercase(),
        trailingColor = if (ready) GbxColors.Ok else GbxColors.Warn,
    ) {
        when (turbo.status) {
            ShizukuStatus.NOT_INSTALLED -> {
                Text(
                    "O Turbo precisa do app Shizuku (gratuito, sem root) para pedir ao Android que rode o jogo " +
                        "em resolução menor e com FPS estável.",
                    style = MaterialTheme.typography.bodySmall, color = GbxColors.TextSecondary,
                )
                Button(onClick = actions.onOpenShizuku, modifier = Modifier.fillMaxWidth()) { Text("Instalar Shizuku") }
            }
            ShizukuStatus.NOT_RUNNING -> {
                Text(
                    "Inicie o Shizuku: abra o app Shizuku → Depuração sem fio → Parear (só na primeira vez) → Iniciar. " +
                        "Depois de reiniciar o celular é preciso tocar em Iniciar de novo.",
                    style = MaterialTheme.typography.bodySmall, color = GbxColors.TextSecondary,
                )
                Button(onClick = actions.onOpenShizuku, modifier = Modifier.fillMaxWidth()) { Text("Abrir Shizuku") }
            }
            ShizukuStatus.OUTDATED -> {
                Text("Atualize o Shizuku pela Play Store.", style = MaterialTheme.typography.bodySmall, color = GbxColors.Warn)
                Button(onClick = actions.onOpenShizuku, modifier = Modifier.fillMaxWidth()) { Text("Abrir Shizuku") }
            }
            ShizukuStatus.NO_PERMISSION -> {
                Text(
                    "O Shizuku está rodando. Falta permitir que o Game Booster X o use.",
                    style = MaterialTheme.typography.bodySmall, color = GbxColors.TextSecondary,
                )
                Button(onClick = actions.onRequestPermission, modifier = Modifier.fillMaxWidth()) { Text("Permitir acesso") }
            }
            ShizukuStatus.READY -> Unit
        }

        if (state.games.isEmpty()) {
            Text("Nenhum jogo detectado para o Turbo.", style = MaterialTheme.typography.bodySmall, color = GbxColors.TextSecondary)
        } else {
            Text("Jogo", style = MaterialTheme.typography.labelSmall, color = GbxColors.TextSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.games.forEach { game ->
                    FilterChip(
                        selected = turbo.selectedPackage == game.packageName,
                        onClick = { actions.onSelectGame(game.packageName) },
                        label = { Text(game.name) },
                    )
                }
            }
            Text("Resolução do jogo", style = MaterialTheme.typography.labelSmall, color = GbxColors.TextSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TurboSettings.SCALE_OPTIONS.forEach { scale ->
                    FilterChip(
                        selected = turbo.settings.resolutionScale == scale,
                        onClick = { actions.onSettings(turbo.settings.copy(resolutionScale = scale)) },
                        label = { Text(scaleLabel(scale)) },
                    )
                }
            }
            Text("Limite de FPS", style = MaterialTheme.typography.labelSmall, color = GbxColors.TextSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TurboSettings.FPS_OPTIONS.forEach { fps ->
                    FilterChip(
                        selected = turbo.settings.fpsCap == fps,
                        onClick = { actions.onSettings(turbo.settings.copy(fpsCap = fps)) },
                        label = { Text(fpsLabel(fps)) },
                    )
                }
            }
            Text(
                "Menos resolução = mais FPS (imagem um pouco menos nítida). FPS limitado a 60 costuma travar menos " +
                    "que FPS oscilando. O jogo precisa ser fechado e aberto de novo para aplicar.",
                style = MaterialTheme.typography.bodySmall, color = GbxColors.TextSecondary,
            )
            Button(
                onClick = actions.onApplyAndPlay,
                enabled = ready && !turbo.running && turbo.selectedPackage != null,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = GbxColors.ElectricBlue),
            ) {
                if (turbo.running) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Aplicando…")
                } else {
                    Text("🚀 TURBO E JOGAR", fontWeight = FontWeight.Bold)
                }
            }
            OutlinedButton(
                onClick = actions.onRestore,
                enabled = ready && !turbo.running && turbo.selectedPackage != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Restaurar padrão do jogo", color = GbxColors.Neon) }
        }

        turbo.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = GbxColors.TextPrimary) }
        if (turbo.log.isNotEmpty()) {
            Text("Registro (respostas reais do sistema)", style = MaterialTheme.typography.labelSmall, color = GbxColors.TextSecondary)
            turbo.log.forEach { r ->
                Text(
                    (if (r.ok) "✔ " else "✖ ") + r.command,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (r.ok) GbxColors.Ok else GbxColors.Danger,
                )
                if (r.output.isNotBlank()) {
                    Text(r.output.take(400), style = MaterialTheme.typography.bodySmall, color = GbxColors.TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun BoostPanel(state: DashboardUiState, onBoost: () -> Unit) {
    GbxPanel(title = "Otimização") {
        Button(
            onClick = onBoost,
            enabled = !state.boost.running,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = GbxColors.ElectricBlue),
        ) {
            if (state.boost.running) {
                CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Otimizando e medindo…")
            } else {
                Text("⚡ OTIMIZAR AGORA", fontWeight = FontWeight.Bold)
            }
        }
        state.boost.last?.let { r ->
            StatRow("Antes", Formatters.bytes(r.availableBeforeBytes) + " livres")
            StatRow("Depois", Formatters.bytes(r.availableAfterBytes) + " livres")
            Text(
                r.summary(),
                style = MaterialTheme.typography.bodySmall,
                color = if (r.deltaBytes > 0) GbxColors.Ok else GbxColors.TextSecondary,
            )
        }
        state.boost.error?.let { Text(it, color = GbxColors.Danger, style = MaterialTheme.typography.bodySmall) }
        Text(
            "Encerra processos em segundo plano de apps instalados por você (API oficial do Android, sem root). " +
                "Apps do sistema e o app em primeiro plano não são afetados.",
            style = MaterialTheme.typography.bodySmall,
            color = GbxColors.TextSecondary,
        )
    }
}

private fun pressureColor(p: MemoryPressure?): Color = when (p) {
    MemoryPressure.LOW -> GbxColors.Ok
    MemoryPressure.MODERATE -> GbxColors.Neon
    MemoryPressure.HIGH -> GbxColors.Warn
    MemoryPressure.CRITICAL -> GbxColors.Danger
    null -> GbxColors.TextSecondary
}

private fun thermalColor(level: ThermalLevel?): Color = when (level) {
    ThermalLevel.NORMAL -> GbxColors.Ok
    ThermalLevel.WARM -> GbxColors.Neon
    ThermalLevel.HOT -> GbxColors.Warn
    ThermalLevel.CRITICAL -> GbxColors.Danger
    ThermalLevel.UNKNOWN, null -> GbxColors.TextSecondary
}

@Composable
private fun MemoryPanel(state: DashboardUiState) {
    val mem = state.monitor.snapshot?.memory
    val assess = state.memory
    GbxPanel(
        title = "Memória RAM",
        trailing = assess?.let { "${it.usedPercent}% · ${it.pressure.label}" },
        trailingColor = pressureColor(assess?.pressure),
    ) {
        if (mem == null) {
            Text("Lendo…", color = GbxColors.TextSecondary)
        } else {
            NeonBar(mem.usedPercent / 100f, pressureColor(assess?.pressure))
            StatRow("Em uso", Formatters.bytes(mem.usedBytes))
            StatRow("Disponível", Formatters.bytes(mem.availableBytes))
            StatRow("Total", Formatters.bytes(mem.totalBytes))
            StatRow(
                "Sistema em memória baixa",
                if (mem.systemLowMemory) "SIM" else "Não",
                if (mem.systemLowMemory) GbxColors.Danger else GbxColors.TextPrimary,
            )
            Text("Uso de RAM (%) — últimas leituras", style = MaterialTheme.typography.labelSmall, color = GbxColors.TextSecondary)
            Sparkline(state.monitor.memoryHistory.values, 0f, 100f, GbxColors.ElectricBlue)
        }
    }
}

@Composable
private fun ThermalPanel(state: DashboardUiState) {
    val t = state.monitor.snapshot?.thermal
    val assess = state.thermal
    GbxPanel(
        title = "Temperatura",
        trailing = assess?.level?.label,
        trailingColor = thermalColor(assess?.level),
    ) {
        if (t == null) {
            Text("Lendo…", color = GbxColors.TextSecondary)
        } else {
            StatRow("Bateria", Formatters.celsius(t.batteryCelsius))
            StatRow("Sensor mais quente", t.hottestSensorCelsius?.let { Formatters.celsius(it) } ?: "Bloqueado pelo sistema")
            StatRow("Status térmico do sistema", t.systemThermalStatus?.let { thermalStatusLabel(it) } ?: "Indisponível (Android < 10)")
            StatRow("Margem térmica", t.thermalHeadroom?.let { "%.2f".format(it) } ?: "Indisponível")
            StatRow(
                "Limitação térmica",
                if (assess?.throttlingLikely == true) "PROVÁVEL" else "Não detectada",
                if (assess?.throttlingLikely == true) GbxColors.Warn else GbxColors.TextPrimary,
            )
            assess?.reasons?.forEach {
                Text("• $it", style = MaterialTheme.typography.bodySmall, color = GbxColors.TextSecondary)
            }
            val hist = state.monitor.temperatureHistory
            if (hist.values.isNotEmpty()) {
                Text("Temperatura da bateria (°C)", style = MaterialTheme.typography.labelSmall, color = GbxColors.TextSecondary)
                Sparkline(hist.values, 20f, 50f, thermalColor(assess?.level))
            }
        }
    }
}

private fun thermalStatusLabel(status: Int): String = when (status) {
    0 -> "Nenhum (0)"
    1 -> "Leve (1)"
    2 -> "Moderado (2)"
    3 -> "Severo (3)"
    4 -> "Crítico (4)"
    5 -> "Emergência (5)"
    6 -> "Desligamento (6)"
    else -> "Desconhecido ($status)"
}

@Composable
private fun CpuPanel(state: DashboardUiState) {
    val cpu = state.monitor.snapshot?.cpu
    GbxPanel(
        title = "CPU",
        trailing = cpu?.averageLoadOfMaxPercent?.let { "$it% da freq. máx." },
    ) {
        if (cpu == null) {
            Text("Lendo…", color = GbxColors.TextSecondary)
        } else if (cpu.currentFreqKhz.all { it == null }) {
            Text(
                "${cpu.coreCount} núcleos. O sistema bloqueia a leitura de frequência neste aparelho.",
                style = MaterialTheme.typography.bodySmall,
                color = GbxColors.TextSecondary,
            )
        } else {
            cpu.currentFreqKhz.forEachIndexed { i, cur ->
                val max = cpu.maxFreqKhz.getOrNull(i)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("C$i", style = MaterialTheme.typography.labelSmall, color = GbxColors.TextSecondary, modifier = Modifier.width(28.dp))
                    NeonBar(
                        fraction = if (cur != null && max != null && max > 0) cur.toFloat() / max else 0f,
                        color = GbxColors.Neon,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        Formatters.mhz(cur),
                        style = MaterialTheme.typography.labelSmall,
                        color = GbxColors.TextPrimary,
                        modifier = Modifier.padding(start = 8.dp).width(72.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FpsDisplayPanel(state: DashboardUiState) {
    val d = state.monitor.snapshot?.display
    GbxPanel(title = "FPS e tela", trailing = "FPS: sem fonte", trailingColor = GbxColors.TextSecondary) {
        if (d != null) {
            StatRow("Taxa de atualização atual", Formatters.hz(d.currentRefreshHz))
            StatRow("Taxas suportadas", d.supportedRefreshHz.joinToString(" / ") { Formatters.hz(it) })
            StatRow("Resolução", "${d.widthPx} × ${d.heightPx}")
        }
        Text(
            "O Android não permite que um app leia o FPS de outro app sem root ou depuração USB. " +
                "Nenhum valor de FPS é exibido até existir uma fonte de medição real (FPS Engine, próximas fases).",
            style = MaterialTheme.typography.bodySmall,
            color = GbxColors.TextSecondary,
        )
    }
}

@Composable
private fun GamesPanel(state: DashboardUiState, onBoostAndPlay: (String) -> Unit, onRefresh: () -> Unit) {
    GbxPanel(title = "Jogos detectados", trailing = if (state.gamesLoaded) "${state.games.size}" else null) {
        when {
            !state.gamesLoaded -> Text("Procurando jogos…", color = GbxColors.TextSecondary)
            state.games.isEmpty() -> Text(
                "Nenhum jogo encontrado. Suportados: Minecraft Bedrock, Roblox, Free Fire e apps que se declaram jogos.",
                style = MaterialTheme.typography.bodySmall,
                color = GbxColors.TextSecondary,
            )
            else -> state.games.forEach { game ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(game.name, color = GbxColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text(game.packageName, style = MaterialTheme.typography.labelSmall, color = GbxColors.TextSecondary)
                    }
                    OutlinedButton(onClick = { onBoostAndPlay(game.packageName) }, enabled = !state.boost.running) {
                        Text("Otimizar e jogar", color = GbxColors.Neon)
                    }
                }
            }
        }
        TextButton(onClick = onRefresh) { Text("Procurar novamente", color = GbxColors.ElectricBlue) }
    }
}

@Composable
private fun AdvicePanel(state: DashboardUiState) {
    if (state.advice.isEmpty()) return
    GbxPanel(title = "Recomendações") {
        state.advice.forEach { a ->
            val color = when (a.severity) {
                AdviceSeverity.INFO -> GbxColors.Neon
                AdviceSeverity.WARNING -> GbxColors.Warn
                AdviceSeverity.CRITICAL -> GbxColors.Danger
            }
            Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(a.title, color = color, fontWeight = FontWeight.SemiBold)
                Text(a.detail, style = MaterialTheme.typography.bodySmall, color = GbxColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun DevicePanel(state: DashboardUiState) {
    val id = state.identity ?: return
    val battery = state.monitor.snapshot?.battery
    GbxPanel(title = "Estado do dispositivo") {
        StatRow("Fabricante / modelo", "${id.manufacturer} ${id.model}")
        id.marketName?.let { StatRow("Nome comercial", it) }
        StatRow("Android", "${id.androidRelease} (API ${id.sdkInt})")
        StatRow("Chip", listOfNotNull(id.socManufacturer, id.socModel).joinToString(" ").ifBlank { "Não informado pelo sistema" })
        StatRow("Núcleos de CPU", id.cpuCores.toString())
        StatRow("Arquitetura", id.abis.firstOrNull() ?: "—")
        battery?.let {
            StatRow("Bateria", (it.levelPercent?.let { p -> "$p%" } ?: "—") + if (it.charging) " · carregando" else "")
        }
        state.profile?.notes?.forEach {
            Text("• $it", style = MaterialTheme.typography.bodySmall, color = GbxColors.TextSecondary)
        }
    }
}
