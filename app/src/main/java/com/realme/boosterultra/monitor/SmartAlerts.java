package com.realme.boosterultra.monitor;

import com.realme.boosterultra.*;
import com.realme.boosterultra.engine.*;
import com.realme.boosterultra.ai.*;
import com.realme.boosterultra.gaming.*;
import com.realme.boosterultra.monitor.*;
import com.realme.boosterultra.utils.*;
import com.realme.boosterultra.ui.*;


import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/** Smart Alerts: avisos de temperatura e bateria. */
public final class SmartAlerts {
    private SmartAlerts() {}

    public static void check(Context c, int level, float temp, boolean charging) {
        SharedPreferences p = ProfileManager.prefs(c);
        if (!p.getBoolean("alerts_on", false)) return;
        long now = System.currentTimeMillis();
        int tempLimit = p.getInt("alert_temp", 42);
        int lowLimit = p.getInt("alert_low", 15);

        if (temp >= tempLimit && now - p.getLong("al_temp_t", 0) > 10 * 60 * 1000L) {
            p.edit().putLong("al_temp_t", now).apply();
            post(c, 101, "🌡 Celular quente: " + Math.round(temp) + "°C",
                    charging ? "Tire da tomada e dê uma pausa no jogo." : "Dê uma pausa ou abaixe os gráficos do jogo.");
        }
        if (!charging && level <= lowLimit && !p.getBoolean("al_low_done", false)) {
            p.edit().putBoolean("al_low_done", true).apply();
            post(c, 102, "🔋 Bateria baixa: " + level + "%", "Ative a economia ou coloque para carregar.");
        }
        if (charging) p.edit().putBoolean("al_low_done", false).apply();
        if (charging && level >= 100 && p.getBoolean("alert_full", true) && !p.getBoolean("al_full_done", false)) {
            p.edit().putBoolean("al_full_done", true).apply();
            post(c, 103, "🔌 Bateria cheia", "Pode tirar da tomada para cuidar da bateria.");
        }
        if (!charging) p.edit().putBoolean("al_full_done", false).apply();
    }

    public static void post(Context c, int id, String title, String text) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        nm.createNotificationChannel(new NotificationChannel("alerts", "Smart Alerts", NotificationManager.IMPORTANCE_HIGH));
        PendingIntent open = PendingIntent.getActivity(c, id, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(c, "alerts")
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build();
        try { nm.notify(id, n); } catch (Exception ignored) {}
    }

    /** SMART AUTO PILOT: 0 desligado, 1 desempenho, 2 equilibrado, 3 bateria. */
    public static void setPilot(Context c, int mode) {
        SharedPreferences.Editor e = ProfileManager.prefs(c).edit().putInt("pilot_mode", mode);
        if (mode == 1) e.putBoolean("auto_detect", true).putBoolean("eco_on", true).putInt("eco_threshold", 15)
                .putBoolean("alerts_on", true).putBoolean("heat_guard", true).putInt("heat_level", 44).putBoolean("hist_on", true);
        else if (mode == 2) e.putBoolean("auto_detect", true).putBoolean("eco_on", true).putInt("eco_threshold", 20)
                .putBoolean("alerts_on", true).putBoolean("heat_guard", true).putInt("heat_level", 42).putBoolean("hist_on", true);
        else if (mode == 3) e.putBoolean("auto_detect", false).putBoolean("eco_on", true).putInt("eco_threshold", 30)
                .putBoolean("alerts_on", true).putBoolean("heat_guard", true).putInt("heat_level", 40).putBoolean("hist_on", true);
        else e.putBoolean("auto_detect", false).putBoolean("heat_guard", false);
        e.apply();
        sync(c);
    }

    /** Liga/desliga o serviço de vigia conforme as opções. */
    public static void sync(Context c) {
        SharedPreferences p = ProfileManager.prefs(c);
        Intent i = new Intent(c, WatchService.class);
        try {
            if (p.getBoolean("eco_on", false) || p.getBoolean("alerts_on", false) || p.getBoolean("hist_on", false)
                    || p.getBoolean("auto_detect", false) || p.getBoolean("heat_guard", false)) {
                c.startForegroundService(i);
            } else {
                c.stopService(i);
            }
        } catch (Exception ignored) {}
    }
}
