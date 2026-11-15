package com.realme.boosterultra.gaming;

import com.realme.boosterultra.*;
import com.realme.boosterultra.engine.*;
import com.realme.boosterultra.ai.*;
import com.realme.boosterultra.gaming.*;
import com.realme.boosterultra.monitor.*;
import com.realme.boosterultra.utils.*;
import com.realme.boosterultra.ui.*;


import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/** Game Launcher: todos os jogos numa tela só, abrindo já no Modo Jogo. */
public class GameLauncherActivity extends Activity {
    private final int BG = MainActivity.BG, ACCENT = MainActivity.ACCENT, TXT = MainActivity.TXT,
            SUB = MainActivity.SUB, BAD = MainActivity.BAD, CARD = MainActivity.CARD, WARN = MainActivity.WARN;
    private final Handler h = new Handler(Looper.getMainLooper());
    private LinearLayout grid;
    private TextView status;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            updateStatus();
            h.postDelayed(this, 3000);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(12));
        frame.addView(root, new FrameLayout.LayoutParams(-1, -1));
        if (Build.VERSION.SDK_INT >= 35) {
            frame.setOnApplyWindowInsetsListener((v, ins) -> {
                android.graphics.Insets i = ins.getInsets(WindowInsets.Type.systemBars());
                root.setPadding(dp(16) + i.left, dp(16) + i.top, dp(16) + i.right, dp(12) + i.bottom);
                return ins;
            });
        }

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView m = new ImageView(this);
        m.setImageResource(R.drawable.mascot);
        head.addView(m, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout ht = new LinearLayout(this);
        ht.setOrientation(LinearLayout.VERTICAL);
        ht.setPadding(dp(12), 0, 0, 0);
        ht.addView(tv("● REC  GAME LAUNCHER", 11, BAD, true));
        ht.addView(tv("ESCOLHA SEU JOGO", 22, ACCENT, true));
        head.addView(ht, new LinearLayout.LayoutParams(0, -2, 1f));
        root.addView(head);

        status = tv("", 12, SUB, false);
        status.setPadding(0, dp(10), 0, dp(6));
        root.addView(status);

        Button turbo = new Button(this);
        turbo.setText("> ULTRA TURBO ANTES DE JOGAR_");
        turbo.setAllCaps(false);
        turbo.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        turbo.setTextColor(0xFF000000);
        turbo.setStateListAnimator(null);
        turbo.setBackground(round(BAD, 4, 0));
        turbo.setOnClickListener(v -> {
            turbo.setEnabled(false);
            turbo.setText("> TURBINANDO..._");
            new Thread(() -> {
                int ok = 0, total = 0;
                for (String[] it : TurboManager.run(this)) { total++; if ("ok".equals(it[0])) ok++; }
                final String txt = "> TURBO ATIVO: " + ok + "/" + total + " NO VERDE ✔";
                h.post(() -> { turbo.setText(txt); turbo.setEnabled(true); updateStatus(); });
            }).start();
        });
        root.addView(turbo, new LinearLayout.LayoutParams(-1, dp(48)));

        ScrollView sc = new ScrollView(this);
        grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(0, dp(12), 0, dp(12));
        sc.addView(grid);
        root.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1f));

        frame.addView(new ScanlineView(this), new FrameLayout.LayoutParams(-1, -1));
        setContentView(frame);
    }

    @Override protected void onResume() {
        super.onResume();
        load();
        h.post(tick);
    }

    @Override protected void onPause() {
        super.onPause();
        h.removeCallbacks(tick);
    }

    private void updateStatus() {
        long[] r = DeviceInfo.ram(this);
        DeviceInfo.Battery b = DeviceInfo.battery(this);
        status.setText(String.format(Locale.US, "RAM %d%%   •   %.1f°C   •   BATERIA %d%%%s",
                DeviceInfo.pct(r[0] - r[1], r[0]), b.temp, b.level, ProfileManager.isActive(this) ? "   •   MODO JOGO ON" : ""));
        status.setTextColor(b.temp >= 42 ? BAD : b.temp >= 38 ? WARN : SUB);
    }

    private void load() {
        grid.removeAllViews();
        grid.addView(tv("carregando jogos_", 13, SUB, false));
        new Thread(() -> {
            final List<GameManager.Item> all = GameManager.launchable(this);
            final List<GameManager.Item> games = GameManager.games(this, all);
            h.post(() -> build(all, games));
        }).start();
    }

    private void build(List<GameManager.Item> all, List<GameManager.Item> games) {
        grid.removeAllViews();
        LinearLayout row = null;
        int count = 0;
        for (GameManager.Item g : games) {
            if (count % 3 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row);
            }
            row.addView(tile(g), tileLp());
            count++;
        }
        if (row != null) while (count % 3 != 0) { row.addView(new View(this), tileLp()); count++; }

        Button add = new Button(this);
        add.setText("+ ADICIONAR JOGO");
        add.setAllCaps(false);
        add.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        add.setTextColor(ACCENT);
        add.setStateListAnimator(null);
        add.setBackground(round(0x00000000, 4, ACCENT));
        add.setOnClickListener(v -> {
            String[] labels = new String[all.size()];
            for (int i = 0; i < labels.length; i++) labels[i] = all.get(i).label;
            new AlertDialog.Builder(this).setTitle("Escolha o jogo")
                    .setItems(labels, (d, w) -> { GameManager.add(this, all.get(w).pkg); load(); })
                    .setNegativeButton("Cancelar", null).show();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(46));
        lp.topMargin = dp(14);
        grid.addView(add, lp);
        grid.addView(tv("Toque para jogar no Modo Jogo • segure para remover", 11, SUB, false));
    }

    private LinearLayout.LayoutParams tileLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(128), 1f);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        return lp;
    }

    private View tile(GameManager.Item g) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(6), dp(10), dp(6), dp(8));
        t.setBackground(round(CARD, 6, MainActivity.a(0x55)));
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(g.icon);
        t.addView(iv, new LinearLayout.LayoutParams(dp(58), dp(58)));
        TextView name = tv(g.label, 12, TXT, true);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setPadding(0, dp(8), 0, 0);
        t.addView(name);
        t.setOnClickListener(v -> ProfileManager.start(this, g.pkg));
        t.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this).setTitle(g.label).setMessage("Remover do Game Launcher?")
                    .setPositiveButton("Remover", (d, w) -> { GameManager.remove(this, g.pkg); load(); })
                    .setNegativeButton("Cancelar", null).show();
            return true;
        });
        return t;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private GradientDrawable round(int color, int radius, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    private TextView tv(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.MONOSPACE, bold ? Typeface.BOLD : Typeface.NORMAL);
        return t;
    }
}
