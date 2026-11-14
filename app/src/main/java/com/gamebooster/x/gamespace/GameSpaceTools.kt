package com.gamebooster.x.gamespace

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.media.MediaScannerConnection
import com.gamebooster.x.core.FpsCalculator
import com.gamebooster.x.core.FrameAccumulator
import com.gamebooster.x.core.GameSessionRecord
import com.gamebooster.x.core.LatencyParser
import com.gamebooster.x.core.SessionCodec
import com.gamebooster.x.core.TimeStatsParser
import com.gamebooster.x.performance.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Descobre qual app está em primeiro plano usando UsageStats (permissão "Acesso ao uso").
 *
 * Relê sempre uma janela recente (com sobreposição) porque o sistema pode gravar eventos com
 * atraso; ler só "desde a última consulta" perdia a abertura do jogo em alguns aparelhos.
 */
class ForegroundDetector(context: Context) {
    private val usm: UsageStatsManager? = context.applicationContext.getSystemService(UsageStatsManager::class.java)
    private var current: String? = null
    private var firstQuery = true

    @Suppress("DEPRECATION")
    fun foregroundPackage(): String? {
        val manager = usm ?: return null
        val now = System.currentTimeMillis()
        // Na primeira leitura olha mais para trás para pegar um jogo que já estava aberto.
        val from = now - if (firstQuery) 10 * 60_000L else WINDOW_MS
        firstQuery = false
        val events = manager.queryEvents(from, now) ?: return current
        var state: String? = current
        var sawEvent = false
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> { state = e.packageName; sawEvent = true }
                UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    if (e.packageName == state) state = null
                    sawEvent = true
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> { state = null; sawEvent = true }
            }
        }
        if (sawEvent) current = state
        return current
    }

    private companion object {
        const val WINDOW_MS = 15_000L
    }
}

enum class FpsSource(val label: String) {
    NONE("nenhuma"),
    LATENCY("SurfaceFlinger --latency"),
    TIMESTATS("SurfaceFlinger --timestats"),
}

/**
 * FPS real do jogo (precisa do Shizuku). Tenta `--latency` (dá também o 1% low); se o sistema
 * não devolver dados, usa `--timestats` (contagem de quadros por segundo).
 * Retorna nulo quando não há fonte de medição — nunca estima.
 */
class FpsSampler(private val shizuku: ShizukuManager) {
    private var layer: String? = null
    private var layerPackage: String? = null
    private var latencyFailures = 0
    private var timestatsEnabled = false
    private var prevFrames: Long? = null
    private var prevTime = 0L
    private var firstFrames: Long? = null
    private var firstTime = 0L
    private var lastFrames: Long? = null
    private var lastTime = 0L

    val accumulator = FrameAccumulator()
    var source: FpsSource = FpsSource.NONE
        private set
    var lastProblem: String? = null
        private set

    private var lastSampleAt = 0L
    private var lastValue: Int? = null

    /**
     * Mede no máximo a cada [MIN_INTERVAL_MS]: cada consulta ao SurfaceFlinger custa um pouco de
     * tempo de quadro, então menos consultas = menos risco de travadinha no jogo.
     */
    suspend fun sample(packageName: String): Int? {
        val now = System.currentTimeMillis()
        if (now - lastSampleAt < MIN_INTERVAL_MS) return lastValue
        lastSampleAt = now
        lastValue = sampleNow(packageName)
        return lastValue
    }

    private suspend fun sampleNow(packageName: String): Int? {
        if (!shizuku.isReady) {
            source = FpsSource.NONE
            lastProblem = "Shizuku parado"
            return null
        }
        if (latencyFailures < 3) {
            val fps = sampleLatency(packageName)
            if (fps != null) {
                latencyFailures = 0
                source = FpsSource.LATENCY
                lastProblem = null
                return fps
            }
            latencyFailures++
        }
        val fps = sampleTimeStats(packageName)
        source = if (fps != null) FpsSource.TIMESTATS else FpsSource.NONE
        return fps
    }

    private suspend fun sampleLatency(packageName: String): Int? {
        if (layer == null || layerPackage != packageName) {
            val list = shizuku.run("dumpsys SurfaceFlinger --list")
            layer = LatencyParser.pickLayer(list.output, packageName)
            layerPackage = packageName
        }
        val name = layer ?: run {
            lastProblem = "Camada do jogo não encontrada"
            return null
        }
        val out = shizuku.run("dumpsys SurfaceFlinger --latency '${name.replace("'", "")}'")
        val times = LatencyParser.presentTimes(out.output)
        if (times.isEmpty()) {
            layer = null
            lastProblem = "--latency sem dados"
            return null
        }
        accumulator.add(times)
        return FpsCalculator.instantFps(times)
    }

    private suspend fun sampleTimeStats(packageName: String): Int? {
        if (!timestatsEnabled) {
            shizuku.run("dumpsys SurfaceFlinger --timestats -enable")
            timestatsEnabled = true
        }
        // -maxlayers limita a saída às camadas com mais quadros (o jogo), deixando o comando leve.
        val out = shizuku.run("dumpsys SurfaceFlinger --timestats -dump -maxlayers 3")
        val now = System.currentTimeMillis()
        val frames = TimeStatsParser.totalFrames(out.output, packageName) ?: run {
            lastProblem = "--timestats sem dados do jogo"
            return null
        }
        if (firstFrames == null) { firstFrames = frames; firstTime = now }
        lastFrames = frames; lastTime = now
        val pf = prevFrames
        val pt = prevTime
        prevFrames = frames; prevTime = now
        if (pf == null) return null
        val fps = TimeStatsParser.fpsBetween(pf, pt, frames, now)
        if (fps == null) {
            // Contador reiniciou (jogo reaberto): recomeça a média.
            firstFrames = frames; firstTime = now
        } else {
            lastProblem = null
        }
        return fps
    }

    /** FPS médio da sessão pela fonte que funcionou. */
    fun sessionAverageFps(): Float? = accumulator.averageFps() ?: run {
        val f0 = firstFrames ?: return null
        val f1 = lastFrames ?: return null
        val seconds = (lastTime - firstTime) / 1000.0
        if (seconds < 10) null else ((f1 - f0) / seconds).toFloat()
    }

    /** 1% low só existe com --latency (precisa dos tempos de cada quadro). */
    fun sessionOnePercentLow(): Float? = accumulator.onePercentLowFps()

    companion object {
        const val MIN_INTERVAL_MS = 3_000L
    }

    suspend fun release() {
        if (timestatsEnabled) shizuku.run("dumpsys SurfaceFlinger --timestats -disable")
        timestatsEnabled = false
    }
}

/** Captura e gravação de tela pelo Shizuku (comandos screencap/screenrecord do shell). */
class CaptureTools(private val context: Context, private val shizuku: ShizukuManager) {

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()
    private var recordingPath: String? = null

    suspend fun screenshot(): String? {
        if (!shizuku.isReady) return null
        val path = "/sdcard/Pictures/GameBoosterX/gbx_${System.currentTimeMillis()}.png"
        val r = shizuku.run("mkdir -p /sdcard/Pictures/GameBoosterX && screencap -p $path")
        if (r.exitCode != 0) return null
        scan(path)
        return path
    }

    fun startRecording(): Boolean {
        if (!shizuku.isReady || _recording.value) return false
        val path = "/sdcard/Movies/GameBoosterX/gbx_${System.currentTimeMillis()}.mp4"
        val ok = shizuku.startBackground("mkdir -p /sdcard/Movies/GameBoosterX && screenrecord --time-limit 180 $path")
        if (ok) {
            recordingPath = path
            _recording.value = true
        }
        return ok
    }

    /** Para com SIGINT para o arquivo MP4 ser finalizado corretamente. */
    suspend fun stopRecording(): String? {
        if (!_recording.value) return null
        shizuku.run("pkill -INT screenrecord")
        _recording.value = false
        delay(1_500)
        val path = recordingPath
        recordingPath = null
        path?.let { scan(it) }
        return path
    }

    private fun scan(path: String) {
        MediaScannerConnection.scanFile(context.applicationContext, arrayOf(path), null, null)
    }
}

/** Histórico de partidas em arquivo local (sem internet). */
class SessionRepository(context: Context) {
    private val file = File(context.applicationContext.filesDir, "sessions.txt")
    private val mutex = Mutex()
    private val _sessions = MutableStateFlow<List<GameSessionRecord>>(emptyList())
    val sessions: StateFlow<List<GameSessionRecord>> = _sessions.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            _sessions.value = if (file.exists()) SessionCodec.decodeAll(file.readText()) else emptyList()
        }
    }

    suspend fun add(record: GameSessionRecord) = withContext(Dispatchers.IO) {
        mutex.withLock {
            file.appendText(SessionCodec.encode(record) + "\n")
            _sessions.value = _sessions.value + record
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock {
            file.delete()
            _sessions.value = emptyList()
        }
    }
}
