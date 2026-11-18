package com.omni.c71;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Aba Apps: lista, congelar, remover bloatware, restaurar e permissões. */
class AppsPage extends Page {

    static class Row {
        String pkg, label, norm;
        boolean system, enabled, installed;
        ApplicationInfo ai;
    }

    /** Apps que nunca podem ser congelados/removidos (deixariam o celular sem funcionar). */
    static final Set<String> CORE = new HashSet<>(Arrays.asList(
            "android", "com.android.systemui", "com.android.settings", "com.android.phone",
            "com.android.shell", "com.android.server.telecom", "com.android.packageinstaller",
            "com.google.android.packageinstaller", "com.android.permissioncontroller",
            "com.google.android.permissioncontroller", "com.google.android.gms", "com.google.android.gsf",
            "com.android.vending", "com.android.networkstack", "com.google.android.networkstack",
            "com.android.bluetooth", "com.android.nfc", "com.android.externalstorage",
            "com.android.documentsui", "com.google.android.documentsui", "com.android.keychain",
            "com.android.location.fused", "com.android.se", "com.android.ims", "com.android.carrierconfig",
            "com.android.inputdevices", "com.android.webview", "com.google.android.webview",
            "com.android.wallpaperbackup", "com.android.dynsystem", Shell.SHIZUKU_PKG));

    static final Map<String, Drawable> ICONS = new ConcurrentHashMap<>();
    static final ExecutorService IO = Executors.newFixedThreadPool(2);

    final List<Row> all = new ArrayList<>();
    final List<Row> shown = new ArrayList<>();
    int filter = 0;
    boolean loading = false;
    EditText search;
    TextView count;
    RowAdapter adapter;

    AppsPage(MainActivity a) { super(a); }

    @Override
    View build() {
        LinearLayout b = Ui.vbox(a);
        int p = Ui.dp(a, 16);
        b.setPadding(p, Ui.dp(a, 4), p, 0);

        search = Ui.input(a, "🔎 Buscar app ou pacote");
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) { }
            @Override public void onTextChanged(CharSequence s, int st, int bf, int c) { apply(); }
            @Override public void afterTextChanged(Editable e) { }
        });
        b.addView(search);
        b.addView(Ui.chips(a, new String[]{"Meus", "Sistema", "Congelados", "Todos"}, 0, i -> {
            filter = i;
            apply();
        }));
        count = Ui.note(a, "Carregando apps…");
        b.addView(count);

        ListView list = new ListView(a);
        list.setDivider(new ColorDrawable(0));
        list.setDividerHeight(Ui.dp(a, 6));
        list.setSelector(new ColorDrawable(0));
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, Ui.dp(a, 16));
        adapter = new RowAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, v, pos, id) -> openDetail(shown.get(pos)));
        b.addView(list, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));

        load();
        return b;
    }

    @Override
    void refresh() {
        if (view != null) load();
    }

    void load() {
        if (loading) return;
        loading = true;
        IO.execute(() -> {
            PackageManager pm = a.getPackageManager();
            int flags = PackageManager.MATCH_UNINSTALLED_PACKAGES | PackageManager.MATCH_DISABLED_COMPONENTS;
            List<ApplicationInfo> apps;
            try {
                apps = pm.getInstalledApplications(flags);
            } catch (Throwable t) {
                apps = new ArrayList<>();
            }
            List<Row> rows = new ArrayList<>();
            for (ApplicationInfo ai : apps) {
                Row r = new Row();
                r.ai = ai;
                r.pkg = ai.packageName;
                CharSequence l;
                try { l = ai.loadLabel(pm); } catch (Throwable t) { l = ai.packageName; }
                r.label = l == null ? ai.packageName : l.toString();
                r.system = (ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
                r.enabled = ai.enabled;
                r.installed = (ai.flags & ApplicationInfo.FLAG_INSTALLED) != 0;
                r.norm = Ui.norm(r.label + " " + r.pkg);
                rows.add(r);
            }
            Collections.sort(rows, (x, y) -> x.label.compareToIgnoreCase(y.label));
            a.runOnUiThread(() -> {
                all.clear();
                all.addAll(rows);
                loading = false;
                apply();
            });
        });
    }

    void apply() {
        if (search == null) return;
        String q = Ui.norm(search.getText().toString().trim());
        shown.clear();
        for (Row r : all) {
            boolean ok;
            switch (filter) {
                case 0: ok = !r.system && r.installed; break;
                case 1: ok = r.system && r.installed; break;
                case 2: ok = !r.enabled || !r.installed; break;
                default: ok = true;
            }
            if (ok && (q.isEmpty() || r.norm.contains(q))) shown.add(r);
        }
        count.setText(loading ? "Carregando apps…" : shown.size() + " apps • toque em um app para mexer nele");
        adapter.notifyDataSetChanged();
    }

    boolean isProtected(String pkg) {
        if (CORE.contains(pkg) || pkg.equals(a.getPackageName())) return true;
        if (pkg.startsWith("com.android.providers.")) return true;
        try {
            String ime = Settings.Secure.getString(a.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            if (ime != null && ime.startsWith(pkg + "/")) return true;
        } catch (Throwable ignored) { }
        try {
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo ri = a.getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            if (ri != null && ri.activityInfo != null && pkg.equals(ri.activityInfo.packageName)) return true;
        } catch (Throwable ignored) { }
        return false;
    }

    void loadIcon(Row r, ImageView iv) {
        Drawable d = ICONS.get(r.pkg);
        iv.setTag(r.pkg);
        if (d != null) {
            iv.setImageDrawable(d);
            return;
        }
        iv.setImageDrawable(null);
        IO.execute(() -> {
            Drawable icon;
            try { icon = r.ai.loadIcon(a.getPackageManager()); } catch (Throwable t) { icon = null; }
            if (icon == null) return;
            ICONS.put(r.pkg, icon);
            final Drawable fi = icon;
            a.runOnUiThread(() -> {
                if (r.pkg.equals(iv.getTag())) iv.setImageDrawable(fi);
            });
        });
    }

    class RowAdapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int i) { return shown.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View cv, ViewGroup parent) {
            LinearLayout row;
            ImageView iv;
            TextView name, pkg, tag;
            if (cv == null) {
                row = Ui.hbox(a);
                int p = Ui.dp(a, 10);
                row.setPadding(p, p, p, p);
                row.setBackground(Ui.round(Ui.CARD, Ui.dp(a, 14)));
                iv = new ImageView(a);
                int s = Ui.dp(a, 40);
                row.addView(iv, new LinearLayout.LayoutParams(s, s));
                LinearLayout col = Ui.vbox(a);
                col.setPadding(Ui.dp(a, 12), 0, Ui.dp(a, 6), 0);
                name = Ui.text(a, "", 15, Ui.TEXT, true);
                name.setSingleLine(true);
                pkg = Ui.text(a, "", 11, Ui.SUB, false);
                pkg.setSingleLine(true);
                col.addView(name);
                col.addView(pkg);
                row.addView(col, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
                tag = Ui.text(a, "", 10, Ui.TEXT, true);
                tag.setPadding(Ui.dp(a, 8), Ui.dp(a, 3), Ui.dp(a, 8), Ui.dp(a, 3));
                row.addView(tag);
                row.setTag(new Object[]{iv, name, pkg, tag});
            } else {
                row = (LinearLayout) cv;
                Object[] h = (Object[]) row.getTag();
                iv = (ImageView) h[0];
                name = (TextView) h[1];
                pkg = (TextView) h[2];
                tag = (TextView) h[3];
            }
            Row r = shown.get(pos);
            name.setText(r.label);
            pkg.setText(r.pkg);
            if (!r.installed) {
                tag.setText("REMOVIDO");
                tag.setBackground(Ui.round(0xFF4A1420, Ui.dp(a, 8)));
                tag.setVisibility(View.VISIBLE);
            } else if (!r.enabled) {
                tag.setText("CONGELADO");
                tag.setBackground(Ui.round(0xFF4A3A0A, Ui.dp(a, 8)));
                tag.setVisibility(View.VISIBLE);
            } else if (r.system) {
                tag.setText("SISTEMA");
                tag.setBackground(Ui.round(Ui.CARD2, Ui.dp(a, 8)));
                tag.setVisibility(View.VISIBLE);
            } else {
                tag.setVisibility(View.GONE);
            }
            loadIcon(r, iv);
            return row;
        }
    }

    // ===================================================== DETALHES DO APP

    void openDetail(Row r) {
        PackageManager pm = a.getPackageManager();
        PackageInfo pi = null;
        try {
            pi = pm.getPackageInfo(r.pkg, PackageManager.GET_PERMISSIONS | PackageManager.MATCH_UNINSTALLED_PACKAGES);
        } catch (Throwable ignored) { }
        boolean prot = isProtected(r.pkg);

        ScrollView sv = new ScrollView(a);
        LinearLayout b = Ui.vbox(a);
        int p = Ui.dp(a, 20);
        b.setPadding(p, p, p, Ui.dp(a, 8));
        sv.addView(b);

        LinearLayout head = Ui.hbox(a);
        ImageView iv = new ImageView(a);
        int s = Ui.dp(a, 52);
        head.addView(iv, new LinearLayout.LayoutParams(s, s));
        loadIcon(r, iv);
        LinearLayout hc = Ui.vbox(a);
        hc.setPadding(Ui.dp(a, 12), 0, 0, 0);
        hc.addView(Ui.text(a, r.label, 18, Ui.TEXT, true));
        hc.addView(Ui.text(a, r.pkg, 12, Ui.SUB, false));
        head.addView(hc, new LinearLayout.LayoutParams(0, Ui.WRAP, 1f));
        b.addView(head);

        String state = !r.installed ? "Removido deste usuário (pode restaurar)"
                : !r.enabled ? "Congelado" : "Ativo";
        String ver = pi != null && pi.versionName != null ? pi.versionName : "?";
        b.addView(Ui.note(a, "\nVersão " + ver + " • " + (r.system ? "App do sistema" : "Instalado por você")
                + "\nEstado: " + state));
        if (prot) {
            TextView t = Ui.text(a, "🔒 App essencial — congelar/remover está bloqueado para proteger seu celular.", 13, Ui.YELLOW, true);
            t.setPadding(0, 0, 0, Ui.dp(a, 8));
            b.addView(t);
        }

        AlertDialog dlg = Ui.dialog(a).setView(sv).setPositiveButton("Fechar", null).create();

        // ---- Ações
        List<String> names = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        Intent launch = pm.getLaunchIntentForPackage(r.pkg);
        if (launch != null && r.installed && r.enabled) {
            names.add("▶ Abrir");
            acts.add(() -> safeStart(launch));
        }
        if (r.installed) {
            names.add("ℹ Info Android");
            acts.add(() -> safeStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + r.pkg))));
            names.add("⏹ Forçar parada");
            acts.add(() -> shell("am force-stop " + r.pkg, r.label + " parado", dlg));
        }
        if (r.installed && !prot) {
            if (r.enabled) {
                names.add("❄ Congelar");
                acts.add(() -> confirm("Congelar " + r.label + "?",
                        "O app some e para de rodar, mas não é apagado. Você pode descongelar aqui na aba Congelados.",
                        () -> shell("pm disable-user --user 0 " + r.pkg, r.label + " congelado", dlg)));
            } else {
                names.add("🔥 Descongelar");
                acts.add(() -> shell("pm enable " + r.pkg, r.label + " ativo de novo", dlg));
            }
            if (r.system) {
                names.add("🗑 Remover");
                acts.add(() -> confirm("Remover " + r.label + "?",
                        "Remove o app do sistema só para o seu usuário (bloatware). Os arquivos de fábrica ficam guardados e você pode restaurar na aba Congelados.\n\nSó remova apps que você conhece!",
                        () -> shell("pm uninstall -k --user 0 " + r.pkg, r.label + " removido", dlg)));
            } else {
                names.add("🗑 Desinstalar");
                acts.add(() -> {
                    dlg.dismiss();
                    safeStart(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + r.pkg)));
                });
            }
            names.add("🧹 Limpar dados");
            acts.add(() -> confirm("Apagar dados de " + r.label + "?",
                    "Apaga login, configurações e arquivos internos do app. Não dá para desfazer.",
                    () -> shell("pm clear " + r.pkg, "Dados de " + r.label + " apagados", dlg)));
        }
        if (!r.installed) {
            names.add("♻ Restaurar");
            acts.add(() -> shell("cmd package install-existing " + r.pkg, r.label + " restaurado", dlg));
        }
        for (int i = 0; i < names.size(); i += 2) {
            LinearLayout row = Ui.hbox(a);
            for (int j = i; j < Math.min(i + 2, names.size()); j++) {
                final Runnable act = acts.get(j);
                boolean danger = names.get(j).contains("Remover") || names.get(j).contains("Limpar");
                TextView bt = Ui.btn(a, names.get(j), danger ? 0xFF5A1A28 : Ui.CARD2, v -> act.run());
                row.addView(bt, Ui.weight(a));
            }
            if (names.size() - i == 1) row.addView(new View(a), Ui.weight(a));
            b.addView(row);
        }

        // ---- Permissões
        if (pi != null && pi.requestedPermissions != null && pi.requestedPermissions.length > 0) {
            TextView pt = Ui.title(a, "\n🔐 Permissões");
            b.addView(pt);
            b.addView(Ui.note(a, Shell.ready()
                    ? "Ligue/desligue as permissões perigosas abaixo."
                    : "Para ligar/desligar permissões, ative o Shizuku. Sem ele, use \"Info Android\"."));
            List<String> others = new ArrayList<>();
            int dangerous = 0;
            for (int i = 0; i < pi.requestedPermissions.length; i++) {
                String perm = pi.requestedPermissions[i];
                boolean granted = pi.requestedPermissionsFlags != null
                        && (pi.requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0;
                String shortName = perm.substring(perm.lastIndexOf('.') + 1);
                PermissionInfo info;
                try {
                    info = pm.getPermissionInfo(perm, 0);
                } catch (Throwable t) {
                    others.add(shortName);
                    continue;
                }
                int level = Build.VERSION.SDK_INT >= 28 ? info.getProtection()
                        : (info.protectionLevel & PermissionInfo.PROTECTION_MASK_BASE);
                if (level != PermissionInfo.PROTECTION_DANGEROUS) {
                    others.add(shortName);
                    continue;
                }
                dangerous++;
                CharSequence lbl = info.loadLabel(pm);
                String text = (lbl != null ? lbl.toString() : shortName) + "\n" + shortName;
                final boolean[] lock = {false};
                Switch sw = Ui.sw(a, text, granted, null);
                sw.setTextSize(13);
                sw.setOnCheckedChangeListener((btn, on) -> {
                    if (lock[0]) return;
                    if (!Shell.ready() || !r.installed) {
                        lock[0] = true;
                        btn.setChecked(!on);
                        lock[0] = false;
                        Ui.toast(a, "Precisa do Shizuku ativo para mudar permissões.");
                        return;
                    }
                    Shell.run("pm " + (on ? "grant " : "revoke ") + r.pkg + " " + perm, (ok, out) -> {
                        if (ok) {
                            Ui.toast(a, (on ? "Permitido: " : "Negado: ") + shortName);
                        } else {
                            lock[0] = true;
                            btn.setChecked(!on);
                            lock[0] = false;
                            Ui.toast(a, "Não deu: " + out);
                        }
                    });
                });
                b.addView(sw);
            }
            if (dangerous == 0) b.addView(Ui.note(a, "Nenhuma permissão perigosa."));
            if (!others.isEmpty()) {
                Collections.sort(others);
                StringBuilder sb = new StringBuilder();
                for (String o : others) sb.append("• ").append(o).append('\n');
                TextView more = Ui.text(a, "▸ Outras permissões (" + others.size() + ") — toque para ver", 13, Prefs.accent(a), true);
                more.setPadding(0, Ui.dp(a, 10), 0, Ui.dp(a, 4));
                TextView list = Ui.note(a, sb.toString().trim());
                list.setVisibility(View.GONE);
                more.setOnClickListener(v -> list.setVisibility(list.getVisibility() == View.GONE ? View.VISIBLE : View.GONE));
                b.addView(more);
                b.addView(list);
            }
        }

        dlg.show();
    }

    void confirm(String title, String msg, Runnable go) {
        Ui.dialog(a).setTitle(title).setMessage(msg)
                .setPositiveButton("Sim", (d, w) -> go.run())
                .setNegativeButton("Cancelar", null).show();
    }

    void shell(String cmd, String okMsg, AlertDialog dlg) {
        if (!needShizuku()) return;
        Shell.run(cmd, (ok, out) -> {
            Ui.toast(a, ok ? "✅ " + okMsg : "Falhou: " + (out.isEmpty() ? "erro desconhecido" : out));
            if (ok && dlg != null) dlg.dismiss();
            load();
        });
    }
}
