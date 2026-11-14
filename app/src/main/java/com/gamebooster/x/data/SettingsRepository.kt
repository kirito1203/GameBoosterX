package com.gamebooster.x.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gamebooster.x.core.GameSpaceSettings
import com.gamebooster.x.core.PerformanceMode
import com.gamebooster.x.core.TurboSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "gbx_settings")

/** Preferências persistentes (DataStore). */
class SettingsRepository(context: Context) {

    private val store = context.applicationContext.settingsStore

    val performanceMode: Flow<PerformanceMode> =
        store.data.map { prefs -> PerformanceMode.fromName(prefs[KEY_MODE]) }

    suspend fun setPerformanceMode(mode: PerformanceMode) {
        store.edit { it[KEY_MODE] = mode.name }
    }

    /** Configuração do Modo Turbo salva por jogo. */
    fun turboSettings(packageName: String): Flow<TurboSettings> = store.data.map { prefs ->
        TurboSettings(
            resolutionScale = prefs[floatPreferencesKey("turbo_scale_$packageName")]
                ?: TurboSettings.DEFAULT.resolutionScale,
            fpsCap = prefs[intPreferencesKey("turbo_fps_$packageName")] ?: TurboSettings.DEFAULT.fpsCap,
        )
    }

    suspend fun setTurboSettings(packageName: String, settings: TurboSettings) {
        store.edit {
            it[floatPreferencesKey("turbo_scale_$packageName")] = settings.resolutionScale
            it[intPreferencesKey("turbo_fps_$packageName")] = settings.fpsCap
        }
    }

    val gameSpaceSettings: Flow<GameSpaceSettings> = store.data.map { p ->
        val d = GameSpaceSettings()
        GameSpaceSettings(
            doNotDisturb = p[GS_DND] ?: d.doNotDisturb,
            lockBrightness = p[GS_BRIGHTNESS] ?: d.lockBrightness,
            overlay = p[GS_OVERLAY] ?: d.overlay,
            fpsCounter = p[GS_FPS] ?: d.fpsCounter,
            notificationTicker = p[GS_TICKER] ?: d.notificationTicker,
        )
    }

    suspend fun gameSpaceSettingsNow(): GameSpaceSettings = gameSpaceSettings.first()

    suspend fun setGameSpaceSettings(s: GameSpaceSettings) {
        store.edit {
            it[GS_DND] = s.doNotDisturb
            it[GS_BRIGHTNESS] = s.lockBrightness
            it[GS_OVERLAY] = s.overlay
            it[GS_FPS] = s.fpsCounter
            it[GS_TICKER] = s.notificationTicker
        }
    }

    private companion object {
        val GS_DND = booleanPreferencesKey("gs_dnd")
        val GS_BRIGHTNESS = booleanPreferencesKey("gs_brightness")
        val GS_OVERLAY = booleanPreferencesKey("gs_overlay")
        val GS_FPS = booleanPreferencesKey("gs_fps")
        val GS_TICKER = booleanPreferencesKey("gs_ticker")
        val KEY_MODE = stringPreferencesKey("performance_mode")
    }
}
