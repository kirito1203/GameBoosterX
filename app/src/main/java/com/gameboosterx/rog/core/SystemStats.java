package com.gameboosterx.rog.core;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StatFs;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/**
 * Leitura de dados reais do sistema, apenas por APIs públicas (sem root).
 * Quando um dado não está disponível, o valor é marcado como indisponível
 * (NaN / -1) em vez de ser inventado.
 */
public final class SystemStats {

    private SystemStats() {}

    // ---------------- RAM ----------------
    public static final class RamInfo {
        public long totalBytes;
        public long availBytes;
        public long thresholdBytes;
        public boolean lowMemory;

        public long usedBytes() { return totalBytes - availBytes; }

        public int usedPercent() {
            if (totalBytes <= 0) return 0;
            return (int) Math.round(usedBytes() * 100.0 / totalBytes);
        }
    }

    public static RamInfo ram(Context ctx) {
        RamInfo r = new RamInfo();
        try {
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            r.totalBytes = mi.totalMem;
            r.availBytes = mi.availMem;
            r.thresholdBytes = mi.threshold;
            r.lowMemory = mi.lowMemory;
        } catch (Exception ignored) {
        }
        return r;
    }

    // ---------------- Bateria ----------------
    public static final class BatteryInfo {
        public int level = -1;            // %
        public float tempC = Float.NaN;   // temperatura da bateria
        public int voltageMv = -1;
        public boolean charging;
        public int plugged;
        public int health;
        /** Corrente instantânea em mA (positivo = carregando, negativo = descarregando). NaN se indisponível. */
        public float currentMa = Float.NaN;

        public boolean hasTemp() { return !Float.isNaN(tempC); }
    }

    public static BatteryInfo battery(Context ctx) {
        BatteryInfo b = new BatteryInfo();
        try {
            Intent i = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (i != null) {
                int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                if (level >= 0 && scale > 0) b.level = Math.round(level * 100f / scale);
                int t = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
                if (t != Integer.MIN_VALUE && t > -400 && t < 1000) b.tempC = t / 10f;
                b.voltageMv = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
                int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                b.charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                        || status == BatteryManager.BATTERY_STATUS_FULL;
                b.plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
                b.health = i.getIntExtra(BatteryManager.EXTRA_HEALTH, 0);
            }
            BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
            if (bm != null) {
                int cur = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                if (cur != Integer.MIN_VALUE && cur != 0) {
                    // A unidade é µA pela documentação, mas alguns fabricantes usam mA.
                    float ma = Math.abs(cur) > 20000 ? cur / 1000f : cur;
                    b.currentMa = ma;
                }
                if (b.level < 0) {
                    int cap = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                    if (cap > 0) b.level = cap;
                }
            }
        } catch (Exception ignored) {
        }
        return b;
    }

    // ---------------- Térmico ----------------
    public static final class ThermalInfo {
        /** PowerManager.THERMAL_STATUS_* ou -1 se indisponível */
        public int status = -1;
        /** 0.0 = frio, 1.0 = limite de throttling. NaN se indisponível. */
        public float headroom = Float.NaN;
        /** Maior temperatura lida de zonas térmicas acessíveis (quase sempre bloqueado pelo SELinux). */
        public float zoneMaxC = Float.NaN;
    }

    private static long lastHeadroomCall = 0;
    private static float lastHeadroom = Float.NaN;

    public static ThermalInfo thermal(Context ctx) {
        ThermalInfo t = new ThermalInfo();
        try {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                t.status = pm.getCurrentThermalStatus();
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    long now = System.currentTimeMillis();
                    // A API exige intervalo mínimo de ~1 s entre chamadas.
                    if (now - lastHeadroomCall >= 1500) {
                        lastHeadroomCall = now;
                        lastHeadroom = pm.getThermalHeadroom(10);
                    }
                    t.headroom = lastHeadroom;
                }
            }
        } catch (Exception ignored) {
        }
        t.zoneMaxC = readThermalZonesMax();
        return t;
    }

    /** Tenta ler /sys/class/thermal. Em aparelhos atuais costuma ser negado; nesse caso retorna NaN. */
    private static float readThermalZonesMax() {
        float max = Float.NaN;
        try {
            File dir = new File("/sys/class/thermal");
            File[] zones = dir.listFiles();
            if (zones == null) return max;
            for (File z : zones) {
                if (!z.getName().startsWith("thermal_zone")) continue;
                File f = new File(z, "temp");
                if (!f.canRead()) continue;
                try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                    String line = br.readLine();
                    if (line == null) continue;
                    float v = Float.parseFloat(line.trim());
                    if (v > 1000) v /= 1000f;
                    if (v > 10 && v < 130) {
                        if (Float.isNaN(max) || v > max) max = v;
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return max;
    }

    public static String thermalLabel(int status) {
        switch (status) {
            case PowerManager.THERMAL_STATUS_NONE: return "Normal";
            case PowerManager.THERMAL_STATUS_LIGHT: return "Leve";
            case PowerManager.THERMAL_STATUS_MODERATE: return "Moderado";
            case PowerManager.THERMAL_STATUS_SEVERE: return "Severo";
            case PowerManager.THERMAL_STATUS_CRITICAL: return "Crítico";
            case PowerManager.THERMAL_STATUS_EMERGENCY: return "Emergência";
            case PowerManager.THERMAL_STATUS_SHUTDOWN: return "Desligamento";
            default: return "Indisponível";
        }
    }

    /** 0 = ok, 1 = morno, 2 = quente, 3 = crítico */
    public static int heatLevel(BatteryInfo b, ThermalInfo t, float warnC) {
        int lvl = 0;
        if (b != null && b.hasTemp()) {
            if (b.tempC >= warnC + 3) lvl = 3;
            else if (b.tempC >= warnC) lvl = 2;
            else if (b.tempC >= warnC - 3) lvl = 1;
        }
        if (t != null) {
            if (t.status >= PowerManager.THERMAL_STATUS_SEVERE) lvl = Math.max(lvl, 3);
            else if (t.status == PowerManager.THERMAL_STATUS_MODERATE) lvl = Math.max(lvl, 2);
            else if (t.status == PowerManager.THERMAL_STATUS_LIGHT) lvl = Math.max(lvl, 1);
            if (!Float.isNaN(t.headroom)) {
                if (t.headroom >= 1.0f) lvl = Math.max(lvl, 3);
                else if (t.headroom >= 0.85f) lvl = Math.max(lvl, 2);
            }
        }
        return lvl;
    }

    // ---------------- Armazenamento ----------------
    public static final class StorageInfo {
        public long totalBytes;
        public long freeBytes;

        public int usedPercent() {
            if (totalBytes <= 0) return 0;
            return (int) Math.round((totalBytes - freeBytes) * 100.0 / totalBytes);
        }
    }

    public static StorageInfo storage() {
        StorageInfo s = new StorageInfo();
        try {
            StatFs fs = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            s.totalBytes = fs.getTotalBytes();
            s.freeBytes = fs.getAvailableBytes();
        } catch (Exception ignored) {
        }
        return s;
    }

    // ---------------- Formatação ----------------
    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(java.util.Locale.US, "%.0f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(java.util.Locale.US, "%.1f MB", mb);
        return String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0);
    }

    public static String formatDuration(long ms) {
        long s = ms / 1000;
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        if (h > 0) return String.format(java.util.Locale.US, "%dh %02dmin", h, m);
        return String.format(java.util.Locale.US, "%dmin %02ds", m, sec);
    }
}
