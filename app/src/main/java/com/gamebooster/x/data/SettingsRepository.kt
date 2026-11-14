package com.gamebooster.x.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gamebooster.x.core.PerformanceMode
import com.gamebooster.x.core.TurboSettings
import kotlinx.coroutines.flow.Flow
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

    private companion object {
        val KEY_MODE = stringPreferencesKey("performance_mode")
    }
}
