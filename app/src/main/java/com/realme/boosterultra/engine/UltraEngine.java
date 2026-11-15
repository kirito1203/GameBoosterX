package com.realme.boosterultra.engine;

import android.content.Context;
import android.content.SharedPreferences;

import com.realme.boosterultra.monitor.WatchService;
import com.realme.boosterultra.utils.PreferencesManager;

/** ULTRA ENGINE: coordena o turbo e o AUTO RESTORE. */
public final class UltraEngine {
    private UltraEngine() {}

    /** AUTO RESTORE: devolve tudo que o próprio app mudou (brilho, tela, Não perturbe, economia). */
    public static String restoreAll(Context c) {
        SharedPreferences p = PreferencesManager.get(c);
        boolean changed = p.getBoolean("did_dnd", false) || p.getBoolean("did_bright", false)
                || p.getBoolean("did_timeout", false) || p.getBoolean("eco_applied", false) || p.getBoolean("active", false);
        ProfileManager.stop(c);
        WatchService.restore(c);
        return changed ? "Configurações restauradas: brilho, tempo de tela, Não perturbe e economia voltaram como estavam."
                : "Nada para restaurar: o app não está com nenhuma configuração alterada.";
    }

    /** Há alguma configuração alterada pelo app agora? */
    public static boolean hasChanges(Context c) {
        SharedPreferences p = PreferencesManager.get(c);
        return p.getBoolean("did_dnd", false) || p.getBoolean("did_bright", false)
                || p.getBoolean("did_timeout", false) || p.getBoolean("eco_applied", false);
    }
}
