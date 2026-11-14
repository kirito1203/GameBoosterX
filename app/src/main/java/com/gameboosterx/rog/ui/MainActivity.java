package com.gameboosterx.rog.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.gameboosterx.rog.R;
import com.gameboosterx.rog.core.Perms;
import com.gameboosterx.rog.core.Prefs;
import com.gameboosterx.rog.core.SystemStats;
import com.gameboosterx.rog.data.GameRepository;
import com.gameboosterx.rog.service.BoostService;
import com.gameboosterx.rog.views.GaugeView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Dashboard principal. */
public class MainActivity extends BaseActivity {

    private GaugeView gRam, gBat, gTemp, gFps;
    private TextView status, btnSidebar;
    private GameRepository games;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        games = new GameRepository(this);

        gRam = findViewById(R.id.gaugeRam);
        gBat = findViewById(R.id.gaugeBattery);
        gTemp = findViewById(R.id.gaugeTemp);
        gFps = findViewById(R.id.gaugeFps);
        gRam.setLabel("RAM");
        gBat.setLabel("BATERIA");
        gTemp.setLabel("TEMP");
        gFps.setLabel("FPS");
        status = findViewById(R.id.txtStatus);
        btnSidebar = findViewById(R.id.btnSidebar);

        View turbo = findViewById(R.id.btnTurbo);
        turbo.startAnimation(AnimationUtils.loadAnimation(this, R.anim.pulse));
        turbo.setOnClickListener(v -> startActivity(new Intent(this, TurboActivity.class)));
        findViewById(R.id.btnOptimize).setOnClickListener(v -> startActivity(new Intent(this, OptimizeActivity.class)));
        btnSidebar.setOnClickListener(v -> toggleSidebar());
        findViewById(R.id.btnSettings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btnGames).setOnClickListener(v -> startActivity(new Intent(this, GamesActivity.class)));

        bindModule(R.id.modFps, FpsActivity.class);
        bindModule(R.id.modTouch, TouchActivity.class);
        bindModule(R.id.modThermal, ThermalActivity.class);
        bindModule(R.id.modNetwork, NetworkActivity.class);
        bindModule(R.id.modRefresh, RefreshRateActivity.class);
        bindModule(R.id.modSessions, SessionsActivity.class);
        bindModule(R.id.modBattery, BatteryActivity.class);
        bindModule(R.id.modStorage, StorageActivity.class);
        bindModule(R.id.modSettings, SettingsActivity.class);

        runBg(() -> {
            games.autoSelectIfFirstRun();
            ui.post(this::loadGames);
        });
        if (!Perms.notifications(this)) Perms.requestNotifications(this);
    }

    private void bindModule(int id, Class<?> cls) {
        findViewById(id).setOnClickListener(v -> startActivity(new Intent(this, cls)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadGames();
        ui.post(refresher);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(refresher);
    }

    private final Runnable refresher = new Runnable() {
        @Override
        public void run() {
            if (!resumed) return;
            refreshStats();
            ui.postDelayed(this, 2000);
        }
    };

    private void refreshStats() {
        SystemStats.RamInfo r = SystemStats.ram(this);
        SystemStats.BatteryInfo b = SystemStats.battery(this);
        SystemStats.ThermalInfo t = SystemStats.thermal(this);

        int ramPct = r.usedPercent();
        gRam.setValue(ramPct / 100f, ramPct + "%", colorForPercent(ramPct));

        if (b.level >= 0) {
            int c = b.level <= 15 ? RED : (b.level <= 30 ? ORANGE : GREEN);
            gBat.setValue(b.level / 100f, b.level + "%", c);
        } else {
            gBat.setValue(Float.NaN, "--", GREY);
        }

        float warn = Prefs.tempWarn(this);
        if (b.hasTemp()) {
            int lvl = SystemStats.heatLevel(b, t, warn);
            int c = lvl >= 2 ? RED : (lvl == 1 ? ORANGE : CYAN);
            gTemp.setValue((b.tempC - 20f) / 30f, String.format(Locale.US, "%.0f°", b.tempC), c);
        } else {
            gTemp.setValue(Float.NaN, "--", GREY);
        }

        float fps = BoostService.LIVE.running ? BoostService.LIVE.fps : Float.NaN;
        if (!Float.isNaN(fps)) {
            gFps.setValue(fps / 120f, String.format(Locale.US, "%.0f", fps), RED);
        } else {
            gFps.setValue(Float.NaN, "N/D", GREY);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(BoostService.LIVE.running ? "● MONITOR ATIVO" : "○ MONITOR DESLIGADO");
        if (Prefs.turboActive(this)) sb.append("   •   TURBO X ON");
        String sess = BoostService.LIVE.sessionLabel;
        if (sess != null) sb.append("   •   REC ").append(sess);
        sb.append("\nTérmico: ").append(SystemStats.thermalLabel(t.status));
        if (!Perms.dump(this)) sb.append("   •   FPS: sem fonte confiável (veja FPS Stabilizer)");
        status.setText(sb.toString());
        btnSidebar.setText(BoostService.LIVE.running ? "SIDEBAR ✓" : "GAME SIDEBAR");
    }

    private void toggleSidebar() {
        if (!Perms.overlay(this)) {
            Toast.makeText(this, "Autorize \"Sobrepor a outros apps\" para usar a barra.", Toast.LENGTH_LONG).show();
            Perms.requestOverlay(this);
            return;
        }
        if (BoostService.LIVE.running) {
            BoostService.stop(this);
            Toast.makeText(this, "Game Sidebar desligada", Toast.LENGTH_SHORT).show();
        } else {
            BoostService.start(this);
            Toast.makeText(this, "Game Sidebar ativa: toque na aba vermelha na borda direita", Toast.LENGTH_LONG).show();
        }
        ui.postDelayed(this::refreshStats, 400);
    }

    private void loadGames() {
        LinearLayout row = findViewById(R.id.gamesRow);
        row.removeAllViews();
        List<String> sel = new ArrayList<>(games.selectedGames());
        LayoutInflater inf = LayoutInflater.from(this);
        int count = 0;
        for (String pkg : sel) {
            if (!games.isInstalled(pkg)) continue;
            View tile = inf.inflate(R.layout.item_game_tile, row, false);
            ((ImageView) tile.findViewById(R.id.gameIcon)).setImageDrawable(games.icon(pkg));
            ((TextView) tile.findViewById(R.id.gameName)).setText(games.label(pkg));
            tile.setOnClickListener(v -> GameLauncher.launch(this, pkg));
            tile.setOnLongClickListener(v -> {
                startActivity(new Intent(this, ProfileActivity.class).putExtra("pkg", pkg));
                return true;
            });
            row.addView(tile);
            count++;
        }
        findViewById(R.id.txtNoGames).setVisibility(count == 0 ? View.VISIBLE : View.GONE);
    }
}
