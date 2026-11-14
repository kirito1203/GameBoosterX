package com.gamebooster.x.performance

import android.os.Environment
import android.os.StatFs
import com.gamebooster.x.core.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Limpa o cache de todos os apps com `pm trim-caches` (comando oficial do Android, via Shizuku).
 * Só apaga arquivos de cache — nunca dados, contas ou mundos salvos.
 * Sem Shizuku o Android não permite que um app limpe o cache de outros.
 */
class StorageCleaner(private val shizuku: ShizukuManager) {

    data class Result(val ok: Boolean, val freedBytes: Long, val message: String)

    private fun freeBytes(): Long = StatFs(Environment.getDataDirectory().path).availableBytes

    suspend fun clearAppCaches(): Result = withContext(Dispatchers.IO) {
        if (!shizuku.isReady) {
            return@withContext Result(false, 0, "Limpar cache precisa do Shizuku iniciado")
        }
        val before = freeBytes()
        // Pedir um valor enorme faz o sistema liberar todo o cache possível.
        val r = shizuku.run("pm trim-caches 999G")
        delay(1_000)
        val freed = freeBytes() - before
        when {
            !r.ok -> Result(false, 0, "O sistema recusou a limpeza: ${r.output.take(120)}")
            freed > 0 -> Result(true, freed, "Cache limpo: ${Formatters.bytes(freed)} liberados")
            else -> Result(true, 0, "Cache já estava limpo (nada a liberar)")
        }
    }
}
