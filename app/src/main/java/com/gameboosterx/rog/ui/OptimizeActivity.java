package com.gameboosterx.rog.ui;

import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gameboosterx.rog.R;
import com.gameboosterx.rog.core.Perms;
import com.gameboosterx.rog.core.SystemStats;
import com.gameboosterx.rog.data.GameRepository;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * MODO OTIMIZAÇÃO INTELIGENTE.
 * Limitação do Android 14+: apps comuns NÃO podem encerrar processos de outros apps
 * (killBackgroundProcesses só afeta o próprio app). Por isso o app identifica quem
 * consome recursos e leva o usuário direto à tela "Forçar parada / Restringir bateria"
 * daquele app — o caminho permitido pelo sistema. Processos do sistema são ocultados.
 */
public class OptimizeActivity extends BaseActivity {

    private LinearLayout content;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_module);
        setupHeader("OTIMIZAR", "Otimização inteligente");
        content = findViewById(R.id.content);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render(null);
    }

    private void render(String lastResult) {
        content.removeAllViews();
        SystemStats.RamInfo r = SystemStats.ram(this);
        SystemStats.StorageInfo s = SystemStats.storage();

        addSection(content, "RESUMO");
        addNote(content, String.format(Locale.US,
                "RAM: %s livres de %s (%d%% em uso)%s\nArmazenamento: %s livres de %s (%d%% em uso)",
                SystemStats.formatBytes(r.availBytes), SystemStats.formatBytes(r.totalBytes), r.usedPercent(),
                r.lowMemory ? "  ⚠ memória baixa" : "",
                SystemStats.formatBytes(s.freeBytes), SystemStats.formatBytes(s.totalBytes), s.usedPercent()));

        addOptimizeButton();
        if (lastResult != null) addNote(content, lastResult);

        addSection(content, "APPS QUE MAIS USARAM RECURSOS (24 H)");
        if (!Perms.usageStats(this)) {
            addRow(content, "Acesso de Uso necessário",
                    "Sem ele o Android não informa quais apps estão ativos. Nenhum dado é enviado para fora do aparelho.",
                    "Conceder", v -> Perms.requestUsage(this));
        } else {
            TextView loading = addNote(content, "Analisando…");
            runBg(() -> {
                List<String[]> rows = heavyApps();
                ui.post(() -> {
                    int idx = content.indexOfChild(loading);
                    content.removeView(loading);
                    if (rows.isEmpty()) {
                        addNote(content, "Nenhum app de terceiros com uso relevante nas últimas 24 h.");
                    }
                    LinearLayout tmp = new LinearLayout(this);
                    tmp.setOrientation(LinearLayout.VERTICAL);
                    for (String[] row : rows) {
                        final String pkg = row[0];
                        addRow(tmp, row[1], row[2], "Gerenciar", v -> Perms.openAppDetails(this, pkg));
                    }
                    content.addView(tmp, Math.max(0, Math.min(idx, content.getChildCount())));
                });
            });
            addNote(content, "Em \"Gerenciar\" use \"Forçar parada\" para fechar o app ou \"Uso da bateria ▸ Restringir\" "
                    + "para impedir atividade em segundo plano. Não force a parada de apps que você precisa receber notificações.");
        }

        addSection(content, "ARMAZENAMENTO E TEMPORÁRIOS");
        addRow(content, "Game Storage Manager", "Arquivos grandes e temporários com limpeza mediante confirmação.",
                "Abrir", v -> startActivity(new Intent(this, StorageActivity.class)));

        addSection(content, "RECOMENDAÇÕES");
        List<String> recs = new ArrayList<>();
        if (r.lowMemory || r.availBytes < r.totalBytes * 0.2) {
            recs.add("Pouca RAM livre: feche apps na tela de recentes antes de jogar.");
        }
        if (s.totalBytes > 0 && s.freeBytes < s.totalBytes * 0.15) {
            recs.add("Libere espaço: mantenha pelo menos 15% do armazenamento livre.");
        }
        recs.add("Desinstale ou desative apps que você não usa e que rodam em segundo plano (redes sociais costumam ser os maiores).");
        recs.add("Restringir a bateria de apps pesados evita que eles acordem durante as partidas.");
        recs.add("Reinicie o aparelho a cada poucos dias: libera memória de forma segura e completa.");
        StringBuilder sb = new StringBuilder();
        for (String x : recs) sb.append("• ").append(x).append('\n');
        addNote(content, sb.toString().trim());
    }

    private void addOptimizeButton() {
        addRow(content, "⚡ OTIMIZAR AGORA",
                "Limpa o cache do Game Booster X e mede a RAM antes/depois. Não encerra processos do Android.",
                "Executar", v -> runBg(() -> {
                    SystemStats.RamInfo before = SystemStats.ram(this);
                    long freed = deleteRecursive(getCacheDir(), false);
                    File ext = getExternalCacheDir();
                    if (ext != null) freed += deleteRecursive(ext, false);
                    System.gc();
                    try { Thread.sleep(600); } catch (InterruptedException ignored) { }
                    SystemStats.RamInfo after = SystemStats.ram(this);
                    long diff = after.availBytes - before.availBytes;
                    String res = String.format(Locale.US,
                            "Resultado: cache do app limpo (%s). RAM livre: %s → %s (%s%s).\n"
                                    + "Para liberar mais, use \"Gerenciar\" nos apps listados abaixo.",
                            SystemStats.formatBytes(freed),
                            SystemStats.formatBytes(before.availBytes), SystemStats.formatBytes(after.availBytes),
                            diff >= 0 ? "+" : "-", SystemStats.formatBytes(Math.abs(diff)));
                    ui.post(() -> render(res));
                }));
    }

    private static long deleteRecursive(File f, boolean deleteSelf) {
        long total = 0;
        if (f == null || !f.exists()) return 0;
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) {
                if (k.isDirectory()) total += deleteRecursive(k, true);
                else {
                    long len = k.length();
                    if (k.delete()) total += len;
                }
            }
        }
        if (deleteSelf) f.delete();
        return total;
    }

    /** [pkg, título, descrição] dos apps de terceiros com mais tempo de uso nas últimas 24 h. */
    private List<String[]> heavyApps() {
        List<String[]> out = new ArrayList<>();
        try {
            UsageStatsManager usm = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
            long now = System.currentTimeMillis();
            Map<String, UsageStats> map = usm.queryAndAggregateUsageStats(now - 24 * 3600_000L, now);
            PackageManager pm = getPackageManager();
            String launcher = defaultLauncher();
            Set<String> gamesSel = new GameRepository(this).selectedGames();
            List<UsageStats> list = new ArrayList<>(map.values());
            list.sort((a, c) -> Long.compare(c.getTotalTimeInForeground(), a.getTotalTimeInForeground()));
            for (UsageStats us : list) {
                if (out.size() >= 15) break;
                String pkg = us.getPackageName();
                if (us.getTotalTimeInForeground() < 60_000) continue;
                if (isEssential(pm, pkg, launcher)) continue;
                String label;
                try {
                    label = String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
                } catch (Exception e) {
                    continue; // desinstalado ou não visível
                }
                long ago = (now - us.getLastTimeUsed()) / 60_000;
                String agoTxt = ago < 1 ? "agora" : (ago < 60 ? "há " + ago + " min" : "há " + (ago / 60) + " h");
                String fg = SystemStats.formatDuration(us.getTotalTimeInForeground());
                String extra = ago <= 30 ? " • provavelmente ainda na memória" : "";
                String tag = gamesSel.contains(pkg) ? "🎮 " : "";
                out.add(new String[]{pkg, tag + label, "Em uso por " + fg + " • último uso " + agoTxt + extra});
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private String defaultLauncher() {
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo ri = getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            return ri != null ? ri.activityInfo.packageName : "";
        } catch (Exception e) {
            return "";
        }
    }

    /** Evita listar componentes essenciais do Android/fabricante. */
    private boolean isEssential(PackageManager pm, String pkg, String launcher) {
        if (pkg.equals(getPackageName()) || pkg.equals(launcher)) return true;
        String p = pkg.toLowerCase(Locale.ROOT);
        if (p.equals("android") || p.startsWith("com.android.") || p.contains("systemui")
                || p.startsWith("com.google.android.gms") || p.contains("inputmethod")
                || p.startsWith("com.oplus.") || p.startsWith("com.coloros.") || p.startsWith("com.heytap.")
                || p.startsWith("com.realme.")) return true;
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            boolean system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            boolean updated = (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
            return system && !updated;
        } catch (Exception e) {
            return true;
        }
    }
}
