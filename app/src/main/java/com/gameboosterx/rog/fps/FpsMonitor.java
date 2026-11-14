package com.gameboosterx.rog.fps;

import android.content.Context;

import com.gameboosterx.rog.core.Perms;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Medição REAL de FPS de outro app, sem root.
 *
 * O Android não oferece API pública para ler o FPS de outro aplicativo.
 * A única fonte confiável sem root é o SurfaceFlinger (compositor do sistema),
 * acessível via "dumpsys SurfaceFlinger --latency", o que exige a permissão
 * android.permission.DUMP. Ela é de nível "development" e pode ser concedida
 * pelo próprio usuário via ADB (ou pelo app LADB, direto no celular):
 *
 *   adb shell pm grant com.gameboosterx.rog android.permission.DUMP
 *
 * Sem essa permissão, available() retorna false e o app mostra "FPS indisponível"
 * — nenhum valor é inventado.
 */
public class FpsMonitor {

    private static final long NS = 1_000_000_000L;
    private static final long PENDING = Long.MAX_VALUE;

    private final Context ctx;
    private String cachedLayer;
    private String cachedPkg;
    private long layerResolvedAt;
    private String lastError;

    public FpsMonitor(Context c) {
        ctx = c.getApplicationContext();
    }

    public boolean available() {
        return Perms.dump(ctx);
    }

    public String lastError() { return lastError; }

    /**
     * Mede o FPS do app informado na última janela de ~1 s.
     * Retorna NaN quando não é possível medir.
     * Deve ser chamado fora da thread principal.
     */
    public float measure(String pkg) {
        lastError = null;
        if (pkg == null) return Float.NaN;
        if (!available()) {
            lastError = "Permissão DUMP não concedida";
            return Float.NaN;
        }
        try {
            String layer = resolveLayer(pkg);
            if (layer == null) {
                lastError = "Camada de vídeo do jogo não encontrada";
                return Float.NaN;
            }
            List<String> out = run(new String[]{"dumpsys", "SurfaceFlinger", "--latency", layer}, 1500);
            if (out.size() < 2) {
                cachedLayer = null; // força nova busca
                lastError = "Sem dados de quadros";
                return Float.NaN;
            }
            long now = System.nanoTime();
            long newest = 0;
            List<Long> presents = new ArrayList<>();
            for (int i = 1; i < out.size(); i++) {
                String[] p = out.get(i).trim().split("\\s+");
                if (p.length < 3) continue;
                long actual;
                try {
                    actual = Long.parseLong(p[1]);
                } catch (NumberFormatException e) {
                    continue;
                }
                if (actual <= 0 || actual == PENDING) continue;
                presents.add(actual);
                if (actual > newest) newest = actual;
            }
            if (presents.isEmpty()) {
                lastError = "Sem quadros recentes";
                return Float.NaN;
            }
            // Se o último quadro tem mais de 1,5 s, o jogo está parado/pausado.
            if (now - newest > (NS * 3) / 2) return 0f;
            long windowStart = newest - NS;
            int count = 0;
            long first = Long.MAX_VALUE;
            for (long t : presents) {
                if (t > windowStart) {
                    count++;
                    if (t < first) first = t;
                }
            }
            if (count < 2) return count;
            float span = (newest - first) / (float) NS;
            if (span <= 0) return Float.NaN;
            return (count - 1) / span;
        } catch (Exception e) {
            lastError = e.getClass().getSimpleName();
            return Float.NaN;
        }
    }

    private String resolveLayer(String pkg) throws Exception {
        long now = System.currentTimeMillis();
        if (cachedLayer != null && pkg.equals(cachedPkg) && now - layerResolvedAt < 60000) {
            return cachedLayer;
        }
        List<String> layers = run(new String[]{"dumpsys", "SurfaceFlinger", "--list"}, 1500);
        String best = null;
        int bestScore = -1;
        for (String raw : layers) {
            String l = raw.trim();
            if (!l.contains(pkg)) continue;
            int score = 1;
            if (l.startsWith("SurfaceView")) score += 4;   // jogos renderizam em SurfaceView
            if (l.contains("BLAST")) score += 2;
            if (l.contains("Background for")) score -= 3;
            if (score > bestScore) {
                bestScore = score;
                best = l;
            }
        }
        cachedLayer = best;
        cachedPkg = pkg;
        layerResolvedAt = now;
        return best;
    }

    private static List<String> run(String[] cmd, long timeoutMs) throws Exception {
        java.lang.Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line);
                if (lines.size() > 4000) break;
            }
        }
        if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) p.destroy();
        return lines;
    }
}
