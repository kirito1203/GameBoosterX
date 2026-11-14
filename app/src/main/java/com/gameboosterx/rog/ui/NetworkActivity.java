package com.gameboosterx.rog.ui;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gameboosterx.rog.R;
import com.gameboosterx.rog.core.Prefs;
import com.gameboosterx.rog.net.NetworkTester;
import com.gameboosterx.rog.views.LineChartView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * NETWORK GAMING — mede ping, latência, oscilação (jitter) e perda de pacotes.
 * O app apenas mede: não existe forma legítima de reduzir o ping artificialmente.
 */
public class NetworkActivity extends BaseActivity {

    private static final int WINDOW = 60;

    private TextView conn, ping, quality, stats, btnMonitor, btnStability, events;
    private EditText host;
    private LineChartView chart;
    private final List<Float> values = new ArrayList<>();
    private final List<String> spikes = new ArrayList<>();
    private final AtomicBoolean monitoring = new AtomicBoolean(false);
    private final AtomicBoolean testing = new AtomicBoolean(false);
    private Thread monitorThread;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_network);
        setupHeader("NETWORK GAMING", "Ping, latência e estabilidade");
        conn = findViewById(R.id.txtNetConn);
        ping = findViewById(R.id.txtPing);
        quality = findViewById(R.id.txtQuality);
        stats = findViewById(R.id.txtNetStats);
        events = findViewById(R.id.txtNetEvents);
        host = findViewById(R.id.editHost);
        chart = findViewById(R.id.chartPing);
        chart.setUnit("ms");
        chart.setEmptyText("Inicie o monitor ou o teste");
        btnMonitor = findViewById(R.id.btnPingMonitor);
        btnStability = findViewById(R.id.btnStability);
        host.setText(Prefs.lastPingHost(this));

        findViewById(R.id.hostGoogle).setOnClickListener(v -> host.setText("8.8.8.8"));
        findViewById(R.id.hostCloudflare).setOnClickListener(v -> host.setText("1.1.1.1"));
        findViewById(R.id.hostRoblox).setOnClickListener(v -> host.setText("roblox.com"));
        btnMonitor.setOnClickListener(v -> toggleMonitor());
        btnStability.setOnClickListener(v -> stabilityTest());

        LinearLayout c = findViewById(R.id.netContent);
        addSection(c, "COMO LER");
        addNote(c, "• Ping: tempo de ida e volta até o servidor (menor é melhor; até 60 ms é ótimo para jogos).\n"
                + "• Jitter: variação entre medições. Acima de 20 ms causa \"teleportes\" e atrasos.\n"
                + "• Perda: pacotes sem resposta. Qualquer perda constante prejudica jogos online.\n"
                + "• O método ICMP é usado quando disponível; caso contrário, mede-se o tempo de conexão TCP.");
        addSection(c, "PARA MELHORAR A CONEXÃO");
        addNote(c, "• Prefira Wi-Fi 5 GHz perto do roteador; 2,4 GHz sofre mais interferência.\n"
                + "• Pause downloads, atualizações e streaming em outros aparelhos da rede.\n"
                + "• Em dados móveis, verifique se está em 4G/5G e não em área de sinal fraco.\n"
                + "• Escolha no jogo o servidor/região mais próximo (ex.: América do Sul).");
    }

    @Override
    protected void onResume() {
        super.onResume();
        conn.setText(NetworkTester.connectionInfo(this));
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopMonitor();
    }

    private String targetHost() {
        String h = host.getText().toString().trim();
        if (h.isEmpty()) h = "8.8.8.8";
        Prefs.setLastPingHost(this, h);
        return h;
    }

    private void toggleMonitor() {
        if (monitoring.get()) {
            stopMonitor();
            return;
        }
        final String h = targetHost();
        monitoring.set(true);
        btnMonitor.setText("PARAR MONITOR");
        values.clear();
        spikes.clear();
        monitorThread = new Thread(() -> {
            while (monitoring.get()) {
                NetworkTester.Result r = NetworkTester.ping(h, 1500);
                ui.post(() -> addValue(r));
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "gbx-ping");
        monitorThread.start();
    }

    private void stopMonitor() {
        monitoring.set(false);
        if (monitorThread != null) monitorThread.interrupt();
        if (btnMonitor != null) btnMonitor.setText("MONITOR CONTÍNUO");
    }

    private void addValue(NetworkTester.Result r) {
        // Oscilação: pico acima do dobro da média recente (e > 40 ms) ou perda
        NetworkTester.Stats before = NetworkTester.stats(values);
        if (!Float.isNaN(before.avg) && values.size() >= 5) {
            String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
            if (Float.isNaN(r.ms)) spikes.add(0, time + "  perda de pacote");
            else if (r.ms > before.avg * 2 && r.ms - before.avg > 40) {
                spikes.add(0, String.format(Locale.US, "%s  pico de %.0f ms (média %.0f)", time, r.ms, before.avg));
            }
            while (spikes.size() > 8) spikes.remove(spikes.size() - 1);
        }
        values.add(r.ms);
        while (values.size() > WINDOW) values.remove(0);
        showStats(r.method);
    }

    private void showStats(String method) {
        NetworkTester.Stats st = NetworkTester.stats(values);
        Float last = values.isEmpty() ? null : values.get(values.size() - 1);
        ping.setText(last == null || Float.isNaN(last) ? "-- ms" : String.format(Locale.US, "%.0f ms", last));
        int sc = st.score();
        quality.setText(st.quality() + " • nota " + sc + "/100");
        quality.setTextColor(sc >= 70 ? GREEN : sc >= 50 ? ORANGE : RED);
        stats.setText(String.format(Locale.US,
                "Média %s • Mín %s • Máx %s\nJitter %s • Perda %.0f%% (%d/%d) • Método %s",
                fmt(st.avg), fmt(st.min), fmt(st.max), fmt(st.jitter), st.lossPct(), st.lost, st.sent, method));
        float[] arr = new float[values.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = values.get(i);
        chart.clear();
        chart.addSeries(arr, CYAN);
        if (spikes.isEmpty()) events.setText("Nenhuma oscilação detectada.");
        else events.setText("Oscilações detectadas:\n" + String.join("\n", spikes));
    }

    private static String fmt(float v) {
        return Float.isNaN(v) ? "--" : String.format(Locale.US, "%.0f ms", v);
    }

    /** Teste de estabilidade: 30 medições seguidas, depois relatório. */
    private void stabilityTest() {
        if (testing.getAndSet(true)) return;
        stopMonitor();
        final String h = targetHost();
        values.clear();
        spikes.clear();
        btnStability.setText("TESTANDO… 0/30");
        runBg(() -> {
            String method = "";
            for (int i = 0; i < 30; i++) {
                NetworkTester.Result r = NetworkTester.ping(h, 1500);
                method = r.method;
                final int n = i + 1;
                ui.post(() -> {
                    addValue(r);
                    btnStability.setText("TESTANDO… " + n + "/30");
                });
                try { Thread.sleep(300); } catch (InterruptedException e) { break; }
            }
            final String m = method;
            ui.post(() -> {
                testing.set(false);
                btnStability.setText("TESTE DE ESTABILIDADE");
                showStats(m);
                NetworkTester.Stats st = NetworkTester.stats(values);
                String verdict;
                if (st.sent == 0 || Float.isNaN(st.avg)) verdict = "Sem resposta do servidor. Verifique a conexão ou tente outro host.";
                else if (st.score() >= 85) verdict = "Conexão excelente para jogos competitivos.";
                else if (st.score() >= 70) verdict = "Conexão boa. Pequenas oscilações podem ocorrer.";
                else if (st.score() >= 50) verdict = "Conexão regular: espere atrasos ocasionais em jogos online.";
                else verdict = "Conexão ruim para jogos online. Veja as dicas abaixo.";
                events.setText(events.getText() + "\n\nResultado do teste: " + verdict);
            });
        });
    }
}
