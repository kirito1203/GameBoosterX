package com.umbral.codex.model

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val accent: AccentTheme = AccentTheme.AMETHYST,
    /** Cor de destaque escolhida livremente (ARGB). Quando definida, substitui o tema de [accent]. */
    val customAccent: Long? = null,
    /** Brilhos mágicos (sombras luminosas) nos painéis e botões. */
    val glowEffects: Boolean = true,
    /** Ornamentos dourados nas bordas dos painéis. */
    val ornaments: Boolean = true,
    val textScale: TextScale = TextScale.NORMAL,
    val diceAnimation: Boolean = true,
    val haptics: Boolean = true,
    val librarySort: LibrarySort = LibrarySort.EDITED,
    val compactLibrary: Boolean = false,
    /** Mostra o resultado de rolagens rápidas (perícias, magias) em destaque. */
    val showRollPopup: Boolean = true,
    val defaultCatalogId: String? = null,
    /** Sem personagens? Oferecer exemplos na tela inicial. */
    val offerDemo: Boolean = true,
    val lastBackupAt: Long = 0,
)

enum class AccentTheme(val label: String, val primary: Long, val deep: Long) {
    AMETHYST("Ametista arcana", 0xFF9D6BFF, 0xFF4C1D95),
    BLOODMOON("Lua de sangue", 0xFFD14B6A, 0xFF5E1428),
    SPECTRAL("Esmeralda espectral", 0xFF4FC79A, 0xFF0F4A39),
    ABYSS("Azul abissal", 0xFF6C8CFF, 0xFF1C2C6B),
    ECLIPSE("Ouro de eclipse", 0xFFD4A853, 0xFF5A4012),
}

enum class TextScale(val label: String, val factor: Float) {
    SMALL("Compacto", 0.92f), NORMAL("Normal", 1f), LARGE("Grande", 1.12f),
}

enum class LibrarySort(val label: String) {
    EDITED("Última edição"), NAME("Nome"), LEVEL("Nível"), CREATED("Criação"),
}
