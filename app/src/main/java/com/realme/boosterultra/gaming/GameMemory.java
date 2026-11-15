package com.realme.boosterultra.gaming;

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
import java.io.FileReader;
import java.io.FileWriter;
import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** GAME MEMORY: guarda o desempenho de cada sessão de jogo. */
public final class GameMemory {
    private GameMemory() {}

    public static final class Session {
        public String pkg, label;
        public long start, end;
        public float avgTemp, maxTemp, avgRam, avgHz, minHz, stability, janksPerMin, avgPing;
        public int batStart, batEnd;

        public long minutes() { return Math.max(1, (end - start) / 60000); }

        public JSONObject toJson() throws Exception {
            return new JSONObject().put("pkg", pkg).put("label", label).put("start", start).put("end", end)
                    .put("avgTemp", avgTemp).put("maxTemp", maxTemp).put("avgRam", avgRam).put("avgHz", avgHz)
                    .put("minHz", minHz).put("stability", stability).put("jpm", janksPerMin).put("ping", avgPing)
                    .put("b0", batStart).put("b1", batEnd);
        }

        public static Session from(JSONObject o) {
            Session s = new Session();
            s.pkg = o.optString("pkg"); s.label = o.optString("label", s.pkg);
            s.start = o.optLong("start"); s.end = o.optLong("end");
            s.avgTemp = (float) o.optDouble("avgTemp"); s.maxTemp = (float) o.optDouble("maxTemp");
            s.avgRam = (float) o.optDouble("avgRam"); s.avgHz = (float) o.optDouble("avgHz", 0);
            s.minHz = (float) o.optDouble("minHz", 0); s.stability = (float) o.optDouble("stability", -1);
            s.janksPerMin = (float) o.optDouble("jpm", 0); s.avgPing = (float) o.optDouble("ping", -1);
            s.batStart = o.optInt("b0"); s.batEnd = o.optInt("b1");
            return s;
        }
    }

    private static File file(Context c) { return new File(c.getFilesDir(), "sessions.json"); }

    public static synchronized List<Session> all(Context c) {
        List<Session> out = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file(c)))) {
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = br.readLine()) != null) sb.append(l);
            JSONArray a = new JSONArray(sb.toString());
            for (int i = 0; i < a.length(); i++) out.add(Session.from(a.getJSONObject(i)));
        } catch (Exception ignored) {}
        return out;
    }

    public static synchronized void save(Context c, Session s) {
        List<Session> l = all(c);
        l.add(s);
        while (l.size() > 150) l.remove(0);
        try {
            JSONArray a = new JSONArray();
            for (Session x : l) a.put(x.toJson());
            try (FileWriter w = new FileWriter(file(c), false)) { w.write(a.toString()); }
        } catch (Exception ignored) {}
    }

    /** Sessões agrupadas por jogo (mais recente primeiro dentro de cada jogo). */
    public static Map<String, List<Session>> byGame(Context c) {
        Map<String, List<Session>> m = new LinkedHashMap<>();
        List<Session> l = all(c);
        for (int i = l.size() - 1; i >= 0; i--) {
            Session s = l.get(i);
            List<Session> g = m.get(s.pkg);
            if (g == null) { g = new ArrayList<>(); m.put(s.pkg, g); }
            g.add(s);
        }
        return m;
    }

    /** ADAPTIVE PERFORMANCE: recomendação para o próximo jogo com base nas sessões anteriores. */
    public static String advice(Session s) {
        StringBuilder sb = new StringBuilder();
        if (s.maxTemp >= 44) sb.append("• Chegou a ").append(Math.round(s.maxTemp)).append("°C: abaixe gráficos/FPS do jogo e tire a capinha\n");
        else if (s.maxTemp >= 40) sb.append("• Esquentou (").append(Math.round(s.maxTemp)).append("°C): sessões mais curtas ou gráfico médio\n");
        if (s.stability >= 0 && s.stability < 80) sb.append("• Fluidez de ").append(Math.round(s.stability)).append("%: reduza distância de visão/sombras\n");
        if (s.avgRam >= 85) sb.append("• RAM quase cheia: use o Ultra Turbo antes de abrir\n");
        if (s.avgPing > 120) sb.append("• Ping alto (").append(Math.round(s.avgPing)).append(" ms): use Wi-Fi 5 GHz ou chegue perto do roteador\n");
        long mins = s.minutes();
        int drop = s.batStart - s.batEnd;
        if (drop > 0 && mins >= 10) {
            float perHour = drop * 60f / mins;
            if (perHour >= 25) sb.append("• Gastou ").append(Math.round(perHour)).append("%/h de bateria: abaixe o brilho\n");
        }
        if (sb.length() == 0) sb.append("• Rodou bem ✔ pode manter as configurações\n");
        return sb.toString().trim();
    }
}
