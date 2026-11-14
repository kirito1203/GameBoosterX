package com.gameboosterx.rog.core;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Apps de terceiros usados recentemente (candidatos a ainda estarem na memória). */
public final class AppUsage {

    public static final class App {
        public String pkg;
        public String label;
        public long foregroundMs;
        public long lastUsed;
    }

    private AppUsage() {}

    /**
     * @param windowMs   janela de tempo considerada
     * @param exclude    pacotes a ignorar (ex.: jogos selecionados)
     */
    public static List<App> recent(Context c, long windowMs, Set<String> exclude, int max) {
        List<App> out = new ArrayList<>();
        if (!Perms.usageStats(c)) return out;
        try {
            UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
            long now = System.currentTimeMillis();
            Map<String, UsageStats> map = usm.queryAndAggregateUsageStats(now - windowMs, now);
            PackageManager pm = c.getPackageManager();
            String launcher = defaultLauncher(c);
            List<UsageStats> list = new ArrayList<>(map.values());
            list.sort((a, b) -> Long.compare(b.getLastTimeUsed(), a.getLastTimeUsed()));
            for (UsageStats us : list) {
                if (out.size() >= max) break;
                String pkg = us.getPackageName();
                if (us.getLastTimeUsed() < now - windowMs) continue;
                if (us.getTotalTimeInForeground() < 5_000) continue;
                if (exclude != null && exclude.contains(pkg)) continue;
                if (isEssential(c, pm, pkg, launcher)) continue;
                App a = new App();
                a.pkg = pkg;
                try {
                    a.label = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
                } catch (Exception e) {
                    continue;
                }
                a.foregroundMs = us.getTotalTimeInForeground();
                a.lastUsed = us.getLastTimeUsed();
                out.add(a);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static String defaultLauncher(Context c) {
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo ri = c.getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            return ri != null ? ri.activityInfo.packageName : "";
        } catch (Exception e) {
            return "";
        }
    }

    /** Componentes do Android/fabricante que nunca devem ser fechados. */
    public static boolean isEssential(Context c, PackageManager pm, String pkg, String launcher) {
        if (pkg.equals(c.getPackageName()) || pkg.equals(launcher)) return true;
        String p = pkg.toLowerCase(Locale.ROOT);
        if (p.equals("android") || p.startsWith("com.android.") || p.contains("systemui")
                || p.startsWith("com.google.android.gms") || p.contains("inputmethod") || p.contains("keyboard")
                || p.startsWith("com.oplus.") || p.startsWith("com.coloros.") || p.startsWith("com.heytap.")
                || p.startsWith("com.realme.") || p.startsWith("com.google.android.apps.nexuslauncher")) return true;
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            boolean system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            boolean updated = (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
            return system && !updated;
        } catch (Exception e) {
            return true;
        }
    }
}
