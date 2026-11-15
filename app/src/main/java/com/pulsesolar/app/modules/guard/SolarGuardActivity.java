package com.pulsesolar.app.modules.guard;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.pulsesolar.app.BaseActivity;
import com.pulsesolar.app.R;
import com.pulsesolar.app.core.Format;
import com.pulsesolar.app.core.Nav;
import com.pulsesolar.app.core.Palette;
import com.pulsesolar.app.core.SystemInfo;
import com.pulsesolar.app.core.Ui;
import com.pulsesolar.app.ui.LineGraph;
import com.pulsesolar.app.ui.RingGauge;

import java.util.Locale;

/** Módulo 2 — Solar Guard: bateria e temperatura, com monitor contínuo opcional. */
public class SolarGuardActivity extends BaseActivity {

    private static final int REQ_NOTIF = 42;
    private static final int TEMP_MIN = 35;
    private static final int LOW_MIN = 5;

    private RingGauge gaugeLevel, gaugeTemp;
    private LineGraph graph;
    private Ui.KvList kv;
    private Switch swGuard;
    private TextView txtGuardState, txtLimit, txtLow;
    private SeekBar seekLimit, seekLow;
    private SharedPreferences prefs;
    private boolean ignoreSwitch = false;

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
        setContentView(R.layout.activity_guard);
        prefs = getSharedPreferences(GuardService.PREFS, MODE_PRIVATE);

        gaugeLevel = findViewById(R.id.gaugeLevel);
        gaugeTemp = findViewById(R.id.gaugeTemp);
        graph = findViewById(R.id.graph);
        kv = new Ui.KvList((LinearLayout) findViewById(R.id.kvBox));
        swGuard = findViewById(R.id.swGuard);
        txtGuardState = findViewById(R.id.txtGuardState);
        txtLimit = findViewById(R.id.txtLimit);
        txtLow = findViewById(R.id.txtLow);
        seekLimit = findViewById(R.id.seekLimit);
        seekLow = findViewById(R.id.seekLow);

        gaugeLevel.setCaption("carga");
        gaugeTemp.setCaption("temperatura");
        graph.setRange(20, 50);
        graph.setColor(Palette.GOLD);

        seekLimit.setProgress(prefs.getInt(GuardService.KEY_TEMP_LIMIT, GuardService.DEFAULT_TEMP_LIMIT) - TEMP_MIN);
        seekLow.setProgress(prefs.getInt(GuardService.KEY_LOW_LEVEL, GuardService.DEFAULT_LOW_LEVEL) - LOW_MIN);
        updateLabels();
        seekLimit.setOnSeekBarChangeListener(new SimpleSeek(() -> {
            prefs.edit().putInt(GuardService.KEY_TEMP_LIMIT, seekLimit.getProgress() + TEMP_MIN).apply();
            updateLabels();
        }));
        seekLow.setOnSeekBarChangeListener(new SimpleSeek(() -> {
            prefs.edit().putInt(GuardService.KEY_LOW_LEVEL, seekLow.getProgress() + LOW_MIN).apply();
            updateLabels();
        }));

        swGuard.setOnCheckedChangeListener((b, checked) -> {
            if (ignoreSwitch) return;
            if (checked) startGuard();
            else {
                GuardService.stop(this);
                handler.postDelayed(this::updateGuardState, 300);
            }
        });

        findViewById(R.id.btnSaver).setOnClickListener(v -> Nav.open(this,
                new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS), Nav.settings()));
        findViewById(R.id.btnUsage).setOnClickListener(v -> Nav.open(this,
                new Intent(Intent.ACTION_POWER_USAGE_SUMMARY),
                new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS), Nav.settings()));
        findViewById(R.id.btnNotif).setOnClickListener(v -> Nav.open(this,
                new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()),
                Nav.appDetails(getPackageName())));
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(tick);
        updateGuardState();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }

    private void updateLabels() {
        txtLimit.setText("Alertar quando a bateria passar de " + (seekLimit.getProgress() + TEMP_MIN) + " °C");
        txtLow.setText("Alertar quando a bateria ficar em " + (seekLow.getProgress() + LOW_MIN) + "% ou menos");
    }

    private void updateGuardState() {
        boolean on = GuardService.running;
        ignoreSwitch = true;
        swGuard.setChecked(on);
        ignoreSwitch = false;
        boolean notifOk = Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        if (on && notifOk) {
            txtGuardState.setText("● Monitor ligado — veja a notificação fixa");
            txtGuardState.setTextColor(Palette.GREEN);
        } else if (on) {
            txtGuardState.setText("● Monitor ligado, mas as notificações estão bloqueadas: os alertas não vão aparecer");
            txtGuardState.setTextColor(Palette.AMBER);
        } else {
            txtGuardState.setText("○ Monitor desligado");
            txtGuardState.setTextColor(Palette.DIM);
        }
    }

    private void startGuard() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
            return; // continua em onRequestPermissionsResult
        }
        try {
            GuardService.start(this);
        } catch (RuntimeException e) {
            toast("O Android não permitiu iniciar o monitor agora");
        }
        handler.postDelayed(this::updateGuardState, 500);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_NOTIF) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startGuard();
        } else {
            toast("Sem permissão de notificação o Solar Guard não consegue avisar você");
            updateGuardState();
        }
    }

    private void refresh() {
        SystemInfo.Battery b = SystemInfo.battery(this);
        if (b.level >= 0) {
            gaugeLevel.setProgress(b.level);
            gaugeLevel.setCenterText(b.level + "%");
            gaugeLevel.setColor(Palette.forLevel(b.level));
        }
        if (!Float.isNaN(b.tempC)) {
            gaugeTemp.setProgress((b.tempC - 20f) * 100f / 30f);
            gaugeTemp.setCenterText(Format.one(b.tempC) + "°");
            gaugeTemp.setColor(Palette.forTemp(b.tempC));
            graph.addPoint(b.tempC);
        }

        kv.clear();
        kv.add("Status", SystemInfo.statusText(b.status));
        kv.add("Fonte de energia", SystemInfo.pluggedText(b.plugged));
        kv.add("Saúde", SystemInfo.healthText(b.health));
        kv.add("Temperatura", Float.isNaN(b.tempC) ? "—" : Format.one(b.tempC) + " °C");
        kv.add("Tensão", b.voltageMv > 0 ? String.format(Locale.getDefault(), "%.2f V", b.voltageMv / 1000f) : "—");
        kv.add("Tecnologia", b.technology != null && !b.technology.isEmpty() ? b.technology : "—");
        Float ma = b.currentMa();
        kv.add("Corrente agora", ma == null ? "Não informada pelo aparelho" : Math.round(ma) + " mA");
        if (b.chargeCounterUah != Integer.MIN_VALUE && b.chargeCounterUah > 0) {
            kv.add("Carga restante", (b.chargeCounterUah / 1000) + " mAh");
        }
        if (b.isCharging()) {
            kv.add("Tempo até carga completa", b.chargeTimeRemainingMs > 0
                    ? Format.duration(b.chargeTimeRemainingMs) + " (estimativa do Android)" : "Estimativa indisponível");
        }
        kv.add("Economia de bateria", b.powerSave ? "ATIVADA (reduz desempenho)" : "Desativada");
        if (b.thermalStatus >= 0) kv.add("Estado térmico (sistema)", SystemInfo.thermalText(b.thermalStatus));
        if (Build.VERSION.SDK_INT >= 30) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            float head = pm.getThermalHeadroom(10);
            kv.add("Margem térmica (10 s)", Float.isNaN(head) ? "Não informada"
                    : String.format(Locale.getDefault(), "%.2f (1,00 = início da limitação)", head));
        }
    }

    /** SeekBar que salva ao soltar o dedo. */
    private static final class SimpleSeek implements SeekBar.OnSeekBarChangeListener {
        private final Runnable onChange;

        SimpleSeek(Runnable onChange) {
            this.onChange = onChange;
        }

        @Override
        public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
            if (fromUser) onChange.run();
        }

        @Override
        public void onStartTrackingTouch(SeekBar s) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar s) {
            onChange.run();
        }
    }
}
