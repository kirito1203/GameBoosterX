package com.realme.boosterultra.gaming;

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
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Notificação do Modo Jogo. O painel e a gravação da sessão ficam no OverlayPanel. */
public class OverlayService extends Service {
    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        MainActivity.loadTheme(this);
        goForeground();
        OverlayPanel.startFor(this, ProfileManager.prefs(this).getString("game", null));
    }

    @Override public int onStartCommand(Intent i, int flags, int id) {
        if (i != null && "STOP".equals(i.getAction())) {
            ProfileManager.stop(this);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void goForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.createNotificationChannel(new NotificationChannel("turbo", "Modo Jogo",
                    NotificationManager.IMPORTANCE_LOW));
        }
        int fl = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, OverlayService.class).setAction("STOP"), fl);
        PendingIntent open = PendingIntent.getActivity(this, 2, new Intent(this, MainActivity.class), fl);
        Notification n = new Notification.Builder(this, "turbo")
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("🎮 Modo Jogo ativo")
                .setContentText("Toque em Encerrar para restaurar tudo")
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_stat), "Encerrar", stop).build())
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(7, n);
        }
    }

    @Override public void onDestroy() {
        OverlayPanel.stopAll();
        super.onDestroy();
    }
}
