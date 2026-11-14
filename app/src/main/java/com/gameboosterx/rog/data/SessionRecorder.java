package com.gameboosterx.rog.data;

import android.content.Context;

/** Acumula amostras de uma sessão em andamento e grava no banco ao terminar. */
public class SessionRecorder {

    private static final int MAX_SAMPLES = 1800; // ~1 h a cada 2 s

    private final Session s = new Session();
    private float fpsSum = 0;
    private float lastFps = Float.NaN;

    public SessionRecorder(String pkg, String label) {
        s.pkg = pkg;
        s.label = label;
        s.start = System.currentTimeMillis();
    }

    public String pkg() { return s.pkg; }
    public String label() { return s.label; }
    public long startedAt() { return s.start; }

    public void addSample(float tempC, int battery, float fps, int thermal, int ramPct) {
        long now = System.currentTimeMillis();
        Session.Sample sm = new Session.Sample();
        sm.t = now - s.start;
        sm.tempC = tempC;
        sm.battery = battery;
        sm.fps = fps;
        sm.thermal = thermal;
        sm.ramPct = ramPct;

        if (s.samples.isEmpty()) {
            s.tempStart = tempC;
            s.battStart = battery;
        }
        if (!Float.isNaN(tempC)) {
            if (Float.isNaN(s.tempMax) || tempC > s.tempMax) s.tempMax = tempC;
            s.tempEnd = tempC;
        }
        if (battery >= 0) s.battEnd = battery;
        if (thermal > s.thermalMax) s.thermalMax = thermal;

        if (!Float.isNaN(fps)) {
            s.fpsSamples++;
            fpsSum += fps;
            s.fpsAvg = fpsSum / s.fpsSamples;
            if (Float.isNaN(s.fpsMin) || fps < s.fpsMin) s.fpsMin = fps;
            // Queda: redução brusca (≥ 25% e ≥ 10 FPS) em relação à amostra anterior
            if (!Float.isNaN(lastFps) && lastFps - fps >= 10 && fps < lastFps * 0.75f) s.drops++;
            lastFps = fps;
        }

        if (s.samples.size() >= MAX_SAMPLES) {
            // Mantém o histórico compacto descartando amostras alternadas
            for (int i = s.samples.size() - 1; i > 0; i -= 2) s.samples.remove(i);
        }
        s.samples.add(sm);
    }

    public Session current() { return s; }

    /** Grava se a sessão durou pelo menos 1 minuto. Retorna o id ou -1. */
    public long finish(Context c) {
        s.end = System.currentTimeMillis();
        if (s.durationMs() < 60_000 || s.samples.isEmpty()) return -1;
        return SessionDb.get(c).insert(s);
    }
}
