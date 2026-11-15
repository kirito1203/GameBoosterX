package com.pulsesolar.app.modules.boost;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.pulsesolar.app.BaseActivity;
import com.pulsesolar.app.R;
import com.pulsesolar.app.core.Files;
import com.pulsesolar.app.core.Format;
import com.pulsesolar.app.core.Nav;
import com.pulsesolar.app.core.Perms;
import com.pulsesolar.app.core.SystemInfo;
import com.pulsesolar.app.core.Ui;
import com.pulsesolar.app.core.UsageHelper;

import java.util.List;

/** Módulo 1 — Solar Boost: monitoramento e gerenciamento seguro de recursos. */
public class SolarBoostActivity extends BaseActivity {

    private Ui.KvList kv;
    private LinearLayout appsBox;
    private TextView txtAppsMsg, txtCache;
    private Button btnUsageAccess, btnRefreshApps;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refreshResources();
            handler.postDelayed(this, 2000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_boost);
        kv = new Ui.KvList((LinearLayout) findViewById(R.id.kvBox));
        appsBox = findViewById(R.id.appsBox);
        txtAppsMsg = findViewById(R.id.txtAppsMsg);
        txtCache = findViewById(R.id.txtCache);
        btnUsageAccess = findViewById(R.id.btnUsageAccess);
        btnRefreshApps = findViewById(R.id.btnRefreshApps);

        btnUsageAccess.setOnClickListener(v -> Nav.open(this,
                new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), Nav.settings()));
        btnRefreshApps.setOnClickListener(v -> loadApps());

        findViewById(R.id.btnClearCache).setOnClickListener(v -> runBg(() -> {
            long freed = Files.clearDir(getCacheDir()) + Files.clearDir(getCodeCacheDir());
            ui(() -> {
                toast("Cache do Pulse Solar limpo: " + Format.bytes(freed));
                refreshCache();
            });
        }));
        findViewById(R.id.btnBatteryOpt).setOnClickListener(v -> Nav.open(this,
                new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), Nav.settings()));
        findViewById(R.id.btnRunning).setOnClickListener(v -> Nav.open(this,
                new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
                new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)));
        findViewById(R.id.btnAllApps).setOnClickListener(v -> Nav.open(this,
                new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS), Nav.settings()));
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(tick);
        refreshCache();
        boolean ok = Perms.hasUsageAccess(this);
        btnUsageAccess.setVisibility(ok ? View.GONE : View.VISIBLE);
        btnRefreshApps.setEnabled(ok);
        if (ok) loadApps();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }

    private void refreshResources() {
        SystemInfo.Ram r = SystemInfo.ram(this);
        SystemInfo.Storage s = SystemInfo.storage();
        SystemInfo.Battery b = SystemInfo.battery(this);
        kv.clear();
        kv.add("RAM livre", Format.bytes(r.avail) + " (" + Format.percent(r.availPercent()) + ")");
        kv.add("Memória baixa?", r.low ? "SIM — o Android está fechando apps" : "Não");
        kv.add("Armazenamento livre", Format.bytes(s.free) + " (" + Format.percent(s.freePercent()) + ")");
        kv.add("Temperatura da bateria", Float.isNaN(b.tempC) ? "—" : Format.one(b.tempC) + " °C");
        if (b.thermalStatus >= 0) kv.add("Estado térmico", SystemInfo.thermalText(b.thermalStatus));
        kv.add("Economia de bateria", b.powerSave ? "ATIVADA (limita a CPU)" : "Desativada");
        kv.add("Ligado há", Format.duration(SystemInfo.uptimeMs()));
    }

    private void refreshCache() {
        long size = Files.size(getCacheDir()) + Files.size(getCodeCacheDir());
        txtCache.setText("Cache do Pulse Solar: " + Format.bytes(size));
    }

    private void loadApps() {
        btnRefreshApps.setEnabled(false);
        runBg(() -> {
            List<UsageHelper.AppUse> list = UsageHelper.query(getApplicationContext(),
                    3L * 60 * 60 * 1000, 15, false);
            ui(() -> {
                btnRefreshApps.setEnabled(true);
                appsBox.removeAllViews();
                if (list.isEmpty()) {
                    txtAppsMsg.setText("Nenhum app usado nas últimas 3 horas.");
                    return;
                }
                txtAppsMsg.setText(list.size() + " apps usados recentemente. Apps abertos há pouco tempo tendem a continuar na memória. Toque para abrir \"Forçar parada\".");
                long now = System.currentTimeMillis();
                for (UsageHelper.AppUse a : list) {
                    String sub = "Usado há " + Format.duration(now - a.lastUsed)
                            + "  •  em tela: " + Format.duration(a.foregroundMs);
                    Ui.appRow(appsBox, a.icon, a.label, sub, v -> Nav.open(this, Nav.appDetails(a.pkg)));
                }
            });
        });
    }
}
