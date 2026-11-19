package com.memesoundboardx.domain.model

/** De onde o som veio. */
enum class SoundSource { BUNDLED, IMPORTED }

/** Licenças aceitas para sons embutidos no APK. */
enum class SoundLicense(val label: String, val requiresAttribution: Boolean) {
    CC0("CC0 (domínio público)", false),
    PUBLIC_DOMAIN("Domínio público", false),
    CC_BY("CC BY", true),
    CC_BY_SA("CC BY-SA", true),
    ORIGINAL("Original do app", false),
    LICENSED("Licenciado", true),
    USER_FILE("Arquivo do usuário", false),
}

/** Crédito de um som de terceiros incluído no APK (exigido por CC BY / CC BY-SA). */
data class CreditEntry(
    val name: String,
    val author: String?,
    val license: SoundLicense,
    val sourceUrl: String?,
)

/** Meme conhecido sem fonte autorizada: fica como vaga esperando o arquivo do usuário. */
data class PendingItem(
    val id: String,
    val name: String,
    val categoryId: String,
    /** Por que não foi incluído (ex.: "música protegida por direitos autorais"). */
    val reason: String,
    /** Dica de identificação (frase, origem). */
    val hint: String?,
)

data class Sound(
    val id: Long,
    /** Chave estável: "bundled:<id-do-manifesto>" ou "imported:<uuid>". */
    val key: String,
    val name: String,
    val categoryId: String?,
    val source: SoundSource,
    /** Caminho no assets (BUNDLED) ou content:// URI (IMPORTED). */
    val uri: String,
    val durationMs: Long?,
    val sizeBytes: Long?,
    val isFavorite: Boolean,
    val license: SoundLicense,
    val author: String?,
    val sourceUrl: String?,
    val tags: List<String>,
    val addedAt: Long,
    /** false quando o arquivo de origem sumiu ou perdeu permissão. */
    val isAvailable: Boolean,
)

data class Category(
    val id: String,
    val name: String,
    val isCustom: Boolean,
    val order: Int,
    /** Meta de quantidade de sons (só nas categorias padrão). */
    val targetCount: Int?,
    val soundCount: Int,
)

data class LibraryStats(
    val total: Int,
    val favorites: Int,
    val imported: Int,
    val bundled: Int,
)

/** Resultado da última sincronização do manifesto embutido. */
data class LibrarySyncReport(
    val validSounds: Int,
    val rejected: List<String>,
    val hidden: Int = 0,
    val finishedAt: Long,
)

data class AppSettings(
    val masterVolume: Float = DEFAULT_VOLUME,
    val maxSimultaneous: Int = DEFAULT_MAX_SIMULTANEOUS,
    /** Se true, o app pede foco de áudio e pode abaixar o Roblox. Padrão: false. */
    val requestAudioFocus: Boolean = false,
    val onboardingDone: Boolean = false,
    /** Chaves de sons embutidos que o usuário removeu da biblioteca. */
    val hiddenBundled: Set<String> = emptySet(),
) {
    companion object {
        const val DEFAULT_VOLUME = 0.8f
        const val DEFAULT_MAX_SIMULTANEOUS = 6
        const val MIN_SIMULTANEOUS = 1
        /** Limite do SoundPool definido para o C71 (ver ARQUITETURA.md). */
        const val MAX_SIMULTANEOUS = 8

        fun clampVolume(v: Float): Float = if (v.isNaN()) DEFAULT_VOLUME else v.coerceIn(0f, 1f)
        fun clampSimultaneous(n: Int): Int = n.coerceIn(MIN_SIMULTANEOUS, MAX_SIMULTANEOUS)
    }
}
