package com.gameboosterx.rog.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Área de teste de toque. Mede a taxa REAL de eventos de toque entregues
 * ao app (inclui eventos históricos agrupados pelo sistema), o intervalo
 * médio entre amostras e o número de dedos simultâneos.
 */
public class TouchTestView extends View {

    public interface Listener {
        void onTouchStats(float sampleRateHz, float avgIntervalMs, int pointers, int totalSamples);
    }

    private final Paint trail = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private Listener listener;

    private long firstEventNs;
    private long lastEventTime;
    private int samples;
    private double intervalSum;
    private int intervals;
    private float lastX = -1, lastY = -1;
    private int maxPointers;

    public TouchTestView(Context c) { this(c, null); }

    public TouchTestView(Context c, AttributeSet a) {
        super(c, a);
        float d = getResources().getDisplayMetrics().density;
        trail.setStyle(Paint.Style.STROKE);
        trail.setStrokeWidth(3 * d);
        trail.setColor(0xFFFF1744);
        dot.setColor(0xFFFFFFFF);
        hint.setColor(0xFF5A5A5A);
        hint.setTextSize(14 * d);
        hint.setTextAlign(Paint.Align.CENTER);
    }

    public void setListener(Listener l) { listener = l; }

    public void reset() {
        path.reset();
        samples = 0;
        intervals = 0;
        intervalSum = 0;
        firstEventNs = 0;
        lastEventTime = 0;
        maxPointers = 0;
        lastX = lastY = -1;
        invalidate();
    }

    private void addSample(long eventTimeMs, float x, float y) {
        if (lastEventTime > 0) {
            long dt = eventTimeMs - lastEventTime;
            if (dt > 0 && dt < 100) {
                intervalSum += dt;
                intervals++;
            }
        }
        lastEventTime = eventTimeMs;
        samples++;
        if (lastX < 0) path.moveTo(x, y); else path.lineTo(x, y);
        lastX = x;
        lastY = y;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            // Impede que a rolagem da tela "roube" o gesto durante o teste
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
            reset();
            firstEventNs = System.nanoTime();
        }
        maxPointers = Math.max(maxPointers, e.getPointerCount());
        // Eventos históricos: o sistema agrupa amostras entre quadros
        for (int h = 0; h < e.getHistorySize(); h++) {
            addSample(e.getHistoricalEventTime(h), e.getHistoricalX(0, h), e.getHistoricalY(0, h));
        }
        addSample(e.getEventTime(), e.getX(0), e.getY(0));
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_MOVE) {
            report();
        }
        invalidate();
        return true;
    }

    private void report() {
        if (listener == null || intervals < 5) return;
        float avg = (float) (intervalSum / intervals);
        float hz = avg > 0 ? 1000f / avg : 0;
        listener.onTouchStats(hz, avg, maxPointers, samples);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (samples == 0) {
            c.drawText("Arraste o dedo continuamente aqui", getWidth() / 2f, getHeight() / 2f, hint);
            return;
        }
        c.drawPath(path, trail);
        if (lastX >= 0) c.drawCircle(lastX, lastY, 8 * getResources().getDisplayMetrics().density, dot);
    }
}
