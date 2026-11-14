package com.gamebooster.x.core

/**
 * Cálculo de FPS a partir de `dumpsys SurfaceFlinger --latency <camada>`.
 *
 * Formato da saída: 1ª linha = período de atualização (ns); demais linhas =
 * "desiredPresent actualPresent frameReady" (ns). Linhas com 0 ou INT64_MAX são quadros pendentes.
 */
object LatencyParser {

    private const val PENDING = Long.MAX_VALUE

    /** Instantes reais em que cada quadro apareceu na tela, em ordem crescente. */
    fun presentTimes(output: String): List<Long> =
        output.lineSequence()
            .drop(1)
            .mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size < 3) return@mapNotNull null
                parts[1].toLongOrNull()?.takeIf { it != 0L && it != PENDING }
            }
            .distinct()
            .sorted()
            .toList()

    /**
     * Escolhe a camada do jogo em `dumpsys SurfaceFlinger --list`.
     * Jogos desenham numa SurfaceView; se não houver, usa a camada da atividade.
     */
    fun pickLayer(listOutput: String, packageName: String): String? {
        val lines = listOutput.lineSequence().map { it.trim() }.filter { it.contains(packageName) }.toList()
        return lines.firstOrNull { it.startsWith("SurfaceView") && !it.contains("Background") }
            ?: lines.firstOrNull { it.contains("SurfaceView") && !it.contains("Background") }
            ?: lines.firstOrNull { it.contains("/") && !it.startsWith("ActivityRecord") }
    }
}

/** FPS instantâneo: quadros apresentados no último segundo antes do quadro mais recente. */
object FpsCalculator {
    private const val SECOND_NS = 1_000_000_000L

    fun instantFps(presentTimes: List<Long>): Int? {
        if (presentTimes.size < 2) return null
        val last = presentTimes.last()
        val inWindow = presentTimes.count { it > last - SECOND_NS }
        // Precisa cobrir ~1 s de histórico para a contagem ser válida.
        if (last - presentTimes.first() < SECOND_NS) {
            val span = (last - presentTimes.first()).toDouble()
            return if (span <= 0) null else ((presentTimes.size - 1) * SECOND_NS / span).toInt()
        }
        return inWindow
    }
}

/**
 * Acumula intervalos entre quadros durante uma sessão de jogo, sem contar quadros repetidos
 * entre leituras consecutivas.
 */
class FrameAccumulator {
    private var lastPresent: Long = Long.MIN_VALUE
    private val intervals = ArrayList<Long>()

    val frameCount: Int get() = intervals.size

    fun add(presentTimes: List<Long>) {
        for (t in presentTimes) {
            if (t <= lastPresent) continue
            if (lastPresent != Long.MIN_VALUE) {
                val dt = t - lastPresent
                // Intervalos acima de 1 s significam jogo pausado/minimizado: não contam como quadro lento.
                if (dt in 1..1_000_000_000L) intervals += dt
            }
            lastPresent = t
        }
    }

    /** FPS médio da sessão; nulo com menos de 100 quadros (dados insuficientes). */
    fun averageFps(): Float? {
        if (intervals.size < MIN_FRAMES) return null
        return (1e9 / intervals.average()).toFloat()
    }

    /** "1% low": FPS médio do 1% de quadros mais lentos. */
    fun onePercentLowFps(): Float? {
        if (intervals.size < MIN_FRAMES) return null
        val slowest = intervals.sortedDescending().take(maxOf(1, intervals.size / 100))
        return (1e9 / slowest.average()).toFloat()
    }

    companion object {
        const val MIN_FRAMES = 100
    }
}

/**
 * Leitura de `dumpsys SurfaceFlinger --timestats -dump`: contagem total de quadros por camada.
 * Usada quando `--latency` não devolve dados (comum em Android 14/15).
 */
object TimeStatsParser {

    /** Maior `totalFrames` entre as camadas do pacote; nulo se o pacote não aparece. */
    fun totalFrames(output: String, packageName: String): Long? {
        var best: Long? = null
        var inPackageLayer = false
        for (raw in output.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("layerName") -> inPackageLayer = line.contains(packageName)
                line.startsWith("packageName") -> if (line.substringAfter("=").trim() == packageName) inPackageLayer = true
                line.startsWith("totalFrames") && inPackageLayer -> {
                    val n = line.substringAfter("=").trim().toLongOrNull()
                    if (n != null && (best == null || n > best)) best = n
                }
            }
        }
        return best
    }

    /** FPS entre duas leituras; nulo se o intervalo é inválido ou o contador reiniciou. */
    fun fpsBetween(prevFrames: Long, prevTimeMs: Long, frames: Long, timeMs: Long): Int? {
        val dt = timeMs - prevTimeMs
        val df = frames - prevFrames
        if (dt < 300 || df < 0) return null
        return Math.round(df * 1000.0 / dt).toInt()
    }
}
