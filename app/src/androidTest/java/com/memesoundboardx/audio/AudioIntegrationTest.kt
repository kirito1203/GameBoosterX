package com.memesoundboardx.audio

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.memesoundboardx.core.audio.PlaybackPolicy
import com.memesoundboardx.core.audio.SoundEngine
import com.memesoundboardx.data.importer.ImportedFileStore
import com.memesoundboardx.data.importer.SafAudioImporter
import com.memesoundboardx.data.local.db.AppDatabase
import com.memesoundboardx.data.local.db.CategoryEntity
import com.memesoundboardx.data.manifest.SoundManifestParser
import com.memesoundboardx.data.repository.SoundRepositoryImpl
import com.memesoundboardx.data.repository.toDomain
import com.memesoundboardx.domain.model.AppSettings
import com.memesoundboardx.domain.model.PlayStatus
import com.memesoundboardx.domain.model.PlayerState
import com.memesoundboardx.domain.model.Sound
import com.memesoundboardx.domain.model.SoundLicense
import com.memesoundboardx.domain.model.SoundSource
import com.memesoundboardx.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

private class FakeSettings : SettingsRepository {
    val flow = MutableStateFlow(AppSettings())
    override val settings: Flow<AppSettings> = flow
    override suspend fun setMasterVolume(volume: Float) { flow.value = flow.value.copy(masterVolume = AppSettings.clampVolume(volume)) }
    override suspend fun setMaxSimultaneous(count: Int) { flow.value = flow.value.copy(maxSimultaneous = AppSettings.clampSimultaneous(count)) }
    override suspend fun setRequestAudioFocus(enabled: Boolean) { flow.value = flow.value.copy(requestAudioFocus = enabled) }
    override suspend fun setOnboardingDone(done: Boolean) = Unit
}

/** Gera um WAV PCM 16-bit mono válido (tom senoidal). */
fun writeWav(file: File, seconds: Double, freq: Double = 440.0, rate: Int = 22050) {
    val n = (seconds * rate).toInt()
    val data = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
    for (i in 0 until n) data.putShort((Math.sin(2 * Math.PI * freq * i / rate) * 12000).toInt().toShort())
    val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
    h.put("RIFF".toByteArray()).putInt(36 + n * 2).put("WAVE".toByteArray())
    h.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
    h.put("data".toByteArray()).putInt(n * 2)
    file.outputStream().use { it.write(h.array()); it.write(data.array()) }
}

@RunWith(AndroidJUnit4::class)
class AudioIntegrationTest {

    private val ctx: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var scope: CoroutineScope
    private lateinit var settings: FakeSettings
    private lateinit var engine: SoundEngine
    private lateinit var bundled: List<Sound>

    @Before fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        settings = FakeSettings()
        engine = SoundEngine(ctx, settings, scope)
        val manifest = SoundManifestParser.parse(ctx.assets.open("sounds.json").bufferedReader().readText())
        bundled = manifest.sounds.mapIndexed { i, s ->
            Sound(
                id = i + 1L, key = "bundled:${s.id}", name = s.name, categoryId = s.categoryId,
                source = SoundSource.BUNDLED, uri = "sounds/${s.file}", durationMs = s.durationMs, sizeBytes = null,
                isFavorite = false, license = s.license, author = s.author, sourceUrl = null, tags = s.tags,
                addedAt = 0, isAvailable = true,
            )
        }
    }

    @After fun tearDown() {
        engine.stopAll()
        Thread.sleep(200)
        scope.cancel()
    }

    private fun bundledById(id: String) = bundled.first { it.key == "bundled:$id" }

    private suspend fun await(timeoutMs: Long = 4_000, what: String, p: (PlayerState) -> Boolean): PlayerState =
        try {
            withTimeout(timeoutMs) { engine.state.first(p) }
        } catch (e: Exception) {
            throw AssertionError("timeout esperando: $what. estado=${engine.state.value}")
        }

    // ------------------------------------------------------------------ reprodução

    @Test fun shortBundledSoundPlaysAndFinishesByItself() = runBlocking<Unit> {
        val bip = bundledById("bip")
        engine.play(bip)
        await(what = "bip tocando") { it.statusOf(bip.id) == PlayStatus.PLAYING }
        await(timeoutMs = (bip.durationMs ?: 0) + 3_000, what = "bip terminar") { it.statusOf(bip.id) == null }
        assertNull(engine.state.value.lastError)
    }

    @Test fun pauseResumeStop() = runBlocking<Unit> {
        val s = bundledById("aplausos")
        assertTrue(PlaybackPolicy.useSoundPool(s.durationMs, null))
        engine.toggle(s)
        await(what = "tocando") { it.statusOf(s.id) == PlayStatus.PLAYING }
        engine.toggle(s)
        await(what = "pausado") { it.statusOf(s.id) == PlayStatus.PAUSED }
        delay((s.durationMs ?: 3000) + 500)
        assertEquals("pausado não pode terminar sozinho", PlayStatus.PAUSED, engine.state.value.statusOf(s.id))
        engine.toggle(s)
        await(what = "retomado") { it.statusOf(s.id) == PlayStatus.PLAYING }
        engine.stop(s.id)
        await(what = "parado") { it.statusOf(s.id) == null }
    }

    @Test fun longSoundStreamsWithMediaPlayer() = runBlocking<Unit> {
        val long = bundled.first { (it.durationMs ?: 0) > PlaybackPolicy.SHORT_SOUND_MS }
        engine.play(long)
        await(what = "longo tocando (${long.name})") { it.statusOf(long.id) == PlayStatus.PLAYING }
        engine.toggle(long)
        await(what = "longo pausado") { it.statusOf(long.id) == PlayStatus.PAUSED }
        engine.stop(long.id)
        await(what = "longo parado") { it.statusOf(long.id) == null }
    }

    @Test fun tappingTwiceDoesNotDuplicate() = runBlocking<Unit> {
        val s = bundledById("aplausos_fortes")
        engine.toggle(s)
        await(what = "tocando") { it.statusOf(s.id) == PlayStatus.PLAYING }
        engine.play(s) // reinicia, não cria um segundo
        delay(300)
        assertEquals(1, engine.state.value.active.size)
    }

    @Test fun respectsMaxSimultaneous() = runBlocking<Unit> {
        settings.setMaxSimultaneous(2)
        delay(200)
        val three = listOf("drone_sombrio", "vento_uivante", "chuva").map(::bundledById)
        three.forEach { engine.play(it); delay(150) }
        await(what = "dois ativos") { st -> st.active.size == 2 && three.drop(1).all { st.statusOf(it.id) != null } }
        assertNull("o mais antigo deve ter sido interrompido", engine.state.value.statusOf(three[0].id))
    }

    @Test fun stopAllStopsEverything() = runBlocking<Unit> {
        listOf("drone_sombrio", "chuva", "fogueira").map(::bundledById).forEach { engine.play(it) }
        await(what = "três ativos") { it.active.size == 3 }
        engine.stopAll()
        await(what = "nenhum ativo") { it.active.isEmpty() }
    }

    @Test fun volumeFollowsSettings() = runBlocking<Unit> {
        settings.setMasterVolume(0.25f)
        await(what = "volume 0.25") { it.volume == 0.25f }
        val s = bundledById("drone_grave")
        engine.play(s)
        await(what = "tocando") { it.statusOf(s.id) == PlayStatus.PLAYING }
        settings.setMasterVolume(1f) // aplicado ao som em andamento
        await(what = "volume 1.0") { it.volume == 1f }
        assertEquals(PlayStatus.PLAYING, engine.state.value.statusOf(s.id))
    }

    @Test fun repeatKeepsShortSoundAlive() = runBlocking<Unit> {
        engine.setRepeat(true)
        val bip = bundledById("bip")
        engine.play(bip)
        await(what = "tocando") { it.statusOf(bip.id) == PlayStatus.PLAYING }
        delay(1_500) // bip tem ~0,2 s; com repetição continua ativo
        assertEquals(PlayStatus.PLAYING, engine.state.value.statusOf(bip.id))
        engine.setRepeat(false)
        await(timeoutMs = 3_000, what = "terminar após desligar repetição") { it.statusOf(bip.id) == null }
    }

    @Test fun recoversAfterBrokenSound() = runBlocking<Unit> {
        val broken = bundledById("bip").copy(id = 9_999, uri = "sounds/nao_existe.ogg")
        engine.play(broken)
        await(what = "erro reportado") { it.lastError != null }
        assertNull(engine.state.value.statusOf(broken.id))
        engine.clearError()
        val ok = bundledById("ding")
        engine.play(ok)
        await(what = "som válido toca depois do erro") { it.statusOf(ok.id) == PlayStatus.PLAYING }
    }

    @Test fun unavailableSoundIsRefused() = runBlocking<Unit> {
        val s = bundledById("bip").copy(isAvailable = false)
        engine.play(s)
        await(what = "erro") { it.lastError != null }
        assertTrue(engine.state.value.active.isEmpty())
    }

    // ------------------------------------------------------------------ memória

    @Test fun sampleCacheNeverExceedsLimit() = runBlocking<Unit> {
        val short = bundled.filter { PlaybackPolicy.useSoundPool(it.durationMs, null) }
        assertTrue("precisa de 60+ sons curtos, tem ${short.size}", short.size >= 60)
        short.take(80).chunked(16).forEach { engine.preload(it); delay(400) }
        val loaded = engine.loadedSampleCount()
        assertTrue("carregados=$loaded", loaded in 1..PlaybackPolicy.SAMPLE_CACHE_SIZE)
        engine.trimMemory()
        delay(300)
        assertEquals(0, engine.loadedSampleCount())
    }

    // ------------------------------------------------------------------ importação + persistência

    private fun newDbFile(): File = ctx.getDatabasePath("import-test-${System.nanoTime()}.db")

    private fun openDb(file: File): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, file.absolutePath).build()

    private suspend fun seedCategories(db: AppDatabase) {
        db.categoryDao().upsertAll(
            listOf("risadas_reacoes", "sons_aleatorios", "memes_classicos").mapIndexed { i, id ->
                CategoryEntity(id, id, false, i, 10)
            },
        )
    }

    @Test fun importsValidatesDeduplicatesPersistsAndDeletes() = runBlocking<Unit> {
        val store = ImportedFileStore(ctx)
        val dbFile = newDbFile()
        var db = openDb(dbFile)
        seedCategories(db)
        val importer = SafAudioImporter(ctx, db.soundDao(), db.categoryDao(), store, Dispatchers.IO)

        val dir = File(ctx.cacheDir, "import-src").apply { deleteRecursively(); mkdirs() }
        val wav = File(dir, "kkkk_risada_teste.wav").also { writeWav(it, 1.5) }
        val ogg = File(dir, "vine boom.ogg").also { f -> ctx.assets.open("sounds/bonk.ogg").use { i -> f.outputStream().use { i.copyTo(it) } } }
        val fakeMp3 = File(dir, "falso.mp3").also { it.writeText("isto não é áudio") }
        val txt = File(dir, "notas.txt").also { it.writeText("x") }
        val empty = File(dir, "vazio.wav").also { it.writeBytes(ByteArray(0)) }
        val uris = listOf(wav, ogg, fakeMp3, txt, empty).map { Uri.fromFile(it).toString() }

        val r1 = importer.import(uris)
        assertEquals("importados: ${r1.imported} falhas: ${r1.failures}", 2, r1.imported.size)
        assertEquals(3, r1.failures.size)
        assertTrue(r1.failures.any { it.fileName == "falso.mp3" })
        assertTrue(r1.failures.any { it.fileName == "notas.txt" && it.reason.contains("formato") })

        val rows = db.soundDao().getBySource("IMPORTED")
        val risada = rows.first { it.name == "kkkk risada teste" }
        assertEquals("categoria automática", "risadas_reacoes", risada.categoryId)
        assertEquals("memes_classicos", rows.first { it.name == "vine boom" }.categoryId)
        assertTrue("duração medida: ${risada.durationMs}", (risada.durationMs ?: 0) in 1_400..1_600)
        assertEquals(wav.length(), risada.sizeBytes)
        assertEquals(SoundLicense.USER_FILE.name, risada.license)
        val copy = store.fileFor(risada.uri)
        assertNotNull(copy); assertTrue(copy!!.exists())

        // O original continua intacto
        assertTrue(wav.exists())

        // Mesmo conteúdo de novo -> duplicado, sem nova linha
        val r2 = importer.import(listOf(Uri.fromFile(wav).toString()))
        assertEquals(1, r2.duplicates.size)
        assertEquals(2, db.soundDao().getBySource("IMPORTED").size)

        // "Fechar o app": fecha o banco, apaga a origem, reabre
        db.close()
        wav.delete()
        db = openDb(dbFile)
        val reopened = db.soundDao().getBySource("IMPORTED")
        assertEquals("persistiu após reabrir", 2, reopened.size)
        val persisted = reopened.first { it.name == "kkkk risada teste" }
        assertTrue("cópia interna sobrevive à remoção do original", store.exists(persisted.uri))

        // O som importado toca pelo motor
        val sound = persisted.toDomain()
        engine.play(sound)
        await(what = "importado tocando") { it.statusOf(sound.id) == PlayStatus.PLAYING }
        engine.stop(sound.id)

        // Excluir remove a linha e a cópia
        SoundRepositoryImpl(db.soundDao(), store).delete(persisted.id)
        assertNull(db.soundDao().getById(persisted.id))
        assertFalse(store.exists(persisted.uri))

        db.close()
        dbFile.delete()
        dir.deleteRecursively()
    }
}
