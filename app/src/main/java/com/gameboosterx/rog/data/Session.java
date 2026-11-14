package com.gameboosterx.rog.data;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/** Uma sessão de jogo registrada. Campos NaN/-1 = dado não disponível na sessão. */
public class Session {

    public static final class Sample {
        public long t;                 // ms desde o início
        public float tempC = Float.NaN;
        public int battery = -1;
        public float fps = Float.NaN;
        public int thermal = -1;
        public int ramPct = -1;
    }

    public long id;
    public String pkg;
    public String label;
    public long start;
    public long end;
    public float tempStart = Float.NaN;
    public float tempEnd = Float.NaN;
    public float tempMax = Float.NaN;
    public int battStart = -1;
    public int battEnd = -1;
    public float fpsAvg = Float.NaN;
    public float fpsMin = Float.NaN;
    public int fpsSamples;
    public int drops;
    public int thermalMax = -1;
    public List<Sample> samples = new ArrayList<>();

    public long durationMs() { return Math.max(0, end - start); }

    public int batteryUsed() {
        if (battStart < 0 || battEnd < 0) return -1;
        return Math.max(0, battStart - battEnd);
    }

    /** Consumo em %/hora, ou NaN se a sessão for curta demais para estimar. */
    public float drainPerHour() {
        int used = batteryUsed();
        long d = durationMs();
        if (used < 0 || d < 5 * 60 * 1000L) return Float.NaN;
        return used / (d / 3600000f);
    }

    /** Desvio padrão do FPS — quanto menor, mais estável. */
    public float fpsStdDev() {
        float sum = 0; int n = 0;
        for (Sample s : samples) if (!Float.isNaN(s.fps)) { sum += s.fps; n++; }
        if (n < 2) return Float.NaN;
        float mean = sum / n;
        float acc = 0;
        for (Sample s : samples) if (!Float.isNaN(s.fps)) acc += (s.fps - mean) * (s.fps - mean);
        return (float) Math.sqrt(acc / (n - 1));
    }

    public String samplesToJson() {
        JSONArray arr = new JSONArray();
        try {
            for (Sample s : samples) {
                JSONArray a = new JSONArray();
                a.put(s.t);
                a.put(Float.isNaN(s.tempC) ? -999 : s.tempC);
                a.put(s.battery);
                a.put(Float.isNaN(s.fps) ? -1 : s.fps);
                a.put(s.thermal);
                a.put(s.ramPct);
                arr.put(a);
            }
        } catch (Exception ignored) {
        }
        return arr.toString();
    }

    public void samplesFromJson(String json) {
        samples.clear();
        if (json == null) return;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONArray a = arr.getJSONArray(i);
                Sample s = new Sample();
                s.t = a.getLong(0);
                double tc = a.getDouble(1);
                s.tempC = tc <= -900 ? Float.NaN : (float) tc;
                s.battery = a.getInt(2);
                double f = a.getDouble(3);
                s.fps = f < 0 ? Float.NaN : (float) f;
                s.thermal = a.getInt(4);
                s.ramPct = a.getInt(5);
                samples.add(s);
            }
        } catch (Exception ignored) {
        }
    }
}
