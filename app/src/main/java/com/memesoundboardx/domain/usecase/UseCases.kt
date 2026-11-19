package com.memesoundboardx.domain.usecase

import com.memesoundboardx.core.util.TextNormalizer
import com.memesoundboardx.domain.model.Category
import com.memesoundboardx.domain.model.Sound
import com.memesoundboardx.domain.repository.CategoryRepository
import com.memesoundboardx.domain.repository.SoundRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class ToggleFavoriteUseCase @Inject constructor(
    private val sounds: SoundRepository,
) {
    suspend operator fun invoke(sound: Sound) {
        sounds.setFavorite(sound.id, !sound.isFavorite)
    }
}

class SearchSoundsUseCase @Inject constructor(
    private val sounds: SoundRepository,
) {
    /** Consulta vazia ou com menos de [MIN_LENGTH] caracteres não pesquisa. */
    operator fun invoke(rawQuery: String): Flow<List<Sound>> {
        val normalized = TextNormalizer.normalize(rawQuery)
        if (normalized.length < MIN_LENGTH) return flowOf(emptyList())
        return sounds.search(normalized)
    }

    companion object {
        const val MIN_LENGTH = 1
    }
}

sealed interface NameValidation {
    data class Valid(val name: String) : NameValidation
    data object Empty : NameValidation
    data object TooLong : NameValidation
    data object Duplicate : NameValidation
}

object NameRules {
    const val MAX_LENGTH = 40

    fun validate(raw: String, existingNames: List<String>): NameValidation {
        val name = raw.trim().replace(Regex("\\s+"), " ")
        if (name.isEmpty()) return NameValidation.Empty
        if (name.length > MAX_LENGTH) return NameValidation.TooLong
        val normalized = TextNormalizer.normalize(name)
        if (existingNames.any { TextNormalizer.normalize(it) == normalized }) {
            return NameValidation.Duplicate
        }
        return NameValidation.Valid(name)
    }
}

class CreateCategoryUseCase @Inject constructor(
    private val categories: CategoryRepository,
) {
    sealed interface Result {
        data class Created(val category: Category) : Result
        data class Invalid(val reason: NameValidation) : Result
    }

    suspend operator fun invoke(rawName: String): Result {
        val existing = categories.getAll().map { it.name }
        return when (val v = NameRules.validate(rawName, existing)) {
            is NameValidation.Valid -> Result.Created(categories.createCustom(v.name))
            else -> Result.Invalid(v)
        }
    }
}

class RenameSoundUseCase @Inject constructor(
    private val sounds: SoundRepository,
) {
    /** Renomear som não exige nome único; só não pode ser vazio ou longo demais. */
    suspend operator fun invoke(soundId: Long, rawName: String): NameValidation {
        val v = NameRules.validate(rawName, existingNames = emptyList())
        if (v is NameValidation.Valid) sounds.rename(soundId, v.name)
        return v
    }
}
