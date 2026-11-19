package com.memesoundboardx.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.memesoundboardx.core.util.TextNormalizer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Testes instrumentados (rodam no celular ou emulador):
 *   gradle connectedDebugAndroidTest
 * Cobrem favoritos, pesquisa e categorias contra o SQLite real.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseTest {

    private lateinit var db: AppDatabase
    private lateinit var sounds: SoundDao
    private lateinit var categories: CategoryDao

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        sounds = db.soundDao()
        categories = db.categoryDao()
    }

    @After fun tearDown() = db.close()

    private fun entity(key: String, name: String, cat: String? = "impactos", tags: List<String> = emptyList()) =
        SoundEntity(
            key = key, name = name, searchName = TextNormalizer.searchIndex(name, tags),
            categoryId = cat, source = "IMPORTED", uri = "content://$key", durationMs = 1000,
            sizeBytes = 2048, isFavorite = false, license = "USER_FILE", author = null,
            sourceUrl = null, tags = tags.joinToString("|"), addedAt = 0,
        )

    private suspend fun seedCategory() =
        categories.upsertAll(listOf(CategoryEntity("impactos", "Impactos", false, 1, 35)))

    @Test fun favoriteToggleIsPersisted() = runBlocking<Unit> {
        seedCategory()
        val id = sounds.insert(entity("k1", "Soco"))
        sounds.setFavorite(id, true)
        assertEquals(listOf("Soco"), sounds.observeFavorites().first().map { it.name })
        sounds.setFavorite(id, false)
        assertTrue(sounds.observeFavorites().first().isEmpty())
    }

    @Test fun searchIgnoresAccentsAndMatchesTags() = runBlocking<Unit> {
        seedCategory()
        sounds.insert(entity("k1", "Reação épica"))
        sounds.insert(entity("k2", "Tapa", tags = listOf("impacto")))
        sounds.insert(entity("k3", "Outro"))

        assertEquals(listOf("Reação épica"), sounds.search("reacao").first().map { it.name })
        assertEquals(listOf("Tapa"), sounds.search("impacto").first().map { it.name })
    }

    @Test fun searchTreatsPercentLiterally() = runBlocking<Unit> {
        seedCategory()
        sounds.insert(entity("k1", "100% bruh"))
        sounds.insert(entity("k2", "Nada"))
        val escaped = TextNormalizer.escapeLike("%")
        assertEquals(listOf("100% bruh"), sounds.search(escaped).first().map { it.name })
    }

    @Test fun deletingCustomCategoryKeepsSoundsUncategorized() = runBlocking<Unit> {
        categories.insert(CategoryEntity("custom_1", "Minha", true, 1, null))
        val id = sounds.insert(entity("k1", "Som", cat = "custom_1"))
        categories.deleteCustom("custom_1")
        assertNull(sounds.getById(id)!!.categoryId)
        assertEquals(1, sounds.observeAll().first().size)
    }

    @Test fun builtInCategoryCannotBeDeletedAsCustom() = runBlocking<Unit> {
        seedCategory()
        categories.deleteCustom("impactos")
        assertEquals(1, categories.getAllWithCounts().size)
    }

    @Test fun categoryCountsSounds() = runBlocking<Unit> {
        seedCategory()
        sounds.insert(entity("k1", "A"))
        sounds.insert(entity("k2", "B"))
        assertEquals(2, categories.observeWithCounts().first().single().soundCount)
    }

    @Test fun renameUpdatesSearchIndex() = runBlocking<Unit> {
        seedCategory()
        val id = sounds.insert(entity("k1", "Antigo"))
        sounds.rename(id, "Novo nome", TextNormalizer.searchIndex("Novo nome", emptyList()))
        assertEquals(1, sounds.search("novo").first().size)
        assertTrue(sounds.search("antigo").first().isEmpty())
    }

    @Test fun statsCountsBySource() = runBlocking<Unit> {
        seedCategory()
        val id = sounds.insert(entity("k1", "A"))
        sounds.insert(entity("k2", "B"))
        sounds.setFavorite(id, true)
        val c = sounds.observeCounts().first()
        assertEquals(2, c.total)
        assertEquals(1, c.favorites)
        assertEquals(2, c.imported)
        assertEquals(0, c.bundled)
    }
}
