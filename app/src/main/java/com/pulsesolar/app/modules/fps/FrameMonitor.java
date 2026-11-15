package com.pulsesolar.app.modules.fps;

import android.view.Choreographer;

import java.util.Arrays;

/**
 * Mede o ritmo real de quadros (vsync) que o PRÓPRIO Pulse Solar recebe do Android,
 * usando Choreographer. NÃO é o FPS de outro app/jogo: o Android não fornece essa
 * leitura a apps sem root/ADB. Quando o sistema está sobrecarregado (CPU, memória,
 * temperatura), os quadros do Pulse Solar também atrasam — por isso funciona como
 * "pulso do sistema".
 *
 * Deve ser usado na thread principal.
 */
public final class FrameMonitor implements Choreographer.FrameCallback {

    public interface Listener {
        void onSecond(Stats s);
    }

    public static final class Stats {
        /** Quadros por segundo entregues ao Pulse Solar no último segundo. */
        public float fps;
        /** Taxa de vsync estimada pela mediana dos intervalos (≈ taxa da tela). */
        public float vsyncHz;
        public int frames;
        /** Quadros que chegaram com mais de 1,5× o intervalo normal. */
        public int late;
        /** Pausas acima de 100 ms (travadas perceptíveis). */
        public int stalls;

        public float stability() {
            return frames == 0 ? 0f : (frames - late) * 100f / frames;
        }
    }

    private static final long STALL_NS = 100_000_000L;
    private static final long SECOND_NS = 1_000_000_000L;

    private final Listener listener;
    private final long[] intervals = new long[512];
    private int count = 0;
    private long lastNs = 0;
    private long windowStartNs = 0;
    private boolean running = false;

    // Totais desde o início
    public long totalFrames = 0;
    public long totalLate = 0;
    public long totalStalls = 0;

    public FrameMonitor(Listener listener) {
        this.listener = listener;
    }

    public void start() {
        if (running) return;
        running = true;
        lastNs = 0;
        count = 0;
        Choreographer.getInstance().postFrameCallback(this);
    }

    public void stop() {
        running = false;
        Choreographer.getInstance().removeFrameCallback(this);
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!running) return;
        if (lastNs == 0) {
            windowStartNs = frameTimeNanos;
        } else {
            long dt = frameTimeNanos - lastNs;
            if (dt > 0 && count < intervals.length) intervals[count++] = dt;
        }
        lastNs = frameTimeNanos;

        long win = frameTimeNanos - windowStartNs;
        if (win >= SECOND_NS && count > 0) {
            emit(win);
            count = 0;
            windowStartNs = frameTimeNanos;
        }
        Choreographer.getInstance().postFrameCallback(this);
    }

    private void emit(long windowNs) {
        long[] sorted = Arrays.copyOf(intervals, count);
        Arrays.sort(sorted);
        long median = sorted[count / 2];
        Stats s = new Stats();
        s.frames = count;
        s.fps = count * 1e9f / windowNs;
        s.vsyncHz = median > 0 ? 1e9f / median : 0f;
        for (int i = 0; i < count; i++) {
            long dt = intervals[i];
            if (dt > median * 3 / 2) s.late++;
            if (dt > STALL_NS) s.stalls++;
        }
        totalFrames += s.frames;
        totalLate += s.late;
        totalStalls += s.stalls;
        if (listener != null) listener.onSecond(s);
    }
}
