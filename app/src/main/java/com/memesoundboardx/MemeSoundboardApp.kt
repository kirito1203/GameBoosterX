package com.memesoundboardx

import android.app.Application
import android.util.Log
import com.memesoundboardx.di.ApplicationScope
import com.memesoundboardx.domain.repository.LibrarySyncer
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MemeSoundboardApp : Application() {

    @Inject lateinit var librarySyncer: LibrarySyncer
    @Inject @field:ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        // Sincronização em segundo plano: não atrasa a abertura do app.
        appScope.launch {
            runCatching { librarySyncer.syncBundled() }
                .onFailure { Log.e("MemeSoundboardApp", "Falha ao sincronizar biblioteca", it) }
        }
    }
}
