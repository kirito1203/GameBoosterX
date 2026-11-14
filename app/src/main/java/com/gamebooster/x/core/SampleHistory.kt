package com.gamebooster.x.core

/**
 * Histórico imutável de tamanho fixo para gráficos em tempo real.
 * Cada [add] devolve uma nova instância (seguro para StateFlow).
 */
class SampleHistory private constructor(
    val capacity: Int,
    val values: List<Float>,
) {
    constructor(capacity: Int) : this(capacity, emptyList())

    init {
        require(capacity > 0) { "capacity deve ser > 0" }
    }

    fun add(value: Float): SampleHistory {
        val next = if (values.size >= capacity) values.drop(values.size - capacity + 1) + value else values + value
        return SampleHistory(capacity, next)
    }

    val latest: Float? get() = values.lastOrNull()
    val min: Float? get() = values.minOrNull()
    val max: Float? get() = values.maxOrNull()
    val average: Float? get() = if (values.isEmpty()) null else values.average().toFloat()
}
