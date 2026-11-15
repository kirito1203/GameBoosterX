package com.pulsesolar.app.modules.optimizer;

import android.content.Context;

import com.pulsesolar.app.core.Analyzer;
import com.pulsesolar.app.core.Format;

import java.util.ArrayList;
import java.util.List;

/** SOLAR CRAFT (Minecraft Bedrock): renderização, efeitos visuais e distância de visão. */
public class CraftSpec extends GameSpec {

    @Override
    public String id() {
        return CRAFT;
    }

    @Override
    public String title() {
        return "SOLAR CRAFT";
    }

    @Override
    public String gameName() {
        return "Minecraft";
    }

    @Override
    public String tagline() {
        return "Renderização, efeitos e distância de visão";
    }

    @Override
    public String[] knownPackages() {
        return new String[]{"com.mojang.minecraftpe"};
    }

    @Override
    public String[] labelKeywords() {
        return new String[]{"minecraft"};
    }

    @Override
    public String graphicsWhere() {
        return "No Minecraft: Configurações → Vídeo (distância de simulação fica nas configurações do mundo). Os nomes podem mudar entre versões. O Pulse Solar não altera o jogo nem seus mundos.";
    }

    /** Distância de renderização recomendada (chunks). */
    static int renderDistance(DeviceState d) {
        int t = d.effectiveTier();
        int base = t == DeviceState.LOW ? 6 : t == DeviceState.MID ? 10 : 14;
        float avail = d.ramAvailGb();
        if (avail < 1.5f) base -= 4;
        else if (avail < 2.5f) base -= 2;
        return Math.max(4, base);
    }

    @Override
    protected void gameDiagnose(Context c, DeviceState d, List<Analyzer.Finding> out) {
        float avail = d.ramAvailGb();
        if (avail < 2f) {
            out.add(new Analyzer.Finding(Analyzer.WARN, "Pouca RAM livre para o Minecraft",
                    Format.one(avail) + " GB livres. Distâncias de renderização altas e add-ons pesados podem travar ou fechar o jogo."));
        } else {
            out.add(new Analyzer.Finding(Analyzer.OK, "RAM livre adequada", Format.one(avail) + " GB livres."));
        }
        if (!Float.isNaN(d.point.storageFreePct) && d.point.storageFreePct < 8f) {
            out.add(new Analyzer.Finding(Analyzer.WARN, "Pouco espaço livre",
                    "Mundos crescem conforme você explora; com pouco espaço o salvamento fica lento."));
        }
        out.add(new Analyzer.Finding(Analyzer.INFO, "Distância de renderização sugerida: " + renderDistance(d) + " chunks",
                "Calculada pela classe do aparelho (" + DeviceState.tierName(d.effectiveTier()) + ") e pela RAM livre agora ("
                        + Format.one(avail) + " GB)."));
    }

    @Override
    public List<String[]> graphics(Context c, DeviceState d) {
        int t = d.effectiveTier();
        boolean low = t == DeviceState.LOW, high = t == DeviceState.HIGH;
        List<String[]> r = new ArrayList<>();
        r.add(row("Distância de renderização", renderDistance(d) + " chunks"));
        r.add(row("Distância de simulação (mundo)", high ? "4–6 chunks" : "4 chunks"));
        r.add(row("Gráficos sofisticados", high ? "Ligado" : "Desligado"));
        r.add(row("Iluminação suave", low ? "Desligado" : "Ligado"));
        r.add(row("Céu bonito", high ? "Ligado" : "Desligado"));
        r.add(row("Renderizar nuvens", high ? "Ligado" : "Desligado"));
        r.add(row("Folhas sofisticadas", high ? "Ligado" : "Desligado"));
        r.add(row("Visuais vibrantes (se disponível)", high && !d.hot ? "Opcional — observe a temperatura" : "Desligado"));
        r.add(row("Taxa da tela (Android)", d.maxHz > 60 && !low ? "Teste " + d.maxHz + " Hz; 60 Hz se esquentar" : "60 Hz"));
        return r;
    }

    @Override
    protected void gameTips(Context c, DeviceState d, OptimizerStore.AppStorage st, List<Analyzer.Finding> out) {
        if (st != null) {
            out.add(new Analyzer.Finding(st.data > 4L * 1024 * 1024 * 1024 ? Analyzer.WARN : Analyzer.INFO,
                    "Dados do Minecraft: " + Format.bytes(st.data),
                    "Inclui mundos, pacotes e add-ons. Mundos muito grandes demoram mais para carregar; faça backup e apague os que não usa."));
        }
        out.add(new Analyzer.Finding(Analyzer.INFO, "Add-ons e texturas pesam na RAM",
                "Add-ons com muitas entidades, modelos 3D e texturas em alta resolução aumentam o uso de memória. Ative só os que estiver usando no mundo."));
        out.add(new Analyzer.Finding(Analyzer.INFO, "Travou ao explorar? Reduza a distância",
                "A geração de chunks novos é o momento mais pesado. Diminua 2 chunks se travar ao andar rápido ou voar."));
        out.add(new Analyzer.Finding(Analyzer.INFO, "Muitas entidades em um lugar",
                "Fazendas com muitos animais, itens no chão e redstone rápida exigem CPU; espalhe ou limite."));
    }
}
