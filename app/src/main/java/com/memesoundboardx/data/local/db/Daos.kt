package com.memesoundboardx.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SoundDao {

    @Query("SELECT * FROM sounds WHERE isAvailable = 1 ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<SoundEntity>>

    @Query("SELECT * FROM sounds WHERE isAvailable = 1 AND categoryId = :categoryId ORDER BY name COLLATE NOCASE")
    fun observeByCategory(categoryId: String): Flow<List<SoundEntity>>

    @Query("SELECT * FROM sounds WHERE isAvailable = 1 AND isFavorite = 1 ORDER BY name COLLATE NOCASE")
    fun observeFavorites(): Flow<List<SoundEntity>>

    /** Inclui indisponíveis, para o usuário ver e excluir arquivos órfãos. */
    @Query("SELECT * FROM sounds WHERE source = :source ORDER BY addedAt DESC")
    fun observeBySource(source: String): Flow<List<SoundEntity>>

    /** [escapedQuery] deve vir normalizado e escapado (TextNormalizer.escapeLike). */
    @Query(
        """
        SELECT * FROM sounds
        WHERE isAvailable = 1 AND searchName LIKE '%' || :escapedQuery || '%' ESCAPE '\'
        ORDER BY
            CASE WHEN searchName LIKE :escapedQuery || '%' ESCAPE '\' THEN 0 ELSE 1 END,
            name COLLATE NOCASE
        LIMIT 200
        """
    )
    fun search(escapedQuery: String): Flow<List<SoundEntity>>

    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM sounds WHERE isAvailable = 1) AS total,
            (SELECT COUNT(*) FROM sounds WHERE isAvailable = 1 AND isFavorite = 1) AS favorites,
            (SELECT COUNT(*) FROM sounds WHERE source = 'IMPORTED') AS imported,
            (SELECT COUNT(*) FROM sounds WHERE source = 'BUNDLED' AND isAvailable = 1) AS bundled
        """
    )
    fun observeCounts(): Flow<LibraryCounts>

    @Query("SELECT * FROM sounds WHERE source = :source")
    suspend fun getBySource(source: String): List<SoundEntity>

    @Query("SELECT * FROM sounds WHERE id = :id")
    suspend fun getById(id: Long): SoundEntity?

    @Upsert
    suspend fun upsertAll(sounds: List<SoundEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(sound: SoundEntity): Long

    @Query("UPDATE sounds SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE sounds SET name = :name, searchName = :searchName WHERE id = :id")
    suspend fun rename(id: Long, name: String, searchName: String)

    @Query("UPDATE sounds SET categoryId = :categoryId WHERE id = :id")
    suspend fun setCategory(id: Long, categoryId: String?)

    @Query("DELETE FROM sounds WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM sounds WHERE `key` IN (:keys)")
    suspend fun deleteByKeys(keys: List<String>)
}

@Dao
interface CategoryDao {

    @Query(
        """
        SELECT c.*,
            (SELECT COUNT(*) FROM sounds s WHERE s.categoryId = c.id AND s.isAvailable = 1) AS soundCount
        FROM categories c
        ORDER BY c.isCustom, c.sortOrder, c.name COLLATE NOCASE
        """
    )
    fun observeWithCounts(): Flow<List<CategoryWithCount>>

    @Query(
        """
        SELECT c.*,
            (SELECT COUNT(*) FROM sounds s WHERE s.categoryId = c.id AND s.isAvailable = 1) AS soundCount
        FROM categories c
        ORDER BY c.isCustom, c.sortOrder, c.name COLLATE NOCASE
        """
    )
    suspend fun getAllWithCounts(): List<CategoryWithCount>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM categories WHERE isCustom = 1")
    suspend fun maxCustomOrder(): Int

    @Upsert
    suspend fun upsertAll(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: CategoryEntity)

    @Query("UPDATE categories SET name = :name WHERE id = :id AND isCustom = 1")
    suspend fun renameCustom(id: String, name: String)

    @Query("DELETE FROM categories WHERE id = :id AND isCustom = 1")
    suspend fun deleteCustom(id: String)

    @Query("DELETE FROM categories WHERE isCustom = 0 AND id NOT IN (:keepIds)")
    suspend fun deleteBuiltInNotIn(keepIds: List<String>)
}
