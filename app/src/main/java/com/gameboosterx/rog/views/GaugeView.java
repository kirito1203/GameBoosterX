package com.gameboosterx.rog.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Indicador circular neon estilo ROG, com animação suave do valor. */
public class GaugeView extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private float fraction = 0f;       // 0..1 animado
    private String valueText = "--";
    private String labelText = "";
    private int color = 0xFFFF1744;
    private ValueAnimator anim;

    public GaugeView(Context c) { this(c, null); }

    public GaugeView(Context c, AttributeSet a) {
        super(c, a);
        setLayerType(LAYER_TYPE_SOFTWARE, null); // necessário para o BlurMaskFilter
        float d = getResources().getDisplayMetrics().density;
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(6 * d);
        track.setColor(0xFF1E1E1E);
        track.setStrokeCap(Paint.Cap.ROUND);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(6 * d);
        arc.setStrokeCap(Paint.Cap.ROUND);
        glow.setStyle(Paint.Style.STROKE);
        glow.setStrokeWidth(10 * d);
        glow.setStrokeCap(Paint.Cap.ROUND);
        glow.setMaskFilter(new BlurMaskFilter(8 * d, BlurMaskFilter.Blur.NORMAL));
        value.setColor(0xFFFFFFFF);
        value.setTextAlign(Paint.Align.CENTER);
        value.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        label.setColor(0xFF9A9A9A);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        label.setLetterSpacing(0.12f);
    }

    public void setLabel(String l) {
        labelText = l;
        invalidate();
    }

    /** @param f fração 0..1 (NaN = indisponível) */
    public void setValue(float f, String text, int col) {
        valueText = text;
        color = col;
        float target = Float.isNaN(f) ? 0f : Math.max(0f, Math.min(1f, f));
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(fraction, target);
        anim.setDuration(600);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(a -> {
            fraction = (float) a.getAnimatedValue();
            invalidate();
        });
        anim.start();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        setMeasuredDimension(w, w);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float pad = 12 * getResources().getDisplayMetrics().density;
        rect.set(pad, pad, w - pad, h - pad);
        c.drawArc(rect, 135, 270, false, track);
        arc.setColor(color);
        glow.setColor((color & 0x00FFFFFF) | 0x88000000);
        float sweep = 270 * fraction;
        if (sweep > 0.5f) {
            c.drawArc(rect, 135, sweep, false, glow);
            c.drawArc(rect, 135, sweep, false, arc);
        }
        value.setTextSize(w * 0.20f);
        c.drawText(valueText, w / 2, h / 2 + w * 0.06f, value);
        label.setTextSize(w * 0.09f);
        c.drawText(labelText, w / 2, h - pad * 0.9f, label);
    }
}
