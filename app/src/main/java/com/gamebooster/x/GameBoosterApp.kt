package com.gamebooster.x

import android.app.Application
import android.content.Context
import com.gamebooster.x.data.SettingsRepository
import com.gamebooster.x.games.InstalledGamesScanner
import com.gamebooster.x.hardware.HardwareRepository
import com.gamebooster.x.performance.BackgroundCleaner
import com.gamebooster.x.performance.ShizukuManager

/** Injeção de dependências manual: um único container por processo. */
class AppContainer(context: Context) {
    val hardware = HardwareRepository(context)
    val settings = SettingsRepository(context)
    val cleaner = BackgroundCleaner(context)
    val games = InstalledGamesScanner(context)
    val shizuku = ShizukuManager(context)
}

class GameBoosterApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
