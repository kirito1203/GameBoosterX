package com.gamebooster.x.gamespace

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.ActivityOptions
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.gamebooster.x.GameBoosterApp
import com.gamebooster.x.MainActivity
import com.gamebooster.x.R
import com.gamebooster.x.core.GameSessionRecord
import com.gamebooster.x.core.GameSpaceSettings
import com.gamebooster.x.core.InstalledGame
import com.gamebooster.x.core.PerformanceMode
import com.gamebooster.x.core.TurboCommands
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Serviço do Game Space: detecta quando um jogo abre e, durante a partida,
 * ativa Não perturbe, trava o brilho, mostra o painel flutuante e registra FPS/temperatura.
 * Ao sair do jogo, tudo é restaurado e a sessão é salva.
 */
/** O que o serviço está vendo agora (mostrado na tela para diagnóstico). */
data class GsServiceStatus(
    val foregroundPackage: String? = null,
    val sessionGame: String? = null,
    val overlayPermission: Boolean = false,
    val usagePermission: Boolean = false,
    val gamesKnown: Int = 0,
    val barVisible: Boolean = false,
)

class GameSpaceService : Service(), OverlayActions {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val container by lazy { (application as GameBoosterApp).container }
    private val controls by lazy { SystemControls(this) }
    private val detector by lazy { ForegroundDetector(this) }
    private val overlay by lazy { OverlayController(this, this) }
    private val capture by lazy { CaptureTools(this, container.shizuku) }

    private var loopJob: Job? = null
    private var games: Map<String, InstalledGame> = emptyMap()
    private var gamesScannedAt = 0L

    // Estado da sessão
    private var sessionGame: InstalledGame? = null
    private var sessionStart = 0L
    private var lastSeenInForeground = 0L
    private var previousDnd: Int? = null
    private var previousBrightness: Int? = null
    private var maxTemp: Float? = null
    private var fpsSampler: FpsSampler? = null
    private var lastFps: Int? = null
    private var settings = GameSpaceSettings()
    private var sidebarApps: Set<String> = emptySet()
    private var previewUntil = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val notification = buildNotification("Aguardando um jogo abrir…")
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        _running.value = true

        scope.launch {
            GameNotificationListener.notifications.collect { n ->
                if (sessionGame != null && settings.notificationTicker && settings.overlay) {
                    overlay.showNotification("${n.appLabel}: ${n.title} ${n.text}".trim())
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_PREVIEW) {
            previewUntil = System.currentTimeMillis() + PREVIEW_MS
        }
        if (loopJob?.isActive != true) loopJob = scope.launch { loop() }
        return START_STICKY
    }

    private suspend fun loop() {
        while (scope.isActive) {
            try {
                tick()
            } catch (e: Exception) {
                // Nunca derruba o serviço por uma leitura que falhou; tenta de novo no próximo ciclo.
            }
            delay(if (sessionGame != null) 1_000L else 2_000L)
        }
    }

    private suspend fun tick() {
        val now = System.currentTimeMillis()
        if (now - gamesScannedAt > 5 * 60_000L) {
            games = container.games.scan().associateBy { it.packageName }
            gamesScannedAt = now
        }
        settings = container.settings.gameSpaceSettingsNow()
        val apps = container.settings.sidebarAppsNow()
        if (apps != sidebarApps) {
            sidebarApps = apps
            overlay.setShortcuts(apps.map { SidebarShortcut(it, container.games.label(it)) }.sortedBy { it.label.lowercase() })
        }
        container.shizuku.refresh()

        val fg = detector.foregroundPackage()
        val current = sessionGame
        val fgGame = fg?.let { games[it] }

        if (fgGame != null) {
            lastSeenInForeground = now
            if (current == null) startSession(fgGame)
            else if (current.packageName != fgGame.packageName) {
                endSession(); startSession(fgGame)
            }
        } else if (current != null && fg != null && fg in sidebarApps) {
            // App aberto pela barra lateral (janela flutuante ou tela cheia): a partida continua.
            lastSeenInForeground = now
        } else if (current != null && now - lastSeenInForeground > END_GRACE_MS) {
            endSession()
        }

        sessionGame?.let { updateSession(it) }

        // Prévia: mostra a barra fora do jogo por alguns segundos para testar a permissão.
        if (sessionGame == null) {
            if (now < previewUntil) {
                if (!overlay.isShowing) overlay.show()
                overlay.setShortcuts(sidebarApps.map { SidebarShortcut(it, container.games.label(it)) })
                val snap = container.hardware.snapshot()
                overlay.update(
                    OverlayData(
                        gameName = "Prévia", fps = null, batteryCelsius = snap.thermal.batteryCelsius,
                        ramPercent = snap.memory.usedPercent, batteryPercent = snap.battery.levelPercent,
                        clock = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
                        dndActive = false, brightnessLocked = false, recording = false,
                        captureAvailable = container.shizuku.isReady, showFps = settings.fpsCounter,
                    ),
                )
            } else if (previewUntil != 0L) {
                previewUntil = 0L
                overlay.hide()
            }
        }

        _status.value = GsServiceStatus(
            foregroundPackage = fg,
            sessionGame = sessionGame?.name,
            overlayPermission = controls.isGranted(GsPermission.OVERLAY),
            usagePermission = controls.isGranted(GsPermission.USAGE),
            gamesKnown = games.size,
            barVisible = overlay.isShowing,
        )
    }

    private fun startSession(game: InstalledGame) {
        sessionGame = game
        sessionStart = System.currentTimeMillis()
        lastSeenInForeground = sessionStart
        maxTemp = null
        lastFps = null
        fpsSampler = FpsSampler(container.shizuku)
        GameNotificationListener.sessionActive = true
        if (settings.doNotDisturb) previousDnd = controls.enableGameDnd()
        if (settings.lockBrightness) previousBrightness = controls.lockBrightness()
        if (settings.overlay) overlay.show()
        notify("Jogando ${game.name}")
    }

    private suspend fun updateSession(game: InstalledGame) {
        val turbo = container.settings.turboSettings(game.packageName).first()
        val snap = container.hardware.snapshot()
        snap.thermal.batteryCelsius?.let { t -> maxTemp = maxOf(maxTemp ?: t, t) }
        lastFps = if (settings.fpsCounter) fpsSampler?.sample(game.packageName) else null

        if (settings.overlay && !overlay.isShowing) overlay.show()
        if (!settings.overlay && overlay.isShowing) overlay.hide()
        overlay.update(
            OverlayData(
                gameName = game.name,
                fps = lastFps,
                batteryCelsius = snap.thermal.batteryCelsius,
                ramPercent = snap.memory.usedPercent,
                batteryPercent = snap.battery.levelPercent,
                clock = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
                dndActive = previousDnd != null,
                brightnessLocked = previousBrightness != null,
                recording = capture.recording.value,
                captureAvailable = container.shizuku.isReady,
                showFps = settings.fpsCounter,
                turboAvailable = container.shizuku.isReady,
                turboScale = turbo.resolutionScale,
                turboFps = turbo.fpsCap,
                alertMode = container.settings.performanceMode.first(),
            ),
        )
    }

    private suspend fun endSession() {
        val game = sessionGame ?: return
        if (capture.recording.value) capture.stopRecording()
        previousDnd?.let { controls.restoreDnd(it) }
        previousBrightness?.let { controls.restoreBrightness(it) }
        previousDnd = null
        previousBrightness = null
        GameNotificationListener.sessionActive = false
        overlay.hide()

        val duration = lastSeenInForeground - sessionStart
        if (duration >= MIN_SESSION_MS) {
            val acc = fpsSampler?.accumulator
            container.sessions.add(
                GameSessionRecord(
                    packageName = game.packageName,
                    startMs = sessionStart,
                    durationMs = duration,
                    maxBatteryCelsius = maxTemp,
                    averageFps = acc?.averageFps(),
                    onePercentLowFps = acc?.onePercentLowFps(),
                ),
            )
        }
        sessionGame = null
        fpsSampler = null
        notify("Aguardando um jogo abrir…")
    }

    // ---------- Ações do painel ----------

    override fun onScreenshot() {
        scope.launch {
            overlay.setHidden(true)
            delay(350)
            val path = capture.screenshot()
            overlay.setHidden(false)
            overlay.showNotification(if (path != null) "Captura salva em Imagens/GameBoosterX" else "Falha na captura (Shizuku ativo?)")
        }
    }

    override fun onToggleRecording() {
        scope.launch {
            if (capture.recording.value) {
                val path = capture.stopRecording()
                overlay.showNotification(if (path != null) "Vídeo salvo em Filmes/GameBoosterX" else "Falha ao salvar o vídeo")
            } else {
                val ok = capture.startRecording()
                overlay.showNotification(if (ok) "Gravando (até 3 min, sem áudio)" else "Falha ao gravar (Shizuku ativo?)")
            }
        }
    }

    override fun onLaunchShortcut(packageName: String, floating: Boolean) {
        scope.launch {
            val intent = container.games.launchIntent(packageName) ?: run {
                overlay.showNotification("App não encontrado")
                return@launch
            }
            val component = intent.component?.flattenToShortString()
            if (floating) {
                // Quadro no centro: ~55% da largura e 65% da altura da tela atual.
                val dm = resources.displayMetrics
                val w = (dm.widthPixels * 0.55f).toInt()
                val h = (dm.heightPixels * 0.65f).toInt()
                val left = (dm.widthPixels - w) / 2
                val top = (dm.heightPixels - h) / 2
                if (component != null && container.shizuku.isReady) {
                    // windowingMode 5 = janela livre (freeform). O sistema pode ignorar o pedido.
                    val r = container.shizuku.run("am start --windowingMode 5 -n $component")
                    if (r.ok) {
                        overlay.showNotification("Se abriu em tela cheia: ative \"Janelas flutuantes\" na aba Game Space")
                        return@launch
                    }
                }
                val options = ActivityOptions.makeBasic().setLaunchBounds(Rect(left, top, left + w, top + h))
                val ok = runCatching { startActivity(intent, options.toBundle()) }.isSuccess
                if (ok) {
                    overlay.showNotification("Se abriu em tela cheia: ative \"Janelas flutuantes\" na aba Game Space")
                    return@launch
                }
            }
            runCatching { startActivity(intent) }
                .onFailure { overlay.showNotification("Não foi possível abrir o app") }
        }
    }

    // ---------- Turbo, modos e RAM pela barra ----------

    override fun onTurboScale(scale: Float) {
        val pkg = sessionGame?.packageName ?: return
        scope.launch {
            val cur = container.settings.turboSettings(pkg).first()
            container.settings.setTurboSettings(pkg, cur.copy(resolutionScale = scale))
        }
    }

    override fun onTurboFps(fps: Int) {
        val pkg = sessionGame?.packageName ?: return
        scope.launch {
            val cur = container.settings.turboSettings(pkg).first()
            container.settings.setTurboSettings(pkg, cur.copy(fpsCap = fps))
        }
    }

    override fun onTurboApply() {
        val game = sessionGame ?: return
        if (!container.shizuku.isReady) {
            overlay.showNotification("Inicie o Shizuku para usar o Turbo")
            return
        }
        scope.launch {
            val settings = container.settings.turboSettings(game.packageName).first()
            val results = TurboCommands.apply(game.packageName, settings).map { container.shizuku.run(it) }
            val setOk = results.filter { it.command.startsWith("cmd game") && !it.command.contains("list-configs") }
                .any { it.ok }
            // A resolução do Game Mode só vale quando o jogo abre de novo.
            container.shizuku.run("am force-stop ${game.packageName}")
            delay(900)
            container.games.launchIntent(game.packageName)?.let { runCatching { startActivity(it) } }
            delay(2_500)
            overlay.showNotification(
                if (setOk) "Turbo aplicado: ${TurboCommands.formatScale(settings.resolutionScale)}x, " +
                    (if (settings.fpsCap == 0) "FPS livre" else "${settings.fpsCap} FPS")
                else "O sistema recusou o Turbo neste jogo (veja o Registro no Painel)",
            )
        }
    }

    override fun onTurboRestore() {
        val pkg = sessionGame?.packageName ?: return
        scope.launch {
            val results = TurboCommands.restore(pkg).map { container.shizuku.run(it) }
            overlay.showNotification(
                if (results.any { it.ok }) "Padrão restaurado (vale na próxima abertura do jogo)"
                else "Não foi possível restaurar",
            )
        }
    }

    override fun onAlertMode(mode: PerformanceMode) {
        scope.launch {
            container.settings.setPerformanceMode(mode)
            overlay.showNotification("Alertas: ${mode.label}")
        }
    }

    override fun onCleanRam() {
        val keep = setOfNotNull(sessionGame?.packageName)
        scope.launch {
            val report = container.cleaner.boost(keep)
            overlay.showNotification(report.summary())
        }
    }

    override fun onClearCache() {
        scope.launch {
            overlay.showNotification("Limpando cache…")
            overlay.showNotification(container.storage.clearAppCaches().message)
        }
    }

    override fun onOptimizeAll() {
        val keep = setOfNotNull(sessionGame?.packageName)
        scope.launch {
            overlay.showNotification("Otimizando…")
            val ram = container.cleaner.boost(keep)
            val cache = container.storage.clearAppCaches()
            val ramText = if (ram.deltaBytes > 0) "RAM +${com.gamebooster.x.core.Formatters.bytes(ram.deltaBytes)}" else "RAM sem variação"
            overlay.showNotification("$ramText · ${cache.message}")
        }
    }

    override fun onToggleDnd() {
        scope.launch {
            val prev = previousDnd
            if (prev != null) {
                controls.restoreDnd(prev); previousDnd = null
            } else {
                previousDnd = controls.enableGameDnd()
                if (previousDnd == null) overlay.showNotification("Conceda o acesso ao Não perturbe no app")
            }
        }
    }

    override fun onToggleBrightness() {
        scope.launch {
            val prev = previousBrightness
            if (prev != null) {
                controls.restoreBrightness(prev); previousBrightness = null
            } else {
                previousBrightness = controls.lockBrightness()
                if (previousBrightness == null) overlay.showNotification("Conceda \"Modificar configurações\" no app")
            }
        }
    }

    // ---------- Notificação do serviço ----------

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Game Space", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Mostra que o Game Space está ativo"
            },
        )
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, GameSpaceService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_gbx)
            .setContentTitle("Game Space ativo")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Desligar", stop)
            .build()
    }

    private fun notify(text: String) {
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        // Garante que nada fique alterado se o serviço for desligado no meio de uma partida.
        previousDnd?.let { controls.restoreDnd(it) }
        previousBrightness?.let { controls.restoreBrightness(it) }
        GameNotificationListener.sessionActive = false
        overlay.hide()
        if (capture.recording.value) container.shizuku.startBackground("pkill -INT screenrecord")
        scope.cancel()
        _running.value = false
        _status.value = GsServiceStatus()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "game_space"
        private const val NOTIFICATION_ID = 7101
        private const val ACTION_STOP = "com.gamebooster.x.STOP_GAME_SPACE"
        private const val END_GRACE_MS = 5_000L
        private const val MIN_SESSION_MS = 30_000L

        private const val ACTION_PREVIEW = "com.gamebooster.x.PREVIEW_SIDEBAR"
        private const val PREVIEW_MS = 15_000L

        private val _status = MutableStateFlow(GsServiceStatus())
        val status: StateFlow<GsServiceStatus> = _status.asStateFlow()

        /** Liga o serviço (se preciso) e mostra a barra por 15 s fora do jogo. */
        fun preview(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, GameSpaceService::class.java).setAction(ACTION_PREVIEW),
            )
        }

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, GameSpaceService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GameSpaceService::class.java))
        }
    }
}
