package com.memesoundboardx.domain.usecase

import com.memesoundboardx.core.util.ImportNaming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryAutoMatcherTest {

    @Test fun matchesByKeyword() {
        assertEquals("risadas_reacoes", CategoryAutoMatcher.match("risada_alta.mp3"))
        assertEquals("risadas_reacoes", CategoryAutoMatcher.match("KKKKKK.wav"))
        assertEquals("memes_classicos", CategoryAutoMatcher.match("Vine Boom.mp3"))
        assertEquals("memes_brasileiros", CategoryAutoMatcher.match("faustão-errou.m4a"))
        assertEquals("suspense_terror", CategoryAutoMatcher.match("jumpscare fnaf.ogg"))
        assertEquals("anime_poderes", CategoryAutoMatcher.match("kamehameha.mp3"))
        assertEquals("games_roblox", CategoryAutoMatcher.match("roblox-death.mp3"))
        assertEquals("vinhetas_musicas", CategoryAutoMatcher.match("intro_canal.mp3"))
        assertEquals("impactos_comicos", CategoryAutoMatcher.match("explosao_grande.wav"))
    }

    @Test fun shortKeywordsNeedWholeWord() {
        // "sus" (meme) não pode capturar "susto" (terror)
        assertEquals("suspense_terror", CategoryAutoMatcher.match("susto.mp3"))
        assertEquals("memes_classicos", CategoryAutoMatcher.match("sus.mp3"))
        // "win" não captura "window" sozinho
        assertEquals(CategoryAutoMatcher.FALLBACK, CategoryAutoMatcher.match("window cleaning.mp3"))
    }

    @Test fun ignoresAccentsAndCase() {
        assertEquals("suspense_terror", CategoryAutoMatcher.match("TERROR_Assombração.mp3"))
    }

    @Test fun fallbackWhenNoKeyword() {
        assertEquals(CategoryAutoMatcher.FALLBACK, CategoryAutoMatcher.match("gravacao 2024-05-01.m4a"))
        assertEquals(CategoryAutoMatcher.FALLBACK, CategoryAutoMatcher.match(""))
    }
}

class ImportNamingTest {

    @Test fun cleansDisplayName() {
        assertEquals("kkk risada ALTA (1)", ImportNaming.displayName("kkk_risada-ALTA (1).mp3"))
        assertEquals("som", ImportNaming.displayName("som.wav"))
        assertEquals("Áudio importado", ImportNaming.displayName(".mp3"))
        assertEquals("sem extensao", ImportNaming.displayName("sem_extensao"))
    }

    @Test fun limitsLength() {
        val n = ImportNaming.displayName("a".repeat(100) + ".mp3")
        assertTrue(n.length <= ImportNaming.MAX_NAME)
    }

    @Test fun keepsMinusBetweenSpaces() {
        assertEquals("a - b", ImportNaming.displayName("a - b.mp3"))
    }

    @Test fun extensionFromNameOrMime() {
        assertEquals("mp3", ImportNaming.extensionFor("x.MP3", null))
        assertEquals("ogg", ImportNaming.extensionFor("document:1234", "application/ogg"))
        assertEquals("m4a", ImportNaming.extensionFor("audio", "audio/mp4"))
        assertEquals("wav", ImportNaming.extensionFor("x.wav", "application/octet-stream"))
        assertNull(ImportNaming.extensionFor("x.flac", "audio/flac"))
        assertNull(ImportNaming.extensionFor("x.txt", "text/plain"))
    }
}
