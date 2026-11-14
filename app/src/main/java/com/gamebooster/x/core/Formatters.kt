package com.gamebooster.x.core

import java.util.Locale

object Formatters {

    private val ptBR: Locale = Locale.forLanguageTag("pt-BR")
    private const val KIB = 1024.0
    private const val MIB = KIB * 1024.0
    private const val GIB = MIB * 1024.0

    fun bytes(value: Long): String {
        val abs = kotlin.math.abs(value.toDouble())
        val sign = if (value < 0) "-" else ""
        return when {
            abs >= GIB -> sign + String.format(ptBR, "%.1f GB", abs / GIB)
            abs >= MIB -> sign + String.format(ptBR, "%.0f MB", abs / MIB)
            abs >= KIB -> sign + String.format(ptBR, "%.0f KB", abs / KIB)
            else -> "$value B"
        }
    }

    fun celsius(value: Float?): String =
        value?.let { String.format(ptBR, "%.1f °C", it) } ?: "Indisponível"

    fun mhz(khz: Long?): String =
        khz?.let { "${it / 1000} MHz" } ?: "—"

    fun hz(value: Float): String = String.format(ptBR, "%.0f Hz", value)
}
