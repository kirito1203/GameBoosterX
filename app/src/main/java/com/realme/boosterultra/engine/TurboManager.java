package com.realme.boosterultra.engine;

import com.realme.boosterultra.*;
import com.realme.boosterultra.engine.*;
import com.realme.boosterultra.ai.*;
import com.realme.boosterultra.gaming.*;
import com.realme.boosterultra.monitor.*;
import com.realme.boosterultra.utils.*;
import com.realme.boosterultra.ui.*;


import android.app.Activity;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Aplica tudo o que realmente reduz travadas (sem root) e devolve um relatório. */
public final class TurboManager {
    private TurboManager() {}

    /** Cada item: {"ok"|"warn"|"bad", texto, açãoOpcional} */
    public static List<String[]> run(Activity a) {
        List<String[]> out = new ArrayList<>();
        SharedPreferences p = ProfileManager.prefs(a);

        long before = DeviceInfo.ram(a)[1];
        int n = Optimizer.killBackground(a, null);
        try { Thread.sleep(800); } catch (InterruptedException ignored) {}
        long freed = Math.max(0, DeviceInfo.ram(a)[1] - before);
        out.add(new String[]{"ok", n + " apps em segundo plano encerrados • " + DeviceInfo.fmt(freed) + " liberados", ""});

        long[] r = DeviceInfo.ram(a);
        int used = DeviceInfo.pct(r[0] - r[1], r[0]);
        if (used > 80) out.add(new String[]{"warn", "RAM ainda em " + used + "%: feche apps pesados pela tela de recentes", ""});

        PowerManager pm = (PowerManager) a.getSystemService(Activity.POWER_SERVICE);
        if (pm != null && pm.isPowerSaveMode()) {
            out.add(new String[]{"bad", "Economia de bateria LIGADA: ela deixa o processador lento. Toque para desligar", "saver"});
        } else {
            out.add(new String[]{"ok", "Economia de bateria desligada (processador livre)", ""});
        }

        if (Settings.System.canWrite(a)) {
            ContentResolver cr = a.getContentResolver();
            try {
                SharedPreferences.Editor e = p.edit();
                if (!p.getBoolean("did_bright", false)) {
                    e.putInt("prev_bmode", Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, 0))
                     .putInt("prev_bright", Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128))
                     .putBoolean("did_bright", true);
                }
                if (!p.getBoolean("did_timeout", false)) {
                    e.putInt("prev_timeout", Settings.System.getInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, 30000))
                     .putBoolean("did_timeout", true);
                }
                e.apply();
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, 0);
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, 180);
                Settings.System.putInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, 600000);
                Settings.System.putInt(cr, Settings.System.HAPTIC_FEEDBACK_ENABLED, 0);
                Settings.System.putInt(cr, Settings.System.SOUND_EFFECTS_ENABLED, 0);
                out.add(new String[]{"ok", "Brilho fixo em 70% (menos calor), tela sem apagar, sons e vibração de toque desligados", ""});
            } catch (Exception e) {
                out.add(new String[]{"warn", "O sistema bloqueou parte dos ajustes de tela", ""});
            }
        } else {
            out.add(new String[]{"warn", "Sem permissão para ajustar tela e sons. Toque para liberar", "write"});
        }

        NotificationManager nm = a.getSystemService(NotificationManager.class);
        if (nm != null && nm.isNotificationPolicyAccessGranted()) {
            try {
                if (!p.getBoolean("did_dnd", false)) {
                    p.edit().putInt("prev_filter", nm.getCurrentInterruptionFilter()).putBoolean("did_dnd", true).apply();
                }
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
                out.add(new String[]{"ok", "Não perturbe ligado (notificações não interrompem)", ""});
            } catch (Exception ignored) {}
        } else {
            out.add(new String[]{"warn", "Sem acesso ao Não perturbe. Toque para liberar", "dnd"});
        }

        DeviceInfo.Battery b = DeviceInfo.battery(a);
        if (b.temp >= 42) out.add(new String[]{"bad", String.format(Locale.US, "Bateria a %.1f°C: muito quente, vai travar. Deixe esfriar", b.temp), ""});
        else if (b.temp >= 38) out.add(new String[]{"warn", String.format(Locale.US, "Bateria a %.1f°C: morna. Tire a capinha", b.temp), ""});
        else out.add(new String[]{"ok", String.format(Locale.US, "Temperatura boa (%.1f°C)", b.temp), ""});
        if (b.charging) out.add(new String[]{"warn", "Carregando: jogar na tomada esquenta e o chip fica mais lento", ""});
        int th = DeviceInfo.thermalStatus(a);
        if (th >= 2) out.add(new String[]{"bad", "O sistema já está reduzindo o desempenho por calor", ""});

        long[] s = DeviceInfo.storage();
        int su = DeviceInfo.pct(s[0] - s[1], s[0]);
        if (su > 90) out.add(new String[]{"bad", "Armazenamento " + su + "% cheio: isso deixa tudo lento. Toque para limpar", "storage"});
        else out.add(new String[]{"ok", "Armazenamento com espaço (" + DeviceInfo.fmt(s[1]) + " livres)", ""});

        if (pm != null && !pm.isIgnoringBatteryOptimizations(a.getPackageName())) {
            out.add(new String[]{"warn", "O Android pode fechar o turbo contínuo. Toque para liberar", "battery"});
        }

        p.edit().putBoolean("opt_loop", true).putBoolean("active", true).apply();
        out.add(new String[]{"ok", "Turbo contínuo ligado: limpa a RAM a cada 90s enquanto você joga no Modo Jogo", ""});
        return out;
    }
}
