package com.realme.boosterultra.utils;

import android.content.Context;
import android.content.SharedPreferences;

/** Todas as configurações do app ficam num lugar só. */
public final class PreferencesManager {
    private PreferencesManager() {}

    public static final String FILE = "turbo";

    public static SharedPreferences get(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}
