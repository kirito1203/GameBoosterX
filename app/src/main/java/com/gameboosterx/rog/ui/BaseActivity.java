package com.gameboosterx.rog.ui;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gameboosterx.rog.R;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Base comum: cabeçalho ROG, helpers de cartões e execução em segundo plano. */
public abstract class BaseActivity extends Activity {

    public static final int RED = 0xFFFF1744;
    public static final int RED_DARK = 0xFFB0102E;
    public static final int ORANGE = 0xFFFF9100;
    public static final int GREEN = 0xFF00E676;
    public static final int CYAN = 0xFF18FFFF;
    public static final int GREY = 0xFF9A9A9A;

    protected final Handler ui = new Handler(Looper.getMainLooper());
    protected final ExecutorService bg = Executors.newSingleThreadExecutor();
    protected boolean resumed;

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        bg.shutdownNow();
        super.onDestroy();
    }

    protected int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    /** Configura o cabeçalho incluído via @layout/view_header. */
    protected void setupHeader(String title, String subtitle) {
        TextView t = findViewById(R.id.headerTitle);
        TextView s = findViewById(R.id.headerSubtitle);
        View back = findViewById(R.id.headerBack);
        if (t != null) t.setText(title);
        if (s != null) {
            s.setText(subtitle);
            s.setVisibility(subtitle == null || subtitle.isEmpty() ? View.GONE : View.VISIBLE);
        }
        if (back != null) back.setOnClickListener(v -> finish());
    }

    protected TextView addSection(LinearLayout parent, String title) {
        TextView tv = (TextView) LayoutInflater.from(this).inflate(R.layout.item_section, parent, false);
        tv.setText(title);
        parent.addView(tv);
        return tv;
    }

    /** Linha de cartão com título, descrição e botão opcional. */
    protected View addRow(LinearLayout parent, String title, String subtitle, String action, View.OnClickListener click) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_row, parent, false);
        ((TextView) row.findViewById(R.id.rowTitle)).setText(title);
        TextView sub = row.findViewById(R.id.rowSubtitle);
        if (subtitle == null || subtitle.isEmpty()) sub.setVisibility(View.GONE); else sub.setText(subtitle);
        TextView btn = row.findViewById(R.id.rowAction);
        if (action == null) {
            btn.setVisibility(View.GONE);
            if (click != null) row.setOnClickListener(click);
        } else {
            btn.setText(action);
            btn.setOnClickListener(click);
        }
        parent.addView(row);
        return row;
    }

    /** Cartão de texto (recomendações, explicações). */
    protected TextView addNote(LinearLayout parent, String text) {
        TextView tv = (TextView) LayoutInflater.from(this).inflate(R.layout.item_note, parent, false);
        tv.setText(text);
        parent.addView(tv);
        return tv;
    }

    protected void runBg(Runnable r) {
        try {
            bg.execute(r);
        } catch (Exception ignored) {
        }
    }

    protected static int colorForPercent(int pct) {
        if (pct >= 85) return RED;
        if (pct >= 70) return ORANGE;
        return GREEN;
    }
}
