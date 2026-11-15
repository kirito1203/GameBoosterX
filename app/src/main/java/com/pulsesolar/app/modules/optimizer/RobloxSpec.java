package com.pulsesolar.app.modules.optimizer;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

import com.pulsesolar.app.core.Analyzer;
import com.pulsesolar.app.core.Format;
import com.pulsesolar.app.core.Nav;

import java.util.ArrayList;
import java.util.List;

/** SOLAR ROBLOX: ajustes gráficos do Roblox conforme a classe e o estado do aparelho. */
public class RobloxSpec extends GameSpec {

    @Override
    public String id() {
        return ROBLOX;
    }

    @Override
    public String title() {
        return "SOLAR ROBLOX";
    }

    @Override
    public String gameName() {
        return "Roblox";
    }

    @Override
    public String tagline() {
        return "Qualidade gráfica, memória e conexão";
    }

    @Override
    public String[] knownPackages() {
        return new String[]{"com.roblox.client"};
    }

    @Override
    public String[] labelKeywords() {
        return new String[]{"roblox"};
    }

    @Override
    public String graphicsWhere() {
        return "No Roblox: toque no logo/menu → Configurações. O Pulse Solar não altera o jogo; aplique estes valores manualmente.";
    }

    @Override
    protected void gameDiagnose(Context c, DeviceState d, List<Analyzer.Finding> out) {
        if (!d.net.connected || !d.net.validated) {
            out.add(new Analyzer.Finding(Analyzer.CRIT, "Roblox precisa de internet",
                    "Conexão atual: " + d.net.transport() + (d.net.connected ? " sem acesso validado" : "") + "."));
        } else {
            out.add(new Analyzer.Finding(Analyzer.OK, "Internet disponível", "Conectado por " + d.net.transport() + "."));
        }
        float avail = d.ramAvailGb();
        if (avail < 1.5f) {
            out.add(new Analyzer.Finding(Analyzer.WARN, "Pouca RAM livre para o Roblox",
                    Format.one(avail) + " GB livres. Experiências grandes podem fechar ou travar; feche outros apps."));
        } else {
            out.add(new Analyzer.Finding(Analyzer.OK, "RAM livre adequada", Format.one(avail) + " GB livres."));
        }
        if (!Float.isNaN(d.point.storageFreePct) && d.point.storageFreePct < 8f) {
            out.add(new Analyzer.Finding(Analyzer.WARN, "Pouco espaço livre",
                    "O Roblox baixa conteúdo de cada experiência; com pouco espaço o carregamento fica lento.")
                    .action("Abrir armazenamento", new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS), Nav.settings()));
        }
    }

    @Override
    public List<String[]> graphics(Context c, DeviceState d) {
        int t = d.effectiveTier();
        List<String[]> r = new ArrayList<>();
        r.add(row("Modo gráfico", "Manual"));
        r.add(row("Qualidade gráfica",
                t == DeviceState.LOW ? "1–2 barras" : t == DeviceState.MID ? "3–4 barras" : "5–7 barras"));
        String fps;
        if (t == DeviceState.HIGH && d.maxHz >= 90 && !d.hot) {
            fps = d.maxHz + " se a temperatura ficar estável; senão 60";
        } else {
            fps = "60";
        }
        r.add(row("Taxa de quadros máxima (se a opção existir)", fps));
        r.add(row("Taxa da tela (Configurações do Android)",
                d.maxHz > 60 ? "Teste " + d.maxHz + " Hz; use 60 Hz se esquentar" : "60 Hz"));
        r.add(row("Rede", d.net.wifi ? "Wi-Fi atual" : "Prefira Wi-Fi, de preferência 5 GHz"));
        return r;
    }

    @Override
    protected void gameTips(Context c, DeviceState d, OptimizerStore.AppStorage st, List<Analyzer.Finding> out) {
        if (st != null && st.cache > 1024L * 1024 * 1024) {
            out.add(new Analyzer.Finding(Analyzer.WARN, "Cache do Roblox grande",
                    "O Roblox está com " + Format.bytes(st.cache) + " de cache. Limpar o cache (não os dados) na tela do app libera espaço sem apagar sua conta."));
        }
        out.add(new Analyzer.Finding(Analyzer.INFO, "Abaixe a qualidade antes de experiências pesadas",
                "Mapas com muitos jogadores, efeitos e construções consomem mais CPU e RAM. Servidores menos cheios costumam travar menos."));
        out.add(new Analyzer.Finding(Analyzer.INFO, "Reinicie o Roblox em sessões longas",
                "Ficar horas trocando de experiência acumula uso de memória; fechar e abrir de novo libera."));
        out.add(new Analyzer.Finding(Analyzer.INFO, "Conexão estável importa tanto quanto gráficos",
                "Travadas com personagem \"teleportando\" são de rede, não de gráficos. Use o teste de conexão desta tela."));
    }
}
