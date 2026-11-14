package com.gamebooster.x.performance

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.gamebooster.x.core.ShellResult
import com.gamebooster.x.core.ShizukuStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.lang.reflect.Method

/**
 * Ponte com o Shizuku: permite executar comandos com a mesma permissão do `adb shell`
 * (sem root). O usuário precisa ter o Shizuku instalado, iniciado e ter autorizado este app.
 */
class ShizukuManager(context: Context) {

    private val appContext = context.applicationContext
    private val pm: PackageManager = appContext.packageManager

    private val _status = MutableStateFlow(ShizukuStatus.NOT_INSTALLED)
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener { refresh() }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    init {
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        refresh()
    }

    fun refresh() {
        _status.value = computeStatus()
    }

    private fun computeStatus(): ShizukuStatus {
        val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!running) return if (isInstalled()) ShizukuStatus.NOT_RUNNING else ShizukuStatus.NOT_INSTALLED
        if (runCatching { Shizuku.isPreV11() }.getOrDefault(true)) return ShizukuStatus.OUTDATED
        val granted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        return if (granted) ShizukuStatus.READY else ShizukuStatus.NO_PERMISSION
    }

    @Suppress("DEPRECATION")
    private fun isInstalled(): Boolean = try {
        pm.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun requestPermission() {
        if (runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        }
        refresh()
    }

    /** Abre o Shizuku se instalado; senão, a página dele na Play Store. */
    fun openShizukuIntent(): Intent =
        pm.getLaunchIntentForPackage(SHIZUKU_PACKAGE)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$SHIZUKU_PACKAGE"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Shizuku.newProcess é privado na API 13; o acesso por reflexão é a forma usada
     * pela comunidade para comandos curtos. Mantido pelas regras do ProGuard.
     */
    private val newProcess: Method by lazy {
        Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        ).apply { isAccessible = true }
    }

    suspend fun run(command: String): ShellResult = withContext(Dispatchers.IO) {
        if (status.value != ShizukuStatus.READY) {
            return@withContext ShellResult(command, -1, "Shizuku não está pronto: ${status.value.label}")
        }
        try {
            val process = newProcess.invoke(null, arrayOf("sh", "-c", "$command 2>&1"), null, null) as Process
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            val code = process.waitFor()
            ShellResult(command, code, output)
        } catch (e: Exception) {
            val cause = (e.cause ?: e)
            ShellResult(command, -1, "${cause.javaClass.simpleName}: ${cause.message}")
        }
    }

    companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val PERMISSION_REQUEST_CODE = 4210
    }
}
