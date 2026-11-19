package com.memesoundboardx.core.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.memesoundboardx.di.ApplicationScope
import com.memesoundboardx.domain.model.AppSettings
import com.memesoundboardx.domain.model.PlayStatus
import com.memesoundboardx.domain.model.PlayerState
import com.memesoundboardx.domain.model.Sound
import com.memesoundboardx.domain.model.SoundSource
import com.memesoundboardx.domain.repository.SettingsRepository
import com.memesoundboardx.domain.repository.SoundPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Motor de áudio do MEME SOUNDBOARD X.
 *
 * - Sons curtos (≤ 5 s): SoundPool. Amostra decodificada na RAM → disparo quase instantâneo.
 *   Cache LRU de [PlaybackPolicy.SAMPLE_CACHE_SIZE] amostras; o resto é descarregado.
 * - Sons longos (vinhetas/músicas): MediaPlayer em streaming, no máximo [PlaybackPolicy.MAX_LONG_PLAYERS].
 * - Todo o estado é alterado numa única fila (dispatcher de paralelismo 1): sem condições de corrida.
 * - Não pede foco de áudio por padrão → o Roblox continua tocando normalmente.
 * - Fone desconectado (AUDIO_BECOMING_NOISY) → para tudo, para não tocar alto no alto-falante.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SoundEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    settings: SettingsRepository,
    @ApplicationScope appScope: CoroutineScope,
) : SoundPlayer {

    private val engine = Dispatchers.Default.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob(appScope.coroutineContext[Job]) + engine)

    private val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(PlaybackPolicy.MAX_POOL_STREAMS)
        .setAudioAttributes(attributes)
        .build()
        .also { p -> p.setOnLoadCompleteListener { _, sampleId, status -> scope.launch { onSampleLoaded(sampleId, status) } } }

    private val audioManager = context.getSystemService(AudioManager::class.java)

    // ---------------------------------------------------------------- estado (só no dispatcher `engine`)

    private class Sample(val sampleId: Int) {
        var loaded = false
    }

    private sealed class Voice(val sound: Sound) {
        var paused = false
    }

    private class LoadingVoice(sound: Sound) : Voice(sound)

    private class PoolVoice(sound: Sound, val streamId: Int, var looping: Boolean, var remainingMs: Long) : Voice(sound) {
        var resumedAt = SystemClock.elapsedRealtime()
        var endJob: Job? = null
    }

    private class MediaVoice(sound: Sound, val player: MediaPlayer) : Voice(sound) {
        var prepared = false
    }

    private val samples = HashMap<String, Sample>()
    private val uriBySampleId = HashMap<Int, String>()
    private val cache = LruKeyCache<String>(PlaybackPolicy.SAMPLE_CACHE_SIZE)

    /** Ordem de inserção = ordem de início (o mais antigo primeiro). */
    private val voices = LinkedHashMap<Long, Voice>()

    private var volume = AppSettings.DEFAULT_VOLUME
    private var maxSimultaneous = AppSettings.DEFAULT_MAX_SIMULTANEOUS
    private var useFocus = false
    private var repeat = false
    private var lastError: String? = null
    private var focusRequest: AudioFocusRequest? = null

    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) stopAll()
        }
    }

    init {
        ContextCompat.registerReceiver(
            context, noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        scope.launch {
            settings.settings.collect { s ->
                volume = s.masterVolume
                maxSimultaneous = s.maxSimultaneous
                useFocus = s.requestAudioFocus
                if (!useFocus) abandonFocus()
                applyVolume()
                publish()
            }
        }
    }

    // ---------------------------------------------------------------- API pública (assíncrona, enfileirada)

    override fun toggle(sound: Sound) {
        scope.launch {
            when (val v = voices[sound.id]) {
                null -> startPlay(sound)
                is LoadingVoice -> stopInternal(sound.id)
                else -> if (v.paused) resumeInternal(v) else pauseInternal(v)
            }
            publish()
        }
    }

    override fun play(sound: Sound) {
        scope.launch {
            stopInternal(sound.id)
            startPlay(sound)
            publish()
        }
    }

    override fun pause(soundId: Long) {
        scope.launch { voices[soundId]?.let { pauseInternal(it) }; publish() }
    }

    override fun resume(soundId: Long) {
        scope.launch { voices[soundId]?.let { resumeInternal(it) }; publish() }
    }

    override fun stop(soundId: Long) {
        scope.launch { stopInternal(soundId); afterVoiceEnded() }
    }

    override fun stopAll() {
        scope.launch {
            voices.keys.toList().forEach { stopInternal(it) }
            afterVoiceEnded()
        }
    }

    override fun setRepeat(enabled: Boolean) {
        scope.launch {
            repeat = enabled
            for (v in voices.values) {
                when (v) {
                    is PoolVoice -> {
                        pool.setLoop(v.streamId, if (enabled) -1 else 0)
                        v.looping = enabled
                        if (enabled) {
                            v.endJob?.cancel()
                        } else if (!v.paused) {
                            // Termina a volta atual; duração exata da volta é desconhecida, usa a do som.
                            v.remainingMs = v.sound.durationMs ?: PlaybackPolicy.UNKNOWN_DURATION_FALLBACK_MS
                            v.resumedAt = SystemClock.elapsedRealtime()
                            scheduleEnd(v)
                        }
                    }
                    is MediaVoice -> v.player.isLooping = enabled
                    is LoadingVoice -> Unit
                }
            }
            publish()
        }
    }

    override fun preload(sounds: List<Sound>) {
        scope.launch {
            sounds.asSequence()
                .filter { it.isAvailable && PlaybackPolicy.useSoundPool(it.durationMs, it.sizeBytes) }
                .take(PRELOAD_LIMIT)
                .forEach { s -> if (!samples.containsKey(s.uri)) ensureSample(s) }
        }
    }

    override fun trimMemory() {
        scope.launch { cache.clearUnpinned(::isPinned).forEach(::unload) }
    }

    override fun clearError() {
        scope.launch { lastError = null; publish() }
    }

    /** Diagnóstico: quantas amostras curtas estão na RAM agora. */
    override suspend fun loadedSampleCount(): Int = withContext(engine) { samples.size }

    // ---------------------------------------------------------------- internos

    private fun startPlay(sound: Sound) {
        if (!sound.isAvailable) {
            fail("\"${sound.name}\" está indisponível (arquivo não encontrado).")
            return
        }
        PlaybackPolicy.toEvict(voices.keys.toList(), maxSimultaneous).forEach { stopInternal(it) }
        if (PlaybackPolicy.useSoundPool(sound.durationMs, sound.sizeBytes)) startPool(sound) else startMedia(sound)
        requestFocusIfNeeded()
    }

    private fun ensureSample(sound: Sound): Sample? {
        samples[sound.uri]?.let { touch(sound.uri); return it }
        val id = try {
            when (sound.source) {
                SoundSource.BUNDLED -> context.assets.openFd(sound.uri).use { pool.load(it, 1) }
                SoundSource.IMPORTED -> pool.load(pathOf(sound.uri), 1)
            }
        } catch (e: IOException) {
            Log.w(TAG, "Falha ao abrir ${sound.uri}", e)
            0
        }
        if (id == 0) return null
        val s = Sample(id)
        samples[sound.uri] = s
        uriBySampleId[id] = sound.uri
        touch(sound.uri)
        return s
    }

    private fun touch(uri: String) {
        cache.touch(uri, ::isPinned).forEach(::unload)
    }

    private fun startPool(sound: Sound) {
        val sample = ensureSample(sound) ?: run {
            fail("Não foi possível carregar \"${sound.name}\".")
            return
        }
        if (sample.loaded) startStream(sound, sample) else voices[sound.id] = LoadingVoice(sound)
    }

    private fun onSampleLoaded(sampleId: Int, status: Int) {
        val uri = uriBySampleId[sampleId] ?: return
        val sample = samples[uri] ?: return
        val waiting = voices.values.filterIsInstance<LoadingVoice>().filter { it.sound.uri == uri }
        if (status != 0) {
            waiting.forEach { voices.remove(it.sound.id) }
            unload(uri)
            if (waiting.isNotEmpty()) fail("Arquivo de áudio inválido: \"${waiting.first().sound.name}\".")
            publish()
            return
        }
        sample.loaded = true
        waiting.forEach { startStream(it.sound, sample) }
        publish()
    }

    private fun startStream(sound: Sound, sample: Sample) {
        val streamId = pool.play(sample.sampleId, volume, volume, 1, if (repeat) -1 else 0, 1f)
        if (streamId == 0) {
            voices.remove(sound.id)
            fail("O sistema recusou tocar \"${sound.name}\".")
            return
        }
        val v = PoolVoice(
            sound, streamId, repeat,
            sound.durationMs ?: PlaybackPolicy.UNKNOWN_DURATION_FALLBACK_MS,
        )
        // Substitui no mesmo lugar: a ordem de início vale a partir do toque, não do fim do carregamento.
        // (LinkedHashMap mantém a posição original ao sobrescrever uma chave existente.)
        voices[sound.id] = v
        scheduleEnd(v)
    }

    private fun scheduleEnd(v: PoolVoice) {
        v.endJob?.cancel()
        if (v.looping) return
        v.endJob = scope.launch {
            delay(v.remainingMs + END_MARGIN_MS)
            if (voices[v.sound.id] === v) {
                voices.remove(v.sound.id)
                afterVoiceEnded()
            }
        }
    }

    private fun startMedia(sound: Sound) {
        val longOnes = voices.values.filterIsInstance<MediaVoice>()
        if (longOnes.size >= PlaybackPolicy.MAX_LONG_PLAYERS) stopInternal(longOnes.first().sound.id)

        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(attributes)
            when (sound.source) {
                SoundSource.BUNDLED -> context.assets.openFd(sound.uri).use {
                    mp.setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
                SoundSource.IMPORTED -> mp.setDataSource(pathOf(sound.uri))
            }
        } catch (e: Exception) {
            mp.release()
            fail("Não foi possível abrir \"${sound.name}\".")
            return
        }
        mp.isLooping = repeat
        mp.setVolume(volume, volume)
        val v = MediaVoice(sound, mp)
        voices[sound.id] = v
        mp.setOnPreparedListener {
            scope.launch {
                if (voices[sound.id] === v) {
                    v.prepared = true
                    if (!v.paused) mp.start()
                    publish()
                }
            }
        }
        mp.setOnCompletionListener {
            scope.launch {
                if (voices[sound.id] === v && !mp.isLooping) {
                    voices.remove(sound.id)
                    mp.release()
                    afterVoiceEnded()
                }
            }
        }
        mp.setOnErrorListener { _, what, extra ->
            scope.launch {
                if (voices[sound.id] === v) {
                    voices.remove(sound.id)
                    mp.release()
                    fail("Erro ao tocar \"${sound.name}\" ($what/$extra).")
                    afterVoiceEnded()
                }
            }
            true
        }
        mp.prepareAsync()
    }

    private fun pauseInternal(v: Voice) {
        if (v.paused) return
        when (v) {
            is PoolVoice -> {
                pool.pause(v.streamId)
                v.endJob?.cancel()
                v.remainingMs = (v.remainingMs - (SystemClock.elapsedRealtime() - v.resumedAt)).coerceAtLeast(0)
            }
            is MediaVoice -> if (v.prepared && v.player.isPlaying) v.player.pause()
            is LoadingVoice -> { stopInternal(v.sound.id); return }
        }
        v.paused = true
    }

    private fun resumeInternal(v: Voice) {
        if (!v.paused) return
        v.paused = false
        when (v) {
            is PoolVoice -> {
                pool.resume(v.streamId)
                v.resumedAt = SystemClock.elapsedRealtime()
                scheduleEnd(v)
            }
            is MediaVoice -> if (v.prepared) v.player.start()
            is LoadingVoice -> Unit
        }
        requestFocusIfNeeded()
    }

    private fun stopInternal(soundId: Long) {
        when (val v = voices.remove(soundId)) {
            is PoolVoice -> {
                v.endJob?.cancel()
                pool.stop(v.streamId)
            }
            is MediaVoice -> {
                runCatching { if (v.prepared) v.player.stop() }
                v.player.release()
            }
            is LoadingVoice, null -> Unit
        }
    }

    private fun afterVoiceEnded() {
        if (voices.isEmpty()) abandonFocus()
        cache.evictOverflow(::isPinned).forEach(::unload)
        publish()
    }

    private fun isPinned(uri: String): Boolean = voices.values.any { it.sound.uri == uri }

    private fun unload(uri: String) {
        cache.remove(uri)
        val s = samples.remove(uri) ?: return
        uriBySampleId.remove(s.sampleId)
        pool.unload(s.sampleId)
    }

    private fun applyVolume() {
        for (v in voices.values) {
            when (v) {
                is PoolVoice -> pool.setVolume(v.streamId, volume, volume)
                is MediaVoice -> v.player.setVolume(volume, volume)
                is LoadingVoice -> Unit
            }
        }
    }

    private fun requestFocusIfNeeded() {
        if (!useFocus || focusRequest != null || voices.isEmpty()) return
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    // Outro app (ex.: ligação) tomou o áudio: para tudo em vez de tocar por cima.
                    stopAll()
                }
            }
            .build()
        if (audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) focusRequest = req
    }

    private fun abandonFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun fail(message: String) {
        Log.w(TAG, message)
        lastError = message
    }

    private fun publish() {
        val active = LinkedHashMap<Long, PlayStatus>()
        for ((id, v) in voices) {
            active[id] = when {
                v is LoadingVoice -> PlayStatus.LOADING
                v.paused -> PlayStatus.PAUSED
                v is MediaVoice && !v.prepared -> PlayStatus.LOADING
                else -> PlayStatus.PLAYING
            }
        }
        _state.value = PlayerState(active, repeat, volume, lastError)
    }

    private fun pathOf(uri: String): String = Uri.parse(uri).path ?: uri

    private companion object {
        const val TAG = "SoundEngine"
        const val END_MARGIN_MS = 80L
        const val PRELOAD_LIMIT = 16
    }
}
