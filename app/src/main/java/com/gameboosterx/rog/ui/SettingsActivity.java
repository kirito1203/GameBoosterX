package com.gameboosterx.rog.ui;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;

import com.gameboosterx.rog.R;
import com.gameboosterx.rog.core.Perms;
import com.gameboosterx.rog.core.Prefs;

import java.util.Locale;

/** Central de permissões, preferências e limitações do sistema. */
public class SettingsActivity extends BaseActivity {

    private LinearLayout content;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_module);
        setupHeader("CONFIGURAÇÕES", "Permissões e preferências");
        content = findViewById(R.id.content);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        render();
    }

    private static String st(boolean ok) { return ok ? "✓ Concedida" : "✗ Não concedida"; }

    private void render() {
        content.removeAllViews();

        addSection(content, "PERMISSÕES");
        perm("Sobrepor a outros apps", "Necessária para a Game Sidebar.", Perms.overlay(this),
                () -> Perms.requestOverlay(this));
        perm("Acesso de Uso", "Detecta o jogo aberto, aplica o perfil e grava a sessão; lista apps ativos.",
                Perms.usageStats(this), () -> Perms.requestUsage(this));
        perm("Notificações", "Mostra o monitor ativo e alertas de temperatura.", Perms.notifications(this),
                () -> Perms.requestNotifications(this));
        perm("Modificar configurações do sistema", "Controle de brilho na barra e nos perfis.",
                Perms.writeSettings(this), () -> Perms.requestWriteSettings(this));
        perm("Acesso ao Não Perturbe", "Silencia notificações durante os jogos.", Perms.dnd(this),
                () -> Perms.requestDnd(this));
        perm("Acesso a todos os arquivos (opcional)", "Localiza arquivos grandes/temporários.", Perms.allFiles(),
                () -> Perms.requestAllFiles(this));
        addRow(content, "Medição de FPS (opcional, via ADB) — " + st(Perms.dump(this)),
                "Permissão DUMP concedida por comando. Veja as instruções no FPS Stabilizer.",
                "Ver", v -> startActivity(new Intent(this, FpsActivity.class)));
        addRow(content, "Atividade em segundo plano",
                "Recomendado: deixe o Game Booster X sem restrição de bateria para a barra não ser fechada pelo sistema.",
                "Ajustar", v -> Perms.openSetting(this, Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));

        addSection(content, "MONITOR");
        float warn = Prefs.tempWarn(this);
        addRow(content, String.format(Locale.US, "Alerta de temperatura: %.0f °C", warn),
                "Temperatura da bateria considerada quente (padrão 41 °C). Toque para alternar 38–44 °C.",
                "Alterar", v -> {
                    float n = warn + 1;
                    if (n > 44) n = 38;
                    Prefs.setTempWarn(this, n);
                    render();
                });
        int sec = Prefs.sampleSeconds(this);
        addRow(content, "Intervalo de amostragem: " + sec + " s",
                "Menor = gráficos mais detalhados, consumo levemente maior.",
                "Alterar", v -> {
                    int n = sec == 1 ? 2 : sec == 2 ? 3 : sec == 3 ? 5 : 1;
                    Prefs.setSampleSeconds(this, n);
                    render();
                });
        boolean auto = Prefs.autoDetect(this);
        addRow(content, "Detecção automática de jogos: " + (auto ? "ligada" : "desligada"),
                "Aplica perfil e grava sessão ao abrir um jogo marcado.",
                auto ? "Desligar" : "Ligar", v -> { Prefs.setAutoDetect(this, !auto); render(); });
        boolean alerts = Prefs.heatAlerts(this);
        addRow(content, "Alertas de aquecimento: " + (alerts ? "ligados" : "desligados"),
                "Notifica quando é hora de pausar.",
                alerts ? "Desligar" : "Ligar", v -> { Prefs.setHeatAlerts(this, !alerts); render(); });

        addSection(content, "JOGOS");
        addRow(content, "Meus jogos e perfis", "Selecionar jogos e editar perfis.", "Abrir",
                v -> startActivity(new Intent(this, GamesActivity.class)));

        addSection(content, "O QUE ESTE APP FAZ E NÃO FAZ");
        addNote(content, "Faz (sem root): mede RAM, bateria, temperatura da bateria, estado térmico, rede e — com permissão DUMP — FPS real; "
                + "grava sessões; aplica Não Perturbe e brilho por jogo; mostra a barra flutuante; identifica limitações e leva às configurações certas.\n\n"
                + "Não faz (o Android não permite a apps comuns): alterar CPU/GPU, desbloquear FPS, reduzir ping, encerrar processos de outros apps, "
                + "limpar cache de outros apps, mudar a taxa de atualização global ou a sensibilidade física do toque, desativar proteções térmicas.");

        String version = "1.0.0";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        addNote(content, "GAME BOOSTER X — ROG ULTIMATE • versão " + version);
    }

    private void perm(String title, String desc, boolean granted, Runnable request) {
        addRow(content, title + " — " + st(granted), desc, granted ? null : "Conceder", v -> request.run());
    }
}
