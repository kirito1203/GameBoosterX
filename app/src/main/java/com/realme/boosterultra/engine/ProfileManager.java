package com.realme.boosterultra.engine;

import com.realme.boosterultra.*;
import com.realme.boosterultra.engine.*;
import com.realme.boosterultra.ai.*;
import com.realme.boosterultra.gaming.*;
import com.realme.boosterultra.monitor.*;
import com.realme.boosterultra.utils.*;
import com.realme.boosterultra.ui.*;


import android.app.Activity;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.widget.Toast;

/** Liga/desliga o Modo Jogo e restaura tudo depois. */
public final class ProfileManager {
    private ProfileManager() {}

    public static SharedPreferences prefs(Context c) {
        return com.realme.boosterultra.utils.PreferencesManager.get(c);
    }

    /** Perfil do jogo (GAMING PRO) tem prioridade; senão usa a opção geral. */
    public static boolean opt(SharedPreferences p, String pkg, String key, boolean def) {
        if (pkg != null) {
            String j = p.getString("prof_" + pkg, null);
            if (j != null) {
                try {
                    org.json.JSONObject o = new org.json.JSONObject(j);
                    if (o.has(key)) return o.getBoolean(key);
                } catch (Exception ignored) {}
            }
        }
        return p.getBoolean(key, def);
    }

    public static boolean isActive(Context c) {
        return prefs(c).getBoolean("active", false);
    }

    public static void start(Activity a, String pkg) {
        Intent launch = a.getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch == null) {
            Toast.makeText(a, "Não consegui abrir esse jogo", Toast.LENGTH_SHORT).show();
            return;
        }
        apply(a, pkg);
        prefs(a).edit().putBoolean("auto_game", false).apply();
        try {
            a.startForegroundService(new Intent(a, OverlayService.class));
        } catch (Exception ignored) {}
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        a.startActivity(launch);
    }

    /** Aplica o perfil do jogo (sem abrir o jogo). Usado também pelo AUTO GAME DETECT. */
    public static void apply(Context a, String pkg) {
        SharedPreferences p = prefs(a);
        SharedPreferences.Editor e = p.edit();
        boolean wasActive = p.getBoolean("active", false);

        if (opt(p, pkg, "opt_ram", true)) Optimizer.killBackground(a, pkg);

        if (!wasActive) {
            NotificationManager nm = a.getSystemService(NotificationManager.class);
            if (opt(p, pkg, "opt_dnd", false) && nm != null && nm.isNotificationPolicyAccessGranted()) {
                try {
                    e.putInt("prev_filter", nm.getCurrentInterruptionFilter());
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
                    e.putBoolean("did_dnd", true);
                } catch (Exception ignored) {}
            }
            if (Settings.System.canWrite(a)) {
                ContentResolver cr = a.getContentResolver();
                if (opt(p, pkg, "opt_bright", false)) {
                    try {
                        e.putInt("prev_bmode", Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, 0));
                        e.putInt("prev_bright", Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128));
                        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE,
                                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, 255);
                        e.putBoolean("did_bright", true);
                    } catch (Exception ignored) {}
                }
                if (opt(p, pkg, "opt_timeout", true)) {
                    try {
                        e.putInt("prev_timeout", Settings.System.getInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, 30000));
                        Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, 600000);
                        e.putBoolean("did_timeout", true);
                    } catch (Exception ignored) {}
                }
            }
        }
        e.putBoolean("active", true).putString("game", pkg).apply();
    }

    public static void stop(Context c) {
        SharedPreferences p = prefs(c);
        SharedPreferences.Editor e = p.edit();
        if (p.getBoolean("did_dnd", false)) {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            if (nm != null && nm.isNotificationPolicyAccessGranted()) {
                int f = p.getInt("prev_filter", NotificationManager.INTERRUPTION_FILTER_ALL);
                if (f <= 0) f = NotificationManager.INTERRUPTION_FILTER_ALL;
                try { nm.setInterruptionFilter(f); } catch (Exception ignored) {}
            }
        }
        if (Settings.System.canWrite(c)) {
            ContentResolver cr = c.getContentResolver();
            try {
                if (p.getBoolean("did_bright", false)) {
                    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, p.getInt("prev_bright", 128));
                    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, p.getInt("prev_bmode", 0));
                }
                if (p.getBoolean("did_timeout", false)) {
                    Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, p.getInt("prev_timeout", 30000));
                }
            } catch (Exception ignored) {}
        }
        e.putBoolean("did_dnd", false).putBoolean("did_bright", false).putBoolean("did_timeout", false)
                .putBoolean("active", false).putBoolean("auto_game", false).apply();
        OverlayPanel.stopAll();
        c.stopService(new Intent(c, OverlayService.class));
    }
}
