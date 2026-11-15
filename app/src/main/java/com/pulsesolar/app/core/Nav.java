package com.pulsesolar.app.core;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;

/** Abre telas do sistema tentando alternativas, pois cada fabricante expõe telas diferentes. */
public final class Nav {

    private Nav() {
    }

    public static boolean open(Context c, Intent... intents) {
        for (Intent i : intents) {
            if (i == null) continue;
            try {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
                return true;
            } catch (ActivityNotFoundException | SecurityException ignored) {
                // tenta a próxima
            }
        }
        Toast.makeText(c, "Esta tela não está disponível neste aparelho", Toast.LENGTH_SHORT).show();
        return false;
    }

    public static Intent appDetails(String pkg) {
        return new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg));
    }

    public static Intent settings() {
        return new Intent(Settings.ACTION_SETTINGS);
    }
}
