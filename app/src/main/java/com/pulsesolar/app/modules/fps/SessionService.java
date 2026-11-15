package com.pulsesolar.app.modules.fps;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.TextView;

import com.pulsesolar.app.R;
import com.pulsesolar.app.core.Format;
import com.pulsesolar.app.core.SystemInfo;
import com.pulsesolar.app.core.UsageHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sessão de jogo do Solar FPS Engine (serviço em primeiro plano):
 * grava leituras reais a cada 2 s, mostra um painel sobre o jogo (se permitido),
 * emite alertas térmicos e salva o relatório ao encerrar.
 */
public class SessionService extends Service {

    public static final String ACTION_STOP = "com.pulsesolar.app.action.STOP_SESSION";
    private static final String EXTRA_PKG = "pkg";
    private static final String EXTRA_LABEL = "label";
    private static final String EXTRA_LIMIT = "limit";

    private static final String CH_SESSION = "fps_session";
    private static final String CH_ALERT = "fps_alert";
    private static final int ID_SESSION = 2001;
    private static final int ID_ALERT = 2002;
    private static final int ID_REPORT = 2003;
    private static final long SAMPLE_MS = 2000;
    private static final int MAX_SAMPLES = 900;
    private static final long TEMP_ALERT_COOLDOWN = 5 * 60 * 1000L;

    public static volatile boolean running = false;
    public static volatile String runningLabel = null;
    public static volatile long runningSince = 0;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private NotificationManager nm;
    private Session session;
    private FrameMonitor monitor;
    private FrameMonitor.Stats lastStats;
    private int tick = 0, stride = 1;

    private WindowManager wm;
    private TextView overlay;
    private WindowManager.LayoutParams lp;
    private boolean expanded = true;
    private Session.Point lastPoint;

    private long lastTempAlert = 0;
    private boolean thermalAlerted = false, headroomAlerted = false, capAlerted = false;

    private final Runnable sampler = new Runnable() {
        @Override
        public void run() {
            sample();
            handler.postDelayed(this, SAMPLE_MS);
        }
    };

    public static void start(Context c, String pkg, String label, int tempLimit) {
        Intent i = new Intent(c, SessionService.class)
                .putExtra(EXTRA_PKG, pkg)
                .putExtra(EXTRA_LABEL, label)
                .putExtra(EXTRA_LIMIT, tempLimit);
        c.startForegroundService(i);
    }

    public static void stop(Context c) {
        c.startService(new Intent(c, SessionService.class).setAction(ACTION_STOP));
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            if (!running) {
                // Pode ter sido iniciado só para parar; precisa sair do primeiro plano corretamente.
                stopSelf();
                return START_NOT_STICKY;
            }
            finishSession();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (running) return START_NOT_STICKY;
        begin(intent);
        return START_NOT_STICKY;
    }

    private void begin(Intent intent) {
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createChannels();
        String label = intent != null ? intent.getStringExtra(EXTRA_LABEL) : null;
        Notification n = buildSessionNotification(label);
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID_SESSION, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(ID_SESSION, n);
        }

        session = new Session();
        session.id = session.start = System.currentTimeMillis();
        if (intent != null) {
            session.pkg = intent.getStringExtra(EXTRA_PKG);
            session.label = label;
            session.tempLimit = intent.getIntExtra(EXTRA_LIMIT, 42);
        } else {
            session.tempLimit = 42;
        }
        session.before = Session.Point.capture(this, true);

        running = true;
        runningLabel = label;
        runningSince = session.start;

        monitor = new FrameMonitor(s -> lastStats = s);
        monitor.start();
        addOverlay();
        handler.postDelayed(sampler, SAMPLE_MS);
    }

    // ------------------------------------------------------------ Amostragem

    private void sample() {
        if (session == null) return;
        tick++;
        Session.Point p = Session.Point.capture(this, true);
        if (lastStats != null) {
            p.fps = lastStats.fps;
            p.stability = lastStats.stability();
        }
        lastPoint = p;
        if (tick % stride == 0) {
            session.samples.add(p);
            if (session.samples.size() >= MAX_SAMPLES) {
                // Sessões longas: mantém metade das amostras e passa a gravar com metade da frequência.
                List<Session.Point> kept = new ArrayList<>();
                for (int i = 0; i < session.samples.size(); i += 2) kept.add(session.samples.get(i));
                session.samples.clear();
                session.samples.addAll(kept);
                stride *= 2;
            }
        }
        checkAlerts(p);
        updateOverlay();
    }

    private void checkAlerts(Session.Point p) {
        long now = System.currentTimeMillis();
        if (!Float.isNaN(p.tempC) && p.tempC >= session.tempLimit && now - lastTempAlert > TEMP_ALERT_COOLDOWN) {
            lastTempAlert = now;
            alert("Bateria a " + Format.one(p.tempC) + " °C",
                    "Passou do limite de " + session.tempLimit + " °C. Faça uma pausa, tire a capa e evite jogar carregando.");
        }
        if (p.thermal >= PowerManager.THERMAL_STATUS_SEVERE && !thermalAlerted) {
            thermalAlerted = true;
            alert("Limitação térmica ativa", "O Android está reduzindo o desempenho para esfriar ("
                    + SystemInfo.thermalText(p.thermal) + ").");
        } else if (p.thermal >= 0 && p.thermal < PowerManager.THERMAL_STATUS_MODERATE) {
            thermalAlerted = false;
        }
        if (!Float.isNaN(p.headroom)) {
            if (p.headroom >= 1f && !headroomAlerted) {
                headroomAlerted = true;
                alert("Limitação térmica prevista",
                        String.format(Locale.getDefault(), "Previsão do Android: %.2f. A limitação deve começar em ~10 s.", p.headroom));
            } else if (p.headroom < 0.85f) {
                headroomAlerted = false;
            }
        }
        float cap = p.capRatio();
        if (cap > 0) {
            if (cap < 0.85f && !capAlerted) {
                capAlerted = true;
                alert("Teto da CPU reduzido", "Núcleos rápidos limitados a " + Math.round(cap * 100)
                        + "% da frequência máxima (" + Format.ghz(p.bigCap) + ").");
            } else if (cap >= 0.95f) {
                capAlerted = false;
            }
        }
    }

    private void alert(String title, String text) {
        long offset = (System.currentTimeMillis() - session.start) / 1000;
        session.alerts.add(String.format(Locale.getDefault(), "%02d:%02d  %s — %s",
                offset / 60, offset % 60, title, text));
        Notification n = new Notification.Builder(this, CH_ALERT)
                .setSmallIcon(R.drawable.ic_stat_sun)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setColor(0xFFFF6D00)
                .build();
        nm.notify(ID_ALERT, n);
    }

    // ------------------------------------------------------------ Encerramento

    private void finishSession() {
        if (!running || session == null) return;
        running = false;
        runningLabel = null;
        handler.removeCallbacks(sampler);
        if (monitor != null) {
            monitor.stop();
            session.frames = monitor.totalFrames;
            session.lateFrames = monitor.totalLate;
            session.stalls = monitor.totalStalls;
        }
        session.end = System.currentTimeMillis();
        session.after = Session.Point.capture(this, false);
        if (session.pkg == null) {
            session.pkg = UsageHelper.topForegroundBetween(this, session.start, session.end);
        }
        if (session.pkg != null && session.label == null) {
            try {
                PackageManager pm = getPackageManager();
                session.label = pm.getApplicationLabel(pm.getApplicationInfo(session.pkg, 0)).toString();
            } catch (PackageManager.NameNotFoundException ignored) {
                session.label = session.pkg;
            }
        }
        removeOverlay();
        final Session done = session;
        session = null;
        new Thread(() -> {
            SessionStore.add(getApplicationContext(), done);
            notifyReport(done);
        }, "save-session").start();
    }

    private void notifyReport(Session s) {
        Intent i = new Intent(this, SessionReportActivity.class)
                .putExtra(SessionReportActivity.EXTRA_ID, s.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(this, (int) (s.id & 0x7fffffff), i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, CH_ALERT)
                .setSmallIcon(R.drawable.ic_stat_sun)
                .setContentTitle("Relatório da sessão pronto")
                .setContentText((s.label != null ? s.label + " • " : "") + Format.duration(s.durationMs()))
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build();
        nm.notify(ID_REPORT, n);
    }

    @Override
    public void onDestroy() {
        finishSession();
        removeOverlay();
        super.onDestroy();
    }

    // ------------------------------------------------------------ Notificações

    private void createChannels() {
        NotificationChannel a = new NotificationChannel(CH_SESSION, "Solar FPS Engine (sessão)",
                NotificationManager.IMPORTANCE_LOW);
        a.setDescription("Mostrado enquanto uma sessão de jogo está sendo monitorada.");
        NotificationChannel b = new NotificationChannel(CH_ALERT, "Solar FPS Engine (alertas)",
                NotificationManager.IMPORTANCE_HIGH);
        b.setDescription("Alertas de aquecimento e relatórios de sessão.");
        nm.createNotificationChannel(a);
        nm.createNotificationChannel(b);
    }

    private Notification buildSessionNotification(String label) {
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, SessionService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent open = PendingIntent.getActivity(this, 2,
                new Intent(this, SolarFpsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CH_SESSION)
                .setSmallIcon(R.drawable.ic_stat_sun)
                .setContentTitle("Sessão de jogo em monitoramento")
                .setContentText(label != null ? label : "Gravando temperatura, CPU, memória e pulso do sistema")
                .setOngoing(true)
                .setColor(0xFFFFB300)
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(null, "Encerrar e gerar relatório", stop).build())
                .build();
    }

    // ------------------------------------------------------------ Painel sobre o jogo

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    @SuppressLint("ClickableViewAccessibility")
    private void addOverlay() {
        if (!Settings.canDrawOverlays(this)) return;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlay = new TextView(this);
        overlay.setTypeface(Typeface.MONOSPACE);
        overlay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        overlay.setTextColor(0xFFEAF2FF);
        overlay.setBackgroundResource(R.drawable.bg_overlay);
        overlay.setPadding(dp(10), dp(6), dp(10), dp(6));
        overlay.setText("☀ Pulse Solar\niniciando…");

        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = dp(8);
        lp.y = dp(64);

        overlay.setOnTouchListener(new android.view.View.OnTouchListener() {
            private float downX, downY;
            private int startX, startY;
            private boolean moved;

            @Override
            public boolean onTouch(android.view.View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        startX = lp.x;
                        startY = lp.y;
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                        if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) moved = true;
                        if (moved && overlay != null) {
                            lp.x = startX + (int) dx;
                            lp.y = startY + (int) dy;
                            wm.updateViewLayout(overlay, lp);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) {
                            expanded = !expanded;
                            updateOverlay();
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });
        try {
            wm.addView(overlay, lp);
        } catch (RuntimeException e) {
            overlay = null;
        }
    }

    private void updateOverlay() {
        if (overlay == null || lastPoint == null || session == null) return;
        Session.Point p = lastPoint;
        String temp = Float.isNaN(p.tempC) ? "--" : Format.one(p.tempC);
        boolean hot = (!Float.isNaN(p.tempC) && p.tempC >= session.tempLimit)
                || p.thermal >= PowerManager.THERMAL_STATUS_MODERATE;
        String pulse = p.fps >= 0
                ? String.format(Locale.getDefault(), "%.0f q/s  %.0f%% estável", p.fps, p.stability)
                : "--";
        StringBuilder sb = new StringBuilder();
        if (!expanded) {
            sb.append("☀ ").append(temp).append("°  ").append(p.fps >= 0 ? Math.round(p.stability) + "%" : "");
        } else {
            sb.append("☀ PULSE SOLAR\n");
            sb.append("Pulso*  ").append(pulse).append('\n');
            sb.append("Temp    ").append(temp).append(" °C  ").append(SystemInfo.thermalText(p.thermal)).append('\n');
            if (p.bigHw > 0) {
                sb.append("CPU     ").append(p.bigCur > 0 ? Format.ghz(p.bigCur) : "--");
                float cap = p.capRatio();
                if (cap > 0) sb.append("  teto ").append(Math.round(cap * 100)).append('%');
                sb.append('\n');
            }
            sb.append("RAM     ").append(Format.bytes(p.ramAvail)).append(" livre\n");
            sb.append("*do sistema, não do jogo");
        }
        overlay.setText(sb.toString());
        overlay.setTextColor(hot ? 0xFFFF8A65 : 0xFFEAF2FF);
    }

    private void removeOverlay() {
        if (overlay != null && wm != null) {
            try {
                wm.removeView(overlay);
            } catch (RuntimeException ignored) {
            }
        }
        overlay = null;
    }
}
