package com.memesoundboardx.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isCustom: Boolean,
    val sortOrder: Int,
    val targetCount: Int?,
)

@Entity(
    tableName = "sounds",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["key"], unique = true),
        Index(value = ["categoryId"]),
        Index(value = ["isFavorite"]),
        Index(value = ["source"]),
    ],
)
data class SoundEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val name: String,
    /** Nome + tags normalizados, usado na pesquisa. */
    val searchName: String,
    val categoryId: String?,
    /** "BUNDLED" ou "IMPORTED". */
    val source: String,
    val uri: String,
    val durationMs: Long?,
    val sizeBytes: Long?,
    val isFavorite: Boolean,
    val license: String,
    val author: String?,
    val sourceUrl: String?,
    /** Tags separadas por '|'. */
    val tags: String,
    val addedAt: Long,
    @ColumnInfo(defaultValue = "1") val isAvailable: Boolean = true,
)

data class CategoryWithCount(
    @Embedded val category: CategoryEntity,
    val soundCount: Int,
)

data class LibraryCounts(
    val total: Int,
    val favorites: Int,
    val imported: Int,
    val bundled: Int,
)
