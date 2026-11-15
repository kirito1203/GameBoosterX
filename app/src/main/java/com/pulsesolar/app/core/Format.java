package com.pulsesolar.app.core;

import java.util.Locale;

/** Formatação de números para exibição. */
public final class Format {

    private Format() {
    }

    public static String bytes(long b) {
        if (b < 0) return "—";
        if (b < 1024) return b + " B";
        double kb = b / 1024.0;
        if (kb < 1024) return String.format(Locale.getDefault(), "%.0f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB", mb);
        double gb = mb / 1024.0;
        return String.format(Locale.getDefault(), "%.2f GB", gb);
    }

    public static String percent(float p) {
        return String.format(Locale.getDefault(), "%.1f%%", p);
    }

    public static String duration(long ms) {
        if (ms < 0) return "—";
        long totalMin = ms / 60000L;
        long days = totalMin / (60 * 24);
        long hours = (totalMin / 60) % 24;
        long min = totalMin % 60;
        if (days > 0) return days + " d " + hours + " h " + min + " min";
        if (hours > 0) return hours + " h " + min + " min";
        return min + " min";
    }

    public static String ghz(long khz) {
        if (khz <= 0) return "indisponível";
        if (khz >= 1_000_000) {
            return String.format(Locale.getDefault(), "%.2f GHz", khz / 1_000_000.0);
        }
        return String.format(Locale.getDefault(), "%d MHz", khz / 1000);
    }

    public static String one(float v) {
        return String.format(Locale.getDefault(), "%.1f", v);
    }
}
