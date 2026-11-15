package com.realme.boosterultra.monitor;

import com.realme.boosterultra.*;
import com.realme.boosterultra.engine.*;
import com.realme.boosterultra.ai.*;
import com.realme.boosterultra.gaming.*;
import com.realme.boosterultra.monitor.*;
import com.realme.boosterultra.utils.*;
import com.realme.boosterultra.ui.*;


import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

/** Histórico de desempenho: bateria, temperatura e RAM ao longo do tempo. */
public final class History {
    private History() {}

    public static final class Sample {
        public long t; public int level; public float temp; public int ram; public boolean charging;
    }

    private static long lastWrite = 0;
    private static final long EVERY = 5 * 60 * 1000L;
    private static final long KEEP = 7L * 24 * 60 * 60 * 1000L;

    private static File file(Context c) { return new File(c.getFilesDir(), "hist.csv"); }

    public static synchronized void record(Context c, boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastWrite < EVERY) return;
        lastWrite = now;
        DeviceInfo.Battery b = DeviceInfo.battery(c);
        long[] r = DeviceInfo.ram(c);
        String line = now + "," + b.level + "," + b.temp + "," + DeviceInfo.pct(r[0] - r[1], r[0]) + "," + (b.charging ? 1 : 0) + "\n";
        try (FileWriter w = new FileWriter(file(c), true)) { w.write(line); } catch (Exception ignored) {}
        if (file(c).length() > 120_000) trim(c);
    }

    private static void trim(Context c) {
        List<Sample> all = load(c, KEEP);
        StringBuilder sb = new StringBuilder();
        for (Sample s : all) sb.append(s.t).append(',').append(s.level).append(',').append(s.temp)
                .append(',').append(s.ram).append(',').append(s.charging ? 1 : 0).append('\n');
        try (FileWriter w = new FileWriter(file(c), false)) { w.write(sb.toString()); } catch (Exception ignored) {}
    }

    public static synchronized List<Sample> load(Context c, long windowMs) {
        List<Sample> out = new ArrayList<>();
        long from = System.currentTimeMillis() - windowMs;
        File f = file(c);
        if (!f.exists()) return out;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String l;
            while ((l = br.readLine()) != null) {
                String[] p = l.split(",");
                if (p.length < 5) continue;
                try {
                    Sample s = new Sample();
                    s.t = Long.parseLong(p[0]);
                    if (s.t < from) continue;
                    s.level = Integer.parseInt(p[1]);
                    s.temp = Float.parseFloat(p[2]);
                    s.ram = Integer.parseInt(p[3]);
                    s.charging = "1".equals(p[4]);
                    out.add(s);
                } catch (NumberFormatException ignored) {}
            }
        } catch (Exception ignored) {}
        return out;
    }

    /** % de bateria por hora gasto nas últimas horas sem carregar. NaN se não há dados. */
    public static float drainPerHour(Context c) {
        List<Sample> l = load(c, 6L * 60 * 60 * 1000);
        Sample end = null, start = null;
        for (int i = l.size() - 1; i >= 0; i--) {
            Sample s = l.get(i);
            if (s.charging) break;
            if (end == null) end = s;
            start = s;
        }
        if (start == null || end == null || end.t - start.t < 30 * 60 * 1000L) return Float.NaN;
        float hours = (end.t - start.t) / 3_600_000f;
        return Math.max(0, (start.level - end.level) / hours);
    }
}
