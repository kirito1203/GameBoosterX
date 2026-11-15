package com.pulsesolar.app.core;

import android.os.SystemClock;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Teste de CPU real: executa blocos fixos de cálculo inteiro/ponto flutuante e mede
 * quantos blocos são concluídos por segundo. Deve ser chamado fora da thread principal.
 */
public final class Benchmark {

    private static volatile long sink;

    private Benchmark() {
    }

    /** Um bloco de trabalho fixo (xorshift + ponto flutuante). */
    static long unit(long seed) {
        long x = seed;
        double d = 1.0001;
        for (int i = 0; i < 100_000; i++) {
            x ^= x << 13;
            x ^= x >>> 7;
            x ^= x << 17;
            d = d * 1.0000001 + (x & 0xFF) * 1e-9;
        }
        return x + (long) d;
    }

    /** Executa por `millis` ms em `threads` threads e retorna blocos por segundo × 10 (pontos). */
    public static long run(int threads, long millis) throws InterruptedException {
        final long deadline = SystemClock.elapsedRealtime() + millis;
        final AtomicLong total = new AtomicLong();
        Thread[] ts = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            final long seed = 88172645463325252L + t;
            ts[t] = new Thread(() -> {
                long n = 0;
                long s = seed;
                while (SystemClock.elapsedRealtime() < deadline) {
                    s = unit(s);
                    n++;
                }
                total.addAndGet(n);
                sink += s;
            }, "bench-" + t);
            ts[t].start();
        }
        for (Thread th : ts) th.join();
        return total.get() * 10_000L / millis;
    }
}
