package com.gameboosterx.rog.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.PowerManager;
import android.text.format.DateFormat;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gameboosterx.rog.R;
import com.gameboosterx.rog.core.SystemStats;
import com.gameboosterx.rog.data.Session;
import com.gameboosterx.rog.data.SessionDb;
import com.gameboosterx.rog.views.LineChartView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Detalhe de uma sessão com gráficos de FPS e temperatura e comparação com outra sessão. */
public class SessionDetailActivity extends BaseActivity {

    private Session session;
    private Session other;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_session_detail);
        session = SessionDb.get(this).byId(getIntent().getLongExtra("id", -1));
        if (session == null) {
            finish();
            return;
        }
        setupHeader("SESSÃO", session.label);
        findViewById(R.id.btnCompare).setOnClickListener(v -> pickCompare());
        findViewById(R.id.btnDeleteSession).setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Apagar sessão?")
                        .setPositiveButton("Apagar", (d, w) -> { SessionDb.get(this).delete(session.id); finish(); })
                        .setNegativeButton("Cancelar", null)
                        .show());
        render();
    }

    private void render() {
        TextView summary = findViewById(R.id.txtSessionSummary);
        summary.setText(summaryText(session));

        LineChartView fps = findViewById(R.id.chartSessionFps);
        LineChartView temp = findViewById(R.id.chartSessionTemp);
        fps.setEmptyText("FPS não medido nesta sessão");
        temp.setEmptyText("Temperatura indisponível");
        temp.setUnit("°");
        fps.clear();
        temp.clear();
        fps.addSeries(seriesFps(session), RED);
        temp.addSeries(seriesTemp(session), ORANGE);
        if (other != null) {
            fps.addSeries(resample(seriesFps(other), session.samples.size()), CYAN);
            temp.addSeries(resample(seriesTemp(other), session.samples.size()), CYAN);
        }
        TextView legend = findViewById(R.id.txtLegend);
        legend.setText(other == null ? "Vermelho/laranja: esta sessão"
                : "Vermelho/laranja: esta sessão • Ciano: " + other.label + " "
                + DateFormat.format("dd/MM HH:mm", other.start) + " (tempo normalizado)");

        LinearLayout c = findViewById(R.id.sessionContent);
        c.removeAllViews();
        if (other != null) {
            addSection(c, "COMPARAÇÃO");
            addNote(c, compare(session, other));
        }
        addSection(c, "ANÁLISE");
        addNote(c, analysis(session));
    }

    private static String summaryText(Session s) {
        StringBuilder sb = new StringBuilder();
        sb.append(DateFormat.format("dd/MM/yyyy HH:mm", s.start)).append(" • duração ")
                .append(SystemStats.formatDuration(s.durationMs()));
        sb.append("\nTemperatura (bateria): ");
        if (Float.isNaN(s.tempStart)) sb.append("indisponível");
        else sb.append(String.format(Locale.US, "início %.1f°C • fim %.1f°C • máx %.1f°C",
                s.tempStart, Float.isNaN(s.tempEnd) ? s.tempStart : s.tempEnd, s.tempMax));
        int used = s.batteryUsed();
        sb.append("\nBateria: ");
        if (used < 0) sb.append("indisponível");
        else {
            sb.append(s.battStart).append("% → ").append(s.battEnd).append("% (−").append(used).append("%)");
            float d = s.drainPerHour();
            if (!Float.isNaN(d)) sb.append(String.format(Locale.US, " • ~%.0f%%/h", d));
        }
        sb.append("\nFPS: ");
        if (Float.isNaN(s.fpsAvg)) sb.append("não medido (sem fonte confiável)");
        else {
            float sd = s.fpsStdDev();
            sb.append(String.format(Locale.US, "média %.1f • mín %.0f • quedas %d", s.fpsAvg, s.fpsMin, s.drops));
            if (!Float.isNaN(sd)) sb.append(String.format(Locale.US, " • desvio %.1f", sd));
        }
        sb.append("\nEstado térmico máximo: ").append(SystemStats.thermalLabel(s.thermalMax));
        return sb.toString();
    }

    private static String analysis(Session s) {
        List<String> out = new ArrayList<>();
        if (s.thermalMax >= PowerManager.THERMAL_STATUS_SEVERE) {
            out.add("O sistema chegou a limitação térmica severa: quedas de desempenho no fim da sessão são esperadas. Faça pausas.");
        } else if (s.thermalMax == PowerManager.THERMAL_STATUS_MODERATE) {
            out.add("Aquecimento moderado detectado. Reduzir gráficos ou o limite de FPS ajuda a manter o desempenho.");
        }
        if (!Float.isNaN(s.tempStart) && !Float.isNaN(s.tempMax) && s.tempMax - s.tempStart >= 6) {
            out.add(String.format(Locale.US, "A bateria subiu %.1f °C durante a sessão.", s.tempMax - s.tempStart));
        }
        if (!Float.isNaN(s.fpsAvg)) {
            float sd = s.fpsStdDev();
            if (!Float.isNaN(sd) && sd >= 6) {
                out.add("FPS oscilante. Limitar o FPS no jogo perto de " + Math.round(s.fpsAvg) + " deve deixar a experiência mais estável.");
            }
            if (s.drops >= 5) out.add(s.drops + " quedas bruscas de FPS registradas.");
        }
        float d = s.drainPerHour();
        if (!Float.isNaN(d) && d >= 25) {
            out.add(String.format(Locale.US, "Consumo alto (~%.0f%%/h). Brilho menor e FPS limitado reduzem o gasto.", d));
        }
        if (out.isEmpty()) out.add("Sessão sem problemas detectados.");
        StringBuilder sb = new StringBuilder();
        for (String x : out) sb.append("• ").append(x).append('\n');
        return sb.toString().trim();
    }

    private static String compare(Session a, Session b) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US, "Duração: %s vs %s\n",
                SystemStats.formatDuration(a.durationMs()), SystemStats.formatDuration(b.durationMs())));
        sb.append("FPS médio: ").append(f(a.fpsAvg, "%.1f")).append(" vs ").append(f(b.fpsAvg, "%.1f")).append('\n');
        sb.append("FPS mínimo: ").append(f(a.fpsMin, "%.0f")).append(" vs ").append(f(b.fpsMin, "%.0f")).append('\n');
        sb.append("Desvio do FPS: ").append(f(a.fpsStdDev(), "%.1f")).append(" vs ").append(f(b.fpsStdDev(), "%.1f")).append('\n');
        sb.append("Temp. máxima: ").append(f(a.tempMax, "%.1f°C")).append(" vs ").append(f(b.tempMax, "%.1f°C")).append('\n');
        sb.append("Consumo: ").append(f(a.drainPerHour(), "%.0f%%/h")).append(" vs ").append(f(b.drainPerHour(), "%.0f%%/h")).append('\n');
        sb.append("Quedas: ").append(a.drops).append(" vs ").append(b.drops);
        return sb.toString();
    }

    private static String f(float v, String fmt) {
        return Float.isNaN(v) ? "N/D" : String.format(Locale.US, fmt, v);
    }

    private static float[] seriesFps(Session s) {
        float[] a = new float[s.samples.size()];
        for (int i = 0; i < a.length; i++) a[i] = s.samples.get(i).fps;
        return a;
    }

    private static float[] seriesTemp(Session s) {
        float[] a = new float[s.samples.size()];
        for (int i = 0; i < a.length; i++) a[i] = s.samples.get(i).tempC;
        return a;
    }

    /** Reamostra para o mesmo número de pontos, comparando em tempo proporcional. */
    private static float[] resample(float[] src, int n) {
        if (n < 2 || src.length < 2) return src;
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            int j = Math.round(i * (src.length - 1) / (float) (n - 1));
            out[i] = src[j];
        }
        return out;
    }

    private void pickCompare() {
        List<Session> all = SessionDb.get(this).list(100, false);
        List<Session> opts = new ArrayList<>();
        for (Session s : all) if (s.id != session.id) opts.add(s);
        if (opts.isEmpty()) {
            new AlertDialog.Builder(this).setMessage("Não há outras sessões para comparar.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        String[] names = new String[opts.size()];
        for (int i = 0; i < names.length; i++) {
            Session s = opts.get(i);
            names[i] = s.label + " • " + DateFormat.format("dd/MM HH:mm", s.start) + " • "
                    + SystemStats.formatDuration(s.durationMs());
        }
        new AlertDialog.Builder(this)
                .setTitle("Comparar com…")
                .setItems(names, (d, which) -> {
                    other = SessionDb.get(this).byId(opts.get(which).id);
                    render();
                })
                .show();
    }
}
