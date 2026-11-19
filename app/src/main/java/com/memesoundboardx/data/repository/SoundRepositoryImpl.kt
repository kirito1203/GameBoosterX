package com.memesoundboardx.data.repository

import com.memesoundboardx.core.util.TextNormalizer
import com.memesoundboardx.data.importer.ImportedFileStore
import com.memesoundboardx.data.local.db.SoundDao
import com.memesoundboardx.domain.model.LibraryStats
import com.memesoundboardx.domain.model.Sound
import com.memesoundboardx.domain.model.SoundSource
import com.memesoundboardx.domain.repository.SoundRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SoundRepositoryImpl @Inject constructor(
    private val dao: SoundDao,
    private val importedFiles: ImportedFileStore,
) : SoundRepository {

    override fun observeAll(): Flow<List<Sound>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeByCategory(categoryId: String): Flow<List<Sound>> =
        dao.observeByCategory(categoryId).map { list -> list.map { it.toDomain() } }

    override fun observeFavorites(): Flow<List<Sound>> =
        dao.observeFavorites().map { list -> list.map { it.toDomain() } }

    override fun observeImported(): Flow<List<Sound>> =
        dao.observeBySource(SoundSource.IMPORTED.name).map { list -> list.map { it.toDomain() } }

    override fun search(normalizedQuery: String): Flow<List<Sound>> =
        dao.search(TextNormalizer.escapeLike(normalizedQuery)).map { list -> list.map { it.toDomain() } }

    override fun observeStats(): Flow<LibraryStats> =
        dao.observeCounts().map { it.toDomain() }.distinctUntilChanged()

    override suspend fun setFavorite(soundId: Long, favorite: Boolean) =
        dao.setFavorite(soundId, favorite)

    override suspend fun rename(soundId: Long, newName: String) {
        val current = dao.getById(soundId) ?: return
        val tags = if (current.tags.isEmpty()) emptyList() else current.tags.split(TAG_SEPARATOR)
        dao.rename(soundId, newName, TextNormalizer.searchIndex(newName, tags))
    }

    override suspend fun setCategory(soundId: Long, categoryId: String?) =
        dao.setCategory(soundId, categoryId)

    /**
     * Remove o som da biblioteca e apaga a CÓPIA interna (pasta privada do app).
     * O arquivo original do usuário, de onde o som foi importado, nunca é tocado.
     * Sons embutidos não podem ser excluídos (voltariam na próxima sincronização).
     */
    override suspend fun delete(soundId: Long) {
        val s = dao.getById(soundId) ?: return
        if (s.source != SoundSource.IMPORTED.name) return
        dao.deleteById(soundId)
        importedFiles.delete(s.uri)
    }
}
