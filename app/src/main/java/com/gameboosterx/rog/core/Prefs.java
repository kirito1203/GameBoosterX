package com.gameboosterx.rog.core;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/** Configurações gerais do app. */
public final class Prefs {

    private static final String FILE = "gbx_settings";

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Temperatura da bateria (°C) a partir da qual o app considera "quente". */
    public static float tempWarn(Context c) { return sp(c).getFloat("temp_warn", 41f); }
    public static void setTempWarn(Context c, float v) { sp(c).edit().putFloat("temp_warn", v).apply(); }

    /** Intervalo de amostragem do monitor, em segundos. */
    public static int sampleSeconds(Context c) { return sp(c).getInt("sample_s", 3); }
    public static void setSampleSeconds(Context c, int v) { sp(c).edit().putInt("sample_s", v).apply(); }

    public static boolean autoDetect(Context c) { return sp(c).getBoolean("auto_detect", true); }
    public static void setAutoDetect(Context c, boolean v) { sp(c).edit().putBoolean("auto_detect", v).apply(); }

    public static boolean heatAlerts(Context c) { return sp(c).getBoolean("heat_alerts", true); }
    public static void setHeatAlerts(Context c, boolean v) { sp(c).edit().putBoolean("heat_alerts", v).apply(); }

    public static boolean turboActive(Context c) { return sp(c).getBoolean("turbo", false); }
    public static void setTurboActive(Context c, boolean v) { sp(c).edit().putBoolean("turbo", v).apply(); }

    /** Apps fixados como atalho na barra lateral (máx. 5). */
    public static Set<String> sidebarApps(Context c) {
        return new HashSet<>(sp(c).getStringSet("sidebar_apps", new HashSet<>()));
    }
    public static void setSidebarApps(Context c, Set<String> s) {
        sp(c).edit().putStringSet("sidebar_apps", new HashSet<>(s)).apply();
    }

    public static String lastPingHost(Context c) { return sp(c).getString("ping_host", "8.8.8.8"); }
    public static void setLastPingHost(Context c, String h) { sp(c).edit().putString("ping_host", h).apply(); }
}
