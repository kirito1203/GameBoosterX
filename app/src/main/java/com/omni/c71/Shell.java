package com.omni.c71;

import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

/** Executa comandos com permissão ADB através do Shizuku (sem root, sem PC). */
public final class Shell {

    public interface Callback { void done(boolean ok, String out); }

    public static final String SHIZUKU_PKG = "moe.shizuku.privileged.api";

    private static final ExecutorService EX = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Method newProcess;

    private Shell() {}

    /** O serviço do Shizuku está rodando? */
    public static boolean running() {
        try { return Shizuku.pingBinder(); } catch (Throwable t) { return false; }
    }

    /** Rodando e com permissão liberada para este app? */
    public static boolean ready() {
        try {
            if (!Shizuku.pingBinder() || Shizuku.isPreV11()) return false;
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void request(int code) {
        try { Shizuku.requestPermission(code); } catch (Throwable ignored) { }
    }

    public static String mode() {
        try { return Shizuku.getUid() == 0 ? "root" : "ADB"; } catch (Throwable t) { return "?"; }
    }

    /** Retorna {códigoDeSaída, saída}. Bloqueia — use fora da thread principal. */
    public static String[] runSync(String cmd) throws Exception {
        if (newProcess == null) {
            newProcess = Shizuku.class.getDeclaredMethod("newProcess",
                    String[].class, String[].class, String.class);
            newProcess.setAccessible(true);
        }
        Process p = (Process) newProcess.invoke(null,
                new String[]{"sh", "-c", cmd + " 2>&1"}, null, null);
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (sb.length() < 60000) sb.append(line).append('\n');
            }
        }
        int code = p.waitFor();
        return new String[]{String.valueOf(code), sb.toString().trim()};
    }

    public static void run(String cmd, Callback cb) {
        EX.execute(() -> {
            boolean ok;
            String out;
            if (!ready()) {
                ok = false;
                out = "Shizuku não está ativo ou sem permissão.";
            } else {
                try {
                    String[] r = runSync(cmd);
                    ok = "0".equals(r[0]);
                    out = r[1];
                } catch (Throwable t) {
                    ok = false;
                    Throwable c = t.getCause() != null ? t.getCause() : t;
                    out = "Erro: " + c;
                }
            }
            if (cb != null) {
                final boolean fo = ok;
                final String fs = out;
                MAIN.post(() -> cb.done(fo, fs));
            }
        });
    }
}
