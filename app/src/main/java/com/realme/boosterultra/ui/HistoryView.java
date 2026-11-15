package com.realme.boosterultra.ui;

import com.realme.boosterultra.*;
import com.realme.boosterultra.engine.*;
import com.realme.boosterultra.ai.*;
import com.realme.boosterultra.gaming.*;
import com.realme.boosterultra.monitor.*;
import com.realme.boosterultra.utils.*;
import com.realme.boosterultra.ui.*;


import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.View;

import java.util.List;

/** Gráfico de linha simples (últimas 24h). */
public class HistoryView extends View {
    public interface Getter { float get(History.Sample s); }

    private final List<History.Sample> data;
    private final Getter getter;
    private final float min, max;
    private final int color;
    private final String unit;
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG), grid = new Paint(), text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final long window;

    public HistoryView(Context c, List<History.Sample> data, Getter g, float min, float max, int color, String unit, long window) {
        super(c);
        this.data = data; this.getter = g; this.min = min; this.max = max; this.color = color; this.unit = unit; this.window = window;
        float d = getResources().getDisplayMetrics().density;
        line.setColor(color);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2 * d);
        grid.setColor(0x22FFFFFF);
        grid.setStrokeWidth(1);
        text.setColor(MainActivity.SUB);
        text.setTextSize(10 * d);
        text.setTypeface(Typeface.MONOSPACE);
    }

    @Override protected void onDraw(Canvas c) {
        float d = getResources().getDisplayMetrics().density;
        float left = 34 * d, right = getWidth() - 4 * d, top = 6 * d, bottom = getHeight() - 16 * d;
        for (int i = 0; i <= 4; i++) {
            float y = top + (bottom - top) * i / 4f;
            c.drawLine(left, y, right, y, grid);
        }
        c.drawText(Math.round(max) + unit, 0, top + 10 * d, text);
        c.drawText(Math.round(min) + unit, 0, bottom, text);
        c.drawText("-" + (window / 3_600_000L) + "h", left, getHeight() - 2 * d, text);
        c.drawText("agora", right - text.measureText("agora"), getHeight() - 2 * d, text);
        if (data.size() < 2) {
            c.drawText("coletando dados...", left + 10 * d, (top + bottom) / 2, text);
            return;
        }
        long now = System.currentTimeMillis(), from = now - window;
        Path p = new Path();
        boolean first = true;
        long prevT = 0;
        for (History.Sample s : data) {
            float x = left + (right - left) * (s.t - from) / (float) window;
            float v = Math.max(min, Math.min(max, getter.get(s)));
            float y = bottom - (bottom - top) * (v - min) / (max - min);
            if (first || s.t - prevT > 40 * 60 * 1000L) p.moveTo(x, y); else p.lineTo(x, y);
            first = false;
            prevT = s.t;
        }
        c.drawPath(p, line);
    }
}
