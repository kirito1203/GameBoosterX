package com.realme.boosterultra.gaming;

import com.realme.boosterultra.*;
import com.realme.boosterultra.engine.*;
import com.realme.boosterultra.ai.*;
import com.realme.boosterultra.gaming.*;
import com.realme.boosterultra.monitor.*;
import com.realme.boosterultra.utils.*;
import com.realme.boosterultra.ui.*;


import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Painel flutuante + gravação da sessão (FPS STABILITY, temperatura, RAM, ping) para o GAME MEMORY. */
public final class OverlayPanel {
    private static OverlayPanel current;

    private final Context c;
    private final String pkg;
    private final Handler h = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private TextView view;
    private volatile boolean running = true;
    private volatile int ping = -1;
    private boolean compact = false;

    // FPS / fluidez
    private int frames = 0, janksAcc = 0, hz = 0, janks = 0;
    private long secStart = 0, lastFrame = 0, expectedNs = 16_666_667L;
    // sessão
    private final long startT = System.currentTimeMillis();
    private final int batStart;
    private int samples = 0, fpsSecs = 0, smoothSecs = 0, totalJanks = 0, pingN = 0;
    private float tempSum = 0, tempMax = 0, ramSum = 0, hzSum = 0, hzMin = 999, pingSum = 0;

    private final Choreographer.FrameCallback fpsCb = new Choreographer.FrameCallback() {
        @Override public void doFrame(long t) {
            if (!running) return;
            if (lastFrame != 0 && t - lastFrame > expectedNs * 3 / 2) janksAcc++;
            lastFrame = t;
            frames++;
            if (secStart == 0) secStart = t;
            if (t - secStart >= 1_000_000_000L) {
                hz = Math.round(frames * 1_000_000_000f / (t - secStart));
                janks = janksAcc;
                fpsSecs++; hzSum += hz; hzMin = Math.min(hzMin, hz); totalJanks += janks;
                if (janks == 0) smoothSecs++;
                frames = 0; janksAcc = 0; secStart = t;
            }
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            sample();
            update();
            h.postDelayed(this, 2000);
        }
    };

    private OverlayPanel(Context ctx, String pkg) {
        this.c = ctx.getApplicationContext();
        this.pkg = pkg;
        this.batStart = DeviceInfo.battery(c).level;
    }

    public static synchronized void startFor(Context ctx, String pkg) {
        if (current != null && current.pkg != null && current.pkg.equals(pkg)) return;
        stopAll();
        current = new OverlayPanel(ctx, pkg);
        current.begin();
    }

    public static synchronized void stopAll() {
        if (current != null) { current.end(); current = null; }
    }

    private boolean opt(String key) {
        return ProfileManager.opt(ProfileManager.prefs(c), pkg, key, true);
    }

    private void begin() {
        MainActivity.loadTheme(c);
        if (opt("opt_overlay") && Settings.canDrawOverlays(c)) addOverlay();
        if (opt("opt_fps")) {
            try {
                float rr = ((WindowManager) c.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRefreshRate();
                if (rr > 1) expectedNs = (long) (1_000_000_000L / rr);
            } catch (Exception ignored) {}
            Choreographer.getInstance().postFrameCallback(fpsCb);
        }
        h.post(tick);
        new Thread(() -> {
            long lastClean = SystemClock.elapsedRealtime();
            while (running) {
                ping = NetworkMonitor.tcpPing("8.8.8.8", 53, 1500);
                if (ping >= 0) { pingSum += ping; pingN++; }
                if (opt("opt_loop") && SystemClock.elapsedRealtime() - lastClean > 90000) {
                    Optimizer.killBackground(c, pkg);
                    lastClean = SystemClock.elapsedRealtime();
                }
                try { Thread.sleep(5000); } catch (InterruptedException e) { return; }
            }
        }).start();
    }

    private void sample() {
        DeviceInfo.Battery b = DeviceInfo.battery(c);
        long[] r = DeviceInfo.ram(c);
        samples++;
        tempSum += b.temp;
        tempMax = Math.max(tempMax, b.temp);
        ramSum += DeviceInfo.pct(r[0] - r[1], r[0]);
    }

    private void end() {
        running = false;
        h.removeCallbacksAndMessages(null);
        Choreographer.getInstance().removeFrameCallback(fpsCb);
        if (view != null && wm != null) {
            try { wm.removeView(view); } catch (Exception ignored) {}
        }
        view = null;
        long now = System.currentTimeMillis();
        if (pkg != null && now - startT >= 60_000 && samples > 0) {
            GameMemory.Session s = new GameMemory.Session();
            s.pkg = pkg;
            s.label = label(pkg);
            s.start = startT; s.end = now;
            s.avgTemp = tempSum / samples; s.maxTemp = tempMax; s.avgRam = ramSum / samples;
            if (fpsSecs > 0) {
                s.avgHz = hzSum / fpsSecs; s.minHz = hzMin;
                s.stability = smoothSecs * 100f / fpsSecs;
                s.janksPerMin = totalJanks * 60f / fpsSecs;
            } else {
                s.stability = -1;
            }
            s.avgPing = pingN > 0 ? pingSum / pingN : -1;
            s.batStart = batStart; s.batEnd = DeviceInfo.battery(c).level;
            GameMemory.save(c, s);
        }
    }

    private String label(String p) {
        try {
            android.content.pm.PackageManager pm = c.getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(p, 0)));
        } catch (Exception e) {
            return p;
        }
    }

    private int dp(int v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    private void addOverlay() {
        wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        view = new TextView(c);
        view.setTextColor(MainActivity.ACCENT);
        view.setTextSize(11);
        view.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        view.setPadding(dp(8), dp(5), dp(8), dp(5));
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xCC05080A);
        g.setCornerRadius(dp(10));
        g.setStroke(dp(1), MainActivity.a(0x88));
        view.setBackground(g);
        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = ProfileManager.prefs(c).getInt("ox", dp(8));
        lp.y = ProfileManager.prefs(c).getInt("oy", dp(60));
        view.setOnTouchListener(new View.OnTouchListener() {
            int sx, sy; float tx, ty; long down; boolean moved;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        sx = lp.x; sy = lp.y; tx = e.getRawX(); ty = e.getRawY();
                        down = SystemClock.uptimeMillis(); moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (e.getRawX() - tx), dy = (int) (e.getRawY() - ty);
                        if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) moved = true;
                        if (moved) {
                            lp.x = sx + dx; lp.y = sy + dy;
                            try { wm.updateViewLayout(view, lp); } catch (Exception ignored) {}
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (moved) ProfileManager.prefs(c).edit().putInt("ox", lp.x).putInt("oy", lp.y).apply();
                        else if (SystemClock.uptimeMillis() - down > 700) ProfileManager.stop(c);
                        else { compact = !compact; update(); }
                        return true;
                }
                return false;
            }
        });
        try { wm.addView(view, lp); } catch (Exception ex) { view = null; }
    }

    private void update() {
        if (view == null) return;
        long[] r = DeviceInfo.ram(c);
        int ru = DeviceInfo.pct(r[0] - r[1], r[0]);
        DeviceInfo.Battery b = DeviceInfo.battery(c);
        String pg = ping < 0 ? "--" : ping + "ms";
        String t;
        if (compact) {
            t = String.format(Locale.US, "%.0f°C  %s%s", b.temp, pg, hz > 0 ? "  " + hz + "fps" : "");
        } else {
            String clock = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
            t = String.format(Locale.US, "● REC %s  RAM %d%%\n🌡 %.1f°C   🔋 %d%%\n📶 ping %s", clock, ru, b.temp, b.level, pg);
            if (hz > 0) t += String.format(Locale.US, "\n🎞 %dFPS tela  travadas %d", hz, janks);
            if (DeviceInfo.thermalStatus(c) >= 2) t += "\n⚠ AQUECENDO";
        }
        view.setText(t);
        view.setTextColor(b.temp >= 42 ? MainActivity.BAD : b.temp >= 38 ? MainActivity.WARN : MainActivity.ACCENT);
    }
}
