package com.pulsesolar.app.modules.gaming;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.pulsesolar.app.BaseActivity;
import com.pulsesolar.app.R;
import com.pulsesolar.app.core.Analyzer;
import com.pulsesolar.app.core.Format;
import com.pulsesolar.app.core.GameModeController;
import com.pulsesolar.app.core.Nav;
import com.pulsesolar.app.core.NetTests;
import com.pulsesolar.app.core.Palette;
import com.pulsesolar.app.core.Perms;
import com.pulsesolar.app.core.SystemInfo;
import com.pulsesolar.app.core.Ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Módulo 6 — Solar Gaming: checklist real, Modo Jogos permitido pelo Android e lançador de jogos. */
public class SolarGamingActivity extends BaseActivity {

    private static final String[] GAME_SPACE_PKGS = {
            "com.oplus.games", "com.coloros.gamespace", "com.oplus.gamespace", "com.coloros.gamespaceui"
    };

    private GameModeController.Options opts;
    private TextView txtCheck, txtState, txtPermDnd, txtPermWrite, txtBright, txtGamesMsg;
    private Button btnCheck, btnActivate, btnRestore, btnPermDnd, btnPermWrite, btnShowAll;
    private Switch swDnd, swBright, swTimeout;
    private SeekBar seekBright;
    private LinearLayout checkBox, gamesBox;
    private boolean showAll = false;

    private static final class AppEntry {
        String pkg, label;
        Drawable icon;
        boolean game;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gaming);
        opts = GameModeController.load(this);

        txtCheck = findViewById(R.id.txtCheck);
        txtState = findViewById(R.id.txtState);
        txtPermDnd = findViewById(R.id.txtPermDnd);
        txtPermWrite = findViewById(R.id.txtPermWrite);
        txtBright = findViewById(R.id.txtBright);
        txtGamesMsg = findViewById(R.id.txtGamesMsg);
        btnCheck = findViewById(R.id.btnCheck);
        btnActivate = findViewById(R.id.btnActivate);
        btnRestore = findViewById(R.id.btnRestore);
        btnPermDnd = findViewById(R.id.btnPermDnd);
        btnPermWrite = findViewById(R.id.btnPermWrite);
        btnShowAll = findViewById(R.id.btnShowAll);
        swDnd = findViewById(R.id.swDnd);
        swBright = findViewById(R.id.swBright);
        swTimeout = findViewById(R.id.swTimeout);
        seekBright = findViewById(R.id.seekBright);
        checkBox = findViewById(R.id.checkBox);
        gamesBox = findViewById(R.id.gamesBox);

        swDnd.setChecked(opts.dnd);
        swBright.setChecked(opts.bright);
        swTimeout.setChecked(opts.timeout);
        seekBright.setProgress(opts.brightValue);
        updateBrightLabel();

        swDnd.setOnCheckedChangeListener((b, c) -> {
            opts.dnd = c;
            GameModeController.save(this, opts);
        });
        swBright.setOnCheckedChangeListener((b, c) -> {
            opts.bright = c;
            GameModeController.save(this, opts);
        });
        swTimeout.setOnCheckedChangeListener((b, c) -> {
            opts.timeout = c;
            GameModeController.save(this, opts);
        });
        seekBright.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                updateBrightLabel();
                if (fromUser) GameModeController.liveBrightness(SolarGamingActivity.this, p);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                opts.brightValue = s.getProgress();
                GameModeController.save(SolarGamingActivity.this, opts);
            }
        });

        btnCheck.setOnClickListener(v -> runChecklist());
        btnActivate.setOnClickListener(v -> {
            String err = GameModeController.activate(this, opts);
            toast(err == null ? "Modo Jogos ativado" : err);
            updateUi();
        });
        btnRestore.setOnClickListener(v -> {
            GameModeController.restore(this);
            toast("Configurações restauradas");
            updateUi();
        });
        btnPermDnd.setOnClickListener(v -> Nav.open(this,
                new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS), Nav.settings()));
        btnPermWrite.setOnClickListener(v -> Nav.open(this,
                new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName())),
                Nav.settings()));
        btnShowAll.setOnClickListener(v -> {
            showAll = !showAll;
            loadApps();
        });

        Intent gs = findGameSpace();
        if (gs != null) {
            Button b = findViewById(R.id.btnGameSpace);
            b.setVisibility(View.VISIBLE);
            b.setOnClickListener(v -> Nav.open(this, gs));
        }

        loadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUi();
    }

    private void updateBrightLabel() {
        txtBright.setText(Math.round(seekBright.getProgress() * 100f / 255f) + "%");
    }

    private void updateUi() {
        boolean dnd = Perms.hasDndAccess(this);
        boolean write = Perms.canWriteSettings(this);
        txtPermDnd.setText(dnd ? "✔ Acesso ao Não Perturbe concedido" : "✖ Sem acesso ao Não Perturbe");
        txtPermDnd.setTextColor(dnd ? Palette.GREEN : Palette.AMBER);
        btnPermDnd.setVisibility(dnd ? View.GONE : View.VISIBLE);
        txtPermWrite.setText(write ? "✔ Pode ajustar brilho e tempo de tela" : "✖ Sem permissão para brilho e tempo de tela");
        txtPermWrite.setTextColor(write ? Palette.GREEN : Palette.AMBER);
        btnPermWrite.setVisibility(write ? View.GONE : View.VISIBLE);

        boolean active = GameModeController.isActive(this);
        txtState.setText(active ? "● ATIVO" : "○ Desativado");
        txtState.setTextColor(active ? Palette.GREEN : Palette.DIM);
        btnActivate.setEnabled(!active);
        btnRestore.setEnabled(active);
    }

    // ------------------------------------------------------------ Checklist

    private void runChecklist() {
        btnCheck.setEnabled(false);
        txtCheck.setText("Verificando… (cerca de 3 s)");
        checkBox.removeAllViews();
        runBg(() -> {
            List<Analyzer.Finding> out = new ArrayList<>();
            SystemInfo.Ram r = SystemInfo.ram(this);
            float ap = r.availPercent();
            String ram = Format.bytes(r.avail) + " livres (" + Format.percent(ap) + ")";
            if (r.low || ap < 15f) {
                out.add(new Analyzer.Finding(Analyzer.CRIT, "Pouca RAM livre", ram + ". Feche apps na tela de recentes antes de abrir o jogo."));
            } else if (ap < 25f) {
                out.add(new Analyzer.Finding(Analyzer.WARN, "RAM moderada", ram + ". Jogos pesados podem recarregar texturas."));
            } else {
                out.add(new Analyzer.Finding(Analyzer.OK, "RAM livre suficiente", ram + "."));
            }

            SystemInfo.Storage s = SystemInfo.storage();
            if (s.freePercent() < 10f) {
                out.add(new Analyzer.Finding(Analyzer.WARN, "Pouco espaço livre",
                        Format.bytes(s.free) + " livres. Jogos precisam de espaço para cache e atualizações.")
                        .action("Abrir armazenamento", new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS), Nav.settings()));
            } else {
                out.add(new Analyzer.Finding(Analyzer.OK, "Espaço livre suficiente", Format.bytes(s.free) + " livres."));
            }

            SystemInfo.Battery b = SystemInfo.battery(this);
            if (b.powerSave) {
                out.add(new Analyzer.Finding(Analyzer.CRIT, "Economia de bateria ligada",
                        "Ela limita o processador e derruba o desempenho em jogos.")
                        .action("Desligar economia de bateria", new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS), Nav.settings()));
            }
            if (!Float.isNaN(b.tempC) && b.tempC >= 40f) {
                out.add(new Analyzer.Finding(Analyzer.WARN, "Aparelho já está quente",
                        "Bateria a " + Format.one(b.tempC) + " °C. Começar quente faz o sistema limitar o desempenho mais cedo."));
            } else if (!Float.isNaN(b.tempC)) {
                out.add(new Analyzer.Finding(Analyzer.OK, "Temperatura boa para começar", Format.one(b.tempC) + " °C na bateria."));
            }
            if (b.thermalStatus >= android.os.PowerManager.THERMAL_STATUS_MODERATE) {
                out.add(new Analyzer.Finding(Analyzer.WARN, "Sistema em alerta térmico",
                        "Estado: " + SystemInfo.thermalText(b.thermalStatus) + "."));
            }
            if (b.level >= 0 && b.level < 20 && !b.isCharging()) {
                out.add(new Analyzer.Finding(Analyzer.WARN, "Bateria baixa",
                        b.level + "%. Abaixo de ~20% o sistema pode ativar economia automática."));
            }

            NetTests.State ns = NetTests.state(this);
            if (!ns.connected || !ns.validated) {
                out.add(new Analyzer.Finding(Analyzer.WARN, "Internet indisponível",
                        "Jogos online não vão conectar. " + ns.transport() + "."));
            } else {
                NetTests.Ping p = NetTests.tcpPing("Cloudflare", "1.1.1.1", 443, 5, 2000);
                if (p.received == 0) {
                    out.add(new Analyzer.Finding(Analyzer.WARN, "Servidor de teste não respondeu", "Pode haver bloqueio ou instabilidade na rede."));
                } else {
                    String rating = NetTests.gameRating(p.avg, p.jitter, p.lossPercent());
                    int sev = rating.startsWith("Boa") ? Analyzer.OK : rating.startsWith("Aceit") ? Analyzer.WARN : Analyzer.CRIT;
                    out.add(new Analyzer.Finding(sev, "Latência: " + rating,
                            String.format(Locale.getDefault(), "%s • média %.0f ms, jitter %.0f ms, perda %d%%.",
                                    ns.transport(), p.avg, p.jitter, p.lossPercent())));
                }
            }

            Collections.sort(out, (x, y) -> Integer.compare(y.severity, x.severity));
            ui(() -> {
                btnCheck.setEnabled(true);
                int bad = 0;
                for (Analyzer.Finding f : out) if (f.severity >= Analyzer.WARN) bad++;
                txtCheck.setText(bad == 0 ? "✔ Tudo pronto para jogar." : bad + " ponto(s) para resolver antes de jogar:");
                txtCheck.setTextColor(bad == 0 ? Palette.GREEN : Palette.AMBER);
                checkBox.removeAllViews();
                for (Analyzer.Finding f : out) Ui.findingRow(checkBox, f);
            });
        });
    }

    // ------------------------------------------------------------ Jogos

    private Intent findGameSpace() {
        PackageManager pm = getPackageManager();
        for (String p : GAME_SPACE_PKGS) {
            Intent i = pm.getLaunchIntentForPackage(p);
            if (i != null) return i;
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    private void loadApps() {
        txtGamesMsg.setText("Carregando…");
        gamesBox.removeAllViews();
        final boolean all = showAll;
        runBg(() -> {
            PackageManager pm = getPackageManager();
            Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> ris = pm.queryIntentActivities(launcher, 0);
            Set<String> seen = new HashSet<>();
            List<AppEntry> list = new ArrayList<>();
            for (ResolveInfo ri : ris) {
                ApplicationInfo ai = ri.activityInfo.applicationInfo;
                if (ai.packageName.equals(getPackageName()) || !seen.add(ai.packageName)) continue;
                boolean game = ai.category == ApplicationInfo.CATEGORY_GAME
                        || (ai.flags & ApplicationInfo.FLAG_IS_GAME) != 0;
                if (!all && !game) continue;
                AppEntry a = new AppEntry();
                a.pkg = ai.packageName;
                a.label = ri.loadLabel(pm).toString();
                a.icon = ri.loadIcon(pm);
                a.game = game;
                list.add(a);
            }
            Collections.sort(list, (x, y) -> x.label.compareToIgnoreCase(y.label));
            ui(() -> {
                gamesBox.removeAllViews();
                btnShowAll.setText(all ? "Mostrar só jogos" : "Mostrar todos os apps");
                if (list.isEmpty()) {
                    txtGamesMsg.setText("Nenhum app marcado como jogo foi encontrado. Alguns jogos não se declaram como jogo; toque em \"Mostrar todos os apps\".");
                    return;
                }
                txtGamesMsg.setText(list.size() + (all ? " apps" : " jogos") + ". Toque para ativar o Modo Jogos e abrir.");
                for (AppEntry a : list) {
                    Ui.appRow(gamesBox, a.icon, a.label, a.game ? "Jogo" : a.pkg, v -> launch(a.pkg));
                }
            });
        });
    }

    private void launch(String pkg) {
        if (!GameModeController.isActive(this)) {
            String err = GameModeController.activate(this, opts);
            if (err != null) toast("Abrindo sem Modo Jogos: " + err);
        }
        Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) {
            toast("Não foi possível abrir este app");
            return;
        }
        Nav.open(this, i);
    }
}
