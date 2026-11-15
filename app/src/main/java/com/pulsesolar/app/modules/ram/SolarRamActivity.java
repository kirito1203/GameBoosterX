package com.pulsesolar.app.modules.ram;

import android.app.ActivityManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Debug;
import android.os.Process;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.pulsesolar.app.BaseActivity;
import com.pulsesolar.app.R;
import com.pulsesolar.app.ui.LineGraph;
import com.pulsesolar.app.ui.RingGauge;
import com.pulsesolar.app.core.Format;
import com.pulsesolar.app.core.Nav;
import com.pulsesolar.app.core.Palette;
import com.pulsesolar.app.core.SystemInfo;
import com.pulsesolar.app.core.Ui;

/** Módulo 3 — Solar RAM: memória disponível e utilizada, atualizada a cada segundo. */
public class SolarRamActivity extends BaseActivity {

    private RingGauge gauge;
    private LineGraph graph;
    private TextView headline;
    private Ui.KvList kv;
    private ActivityManager am;
    private long lastPssBytes = -1;
    private int ticks = 0;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ram);
        gauge = findViewById(R.id.gauge);
        graph = findViewById(R.id.graph);
        headline = findViewById(R.id.txtHeadline);
        kv = new Ui.KvList((LinearLayout) findViewById(R.id.kvBox));
        am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        graph.setRange(0, 100);
        gauge.setCaption("em uso");

        findViewById(R.id.btnApps).setOnClickListener(v -> Nav.open(this,
                new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS), Nav.settings()));
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }

    private void refresh() {
        SystemInfo.Ram r = SystemInfo.ram(this);
        float u = r.usedPercent();
        int color = Palette.forLoad(u);
        gauge.setProgress(u);
        gauge.setCenterText(Math.round(u) + "%");
        gauge.setColor(color);
        graph.setColor(color);
        graph.addPoint(u);
        headline.setText(Format.bytes(r.avail) + " disponíveis de " + Format.bytes(r.total));

        // O Android limita a frequência dessa consulta; atualizamos a cada 10 s.
        if (ticks++ % 10 == 0) {
            Debug.MemoryInfo[] mi = am.getProcessMemoryInfo(new int[]{Process.myPid()});
            if (mi != null && mi.length > 0) lastPssBytes = mi[0].getTotalPss() * 1024L;
        }

        kv.clear();
        kv.add("Total", Format.bytes(r.total));
        kv.add("Disponível", Format.bytes(r.avail) + " (" + Format.percent(r.availPercent()) + ")");
        kv.add("Em uso", Format.bytes(r.used()) + " (" + Format.percent(u) + ")");
        kv.add("Limite de memória baixa", Format.bytes(r.threshold));
        kv.add("Estado do sistema", r.low ? "MEMÓRIA BAIXA" : "Normal");
        kv.add("Uso deste app", Format.bytes(lastPssBytes));
        kv.add("Limite por app", am.getMemoryClass() + " MB (estendido: " + am.getLargeMemoryClass() + " MB)");
        kv.add("Aparelho com pouca RAM?", am.isLowRamDevice() ? "Sim (modo Go)" : "Não");
    }
}
