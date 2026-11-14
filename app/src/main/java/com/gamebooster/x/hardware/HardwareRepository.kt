package com.gamebooster.x.hardware

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.view.Display
import com.gamebooster.x.core.BatteryReading
import com.gamebooster.x.core.CpuReading
import com.gamebooster.x.core.DeviceIdentity
import com.gamebooster.x.core.DisplayReading
import com.gamebooster.x.core.HardwareSnapshot
import com.gamebooster.x.core.MemoryReading
import com.gamebooster.x.core.ThermalReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Acesso ao hardware usando somente APIs públicas e arquivos legíveis sem root.
 * Toda leitura que o aparelho bloquear retorna null — nunca um valor inventado.
 */
class HardwareRepository(context: Context) {

    private val appContext = context.applicationContext
    private val activityManager: ActivityManager =
        checkNotNull(appContext.getSystemService(ActivityManager::class.java)) { "ActivityManager indisponível" }
    private val powerManager: PowerManager =
        checkNotNull(appContext.getSystemService(PowerManager::class.java)) { "PowerManager indisponível" }
    private val displayManager: DisplayManager =
        checkNotNull(appContext.getSystemService(DisplayManager::class.java)) { "DisplayManager indisponível" }

    private val coreCount: Int = Runtime.getRuntime().availableProcessors()

    @Volatile
    private var sensorsReadable: Boolean = true

    fun identity(): DeviceIdentity {
        val socManufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MANUFACTURER else null
        val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else null
        return DeviceIdentity(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            brand = Build.BRAND.orEmpty(),
            model = Build.MODEL.orEmpty(),
            device = Build.DEVICE.orEmpty(),
            product = Build.PRODUCT.orEmpty(),
            marketName = readSystemProperty("ro.product.marketname")
                ?: readSystemProperty("ro.vendor.oplus.market.name")
                ?: readSystemProperty("ro.product.vendor.marketname"),
            socManufacturer = socManufacturer?.takeUnless { it.isBlank() || it == Build.UNKNOWN },
            socModel = socModel?.takeUnless { it.isBlank() || it == Build.UNKNOWN },
            androidRelease = Build.VERSION.RELEASE.orEmpty(),
            sdkInt = Build.VERSION.SDK_INT,
            abis = Build.SUPPORTED_ABIS.toList(),
            cpuCores = coreCount,
            totalRamBytes = memory().totalBytes,
        )
    }

    fun memory(): MemoryReading {
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        return MemoryReading(
            totalBytes = info.totalMem,
            availableBytes = info.availMem,
            lowMemoryThresholdBytes = info.threshold,
            systemLowMemory = info.lowMemory,
        )
    }

    fun battery(): Pair<BatteryReading, Float?> {
        val intent: Intent? = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (intent == null) return BatteryReading(null, false) to null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else null
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val temp = if (tempTenths == Int.MIN_VALUE) null else tempTenths / 10f
        return BatteryReading(percent, charging) to temp
    }

    fun thermal(batteryCelsius: Float?): ThermalReading {
        val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) powerManager.currentThermalStatus else null
        val headroom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Retorna NaN quando não suportado ou se chamado com frequência maior que 1 Hz.
            powerManager.getThermalHeadroom(10).takeUnless { it.isNaN() }
        } else null
        return ThermalReading(
            batteryCelsius = batteryCelsius,
            systemThermalStatus = status,
            thermalHeadroom = headroom,
            hottestSensorCelsius = hottestThermalZone(),
        )
    }

    /** Lê /sys/class/thermal. Em muitos aparelhos recentes o SELinux bloqueia; então retorna null. */
    private fun hottestThermalZone(): Float? {
        if (!sensorsReadable) return null
        val zones = File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") }
        if (zones.isNullOrEmpty()) {
            sensorsReadable = false
            return null
        }
        val temps = zones.mapNotNull { zone ->
            val raw = readLong(File(zone, "temp")) ?: return@mapNotNull null
            val celsius = if (raw > 1000) raw / 1000f else raw.toFloat()
            celsius.takeIf { it in 15f..120f }
        }
        if (temps.isEmpty()) sensorsReadable = false
        return temps.maxOrNull()
    }

    fun cpu(): CpuReading {
        val cur = (0 until coreCount).map { readLong(File("/sys/devices/system/cpu/cpu$it/cpufreq/scaling_cur_freq")) }
        val max = (0 until coreCount).map { readLong(File("/sys/devices/system/cpu/cpu$it/cpufreq/cpuinfo_max_freq")) }
        return CpuReading(coreCount, cur, max)
    }

    fun display(): DisplayReading? {
        val d: Display = displayManager.getDisplay(Display.DEFAULT_DISPLAY) ?: return null
        val mode = d.mode
        val supported = d.supportedModes
            .filter { it.physicalWidth == mode.physicalWidth && it.physicalHeight == mode.physicalHeight }
            .map { it.refreshRate }
            .distinct()
            .sorted()
        return DisplayReading(
            currentRefreshHz = d.refreshRate,
            supportedRefreshHz = supported,
            widthPx = mode.physicalWidth,
            heightPx = mode.physicalHeight,
        )
    }

    fun snapshot(): HardwareSnapshot {
        val (battery, batteryTemp) = battery()
        return HardwareSnapshot(
            timestampMs = System.currentTimeMillis(),
            memory = memory(),
            thermal = thermal(batteryTemp),
            cpu = cpu(),
            battery = battery,
            display = runCatching { display() }.getOrNull(),
        )
    }

    /** Fluxo contínuo de leituras no intervalo indicado (definido pelo modo ativo). */
    fun snapshots(intervalMs: Long): Flow<HardwareSnapshot> = flow {
        while (true) {
            emit(snapshot())
            delay(intervalMs)
        }
    }.flowOn(Dispatchers.IO)

    private fun readLong(file: File): Long? = try {
        file.readText().trim().toLongOrNull()
    } catch (_: Exception) {
        null
    }

    /** getprop é um utilitário comum do Android, executado sem privilégios. */
    private fun readSystemProperty(key: String): String? = try {
        val process = ProcessBuilder("getprop", key).redirectErrorStream(true).start()
        val finished = process.waitFor(500, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroy()
            null
        } else {
            process.inputStream.bufferedReader().readText().trim().takeIf { it.isNotEmpty() }
        }
    } catch (_: Exception) {
        null
    }
}
