package com.gameboosterx.rog.core;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import rikka.shizuku.Shizuku;

/**
 * Integração com o Shizuku (grátis, sem root). Com ele ativo, o Game Booster X
 * concede a si mesmo, com um toque, as permissões DUMP (FPS real) e
 * WRITE_SECURE_SETTINGS (Turbo avançado) — sem terminal e sem digitar comandos.
 */
public final class ShizukuHelper {

    public static final String SHIZUKU_PKG = "moe.shizuku.privileged.api";
    private static final int REQ = 4711;

    private ShizukuHelper() {}

    public static boolean installed(Activity a) {
        try {
            a.getPackageManager().getPackageInfo(SHIZUKU_PKG, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean running() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean permitted() {
        try {
            return !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Fluxo completo com diálogos. onDone roda na thread principal ao final. */
    public static void unlock(Activity a, Runnable onDone) {
        if (Perms.dump(a) && SecureTweaks.available(a)) {
            Toast.makeText(a, "As duas permissões já estão liberadas ✓", Toast.LENGTH_SHORT).show();
            if (onDone != null) onDone.run();
            return;
        }
        if (!installed(a)) {
            new AlertDialog.Builder(a)
                    .setTitle("Instale o Shizuku")
                    .setMessage("O Shizuku é grátis e não precisa de root. Instale, abra e siga o pareamento "
                            + "com a Depuração por Wi-Fi. Depois volte e toque neste botão de novo.")
                    .setPositiveButton("Play Store", (d, w) -> {
                        try {
                            a.startActivity(new Intent(Intent.ACTION_VIEW,
                                    Uri.parse("market://details?id=" + SHIZUKU_PKG)));
                        } catch (Exception e) {
                            Perms.open(a, new Intent(Intent.ACTION_VIEW,
                                    Uri.parse("https://play.google.com/store/apps/details?id=" + SHIZUKU_PKG)));
                        }
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
            return;
        }
        if (!running()) {
            new AlertDialog.Builder(a)
                    .setTitle("Inicie o Shizuku")
                    .setMessage("Abra o Shizuku e toque em \"Iniciar\" (via Depuração por Wi-Fi). "
                            + "Quando aparecer \"Shizuku está em execução\", volte e toque neste botão de novo.")
                    .setPositiveButton("Abrir Shizuku", (d, w) -> {
                        Intent i = a.getPackageManager().getLaunchIntentForPackage(SHIZUKU_PKG);
                        if (i != null) Perms.open(a, i);
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
            return;
        }
        if (Shizuku.isPreV11()) {
            Toast.makeText(a, "Versão do Shizuku muito antiga. Atualize pela Play Store.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!permitted()) {
            Shizuku.OnRequestPermissionResultListener[] holder = new Shizuku.OnRequestPermissionResultListener[1];
            holder[0] = (code, result) -> {
                if (code != REQ) return;
                Shizuku.removeRequestPermissionResultListener(holder[0]);
                if (result == PackageManager.PERMISSION_GRANTED) grant(a, onDone);
                else Toast.makeText(a, "Permissão do Shizuku negada.", Toast.LENGTH_SHORT).show();
            };
            Shizuku.addRequestPermissionResultListener(holder[0]);
            Shizuku.requestPermission(REQ);
            return;
        }
        grant(a, onDone);
    }

    private static void grant(Activity a, Runnable onDone) {
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            List<String> rep = new ArrayList<>();
            String pkg = a.getPackageName();
            String[] perms = {"android.permission.DUMP", "android.permission.WRITE_SECURE_SETTINGS"};
            for (String p : perms) {
                String err = exec(new String[]{"pm", "grant", pkg, p});
                rep.add(err == null ? "✓ " + p.substring(p.lastIndexOf('.') + 1) : "✗ " + p.substring(p.lastIndexOf('.') + 1) + ": " + err);
            }
            main.post(() -> {
                boolean ok = Perms.dump(a) && SecureTweaks.available(a);
                new AlertDialog.Builder(a)
                        .setTitle(ok ? "Liberado!" : "Resultado")
                        .setMessage(String.join("\n", rep) + (ok
                                ? "\n\nFPS real e Turbo avançado ativos. Você já pode desligar a Depuração por Wi-Fi: as permissões continuam valendo."
                                : "\n\nSe algo falhou, reinicie o Shizuku e tente de novo."))
                        .setPositiveButton("OK", null)
                        .show();
                if (onDone != null) onDone.run();
            });
        }, "gbx-shizuku").start();
    }

    /** Executa um comando com a identidade do Shizuku (shell). Retorna null se ok, ou a mensagem de erro. */
    private static String exec(String[] cmd) {
        try {
            // newProcess não é público nas versões novas da API; acesso por reflexão (prática comum).
            Method m = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
            m.setAccessible(true);
            Process p = (Process) m.invoke(null, cmd, null, null);
            StringBuilder out = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
                String line;
                while ((line = br.readLine()) != null) out.append(line).append(' ');
            }
            if (!p.waitFor(10, TimeUnit.SECONDS)) p.destroy();
            int code = p.exitValue();
            return code == 0 ? null : (out.length() > 0 ? out.toString().trim() : "código " + code);
        } catch (Throwable t) {
            return t.getClass().getSimpleName();
        }
    }
}
