package com.memesoundboardx.data.manifest

import com.memesoundboardx.domain.model.SoundLicense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundManifestParserTest {

    private fun manifest(sounds: String, categories: String = """[{"id":"impactos","name":"Impactos","targetCount":35,"order":1}]""") =
        """{"schemaVersion":1,"categories":$categories,"sounds":$sounds}"""

    private val valid = """{"id":"soco_01","file":"soco_01.ogg","name":"Soco","categoryId":"impactos","license":"CC0","tags":["soco","luta"]}"""

    @Test fun parsesValidEntry() {
        val r = SoundManifestParser.parse(manifest("[$valid]"))
        assertTrue(r.errors.toString(), r.errors.isEmpty())
        val s = r.sounds.single()
        assertEquals("soco_01", s.id)
        assertEquals(SoundLicense.CC0, s.license)
        assertEquals(listOf("soco", "luta"), s.tags)
        assertEquals(35, r.categories.single().targetCount)
    }

    @Test fun emptyLibraryIsValid() {
        val r = SoundManifestParser.parse(manifest("[]"))
        assertTrue(r.errors.isEmpty())
        assertTrue(r.sounds.isEmpty())
    }

    @Test fun rejectsMissingLicense() {
        val r = SoundManifestParser.parse(manifest("""[{"id":"a","file":"a.ogg","name":"A","categoryId":"impactos"}]"""))
        assertTrue(r.sounds.isEmpty())
        assertEquals(1, r.errors.size)
    }

    @Test fun rejectsUserFileLicenseForBundled() {
        val r = SoundManifestParser.parse(manifest("""[{"id":"a","file":"a.ogg","name":"A","categoryId":"impactos","license":"USER_FILE"}]"""))
        assertTrue(r.sounds.isEmpty())
    }

    @Test fun rejectsUnknownCategory() {
        val r = SoundManifestParser.parse(manifest("""[{"id":"a","file":"a.ogg","name":"A","categoryId":"nao_existe","license":"CC0"}]"""))
        assertTrue(r.sounds.isEmpty())
        assertTrue(r.errors.single().contains("categoria inexistente"))
    }

    @Test fun rejectsPathTraversalAndBadFormat() {
        val r = SoundManifestParser.parse(
            manifest(
                """[
                {"id":"a","file":"../segredo.ogg","name":"A","categoryId":"impactos","license":"CC0"},
                {"id":"b","file":"/abs.ogg","name":"B","categoryId":"impactos","license":"CC0"},
                {"id":"c","file":"c.flac","name":"C","categoryId":"impactos","license":"CC0"}
                ]""",
            ),
        )
        assertTrue(r.sounds.isEmpty())
        assertEquals(3, r.errors.size)
    }

    @Test fun rejectsDuplicateIdButKeepsFirst() {
        val r = SoundManifestParser.parse(manifest("[$valid,$valid]"))
        assertEquals(1, r.sounds.size)
        assertEquals(1, r.errors.size)
    }

    @Test fun rejectsInvalidIdCharacters() {
        val r = SoundManifestParser.parse(manifest("""[{"id":"Com Espaço","file":"a.ogg","name":"A","categoryId":"impactos","license":"CC0"}]"""))
        assertTrue(r.sounds.isEmpty())
    }

    @Test fun wrongSchemaRejectsEverything() {
        val r = SoundManifestParser.parse("""{"schemaVersion":2,"categories":[],"sounds":[]}""")
        assertTrue(r.categories.isEmpty())
        assertEquals(1, r.errors.size)
    }

    @Test fun brokenJsonDoesNotCrash() {
        val r = SoundManifestParser.parse("{ isto não é json")
        assertEquals(1, r.errors.size)
    }

    @Test fun parsesOptionalDuration() {
        val r = SoundManifestParser.parse(manifest("""[{"id":"a","file":"a.ogg","name":"A","categoryId":"impactos","license":"ORIGINAL","durationMs":1234}]"""))
        assertEquals(1234L, r.sounds.single().durationMs)
        assertEquals(null, SoundManifestParser.parse(manifest("[$valid]")).sounds.single().durationMs)
    }

    @Test fun parsesPendingItems() {
        val json = """{"schemaVersion":1,"categories":[{"id":"memes","name":"Memes","order":1}],"sounds":[],
            "pending":[
              {"id":"vine_boom","name":"Vine Boom","categoryId":"memes","reason":"protegido","hint":"batida grave"},
              {"id":"vine_boom","name":"Repetido","categoryId":"memes"},
              {"id":"x","name":"Sem categoria","categoryId":"nao_existe"},
              {"id":"semmotivo","name":"Sem motivo","categoryId":"memes"}
            ]}"""
        val r = SoundManifestParser.parse(json)
        assertEquals(listOf("vine_boom", "semmotivo"), r.pending.map { it.id })
        assertEquals("protegido", r.pending[0].reason)
        assertEquals("batida grave", r.pending[0].hint)
        assertEquals("sem fonte autorizada", r.pending[1].reason)
        assertEquals(2, r.errors.size)
    }

    @Test fun acceptsCcBySa() {
        val r = SoundManifestParser.parse(manifest("""[{"id":"a","file":"a.ogg","name":"A","categoryId":"impactos","license":"CC_BY_SA","author":"Fulano","sourceUrl":"https://commons.wikimedia.org/wiki/File:A.ogg"}]"""))
        assertEquals(SoundLicense.CC_BY_SA, r.sounds.single().license)
        assertEquals("Fulano", r.sounds.single().author)
    }

    @Test fun realBundledManifestIsValidAndEveryFileExists() {
        val json = java.io.File("src/main/assets/sounds.json").readText()
        val r = SoundManifestParser.parse(json)
        assertTrue(r.errors.toString(), r.errors.isEmpty())
        assertEquals(9, r.categories.size)
        assertEquals(350, r.categories.sumOf { it.targetCount ?: 0 })
        assertTrue("esperado 200+ sons, veio ${r.sounds.size}", r.sounds.size >= 200)
        for (s in r.sounds) {
            val f = java.io.File("src/main/assets/sounds/" + s.file)
            assertTrue("arquivo ausente: ${s.file}", f.isFile)
            assertTrue("arquivo pequeno demais (vazio?): ${s.file}", f.length() > 1_000)
            assertTrue("sem duração: ${s.id}", (s.durationMs ?: 0) > 0)
            if (s.file.startsWith("x_")) {
                // Fonte externa: precisa de link de origem e licença que não seja ORIGINAL
                assertTrue("sem link de origem: ${s.id}", !s.sourceUrl.isNullOrBlank())
                assertTrue("licença de ${s.id}", s.license != SoundLicense.ORIGINAL)
                if (s.license.requiresAttribution) assertTrue("CC BY sem autor: ${s.id}", !s.author.isNullOrBlank())
            } else {
                assertEquals("licença de ${s.id}", SoundLicense.ORIGINAL, s.license)
            }
        }
    }
}
