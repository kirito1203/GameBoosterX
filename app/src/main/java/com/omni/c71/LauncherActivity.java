package com.omni.c71;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.AlarmClock;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** OMNI Home: tela inicial personalizável. */
public class LauncherActivity extends Activity {

    static final Map<String, Drawable> ICONS = new ConcurrentHashMap<>();

    static class Item {
        String label, pkg, key, norm;
        ComponentName cn;
    }

    final List<Item> all = new ArrayList<>();
    final List<Item> shown = new ArrayList<>();

    View dim;
    TextView clock, date;
    LinearLayout clockBox;
    EditText search;
    GridView grid;
    LinearLayout dock;
    Adapter adapter;

    int appliedVer = -1, accent, iconPx;
    boolean labels, frame;
    BroadcastReceiver pkgRx, timeRx;
    final Locale br = new Locale("pt", "BR");

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        FrameLayout root = new FrameLayout(this);
        dim = new View(this);
        root.addView(dim, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        FrameLayout safe = new FrameLayout(this);
        safe.setFitsSystemWindows(true);
        root.addView(safe, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        LinearLayout col = Ui.vbox(this);
        int p = Ui.dp(this, 16);
        col.setPadding(p, Ui.dp(this, 20), p, Ui.dp(this, 10));
        safe.addView(col, new FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH));

        // Relógio
        clockBox = Ui.vbox(this);
        clock = Ui.text(this, "", 60, Color.WHITE, false);
        clock.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        clock.setShadowLayer(10, 0, 2, 0x99000000);
        date = Ui.text(this, "", 16, Color.WHITE, true);
        date.setShadowLayer(8, 0, 1, 0x99000000);
        clockBox.addView(clock);
        clockBox.addView(date);
        clockBox.setPadding(Ui.dp(this, 6), 0, 0, Ui.dp(this, 14));
        clockBox.setOnClickListener(v -> {
            try {
                startActivity(new Intent(AlarmClock.ACTION_SHOW_ALARMS));
            } catch (Throwable ignored) { }
        });
        col.addView(clockBox);

        // Busca
        search = new EditText(this);
        search.setHint("🔎  Buscar apps");
        search.setHintTextColor(0xCCFFFFFF);
        search.setTextColor(Color.WHITE);
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_GO);
        search.setBackground(Ui.round(0x55000000, Ui.dp(this, 24)));
        search.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18), Ui.dp(this, 12));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) { filter(); }
            @Override public void afterTextChanged(Editable e) { }
        });
        search.setOnEditorActionListener((v, id, ev) -> {
            if (!shown.isEmpty()) launch(shown.get(0));
            return true;
        });
        col.addView(search, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));

        // Grade de apps
        grid = new GridView(this);
        grid.setVerticalSpacing(Ui.dp(this, 16));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setSelector(new ColorDrawable(0));
        grid.setVerticalScrollBarEnabled(false);
        grid.setClipToPadding(false);
        grid.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 16));
        adapter = new Adapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, v, pos, id) -> launch(shown.get(pos)));
        grid.setOnItemLongClickListener((parent, v, pos, id) -> {
            menu(shown.get(pos));
            return true;
        });
        col.addView(grid, new LinearLayout.LayoutParams(Ui.MATCH, 0, 1f));

        // Dock
        dock = Ui.hbox(this);
        dock.setGravity(Gravity.CENTER);
        dock.setBackground(Ui.round(0x55000000, Ui.dp(this, 28)));
        dock.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        col.addView(dock, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));

        setContentView(root);

        registerReceivers();
        applyPrefs();
        loadApps();
        tick();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Prefs.ver(this) != appliedVer) {
            applyPrefs();
            filter();
            buildDock();
        }
        tick();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        search.setText("");
        hideKeyboard();
        grid.smoothScrollToPosition(0);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // A tela inicial não fecha com "voltar".
        search.setText("");
        hideKeyboard();
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(pkgRx); } catch (Throwable ignored) { }
        try { unregisterReceiver(timeRx); } catch (Throwable ignored) { }
        super.onDestroy();
    }

    void hideKeyboard() {
        InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (im != null) im.hideSoftInputFromWindow(search.getWindowToken(), 0);
        search.clearFocus();
    }

    void reg(BroadcastReceiver r, IntentFilter f) {
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(r, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(r, f);
    }

    void registerReceivers() {
        pkgRx = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) { loadApps(); }
        };
        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addAction(Intent.ACTION_PACKAGE_REPLACED);
        pf.addDataScheme("package");
        reg(pkgRx, pf);

        timeRx = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) { tick(); }
        };
        IntentFilter tf = new IntentFilter();
        tf.addAction(Intent.ACTION_TIME_TICK);
        tf.addAction(Intent.ACTION_TIME_CHANGED);
        tf.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        reg(timeRx, tf);
    }

    void tick() {
        Date now = new Date();
        boolean h24 = android.text.format.DateFormat.is24HourFormat(this);
        clock.setText(new SimpleDateFormat(h24 ? "HH:mm" : "h:mm", br).format(now));
        String d = new SimpleDateFormat("EEEE, d 'de' MMMM", br).format(now);
        if (!d.isEmpty()) d = Character.toUpperCase(d.charAt(0)) + d.substring(1);
        date.setText(d);
    }

    void applyPrefs() {
        appliedVer = Prefs.ver(this);
        accent = Prefs.accent(this);
        iconPx = Ui.dp(this, Prefs.iconDp(this));
        labels = Prefs.labels(this);
        frame = Prefs.frame(this);
        int alpha = Math.round(Prefs.dim(this) * 255f / 100f);
        dim.setBackgroundColor(Color.argb(alpha, 0, 0, 0));
        clockBox.setVisibility(Prefs.clock(this) ? View.VISIBLE : View.GONE);
        grid.setNumColumns(Prefs.cols(this));
        adapter.notifyDataSetChanged();
    }

    void loadApps() {
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> ris;
            try { ris = pm.queryIntentActivities(i, 0); } catch (Throwable t) { ris = new ArrayList<>(); }
            List<Item> list = new ArrayList<>();
            for (ResolveInfo ri : ris) {
                if (ri.activityInfo == null) continue;
                Item it = new Item();
                it.pkg = ri.activityInfo.packageName;
                it.cn = new ComponentName(it.pkg, ri.activityInfo.name);
                it.key = it.cn.flattenToString();
                CharSequence l;
                try { l = ri.loadLabel(pm); } catch (Throwable t) { l = it.pkg; }
                it.label = l == null ? it.pkg : l.toString();
                it.norm = Ui.norm(it.label);
                if (!ICONS.containsKey(it.key)) {
                    try {
                        Drawable d = ri.loadIcon(pm);
                        if (d != null) ICONS.put(it.key, d);
                    } catch (Throwable ignored) { }
                }
                list.add(it);
            }
            Collections.sort(list, (x, y) -> x.label.compareToIgnoreCase(y.label));
            runOnUiThread(() -> {
                all.clear();
                all.addAll(list);
                filter();
                buildDock();
            });
        }).start();
    }

    void filter() {
        String q = Ui.norm(search.getText().toString().trim());
        Set<String> hidden = Prefs.hidden(this);
        shown.clear();
        for (Item it : all) {
            if (q.isEmpty()) {
                if (!hidden.contains(it.key)) shown.add(it);
            } else if (it.norm.contains(q)) {
                shown.add(it);
            }
        }
        adapter.notifyDataSetChanged();
    }

    void launch(Item it) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(it.cn)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(i);
            if (search.getText().length() > 0) {
                search.setText("");
                hideKeyboard();
            }
        } catch (Throwable t) {
            Ui.toast(this, "Não consegui abrir " + it.label);
        }
    }

    // ------------------------------------------------ menu ao segurar um app
    void menu(Item it) {
        List<String> dockList = Prefs.dock(this);
        boolean inDock = dockList.contains(it.pkg);
        boolean isHidden = Prefs.hidden(this).contains(it.key);
        List<String> opts = new ArrayList<>();
        opts.add("Abrir");
        opts.add(inDock ? "Tirar do dock" : "Fixar no dock");
        opts.add(isHidden ? "Mostrar na tela inicial" : "Ocultar da tela inicial");
        opts.add("Informações do app");
        opts.add("Desinstalar");
        if (Shell.ready()) opts.add("❄ Congelar (Shizuku)");
        Ui.dialog(this).setTitle(it.label)
                .setItems(opts.toArray(new String[0]), (d, which) -> {
                    String o = opts.get(which);
                    if (o.equals("Abrir")) {
                        launch(it);
                    } else if (o.equals("Fixar no dock")) {
                        if (dockList.size() >= 5) {
                            Ui.toast(this, "O dock tem no máximo 5 apps.");
                            return;
                        }
                        dockList.add(it.pkg);
                        Prefs.setDock(this, dockList);
                        appliedVer = Prefs.ver(this);
                        buildDock();
                    } else if (o.equals("Tirar do dock")) {
                        dockList.remove(it.pkg);
                        Prefs.setDock(this, dockList);
                        appliedVer = Prefs.ver(this);
                        buildDock();
                    } else if (o.startsWith("Ocultar") || o.startsWith("Mostrar")) {
                        Set<String> h = Prefs.hidden(this);
                        if (isHidden) h.remove(it.key); else h.add(it.key);
                        Prefs.setHidden(this, h);
                        appliedVer = Prefs.ver(this);
                        filter();
                        if (!isHidden) Ui.toast(this, it.label + " oculto. Busque pelo nome ou reexiba no painel OMNI › Visual.");
                    } else if (o.equals("Informações do app")) {
                        try {
                            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + it.pkg)));
                        } catch (Throwable ignored) { }
                    } else if (o.equals("Desinstalar")) {
                        try {
                            startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + it.pkg)));
                        } catch (Throwable ignored) { }
                    } else if (o.startsWith("❄")) {
                        Shell.run("pm disable-user --user 0 " + it.pkg, (ok, out) -> {
                            Ui.toast(this, ok ? it.label + " congelado. Descongele em OMNI › Apps › Congelados." : "Falhou: " + out);
                            loadApps();
                        });
                    }
                }).show();
    }

    // ------------------------------------------------ dock
    String pkgFor(Intent i) {
        try {
            ResolveInfo ri = getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
            if (ri == null || ri.activityInfo == null) return null;
            String p = ri.activityInfo.packageName;
            return "android".equals(p) ? null : p;
        } catch (Throwable t) {
            return null;
        }
    }

    void buildDock() {
        List<String> pk = Prefs.dock(this);
        if (!Prefs.dockInit(this)) {
            Intent[] defs = {
                    new Intent(Intent.ACTION_DIAL),
                    Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING),
                    Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER),
                    new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)};
            for (Intent i : defs) {
                String p = pkgFor(i);
                if (p != null && !pk.contains(p)) pk.add(p);
            }
            Prefs.setDock(this, pk);
            appliedVer = Prefs.ver(this);
        }
        dock.removeAllViews();
        PackageManager pm = getPackageManager();
        int size = Ui.dp(this, 52);
        for (String p : pk) {
            Drawable icon;
            try { icon = pm.getApplicationIcon(p); } catch (Throwable t) { continue; }
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(icon);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int pad = Ui.dp(this, 4);
            iv.setPadding(pad, pad, pad, pad);
            iv.setOnClickListener(v -> {
                Intent li = pm.getLaunchIntentForPackage(p);
                if (li != null) {
                    try { startActivity(li); } catch (Throwable ignored) { }
                }
            });
            iv.setOnLongClickListener(v -> {
                Ui.dialog(this).setMessage("Tirar este app do dock?")
                        .setPositiveButton("Tirar", (d, w) -> {
                            List<String> l = Prefs.dock(this);
                            l.remove(p);
                            Prefs.setDock(this, l);
                            appliedVer = Prefs.ver(this);
                            buildDock();
                        }).setNegativeButton("Cancelar", null).show();
                return true;
            });
            dock.addView(iv, new LinearLayout.LayoutParams(0, size, 1f));
        }
        // Botão do painel OMNI
        TextView omni = Ui.text(this, "⚙", 22, Color.WHITE, true);
        omni.setGravity(Gravity.CENTER);
        omni.setBackground(Ui.round(accent, size / 2f));
        omni.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMargins(Ui.dp(this, 6), 0, Ui.dp(this, 4), 0);
        dock.addView(omni, lp);
    }

    // ------------------------------------------------ grade
    class Adapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int i) { return shown.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View cv, ViewGroup parent) {
            LinearLayout box;
            ImageView iv;
            TextView tv;
            if (cv == null) {
                box = Ui.vbox(LauncherActivity.this);
                box.setGravity(Gravity.CENTER_HORIZONTAL);
                iv = new ImageView(LauncherActivity.this);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                tv = Ui.text(LauncherActivity.this, "", 12, Color.WHITE, false);
                tv.setSingleLine(true);
                tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
                tv.setGravity(Gravity.CENTER);
                tv.setShadowLayer(6, 0, 1, 0xCC000000);
                tv.setPadding(Ui.dp(LauncherActivity.this, 2), Ui.dp(LauncherActivity.this, 5), Ui.dp(LauncherActivity.this, 2), 0);
                box.addView(iv);
                box.addView(tv, new LinearLayout.LayoutParams(Ui.MATCH, Ui.WRAP));
                box.setTag(new Object[]{iv, tv});
            } else {
                box = (LinearLayout) cv;
                Object[] h = (Object[]) box.getTag();
                iv = (ImageView) h[0];
                tv = (TextView) h[1];
            }
            Item it = shown.get(pos);
            iv.setLayoutParams(new LinearLayout.LayoutParams(iconPx, iconPx));
            if (frame) {
                int pad = iconPx / 6;
                iv.setPadding(pad, pad, pad, pad);
                iv.setBackground(Ui.round((accent & 0x00FFFFFF) | 0x66000000, iconPx / 2f));
            } else {
                iv.setPadding(0, 0, 0, 0);
                iv.setBackground(null);
            }
            iv.setImageDrawable(ICONS.get(it.key));
            tv.setText(it.label);
            tv.setVisibility(labels ? View.VISIBLE : View.GONE);
            return box;
        }
    }
}
