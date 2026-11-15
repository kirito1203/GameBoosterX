package com.realme.boosterultra.ai;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Display;
import android.view.WindowManager;

import com.realme.boosterultra.utils.DeviceInfo;

import java.util.ArrayList;
import java.util.List;

/** AI INTELLIGENCE: ULTRA SCORE com critérios transparentes. */
public final class IntelligenceEngine {
    private IntelligenceEngine() {}

    public static final class Criterion {
        public String name, detail;
        public int points, max;
        Criterion(String n, int p, int m, String d) { name = n; points = p; max = m; detail = d; }
    }

    public static List<Criterion> ultraScore(Context c) {
        List<Criterion> l = new ArrayList<>();
        long[] r = DeviceInfo.ram(c);
        int ru = DeviceInfo.pct(r[0] - r[1], r[0]);
        l.add(new Criterion("Memória RAM", ru <= 60 ? 20 : ru <= 75 ? 14 : ru <= 85 ? 8 : 3, 20, ru + "% em uso (ideal até 60%)"));

        DeviceInfo.Battery b = DeviceInfo.battery(c);
        int tp = b.temp <= 35 ? 20 : b.temp <= 38 ? 15 : b.temp <= 41 ? 9 : b.temp <= 44 ? 4 : 0;
        l.add(new Criterion("Temperatura", tp, 20, String.format(java.util.Locale.US, "%.1f°C (ideal até 35°C)", b.temp)));

        int th = DeviceInfo.thermalStatus(c);
        l.add(new Criterion("Estado térmico do sistema", th <= 0 ? 10 : th == 1 ? 6 : 0, 10, DeviceInfo.thermalName(th)));

        long[] s = DeviceInfo.storage();
        int su = DeviceInfo.pct(s[0] - s[1], s[0]);
        l.add(new Criterion("Armazenamento", su <= 70 ? 15 : su <= 80 ? 11 : su <= 90 ? 6 : 2, 15, su + "% usado (ideal até 70%)"));

        Intent bi = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int health = bi == null ? 0 : bi.getIntExtra(BatteryManager.EXTRA_HEALTH, 0);
        boolean good = health == 0 || health == BatteryManager.BATTERY_HEALTH_GOOD || health == BatteryManager.BATTERY_HEALTH_UNKNOWN;
        l.add(new Criterion("Saúde da bateria", good ? 10 : 2, 10, good ? "sem alertas do sistema" : "o sistema indica problema"));

        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        boolean saver = pm != null && pm.isPowerSaveMode();
        l.add(new Criterion("Economia de bateria do Android", saver ? 1 : 5, 5, saver ? "ligada (reduz desempenho)" : "desligada"));

        long days = SystemClock.elapsedRealtime() / 86_400_000L;
        l.add(new Criterion("Tempo ligado", days < 1 ? 10 : days < 3 ? 7 : days < 7 ? 3 : 0, 10, days + " dia(s) sem reiniciar (ideal: menos de 3)"));

        int hzPts = 5; String hzTxt = "indisponível";
        try {
            Display d = ((WindowManager) c.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
            float max = 0;
            for (Display.Mode m : d.getSupportedModes()) max = Math.max(max, m.getRefreshRate());
            boolean low = d.getRefreshRate() + 1 < max;
            hzPts = low ? 2 : 5;
            hzTxt = Math.round(d.getRefreshRate()) + "Hz de " + Math.round(max) + "Hz possíveis";
        } catch (Exception ignored) {}
        l.add(new Criterion("Taxa de atualização da tela", hzPts, 5, hzTxt));

        int perms = 0;
        if (Settings.canDrawOverlays(c)) perms++;
        if (Settings.System.canWrite(c)) perms++;
        if (usageOk(c)) perms++;
        l.add(new Criterion("Permissões do app", perms == 3 ? 5 : perms == 2 ? 3 : perms, 5, perms + " de 3 (painel, configurações, uso de apps)"));
        return l;
    }

    public static int total(List<Criterion> l) {
        int t = 0;
        for (Criterion c : l) t += c.points;
        return t;
    }

    public static String grade(int score) {
        return score >= 90 ? "ULTRA" : score >= 75 ? "ÓTIMO" : score >= 60 ? "BOM" : score >= 40 ? "REGULAR" : "CRÍTICO";
    }

    public static boolean usageOk(Context c) {
        try {
            AppOpsManager ao = c.getSystemService(AppOpsManager.class);
            int mode = Build.VERSION.SDK_INT >= 29
                    ? ao.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName())
                    : ao.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }
}
