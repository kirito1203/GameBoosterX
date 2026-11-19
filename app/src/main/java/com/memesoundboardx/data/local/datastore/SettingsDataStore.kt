package com.memesoundboardx.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.memesoundboardx.domain.model.AppSettings
import com.memesoundboardx.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    private object Keys {
        val VOLUME = floatPreferencesKey("master_volume")
        val MAX_SIMULTANEOUS = intPreferencesKey("max_simultaneous")
        val AUDIO_FOCUS = booleanPreferencesKey("request_audio_focus")
        val ONBOARDING = booleanPreferencesKey("onboarding_done")
        val HIDDEN = stringSetPreferencesKey("hidden_bundled")
    }

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            AppSettings(
                masterVolume = AppSettings.clampVolume(p[Keys.VOLUME] ?: AppSettings.DEFAULT_VOLUME),
                maxSimultaneous = AppSettings.clampSimultaneous(
                    p[Keys.MAX_SIMULTANEOUS] ?: AppSettings.DEFAULT_MAX_SIMULTANEOUS,
                ),
                requestAudioFocus = p[Keys.AUDIO_FOCUS] ?: false,
                onboardingDone = p[Keys.ONBOARDING] ?: false,
                hiddenBundled = p[Keys.HIDDEN] ?: emptySet(),
            )
        }
        .distinctUntilChanged()

    override suspend fun setMasterVolume(volume: Float) {
        dataStore.edit { it[Keys.VOLUME] = AppSettings.clampVolume(volume) }
    }

    override suspend fun setMaxSimultaneous(count: Int) {
        dataStore.edit { it[Keys.MAX_SIMULTANEOUS] = AppSettings.clampSimultaneous(count) }
    }

    override suspend fun setRequestAudioFocus(enabled: Boolean) {
        dataStore.edit { it[Keys.AUDIO_FOCUS] = enabled }
    }

    override suspend fun setOnboardingDone(done: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING] = done }
    }

    override suspend fun hideBundled(key: String) {
        dataStore.edit { it[Keys.HIDDEN] = (it[Keys.HIDDEN] ?: emptySet()) + key }
    }

    override suspend fun restoreBundled() {
        dataStore.edit { it.remove(Keys.HIDDEN) }
    }
}
