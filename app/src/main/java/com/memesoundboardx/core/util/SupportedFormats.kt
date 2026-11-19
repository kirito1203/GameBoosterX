package com.memesoundboardx.core.util

/**
 * Formatos que o app aceita. A extensão é só a primeira barreira:
 * na ETAPA 5 cada arquivo importado também é validado decodificando o conteúdo
 * (MediaExtractor), porque extensão não garante que o arquivo é válido.
 */
object SupportedFormats {

    val extensions: Set<String> = setOf("mp3", "wav", "ogg", "m4a", "aac")

    val mimeTypes: Set<String> = setOf(
        "audio/mpeg",
        "audio/mp3",
        "audio/wav",
        "audio/x-wav",
        "audio/wave",
        "audio/ogg",
        "application/ogg",
        "audio/mp4",
        "audio/x-m4a",
        "audio/aac",
        "audio/aacp",
    )

    fun extensionOf(fileName: String): String? {
        val dot = fileName.lastIndexOf('.')
        if (dot <= 0 || dot == fileName.lastIndex) return null
        return fileName.substring(dot + 1).lowercase()
    }

    fun isSupportedFileName(fileName: String): Boolean =
        extensionOf(fileName) in extensions

    fun isSupportedMime(mime: String?): Boolean =
        mime != null && mime.lowercase() in mimeTypes
}
