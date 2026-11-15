package com.pulsesolar.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import com.pulsesolar.app.core.Analyzer;
import com.pulsesolar.app.core.Format;
import com.pulsesolar.app.core.GameModeController;
import com.pulsesolar.app.core.NetTests;
import com.pulsesolar.app.core.Palette;
import com.pulsesolar.app.core.SystemInfo;
import com.pulsesolar.app.modules.boost.SolarBoostActivity;
import com.pulsesolar.app.modules.fps.SessionService;
import com.pulsesolar.app.modules.fps.SolarFpsActivity;
import com.pulsesolar.app.modules.gaming.SolarGamingActivity;
import com.pulsesolar.app.modules.guard.GuardService;
import com.pulsesolar.app.modules.guard.SolarGuardActivity;
import com.pulsesolar.app.modules.intelligence.SolarIntelligenceActivity;
import com.pulsesolar.app.modules.network.SolarNetworkActivity;
import com.pulsesolar.app.modules.ram.SolarRamActivity;
import com.pulsesolar.app.modules.storage.SolarStorageActivity;
import com.pulsesolar.app.ui.RingGauge;
import com.pulsesolar.app.ui.SolarCoreView;

import java.util.List;

/** Tela inicial: núcleo solar com status real, medidores ao vivo e os 7 módulos. */
public class MainActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SolarCoreView solarCore;
    private RingGauge gRam, gSto, gTemp;
    private TextView subGuard, subRam, subStorage, subNetwork, subGaming, subIntel, subFps;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 2000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        solarCore = findViewById(R.id.solarCore);
        gRam = findViewById(R.id.gaugeRam);
        gSto = findViewById(R.id.gaugeStorage);
        gTemp = findViewById(R.id.gaugeTemp);
        subGuard = findViewById(R.id.subGuard);
        subRam = findViewById(R.id.subRam);
        subStorage = findViewById(R.id.subStorage);
        subNetwork = findViewById(R.id.subNetwork);
        subGaming = findViewById(R.id.subGaming);
        subIntel = findViewById(R.id.subIntel);
        subFps = findViewById(R.id.subFps);

        TextView device = findViewById(R.id.txtDevice);
        device.setText(Build.MANUFACTURER + " " + Build.MODEL
                + "  •  Android " + Build.VERSION.RELEASE
                + "  •  RAM " + Format.bytes(SystemInfo.ram(this).total)
                + "  •  " + Format.bytes(SystemInfo.storage().total));

        gRam.setCaption("RAM");
        gSto.setCaption("Armaz.");
        gTemp.setCaption("Bateria °C");

        bind(R.id.tileBoost, SolarBoostActivity.class);
        bind(R.id.tileGuard, SolarGuardActivity.class);
        bind(R.id.tileRam, SolarRamActivity.class);
        bind(R.id.tileStorage, SolarStorageActivity.class);
        bind(R.id.tileNetwork, SolarNetworkActivity.class);
        bind(R.id.tileGaming, SolarGamingActivity.class);
        bind(R.id.tileIntel, SolarIntelligenceActivity.class);
        bind(R.id.tileFps, SolarFpsActivity.class);
        bind(R.id.solarCore, SolarIntelligenceActivity.class);
        bind(R.id.gaugeRam, SolarRamActivity.class);
        bind(R.id.gaugeStorage, SolarStorageActivity.class);
        bind(R.id.gaugeTemp, SolarGuardActivity.class);
    }

    private void bind(int id, Class<?> cls) {
        findViewById(id).setOnClickListener(v -> startActivity(new Intent(this, cls)));
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
        float ru = r.usedPercent();
        gRam.setProgress(ru);
        gRam.setCenterText(Math.round(ru) + "%");
        gRam.setColor(Palette.forLoad(ru));
        subRam.setText(Format.bytes(r.avail) + " livres");

        SystemInfo.Storage s = SystemInfo.storage();
        float su = s.usedPercent();
        gSto.setProgress(su);
        gSto.setCenterText(Math.round(su) + "%");
        gSto.setColor(Palette.forLoad(su));
        subStorage.setText(Format.bytes(s.free) + " livres");

        SystemInfo.Battery b = SystemInfo.battery(this);
        if (!Float.isNaN(b.tempC)) {
            gTemp.setProgress((b.tempC - 20f) * 100f / 30f); // escala 20–50 °C
            gTemp.setCenterText(Math.round(b.tempC) + "°");
            gTemp.setColor(Palette.forTemp(b.tempC));
        }
        subGuard.setText((b.level >= 0 ? b.level + "%" : "—")
                + (GuardService.running ? "  •  monitor ativo" : "  •  monitor desligado"));

        NetTests.State ns = NetTests.state(this);
        subNetwork.setText(ns.transport() + (ns.connected ? (ns.validated ? " • internet OK" : " • sem internet") : ""));

        subGaming.setText(GameModeController.isActive(this) ? "● Modo Jogos ATIVO" : "Assistente para jogos");
        subFps.setText(SessionService.running
                ? "● Gravando sessão" + (SessionService.runningLabel != null ? ": " + SessionService.runningLabel : "")
                : "Pulso do sistema, gargalos, perfis e relatórios de sessão");

        List<Analyzer.Finding> list = Analyzer.analyze(this, false);
        int crit = 0, warn = 0;
        for (Analyzer.Finding f : list) {
            if (f.severity == Analyzer.CRIT) crit++;
            else if (f.severity == Analyzer.WARN) warn++;
        }
        if (crit == 0 && warn == 0) {
            solarCore.setStatus("ESTÁVEL", "Nenhum problema detectado agora", Palette.GREEN);
            subIntel.setText("Nenhuma causa de travamento detectada");
        } else {
            int n = crit + warn;
            int color = crit > 0 ? Palette.RED : Palette.AMBER;
            solarCore.setStatus(n + (n == 1 ? " ALERTA" : " ALERTAS"), list.get(0).title + " — toque para ver", color);
            subIntel.setText(list.get(0).title);
        }
    }
}
