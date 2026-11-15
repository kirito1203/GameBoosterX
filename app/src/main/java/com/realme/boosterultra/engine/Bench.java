package com.realme.boosterultra.engine;

import com.realme.boosterultra.*;
import com.realme.boosterultra.engine.*;
import com.realme.boosterultra.ai.*;
import com.realme.boosterultra.gaming.*;
import com.realme.boosterultra.monitor.*;
import com.realme.boosterultra.utils.*;
import com.realme.boosterultra.ui.*;


import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicLong;

/** PERFORMANCE BENCHMARK: testes reais de CPU, memória e armazenamento. */
public final class Bench {
    private Bench() {}

    public static final class Result {
        public long cpu1, cpuN, mem, write, read, total;
        public String label;
        public long time;

        public JSONObject json() throws Exception {
            return new JSONObject().put("c1", cpu1).put("cn", cpuN).put("m", mem).put("w", write).put("r", read)
                    .put("t", total).put("l", label).put("time", time);
        }

        public static Result from(JSONObject o) {
            Result r = new Result();
            r.cpu1 = o.optLong("c1"); r.cpuN = o.optLong("cn"); r.mem = o.optLong("m");
            r.write = o.optLong("w"); r.read = o.optLong("r"); r.total = o.optLong("t");
            r.label = o.optString("l"); r.time = o.optLong("time");
            return r;
        }
    }

    public interface Progress { void on(String step); }

    private static long cpuWork(long ms) {
        long end = System.nanoTime() + ms * 1_000_000L, ops = 0;
        double x = 1.0001;
        while (System.nanoTime() < end) {
            for (int i = 0; i < 2000; i++) { x = Math.sqrt(x * 1.000001 + i) % 1000 + 1; }
            ops++;
        }
        if (x == 42.4242) ops++; // evita o compilador apagar o cálculo
        return ops;
    }

    public static Result run(Context c, String label, Progress pr) {
        Result r = new Result();
        r.label = label;
        r.time = System.currentTimeMillis();

        pr.on("CPU (1 núcleo)...");
        r.cpu1 = cpuWork(1500) * 10 / 15;

        pr.on("CPU (todos os núcleos)...");
        int n = Runtime.getRuntime().availableProcessors();
        final AtomicLong sum = new AtomicLong();
        Thread[] ts = new Thread[n];
        for (int i = 0; i < n; i++) { ts[i] = new Thread(() -> sum.addAndGet(cpuWork(1500))); ts[i].start(); }
        for (Thread t : ts) try { t.join(); } catch (InterruptedException ignored) {}
        r.cpuN = sum.get() * 10 / 15;

        pr.on("Memória...");
        int[] a = new int[4 * 1024 * 1024], b = new int[4 * 1024 * 1024];
        long end = System.nanoTime() + 1_000_000_000L, copies = 0;
        while (System.nanoTime() < end) { System.arraycopy(a, 0, b, 0, a.length); copies++; }
        r.mem = copies * 16; // MB/s (16 MB por cópia)

        pr.on("Armazenamento...");
        File f = new File(c.getCacheDir(), "bench.tmp");
        byte[] buf = new byte[1024 * 1024];
        try {
            long t0 = System.nanoTime();
            try (FileOutputStream o = new FileOutputStream(f)) {
                for (int i = 0; i < 64; i++) o.write(buf);
                o.getFD().sync();
            }
            long t1 = System.nanoTime();
            try (FileInputStream in = new FileInputStream(f)) { while (in.read(buf) > 0) { } }
            long t2 = System.nanoTime();
            r.write = Math.round(64 / ((t1 - t0) / 1e9));
            r.read = Math.round(64 / ((t2 - t1) / 1e9));
        } catch (Exception ignored) {}
        f.delete();

        r.total = r.cpu1 / 10 + r.cpuN / 40 + r.mem / 20 + r.write / 4 + r.read / 10;
        save(c, r);
        return r;
    }

    public static void save(Context c, Result r) {
        try {
            JSONArray a = new JSONArray(ProfileManager.prefs(c).getString("bench_hist", "[]"));
            a.put(r.json());
            JSONArray keep = new JSONArray();
            for (int i = Math.max(0, a.length() - 20); i < a.length(); i++) keep.put(a.get(i));
            ProfileManager.prefs(c).edit().putString("bench_hist", keep.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static java.util.List<Result> history(Context c) {
        java.util.List<Result> l = new java.util.ArrayList<>();
        try {
            JSONArray a = new JSONArray(ProfileManager.prefs(c).getString("bench_hist", "[]"));
            for (int i = 0; i < a.length(); i++) l.add(Result.from(a.getJSONObject(i)));
        } catch (Exception ignored) {}
        return l;
    }
}
