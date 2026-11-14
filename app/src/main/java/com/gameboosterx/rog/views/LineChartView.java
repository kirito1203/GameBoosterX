package com.gameboosterx.rog.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Gráfico de linhas simples (até 2 séries). Valores NaN criam lacunas,
 * ou seja, o gráfico nunca "preenche" dados que não foram medidos.
 */
public class LineChartView extends View {

    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<float[]> series = new ArrayList<>();
    private final List<Integer> colors = new ArrayList<>();
    private float minY = 0, maxY = 60;
    private boolean autoScale = true;
    private String emptyText = "Sem dados";
    private String unit = "";

    public LineChartView(Context c) { this(c, null); }

    public LineChartView(Context c, AttributeSet a) {
        super(c, a);
        float d = getResources().getDisplayMetrics().density;
        grid.setColor(0xFF242424);
        grid.setStrokeWidth(1 * d);
        text.setColor(0xFF8A8A8A);
        text.setTextSize(10 * d);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2 * d);
        line.setStrokeJoin(Paint.Join.ROUND);
        fill.setStyle(Paint.Style.FILL);
    }

    public void setEmptyText(String t) { emptyText = t; invalidate(); }
    public void setUnit(String u) { unit = u; }

    public void setRange(float min, float max) {
        minY = min;
        maxY = max;
        autoScale = false;
        invalidate();
    }

    public void clear() {
        series.clear();
        colors.clear();
        invalidate();
    }

    public void addSeries(float[] values, int color) {
        series.add(values);
        colors.add(color);
        if (autoScale) computeRange();
        invalidate();
    }

    private void computeRange() {
        float mn = Float.MAX_VALUE, mx = -Float.MAX_VALUE;
        for (float[] s : series) for (float v : s) {
            if (Float.isNaN(v)) continue;
            mn = Math.min(mn, v);
            mx = Math.max(mx, v);
        }
        if (mn == Float.MAX_VALUE) { minY = 0; maxY = 60; return; }
        float padding = Math.max(2f, (mx - mn) * 0.15f);
        minY = Math.max(0, (float) Math.floor(mn - padding));
        maxY = (float) Math.ceil(mx + padding);
    }

    private boolean hasData() {
        for (float[] s : series) for (float v : s) if (!Float.isNaN(v)) return true;
        return false;
    }

    @Override
    protected void onDraw(Canvas c) {
        float d = getResources().getDisplayMetrics().density;
        float left = 34 * d, right = getWidth() - 8 * d, top = 8 * d, bottom = getHeight() - 14 * d;
        float h = bottom - top;
        for (int i = 0; i <= 4; i++) {
            float y = top + h * i / 4f;
            c.drawLine(left, y, right, y, grid);
            float v = maxY - (maxY - minY) * i / 4f;
            c.drawText(String.format(Locale.US, "%.0f%s", v, unit), 2 * d, y + 4 * d, text);
        }
        if (!hasData()) {
            text.setTextAlign(Paint.Align.CENTER);
            c.drawText(emptyText, (left + right) / 2, top + h / 2, text);
            text.setTextAlign(Paint.Align.LEFT);
            return;
        }
        float range = Math.max(0.001f, maxY - minY);
        for (int si = 0; si < series.size(); si++) {
            float[] s = series.get(si);
            int col = colors.get(si);
            if (s.length < 2) continue;
            Path p = new Path();
            Path area = new Path();
            boolean pen = false;
            float firstX = 0, lastX = 0;
            for (int i = 0; i < s.length; i++) {
                float v = s[i];
                float x = left + (right - left) * i / (float) (s.length - 1);
                if (Float.isNaN(v)) {
                    if (pen && si == 0) {
                        area.lineTo(lastX, bottom);
                        area.lineTo(firstX, bottom);
                        area.close();
                    }
                    pen = false;
                    continue;
                }
                float y = bottom - (Math.max(minY, Math.min(maxY, v)) - minY) / range * h;
                if (!pen) {
                    p.moveTo(x, y);
                    area.moveTo(x, bottom);
                    area.lineTo(x, y);
                    firstX = x;
                    pen = true;
                } else {
                    p.lineTo(x, y);
                    area.lineTo(x, y);
                }
                lastX = x;
            }
            if (pen && si == 0) {
                area.lineTo(lastX, bottom);
                area.lineTo(firstX, bottom);
                area.close();
            }
            if (si == 0) {
                fill.setShader(new LinearGradient(0, top, 0, bottom,
                        (col & 0x00FFFFFF) | 0x55000000, 0x00000000, Shader.TileMode.CLAMP));
                c.drawPath(area, fill);
            }
            line.setColor(col);
            c.drawPath(p, line);
        }
    }
}
