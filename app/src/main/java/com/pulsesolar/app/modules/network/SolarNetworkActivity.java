package com.pulsesolar.app.modules.network;

import android.content.Intent;
import android.net.TrafficStats;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.telephony.CellSignalStrength;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.pulsesolar.app.BaseActivity;
import com.pulsesolar.app.R;
import com.pulsesolar.app.core.Format;
import com.pulsesolar.app.core.Nav;
import com.pulsesolar.app.core.NetTests;
import com.pulsesolar.app.core.Palette;
import com.pulsesolar.app.core.Ui;
import com.pulsesolar.app.ui.LineGraph;

import java.util.List;
import java.util.Locale;

/** Módulo 5 — Solar Network: diagnóstico real de conexão. */
public class SolarNetworkActivity extends BaseActivity {

    private Ui.KvList kv;
    private TextView txtNetHeadline, txtTraffic, txtPing, txtSpeed;
    private Button btnPing, btnSpeed;
    private LineGraph graph;

    private long lastRx = -1, lastTx = -1, lastTime = 0;
    private float maxSeenKbs = 100f;
    private int ticks = 0;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refreshTraffic();
            if (ticks++ % 3 == 0) refreshState();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_network);
        kv = new Ui.KvList((LinearLayout) findViewById(R.id.kvBox));
        txtNetHeadline = findViewById(R.id.txtNetHeadline);
        txtTraffic = findViewById(R.id.txtTraffic);
        txtPing = findViewById(R.id.txtPing);
        txtSpeed = findViewById(R.id.txtSpeed);
        btnPing = findViewById(R.id.btnPing);
        btnSpeed = findViewById(R.id.btnSpeed);
        graph = findViewById(R.id.graph);
        graph.setRange(0, maxSeenKbs);
        graph.setColor(Palette.BLUE);

        txtPing.setText("Nenhum teste executado.");
        txtSpeed.setText("Nenhum teste executado.");

        btnPing.setOnClickListener(v -> runPing());
        btnSpeed.setOnClickListener(v -> runSpeed());

        findViewById(R.id.btnPanel).setOnClickListener(v -> Nav.open(this,
                Build.VERSION.SDK_INT >= 29 ? new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY) : null,
                new Intent(Settings.ACTION_WIRELESS_SETTINGS), Nav.settings()));
        findViewById(R.id.btnWifi).setOnClickListener(v -> Nav.open(this,
                new Intent(Settings.ACTION_WIFI_SETTINGS), Nav.settings()));
        findViewById(R.id.btnDataUsage).setOnClickListener(v -> Nav.open(this,
                new Intent(Settings.ACTION_DATA_USAGE_SETTINGS),
                new Intent(Settings.ACTION_WIRELESS_SETTINGS), Nav.settings()));
        findViewById(R.id.btnNetSettings).setOnClickListener(v -> Nav.open(this,
                new Intent(Settings.ACTION_WIRELESS_SETTINGS), Nav.settings()));
    }

    @Override
    protected void onResume() {
        super.onResume();
        lastRx = -1;
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }

    // ------------------------------------------------------------ Estado

    private void refreshState() {
        NetTests.State s = NetTests.state(this);
        if (!s.connected) {
            txtNetHeadline.setText("○ Sem conexão");
            txtNetHeadline.setTextColor(Palette.RED);
        } else if (!s.validated) {
            txtNetHeadline.setText("● " + s.transport() + " — sem acesso à internet");
            txtNetHeadline.setTextColor(Palette.AMBER);
        } else {
            txtNetHeadline.setText("● " + s.transport() + " — internet OK");
            txtNetHeadline.setTextColor(Palette.GREEN);
        }

        kv.clear();
        kv.add("Tipo", s.transport());
        if (!s.connected) return;
        kv.add("Internet validada pelo Android", s.validated ? "Sim" : "Não");
        kv.add("Rede tarifada (franquia)", s.metered ? "Sim" : "Não");
        if (s.iface != null) kv.add("Interface", s.iface);
        if (s.wifi) addWifi();
        if (s.cellular) addCellular();
        if (s.downKbps > 0) {
            kv.add("Banda estimada pelo Android",
                    "↓ " + mbps(s.downKbps) + "  ↑ " + mbps(s.upKbps));
        }
        if (!s.dns.isEmpty()) kv.add("Servidores DNS", String.join("\n", s.dns));
        kv.add("DNS privado", s.privateDns
                ? "Ativo" + (s.privateDnsServer != null ? " (" + s.privateDnsServer + ")" : " (automático)")
                : "Desativado");
    }

    private static String mbps(int kbps) {
        if (kbps <= 0) return "—";
        return String.format(Locale.getDefault(), "%.1f Mbps", kbps / 1000f);
    }

    @SuppressWarnings("deprecation")
    private void addWifi() {
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wm == null) return;
        WifiInfo wi = wm.getConnectionInfo();
        if (wi == null) return;
        int rssi = wi.getRssi();
        if (rssi > -127 && rssi < 0) {
            String q = rssi >= -55 ? "excelente" : rssi >= -67 ? "bom" : rssi >= -75 ? "regular" : "fraco";
            kv.add("Sinal Wi-Fi", rssi + " dBm (" + q + ")");
        }
        if (wi.getLinkSpeed() > 0) kv.add("Velocidade do link Wi-Fi", wi.getLinkSpeed() + " Mbps");
        int f = wi.getFrequency();
        if (f > 0) {
            String band = f >= 5925 ? "6 GHz" : f >= 4900 ? "5 GHz" : "2,4 GHz";
            kv.add("Frequência", f + " MHz (" + band + ")");
        }
    }

    private void addCellular() {
        if (Build.VERSION.SDK_INT < 28) return;
        try {
            TelephonyManager tm = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
            SignalStrength ss = tm.getSignalStrength();
            if (ss == null) return;
            String value = ss.getLevel() + "/4";
            if (Build.VERSION.SDK_INT >= 29) {
                List<CellSignalStrength> list = ss.getCellSignalStrengths();
                if (!list.isEmpty()) {
                    int dbm = list.get(0).getDbm();
                    if (dbm != Integer.MAX_VALUE && dbm < 0) value += "  (" + dbm + " dBm)";
                }
            }
            kv.add("Sinal celular", value);
        } catch (SecurityException ignored) {
            kv.add("Sinal celular", "Leitura bloqueada pelo sistema");
        }
    }

    // ------------------------------------------------------------ Tráfego

    private void refreshTraffic() {
        long rx = TrafficStats.getTotalRxBytes();
        long tx = TrafficStats.getTotalTxBytes();
        long now = SystemClock.elapsedRealtime();
        if (rx == TrafficStats.UNSUPPORTED || tx == TrafficStats.UNSUPPORTED) {
            txtTraffic.setText("O aparelho não informa o tráfego de rede.");
            return;
        }
        if (lastRx >= 0 && now > lastTime) {
            float sec = (now - lastTime) / 1000f;
            float down = (rx - lastRx) / 1024f / sec;
            float up = (tx - lastTx) / 1024f / sec;
            txtTraffic.setText(String.format(Locale.getDefault(), "↓ %.1f KB/s    ↑ %.1f KB/s", down, up));
            if (down > maxSeenKbs) {
                maxSeenKbs = down * 1.2f;
                graph.setRange(0, maxSeenKbs);
            }
            graph.addPoint(down);
        }
        lastRx = rx;
        lastTx = tx;
        lastTime = now;
    }

    // ------------------------------------------------------------ Testes

    private void runPing() {
        btnPing.setEnabled(false);
        txtPing.setText("Testando… (cerca de 15 s)");
        runBg(() -> {
            long dns = NetTests.dnsMs("www.google.com");
            NetTests.Ping[] results = {
                    NetTests.tcpPing("Cloudflare", "1.1.1.1", 443, 10, 2000),
                    NetTests.tcpPing("Google DNS", "8.8.8.8", 53, 10, 2000),
                    NetTests.tcpPing("Google", "www.google.com", 443, 10, 2000)
            };
            StringBuilder sb = new StringBuilder();
            sb.append("Resolução DNS: ").append(dns >= 0 ? dns + " ms" : "falhou").append("\n\n");
            double best = -1, bestJitter = 0;
            int bestLoss = 100;
            for (NetTests.Ping p : results) {
                sb.append(p.name).append('\n');
                if (p.received == 0) {
                    sb.append("  ").append(p.error != null ? p.error : "Sem resposta").append("\n\n");
                    continue;
                }
                sb.append(String.format(Locale.getDefault(),
                        "  média %.0f ms  (mín %d / máx %d)%n  jitter %.0f ms  •  perda %d%%%n%n",
                        p.avg, p.min, p.max, p.jitter, p.lossPercent()));
                if (best < 0 || p.avg < best) {
                    best = p.avg;
                    bestJitter = p.jitter;
                    bestLoss = p.lossPercent();
                }
            }
            sb.append("Avaliação: ").append(NetTests.gameRating(best, bestJitter, bestLoss));
            sb.append("\n(O ping dentro de cada jogo depende da distância até o servidor do jogo.)");
            String out = sb.toString();
            ui(() -> {
                btnPing.setEnabled(true);
                txtPing.setText(out);
            });
        });
    }

    private void runSpeed() {
        btnSpeed.setEnabled(false);
        txtSpeed.setText("Conectando ao servidor de teste…");
        runBg(() -> {
            NetTests.Download d = NetTests.download(NetTests.DOWNLOAD_URL, 10_000, (bytes, ms) -> {
                double mbps = bytes * 8.0 / (Math.max(1, ms) * 1000.0);
                ui(() -> txtSpeed.setText(String.format(Locale.getDefault(),
                        "Baixando… %s  •  %.1f Mbps", Format.bytes(bytes), mbps)));
            });
            ui(() -> {
                btnSpeed.setEnabled(true);
                if (d.error != null) {
                    txtSpeed.setText(d.error);
                } else {
                    txtSpeed.setText(String.format(Locale.getDefault(),
                            "Velocidade média: %.1f Mbps%nBaixados %s em %.1f s",
                            d.mbps, Format.bytes(d.bytes), d.millis / 1000f));
                }
            });
        });
    }
}
