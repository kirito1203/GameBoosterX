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
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/** Linhas de TV antiga + bordas escuras, por cima de tudo (não bloqueia toques). */
public class ScanlineView extends View {
    private final Paint line = new Paint();
    private final Paint vignette = new Paint();
    private final boolean enabled;

    public ScanlineView(Context c) {
        super(c);
        line.setColor(0x16000000);
        enabled = ProfileManager.prefs(c).getBoolean("scan_on", true);
        setClickable(false);
        setFocusable(false);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        float r = Math.max(w, h) * 0.75f;
        vignette.setShader(new RadialGradient(w / 2f, h / 2f, r,
                new int[]{0x00000000, 0x00000000, 0x99000000}, new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP));
    }

    @Override protected void onDraw(Canvas c) {
        if (!enabled) return;
        int step = Math.max(3, Math.round(3 * getResources().getDisplayMetrics().density));
        float th = Math.max(1f, step / 3f);
        line.setStrokeWidth(th);
        for (int y = 0; y < getHeight(); y += step) c.drawLine(0, y, getWidth(), y, line);
        c.drawRect(0, 0, getWidth(), getHeight(), vignette);
    }
}
