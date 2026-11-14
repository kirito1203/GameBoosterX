package com.gameboosterx.rog.ui;

import android.content.Intent;
import android.os.BatteryManager;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gameboosterx.rog.R;
import com.gameboosterx.rog.core.Perms;
import com.gameboosterx.rog.core.SystemStats;
import com.gameboosterx.rog.data.Session;
import com.gameboosterx.rog.data.SessionDb;
import com.gameboosterx.rog.views.GaugeView;

import java.util.List;
import java.util.Locale;

/** SMART BATTERY GAMING — consumo, autonomia estimada e temperatura da bateria. */
public class BatteryActivity extends BaseActivity {

    private GaugeView gauge;
    private TextView details, estimate;
    private float currentAvg = Float.NaN;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_battery);
        setupHeader("SMART BATTERY", "Bateria durante os jogos");
        gauge = findViewById(R.id.gaugeBat);
        gauge.setLabel("CARGA");
        details = findViewById(R.id.txtBatDetails);
        estimate = findViewById(R.id.txtBatEstimate);

        LinearLayout c = findViewById(R.id.batteryContent);
        addSection(c, "AUTONOMIA JOGANDO (SUAS SESSÕES)");
        addNote(c, sessionsEstimate());

        addSection(c, "ECONOMIZAR ENERGIA NOS JOGOS");
        addNote(c, "• Brilho: é o maior consumidor na maioria das partidas. 60–70% costuma bastar em ambientes internos.\n"
                + "• Limite de FPS: 30 FPS gasta bem menos que 60 em jogos pesados (Genshin, CODM).\n"
                + "• Taxa de atualização: se o jogo roda a 60 FPS, a tela em 120 Hz quase não traz ganho.\n"
                + "• Wi-Fi gasta menos que dados móveis, principalmente com sinal fraco.\n"
                + "• Fones com fio gastam menos que Bluetooth.\n"
                + "• Jogar carregando aquece a bateria e acelera seu desgaste.");
        addRow(c, "Economia de bateria do sistema", "Use fora dos jogos; durante o jogo ela reduz o desempenho.",
                "Abrir", v -> Perms.openSetting(this, Settings.ACTION_BATTERY_SAVER_SETTINGS));
        addRow(c, "Uso da bateria por app", "Veja quais apps mais consumiram.",
                "Abrir", v -> Perms.open(this, new Intent(Intent.ACTION_POWER_USAGE_SUMMARY)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        ui.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(tick);
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!resumed) return;
            update();
            ui.postDelayed(this, 2000);
        }
    };

    private void update() {
        SystemStats.BatteryInfo b = SystemStats.battery(this);
        if (b.level >= 0) {
            gauge.setValue(b.level / 100f, b.level + "%", b.level <= 15 ? RED : b.level <= 30 ? ORANGE : GREEN);
        } else {
            gauge.setValue(Float.NaN, "N/D", GREY);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Status: ").append(b.charging ? "carregando" : "descarregando");
        if (b.plugged == BatteryManager.BATTERY_PLUGGED_USB) sb.append(" (USB)");
        else if (b.plugged == BatteryManager.BATTERY_PLUGGED_AC) sb.append(" (tomada)");
        sb.append("\nTemperatura da bateria: ")
                .append(b.hasTemp() ? String.format(Locale.US, "%.1f °C", b.tempC) : "indisponível");
        if (b.voltageMv > 0) sb.append(String.format(Locale.US, "\nTensão: %.2f V", b.voltageMv / 1000f));
        if (!Float.isNaN(b.currentMa)) {
            currentAvg = Float.isNaN(currentAvg) ? b.currentMa : currentAvg * 0.7f + b.currentMa * 0.3f;
            sb.append(String.format(Locale.US, "\nCorrente: %.0f mA %s", Math.abs(currentAvg),
                    currentAvg < 0 || !b.charging ? "(consumo)" : "(entrada)"));
        } else {
            sb.append("\nCorrente: não informada pelo aparelho");
        }
        sb.append("\nSaúde: ").append(health(b.health));
        details.setText(sb.toString());

        // Estimativa instantânea: carga restante (µAh) ÷ corrente atual
        String est;
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        int chargeUah = bm != null ? bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) : Integer.MIN_VALUE;
        if (!b.charging && !Float.isNaN(currentAvg) && Math.abs(currentAvg) > 50
                && chargeUah != Integer.MIN_VALUE && chargeUah > 0) {
            float mah = chargeUah > 100_000 ? chargeUah / 1000f : chargeUah; // alguns aparelhos já reportam mAh
            float hours = mah / Math.abs(currentAvg);
            est = String.format(Locale.US, "Com o consumo de AGORA: ~%s restantes (%.0f mAh ÷ %.0f mA).\n"
                            + "Abra esta tela durante/logo após um jogo para uma estimativa de jogo.",
                    SystemStats.formatDuration((long) (hours * 3600_000L)), mah, Math.abs(currentAvg));
        } else if (b.charging) {
            est = "Carregando — a estimativa aparece quando o carregador for desconectado.";
        } else {
            est = "O aparelho não informa carga/corrente suficientes para estimativa instantânea. Use a estimativa pelas sessões abaixo.";
        }
        estimate.setText(est);
    }

    private String sessionsEstimate() {
        List<Session> list = SessionDb.get(this).list(30, false);
        float sum = 0;
        int n = 0;
        for (Session s : list) {
            float d = s.drainPerHour();
            if (!Float.isNaN(d) && d > 0) { sum += d; n++; }
        }
        if (n == 0) return "Grave sessões de pelo menos 5 minutos para estimar quanto tempo a bateria dura jogando.";
        float avg = sum / n;
        int level = SystemStats.battery(this).level;
        String base = String.format(Locale.US, "Consumo médio jogando: ~%.0f%% por hora (%d sessões).", avg, n);
        if (level > 0 && avg > 0) {
            float hours = level / avg;
            base += "\nCom " + level + "% agora: ~" + SystemStats.formatDuration((long) (hours * 3600_000L)) + " de jogo.";
        }
        return base;
    }

    private static String health(int h) {
        switch (h) {
            case BatteryManager.BATTERY_HEALTH_GOOD: return "boa";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: return "superaquecida";
            case BatteryManager.BATTERY_HEALTH_DEAD: return "esgotada";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return "sobretensão";
            case BatteryManager.BATTERY_HEALTH_COLD: return "fria";
            default: return "não informada";
        }
    }
}
