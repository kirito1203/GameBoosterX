package com.pulsesolar.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** Gráfico de linha simples com as últimas N leituras. */
public class LineGraph extends View {

    private final float[] data = new float[60];
    private int count = 0;
    private float min = 0f;
    private float max = 100f;
    private int color = 0xFF2EA8FF;

    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path linePath = new Path();
    private final Path fillPath = new Path();

    public LineGraph(Context context) {
        this(context, null);
    }

    public LineGraph(Context context, AttributeSet attrs) {
        super(context, attrs);
        float d = getResources().getDisplayMetrics().density;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(2.2f * d);
        line.setStrokeJoin(Paint.Join.ROUND);
        fill.setStyle(Paint.Style.FILL);
        grid.setColor(0xFF13223A);
        grid.setStrokeWidth(1f * d);
        label.setColor(0xFF8A9BB8);
        label.setTextSize(10f * d);
    }

    public void setRange(float min, float max) {
        this.min = min;
        this.max = max;
        invalidate();
    }

    public void setColor(int c) {
        color = c;
        updateShader(getHeight());
        invalidate();
    }

    /** Substitui os dados por uma série completa (reduzida para os pontos do gráfico). */
    public void setSeries(float[] values) {
        count = 0;
        if (values.length <= data.length) {
            for (float v : values) data[count++] = v;
        } else {
            for (int i = 0; i < data.length; i++) {
                int a = (int) ((long) i * values.length / data.length);
                int b = (int) ((long) (i + 1) * values.length / data.length);
                float sum = 0;
                for (int j = a; j < b; j++) sum += values[j];
                data[count++] = sum / Math.max(1, b - a);
            }
        }
        invalidate();
    }

    public void addPoint(float v) {
        if (count < data.length) {
            data[count++] = v;
        } else {
            System.arraycopy(data, 1, data, 0, data.length - 1);
            data[data.length - 1] = v;
        }
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateShader(h);
    }

    private void updateShader(int h) {
        if (h <= 0) return;
        int top = (color & 0x00FFFFFF) | 0x66000000;
        fill.setShader(new LinearGradient(0, 0, 0, h, top, 0x00000000, Shader.TileMode.CLAMP));
    }

    private float yFor(float v, float h) {
        float n = (v - min) / (max - min);
        n = Math.max(0f, Math.min(1f, n));
        return h - n * h;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        for (int i = 1; i < 4; i++) {
            float y = h * i / 4f;
            canvas.drawLine(0, y, w, y, grid);
        }
        canvas.drawText(Math.round(max) + "", 4, label.getTextSize(), label);
        canvas.drawText(Math.round(min) + "", 4, h - 4, label);
        if (count < 2) return;

        line.setColor(color);
        float step = w / (data.length - 1);
        linePath.reset();
        fillPath.reset();
        float firstX = w - (count - 1) * step;
        for (int i = 0; i < count; i++) {
            float x = w - (count - 1 - i) * step;
            float y = yFor(data[i], h);
            if (i == 0) {
                linePath.moveTo(x, y);
                fillPath.moveTo(x, h);
                fillPath.lineTo(x, y);
            } else {
                linePath.lineTo(x, y);
                fillPath.lineTo(x, y);
            }
        }
        fillPath.lineTo(w, h);
        fillPath.lineTo(firstX, h);
        fillPath.close();
        canvas.drawPath(fillPath, fill);
        canvas.drawPath(linePath, line);
    }
}
