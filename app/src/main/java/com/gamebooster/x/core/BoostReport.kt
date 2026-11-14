package com.gamebooster.x.core

/**
 * Resultado medido de uma otimização. Os valores vêm de leituras reais antes e
 * depois; o ganho pode ser zero ou negativo (o Android pode reabrir processos).
 */
data class BoostReport(
    val availableBeforeBytes: Long,
    val availableAfterBytes: Long,
    val processesTargeted: Int,
    val durationMs: Long,
    val timestampMs: Long,
) {
    val deltaBytes: Long get() = availableAfterBytes - availableBeforeBytes

    fun summary(): String = when {
        deltaBytes > 0 -> "Memória disponível aumentou ${Formatters.bytes(deltaBytes)} " +
            "($processesTargeted apps em segundo plano solicitados)."
        deltaBytes == 0L -> "Nenhuma variação de memória medida. Os apps em segundo plano já estavam encerrados " +
            "ou o sistema os mantém protegidos."
        else -> "Memória disponível caiu ${Formatters.bytes(-deltaBytes)} durante a medição " +
            "(o sistema reabriu processos). Resultado registrado sem ajustes."
    }
}
