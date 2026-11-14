package com.gameboosterx.rog.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.gameboosterx.rog.R;
import com.gameboosterx.rog.core.Perms;
import com.gameboosterx.rog.data.Session;
import com.gameboosterx.rog.data.SessionDb;
import com.gameboosterx.rog.service.BoostService;
import com.gameboosterx.rog.views.LineChartView;

import java.util.List;
import java.util.Locale;

/**
 * FPS STABILIZER — monitoramento e análise de estabilidade.
 * Os valores vêm do SurfaceFlinger (ver FpsMonitor). Sem fonte confiável,
 * a tela mostra "indisponível" em vez de números inventados.
 */
public class FpsActivity extends BaseActivity {

    private static final String GRANT_CMD = "adb shell pm grant com.gameboosterx.rog android.permission.DUMP";

    private TextView big, source;
    private LineChartView chartFps, chartTemp;
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_fps);
        setupHeader("FPS STABILIZER", "Monitor de quadros e estabilidade");
        big = findViewById(R.id.txtFpsBig);
        source = findViewById(R.id.txtFpsSource);
        chartFps = findViewById(R.id.chartFps);
        chartTemp = findViewById(R.id.chartTemp);
        content = findViewById(R.id.fpsContent);
        chartFps.setEmptyText("Sem medição de FPS");
        chartTemp.setEmptyText("Sem leitura de temperatura");
        chartTemp.setUnit("°");
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderStatic();
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
            BoostService.Live L = BoostService.LIVE;
            float fps = L.running ? L.fps : Float.NaN;
            big.setText(Float.isNaN(fps) ? "N/D" : String.format(Locale.US, "%.0f FPS", fps));
            big.setTextColor(Float.isNaN(fps) ? GREY : RED);
            String fg = L.sessionLabel != null ? L.sessionLabel : L.foreground;
            if (!Perms.dump(FpsActivity.this)) {
                source.setText("Fonte: indisponível — o Android não libera o FPS de outros apps sem a permissão DUMP.");
            } else if (!L.running) {
                source.setText("Fonte: SurfaceFlinger (pronta). Ligue a Game Sidebar ou o Turbo X para medir durante o jogo.");
            } else {
                source.setText("Fonte: SurfaceFlinger • " + (fg != null ? fg : "—")
                        + (L.fpsError != null ? " • " + L.fpsError : ""));
            }
            chartFps.clear();
            chartFps.addSeries(L.copyFps(), RED);
            chartTemp.clear();
            chartTemp.addSeries(L.copyTemp(), ORANGE);
            ui.postDelayed(this, 1000);
        }
    };

    private void renderStatic() {
        content.removeAllViews();

        addSection(content, "FONTE DE DADOS");
        if (Perms.dump(this)) {
            addNote(content, "✓ Medição real habilitada (permissão DUMP concedida). O FPS é calculado a partir dos "
                    + "quadros efetivamente exibidos pelo compositor do Android.");
        } else {
            addNote(content, "O Android 16 não oferece API pública para ler o FPS de outro app. A única fonte confiável sem root "
                    + "é o compositor do sistema, que exige a permissão DUMP — você mesmo pode concedê-la UMA vez:\n\n"
                    + "1. Ative as Opções do desenvolvedor (Sobre o telefone ▸ toque 7x em Versão).\n"
                    + "2. Ative a Depuração por Wi-Fi.\n"
                    + "3. Instale o app LADB (ou use um PC com ADB) e execute o comando abaixo.\n"
                    + "4. Volte aqui: o status muda para habilitado.\n\n"
                    + "Sem isso, o restante do app funciona normalmente e o FPS aparece como N/D.");
            addRow(content, "Comando", GRANT_CMD, "Copiar", v -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("cmd", GRANT_CMD));
                Toast.makeText(this, "Comando copiado", Toast.LENGTH_SHORT).show();
            });
        }

        addSection(content, "ESTABILIDADE (ÚLTIMOS 2 MIN)");
        float[] h = BoostService.LIVE.copyFps();
        float sum = 0, min = Float.NaN, max = Float.NaN;
        int n = 0, drops = 0;
        float prev = Float.NaN;
        for (float v : h) {
            if (Float.isNaN(v) || v <= 0) continue;
            sum += v; n++;
            if (Float.isNaN(min) || v < min) min = v;
            if (Float.isNaN(max) || v > max) max = v;
            if (!Float.isNaN(prev) && prev - v >= 10 && v < prev * 0.75f) drops++;
            prev = v;
        }
        if (n < 3) {
            addNote(content, "Ainda não há medições suficientes. Abra um jogo com o monitor ligado.");
        } else {
            float avg = sum / n;
            float acc = 0;
            for (float v : h) if (!Float.isNaN(v) && v > 0) acc += (v - avg) * (v - avg);
            float sd = (float) Math.sqrt(acc / (n - 1));
            String verdict = sd < 3 ? "Muito estável" : (sd < 6 ? "Estável" : (sd < 10 ? "Oscilando" : "Instável"));
            addNote(content, String.format(Locale.US,
                    "Média %.1f • Mín %.0f • Máx %.0f • Desvio %.1f • Quedas bruscas %d\nAvaliação: %s",
                    avg, min, max, sd, drops, verdict));
            if (sd >= 6) {
                addNote(content, "O FPS está oscilando. Limitar o FPS no jogo a um valor próximo da média ("
                        + Math.round(avg) + ") costuma deixar a experiência mais suave do que um FPS alto instável.");
            }
        }

        addSection(content, "AQUECIMENTO x DESEMPENHO");
        float[] temps = BoostService.LIVE.copyTemp();
        float tFirst = Float.NaN, tLast = Float.NaN;
        for (float t : temps) if (!Float.isNaN(t)) { if (Float.isNaN(tFirst)) tFirst = t; tLast = t; }
        if (Float.isNaN(tFirst)) {
            addNote(content, "Sem leituras de temperatura no período.");
        } else {
            float delta = tLast - tFirst;
            addNote(content, String.format(Locale.US, "Temperatura da bateria: %.1f → %.1f °C (%+.1f °C).%s",
                    tFirst, tLast, delta,
                    delta >= 2 ? "\nSubida rápida: se o FPS cair junto, é provável limitação térmica. Reduza gráficos ou pause."
                            : ""));
        }

        addSection(content, "COMPARAR SESSÕES");
        List<Session> list = SessionDb.get(this).list(2, false);
        if (list.size() < 2) {
            addNote(content, "Grave ao menos duas sessões para comparar.");
        } else {
            Session a = list.get(0), c = list.get(1);
            addNote(content, compareLine(a) + "\n" + compareLine(c));
        }
        addRow(content, "Histórico completo", "Gráficos e comparação detalhada de sessões.", "Abrir",
                v -> startActivity(new Intent(this, SessionsActivity.class)));

        addSection(content, "RECOMENDAÇÕES");
        addNote(content, "• Defina um limite de FPS no jogo que ele mantenha em 90% do tempo.\n"
                + "• Quedas que coincidem com aumento de temperatura indicam limitação térmica.\n"
                + "• Quedas isoladas em jogos online costumam ser carregamento de mapa ou rede, não o aparelho.\n"
                + "• Este app não desbloqueia FPS: o máximo é definido pelo jogo e pela tela.");
    }

    private static String compareLine(Session s) {
        String fps = Float.isNaN(s.fpsAvg) ? "FPS N/D" : String.format(Locale.US, "FPS médio %.0f (mín %.0f)", s.fpsAvg, s.fpsMin);
        String temp = Float.isNaN(s.tempMax) ? "temp N/D" : String.format(Locale.US, "temp. máx %.1f°C", s.tempMax);
        return "• " + s.label + ": " + fps + " • " + temp + " • quedas " + s.drops;
    }
}
