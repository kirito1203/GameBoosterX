package com.pulsesolar.app.modules.intelligence;

import android.app.ActivityManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ConfigurationInfo;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Display;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.pulsesolar.app.BaseActivity;
import com.pulsesolar.app.R;
import com.pulsesolar.app.core.Analyzer;
import com.pulsesolar.app.core.Benchmark;
import com.pulsesolar.app.core.Format;
import com.pulsesolar.app.core.Nav;
import com.pulsesolar.app.core.Palette;
import com.pulsesolar.app.core.SystemInfo;
import com.pulsesolar.app.core.Ui;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/** Módulo 7 — Solar Intelligence: possíveis causas de travamentos, teste térmico e dados do hardware. */
public class SolarIntelligenceActivity extends BaseActivity {

    private LinearLayout findingsBox;
    private TextView txtSummary, txtBench;
    private Button btnAnalyze, btnQuick, btnStability;
    private Ui.KvList kvCpu;
    private SharedPreferences prefs;
    private boolean running = false;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refreshCpu();
            handler.postDelayed(this, 1500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intelligence);
        prefs = getSharedPreferences("bench", MODE_PRIVATE);
        findingsBox = findViewById(R.id.findingsBox);
        txtSummary = findViewById(R.id.txtSummary);
        txtBench = findViewById(R.id.txtBench);
        btnAnalyze = findViewById(R.id.btnAnalyze);
        btnQuick = findViewById(R.id.btnQuick);
        btnStability = findViewById(R.id.btnStability);
        kvCpu = new Ui.KvList((LinearLayout) findViewById(R.id.kvCpu));

        btnAnalyze.setOnClickListener(v -> analyze());
        btnQuick.setOnClickListener(v -> runQuick());
        btnStability.setOnClickListener(v -> runStability());
        findViewById(R.id.btnDisplay).setOnClickListener(v -> Nav.open(this,
                new Intent(Settings.ACTION_DISPLAY_SETTINGS), Nav.settings()));

        fillDevice();
        fillDisplay();
        showLastBench();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(tick);
        analyze(); // reanalisa ao voltar das Configurações
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }

    // ------------------------------------------------------------ Análise

    private void analyze() {
        btnAnalyze.setEnabled(false);
        txtSummary.setText("Analisando…");
        runBg(() -> {
            List<Analyzer.Finding> list = Analyzer.analyze(getApplicationContext(), true);
            ui(() -> {
                btnAnalyze.setEnabled(true);
                findingsBox.removeAllViews();
                int crit = 0, warn = 0;
                for (Analyzer.Finding f : list) {
                    if (f.severity == Analyzer.CRIT) crit++;
                    if (f.severity == Analyzer.WARN) warn++;
                    Ui.findingRow(findingsBox, f);
                }
                if (crit == 0 && warn == 0) {
                    txtSummary.setText("✔ Nenhuma causa de travamento encontrada agora. Se ainda houver travadas durante o jogo, rode o teste de estabilidade abaixo.");
                    txtSummary.setTextColor(Palette.GREEN);
                } else {
                    txtSummary.setText(crit + " crítico(s) e " + warn + " alerta(s). Resolva primeiro os itens em vermelho; cada um tem um botão para a tela certa.");
                    txtSummary.setTextColor(crit > 0 ? Palette.RED : Palette.AMBER);
                }
            });
        });
    }

    // ------------------------------------------------------------ Hardware

    private void fillDevice() {
        Ui.KvList kv = new Ui.KvList((LinearLayout) findViewById(R.id.kvDevice));
        kv.add("Fabricante", Build.MANUFACTURER);
        kv.add("Modelo", Build.MODEL + " (" + Build.DEVICE + ")");
        kv.add("Hardware", Build.HARDWARE);
        if (Build.VERSION.SDK_INT >= 31) kv.add("Chip (SoC)", Build.SOC_MANUFACTURER + " " + Build.SOC_MODEL);
        kv.add("Android", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        kv.add("Patch de segurança", Build.VERSION.SECURITY_PATCH);
        kv.add("Kernel", String.valueOf(System.getProperty("os.version")));
        kv.add("Arquiteturas", String.join(", ", Build.SUPPORTED_ABIS));
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ConfigurationInfo ci = am.getDeviceConfigurationInfo();
        kv.add("OpenGL ES", ci.getGlEsVersion());
        kv.add("RAM total (visível ao Android)", Format.bytes(SystemInfo.ram(this).total));
        kv.add("Armazenamento (dados)", Format.bytes(SystemInfo.storage().total));
    }

    @SuppressWarnings("deprecation")
    private void fillDisplay() {
        Ui.KvList kv = new Ui.KvList((LinearLayout) findViewById(R.id.kvDisplay));
        Display d = getWindowManager().getDefaultDisplay();
        Display.Mode mode = d.getMode();
        kv.add("Resolução", mode.getPhysicalWidth() + " × " + mode.getPhysicalHeight());
        kv.add("Taxa de atualização agora", Math.round(d.getRefreshRate()) + " Hz");
        TreeSet<Integer> rates = new TreeSet<>();
        for (Display.Mode m : d.getSupportedModes()) rates.add(Math.round(m.getRefreshRate()));
        StringBuilder sb = new StringBuilder();
        for (Integer r : rates) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(r).append(" Hz");
        }
        kv.add("Taxas suportadas", sb.toString());
        DisplayMetrics dm = getResources().getDisplayMetrics();
        kv.add("Densidade", dm.densityDpi + " dpi");
        if (rates.size() > 1) kv.add("Dica", "Escolha a taxa mais alta em Configurações > Tela; o jogo ainda decide quantos FPS usa.");
    }

    private void refreshCpu() {
        int n = SystemInfo.cores();
        kvCpu.clear();
        kvCpu.add("Núcleos", String.valueOf(n));
        String gov = SystemInfo.readLine("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor");
        if (gov != null) kvCpu.add("Governador", gov);
        boolean any = false;
        for (int i = 0; i < n; i++) {
            long cur = SystemInfo.cpuFreqKhz(i, "scaling_cur_freq");
            long max = SystemInfo.cpuFreqKhz(i, "cpuinfo_max_freq");
            if (cur > 0 || max > 0) any = true;
            kvCpu.add("Núcleo " + i, (cur > 0 ? Format.ghz(cur) : "desligado/bloqueado")
                    + (max > 0 ? "  (máx " + Format.ghz(max) + ")" : ""));
        }
        if (!any) kvCpu.add("Frequências", "O sistema não permite a leitura neste aparelho");
        kvCpu.add("Ligado há", Format.duration(SystemInfo.uptimeMs()));
    }

    // ------------------------------------------------------------ Teste de CPU

    private void setRunning(boolean r) {
        running = r;
        btnQuick.setEnabled(!r);
        btnStability.setEnabled(!r);
    }

    private void showLastBench() {
        long single = prefs.getLong("single", -1);
        long multi = prefs.getLong("multi", -1);
        float drop = prefs.getFloat("stab_drop", -1f);
        StringBuilder sb = new StringBuilder();
        if (single > 0) {
            sb.append("Último teste rápido:\n  1 núcleo: ").append(single).append(" pts\n  ")
                    .append(SystemInfo.cores()).append(" núcleos: ").append(multi).append(" pts");
            long when = prefs.getLong("when", 0);
            if (when > 0) sb.append("\n  em ").append(DateFormat.getDateTimeInstance().format(new Date(when)));
        }
        if (drop >= 0) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append("Última estabilidade: queda de ").append(Format.one(drop)).append("%");
        }
        txtBench.setText(sb.length() > 0 ? sb.toString() : "Nenhum teste executado ainda.");
    }

    private void runQuick() {
        if (running) return;
        setRunning(true);
        txtBench.setText("Testando 1 núcleo…");
        final int cores = SystemInfo.cores();
        runBg(() -> {
            try {
                long single = Benchmark.run(1, 2500);
                ui(() -> txtBench.setText("1 núcleo: " + single + " pts\nTestando " + cores + " núcleos…"));
                long multi = Benchmark.run(cores, 2500);
                prefs.edit().putLong("single", single).putLong("multi", multi)
                        .putLong("when", System.currentTimeMillis()).apply();
                ui(() -> {
                    setRunning(false);
                    txtBench.setText("Resultado:\n  1 núcleo: " + single + " pts\n  " + cores
                            + " núcleos: " + multi + " pts\n  Ganho multinúcleo: "
                            + Format.one(single > 0 ? (float) multi / single : 0) + "x\n\n"
                            + "Compare com testes anteriores: resultados bem menores indicam aquecimento, economia de bateria ou apps pesados rodando.");
                });
            } catch (InterruptedException e) {
                ui(() -> setRunning(false));
            }
        });
    }

    private void runStability() {
        if (running) return;
        setRunning(true);
        final int cores = SystemInfo.cores();
        final int rounds = 10;
        final long[] scores = new long[rounds];
        txtBench.setText("Iniciando teste de estabilidade…");
        runBg(() -> {
            try {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < rounds; i++) {
                    scores[i] = Benchmark.run(cores, 3000);
                    sb.append(String.format(Locale.getDefault(), "Rodada %2d: %d pts%n", i + 1, scores[i]));
                    final String partial = sb.toString();
                    ui(() -> txtBench.setText(partial));
                }
                long first = Math.max(scores[0], scores[1]);
                long last = (scores[rounds - 1] + scores[rounds - 2]) / 2;
                float drop = first > 0 ? Math.max(0f, (first - last) * 100f / first) : 0f;
                prefs.edit().putFloat("stab_drop", drop).putLong("stab_when", System.currentTimeMillis()).apply();
                String verdict;
                if (drop < 10f) verdict = "Estável: o aparelho mantém o desempenho sob carga.";
                else if (drop < 25f) verdict = "Queda moderada: há alguma limitação por temperatura.";
                else verdict = "Queda forte: o aparelho reduz bastante a velocidade quando esquenta. Jogue sem capa, longe do carregador e em local ventilado.";
                final String result = sb + "\nQueda de desempenho: " + Format.one(drop) + "%\n" + verdict;
                ui(() -> {
                    setRunning(false);
                    txtBench.setText(result);
                });
            } catch (InterruptedException e) {
                ui(() -> setRunning(false));
            }
        });
    }
}
