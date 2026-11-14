package com.gamebooster.x.core

/** Uma partida registrada. Campos nulos = não havia fonte de medição. */
data class GameSessionRecord(
    val packageName: String,
    val startMs: Long,
    val durationMs: Long,
    val maxBatteryCelsius: Float?,
    val averageFps: Float?,
    val onePercentLowFps: Float?,
)

/** Resumo por jogo. */
data class GameStats(
    val packageName: String,
    val sessions: Int,
    val totalPlayMs: Long,
    val lastAverageFps: Float?,
    val bestAverageFps: Float?,
    val maxBatteryCelsius: Float?,
)

/** Configurações das funções estilo Game Space. */
data class GameSpaceSettings(
    val doNotDisturb: Boolean = true,
    val lockBrightness: Boolean = true,
    val overlay: Boolean = true,
    val fpsCounter: Boolean = true,
    val notificationTicker: Boolean = true,
)

/** Formato de arquivo simples (uma sessão por linha), sem dependências, testável na JVM. */
object SessionCodec {
    private const val SEP = "|"

    fun encode(r: GameSessionRecord): String = listOf(
        r.packageName, r.startMs, r.durationMs,
        r.maxBatteryCelsius ?: "", r.averageFps ?: "", r.onePercentLowFps ?: "",
    ).joinToString(SEP)

    fun decode(line: String): GameSessionRecord? {
        val p = line.trim().split(SEP)
        if (p.size != 6 || !TurboCommands.isValidPackage(p[0])) return null
        return GameSessionRecord(
            packageName = p[0],
            startMs = p[1].toLongOrNull() ?: return null,
            durationMs = p[2].toLongOrNull() ?: return null,
            maxBatteryCelsius = p[3].toFloatOrNull(),
            averageFps = p[4].toFloatOrNull(),
            onePercentLowFps = p[5].toFloatOrNull(),
        )
    }

    fun decodeAll(text: String): List<GameSessionRecord> = text.lineSequence().mapNotNull { decode(it) }.toList()
}

object GameStatsAggregator {
    fun aggregate(records: List<GameSessionRecord>): Map<String, GameStats> =
        records.groupBy { it.packageName }.mapValues { (pkg, list) ->
            val sorted = list.sortedBy { it.startMs }
            GameStats(
                packageName = pkg,
                sessions = list.size,
                totalPlayMs = list.sumOf { it.durationMs },
                lastAverageFps = sorted.lastOrNull { it.averageFps != null }?.averageFps,
                bestAverageFps = list.mapNotNull { it.averageFps }.maxOrNull(),
                maxBatteryCelsius = list.mapNotNull { it.maxBatteryCelsius }.maxOrNull(),
            )
        }

    fun formatDuration(ms: Long): String {
        val totalMin = ms / 60_000
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h > 0 -> "${h}h ${m}min"
            m > 0 -> "${m} min"
            else -> "< 1 min"
        }
    }
}
