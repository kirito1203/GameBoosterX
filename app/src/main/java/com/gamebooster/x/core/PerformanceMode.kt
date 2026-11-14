package com.gamebooster.x.core

/**
 * Modos de desempenho do GAME BOOSTER X.
 *
 * Código puro em Kotlin (sem dependências Android) para ser testável na JVM.
 */
enum class PerformanceMode(val label: String, val tagline: String) {
    EXTREME("Extreme", "Leituras mais rápidas, alertas de calor mais tolerantes"),
    BALANCED("Balanced", "Equilíbrio entre precisão das leituras e economia"),
    COOL("Cool", "Avisa mais cedo sobre aquecimento; ideal para partidas longas");

    companion object {
        val DEFAULT = BALANCED

        fun fromName(name: String?): PerformanceMode =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * Configuração documentada e aplicada de cada modo.
 *
 * Comportamentos reais aplicados na Fase 1:
 * - [sampleIntervalMs]: intervalo de leitura do monitor de hardware (menos leituras = menos custo).
 * - [thermalWarnCelsius]/[thermalCriticalCelsius]: limites usados pelos alertas térmicos.
 * - [keepScreenOn]: mantém a tela ligada enquanto o app está aberto.
 * - [memoryAlertPercent]: uso de RAM a partir do qual o app recomenda liberar memória.
 */
data class ModePolicy(
    val mode: PerformanceMode,
    val sampleIntervalMs: Long,
    val thermalWarnCelsius: Float,
    val thermalCriticalCelsius: Float,
    val keepScreenOn: Boolean,
    val memoryAlertPercent: Int,
    val description: String,
)

object ModePolicies {

    fun of(mode: PerformanceMode): ModePolicy = when (mode) {
        PerformanceMode.EXTREME -> ModePolicy(
            mode = mode,
            sampleIntervalMs = 1_000L,
            thermalWarnCelsius = 42f,
            thermalCriticalCelsius = 46f,
            keepScreenOn = true,
            memoryAlertPercent = 70,
            description = "Leitura a cada 1 s, tela ligada com o app aberto, " +
                "alerta de RAM a partir de 70% e limites térmicos mais altos.",
        )
        PerformanceMode.BALANCED -> ModePolicy(
            mode = mode,
            sampleIntervalMs = 2_000L,
            thermalWarnCelsius = 40f,
            thermalCriticalCelsius = 44f,
            keepScreenOn = true,
            memoryAlertPercent = 75,
            description = "Leitura a cada 2 s, tela ligada com o app aberto, " +
                "alerta de RAM a partir de 75%.",
        )
        PerformanceMode.COOL -> ModePolicy(
            mode = mode,
            sampleIntervalMs = 3_000L,
            thermalWarnCelsius = 38f,
            thermalCriticalCelsius = 42f,
            keepScreenOn = false,
            memoryAlertPercent = 80,
            description = "Leitura a cada 3 s (menor custo), tela pode apagar, " +
                "alertas térmicos mais cedo para evitar aquecimento.",
        )
    }
}
