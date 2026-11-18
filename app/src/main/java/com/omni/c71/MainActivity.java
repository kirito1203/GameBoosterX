package com.omni.c71;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Arrays;

import rikka.shizuku.Shizuku;

/** Painel de controle OMNI C71. */
public class MainActivity extends Activity {

    static final String[] TABS = {"🎨\nVisual", "⚙️\nSistema", "📦\nApps", "⚡\nShizuku"};

    final Page[] pages = new Page[4];
    int tab = 0;
    FrameLayout content;
    TextView[] navItems = new TextView[4];
    TextView pill;
    boolean firstResume = true;

    final Shizuku.OnBinderReceivedListener binderUp = () -> runOnUiThread(this::refreshAll);
    final Shizuku.OnBinderDeadListener binderDown = () -> runOnUiThread(this::refreshAll);
    final Shizuku.OnRequestPermissionResultListener permResult = (code, res) -> runOnUiThread(() -> {
        Ui.toast(this, res == PackageManager.PERMISSION_GRANTED
                ? "⚡ Shizuku liberado! Funções avançadas ativas."
                : "Permissão do Shizuku negada.");
        refreshAll();
    });

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.CARD);
        if (b != null) tab = b.getInt("tab", 0);
        if (getIntent() != null) tab = getIntent().getIntExtra("tab", tab);
        build();
        try {
            Shizuku.addBinderReceivedListenerSticky(binderUp);
            Shizuku.addBinderDeadListener(binderDown);
            Shizuku.addRequestPermissionResultListener(permResult);
        } catch (Throwable ignored) { }
    }

    void build() {
        LinearLayout root = Ui.vbox(this);
        root.setBackgroundColor(Ui.BG);

        // Cabeçalho
        LinearLayout head = Ui.hbox(this);
        int p = Ui.dp(this, 16);
        head.setPadding(p, Ui.dp(this, 14), p, Ui.dp(this, 10));
        LinearLayout titles = Ui.vbox(this);
        titles.addView(Ui.text(this, "OMNI C71", 24, Prefs.accent(this), true));
        titles.addView(Ui.text(this, "Controle total do seu celular", 12, Ui.SUB, false));
        head.addView(titles, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        pill = Ui.text(this, "", 12, Ui.TEXT, true);
        int pp = Ui.dp(this, 10);
        pill.setPadding(pp, Ui.dp(this, 6), pp, Ui.dp(this, 6));
        pill.setOnClickListener(v -> show(3));
        head.addView(pill);
        root.addView(head);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));

        // Navegação inferior
        LinearLayout nav = Ui.hbox(this);
        nav.setBackgroundColor(Ui.CARD);
        nav.setPadding(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            TextView t = Ui.text(this, TABS[i], 12, Ui.SUB, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
            t.setOnClickListener(v -> show(idx));
            navItems[i] = t;
            nav.addView(t, Ui.weight(this));
        }
        root.addView(nav);

        setContentView(root);
        show(tab);
        updatePill();
    }

    Page page(int i) {
        if (pages[i] == null) {
            switch (i) {
                case 0: pages[i] = new VisualPage(this); break;
                case 1: pages[i] = new SystemPage(this); break;
                case 2: pages[i] = new AppsPage(this); break;
                default: pages[i] = new ShizukuPage(this); break;
            }
        }
        return pages[i];
    }

    void show(int i) {
        tab = i;
        content.removeAllViews();
        View v = page(i).get();
        if (v.getParent() != null) ((ViewGroup) v.getParent()).removeView(v);
        content.addView(v);
        int acc = Prefs.accent(this);
        for (int k = 0; k < navItems.length; k++) {
            boolean on = k == i;
            navItems[k].setTextColor(on ? Ui.TEXT : Ui.SUB);
            navItems[k].setBackground(on ? Ui.round((acc & 0x00FFFFFF) | 0x44000000, Ui.dp(this, 14)) : null);
        }
    }

    void updatePill() {
        if (pill == null) return;
        boolean ok = Shell.ready();
        boolean run = Shell.running();
        pill.setText(ok ? "⚡ Shizuku ATIVO" : run ? "⚡ Falta permissão" : "⚡ Shizuku OFF");
        pill.setBackground(Ui.round(ok ? 0xFF0F3D2C : run ? 0xFF4A3A0A : 0xFF4A1420, Ui.dp(this, 20)));
        pill.setTextColor(ok ? Ui.GREEN : run ? Ui.YELLOW : Ui.RED);
    }

    void refreshAll() {
        updatePill();
        for (Page pg : pages) if (pg != null) pg.refresh();
    }

    /** Reconstrói tudo (usado quando a cor de destaque muda). */
    void restart() {
        Arrays.fill(pages, null);
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (firstResume) {
            firstResume = false;
            return;
        }
        refreshAll();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.hasExtra("tab")) show(intent.getIntExtra("tab", tab));
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("tab", tab);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        for (Page pg : pages) if (pg != null) pg.onResult(req, res, data);
    }

    @Override
    protected void onDestroy() {
        try {
            Shizuku.removeBinderReceivedListener(binderUp);
            Shizuku.removeBinderDeadListener(binderDown);
            Shizuku.removeRequestPermissionResultListener(permResult);
        } catch (Throwable ignored) { }
        super.onDestroy();
    }
}
