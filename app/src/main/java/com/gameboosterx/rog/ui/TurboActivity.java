package com.gameboosterx.rog.ui;

import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.gameboosterx.rog.R;
import com.gameboosterx.rog.core.AppUsage;
import com.gameboosterx.rog.core.Diagnostics;
import com.gameboosterx.rog.core.Perms;
import com.gameboosterx.rog.core.Prefs;
import com.gameboosterx.rog.core.SecureTweaks;
import com.gameboosterx.rog.core.SystemControl;
import com.gameboosterx.rog.core.SystemStats;
import com.gameboosterx.rog.data.GameRepository;
import com.gameboosterx.rog.service.BoostService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * MODO TURBO X — preparação real do aparelho para jogar.
 *
 * O que ele EXECUTA (não apenas mostra):
 *  - fecha apps em segundo plano com o "Fechamento assistido" (o Android 14+ só permite
 *    fechar outros apps pela tela "Forçar parada"; o Turbo abre uma a uma e mede a RAM liberada);
 *  - ativa Não Perturbe e o monitor/Sidebar;
 *  - com WRITE_SECURE_SETTINGS (via LADB, sem root): fixa a tela na taxa máxima e acelera animações;
 *  - verifica, item a item, o que ainda está limitando o desempenho.
 * O que NENHUM app sem root consegue: aumentar CPU/GPU ou desbloquear FPS.
 */
public class TurboActivity extends BaseActivity {

    private static final String[] REALME_GAME_SPACE = {
            "com.oplus.games", "com.coloros.gamespace", "com.coloros.gamespaceui", "com.heytap.gamespace"
    };

    // Estado do fechamento assistido (estático para sobreviver à ida às Configurações)
    private static final List<String> closeQueue = new ArrayList<>();
    private static int closeIndex = -1;
    private static long ramBeforeClose;
    private static long lastCloseDoneAt;

    private TextView toggle, state, live;
    private LinearLayout content;
    private List<String> lastReport;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_turbo);
        setupHeader("TURBO X", "Preparação real para jogar");
        toggle = findViewById(R.id.btnTurboToggle);
        state = findViewById(R.id.txtTurboState);
        live = findViewById(R.id.txtTurboLive);
        content = findViewById(R.id.turboContent);
        toggle.setOnClickListener(v -> toggleTurbo());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (closeIndex >= 0) {
            continueAssistedClose();
            return;
        }
        render();
        ui.post(liveTick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(liveTick);
    }

    private final Runnable liveTick = new Runnable() {
        @Override
        public void run() {
            if (!resumed) return;
            SystemStats.RamInfo r = SystemStats.ram(TurboActivity.this);
            SystemStats.BatteryInfo bt = SystemStats.battery(TurboActivity.this);
            SystemStats.ThermalInfo t = SystemStats.thermal(TurboActivity.this);
            String temp = bt.hasTemp() ? String.format(Locale.US, "%.1f °C", bt.tempC) : "N/D";
            live.setText(String.format(Locale.US,
                    "RAM livre %s (%d%% em uso)  •  Bateria %s  •  %s\nEstado térmico: %s",
                    SystemStats.formatBytes(r.availBytes), r.usedPercent(),
                    bt.level >= 0 ? bt.level + "%" : "N/D", temp, SystemStats.thermalLabel(t.status)));
            ui.postDelayed(this, 2000);
        }
    };

    // =====================================================================
    // Ligar / desligar
    // =====================================================================
    private void toggleTurbo() {
        boolean on = !Prefs.turboActive(this);
        Prefs.setTurboActive(this, on);
        if (on) {
            try {
                if (!BoostService.LIVE.running) BoostService.start(this);
            } catch (Exception ignored) {
            }
            if (Perms.dnd(this)) {
                getSharedPreferences("gbx_settings", MODE_PRIVATE).edit()
                        .putInt("turbo_prev_dnd", SystemControl.getDndFilter(this)).apply();
                SystemControl.setDnd(this, true);
            }
            toggle.animate().scaleX(1.12f).scaleY(1.12f).setDuration(140)
                    .withEndAction(() -> toggle.animate().scaleX(1f).scaleY(1f).setDuration(160).start()).start();
            if (SecureTweaks.available(this)) {
                runBg(() -> {
                    List<String> rep = SecureTweaks.apply(this);
                    ui.post(() -> { lastReport = rep; render(); });
                });
            }
            List<AppUsage.App> bgApps = backgroundApps();
            if (!bgApps.isEmpty() && System.currentTimeMillis() - lastCloseDoneAt > 30 * 60_000L) {
                Toast.makeText(this, "TURBO X ativo. Agora feche os apps em segundo plano (item 1).",
                        Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "TURBO X ativo", Toast.LENGTH_SHORT).show();
            }
        } else {
            SecureTweaks.restore(this);
            int prev = getSharedPreferences("gbx_settings", MODE_PRIVATE)
                    .getInt("turbo_prev_dnd", NotificationManager.INTERRUPTION_FILTER_UNKNOWN);
            SystemControl.restoreDnd(this, prev);
            lastReport = null;
            Toast.makeText(this, "TURBO X desligado — ajustes restaurados", Toast.LENGTH_SHORT).show();
        }
        render();
    }

    private List<AppUsage.App> backgroundApps() {
        // Apps usados nas últimas 3 h que não são jogos nem do sistema
        return AppUsage.recent(this, 3 * 3600_000L, new GameRepository(this).selectedGames(), 12);
    }

    // =====================================================================
    // Fechamento assistido
    // =====================================================================
    private void startAssistedClose() {
        if (!Perms.usageStats(this)) {
            Toast.makeText(this, "Conceda o Acesso de Uso para o app saber quais apps estão abertos.", Toast.LENGTH_LONG).show();
            Perms.requestUsage(this);
            return;
        }
        List<AppUsage.App> apps = backgroundApps();
        if (apps.isEmpty()) {
            Toast.makeText(this, "Nenhum app em segundo plano para fechar.", Toast.LENGTH_SHORT).show();
            lastCloseDoneAt = System.currentTimeMillis();
            render();
            return;
        }
        closeQueue.clear();
        for (AppUsage.App a : apps) closeQueue.add(a.pkg);
        closeIndex = 0;
        ramBeforeClose = SystemStats.ram(this).availBytes;
        Toast.makeText(this, "Em cada tela: toque em \"Forçar parada\" ▸ OK e volte.", Toast.LENGTH_LONG).show();
        Perms.openAppDetails(this, closeQueue.get(0));
    }

    private void continueAssistedClose() {
        closeIndex++;
        if (closeIndex < closeQueue.size()) {
            String label = new GameRepository(this).label(closeQueue.get(closeIndex));
            Toast.makeText(this, "Fechando " + (closeIndex + 1) + "/" + closeQueue.size() + ": " + label,
                    Toast.LENGTH_SHORT).show();
            Perms.openAppDetails(this, closeQueue.get(closeIndex));
            return;
        }
        // Fim: mede quanto de RAM foi liberado
        int total = closeQueue.size();
        closeIndex = -1;
        closeQueue.clear();
        lastCloseDoneAt = System.currentTimeMillis();
        ui.postDelayed(() -> {
            long after = SystemStats.ram(this).availBytes;
            long diff = after - ramBeforeClose;
            String msg = String.format(Locale.US, "Fechamento concluído (%d apps). RAM livre: %s → %s (%s%s).",
                    total, SystemStats.formatBytes(ramBeforeClose), SystemStats.formatBytes(after),
                    diff >= 0 ? "+" : "−", SystemStats.formatBytes(Math.abs(diff)));
            lastReport = lastReport == null ? new ArrayList<>() : new ArrayList<>(lastReport);
            lastReport.add(0, "✓ " + msg);
            render();
            ui.post(liveTick);
        }, 1200);
        render();
    }

    // =====================================================================
    // Tela
    // =====================================================================
    private void render() {
        boolean on = Prefs.turboActive(this);
        toggle.setText(on ? "TURBO\nON" : "TURBO\nOFF");
        toggle.setSelected(on);
        state.setText(on ? "Ativo. Complete os itens marcados com ✗ para o máximo de desempenho possível."
                : "Toque para ativar e siga a lista abaixo.");

        content.removeAllViews();

        if (lastReport != null && !lastReport.isEmpty()) {
            addSection(content, "RESULTADO");
            addNote(content, String.join("\n", lastReport));
        }

        // ---------------- Checklist ----------------
        List<Item> items = buildChecklist();
        int ok = 0, total = 0;
        for (Item it : items) if (it.state != Item.INFO) { total++; if (it.state == Item.OK) ok++; }
        addSection(content, String.format(Locale.US, "PRONTO PARA JOGAR: %d/%d", ok, total));
        int n = 1;
        for (Item it : items) {
            String mark = it.state == Item.OK ? "✓ " : it.state == Item.BAD ? "✗ " : "ℹ ";
            addRow(content, n++ + ". " + mark + it.title, it.detail,
                    it.state == Item.OK ? null : it.action, it.state == Item.OK ? null : it.click);
        }

        // ---------------- Jogar ----------------
        GameRepository repo = new GameRepository(this);
        List<String> games = new ArrayList<>(repo.selectedGames());
        if (!games.isEmpty()) {
            addSection(content, "JOGAR AGORA");
            int shown = 0;
            for (String pkg : games) {
                if (!repo.isInstalled(pkg)) continue;
                addRow(content, repo.label(pkg), "Abre com o perfil do jogo e grava a sessão.", "Jogar",
                        v -> GameLauncher.launch(this, pkg));
                if (++shown >= 6) break;
            }
        }

        // ---------------- Expectativa honesta ----------------
        addSection(content, "O QUE ESPERAR");
        addNote(content, "O Turbo X remove o que atrapalha (apps abertos, economia de bateria, tela em 60 Hz, notificações, aquecimento). "
                + "Se o jogo continua travando com tudo ✓, o limite é o processador do aparelho para aquelas configurações: "
                + "o ganho real vem de baixar gráficos/FPS dentro do jogo (veja o perfil do jogo em Meus Jogos). "
                + "Nenhum app sem root aumenta a potência da CPU/GPU — apps que prometem isso só exibem animações.");
    }

    private static final class Item {
        static final int OK = 0, BAD = 1, INFO = 2;
        int state;
        String title, detail, action;
        android.view.View.OnClickListener click;

        Item(int state, String title, String detail, String action, android.view.View.OnClickListener click) {
            this.state = state;
            this.title = title;
            this.detail = detail;
            this.action = action;
            this.click = click;
        }
    }

    private List<Item> buildChecklist() {
        List<Item> l = new ArrayList<>();
        Context c = this;
        SystemStats.RamInfo ram = SystemStats.ram(c);
        SystemStats.BatteryInfo bat = SystemStats.battery(c);
        SystemStats.ThermalInfo th = SystemStats.thermal(c);
        SystemStats.StorageInfo st = SystemStats.storage();
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);

        // 1. Apps em segundo plano
        boolean recentlyClosed = System.currentTimeMillis() - lastCloseDoneAt < 30 * 60_000L;
        if (!Perms.usageStats(c)) {
            l.add(new Item(Item.BAD, "Fechar apps em segundo plano",
                    "Conceda o Acesso de Uso para o Turbo encontrar os apps abertos.", "Conceder",
                    v -> Perms.requestUsage(this)));
        } else {
            List<AppUsage.App> bg = backgroundApps();
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < bg.size() && i < 5; i++) names.append(i == 0 ? "" : ", ").append(bg.get(i).label);
            if (bg.size() > 5) names.append("…");
            boolean good = bg.isEmpty() || recentlyClosed;
            l.add(new Item(good ? Item.OK : Item.BAD, "Fechar apps em segundo plano",
                    good ? "Feito." : bg.size() + " apps usados recentemente podem estar ocupando RAM e CPU: " + names
                            + ". O Turbo abre cada um: toque em Forçar parada e volte.",
                    "Fechar", v -> startAssistedClose()));
        }

        // 2. Economia de bateria
        boolean saver = pm != null && pm.isPowerSaveMode();
        l.add(new Item(saver ? Item.BAD : Item.OK, "Economia de bateria desligada",
                saver ? "Ela reduz CPU, GPU e a taxa da tela. Desligue antes de jogar." : "OK.",
                "Abrir", v -> Perms.openSetting(this, Settings.ACTION_BATTERY_SAVER_SETTINGS)));

        // 3. RAM
        int freePct = ram.totalBytes > 0 ? (int) (ram.availBytes * 100 / ram.totalBytes) : 100;
        l.add(new Item(freePct >= 25 && !ram.lowMemory ? Item.OK : Item.BAD,
                "RAM livre: " + SystemStats.formatBytes(ram.availBytes) + " (" + freePct + "%)",
                freePct >= 25 ? "Suficiente." : "Pouca memória livre causa travadas quando o jogo carrega mapas/texturas. Feche apps (item 1).",
                "Fechar apps", v -> startAssistedClose()));

        // 4. Temperatura
        int heat = SystemStats.heatLevel(bat, th, Prefs.tempWarn(c));
        l.add(new Item(heat < 2 ? Item.OK : Item.BAD, "Temperatura " + (bat.hasTemp()
                ? String.format(Locale.US, "%.1f °C", bat.tempC) : "N/D") + " • " + SystemStats.thermalLabel(th.status),
                heat < 2 ? "Sem limitação térmica." : "Aparelho quente: o sistema reduz o desempenho para se proteger. "
                        + "Deixe esfriar alguns minutos, sem capa e fora do carregador.",
                "Detalhes", v -> startActivity(new Intent(this, ThermalActivity.class))));

        // 5. Tela na taxa máxima
        float[] rr = Diagnostics.refreshRates(c);
        boolean tweaks = SecureTweaks.available(c) && SecureTweaks.isApplied(c);
        if (rr[1] > 61) {
            l.add(new Item(tweaks ? Item.OK : Item.INFO,
                    String.format(Locale.US, "Tela em %.0f Hz", rr[1]),
                    tweaks ? "Fixada no máximo pelo Turbo." : "Em Tela ▸ Taxa de atualização escolha a opção mais alta. "
                            + "Com o Turbo avançado (item 8) o app fixa isso sozinho.",
                    "Abrir", v -> Perms.openSetting(this, Settings.ACTION_DISPLAY_SETTINGS)));
        }

        // 6. Não perturbe
        int f = SystemControl.getDndFilter(c);
        boolean dndOn = f != NotificationManager.INTERRUPTION_FILTER_ALL && f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN;
        l.add(new Item(dndOn ? Item.OK : Item.BAD, "Não Perturbe",
                dndOn ? "Ativo." : "Notificações durante a partida causam engasgos e toques perdidos.",
                Perms.dnd(c) ? "Ativar" : "Autorizar", v -> {
                    if (!Perms.dnd(this)) Perms.requestDnd(this);
                    else { SystemControl.setDnd(this, true); render(); }
                }));

        // 7. Armazenamento
        int freeSt = st.totalBytes > 0 ? (int) (st.freeBytes * 100 / st.totalBytes) : 100;
        l.add(new Item(freeSt >= 10 ? Item.OK : Item.BAD, "Armazenamento livre: " + freeSt + "%",
                freeSt >= 10 ? "OK." : "Menos de 10% livre deixa o sistema lento.",
                "Limpar", v -> startActivity(new Intent(this, StorageActivity.class))));

        // 8. Turbo avançado (ADB)
        if (SecureTweaks.available(c)) {
            l.add(new Item(tweaks ? Item.OK : Item.BAD, "Turbo avançado",
                    tweaks ? "Tela fixa no máximo e animações aceleradas (restaurado ao desligar)."
                            : "Permissão concedida. Ligue o Turbo para aplicar.",
                    "Aplicar", v -> runBg(() -> {
                        List<String> rep = SecureTweaks.apply(this);
                        ui.post(() -> { lastReport = rep; render(); });
                    })));
        } else {
            l.add(new Item(Item.INFO, "Turbo avançado (opcional, sem root)",
                    "Com o Shizuku (grátis) o app libera sozinho: tela travada na taxa máxima, animações rápidas e FPS real.",
                    "Liberar", v -> com.gameboosterx.rog.core.ShizukuHelper.unlock(this, this::render)));
        }

        // 9. Espaço de Jogos da realme (modo de desempenho do fabricante)
        String gs = findGameSpace();
        if (gs != null) {
            l.add(new Item(Item.INFO, "Espaço de Jogos da realme",
                    "É o único lugar com modo de desempenho do próprio fabricante. Adicione o jogo e ative o modo "
                            + "de alto desempenho/competitivo.",
                    "Abrir", v -> {
                        Intent i = getPackageManager().getLaunchIntentForPackage(gs);
                        if (i != null) Perms.open(this, i);
                    }));
        }

        // 10. Alto desempenho na bateria
        l.add(new Item(Item.INFO, "Modo de alto desempenho (Bateria)",
                "No realme UI: Configurações ▸ Bateria ▸ Mais configurações ▸ Modo de alto desempenho (o nome pode variar). "
                        + "Gasta mais bateria e aquece mais.",
                "Abrir", v -> Perms.open(this, new Intent(Intent.ACTION_POWER_USAGE_SUMMARY))));

        // 11. Monitor
        l.add(new Item(BoostService.LIVE.running ? Item.OK : Item.BAD, "Monitor e Game Sidebar",
                BoostService.LIVE.running ? "Ativo: grava a sessão e alerta aquecimento." : "Necessário para perfis e sessões.",
                "Ligar", v -> {
                    if (!Perms.overlay(this)) Perms.requestOverlay(this);
                    else { BoostService.start(this); ui.postDelayed(this::render, 400); }
                }));
        return l;
    }

    private String findGameSpace() {
        for (String p : REALME_GAME_SPACE) {
            if (getPackageManager().getLaunchIntentForPackage(p) != null) return p;
        }
        return null;
    }
}
