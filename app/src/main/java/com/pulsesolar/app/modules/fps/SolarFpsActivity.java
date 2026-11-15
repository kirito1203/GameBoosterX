package com.pulsesolar.app.modules.fps;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.pulsesolar.app.BaseActivity;
import com.pulsesolar.app.R;
import com.pulsesolar.app.core.Analyzer;
import com.pulsesolar.app.core.Format;
import com.pulsesolar.app.core.Nav;
import com.pulsesolar.app.core.Palette;
import com.pulsesolar.app.core.Ui;
import com.pulsesolar.app.ui.LineGraph;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Módulo 8 — Solar FPS Engine: pulso do sistema, gargalos, sessões, perfis e relatórios. */
public class SolarFpsActivity extends BaseActivity {

    private static final int REQ_NOTIF = 77;
    private static final int LIMIT_MIN = 35;

    private TextView txtSession, txtLimit, txtPulse, txtPulseDetail, txtReports;
    private Button btnStart, btnStop, btnOverlay;
    private SeekBar seekLimit;
    private LineGraph graphPulse;
    private LinearLayout findingsBox, reportsBox, compatBox;
    private Ui.KvList kvCpu;
    private FrameMonitor monitor;
    private FrameMonitor.Stats lastStats;
    private SharedPreferences prefs;
    private float graphMax = 70f;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refreshCpu();
            updateSessionState();
            handler.postDelayed(this, 2000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fps);
        prefs = getSharedPreferences("fps_prefs", MODE_PRIVATE);

        txtSession = findViewById(R.id.txtSession);
        txtLimit = findViewById(R.id.txtLimit);
        txtPulse = findViewById(R.id.txtPulse);
        txtPulseDetail = findViewById(R.id.txtPulseDetail);
        txtReports = findViewById(R.id.txtReports);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        btnOverlay = findViewById(R.id.btnOverlay);
        seekLimit = findViewById(R.id.seekLimit);
        graphPulse = findViewById(R.id.graphPulse);
        findingsBox = findViewById(R.id.findingsBox);
        reportsBox = findViewById(R.id.reportsBox);
        compatBox = findViewById(R.id.compatBox);
        kvCpu = new Ui.KvList((LinearLayout) findViewById(R.id.kvCpu));
        graphPulse.setRange(0, graphMax);
        graphPulse.setColor(Palette.GOLD);
        txtPulse.setText("-- q/s");

        seekLimit.setProgress(prefs.getInt("limit", 42) - LIMIT_MIN);
        updateLimitLabel();
        seekLimit.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                updateLimitLabel();
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                prefs.edit().putInt("limit", s.getProgress() + LIMIT_MIN).apply();
            }
        });

        btnStart.setOnClickListener(v -> startSession());
        btnStop.setOnClickListener(v -> {
            SessionService.stop(this);
            toast("Encerrando e salvando o relatório…");
            handler.postDelayed(() -> {
                updateSessionState();
                loadReports();
            }, 1200);
        });
        btnOverlay.setOnClickListener(v -> Nav.open(this,
                new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())),
                Nav.appDetails(getPackageName())));
        findViewById(R.id.btnProfiles).setOnClickListener(v ->
                startActivity(new Intent(this, GameProfileActivity.class)));
        findViewById(R.id.btnDiagnose).setOnClickListener(v -> diagnose());
        findViewById(R.id.btnCompat).setOnClickListener(v -> runCompat());
        findViewById(R.id.btnClearReports).setOnClickListener(v -> runBg(() -> {
            SessionStore.clear(getApplicationContext());
            ui(() -> {
                toast("Relatórios apagados");
                loadReports();
            });
        }));

        monitor = new FrameMonitor(this::onPulse);
        runCompat();
    }

    @Override
    protected void onResume() {
        super.onResume();
        monitor.start();
        handler.post(tick);
        btnOverlay.setVisibility(Settings.canDrawOverlays(this) ? View.GONE : View.VISIBLE);
        loadReports();
    }

    @Override
    protected void onPause() {
        monitor.stop();
        handler.removeCallbacks(tick);
        super.onPause();
    }

    private void updateLimitLabel() {
        txtLimit.setText("Alertar quando a bateria passar de " + (seekLimit.getProgress() + LIMIT_MIN) + " °C");
    }

    // ------------------------------------------------------------ Pulso ao vivo

    private void onPulse(FrameMonitor.Stats s) {
        lastStats = s;
        txtPulse.setText(String.format(Locale.getDefault(), "%.0f q/s", s.fps));
        txtPulseDetail.setText(String.format(Locale.getDefault(),
                "Tela a ~%.0f Hz  •  %.0f%% dos quadros no tempo  •  %d travada(s) > 100 ms",
                s.vsyncHz, s.stability(), s.stalls));
        txtPulseDetail.setTextColor(s.stability() >= 90f ? Palette.GREEN : s.stability() >= 75f ? Palette.AMBER : Palette.RED);
        if (s.vsyncHz * 1.1f > graphMax) {
            graphMax = s.vsyncHz * 1.1f;
            graphPulse.setRange(0, graphMax);
        }
        graphPulse.addPoint(s.fps);
    }

    // ------------------------------------------------------------ CPU / gargalos

    private void refreshCpu() {
        kvCpu.clear();
        List<CpuProbe.Cluster> list = CpuProbe.read();
        if (list.isEmpty()) {
            kvCpu.add("Frequências", "Bloqueadas pelo sistema");
        }
        int i = 1;
        for (CpuProbe.Cluster c : list) {
            String name = list.size() == 1 ? "CPU" : (c == CpuProbe.big(list) ? "Cluster rápido" : "Cluster " + i);
            StringBuilder v = new StringBuilder();
            v.append(c.curKhz > 0 ? Format.ghz(c.curKhz) : "--").append(" / máx ").append(Format.ghz(c.hwMaxKhz));
            float cap = c.capRatio();
            if (cap > 0) v.append("\nteto ").append(Math.round(cap * 100)).append('%');
            kvCpu.add(name + " (" + c.cores.size() + " núcleos)", v.toString());
            i++;
        }
    }

    private void diagnose() {
        findingsBox.removeAllViews();
        final FrameMonitor.Stats live = lastStats;
        runBg(() -> {
            Session.Point p = Session.Point.capture(getApplicationContext(), true);
            List<Analyzer.Finding> list = Bottleneck.now(p, live);
            ui(() -> {
                findingsBox.removeAllViews();
                for (Analyzer.Finding f : list) Ui.findingRow(findingsBox, f);
            });
        });
    }

    // ------------------------------------------------------------ Sessão

    private void updateSessionState() {
        boolean on = SessionService.running;
        if (on) {
            String who = SessionService.runningLabel != null ? SessionService.runningLabel : "jogo atual";
            txtSession.setText("● Gravando: " + who + " há "
                    + Format.duration(System.currentTimeMillis() - SessionService.runningSince));
            txtSession.setTextColor(Palette.GREEN);
        } else {
            txtSession.setText("○ Nenhuma sessão em andamento");
            txtSession.setTextColor(Palette.DIM);
        }
        btnStart.setEnabled(!on);
        btnStop.setEnabled(on);
    }

    private void startSession() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
            return;
        }
        doStart();
    }

    private void doStart() {
        try {
            SessionService.start(this, null, null, seekLimit.getProgress() + LIMIT_MIN);
            toast(Settings.canDrawOverlays(this)
                    ? "Monitor iniciado. Abra seu jogo."
                    : "Monitor iniciado sem painel (permita a sobreposição para vê-lo). Abra seu jogo.");
        } catch (RuntimeException e) {
            toast("O Android não permitiu iniciar o monitor agora");
        }
        handler.postDelayed(this::updateSessionState, 500);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_NOTIF) return;
        if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            toast("Sem notificações você não verá os alertas, mas a sessão será gravada.");
        }
        doStart();
    }

    // ------------------------------------------------------------ Relatórios

    private void loadReports() {
        runBg(() -> {
            List<Session> list = SessionStore.list(getApplicationContext());
            ui(() -> {
                reportsBox.removeAllViews();
                txtReports.setText(list.isEmpty() ? "Nenhuma sessão gravada ainda."
                        : list.size() + " sessão(ões). Toque para ver o relatório.");
                DateFormat df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
                for (Session s : list) {
                    String title = s.label != null ? s.label : "Sessão sem jogo identificado";
                    String sub = df.format(new Date(s.start)) + "  •  " + Format.duration(s.durationMs())
                            + "  •  " + s.alerts.size() + " alerta(s)";
                    Ui.appRow(reportsBox, getDrawable(R.drawable.ic_stat_sun), title, sub, v ->
                            startActivity(new Intent(this, SessionReportActivity.class)
                                    .putExtra(SessionReportActivity.EXTRA_ID, s.id)));
                }
            });
        });
    }

    // ------------------------------------------------------------ Compatibilidade

    private void runCompat() {
        compatBox.removeAllViews();
        runBg(() -> {
            List<CompatCheck.Item> items = CompatCheck.run(this);
            ui(() -> {
                compatBox.removeAllViews();
                LayoutInflater inf = LayoutInflater.from(this);
                for (CompatCheck.Item it : items) {
                    View row = inf.inflate(R.layout.item_compat, compatBox, false);
                    TextView st = row.findViewById(R.id.compatStatus);
                    st.setText(it.status);
                    st.setTextColor("✔".equals(it.status) ? Palette.GREEN : "◐".equals(it.status) ? Palette.AMBER : Palette.RED);
                    ((TextView) row.findViewById(R.id.compatName)).setText(it.name);
                    ((TextView) row.findViewById(R.id.compatDetail)).setText(it.detail);
                    compatBox.addView(row);
                }
            });
        });
    }
}
