package com.pulsesolar.app.modules.fps;

import android.content.Context;
import android.os.Build;
import android.os.PowerManager;

import com.pulsesolar.app.core.SystemInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Sessão de jogo gravada: estado antes, amostras durante, estado depois e alertas. */
public final class Session {

    /** Uma leitura real do sistema em um instante. */
    public static final class Point {
        public long t;
        public long ramAvail = -1, ramTotal = -1;
        public boolean lowMem;
        public float storageFreePct = Float.NaN;
        public int level = -1;
        public boolean charging;
        public float tempC = Float.NaN;
        public int thermal = -1;
        public float headroom = Float.NaN;
        public boolean powerSave;
        public long bigCur = -1, bigCap = -1, bigHw = -1;
        // Pulso do sistema (FrameMonitor) no último segundo; -1 se não medido
        public float fps = -1f, stability = -1f;

        public float ramAvailPct() {
            return ramTotal > 0 && ramAvail >= 0 ? ramAvail * 100f / ramTotal : Float.NaN;
        }

        public float capRatio() {
            return bigHw > 0 && bigCap > 0 ? bigCap / (float) bigHw : -1f;
        }

        public float loadRatio() {
            return bigHw > 0 && bigCur > 0 ? bigCur / (float) bigHw : -1f;
        }

        /**
         * @param headroom true lê a previsão térmica (o Android limita a 1 leitura/s; use com moderação).
         */
        public static Point capture(Context c, boolean headroom) {
            Point p = new Point();
            p.t = System.currentTimeMillis();
            SystemInfo.Ram r = SystemInfo.ram(c);
            p.ramAvail = r.avail;
            p.ramTotal = r.total;
            p.lowMem = r.low;
            p.storageFreePct = SystemInfo.storage().freePercent();
            SystemInfo.Battery b = SystemInfo.battery(c);
            p.level = b.level;
            p.charging = b.isCharging();
            p.tempC = b.tempC;
            p.thermal = b.thermalStatus;
            p.powerSave = b.powerSave;
            if (headroom && Build.VERSION.SDK_INT >= 30) {
                PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
                p.headroom = pm.getThermalHeadroom(10);
            }
            CpuProbe.Cluster big = CpuProbe.big(CpuProbe.read());
            if (big != null) {
                p.bigCur = big.curKhz;
                p.bigCap = big.capKhz;
                p.bigHw = big.hwMaxKhz;
            }
            return p;
        }

        JSONObject toJson() throws JSONException {
            JSONObject o = new JSONObject();
            o.put("t", t);
            o.put("ra", ramAvail);
            o.put("rt", ramTotal);
            o.put("lm", lowMem);
            putF(o, "sf", storageFreePct);
            o.put("lv", level);
            o.put("ch", charging);
            putF(o, "tc", tempC);
            o.put("th", thermal);
            putF(o, "hr", headroom);
            o.put("ps", powerSave);
            o.put("bc", bigCur);
            o.put("bp", bigCap);
            o.put("bh", bigHw);
            putF(o, "fps", fps);
            putF(o, "st", stability);
            return o;
        }

        static Point fromJson(JSONObject o) {
            Point p = new Point();
            p.t = o.optLong("t");
            p.ramAvail = o.optLong("ra", -1);
            p.ramTotal = o.optLong("rt", -1);
            p.lowMem = o.optBoolean("lm");
            p.storageFreePct = (float) o.optDouble("sf", Double.NaN);
            p.level = o.optInt("lv", -1);
            p.charging = o.optBoolean("ch");
            p.tempC = (float) o.optDouble("tc", Double.NaN);
            p.thermal = o.optInt("th", -1);
            p.headroom = (float) o.optDouble("hr", Double.NaN);
            p.powerSave = o.optBoolean("ps");
            p.bigCur = o.optLong("bc", -1);
            p.bigCap = o.optLong("bp", -1);
            p.bigHw = o.optLong("bh", -1);
            p.fps = (float) o.optDouble("fps", -1);
            p.stability = (float) o.optDouble("st", -1);
            return p;
        }
    }

    public long id;
    public long start, end;
    public String pkg, label;
    public int tempLimit;
    public Point before, after;
    public final List<Point> samples = new ArrayList<>();
    public final List<String> alerts = new ArrayList<>();
    public long frames, lateFrames, stalls;

    public long durationMs() {
        return Math.max(0, end - start);
    }

    private static void putF(JSONObject o, String k, float v) throws JSONException {
        if (!Float.isNaN(v) && !Float.isInfinite(v)) o.put(k, (double) v);
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("start", start);
        o.put("end", end);
        if (pkg != null) o.put("pkg", pkg);
        if (label != null) o.put("label", label);
        o.put("limit", tempLimit);
        if (before != null) o.put("before", before.toJson());
        if (after != null) o.put("after", after.toJson());
        JSONArray arr = new JSONArray();
        for (Point p : samples) arr.put(p.toJson());
        o.put("samples", arr);
        JSONArray al = new JSONArray();
        for (String a : alerts) al.put(a);
        o.put("alerts", al);
        o.put("frames", frames);
        o.put("late", lateFrames);
        o.put("stalls", stalls);
        return o;
    }

    public static Session fromJson(JSONObject o) {
        Session s = new Session();
        s.id = o.optLong("id");
        s.start = o.optLong("start");
        s.end = o.optLong("end");
        s.pkg = o.optString("pkg", null);
        s.label = o.optString("label", null);
        s.tempLimit = o.optInt("limit", 42);
        JSONObject b = o.optJSONObject("before");
        if (b != null) s.before = Point.fromJson(b);
        JSONObject a = o.optJSONObject("after");
        if (a != null) s.after = Point.fromJson(a);
        JSONArray arr = o.optJSONArray("samples");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject p = arr.optJSONObject(i);
                if (p != null) s.samples.add(Point.fromJson(p));
            }
        }
        JSONArray al = o.optJSONArray("alerts");
        if (al != null) for (int i = 0; i < al.length(); i++) s.alerts.add(al.optString(i));
        s.frames = o.optLong("frames");
        s.lateFrames = o.optLong("late");
        s.stalls = o.optLong("stalls");
        return s;
    }
}
