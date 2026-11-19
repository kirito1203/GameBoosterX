package com.memesoundboardx

import android.app.Application
import android.util.Log
import com.memesoundboardx.di.ApplicationScope
import com.memesoundboardx.domain.repository.LibrarySyncer
import com.memesoundboardx.domain.repository.SoundPlayer
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MemeSoundboardApp : Application() {

    @Inject lateinit var librarySyncer: LibrarySyncer
    @Inject @field:ApplicationScope lateinit var appScope: CoroutineScope
    @Inject lateinit var player: SoundPlayer

    override fun onCreate() {
        super.onCreate()
        // Sincronização em segundo plano: não atrasa a abertura do app.
        appScope.launch {
            runCatching { librarySyncer.syncBundled() }
                .onFailure { Log.e("MemeSoundboardApp", "Falha ao sincronizar biblioteca", it) }
        }
    }

    /** Sistema pedindo memória (app em segundo plano ou RAM baixa): descarrega amostras ociosas. */
    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND || level == TRIM_MEMORY_RUNNING_LOW) player.trimMemory()
    }
}
