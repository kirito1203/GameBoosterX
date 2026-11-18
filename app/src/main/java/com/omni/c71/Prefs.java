package com.omni.c71;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Preferências do OMNI (tela inicial e tema). */
public final class Prefs {
    private Prefs() {}

    static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("omni", Context.MODE_PRIVATE);
    }

    public static int accent(Context c) { return sp(c).getInt("accent", 0xFF4D8DFF); }
    public static int cols(Context c) { return sp(c).getInt("cols", 4); }
    public static int iconDp(Context c) { return sp(c).getInt("iconDp", 54); }
    public static int dim(Context c) { return sp(c).getInt("dim", 25); }
    public static boolean labels(Context c) { return sp(c).getBoolean("labels", true); }
    public static boolean clock(Context c) { return sp(c).getBoolean("clock", true); }
    public static boolean frame(Context c) { return sp(c).getBoolean("frame", false); }
    public static int ver(Context c) { return sp(c).getInt("ver", 0); }

    public static void putInt(Context c, String k, int v) {
        SharedPreferences s = sp(c);
        s.edit().putInt(k, v).putInt("ver", s.getInt("ver", 0) + 1).apply();
    }

    public static void putBool(Context c, String k, boolean v) {
        SharedPreferences s = sp(c);
        s.edit().putBoolean(k, v).putInt("ver", s.getInt("ver", 0) + 1).apply();
    }

    public static Set<String> hidden(Context c) {
        return new HashSet<>(sp(c).getStringSet("hidden", new HashSet<>()));
    }

    public static void setHidden(Context c, Set<String> s) {
        SharedPreferences p = sp(c);
        p.edit().putStringSet("hidden", new HashSet<>(s)).putInt("ver", p.getInt("ver", 0) + 1).apply();
    }

    public static boolean dockInit(Context c) { return sp(c).getBoolean("dockInit", false); }

    public static List<String> dock(Context c) {
        List<String> out = new ArrayList<>();
        String s = sp(c).getString("dock", "");
        if (s == null || s.isEmpty()) return out;
        for (String x : s.split("\\|")) if (!x.isEmpty()) out.add(x);
        return out;
    }

    public static void setDock(Context c, List<String> pkgs) {
        StringBuilder sb = new StringBuilder();
        for (String p : pkgs) {
            if (sb.length() > 0) sb.append('|');
            sb.append(p);
        }
        SharedPreferences p = sp(c);
        p.edit().putString("dock", sb.toString()).putBoolean("dockInit", true)
                .putInt("ver", p.getInt("ver", 0) + 1).apply();
    }
}
