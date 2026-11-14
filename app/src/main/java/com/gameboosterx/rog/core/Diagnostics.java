package com.gameboosterx.rog.core;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Display;
import android.hardware.display.DisplayManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Identifica limitações de desempenho reais e detectáveis no momento. */
public final class Diagnostics {

    public static final int OK = 0, INFO = 1, WARN = 2, CRIT = 3;

    public static final class Issue {
        public final int severity;
        public final String title;
        public final String detail;
        public final String actionLabel;
        public final Intent action;

        Issue(int severity, String title, String detail, String actionLabel, Intent action) {
            this.severity = severity;
            this.title = title;
            this.detail = detail;
            this.actionLabel = actionLabel;
            this.action = action;
        }
    }

    private Diagnostics() {}

    public static List<Issue> run(Context c) {
        List<Issue> out = new ArrayList<>();
        SystemStats.RamInfo ram = SystemStats.ram(c);
        SystemStats.BatteryInfo bat = SystemStats.battery(c);
        SystemStats.ThermalInfo th = SystemStats.thermal(c);
        SystemStats.StorageInfo st = SystemStats.storage();
        float warn = Prefs.tempWarn(c);
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);

        if (pm != null && pm.isPowerSaveMode()) {
            out.add(new Issue(CRIT, "Economia de bateria ativa",
                    "O modo de economia reduz o desempenho da CPU/GPU e a taxa de atualização. Desative antes de jogar.",
                    "Abrir", new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)));
        }

        if (th.status >= PowerManager.THERMAL_STATUS_SEVERE) {
            out.add(new Issue(CRIT, "Limitação térmica severa",
                    "O sistema está reduzindo o desempenho para proteger o aparelho. Pause e deixe esfriar.", null, null));
        } else if (th.status == PowerManager.THERMAL_STATUS_MODERATE
                || (!Float.isNaN(th.headroom) && th.headroom >= 0.85f)) {
            out.add(new Issue(WARN, "Aparelho perto do limite térmico",
                    "Quedas de FPS por aquecimento são prováveis. Reduza gráficos/FPS ou faça uma pausa.", null, null));
        }

        if (bat.hasTemp() && bat.tempC >= warn) {
            out.add(new Issue(bat.tempC >= warn + 3 ? CRIT : WARN,
                    String.format(Locale.US, "Bateria quente (%.1f °C)", bat.tempC),
                    bat.charging ? "Jogar carregando aquece mais. Desconecte o carregador durante a partida."
                            : "Remova a capa e evite sol/superfícies macias.", null, null));
        }

        int freePct = ram.totalBytes > 0 ? (int) (ram.availBytes * 100 / ram.totalBytes) : 100;
        if (ram.lowMemory || freePct < 15) {
            out.add(new Issue(WARN, "Pouca RAM livre (" + freePct + "%)",
                    "Feche apps abertos recentemente pela tela de recentes antes de iniciar jogos pesados.", null, null));
        }

        int freeStoragePct = st.totalBytes > 0 ? (int) (st.freeBytes * 100 / st.totalBytes) : 100;
        if (freeStoragePct < 10) {
            out.add(new Issue(WARN, "Armazenamento quase cheio (" + freeStoragePct + "% livre)",
                    "Pouco espaço deixa o sistema mais lento e pode causar travamentos em jogos que gravam cache.",
                    "Liberar", new Intent(android.os.storage.StorageManager.ACTION_MANAGE_STORAGE)));
        }

        if (bat.level >= 0 && bat.level <= 20 && !bat.charging) {
            out.add(new Issue(INFO, "Bateria baixa (" + bat.level + "%)",
                    "Alguns aparelhos reduzem o desempenho com bateria baixa e a economia automática pode ligar.", null, null));
        }

        float[] rr = refreshRates(c);
        if (rr[1] > 0 && rr[0] + 1 < rr[1]) {
            out.add(new Issue(INFO, String.format(Locale.US, "Tela em %.0f Hz (máx. %.0f Hz)", rr[0], rr[1]),
                    "A tela está abaixo do máximo suportado agora. Pode ser a taxa adaptativa do sistema "
                            + "(normal em telas paradas) ou a opção fixada em 60 Hz. Confira em Tela ▸ Taxa de atualização.",
                    "Tela", new Intent(Settings.ACTION_DISPLAY_SETTINGS)));
        }

        if (pm != null && !pm.isIgnoringBatteryOptimizations(c.getPackageName())) {
            out.add(new Issue(INFO, "Game Booster X pode ser encerrado em segundo plano",
                    "A otimização de bateria do sistema pode interromper a barra e a gravação de sessões. "
                            + "Permita atividade em segundo plano para o Game Booster X.",
                    "Ajustar", new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));
        }

        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null && cm.getRestrictBackgroundStatus() == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED) {
                out.add(new Issue(INFO, "Economia de dados ativa",
                        "Downloads de atualizações dos jogos em segundo plano podem ser bloqueados.", null, null));
            }
        } catch (Exception ignored) {
        }

        if (!Perms.usageStats(c)) {
            out.add(new Issue(INFO, "Detecção automática de jogos desligada",
                    "Conceda o Acesso de Uso para que o app detecte o jogo aberto, aplique o perfil e grave a sessão.",
                    "Conceder", new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));
        }
        return out;
    }

    /** [atual, máximo] em Hz. */
    public static float[] refreshRates(Context c) {
        float cur = 0, max = 0;
        try {
            DisplayManager dm = (DisplayManager) c.getSystemService(Context.DISPLAY_SERVICE);
            Display d = dm.getDisplay(Display.DEFAULT_DISPLAY);
            cur = d.getRefreshRate();
            for (Display.Mode m : d.getSupportedModes()) max = Math.max(max, m.getRefreshRate());
        } catch (Exception ignored) {
        }
        return new float[]{cur, max};
    }
}
