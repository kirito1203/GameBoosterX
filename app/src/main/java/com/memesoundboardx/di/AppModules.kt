package com.memesoundboardx.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.memesoundboardx.data.local.datastore.SettingsDataStore
import com.memesoundboardx.data.local.db.AppDatabase
import com.memesoundboardx.data.local.db.CategoryDao
import com.memesoundboardx.data.local.db.SoundDao
import com.memesoundboardx.data.repository.AssetLibrarySyncer
import com.memesoundboardx.data.repository.CategoryRepositoryImpl
import com.memesoundboardx.data.repository.SoundRepositoryImpl
import com.memesoundboardx.domain.repository.CategoryRepository
import com.memesoundboardx.domain.repository.LibrarySyncer
import com.memesoundboardx.domain.repository.SettingsRepository
import com.memesoundboardx.domain.repository.SoundRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            // Sem fallbackToDestructiveMigration: favoritos e importações do usuário
            // nunca podem ser apagados por uma atualização. Cada mudança de schema
            // ganha uma Migration explícita.
            .build()

    @Provides
    fun provideSoundDao(db: AppDatabase): SoundDao = db.soundDao()

    @Provides
    fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("settings") },
        )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindSoundRepository(impl: SoundRepositoryImpl): SoundRepository

    @Binds
    abstract fun bindCategoryRepository(impl: CategoryRepositoryImpl): CategoryRepository

    @Binds
    abstract fun bindSettingsRepository(impl: SettingsDataStore): SettingsRepository

    @Binds
    abstract fun bindLibrarySyncer(impl: AssetLibrarySyncer): LibrarySyncer
}
