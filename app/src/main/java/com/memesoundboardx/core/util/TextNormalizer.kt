package com.memesoundboardx.core.util

import java.text.Normalizer

/**
 * Normalização usada na pesquisa: minúsculas, sem acentos, espaços colapsados.
 * "Ação  ÉPICA" -> "acao epica". Assim "acao" encontra "Ação".
 */
object TextNormalizer {

    private val diacritics = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val spaces = Regex("\\s+")

    fun normalize(input: String): String {
        val decomposed = Normalizer.normalize(input, Normalizer.Form.NFD)
        return decomposed
            .replace(diacritics, "")
            .lowercase()
            .replace(spaces, " ")
            .trim()
    }

    /** Escapa curingas do LIKE do SQLite. Usar com `ESCAPE '\'` na query. */
    fun escapeLike(input: String): String = buildString(input.length) {
        for (c in input) {
            if (c == '\\' || c == '%' || c == '_') append('\\')
            append(c)
        }
    }

    /** Texto indexado de um som: nome + tags, normalizado. */
    fun searchIndex(name: String, tags: List<String>): String =
        normalize((listOf(name) + tags).joinToString(" "))
}
