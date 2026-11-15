package com.pulsesolar.app.core;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.ConnectivityManager;
import android.os.PowerManager;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Procura possíveis causas de lentidão a partir de dados reais do sistema. */
public final class Analyzer {

    public static final int OK = 0;
    public static final int INFO = 1;
    public static final int WARN = 2;
    public static final int CRIT = 3;

    public static final class Finding {
        public final int severity;
        public final String title;
        public final String detail;
        public String actionLabel;
        public Intent[] actions;

        public Finding(int severity, String title, String detail) {
            this.severity = severity;
            this.title = title;
            this.detail = detail;
        }

        public Finding action(String label, Intent... intents) {
            this.actionLabel = label;
            this.actions = intents;
            return this;
        }
    }

    private Analyzer() {
    }

    /**
     * @param deep true inclui contagem de apps e estatísticas de uso (mais lento: rode fora da UI).
     */
    public static List<Finding> analyze(Context c, boolean deep) {
        List<Finding> out = new ArrayList<>();
        ContentResolver cr = c.getContentResolver();

        // RAM
        SystemInfo.Ram r = SystemInfo.ram(c);
        Intent appsList = new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS);
        float ap = r.availPercent();
        String ramTxt = Format.bytes(r.avail) + " livres de " + Format.bytes(r.total)
                + " (" + Format.percent(ap) + ")";
        if (r.low) {
            out.add(new Finding(CRIT, "Memória RAM crítica",
                    "O Android sinalizou memória baixa: " + ramTxt + ". Ele vai encerrar apps em segundo plano e jogos podem recarregar ou travar. Feche apps que não está usando.")
                    .action("Ver apps instalados", appsList, Nav.settings()));
        } else if (ap < 10f) {
            out.add(new Finding(CRIT, "RAM quase esgotada", ramTxt + ". Feche apps abertos na tela de recentes antes de jogar.")
                    .action("Ver apps instalados", appsList, Nav.settings()));
        } else if (ap < 20f) {
            out.add(new Finding(WARN, "RAM disponível baixa", ramTxt + ". Muitos apps abertos podem causar travadas.")
                    .action("Ver apps instalados", appsList, Nav.settings()));
        } else {
            out.add(new Finding(OK, "RAM adequada", ramTxt + "."));
        }

        // Armazenamento
        SystemInfo.Storage s = SystemInfo.storage();
        Intent storage = new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS);
        float fp = s.freePercent();
        String stoTxt = Format.bytes(s.free) + " livres de " + Format.bytes(s.total)
                + " (" + Format.percent(fp) + ")";
        if (fp < 5f) {
            out.add(new Finding(CRIT, "Armazenamento quase cheio", stoTxt + ". Com pouco espaço, o sistema fica lento para gravar dados e atualizar apps.")
                    .action("Abrir armazenamento", storage, Nav.settings()));
        } else if (fp < 15f) {
            out.add(new Finding(WARN, "Pouco espaço livre", stoTxt + ". Mantenha pelo menos 15% livre para o sistema funcionar bem.")
                    .action("Abrir armazenamento", storage, Nav.settings()));
        } else {
            out.add(new Finding(OK, "Espaço de armazenamento adequado", stoTxt + "."));
        }

        // Bateria / energia / temperatura
        SystemInfo.Battery b = SystemInfo.battery(c);
        if (b.powerSave) {
            out.add(new Finding(WARN, "Economia de bateria ativada",
                    "Esse modo reduz o desempenho do processador e limita atividades em segundo plano.")
                    .action("Abrir economia de bateria",
                            new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS), Nav.settings()));
        } else {
            out.add(new Finding(OK, "Economia de bateria desativada", "O processador não está sendo limitado por economia de energia."));
        }

        if (b.thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) {
            out.add(new Finding(CRIT, "Aparelho limitando por temperatura",
                    "Estado térmico: " + SystemInfo.thermalText(b.thermalStatus) + ". O sistema está reduzindo a velocidade para esfriar. Pare de jogar alguns minutos e retire a capa."));
        } else if (b.thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE) {
            out.add(new Finding(WARN, "Aparelho aquecido",
                    "Estado térmico: " + SystemInfo.thermalText(b.thermalStatus) + ". O desempenho pode começar a cair."));
        } else if (b.thermalStatus >= 0) {
            out.add(new Finding(OK, "Temperatura do sistema normal",
                    "Estado térmico: " + SystemInfo.thermalText(b.thermalStatus) + "."));
        }

        if (!Float.isNaN(b.tempC)) {
            if (b.tempC >= 42f) {
                out.add(new Finding(CRIT, "Bateria muito quente",
                        "Temperatura da bateria: " + Format.one(b.tempC) + " °C. Evite jogar carregando e deixe esfriar."));
            } else if (b.tempC >= 38f) {
                out.add(new Finding(WARN, "Bateria quente",
                        "Temperatura da bateria: " + Format.one(b.tempC) + " °C. Acima de ~40 °C o aparelho costuma reduzir o desempenho."));
            }
        }

        if (b.level >= 0 && b.level <= 15 && !b.isCharging()) {
            out.add(new Finding(WARN, "Bateria baixa",
                    "Nível em " + b.level + "%. Com bateria baixa, o sistema pode ativar economia automática."));
        }

        // Tempo ligado
        long up = SystemInfo.uptimeMs();
        if (up > 7L * 24 * 60 * 60 * 1000) {
            out.add(new Finding(INFO, "Aparelho ligado há muito tempo",
                    "Ligado há " + Format.duration(up) + ". Reiniciar encerra processos acumulados e libera memória."));
        }

        // Animações
        float anim = Settings.Global.getFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        float trans = Settings.Global.getFloat(cr, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f);
        if (anim > 1f || trans > 1f) {
            out.add(new Finding(INFO, "Animações mais lentas que o padrão",
                    "Escala de animação: " + anim + "x, transição: " + trans + "x. Isso deixa a navegação com sensação de lentidão.")
                    .action("Abrir opções do desenvolvedor",
                            new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS), Nav.settings()));
        }

        // Economia de dados
        ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null && cm.getRestrictBackgroundStatus()
                == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED) {
            out.add(new Finding(INFO, "Economia de dados ativada",
                    "Apps em segundo plano têm a internet limitada. Pode atrasar downloads e sincronização de jogos online."));
        }

        // Conexão
        NetTests.State ns = NetTests.state(c);
        Intent netPanel = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
        if (!ns.connected) {
            out.add(new Finding(INFO, "Sem conexão com a internet",
                    "Jogos e apps online vão travar ou não carregar.")
                    .action("Abrir configurações de rede", netPanel, Nav.settings()));
        } else if (!ns.validated) {
            out.add(new Finding(WARN, "Conectado, mas sem acesso à internet",
                    "O Android não conseguiu validar a internet em " + ns.transport() + ". Pode ser rede com login, sinal fraco ou roteador com problema.")
                    .action("Abrir configurações de rede", netPanel, Nav.settings()));
        } else {
            out.add(new Finding(OK, "Internet funcionando", "Conectado por " + ns.transport() + "."));
        }

        // Resultado do teste térmico
        SharedPreferences bench = c.getSharedPreferences("bench", Context.MODE_PRIVATE);
        float drop = bench.getFloat("stab_drop", -1f);
        if (drop >= 25f) {
            out.add(new Finding(WARN, "Queda de desempenho por aquecimento",
                    "No último teste de estabilidade, a CPU perdeu " + Format.one(drop) + "% do desempenho. Jogos longos tendem a ficar mais lentos com o tempo."));
        }

        if (deep) {
            PackageManager pm = c.getPackageManager();
            Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> ris = pm.queryIntentActivities(launcher, 0);
            Set<String> userApps = new HashSet<>();
            for (ResolveInfo ri : ris) {
                ApplicationInfo ai = ri.activityInfo.applicationInfo;
                if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0) userApps.add(ai.packageName);
            }
            if (userApps.size() > 80) {
                out.add(new Finding(INFO, "Muitos apps instalados",
                        userApps.size() + " apps instalados por você. Apps que rodam serviços em segundo plano consomem RAM e bateria; desinstale os que não usa.")
                        .action("Ver apps instalados", appsList, Nav.settings()));
            }

            if (Perms.hasUsageAccess(c)) {
                UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
                long end = System.currentTimeMillis();
                Map<String, UsageStats> map = usm.queryAndAggregateUsageStats(end - 24L * 60 * 60 * 1000, end);
                List<UsageStats> list = new ArrayList<>();
                for (UsageStats u : map.values()) {
                    if (u.getTotalTimeInForeground() > 60_000 && !u.getPackageName().equals(c.getPackageName())) {
                        list.add(u);
                    }
                }
                Collections.sort(list, (a, bb) -> Long.compare(bb.getTotalTimeInForeground(), a.getTotalTimeInForeground()));
                if (!list.isEmpty()) {
                    StringBuilder sb = new StringBuilder("Tempo em primeiro plano nas últimas 24 h:\n");
                    int n = Math.min(6, list.size());
                    for (int i = 0; i < n; i++) {
                        UsageStats u = list.get(i);
                        sb.append("• ").append(label(pm, u.getPackageName()))
                                .append(" — ").append(Format.duration(u.getTotalTimeInForeground()));
                        if (i < n - 1) sb.append('\n');
                    }
                    out.add(new Finding(INFO, "Apps mais usados", sb.toString()));
                }
            } else {
                out.add(new Finding(INFO, "Análise de uso desativada",
                        "Permita o acesso ao uso para ver quais apps você mais usa e quais ocupam mais espaço.")
                        .action("Permitir acesso ao uso", new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), Nav.settings()));
            }
        }

        Collections.sort(out, (a, bb) -> Integer.compare(bb.severity, a.severity));
        return out;
    }

    private static String label(PackageManager pm, String pkg) {
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    public static String severityText(int s) {
        switch (s) {
            case CRIT: return "CRÍTICO";
            case WARN: return "ATENÇÃO";
            case INFO: return "INFORMAÇÃO";
            default: return "OK";
        }
    }

    public static int severityColor(int s) {
        switch (s) {
            case CRIT: return Palette.RED;
            case WARN: return Palette.AMBER;
            case INFO: return Palette.BLUE;
            default: return Palette.GREEN;
        }
    }
}
