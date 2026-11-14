package com.gameboosterx.rog.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.gameboosterx.rog.R;
import com.gameboosterx.rog.core.Perms;
import com.gameboosterx.rog.core.Prefs;
import com.gameboosterx.rog.core.SystemStats;
import com.gameboosterx.rog.data.GameCatalog;
import com.gameboosterx.rog.data.GameRepository;
import com.gameboosterx.rog.data.Session;
import com.gameboosterx.rog.data.SessionDb;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** PERFIS INDIVIDUAIS — configurações e recomendações por jogo. */
public class ProfileActivity extends BaseActivity {

    private GameRepository repo;
    private GameRepository.Profile profile;
    private String pkg;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_profile);
        pkg = getIntent().getStringExtra("pkg");
        repo = new GameRepository(this);
        if (pkg == null || !repo.isInstalled(pkg)) {
            finish();
            return;
        }
        profile = repo.profile(pkg);
        String label = repo.label(pkg);
        GameCatalog.Entry cat = GameCatalog.find(pkg, label);
        setupHeader("PERFIL", label);

        ((ImageView) findViewById(R.id.profileIcon)).setImageDrawable(repo.icon(pkg));
        ((TextView) findViewById(R.id.profileName)).setText(label);
        ((TextView) findViewById(R.id.profileType)).setText(cat != null
                ? "Perfil dedicado: " + cat.title : "Perfil genérico");

        Switch swMonitor = findViewById(R.id.swMonitor);
        Switch swDnd = findViewById(R.id.swDnd);
        Switch swSidebar = findViewById(R.id.swSidebar);
        Switch swRecord = findViewById(R.id.swRecord);
        Switch swShortcut = findViewById(R.id.swShortcut);
        Switch swBright = findViewById(R.id.swBright);
        SeekBar seek = findViewById(R.id.seekBright);
        TextView brightVal = findViewById(R.id.txtBrightVal);

        swMonitor.setChecked(repo.isSelected(pkg));
        swDnd.setChecked(profile.dnd);
        swSidebar.setChecked(profile.openSidebar);
        swRecord.setChecked(profile.recordSession);
        swShortcut.setChecked(Prefs.sidebarApps(this).contains(pkg));
        swBright.setChecked(profile.brightness >= 0);
        int bv = profile.brightness >= 0 ? profile.brightness : (cat != null ? cat.suggestedBrightness : 70);
        seek.setMax(100);
        seek.setProgress(bv);
        seek.setEnabled(profile.brightness >= 0);
        brightVal.setText(bv + "%");

        swMonitor.setOnCheckedChangeListener((v, c) -> repo.setSelected(pkg, c));
        swDnd.setOnCheckedChangeListener((v, c) -> {
            profile.dnd = c;
            save();
            if (c && !Perms.dnd(this)) Perms.requestDnd(this);
        });
        swSidebar.setOnCheckedChangeListener((v, c) -> { profile.openSidebar = c; save(); });
        swRecord.setOnCheckedChangeListener((v, c) -> { profile.recordSession = c; save(); });
        swShortcut.setOnCheckedChangeListener(this::onShortcut);
        swBright.setOnCheckedChangeListener((v, c) -> {
            seek.setEnabled(c);
            profile.brightness = c ? seek.getProgress() : -1;
            save();
            if (c && !Perms.writeSettings(this)) Perms.requestWriteSettings(this);
        });
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                brightVal.setText(p + "%");
                if (fromUser && profile.brightness >= 0) {
                    profile.brightness = Math.max(5, p);
                    save();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        findViewById(R.id.btnPlay).setOnClickListener(v -> GameLauncher.launch(this, pkg));
        findViewById(R.id.btnAppInfo).setOnClickListener(v -> Perms.openAppDetails(this, pkg));

        LinearLayout c = findViewById(R.id.profileContent);
        addSection(c, "ESTABILIDADE");
        addNote(c, cat != null ? cat.stability : GameCatalog.GENERIC_STABILITY);
        addSection(c, "TEMPERATURA");
        addNote(c, cat != null ? cat.temperature : GameCatalog.GENERIC_TEMPERATURE);
        addSection(c, "GRÁFICOS");
        addNote(c, (cat != null ? cat.graphics : GameCatalog.GENERIC_GRAPHICS)
                + (cat != null ? "\nLimite de FPS sugerido no jogo: " + cat.suggestedFpsCap : ""));
        addSection(c, "BATERIA");
        addNote(c, cat != null ? cat.battery : GameCatalog.GENERIC_BATTERY);

        addSection(c, "SUAS SESSÕES NESTE JOGO");
        int shown = 0;
        for (Session s : SessionDb.get(this).list(50, false)) {
            if (!pkg.equals(s.pkg)) continue;
            String fps = Float.isNaN(s.fpsAvg) ? "FPS N/D" : String.format(Locale.US, "FPS %.0f", s.fpsAvg);
            String temp = Float.isNaN(s.tempMax) ? "" : String.format(Locale.US, " • máx %.1f°C", s.tempMax);
            int used = s.batteryUsed();
            final long id = s.id;
            addRow(c, android.text.format.DateFormat.format("dd/MM HH:mm", s.start) + " • "
                            + SystemStats.formatDuration(s.durationMs()),
                    fps + temp + (used >= 0 ? " • bateria -" + used + "%" : ""), null,
                    v -> startActivity(new Intent(this, SessionDetailActivity.class).putExtra("id", id)));
            if (++shown >= 5) break;
        }
        if (shown == 0) addNote(c, "Nenhuma sessão gravada ainda.");

        if (!Perms.usageStats(this)) {
            addSection(c, "ATENÇÃO");
            addRow(c, "Aplicação automática desligada",
                    "Sem o Acesso de Uso, o app não sabe quando o jogo abre.", "Conceder", v -> Perms.requestUsage(this));
        }
    }

    private void onShortcut(CompoundButton v, boolean checked) {
        Set<String> s = Prefs.sidebarApps(this);
        if (checked) {
            if (s.size() >= 5 && !s.contains(pkg)) {
                v.setChecked(false);
                android.widget.Toast.makeText(this, "Máximo de 5 atalhos na barra.", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            s.add(pkg);
        } else {
            s.remove(pkg);
        }
        Prefs.setSidebarApps(this, s);
    }

    private void save() {
        repo.saveProfile(profile);
    }
}
