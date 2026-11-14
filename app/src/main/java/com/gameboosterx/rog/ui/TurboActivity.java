package com.gameboosterx.rog.ui;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.gameboosterx.rog.R;
import com.gameboosterx.rog.core.Diagnostics;
import com.gameboosterx.rog.core.Perms;
import com.gameboosterx.rog.core.Prefs;
import com.gameboosterx.rog.core.SystemStats;
import com.gameboosterx.rog.data.GameRepository;
import com.gameboosterx.rog.service.BoostService;

import java.util.List;
import java.util.Locale;

/**
 * MODO TURBO X — perfil de desempenho dentro do que o Android permite sem root:
 *  - liga o monitor (sessões, alertas térmicos antecipados);
 *  - ativa Não Perturbe automaticamente durante os jogos;
 *  - identifica limitações reais (economia de bateria, térmico, RAM, armazenamento...);
 *  - dá atalhos às configurações do sistema que realmente afetam o desempenho.
 * O app NÃO altera frequência de CPU/GPU: isso exige root ou permissões de sistema.
 */
public class TurboActivity extends BaseActivity {

    private static final String[] REALME_GAME_SPACE = {
            "com.oplus.games", "com.coloros.gamespace", "com.coloros.gamespaceui", "com.heytap.gamespace"
    };

    private TextView toggle, state, live;
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_turbo);
        setupHeader("TURBO X", "Perfil de desempenho");
        toggle = findViewById(R.id.btnTurboToggle);
        state = findViewById(R.id.txtTurboState);
        live = findViewById(R.id.txtTurboLive);
        content = findViewById(R.id.turboContent);
        toggle.setOnClickListener(v -> toggleTurbo());
    }

    @Override
    protected void onResume() {
        super.onResume();
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
                    "RAM %d%% (%s livres)   •   Bateria %s   •   Temp. bateria %s\nEstado térmico: %s%s",
                    r.usedPercent(), SystemStats.formatBytes(r.availBytes),
                    bt.level >= 0 ? bt.level + "%" : "N/D", temp,
                    SystemStats.thermalLabel(t.status),
                    Float.isNaN(t.headroom) ? "" : String.format(Locale.US, "  •  margem térmica %.0f%%",
                            Math.max(0, (1f - t.headroom) * 100f))));
            ui.postDelayed(this, 2000);
        }
    };

    private void toggleTurbo() {
        boolean on = !Prefs.turboActive(this);
        Prefs.setTurboActive(this, on);
        if (on) {
            try {
                if (!BoostService.LIVE.running) BoostService.start(this);
            } catch (Exception e) {
                Toast.makeText(this, "Não foi possível iniciar o monitor.", Toast.LENGTH_SHORT).show();
            }
            toggle.animate().scaleX(1.12f).scaleY(1.12f).setDuration(140)
                    .withEndAction(() -> toggle.animate().scaleX(1f).scaleY(1f).setDuration(160).start()).start();
            Toast.makeText(this, "TURBO X ativado", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "TURBO X desativado", Toast.LENGTH_SHORT).show();
        }
        render();
    }

    private void render() {
        boolean on = Prefs.turboActive(this);
        toggle.setText(on ? "TURBO\nON" : "TURBO\nOFF");
        toggle.setSelected(on);
        state.setText(on
                ? "Ativo: monitor ligado, Não Perturbe automático nos jogos e alertas térmicos antecipados."
                : "Toque para ativar o perfil de desempenho.");

        content.removeAllViews();

        // ---- Limitações detectadas ----
        addSection(content, "LIMITAÇÕES DETECTADAS");
        List<Diagnostics.Issue> issues = Diagnostics.run(this);
        if (issues.isEmpty()) {
            addNote(content, "✓ Nenhuma limitação detectada agora. O aparelho está pronto para jogar.");
        }
        for (Diagnostics.Issue is : issues) {
            String icon = is.severity >= Diagnostics.CRIT ? "⛔ " : (is.severity == Diagnostics.WARN ? "⚠ " : "ℹ ");
            final Intent act = is.action;
            addRow(content, icon + is.title, is.detail, is.actionLabel,
                    act == null ? null : v -> Perms.open(this, act));
        }

        // ---- Ações rápidas ----
        addSection(content, "AÇÕES RÁPIDAS");
        String gs = findGameSpace();
        if (gs != null) {
            addRow(content, "Espaço de Jogos do sistema",
                    "Ferramenta da realme que pode oferecer modos de desempenho do próprio fabricante.",
                    "Abrir", v -> {
                        Intent i = getPackageManager().getLaunchIntentForPackage(gs);
                        if (i != null) Perms.open(this, i);
                    });
        }
        addRow(content, "Configurações de tela", "Taxa de atualização, brilho e modo escuro.",
                "Abrir", v -> Perms.openSetting(this, Settings.ACTION_DISPLAY_SETTINGS));
        addRow(content, "Bateria e desempenho",
                "Algumas versões do realme UI têm opção de alto desempenho nesta tela.",
                "Abrir", v -> Perms.open(this, new Intent(Intent.ACTION_POWER_USAGE_SUMMARY)));
        addRow(content, "Não Perturbe automático",
                Perms.dnd(this) ? "Autorizado ✓" : "Precisa de autorização para silenciar notificações durante os jogos.",
                Perms.dnd(this) ? null : "Autorizar", v -> Perms.requestDnd(this));
        addRow(content, "Game Sidebar",
                BoostService.LIVE.running ? "Ativa ✓" : "Barra flutuante com monitores durante o jogo.",
                BoostService.LIVE.running ? null : "Ligar", v -> {
                    if (!Perms.overlay(this)) Perms.requestOverlay(this);
                    else { BoostService.start(this); render(); }
                });
        addRow(content, "Otimização inteligente", "Analisar apps em segundo plano e armazenamento.",
                "Abrir", v -> startActivity(new Intent(this, OptimizeActivity.class)));

        // ---- Recomendações ----
        addSection(content, "RECOMENDAÇÕES PARA FLUIDEZ");
        addNote(content, "• Limite o FPS dentro do jogo a um valor que ele consiga manter (30 ou 60). FPS estável parece mais fluido que FPS alto oscilando.\n"
                + "• Feche apps pela tela de recentes antes de jogos pesados.\n"
                + "• Jogue sem carregador e sem capa em sessões longas.\n"
                + "• Prefira Wi-Fi 5 GHz em jogos online.\n"
                + "• Mantenha pelo menos 15% do armazenamento livre.");

        GameRepository repo = new GameRepository(this);
        if (repo.selectedGames().isEmpty()) {
            addRow(content, "Nenhum jogo selecionado", "Selecione seus jogos para carregar perfis automaticamente.",
                    "Jogos", v -> startActivity(new Intent(this, GamesActivity.class)));
        }

        addSection(content, "O QUE O TURBO X NÃO FAZ");
        addNote(content, "Sem root, nenhum app pode alterar frequência de CPU/GPU, desbloquear FPS ou desativar proteções térmicas. "
                + "O Turbo X atua apenas no que o Android permite e mostra o que está limitando o desempenho.");
    }

    private String findGameSpace() {
        for (String p : REALME_GAME_SPACE) {
            if (getPackageManager().getLaunchIntentForPackage(p) != null) return p;
        }
        return null;
    }
}
