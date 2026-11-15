package com.pulsesolar.app.core;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Estatísticas reais de uso de apps (exige "acesso ao uso" concedido pelo usuário). */
public final class UsageHelper {

    public static final class AppUse {
        public String pkg;
        public String label;
        public Drawable icon;
        public long foregroundMs;
        public long lastUsed;
    }

    private UsageHelper() {
    }

    /** Apps usados dentro da janela, ordenados por tempo em primeiro plano (byForeground) ou pelo uso mais recente. */
    public static List<AppUse> query(Context c, long windowMs, int max, boolean byForeground) {
        List<AppUse> out = new ArrayList<>();
        if (!Perms.hasUsageAccess(c)) return out;
        UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
        PackageManager pm = c.getPackageManager();
        long end = System.currentTimeMillis();
        long begin = end - windowMs;
        Map<String, UsageStats> map = usm.queryAndAggregateUsageStats(begin, end);
        for (UsageStats u : map.values()) {
            String pkg = u.getPackageName();
            if (pkg.equals(c.getPackageName())) continue;
            if (u.getLastTimeUsed() < begin) continue;
            if (byForeground && u.getTotalTimeInForeground() < 30_000) continue;
            if (pm.getLaunchIntentForPackage(pkg) == null) continue; // ignora serviços internos do sistema
            AppUse a = new AppUse();
            a.pkg = pkg;
            a.foregroundMs = u.getTotalTimeInForeground();
            a.lastUsed = u.getLastTimeUsed();
            out.add(a);
        }
        if (byForeground) {
            Collections.sort(out, (x, y) -> Long.compare(y.foregroundMs, x.foregroundMs));
        } else {
            Collections.sort(out, (x, y) -> Long.compare(y.lastUsed, x.lastUsed));
        }
        List<AppUse> top = new ArrayList<>(out.subList(0, Math.min(max, out.size())));
        for (AppUse a : top) {
            try {
                ApplicationInfo ai = pm.getApplicationInfo(a.pkg, 0);
                a.label = pm.getApplicationLabel(ai).toString();
                a.icon = pm.getApplicationIcon(ai);
            } catch (PackageManager.NameNotFoundException e) {
                a.label = a.pkg;
            }
        }
        return top;
    }

    /**
     * Pacote que ficou mais tempo em primeiro plano entre begin e end (eventos reais do
     * UsageStatsManager). Ignora o próprio app. Retorna null sem acesso ao uso.
     */
    @SuppressWarnings("deprecation")
    public static String topForegroundBetween(Context c, long begin, long end) {
        if (!Perms.hasUsageAccess(c)) return null;
        UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
        UsageEvents events = usm.queryEvents(begin - 60_000, end);
        UsageEvents.Event e = new UsageEvents.Event();
        Map<String, Long> started = new HashMap<>();
        Map<String, Long> total = new HashMap<>();
        while (events.hasNextEvent()) {
            events.getNextEvent(e);
            String pkg = e.getPackageName();
            if (pkg == null || pkg.equals(c.getPackageName())) continue;
            long ts = Math.max(begin, e.getTimeStamp());
            if (e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                started.put(pkg, ts);
            } else if (e.getEventType() == UsageEvents.Event.MOVE_TO_BACKGROUND) {
                Long s = started.remove(pkg);
                if (s != null && ts > s) total.merge(pkg, ts - s, Long::sum);
            }
        }
        for (Map.Entry<String, Long> en : started.entrySet()) {
            if (end > en.getValue()) total.merge(en.getKey(), end - en.getValue(), Long::sum);
        }
        String best = null;
        long bestMs = 0;
        PackageManager pm = c.getPackageManager();
        for (Map.Entry<String, Long> en : total.entrySet()) {
            if (en.getValue() > bestMs && pm.getLaunchIntentForPackage(en.getKey()) != null) {
                best = en.getKey();
                bestMs = en.getValue();
            }
        }
        return best;
    }
}
