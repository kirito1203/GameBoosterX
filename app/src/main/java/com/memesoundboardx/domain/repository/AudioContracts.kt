package com.memesoundboardx.domain.repository

import com.memesoundboardx.domain.model.ImportReport
import com.memesoundboardx.domain.model.PlayerState
import com.memesoundboardx.domain.model.Sound
import kotlinx.coroutines.flow.StateFlow

/** Motor de reprodução. Implementado em core/audio/SoundEngine. */
interface SoundPlayer {
    val state: StateFlow<PlayerState>

    /** Parado → toca; tocando → pausa; pausado → continua. Nunca duplica o mesmo som. */
    fun toggle(sound: Sound)
    fun play(sound: Sound)
    fun pause(soundId: Long)
    fun resume(soundId: Long)
    fun stop(soundId: Long)
    fun stopAll()
    fun setRepeat(enabled: Boolean)
    /** Carrega antecipadamente (sons curtos) para tocar sem atraso. */
    fun preload(sounds: List<Sound>)
    /** Libera da memória tudo que não está tocando. */
    fun trimMemory()
    fun clearError()
    /** Diagnóstico: amostras curtas carregadas na RAM agora. */
    suspend fun loadedSampleCount(): Int
}

/** Importação via Storage Access Framework. [uris] são content:// (ou file:// em testes). */
interface AudioImporter {
    suspend fun import(uris: List<String>, categoryOverride: String? = null): ImportReport
}
