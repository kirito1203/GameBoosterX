package com.gameboosterx.rog.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.widget.LinearLayout;

import com.gameboosterx.rog.R;
import com.gameboosterx.rog.core.Perms;
import com.gameboosterx.rog.core.SystemStats;
import com.gameboosterx.rog.data.Session;
import com.gameboosterx.rog.data.SessionDb;
import com.gameboosterx.rog.service.BoostService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** GAME SESSION ANALYZER — histórico de sessões. */
public class SessionsActivity extends BaseActivity {

    private LinearLayout content;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_module);
        setupHeader("SESSÕES", "Game Session Analyzer");
        content = findViewById(R.id.content);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        content.removeAllViews();
        List<Session> list = SessionDb.get(this).list(200, false);

        if (BoostService.LIVE.sessionLabel != null) {
            addNote(content, "● Gravando agora: " + BoostService.LIVE.sessionLabel + " ("
                    + SystemStats.formatDuration(System.currentTimeMillis() - BoostService.LIVE.sessionStart) + ")");
        } else if (!BoostService.LIVE.running || !Perms.usageStats(this)) {
            addNote(content, "As sessões são gravadas automaticamente quando o monitor (Game Sidebar ou Turbo X) está ligado, "
                    + "o Acesso de Uso foi concedido e você abre um jogo marcado em Meus Jogos. Sessões com menos de 1 minuto são descartadas.");
        }

        if (list.isEmpty()) {
            addNote(content, "Nenhuma sessão gravada ainda.");
            return;
        }

        addSection(content, "RESUMO POR JOGO");
        Map<String, long[]> per = new LinkedHashMap<>(); // [count, totalMs, battUsed]
        Map<String, String> labels = new LinkedHashMap<>();
        for (Session s : list) {
            long[] a = per.get(s.pkg);
            if (a == null) { a = new long[3]; per.put(s.pkg, a); labels.put(s.pkg, s.label); }
            a[0]++;
            a[1] += s.durationMs();
            if (s.batteryUsed() > 0) a[2] += s.batteryUsed();
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, long[]> e : per.entrySet()) {
            long[] a = e.getValue();
            sb.append(String.format(Locale.US, "• %s: %d sessões • %s • bateria usada %d%%\n",
                    labels.get(e.getKey()), a[0], SystemStats.formatDuration(a[1]), a[2]));
        }
        addNote(content, sb.toString().trim());

        addSection(content, "HISTÓRICO");
        for (Session s : list) {
            String fps = Float.isNaN(s.fpsAvg) ? "FPS N/D" : String.format(Locale.US, "FPS %.0f (mín %.0f)", s.fpsAvg, s.fpsMin);
            String temp = Float.isNaN(s.tempStart) ? "temp N/D"
                    : String.format(Locale.US, "%.1f→%.1f°C", s.tempStart, Float.isNaN(s.tempEnd) ? s.tempStart : s.tempEnd);
            int used = s.batteryUsed();
            final long id = s.id;
            addRow(content, s.label + "  •  " + DateFormat.format("dd/MM HH:mm", s.start),
                    SystemStats.formatDuration(s.durationMs()) + " • " + fps + " • " + temp
                            + (used >= 0 ? " • -" + used + "%" : "") + (s.drops > 0 ? " • " + s.drops + " quedas" : ""),
                    null, v -> startActivity(new Intent(this, SessionDetailActivity.class).putExtra("id", id)));
        }

        addSection(content, "GERENCIAR");
        addRow(content, "Apagar histórico", "Remove todas as sessões gravadas.", "Apagar", v ->
                new AlertDialog.Builder(this)
                        .setTitle("Apagar histórico?")
                        .setMessage("Todas as sessões serão removidas. Esta ação não pode ser desfeita.")
                        .setPositiveButton("Apagar", (d, w) -> { SessionDb.get(this).clear(); render(); })
                        .setNegativeButton("Cancelar", null)
                        .show());
    }
}
