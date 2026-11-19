package com.memesoundboardx.core.util

object ImportNaming {

    const val MAX_NAME = 40

    /** "kkk_risada-ALTA (1).mp3" -> "kkk risada ALTA (1)" */
    fun displayName(fileName: String): String {
        val base = fileName.substringBeforeLast('.', fileName)
        val cleaned = base
            .replace('_', ' ')
            .replace(Regex("(?<=\\S)-(?=\\S)"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifEmpty { "Áudio importado" }
        return if (cleaned.length <= MAX_NAME) cleaned else cleaned.take(MAX_NAME).trimEnd()
    }

    /** Extensão final para a cópia interna; usa o MIME quando o nome não tem extensão útil. */
    fun extensionFor(fileName: String, mime: String?): String? {
        SupportedFormats.extensionOf(fileName)?.takeIf { it in SupportedFormats.extensions }?.let { return it }
        return when (mime?.lowercase()) {
            "audio/mpeg", "audio/mp3" -> "mp3"
            "audio/wav", "audio/x-wav", "audio/wave" -> "wav"
            "audio/ogg", "application/ogg" -> "ogg"
            "audio/mp4", "audio/x-m4a" -> "m4a"
            "audio/aac", "audio/aacp" -> "aac"
            else -> null
        }
    }
}
