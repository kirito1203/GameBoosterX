package com.pulsesolar.app.modules.fps;

import android.os.Bundle;
import android.os.PowerManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.pulsesolar.app.BaseActivity;
import com.pulsesolar.app.R;
import com.pulsesolar.app.core.Analyzer;
import com.pulsesolar.app.core.Format;
import com.pulsesolar.app.core.Palette;
import com.pulsesolar.app.core.SystemInfo;
import com.pulsesolar.app.core.Ui;
import com.pulsesolar.app.ui.LineGraph;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Relatório de uma sessão: antes, durante, gráficos, gargalos e alertas. */
public class SessionReportActivity extends BaseActivity {

    public static final String EXTRA_ID = "session_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fps_report);
        long id = getIntent().getLongExtra(EXTRA_ID, -1);
        findViewById(R.id.btnDelete).setOnClickListener(v -> runBg(() -> {
            SessionStore.delete(getApplicationContext(), id);
            ui(() -> {
                toast("Relatório apagado");
                finish();
            });
        }));
        runBg(() -> {
            Session s = SessionStore.get(getApplicationContext(), id);
            ui(() -> {
                if (s == null) {
                    toast("Relatório não encontrado");
                    finish();
                } else {
                    show(s);
                }
            });
        });
    }

    private static String temp(float t) {
        return Float.isNaN(t) ? "—" : Format.one(t) + " °C";
    }

    private void show(Session s) {
        Ui.KvList sum = new Ui.KvList((LinearLayout) findViewById(R.id.kvSummary));
        sum.add("Jogo", s.label != null ? s.label : "Não identificado");
        sum.add("Início", DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(s.start)));
        sum.add("Duração", Format.duration(s.durationMs()));
        sum.add("Amostras", String.valueOf(s.samples.size()));
        sum.add("Limite de temperatura", s.tempLimit + " °C");

        LinearLayout fb = findViewById(R.id.findingsBox);
        List<Analyzer.Finding> findings = Bottleneck.session(s);
        for (Analyzer.Finding f : findings) Ui.findingRow(fb, f);

        // Antes
        Ui.KvList before = new Ui.KvList((LinearLayout) findViewById(R.id.kvBefore));
        Session.Point b = s.before;
        if (b != null) {
            before.add("RAM livre", Format.bytes(b.ramAvail) + " (" + Format.one(b.ramAvailPct()) + "%)");
            before.add("Armazenamento livre", Float.isNaN(b.storageFreePct) ? "—" : Format.one(b.storageFreePct) + "%");
            before.add("Bateria", b.level + "%" + (b.charging ? " (carregando)" : ""));
            before.add("Temperatura", temp(b.tempC));
            before.add("Estado térmico", SystemInfo.thermalText(b.thermal));
            before.add("Economia de bateria", b.powerSave ? "LIGADA" : "Desligada");
            if (b.capRatio() > 0) before.add("Teto da CPU", Math.round(b.capRatio() * 100) + "% (" + Format.ghz(b.bigCap) + ")");
        }

        // Durante
        Ui.KvList during = new Ui.KvList((LinearLayout) findViewById(R.id.kvDuring));
        List<Session.Point> list = s.samples;
        int n = list.size();
        float[] temps = new float[n];
        float[] stab = new float[n];
        float[] caps = new float[n];
        float tMax = Float.NaN, tSum = 0, fpsSum = 0, fpsMin = Float.NaN, stabSum = 0, minCap = 2f, maxHead = Float.NaN;
        int tN = 0, fpsN = 0, thermalMax = -1, thermalMod = 0, lowMem = 0, capN = 0;
        long minRam = Long.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            Session.Point p = list.get(i);
            temps[i] = Float.isNaN(p.tempC) ? 20f : p.tempC;
            stab[i] = Math.max(0, p.stability);
            float cap = p.capRatio();
            caps[i] = cap > 0 ? cap * 100f : 0f;
            if (!Float.isNaN(p.tempC)) {
                tMax = Float.isNaN(tMax) ? p.tempC : Math.max(tMax, p.tempC);
                tSum += p.tempC;
                tN++;
            }
            if (p.fps >= 0) {
                fpsSum += p.fps;
                stabSum += p.stability;
                fpsMin = Float.isNaN(fpsMin) ? p.fps : Math.min(fpsMin, p.fps);
                fpsN++;
            }
            if (cap > 0) {
                minCap = Math.min(minCap, cap);
                capN++;
            }
            if (!Float.isNaN(p.headroom)) maxHead = Float.isNaN(maxHead) ? p.headroom : Math.max(maxHead, p.headroom);
            thermalMax = Math.max(thermalMax, p.thermal);
            if (p.thermal >= PowerManager.THERMAL_STATUS_MODERATE) thermalMod++;
            if (p.lowMem) lowMem++;
            if (p.ramAvail >= 0) minRam = Math.min(minRam, p.ramAvail);
        }
        if (n == 0) {
            during.add("Dados", "Sessão curta demais");
        } else {
            during.add("Temperatura", "início " + temp(b != null ? b.tempC : Float.NaN)
                    + "\nmáx " + temp(tMax) + "  •  média " + temp(tN > 0 ? tSum / tN : Float.NaN)
                    + "\nfinal " + temp(s.after != null ? s.after.tempC : Float.NaN));
            during.add("Estado térmico máximo", SystemInfo.thermalText(thermalMax));
            during.add("Tempo em alerta térmico", Format.duration(s.durationMs() * thermalMod / n)
                    + " (" + (thermalMod * 100 / n) + "%)");
            if (!Float.isNaN(maxHead)) {
                during.add("Previsão térmica máxima", String.format(Locale.getDefault(), "%.2f (1,00 = limitação)", maxHead));
            }
            if (capN > 0) {
                during.add("Teto mínimo da CPU", Math.round(minCap * 100) + "% do máximo");
            } else {
                during.add("CPU", "Frequências não legíveis neste aparelho");
            }
            during.add("RAM livre mínima", minRam == Long.MAX_VALUE ? "—" : Format.bytes(minRam));
            during.add("Leituras de memória baixa", String.valueOf(lowMem));
            if (fpsN > 0) {
                during.add("Pulso do sistema*", String.format(Locale.getDefault(),
                        "média %.0f q/s  •  mín %.0f q/s\n%.0f%% dos quadros no tempo", fpsSum / fpsN, fpsMin, stabSum / fpsN));
                during.add("* Pulso do sistema", "Medido no Pulse Solar durante o jogo. Não é o FPS do jogo.");
            }
            if (s.frames > 0) {
                during.add("Quadros atrasados / travadas", String.format(Locale.getDefault(), "%.1f%%  •  %d travada(s) > 100 ms",
                        s.lateFrames * 100f / s.frames, s.stalls));
            }
            if (b != null && s.after != null && b.level >= 0 && s.after.level >= 0) {
                String bat = b.level + "% → " + s.after.level + "%";
                long ms = s.durationMs();
                if (!b.charging && !s.after.charging && ms >= 5 * 60 * 1000L && b.level > s.after.level) {
                    float perHour = (b.level - s.after.level) * 3_600_000f / ms;
                    bat += String.format(Locale.getDefault(), "\n≈ %.0f%% por hora", perHour);
                }
                during.add("Bateria", bat);
            }
        }

        LineGraph gt = findViewById(R.id.graphTemp);
        gt.setRange(20, 50);
        gt.setColor(Palette.GOLD);
        gt.setSeries(temps);
        LineGraph gs = findViewById(R.id.graphStability);
        gs.setRange(0, 100);
        gs.setColor(Palette.BLUE);
        gs.setSeries(stab);
        LineGraph gc = findViewById(R.id.graphCap);
        gc.setRange(0, 100);
        gc.setColor(Palette.GREEN);
        gc.setSeries(caps);
        ((TextView) findViewById(R.id.txtCapNote)).setText(capN > 0
                ? "Quedas no teto indicam que o sistema limitou a CPU (calor ou energia)."
                : "Sem dados: o aparelho não permite ler as frequências da CPU.");

        TextView al = findViewById(R.id.txtAlerts);
        if (s.alerts.isEmpty()) {
            al.setText("Nenhum alerta durante a sessão.");
        } else {
            al.setText(String.join("\n\n", s.alerts));
        }
    }
}
