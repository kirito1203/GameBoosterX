package com.gameboosterx.rog.ui;

import android.os.Bundle;
import android.os.PowerManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gameboosterx.rog.R;
import com.gameboosterx.rog.core.Prefs;
import com.gameboosterx.rog.core.SystemStats;
import com.gameboosterx.rog.service.BoostService;
import com.gameboosterx.rog.views.GaugeView;
import com.gameboosterx.rog.views.LineChartView;

import java.util.Locale;

/**
 * THERMAL CONTROL — leitura dos sensores acessíveis:
 *  - temperatura da bateria (API pública, sempre disponível);
 *  - estado térmico do sistema e margem até o throttling (PowerManager);
 *  - zonas térmicas do kernel, somente se o SELinux permitir (normalmente não).
 * O app apenas monitora e recomenda; nunca interfere nas proteções térmicas.
 */
public class ThermalActivity extends BaseActivity {

    private GaugeView gauge;
    private TextView details, advice;
    private LineChartView chart;
    private final float[] local = new float[90];
    private int localCount;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_thermal);
        setupHeader("THERMAL CONTROL", "Monitor térmico");
        gauge = findViewById(R.id.gaugeThermal);
        gauge.setLabel("BATERIA");
        details = findViewById(R.id.txtThermalDetails);
        advice = findViewById(R.id.txtThermalAdvice);
        chart = findViewById(R.id.chartThermal);
        chart.setUnit("°");
        chart.setEmptyText("Coletando…");

        LinearLayout c = findViewById(R.id.thermalContent);
        addSection(c, "SENSORES");
        addNote(c, "• Temperatura da bateria: sensor público, disponível em todos os Android.\n"
                + "• Estado térmico e margem: informados pelo próprio sistema (Android 10/11+).\n"
                + "• Temperatura da CPU/GPU: o Android 16 bloqueia a leitura direta para apps sem privilégios. "
                + "Se o aparelho permitir, ela aparece acima como \"zona térmica\".");
        addSection(c, "PROTEÇÃO");
        addNote(c, "O Game Booster X nunca desativa ou contorna as proteções térmicas. Elas evitam danos à bateria e ao processador.");
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
        SystemStats.ThermalInfo t = SystemStats.thermal(this);
        float warn = Prefs.tempWarn(this);
        int lvl = SystemStats.heatLevel(b, t, warn);
        int col = lvl >= 3 ? RED : lvl == 2 ? ORANGE : lvl == 1 ? 0xFFFFD600 : CYAN;
        if (b.hasTemp()) gauge.setValue((b.tempC - 20f) / 30f, String.format(Locale.US, "%.1f°", b.tempC), col);
        else gauge.setValue(Float.NaN, "N/D", GREY);

        StringBuilder sb = new StringBuilder();
        sb.append("Estado térmico do sistema: ").append(SystemStats.thermalLabel(t.status));
        if (!Float.isNaN(t.headroom)) {
            sb.append(String.format(Locale.US, "\nMargem até limitação: %.0f%%", Math.max(0, (1 - t.headroom) * 100)));
        } else {
            sb.append("\nMargem até limitação: não informada pelo aparelho");
        }
        if (!Float.isNaN(t.zoneMaxC)) sb.append(String.format(Locale.US, "\nZona térmica (kernel): %.1f °C", t.zoneMaxC));
        sb.append(b.charging ? "\nCarregando: sim" : "\nCarregando: não");
        sb.append(String.format(Locale.US, "\nLimite de alerta configurado: %.0f °C", warn));
        details.setText(sb.toString());

        String adv;
        if (lvl >= 3 || t.status >= PowerManager.THERMAL_STATUS_SEVERE) {
            adv = "⛔ PAUSE AGORA. O aparelho está muito quente e o sistema já deve estar limitando o desempenho. "
                    + "Saia do jogo por 5–10 minutos, remova a capa e desconecte o carregador.";
        } else if (lvl == 2) {
            adv = "⚠ Aquecendo. Reduza gráficos ou o limite de FPS e considere uma pausa curta ao fim da partida.";
        } else if (lvl == 1) {
            adv = "Temperatura subindo, ainda em faixa segura. Evite jogar carregando.";
        } else {
            adv = "✓ Temperatura normal.";
        }
        advice.setText(adv);
        advice.setTextColor(col);

        // Histórico: usa o do serviço se estiver rodando, senão coleta localmente
        float[] series;
        if (BoostService.LIVE.running && BoostService.LIVE.historyCount > 2) {
            series = BoostService.LIVE.copyTemp();
        } else {
            System.arraycopy(local, 1, local, 0, local.length - 1);
            local[local.length - 1] = b.tempC;
            if (localCount < local.length) localCount++;
            series = new float[localCount];
            System.arraycopy(local, local.length - localCount, series, 0, localCount);
        }
        chart.clear();
        chart.addSeries(series, col);
    }
}
