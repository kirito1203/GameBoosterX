package com.realme.boosterultra.utils;

import com.realme.boosterultra.*;
import com.realme.boosterultra.engine.*;
import com.realme.boosterultra.ai.*;
import com.realme.boosterultra.gaming.*;
import com.realme.boosterultra.monitor.*;
import com.realme.boosterultra.utils.*;
import com.realme.boosterultra.ui.*;


import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.os.StatFs;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Locale;

/** Leituras reais do sistema (sem root). */
public final class DeviceInfo {
    private DeviceInfo() {}

    public static final class Battery {
        public int level;
        public float temp;
        public boolean charging;
    }

    /** {total, disponível} em bytes */
    public static long[] ram(Context c) {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return new long[]{mi.totalMem, mi.availMem};
    }

    /** {total, livre} em bytes */
    public static long[] storage() {
        StatFs s = new StatFs(Environment.getDataDirectory().getPath());
        return new long[]{s.getTotalBytes(), s.getAvailableBytes()};
    }

    public static Battery battery(Context c) {
        Battery b = new Battery();
        Intent i = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (i == null) return b;
        int lvl = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
        int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        b.level = scale > 0 ? Math.round(lvl * 100f / scale) : lvl;
        b.temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f;
        int st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        b.charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL;
        return b;
    }

    public static int thermalStatus(Context c) {
        if (Build.VERSION.SDK_INT < 29) return -1;
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        return pm == null ? -1 : pm.getCurrentThermalStatus();
    }

    /** 0..1 (1 = começando a reduzir desempenho). NaN se não suportado. */
    public static float thermalHeadroom(Context c) {
        if (Build.VERSION.SDK_INT < 30) return Float.NaN;
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        try { return pm == null ? Float.NaN : pm.getThermalHeadroom(10); }
        catch (Exception e) { return Float.NaN; }
    }

    public static String thermalName(int s) {
        switch (s) {
            case 0: return "Normal ✔";
            case 1: return "Leve";
            case 2: return "Moderado (desempenho pode cair)";
            case 3: return "Severo — deixe esfriar";
            case 4: return "Crítico — pare de jogar";
            case 5: return "Emergência";
            case 6: return "Desligando por calor";
            default: return "Indisponível";
        }
    }

    public static String cpuText() {
        int n = Runtime.getRuntime().availableProcessors();
        StringBuilder sb = new StringBuilder();
        sb.append(n).append(" núcleos");
        if (Build.VERSION.SDK_INT >= 31) {
            String soc = Build.SOC_MODEL;
            if (soc != null && !soc.isEmpty() && !"unknown".equalsIgnoreCase(soc)) {
                sb.append(" • chip ").append(Build.SOC_MANUFACTURER).append(" ").append(soc);
            }
        }
        StringBuilder f = new StringBuilder();
        int ok = 0;
        for (int i = 0; i < n; i++) {
            String v = readLine("/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_cur_freq");
            if (v == null) continue;
            try {
                long khz = Long.parseLong(v.trim());
                if (ok > 0) f.append("  ");
                f.append(khz / 1000);
                ok++;
            } catch (NumberFormatException ignored) {}
        }
        if (ok > 0) sb.append("\nFrequências agora (MHz): ").append(f);
        else sb.append("\nFrequência em tempo real: bloqueada pelo Android (normal sem root)");
        return sb.toString();
    }

    public static String readLine(String path) {
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            return br.readLine();
        } catch (Exception e) {
            return null;
        }
    }

    public static int pct(long part, long total) {
        return total <= 0 ? 0 : (int) Math.round(part * 100.0 / total);
    }

    public static String fmt(long bytes) {
        double gb = bytes / (1024.0 * 1024 * 1024);
        if (gb >= 1) return String.format(Locale.US, "%.1f GB", gb);
        return String.format(Locale.US, "%.0f MB", bytes / (1024.0 * 1024));
    }
}
