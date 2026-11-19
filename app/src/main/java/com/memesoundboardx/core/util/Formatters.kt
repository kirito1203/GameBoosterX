package com.memesoundboardx.core.util

import java.util.Locale

object Formatters {

    /** 3500 -> "0:03", 75_000 -> "1:15", null -> "--:--" */
    fun duration(ms: Long?): String {
        if (ms == null || ms < 0) return "--:--"
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }

    /** 512 -> "512 B", 1536 -> "1,5 KB", 1_258_291 -> "1,2 MB" (formato pt-BR) */
    fun size(bytes: Long?): String {
        if (bytes == null || bytes < 0) return "—"
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.ROOT, "%.1f KB", kb).replace('.', ',')
        val mb = kb / 1024.0
        return String.format(Locale.ROOT, "%.1f MB", mb).replace('.', ',')
    }
}
