package com.memesoundboardx.data.repository

import com.memesoundboardx.data.local.db.CategoryWithCount
import com.memesoundboardx.data.local.db.LibraryCounts
import com.memesoundboardx.data.local.db.SoundEntity
import com.memesoundboardx.domain.model.Category
import com.memesoundboardx.domain.model.LibraryStats
import com.memesoundboardx.domain.model.Sound
import com.memesoundboardx.domain.model.SoundLicense
import com.memesoundboardx.domain.model.SoundSource

internal const val TAG_SEPARATOR = "|"

internal fun SoundEntity.toDomain(): Sound = Sound(
    id = id,
    key = key,
    name = name,
    categoryId = categoryId,
    source = runCatching { SoundSource.valueOf(source) }.getOrDefault(SoundSource.IMPORTED),
    uri = uri,
    durationMs = durationMs,
    sizeBytes = sizeBytes,
    isFavorite = isFavorite,
    license = runCatching { SoundLicense.valueOf(license) }.getOrDefault(SoundLicense.USER_FILE),
    author = author,
    sourceUrl = sourceUrl,
    tags = if (tags.isEmpty()) emptyList() else tags.split(TAG_SEPARATOR),
    addedAt = addedAt,
    isAvailable = isAvailable,
)

internal fun CategoryWithCount.toDomain(): Category = Category(
    id = category.id,
    name = category.name,
    isCustom = category.isCustom,
    order = category.sortOrder,
    targetCount = category.targetCount,
    soundCount = soundCount,
)

internal fun LibraryCounts.toDomain(): LibraryStats =
    LibraryStats(total = total, favorites = favorites, imported = imported, bundled = bundled)

internal fun List<String>.joinTags(): String =
    map { it.replace(TAG_SEPARATOR, " ").trim() }.filter { it.isNotEmpty() }.joinToString(TAG_SEPARATOR)
