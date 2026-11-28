package com.umbral.codex.data

import com.umbral.codex.model.AppSettings
import com.umbral.codex.model.Catalog
import com.umbral.codex.model.Character
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Configuração JSON única do app: tolerante a campos novos/antigos para não perder fichas. */
val CodexJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
    explicitNulls = false
    prettyPrint = false
}

/** Backup completo: fichas, catálogos, configurações e imagens (JPEG em base64). */
@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = 1,
    val exportedAt: Long = 0,
    val characters: List<Character> = emptyList(),
    val catalogs: List<Catalog> = emptyList(),
    val settings: AppSettings? = null,
    val images: Map<String, String> = emptyMap(),
) {
    companion object { const val FORMAT = "codice-umbral-backup" }
}

/** Uma única ficha exportada (para enviar a outra pessoa ou outro aparelho). */
@Serializable
data class CharacterExport(
    val format: String = FORMAT,
    val version: Int = 1,
    val exportedAt: Long = 0,
    val character: Character,
    @SerialName("images") val images: Map<String, String> = emptyMap(),
) {
    companion object { const val FORMAT = "codice-umbral-ficha" }
}

sealed interface ImportPayload {
    data class Full(val backup: BackupFile) : ImportPayload
    data class Single(val export: CharacterExport) : ImportPayload
}

class ImportException(message: String) : Exception(message)

object BackupCodec {
    fun encode(backup: BackupFile): String = CodexJson.encodeToString(BackupFile.serializer(), backup)
    fun encode(export: CharacterExport): String = CodexJson.encodeToString(CharacterExport.serializer(), export)

    /** Lê um arquivo .json de backup ou de ficha única, com mensagens claras em caso de erro. */
    fun decode(text: String): ImportPayload {
        val root = try {
            CodexJson.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw ImportException("O arquivo não é um JSON válido.")
        }
        val format = root["format"]?.jsonPrimitive?.content
        return try {
            when (format) {
                BackupFile.FORMAT -> ImportPayload.Full(CodexJson.decodeFromJsonElement(BackupFile.serializer(), root))
                CharacterExport.FORMAT -> ImportPayload.Single(CodexJson.decodeFromJsonElement(CharacterExport.serializer(), root))
                else -> {
                    // Aceita também uma ficha "crua" (objeto Character) exportada por versões futuras/externas.
                    if (root.containsKey("id") && root.containsKey("attributes")) {
                        ImportPayload.Single(CharacterExport(character = CodexJson.decodeFromJsonElement(Character.serializer(), root)))
                    } else throw ImportException("Este arquivo não é um backup nem uma ficha do Códice Umbral.")
                }
            }
        } catch (e: SerializationException) {
            throw ImportException("O arquivo está incompleto ou corrompido: ${e.message?.take(120)}")
        } catch (e: IllegalArgumentException) {
            throw ImportException("O arquivo está incompleto ou corrompido.")
        }
    }

    /** Ids de imagens referenciadas por uma ficha. */
    fun imageIds(c: Character): Set<String> = buildSet {
        c.portraitId?.let(::add)
        c.spells.forEach { s -> s.imageId?.let(::add) }
        c.abilities.forEach { a -> a.imageId?.let(::add) }
        c.items.forEach { i -> i.imageId?.let(::add) }
    }

    fun imageIds(cat: Catalog): Set<String> = buildSet {
        cat.coverImageId?.let(::add)
        cat.templates.forEach { addAll(imageIds(it.blueprint)) }
    }
}
