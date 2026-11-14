package com.gameboosterx.rog.data;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Detecção de jogos instalados, lista de jogos selecionados e perfis individuais. */
public final class GameRepository {

    public static final class AppItem {
        public String pkg;
        public String label;
        public boolean isGame;           // categoria de jogo declarada pelo próprio app
        public GameCatalog.Entry catalog; // perfil pré-definido, se houver
        public boolean selected;
    }

    /** Perfil salvo para um jogo. */
    public static final class Profile {
        public String pkg;
        public int brightness = -1;      // % ; -1 = não alterar
        public boolean dnd = false;
        public boolean openSidebar = true;
        public boolean recordSession = true;

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("b", brightness);
            o.put("dnd", dnd);
            o.put("sb", openSidebar);
            o.put("rec", recordSession);
            return o;
        }

        static Profile fromJson(String pkg, String s) {
            Profile p = new Profile();
            p.pkg = pkg;
            try {
                JSONObject o = new JSONObject(s);
                p.brightness = o.optInt("b", -1);
                p.dnd = o.optBoolean("dnd", false);
                p.openSidebar = o.optBoolean("sb", true);
                p.recordSession = o.optBoolean("rec", true);
            } catch (Exception ignored) {
            }
            return p;
        }
    }

    private static final String FILE = "gbx_games";
    private final Context ctx;
    private final SharedPreferences sp;

    public GameRepository(Context c) {
        ctx = c.getApplicationContext();
        sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Lista apps com ícone no launcher; jogos aparecem primeiro. */
    public List<AppItem> scanInstalled() {
        PackageManager pm = ctx.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list;
        try {
            list = pm.queryIntentActivities(main, 0);
        } catch (Exception e) {
            list = new ArrayList<>();
        }
        Set<String> selected = selectedGames();
        Set<String> seen = new HashSet<>();
        List<AppItem> out = new ArrayList<>();
        for (ResolveInfo ri : list) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName()) || !seen.add(pkg)) continue;
            AppItem it = new AppItem();
            it.pkg = pkg;
            it.label = String.valueOf(ri.loadLabel(pm));
            ApplicationInfo ai = ri.activityInfo.applicationInfo;
            it.isGame = ai != null && ai.category == ApplicationInfo.CATEGORY_GAME;
            it.catalog = GameCatalog.find(pkg, it.label);
            it.selected = selected.contains(pkg);
            out.add(it);
        }
        Collections.sort(out, (a, b) -> {
            int ra = rank(a), rb = rank(b);
            if (ra != rb) return ra - rb;
            return a.label.toLowerCase(Locale.ROOT).compareTo(b.label.toLowerCase(Locale.ROOT));
        });
        return out;
    }

    private static int rank(AppItem a) {
        if (a.selected) return 0;
        if (a.catalog != null) return 1;
        if (a.isGame) return 2;
        return 3;
    }

    /** Na primeira execução, seleciona automaticamente os jogos reconhecidos. */
    public void autoSelectIfFirstRun() {
        if (sp.getBoolean("auto_done", false)) return;
        Set<String> sel = new HashSet<>();
        for (AppItem it : scanInstalled()) {
            if (it.catalog != null || it.isGame) sel.add(it.pkg);
        }
        sp.edit().putStringSet("selected", sel).putBoolean("auto_done", true).apply();
    }

    public Set<String> selectedGames() {
        return new HashSet<>(sp.getStringSet("selected", new HashSet<>()));
    }

    public void setSelected(String pkg, boolean on) {
        Set<String> s = selectedGames();
        if (on) s.add(pkg); else s.remove(pkg);
        sp.edit().putStringSet("selected", s).apply();
    }

    public boolean isSelected(String pkg) {
        return selectedGames().contains(pkg);
    }

    public Profile profile(String pkg) {
        String s = sp.getString("p_" + pkg, null);
        if (s != null) return Profile.fromJson(pkg, s);
        // Perfil padrão a partir do catálogo
        Profile p = new Profile();
        p.pkg = pkg;
        GameCatalog.Entry e = GameCatalog.find(pkg, label(pkg));
        if (e != null) {
            p.dnd = e.suggestDnd;
            // Brilho só é alterado se o usuário ativar no perfil (valor sugerido aparece na tela).
        }
        return p;
    }

    public void saveProfile(Profile p) {
        try {
            sp.edit().putString("p_" + p.pkg, p.toJson().toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public String label(String pkg) {
        try {
            PackageManager pm = ctx.getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (Exception e) {
            return pkg;
        }
    }

    public Drawable icon(String pkg) {
        try {
            return ctx.getPackageManager().getApplicationIcon(pkg);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isInstalled(String pkg) {
        try {
            ctx.getPackageManager().getApplicationInfo(pkg, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Intent launchIntent(String pkg) {
        Intent i = ctx.getPackageManager().getLaunchIntentForPackage(pkg);
        if (i != null) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }
}
