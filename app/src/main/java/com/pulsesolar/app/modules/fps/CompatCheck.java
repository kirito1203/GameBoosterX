package com.pulsesolar.app.modules.fps;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Display;

import com.pulsesolar.app.core.Format;
import com.pulsesolar.app.core.Perms;
import com.pulsesolar.app.core.SystemInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Testa NO APARELHO quais leituras o Android libera para o Pulse Solar.
 * Resultado: ✔ funciona, ◐ parcial/depende de permissão, ✖ não disponível.
 */
public final class CompatCheck {

    public static final class Item {
        public final String status, name, detail;

        Item(String status, String name, String detail) {
            this.status = status;
            this.name = name;
            this.detail = detail;
        }
    }

    private static final String[] GAME_SPACE = {
            "com.oplus.games", "com.coloros.gamespace", "com.oplus.gamespace", "com.coloros.gamespaceui"
    };

    private CompatCheck() {
    }

    @SuppressWarnings("deprecation")
    public static List<Item> run(Activity a) {
        List<Item> out = new ArrayList<>();

        out.add(new Item("✖", "FPS real de outros jogos",
                "O Android não oferece essa leitura a apps sem root/ADB. Nenhum valor de FPS de jogo é mostrado pelo Pulse Solar."));

        Display d = a.getWindowManager().getDefaultDisplay();
        TreeSet<Integer> rates = new TreeSet<>();
        for (Display.Mode m : d.getSupportedModes()) rates.add(Math.round(m.getRefreshRate()));
        out.add(new Item("✔", "Pulso do sistema (Choreographer)",
                "O Choreographer monitora os quadros do PRÓPRIO Pulse Solar, não o FPS dos jogos. Serve para ver a taxa da tela e engasgos do sistema. Tela agora: " + Math.round(d.getRefreshRate())
                        + " Hz; suportadas: " + rates + "."));

        SystemInfo.Battery b = SystemInfo.battery(a);
        out.add(Float.isNaN(b.tempC)
                ? new Item("✖", "Temperatura da bateria", "Não informada.")
                : new Item("✔", "Temperatura da bateria", Format.one(b.tempC) + " °C agora."));

        out.add(b.thermalStatus >= 0
                ? new Item("✔", "Estado térmico do sistema", SystemInfo.thermalText(b.thermalStatus) + " (API oficial do Android).")
                : new Item("✖", "Estado térmico do sistema", "Exige Android 10+."));

        if (Build.VERSION.SDK_INT >= 30) {
            PowerManager pm = (PowerManager) a.getSystemService(Activity.POWER_SERVICE);
            float h = pm.getThermalHeadroom(10);
            out.add(Float.isNaN(h)
                    ? new Item("◐", "Previsão térmica (headroom)", "API existe, mas o fabricante não forneceu valor agora. Pode aparecer depois de alguns segundos de uso.")
                    : new Item("✔", "Previsão térmica (headroom)", String.format(java.util.Locale.getDefault(), "%.2f agora (1,00 = limitação).", h)));
        }

        List<CpuProbe.Cluster> cl = CpuProbe.read();
        CpuProbe.Cluster big = CpuProbe.big(cl);
        if (big == null) {
            out.add(new Item("✖", "Frequências da CPU", "Leitura do cpufreq bloqueada pelo SELinux deste aparelho."));
        } else {
            out.add(new Item(big.curKhz > 0 ? "✔" : "◐", "Frequências da CPU",
                    cl.size() + " cluster(s); mais rápido até " + Format.ghz(big.hwMaxKhz)
                            + (big.curKhz > 0 ? ", agora " + Format.ghz(big.curKhz) : ", frequência atual bloqueada") + "."));
            out.add(big.capKhz > 0
                    ? new Item("✔", "Teto da CPU (detecção de limitação)", "Teto atual: " + Format.ghz(big.capKhz) + ".")
                    : new Item("✖", "Teto da CPU (detecção de limitação)", "scaling_max_freq bloqueado."));
        }

        out.add(CpuProbe.procStatReadable()
                ? new Item("✔", "Uso total da CPU (%)", "/proc/stat legível neste aparelho.")
                : new Item("✖", "Uso total da CPU (%)", "Bloqueado para apps desde o Android 8. O Pulse Solar não estima esse valor."));

        out.add(new Item("✖", "Uso e frequência da GPU",
                "Não há API oficial do Android para apps lerem a GPU."));

        out.add(Settings.canDrawOverlays(a)
                ? new Item("✔", "Painel sobre o jogo (sobreposição)", "Permitido.")
                : new Item("◐", "Painel sobre o jogo (sobreposição)", "Precisa da permissão \"Exibir sobre outros apps\"."));

        out.add(Perms.hasUsageAccess(a)
                ? new Item("✔", "Identificar o jogo da sessão", "Acesso ao uso concedido.")
                : new Item("◐", "Identificar o jogo da sessão", "Precisa do \"acesso ao uso\" (ou inicie pelo perfil do jogo)."));

        boolean notif = Build.VERSION.SDK_INT < 33
                || a.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        out.add(notif
                ? new Item("✔", "Alertas de aquecimento", "Notificações permitidas.")
                : new Item("◐", "Alertas de aquecimento", "Permita notificações para receber os alertas."));

        PackageManager pmg = a.getPackageManager();
        String gs = null;
        for (String p : GAME_SPACE) if (pmg.getLaunchIntentForPackage(p) != null) gs = p;
        out.add(gs != null
                ? new Item("✔", "Espaço de Jogos da realme", "Instalado (" + gs + "). Se ele tiver medidor de FPS, é a forma oficial de ver o FPS do jogo.")
                : new Item("◐", "Espaço de Jogos da realme", "Não encontrado como app separado neste aparelho."));
        return out;
    }
}
