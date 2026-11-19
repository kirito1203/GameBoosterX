package com.memesoundboardx.data.manifest

import com.memesoundboardx.core.util.SupportedFormats
import com.memesoundboardx.domain.model.SoundLicense
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class ManifestCategory(
    val id: String,
    val name: String,
    val targetCount: Int?,
    val order: Int,
)

data class ManifestSound(
    val id: String,
    val file: String,
    val name: String,
    val categoryId: String,
    val license: SoundLicense,
    /** Duração medida na geração; evita medir no celular. */
    val durationMs: Long?,
    val author: String?,
    val sourceUrl: String?,
    val tags: List<String>,
)

data class ManifestPending(
    val id: String,
    val name: String,
    val categoryId: String,
    val reason: String,
    val hint: String?,
)

data class ManifestParseResult(
    val categories: List<ManifestCategory>,
    val sounds: List<ManifestSound>,
    /** Uma mensagem por entrada rejeitada. Vazio = tudo válido. */
    val errors: List<String>,
    val pending: List<ManifestPending> = emptyList(),
)

/**
 * Lê e valida o assets/sounds.json. Não depende de Android (só org.json),
 * então é testado na JVM. Entradas inválidas são rejeitadas individualmente;
 * um erro de estrutura no arquivo inteiro rejeita tudo.
 */
object SoundManifestParser {

    const val SUPPORTED_SCHEMA = 1
    private val idRegex = Regex("^[a-z0-9_]{1,64}$")
    private val bundledLicenses = SoundLicense.entries.filter { it != SoundLicense.USER_FILE }

    fun parse(json: String): ManifestParseResult {
        val root = try {
            JSONObject(json)
        } catch (e: JSONException) {
            return ManifestParseResult(emptyList(), emptyList(), listOf("JSON inválido: ${e.message}"))
        }

        val schema = root.optInt("schemaVersion", -1)
        if (schema != SUPPORTED_SCHEMA) {
            return ManifestParseResult(
                emptyList(), emptyList(),
                listOf("schemaVersion $schema não suportado (esperado $SUPPORTED_SCHEMA)"),
            )
        }

        val errors = mutableListOf<String>()
        val categories = parseCategories(root.optJSONArray("categories") ?: JSONArray(), errors)
        val categoryIds = categories.map { it.id }.toSet()
        val sounds = parseSounds(root.optJSONArray("sounds") ?: JSONArray(), categoryIds, errors)
        val pending = parsePending(root.optJSONArray("pending") ?: JSONArray(), categoryIds, errors)
        return ManifestParseResult(categories, sounds, errors, pending)
    }

    private fun parseCategories(arr: JSONArray, errors: MutableList<String>): List<ManifestCategory> {
        val out = mutableListOf<ManifestCategory>()
        val seen = mutableSetOf<String>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i)
            if (o == null) { errors += "categoria[$i]: não é um objeto"; continue }
            val id = o.optString("id").trim()
            val name = o.optString("name").trim()
            when {
                !idRegex.matches(id) -> errors += "categoria[$i]: id inválido '$id'"
                name.isEmpty() -> errors += "categoria '$id': nome vazio"
                !seen.add(id) -> errors += "categoria '$id': id repetido"
                else -> out += ManifestCategory(
                    id = id,
                    name = name,
                    targetCount = if (o.has("targetCount")) o.optInt("targetCount").takeIf { it > 0 } else null,
                    order = o.optInt("order", i + 1),
                )
            }
        }
        return out
    }

    private fun parseSounds(
        arr: JSONArray,
        categoryIds: Set<String>,
        errors: MutableList<String>,
    ): List<ManifestSound> {
        val out = mutableListOf<ManifestSound>()
        val seen = mutableSetOf<String>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i)
            if (o == null) { errors += "som[$i]: não é um objeto"; continue }
            val id = o.optString("id").trim()
            val file = o.optString("file").trim()
            val name = o.optString("name").trim()
            val categoryId = o.optString("categoryId").trim()
            val licenseRaw = o.optString("license").trim().uppercase()
            val license = bundledLicenses.firstOrNull { it.name == licenseRaw }

            val problem = when {
                !idRegex.matches(id) -> "id inválido '$id'"
                id in seen -> "id repetido"
                file.isEmpty() -> "campo 'file' vazio"
                file.startsWith("/") || file.contains("..") || file.contains('\\') -> "caminho de arquivo inseguro '$file'"
                !SupportedFormats.isSupportedFileName(file) -> "formato não suportado '$file'"
                name.isEmpty() -> "nome vazio"
                categoryId !in categoryIds -> "categoria inexistente '$categoryId'"
                license == null -> "licença ausente ou não aceita '${o.optString("license")}'"
                else -> null
            }
            if (problem != null) {
                errors += "som[$i] ${id.ifEmpty { "?" }}: $problem"
                continue
            }
            seen += id
            out += ManifestSound(
                id = id,
                file = file,
                name = name,
                categoryId = categoryId,
                license = license!!,
                durationMs = if (o.has("durationMs")) o.optLong("durationMs").takeIf { it > 0 } else null,
                author = o.optString("author").trim().ifEmpty { null },
                sourceUrl = o.optString("sourceUrl").trim().ifEmpty { null },
                tags = o.optJSONArray("tags").toStringList(),
            )
        }
        return out
    }

    private fun parsePending(arr: JSONArray, categoryIds: Set<String>, errors: MutableList<String>): List<ManifestPending> {
        val out = mutableListOf<ManifestPending>()
        val seen = mutableSetOf<String>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id").trim()
            val name = o.optString("name").trim()
            val cat = o.optString("categoryId").trim()
            when {
                !idRegex.matches(id) || !seen.add(id) -> errors += "pendente[$i]: id inválido ou repetido '$id'"
                name.isEmpty() -> errors += "pendente '$id': nome vazio"
                cat !in categoryIds -> errors += "pendente '$id': categoria inexistente '$cat'"
                else -> out += ManifestPending(
                    id, name, cat,
                    reason = o.optString("reason").trim().ifEmpty { "sem fonte autorizada" },
                    hint = o.optString("hint").trim().ifEmpty { null },
                )
            }
        }
        return out
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optString(it).trim().ifEmpty { null } }
    }
}
