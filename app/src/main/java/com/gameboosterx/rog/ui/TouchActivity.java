package com.gameboosterx.rog.ui;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gameboosterx.rog.R;
import com.gameboosterx.rog.core.Diagnostics;
import com.gameboosterx.rog.core.Perms;
import com.gameboosterx.rog.views.TouchTestView;

import java.util.Locale;

/**
 * TOUCH BOOST — o Android não permite que apps alterem a taxa de amostragem
 * ou a sensibilidade física do touchscreen. Este módulo MEDE a resposta ao toque
 * entregue aos apps e leva às configurações do sistema que existem de fato.
 */
public class TouchActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_touch);
        setupHeader("TOUCH BOOST", "Teste e ajustes de resposta ao toque");

        TouchTestView test = findViewById(R.id.touchTest);
        TextView stats = findViewById(R.id.txtTouchStats);
        float[] rr = Diagnostics.refreshRates(this);
        test.setListener((hz, avgMs, pointers, total) -> stats.setText(String.format(Locale.US,
                "Eventos de toque entregues: ~%.0f Hz  •  intervalo médio %.1f ms\nAmostras: %d  •  dedos simultâneos: %d"
                        + "  •  tela agora: %.0f Hz",
                hz, avgMs, total, pointers, rr[0])));
        findViewById(R.id.btnTouchReset).setOnClickListener(v -> {
            test.reset();
            stats.setText("Arraste o dedo pela área acima por alguns segundos.");
        });

        LinearLayout c = findViewById(R.id.touchContent);
        addSection(c, "COMO INTERPRETAR");
        addNote(c, "O valor medido é a frequência com que o sistema entrega eventos de toque aos apps. "
                + "Ele costuma acompanhar a taxa de atualização da tela; com a tela em taxa máxima, a resposta tende a ser melhor. "
                + "Repita o teste com a taxa de atualização em 60 Hz e no máximo para comparar.");

        addSection(c, "ATALHOS DE CONFIGURAÇÃO");
        addRow(c, "Tela", "Taxa de atualização e brilho.", "Abrir",
                v -> Perms.openSetting(this, Settings.ACTION_DISPLAY_SETTINGS));
        addRow(c, "Refresh Rate Manager", "Ver as taxas suportadas pelo aparelho.", "Abrir",
                v -> startActivity(new Intent(this, RefreshRateActivity.class)));
        addRow(c, "Acessibilidade", "\"Atraso ao tocar e manter\": use Curto para respostas mais rápidas em toques longos.", "Abrir",
                v -> Perms.openSetting(this, Settings.ACTION_ACCESSIBILITY_SETTINGS));
        addRow(c, "Opções do desenvolvedor",
                "\"Mostrar toques\" e \"Local do ponteiro\" ajudam a diagnosticar toques falhando. "
                        + "Escalas de animação em 0,5x deixam a interface do sistema mais ágil (não afeta o jogo).", "Abrir",
                v -> Perms.openSetting(this, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));

        addSection(c, "DICAS QUE REALMENTE AJUDAM");
        addNote(c, "• Películas grossas ou com bolhas reduzem a precisão do toque.\n"
                + "• Dedos suados/úmidos causam toques fantasmas — mantenha a tela limpa e seca.\n"
                + "• Carregadores de má qualidade podem gerar ruído no touchscreen; teste sem carregador.\n"
                + "• Nos jogos, ajuste a sensibilidade da câmera/mira nas configurações do próprio jogo.\n"
                + "• Este app não altera a sensibilidade física da tela: isso depende do firmware do fabricante.");
    }
}
