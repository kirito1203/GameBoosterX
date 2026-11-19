package com.memesoundboardx.domain.model

enum class PlayStatus { LOADING, PLAYING, PAUSED }

/** Estado observável do motor de áudio. */
data class PlayerState(
    /** soundId -> status. Só contém sons ativos (carregando, tocando ou pausados). */
    val active: Map<Long, PlayStatus> = emptyMap(),
    val repeat: Boolean = false,
    val volume: Float = AppSettings.DEFAULT_VOLUME,
    /** Última falha (ex.: arquivo apagado). Consumida pela UI. */
    val lastError: String? = null,
) {
    fun statusOf(soundId: Long): PlayStatus? = active[soundId]
    val playingCount: Int get() = active.count { it.value == PlayStatus.PLAYING }
}

data class ImportFailure(val fileName: String, val reason: String)

data class ImportReport(
    val imported: List<String>,
    val duplicates: List<String>,
    val failures: List<ImportFailure>,
) {
    val total: Int get() = imported.size + duplicates.size + failures.size
}
