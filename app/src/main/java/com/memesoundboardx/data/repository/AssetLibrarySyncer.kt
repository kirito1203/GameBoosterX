package com.memesoundboardx.data.repository

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.memesoundboardx.core.util.TextNormalizer
import com.memesoundboardx.data.local.db.AppDatabase
import com.memesoundboardx.data.local.db.CategoryEntity
import com.memesoundboardx.data.local.db.SoundEntity
import com.memesoundboardx.data.importer.ImportedFileStore
import com.memesoundboardx.data.manifest.SoundManifestParser
import com.memesoundboardx.di.IoDispatcher
import com.memesoundboardx.domain.model.LibrarySyncReport
import com.memesoundboardx.domain.model.CreditEntry
import com.memesoundboardx.domain.model.PendingItem
import com.memesoundboardx.domain.model.SoundLicense
import com.memesoundboardx.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import com.memesoundboardx.domain.model.SoundSource
import com.memesoundboardx.domain.repository.LibrarySyncer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sincroniza assets/sounds.json com o banco:
 *  - cria/atualiza as categorias padrão;
 *  - insere/atualiza sons embutidos, preservando favoritos;
 *  - remove sons embutidos que saíram do manifesto;
 *  - verifica se cada arquivo listado existe de fato no APK (sem arquivo = rejeitado).
 * Nunca toca em sons importados nem em categorias criadas pelo usuário.
 */
@Singleton
class AssetLibrarySyncer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val importedFiles: ImportedFileStore,
    private val settings: SettingsRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) : LibrarySyncer {

    private val mutex = Mutex()
    private val _lastReport = MutableStateFlow<LibrarySyncReport?>(null)
    override val lastReport: StateFlow<LibrarySyncReport?> = _lastReport.asStateFlow()
    private val _pending = MutableStateFlow<List<PendingItem>>(emptyList())
    override val pending: StateFlow<List<PendingItem>> = _pending.asStateFlow()
    private val _credits = MutableStateFlow<List<CreditEntry>>(emptyList())
    override val credits: StateFlow<List<CreditEntry>> = _credits.asStateFlow()

    override suspend fun syncBundled(): LibrarySyncReport = mutex.withLock {
        withContext(io) {
            val json = try {
                context.assets.open(MANIFEST).bufferedReader().use { it.readText() }
            } catch (e: IOException) {
                val report = LibrarySyncReport(0, listOf("Manifesto não encontrado: ${e.message}"), System.currentTimeMillis())
                _lastReport.value = report
                return@withContext report
            }

            val parsed = SoundManifestParser.parse(json)
            _pending.value = parsed.pending.map { PendingItem(it.id, it.name, it.categoryId, it.reason, it.hint) }
            _credits.value = parsed.sounds
                .filter { it.license != SoundLicense.ORIGINAL }
                .map { CreditEntry(it.name, it.author, it.license, it.sourceUrl) }
                .sortedBy { it.name.lowercase() }
            val hidden = settings.settings.first().hiddenBundled
            var hiddenCount = 0
            val errors = parsed.errors.toMutableList()
            val bundledFiles = context.assets.list(SOUNDS_DIR)?.toSet().orEmpty()

            val soundDao = db.soundDao()
            val categoryDao = db.categoryDao()
            val now = System.currentTimeMillis()
            var validCount = 0

            db.withTransaction {
                categoryDao.upsertAll(
                    parsed.categories.map {
                        CategoryEntity(it.id, it.name, isCustom = false, sortOrder = it.order, targetCount = it.targetCount)
                    },
                )

                val existing = soundDao.getBySource(SoundSource.BUNDLED.name).associateBy { it.key }
                val keep = mutableSetOf<String>()
                val toUpsert = mutableListOf<SoundEntity>()

                for (s in parsed.sounds) {
                    if (s.file !in bundledFiles) {
                        errors += "som ${s.id}: arquivo '${s.file}' não existe em assets/$SOUNDS_DIR"
                        continue
                    }
                    val key = "bundled:${s.id}"
                    if (key in hidden) { hiddenCount++; continue } // removido pelo usuário
                    val old = existing[key]
                    keep += key
                    toUpsert += SoundEntity(
                        id = old?.id ?: 0,
                        key = key,
                        name = s.name,
                        searchName = TextNormalizer.searchIndex(s.name, s.tags),
                        categoryId = s.categoryId,
                        source = SoundSource.BUNDLED.name,
                        uri = "$SOUNDS_DIR/${s.file}",
                        durationMs = s.durationMs ?: old?.durationMs,
                        sizeBytes = assetSize("$SOUNDS_DIR/${s.file}"),
                        isFavorite = old?.isFavorite ?: false,
                        license = s.license.name,
                        author = s.author,
                        sourceUrl = s.sourceUrl,
                        tags = s.tags.joinTags(),
                        addedAt = old?.addedAt ?: now,
                        isAvailable = true,
                    )
                }

                if (toUpsert.isNotEmpty()) soundDao.upsertAll(toUpsert)
                val removed = existing.keys - keep
                if (removed.isNotEmpty()) soundDao.deleteByKeys(removed.toList())
                if (parsed.categories.isNotEmpty()) {
                    categoryDao.deleteBuiltInNotIn(parsed.categories.map { it.id })
                }
                validCount = toUpsert.size
            }

            // Importados: marca como indisponível se a cópia interna sumiu (ex.: limpeza de dados parcial).
            for (imp in soundDao.getBySource(SoundSource.IMPORTED.name)) {
                val exists = importedFiles.exists(imp.uri)
                if (exists != imp.isAvailable) soundDao.setAvailable(imp.id, exists)
            }

            errors.forEach { Log.w(TAG, it) }
            val report = LibrarySyncReport(validCount, errors, System.currentTimeMillis(), hiddenCount)
            _lastReport.value = report
            report
        }
    }

    /** Tamanho real do asset; null se o arquivo estiver comprimido no APK. */
    private fun assetSize(path: String): Long? = try {
        context.assets.openFd(path).use { it.length }
    } catch (e: IOException) {
        null
    }

    private companion object {
        const val TAG = "LibrarySync"
        const val MANIFEST = "sounds.json"
        const val SOUNDS_DIR = "sounds"
    }
}
