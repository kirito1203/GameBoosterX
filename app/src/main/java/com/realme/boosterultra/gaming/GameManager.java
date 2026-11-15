package com.realme.boosterultra.gaming;

import com.realme.boosterultra.*;
import com.realme.boosterultra.engine.*;
import com.realme.boosterultra.ai.*;
import com.realme.boosterultra.gaming.*;
import com.realme.boosterultra.monitor.*;
import com.realme.boosterultra.utils.*;
import com.realme.boosterultra.ui.*;


import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Lista de apps e detecção de jogos. */
public final class GameManager {
    private GameManager() {}

    public static final class Item {
        public String pkg, label;
        public Drawable icon;
        public boolean autoGame;
    }

    public static final Set<String> KNOWN = new HashSet<>(Arrays.asList(
            "com.mojang.minecraftpe", "com.roblox.client", "com.dts.freefireth", "com.dts.freefiremax",
            "com.tencent.ig", "com.activision.callofduty.shooter", "com.supercell.brawlstars",
            "com.supercell.clashroyale", "com.supercell.clashofclans", "com.miHoYo.GenshinImpact",
            "com.kiloo.subwaysurf", "com.mobile.legends", "com.ea.gp.fifamobile", "com.innersloth.spacemafia"));

    public static List<Item> launchable(Context c) {
        PackageManager pm = c.getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(i, 0);
        Map<String, Item> m = new LinkedHashMap<>();
        for (ResolveInfo ri : list) {
            String p = ri.activityInfo.packageName;
            if (p.equals(c.getPackageName()) || m.containsKey(p)) continue;
            Item it = new Item();
            it.pkg = p;
            it.label = String.valueOf(ri.loadLabel(pm));
            try { it.icon = ri.loadIcon(pm); } catch (Exception ignored) {}
            ApplicationInfo ai = ri.activityInfo.applicationInfo;
            @SuppressWarnings("deprecation")
            boolean flagGame = (ai.flags & ApplicationInfo.FLAG_IS_GAME) != 0;
            it.autoGame = ai.category == ApplicationInfo.CATEGORY_GAME || flagGame || KNOWN.contains(p);
            m.put(p, it);
        }
        List<Item> out = new ArrayList<>(m.values());
        Collections.sort(out, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return out;
    }

    public static List<Item> games(Context c, List<Item> all) {
        SharedPreferences p = ProfileManager.prefs(c);
        Set<String> added = p.getStringSet("added", Collections.emptySet());
        Set<String> hidden = p.getStringSet("hidden", Collections.emptySet());
        List<Item> out = new ArrayList<>();
        for (Item it : all) {
            if (!hidden.contains(it.pkg) && (it.autoGame || added.contains(it.pkg))) out.add(it);
        }
        return out;
    }

    /** AUTO GAME DETECT: esse pacote é um jogo da lista? */
    public static boolean isGame(Context c, String pkg) {
        if (pkg == null || pkg.equals(c.getPackageName())) return false;
        SharedPreferences p = ProfileManager.prefs(c);
        if (p.getStringSet("hidden", Collections.emptySet()).contains(pkg)) return false;
        if (p.getStringSet("added", Collections.emptySet()).contains(pkg) || KNOWN.contains(pkg)) return true;
        try {
            ApplicationInfo ai = c.getPackageManager().getApplicationInfo(pkg, 0);
            @SuppressWarnings("deprecation")
            boolean flagGame = (ai.flags & ApplicationInfo.FLAG_IS_GAME) != 0;
            return ai.category == ApplicationInfo.CATEGORY_GAME || flagGame;
        } catch (Exception e) {
            return false;
        }
    }

    public static void add(Context c, String pkg) {
        SharedPreferences p = ProfileManager.prefs(c);
        Set<String> added = new HashSet<>(p.getStringSet("added", Collections.emptySet()));
        Set<String> hidden = new HashSet<>(p.getStringSet("hidden", Collections.emptySet()));
        added.add(pkg);
        hidden.remove(pkg);
        p.edit().putStringSet("added", added).putStringSet("hidden", hidden).apply();
    }

    public static void remove(Context c, String pkg) {
        SharedPreferences p = ProfileManager.prefs(c);
        Set<String> added = new HashSet<>(p.getStringSet("added", Collections.emptySet()));
        Set<String> hidden = new HashSet<>(p.getStringSet("hidden", Collections.emptySet()));
        added.remove(pkg);
        hidden.add(pkg);
        p.edit().putStringSet("added", added).putStringSet("hidden", hidden).apply();
    }
}
