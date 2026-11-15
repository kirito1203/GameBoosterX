package com.pulsesolar.app.modules.optimizer;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.pulsesolar.app.BaseActivity;
import com.pulsesolar.app.R;
import com.pulsesolar.app.core.Format;
import com.pulsesolar.app.core.Palette;
import com.pulsesolar.app.modules.fps.GameProfiles;

import java.util.ArrayList;
import java.util.List;

/** Módulo 9 — Solar Game Optimizer: lista dos perfis SOLAR ROBLOX, SOLAR RHYTHM e SOLAR CRAFT. */
public class GameOptimizerActivity extends BaseActivity {

    private LinearLayout profilesBox;
    private TextView txtDevice;

    private static final class Row {
        GameSpec spec;
        String pkg, appLabel;
        Drawable icon;
        boolean hasProfile;
        OptimizerStore.Stability stab;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_optimizer);
        profilesBox = findViewById(R.id.profilesBox);
        txtDevice = findViewById(R.id.txtDevice);
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        runBg(() -> {
            DeviceState d = DeviceState.capture(this);
            List<Row> rows = new ArrayList<>();
            PackageManager pm = getPackageManager();
            for (GameSpec s : GameSpec.all()) {
                Row r = new Row();
                r.spec = s;
                r.pkg = s.detectPackage(this);
                if (r.pkg != null) {
                    try {
                        r.appLabel = pm.getApplicationLabel(pm.getApplicationInfo(r.pkg, 0)).toString();
                        r.icon = pm.getApplicationIcon(r.pkg);
                    } catch (PackageManager.NameNotFoundException ignored) {
                        r.pkg = null;
                    }
                    r.hasProfile = r.pkg != null && GameProfiles.has(this, r.pkg);
                    r.stab = OptimizerStore.stability(this, r.pkg);
                }
                rows.add(r);
            }
            ui(() -> show(d, rows));
        });
    }

    private void show(DeviceState d, List<Row> rows) {
        txtDevice.setText("Classe do aparelho: " + DeviceState.tierName(d.tier)
                + (d.limited ? " (agora " + DeviceState.tierName(d.effectiveTier()) + ": quente ou em economia)" : "")
                + "\nRAM livre " + Format.one(d.ramAvailGb()) + " GB  •  "
                + (Float.isNaN(d.point.tempC) ? "--" : Format.one(d.point.tempC) + " °C")
                + "  •  tela até " + d.maxHz + " Hz");
        profilesBox.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(this);
        for (Row r : rows) {
            View v = inf.inflate(R.layout.item_optimizer_profile, profilesBox, false);
            ImageView icon = v.findViewById(R.id.profIcon);
            if (r.icon != null) icon.setImageDrawable(r.icon);
            else icon.setImageResource(R.drawable.ic_stat_sun);
            ((TextView) v.findViewById(R.id.profTitle)).setText(r.spec.title());
            ((TextView) v.findViewById(R.id.profGame)).setText(r.spec.gameName() + " — " + r.spec.tagline());
            TextView st = v.findViewById(R.id.profStatus);
            if (r.pkg == null) {
                st.setText("Jogo não encontrado. Toque para escolher o app manualmente.");
                st.setTextColor(Palette.AMBER);
            } else {
                StringBuilder sb = new StringBuilder("✔ ").append(r.appLabel);
                sb.append(r.hasProfile ? "  •  perfil salvo" : "  •  perfil padrão");
                if (r.stab != null && r.stab.sessions > 0) sb.append("  •  ").append(r.stab.sessions).append(" sessão(ões)");
                st.setText(sb.toString());
                st.setTextColor(Palette.GREEN);
            }
            v.setOnClickListener(x -> startActivity(new Intent(this, OptimizerProfileActivity.class)
                    .putExtra(OptimizerProfileActivity.EXTRA_SPEC, r.spec.id())));
            profilesBox.addView(v);
        }
    }
}
