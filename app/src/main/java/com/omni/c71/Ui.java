package com.omni.c71;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.IntConsumer;

/** Pequena biblioteca de interface (tema escuro com cor de destaque). */
public final class Ui {
    private Ui() {}

    public static final int BG = 0xFF090D15;
    public static final int CARD = 0xFF131A27;
    public static final int CARD2 = 0xFF1E2738;
    public static final int TEXT = 0xFFEEF2FF;
    public static final int SUB = 0xFF8E9AB3;
    public static final int RED = 0xFFFF5470;
    public static final int GREEN = 0xFF34D399;
    public static final int YELLOW = 0xFFFFC857;

    public static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    public static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable round(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    public static Drawable press(Context c, int color, float radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), round(color, dp(c, radiusDp)), null);
    }

    public static TextView text(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    public static LinearLayout vbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout hbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = vbox(c);
        l.setBackground(round(CARD, dp(c, 18)));
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.setMargins(0, 0, 0, dp(c, 12));
        l.setLayoutParams(lp);
        return l;
    }

    public static TextView title(Context c, String s) {
        TextView t = text(c, s, 16, Prefs.accent(c), true);
        t.setPadding(0, 0, 0, dp(c, 8));
        return t;
    }

    public static TextView note(Context c, CharSequence s) {
        TextView t = text(c, s, 13, SUB, false);
        t.setPadding(0, dp(c, 2), 0, dp(c, 8));
        t.setLineSpacing(0, 1.15f);
        return t;
    }

    public static TextView label(Context c, String s) {
        TextView t = text(c, s, 14, TEXT, true);
        t.setPadding(0, dp(c, 10), 0, dp(c, 4));
        return t;
    }

    public static TextView btn(Context c, String s, int color, View.OnClickListener l) {
        TextView t = text(c, s, 14, color == YELLOW ? 0xFF1A1300 : TEXT, true);
        t.setGravity(Gravity.CENTER);
        int h = dp(c, 12), v = dp(c, 11);
        t.setPadding(h, v, h, v);
        t.setBackground(press(c, color, 12));
        t.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.setMargins(0, dp(c, 4), 0, dp(c, 4));
        t.setLayoutParams(lp);
        return t;
    }

    public static LinearLayout.LayoutParams weight(Context c) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, WRAP, 1f);
        int m = dp(c, 3);
        lp.setMargins(m, m, m, m);
        return lp;
    }

    /** Linha de opções onde uma fica marcada. */
    public static LinearLayout chips(Context c, String[] labels, int selected, IntConsumer pick) {
        LinearLayout row = hbox(c);
        int acc = Prefs.accent(c);
        TextView[] arr = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView t = text(c, labels[i], 13, TEXT, true);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            t.setPadding(dp(c, 4), dp(c, 10), dp(c, 4), dp(c, 10));
            t.setBackground(press(c, i == selected ? acc : CARD2, 12));
            t.setOnClickListener(v -> {
                for (int k = 0; k < arr.length; k++)
                    arr[k].setBackground(press(c, k == idx ? acc : CARD2, 12));
                pick.accept(idx);
            });
            arr[i] = t;
            row.addView(t, weight(c));
        }
        return row;
    }

    /** Linha de botões de ação (nenhum fica marcado). */
    public static LinearLayout actions(Context c, String[] labels, IntConsumer pick) {
        LinearLayout row = hbox(c);
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView t = text(c, labels[i], 13, TEXT, true);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            t.setPadding(dp(c, 4), dp(c, 10), dp(c, 4), dp(c, 10));
            t.setBackground(press(c, CARD2, 12));
            t.setOnClickListener(v -> pick.accept(idx));
            row.addView(t, weight(c));
        }
        return row;
    }

    public static SeekBar seek(Context c, int max, int value) {
        SeekBar s = new SeekBar(c);
        s.setMax(max);
        s.setProgress(Math.max(0, Math.min(max, value)));
        ColorStateList acc = ColorStateList.valueOf(Prefs.accent(c));
        s.setProgressTintList(acc);
        s.setThumbTintList(acc);
        s.setPadding(dp(c, 8), dp(c, 8), dp(c, 8), dp(c, 8));
        return s;
    }

    public static Switch sw(Context c, String label, boolean on, CompoundButton.OnCheckedChangeListener l) {
        Switch s = new Switch(c);
        s.setText(label);
        s.setTextColor(TEXT);
        s.setTextSize(14);
        s.setChecked(on);
        s.setPadding(0, dp(c, 8), 0, dp(c, 8));
        int acc = Prefs.accent(c);
        int[][] st = {{android.R.attr.state_checked}, {}};
        s.setThumbTintList(new ColorStateList(st, new int[]{acc, 0xFFB0B8C8}));
        s.setTrackTintList(new ColorStateList(st, new int[]{(acc & 0x00FFFFFF) | 0x88000000, 0x55FFFFFF}));
        s.setOnCheckedChangeListener(l);
        return s;
    }

    public static EditText input(Context c, String hint) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setHintTextColor(SUB);
        e.setTextColor(TEXT);
        e.setTextSize(15);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        e.setBackground(round(CARD2, dp(c, 14)));
        int p = dp(c, 12);
        e.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.setMargins(0, dp(c, 4), 0, dp(c, 6));
        e.setLayoutParams(lp);
        return e;
    }

    public static AlertDialog.Builder dialog(Context c) {
        return new AlertDialog.Builder(c, android.R.style.Theme_Material_Dialog_Alert);
    }

    public static void toast(Context c, String s) {
        if (s != null && s.length() > 220) s = s.substring(0, 220) + "…";
        Toast.makeText(c.getApplicationContext(), s, Toast.LENGTH_LONG).show();
    }

    /** Minúsculas sem acento, para buscas. */
    public static String norm(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    /** Listener de SeekBar que aplica o valor quando o dedo solta. */
    public static SeekBar.OnSeekBarChangeListener onRelease(IntConsumer live, IntConsumer done) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                if (user && live != null) live.accept(p);
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) {
                if (done != null) done.accept(s.getProgress());
            }
        };
    }
}
