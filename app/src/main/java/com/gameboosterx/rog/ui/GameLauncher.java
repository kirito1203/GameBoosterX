package com.gameboosterx.rog.ui;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;

import com.gameboosterx.rog.core.Perms;
import com.gameboosterx.rog.data.GameRepository;
import com.gameboosterx.rog.service.BoostService;

/** Abre um jogo garantindo que o monitor esteja ativo para aplicar o perfil. */
public final class GameLauncher {

    private GameLauncher() {}

    public static void launch(Activity a, String pkg) {
        GameRepository repo = new GameRepository(a);
        Intent li = repo.launchIntent(pkg);
        if (li == null) {
            Toast.makeText(a, "Não foi possível abrir este app.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!repo.isSelected(pkg)) repo.setSelected(pkg, true);
        if (!Perms.usageStats(a)) {
            Toast.makeText(a, "Sem o Acesso de Uso, o perfil e a gravação da sessão não são aplicados automaticamente.",
                    Toast.LENGTH_LONG).show();
        }
        try {
            if (!BoostService.LIVE.running) BoostService.start(a);
        } catch (Exception ignored) {
        }
        Perms.open(a, li);
    }
}
