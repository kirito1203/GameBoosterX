package com.umbral.codex.model

import kotlinx.serialization.Serializable

/**
 * Catálogo de RPG: um gênero ou universo (Dark Fantasy, Sobrenatural...) com ambientação,
 * raças, classes, poderes sugeridos e modelos de ficha. Todo o conteúdo é editável.
 */
@Serializable
data class Catalog(
    val id: String,
    val name: String,
    val tagline: String = "",
    val description: String = "",
    /** Nome do ícone (ver CodexIcons) usado no selo e na capa. */
    val glyph: String = "auto_stories",
    /** Cor de destaque ARGB da capa gerada. */
    val accent: Long = 0xFF8B5CF6,
    /** Imagem de capa escolhida pelo usuário (substitui a arte gerada). */
    val coverImageId: String? = null,
    val setting: String = "",
    val characteristics: List<String> = emptyList(),
    val lore: List<LoreSection> = emptyList(),
    val races: List<CatalogEntry> = emptyList(),
    val classes: List<CatalogEntry> = emptyList(),
    val powers: List<CatalogEntry> = emptyList(),
    val templates: List<CharacterTemplate> = emptyList(),
    /** Catálogo que veio com o app (pode ser editado e restaurado). */
    val builtIn: Boolean = false,
    val order: Int = 0,
    val updatedAt: Long = 0,
)

/** Bloco da ambientação: "Reinos", "Criaturas", "Maldições"... */
@Serializable
data class LoreSection(val id: String, val title: String, val body: String = "")

@Serializable
data class CatalogEntry(val id: String, val name: String, val description: String = "")

/** Modelo de ficha: uma ficha-base copiada (com novos ids) ao criar um personagem. */
@Serializable
data class CharacterTemplate(
    val id: String,
    val name: String,
    val description: String = "",
    val blueprint: Character,
)
