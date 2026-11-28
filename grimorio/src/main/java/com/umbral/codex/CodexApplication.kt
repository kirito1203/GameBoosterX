package com.umbral.codex

import android.app.Application
import com.umbral.codex.data.CodexRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/** Container de dependências: um único repositório para o app inteiro. */
class CodexApplication : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var repository: CodexRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = CodexRepository(File(filesDir, "codex"), appScope)
        appScope.launch { repository.load() }
    }
}
