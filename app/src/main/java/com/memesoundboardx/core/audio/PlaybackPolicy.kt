package com.memesoundboardx.core.audio

/**
 * Regras puras do motor (testadas na JVM).
 */
object PlaybackPolicy {

    /** Sons até este tamanho vão para o SoundPool (decodificados na RAM, latência mínima). */
    const val SHORT_SOUND_MS = 5_000L

    /** Acima disso o SoundPool pode recusar a amostra; vai para MediaPlayer (streaming). */
    const val SHORT_SOUND_MAX_BYTES = 1_000_000L

    /** Quantas amostras curtas ficam carregadas na RAM ao mesmo tempo. */
    const val SAMPLE_CACHE_SIZE = 40

    /** Limite rígido de streams do SoundPool (o usuário escolhe 1..8 dentro disso). */
    const val MAX_POOL_STREAMS = 8

    /** Players de streaming simultâneos (músicas/vinhetas longas). */
    const val MAX_LONG_PLAYERS = 2

    /** Usado quando a duração é desconhecida, para liberar o estado "tocando". */
    const val UNKNOWN_DURATION_FALLBACK_MS = 5_000L

    fun useSoundPool(durationMs: Long?, sizeBytes: Long?): Boolean {
        if (durationMs != null && durationMs > SHORT_SOUND_MS) return false
        if (sizeBytes != null && sizeBytes > SHORT_SOUND_MAX_BYTES) return false
        return true
    }

    /**
     * Dado a ordem de início dos sons ativos (mais antigo primeiro) e o limite,
     * devolve quais devem ser interrompidos para caber um novo som.
     */
    fun <T> toEvict(activeOldestFirst: List<T>, maxSimultaneous: Int): List<T> {
        val allowedExisting = (maxSimultaneous - 1).coerceAtLeast(0)
        val excess = activeOldestFirst.size - allowedExisting
        return if (excess > 0) activeOldestFirst.take(excess) else emptyList()
    }
}

/**
 * Cache LRU de chaves com teto. Não conhece o SoundPool: quem chama decide
 * o que fazer com as chaves despejadas (descarregar a amostra).
 * [isPinned] protege chaves em uso (som tocando) de serem despejadas.
 */
class LruKeyCache<K>(private val capacity: Int) {
    private val order = LinkedHashMap<K, Unit>(16, 0.75f, true)

    val size: Int get() = order.size
    fun contains(key: K): Boolean = order.containsKey(key)
    fun keys(): List<K> = order.keys.toList()

    /** Marca [key] como usada agora. Devolve as chaves despejadas. */
    fun touch(key: K, isPinned: (K) -> Boolean = { false }): List<K> {
        order[key] = Unit
        return evictOverflow(isPinned)
    }

    fun remove(key: K) {
        order.remove(key)
    }

    fun evictOverflow(isPinned: (K) -> Boolean): List<K> {
        val evicted = mutableListOf<K>()
        if (order.size <= capacity) return evicted
        val it = order.keys.iterator()
        while (order.size > capacity && it.hasNext()) {
            val k = it.next()
            if (!isPinned(k)) {
                it.remove()
                evicted += k
            }
        }
        return evicted
    }

    /** Remove tudo que não está protegido. */
    fun clearUnpinned(isPinned: (K) -> Boolean): List<K> {
        val removed = order.keys.filterNot(isPinned)
        removed.forEach { order.remove(it) }
        return removed
    }
}
