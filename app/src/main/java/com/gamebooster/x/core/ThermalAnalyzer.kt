package com.gamebooster.x.core

enum class ThermalLevel(val label: String) {
    UNKNOWN("Indisponível"),
    NORMAL("Normal"),
    WARM("Morno"),
    HOT("Quente"),
    CRITICAL("Crítico"),
}

data class ThermalAssessment(
    val level: ThermalLevel,
    /** true quando há sinal concreto de limitação térmica (throttling) pelo sistema. */
    val throttlingLikely: Boolean,
    val reasons: List<String>,
)

/**
 * Combina as fontes térmicas disponíveis e devolve o pior nível encontrado.
 * Nunca inventa dados: se nenhuma fonte existir, o nível é [ThermalLevel.UNKNOWN].
 */
object ThermalAnalyzer {

    // Valores de android.os.PowerManager.THERMAL_STATUS_*
    const val STATUS_NONE = 0
    const val STATUS_LIGHT = 1
    const val STATUS_MODERATE = 2
    const val STATUS_SEVERE = 3

    fun assess(reading: ThermalReading, policy: ModePolicy): ThermalAssessment {
        var level = ThermalLevel.UNKNOWN
        var throttling = false
        val reasons = mutableListOf<String>()

        fun raise(to: ThermalLevel) {
            if (to.ordinal > level.ordinal) level = to
        }

        reading.systemThermalStatus?.let { status ->
            when {
                status >= STATUS_SEVERE -> {
                    raise(ThermalLevel.CRITICAL); throttling = true
                    reasons += "Sistema informa estado térmico severo (nível $status)."
                }
                status == STATUS_MODERATE -> {
                    raise(ThermalLevel.HOT); throttling = true
                    reasons += "Sistema informa limitação térmica moderada."
                }
                status == STATUS_LIGHT -> {
                    raise(ThermalLevel.WARM)
                    reasons += "Sistema informa limitação térmica leve."
                }
                else -> raise(ThermalLevel.NORMAL)
            }
        }

        reading.thermalHeadroom?.let { headroom ->
            when {
                headroom >= 1.0f -> {
                    raise(ThermalLevel.CRITICAL); throttling = true
                    reasons += "Margem térmica esgotada (headroom ${"%.2f".format(headroom)})."
                }
                headroom >= 0.85f -> {
                    raise(ThermalLevel.HOT)
                    reasons += "Margem térmica baixa (headroom ${"%.2f".format(headroom)})."
                }
                else -> raise(ThermalLevel.NORMAL)
            }
        }

        val temps = listOfNotNull(reading.batteryCelsius, reading.hottestSensorCelsius)
        temps.maxOrNull()?.let { t ->
            when {
                t >= policy.thermalCriticalCelsius -> {
                    raise(ThermalLevel.CRITICAL)
                    reasons += "Temperatura ${"%.1f".format(t)} °C acima do limite crítico do modo ${policy.mode.label}."
                }
                t >= policy.thermalWarnCelsius -> {
                    raise(ThermalLevel.HOT)
                    reasons += "Temperatura ${"%.1f".format(t)} °C acima do limite de alerta do modo ${policy.mode.label}."
                }
                t >= policy.thermalWarnCelsius - 3f -> raise(ThermalLevel.WARM)
                else -> raise(ThermalLevel.NORMAL)
            }
        }

        return ThermalAssessment(level, throttling, reasons)
    }
}
