package com.pulsesolar.app.modules.optimizer;

import android.content.Context;

import com.pulsesolar.app.core.Analyzer;
import com.pulsesolar.app.core.Perms;
import com.pulsesolar.app.modules.fps.GameProfiles;

import java.util.ArrayList;
import java.util.List;

/** SOLAR RHYTHM (Friday Night Funkin'): estabilidade de quadros e sincronia musical. */
public class RhythmSpec extends GameSpec {

    @Override
    public String id() {
        return RHYTHM;
    }

    @Override
    public String title() {
        return "SOLAR RHYTHM";
    }

    @Override
    public String gameName() {
        return "Friday Night Funkin'";
    }

    @Override
    public String tagline() {
        return "Quadros estáveis e sincronia musical";
    }

    @Override
    public String[] knownPackages() {
        // FNF no Android existe em versões/ports com pacotes diferentes: a detecção usa o nome do app
        // e o usuário pode escolher o app manualmente.
        return new String[0];
    }

    @Override
    public String[] labelKeywords() {
        return new String[]{"funkin", "fnf"};
    }

    @Override
    public String graphicsWhere() {
        return "No FNF: Options/Opções (os nomes variam entre versões e ports). O Pulse Solar não altera o jogo nem seus arquivos.";
    }

    @Override
    public GameProfiles.Profile defaultProfile() {
        GameProfiles.Profile p = super.defaultProfile();
        p.dnd = true; // som de notificação atrapalha o ritmo
        return p;
    }

    @Override
    protected void gameDiagnose(Context c, DeviceState d, List<Analyzer.Finding> out) {
        if (d.btAudio) {
            out.add(new Analyzer.Finding(Analyzer.WARN, "Fone Bluetooth conectado",
                    "Áudio por Bluetooth costuma ter bastante atraso, o que tira a música de sincronia com as notas. Prefira fone com fio ou o alto-falante, ou calibre o offset com o Bluetooth ligado."));
        } else if (d.wiredAudio) {
            out.add(new Analyzer.Finding(Analyzer.OK, "Fone com fio", "Menor atraso de áudio para jogos de ritmo."));
        }
        out.add(new Analyzer.Finding(d.lowLatencyAudio ? Analyzer.OK : Analyzer.INFO,
                d.lowLatencyAudio ? "Áudio de baixa latência suportado" : "Áudio de baixa latência não declarado",
                d.lowLatencyAudio
                        ? "O aparelho declara suporte a áudio de baixa latência" + (d.proAudio ? " e áudio profissional." : ".")
                        : "O aparelho não declara o recurso de baixa latência; a calibração de offset fica ainda mais importante."));
        if (OptimizerStore.hasRhythmOffset(c)) {
            out.add(new Analyzer.Finding(Analyzer.OK, "Offset calibrado",
                    "Atraso medido: " + OptimizerStore.rhythmOffset(c) + " ms (variação ±" + OptimizerStore.rhythmSd(c) + " ms)"
                            + (OptimizerStore.rhythmBluetooth(c) ? ", com Bluetooth." : ".")));
        } else {
            out.add(new Analyzer.Finding(Analyzer.WARN, "Offset ainda não calibrado",
                    "Faça a calibração de sincronia desta tela para saber o atraso real do seu áudio + toque."));
        }
        if (!Perms.hasDndAccess(c)) {
            out.add(new Analyzer.Finding(Analyzer.INFO, "Não Perturbe sem permissão",
                    "Notificações durante uma música atrapalham o ritmo. Conceda o acesso no Solar Gaming para o perfil silenciar."));
        }
    }

    /** Limite de FPS que prioriza estabilidade. */
    static int recommendedCap(DeviceState d) {
        int t = d.effectiveTier();
        if (t == DeviceState.HIGH && d.maxHz >= 120 && !d.hot) return 120;
        if (t == DeviceState.HIGH && d.maxHz >= 90 && !d.hot) return 90;
        return 60;
    }

    @Override
    public List<String[]> graphics(Context c, DeviceState d) {
        int t = d.effectiveTier();
        int cap = recommendedCap(d);
        List<String[]> r = new ArrayList<>();
        r.add(row("Limite de FPS (FPS cap)", cap + (cap > 60 ? " (volte a 60 se aparecerem engasgos)" : " — estável é melhor que alto")));
        r.add(row("Taxa da tela (Configurações do Android)", Math.min(cap, Math.max(60, d.maxHz)) + " Hz"));
        r.add(row("Qualidade baixa (Low Quality)", t == DeviceState.HIGH ? "Desligado" : "Ligado"));
        r.add(row("Antialiasing", t == DeviceState.HIGH ? "Opcional" : "Desligado"));
        r.add(row("Offset / atraso de áudio", OptimizerStore.hasRhythmOffset(c)
                ? "Comece com " + OptimizerStore.rhythmOffset(c) + " ms (se piorar, use o sinal oposto)"
                : "Use a calibração de sincronia"));
        r.add(row("Saída de áudio", d.btAudio ? "Bluetooth: recalibre ou use fio" : "Fone com fio ou alto-falante"));
        return r;
    }

    @Override
    protected void gameTips(Context c, DeviceState d, OptimizerStore.AppStorage st, List<Analyzer.Finding> out) {
        out.add(new Analyzer.Finding(Analyzer.INFO, "Quadros estáveis valem mais que FPS alto",
                "Em jogo de ritmo, oscilar entre 120 e 70 atrapalha mais do que ficar travado em 60. Use o limite recomendado."));
        out.add(new Analyzer.Finding(Analyzer.INFO, "Silencie o aparelho durante as músicas",
                "O perfil SOLAR RHYTHM liga o Não Perturbe (só prioridade) ao abrir o jogo."));
        out.add(new Analyzer.Finding(Analyzer.INFO, "Feche apps que tocam som",
                "Música, vídeo ou chamadas em segundo plano disputam o áudio e podem causar estalos."));
        out.add(new Analyzer.Finding(Analyzer.INFO, "Mods pesados: qualidade baixa",
                "Mods com muitos efeitos, vídeos ou personagens grandes pesam mais que as semanas originais."));
        out.add(new Analyzer.Finding(Analyzer.INFO, "Recalibre ao trocar de fone",
                "Cada saída de áudio tem um atraso diferente; refaça a calibração quando mudar de fone."));
    }
}
