package com.pulsesolar.app.core;

import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

/**
 * Modo Jogos com recursos permitidos pelo Android (sem root):
 * Não Perturbe, brilho manual fixo e tempo de tela. Guarda os valores anteriores e os restaura.
 */
public final class GameModeController {

    public static final String PREFS = "solar_gaming";

    public static final class Options {
        public boolean dnd = true;
        public boolean bright = false;
        public boolean timeout = true;
        public int brightValue = 200; // escala 0–255 do Settings.System
    }

    private GameModeController() {
    }

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static Options load(Context c) {
        SharedPreferences sp = p(c);
        Options o = new Options();
        o.dnd = sp.getBoolean("opt_dnd", true);
        o.bright = sp.getBoolean("opt_bright", false);
        o.timeout = sp.getBoolean("opt_timeout", true);
        o.brightValue = sp.getInt("opt_bright_value", 200);
        return o;
    }

    public static void save(Context c, Options o) {
        p(c).edit()
                .putBoolean("opt_dnd", o.dnd)
                .putBoolean("opt_bright", o.bright)
                .putBoolean("opt_timeout", o.timeout)
                .putInt("opt_bright_value", o.brightValue)
                .apply();
    }

    public static boolean isActive(Context c) {
        return p(c).getBoolean("active", false);
    }

    public static boolean brightnessApplied(Context c) {
        return p(c).getBoolean("did_bright", false);
    }

    /** Muda o brilho em tempo real enquanto o modo está ativo. */
    public static void liveBrightness(Context c, int value) {
        if (isActive(c) && brightnessApplied(c) && Perms.canWriteSettings(c)) {
            Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, Math.max(1, value));
        }
    }

    /** @return null se ativou; caso contrário, o motivo da falha. */
    public static String activate(Context c, Options o) {
        if (isActive(c)) return null;
        if (!o.dnd && !o.bright && !o.timeout) return "Ative pelo menos uma opção";
        if (o.dnd && !Perms.hasDndAccess(c)) return "Conceda o acesso ao Não Perturbe primeiro";
        if ((o.bright || o.timeout) && !Perms.canWriteSettings(c)) {
            return "Permita alterar configurações do sistema primeiro";
        }

        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        ContentResolver cr = c.getContentResolver();
        SharedPreferences.Editor e = p(c).edit();
        try {
            if (o.dnd) {
                e.putInt("prev_filter", nm.getCurrentInterruptionFilter());
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
                e.putBoolean("did_dnd", true);
            }
            if (o.bright) {
                e.putInt("prev_bmode", Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL));
                e.putInt("prev_bright", Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128));
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, Math.max(1, o.brightValue));
                e.putBoolean("did_bright", true);
            }
            if (o.timeout) {
                e.putInt("prev_timeout", Settings.System.getInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, 30000));
                Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, 10 * 60 * 1000);
                e.putBoolean("did_timeout", true);
            }
            e.putBoolean("active", true);
            e.commit();
            return null;
        } catch (SecurityException ex) {
            e.commit();
            restore(c);
            return "O sistema recusou a alteração";
        }
    }

    /** Devolve exatamente os valores guardados antes da ativação. */
    public static void restore(Context c) {
        SharedPreferences sp = p(c);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        ContentResolver cr = c.getContentResolver();
        try {
            if (sp.getBoolean("did_dnd", false) && Perms.hasDndAccess(c)) {
                nm.setInterruptionFilter(sp.getInt("prev_filter", NotificationManager.INTERRUPTION_FILTER_ALL));
            }
            if (Perms.canWriteSettings(c)) {
                if (sp.getBoolean("did_bright", false)) {
                    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, sp.getInt("prev_bright", 128));
                    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE,
                            sp.getInt("prev_bmode", Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC));
                }
                if (sp.getBoolean("did_timeout", false)) {
                    Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, sp.getInt("prev_timeout", 30000));
                }
            }
        } catch (SecurityException ignored) {
            // permissão revogada pelo usuário
        }
        sp.edit()
                .putBoolean("active", false)
                .putBoolean("did_dnd", false)
                .putBoolean("did_bright", false)
                .putBoolean("did_timeout", false)
                .apply();
    }
}
