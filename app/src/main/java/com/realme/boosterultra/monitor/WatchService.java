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
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;

/** Economia inteligente: entra sozinha quando a bateria baixa e sai quando carrega. */
public class WatchService extends Service {
    private BroadcastReceiver rcv;
    private final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
    private String lastFg = null;
    private long awaySince = 0;

    /** AUTO GAME DETECT: olha qual app está na frente a cada 4 segundos. */
    private final Runnable detect = new Runnable() {
        @Override public void run() {
            try { detectGame(); } catch (Exception ignored) {}
            h.postDelayed(this, 4000);
        }
    };

    private void detectGame() {
        SharedPreferences p = ProfileManager.prefs(this);
        if (!p.getBoolean("auto_detect", false)) return;
        android.app.usage.UsageStatsManager um = getSystemService(android.app.usage.UsageStatsManager.class);
        if (um == null) return;
        long now = System.currentTimeMillis();
        android.app.usage.UsageEvents ev = um.queryEvents(now - 20000, now);
        android.app.usage.UsageEvents.Event e = new android.app.usage.UsageEvents.Event();
        while (ev != null && ev.hasNextEvent()) {
            ev.getNextEvent(e);
            if (e.getEventType() == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND) lastFg = e.getPackageName();
        }
        if (lastFg == null) return;
        boolean active = ProfileManager.isActive(this);
        boolean auto = p.getBoolean("auto_game", false);
        String game = p.getString("game", null);
        if (!active && GameManager.isGame(this, lastFg)) {
            ProfileManager.apply(this, lastFg);
            p.edit().putBoolean("auto_game", true).apply();
            OverlayPanel.startFor(this, lastFg);
            awaySince = 0;
        } else if (active && auto && game != null) {
            boolean away = !lastFg.equals(game) && !lastFg.equals(getPackageName()) && !lastFg.contains("systemui");
            if (away) {
                if (awaySince == 0) awaySince = now;
                else if (now - awaySince > 20000) { ProfileManager.stop(this); awaySince = 0; }
            } else {
                awaySince = 0;
            }
        }
    }

    /** THERMAL INTELLIGENCE: se esquentar demais, reduz brilho e limpa RAM (no máximo a cada 10 min). */
    private void heatGuard(SharedPreferences p, float temp) {
        if (!p.getBoolean("heat_guard", false)) return;
        long now = System.currentTimeMillis();
        if (temp < p.getInt("heat_level", 42) || now - p.getLong("heat_t", 0) < 10 * 60 * 1000L) return;
        p.edit().putLong("heat_t", now).apply();
        int n = Optimizer.killBackground(this, p.getString("game", null));
        String extra = "";
        if (Settings.System.canWrite(this)) {
            ContentResolver cr = getContentResolver();
            int mode = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, 0);
            int br = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128);
            int target = p.getInt("pilot_mode", 0) == 3 ? 80 : 130;
            if (mode == 0 && br > target) {
                try { Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, target); extra = ", baixei o brilho"; }
                catch (Exception ignored) {}
            }
        }
        SmartAlerts.post(this, 104, "🌡 Thermal Intelligence: " + Math.round(temp) + "°C",
                "Esfriando: fechei " + n + " apps em segundo plano" + extra + ".");
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        notifyState("Vigia ativo: bateria, temperatura e histórico");
        rcv = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) { check(i); }
        };
        IntentFilter f = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent sticky;
        if (Build.VERSION.SDK_INT >= 33) sticky = registerReceiver(rcv, f, Context.RECEIVER_NOT_EXPORTED);
        else sticky = registerReceiver(rcv, f);
        if (sticky != null) check(sticky);
        h.post(detect);
    }

    @Override public int onStartCommand(Intent i, int flags, int id) { return START_STICKY; }

    private void notifyState(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(new NotificationChannel("eco", "Vigia em segundo plano",
                NotificationManager.IMPORTANCE_MIN));
        PendingIntent open = PendingIntent.getActivity(this, 3, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, "eco")
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Realme Booster Ultra • Vigia")
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(8, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(8, n);
    }

    private void check(Intent i) {
        int lvl = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
        int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int level = scale > 0 ? lvl * 100 / scale : lvl;
        int st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL;
        SharedPreferences p = ProfileManager.prefs(this);
        float temp = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f;
        SmartAlerts.check(this, level, temp, charging);
        heatGuard(p, temp);
        if (p.getBoolean("hist_on", false) || p.getBoolean("alerts_on", false)) History.record(this, false);
        if (!p.getBoolean("eco_on", false)) {
            if (p.getBoolean("eco_applied", false)) restore(this);
            return;
        }
        int th = p.getInt("eco_threshold", 20);
        boolean applied = p.getBoolean("eco_applied", false);

        if (!applied && !charging && level <= th && !ProfileManager.isActive(this)) {
            if (apply(p)) notifyState("Economia ATIVA (bateria " + level + "%)");
        } else if (applied && (charging || level >= th + 10)) {
            restore(this);
            notifyState("Voltou ao normal (" + (charging ? "carregando" : "bateria " + level + "%") + ")");
        }
    }

    private boolean apply(SharedPreferences p) {
        if (!Settings.System.canWrite(this)) return false;
        ContentResolver cr = getContentResolver();
        try {
            p.edit().putInt("eco_prev_bmode", Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, 0))
                    .putInt("eco_prev_bright", Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128))
                    .putInt("eco_prev_timeout", Settings.System.getInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, 30000))
                    .putInt("eco_prev_haptic", Settings.System.getInt(cr, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1))
                    .putBoolean("eco_applied", true).apply();
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, 0);
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, 50);
            Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, 30000);
            Settings.System.putInt(cr, Settings.System.HAPTIC_FEEDBACK_ENABLED, 0);
            Optimizer.killBackground(this, null);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void restore(Context c) {
        SharedPreferences p = ProfileManager.prefs(c);
        if (!p.getBoolean("eco_applied", false)) return;
        if (Settings.System.canWrite(c)) {
            ContentResolver cr = c.getContentResolver();
            try {
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, p.getInt("eco_prev_bright", 128));
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, p.getInt("eco_prev_bmode", 0));
                Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, p.getInt("eco_prev_timeout", 30000));
                Settings.System.putInt(cr, Settings.System.HAPTIC_FEEDBACK_ENABLED, p.getInt("eco_prev_haptic", 1));
            } catch (Exception ignored) {}
        }
        p.edit().putBoolean("eco_applied", false).apply();
    }

    @Override public void onDestroy() {
        try { unregisterReceiver(rcv); } catch (Exception ignored) {}
        h.removeCallbacksAndMessages(null);
        restore(this);
        super.onDestroy();
    }
}
