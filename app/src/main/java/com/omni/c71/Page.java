package com.omni.c71;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/** Base das abas do painel. */
abstract class Page {
    final MainActivity a;
    View view;
    LinearLayout box;

    Page(MainActivity a) { this.a = a; }

    View get() {
        if (view == null) view = build();
        return view;
    }

    View build() {
        ScrollView sv = new ScrollView(a);
        sv.setFillViewport(true);
        box = Ui.vbox(a);
        int p = Ui.dp(a, 16);
        box.setPadding(p, Ui.dp(a, 4), p, Ui.dp(a, 24));
        sv.addView(box);
        fill(box);
        return sv;
    }

    void fill(LinearLayout box) { }

    void refresh() {
        if (box != null) {
            box.removeAllViews();
            fill(box);
        }
    }

    void onResult(int req, int res, Intent data) { }

    LinearLayout card(LinearLayout parent, String title) {
        LinearLayout k = Ui.card(a);
        k.addView(Ui.title(a, title));
        parent.addView(k);
        return k;
    }

    void safeStart(Intent i) {
        try {
            a.startActivity(i);
        } catch (Throwable t) {
            Ui.toast(a, "Não disponível neste celular.");
        }
    }

    boolean installed(String pkg) {
        try {
            a.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** Verifica Shizuku antes de uma ação avançada. */
    boolean needShizuku() {
        if (Shell.ready()) return true;
        Ui.dialog(a).setTitle("Precisa do Shizuku")
                .setMessage("Essa função mexe em partes protegidas do Android. Ative o Shizuku na aba ⚡ Shizuku (sem root e sem PC).")
                .setPositiveButton("Ir para Shizuku", (d, w) -> a.show(3))
                .setNegativeButton("Agora não", null).show();
        return false;
    }
}
