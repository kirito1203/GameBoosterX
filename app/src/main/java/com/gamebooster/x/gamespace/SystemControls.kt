package com.gamebooster.x.gamespace

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Permissões especiais que o usuário concede nas Configurações do Android. */
enum class GsPermission(val label: String, val purpose: String) {
    OVERLAY("Sobrepor a outros apps", "Painel flutuante dentro do jogo"),
    USAGE("Acesso ao uso", "Saber quando um jogo é aberto"),
    DND("Acesso ao Não perturbe", "Bloquear notificações e chamadas durante o jogo"),
    WRITE_SETTINGS("Modificar configurações do sistema", "Travar o brilho durante o jogo"),
    NOTIFICATION_LISTENER("Acesso às notificações", "Mostrar notificações como texto discreto no painel"),
    POST_NOTIFICATIONS("Notificações do app", "Mostrar que o Game Space está ativo"),
    BATTERY("Bateria sem restrições", "Impede o realme de desligar o Game Space em segundo plano"),
}

class SystemControls(context: Context) {

    private val ctx = context.applicationContext
    private val notificationManager: NotificationManager =
        checkNotNull(ctx.getSystemService(NotificationManager::class.java))

    fun isGranted(p: GsPermission): Boolean = when (p) {
        GsPermission.OVERLAY -> Settings.canDrawOverlays(ctx)
        GsPermission.USAGE -> hasUsageAccess()
        GsPermission.DND -> notificationManager.isNotificationPolicyAccessGranted
        GsPermission.WRITE_SETTINGS -> Settings.System.canWrite(ctx)
        GsPermission.NOTIFICATION_LISTENER ->
            NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
        GsPermission.BATTERY ->
            ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) == true
        GsPermission.POST_NOTIFICATIONS ->
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    @Suppress("DEPRECATION")
    private fun hasUsageAccess(): Boolean {
        val appOps = ctx.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Tela de Configurações onde o usuário concede a permissão. */
    fun settingsIntent(p: GsPermission): Intent {
        val pkgUri = Uri.parse("package:${ctx.packageName}")
        val intent = when (p) {
            GsPermission.OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri)
            GsPermission.USAGE -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            GsPermission.DND -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            GsPermission.WRITE_SETTINGS -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, pkgUri)
            GsPermission.NOTIFICATION_LISTENER -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            GsPermission.BATTERY -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri)
            GsPermission.POST_NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun appDetailsIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    // ---------- Não perturbe ----------

    /** Ativa "só alarmes" e devolve o filtro anterior para restaurar depois; nulo se sem permissão. */
    fun enableGameDnd(): Int? {
        if (!notificationManager.isNotificationPolicyAccessGranted) return null
        val previous = notificationManager.currentInterruptionFilter
        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
        return previous
    }

    fun restoreDnd(previous: Int) {
        if (!notificationManager.isNotificationPolicyAccessGranted) return
        val filter = if (previous == NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {
            NotificationManager.INTERRUPTION_FILTER_ALL
        } else previous
        notificationManager.setInterruptionFilter(filter)
    }

    // ---------- Brilho ----------

    /** Desliga o brilho automático (mantém o nível atual). Devolve o modo anterior; nulo se sem permissão. */
    fun lockBrightness(): Int? {
        if (!Settings.System.canWrite(ctx)) return null
        val previous = Settings.System.getInt(
            ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        )
        Settings.System.putInt(
            ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
        )
        return previous
    }

    fun restoreBrightness(previousMode: Int) {
        if (!Settings.System.canWrite(ctx)) return
        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, previousMode)
    }

    fun listenerComponent(): ComponentName = ComponentName(ctx, GameNotificationListener::class.java)
}
