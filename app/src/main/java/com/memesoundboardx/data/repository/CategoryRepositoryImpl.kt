package com.memesoundboardx.data.repository

import com.memesoundboardx.data.local.db.CategoryDao
import com.memesoundboardx.data.local.db.CategoryEntity
import com.memesoundboardx.domain.model.Category
import com.memesoundboardx.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepositoryImpl @Inject constructor(
    private val dao: CategoryDao,
) : CategoryRepository {

    override fun observeCategories(): Flow<List<Category>> =
        dao.observeWithCounts().map { list -> list.map { it.toDomain() } }

    override suspend fun getAll(): List<Category> =
        dao.getAllWithCounts().map { it.toDomain() }

    override suspend fun createCustom(name: String): Category {
        val entity = CategoryEntity(
            id = "custom_" + UUID.randomUUID().toString().replace("-", "").take(12),
            name = name,
            isCustom = true,
            sortOrder = dao.maxCustomOrder() + 1,
            targetCount = null,
        )
        dao.insert(entity)
        return Category(entity.id, entity.name, true, entity.sortOrder, null, 0)
    }

    override suspend fun renameCustom(id: String, newName: String) = dao.renameCustom(id, newName)

    /** Os sons da categoria excluída ficam "sem categoria" (FK com SET_NULL). */
    override suspend fun deleteCustom(id: String) = dao.deleteCustom(id)
}
