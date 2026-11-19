package com.memesoundboardx.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SoundEntity::class, CategoryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun soundDao(): SoundDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        const val NAME = "meme_soundboard_x.db"
    }
}
