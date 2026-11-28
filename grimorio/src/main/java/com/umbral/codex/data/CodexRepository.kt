package com.umbral.codex.data

import com.umbral.codex.core.Blueprints
import com.umbral.codex.model.AppSettings
import com.umbral.codex.model.Catalog
import com.umbral.codex.model.Character
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64

sealed interface SaveStatus {
    data object Idle : SaveStatus
    data object Pending : SaveStatus
    data object Saving : SaveStatus
    data class Saved(val at: Long) : SaveStatus
    data class Failed(val message: String) : SaveStatus
}

enum class RestoreMode { MERGE, REPLACE }

data class ImportSummary(val characters: Int, val catalogs: Int, val images: Int)

/**
 * Armazenamento local confiável em arquivos JSON (um por ficha), dentro da pasta privada do app.
 *
 * - Toda alteração atualiza o estado em memória na hora e é gravada em disco após um curto
 *   intervalo (salvamento automático com debounce). [flush] grava imediatamente — chamado
 *   quando o app vai para segundo plano.
 * - A gravação é atômica (arquivo temporário + rename), então uma falha no meio não corrompe
 *   a ficha anterior.
 * - É JVM pura (só java.io), o que permite testar persistência e backup sem Android.
 */
class CodexRepository(
    private val root: File,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val debounceMs: Long = 400,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val charDir = File(root, "characters")
    private val imageDir = File(root, "images")
    private val catalogsFile = File(root, "catalogs.json")
    private val settingsFile = File(root, "settings.json")

    private val _characters = MutableStateFlow<List<Character>>(emptyList())
    val characters: StateFlow<List<Character>> = _characters.asStateFlow()

    private val _catalogs = MutableStateFlow<List<Catalog>>(emptyList())
    val catalogs: StateFlow<List<Catalog>> = _catalogs.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _status = MutableStateFlow<SaveStatus>(SaveStatus.Idle)
    val status: StateFlow<SaveStatus> = _status.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /** Avisos de carregamento (ex.: ficha corrompida isolada). */
    private val _warnings = MutableStateFlow<List<String>>(emptyList())
    val warnings: StateFlow<List<String>> = _warnings.asStateFlow()

    private val lock = Mutex()
    private val dirtyChars = mutableSetOf<String>()
    private val deletedChars = mutableSetOf<String>()
    private var catalogsDirty = false
    private var settingsDirty = false
    private var saveJob: Job? = null

    // ---------------- Carregamento ----------------

    suspend fun load() = withContext(io) {
        root.mkdirs(); charDir.mkdirs(); imageDir.mkdirs()
        val warnings = mutableListOf<String>()
        val chars = charDir.listFiles { f -> f.extension == "json" }.orEmpty().mapNotNull { f ->
            try {
                CodexJson.decodeFromString(Character.serializer(), f.readText())
            } catch (e: Exception) {
                // Não apaga: move para quarentena para eventual recuperação manual.
                val q = File(root, "quarentena").apply { mkdirs() }
                f.renameTo(File(q, f.name))
                warnings += "Uma ficha danificada (${f.name}) foi isolada em /quarentena."
                null
            }
        }
        val catalogs = if (catalogsFile.exists()) {
            try {
                CodexJson.decodeFromString(CatalogList.serializer(), catalogsFile.readText()).items
            } catch (e: Exception) {
                catalogsFile.renameTo(File(root, "catalogs.corrompido.json"))
                warnings += "Os catálogos estavam danificados e foram restaurados ao padrão."
                null
            }
        } else null
        val settings = if (settingsFile.exists()) {
            runCatching { CodexJson.decodeFromString(AppSettings.serializer(), settingsFile.readText()) }.getOrNull()
        } else null

        _characters.value = chars.sortedByDescending { it.updatedAt }
        _catalogs.value = catalogs ?: SeedCatalogs.all(clock()).also { lock.withLock { catalogsDirty = true } }
        _settings.value = settings ?: AppSettings()
        _warnings.value = warnings
        _loaded.value = true
        if (catalogs == null) scheduleSave()
    }

    // ---------------- Fichas ----------------

    fun character(id: String): Character? = _characters.value.firstOrNull { it.id == id }

    fun upsert(c: Character, touch: Boolean = true) {
        val updated = if (touch) c.copy(updatedAt = clock()) else c
        _characters.update { list ->
            val idx = list.indexOfFirst { it.id == c.id }
            if (idx >= 0) list.toMutableList().also { it[idx] = updated } else listOf(updated) + list
        }
        markChar(c.id)
    }

    /** Altera uma ficha aplicando uma transformação sobre a versão mais recente. */
    fun update(id: String, touch: Boolean = true, transform: (Character) -> Character) {
        var changed = false
        _characters.update { list ->
            list.map {
                if (it.id == id) {
                    val t = transform(it)
                    if (t != it) { changed = true; if (touch) t.copy(updatedAt = clock()) else t } else it
                } else it
            }
        }
        if (changed) markChar(id)
    }

    fun markOpened(id: String) = update(id, touch = false) { it.copy(lastOpenedAt = clock()) }

    fun delete(id: String) {
        _characters.update { list -> list.filterNot { it.id == id } }
        launchLocked { deletedChars += id; dirtyChars -= id }
        scheduleSave()
    }

    fun duplicate(id: String): Character? {
        val src = character(id) ?: return null
        val copy = Blueprints.duplicate(src, clock())
        upsert(copy, touch = false)
        return copy
    }

    private fun markChar(id: String) {
        launchLocked { dirtyChars += id; deletedChars -= id }
        scheduleSave()
    }

    // ---------------- Catálogos ----------------

    fun catalog(id: String?): Catalog? = _catalogs.value.firstOrNull { it.id == id }

    fun upsertCatalog(c: Catalog) {
        val updated = c.copy(updatedAt = clock())
        _catalogs.update { list ->
            val idx = list.indexOfFirst { it.id == c.id }
            if (idx >= 0) list.toMutableList().also { it[idx] = updated }
            else list + updated.copy(order = (list.maxOfOrNull { it.order } ?: 0) + 1)
        }
        markCatalogs()
    }

    fun updateCatalog(id: String, transform: (Catalog) -> Catalog) {
        val cur = catalog(id) ?: return
        upsertCatalog(transform(cur))
    }

    fun deleteCatalog(id: String) {
        _catalogs.update { list -> list.filterNot { it.id == id } }
        // Fichas do catálogo continuam existindo, apenas sem universo.
        _characters.value.filter { it.catalogId == id }.forEach { c -> update(c.id, touch = false) { it.copy(catalogId = null) } }
        markCatalogs()
    }

    /** Restaura os catálogos originais que foram apagados ou editados (mantém os criados pelo usuário). */
    fun restoreBuiltInCatalogs() {
        val seeds = SeedCatalogs.all(clock())
        _catalogs.update { list -> seeds + list.filterNot { c -> seeds.any { it.id == c.id } } }
        markCatalogs()
    }

    private fun markCatalogs() {
        launchLocked { catalogsDirty = true }
        scheduleSave()
    }

    // ---------------- Configurações ----------------

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        _settings.update(transform)
        launchLocked { settingsDirty = true }
        scheduleSave()
    }

    // ---------------- Imagens ----------------

    fun saveImage(id: String, jpeg: ByteArray) {
        imageDir.mkdirs()
        atomicWrite(File(imageDir, "$id.jpg"), jpeg)
    }

    fun imageFile(id: String): File = File(imageDir, "$id.jpg")

    /** Apaga imagens que nenhuma ficha ou catálogo usa mais. */
    suspend fun cleanupImages() = withContext(io) {
        val used = _characters.value.flatMap { BackupCodec.imageIds(it) }.toSet() +
            _catalogs.value.flatMap { BackupCodec.imageIds(it) }
        imageDir.listFiles().orEmpty().filter { it.nameWithoutExtension !in used }.forEach { it.delete() }
    }

    private fun readImages(ids: Set<String>): Map<String, String> = ids.mapNotNull { id ->
        val f = imageFile(id)
        if (f.exists()) id to Base64.getEncoder().encodeToString(f.readBytes()) else null
    }.toMap()

    private fun writeImages(images: Map<String, String>): Int {
        var n = 0
        images.forEach { (id, b64) ->
            if (id.isNotBlank() && !id.contains('/') && !id.contains("..")) {
                runCatching { saveImage(id, Base64.getDecoder().decode(b64)); n++ }
            }
        }
        return n
    }

    // ---------------- Backup, exportação e importação ----------------

    suspend fun exportBackup(): String = withContext(io) {
        val chars = _characters.value
        val cats = _catalogs.value
        val ids = chars.flatMap { BackupCodec.imageIds(it) }.toSet() + cats.flatMap { BackupCodec.imageIds(it) }
        BackupCodec.encode(BackupFile(exportedAt = clock(), characters = chars, catalogs = cats, settings = _settings.value, images = readImages(ids)))
    }

    suspend fun exportCharacter(id: String): String? = withContext(io) {
        val c = character(id) ?: return@withContext null
        BackupCodec.encode(CharacterExport(exportedAt = clock(), character = c, images = readImages(BackupCodec.imageIds(c))))
    }

    /**
     * Importa um backup completo ou uma ficha única.
     * Fichas com id já existente: no modo MERGE são substituídas pela versão do arquivo;
     * uma ficha única importada que já existe entra como cópia, para não sobrescrever nada.
     */
    suspend fun import(text: String, mode: RestoreMode = RestoreMode.MERGE): ImportSummary = withContext(io) {
        when (val payload = BackupCodec.decode(text)) {
            is ImportPayload.Full -> {
                val b = payload.backup
                val images = writeImages(b.images)
                if (mode == RestoreMode.REPLACE) {
                    _characters.value.forEach { c -> lock.withLock { deletedChars += c.id; dirtyChars -= c.id } }
                    _characters.value = emptyList()
                    if (b.catalogs.isNotEmpty()) _catalogs.value = emptyList()
                }
                b.characters.forEach { upsert(it, touch = false) }
                if (b.catalogs.isNotEmpty()) {
                    _catalogs.update { list -> list.filterNot { c -> b.catalogs.any { it.id == c.id } } + b.catalogs }
                    markCatalogs()
                }
                if (mode == RestoreMode.REPLACE && b.settings != null) updateSettings { b.settings }
                flush()
                ImportSummary(b.characters.size, b.catalogs.size, images)
            }
            is ImportPayload.Single -> {
                val images = writeImages(payload.export.images)
                val c = payload.export.character
                val toInsert = if (character(c.id) != null) Blueprints.instantiate(c, clock(), name = "${c.name} (importada)")
                    .copy(portraitId = c.portraitId) else c.copy(updatedAt = clock())
                val validCatalog = toInsert.catalogId?.takeIf { catalog(it) != null }
                upsert(toInsert.copy(catalogId = validCatalog), touch = false)
                flush()
                ImportSummary(1, 0, images)
            }
        }
    }

    fun deleteAll() {
        _characters.value.forEach { c -> launchLocked { deletedChars += c.id; dirtyChars -= c.id } }
        _characters.value = emptyList()
        _catalogs.value = SeedCatalogs.all(clock())
        _settings.value = AppSettings()
        launchLocked { catalogsDirty = true; settingsDirty = true }
        scope.launch(io) {
            flush()
            imageDir.listFiles().orEmpty().forEach { it.delete() }
        }
    }

    // ---------------- Gravação ----------------

    private fun launchLocked(block: () -> Unit) {
        // Mutex curto e não reentrante; tryLock evita suspender a UI. Em contenção, agenda.
        if (lock.tryLock()) {
            try { block() } finally { lock.unlock() }
        } else scope.launch { lock.withLock { block() } }
    }

    private fun scheduleSave() {
        _status.value = SaveStatus.Pending
        saveJob?.cancel()
        saveJob = scope.launch(io) {
            delay(debounceMs)
            flush()
        }
    }

    /** Grava imediatamente tudo o que estiver pendente. */
    suspend fun flush() = withContext(io) {
        lock.withLock {
            if (dirtyChars.isEmpty() && deletedChars.isEmpty() && !catalogsDirty && !settingsDirty) {
                if (_status.value == SaveStatus.Pending) _status.value = SaveStatus.Saved(clock())
                return@withLock
            }
            _status.value = SaveStatus.Saving
            try {
                charDir.mkdirs()
                val byId = _characters.value.associateBy { it.id }
                dirtyChars.toList().forEach { id ->
                    val c = byId[id]
                    if (c != null) atomicWrite(File(charDir, "$id.json"), CodexJson.encodeToString(Character.serializer(), c).toByteArray())
                    dirtyChars -= id
                }
                deletedChars.toList().forEach { id ->
                    File(charDir, "$id.json").delete()
                    deletedChars -= id
                }
                if (catalogsDirty) {
                    atomicWrite(catalogsFile, CodexJson.encodeToString(CatalogList.serializer(), CatalogList(_catalogs.value)).toByteArray())
                    catalogsDirty = false
                }
                if (settingsDirty) {
                    atomicWrite(settingsFile, CodexJson.encodeToString(AppSettings.serializer(), _settings.value).toByteArray())
                    settingsDirty = false
                }
                _status.value = SaveStatus.Saved(clock())
            } catch (e: Exception) {
                _status.value = SaveStatus.Failed("Não foi possível salvar: ${e.message ?: "erro de disco"}")
            }
        }
    }

    private fun atomicWrite(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeBytes(bytes)
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}

@kotlinx.serialization.Serializable
internal data class CatalogList(val items: List<Catalog>)
