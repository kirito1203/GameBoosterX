package com.pulsesolar.app.modules.optimizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Cálculos da calibração de sincronia, sem dependências do Android (testáveis em Java puro).
 *
 * Método:
 * 1. O relógio de apresentação do áudio (pares quadro → nanossegundos do AudioTimestamp) é
 *    ajustado por regressão linear. Se os pares forem poucos, ou se a taxa obtida diferir da
 *    taxa nominal / os resíduos forem grandes, o relógio é considerado inconsistente.
 * 2. Cada toque é associado a uma batida usando uma janela assimétrica: até 35% do intervalo
 *    antes da batida e até 65% depois (atraso de áudio é positivo; toques muito adiantados são raros).
 *    Isso evita que atrasos altos (ex.: Bluetooth) "pulem" para a batida seguinte.
 * 3. Estatística robusta: mediana e MAD para descartar toques fora do ritmo; média e desvio
 *    padrão amostral só dos toques válidos; margem ≈ 2·DP/√n.
 */
public final class RhythmMath {

    public static final double EARLY_FRACTION = 0.35;
    public static final int MIN_VALID_TAPS = 14;
    public static final double MAX_SD_MS = 40.0;
    public static final int MAX_REJECT_PCT = 25;
    public static final double MIN_OUTLIER_THRESHOLD_MS = 50.0;
    public static final double MAX_RATE_ERROR_PCT = 2.0;
    public static final double MAX_CLOCK_RMS_MS = 3.0;
    public static final int MIN_CLOCK_POINTS = 5;

    private RhythmMath() {
    }

    // ------------------------------------------------------------ Relógio do áudio

    public static final class Clock {
        public int points;
        public boolean ok;
        public String problem;
        public double rmsMs = Double.NaN;
        public double rateErrorPct = Double.NaN;
        double f0, t0, a, b;

        /** Instante (ns, CLOCK_MONOTONIC) em que o quadro `frame` sai pelo alto-falante/fone. */
        public double nanosAt(double frame) {
            return t0 + a + b * (frame - f0);
        }
    }

    /** @param pairs lista de {framePosition, nanoTime} com framePosition crescente. */
    public static Clock fitClock(List<long[]> pairs, int sampleRate) {
        Clock c = new Clock();
        c.points = pairs.size();
        if (pairs.size() < MIN_CLOCK_POINTS) {
            c.ok = false;
            c.problem = "o Android não forneceu o relógio de apresentação do áudio (AudioTimestamp) — sem ele não dá para medir o atraso real da saída de som";
            return c;
        }
        c.f0 = pairs.get(0)[0];
        c.t0 = pairs.get(0)[1];
        int n = pairs.size();
        double sx = 0, sy = 0;
        for (long[] p : pairs) {
            sx += p[0] - c.f0;
            sy += p[1] - c.t0;
        }
        double mx = sx / n, my = sy / n, sxx = 0, sxy = 0;
        for (long[] p : pairs) {
            double x = p[0] - c.f0 - mx, y = p[1] - c.t0 - my;
            sxx += x * x;
            sxy += x * y;
        }
        if (sxx <= 0) {
            c.ok = false;
            c.problem = "o relógio do áudio não avançou durante o teste";
            return c;
        }
        c.b = sxy / sxx;
        c.a = my - c.b * mx;
        double res = 0;
        for (long[] p : pairs) {
            double pred = c.a + c.b * (p[0] - c.f0);
            double r = (p[1] - c.t0) - pred;
            res += r * r;
        }
        c.rmsMs = Math.sqrt(res / n) / 1e6;
        double expected = 1e9 / sampleRate;
        c.rateErrorPct = (c.b / expected - 1.0) * 100.0;
        if (Math.abs(c.rateErrorPct) > MAX_RATE_ERROR_PCT) {
            c.ok = false;
            c.problem = String.format(java.util.Locale.ROOT,
                    "o relógio do áudio ficou inconsistente (taxa %.1f%% diferente da esperada)", c.rateErrorPct);
        } else if (c.rmsMs > MAX_CLOCK_RMS_MS) {
            c.ok = false;
            c.problem = String.format(java.util.Locale.ROOT,
                    "o relógio do áudio oscilou demais (%.1f ms de erro médio)", c.rmsMs);
        } else {
            c.ok = true;
        }
        return c;
    }

    // ------------------------------------------------------------ Análise dos toques

    public static final class Analysis {
        public int totalTaps, warmupTaps, earlyTaps, lateTaps, duplicateTaps, outlierTaps, usedTaps, beatsAnalyzed;
        public double offsetMs = Double.NaN, medianMs = Double.NaN, sdMs = Double.NaN, marginMs = Double.NaN;
        public boolean valid;
        public final List<String> problems = new ArrayList<>();
    }

    /**
     * @param taps    instantes dos toques (ns, CLOCK_MONOTONIC)
     * @param beatNs  instantes previstos de saída de cada batida (ns), índice = número da batida
     * @param warmup  batidas iniciais descartadas
     */
    public static Analysis analyze(List<Long> taps, double[] beatNs, int warmup, Clock clock) {
        Analysis an = new Analysis();
        an.totalTaps = taps.size();
        int beats = beatNs.length;
        an.beatsAnalyzed = Math.max(0, beats - warmup);
        double period = beats > 1 ? (beatNs[beats - 1] - beatNs[0]) / (beats - 1) : 0;

        if (clock == null || !clock.ok) {
            an.valid = false;
            an.problems.add(clock == null ? "relógio do áudio ausente" : clock.problem);
        }
        if (period <= 0) {
            an.problems.add("intervalo entre batidas inválido");
            return an;
        }

        double[] perBeat = new double[beats];
        boolean[] has = new boolean[beats];
        for (long t : taps) {
            int k = (int) Math.floor((t - beatNs[0] + EARLY_FRACTION * period) / period);
            if (k < 0) {
                an.earlyTaps++;
                continue;
            }
            if (k < warmup) {
                an.warmupTaps++;
                continue;
            }
            if (k >= beats) {
                an.lateTaps++;
                continue;
            }
            if (has[k]) {
                an.duplicateTaps++; // mantém o primeiro toque da batida
                continue;
            }
            has[k] = true;
            perBeat[k] = (t - beatNs[k]) / 1e6;
        }

        List<Double> offsets = new ArrayList<>();
        for (int k = warmup; k < beats; k++) if (has[k]) offsets.add(perBeat[k]);
        if (offsets.size() < 3) {
            an.valid = false;
            an.problems.add("toques insuficientes nas batidas analisadas (" + offsets.size() + ")");
            return an;
        }

        double med = median(offsets);
        List<Double> dev = new ArrayList<>();
        for (double o : offsets) dev.add(Math.abs(o - med));
        double sigma = 1.4826 * median(dev);
        double thr = Math.max(3 * sigma, MIN_OUTLIER_THRESHOLD_MS);
        List<Double> in = new ArrayList<>();
        for (double o : offsets) {
            if (Math.abs(o - med) <= thr) in.add(o);
            else an.outlierTaps++;
        }
        an.medianMs = med;
        an.usedTaps = in.size();
        double sum = 0;
        for (double o : in) sum += o;
        an.offsetMs = sum / in.size();
        double var = 0;
        for (double o : in) var += (o - an.offsetMs) * (o - an.offsetMs);
        an.sdMs = in.size() > 1 ? Math.sqrt(var / (in.size() - 1)) : Double.NaN;
        an.marginMs = in.size() > 1 ? 2 * an.sdMs / Math.sqrt(in.size()) : Double.NaN;

        int rejectPct = offsets.isEmpty() ? 100 : an.outlierTaps * 100 / offsets.size();
        if (an.usedTaps < MIN_VALID_TAPS) {
            an.problems.add("só " + an.usedTaps + " toques válidos (mínimo " + MIN_VALID_TAPS + " de " + an.beatsAnalyzed + ")");
        }
        if (!Double.isNaN(an.sdMs) && an.sdMs > MAX_SD_MS) {
            an.problems.add(String.format(java.util.Locale.ROOT, "variação de ±%.0f ms (máximo aceito: ±%.0f ms)", an.sdMs, MAX_SD_MS));
        }
        if (rejectPct > MAX_REJECT_PCT) {
            an.problems.add(rejectPct + "% dos toques fora do ritmo (máximo " + MAX_REJECT_PCT + "%)");
        }
        an.valid = an.problems.isEmpty();
        return an;
    }

    static double median(List<Double> v) {
        double[] a = new double[v.size()];
        for (int i = 0; i < a.length; i++) a[i] = v.get(i);
        Arrays.sort(a);
        int n = a.length;
        return n % 2 == 1 ? a[n / 2] : (a[n / 2 - 1] + a[n / 2]) / 2.0;
    }
}
