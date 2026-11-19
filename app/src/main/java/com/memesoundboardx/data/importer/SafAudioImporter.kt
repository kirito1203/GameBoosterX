package com.memesoundboardx.data.importer

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.memesoundboardx.core.util.ImportNaming
import com.memesoundboardx.core.util.SupportedFormats
import com.memesoundboardx.core.util.TextNormalizer
import com.memesoundboardx.data.local.db.CategoryDao
import com.memesoundboardx.data.local.db.SoundDao
import com.memesoundboardx.data.local.db.SoundEntity
import com.memesoundboardx.di.IoDispatcher
import com.memesoundboardx.domain.model.ImportFailure
import com.memesoundboardx.domain.model.ImportReport
import com.memesoundboardx.domain.model.PendingItem
import com.memesoundboardx.domain.model.SoundLicense
import com.memesoundboardx.domain.model.SoundSource
import com.memesoundboardx.domain.repository.AudioImporter
import com.memesoundboardx.domain.usecase.CategoryAutoMatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pasta privada onde ficam as cópias dos áudios importados.
 * Copiar (em vez de guardar só a URI) garante que o som continua funcionando
 * depois de fechar o app, reiniciar o celular ou se o app de origem revogar o acesso.
 * O arquivo original do usuário nunca é alterado nem apagado.
 */
@Singleton
class ImportedFileStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val dir: File get() = File(context.filesDir, "imported").apply { mkdirs() }

    fun uriFor(file: File): String = Uri.fromFile(file).toString()

    fun fileFor(uri: String): File? {
        val path = Uri.parse(uri).path ?: return null
        val f = File(path)
        // Só arquivos dentro da nossa pasta podem ser apagados pelo app.
        return f.takeIf { it.canonicalPath.startsWith(dir.canonicalPath + File.separator) }
    }

    fun delete(uri: String): Boolean = fileFor(uri)?.delete() ?: false

    fun exists(uri: String): Boolean = fileFor(uri)?.exists() == true

    fun totalBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L
}

/** Resultado da inspeção técnica do arquivo copiado. */
data class ProbeResult(val durationMs: Long?, val error: String?)

object AudioProbe {
    /**
     * Valida decodificando o contêiner de verdade: o arquivo precisa ter uma faixa
     * de áudio reconhecida pelo Android. Extensão sozinha não basta.
     */
    fun probe(file: File): ProbeResult {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            var audioTrack = -1
            var durationUs = -1L
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrack = i
                    if (format.containsKey(MediaFormat.KEY_DURATION)) durationUs = format.getLong(MediaFormat.KEY_DURATION)
                    break
                }
            }
            if (audioTrack < 0) return ProbeResult(null, "o arquivo não contém áudio")
            // Lê ao menos uma amostra: arquivos truncados/corrompidos falham aqui.
            extractor.selectTrack(audioTrack)
            val buf = java.nio.ByteBuffer.allocate(256 * 1024)
            if (extractor.readSampleData(buf, 0) < 0) return ProbeResult(null, "áudio vazio ou corrompido")

            var durationMs = if (durationUs > 0) durationUs / 1000 else null
            if (durationMs == null) durationMs = retrieverDuration(file)
            if (durationMs != null && durationMs <= 0) return ProbeResult(null, "duração inválida")
            return ProbeResult(durationMs, null)
        } catch (e: Exception) {
            return ProbeResult(null, "formato não reconhecido pelo Android")
        } finally {
            extractor.release()
        }
    }

    private fun retrieverDuration(file: File): Long? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(file.absolutePath)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (e: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }
}

@Singleton
class SafAudioImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val soundDao: SoundDao,
    private val categoryDao: CategoryDao,
    private val store: ImportedFileStore,
    @IoDispatcher private val io: CoroutineDispatcher,
) : AudioImporter {

    override suspend fun import(uris: List<String>, categoryOverride: String?): ImportReport = withContext(io) {
        val imported = mutableListOf<String>()
        val duplicates = mutableListOf<String>()
        val failures = mutableListOf<ImportFailure>()
        val validCategories = categoryDao.getAllWithCounts().map { it.category.id }.toSet()

        for (raw in uris) {
            val uri = Uri.parse(raw)
            val (fileName, declaredSize) = queryNameAndSize(uri)
            when (val r = importOne(uri, fileName, declaredSize, categoryOverride, validCategories)) {
                is One.Ok -> imported += r.name
                is One.Duplicate -> duplicates += fileName
                is One.Fail -> failures += ImportFailure(fileName, r.reason)
            }
        }
        ImportReport(imported, duplicates, failures)
    }

    override suspend fun importForPending(uri: String, item: PendingItem): ImportReport = withContext(io) {
        val u = Uri.parse(uri)
        val (fileName, declaredSize) = queryNameAndSize(u)
        val valid = categoryDao.getAllWithCounts().map { it.category.id }.toSet()
        when (val r = importOne(u, fileName, declaredSize, item.categoryId, valid, nameOverride = item.name, tag = "pending:${item.id}")) {
            is One.Ok -> ImportReport(listOf(r.name), emptyList(), emptyList())
            is One.Duplicate -> ImportReport(emptyList(), listOf(fileName), emptyList())
            is One.Fail -> ImportReport(emptyList(), emptyList(), listOf(ImportFailure(fileName, r.reason)))
        }
    }

    private sealed interface One {
        data class Ok(val name: String) : One
        data object Duplicate : One
        data class Fail(val reason: String) : One
    }

    private suspend fun importOne(
        uri: Uri,
        fileName: String,
        declaredSize: Long?,
        categoryOverride: String?,
        validCategories: Set<String>,
        nameOverride: String? = null,
        tag: String? = null,
    ): One {
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        val ext = ImportNaming.extensionFor(fileName, mime)
            ?: return One.Fail("formato não suportado (use MP3, WAV, OGG, M4A ou AAC)")
        if (declaredSize != null && declaredSize > MAX_BYTES) return One.Fail("arquivo maior que ${MAX_BYTES / 1_000_000} MB")
        if (declaredSize == 0L) return One.Fail("arquivo vazio")

        val dest = File(store.dir, UUID.randomUUID().toString() + "." + ext)
        val hash: String
        val size: Long
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return One.Fail("não foi possível abrir o arquivo")
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            input.use { ins ->
                dest.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = ins.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) {
                            out.close(); dest.delete()
                            return One.Fail("arquivo maior que ${MAX_BYTES / 1_000_000} MB")
                        }
                        digest.update(buffer, 0, n)
                        out.write(buffer, 0, n)
                    }
                }
            }
            if (total == 0L) { dest.delete(); return One.Fail("arquivo vazio") }
            size = total
            hash = digest.digest().joinToString("") { "%02x".format(it) }.take(32)
        } catch (e: SecurityException) {
            dest.delete(); return One.Fail("sem permissão para ler o arquivo")
        } catch (e: IOException) {
            dest.delete(); return One.Fail("erro de leitura: ${e.message}")
        }

        val key = "imported:$hash"
        if (soundDao.getByKey(key) != null) {
            dest.delete()
            return One.Duplicate
        }

        val probe = AudioProbe.probe(dest)
        if (probe.error != null) {
            dest.delete()
            return One.Fail(probe.error)
        }
        if (probe.durationMs != null && probe.durationMs > MAX_DURATION_MS) {
            dest.delete()
            return One.Fail("áudio com mais de ${MAX_DURATION_MS / 60_000} minutos")
        }

        val name = nameOverride ?: ImportNaming.displayName(fileName)
        val tags = listOfNotNull(tag)
        val category = categoryOverride?.takeIf { it in validCategories }
            ?: CategoryAutoMatcher.match(fileName).takeIf { it in validCategories }
        return try {
            soundDao.insert(
                SoundEntity(
                    key = key,
                    name = name,
                    searchName = TextNormalizer.searchIndex(name, tags),
                    categoryId = category,
                    source = SoundSource.IMPORTED.name,
                    uri = store.uriFor(dest),
                    durationMs = probe.durationMs,
                    sizeBytes = size,
                    isFavorite = false,
                    license = SoundLicense.USER_FILE.name,
                    author = null,
                    sourceUrl = null,
                    tags = tags.joinToString("|"),
                    addedAt = System.currentTimeMillis(),
                    isAvailable = true,
                ),
            )
            One.Ok(name)
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao salvar $fileName", e)
            dest.delete()
            One.Fail("erro ao salvar na biblioteca")
        }
    }

    private fun queryNameAndSize(uri: Uri): Pair<String, Long?> {
        var name: String? = null
        var size: Long? = null
        if (uri.scheme == "content") {
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                    ?.use { c ->
                        if (c.moveToFirst()) {
                            val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            val si = c.getColumnIndex(OpenableColumns.SIZE)
                            if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni)
                            if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                        }
                    }
            }
        }
        val fallback = uri.lastPathSegment?.substringAfterLast('/') ?: "audio"
        return (name ?: fallback) to size
    }

    companion object {
        private const val TAG = "AudioImporter"
        const val MAX_BYTES = 30_000_000L
        const val MAX_DURATION_MS = 10 * 60_000L
    }
}
