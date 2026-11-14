package com.gameboosterx.rog.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.gameboosterx.rog.R;
import com.gameboosterx.rog.core.Perms;
import com.gameboosterx.rog.core.Prefs;
import com.gameboosterx.rog.core.SystemControl;
import com.gameboosterx.rog.core.SystemStats;
import com.gameboosterx.rog.data.GameRepository;
import com.gameboosterx.rog.data.SessionRecorder;
import com.gameboosterx.rog.fps.FpsMonitor;
import com.gameboosterx.rog.ui.MainActivity;
import com.gameboosterx.rog.ui.OptimizeActivity;
import com.gameboosterx.rog.ui.TurboActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Serviço em primeiro plano (sempre com notificação visível) que:
 *  - mostra a Game Sidebar (sobreposição autorizada pelo usuário);
 *  - detecta o jogo em primeiro plano e aplica seu perfil;
 *  - grava a sessão (temperatura, bateria, FPS quando mensurável);
 *  - alerta sobre aquecimento.
 */
public class BoostService extends Service {

    public static final String ACTION_START = "gbx.START";
    public static final String ACTION_SHOW = "gbx.SHOW";
    public static final String ACTION_STOP = "gbx.STOP";

    private static final String CH_MONITOR = "monitor";
    private static final String CH_ALERT = "alert";
    private static final int NOTIF_ID = 1001;
    private static final int ALERT_ID = 1002;
    private static final long GAME_EXIT_GRACE_MS = 15_000;

    // ---------- Estado compartilhado com as telas (somente leitura nelas) ----------
    public static final class Live {
        public volatile boolean running;
        public volatile String foreground;
        public volatile String sessionPkg;
        public volatile String sessionLabel;
        public volatile long sessionStart;
        public volatile float fps = Float.NaN;
        public volatile String fpsError;
        public volatile float tempC = Float.NaN;
        public volatile int thermal = -1;
        public final float[] fpsHistory = new float[120];
        public final float[] tempHistory = new float[120];
        public volatile int historyCount;

        synchronized void push(float fps, float temp) {
            System.arraycopy(fpsHistory, 1, fpsHistory, 0, fpsHistory.length - 1);
            System.arraycopy(tempHistory, 1, tempHistory, 0, tempHistory.length - 1);
            fpsHistory[fpsHistory.length - 1] = fps;
            tempHistory[tempHistory.length - 1] = temp;
            if (historyCount < fpsHistory.length) historyCount++;
        }

        public synchronized float[] copyFps() { return trimmed(fpsHistory); }
        public synchronized float[] copyTemp() { return trimmed(tempHistory); }

        private float[] trimmed(float[] src) {
            float[] out = new float[historyCount];
            System.arraycopy(src, src.length - historyCount, out, 0, historyCount);
            return out;
        }
    }

    public static final Live LIVE = new Live();

    public static void start(Context c) {
        Intent i = new Intent(c, BoostService.class).setAction(ACTION_START);
        c.startForegroundService(i);
    }

    public static void stop(Context c) {
        c.startService(new Intent(c, BoostService.class).setAction(ACTION_STOP));
    }

    // ---------- Campos ----------
    private final Handler main = new Handler(Looper.getMainLooper());
    private HandlerThread worker;
    private Handler bg;
    private WindowManager wm;
    private View overlay;
    private WindowManager.LayoutParams lp;
    private boolean expanded;
    private GameRepository games;
    private FpsMonitor fpsMonitor;
    private volatile SessionRecorder recorder;
    private long lastSeenGameAt;
    private long lastAlertAt;

    // Estado anterior para restaurar ao sair do jogo
    private int savedDnd = NotificationManager.INTERRUPTION_FILTER_UNKNOWN;
    private int savedBrightness = -1;
    private boolean savedAuto;
    private boolean profileApplied;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        games = new GameRepository(this);
        fpsMonitor = new FpsMonitor(this);
        createChannels();
        worker = new HandlerThread("gbx-monitor");
        worker.start();
        bg = new Handler(worker.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : ACTION_START;
        if (ACTION_STOP.equals(action)) {
            stopSelfSafely();
            return START_NOT_STICKY;
        }
        goForeground();
        if (!LIVE.running) {
            LIVE.running = true;
            bg.post(tick);
        }
        if (Perms.overlay(this)) {
            showOverlay();
            if (ACTION_SHOW.equals(action)) setExpanded(true);
        } else {
            Toast.makeText(this, "Autorize a sobreposição para ver a barra. O monitor continua ativo.",
                    Toast.LENGTH_LONG).show();
        }
        return START_NOT_STICKY;
    }

    private void goForeground() {
        Notification n = buildNotification("Monitor ativo • toque para abrir a barra");
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIF_ID, n);
            }
        } catch (Exception e) {
            // Ex.: início em segundo plano não permitido. Encerra com segurança.
            stopSelf();
        }
    }

    private void stopSelfSafely() {
        endSession();
        LIVE.running = false;
        removeOverlay();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        LIVE.running = false;
        endSession();
        removeOverlay();
        if (bg != null) bg.removeCallbacksAndMessages(null);
        if (worker != null) worker.quitSafely();
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // =====================================================================
    // Loop de monitoramento (thread de fundo)
    // =====================================================================
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!LIVE.running) return;
            try {
                sample();
            } catch (Exception ignored) {
            }
            bg.postDelayed(this, Math.max(1, Prefs.sampleSeconds(BoostService.this)) * 1000L);
        }
    };

    private void sample() {
        SystemStats.BatteryInfo b = SystemStats.battery(this);
        SystemStats.ThermalInfo t = SystemStats.thermal(this);
        SystemStats.RamInfo r = SystemStats.ram(this);

        String fg = SystemControl.foregroundPackage(this);
        LIVE.foreground = fg;
        boolean fgIsGame = fg != null && !fg.equals(getPackageName()) && games.isSelected(fg);
        long now = System.currentTimeMillis();

        if (fgIsGame) {
            lastSeenGameAt = now;
            if (recorder == null || !recorder.pkg().equals(fg)) {
                endSession();
                if (Prefs.autoDetect(this)) startSession(fg);
            }
        } else if (recorder != null && now - lastSeenGameAt > GAME_EXIT_GRACE_MS) {
            endSession();
        }

        // FPS: mede o app em primeiro plano (exceto o próprio Game Booster)
        float fps = Float.NaN;
        String measurePkg = recorder != null ? recorder.pkg()
                : (fg != null && !fg.equals(getPackageName()) ? fg : null);
        if (measurePkg != null && fpsMonitor.available()) {
            fps = fpsMonitor.measure(measurePkg);
            LIVE.fpsError = fpsMonitor.lastError();
        } else {
            LIVE.fpsError = fpsMonitor.available() ? "Nenhum jogo em primeiro plano" : "Fonte de FPS indisponível";
        }

        LIVE.fps = fps;
        LIVE.tempC = b.tempC;
        LIVE.thermal = t.status;
        LIVE.push(fps, b.tempC);

        if (recorder != null && fgIsGame) {
            recorder.addSample(b.tempC, b.level, fps, t.status, r.usedPercent());
        }

        checkHeat(b, t);

        final String fpsTxt = Float.isNaN(fps) ? "--" : String.format(Locale.US, "%.0f", fps);
        final String tempTxt = b.hasTemp() ? String.format(Locale.US, "%.1f°C", b.tempC) : "--";
        final String ramTxt = r.usedPercent() + "%";
        final String batTxt = b.level >= 0 ? b.level + "%" : "--";
        final String thermalTxt = "Térmico: " + SystemStats.thermalLabel(t.status)
                + (recorder != null ? "  •  REC " + recorder.label() : "");
        main.post(() -> updateOverlay(fpsTxt, tempTxt, ramTxt, batTxt, thermalTxt));
    }

    private synchronized void startSession(String pkg) {
        String label = games.label(pkg);
        recorder = new SessionRecorder(pkg, label);
        LIVE.sessionPkg = pkg;
        LIVE.sessionLabel = label;
        LIVE.sessionStart = recorder.startedAt();
        GameRepository.Profile p = games.profile(pkg);
        main.post(() -> applyProfile(p));
        updateNotification("Gravando sessão: " + label);
    }

    private synchronized void endSession() {
        if (recorder == null) return;
        SessionRecorder rec = recorder;
        recorder = null;
        LIVE.sessionPkg = null;
        LIVE.sessionLabel = null;
        GameRepository.Profile p = games.profile(rec.pkg());
        if (p.recordSession) rec.finish(this);
        // Na thread principal restaura na hora (o serviço pode estar sendo destruído)
        if (Looper.myLooper() == Looper.getMainLooper()) restoreProfile();
        else main.post(this::restoreProfile);
        updateNotification("Monitor ativo • toque para abrir a barra");
    }

    // ---------- Perfil do jogo ----------
    private void applyProfile(GameRepository.Profile p) {
        if (profileApplied) return;
        profileApplied = true;
        if (p.dnd || Prefs.turboActive(this)) {
            savedDnd = SystemControl.getDndFilter(this);
            SystemControl.setDnd(this, true);
        }
        if (p.brightness >= 0 && Perms.writeSettings(this)) {
            savedAuto = SystemControl.isAutoBrightness(this);
            savedBrightness = SystemControl.getBrightnessPercent(this);
            SystemControl.setBrightnessPercent(this, p.brightness);
        }
        if (p.openSidebar && Perms.overlay(this)) showOverlay();
    }

    private void restoreProfile() {
        if (!profileApplied) return;
        profileApplied = false;
        if (savedDnd != NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {
            SystemControl.restoreDnd(this, savedDnd);
            savedDnd = NotificationManager.INTERRUPTION_FILTER_UNKNOWN;
        }
        if (savedBrightness >= 0) {
            SystemControl.setBrightnessPercent(this, savedBrightness);
            if (savedAuto) SystemControl.setAutoBrightness(this, true);
            savedBrightness = -1;
        }
    }

    // ---------- Aquecimento ----------
    private void checkHeat(SystemStats.BatteryInfo b, SystemStats.ThermalInfo t) {
        if (!Prefs.heatAlerts(this)) return;
        float warn = Prefs.tempWarn(this);
        if (Prefs.turboActive(this)) warn -= 1f; // Turbo: alerta um pouco mais cedo
        int lvl = SystemStats.heatLevel(b, t, warn);
        long now = System.currentTimeMillis();
        if (lvl >= 2 && now - lastAlertAt > 5 * 60_000L) {
            lastAlertAt = now;
            String msg = lvl >= 3
                    ? "Aquecimento alto. Pause o jogo por alguns minutos para evitar queda de desempenho."
                    : "O aparelho está esquentando. Considere reduzir gráficos/FPS ou fazer uma pausa.";
            if (b.hasTemp()) msg += String.format(Locale.US, " (bateria %.1f°C)", b.tempC);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null && Perms.notifications(this)) {
                Notification n = new Notification.Builder(this, CH_ALERT)
                        .setSmallIcon(R.drawable.ic_thermal)
                        .setContentTitle("Thermal Control")
                        .setContentText(msg)
                        .setStyle(new Notification.BigTextStyle().bigText(msg))
                        .setAutoCancel(true)
                        .build();
                nm.notify(ALERT_ID, n);
            }
        }
    }

    // =====================================================================
    // Notificação
    // =====================================================================
    private void createChannels() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel mon = new NotificationChannel(CH_MONITOR, "Monitor de jogos",
                NotificationManager.IMPORTANCE_LOW);
        mon.setDescription("Indica que a barra e o monitor estão ativos");
        NotificationChannel al = new NotificationChannel(CH_ALERT, "Alertas de temperatura",
                NotificationManager.IMPORTANCE_HIGH);
        nm.createNotificationChannel(mon);
        nm.createNotificationChannel(al);
    }

    private Notification buildNotification(String text) {
        PendingIntent show = PendingIntent.getService(this, 1,
                new Intent(this, BoostService.class).setAction(ACTION_SHOW),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 2,
                new Intent(this, BoostService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent app = PendingIntent.getActivity(this, 3,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CH_MONITOR)
                .setSmallIcon(R.drawable.ic_turbo)
                .setContentTitle("GAME BOOSTER X")
                .setContentText(text)
                .setContentIntent(show)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "Abrir app", app).build())
                .addAction(new Notification.Action.Builder(null, "Parar", stop).build())
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIF_ID, buildNotification(text));
    }

    // =====================================================================
    // Game Sidebar (sobreposição)
    // =====================================================================
    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    @SuppressLint({"InflateParams", "ClickableViewAccessibility"})
    private void showOverlay() {
        if (overlay != null || !Perms.overlay(this)) return;
        overlay = LayoutInflater.from(this).inflate(R.layout.overlay_sidebar, null);
        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = 0;
        lp.y = dp(160);

        View handle = overlay.findViewById(R.id.sbHandle);
        handle.setOnTouchListener(new View.OnTouchListener() {
            float downY;
            int startY;
            boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downY = e.getRawY();
                        startY = lp.y;
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dy = e.getRawY() - downY;
                        if (Math.abs(dy) > dp(6)) moved = true;
                        if (moved) {
                            lp.y = Math.max(0, startY + (int) dy);
                            safeUpdate();
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) setExpanded(!expanded);
                        return true;
                    default:
                        return false;
                }
            }
        });

        overlay.findViewById(R.id.sbTurbo).setOnClickListener(v -> openActivity(TurboActivity.class));
        overlay.findViewById(R.id.sbOptimize).setOnClickListener(v -> openActivity(OptimizeActivity.class));
        overlay.findViewById(R.id.sbSettings).setOnClickListener(v -> {
            setExpanded(false);
            Perms.open(this, new Intent(Settings.ACTION_SETTINGS));
        });
        overlay.findViewById(R.id.sbDnd).setOnClickListener(v -> toggleDnd());
        overlay.findViewById(R.id.sbHide).setOnClickListener(v -> setExpanded(false));
        overlay.findViewById(R.id.sbClose).setOnClickListener(v -> removeOverlay());
        overlay.findViewById(R.id.sbStop).setOnClickListener(v -> stopSelfSafely());

        setupBrightness();
        fillShortcuts();
        updateDndLabel();

        try {
            wm.addView(overlay, lp);
        } catch (Exception e) {
            overlay = null;
        }
    }

    private void setupBrightness() {
        SeekBar sb = overlay.findViewById(R.id.sbBright);
        TextView label = overlay.findViewById(R.id.sbBrightLabel);
        int cur = SystemControl.getBrightnessPercent(this);
        sb.setMax(100);
        sb.setProgress(Math.max(0, cur));
        label.setText(Perms.writeSettings(this)
                ? "Brilho " + Math.max(0, cur) + "%"
                : "Brilho (autorize em Configurações do app)");
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (!fromUser) return;
                if (SystemControl.setBrightnessPercent(BoostService.this, p)) {
                    label.setText("Brilho " + p + "%");
                } else {
                    label.setText("Sem permissão para alterar o brilho");
                }
            }

            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
    }

    private void fillShortcuts() {
        LinearLayout row = overlay.findViewById(R.id.sbApps);
        row.removeAllViews();
        Set<String> pkgs = Prefs.sidebarApps(this);
        List<String> list = new ArrayList<>(pkgs);
        if (list.isEmpty()) {
            // Sem atalhos definidos: usa os jogos selecionados
            list.addAll(games.selectedGames());
        }
        int added = 0;
        for (String pkg : list) {
            if (added >= 5) break;
            Drawable icon = games.icon(pkg);
            if (icon == null) continue;
            ImageView iv = new ImageView(this);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(40), dp(40));
            p.setMargins(dp(4), 0, dp(4), 0);
            iv.setLayoutParams(p);
            iv.setImageDrawable(icon);
            iv.setContentDescription(games.label(pkg));
            iv.setOnClickListener(v -> {
                Intent li = games.launchIntent(pkg);
                if (li != null) {
                    setExpanded(false);
                    Perms.open(this, li);
                }
            });
            row.addView(iv);
            added++;
        }
        if (added == 0) {
            TextView tv = new TextView(this);
            tv.setText("Selecione jogos no app para criar atalhos");
            tv.setTextColor(0xFF9A9A9A);
            tv.setTextSize(11);
            row.addView(tv);
        }
    }

    private void toggleDnd() {
        if (!Perms.dnd(this)) {
            Toast.makeText(this, "Autorize o acesso ao Não Perturbe nas Configurações do app.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        boolean on = SystemControl.getDndFilter(this) == NotificationManager.INTERRUPTION_FILTER_ALL;
        SystemControl.setDnd(this, on);
        updateDndLabel();
    }

    private void updateDndLabel() {
        if (overlay == null) return;
        TextView t = overlay.findViewById(R.id.sbDnd);
        boolean active = SystemControl.getDndFilter(this) != NotificationManager.INTERRUPTION_FILTER_ALL
                && SystemControl.getDndFilter(this) != NotificationManager.INTERRUPTION_FILTER_UNKNOWN;
        t.setText(active ? "NÃO PERT. ✓" : "NÃO PERT.");
    }

    private void openActivity(Class<?> cls) {
        setExpanded(false);
        Intent i = new Intent(this, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Perms.open(this, i);
    }

    private void setExpanded(boolean e) {
        if (overlay == null) return;
        expanded = e;
        View panel = overlay.findViewById(R.id.sbPanel);
        if (e) {
            fillShortcuts();
            updateDndLabel();
            setupBrightness();
            panel.setVisibility(View.VISIBLE);
            panel.setAlpha(0f);
            panel.setTranslationX(dp(40));
            panel.animate().alpha(1f).translationX(0).setDuration(180).start();
        } else {
            panel.setVisibility(View.GONE);
        }
        safeUpdate();
    }

    private void safeUpdate() {
        try {
            if (overlay != null) wm.updateViewLayout(overlay, lp);
        } catch (Exception ignored) {
        }
    }

    private void updateOverlay(String fps, String temp, String ram, String bat, String thermal) {
        if (overlay == null) return;
        ((TextView) overlay.findViewById(R.id.sbFps)).setText(fps);
        ((TextView) overlay.findViewById(R.id.sbTemp)).setText(temp);
        ((TextView) overlay.findViewById(R.id.sbRam)).setText(ram);
        ((TextView) overlay.findViewById(R.id.sbBat)).setText(bat);
        ((TextView) overlay.findViewById(R.id.sbThermal)).setText(thermal);
        TextView mini = overlay.findViewById(R.id.sbHandleFps);
        mini.setText(fps.equals("--") ? "GBX" : fps);
        int heat = LIVE.thermal >= PowerManager.THERMAL_STATUS_MODERATE ? 0xFFFF3B30 : 0xFFFF1744;
        mini.setTextColor(heat);
    }

    private void removeOverlay() {
        if (overlay != null) {
            try {
                wm.removeView(overlay);
            } catch (Exception ignored) {
            }
            overlay = null;
            expanded = false;
        }
    }
}
