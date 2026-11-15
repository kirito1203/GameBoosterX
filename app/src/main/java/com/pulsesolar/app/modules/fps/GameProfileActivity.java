package com.pulsesolar.app.modules.fps;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.pulsesolar.app.BaseActivity;
import com.pulsesolar.app.R;
import com.pulsesolar.app.core.Perms;
import com.pulsesolar.app.core.Ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Perfis de jogos com configurações seguras e início direto da sessão monitorada. */
public class GameProfileActivity extends BaseActivity {

    public static final String EXTRA_PKG = "pkg";
    public static final String EXTRA_LABEL = "label";
    private static final int LIMIT_MIN = 35;
    private static final int REQ_NOTIF = 78;

    private LinearLayout editor, gamesBox;
    private TextView txtGame, txtLimit, txtMsg;
    private Switch swDnd, swBright, swTimeout, swMonitor;
    private SeekBar seekBright, seekLimit;
    private Button btnShowAll;
    private boolean showAll = false;
    private String selPkg, selLabel;

    private static final class AppEntry {
        String pkg, label;
        Drawable icon;
        boolean game;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fps_profiles);
        editor = findViewById(R.id.editor);
        gamesBox = findViewById(R.id.gamesBox);
        txtGame = findViewById(R.id.txtGame);
        txtLimit = findViewById(R.id.txtLimit);
        txtMsg = findViewById(R.id.txtMsg);
        swDnd = findViewById(R.id.swDnd);
        swBright = findViewById(R.id.swBright);
        swTimeout = findViewById(R.id.swTimeout);
        swMonitor = findViewById(R.id.swMonitor);
        seekBright = findViewById(R.id.seekBright);
        seekLimit = findViewById(R.id.seekLimit);
        btnShowAll = findViewById(R.id.btnShowAll);

        seekLimit.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                updateLimit();
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });

        findViewById(R.id.btnSave).setOnClickListener(v -> {
            if (selPkg == null) return;
            GameProfiles.save(this, selPkg, readEditor());
            toast("Perfil salvo para " + selLabel);
            loadApps();
        });
        findViewById(R.id.btnDelete).setOnClickListener(v -> {
            if (selPkg == null) return;
            GameProfiles.delete(this, selPkg);
            toast("Perfil excluído");
            editor.setVisibility(View.GONE);
            selPkg = null;
            loadApps();
        });
        findViewById(R.id.btnPlay).setOnClickListener(v -> play());
        btnShowAll.setOnClickListener(v -> {
            showAll = !showAll;
            loadApps();
        });
        loadApps();

        // Aberto a partir de outro módulo (ex.: Solar Game Optimizer) já com o jogo escolhido
        String prePkg = getIntent().getStringExtra(EXTRA_PKG);
        if (prePkg != null) {
            String preLabel = getIntent().getStringExtra(EXTRA_LABEL);
            openEditor(prePkg, preLabel != null ? preLabel : prePkg);
        }
    }

    private void updateLimit() {
        txtLimit.setText("Alerta de temperatura: " + (seekLimit.getProgress() + LIMIT_MIN) + " °C");
    }

    private void openEditor(String pkg, String label) {
        selPkg = pkg;
        selLabel = label;
        GameProfiles.Profile p = GameProfiles.get(this, pkg);
        txtGame.setText(label + (GameProfiles.has(this, pkg) ? "" : "  (perfil novo, valores seguros)"));
        swDnd.setChecked(p.dnd);
        swBright.setChecked(p.bright);
        seekBright.setProgress(p.brightValue);
        swTimeout.setChecked(p.timeout);
        swMonitor.setChecked(p.monitor);
        seekLimit.setProgress(p.tempLimit - LIMIT_MIN);
        updateLimit();
        editor.setVisibility(View.VISIBLE);
        ((ScrollView) findViewById(R.id.scroll)).smoothScrollTo(0, 0);
    }

    private GameProfiles.Profile readEditor() {
        GameProfiles.Profile p = new GameProfiles.Profile();
        p.dnd = swDnd.isChecked();
        p.bright = swBright.isChecked();
        p.brightValue = seekBright.getProgress();
        p.timeout = swTimeout.isChecked();
        p.monitor = swMonitor.isChecked();
        p.tempLimit = seekLimit.getProgress() + LIMIT_MIN;
        return p;
    }

    private void play() {
        if (selPkg == null) return;
        GameProfiles.Profile p = readEditor();
        GameProfiles.save(this, selPkg, p);
        if (p.monitor && Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
            return;
        }
        doPlay(p);
    }

    private void doPlay(GameProfiles.Profile p) {
        if ((p.dnd && !Perms.hasDndAccess(this)) || ((p.bright || p.timeout) && !Perms.canWriteSettings(this))) {
            toast("Algumas opções precisam de permissão: conceda no Solar Gaming");
        }
        String warn = GameProfiles.play(this, selPkg, selLabel, p);
        if (warn != null) toast(warn);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIF && selPkg != null) doPlay(readEditor());
    }

    @SuppressWarnings("deprecation")
    private void loadApps() {
        txtMsg.setText("Carregando…");
        final boolean all = showAll;
        runBg(() -> {
            PackageManager pm = getPackageManager();
            Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> ris = pm.queryIntentActivities(launcher, 0);
            Set<String> seen = new HashSet<>();
            List<AppEntry> list = new ArrayList<>();
            for (ResolveInfo ri : ris) {
                ApplicationInfo ai = ri.activityInfo.applicationInfo;
                if (ai.packageName.equals(getPackageName()) || !seen.add(ai.packageName)) continue;
                boolean game = ai.category == ApplicationInfo.CATEGORY_GAME
                        || (ai.flags & ApplicationInfo.FLAG_IS_GAME) != 0;
                boolean hasProfile = GameProfiles.has(this, ai.packageName);
                if (!all && !game && !hasProfile) continue;
                AppEntry a = new AppEntry();
                a.pkg = ai.packageName;
                a.label = ri.loadLabel(pm).toString();
                a.icon = ri.loadIcon(pm);
                a.game = game;
                list.add(a);
            }
            Collections.sort(list, (x, y) -> x.label.compareToIgnoreCase(y.label));
            ui(() -> {
                gamesBox.removeAllViews();
                btnShowAll.setText(all ? "Mostrar só jogos" : "Mostrar todos os apps");
                if (list.isEmpty()) {
                    txtMsg.setText("Nenhum app marcado como jogo. Toque em \"Mostrar todos os apps\".");
                    return;
                }
                txtMsg.setText("Toque em um jogo para criar ou editar o perfil.");
                for (AppEntry a : list) {
                    String sub = GameProfiles.has(this, a.pkg) ? "✔ Perfil salvo" : (a.game ? "Jogo • sem perfil" : a.pkg);
                    Ui.appRow(gamesBox, a.icon, a.label, sub, v -> openEditor(a.pkg, a.label));
                }
            });
        });
    }
}
