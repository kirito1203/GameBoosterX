package com.gameboosterx.rog.core;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ajustes REAIS de sistema que exigem WRITE_SECURE_SETTINGS.
 * Essa permissão não exige root: o próprio usuário a concede uma vez via ADB/LADB:
 *
 *   adb shell pm grant com.gameboosterx.rog android.permission.WRITE_SECURE_SETTINGS
 *
 * Tudo que é alterado é salvo antes e restaurado ao desligar o Turbo X.
 * Cada ajuste é conferido após a escrita: se o sistema recusar, o app informa.
 */
public final class SecureTweaks {

    public static final String GRANT_CMD =
            "adb shell pm grant com.gameboosterx.rog android.permission.WRITE_SECURE_SETTINGS";

    private static final String FILE = "gbx_tweaks_backup";
    private static final String[] ANIM_KEYS = {
            Settings.Global.ANIMATOR_DURATION_SCALE,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
            Settings.Global.WINDOW_ANIMATION_SCALE
    };
    // Chaves usadas pelo Android/realme UI para a taxa de atualização
    private static final String[] REFRESH_KEYS = {"peak_refresh_rate", "min_refresh_rate"};

    private SecureTweaks() {}

    public static boolean available(Context c) {
        return c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isApplied(Context c) {
        return sp(c).getBoolean("applied", false);
    }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Aplica os ajustes e devolve um relatório linha a linha do que funcionou. */
    public static List<String> apply(Context c) {
        List<String> report = new ArrayList<>();
        if (!available(c)) {
            report.add("✗ Permissão WRITE_SECURE_SETTINGS não concedida");
            return report;
        }
        ContentResolver cr = c.getContentResolver();
        SharedPreferences.Editor ed = sp(c).edit();
        boolean firstApply = !isApplied(c);

        // 1) Animações do sistema em 0,5x: troca de telas e menus mais rápida
        int animOk = 0;
        for (String k : ANIM_KEYS) {
            try {
                if (firstApply) ed.putString("g_" + k, Settings.Global.getString(cr, k));
                Settings.Global.putFloat(cr, k, 0.5f);
                if (Math.abs(Settings.Global.getFloat(cr, k, 1f) - 0.5f) < 0.01f) animOk++;
            } catch (Exception ignored) {
            }
        }
        report.add(animOk == ANIM_KEYS.length ? "✓ Animações do sistema aceleradas (0,5x)"
                : "✗ O sistema não aceitou alterar as animações");

        // 2) Tela travada na taxa máxima (evita cair para 60 Hz durante o jogo)
        float max = Diagnostics.refreshRates(c)[1];
        if (max > 61) {
            int ok = 0;
            for (String k : REFRESH_KEYS) {
                try {
                    if (firstApply) ed.putString("s_" + k, Settings.System.getString(cr, k));
                    Settings.System.putFloat(cr, k, max);
                    if (Math.abs(Settings.System.getFloat(cr, k, 0f) - max) < 0.5f) ok++;
                } catch (Exception ignored) {
                }
            }
            report.add(ok == REFRESH_KEYS.length
                    ? String.format(Locale.US, "✓ Tela fixada em %.0f Hz durante o Turbo", max)
                    : "✗ O sistema não aceitou fixar a taxa de atualização (ajuste em Tela)");
        } else {
            report.add("ℹ A tela não suporta mais de 60 Hz");
        }

        ed.putBoolean("applied", true).apply();
        return report;
    }

    /** Restaura exatamente os valores anteriores ao Turbo. */
    public static void restore(Context c) {
        if (!isApplied(c)) return;
        SharedPreferences s = sp(c);
        if (available(c)) {
            ContentResolver cr = c.getContentResolver();
            for (String k : ANIM_KEYS) {
                try {
                    String v = s.getString("g_" + k, null);
                    Settings.Global.putString(cr, k, v == null ? "1.0" : v);
                } catch (Exception ignored) {
                }
            }
            for (String k : REFRESH_KEYS) {
                try {
                    String v = s.getString("s_" + k, null);
                    if (v != null) Settings.System.putString(cr, k, v);
                    else if (k.equals("min_refresh_rate")) Settings.System.putFloat(cr, k, 0f);
                } catch (Exception ignored) {
                }
            }
        }
        s.edit().clear().apply();
    }
}
