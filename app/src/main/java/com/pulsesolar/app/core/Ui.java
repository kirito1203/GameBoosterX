package com.pulsesolar.app.core;

import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.pulsesolar.app.R;

/** Ajudantes para montar listas simples sem bibliotecas externas. */
public final class Ui {

    private Ui() {
    }

    /** Lista "rótulo — valor". */
    public static final class KvList {
        private final LinearLayout box;
        private final LayoutInflater inflater;

        public KvList(LinearLayout box) {
            this.box = box;
            this.inflater = LayoutInflater.from(box.getContext());
        }

        public void clear() {
            box.removeAllViews();
        }

        public void add(String key, String value) {
            View row = inflater.inflate(R.layout.item_kv, box, false);
            ((TextView) row.findViewById(R.id.kvKey)).setText(key);
            ((TextView) row.findViewById(R.id.kvValue)).setText(value);
            box.addView(row);
        }
    }

    /** Linha com ícone, título e subtítulo. */
    public static View appRow(LinearLayout parent, Drawable icon, String title, String sub,
                              View.OnClickListener click) {
        View row = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        ((ImageView) row.findViewById(R.id.appIcon)).setImageDrawable(icon);
        ((TextView) row.findViewById(R.id.appTitle)).setText(title);
        ((TextView) row.findViewById(R.id.appSub)).setText(sub);
        row.setOnClickListener(click);
        parent.addView(row);
        return row;
    }

    /** Cartão de diagnóstico com cor de gravidade e botão de ação (se houver). */
    public static View findingRow(LinearLayout parent, Analyzer.Finding f) {
        View row = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_finding, parent, false);
        int color = Analyzer.severityColor(f.severity);
        row.findViewById(R.id.sevBar).setBackgroundColor(color);
        TextView sev = row.findViewById(R.id.sevLabel);
        sev.setText(Analyzer.severityText(f.severity));
        sev.setTextColor(color);
        ((TextView) row.findViewById(R.id.findTitle)).setText(f.title);
        ((TextView) row.findViewById(R.id.findDetail)).setText(f.detail);
        Button action = row.findViewById(R.id.findAction);
        if (f.actions != null && f.actions.length > 0) {
            action.setVisibility(View.VISIBLE);
            action.setText(f.actionLabel);
            action.setOnClickListener(v -> Nav.open(v.getContext(), f.actions));
        }
        parent.addView(row);
        return row;
    }
}
