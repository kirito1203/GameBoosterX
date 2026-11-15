package com.pulsesolar.app.modules.storage;

import android.app.usage.ExternalStorageStats;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.os.storage.StorageManager;
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
import com.pulsesolar.app.core.Palette;
import com.pulsesolar.app.core.Perms;
import com.pulsesolar.app.core.SystemInfo;
import com.pulsesolar.app.core.Ui;
import com.pulsesolar.app.ui.RingGauge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Módulo 4 — Solar Storage: espaço total, categorias reais e ranking de apps. */
public class SolarStorageActivity extends BaseActivity {

    private RingGauge gauge;
    private TextView headline, txtCache, txtAppsMsg, txtCatMsg;
    private Ui.KvList kv, kvCat;
    private LinearLayout appsBox;
    private Button btnUsageAccess, btnScan;
    private boolean scanning = false;

    private static final class AppSize {
        String pkg, label;
        Drawable icon;
        long app, data, cache;

        long total() {
            return app + data;
        }
    }

    private static final class Categories {
        long total = -1, images, video, audio, apps;
        String error;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_storage);
        gauge = findViewById(R.id.gauge);
        headline = findViewById(R.id.txtHeadline);
        txtCache = findViewById(R.id.txtCache);
        txtAppsMsg = findViewById(R.id.txtAppsMsg);
        txtCatMsg = findViewById(R.id.txtCatMsg);
        appsBox = findViewById(R.id.appsBox);
        btnUsageAccess = findViewById(R.id.btnUsageAccess);
        btnScan = findViewById(R.id.btnScan);
        kv = new Ui.KvList((LinearLayout) findViewById(R.id.kvBox));
        kvCat = new Ui.KvList((LinearLayout) findViewById(R.id.kvCategories));
        gauge.setCaption("ocupado");

        findViewById(R.id.btnSystemStorage).setOnClickListener(v -> Nav.open(this,
                new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS), Nav.settings()));
        findViewById(R.id.btnClearCache).setOnClickListener(v -> runBg(() -> {
            long freed = Files.clearDir(getCacheDir());
            ui(() -> {
                toast("Cache do Pulse Solar limpo: " + Format.bytes(freed));
                refresh();
            });
        }));
        btnUsageAccess.setOnClickListener(v -> Nav.open(this,
                new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), Nav.settings()));
        btnScan.setOnClickListener(v -> scan());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        boolean ok = Perms.hasUsageAccess(this);
        btnUsageAccess.setVisibility(ok ? View.GONE : View.VISIBLE);
        btnScan.setEnabled(ok && !scanning);
        if (!ok) {
            txtCatMsg.setText("Para analisar categorias e apps, o Android exige que você permita o \"acesso ao uso\" para o Pulse Solar.");
        }
    }

    private void refresh() {
        SystemInfo.Storage s = SystemInfo.storage();
        float u = s.usedPercent();
        gauge.setProgress(u);
        gauge.setCenterText(Math.round(u) + "%");
        gauge.setColor(Palette.forLoad(u));
        headline.setText(Format.bytes(s.free) + " livres de " + Format.bytes(s.total));

        kv.clear();
        kv.add("Capacidade (partição de dados)", Format.bytes(s.total));
        kv.add("Livre", Format.bytes(s.free) + " (" + Format.percent(s.freePercent()) + ")");
        kv.add("Ocupado", Format.bytes(s.used()) + " (" + Format.percent(u) + ")");
        kv.add("Recomendado livre", "≥ 15%  (" + Format.bytes((long) (s.total * 0.15)) + ")");
        String state = s.freePercent() < 5 ? "CRÍTICO — o sistema fica lento"
                : s.freePercent() < 15 ? "Atenção — pouco espaço" : "Adequado";
        kv.add("Situação", state);

        txtCache.setText("Cache atual: " + Format.bytes(Files.size(getCacheDir())));
    }

    private void scan() {
        if (!Perms.hasUsageAccess(this)) {
            toast("Permita o acesso ao uso primeiro");
            return;
        }
        scanning = true;
        btnScan.setEnabled(false);
        txtCatMsg.setText("Analisando…");
        txtAppsMsg.setText("Analisando apps…");
        appsBox.removeAllViews();
        kvCat.clear();

        runBg(() -> {
            StorageStatsManager ssm = (StorageStatsManager) getSystemService(STORAGE_STATS_SERVICE);
            UserHandle me = Process.myUserHandle();

            // Categorias (fotos, vídeos, áudio) — dados do próprio Android
            Categories cat = new Categories();
            try {
                ExternalStorageStats ext = ssm.queryExternalStatsForUser(StorageManager.UUID_DEFAULT, me);
                cat.total = ext.getTotalBytes();
                cat.images = ext.getImageBytes();
                cat.video = ext.getVideoBytes();
                cat.audio = ext.getAudioBytes();
                StorageStats appsAll = ssm.queryStatsForUser(StorageManager.UUID_DEFAULT, me);
                cat.apps = appsAll.getAppBytes() + appsAll.getDataBytes();
            } catch (Exception e) {
                cat.error = "O sistema não forneceu as categorias: " + e.getClass().getSimpleName();
            }

            // Ranking de apps
            PackageManager pm = getPackageManager();
            Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> ris = pm.queryIntentActivities(launcher, 0);
            Set<String> seen = new HashSet<>();
            List<AppSize> list = new ArrayList<>();
            for (ResolveInfo ri : ris) {
                String pkg = ri.activityInfo.packageName;
                if (!seen.add(pkg)) continue;
                try {
                    StorageStats st = ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, pkg, me);
                    AppSize a = new AppSize();
                    a.pkg = pkg;
                    a.label = ri.loadLabel(pm).toString();
                    a.app = st.getAppBytes();
                    a.data = st.getDataBytes();
                    a.cache = st.getCacheBytes();
                    list.add(a);
                } catch (Exception ignored) {
                    // app removido durante a análise ou sem dados
                }
            }
            Collections.sort(list, (x, y) -> Long.compare(y.total(), x.total()));
            List<AppSize> top = new ArrayList<>(list.subList(0, Math.min(15, list.size())));
            long cacheAll = 0;
            for (AppSize a : list) cacheAll += a.cache;
            for (AppSize a : top) {
                try {
                    a.icon = pm.getApplicationIcon(a.pkg);
                } catch (PackageManager.NameNotFoundException ignored) {
                }
            }
            final long cacheTotal = cacheAll;
            final int count = list.size();

            ui(() -> {
                scanning = false;
                btnScan.setEnabled(true);
                showCategories(cat, cacheTotal);
                appsBox.removeAllViews();
                txtAppsMsg.setText(count + " apps analisados. Juntos, eles têm " + Format.bytes(cacheTotal)
                        + " de cache que pode ser limpo pela tela de cada app.");
                for (AppSize a : top) {
                    String sub = "Total " + Format.bytes(a.total()) + "  •  app " + Format.bytes(a.app)
                            + "  •  dados " + Format.bytes(a.data) + "  •  cache " + Format.bytes(a.cache);
                    Ui.appRow(appsBox, a.icon, a.label, sub, v -> Nav.open(this, Nav.appDetails(a.pkg)));
                }
            });
        });
    }

    private void showCategories(Categories c, long cacheTotal) {
        kvCat.clear();
        if (c.error != null) {
            txtCatMsg.setText(c.error);
            return;
        }
        SystemInfo.Storage s = SystemInfo.storage();
        long known = c.images + c.video + c.audio + c.apps;
        long other = Math.max(0, s.used() - known);
        txtCatMsg.setText("Valores calculados pelo Android para o seu usuário:");
        kvCat.add("Apps e dados de apps", Format.bytes(c.apps));
        kvCat.add("Fotos e imagens", Format.bytes(c.images));
        kvCat.add("Vídeos", Format.bytes(c.video));
        kvCat.add("Áudio", Format.bytes(c.audio));
        kvCat.add("Cache dos apps (incluso)", Format.bytes(cacheTotal));
        kvCat.add("Sistema e outros arquivos", Format.bytes(other));
    }
}
