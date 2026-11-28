package com.umbral.codex

import com.umbral.codex.core.Blueprints
import com.umbral.codex.core.SystemPreset
import com.umbral.codex.data.BackupCodec
import com.umbral.codex.data.CodexRepository
import com.umbral.codex.data.DemoContent
import com.umbral.codex.data.ImportException
import com.umbral.codex.data.RestoreMode
import com.umbral.codex.data.SeedCatalogs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.repo(dir: java.io.File) =
        CodexRepository(dir, this, StandardTestDispatcher(testScheduler), debounceMs = 10, clock = { 1000L })

    @Test fun persistsAcrossInstances() = runTest {
        val dir = tmp.newFolder()
        val r1 = repo(dir); r1.load()
        assertEquals(8, r1.catalogs.value.size)
        val c = SystemPreset.CLASSIC.build("Aldric", SeedCatalogs.FANTASY)
        r1.upsert(c)
        r1.update(c.id) { it.copy(level = 4) }
        advanceUntilIdle(); r1.flush()

        val r2 = repo(dir); r2.load()
        assertEquals(1, r2.characters.value.size)
        assertEquals("Aldric", r2.characters.value[0].name)
        assertEquals(4, r2.characters.value[0].level)
        assertEquals(8, r2.catalogs.value.size)

        r2.delete(c.id); advanceUntilIdle(); r2.flush()
        val r3 = repo(dir); r3.load()
        assertTrue(r3.characters.value.isEmpty())
    }

    @Test fun backupRoundTripAndImageTransfer() = runTest {
        val src = repo(tmp.newFolder()); src.load()
        val demo = DemoContent.build(src.catalogs.value, 5000)
        assertEquals(5, demo.size)
        demo.forEach { src.upsert(it) }
        src.saveImage("img1", byteArrayOf(1, 2, 3))
        src.update(demo[0].id) { it.copy(portraitId = "img1") }
        val json = src.exportBackup()

        val dst = repo(tmp.newFolder()); dst.load()
        val summary = dst.import(json, RestoreMode.REPLACE)
        assertEquals(5, summary.characters); assertEquals(1, summary.images)
        assertEquals(5, dst.characters.value.size)
        assertTrue(dst.imageFile("img1").exists())
        assertEquals(demo[0].attributes, dst.character(demo[0].id)!!.attributes)
    }

    @Test fun singleCharacterImportNeverOverwrites() = runTest {
        val r = repo(tmp.newFolder()); r.load()
        val c = SystemPreset.SUPERNATURAL.build("Lysandra")
        r.upsert(c)
        val exported = r.exportCharacter(c.id)!!
        r.import(exported)
        assertEquals(2, r.characters.value.size)
        assertNotEquals(r.characters.value[0].id, r.characters.value[1].id)
    }

    @Test fun invalidFilesGiveClearErrors() {
        listOf("nada", "{\"format\":\"outra-coisa\"}").forEach {
            try { BackupCodec.decode(it); throw AssertionError() } catch (e: ImportException) { assertNotNull(e.message) }
        }
    }

    @Test fun instantiateRemapsSkillAttributes() {
        val tpl = SeedCatalogs.all().flatMap { it.templates }.first()
        val c = Blueprints.instantiate(tpl.blueprint, 1, "Novo")
        val attrIds = c.attributes.map { it.id }.toSet()
        assertTrue(c.skills.all { it.attributeId == null || it.attributeId in attrIds })
        assertTrue(c.attributes.none { a -> tpl.blueprint.attributes.any { it.id == a.id } })
    }

    @Test fun everySeedCatalogIsComplete() {
        SeedCatalogs.all().forEach { cat ->
            assertTrue(cat.name, cat.setting.isNotBlank() && cat.races.isNotEmpty() && cat.classes.isNotEmpty())
            assertTrue(cat.name, cat.powers.isNotEmpty() && cat.templates.isNotEmpty() && cat.lore.isNotEmpty())
        }
    }
}
