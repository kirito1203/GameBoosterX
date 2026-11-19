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

    @Test fun realBundledManifestHasNineCategoriesAndTarget350() {
        val json = java.io.File("src/main/assets/sounds.json").readText()
        val r = SoundManifestParser.parse(json)
        assertTrue(r.errors.toString(), r.errors.isEmpty())
        assertEquals(9, r.categories.size)
        assertEquals(350, r.categories.sumOf { it.targetCount ?: 0 })
    }
}
