package com.gamebooster.x.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LatencyParserTest {

    private fun latencyOutput(presents: List<Long>): String =
        "16666666\n" + presents.joinToString("\n") { "${it - 5}\t$it\t${it - 2}" } +
            "\n0\t0\t0\n9223372036854775807\t9223372036854775807\t9223372036854775807\n"

    @Test fun parsesAndIgnoresPending() {
        val times = LatencyParser.presentTimes(latencyOutput(listOf(300L, 100L, 200L)))
        assertEquals(listOf(100L, 200L, 300L), times)
    }

    @Test fun emptyOrPeriodOnly() {
        assertTrue(LatencyParser.presentTimes("").isEmpty())
        assertTrue(LatencyParser.presentTimes("16666666\n").isEmpty())
    }

    @Test fun picksSurfaceViewLayer() {
        val list = """
            com.android.systemui.ImageWallpaper#0
            ActivityRecord{abc u0 com.mojang.minecraftpe/.MainActivity t12}#11
            com.mojang.minecraftpe/com.mojang.minecraftpe.MainActivity#12
            Background for SurfaceView[com.mojang.minecraftpe/com.mojang.minecraftpe.MainActivity]#13
            SurfaceView[com.mojang.minecraftpe/com.mojang.minecraftpe.MainActivity](BLAST)#14
        """.trimIndent()
        assertEquals(
            "SurfaceView[com.mojang.minecraftpe/com.mojang.minecraftpe.MainActivity](BLAST)#14",
            LatencyParser.pickLayer(list, "com.mojang.minecraftpe"),
        )
        assertNull(LatencyParser.pickLayer(list, "com.roblox.client"))
    }

    @Test fun fallsBackToActivityLayer() {
        val list = "com.roblox.client/com.roblox.client.ActivityNativeMain#5\nActivityRecord{x com.roblox.client}#4"
        assertEquals("com.roblox.client/com.roblox.client.ActivityNativeMain#5", LatencyParser.pickLayer(list, "com.roblox.client"))
    }
}

class FpsTest {
    private val frame60 = 16_666_667L

    @Test fun instantFpsAt60() {
        val times = (0 until 128).map { it * frame60 }
        assertEquals(60, FpsCalculator.instantFps(times))
    }

    @Test fun instantFpsShortHistory() {
        val times = (0 until 16).map { it * 33_333_333L }  // 30 FPS, ~0,5 s
        assertEquals(30, FpsCalculator.instantFps(times))
        assertNull(FpsCalculator.instantFps(listOf(5L)))
    }

    @Test fun accumulatorNeedsEnoughFrames() {
        val acc = FrameAccumulator()
        acc.add((0 until 50).map { it * frame60 })
        assertNull(acc.averageFps())
    }

    @Test fun accumulatorAvoidsDuplicatesAndComputesLows() {
        val acc = FrameAccumulator()
        val times = ArrayList<Long>()
        var t = 0L
        repeat(300) { i ->
            t += if (i % 100 == 99) 100_000_000L else frame60  // 3 quadros de 100 ms (10 FPS)
            times += t
        }
        acc.add(times.subList(0, 200))
        acc.add(times.subList(100, 300)) // sobreposição: não pode contar duas vezes
        assertEquals(299, acc.frameCount)
        val avg = acc.averageFps()
        assertNotNull(avg)
        assertTrue(avg!! in 55f..60f)
        assertEquals(10f, acc.onePercentLowFps()!!, 0.5f)
    }

    @Test fun pauseGapsIgnored() {
        val acc = FrameAccumulator()
        val first = (1..150).map { it * frame60 }
        val second = (1..150).map { 10_000_000_000L + it * frame60 }
        acc.add(first); acc.add(second)
        assertTrue(acc.onePercentLowFps()!! > 55f)
    }
}

class SessionsTest {
    private val r = GameSessionRecord("com.mojang.minecraftpe", 1000L, 3_600_000L, 41.5f, 52.3f, 28.1f)

    @Test fun roundTrip() {
        assertEquals(r, SessionCodec.decode(SessionCodec.encode(r)))
        val noData = r.copy(maxBatteryCelsius = null, averageFps = null, onePercentLowFps = null)
        assertEquals(noData, SessionCodec.decode(SessionCodec.encode(noData)))
    }

    @Test fun rejectsGarbage() {
        assertNull(SessionCodec.decode("lixo"))
        assertNull(SessionCodec.decode("com.x; rm|1|2|||"))
        assertEquals(1, SessionCodec.decodeAll(SessionCodec.encode(r) + "\nquebrado\n").size)
    }

    @Test fun aggregate() {
        val list = listOf(
            r,
            r.copy(startMs = 5000L, durationMs = 600_000L, averageFps = 58f, maxBatteryCelsius = 39f),
            GameSessionRecord("com.roblox.client", 1L, 60_000L, null, null, null),
        )
        val stats = GameStatsAggregator.aggregate(list)
        val mc = stats.getValue("com.mojang.minecraftpe")
        assertEquals(2, mc.sessions)
        assertEquals(4_200_000L, mc.totalPlayMs)
        assertEquals(58f, mc.lastAverageFps)
        assertEquals(58f, mc.bestAverageFps)
        assertEquals(41.5f, mc.maxBatteryCelsius)
        assertNull(stats.getValue("com.roblox.client").lastAverageFps)
    }

    @Test fun durations() {
        assertEquals("1h 10min", GameStatsAggregator.formatDuration(4_200_000L))
        assertEquals("5 min", GameStatsAggregator.formatDuration(300_000L))
        assertEquals("< 1 min", GameStatsAggregator.formatDuration(10_000L))
    }
}

class TimeStatsParserTest {
    private val dump = """
        SurfaceFlinger TimeStats:
        statsStart = 100
        totalFrames = 99999
        layerName = com.android.systemui.ImageWallpaper#0
        packageName =
        totalFrames = 50
        layerName = SurfaceView[com.roblox.client/com.roblox.client.ActivityNativeMain](BLAST)#88
        packageName = com.roblox.client
        totalFrames = 1800
        averageFPS = 59.9
        layerName = com.roblox.client/com.roblox.client.ActivityNativeMain#87
        packageName = com.roblox.client
        totalFrames = 40
    """.trimIndent()

    @Test fun picksBiggestLayerOfPackage() {
        assertEquals(1800L, TimeStatsParser.totalFrames(dump, "com.roblox.client"))
        assertNull(TimeStatsParser.totalFrames(dump, "com.mojang.minecraftpe"))
    }

    @Test fun ignoresGlobalTotal() {
        assertNull(TimeStatsParser.totalFrames("totalFrames = 500", "com.roblox.client"))
    }

    @Test fun fpsBetweenReads() {
        assertEquals(60, TimeStatsParser.fpsBetween(1000, 0, 1060, 1000))
        assertEquals(30, TimeStatsParser.fpsBetween(1000, 0, 1060, 2000))
        assertNull(TimeStatsParser.fpsBetween(1000, 0, 900, 1000))
        assertNull(TimeStatsParser.fpsBetween(1000, 0, 1010, 100))
    }
}
