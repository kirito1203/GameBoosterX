package com.gameboosterx.rog.ui;

import android.app.AlertDialog;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Process;
import android.os.storage.StorageManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.widget.CheckBox;
import android.widget.LinearLayout;

import com.gameboosterx.rog.R;
import com.gameboosterx.rog.core.Perms;
import com.gameboosterx.rog.core.SystemStats;
import com.gameboosterx.rog.data.GameRepository;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * GAME STORAGE MANAGER.
 * Limites do Android 16: apps não podem limpar o cache de outros apps nem acessar
 * Android/data e Android/obb. O módulo:
 *  - mostra espaço livre e tamanho dos jogos (StorageStatsManager);
 *  - localiza arquivos grandes e temporários no armazenamento compartilhado
 *    (requer "Acesso a todos os arquivos", opcional);
 *  - apaga SOMENTE o que o usuário marcar, após confirmação;
 *  - nunca toca em pastas de jogos ou em Android/.
 */
public class StorageActivity extends BaseActivity {

    private static final long LARGE = 100L * 1024 * 1024;
    private static final String[] TEMP_EXT = {".tmp", ".temp", ".crdownload", ".part", ".partial"};
    private static final String[] PROTECTED_HINTS = {
            "com.mojang", "roblox", "mihoyo", "genshin", "activision", "callofduty", "codm", "funkin", "/games/"
    };

    private LinearLayout content;
    private final List<File> large = new ArrayList<>();
    private final List<File> temps = new ArrayList<>();
    private final List<CheckBox> tempChecks = new ArrayList<>();
    private boolean scanned;
    private Set<String> gamePkgs;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_module);
        setupHeader("STORAGE MANAGER", "Armazenamento dos jogos");
        content = findViewById(R.id.content);
        gamePkgs = new GameRepository(this).selectedGames();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        content.removeAllViews();
        tempChecks.clear();
        SystemStats.StorageInfo s = SystemStats.storage();

        addSection(content, "ESPAÇO");
        addNote(content, String.format(Locale.US, "%s livres de %s (%d%% em uso)%s",
                SystemStats.formatBytes(s.freeBytes), SystemStats.formatBytes(s.totalBytes), s.usedPercent(),
                s.totalBytes > 0 && s.freeBytes < s.totalBytes * 0.1 ? "\n⚠ Menos de 10% livre: libere espaço para evitar lentidão." : ""));
        addRow(content, "Liberar espaço (ferramenta do sistema)", "Ferramenta oficial do Android para limpar arquivos.",
                "Abrir", v -> Perms.open(this, new android.content.Intent(StorageManager.ACTION_MANAGE_STORAGE)));
        addRow(content, "Configurações de armazenamento", null, "Abrir",
                v -> Perms.openSetting(this, Settings.ACTION_INTERNAL_STORAGE_SETTINGS));

        addSection(content, "CACHE DO GAME BOOSTER X");
        long own = size(getCacheDir()) + size(getExternalCacheDir());
        addRow(content, SystemStats.formatBytes(own), "Cache do próprio app — pode ser limpo com segurança.",
                "Limpar", v -> confirm("Limpar cache do Game Booster X?", () -> {
                    delete(getCacheDir(), false);
                    delete(getExternalCacheDir(), false);
                    render();
                }));

        addSection(content, "TAMANHO DOS JOGOS");
        if (!Perms.usageStats(this)) {
            addRow(content, "Acesso de Uso necessário", "Permite consultar o tamanho de cada jogo.", "Conceder",
                    v -> Perms.requestUsage(this));
        } else {
            gameSizes();
        }

        addSection(content, "ARQUIVOS GRANDES E TEMPORÁRIOS");
        if (!Perms.allFiles()) {
            addRow(content, "Acesso a todos os arquivos (opcional)",
                    "Necessário para procurar arquivos grandes e temporários na memória interna. Pastas de jogos e Android/ nunca são alteradas.",
                    "Conceder", v -> Perms.requestAllFiles(this));
            return;
        }
        if (!scanned) {
            addRow(content, "Analisar armazenamento", "Procura arquivos ≥ 100 MB e temporários (.tmp, downloads incompletos, miniaturas).",
                    "Analisar", v -> scan());
            return;
        }
        renderResults();
    }

    private void gameSizes() {
        StorageStatsManager ssm = (StorageStatsManager) getSystemService(STORAGE_STATS_SERVICE);
        GameRepository repo = new GameRepository(this);
        int n = 0;
        for (String pkg : gamePkgs) {
            if (!repo.isInstalled(pkg)) continue;
            try {
                StorageStats st = ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, pkg, Process.myUserHandle());
                addRow(content, repo.label(pkg) + "  •  " + SystemStats.formatBytes(st.getAppBytes() + st.getDataBytes()),
                        "App " + SystemStats.formatBytes(st.getAppBytes()) + " • dados " + SystemStats.formatBytes(st.getDataBytes())
                                + " • cache " + SystemStats.formatBytes(st.getCacheBytes()),
                        "Gerenciar", v -> Perms.openAppDetails(this, pkg));
                n++;
            } catch (Exception ignored) {
            }
        }
        if (n == 0) addNote(content, "Nenhum jogo selecionado em Meus Jogos.");
        else addNote(content, "Em \"Gerenciar ▸ Armazenamento\" você pode limpar o cache de um jogo. "
                + "NÃO use \"Limpar dados\": isso apaga progresso e downloads do jogo.");
    }

    private void scan() {
        content.removeAllViews();
        addNote(content, "Analisando… isso pode levar alguns segundos.");
        runBg(() -> {
            large.clear();
            temps.clear();
            File root = Environment.getExternalStorageDirectory();
            walk(root, 0);
            large.sort((a, c) -> Long.compare(c.length(), a.length()));
            while (large.size() > 30) large.remove(large.size() - 1);
            ui.post(() -> {
                scanned = true;
                render();
            });
        });
    }

    private boolean isProtected(File f) {
        String p = f.getAbsolutePath().toLowerCase(Locale.ROOT);
        String root = Environment.getExternalStorageDirectory().getAbsolutePath().toLowerCase(Locale.ROOT);
        if (p.startsWith(root + "/android")) return true; // Android/data, obb, media
        for (String h : PROTECTED_HINTS) if (p.contains(h)) return true;
        for (String pkg : gamePkgs) if (p.contains(pkg.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    private void walk(File dir, int depth) {
        if (depth > 8 || isProtected(dir)) return;
        File[] kids = dir.listFiles();
        if (kids == null) return;
        boolean thumbDir = dir.getName().equalsIgnoreCase(".thumbnails") || dir.getName().equalsIgnoreCase("LOST.DIR");
        for (File k : kids) {
            if (isProtected(k)) continue;
            if (k.isDirectory()) {
                walk(k, depth + 1);
            } else {
                long len = k.length();
                String name = k.getName().toLowerCase(Locale.ROOT);
                boolean temp = thumbDir;
                for (String e : TEMP_EXT) if (name.endsWith(e)) { temp = true; break; }
                if (temp) temps.add(k);
                else if (len >= LARGE) large.add(k);
            }
        }
    }

    private void renderResults() {
        long tempTotal = 0;
        for (File f : temps) tempTotal += f.length();
        addNote(content, String.format(Locale.US, "Temporários: %d arquivos (%s)  •  Grandes: %d arquivos",
                temps.size(), SystemStats.formatBytes(tempTotal), large.size()));

        addSection(content, "TEMPORÁRIOS (MARQUE O QUE APAGAR)");
        if (temps.isEmpty()) addNote(content, "Nenhum arquivo temporário encontrado.");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int shown = 0;
        for (File f : temps) {
            if (shown++ >= 200) break;
            CheckBox cb = (CheckBox) LayoutInflater.from(this).inflate(R.layout.item_check, box, false);
            cb.setText(shortPath(f) + "  (" + SystemStats.formatBytes(f.length()) + ")");
            cb.setChecked(true);
            cb.setTag(f);
            tempChecks.add(cb);
            box.addView(cb);
        }
        content.addView(box);
        if (!temps.isEmpty()) {
            addRow(content, "Apagar temporários marcados", "Será pedida confirmação.", "Apagar", v -> deleteSelectedTemps());
        }

        addSection(content, "ARQUIVOS GRANDES (≥ 100 MB)");
        if (large.isEmpty()) addNote(content, "Nenhum arquivo grande fora das pastas protegidas.");
        for (File f : large) {
            addRow(content, f.getName() + "  •  " + SystemStats.formatBytes(f.length()), shortPath(f.getParentFile()),
                    "Apagar", v -> confirm("Apagar \"" + f.getName() + "\" (" + SystemStats.formatBytes(f.length())
                            + ")?\nO arquivo não poderá ser recuperado.", () -> {
                        if (f.delete()) large.remove(f);
                        render();
                    }));
        }
        addRow(content, "Analisar novamente", null, "Analisar", v -> scan());
    }

    private void deleteSelectedTemps() {
        List<File> sel = new ArrayList<>();
        long total = 0;
        for (CheckBox cb : tempChecks) {
            if (cb.isChecked() && cb.getTag() instanceof File) {
                File f = (File) cb.getTag();
                sel.add(f);
                total += f.length();
            }
        }
        if (sel.isEmpty()) return;
        confirm(String.format(Locale.US, "Apagar %d arquivo(s) temporário(s) (%s)?", sel.size(), SystemStats.formatBytes(total)), () -> {
            int ok = 0;
            for (File f : sel) {
                if (!isProtected(f) && f.delete()) { temps.remove(f); ok++; }
            }
            android.widget.Toast.makeText(this, ok + " arquivo(s) apagado(s)", android.widget.Toast.LENGTH_SHORT).show();
            render();
        });
    }

    private String shortPath(File f) {
        if (f == null) return "";
        String root = Environment.getExternalStorageDirectory().getAbsolutePath();
        String p = f.getAbsolutePath();
        return p.startsWith(root) ? "Memória interna" + p.substring(root.length()) : p;
    }

    private void confirm(String msg, Runnable onYes) {
        new AlertDialog.Builder(this)
                .setTitle("Confirmar")
                .setMessage(msg)
                .setPositiveButton("Apagar", (d, w) -> onYes.run())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private static long size(File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isFile()) return f.length();
        long t = 0;
        File[] k = f.listFiles();
        if (k != null) for (File x : k) t += size(x);
        return t;
    }

    private static void delete(File f, boolean self) {
        if (f == null || !f.exists()) return;
        File[] k = f.listFiles();
        if (k != null) for (File x : k) delete(x, true);
        if (self) f.delete();
    }
}
