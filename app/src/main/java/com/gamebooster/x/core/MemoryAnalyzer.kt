package com.gamebooster.x.core

enum class MemoryPressure(val label: String) {
    LOW("Baixa"),
    MODERATE("Moderada"),
    HIGH("Alta"),
    CRITICAL("Crítica"),
}

data class MemoryAssessment(
    val usedPercent: Int,
    val pressure: MemoryPressure,
    /** true quando o uso ultrapassa o limite de alerta do modo ativo. */
    val aboveModeAlert: Boolean,
)

object MemoryAnalyzer {

    fun assess(reading: MemoryReading, policy: ModePolicy): MemoryAssessment {
        val used = reading.usedPercent
        val pressure = when {
            reading.systemLowMemory -> MemoryPressure.CRITICAL
            used >= 88 -> MemoryPressure.CRITICAL
            used >= 75 -> MemoryPressure.HIGH
            used >= 60 -> MemoryPressure.MODERATE
            else -> MemoryPressure.LOW
        }
        return MemoryAssessment(
            usedPercent = used,
            pressure = pressure,
            aboveModeAlert = reading.systemLowMemory || used >= policy.memoryAlertPercent,
        )
    }
}
