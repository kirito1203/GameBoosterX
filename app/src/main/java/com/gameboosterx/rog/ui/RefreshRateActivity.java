package com.gameboosterx.rog.ui;

import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Display;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.gameboosterx.rog.R;
import com.gameboosterx.rog.core.Perms;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * REFRESH RATE MANAGER.
 * Apps comuns não podem mudar a taxa de atualização do sistema inteiro
 * (exige permissão de sistema). O que é permitido:
 *  - consultar os modos suportados;
 *  - escolher um modo para a janela do PRÓPRIO app (usado aqui como prévia);
 *  - abrir a configuração de Tela, onde o usuário define a taxa global.
 */
public class RefreshRateActivity extends BaseActivity {

    private LinearLayout content;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_module);
        setupHeader("REFRESH RATE", "Taxas de atualização da tela");
        content = findViewById(R.id.content);
        render();
    }

    private Display display() {
        DisplayManager dm = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        return dm.getDisplay(Display.DEFAULT_DISPLAY);
    }

    private void render() {
        content.removeAllViews();
        Display d = display();
        if (d == null) {
            addNote(content, "Não foi possível consultar a tela.");
            return;
        }
        Display.Mode cur = d.getMode();
        List<Display.Mode> modes = new ArrayList<>();
        for (Display.Mode m : d.getSupportedModes()) modes.add(m);
        modes.sort((a, c) -> Float.compare(c.getRefreshRate(), a.getRefreshRate()));

        addSection(content, "AGORA");
        addNote(content, String.format(Locale.US, "Taxa atual: %.0f Hz  •  Resolução: %d x %d\n"
                        + "Observação: com a taxa adaptativa, o sistema reduz a taxa quando a tela está parada.",
                d.getRefreshRate(), cur.getPhysicalWidth(), cur.getPhysicalHeight()));

        addSection(content, "MODOS SUPORTADOS");
        for (Display.Mode m : modes) {
            boolean isCur = m.getModeId() == cur.getModeId();
            String title = String.format(Locale.US, "%.0f Hz  •  %d x %d%s",
                    m.getRefreshRate(), m.getPhysicalWidth(), m.getPhysicalHeight(), isCur ? "  (atual)" : "");
            addRow(content, title, impact(m.getRefreshRate()), "Prévia", v -> preview(m));
        }

        addSection(content, "DEFINIR PARA O SISTEMA");
        addRow(content, "Configurações de tela",
                "No realme UI: Tela e brilho ▸ Taxa de atualização da tela. Escolha \"Alta\" para jogos que passam de 60 FPS.",
                "Abrir", v -> Perms.openSetting(this, Settings.ACTION_DISPLAY_SETTINGS));

        addSection(content, "IMPACTO NA BATERIA");
        addNote(content, "• Taxas maiores deixam a rolagem e os jogos que passam de 60 FPS mais fluidos, mas a tela consome mais energia.\n"
                + "• Se o jogo está limitado a 30 ou 60 FPS, usar 90/120 Hz traz pouco ganho visual e gasta mais bateria.\n"
                + "• O modo automático/adaptativo equilibra: alto nas interações, baixo em telas paradas.\n"
                + "• Muitos jogos escolhem a própria taxa; o sistema respeita esse pedido quando possível.");
    }

    private static String impact(float hz) {
        if (hz >= 110) return "Máxima fluidez • maior consumo de bateria";
        if (hz >= 85) return "Fluidez alta • consumo intermediário";
        if (hz >= 55) return "Padrão • menor consumo";
        return "Economia máxima • pouca fluidez";
    }

    /** Aplica o modo apenas na janela deste app (permitido pelo Android). */
    private void preview(Display.Mode m) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.preferredDisplayModeId = m.getModeId();
        getWindow().setAttributes(lp);
        Toast.makeText(this, String.format(Locale.US,
                "Prévia de %.0f Hz aplicada nesta tela. Role a página para sentir a diferença.", m.getRefreshRate()),
                Toast.LENGTH_LONG).show();
        ui.postDelayed(this::render, 800);
    }
}
