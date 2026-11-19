package com.memesoundboardx.domain.usecase

import com.memesoundboardx.domain.model.Category
import com.memesoundboardx.domain.model.LibraryStats
import com.memesoundboardx.domain.model.Sound
import com.memesoundboardx.domain.model.SoundLicense
import com.memesoundboardx.domain.model.SoundSource
import com.memesoundboardx.domain.repository.CategoryRepository
import com.memesoundboardx.domain.repository.SoundRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun sound(id: Long, name: String, fav: Boolean = false) = Sound(
    id = id, key = "imported:$id", name = name, categoryId = null, source = SoundSource.IMPORTED,
    uri = "content://x/$id", durationMs = null, sizeBytes = null, isFavorite = fav,
    license = SoundLicense.USER_FILE, author = null, sourceUrl = null, tags = emptyList(),
    addedAt = 0, isAvailable = true,
)

/** Repositório em memória para testar regras sem Android. */
private class FakeSoundRepository(initial: List<Sound>) : SoundRepository {
    val data = MutableStateFlow(initial)
    var lastSearch: String? = null

    override fun observeAll(): Flow<List<Sound>> = data
    override fun observeByCategory(categoryId: String) = data.map { l -> l.filter { it.categoryId == categoryId } }
    override fun observeFavorites() = data.map { l -> l.filter { it.isFavorite } }
    override fun observeImported() = data.map { l -> l.filter { it.source == SoundSource.IMPORTED } }
    override fun search(normalizedQuery: String): Flow<List<Sound>> {
        lastSearch = normalizedQuery
        return data.map { l -> l.filter { it.name.lowercase().contains(normalizedQuery) } }
    }
    override fun observeStats() = flowOf(LibraryStats(0, 0, 0, 0))
    override suspend fun setFavorite(soundId: Long, favorite: Boolean) {
        data.value = data.value.map { if (it.id == soundId) it.copy(isFavorite = favorite) else it }
    }
    override suspend fun rename(soundId: Long, newName: String) {
        data.value = data.value.map { if (it.id == soundId) it.copy(name = newName) else it }
    }
    override suspend fun setCategory(soundId: Long, categoryId: String?) = Unit
    override suspend fun delete(soundId: Long) { data.value = data.value.filterNot { it.id == soundId } }
    override fun observeFulfilledPending(): Flow<Set<String>> = flowOf(emptySet())
}

private class FakeCategoryRepository(names: List<String>) : CategoryRepository {
    val list = names.mapIndexed { i, n -> Category("c$i", n, false, i, null, 0) }.toMutableList()
    override fun observeCategories() = flowOf(list.toList())
    override suspend fun getAll() = list.toList()
    override suspend fun createCustom(name: String): Category =
        Category("custom_${list.size}", name, true, list.size, null, 0).also { list += it }
    override suspend fun renameCustom(id: String, newName: String) = Unit
    override suspend fun deleteCustom(id: String) = Unit
}

class ToggleFavoriteUseCaseTest {
    @Test fun togglesBothWays() = runBlocking {
        val repo = FakeSoundRepository(listOf(sound(1, "Bruh")))
        val toggle = ToggleFavoriteUseCase(repo)

        toggle(repo.data.value.first())
        assertTrue(repo.observeFavorites().first().single().id == 1L)

        toggle(repo.data.value.first())
        assertTrue(repo.observeFavorites().first().isEmpty())
    }
}

class SearchSoundsUseCaseTest {
    @Test fun blankQueryReturnsEmptyWithoutHittingRepository() = runBlocking {
        val repo = FakeSoundRepository(listOf(sound(1, "Bruh")))
        val result = SearchSoundsUseCase(repo)("   ").first()
        assertTrue(result.isEmpty())
        assertEquals(null, repo.lastSearch)
    }

    @Test fun normalizesBeforeSearching() = runBlocking {
        val repo = FakeSoundRepository(listOf(sound(1, "acao"), sound(2, "outro")))
        val result = SearchSoundsUseCase(repo)("  AÇÃO ").first()
        assertEquals("acao", repo.lastSearch)
        assertEquals(listOf(1L), result.map { it.id })
    }
}

class NameRulesTest {
    @Test fun trimsAndCollapsesSpaces() {
        assertEquals(NameValidation.Valid("Meus Memes"), NameRules.validate("  Meus   Memes ", emptyList()))
    }

    @Test fun rejectsEmpty() {
        assertEquals(NameValidation.Empty, NameRules.validate("   ", emptyList()))
    }

    @Test fun rejectsTooLong() {
        assertEquals(NameValidation.TooLong, NameRules.validate("x".repeat(NameRules.MAX_LENGTH + 1), emptyList()))
    }

    @Test fun duplicateIgnoresCaseAndAccents() {
        assertEquals(NameValidation.Duplicate, NameRules.validate("memes brasileiros", listOf("Memes Brasileiros")))
        assertEquals(NameValidation.Duplicate, NameRules.validate("Acao", listOf("Ação")))
    }
}

class CreateCategoryUseCaseTest {
    @Test fun createsValidCategory() = runBlocking {
        val repo = FakeCategoryRepository(listOf("Memes clássicos"))
        val r = CreateCategoryUseCase(repo)("Roblox favoritos")
        assertTrue(r is CreateCategoryUseCase.Result.Created)
        assertEquals(2, repo.list.size)
    }

    @Test fun refusesDuplicateOfBuiltIn() = runBlocking {
        val repo = FakeCategoryRepository(listOf("Memes clássicos"))
        val r = CreateCategoryUseCase(repo)("MEMES CLASSICOS")
        assertEquals(CreateCategoryUseCase.Result.Invalid(NameValidation.Duplicate), r)
        assertEquals(1, repo.list.size)
    }
}

class RenameSoundUseCaseTest {
    @Test fun renamesWithTrim() = runBlocking {
        val repo = FakeSoundRepository(listOf(sound(1, "velho")))
        val v = RenameSoundUseCase(repo)(1, "  novo nome ")
        assertEquals(NameValidation.Valid("novo nome"), v)
        assertEquals("novo nome", repo.data.value.single().name)
    }

    @Test fun refusesEmptyAndKeepsOldName() = runBlocking {
        val repo = FakeSoundRepository(listOf(sound(1, "velho")))
        assertEquals(NameValidation.Empty, RenameSoundUseCase(repo)(1, " "))
        assertEquals("velho", repo.data.value.single().name)
    }
}
