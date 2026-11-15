package com.pulsesolar.app.core;

/** Cores do tema Pulse Solar usadas em código (iguais a res/values/colors.xml). */
public final class Palette {

    public static final int GOLD = 0xFFFFB300;
    public static final int ORANGE = 0xFFFF6D00;
    public static final int SOLAR_LIGHT = 0xFFFFE082;
    public static final int BLUE = 0xFF2EA8FF;
    public static final int GREEN = 0xFF4CFF9A;
    public static final int AMBER = 0xFFFFC400;
    public static final int RED = 0xFFFF4D4D;
    public static final int DIM = 0xFF7F93B2;
    public static final int TEXT = 0xFFEAF2FF;

    private Palette() {
    }

    /** Uso de RAM/armazenamento (%). */
    public static int forLoad(float usedPercent) {
        if (usedPercent >= 90f) return RED;
        if (usedPercent >= 75f) return AMBER;
        return BLUE;
    }

    /** Nível de bateria (%). */
    public static int forLevel(int level) {
        if (level <= 15) return RED;
        if (level <= 30) return AMBER;
        return GREEN;
    }

    /** Temperatura da bateria (°C). */
    public static int forTemp(float c) {
        if (Float.isNaN(c)) return DIM;
        if (c >= 42f) return RED;
        if (c >= 38f) return AMBER;
        return GREEN;
    }
}
