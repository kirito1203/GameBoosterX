package com.pulsesolar.app.core;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StatFs;
import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.FileReader;

/** Leituras reais do sistema Android. Nada aqui é simulado. */
public final class SystemInfo {

    private SystemInfo() {
    }

    // ---------------------------------------------------------------- RAM

    public static final class Ram {
        public long total;
        public long avail;
        public long threshold;
        public boolean low;

        public long used() {
            return total - avail;
        }

        public float usedPercent() {
            return total > 0 ? used() * 100f / total : 0f;
        }

        public float availPercent() {
            return total > 0 ? avail * 100f / total : 0f;
        }
    }

    public static Ram ram(Context c) {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        Ram r = new Ram();
        r.total = mi.totalMem;
        r.avail = mi.availMem;
        r.threshold = mi.threshold;
        r.low = mi.lowMemory;
        return r;
    }

    // ------------------------------------------------------- Armazenamento

    public static final class Storage {
        public long total;
        public long free;
        public String path;

        public long used() {
            return total - free;
        }

        public float usedPercent() {
            return total > 0 ? used() * 100f / total : 0f;
        }

        public float freePercent() {
            return total > 0 ? free * 100f / total : 0f;
        }
    }

    public static Storage storage() {
        String path = Environment.getDataDirectory().getPath();
        StatFs s = new StatFs(path);
        Storage st = new Storage();
        st.total = s.getTotalBytes();
        st.free = s.getAvailableBytes();
        st.path = path;
        return st;
    }

    // ------------------------------------------------------------- Bateria

    public static final class Battery {
        public int level = -1;
        public int status = BatteryManager.BATTERY_STATUS_UNKNOWN;
        public int plugged = 0;
        public int health = BatteryManager.BATTERY_HEALTH_UNKNOWN;
        public int voltageMv = -1;
        public float tempC = Float.NaN;
        public String technology;
        public int currentRaw = Integer.MIN_VALUE;
        public int chargeCounterUah = Integer.MIN_VALUE;
        public long chargeTimeRemainingMs = -1;
        public boolean powerSave;
        public int thermalStatus = -1;

        public boolean isCharging() {
            return status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
        }

        /** Corrente em mA. Fabricantes reportam em µA ou mA; valores grandes são tratados como µA. */
        public Float currentMa() {
            if (currentRaw == Integer.MIN_VALUE || currentRaw == Integer.MAX_VALUE) return null;
            if (Math.abs(currentRaw) > 10000) return currentRaw / 1000f;
            return (float) currentRaw;
        }
    }

    public static Battery battery(Context c) {
        Battery b = new Battery();
        Intent i = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (i != null) {
            int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            if (level >= 0 && scale > 0) b.level = Math.round(level * 100f / scale);
            b.status = i.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
            b.plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            b.health = i.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN);
            int t = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
            if (t != Integer.MIN_VALUE) b.tempC = t / 10f;
            b.voltageMv = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
            b.technology = i.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
        }
        BatteryManager bm = (BatteryManager) c.getSystemService(Context.BATTERY_SERVICE);
        if (bm != null) {
            b.currentRaw = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            b.chargeCounterUah = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
            if (Build.VERSION.SDK_INT >= 28) {
                b.chargeTimeRemainingMs = bm.computeChargeTimeRemaining();
            }
        }
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            b.powerSave = pm.isPowerSaveMode();
            if (Build.VERSION.SDK_INT >= 29) b.thermalStatus = pm.getCurrentThermalStatus();
        }
        return b;
    }

    public static String statusText(int s) {
        switch (s) {
            case BatteryManager.BATTERY_STATUS_CHARGING: return "Carregando";
            case BatteryManager.BATTERY_STATUS_DISCHARGING: return "Descarregando";
            case BatteryManager.BATTERY_STATUS_FULL: return "Carga completa";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return "Conectado, sem carregar";
            default: return "Desconhecido";
        }
    }

    public static String pluggedText(int p) {
        switch (p) {
            case BatteryManager.BATTERY_PLUGGED_AC: return "Carregador (tomada)";
            case BatteryManager.BATTERY_PLUGGED_USB: return "USB";
            case BatteryManager.BATTERY_PLUGGED_WIRELESS: return "Sem fio";
            case 0: return "Desconectado";
            default: return "Outro";
        }
    }

    public static String healthText(int h) {
        switch (h) {
            case BatteryManager.BATTERY_HEALTH_GOOD: return "Boa";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: return "Superaquecida";
            case BatteryManager.BATTERY_HEALTH_DEAD: return "Danificada";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "Sobretensão";
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE: return "Falha";
            case BatteryManager.BATTERY_HEALTH_COLD: return "Fria demais";
            default: return "Desconhecida";
        }
    }

    /** Texto do estado térmico do PowerManager (Android 10+). */
    public static String thermalText(int s) {
        switch (s) {
            case PowerManager.THERMAL_STATUS_NONE: return "Normal";
            case PowerManager.THERMAL_STATUS_LIGHT: return "Leve";
            case PowerManager.THERMAL_STATUS_MODERATE: return "Moderado (pode limitar)";
            case PowerManager.THERMAL_STATUS_SEVERE: return "Severo (limitando)";
            case PowerManager.THERMAL_STATUS_CRITICAL: return "Crítico";
            case PowerManager.THERMAL_STATUS_EMERGENCY: return "Emergência";
            case PowerManager.THERMAL_STATUS_SHUTDOWN: return "Desligamento iminente";
            default: return "Não informado";
        }
    }

    // ----------------------------------------------------------------- CPU

    /** Lê a primeira linha de um arquivo do sistema; retorna null se for bloqueado. */
    public static String readLine(String path) {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line = br.readLine();
            return line == null ? null : line.trim();
        } catch (Exception e) {
            return null;
        }
    }

    /** Frequência em kHz de um núcleo, ou -1 se o sistema não permitir a leitura. */
    public static long cpuFreqKhz(int core, String file) {
        String v = readLine("/sys/devices/system/cpu/cpu" + core + "/cpufreq/" + file);
        if (v == null) return -1;
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static int cores() {
        return Runtime.getRuntime().availableProcessors();
    }

    public static long uptimeMs() {
        return SystemClock.elapsedRealtime();
    }
}
