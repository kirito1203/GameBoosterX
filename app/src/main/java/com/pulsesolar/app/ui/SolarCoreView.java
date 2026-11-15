package com.pulsesolar.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/**
 * Núcleo solar animado da tela inicial. A animação é apenas visual;
 * o texto central mostra o status real calculado pelo Analyzer.
 */
public class SolarCoreView extends View {

    private final Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ray = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringStatus = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sub = new Paint(Paint.ANTI_ALIAS_FLAG);

    private String status = "…";
    private String subtitle = "";
    private int statusColor = 0xFFFFB300;
    private boolean attached = false;

    public SolarCoreView(Context context) {
        this(context, null);
    }

    public SolarCoreView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        ray.setStyle(Paint.Style.STROKE);
        ray.setStrokeCap(Paint.Cap.ROUND);
        ray.setColor(0xFFFFB300);
        ring.setStyle(Paint.Style.STROKE);
        ring.setColor(0xFF2EA8FF);
        ringStatus.setStyle(Paint.Style.STROKE);
        ringStatus.setStrokeCap(Paint.Cap.ROUND);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        text.setColor(0xFF140A00);
        sub.setTextAlign(Paint.Align.CENTER);
        sub.setColor(0xFF7F93B2);
    }

    public void setStatus(String status, String subtitle, int color) {
        this.status = status;
        this.subtitle = subtitle;
        this.statusColor = color;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        attached = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desired = (int) (230 * getResources().getDisplayMetrics().density);
        setMeasuredDimension(resolveSize(desired, widthMeasureSpec), resolveSize(desired, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float size = Math.min(w, h);
        if (size <= 0) return;
        float cx = w / 2f;
        float cy = h / 2f - size * 0.04f;

        long now = SystemClock.uptimeMillis();
        double phase = (now % 3000L) / 3000.0 * Math.PI * 2;
        float pulse = (float) (0.5 + 0.5 * Math.sin(phase));
        float rotation = (now % 60000L) / 60000f * 360f;

        // Anéis azuis
        float rOuter = size * 0.40f;
        ring.setStrokeWidth(size * 0.006f);
        ring.setAlpha(110);
        canvas.drawCircle(cx, cy, rOuter, ring);
        ring.setAlpha(50);
        canvas.drawCircle(cx, cy, rOuter * (1.05f + 0.03f * pulse), ring);

        // Anel de status (cor do status real)
        ringStatus.setStrokeWidth(size * 0.018f);
        ringStatus.setColor(statusColor);
        ringStatus.setShadowLayer(size * 0.03f, 0, 0, statusColor);
        canvas.drawArc(cx - rOuter, cy - rOuter, cx + rOuter, cy + rOuter, rotation, 300f, false, ringStatus);

        // Raios solares
        float rCore = size * 0.17f * (1f + 0.05f * pulse);
        ray.setStrokeWidth(size * 0.016f);
        ray.setShadowLayer(size * 0.025f, 0, 0, 0xFFFF6D00);
        int rays = 16;
        for (int i = 0; i < rays; i++) {
            double a = Math.toRadians(rotation * -0.5 + i * (360.0 / rays));
            float inner = rCore * 1.3f;
            float outer = rCore * ((i % 2 == 0) ? 1.85f + 0.2f * pulse : 1.6f + 0.1f * pulse);
            canvas.drawLine(
                    cx + (float) Math.cos(a) * inner, cy + (float) Math.sin(a) * inner,
                    cx + (float) Math.cos(a) * outer, cy + (float) Math.sin(a) * outer, ray);
        }

        // Núcleo
        core.setShader(new RadialGradient(cx, cy, rCore,
                new int[]{0xFFFFF3C4, 0xFFFFB300, 0xFFFF6D00},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        core.setShadowLayer(size * 0.06f, 0, 0, 0xFFFF8F00);
        canvas.drawCircle(cx, cy, rCore, core);

        // Textos
        text.setTextSize(rCore * 0.30f);
        canvas.drawText(status, cx, cy + text.getTextSize() * 0.35f, text);
        sub.setTextSize(size * 0.055f);
        canvas.drawText(subtitle, cx, cy + rOuter + size * 0.11f, sub);

        if (attached && getWindowVisibility() == VISIBLE) {
            postInvalidateDelayed(40); // ~25 quadros/s, leve para a bateria
        }
    }
}
