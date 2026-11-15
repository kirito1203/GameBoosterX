package com.pulsesolar.app.modules.guard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import com.pulsesolar.app.R;
import com.pulsesolar.app.core.Format;
import com.pulsesolar.app.core.SystemInfo;

/**
 * Serviço em primeiro plano do Solar Guard. Escuta as mudanças reais de bateria enviadas
 * pelo Android e avisa quando a temperatura passa do limite escolhido ou a bateria fica baixa.
 */
public class GuardService extends Service {

    public static final String PREFS = "solar_guard";
    public static final String KEY_TEMP_LIMIT = "temp_limit";
    public static final String KEY_LOW_LEVEL = "low_level";
    public static final int DEFAULT_TEMP_LIMIT = 40;
    public static final int DEFAULT_LOW_LEVEL = 15;

    private static final String CH_STATUS = "guard_status";
    private static final String CH_ALERT = "guard_alert";
    private static final int ID_STATUS = 1001;
    private static final int ID_ALERT_TEMP = 1002;
    private static final int ID_ALERT_LOW = 1003;
    private static final int ID_ALERT_THERMAL = 1004;
    private static final long ALERT_COOLDOWN_MS = 10 * 60 * 1000L;

    public static volatile boolean running = false;

    private NotificationManager nm;
    private long lastTempAlert = 0;
    private boolean lowAlerted = false;
    private int lastThermal = -1;
    private PowerManager.OnThermalStatusChangedListener thermalListener;

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            onBattery(intent);
        }
    };

    public static void start(Context c) {
        Intent i = new Intent(c, GuardService.class);
        c.startForegroundService(i);
    }

    public static void stop(Context c) {
        c.stopService(new Intent(c, GuardService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createChannels();
        Notification n = buildStatus("Iniciando monitoramento…");
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID_STATUS, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(ID_STATUS, n);
        }
        running = true;
        registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        if (Build.VERSION.SDK_INT >= 29) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            thermalListener = status -> {
                if (status >= PowerManager.THERMAL_STATUS_SEVERE && lastThermal < PowerManager.THERMAL_STATUS_SEVERE) {
                    alert(ID_ALERT_THERMAL, "Aparelho limitando por temperatura",
                            "Estado térmico: " + SystemInfo.thermalText(status)
                                    + ". O sistema está reduzindo o desempenho para esfriar.");
                }
                lastThermal = status;
            };
            pm.addThermalStatusListener(thermalListener);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        try {
            unregisterReceiver(batteryReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        if (Build.VERSION.SDK_INT >= 29 && thermalListener != null) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            pm.removeThermalStatusListener(thermalListener);
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void onBattery(Intent i) {
        int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int pct = (level >= 0 && scale > 0) ? Math.round(level * 100f / scale) : -1;
        int t = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
        float temp = t == Integer.MIN_VALUE ? Float.NaN : t / 10f;
        int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;

        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        int limit = sp.getInt(KEY_TEMP_LIMIT, DEFAULT_TEMP_LIMIT);
        int low = sp.getInt(KEY_LOW_LEVEL, DEFAULT_LOW_LEVEL);

        String text = (pct >= 0 ? pct + "%" : "—") + "  •  "
                + (Float.isNaN(temp) ? "—" : Format.one(temp) + " °C") + "  •  "
                + SystemInfo.statusText(status) + "  •  alerta em " + limit + " °C";
        nm.notify(ID_STATUS, buildStatus(text));

        long now = System.currentTimeMillis();
        if (!Float.isNaN(temp) && temp >= limit && now - lastTempAlert > ALERT_COOLDOWN_MS) {
            lastTempAlert = now;
            alert(ID_ALERT_TEMP, "Bateria a " + Format.one(temp) + " °C",
                    "Passou do seu limite de " + limit + " °C. Pause o jogo, tire a capa e evite usar carregando.");
        }

        if (pct >= 0 && pct <= low && !charging) {
            if (!lowAlerted) {
                lowAlerted = true;
                alert(ID_ALERT_LOW, "Bateria em " + pct + "%",
                        "Com bateria baixa o sistema pode ativar a economia de energia e reduzir o desempenho.");
            }
        } else if (charging || pct > low + 5) {
            lowAlerted = false;
        }
    }

    private void createChannels() {
        NotificationChannel status = new NotificationChannel(CH_STATUS, "Solar Guard (monitor)",
                NotificationManager.IMPORTANCE_LOW);
        status.setDescription("Mostra bateria e temperatura enquanto o monitor está ligado.");
        NotificationChannel alert = new NotificationChannel(CH_ALERT, "Solar Guard (alertas)",
                NotificationManager.IMPORTANCE_HIGH);
        alert.setDescription("Avisos de superaquecimento e bateria baixa.");
        nm.createNotificationChannel(status);
        nm.createNotificationChannel(alert);
    }

    private PendingIntent openGuard() {
        Intent i = new Intent(this, SolarGuardActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private Notification buildStatus(String text) {
        return new Notification.Builder(this, CH_STATUS)
                .setSmallIcon(R.drawable.ic_stat_sun)
                .setContentTitle("Solar Guard ativo")
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setColor(0xFFFFB300)
                .setContentIntent(openGuard())
                .build();
    }

    private void alert(int id, String title, String text) {
        Notification n = new Notification.Builder(this, CH_ALERT)
                .setSmallIcon(R.drawable.ic_stat_sun)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setColor(0xFFFF6D00)
                .setContentIntent(openGuard())
                .build();
        nm.notify(id, n);
    }
}
