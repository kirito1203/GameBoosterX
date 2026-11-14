package com.gamebooster.x.games

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.gamebooster.x.core.InstalledGame
import com.gamebooster.x.core.KnownGames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Detecta jogos instalados respeitando a visibilidade de pacotes do Android 11+:
 * consulta apenas apps com ícone no launcher (declarado em <queries> no manifesto).
 *
 * Um app é considerado jogo se for um pacote conhecido ou se o próprio app se
 * declarar como jogo (ApplicationInfo.CATEGORY_GAME).
 */
class InstalledGamesScanner(context: Context) {

    private val appContext = context.applicationContext
    private val pm: PackageManager = appContext.packageManager

    @Suppress("DEPRECATION")
    suspend fun scan(): List<InstalledGame> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .mapNotNull { it.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != appContext.packageName }
            .mapNotNull { info ->
                val known = KnownGames.find(info.packageName)
                val declaresGame = info.category == ApplicationInfo.CATEGORY_GAME
                if (known == null && !declaresGame) return@mapNotNull null
                InstalledGame(
                    packageName = info.packageName,
                    name = known?.name ?: pm.getApplicationLabel(info).toString(),
                    isKnown = known != null,
                )
            }
            .sortedWith(compareByDescending<InstalledGame> { it.isKnown }.thenBy { it.name.lowercase() })
    }

    /** Apps com ícone no launcher (para atalhos da barra lateral), em ordem alfabética. */
    @Suppress("DEPRECATION")
    suspend fun launchableApps(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .mapNotNull { it.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != appContext.packageName }
            .map { it.packageName to pm.getApplicationLabel(it).toString() }
            .sortedBy { it.second.lowercase() }
    }

    fun label(packageName: String): String = runCatching {
        @Suppress("DEPRECATION")
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    fun launchIntent(packageName: String): Intent? =
        pm.getLaunchIntentForPackage(packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
