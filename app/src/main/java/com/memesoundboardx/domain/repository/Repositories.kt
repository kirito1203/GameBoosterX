package com.memesoundboardx.domain.repository

import com.memesoundboardx.domain.model.AppSettings
import com.memesoundboardx.domain.model.Category
import com.memesoundboardx.domain.model.LibraryStats
import com.memesoundboardx.domain.model.LibrarySyncReport
import com.memesoundboardx.domain.model.Sound
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface SoundRepository {
    fun observeAll(): Flow<List<Sound>>
    fun observeByCategory(categoryId: String): Flow<List<Sound>>
    fun observeFavorites(): Flow<List<Sound>>
    fun observeImported(): Flow<List<Sound>>
    /** [normalizedQuery] já deve vir normalizado (ver TextNormalizer). */
    fun search(normalizedQuery: String): Flow<List<Sound>>
    fun observeStats(): Flow<LibraryStats>
    suspend fun setFavorite(soundId: Long, favorite: Boolean)
    suspend fun rename(soundId: Long, newName: String)
    suspend fun setCategory(soundId: Long, categoryId: String?)
    suspend fun delete(soundId: Long)
    /** Ids de itens pendentes que já receberam arquivo do usuário. */
    fun observeFulfilledPending(): Flow<Set<String>>
}

interface CategoryRepository {
    fun observeCategories(): Flow<List<Category>>
    suspend fun getAll(): List<Category>
    suspend fun createCustom(name: String): Category
    suspend fun renameCustom(id: String, newName: String)
    suspend fun deleteCustom(id: String)
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setMasterVolume(volume: Float)
    suspend fun setMaxSimultaneous(count: Int)
    suspend fun setRequestAudioFocus(enabled: Boolean)
    suspend fun setOnboardingDone(done: Boolean)
    suspend fun hideBundled(key: String)
    suspend fun restoreBundled()
}

interface LibrarySyncer {
    val lastReport: StateFlow<LibrarySyncReport?>
    /** Memes conhecidos aguardando arquivo do usuário (do manifesto). */
    val pending: StateFlow<List<com.memesoundboardx.domain.model.PendingItem>>
    /** Créditos dos sons de terceiros embutidos no APK. */
    val credits: StateFlow<List<com.memesoundboardx.domain.model.CreditEntry>>
    /** Lê o manifesto embutido e sincroniza com o banco, preservando favoritos. */
    suspend fun syncBundled(): LibrarySyncReport
}
