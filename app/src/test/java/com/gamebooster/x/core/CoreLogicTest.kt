package com.gamebooster.x.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val GIB = 1024L * 1024L * 1024L

class PerformanceModeTest {
    @Test fun fromName_fallsBackToDefault() {
        assertEquals(PerformanceMode.EXTREME, PerformanceMode.fromName("EXTREME"))
        assertEquals(PerformanceMode.BALANCED, PerformanceMode.fromName(null))
        assertEquals(PerformanceMode.BALANCED, PerformanceMode.fromName("lixo"))
    }

    @Test fun policies_areOrderedByAggressiveness() {
        val e = ModePolicies.of(PerformanceMode.EXTREME)
        val b = ModePolicies.of(PerformanceMode.BALANCED)
        val c = ModePolicies.of(PerformanceMode.COOL)
        assertTrue(e.sampleIntervalMs < b.sampleIntervalMs && b.sampleIntervalMs < c.sampleIntervalMs)
        assertTrue(c.thermalWarnCelsius < b.thermalWarnCelsius && b.thermalWarnCelsius < e.thermalWarnCelsius)
        PerformanceMode.entries.forEach {
            val p = ModePolicies.of(it)
            assertEquals(it, p.mode)
            assertTrue(p.thermalCriticalCelsius > p.thermalWarnCelsius)
            assertTrue(p.description.isNotBlank())
        }
        assertFalse(c.keepScreenOn)
    }
}

class ThermalAnalyzerTest {
    private val balanced = ModePolicies.of(PerformanceMode.BALANCED)

    @Test fun noData_isUnknown() {
        val a = ThermalAnalyzer.assess(ThermalReading(null, null, null, null), balanced)
        assertEquals(ThermalLevel.UNKNOWN, a.level)
        assertFalse(a.throttlingLikely)
    }

    @Test fun coolBattery_isNormal() {
        val a = ThermalAnalyzer.assess(ThermalReading(30f, 0, 0.3f, null), balanced)
        assertEquals(ThermalLevel.NORMAL, a.level)
    }

    @Test fun batteryAboveWarn_isHot() {
        val a = ThermalAnalyzer.assess(ThermalReading(41f, null, null, null), balanced)
        assertEquals(ThermalLevel.HOT, a.level)
        assertFalse(a.throttlingLikely)
    }

    @Test fun sameTemp_dependsOnMode() {
        val r = ThermalReading(39f, null, null, null)
        assertEquals(ThermalLevel.HOT, ThermalAnalyzer.assess(r, ModePolicies.of(PerformanceMode.COOL)).level)
        assertEquals(ThermalLevel.WARM, ThermalAnalyzer.assess(r, ModePolicies.of(PerformanceMode.EXTREME)).level)
    }

    @Test fun systemModerate_marksThrottling() {
        val a = ThermalAnalyzer.assess(ThermalReading(33f, ThermalAnalyzer.STATUS_MODERATE, null, null), balanced)
        assertEquals(ThermalLevel.HOT, a.level)
        assertTrue(a.throttlingLikely)
    }

    @Test fun headroomExhausted_isCritical() {
        val a = ThermalAnalyzer.assess(ThermalReading(33f, 0, 1.05f, null), balanced)
        assertEquals(ThermalLevel.CRITICAL, a.level)
        assertTrue(a.throttlingLikely)
    }

    @Test fun usesHottestSource() {
        val a = ThermalAnalyzer.assess(ThermalReading(30f, null, null, 45f), balanced)
        assertEquals(ThermalLevel.CRITICAL, a.level)
    }
}

class MemoryAnalyzerTest {
    private val balanced = ModePolicies.of(PerformanceMode.BALANCED)

    private fun reading(usedPercent: Int, low: Boolean = false): MemoryReading {
        val total = 4L * GIB
        return MemoryReading(total, total - total * usedPercent / 100, 256L * 1024 * 1024, low)
    }

    @Test fun usedPercent_computed() {
        assertEquals(50, reading(50).usedPercent)
        assertEquals(0, MemoryReading(0, 0, 0, false).usedPercent)
    }

    @Test fun pressureBands() {
        assertEquals(MemoryPressure.LOW, MemoryAnalyzer.assess(reading(40), balanced).pressure)
        assertEquals(MemoryPressure.MODERATE, MemoryAnalyzer.assess(reading(65), balanced).pressure)
        assertEquals(MemoryPressure.HIGH, MemoryAnalyzer.assess(reading(80), balanced).pressure)
        assertEquals(MemoryPressure.CRITICAL, MemoryAnalyzer.assess(reading(90), balanced).pressure)
        assertEquals(MemoryPressure.CRITICAL, MemoryAnalyzer.assess(reading(30, low = true), balanced).pressure)
    }

    @Test fun alertThresholdFollowsMode() {
        val r = reading(72)
        assertTrue(MemoryAnalyzer.assess(r, ModePolicies.of(PerformanceMode.EXTREME)).aboveModeAlert)
        assertFalse(MemoryAnalyzer.assess(r, ModePolicies.of(PerformanceMode.COOL)).aboveModeAlert)
    }
}

class DeviceProfileResolverTest {
    private fun id(
        manufacturer: String = "realme", brand: String = "realme", model: String = "RMX0000",
        marketName: String? = null, ram: Long = 4L * GIB, cores: Int = 8,
    ) = DeviceIdentity(
        manufacturer, brand, model, device = "dev", product = "prod", marketName = marketName,
        socManufacturer = null, socModel = null, androidRelease = "15", sdkInt = 35,
        abis = listOf("arm64-v8a"), cpuCores = cores, totalRamBytes = ram,
    )

    @Test fun detectsC71ByMarketName() {
        val p = DeviceProfileResolver.resolve(id(marketName = "realme C71"))
        assertTrue(p.isRealmeC71)
        assertEquals(DeviceProfileResolver.REALME_C71_KEY, p.key)
        assertEquals("realme C71", p.displayName)
    }

    @Test fun detectsC71ByModel() {
        assertTrue(DeviceProfileResolver.isRealmeC71(id(model = "realme C71")))
    }

    @Test fun doesNotConfuseSimilarModels() {
        assertFalse(DeviceProfileResolver.isRealmeC71(id(marketName = "realme C715")))
        assertFalse(DeviceProfileResolver.isRealmeC71(id(marketName = "realme C61")))
        assertFalse(DeviceProfileResolver.isRealmeC71(id(manufacturer = "samsung", brand = "samsung", marketName = "C71")))
    }

    @Test fun genericProfileHasSafeKey() {
        val p = DeviceProfileResolver.resolve(id(manufacturer = "Samsung", brand = "samsung", model = "SM-A155M"))
        assertFalse(p.isRealmeC71)
        assertEquals("generic_samsung_sm_a155m", p.key)
    }

    @Test fun tierUsesMeasuredValues() {
        assertEquals(DeviceTier.ENTRY, DeviceProfileResolver.tierFor(4L * GIB, 8))
        assertEquals(DeviceTier.ENTRY, DeviceProfileResolver.tierFor(8L * GIB, 4))
        assertEquals(DeviceTier.MID, DeviceProfileResolver.tierFor(6L * GIB, 8))
        assertEquals(DeviceTier.HIGH, DeviceProfileResolver.tierFor(12L * GIB, 8))
    }
}

class FormattersAndHistoryTest {
    @Test fun bytes() {
        assertEquals("2,5 GB", Formatters.bytes(5L * GIB / 2))
        assertEquals("512 MB", Formatters.bytes(512L * 1024 * 1024))
        assertEquals("-512 MB", Formatters.bytes(-512L * 1024 * 1024))
        assertEquals("100 B", Formatters.bytes(100))
    }

    @Test fun misc() {
        assertEquals("Indisponível", Formatters.celsius(null))
        assertEquals("36,5 °C", Formatters.celsius(36.5f))
        assertEquals("1800 MHz", Formatters.mhz(1_800_000))
        assertEquals("—", Formatters.mhz(null))
        assertEquals("90 Hz", Formatters.hz(90f))
    }

    @Test fun historyKeepsLastN() {
        var h = SampleHistory(3)
        assertNull(h.latest)
        listOf(1f, 2f, 3f, 4f, 5f).forEach { h = h.add(it) }
        assertEquals(listOf(3f, 4f, 5f), h.values)
        assertEquals(5f, h.latest)
        assertEquals(3f, h.min)
        assertEquals(4f, h.average!!, 0.0001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun historyRejectsZeroCapacity() {
        SampleHistory(0)
    }

    @Test fun cpuAverage() {
        val r = CpuReading(3, listOf(1000L, 500L, null), listOf(2000L, 1000L, 2000L))
        assertEquals(50, r.averageLoadOfMaxPercent)
        assertNull(CpuReading(1, listOf(null), listOf(null)).averageLoadOfMaxPercent)
    }
}

class BoostAndAdvisorTest {
    private val balanced = ModePolicies.of(PerformanceMode.BALANCED)
    private val entryProfile = DeviceProfile("k", "n", false, DeviceTier.ENTRY, emptyList())
    private val midProfile = entryProfile.copy(tier = DeviceTier.MID)

    @Test fun boostReportDeltaAndHonestSummary() {
        val gain = BoostReport(1 * GIB, 1 * GIB + 200L * 1024 * 1024, 5, 1500, 0)
        assertEquals(200L * 1024 * 1024, gain.deltaBytes)
        assertTrue(gain.summary().contains("aumentou"))
        assertTrue(BoostReport(GIB, GIB, 3, 1, 0).summary().contains("Nenhuma variação"))
        assertTrue(BoostReport(GIB, GIB - 1024 * 1024, 3, 1, 0).summary().contains("caiu"))
    }

    @Test fun advisorAllGood() {
        val advice = DashboardAdvisor.advise(
            ThermalAssessment(ThermalLevel.NORMAL, false, emptyList()),
            MemoryAssessment(40, MemoryPressure.LOW, false), balanced, midProfile, null,
        )
        assertEquals(1, advice.size)
        assertEquals(AdviceSeverity.INFO, advice.first().severity)
    }

    @Test fun advisorFlagsHeatAndMemory() {
        val advice = DashboardAdvisor.advise(
            ThermalAssessment(ThermalLevel.CRITICAL, true, emptyList()),
            MemoryAssessment(92, MemoryPressure.CRITICAL, true), balanced, entryProfile, null,
        )
        assertEquals(AdviceSeverity.CRITICAL, advice[0].severity)
        assertTrue(advice.any { it.title.startsWith("RAM") && it.severity == AdviceSeverity.CRITICAL })
        assertTrue(advice.any { it.title == "Gráficos recomendados" })
    }

    @Test fun advisorRefreshRateOnlyWhenRealAndExtreme() {
        val display = DisplayReading(60f, listOf(60f, 90f), 720, 1600)
        val extreme = ModePolicies.of(PerformanceMode.EXTREME)
        val normal = ThermalAssessment(ThermalLevel.NORMAL, false, emptyList())
        val mem = MemoryAssessment(40, MemoryPressure.LOW, false)
        assertTrue(DashboardAdvisor.advise(normal, mem, extreme, midProfile, display).any { it.title == "Taxa de atualização" })
        assertFalse(DashboardAdvisor.advise(normal, mem, extreme, midProfile, display.copy(supportedRefreshHz = listOf(60f)))
            .any { it.title == "Taxa de atualização" })
    }

    @Test fun knownGamesLookup() {
        assertEquals("Roblox", KnownGames.find("com.roblox.client")?.name)
        assertNull(KnownGames.find("com.example"))
        assertEquals(KnownGames.all.size, KnownGames.all.map { it.packageName }.toSet().size)
    }
}
