package com.realme.boosterultra;

import com.realme.boosterultra.*;
import com.realme.boosterultra.engine.*;
import com.realme.boosterultra.ai.*;
import com.realme.boosterultra.gaming.*;
import com.realme.boosterultra.monitor.*;
import com.realme.boosterultra.utils.*;
import com.realme.boosterultra.ui.*;


import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.text.InputType;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import com.realme.boosterultra.ai.IntelligenceEngine;
import java.util.Locale;

public class MainActivity extends Activity {
    public static final int WARN = 0xFFFFB020, BAD = 0xFFFF3B3B;
    /** Cores do tema atual (padrão: TERROR CRT). Mudam pela Ultra Interface. */
    public static int BG = 0xFF050807, CARD = 0xFF0B120E, TXT = 0xFFD6F5CF, SUB = 0xFF6E8F72,
            TRACK = 0xFF17261B, BAR = 0xFF08100B;
    public static int ACCENT = 0xFF7CFF4F;

    /** Os 6 módulos na barra de baixo; Configurações & Diagnósticos ficam no ⚙ do topo. */
    private static final String[] TAB_NAMES = {"ENGINE", "GAMING", "IA", "SISTEMA", "ENERGIA", "REDE"};
    private static final int[] TAB_PAGES = {0, 1, 2, 3, 10, 4};

    /** Ultra Interface: cores de destaque. */
    public static final String[] THEME_NAMES = {"TERROR CRT", "TERROR SANGUE", "ROXO NEON", "VHS ÂMBAR", "CÂMERA CIANO"};
    /** {destaque, fundo, cartão, texto, texto fraco, trilha, barra} */
    public static final int[][] THEMES = {
            {0xFF7CFF4F, 0xFF050807, 0xFF0B120E, 0xFFD6F5CF, 0xFF6E8F72, 0xFF17261B, 0xFF08100B},
            {0xFFFF4444, 0xFF0A0505, 0xFF160A0A, 0xFFF5DCDC, 0xFF9A6E6E, 0xFF2A1515, 0xFF100707},
            {0xFFB57CFF, 0xFF0A0612, 0xFF140C22, 0xFFEDE3FF, 0xFF9A88B8, 0xFF2A1E3D, 0xFF0E0818},
            {0xFFFFC23D, 0xFF0A0804, 0xFF16110A, 0xFFF5ECD6, 0xFF9A8A6A, 0xFF2A2214, 0xFF100C06},
            {0xFF3DE8FF, 0xFF040A0C, 0xFF0A1518, 0xFFD6F2F5, 0xFF6E8E92, 0xFF15282C, 0xFF060F11}};

    public static int a(int alpha) { return (alpha << 24) | (ACCENT & 0xFFFFFF); }

    public static void loadTheme(android.content.Context c) {
        int i = ProfileManager.prefs(c).getInt("theme", 0);
        int[] t = THEMES[Math.max(0, Math.min(THEMES.length - 1, i))];
        ACCENT = t[0]; BG = t[1]; CARD = t[2]; TXT = t[3]; SUB = t[4]; TRACK = t[5]; BAR = t[6];
    }
    private final java.util.Random rnd = new java.util.Random();
    private TextView titleTv, recTv;
    private ImageView mascotIv;

    private final Handler h = new Handler(Looper.getMainLooper());
    private LinearLayout content;
    private ScrollView scroll;
    private final Button[] tabBtns = new Button[6];
    private int tab = 0;

    private TextView scoreTv, scoreSub, ramTv, stoTv, batTv, thermTv, cpuTv;
    private ProgressBar ramBar, stoBar, batBar;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (tab == 0) updateDash();
            flicker();
            h.postDelayed(this, 2000);
        }
    };

    // ------------------------------------------------------------------ ciclo de vida

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        android.widget.FrameLayout frame = findViewById(R.id.frame);
        frame.setBackgroundColor(BG);
        getWindow().getDecorView().setBackgroundColor(BG);
        LinearLayout root = findViewById(R.id.root);
        if (Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener((v, ins) -> {
                android.graphics.Insets i = ins.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(i.left, i.top, i.right, i.bottom);
                return ins;
            });
        }
        mascotIv = findViewById(R.id.mascot);
        mascotIv.setOnClickListener(v -> openJarvis(false));
        mascotIv.setOnLongClickListener(v -> { openJarvis(true); return true; });
        recTv = findViewById(R.id.rec);
        titleTv = findViewById(R.id.title);
        titleTv.setTextColor(ACCENT);
        titleTv.setShadowLayer(dp(8), 0, 0, ACCENT);
        ((TextView) findViewById(R.id.subtitle)).setTextColor(SUB);
        ((TextView) findViewById(R.id.subtitle)).setText(Build.MANUFACTURER.toUpperCase(Locale.ROOT) + " " + Build.MODEL
                + " • ANDROID " + Build.VERSION.RELEASE + " • NO ROOT");
        Button gear = findViewById(R.id.settingsBtn);
        gear.setTextColor(ACCENT);
        gear.setBackground(round(0x00000000, 6, a(0x88)));
        gear.setOnClickListener(v -> show(5));
        LinearLayout badges = findViewById(R.id.badges);
        String[] badgeTxt = {"TURBO\nmanual + auto", "INTELLIGENCE\nanálise", "ROOT\nnão precisa", "VIGILÂNCIA\n24h"};
        for (int k = 0; k < badges.getChildCount(); k++) {
            TextView bv = (TextView) badges.getChildAt(k);
            bv.setBackground(round(CARD, 4, a(0x66)));
            bv.setTextColor(TXT);
            if (k < badgeTxt.length) bv.setText(badgeTxt[k]);
        }
        findViewById(R.id.divider).setBackgroundColor(a(0x55));
        scroll = findViewById(R.id.scroll);
        content = findViewById(R.id.content);

        LinearLayout bar = findViewById(R.id.tabBar);
        bar.setBackgroundColor(BAR);
        for (int i = 0; i < TAB_NAMES.length; i++) {
            final int dest = TAB_PAGES[i];
            Button bt = new Button(this);
            bt.setText(TAB_NAMES[i]);
            bt.setAllCaps(false);
            bt.setTextSize(10);
            bt.setPadding(0, 0, 0, 0);
            bt.setMinWidth(0);
            bt.setMinimumWidth(0);
            bt.setTypeface(Typeface.MONOSPACE);
            bt.setStateListAnimator(null);
            bt.setOnClickListener(v -> show(dest));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f);
            lp.setMargins(dp(2), 0, dp(2), 0);
            bar.addView(bt, lp);
            tabBtns[i] = bt;
        }
        frame.addView(new ScanlineView(this), new android.widget.FrameLayout.LayoutParams(-1, -1));

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        SmartAlerts.sync(this);
    }

    @Override protected void onResume() {
        super.onResume();
        show(tab);
        h.removeCallbacks(ticker);
        h.postDelayed(ticker, 2000);
    }

    @Override protected void onPause() {
        super.onPause();
        h.removeCallbacks(ticker);
    }

    private void show(int t) {
        tab = t;
        if (recTv != null) {
            String[] cams = {"ENGINE", "GAMING", "IA", "SISTEMA", "REDE", "CONTROLE"};
            String name = t < cams.length ? cams[t] : t == 10 ? "ENERGIA" : t == 11 ? "SYSTEM PRO" : t == 12 ? "ULTRA ENGINE"
                    : t == 13 ? "GAME MEMORY" : t == 14 ? "BENCHMARK" : "PRO TOOLS";
            int cam = t < cams.length ? t + 1 : t - 3;
            recTv.setText(String.format(Locale.US, "● REC  CAM-%02d  %s", cam, name));
        }
        content.removeAllViews();
        ramTv = null;
        for (int i = 0; i < TAB_NAMES.length; i++) {
            boolean sel = TAB_PAGES[i] == t;
            tabBtns[i].setBackground(round(sel ? ACCENT : 0x00000000, 12, 0));
            tabBtns[i].setTextColor(sel ? BG : SUB);
            tabBtns[i].setTypeface(Typeface.MONOSPACE, sel ? Typeface.BOLD : Typeface.NORMAL);
        }
        switch (t) {
            case 0: buildDash(); break;
            case 1: buildGames(); break;
            case 2: buildAi(); break;
            case 10: buildBatteryMax(); break;
            case 11: buildSystemPro(); break;
            case 12: buildUltraEngine(); break;
            case 13: buildGameMemory(); break;
            case 14: buildBenchmark(); break;
            case 15: buildProTools(); break;
            case 3: buildSystem(); break;
            case 4: buildNet(); break;
            default: buildSettings(); break;
        }
        scroll.scrollTo(0, 0);
    }

    // ------------------------------------------------------------------ PAINEL

    private void buildDash() {
        if (ProfileManager.isActive(this)) activeBanner();
        if (!ProfileManager.prefs(this).getBoolean("fps_boost", false)) {
            LinearLayout fb = card(null);
            fb.setBackground(round(CARD, 6, ACCENT));
            fb.addView(tv("▌⚡ FPS BOOST desligado", 13, ACCENT, true));
            fb.addView(btn("> LIGAR FPS BOOST (aba GAMING)", v -> show(1)));
        }
        ultraScoreCard();

        LinearLayout st = card(null);
        st.setBackground(round(0xFF140606, 6, BAD));
        st.addView(tv("▌ULTRA TURBO", 12, BAD, true));
        st.addView(tv("Um toque: limpa a memória, solta o processador, corta distrações, "
                + "segura o calor e checa tudo que causa travadas.", 13, SUB, false));
        final LinearLayout report = new LinearLayout(this);
        report.setOrientation(LinearLayout.VERTICAL);
        Button sb = btn("> ATIVAR ULTRA TURBO_", null);
        sb.setBackground(round(BAD, 4, 0));
        sb.setTextColor(0xFF000000);
        sb.setOnClickListener(v -> runSuperTurbo(sb, report));
        st.addView(sb);
        st.addView(report);

        LinearLayout modes = card("MODOS ESPECIAIS");
        modes.addView(modeBtn("ULTRA BOOST ENGINE", "Central: IA, Auto Pilot, diagnósticos e tudo junto", ACCENT, v -> show(12)));
        modes.addView(modeBtn("ULTRA TURBO", "Preparação total para jogos", BAD, v -> runSuperTurbo(sb, report)));
        modes.addView(modeBtn("GAMING PRO", "Perfil próprio para cada jogo", ACCENT, v -> show(1)));
        modes.addView(modeBtn("BATTERY MAX", "Battery AI, alertas e economia", WARN, v -> show(10)));
        modes.addView(modeBtn("SYSTEM PRO", "Diagnóstico, aparelho e histórico", 0xFF3DE8FF, v -> show(11)));

        LinearLayout tools = card("TODAS AS FUNÇÕES");
        String[][] fx = {
                {"⚡ Turbo Extreme", "0"}, {"🎮 Game Mode / perfis", "1"}, {"📊 FPS Monitor", "1"},
                {"🌡 Thermal Monitor", "11"}, {"🔋 Battery AI", "10"}, {"🌐 Ping Monitor", "4"},
                {"📡 Wi-Fi Analyzer", "4"}, {"🧹 Smart Cleaner", "3"}, {"🚀 App Launcher", "L"},
                {"🔕 Focus Mode", "1"}, {"📱 System Monitor", "11"}, {"🎨 Ultra Interface", "5"},
                {"🔔 Smart Alerts", "10"}, {"📈 Performance History", "11"}, {"🤖 Jarvis", "2"},
                {"🧠 Ultra AI Engine", "12"}, {"🛰 Smart Auto Pilot", "12"}, {"🎯 Auto Game Detect", "12"},
                {"🎞 FPS Stability / Game Memory", "13"}, {"🏁 Benchmark", "14"}, {"🌐 Network Latency AI", "4"},
                {"🛠 Pro Tools", "15"}};
        for (String[] f : fx) {
            final String dest = f[1];
            tools.addView(btn2(f[0], v -> {
                if ("L".equals(dest)) openLauncher();
                else if ("0".equals(dest)) scroll.scrollTo(0, 0);
                else show(Integer.parseInt(dest));
            }));
        }

        LinearLayout c = card(null);
        c.addView(tv("ESTADO PARA JOGAR AGORA", 12, SUB, true));
        scoreTv = tv("--", 28, ACCENT, true);
        c.addView(scoreTv);
        scoreSub = tv("", 13, SUB, false);
        c.addView(scoreSub);

        c = card("MEMÓRIA RAM");
        ramTv = tv("", 15, TXT, false); c.addView(ramTv);
        ramBar = bar(); c.addView(ramBar);

        c = card("ARMAZENAMENTO");
        stoTv = tv("", 15, TXT, false); c.addView(stoTv);
        stoBar = bar(); c.addView(stoBar);

        c = card("BATERIA E TEMPERATURA");
        batTv = tv("", 15, TXT, false); c.addView(batTv);
        batBar = bar(); c.addView(batBar);
        thermTv = tv("", 13, SUB, false); c.addView(thermTv);

        c = card("PROCESSADOR");
        cpuTv = tv("", 14, TXT, false); c.addView(cpuTv);

        c = card("AÇÕES RÁPIDAS");
        final TextView res = tv("", 13, SUB, false);
        c.addView(btn("> LIBERAR MEMÓRIA_", v -> freeRam(res)));
        c.addView(btn2("> ABRIR MEUS JOGOS", v -> show(1)));
        c.addView(res);

        updateDash();
    }

    /** Piscadas de "monitor velho" no título, no REC e no mascote. */
    private void flicker() {
        if (recTv != null) recTv.setAlpha(recTv.getAlpha() > 0.5f ? 0.15f : 1f);
        if (mascotIv != null && rnd.nextInt(4) == 0) {
            mascotIv.setImageResource(R.drawable.mascot_blink);
            h.postDelayed(() -> mascotIv.setImageResource(R.drawable.mascot), 180);
        }
        if (titleTv == null || rnd.nextInt(3) != 0 || !ProfileManager.prefs(this).getBoolean("flicker_on", true)) return;
        titleTv.setAlpha(0.25f);
        mascotIv.setAlpha(0.3f);
        h.postDelayed(() -> { titleTv.setAlpha(1f); mascotIv.setAlpha(1f); }, 90);
        h.postDelayed(() -> titleTv.setAlpha(0.5f), 160);
        h.postDelayed(() -> titleTv.setAlpha(1f), 220);
    }

    private void updateDash() {
        if (ramTv == null || tab != 0) return;
        History.record(this, false);
        long[] r = DeviceInfo.ram(this);
        int ramUsed = DeviceInfo.pct(r[0] - r[1], r[0]);
        ramTv.setText("Em uso: " + DeviceInfo.fmt(r[0] - r[1]) + " de " + DeviceInfo.fmt(r[0])
                + "  (" + ramUsed + "%)\nLivre: " + DeviceInfo.fmt(r[1]));
        setBar(ramBar, ramUsed, ramUsed > 85 ? BAD : ramUsed > 70 ? WARN : ACCENT);

        long[] s = DeviceInfo.storage();
        int stoUsed = DeviceInfo.pct(s[0] - s[1], s[0]);
        stoTv.setText("Livre: " + DeviceInfo.fmt(s[1]) + " de " + DeviceInfo.fmt(s[0]) + "  (" + stoUsed + "% usado)");
        setBar(stoBar, stoUsed, stoUsed > 90 ? BAD : stoUsed > 80 ? WARN : ACCENT);

        DeviceInfo.Battery b = DeviceInfo.battery(this);
        batTv.setText(b.level + "%" + (b.charging ? " • carregando" : "")
                + "   •   " + String.format(Locale.US, "%.1f°C", b.temp));
        setBar(batBar, b.level, b.level < 20 ? BAD : b.level < 40 ? WARN : ACCENT);
        int th = DeviceInfo.thermalStatus(this);
        float hr = DeviceInfo.thermalHeadroom(this);
        String therm = "Estado térmico do sistema: " + DeviceInfo.thermalName(th);
        if (!Float.isNaN(hr) && hr > 0) {
            therm += "\nUso do limite térmico: " + Math.min(100, Math.round(hr * 100)) + "%";
        }
        thermTv.setText(therm);

        cpuTv.setText(DeviceInfo.cpuText());

        int score = 100;
        StringBuilder why = new StringBuilder();
        if (ramUsed > 85) { score -= 30; why.append("RAM quase cheia • "); }
        else if (ramUsed > 70) { score -= 12; why.append("RAM alta • "); }
        if (b.temp >= 42) { score -= 30; why.append("bateria quente • "); }
        else if (b.temp >= 38) { score -= 15; why.append("bateria morna • "); }
        if (th >= 2) { score -= 25; why.append("sistema reduzindo desempenho por calor • "); }
        if (stoUsed > 90) { score -= 15; why.append("pouco armazenamento • "); }
        if (b.level < 15 && !b.charging) { score -= 10; why.append("bateria baixa • "); }
        if (b.charging) { score -= 5; why.append("jogar carregando esquenta • "); }
        score = Math.max(0, score);

        String label; int color;
        if (score >= 80) { label = "ÓTIMO"; color = ACCENT; }
        else if (score >= 55) { label = "BOM"; color = WARN; }
        else { label = "ATENÇÃO"; color = BAD; }
        scoreTv.setText(label + "  " + score + "/100");
        scoreTv.setTextColor(color);
        String w = why.length() > 0 ? why.substring(0, why.length() - 3) : "Tudo certo para jogar 👍";
        scoreSub.setText(w);
    }

    private View modeBtn(String title, String sub, int color, View.OnClickListener l) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(dp(14), dp(10), dp(14), dp(10));
        b.setBackground(round(0x00000000, 4, color));
        b.addView(tv("▶ " + title, 16, color, true));
        b.addView(tv(sub, 12, SUB, false));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    private void runSuperTurbo(Button b, LinearLayout report) {
        b.setEnabled(false);
        b.setText("> TURBINANDO..._");
        report.removeAllViews();
        new Thread(() -> {
            final List<String[]> items = TurboManager.run(this);
            h.post(() -> {
                b.setEnabled(true);
                b.setText("> ULTRA TURBO ATIVO ✔");
                for (String[] it : items) {
                    String mark = "ok".equals(it[0]) ? "[✔] " : "warn".equals(it[0]) ? "[!] " : "[X] ";
                    int color = "ok".equals(it[0]) ? ACCENT : "warn".equals(it[0]) ? WARN : BAD;
                    TextView t = tv(mark + it[1], 13, color, false);
                    t.setPadding(0, dp(6), 0, 0);
                    final String act = it[2];
                    if (!act.isEmpty()) t.setOnClickListener(v -> turboFix(act));
                    report.addView(t);
                }
                updateDash();
            });
        }).start();
    }

    private void turboFix(String act) {
        switch (act) {
            case "saver": open(new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)); break;
            case "storage": open(new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)); break;
            default: askPerm(act);
        }
    }

    private void freeRam(TextView res) {
        res.setText("Liberando memória...");
        new Thread(() -> {
            long before = DeviceInfo.ram(this)[1];
            int n = Optimizer.killBackground(this, null);
            try { Thread.sleep(1200); } catch (InterruptedException ignored) {}
            long diff = Math.max(0, DeviceInfo.ram(this)[1] - before);
            h.post(() -> {
                res.setText(n + " apps em segundo plano verificados • " + DeviceInfo.fmt(diff) + " liberados"
                        + "\n(O Android pode reabrir alguns serviços sozinho, isso é normal.)");
                updateDash();
            });
        }).start();
    }

    // ------------------------------------------------------------------ JOGOS

    private void buildGames() {
        if (ProfileManager.isActive(this)) activeBanner();
        fpsBoostCard();

        LinearLayout gl = card("GAME LAUNCHER");
        gl.addView(tv("Todos os seus jogos numa tela só, abrindo já com Modo Jogo.", 13, SUB, false));
        gl.addView(btn("> ABRIR GAME LAUNCHER_", v -> openLauncher()));
        gl.addView(btn2("> CRIAR ÍCONE NA TELA INICIAL", v -> pinLauncher()));

        fpsGuideCard();

        LinearLayout fps = card("MONITOR DE FPS");
        fps.addView(tv(screenInfo(), 14, TXT, false));
        fps.addView(tv("\nNo painel flutuante aparece o FPS da tela em tempo real e quantas travadas "
                + "o sistema teve no último segundo. Se as travadas subirem, o celular está sobrecarregado ou quente.\n"
                + "Sem root, o Android não deixa nenhum app ler o FPS interno de outro jogo. Para o FPS exato do jogo, "
                + "use o contador do próprio jogo ou do Espaço de Jogos do realme, se tiver.", 12, SUB, false));

        LinearLayout c = card("OPÇÕES DO MODO JOGO");
        c.addView(tv("Padrão para todos os jogos. Para um jogo ter opções próprias (GAMING PRO), toque no ⚙ dele.", 13, SUB, false));
        addToggle(c, "opt_ram", true, "Liberar RAM antes de abrir o jogo", null);
        addToggle(c, "opt_loop", true, "Turbo contínuo: limpar RAM a cada 90s no jogo", null);
        addToggle(c, "opt_overlay", true, "Painel flutuante: RAM, temperatura e ping", "overlay");
        addToggle(c, "opt_fps", true, "Monitor de FPS da tela no painel flutuante", "overlay");
        addToggle(c, "opt_dnd", false, "Focus Mode: silenciar notificações", "dnd");
        addToggle(c, "opt_timeout", true, "Tela não apaga sozinha (10 min)", "write");
        addToggle(c, "opt_bright", false, "Brilho máximo durante o jogo", "write");
        c.addView(tv("Tudo volta ao normal quando você encerra o Modo Jogo (pela notificação, "
                + "segurando o painel flutuante ou pelo botão aqui no app).", 12, SUB, false));

        final LinearLayout g = card("SEUS JOGOS");
        final TextView loading = tv("Procurando jogos instalados...", 13, SUB, false);
        g.addView(loading);
        new Thread(() -> {
            final List<GameManager.Item> all = GameManager.launchable(this);
            final List<GameManager.Item> games = GameManager.games(this, all);
            h.post(() -> {
                if (tab != 1) return;
                g.removeView(loading);
                if (games.isEmpty()) {
                    g.addView(tv("Nenhum jogo detectado. Toque em \"Adicionar jogo\".", 13, SUB, false));
                }
                for (GameManager.Item a : games) g.addView(gameRow(a));
                g.addView(tv("Segure um jogo para tirar da lista.", 12, SUB, false));
                g.addView(btn2("+ Adicionar jogo", v -> pickGame(all)));
            });
        }).start();
    }

    private View gameRow(GameManager.Item a) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));

        ImageView iv = new ImageView(this);
        iv.setImageDrawable(a.icon);
        row.addView(iv, new LinearLayout.LayoutParams(dp(44), dp(44)));

        boolean hasProf = ProfileManager.prefs(this).contains("prof_" + a.pkg);
        TextView name = tv(a.label + (hasProf ? "\n★ perfil GAMING PRO" : ""), 15, TXT, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.leftMargin = dp(12);
        row.addView(name, lp);
        Button gear = new Button(this);
        gear.setText("⚙");
        gear.setTextSize(18);
        gear.setPadding(0, 0, 0, 0);
        gear.setMinWidth(0);
        gear.setMinimumWidth(0);
        gear.setStateListAnimator(null);
        gear.setTextColor(hasProf ? BG : ACCENT);
        gear.setBackground(hasProf ? round(ACCENT, 4, 0) : round(0x00000000, 4, ACCENT));
        gear.setOnClickListener(v -> profileDialog(a));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(dp(42), dp(42));
        glp.rightMargin = dp(6);
        row.addView(gear, glp);

        Button play = new Button(this);
        play.setText("JOGAR");
        play.setTextColor(BG);
        play.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        play.setStateListAnimator(null);
        play.setBackground(round(ACCENT, 4, 0));
        play.setOnClickListener(v -> ProfileManager.start(this, a.pkg));
        row.addView(play, new LinearLayout.LayoutParams(dp(84), dp(42)));

        row.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle(a.label)
                    .setMessage("Remover da lista de jogos?")
                    .setPositiveButton("Remover", (d, w) -> { GameManager.remove(this, a.pkg); show(1); })
                    .setNegativeButton("Cancelar", null)
                    .show();
            return true;
        });
        return row;
    }

    private void pickGame(List<GameManager.Item> all) {
        String[] labels = new String[all.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = all.get(i).label;
        new AlertDialog.Builder(this)
                .setTitle("Escolha o jogo")
                .setItems(labels, (d, w) -> { GameManager.add(this, all.get(w).pkg); show(1); })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void addToggle(LinearLayout c, String key, boolean def, String label, String perm) {
        SharedPreferences p = ProfileManager.prefs(this);
        CheckBox cb = new CheckBox(this);
        cb.setText(label);
        cb.setTextColor(TXT);
        cb.setTextSize(14);
        cb.setButtonTintList(ColorStateList.valueOf(ACCENT));
        cb.setChecked(p.getBoolean(key, def));
        cb.setOnCheckedChangeListener((v, checked) -> {
            p.edit().putBoolean(key, checked).apply();
            if (checked && !hasPerm(perm)) askPerm(perm);
        });
        c.addView(cb);
        if (cb.isChecked() && !hasPerm(perm)) {
            TextView warn = tv("   ⚠ Falta permissão — toque aqui para liberar", 12, WARN, false);
            warn.setPadding(dp(32), 0, 0, dp(6));
            warn.setOnClickListener(v -> askPerm(perm));
            c.addView(warn);
        }
    }

    private void activeBanner() {
        LinearLayout c = card(null);
        c.setBackground(round(0xFF1A0707, 6, BAD));
        c.addView(tv("● MODO JOGO ATIVO", 14, BAD, true));
        c.addView(tv("Algumas configurações estão alteradas para jogar.", 13, SUB, false));
        c.addView(btn("Encerrar Modo Jogo e restaurar", v -> { ProfileManager.stop(this); show(tab); }));
    }

    // ------------------------------------------------------------------ FPS BOOST

    private static final String[] OEM_GAME_APPS = {"com.oplus.games", "com.coloros.gamespaceui", "com.heytap.games", "com.realme.games"};

    private String oemGameApp() {
        for (String p : OEM_GAME_APPS) if (getPackageManager().getLaunchIntentForPackage(p) != null) return p;
        return null;
    }

    private void fpsBoostCard() {
        SharedPreferences p = ProfileManager.prefs(this);
        boolean on = p.getBoolean("fps_boost", false);
        LinearLayout c = card("⚡ FPS BOOST");
        c.setBackground(round(CARD, 6, on ? ACCENT : BAD));
        c.addView(tv(on ? "● ATIVO" : "○ DESLIGADO", 16, on ? ACCENT : SUB, true));
        c.addView(tv("Recupera o FPS que o celular perde: limpa a RAM a cada 45s durante o jogo, corta tudo que pesa, "
                + "vigia calor e travadas e te avisa na hora de abaixar os gráficos. Funciona junto com o Modo Jogo.", 12, SUB, false));
        final LinearLayout rep = new LinearLayout(this);
        rep.setOrientation(LinearLayout.VERTICAL);
        Button b = btn(on ? "> DESLIGAR FPS BOOST" : "> ATIVAR FPS BOOST_", null);
        if (on) { b.setBackground(round(0x00000000, 4, ACCENT)); b.setTextColor(ACCENT); }
        b.setOnClickListener(v -> {
            if (p.getBoolean("fps_boost", false)) { p.edit().putBoolean("fps_boost", false).apply(); show(tab); return; }
            p.edit().putBoolean("fps_boost", true).putBoolean("opt_loop", true).putBoolean("opt_ram", true)
                    .putBoolean("opt_fps", true).putBoolean("opt_overlay", true).putBoolean("opt_bright", false).apply();
            b.setEnabled(false);
            b.setText("> PREPARANDO..._");
            new Thread(() -> {
                final List<String[]> items = TurboManager.run(this);
                h.post(() -> { show(tab); showFpsReport(items); });
            }).start();
        });
        c.addView(b);
        c.addView(rep);
        String oem = oemGameApp();
        if (oem != null) {
            c.addView(btn2("> ESPAÇO DE JOGOS DO REALME (modo alto desempenho)", v -> {
                Intent i = getPackageManager().getLaunchIntentForPackage(oem);
                if (i != null) open(i);
            }));
            c.addView(tv("O próprio realme tem um modo de alto desempenho dentro do Espaço de Jogos. "
                    + "Ele pode acelerar o chip, coisa que nenhum app comum consegue: ative lá para cada jogo.", 11, SUB, false));
        }
        c.addView(btn2("> TELA NA TAXA MÁXIMA (Hz)", v -> open(new Intent(Settings.ACTION_DISPLAY_SETTINGS))));
    }

    private void showFpsReport(List<String[]> items) {
        StringBuilder sb = new StringBuilder();
        int bad = 0;
        for (String[] it : items) {
            String mark = "ok".equals(it[0]) ? "[✔] " : "warn".equals(it[0]) ? "[!] " : "[X] ";
            if (!"ok".equals(it[0])) bad++;
            sb.append(mark).append(it[1]).append("\n");
        }
        try {
            android.view.Display d = getWindowManager().getDefaultDisplay();
            float max = 0;
            for (android.view.Display.Mode m : d.getSupportedModes()) max = Math.max(max, m.getRefreshRate());
            if (d.getRefreshRate() + 1 < max) { sb.append("[!] Tela em ").append(Math.round(d.getRefreshRate()))
                    .append("Hz: coloque em ").append(Math.round(max)).append("Hz para o jogo poder rodar mais rápido\n"); bad++; }
            else sb.append("[✔] Tela na taxa máxima (").append(Math.round(max)).append("Hz)\n");
        } catch (Exception ignored) {}
        sb.append(bad == 0 ? "\nTudo pronto: abra o jogo pelo botão JOGAR." : "\nResolva os itens com [!] ou [X] para ganhar mais FPS.");
        new AlertDialog.Builder(this).setTitle("⚡ FPS BOOST ATIVADO").setMessage(sb.toString().trim())
                .setPositiveButton("Ok", null).show();
    }

    private void fpsGuideCard() {
        LinearLayout c = card("AJUSTES QUE MAIS AUMENTAM O FPS");
        c.addView(tv("Dentro de cada jogo é onde o FPS sobe de verdade:", 12, SUB, false));
        c.addView(tv("MINECRAFT\n• Distância de renderização: 8 a 10 chunks\n• Gráficos elegantes, nuvens bonitas e "
                + "sombras: DESLIGADOS\n• Partículas: mínimo • Shaders/texturas pesadas: tire\n\n"
                + "ROBLOX\n• Modo gráfico: Manual, qualidade 2 a 4\n• Feche o chat de voz em jogos pesados\n\n"
                + "FREE FIRE\n• Gráficos: Suave • Taxa de quadros: Alta\n\n"
                + "FRIDAY NIGHT FUNKIN' (mods)\n• Qualidade baixa / antialiasing desligado\n• Desligue fundos animados e shaders\n\n"
                + "QUALQUER JOGO\n• FPS travado num valor estável (30 ou 60) trava menos que \"ilimitado\"\n"
                + "• Resolução menor = mais FPS", 13, TXT, false));
    }

    // ------------------------------------------------------------------ ULTRA SCORE / REPORT / RESTORE

    private void ultraScoreCard() {
        List<IntelligenceEngine.Criterion> l = IntelligenceEngine.ultraScore(this);
        int total = IntelligenceEngine.total(l);
        int col = total >= 75 ? ACCENT : total >= 55 ? WARN : BAD;
        LinearLayout c = card("ULTRA SCORE");
        c.setBackground(round(CARD, 6, col));
        c.addView(tv(total + "/100 • " + IntelligenceEngine.grade(total), 28, col, true));
        final LinearLayout det = new LinearLayout(this);
        det.setOrientation(LinearLayout.VERTICAL);
        det.setVisibility(View.GONE);
        for (IntelligenceEngine.Criterion k : l) {
            int kc = k.points >= k.max * 0.8 ? ACCENT : k.points >= k.max * 0.5 ? WARN : BAD;
            det.addView(line(k.name + ": " + k.points + "/" + k.max + " — " + k.detail, kc));
        }
        det.addView(line("A nota soma esses critérios. Nenhum valor é inventado: tudo vem das leituras do Android.", SUB));
        final Button tg = btn2("> VER CRITÉRIOS DA NOTA", null);
        tg.setOnClickListener(v -> {
            boolean open = det.getVisibility() == View.VISIBLE;
            det.setVisibility(open ? View.GONE : View.VISIBLE);
            tg.setText(open ? "> VER CRITÉRIOS DA NOTA" : "> ESCONDER CRITÉRIOS");
        });
        c.addView(tg);
        c.addView(det);
    }

    private void autoRestoreCard() {
        LinearLayout c = card("AUTO RESTORE");
        boolean ch = UltraEngine.hasChanges(this);
        c.addView(tv(ch ? "O app está com configurações alteradas agora (brilho, tela, Não perturbe ou economia)."
                : "Nenhuma configuração alterada pelo app no momento ✔", 13, ch ? WARN : SUB, false));
        c.addView(tv("Sempre que um perfil termina, o app devolve o que mudou. Este botão força a restauração.", 12, SUB, false));
        c.addView(btn2("> RESTAURAR TUDO QUE O APP MUDOU", v -> { toast(UltraEngine.restoreAll(this)); show(tab); }));
    }

    private void reportCard() {
        LinearLayout c = card("SYSTEM HEALTH REPORT");
        c.addView(tv("Relatório completo do aparelho: nota, critérios, hardware, bateria, armazenamento, histórico, jogos e rede.", 12, SUB, false));
        c.addView(btn("> GERAR RELATÓRIO_", v -> {
            toast("Gerando relatório...");
            new Thread(() -> {
                final String rep = healthReport();
                h.post(() -> showReport(rep));
            }).start();
        }));
    }

    private String healthReport() {
        StringBuilder sb = new StringBuilder();
        String date = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new java.util.Date());
        sb.append("REALME BOOSTER ULTRA — SYSTEM HEALTH REPORT\n").append(date).append("\n\n");
        sb.append("APARELHO\n").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append(" • Android ")
                .append(Build.VERSION.RELEASE).append(" • patch ").append(Build.VERSION.SECURITY_PATCH).append("\n");
        sb.append(sysInfoPro()).append("\n\n");
        List<IntelligenceEngine.Criterion> l = IntelligenceEngine.ultraScore(this);
        int total = IntelligenceEngine.total(l);
        sb.append("ULTRA SCORE: ").append(total).append("/100 (").append(IntelligenceEngine.grade(total)).append(")\n");
        for (IntelligenceEngine.Criterion k : l) sb.append("- ").append(k.name).append(": ").append(k.points).append("/").append(k.max).append(" — ").append(k.detail).append("\n");
        sb.append("\nESTADO ATUAL\n").append(stateSummary()).append("\n");
        sb.append("\nBATERIA\n").append(batteryHealthPro()).append("\n");
        List<History.Sample> day = History.load(this, 24L * 3600_000);
        if (!day.isEmpty()) {
            float ts = 0, tm = 0; int rs = 0;
            for (History.Sample x : day) { ts += x.temp; tm = Math.max(tm, x.temp); rs += x.ram; }
            sb.append(String.format(Locale.US, "\nHISTÓRICO 24H\n%d registros • temperatura média %.1f°C, máxima %.1f°C • RAM média %d%%\n",
                    day.size(), ts / day.size(), tm, rs / day.size()));
        }
        java.util.Map<String, List<GameMemory.Session>> gm = GameMemory.byGame(this);
        if (!gm.isEmpty()) {
            sb.append("\nJOGOS (última sessão)\n");
            for (List<GameMemory.Session> g : gm.values()) {
                GameMemory.Session s = g.get(0);
                sb.append(String.format(Locale.US, "- %s: %d min, máx %.0f°C, estabilidade %s, ping %s\n", s.label, s.minutes(), s.maxTemp,
                        s.stability >= 0 ? Math.round(s.stability) + "%" : "-", s.avgPing >= 0 ? Math.round(s.avgPing) + " ms" : "-"));
            }
        }
        String net = netHistoryText();
        if (!net.isEmpty()) sb.append("\nREDE (últimos testes)\n").append(net).append("\n");
        sb.append("\nRECOMENDAÇÕES\n");
        for (String[] r : aiAnalyze()) sb.append("- ").append(r[1]).append("\n");
        sb.append("\nGerado pelo Realme Booster Ultra • sem root • dados oficiais do Android");
        return sb.toString();
    }

    private void showReport(String rep) {
        TextView t = new TextView(this);
        t.setText(rep);
        t.setTextColor(TXT);
        t.setTextSize(12);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextIsSelectable(true);
        t.setPadding(dp(16), dp(12), dp(16), dp(12));
        ScrollView sv = new ScrollView(this);
        sv.addView(t);
        new AlertDialog.Builder(this)
                .setTitle("System Health Report")
                .setView(sv)
                .setPositiveButton("Compartilhar", (d, w) -> {
                    Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, rep);
                    open(Intent.createChooser(i, "Compartilhar relatório"));
                })
                .setNeutralButton("Salvar", (d, w) -> saveReport(rep))
                .setNegativeButton("Fechar", null)
                .show();
    }

    private void saveReport(String rep) {
        String name = "RealmeBoosterUltra_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new java.util.Date()) + ".txt";
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                android.content.ContentValues cv = new android.content.ContentValues();
                cv.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name);
                cv.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                cv.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Download");
                Uri u = getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (u == null) throw new Exception("sem acesso");
                try (java.io.OutputStream os = getContentResolver().openOutputStream(u)) {
                    os.write(rep.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            } else {
                java.io.File f = new java.io.File(android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_DOWNLOADS), name);
                try (java.io.FileWriter w = new java.io.FileWriter(f)) { w.write(rep); }
            }
            toast("Salvo em Downloads: " + name);
        } catch (Exception e) {
            toast("Não deu para salvar: " + e.getMessage());
        }
    }

    private void saveNetResult(String grade, int avg, int jit, int loss) {
        try {
            SharedPreferences p = ProfileManager.prefs(this);
            org.json.JSONArray a = new org.json.JSONArray(p.getString("net_hist", "[]"));
            a.put(new org.json.JSONObject().put("t", System.currentTimeMillis()).put("g", grade)
                    .put("a", avg).put("j", jit).put("l", loss));
            org.json.JSONArray keep = new org.json.JSONArray();
            for (int i = Math.max(0, a.length() - 20); i < a.length(); i++) keep.put(a.get(i));
            p.edit().putString("net_hist", keep.toString()).apply();
        } catch (Exception ignored) {}
    }

    private String netHistoryText() {
        StringBuilder sb = new StringBuilder();
        try {
            org.json.JSONArray a = new org.json.JSONArray(ProfileManager.prefs(this).getString("net_hist", "[]"));
            java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());
            for (int i = a.length() - 1; i >= Math.max(0, a.length() - 8); i--) {
                org.json.JSONObject o = a.getJSONObject(i);
                sb.append(df.format(new java.util.Date(o.getLong("t")))).append(" • nota ").append(o.getString("g"));
                if (o.getInt("a") >= 0) sb.append(" • ").append(o.getInt("a")).append(" ms • jitter ").append(o.getInt("j"))
                        .append(" ms • perda ").append(o.getInt("l")).append("%");
                sb.append("\n");
            }
        } catch (Exception ignored) {}
        return sb.toString().trim();
    }

    // ------------------------------------------------------------------ ULTRA BOOST ENGINE

    private void buildUltraEngine() {
        backBar("ULTRA ENGINE");
        SharedPreferences p = ProfileManager.prefs(this);
        ultraScoreCard();

        String[] ad = adaptive();
        LinearLayout ap = card("ADAPTIVE PERFORMANCE");
        ap.setBackground(round(CARD, 6, Integer.parseInt(ad[2])));
        ap.addView(tv("MODO RECOMENDADO AGORA:", 12, SUB, true));
        ap.addView(tv(ad[0], 20, Integer.parseInt(ad[2]), true));
        ap.addView(tv(ad[1], 13, TXT, false));

        LinearLayout ai = card("ULTRA AI ENGINE");
        ai.addView(tv("Analisa o uso, o histórico e as sessões de jogo e recomenda ajustes:", 12, SUB, false));
        List<String[]> recs = aiAnalyze();
        for (String[] r : recs) {
            int col = "bad".equals(r[0]) ? BAD : "warn".equals(r[0]) ? WARN : ACCENT;
            String mark = "bad".equals(r[0]) ? "[X] " : "warn".equals(r[0]) ? "[!] " : "[✔] ";
            ai.addView(line(mark + r[1], col));
        }
        ai.addView(btn("> PERGUNTAR AO JARVIS_", v -> {
            StringBuilder sb = new StringBuilder("Analise meu celular e me dê as 3 dicas mais importantes, curtas. Dados: ");
            sb.append(stateSummary()).append(" Recomendações do app: ");
            for (String[] r : recs) sb.append(r[1]).append("; ");
            startActivity(new Intent(this, AiActivity.class).putExtra(AiActivity.EXTRA_ASK, sb.toString()));
        }));

        LinearLayout pl = card("SMART AUTO PILOT");
        pl.addView(tv("Automatiza tudo que o Android permite conforme o perfil:", 12, SUB, false));
        pl.addView(chips(new String[]{"OFF", "DESEMP.", "EQUILÍB.", "BATERIA"}, new int[]{0, 1, 2, 3},
                p.getInt("pilot_mode", 0), v -> {
                    if (v != 0 && v != 3 && !usageOk()) toast("Libere o acesso ao uso de apps para detectar jogos");
                    SmartAlerts.setPilot(this, v);
                }));
        String[] desc = {
                "Desligado: nada automático.",
                "DESEMPENHO: detecta jogos e aplica o perfil sozinho • economia só em 15% • esfria a partir de 44°C",
                "EQUILIBRADO: detecta jogos • economia em 20% • esfria a partir de 42°C • alertas e histórico",
                "BATERIA: sem detecção de jogos • economia em 30% • esfria a partir de 40°C com brilho baixo"};
        pl.addView(tv(desc[Math.max(0, Math.min(3, p.getInt("pilot_mode", 0)))], 13, TXT, false));
        CheckBox ad2 = check("AUTO GAME DETECT (aplica o perfil quando você abre um jogo)", p.getBoolean("auto_detect", false));
        ad2.setOnCheckedChangeListener((v, c) -> {
            p.edit().putBoolean("auto_detect", c).apply();
            if (c && !usageOk()) open(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            SmartAlerts.sync(this);
        });
        pl.addView(ad2);
        CheckBox hg = check("THERMAL INTELLIGENCE (esfria sozinho quando esquentar)", p.getBoolean("heat_guard", false));
        hg.setOnCheckedChangeListener((v, c) -> { p.edit().putBoolean("heat_guard", c).apply(); SmartAlerts.sync(this); });
        pl.addView(hg);
        if (p.getBoolean("auto_detect", false) && !usageOk()) {
            pl.addView(btn2("> LIBERAR ACESSO AO USO DE APPS (necessário)", v -> open(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))));
        }

        LinearLayout m = card("MÓDULOS");
        String[][] mods = {
                {"🧠 SYSTEM DIAGNOSTIC AI", "11"}, {"🎮 GAME MEMORY + FPS STABILITY", "13"},
                {"🏁 PERFORMANCE BENCHMARK", "14"}, {"🌐 NETWORK LATENCY AI", "4"},
                {"🔋 BATTERY HEALTH CENTER", "10"}, {"🛠 PRO TOOLS (RAM, armazenamento, permissões, 2º plano)", "15"},
                {"📱 SYSTEM INFORMATION PRO", "11"}, {"⚡ ULTRA TURBO", "0"}};
        for (String[] md : mods) {
            final int dest = Integer.parseInt(md[1]);
            m.addView(btn2(md[0], v -> show(dest)));
        }
    }

    /** {nome, explicação, cor} */
    private String[] adaptive() {
        DeviceInfo.Battery b = DeviceInfo.battery(this);
        long[] r = DeviceInfo.ram(this);
        int ru = DeviceInfo.pct(r[0] - r[1], r[0]);
        int th = DeviceInfo.thermalStatus(this);
        if (b.temp >= 42 || th >= 2) return new String[]{"❄ RESFRIAR",
                "O aparelho está quente. Jogue com gráficos baixos, tire a capinha e saia da tomada. O chip já pode estar reduzindo a velocidade.", String.valueOf(BAD)};
        if (b.level <= 25 && !b.charging) return new String[]{"🔋 ECONOMIA",
                "Bateria baixa. Prefira jogos leves, brilho baixo e FPS travado em 30.", String.valueOf(WARN)};
        if (ru >= 75) return new String[]{"🧹 LIMPAR ANTES",
                "Memória alta. Rode o Ultra Turbo antes de abrir o jogo para evitar travadas.", String.valueOf(WARN)};
        if (b.temp >= 38) return new String[]{"⚖ EQUILIBRADO",
                "Tudo ok, mas morno. Gráfico médio mantém o FPS estável por mais tempo.", String.valueOf(0xFF3DE8FF)};
        return new String[]{"⚡ MÁXIMO DESEMPENHO",
                "Frio, com memória e bateria boas. Pode usar o gráfico mais alto que o jogo aguentar.", String.valueOf(ACCENT)};
    }

    private String stateSummary() {
        DeviceInfo.Battery b = DeviceInfo.battery(this);
        long[] r = DeviceInfo.ram(this);
        long[] s = DeviceInfo.storage();
        float drain = History.drainPerHour(this);
        return String.format(Locale.US, "bateria %d%%%s, %.1f°C, RAM %d%%, armazenamento %d%% usado, consumo %s, ligado há %d h.",
                b.level, b.charging ? " carregando" : "", b.temp, DeviceInfo.pct(r[0] - r[1], r[0]),
                DeviceInfo.pct(s[0] - s[1], s[0]), Float.isNaN(drain) ? "desconhecido" : String.format(Locale.US, "%.1f%%/h", drain),
                android.os.SystemClock.elapsedRealtime() / 3_600_000L);
    }

    private List<String[]> aiAnalyze() {
        List<String[]> out = new java.util.ArrayList<>();
        SharedPreferences p = ProfileManager.prefs(this);
        DeviceInfo.Battery b = DeviceInfo.battery(this);
        long[] r = DeviceInfo.ram(this);
        int ru = DeviceInfo.pct(r[0] - r[1], r[0]);
        if (ru > 80) out.add(new String[]{"bad", "RAM em " + ru + "% agora: rode o Ultra Turbo"});
        List<History.Sample> day = History.load(this, 24L * 3600_000);
        if (day.size() >= 6) {
            int rs = 0, peaks = 0; float tmax = 0;
            for (History.Sample x : day) { rs += x.ram; if (x.ram >= 85) peaks++; tmax = Math.max(tmax, x.temp); }
            if (rs / day.size() > 75) out.add(new String[]{"warn", "A RAM fica cheia com frequência (" + peaks + " picos hoje): feche/desinstale apps que ficam rodando"});
            if (tmax >= 44) out.add(new String[]{"bad", "Passou de " + Math.round(tmax) + "°C nas últimas 24h: jogue longe do carregador e sem capinha"});
            List<History.Sample> recent = History.load(this, 30L * 60_000);
            if (recent.size() >= 2 && recent.get(recent.size() - 1).temp - recent.get(0).temp >= 4)
                out.add(new String[]{"warn", "Temperatura subindo rápido na última meia hora"});
        } else if (!p.getBoolean("hist_on", false)) {
            out.add(new String[]{"warn", "Ligue o histórico (Battery Max) para eu aprender seu uso"});
        }
        float drain = History.drainPerHour(this);
        if (!Float.isNaN(drain) && drain >= 15) out.add(new String[]{"warn", String.format(Locale.US, "Consumo alto: %.0f%% por hora. Abaixe o brilho e feche apps em 2º plano", drain)});
        long[] s = DeviceInfo.storage();
        if (DeviceInfo.pct(s[0] - s[1], s[0]) > 85) out.add(new String[]{"warn", "Armazenamento quase cheio: use o Smart Cleaner"});
        if (android.os.SystemClock.elapsedRealtime() > 3 * 86_400_000L) out.add(new String[]{"warn", "Celular ligado há mais de 3 dias: reinicie"});
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && pm.isPowerSaveMode()) out.add(new String[]{"warn", "Economia de bateria do Android ligada: desligue antes de jogar"});
        try {
            android.view.Display d = getWindowManager().getDefaultDisplay();
            float max = 0;
            for (android.view.Display.Mode md : d.getSupportedModes()) max = Math.max(max, md.getRefreshRate());
            if (d.getRefreshRate() + 1 < max) out.add(new String[]{"warn", "Tela abaixo da taxa máxima (" + Math.round(max) + "Hz): ajuste em Configurações > Tela"});
        } catch (Exception ignored) {}
        List<GameMemory.Session> ss = GameMemory.all(this);
        if (!ss.isEmpty()) {
            GameMemory.Session last = ss.get(ss.size() - 1);
            String adv = GameMemory.advice(last).split("\n")[0].replace("• ", "");
            out.add(new String[]{adv.contains("✔") ? "ok" : "warn", "Última sessão (" + last.label + "): " + adv});
        }
        List<Bench.Result> bh = Bench.history(this);
        if (bh.size() >= 2) {
            long a = bh.get(bh.size() - 2).total, c = bh.get(bh.size() - 1).total;
            if (a > 0) out.add(new String[]{c >= a ? "ok" : "warn", "Benchmark: " + (c >= a ? "+" : "") + Math.round((c - a) * 100f / a) + "% em relação ao teste anterior"});
        }
        if (p.getInt("pilot_mode", 0) == 0) out.add(new String[]{"warn", "Smart Auto Pilot desligado: ative abaixo para automatizar"});
        if (b.charging && b.temp >= 38) out.add(new String[]{"warn", "Carregando e morno: evite jogar agora"});
        if (out.isEmpty()) out.add(new String[]{"ok", "Tudo otimizado. Nenhum ajuste necessário agora"});
        return out;
    }

    // ---------------- GAME MEMORY

    private void buildGameMemory() {
        backBar("GAME MEMORY");
        java.util.Map<String, List<GameMemory.Session>> m = GameMemory.byGame(this);
        LinearLayout info = card(null);
        info.addView(tv("Cada partida no Modo Jogo (ou detectada pelo Auto Game Detect) com mais de 1 minuto é gravada: "
                + "temperatura, RAM, FPS da tela, estabilidade, travadas, ping e bateria.", 12, SUB, false));
        if (m.isEmpty()) {
            info.addView(tv("\nNenhuma sessão ainda. Jogue pelo botão JOGAR ou ative o Auto Game Detect.", 13, WARN, false));
            return;
        }
        java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());
        for (java.util.Map.Entry<String, List<GameMemory.Session>> en : m.entrySet()) {
            List<GameMemory.Session> l = en.getValue();
            GameMemory.Session last = l.get(0);
            LinearLayout c = card(last.label.toUpperCase(Locale.ROOT) + " • " + l.size() + " sessões");
            for (int i = 0; i < Math.min(5, l.size()); i++) {
                GameMemory.Session s = l.get(i);
                StringBuilder sb = new StringBuilder();
                sb.append(df.format(new java.util.Date(s.start))).append(" • ").append(s.minutes()).append(" min\n");
                sb.append(String.format(Locale.US, "  temp %.0f°C (máx %.0f°C) • RAM %.0f%%", s.avgTemp, s.maxTemp, s.avgRam));
                if (s.stability >= 0) sb.append(String.format(Locale.US, "\n  FPS tela %.0f (mín %.0f) • estabilidade %.0f%% • %.1f travadas/min",
                        s.avgHz, s.minHz, s.stability, s.janksPerMin));
                if (s.avgPing >= 0) sb.append(String.format(Locale.US, "\n  ping %.0f ms", s.avgPing));
                sb.append("  • bateria ").append(s.batStart).append("% → ").append(s.batEnd).append("%");
                c.addView(line(sb.toString(), i == 0 ? TXT : SUB));
            }
            if (l.size() >= 2) {
                GameMemory.Session a = l.get(1);
                StringBuilder cmp = new StringBuilder("COMPARAÇÃO com a sessão anterior: ");
                cmp.append(last.maxTemp <= a.maxTemp ? "▼ mais fria" : "▲ mais quente");
                if (last.stability >= 0 && a.stability >= 0) cmp.append(last.stability >= a.stability ? " • ▲ mais fluida" : " • ▼ menos fluida");
                c.addView(line(cmp.toString(), ACCENT));
            }
            c.addView(tv("\nADAPTIVE PERFORMANCE:", 12, ACCENT, true));
            c.addView(tv(GameMemory.advice(last), 13, TXT, false));
        }
    }

    // ---------------- BENCHMARK

    private void buildBenchmark() {
        backBar("BENCHMARK");
        LinearLayout c = card("PERFORMANCE BENCHMARK");
        c.addView(tv("Testes reais de processador, memória e armazenamento (~8 segundos cada rodada). "
                + "O resultado varia com o calor: compare sempre nas mesmas condições.", 12, SUB, false));
        final LinearLayout res = new LinearLayout(this);
        res.setOrientation(LinearLayout.VERTICAL);
        final TextView prog = tv("", 13, WARN, false);
        final Button quick = btn("> TESTE RÁPIDO_", null);
        final Button ab = btn2("> ANTES x DEPOIS DO ULTRA TURBO", null);
        quick.setOnClickListener(v -> runBench(false, quick, ab, prog, res));
        ab.setOnClickListener(v -> runBench(true, quick, ab, prog, res));
        c.addView(quick);
        c.addView(ab);
        c.addView(prog);
        c.addView(res);

        List<Bench.Result> hist = Bench.history(this);
        if (!hist.isEmpty()) {
            LinearLayout h2 = card("HISTÓRICO DE TESTES");
            java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());
            for (int i = hist.size() - 1; i >= Math.max(0, hist.size() - 8); i--) {
                Bench.Result r = hist.get(i);
                h2.addView(line(df.format(new java.util.Date(r.time)) + " • " + r.label + " • NOTA " + r.total, SUB));
            }
        }
    }

    private void runBench(boolean beforeAfter, Button b1, Button b2, TextView prog, LinearLayout res) {
        b1.setEnabled(false);
        b2.setEnabled(false);
        res.removeAllViews();
        new Thread(() -> {
            Bench.Result before = Bench.run(this, beforeAfter ? "antes" : "normal", s -> h.post(() -> prog.setText("Testando " + s)));
            Bench.Result after = null;
            if (beforeAfter) {
                h.post(() -> prog.setText("Aplicando Ultra Turbo..."));
                TurboManager.run(this);
                try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
                after = Bench.run(this, "depois", s -> h.post(() -> prog.setText("Testando de novo: " + s)));
            }
            final Bench.Result fa = after;
            h.post(() -> {
                prog.setText("");
                b1.setEnabled(true);
                b2.setEnabled(true);
                res.addView(benchView(before, fa));
            });
        }).start();
    }

    private View benchView(Bench.Result a, Bench.Result b) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        String[] names = {"CPU 1 núcleo", "CPU todos os núcleos", "Memória (MB/s)", "Gravação (MB/s)", "Leitura (MB/s)", "NOTA FINAL"};
        long[] va = {a.cpu1, a.cpuN, a.mem, a.write, a.read, a.total};
        long[] vb = b == null ? null : new long[]{b.cpu1, b.cpuN, b.mem, b.write, b.read, b.total};
        for (int i = 0; i < names.length; i++) {
            String t = names[i] + ": " + va[i];
            int col = i == names.length - 1 ? ACCENT : TXT;
            if (vb != null) {
                int diff = va[i] > 0 ? Math.round((vb[i] - va[i]) * 100f / va[i]) : 0;
                t = names[i] + ": " + va[i] + " → " + vb[i] + "  (" + (diff >= 0 ? "+" : "") + diff + "%)";
                col = diff >= 2 ? ACCENT : diff <= -2 ? WARN : TXT;
            }
            TextView tvv = line(t, col);
            if (i == names.length - 1) tvv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            l.addView(tvv);
        }
        if (b != null) l.addView(line("Se a diferença for pequena, o celular já estava otimizado. "
                + "Ganhos maiores aparecem quando a RAM estava cheia ou havia apps pesados abertos.", SUB));
        return l;
    }

    // ---------------- PRO TOOLS

    private void buildProTools() {
        backBar("PRO TOOLS");

        LinearLayout ri = card("RAM INTELLIGENCE");
        List<History.Sample> day = History.load(this, 24L * 3600_000);
        if (day.isEmpty()) {
            ri.addView(tv("Sem histórico ainda. Ligue o histórico no Battery Max.", 13, WARN, false));
        } else {
            int sum = 0, max = 0, peaks = 0;
            for (History.Sample x : day) { sum += x.ram; max = Math.max(max, x.ram); if (x.ram >= 85) peaks++; }
            ri.addView(tv("Últimas 24h: média " + (sum / day.size()) + "% • máximo " + max + "% • " + peaks + " picos acima de 85%", 13, TXT, false));
            ri.addView(new HistoryView(this, day, x -> x.ram, 0, 100, 0xFF3DE8FF, "%", 24L * 3600_000), new LinearLayout.LayoutParams(-1, dp(120)));
        }

        LinearLayout sa = card("STORAGE ANALYZER");
        if (!Cleaner.hasAccess(this)) {
            sa.addView(btn2("> PERMITIR ACESSO AOS ARQUIVOS", v -> open(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + getPackageName())))));
        } else {
            final LinearLayout sres = new LinearLayout(this);
            sres.setOrientation(LinearLayout.VERTICAL);
            final Button sb = btn("> ANALISAR ARMAZENAMENTO_", null);
            sb.setOnClickListener(v -> {
                sb.setEnabled(false);
                sres.removeAllViews();
                sres.addView(line("Analisando...", WARN));
                new Thread(() -> {
                    final java.util.Map<String, Long> folders = new java.util.HashMap<>();
                    final long[] cat = Cleaner.analyze(folders);
                    h.post(() -> { sb.setEnabled(true); showStorage(sres, cat, folders); });
                }).start();
            });
            sa.addView(sb);
            sa.addView(sres);
        }

        LinearLayout ps = card("APP PERMISSION SCANNER");
        final LinearLayout pres = new LinearLayout(this);
        pres.setOrientation(LinearLayout.VERTICAL);
        final Button pb = btn("> ESCANEAR PERMISSÕES_", null);
        pb.setOnClickListener(v -> {
            pb.setEnabled(false);
            pres.removeAllViews();
            new Thread(() -> {
                final List<AppScanner.AppPerm> l = AppScanner.scanPermissions(this);
                h.post(() -> {
                    pb.setEnabled(true);
                    pres.addView(line(l.size() + " apps com acesso a dados sensíveis (toque para revisar):", SUB));
                    for (int i = 0; i < Math.min(15, l.size()); i++) {
                        AppScanner.AppPerm a = l.get(i);
                        int col = a.risk >= 8 ? BAD : a.risk >= 4 ? WARN : TXT;
                        TextView t = line(a.label + " — " + android.text.TextUtils.join(", ", a.granted), col);
                        t.setOnClickListener(x -> openAppDetails(a.pkg));
                        pres.addView(t);
                    }
                });
            }).start();
        });
        ps.addView(pb);
        ps.addView(pres);

        LinearLayout bg = card("BACKGROUND MONITOR");
        bg.addView(tv("Apps que rodaram serviços em segundo plano nas últimas 3 horas:", 12, SUB, false));
        if (!usageOk()) {
            bg.addView(btn2("> PERMITIR ACESSO AO USO DE APPS", v -> open(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))));
        } else {
            List<String[]> l = AppScanner.background(this);
            if (l.isEmpty()) bg.addView(line("Nenhum app pesado em segundo plano ✔", ACCENT));
            for (int i = 0; i < Math.min(10, l.size()); i++) {
                final String[] it = l.get(i);
                TextView t = line(it[1] + " — " + it[2] + "x", Integer.parseInt(it[2]) >= 10 ? WARN : TXT);
                t.setOnClickListener(x -> openAppDetails(it[0]));
                bg.addView(t);
            }
            bg.addView(line("Toque num app para abrir a página dele e forçar parada ou limitar o uso em segundo plano.", SUB));
        }

        LinearLayout rr = card("REFRESH RATE MONITOR");
        rr.addView(tv(screenInfo(), 13, TXT, false));
    }

    private void showStorage(LinearLayout res, long[] cat, java.util.Map<String, Long> folders) {
        res.removeAllViews();
        long[] st = DeviceInfo.storage();
        long used = st[0] - st[1], files = 0;
        for (long x : cat) files += x;
        res.addView(line("Usado: " + DeviceInfo.fmt(used) + " de " + DeviceInfo.fmt(st[0]), TXT));
        int[] cols = {ACCENT, 0xFF3DE8FF, 0xFFB57CFF, WARN, BAD, 0xFFFF8A3D, SUB};
        for (int i = 0; i < cat.length; i++) {
            if (cat[i] <= 0) continue;
            res.addView(line(Cleaner.CATS[i] + ": " + DeviceInfo.fmt(cat[i]), cols[i]));
            ProgressBar pbar = bar();
            setBar(pbar, DeviceInfo.pct(cat[i], Math.max(1, used)), cols[i]);
            res.addView(pbar);
        }
        res.addView(line("Sistema, apps e dados de apps: " + DeviceInfo.fmt(Math.max(0, used - files)), SUB));
        List<java.util.Map.Entry<String, Long>> fl = new java.util.ArrayList<>(folders.entrySet());
        java.util.Collections.sort(fl, (x, y) -> Long.compare(y.getValue(), x.getValue()));
        StringBuilder sb = new StringBuilder("\nPASTAS MAIORES:\n");
        for (int i = 0; i < Math.min(8, fl.size()); i++) sb.append("• ").append(fl.get(i).getKey()).append(" — ").append(DeviceInfo.fmt(fl.get(i).getValue())).append("\n");
        res.addView(line(sb.toString().trim(), TXT));
    }

    private void openAppDetails(String pkg) {
        open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
    }

    private void runLatencyAI(Button b, LinearLayout res) {
        b.setEnabled(false);
        b.setText("> ANALISANDO (20 testes por servidor)..._");
        res.removeAllViews();
        new Thread(() -> {
            String[][] sv = {{"Google", "8.8.8.8", "53"}, {"Cloudflare", "1.1.1.1", "53"}, {"Roblox", "www.roblox.com", "443"}};
            int worstLoss = 0, worstJit = 0, bestAvg = Integer.MAX_VALUE;
            for (String[] s : sv) {
                final int[] r = NetworkMonitor.advanced(s[1], Integer.parseInt(s[2]), 20);
                final String name = s[0];
                if (r[0] >= 0) { bestAvg = Math.min(bestAvg, r[0]); worstJit = Math.max(worstJit, r[3]); }
                worstLoss = Math.max(worstLoss, r[4]);
                h.post(() -> res.addView(line(r[0] < 0 ? "[X] " + name + ": sem resposta"
                        : String.format(Locale.US, "%s: %d ms (mín %d, máx %d) • jitter %d ms • perda %d%%", name, r[0], r[1], r[2], r[3], r[4]),
                        r[0] < 0 || r[4] > 2 ? BAD : r[3] > 30 || r[0] > 100 ? WARN : ACCENT)));
            }
            final int loss = worstLoss, jit = worstJit, avg = bestAvg;
            h.post(() -> {
                b.setEnabled(true);
                b.setText("> ANALISAR DE NOVO_");
                String grade; int col; StringBuilder why = new StringBuilder();
                if (avg == Integer.MAX_VALUE) { grade = "F"; col = BAD; why.append("Sem conexão."); }
                else {
                    int score = 100;
                    if (avg > 150) { score -= 40; why.append("Ping alto: atraso nas ações. "); }
                    else if (avg > 80) { score -= 15; why.append("Ping médio. "); }
                    if (jit > 40) { score -= 30; why.append("Conexão instável (jitter alto): engasgos. "); }
                    else if (jit > 20) { score -= 10; why.append("Pequena oscilação. "); }
                    if (loss > 5) { score -= 40; why.append("Perda de pacotes: teleportes e quedas. "); }
                    else if (loss > 0) { score -= 10; why.append("Pouca perda de pacotes. "); }
                    grade = score >= 90 ? "A" : score >= 75 ? "B" : score >= 55 ? "C" : score >= 35 ? "D" : "F";
                    col = score >= 75 ? ACCENT : score >= 55 ? WARN : BAD;
                    if (why.length() == 0) why.append("Excelente para jogos online.");
                    else why.append("Dica: Wi-Fi 5 GHz, perto do roteador e sem downloads rodando.");
                }
                res.addView(line("\nNOTA DA CONEXÃO PARA JOGOS: " + grade, col), 0);
                saveNetResult(grade, avg == Integer.MAX_VALUE ? -1 : avg, jit, loss);
                res.addView(line(why.toString(), TXT), 1);
            });
        }).start();
    }

    private String batteryHealthPro() {
        StringBuilder sb = new StringBuilder();
        Intent bi = registerReceiver(null, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (bi != null) {
            String tech = bi.getStringExtra(android.os.BatteryManager.EXTRA_TECHNOLOGY);
            if (tech != null) sb.append("Tecnologia: ").append(tech).append("\n");
            int cycles = bi.getIntExtra("android.os.extra.CYCLE_COUNT", -1);
            sb.append("Ciclos de carga: ").append(cycles >= 0 ? String.valueOf(cycles) : "não informado pelo aparelho").append("\n");
        }
        android.os.BatteryManager bm = getSystemService(android.os.BatteryManager.class);
        double design = 0;
        try {
            Class<?> pp = Class.forName("com.android.internal.os.PowerProfile");
            Object o = pp.getConstructor(android.content.Context.class).newInstance(this);
            design = (Double) pp.getMethod("getBatteryCapacity").invoke(o);
        } catch (Throwable ignored) {}
        if (design > 0) sb.append("Capacidade original: ").append(Math.round(design)).append(" mAh\n");
        if (bm != null) {
            int cc = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
            int lvl = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY);
            if (cc > 0 && lvl > 5) {
                double full = cc / 1000.0 / (lvl / 100.0);
                sb.append("Carga cheia estimada agora: ").append(Math.round(full)).append(" mAh\n");
                if (design > 0) {
                    int hp = (int) Math.round(Math.min(100, full * 100 / design));
                    sb.append("Saúde estimada: ").append(hp).append("% ").append(hp >= 80 ? "✔" : "⚠ desgastada").append("\n");
                }
            }
        }
        sb.append("(Estimativas: o valor fica mais preciso com a bateria acima de 50%.)");
        return sb.toString();
    }

    private String sysInfoPro() {
        StringBuilder sb = new StringBuilder();
        try {
            android.app.ActivityManager am = getSystemService(android.app.ActivityManager.class);
            sb.append("OpenGL ES: ").append(am.getDeviceConfigurationInfo().getGlEsVersion()).append("\n");
        } catch (Exception ignored) {}
        sb.append("Hardware: ").append(Build.HARDWARE).append(" • placa ").append(Build.BOARD).append("\n");
        sb.append("Bootloader: ").append(Build.BOOTLOADER).append("\n");
        StringBuilder mx = new StringBuilder();
        java.util.TreeSet<Long> freqs = new java.util.TreeSet<>();
        for (int i = 0; i < Runtime.getRuntime().availableProcessors(); i++) {
            String v = DeviceInfo.readLine("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq");
            if (v != null) try { freqs.add(Long.parseLong(v.trim()) / 1000); } catch (NumberFormatException ignored) {}
        }
        for (Long f : freqs) mx.append(f).append("MHz ");
        sb.append("Frequência máxima dos núcleos: ").append(mx.length() > 0 ? mx.toString().trim() : "bloqueada pelo Android").append("\n");
        try {
            android.hardware.SensorManager sm = getSystemService(android.hardware.SensorManager.class);
            sb.append("Sensores: ").append(sm.getSensorList(android.hardware.Sensor.TYPE_ALL).size()).append("\n");
        } catch (Exception ignored) {}
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        double inches = Math.sqrt(Math.pow(dm.widthPixels / dm.xdpi, 2) + Math.pow(dm.heightPixels / dm.ydpi, 2));
        sb.append(String.format(Locale.US, "Tela: %.1f polegadas\n", inches));
        sb.append("Build: ").append(Build.DISPLAY);
        return sb.toString();
    }

    // ------------------------------------------------------------------ MODOS ESPECIAIS

    private void backBar(String title) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(10));
        Button back = new Button(this);
        back.setText("< VOLTAR");
        back.setAllCaps(false);
        back.setTextSize(12);
        back.setPadding(0, 0, 0, 0);
        back.setMinWidth(0);
        back.setMinimumWidth(0);
        back.setStateListAnimator(null);
        back.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        back.setTextColor(ACCENT);
        back.setBackground(round(0x00000000, 4, ACCENT));
        back.setOnClickListener(v -> show(tab >= 13 ? 12 : 0));
        row.addView(back, new LinearLayout.LayoutParams(dp(96), dp(38)));
        TextView t = tv(title, 18, ACCENT, true);
        t.setPadding(dp(12), 0, 0, 0);
        row.addView(t);
        content.addView(row);
    }

    @Override public void onBackPressed() {
        if (tab >= 13) show(12); else if (tab >= 10) show(0); else super.onBackPressed();
    }

    // ---------------- BATTERY MAX

    private void buildBatteryMax() {
        backBar("BATTERY MAX");
        SharedPreferences p = ProfileManager.prefs(this);
        Intent bi = registerReceiver(null, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        DeviceInfo.Battery b = DeviceInfo.battery(this);
        android.os.BatteryManager bm = getSystemService(android.os.BatteryManager.class);

        LinearLayout now = card("BATTERY AI • AGORA");
        StringBuilder sb = new StringBuilder();
        sb.append("Nível: ").append(b.level).append("%").append(b.charging ? " (carregando)" : "").append("\n");
        sb.append(String.format(Locale.US, "Temperatura: %.1f°C\n", b.temp));
        int health = 0;
        if (bi != null) {
            int mv = bi.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, 0);
            if (mv > 0) sb.append(String.format(Locale.US, "Tensão: %.2f V\n", mv / 1000f));
            health = bi.getIntExtra(android.os.BatteryManager.EXTRA_HEALTH, 0);
            sb.append("Saúde: ").append(healthName(health)).append("\n");
        }
        if (bm != null) {
            int cur = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            if (cur != Integer.MIN_VALUE && cur != 0) {
                int ma = Math.abs(cur) > 20000 ? Math.abs(cur) / 1000 : Math.abs(cur);
                sb.append("Corrente: ").append(ma).append(" mA ").append(b.charging ? "entrando" : "saindo").append("\n");
            }
            if (b.charging && Build.VERSION.SDK_INT >= 28) {
                long ms = bm.computeChargeTimeRemaining();
                if (ms > 0) sb.append("Carga completa em: ").append(fmtTime(ms / 60000f)).append("\n");
            }
        }
        now.addView(tv(sb.toString().trim(), 14, TXT, false));

        LinearLayout hc = card("BATTERY HEALTH CENTER");
        hc.addView(tv(batteryHealthPro(), 13, TXT, false));

        LinearLayout ai = card("BATTERY AI • ANÁLISE");
        float drain = History.drainPerHour(this);
        StringBuilder an = new StringBuilder();
        if (Float.isNaN(drain)) {
            an.append("• Ainda aprendendo seu uso. Ligue o histórico abaixo e use o celular sem carregar por pelo menos 30 min.\n");
        } else {
            an.append(String.format(Locale.US, "• Consumo recente: %.1f%% por hora", drain));
            if (drain >= 20) an.append(" — MUITO ALTO (jogo pesado, brilho alto ou sinal fraco)\n");
            else if (drain >= 10) an.append(" — moderado\n");
            else an.append(" — ótimo\n");
            if (!b.charging && drain > 0.5f) an.append("• Dura mais uns ").append(fmtTime(b.level / drain * 60f)).append(" nesse ritmo\n");
        }
        if (b.temp >= 40) an.append("• Calor acima de 40°C gasta mais bateria e desgasta ela com o tempo\n");
        if (b.charging && b.temp >= 38) an.append("• Evite jogar carregando: esquenta muito\n");
        if (health != 0 && health != android.os.BatteryManager.BATTERY_HEALTH_GOOD) an.append("• O sistema indica problema na saúde da bateria\n");
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && pm.isPowerSaveMode()) an.append("• Economia de bateria do Android está ligada (bom para durar, ruim para jogar)\n");
        int bright = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128);
        if (bright > 200) an.append("• Brilho muito alto: é um dos maiores gastos de bateria\n");
        ai.addView(tv(an.toString().trim(), 13, TXT, false));

        ai.addView(tv("\nTEMPO DE USO HOJE (quem mais usou a tela costuma ser quem mais gastou):", 12, SUB, true));
        if (!usageOk()) {
            ai.addView(btn2("> PERMITIR ACESSO AO USO DE APPS", v -> open(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))));
        } else {
            ai.addView(tv(topApps(), 13, TXT, false));
        }

        LinearLayout al = card("SMART ALERTS");
        al.addView(tv("Avisa por notificação quando o celular esquentar, a bateria baixar ou terminar de carregar.", 13, SUB, false));
        CheckBox alOn = check("Smart Alerts ligados", p.getBoolean("alerts_on", false));
        alOn.setOnCheckedChangeListener((v, c) -> { p.edit().putBoolean("alerts_on", c).apply(); SmartAlerts.sync(this); });
        al.addView(alOn);
        al.addView(tv("Avisar calor a partir de:", 12, SUB, false));
        al.addView(chips(new String[]{"40°C", "42°C", "45°C"}, new int[]{40, 42, 45}, p.getInt("alert_temp", 42),
                v -> p.edit().putInt("alert_temp", v).apply()));
        al.addView(tv("\nAvisar bateria baixa em:", 12, SUB, false));
        al.addView(chips(new String[]{"10%", "15%", "20%"}, new int[]{10, 15, 20}, p.getInt("alert_low", 15),
                v -> p.edit().putInt("alert_low", v).apply()));
        CheckBox full = check("Avisar quando terminar de carregar", p.getBoolean("alert_full", true));
        full.setOnCheckedChangeListener((v, c) -> p.edit().putBoolean("alert_full", c).apply());
        al.addView(full);

        LinearLayout hi = card("HISTÓRICO DE DESEMPENHO");
        CheckBox hOn = check("Gravar histórico (bateria, temperatura, RAM a cada 5 min)", p.getBoolean("hist_on", false));
        hOn.setOnCheckedChangeListener((v, c) -> { p.edit().putBoolean("hist_on", c).apply(); SmartAlerts.sync(this); });
        hi.addView(hOn);
        hi.addView(btn2("> VER GRÁFICOS NO SYSTEM PRO", v -> show(11)));

        buildEco();
    }

    private String healthName(int h) {
        switch (h) {
            case android.os.BatteryManager.BATTERY_HEALTH_GOOD: return "boa ✔";
            case android.os.BatteryManager.BATTERY_HEALTH_OVERHEAT: return "superaquecida ⚠";
            case android.os.BatteryManager.BATTERY_HEALTH_DEAD: return "desgastada ⚠";
            case android.os.BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "tensão alta ⚠";
            case android.os.BatteryManager.BATTERY_HEALTH_COLD: return "fria";
            default: return "desconhecida";
        }
    }

    private String fmtTime(float minutes) {
        int m = Math.round(minutes);
        if (m < 60) return m + " min";
        return (m / 60) + "h" + String.format(Locale.US, "%02d", m % 60);
    }

    private boolean usageOk() {
        try {
            android.app.AppOpsManager ao = getSystemService(android.app.AppOpsManager.class);
            int mode = Build.VERSION.SDK_INT >= 29
                    ? ao.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), getPackageName())
                    : ao.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), getPackageName());
            return mode == android.app.AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    private String topApps() {
        try {
            android.app.usage.UsageStatsManager um = getSystemService(android.app.usage.UsageStatsManager.class);
            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
            cal.set(java.util.Calendar.MINUTE, 0);
            cal.set(java.util.Calendar.SECOND, 0);
            java.util.Map<String, android.app.usage.UsageStats> m =
                    um.queryAndAggregateUsageStats(cal.getTimeInMillis(), System.currentTimeMillis());
            java.util.List<android.app.usage.UsageStats> l = new java.util.ArrayList<>(m.values());
            java.util.Collections.sort(l, (x, y) -> Long.compare(y.getTotalTimeInForeground(), x.getTotalTimeInForeground()));
            PackageManager pm = getPackageManager();
            StringBuilder sb = new StringBuilder();
            int n = 0;
            for (android.app.usage.UsageStats u : l) {
                if (u.getTotalTimeInForeground() < 60000 || u.getPackageName().equals(getPackageName())) continue;
                String label = u.getPackageName();
                try { label = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(label, 0))); } catch (Exception ignored) {}
                sb.append(n + 1).append(". ").append(label).append(" — ").append(fmtTime(u.getTotalTimeInForeground() / 60000f)).append("\n");
                if (++n >= 6) break;
            }
            return n == 0 ? "Sem dados de hoje ainda." : sb.toString().trim();
        } catch (Exception e) {
            return "Não deu para ler o uso dos apps.";
        }
    }

    private CheckBox check(String label, boolean on) {
        CheckBox cb = new CheckBox(this);
        cb.setText(label);
        cb.setTextColor(TXT);
        cb.setTextSize(14);
        cb.setTypeface(Typeface.MONOSPACE);
        cb.setButtonTintList(ColorStateList.valueOf(ACCENT));
        cb.setChecked(on);
        return cb;
    }

    // ---------------- SYSTEM PRO

    private void buildSystemPro() {
        backBar("SYSTEM PRO");
        SharedPreferences p = ProfileManager.prefs(this);

        LinearLayout dv = card("APARELHO");
        long[] r = DeviceInfo.ram(this);
        long[] s = DeviceInfo.storage();
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        long up = android.os.SystemClock.elapsedRealtime() / 60000;
        StringBuilder sb = new StringBuilder();
        sb.append("Modelo: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append(" (").append(Build.DEVICE).append(")\n");
        sb.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("Patch de segurança: ").append(Build.VERSION.SECURITY_PATCH).append("\n");
        if (Build.VERSION.SDK_INT >= 31) sb.append("Chip: ").append(Build.SOC_MANUFACTURER).append(" ").append(Build.SOC_MODEL).append("\n");
        sb.append("Núcleos: ").append(Runtime.getRuntime().availableProcessors()).append(" • ").append(Build.SUPPORTED_ABIS[0]).append("\n");
        sb.append("RAM: ").append(DeviceInfo.fmt(r[0])).append(" • Armazenamento: ").append(DeviceInfo.fmt(s[0])).append("\n");
        sb.append("Tela: ").append(dm.heightPixels).append("x").append(dm.widthPixels).append(" • ")
                .append(Math.round(getWindowManager().getDefaultDisplay().getRefreshRate())).append("Hz • ").append(dm.densityDpi).append("dpi\n");
        sb.append("Kernel: ").append(System.getProperty("os.version")).append("\n");
        sb.append("Ligado há: ").append(fmtTime(up));
        dv.addView(tv(sb.toString(), 13, TXT, false));
        LinearLayout sip = card("SYSTEM INFORMATION PRO");
        sip.addView(tv(sysInfoPro(), 13, TXT, false));

        reportCard();

        LinearLayout dg = card("SYSTEM DIAGNOSTIC AI");
        final LinearLayout dres = new LinearLayout(this);
        dres.setOrientation(LinearLayout.VERTICAL);
        dg.addView(btn("> RODAR DIAGNÓSTICO_", v -> runDiagnostic(dres)));
        dg.addView(dres);

        LinearLayout hi = card("PERFORMANCE HISTORY");
        if (!p.getBoolean("hist_on", false)) {
            hi.addView(tv("O histórico está desligado.", 13, WARN, false));
            hi.addView(btn("> LIGAR HISTÓRICO_", v -> { p.edit().putBoolean("hist_on", true).apply(); SmartAlerts.sync(this); History.record(this, true); show(tab); }));
        }
        final long win = p.getInt("hist_days", 1) * 24L * 60 * 60 * 1000;
        hi.addView(chips(new String[]{"24 HORAS", "7 DIAS"}, new int[]{1, 7}, p.getInt("hist_days", 1),
                v -> p.edit().putInt("hist_days", v).apply()));
        java.util.List<History.Sample> data = History.load(this, win);
        float tSum = 0, tMax = 0; int lMin = 100, rSum = 0;
        for (History.Sample x : data) { tSum += x.temp; tMax = Math.max(tMax, x.temp); lMin = Math.min(lMin, x.level); rSum += x.ram; }
        if (!data.isEmpty()) {
            hi.addView(tv(String.format(Locale.US, "\n%d registros • temp. média %.1f°C (máx %.1f°C) • RAM média %d%% • bateria mínima %d%%",
                    data.size(), tSum / data.size(), tMax, rSum / data.size(), lMin), 12, SUB, false));
        }
        hi.addView(tv("\nBATERIA (%)", 12, ACCENT, true));
        hi.addView(new HistoryView(this, data, x -> x.level, 0, 100, ACCENT, "%", win), new LinearLayout.LayoutParams(-1, dp(130)));
        hi.addView(tv("\nTEMPERATURA (°C)", 12, WARN, true));
        hi.addView(new HistoryView(this, data, x -> x.temp, 20, 50, WARN, "°", win), new LinearLayout.LayoutParams(-1, dp(130)));
        hi.addView(tv("\nUSO DE RAM (%)", 12, 0xFF3DE8FF, true));
        hi.addView(new HistoryView(this, data, x -> x.ram, 0, 100, 0xFF3DE8FF, "%", win), new LinearLayout.LayoutParams(-1, dp(130)));
    }

    private void runDiagnostic(LinearLayout res) {
        res.removeAllViews();
        java.util.List<String[]> items = new java.util.ArrayList<>();
        long[] r = DeviceInfo.ram(this);
        int ru = DeviceInfo.pct(r[0] - r[1], r[0]);
        items.add(new String[]{ru > 85 ? "bad" : ru > 70 ? "warn" : "ok", "Memória RAM em " + ru + "%"});
        long[] s = DeviceInfo.storage();
        int su = DeviceInfo.pct(s[0] - s[1], s[0]);
        items.add(new String[]{su > 90 ? "bad" : su > 80 ? "warn" : "ok", "Armazenamento " + su + "% usado"});
        DeviceInfo.Battery b = DeviceInfo.battery(this);
        items.add(new String[]{b.temp >= 42 ? "bad" : b.temp >= 38 ? "warn" : "ok", String.format(Locale.US, "Temperatura %.1f°C", b.temp)});
        int th = DeviceInfo.thermalStatus(this);
        items.add(new String[]{th >= 2 ? "bad" : th == 1 ? "warn" : "ok", "Estado térmico: " + DeviceInfo.thermalName(th)});
        Intent bi = registerReceiver(null, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int health = bi == null ? 0 : bi.getIntExtra(android.os.BatteryManager.EXTRA_HEALTH, 0);
        items.add(new String[]{health == android.os.BatteryManager.BATTERY_HEALTH_GOOD || health == 0 ? "ok" : "bad", "Saúde da bateria: " + healthName(health)});
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        items.add(new String[]{pm != null && pm.isPowerSaveMode() ? "warn" : "ok",
                pm != null && pm.isPowerSaveMode() ? "Economia de bateria ligada (deixa jogos lentos)" : "Economia de bateria desligada"});
        long days = android.os.SystemClock.elapsedRealtime() / 86_400_000L;
        items.add(new String[]{days >= 3 ? "warn" : "ok", days >= 3 ? "Ligado há " + days + " dias: reinicie para limpar o sistema" : "Reiniciado recentemente"});
        try {
            android.view.Display d = getWindowManager().getDefaultDisplay();
            float maxHz = 0;
            for (android.view.Display.Mode m : d.getSupportedModes()) maxHz = Math.max(maxHz, m.getRefreshRate());
            boolean low = d.getRefreshRate() + 1 < maxHz;
            items.add(new String[]{low ? "warn" : "ok", low ? "Tela em " + Math.round(d.getRefreshRate()) + "Hz (suporta " + Math.round(maxHz) + "Hz)"
                    : "Tela na taxa máxima (" + Math.round(maxHz) + "Hz)"});
        } catch (Exception ignored) {}
        if (!Settings.canDrawOverlays(this)) items.add(new String[]{"warn", "Falta permissão do painel flutuante"});
        if (!Settings.System.canWrite(this)) items.add(new String[]{"warn", "Falta permissão de modificar configurações"});
        int bad = 0, warn = 0;
        for (String[] it : items) {
            if ("bad".equals(it[0])) bad++; else if ("warn".equals(it[0])) warn++;
            String mark = "ok".equals(it[0]) ? "[✔] " : "warn".equals(it[0]) ? "[!] " : "[X] ";
            res.addView(line(mark + it[1], "ok".equals(it[0]) ? ACCENT : "warn".equals(it[0]) ? WARN : BAD));
        }
        int score = Math.max(0, 100 - bad * 15 - warn * 6);
        TextView sc = tv("\nNOTA DO SISTEMA: " + score + "/100", 18, score >= 80 ? ACCENT : score >= 55 ? WARN : BAD, true);
        res.addView(sc, 0);
    }

    // ---------------- GAMING PRO (perfil por jogo)

    private void profileDialog(GameManager.Item a) {
        final String[] keys = {"opt_ram", "opt_loop", "opt_dnd", "opt_overlay", "opt_fps", "opt_timeout", "opt_bright"};
        final String[] labels = {"Liberar RAM ao abrir", "Turbo contínuo (RAM a cada 90s)", "Focus Mode (silenciar notificações)",
                "Painel flutuante", "Monitor de FPS", "Tela não apaga", "Brilho máximo"};
        final boolean[] defs = {true, true, false, true, true, true, false};
        final SharedPreferences p = ProfileManager.prefs(this);
        final boolean[] cur = new boolean[keys.length];
        for (int i = 0; i < keys.length; i++) cur[i] = ProfileManager.opt(p, a.pkg, keys[i], defs[i]);
        new AlertDialog.Builder(this)
                .setTitle("GAMING PRO • " + a.label)
                .setMultiChoiceItems(labels, cur, (d, w, c) -> cur[w] = c)
                .setPositiveButton("Salvar perfil", (d, w) -> {
                    try {
                        org.json.JSONObject o = new org.json.JSONObject();
                        for (int i = 0; i < keys.length; i++) o.put(keys[i], cur[i]);
                        p.edit().putString("prof_" + a.pkg, o.toString()).apply();
                        toast("Perfil de " + a.label + " salvo");
                    } catch (Exception ignored) {}
                    show(tab);
                })
                .setNeutralButton("Usar padrão", (d, w) -> { p.edit().remove("prof_" + a.pkg).apply(); show(tab); })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ---------------- WI-FI ANALYZER

    @SuppressWarnings("deprecation")
    private String wifiReport() {
        android.net.ConnectivityManager cm = getSystemService(android.net.ConnectivityManager.class);
        android.net.NetworkCapabilities nc = cm == null ? null : cm.getNetworkCapabilities(cm.getActiveNetwork());
        if (nc == null || !nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) return "Você não está no Wi-Fi agora.";
        android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        android.net.wifi.WifiInfo wi = wm == null ? null : wm.getConnectionInfo();
        if (wi == null) return "Não deu para ler o Wi-Fi.";
        StringBuilder sb = new StringBuilder();
        StringBuilder tips = new StringBuilder();
        int f = wi.getFrequency();
        String band; int ch;
        if (f >= 5925) { band = "6 GHz"; ch = (f - 5950) / 5; }
        else if (f >= 4900) { band = "5 GHz"; ch = (f - 5000) / 5; }
        else { band = "2,4 GHz"; ch = f == 2484 ? 14 : (f - 2407) / 5; }
        sb.append("Faixa: ").append(band).append(" • canal ").append(ch).append("\n");
        if (Build.VERSION.SDK_INT >= 30) {
            int st = wi.getWifiStandard();
            String std = st == 4 ? "Wi-Fi 4 (n)" : st == 5 ? "Wi-Fi 5 (ac)" : st == 6 ? "Wi-Fi 6 (ax)" : st == 8 ? "Wi-Fi 7 (be)" : "antigo/desconhecido";
            sb.append("Padrão: ").append(std).append("\n");
        }
        int rssi = wi.getRssi();
        int bars = rssi > -55 ? 4 : rssi > -67 ? 3 : rssi > -75 ? 2 : 1;
        sb.append("Sinal: ").append(rssi).append(" dBm ").append("▮▮▮▮".substring(0, bars)).append("▯▯▯▯".substring(bars)).append("\n");
        sb.append("Velocidade de ligação: ").append(wi.getLinkSpeed()).append(" Mbps");
        if (Build.VERSION.SDK_INT >= 29) sb.append(" (recebe ").append(wi.getRxLinkSpeedMbps()).append(" Mbps)");
        sb.append("\n");
        if (band.startsWith("2")) tips.append("• Você está em 2,4 GHz: se o roteador tiver rede 5 GHz, conecte nela para jogar (menos atraso)\n");
        if (bars <= 2) tips.append("• Sinal fraco: chegue mais perto do roteador ou tire paredes do caminho\n");
        if (wi.getLinkSpeed() > 0 && wi.getLinkSpeed() < 50) tips.append("• Ligação lenta com o roteador: isso aumenta o ping\n");
        if (band.startsWith("2") && (ch == 1 || ch == 6 || ch == 11)) tips.append("• Canal " + ch + " é comum e pode estar lotado; trocar o canal no roteador pode ajudar\n");
        if (tips.length() == 0) tips.append("• Seu Wi-Fi está bom para jogar ✔\n");
        return sb.toString() + "\n" + tips.toString().trim();
    }

    // ---------------- ULTRA INTERFACE

    private void buildUltraInterface() {
        SharedPreferences p = ProfileManager.prefs(this);
        LinearLayout c = card("ULTRA INTERFACE • TEMA DE TERROR");
        c.addView(tv("Cor de destaque:", 13, SUB, false));
        c.addView(tv("Tema:", 12, SUB, false));
        c.addView(tv(THEME_NAMES[Math.max(0, Math.min(4, p.getInt("theme", 0)))], 15, ACCENT, true));
        c.addView(chips(new String[]{"CRT", "SANGUE", "ROXO", "VHS", "CIANO"}, new int[]{0, 1, 2, 3, 4},
                p.getInt("theme", 0), v -> { p.edit().putInt("theme", v).apply(); loadTheme(this); recreate(); }));
        CheckBox scan = check("Linhas de TV antiga (scanlines)", p.getBoolean("scan_on", true));
        scan.setOnCheckedChangeListener((v, on) -> { p.edit().putBoolean("scan_on", on).apply(); recreate(); });
        c.addView(scan);
        CheckBox st = check("Estática de câmera e \"SINAL PERDIDO\"", p.getBoolean("static_on", true));
        st.setOnCheckedChangeListener((v, on) -> { p.edit().putBoolean("static_on", on).apply(); recreate(); });
        c.addView(st);
        CheckBox fl = check("Efeito de falha no título e mascote piscando", p.getBoolean("flicker_on", true));
        fl.setOnCheckedChangeListener((v, on) -> p.edit().putBoolean("flicker_on", on).apply());
        c.addView(fl);
        c.addView(tv("Desligar os efeitos deixa o app ainda mais leve.", 12, SUB, false));
    }

    // ------------------------------------------------------------------ FERRAMENTAS

    private void openLauncher() {
        startActivity(new Intent(this, GameLauncherActivity.class));
    }

    private void pinLauncher() {
        try {
            android.content.pm.ShortcutManager sm = getSystemService(android.content.pm.ShortcutManager.class);
            if (sm == null || !sm.isRequestPinShortcutSupported()) { toast("Seu launcher não aceita atalhos"); return; }
            Intent i = new Intent(this, GameLauncherActivity.class).setAction(Intent.ACTION_MAIN);
            android.content.pm.ShortcutInfo info = new android.content.pm.ShortcutInfo.Builder(this, "launcher")
                    .setShortLabel("Game Launcher")
                    .setIcon(android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_launcher))
                    .setIntent(i)
                    .build();
            sm.requestPinShortcut(info, null);
        } catch (Exception e) {
            toast("Não deu para criar o atalho");
        }
    }

    private String screenInfo() {
        try {
            android.view.Display d = getWindowManager().getDefaultDisplay();
            StringBuilder modes = new StringBuilder();
            java.util.TreeSet<Integer> rates = new java.util.TreeSet<>();
            for (android.view.Display.Mode m : d.getSupportedModes()) rates.add(Math.round(m.getRefreshRate()));
            for (Integer r : rates) modes.append(r).append("Hz ");
            return "Tela agora: " + Math.round(d.getRefreshRate()) + "Hz\nO seu celular suporta: " + modes.toString().trim()
                    + (rates.size() > 1 ? "\nPara jogos, deixe na maior taxa em Configurações > Tela." : "");
        } catch (Exception e) {
            return "Informação da tela indisponível";
        }
    }

    private String privateDnsInfo() {
        if (Build.VERSION.SDK_INT < 28) return "";
        try {
            android.net.ConnectivityManager cm = getSystemService(android.net.ConnectivityManager.class);
            android.net.LinkProperties lp = cm.getLinkProperties(cm.getActiveNetwork());
            if (lp == null) return "";
            String n = lp.getPrivateDnsServerName();
            if (n != null) return "DNS privado: " + n;
            return lp.isPrivateDnsActive() ? "DNS privado: automático" : "DNS privado: desligado (o da operadora)";
        } catch (Exception e) {
            return "";
        }
    }

    private void runNetBoost(Button b, LinearLayout res) {
        b.setEnabled(false);
        b.setText("> TURBINANDO..._");
        res.removeAllViews();
        new Thread(() -> {
            int n = Optimizer.killBackground(this, null);
            h.post(() -> res.addView(line("[✔] " + n + " apps em segundo plano fechados (param de usar sua internet escondido)", ACCENT)));
            String[][] dns = {
                    {"Cloudflare", "1.1.1.1", "one.one.one.one"},
                    {"Google", "8.8.8.8", "dns.google"},
                    {"Quad9", "9.9.9.9", "dns.quad9.net"},
                    {"AdGuard (bloqueia anúncios)", "94.140.14.14", "dns.adguard-dns.com"}};
            int best = -1, bestMs = Integer.MAX_VALUE;
            for (int i = 0; i < dns.length; i++) {
                int[] r = NetworkMonitor.measure(dns[i][1], 853, 4);
                final String txt = r[0] < 0 ? "[X] " + dns[i][0] + ": sem resposta"
                        : "[ ] " + dns[i][0] + ": " + r[0] + " ms";
                final int col = r[0] < 0 ? BAD : r[0] < 80 ? ACCENT : WARN;
                h.post(() -> res.addView(line(txt, col)));
                if (r[0] >= 0 && r[0] < bestMs) { bestMs = r[0]; best = i; }
            }
            final int bi = best, bms = bestMs;
            h.post(() -> {
                b.setEnabled(true);
                b.setText("> TURBINAR DE NOVO_");
                if (bi < 0) { res.addView(line("[X] Sem internet agora", BAD)); return; }
                final String host = dns[bi][2];
                res.addView(line("\n[✔] MAIS RÁPIDO PARA VOCÊ: " + dns[bi][0] + " (" + bms + " ms)\nEndereço: " + host, ACCENT));
                res.addView(btn("> COPIAR E ABRIR DNS PRIVADO_", v -> {
                    android.content.ClipboardManager cb = getSystemService(android.content.ClipboardManager.class);
                    if (cb != null) cb.setPrimaryClip(android.content.ClipData.newPlainText("dns", host));
                    toast("Copiado! Vá em DNS privado, escolha \"nome do host\" e cole");
                    open(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
                }));
                res.addView(line("No realme: Configurações > Conexão e compartilhamento > DNS privado > "
                        + "Nome do host > cole o endereço. DNS mais rápido deixa sites e logins mais ágeis; "
                        + "o ping dentro do jogo depende mais do Wi-Fi/sinal.", SUB));
            });
        }).start();
    }

    private TextView line(String s, int color) {
        TextView t = tv(s, 13, color, false);
        t.setPadding(0, dp(6), 0, 0);
        return t;
    }

    private void buildCleaner() {
        LinearLayout c = card("LIMPEZA INTELIGENTE");
        c.addView(tv("Procura lixo de verdade: instaladores APK velhos, miniaturas, temporários, logs e pastas vazias. "
                + "Você vê tudo antes e escolhe o que apagar.", 13, SUB, false));
        if (!Cleaner.hasAccess(this)) {
            c.addView(btn("> PERMITIR ACESSO AOS ARQUIVOS_", v -> {
                if (Build.VERSION.SDK_INT >= 30) {
                    open(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:" + getPackageName())));
                } else {
                    requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2);
                }
            }));
            return;
        }
        final LinearLayout res = new LinearLayout(this);
        res.setOrientation(LinearLayout.VERTICAL);
        final Button b = btn("> ANALISAR ARMAZENAMENTO_", null);
        b.setOnClickListener(v -> {
            b.setEnabled(false);
            b.setText("> ANALISANDO..._");
            res.removeAllViews();
            new Thread(() -> {
                final Cleaner.Result r = Cleaner.scan(this);
                h.post(() -> { b.setEnabled(true); b.setText("> ANALISAR DE NOVO_"); showClean(res, r); });
            }).start();
        });
        c.addView(b);
        c.addView(res);
    }

    private void showClean(LinearLayout res, Cleaner.Result r) {
        res.addView(line(r.scanned + " arquivos verificados", SUB));
        final Cleaner.Group[] groups = {r.apks, r.thumbs, r.temps, r.empties, r.cache};
        final CheckBox[] checks = new CheckBox[groups.length];
        long total = 0;
        for (int i = 0; i < groups.length; i++) {
            Cleaner.Group g = groups[i];
            CheckBox cb = new CheckBox(this);
            String count = g == r.empties ? g.files.size() + " pastas" : g.files.size() + " itens • " + DeviceInfo.fmt(g.size);
            cb.setText(g.name + "\n" + count + "\n" + g.desc);
            cb.setTextColor(TXT);
            cb.setTextSize(13);
            cb.setTypeface(Typeface.MONOSPACE);
            cb.setButtonTintList(ColorStateList.valueOf(ACCENT));
            cb.setChecked(!g.files.isEmpty());
            cb.setEnabled(!g.files.isEmpty());
            cb.setPadding(0, dp(6), 0, dp(6));
            res.addView(cb);
            checks[i] = cb;
            total += g.size;
        }
        res.addView(line("Total que pode ser liberado: " + DeviceInfo.fmt(total), ACCENT));
        Button clean = btn("> LIMPAR SELECIONADOS_", null);
        clean.setBackground(round(BAD, 4, 0));
        clean.setTextColor(0xFF000000);
        clean.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Apagar arquivos?")
                .setMessage("Os itens marcados serão apagados para sempre.")
                .setPositiveButton("Apagar", (d, w) -> {
                    final java.util.List<java.io.File> del = new java.util.ArrayList<>();
                    for (int i = 0; i < groups.length; i++) if (checks[i].isChecked()) del.addAll(groups[i].files);
                    clean.setEnabled(false);
                    new Thread(() -> {
                        long freed = Cleaner.delete(del);
                        h.post(() -> {
                            toast(DeviceInfo.fmt(freed) + " liberados");
                            res.removeAllViews();
                            res.addView(line("[✔] Limpeza concluída: " + DeviceInfo.fmt(freed) + " liberados", ACCENT));
                        });
                    }).start();
                })
                .setNegativeButton("Cancelar", null).show());
        res.addView(clean);
        if (!r.big.isEmpty()) {
            StringBuilder sb = new StringBuilder("\nARQUIVOS GRANDES (confira se ainda precisa, não apago sozinho):\n");
            for (java.io.File f : r.big) sb.append("• ").append(f.getName()).append(" — ").append(DeviceInfo.fmt(f.length())).append("\n");
            res.addView(line(sb.toString().trim(), WARN));
            res.addView(btn2("> ABRIR ARMAZENAMENTO DO SISTEMA", v -> open(new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))));
        }
    }

    private void buildEco() {
        SharedPreferences p = ProfileManager.prefs(this);
        LinearLayout c = card("MODO ECONOMIA INTELIGENTE");
        c.addView(tv("Quando a bateria baixar do limite, ele sozinho abaixa o brilho, apaga a tela mais rápido, "
                + "desliga a vibração e fecha apps em segundo plano. Quando você carregar, tudo volta como estava. "
                + "Não ativa durante o Modo Jogo.", 13, SUB, false));
        boolean on = p.getBoolean("eco_on", false);
        if (on && p.getBoolean("eco_applied", false)) c.addView(line("● ECONOMIA ATIVA AGORA", WARN));
        Button tg = btn(on ? "> DESLIGAR ECONOMIA INTELIGENTE" : "> LIGAR ECONOMIA INTELIGENTE_", v -> {
            boolean now = !p.getBoolean("eco_on", false);
            if (now && !Settings.System.canWrite(this)) { askPerm("write"); return; }
            p.edit().putBoolean("eco_on", now).apply();
            Intent i = new Intent(this, WatchService.class);
            try {
                if (now) startForegroundService(i); else stopService(i);
            } catch (Exception e) { toast("Não deu para iniciar agora"); }
            show(tab);
        });
        if (on) { tg.setBackground(round(0x00000000, 4, ACCENT)); tg.setTextColor(ACCENT); }
        c.addView(tg);
        c.addView(tv("\nAtivar quando a bateria chegar em:", 13, SUB, false));
        c.addView(chips(new String[]{"15%", "20%", "30%", "50%"}, new int[]{15, 20, 30, 50},
                p.getInt("eco_threshold", 20), v -> p.edit().putInt("eco_threshold", v).apply()));
        c.addView(btn2("> ECONOMIZAR AGORA", v -> applyProfile(1)));
        c.addView(btn2("> ECONOMIA DE BATERIA DO ANDROID", v -> open(new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))));
    }

    // ------------------------------------------------------------------ IA

    private void buildAi() {
        AiClient.migrate(this);
        LinearLayout ie = card("AI INTELLIGENCE");
        ie.addView(tv("Análise inteligente do aparelho, recomendações e Smart Auto Pilot.", 12, SUB, false));
        ie.addView(btn("> ULTRA AI ENGINE_", v -> show(12)));
        ie.addView(btn2("> SYSTEM HEALTH REPORT", v -> show(11)));
        SharedPreferences p = ProfileManager.prefs(this);
        LinearLayout top = card(null);
        top.addView(tv("▌JARVIS // ASSISTENTE DO APP", 12, ACCENT, true));
        top.addView(tv("Fale ou escreva: \"bom dia\", \"capacidade máxima\", \"liga a lanterna\", "
                + "\"joga Minecraft\", \"brilho em 40\"... Toque no mascote lá em cima para chamar de qualquer aba.", 13, SUB, false));
        top.addView(btn("> FALAR COM O JARVIS_", v -> openJarvis(true)));
        top.addView(btn2("> ABRIR CHAT", v -> openJarvis(false)));

        LinearLayout sv = card("SERVIDOR JARVIS (CLOUDFLARE) — USADO PRIMEIRO");
        final CheckBox srvOn = new CheckBox(this);
        srvOn.setText("Usar meu servidor Jarvis");
        srvOn.setTextColor(TXT);
        srvOn.setTypeface(Typeface.MONOSPACE);
        srvOn.setButtonTintList(ColorStateList.valueOf(ACCENT));
        srvOn.setChecked(p.getBoolean("srv_on", true));
        sv.addView(srvOn);
        sv.addView(tv("endereço", 12, SUB, false));
        final EditText srvUrl = ed(p.getString("srv_url", AiClient.SERVER_URL), false, false);
        sv.addView(srvUrl);
        sv.addView(tv("caminho do chat", 12, SUB, false));
        final EditText srvPath = ed(p.getString("srv_path", "/chat"), false, false);
        sv.addView(srvPath);
        sv.addView(tv("SENHA (a mesma do secret SENHA no Cloudflare)", 12, SUB, false));
        final EditText srvSenha = ed(p.getString("srv_senha", ""), false, true);
        sv.addView(srvSenha);
        sv.addView(tv("As chaves das IAs ficam escondidas no servidor. Se ele falhar, o Jarvis usa as chaves abaixo.", 12, SUB, false));

        LinearLayout c = card("RESERVA: 3 IAs NO CELULAR (OPCIONAL)");
        final EditText[] keys = new EditText[AiClient.SLOTS.length];
        final EditText[] models = new EditText[AiClient.SLOTS.length];
        for (int i = 0; i < AiClient.SLOTS.length; i++) {
            String[] sl = AiClient.SLOTS[i];
            c.addView(tv((i > 0 ? "\n" : "") + "IA " + (i + 1) + " • " + sl[0] + " — chave", 13, SUB, false));
            keys[i] = ed(p.getString("key_" + sl[1], ""), false, true);
            c.addView(keys[i]);
            c.addView(tv("modelo", 12, SUB, false));
            models[i] = ed(p.getString("model_" + sl[1], sl[3]), false, false);
            c.addView(models[i]);
        }
        c.addView(tv("\nAs chaves ficam salvas só no seu celular. Deixe vazio o que não usar.", 12, SUB, false));

        LinearLayout lo = card("IA LOCAL (OFFLINE) — OPCIONAL");
        final CheckBox localOn = new CheckBox(this);
        localOn.setText("Usar IA local primeiro");
        localOn.setTextColor(TXT);
        localOn.setTypeface(Typeface.MONOSPACE);
        localOn.setButtonTintList(ColorStateList.valueOf(ACCENT));
        localOn.setChecked(p.getBoolean("local_on", false));
        lo.addView(localOn);
        lo.addView(tv("endereço", 12, SUB, false));
        final EditText localBase = ed(p.getString("local_base", AiClient.LOCAL_BASE), false, false);
        lo.addView(localBase);
        lo.addView(tv("modelo", 12, SUB, false));
        final EditText localModel = ed(p.getString("local_model", AiClient.LOCAL_MODEL), false, false);
        lo.addView(localModel);
        lo.addView(tv("Ollama no Termux: pkg install ollama • ollama serve & • ollama pull llama3.2:3b", 12, SUB, false));

        LinearLayout per = card("PERSONALIDADE E VOZ");
        per.addView(tv("Nome", 13, SUB, false));
        final EditText name = ed(p.getString("ai_name", "Jarvis"), false, false);
        per.addView(name);
        per.addView(tv("\nComo ele fala e age", 13, SUB, false));
        final EditText persona = ed(p.getString("ai_persona", AiClient.DEFAULT_PERSONA), true, false);
        per.addView(persona);
        final CheckBox voice = new CheckBox(this);
        voice.setText("Jarvis responde em voz alta");
        voice.setTextColor(TXT);
        voice.setTypeface(Typeface.MONOSPACE);
        voice.setButtonTintList(ColorStateList.valueOf(ACCENT));
        voice.setChecked(p.getBoolean("jarvis_voice", true));
        per.addView(voice);

        Button save = btn("> SALVAR JARVIS_", v -> {
            SharedPreferences.Editor e = p.edit();
            for (int i = 0; i < AiClient.SLOTS.length; i++) {
                String id = AiClient.SLOTS[i][1];
                String mdl = models[i].getText().toString().trim();
                e.putString("key_" + id, keys[i].getText().toString().trim())
                 .putString("model_" + id, mdl.isEmpty() ? AiClient.SLOTS[i][3] : mdl);
            }
            String nm = name.getText().toString().trim();
            e.putBoolean("srv_on", srvOn.isChecked())
             .putString("srv_url", srvUrl.getText().toString().trim())
             .putString("srv_path", srvPath.getText().toString().trim())
             .putString("srv_senha", srvSenha.getText().toString().trim())
             .putBoolean("local_on", localOn.isChecked())
             .putString("local_base", localBase.getText().toString().trim())
             .putString("local_model", localModel.getText().toString().trim())
             .putString("ai_name", nm.isEmpty() ? "Jarvis" : nm)
             .putString("ai_persona", persona.getText().toString().trim())
             .putBoolean("jarvis_voice", voice.isChecked())
             .apply();
            toast("Jarvis atualizado");
        });
        content.addView(save);

        LinearLayout k = card("ONDE PEGAR AS CHAVES (GRÁTIS)");
        k.addView(tv("• Gemini: aistudio.google.com/apikey\n• Groq: console.groq.com/keys\n• OpenRouter: openrouter.ai/keys", 13, TXT, false));

        LinearLayout r = card("REGRAS");
        r.addView(tv("O app não coloca filtros próprios, mas cada IA segue as regras do serviço dela.", 12, SUB, false));
    }

    private void openJarvis(boolean voice) {
        startActivity(new Intent(this, AiActivity.class).putExtra(AiActivity.EXTRA_VOICE, voice));
    }

    private EditText ed(String value, boolean multi, boolean secret) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setTextColor(TXT);
        e.setHintTextColor(SUB);
        e.setTextSize(14);
        e.setTypeface(Typeface.MONOSPACE);
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        e.setBackground(round(BG, 4, a(0x66)));
        if (secret) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        } else if (multi) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            e.setMinLines(3);
            e.setGravity(Gravity.TOP | Gravity.START);
        } else {
            e.setInputType(InputType.TYPE_CLASS_TEXT);
            e.setSingleLine(true);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(4);
        e.setLayoutParams(lp);
        return e;
    }

    // ------------------------------------------------------------------ SISTEMA (controle do celular)

    public interface IntCb { void on(int v); }
    private boolean torchOn = false;

    private void buildSystem() {
        if (!Settings.System.canWrite(this)) {
            LinearLayout w = card("ACESSO NECESSÁRIO");
            w.setBackground(round(0xFF1A0707, 6, BAD));
            w.addView(tv("Para mudar brilho, tela, fonte e sons, libere \"Modificar configurações do sistema\" para o Realme Booster Ultra.", 13, TXT, false));
            w.addView(btn("> LIBERAR ACESSO_", v -> askPerm("write")));
        }

        buildCleaner();
        LinearLayout bm = card("ENERGIA");
        bm.addView(btn2("> ABRIR BATTERY MAX (economia, alertas, Battery AI)", v -> show(10)));

        LinearLayout p = card("PERFIS RÁPIDOS");
        p.addView(tv("Muda várias configurações de uma vez:", 13, SUB, false));
        p.addView(btn("> PERFIL JOGO", v -> applyProfile(0)));
        p.addView(btn2("> PERFIL ECONOMIA", v -> applyProfile(1)));
        p.addView(btn2("> PERFIL NORMAL", v -> applyProfile(2)));
        p.addView(tv("JOGO: brilho máximo, tela sem apagar, sem sons/vibração de toque, mídia 70%\n"
                + "ECONOMIA: brilho baixo, tela apaga em 30s, sem vibração\n"
                + "NORMAL: brilho automático, tela 1 min, sons e vibração ligados", 12, SUB, false));

        LinearLayout d = card("TELA");
        d.addView(tv("Brilho", 13, SUB, false));
        d.addView(seek(255, sysGet(Settings.System.SCREEN_BRIGHTNESS, 128), v -> {
            if (sysPut(Settings.System.SCREEN_BRIGHTNESS_MODE, 0)) sysPut(Settings.System.SCREEN_BRIGHTNESS, Math.max(1, v));
        }));
        sysToggle(d, "Brilho automático", Settings.System.SCREEN_BRIGHTNESS_MODE);
        sysToggle(d, "Girar tela automaticamente", Settings.System.ACCELEROMETER_ROTATION);
        d.addView(tv("\nTela apaga depois de:", 13, SUB, false));
        d.addView(chips(new String[]{"15s", "30s", "1m", "2m", "5m", "10m"},
                new int[]{15000, 30000, 60000, 120000, 300000, 600000},
                sysGet(Settings.System.SCREEN_OFF_TIMEOUT, 30000),
                v -> sysPut(Settings.System.SCREEN_OFF_TIMEOUT, v)));
        d.addView(tv("\nTamanho da letra do sistema:", 13, SUB, false));
        float fs = Settings.System.getFloat(getContentResolver(), Settings.System.FONT_SCALE, 1f);
        d.addView(chips(new String[]{"P", "Normal", "G", "GG"}, new int[]{85, 100, 115, 130},
                Math.round(fs * 100), v -> setFontScale(v / 100f)));

        LinearLayout snd = card("SOM E VIBRAÇÃO");
        android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
        int[] streams = {android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.STREAM_RING,
                android.media.AudioManager.STREAM_NOTIFICATION, android.media.AudioManager.STREAM_ALARM};
        String[] sn = {"Mídia e jogos", "Toque", "Notificações", "Alarme"};
        for (int i = 0; i < streams.length; i++) {
            final int st = streams[i];
            snd.addView(tv(sn[i], 13, SUB, false));
            snd.addView(seek(am.getStreamMaxVolume(st), am.getStreamVolume(st), v -> {
                try { am.setStreamVolume(st, v, 0); }
                catch (Exception e) { toast("O sistema bloqueou (o Não perturbe pode estar ligado)"); }
            }));
        }
        sysToggle(snd, "Vibrar ao tocar na tela", Settings.System.HAPTIC_FEEDBACK_ENABLED);
        sysToggle(snd, "Sons ao tocar", Settings.System.SOUND_EFFECTS_ENABLED);

        LinearLayout f = card("LANTERNA");
        Button tb = btn(torchOn ? "> DESLIGAR LANTERNA" : "> LIGAR LANTERNA", null);
        tb.setOnClickListener(v -> { toggleTorch(); tb.setText(torchOn ? "> DESLIGAR LANTERNA" : "> LIGAR LANTERNA"); });
        f.addView(tb);

        LinearLayout c = card("CENTRAL DO CELULAR");
        c.addView(tv("Atalhos diretos para cada parte do sistema:", 13, SUB, false));
        String[][] sc = {
                {"Wi-Fi", Settings.ACTION_WIFI_SETTINGS},
                {"Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS},
                {"Rede e dados móveis", Settings.ACTION_WIRELESS_SETTINGS},
                {"Modo avião", Settings.ACTION_AIRPLANE_MODE_SETTINGS},
                {"Tela", Settings.ACTION_DISPLAY_SETTINGS},
                {"Som", Settings.ACTION_SOUND_SETTINGS},
                {"Papel de parede", Intent.ACTION_SET_WALLPAPER},
                {"Economia de bateria", Settings.ACTION_BATTERY_SAVER_SETTINGS},
                {"Segurança e bloqueio de tela", Settings.ACTION_SECURITY_SETTINGS},
                {"Privacidade", Settings.ACTION_PRIVACY_SETTINGS},
                {"Localização", Settings.ACTION_LOCATION_SOURCE_SETTINGS},
                {"Apps padrão", Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS},
                {"Idioma e teclado", Settings.ACTION_LOCALE_SETTINGS},
                {"Data e hora", Settings.ACTION_DATE_SETTINGS},
                {"Acessibilidade", Settings.ACTION_ACCESSIBILITY_SETTINGS},
                {"Sobre o telefone", Settings.ACTION_DEVICE_INFO_SETTINGS},
                {"Opções do desenvolvedor", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS},
                {"Todas as configurações", Settings.ACTION_SETTINGS}};
        for (String[] e : sc) {
            final String action = e[1];
            c.addView(btn2("[ ] " + e[0], v -> open(new Intent(action))));
        }

        LinearLayout lim = card("LIMITES SEM ROOT");
        lim.addView(tv("O Android não deixa nenhum app comum mudar a velocidade da CPU/GPU, as animações do sistema, "
                + "a interface do realme UI nem desinstalar apps do sistema. Essas partes só mudam pelos atalhos "
                + "acima ou com root.", 13, SUB, false));
    }

    private int sysGet(String key, int def) {
        return Settings.System.getInt(getContentResolver(), key, def);
    }

    private boolean sysPut(String key, int v) {
        if (!Settings.System.canWrite(this)) { askPerm("write"); return false; }
        try {
            return Settings.System.putInt(getContentResolver(), key, v);
        } catch (Exception e) {
            toast("O sistema bloqueou essa mudança");
            return false;
        }
    }

    private void sysToggle(LinearLayout c, String label, String key) {
        CheckBox cb = new CheckBox(this);
        cb.setText(label);
        cb.setTextColor(TXT);
        cb.setTextSize(14);
        cb.setTypeface(Typeface.MONOSPACE);
        cb.setButtonTintList(ColorStateList.valueOf(ACCENT));
        cb.setChecked(sysGet(key, 0) == 1);
        cb.setOnCheckedChangeListener((v, on) -> {
            if (!sysPut(key, on ? 1 : 0)) {
                v.setOnCheckedChangeListener(null);
                v.setChecked(!on);
                show(tab);
            }
        });
        c.addView(cb);
    }

    private SeekBar seek(int max, int value, IntCb cb) {
        SeekBar sb = new SeekBar(this);
        sb.setMax(Math.max(1, max));
        sb.setProgress(value);
        sb.setProgressTintList(ColorStateList.valueOf(ACCENT));
        sb.setThumbTintList(ColorStateList.valueOf(ACCENT));
        sb.setProgressBackgroundTintList(ColorStateList.valueOf(TRACK));
        sb.setPadding(dp(8), dp(10), dp(8), dp(10));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean user) { if (user) cb.on(v); }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        return sb;
    }

    private View chips(String[] labels, int[] values, int current, IntCb cb) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int best = 0;
        for (int i = 0; i < values.length; i++) {
            if (Math.abs(values[i] - current) < Math.abs(values[best] - current)) best = i;
        }
        for (int i = 0; i < labels.length; i++) {
            final int val = values[i];
            boolean sel = i == best;
            Button b = new Button(this);
            b.setText(labels[i]);
            b.setAllCaps(false);
            b.setTextSize(12);
            b.setPadding(0, 0, 0, 0);
            b.setMinWidth(0);
            b.setMinimumWidth(0);
            b.setStateListAnimator(null);
            b.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            b.setTextColor(sel ? BG : ACCENT);
            b.setBackground(sel ? round(ACCENT, 4, 0) : round(0x00000000, 4, a(0x66)));
            b.setOnClickListener(v -> { cb.on(val); show(tab); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(38), 1f);
            lp.setMargins(dp(2), dp(6), dp(2), 0);
            row.addView(b, lp);
        }
        return row;
    }

    private void setFontScale(float f) {
        if (!Settings.System.canWrite(this)) { askPerm("write"); return; }
        try {
            Settings.System.putFloat(getContentResolver(), Settings.System.FONT_SCALE, f);
        } catch (Exception e) {
            toast("Seu Android não deixa apps mudarem a letra. Abrindo Tela...");
            open(new Intent(Settings.ACTION_DISPLAY_SETTINGS));
        }
    }

    private void toggleTorch() {
        try {
            android.hardware.camera2.CameraManager cm =
                    (android.hardware.camera2.CameraManager) getSystemService(CAMERA_SERVICE);
            for (String id : cm.getCameraIdList()) {
                Boolean has = cm.getCameraCharacteristics(id)
                        .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (Boolean.TRUE.equals(has)) {
                    cm.setTorchMode(id, !torchOn);
                    torchOn = !torchOn;
                    return;
                }
            }
            toast("Lanterna não encontrada");
        } catch (Exception e) {
            toast("Não deu pra usar a lanterna agora (a câmera pode estar em uso)");
        }
    }

    private void applyProfile(int which) {
        if (!Settings.System.canWrite(this)) { askPerm("write"); return; }
        android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
        String name;
        if (which == 0) {
            sysPut(Settings.System.SCREEN_BRIGHTNESS_MODE, 0);
            sysPut(Settings.System.SCREEN_BRIGHTNESS, 255);
            sysPut(Settings.System.SCREEN_OFF_TIMEOUT, 600000);
            sysPut(Settings.System.HAPTIC_FEEDBACK_ENABLED, 0);
            sysPut(Settings.System.SOUND_EFFECTS_ENABLED, 0);
            int st = android.media.AudioManager.STREAM_MUSIC;
            try { am.setStreamVolume(st, Math.round(am.getStreamMaxVolume(st) * 0.7f), 0); } catch (Exception ignored) {}
            name = "JOGO";
        } else if (which == 1) {
            sysPut(Settings.System.SCREEN_BRIGHTNESS_MODE, 0);
            sysPut(Settings.System.SCREEN_BRIGHTNESS, 50);
            sysPut(Settings.System.SCREEN_OFF_TIMEOUT, 30000);
            sysPut(Settings.System.HAPTIC_FEEDBACK_ENABLED, 0);
            name = "ECONOMIA";
        } else {
            sysPut(Settings.System.SCREEN_BRIGHTNESS_MODE, 1);
            sysPut(Settings.System.SCREEN_OFF_TIMEOUT, 60000);
            sysPut(Settings.System.HAPTIC_FEEDBACK_ENABLED, 1);
            sysPut(Settings.System.SOUND_EFFECTS_ENABLED, 1);
            name = "NORMAL";
        }
        toast("Perfil " + name + " aplicado");
        show(tab);
    }

    // ------------------------------------------------------------------ REDE

    private void buildNet() {
        LinearLayout c = card("CONEXÃO");
        c.addView(tv(NetworkMonitor.describe(this), 15, TXT, false));
        c.addView(tv(privateDnsInfo(), 13, SUB, false));

        LinearLayout la = card("NETWORK LATENCY AI");
        la.addView(tv("Mede ping, oscilação (jitter) e perda de pacotes, e dá uma nota de A a F para jogos.", 12, SUB, false));
        final LinearLayout laRes = new LinearLayout(this);
        laRes.setOrientation(LinearLayout.VERTICAL);
        final Button laBtn = btn("> ANALISAR CONEXÃO_", null);
        laBtn.setOnClickListener(v -> runLatencyAI(laBtn, laRes));
        la.addView(laBtn);
        la.addView(laRes);

        String nh = netHistoryText();
        if (!nh.isEmpty()) {
            LinearLayout hn = card("HISTÓRICO DE INSTABILIDADE");
            hn.addView(tv(nh, 12, TXT, false));
        }

        LinearLayout wa = card("WI-FI ANALYZER");
        wa.addView(tv(wifiReport(), 13, TXT, false));

        LinearLayout ib = card("INTERNET BOOSTER");
        ib.addView(tv("Fecha apps que usam internet escondido, testa os servidores de DNS mais rápidos "
                + "para a sua conexão e te mostra qual usar.", 13, SUB, false));
        final LinearLayout ibRes = new LinearLayout(this);
        ibRes.setOrientation(LinearLayout.VERTICAL);
        final Button ibBtn = btn("> TURBINAR INTERNET_", null);
        ibBtn.setOnClickListener(v -> runNetBoost(ibBtn, ibRes));
        ib.addView(ibBtn);
        ib.addView(ibRes);

        LinearLayout p = card("PING MONITOR");
        p.addView(tv("Mede o tempo de resposta (5 tentativas por servidor). Quanto menor, melhor.", 13, SUB, false));
        final LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        final Button b = btn("> TESTAR SINAL_", null);
        b.setOnClickListener(v -> runPing(b, results));
        p.addView(b);
        p.addView(results);

        LinearLayout t = card("DICAS DE REDE");
        t.addView(tv("• Wi-Fi 5 GHz costuma ter menos atraso que 2,4 GHz (se o roteador e o celular suportarem)\n"
                + "• Fique perto do roteador: paredes aumentam o ping\n"
                + "• Pare downloads e vídeos em outros aparelhos/apps antes de jogar\n"
                + "• Ping acima de 150 ms causa travadas em jogos online\n"
                + "• Variação alta (jitter) = conexão instável, mesmo com ping baixo", 14, TXT, false));
    }

    private void runPing(Button b, LinearLayout results) {
        b.setEnabled(false);
        b.setText("Testando...");
        results.removeAllViews();
        new Thread(() -> {
            String[][] servers = {
                    {"Google", "8.8.8.8", "53"},
                    {"Cloudflare", "1.1.1.1", "53"},
                    {"Roblox", "www.roblox.com", "443"},
                    {"Minecraft", "www.minecraft.net", "443"}};
            for (String[] s : servers) {
                final int[] r = NetworkMonitor.measure(s[1], Integer.parseInt(s[2]), 5);
                final String name = s[0];
                h.post(() -> { if (tab == 4) results.addView(pingRow(name, r)); });
            }
            h.post(() -> { b.setEnabled(true); b.setText("> TESTAR DE NOVO_"); });
        }).start();
    }

    private View pingRow(String name, int[] r) {
        String txt; int color;
        if (r[0] < 0) {
            txt = name + ": sem resposta";
            color = BAD;
        } else {
            String v; int avg = r[0];
            if (avg < 60) { v = "ótimo"; color = ACCENT; }
            else if (avg < 100) { v = "bom"; color = ACCENT; }
            else if (avg < 150) { v = "ok"; color = WARN; }
            else { v = "ruim"; color = BAD; }
            txt = name + ": " + avg + " ms (" + v + ") • variação " + (r[2] - r[1]) + " ms";
        }
        TextView t = tv(txt, 15, color, true);
        t.setPadding(0, dp(8), 0, 0);
        return t;
    }

    // ------------------------------------------------------------------ AJUSTES

    private void buildSettings() {
        content.addView(tv("SETTINGS & DIAGNOSTICS", 18, ACCENT, true));
        autoRestoreCard();
        reportCard();
        buildUltraInterface();
        LinearLayout c = card("PERMISSÕES");
        permRow(c, "Painel flutuante (sobrepor apps)", "overlay");
        permRow(c, "Não perturbe", "dnd");
        permRow(c, "Mudar brilho e tempo de tela", "write");
        permRow(c, "Não ser fechado pela economia de bateria", "battery");

        LinearLayout s = card("ATALHOS DO SISTEMA");
        s.addView(btn2("[ ] Tela e taxa de atualização", v -> open(new Intent(Settings.ACTION_DISPLAY_SETTINGS))));
        s.addView(btn2("[ ] Bateria", v -> open(new Intent(Intent.ACTION_POWER_USAGE_SUMMARY))));
        s.addView(btn2("[ ] Armazenamento", v -> open(new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))));
        s.addView(btn2("[ ] Apps instalados", v -> open(new Intent(Settings.ACTION_APPLICATION_SETTINGS))));
        s.addView(btn2("[ ] Opções do desenvolvedor", v -> open(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))));

        LinearLayout t = card("DICAS PARA O REALME C71");
        t.addView(tv("• Em Configurações > Tela, use a maior taxa de atualização disponível\n"
                + "• Ative o \"Espaço de Jogos\" do realme, se aparecer no seu celular\n"
                + "• Deixe pelo menos 15% do armazenamento livre\n"
                + "• Evite jogar carregando: esquenta e o chip reduz a velocidade\n"
                + "• Gráfico médio com FPS estável é melhor que gráfico alto travando\n"
                + "• Opções do desenvolvedor: escalas de animação em 0,5x deixam o sistema mais ágil\n"
                + "• Reinicie o celular a cada 2–3 dias\n"
                + "• Tire a capinha em partidas longas se o celular esquentar", 14, TXT, false));

        LinearLayout h2 = card("O QUE ESTE APP FAZ DE VERDADE");
        h2.addView(tv("Sem root, nenhum app consegue mudar a frequência da CPU/GPU nem aumentar o FPS "
                + "acima do que o jogo e o chip permitem. O Realme Booster Ultra libera memória de apps em segundo plano, "
                + "corta distrações, mantém a tela ligada, mostra calor e ping em tempo real e te avisa "
                + "quando o celular está esquentando — que é o que mais derruba o desempenho.", 13, SUB, false));

        content.addView(tv("REALME BOOSTER ULTRA v5.0 • ULTIMATE EDITION", 11, SUB, false));
    }

    private void permRow(LinearLayout c, String label, String perm) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));
        row.addView(tv(label, 14, TXT, false), new LinearLayout.LayoutParams(0, -2, 1f));
        boolean ok = hasPerm(perm);
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setStateListAnimator(null);
        b.setText(ok ? "✔ OK" : "Permitir");
        b.setTextColor(ok ? ACCENT : BG);
        b.setBackground(ok ? round(0x00000000, 10, ACCENT) : round(ACCENT, 10, 0));
        b.setOnClickListener(v -> askPerm(perm));
        row.addView(b, new LinearLayout.LayoutParams(dp(100), dp(40)));
        c.addView(row);
    }

    // ------------------------------------------------------------------ permissões

    private boolean hasPerm(String perm) {
        if (perm == null) return true;
        switch (perm) {
            case "overlay": return Settings.canDrawOverlays(this);
            case "write": return Settings.System.canWrite(this);
            case "dnd": {
                NotificationManager nm = getSystemService(NotificationManager.class);
                return nm != null && nm.isNotificationPolicyAccessGranted();
            }
            case "battery": {
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
            }
        }
        return true;
    }

    private void askPerm(String perm) {
        if (perm == null) return;
        Uri pkg = Uri.parse("package:" + getPackageName());
        switch (perm) {
            case "overlay":
                toast("Ative \"Realme Booster Ultra\" para mostrar o painel sobre os jogos");
                open(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg));
                break;
            case "write":
                toast("Permita \"Realme Booster Ultra\" modificar configurações do sistema");
                open(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, pkg));
                break;
            case "dnd":
                toast("Ative o acesso ao Não perturbe para \"Realme Booster Ultra\"");
                open(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
                break;
            case "battery":
                open(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg));
                break;
        }
    }

    private void open(Intent i) {
        try {
            startActivity(i);
        } catch (Exception e) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Exception ignored) {}
            toast("Abra pelas Configurações do celular");
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    // ------------------------------------------------------------------ UI helpers

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

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
        t.setLineSpacing(dp(2), 1f);
        t.setTypeface(Typeface.MONOSPACE, bold ? Typeface.BOLD : Typeface.NORMAL);
        return t;
    }

    private LinearLayout card(String title) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        c.setBackground(round(CARD, 6, a(0x44)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(12);
        c.setLayoutParams(lp);
        if (title != null) {
            TextView t = tv("▌" + title, 12, ACCENT, true);
            t.setShadowLayer(dp(4), 0, 0, a(0x99));
            t.setLetterSpacing(0.08f);
            t.setPadding(0, 0, 0, dp(8));
            c.addView(t);
        }
        content.addView(c);
        return c;
    }

    private Button btn(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(BG);
        b.setTextSize(15);
        b.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        b.setStateListAnimator(null);
        b.setBackground(round(ACCENT, 4, 0));
        if (l != null) b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(50));
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    private Button btn2(String s, View.OnClickListener l) {
        Button b = btn(s, l);
        b.setTextColor(ACCENT);
        b.setBackground(round(0x00000000, 4, ACCENT));
        return b;
    }

    private ProgressBar bar() {
        ProgressBar p = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        p.setMax(100);
        p.setProgressBackgroundTintList(ColorStateList.valueOf(TRACK));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(8));
        lp.topMargin = dp(8);
        lp.bottomMargin = dp(4);
        p.setLayoutParams(lp);
        return p;
    }

    private void setBar(ProgressBar p, int value, int color) {
        p.setProgress(value);
        p.setProgressTintList(ColorStateList.valueOf(color));
    }
}
