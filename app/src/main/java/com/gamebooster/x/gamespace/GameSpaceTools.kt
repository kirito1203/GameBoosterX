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

/**
 * FPS real do jogo via `dumpsys SurfaceFlinger --latency` (precisa do Shizuku).
 * Retorna nulo quando não há fonte de medição — nunca estima.
 */
class FpsSampler(private val shizuku: ShizukuManager) {
    private var layer: String? = null
    private var layerPackage: String? = null
    val accumulator = FrameAccumulator()

    suspend fun sample(packageName: String): Int? {
        if (!shizuku.isReady) return null
        if (layer == null || layerPackage != packageName) {
            val list = shizuku.run("dumpsys SurfaceFlinger --list")
            layer = LatencyParser.pickLayer(list.output, packageName)
            layerPackage = packageName
        }
        val name = layer ?: return null
        val out = shizuku.run("dumpsys SurfaceFlinger --latency '${name.replace("'", "")}'")
        val times = LatencyParser.presentTimes(out.output)
        if (times.isEmpty()) {
            layer = null // a camada pode ter mudado (troca de tela no jogo); procura de novo na próxima
            return null
        }
        accumulator.add(times)
        return FpsCalculator.instantFps(times)
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
