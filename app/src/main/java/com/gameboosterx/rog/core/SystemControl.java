package com.gameboosterx.rog.core;

import android.app.NotificationManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.provider.Settings;

/**
 * Ações permitidas pelo Android sem root, todas dependentes de
 * autorização explícita do usuário.
 */
public final class SystemControl {

    private SystemControl() {}

    // ---------------- Brilho (WRITE_SETTINGS) ----------------

    /** Valor máximo de brilho do aparelho (muitos realme usam escala maior que 255). */
    public static int brightnessMax() {
        try {
            Resources r = Resources.getSystem();
            int id = r.getIdentifier("config_screenBrightnessSettingMaximum", "integer", "android");
            if (id != 0) {
                int v = r.getInteger(id);
                if (v > 0) return v;
            }
        } catch (Exception ignored) {
        }
        return 255;
    }

    /** Brilho atual em % (0-100) ou -1. */
    public static int getBrightnessPercent(Context c) {
        try {
            int v = Settings.System.getInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
            int max = brightnessMax();
            // Caso o aparelho use escala maior que a reportada
            if (v > max) max = v;
            return Math.round(v * 100f / max);
        } catch (Exception e) {
            return -1;
        }
    }

    public static boolean isAutoBrightness(Context c) {
        try {
            return Settings.System.getInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE)
                    == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
        } catch (Exception e) {
            return false;
        }
    }

    /** Define brilho manual em %. Retorna false se não houver permissão. */
    public static boolean setBrightnessPercent(Context c, int pct) {
        if (!Perms.writeSettings(c)) return false;
        try {
            ContentResolver cr = c.getContentResolver();
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            int max = brightnessMax();
            int v = Math.max(1, Math.round(Math.max(1, Math.min(100, pct)) * max / 100f));
            return Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, v);
        } catch (Exception e) {
            return false;
        }
    }

    public static void setAutoBrightness(Context c, boolean auto) {
        if (!Perms.writeSettings(c)) return;
        try {
            Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                    auto ? Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                            : Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        } catch (Exception ignored) {
        }
    }

    // ---------------- Não perturbe ----------------

    public static int getDndFilter(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        return nm == null ? NotificationManager.INTERRUPTION_FILTER_UNKNOWN : nm.getCurrentInterruptionFilter();
    }

    public static boolean setDnd(Context c, boolean on) {
        if (!Perms.dnd(c)) return false;
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.setInterruptionFilter(on ? NotificationManager.INTERRUPTION_FILTER_PRIORITY
                    : NotificationManager.INTERRUPTION_FILTER_ALL);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void restoreDnd(Context c, int filter) {
        if (!Perms.dnd(c) || filter == NotificationManager.INTERRUPTION_FILTER_UNKNOWN) return;
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.setInterruptionFilter(filter);
        } catch (Exception ignored) {
        }
    }

    // ---------------- App em primeiro plano (PACKAGE_USAGE_STATS) ----------------

    private static String lastForeground;

    /** Pacote em primeiro plano (melhor estimativa via eventos de uso) ou null. */
    public static String foregroundPackage(Context c) {
        if (!Perms.usageStats(c)) return null;
        try {
            UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
            long now = System.currentTimeMillis();
            // Na primeira consulta olha 6 h para trás (o jogo pode já estar aberto há tempo)
            long window = lastForeground == null ? 6 * 3600_000L : 60_000L;
            UsageEvents ev = usm.queryEvents(now - window, now);
            UsageEvents.Event e = new UsageEvents.Event();
            String fg = null;
            long fgTime = 0;
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e);
                int t = e.getEventType();
                if (t == UsageEvents.Event.ACTIVITY_RESUMED && e.getTimeStamp() >= fgTime) {
                    fg = e.getPackageName();
                    fgTime = e.getTimeStamp();
                }
            }
            if (fg != null) lastForeground = fg;
            return lastForeground;
        } catch (Exception e) {
            return lastForeground;
        }
    }
}
