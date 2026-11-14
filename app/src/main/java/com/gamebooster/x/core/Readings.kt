package com.gamebooster.x.core

/** Leitura bruta de memória (valores de ActivityManager.MemoryInfo). */
data class MemoryReading(
    val totalBytes: Long,
    val availableBytes: Long,
    val lowMemoryThresholdBytes: Long,
    val systemLowMemory: Boolean,
) {
    val usedBytes: Long get() = (totalBytes - availableBytes).coerceAtLeast(0L)
    val usedPercent: Int
        get() = if (totalBytes <= 0L) 0 else ((usedBytes * 100L) / totalBytes).toInt()
}

/**
 * Leitura térmica. Cada campo é nulo quando o aparelho não fornece o dado.
 *
 * @param batteryCelsius temperatura da bateria (sempre disponível na prática).
 * @param systemThermalStatus PowerManager.THERMAL_STATUS_* (0 a 6), Android 10+.
 * @param thermalHeadroom PowerManager.getThermalHeadroom, Android 11+. 1.0 = limite de throttling.
 * @param hottestSensorCelsius maior valor lido em /sys/class/thermal, se acessível.
 */
data class ThermalReading(
    val batteryCelsius: Float?,
    val systemThermalStatus: Int?,
    val thermalHeadroom: Float?,
    val hottestSensorCelsius: Float?,
)

/** Frequências de CPU em kHz por núcleo; nulo quando o sistema bloqueia a leitura. */
data class CpuReading(
    val coreCount: Int,
    val currentFreqKhz: List<Long?>,
    val maxFreqKhz: List<Long?>,
) {
    /** Percentual médio da frequência atual sobre a máxima, entre núcleos legíveis. */
    val averageLoadOfMaxPercent: Int?
        get() {
            val pairs = currentFreqKhz.zip(maxFreqKhz)
                .mapNotNull { (cur, max) -> if (cur != null && max != null && max > 0) cur to max else null }
            if (pairs.isEmpty()) return null
            return (pairs.sumOf { (cur, max) -> cur.toDouble() / max } / pairs.size * 100).toInt()
        }
}

data class BatteryReading(
    val levelPercent: Int?,
    val charging: Boolean,
)

data class DisplayReading(
    val currentRefreshHz: Float,
    val supportedRefreshHz: List<Float>,
    val widthPx: Int,
    val heightPx: Int,
)

/** Uma amostra completa do monitor de hardware. */
data class HardwareSnapshot(
    val timestampMs: Long,
    val memory: MemoryReading,
    val thermal: ThermalReading,
    val cpu: CpuReading,
    val battery: BatteryReading,
    val display: DisplayReading?,
)
