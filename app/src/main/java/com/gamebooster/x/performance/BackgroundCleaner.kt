package com.gamebooster.x.performance

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.gamebooster.x.core.BoostReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Otimização sem root: pede ao Android que encerre processos em segundo plano
 * de apps instalados pelo usuário (API pública killBackgroundProcesses).
 *
 * Limitações reais (documentadas na interface):
 * - Só afeta processos em segundo plano/cache; apps em primeiro plano e serviços
 *   ativos não são encerrados.
 * - O sistema pode reiniciar alguns processos logo depois.
 * - Apps do sistema não são tocados.
 */
class BackgroundCleaner(context: Context) {

    private val appContext = context.applicationContext
    private val activityManager: ActivityManager =
        checkNotNull(appContext.getSystemService(ActivityManager::class.java)) { "ActivityManager indisponível" }
    private val packageManager: PackageManager = appContext.packageManager

    suspend fun boost(keepPackages: Set<String>): BoostReport = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val before = availableBytes()

        val targets = launchableUserPackages() - keepPackages - appContext.packageName
        targets.forEach { pkg ->
            runCatching { activityManager.killBackgroundProcesses(pkg) }
        }

        // Dá tempo ao sistema para recalcular a memória disponível antes da segunda leitura.
        delay(SETTLE_MS)
        val after = availableBytes()

        BoostReport(
            availableBeforeBytes = before,
            availableAfterBytes = after,
            processesTargeted = targets.size,
            durationMs = System.currentTimeMillis() - start,
            timestampMs = System.currentTimeMillis(),
        )
    }

    private fun availableBytes(): Long {
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        return info.availMem
    }

    @Suppress("DEPRECATION")
    private fun launchableUserPackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, 0)
            .mapNotNull { it.activityInfo?.applicationInfo }
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .map { it.packageName }
            .toSet()
    }

    companion object {
        private const val SETTLE_MS = 1_500L
    }
}
