package com.realme.boosterultra.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

import com.realme.boosterultra.MainActivity;
import com.realme.boosterultra.engine.ProfileManager;

import java.util.Random;

/**
 * Tema de terror "câmera de segurança": linhas de TV, bordas escuras, estática leve,
 * faixa de interferência e o aviso "SINAL PERDIDO" de vez em quando. Não bloqueia toques.
 * Leve de propósito: redesenha só ~6 vezes por segundo.
 */
public class ScanlineView extends View {
    private final Paint line = new Paint();
    private final Paint vignette = new Paint();
    private final Paint noise = new Paint();
    private final Paint band = new Paint();
    private final Paint lost = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lostBg = new Paint();
    private final boolean enabled, staticOn;
    private final Random rnd = new Random();
    private float bandY = -1;
    private long lostUntil = 0, nextLost;

    public ScanlineView(Context c) {
        super(c);
        line.setColor(0x16000000);
        noise.setColor(0x22FFFFFF);
        band.setColor(0x10FFFFFF);
        lost.setColor(MainActivity.BAD);
        lost.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        lost.setTextAlign(Paint.Align.CENTER);
        lostBg.setColor(0xCC000000);
        setClickable(false);
        setFocusable(false);
        enabled = ProfileManager.prefs(c).getBoolean("scan_on", true);
        staticOn = enabled && ProfileManager.prefs(c).getBoolean("static_on", true);
        nextLost = System.currentTimeMillis() + 20000 + rnd.nextInt(40000);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        float r = Math.max(w, h) * 0.75f;
        vignette.setShader(new RadialGradient(w / 2f, h / 2f, r,
                new int[]{0x00000000, 0x00000000, 0x99000000}, new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP));
        lost.setTextSize(Math.max(28, w / 12f));
    }

    @Override protected void onDraw(Canvas c) {
        if (!enabled) return;
        int w = getWidth(), h = getHeight();
        float d = getResources().getDisplayMetrics().density;
        int step = Math.max(3, Math.round(3 * d));
        line.setStrokeWidth(Math.max(1f, step / 3f));
        for (int y = 0; y < h; y += step) c.drawLine(0, y, w, y, line);
        c.drawRect(0, 0, w, h, vignette);
        if (!staticOn) return;

        // estática de câmera
        float s = 1.5f * d;
        for (int i = 0; i < 70; i++) {
            float x = rnd.nextInt(Math.max(1, w)), y = rnd.nextInt(Math.max(1, h));
            c.drawRect(x, y, x + s, y + s, noise);
        }
        // faixa de interferência descendo
        if (bandY < 0 && rnd.nextInt(40) == 0) bandY = 0;
        if (bandY >= 0) {
            c.drawRect(0, bandY, w, bandY + 26 * d, band);
            bandY += 40 * d;
            if (bandY > h) bandY = -1;
        }
        // SINAL PERDIDO (rápido e raro)
        long now = System.currentTimeMillis();
        if (now > nextLost) {
            lostUntil = now + 450;
            nextLost = now + 30000 + rnd.nextInt(60000);
        }
        if (now < lostUntil) {
            float cy = h * 0.42f;
            c.drawRect(0, cy - lost.getTextSize(), w, cy + lost.getTextSize() * 0.6f, lostBg);
            c.drawText("SINAL PERDIDO", w / 2f + rnd.nextInt(9) - 4, cy, lost);
        }
        postInvalidateDelayed(160);
    }
}
