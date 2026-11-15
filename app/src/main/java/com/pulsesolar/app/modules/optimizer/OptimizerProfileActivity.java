package com.pulsesolar.app.modules.optimizer;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.pulsesolar.app.BaseActivity;
import com.pulsesolar.app.R;
import com.pulsesolar.app.core.Analyzer;
import com.pulsesolar.app.core.Format;
import com.pulsesolar.app.core.Nav;
import com.pulsesolar.app.core.NetTests;
import com.pulsesolar.app.core.Palette;
import com.pulsesolar.app.core.Perms;
import com.pulsesolar.app.core.SystemInfo;
import com.pulsesolar.app.core.Ui;
import com.pulsesolar.app.modules.fps.GameProfileActivity;
import com.pulsesolar.app.modules.fps.GameProfiles;
import com.pulsesolar.app.modules.fps.SessionReportActivity;
import com.pulsesolar.app.ui.LineGraph;
import com.pulsesolar.app.ui.RingGauge;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Tela de um perfil do Game Optimizer (SOLAR ROBLOX, SOLAR RHYTHM ou SOLAR CRAFT). */
public class OptimizerProfileActivity extends BaseActivity {

    public static final String EXTRA_SPEC = "spec";
    private static final int REQ_NOTIF = 91;

    private GameSpec spec;
    private String pkg, appLabel;
    private long lastSessionId = -1;

    private TextView txtProfile, txtGame, txtTier, txtLive, txtDiag, txtGraphicsWhere,
            txtToolTitle, txtToolBody, txtToolResult, txtStability;
    private Button btnPlay, btnEditProfile, btnTool, btnLastReport, btnUsageAccess, btnAppDetails, btnDiagnose;
    private RingGauge gaugeTemp, gaugeRam;
    private LineGraph graphTemp;
    private LinearLayout diagBox, tipsBox;
    private Ui.KvList kvGraphics, kvStability, kvStorage;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refreshLive();
            handler.postDelayed(this, 2000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_optimizer_profile);
        spec = GameSpec.get(getIntent().getStringExtra(EXTRA_SPEC));
        setTitle(spec.title());

        txtProfile = findViewById(R.id.txtProfile);
        txtGame = findViewById(R.id.txtGame);
        txtTier = findViewById(R.id.txtTier);
        txtLive = findViewById(R.id.txtLive);
        txtDiag = findViewById(R.id.txtDiag);
        txtGraphicsWhere = findViewById(R.id.txtGraphicsWhere);
        txtToolTitle = findViewById(R.id.txtToolTitle);
        txtToolBody = findViewById(R.id.txtToolBody);
        txtToolResult = findViewById(R.id.txtToolResult);
        txtStability = findViewById(R.id.txtStability);
        btnPlay = findViewById(R.id.btnPlay);
        btnEditProfile = findViewById(R.id.btnEditProfile);
        btnTool = findViewById(R.id.btnTool);
        btnLastReport = findViewById(R.id.btnLastReport);
        btnUsageAccess = findViewById(R.id.btnUsageAccess);
        btnAppDetails = findViewById(R.id.btnAppDetails);
        btnDiagnose = findViewById(R.id.btnDiagnose);
        gaugeTemp = findViewById(R.id.gaugeTemp);
        gaugeRam = findViewById(R.id.gaugeRam);
        graphTemp = findViewById(R.id.graphTemp);
        diagBox = findViewById(R.id.diagBox);
        tipsBox = findViewById(R.id.tipsBox);
        kvGraphics = new Ui.KvList((LinearLayout) findViewById(R.id.kvGraphics));
        kvStability = new Ui.KvList((LinearLayout) findViewById(R.id.kvStability));
        kvStorage = new Ui.KvList((LinearLayout) findViewById(R.id.kvStorage));

        txtProfile.setText(spec.title());
        txtGraphicsWhere.setText(spec.graphicsWhere());
        gaugeTemp.setCaption("bateria °C");
        gaugeRam.setCaption("RAM em uso");
        graphTemp.setRange(20, 50);
        graphTemp.setColor(Palette.GOLD);
        setupTool();

        btnPlay.setOnClickListener(v -> play());
        btnEditProfile.setOnClickListener(v -> {
            if (pkg == null) {
                toast("Escolha o app do jogo primeiro");
                return;
            }
            if (!GameProfiles.has(this, pkg)) GameProfiles.save(this, pkg, spec.defaultProfile());
            startActivity(new Intent(this, GameProfileActivity.class)
                    .putExtra(GameProfileActivity.EXTRA_PKG, pkg)
                    .putExtra(GameProfileActivity.EXTRA_LABEL, appLabel));
        });
        findViewById(R.id.btnChoose).setOnClickListener(v -> chooseApp());
        btnDiagnose.setOnClickListener(v -> analyze());
        btnLastReport.setOnClickListener(v -> {
            if (lastSessionId > 0) {
                startActivity(new Intent(this, SessionReportActivity.class)
                        .putExtra(SessionReportActivity.EXTRA_ID, lastSessionId));
            }
        });
        btnUsageAccess.setOnClickListener(v -> Nav.open(this,
                new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), Nav.settings()));
        btnAppDetails.setOnClickListener(v -> {
            if (pkg != null) Nav.open(this, Nav.appDetails(pkg));
            else toast("Escolha o app do jogo primeiro");
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(tick);
        analyze();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }

    // ------------------------------------------------------------ Monitor ao vivo

    private void refreshLive() {
        SystemInfo.Battery b = SystemInfo.battery(this);
        SystemInfo.Ram r = SystemInfo.ram(this);
        if (!Float.isNaN(b.tempC)) {
            gaugeTemp.setProgress((b.tempC - 20f) * 100f / 30f);
            gaugeTemp.setCenterText(Math.round(b.tempC) + "°");
            gaugeTemp.setColor(Palette.forTemp(b.tempC));
            graphTemp.addPoint(b.tempC);
        }
        float u = r.usedPercent();
        gaugeRam.setProgress(u);
        gaugeRam.setCenterText(Math.round(u) + "%");
        gaugeRam.setColor(Palette.forLoad(u));
        txtLive.setText(Format.bytes(r.avail) + " de RAM livres  •  " + SystemInfo.thermalText(b.thermalStatus)
                + (b.powerSave ? "  •  ECONOMIA LIGADA" : ""));
    }

    // ------------------------------------------------------------ Análise completa

    private void analyze() {
        btnDiagnose.setEnabled(false);
        txtDiag.setText("Analisando o aparelho…");
        runBg(() -> {
            String p = spec.detectPackage(this);
            String label = null;
            if (p != null) {
                try {
                    PackageManager pm = getPackageManager();
                    label = pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString();
                } catch (PackageManager.NameNotFoundException e) {
                    p = null;
                }
            }
            DeviceState d = DeviceState.capture(this);
            List<Analyzer.Finding> diag = spec.diagnose(this, d);
            List<String[]> gfx = spec.graphics(this, d);
            OptimizerStore.AppStorage st = OptimizerStore.storageOf(this, p);
            OptimizerStore.Stability stab = OptimizerStore.stability(this, p);
            List<Analyzer.Finding> tips = spec.tips(this, d, st, stab.last);
            final String fp = p, fl = label;
            ui(() -> show(fp, fl, d, diag, gfx, st, stab, tips));
        });
    }

    private void show(String p, String label, DeviceState d, List<Analyzer.Finding> diag, List<String[]> gfx,
                      OptimizerStore.AppStorage st, OptimizerStore.Stability stab, List<Analyzer.Finding> tips) {
        pkg = p;
        appLabel = label;
        btnDiagnose.setEnabled(true);

        if (p == null) {
            txtGame.setText(spec.gameName() + ": não encontrado. Toque em \"Escolher o app do jogo\".");
            txtGame.setTextColor(Palette.AMBER);
        } else {
            txtGame.setText(spec.gameName() + " → " + label
                    + (GameProfiles.has(this, p) ? "  (perfil salvo)" : "  (perfil Solar padrão)"));
            txtGame.setTextColor(Palette.TEXT);
        }
        btnPlay.setEnabled(p != null);
        txtTier.setText("Classe estimada: " + DeviceState.tierName(d.tier)
                + (d.limited ? " → recomendações em " + DeviceState.tierName(d.effectiveTier()) + " agora" : "")
                + ". " + d.tierReason);

        int bad = 0;
        diagBox.removeAllViews();
        for (Analyzer.Finding f : diag) {
            if (f.severity >= Analyzer.WARN) bad++;
            Ui.findingRow(diagBox, f);
        }
        txtDiag.setText(bad == 0 ? "✔ Nenhum problema para " + spec.gameName() + " agora."
                : bad + " ponto(s) de atenção para " + spec.gameName() + ":");
        txtDiag.setTextColor(bad == 0 ? Palette.GREEN : Palette.AMBER);

        kvGraphics.clear();
        for (String[] r : gfx) kvGraphics.add(r[0], r[1]);

        // Estabilidade
        kvStability.clear();
        if (stab.sessions == 0) {
            txtStability.setText("Ainda não há sessões gravadas deste jogo. Toque em \"Jogar com o perfil Solar\" (com o monitor ligado) para gravar a primeira.");
            btnLastReport.setVisibility(View.GONE);
            lastSessionId = -1;
        } else {
            txtStability.setText("Baseado em " + stab.sessions + " sessão(ões) reais gravadas pelo Solar FPS Engine.");
            kvStability.add("Tempo total monitorado", Format.duration(stab.totalMs));
            if (stab.avgStability >= 0) {
                kvStability.add("Estabilidade média do pulso*", String.format(Locale.getDefault(), "%.0f%% dos quadros no tempo", stab.avgStability));
            }
            if (!Float.isNaN(stab.maxTemp)) kvStability.add("Temperatura máxima", Format.one(stab.maxTemp) + " °C");
            kvStability.add("Alertas emitidos", String.valueOf(stab.alerts));
            kvStability.add("Última sessão", DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(new Date(stab.last.start)) + " (" + Format.duration(stab.last.durationMs()) + ")");
            kvStability.add("* Pulso", "quadros do próprio Pulse Solar durante o jogo, não o FPS do jogo");
            lastSessionId = stab.last.id;
            btnLastReport.setVisibility(View.VISIBLE);
        }

        // Espaço
        kvStorage.clear();
        boolean usage = Perms.hasUsageAccess(this);
        btnUsageAccess.setVisibility(usage ? View.GONE : View.VISIBLE);
        if (p == null) {
            kvStorage.add("Jogo", "não encontrado");
        } else if (st == null) {
            kvStorage.add("Espaço", usage ? "não informado pelo sistema" : "precisa do acesso ao uso");
        } else {
            kvStorage.add("Total", Format.bytes(st.total()));
            kvStorage.add("App", Format.bytes(st.app));
            kvStorage.add("Dados (mundos, contas, downloads)", Format.bytes(st.data));
            kvStorage.add("Cache (pode ser limpo)", Format.bytes(st.cache));
        }

        tipsBox.removeAllViews();
        for (Analyzer.Finding f : tips) Ui.findingRow(tipsBox, f);
    }

    // ------------------------------------------------------------ Ferramenta do perfil

    private void setupTool() {
        switch (spec.id()) {
            case GameSpec.RHYTHM:
                txtToolTitle.setText("Calibração de sincronia musical");
                txtToolBody.setText("Mede o atraso real entre o som e o seu toque usando o relógio do áudio do Android. Use o valor no ajuste de offset do seu FNF.");
                btnTool.setText("Calibrar sincronia");
                btnTool.setOnClickListener(v -> startActivity(new Intent(this, RhythmCalibrationActivity.class)));
                break;
            case GameSpec.CRAFT:
                txtToolTitle.setText("Teste de estabilidade térmica");
                txtToolBody.setText("Sessões longas de Minecraft aquecem o aparelho. O teste de estabilidade do Solar Intelligence mostra quanto desempenho a CPU perde ao esquentar.");
                btnTool.setText("Abrir teste no Solar Intelligence");
                btnTool.setOnClickListener(v -> startActivity(new Intent(this,
                        com.pulsesolar.app.modules.intelligence.SolarIntelligenceActivity.class)));
                break;
            default:
                txtToolTitle.setText("Teste de conexão");
                txtToolBody.setText("Mede a latência real até o site do Roblox e até a Cloudflare (10 tentativas). O servidor da partida pode estar em outro lugar, então o ping dentro do jogo pode ser diferente.");
                btnTool.setText("Testar conexão");
                btnTool.setOnClickListener(v -> runRobloxPing());
        }
    }

    private void runRobloxPing() {
        btnTool.setEnabled(false);
        txtToolResult.setText("Testando… (cerca de 8 s)");
        runBg(() -> {
            NetTests.Ping[] res = {
                    NetTests.tcpPing("Site do Roblox", "www.roblox.com", 443, 10, 2000),
                    NetTests.tcpPing("Cloudflare", "1.1.1.1", 443, 10, 2000)
            };
            StringBuilder sb = new StringBuilder();
            double best = -1, bj = 0;
            int bl = 100;
            for (NetTests.Ping p : res) {
                sb.append(p.name).append('\n');
                if (p.received == 0) {
                    sb.append("  ").append(p.error != null ? p.error : "Sem resposta").append("\n");
                    continue;
                }
                sb.append(String.format(Locale.getDefault(), "  média %.0f ms • jitter %.0f ms • perda %d%%%n",
                        p.avg, p.jitter, p.lossPercent()));
                if (best < 0 || p.avg < best) {
                    best = p.avg;
                    bj = p.jitter;
                    bl = p.lossPercent();
                }
            }
            sb.append("\nAvaliação: ").append(NetTests.gameRating(best, bj, bl));
            String out = sb.toString();
            ui(() -> {
                btnTool.setEnabled(true);
                txtToolResult.setText(out);
            });
        });
    }

    // ------------------------------------------------------------ Jogar / escolher app

    private void play() {
        if (pkg == null) {
            toast("Escolha o app do jogo primeiro");
            return;
        }
        GameProfiles.Profile p = GameProfiles.has(this, pkg) ? GameProfiles.get(this, pkg) : spec.defaultProfile();
        if (!GameProfiles.has(this, pkg)) GameProfiles.save(this, pkg, p);
        if (p.monitor && Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
            return;
        }
        doPlay(p);
    }

    private void doPlay(GameProfiles.Profile p) {
        String warn = GameProfiles.play(this, pkg, appLabel, p);
        if (warn != null) toast(warn);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIF && pkg != null) doPlay(GameProfiles.get(this, pkg));
    }

    private void chooseApp() {
        runBg(() -> {
            PackageManager pm = getPackageManager();
            Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> ris = pm.queryIntentActivities(launcher, 0);
            Set<String> seen = new HashSet<>();
            List<String[]> apps = new ArrayList<>();
            for (ResolveInfo ri : ris) {
                String p = ri.activityInfo.packageName;
                if (p.equals(getPackageName()) || !seen.add(p)) continue;
                apps.add(new String[]{ri.loadLabel(pm).toString(), p});
            }
            Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));
            String[] labels = new String[apps.size()];
            for (int i = 0; i < apps.size(); i++) labels[i] = apps.get(i)[0];
            ui(() -> new AlertDialog.Builder(this)
                    .setTitle("App do " + spec.gameName())
                    .setItems(labels, (dlg, which) -> {
                        OptimizerStore.setChosenPackage(this, spec.id(), apps.get(which)[1]);
                        toast("Perfil " + spec.title() + " ligado a " + apps.get(which)[0]);
                        analyze();
                    })
                    .setNegativeButton("Cancelar", null)
                    .show());
        });
    }
}
