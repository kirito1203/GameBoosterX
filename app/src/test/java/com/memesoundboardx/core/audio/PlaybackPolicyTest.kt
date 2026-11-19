package com.memesoundboardx.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackPolicyTest {

    @Test fun shortSoundsGoToSoundPool() {
        assertTrue(PlaybackPolicy.useSoundPool(800, 20_000))
        assertTrue(PlaybackPolicy.useSoundPool(5_000, 500_000))
        assertTrue(PlaybackPolicy.useSoundPool(null, null))
    }

    @Test fun longOrHeavySoundsStream() {
        assertFalse(PlaybackPolicy.useSoundPool(5_001, 10_000))
        assertFalse(PlaybackPolicy.useSoundPool(1_000, 2_000_000))
        assertFalse(PlaybackPolicy.useSoundPool(null, 1_000_001))
    }

    @Test fun evictsOldestToFitNewSound() {
        assertEquals(emptyList<Int>(), PlaybackPolicy.toEvict(listOf(1, 2), 3))
        assertEquals(listOf(1), PlaybackPolicy.toEvict(listOf(1, 2, 3), 3))
        assertEquals(listOf(1, 2, 3), PlaybackPolicy.toEvict(listOf(1, 2, 3), 1))
        assertEquals(listOf(1, 2), PlaybackPolicy.toEvict(listOf(1, 2, 3, 4, 5, 6, 7, 8), 7))
    }
}

class LruKeyCacheTest {

    @Test fun evictsLeastRecentlyUsedBeyondCapacity() {
        val c = LruKeyCache<String>(3)
        assertTrue(c.touch("a").isEmpty())
        c.touch("b"); c.touch("c")
        c.touch("a") // "a" volta a ser recente
        assertEquals(listOf("b"), c.touch("d"))
        assertEquals(listOf("c", "a", "d"), c.keys())
    }

    @Test fun neverEvictsPinnedKeys() {
        val c = LruKeyCache<String>(2)
        c.touch("tocando"); c.touch("b")
        val evicted = c.touch("c") { it == "tocando" }
        assertEquals(listOf("b"), evicted)
        assertTrue(c.contains("tocando"))
    }

    @Test fun mayExceedCapacityWhenEverythingIsPinnedThenShrinks() {
        val c = LruKeyCache<String>(1)
        c.touch("a"); c.touch("b") { true }
        assertEquals(2, c.size)
        assertEquals(listOf("a"), c.evictOverflow { false })
        assertEquals(1, c.size)
    }

    @Test fun hundredsOfSoundsStayWithinCapacity() {
        val c = LruKeyCache<Int>(PlaybackPolicy.SAMPLE_CACHE_SIZE)
        var evicted = 0
        repeat(350) { evicted += c.touch(it).size }
        assertEquals(PlaybackPolicy.SAMPLE_CACHE_SIZE, c.size)
        assertEquals(350 - PlaybackPolicy.SAMPLE_CACHE_SIZE, evicted)
    }

    @Test fun clearUnpinned() {
        val c = LruKeyCache<String>(5)
        listOf("a", "b", "c").forEach { c.touch(it) }
        assertEquals(listOf("a", "c"), c.clearUnpinned { it == "b" })
        assertEquals(listOf("b"), c.keys())
    }
}
