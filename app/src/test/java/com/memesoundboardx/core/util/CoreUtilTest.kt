package com.memesoundboardx.core.util

import com.memesoundboardx.domain.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextNormalizerTest {

    @Test fun removesAccentsAndLowercases() {
        assertEquals("acao epica", TextNormalizer.normalize("Ação  ÉPICA"))
        assertEquals("pao de queijo", TextNormalizer.normalize("  Pão de Queijo "))
        assertEquals("cancao", TextNormalizer.normalize("CANÇÃO"))
    }

    @Test fun emptyStaysEmpty() {
        assertEquals("", TextNormalizer.normalize("   "))
    }

    @Test fun escapesLikeWildcards() {
        assertEquals("100\\%", TextNormalizer.escapeLike("100%"))
        assertEquals("a\\_b", TextNormalizer.escapeLike("a_b"))
        assertEquals("c\\\\d", TextNormalizer.escapeLike("c\\d"))
        assertEquals("normal", TextNormalizer.escapeLike("normal"))
    }

    @Test fun searchIndexIncludesTags() {
        assertEquals("bruh reacao meme", TextNormalizer.searchIndex("Bruh", listOf("Reação", "meme")))
    }
}

class FormattersTest {

    @Test fun duration() {
        assertEquals("0:00", Formatters.duration(0))
        assertEquals("0:03", Formatters.duration(3_500))
        assertEquals("1:15", Formatters.duration(75_000))
        assertEquals("--:--", Formatters.duration(null))
        assertEquals("--:--", Formatters.duration(-1))
    }

    @Test fun size() {
        assertEquals("512 B", Formatters.size(512))
        assertEquals("1,5 KB", Formatters.size(1536))
        assertEquals("1,2 MB", Formatters.size(1_258_291))
        assertEquals("—", Formatters.size(null))
    }
}

class SupportedFormatsTest {

    @Test fun acceptsPlannedExtensionsCaseInsensitive() {
        listOf("a.mp3", "b.WAV", "c.ogg", "d.m4a", "e.AAC").forEach {
            assertTrue(it, SupportedFormats.isSupportedFileName(it))
        }
    }

    @Test fun rejectsOthers() {
        listOf("a.flac", "b.txt", "noext", ".mp3", "trailing.").forEach {
            assertFalse(it, SupportedFormats.isSupportedFileName(it))
        }
    }

    @Test fun extension() {
        assertEquals("mp3", SupportedFormats.extensionOf("meu.som.MP3"))
        assertNull(SupportedFormats.extensionOf("semextensao"))
    }

    @Test fun mime() {
        assertTrue(SupportedFormats.isSupportedMime("audio/mpeg"))
        assertTrue(SupportedFormats.isSupportedMime("AUDIO/OGG"))
        assertFalse(SupportedFormats.isSupportedMime("video/mp4"))
        assertFalse(SupportedFormats.isSupportedMime(null))
    }
}

class AppSettingsTest {

    @Test fun clampsVolume() {
        assertEquals(0f, AppSettings.clampVolume(-0.5f))
        assertEquals(1f, AppSettings.clampVolume(3f))
        assertEquals(0.4f, AppSettings.clampVolume(0.4f))
        assertEquals(AppSettings.DEFAULT_VOLUME, AppSettings.clampVolume(Float.NaN))
    }

    @Test fun clampsSimultaneous() {
        assertEquals(1, AppSettings.clampSimultaneous(0))
        assertEquals(8, AppSettings.clampSimultaneous(50))
        assertEquals(4, AppSettings.clampSimultaneous(4))
    }

    @Test fun defaultsDoNotStealAudioFocus() {
        assertFalse(AppSettings().requestAudioFocus)
    }
}
