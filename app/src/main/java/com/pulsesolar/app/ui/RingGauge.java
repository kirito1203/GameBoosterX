package com.pulsesolar.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** Medidor circular neon (arco de 270°). */
public class RingGauge extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint center = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint caption = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();

    private float progress = 0f;
    private String centerText = "--";
    private String captionText = "";
    private int color = 0xFFFFB300;

    public RingGauge(Context context) {
        this(context, null);
    }

    public RingGauge(Context context, AttributeSet attrs) {
        super(context, attrs);
        // Camada em software para o brilho (shadow layer) funcionar.
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeCap(Paint.Cap.ROUND);
        track.setColor(0xFF1A2438);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.ROUND);
        center.setColor(0xFFE8F1FF);
        center.setTextAlign(Paint.Align.CENTER);
        center.setFakeBoldText(true);
        caption.setColor(0xFF8A9BB8);
        caption.setTextAlign(Paint.Align.CENTER);
    }

    public void setProgress(float p) {
        progress = Math.max(0f, Math.min(100f, p));
        invalidate();
    }

    public void setCenterText(String t) {
        centerText = t;
        invalidate();
    }

    public void setCaption(String t) {
        captionText = t;
        invalidate();
    }

    public void setColor(int c) {
        color = c;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desired = (int) (140 * getResources().getDisplayMetrics().density);
        int w = resolveSize(desired, widthMeasureSpec);
        int h = resolveSize(desired, heightMeasureSpec);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float size = Math.min(w, h);
        if (size <= 0) return;
        float stroke = size * 0.085f;
        float pad = stroke / 2f + size * 0.06f;
        float cx = w / 2f;
        float cy = h / 2f;
        float r = size / 2f - pad;
        oval.set(cx - r, cy - r, cx + r, cy + r);

        track.setStrokeWidth(stroke);
        arc.setStrokeWidth(stroke);
        arc.setColor(color);
        arc.setShadowLayer(stroke * 0.9f, 0, 0, color);

        canvas.drawArc(oval, 135f, 270f, false, track);
        if (progress > 0f) {
            canvas.drawArc(oval, 135f, 270f * progress / 100f, false, arc);
        }

        center.setTextSize(size * 0.21f);
        canvas.drawText(centerText, cx, cy + center.getTextSize() * 0.35f, center);
        caption.setTextSize(size * 0.09f);
        canvas.drawText(captionText, cx, cy + r * 0.75f, caption);
    }
}
