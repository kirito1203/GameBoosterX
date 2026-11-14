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

/** Descobre qual app está em primeiro plano usando UsageStats (permissão "Acesso ao uso"). */
class ForegroundDetector(context: Context) {
    private val usm: UsageStatsManager? = context.applicationContext.getSystemService(UsageStatsManager::class.java)
    private var current: String? = null
    private var lastQuery = System.currentTimeMillis() - 60_000

    @Suppress("DEPRECATION")
    fun foregroundPackage(): String? {
        val manager = usm ?: return null
        val now = System.currentTimeMillis()
        val events = manager.queryEvents(lastQuery, now) ?: return current
        lastQuery = now
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> current = e.packageName
                UsageEvents.Event.MOVE_TO_BACKGROUND -> if (e.packageName == current) current = null
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> current = null
            }
        }
        return current
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
