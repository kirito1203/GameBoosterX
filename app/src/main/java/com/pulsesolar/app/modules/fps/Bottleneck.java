package com.pulsesolar.app.modules.fps;

import android.content.Intent;
import android.os.PowerManager;
import android.provider.Settings;

import com.pulsesolar.app.core.Analyzer;
import com.pulsesolar.app.core.Format;
import com.pulsesolar.app.core.Nav;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Diagnóstico de gargalos a partir de leituras reais. As conclusões são
 * indicadores (com a regra usada descrita no texto), não medições do jogo em si.
 */
public final class Bottleneck {

    private Bottleneck() {
    }

    private static String pct(float ratio) {
        return Math.round(ratio * 100) + "%";
    }

    /** Diagnóstico do momento atual. */
    public static List<Analyzer.Finding> now(Session.Point p, FrameMonitor.Stats live) {
        List<Analyzer.Finding> out = new ArrayList<>();

        // Memória
        float ram = p.ramAvailPct();
        if (p.lowMem || ram < 10f) {
            out.add(new Analyzer.Finding(Analyzer.CRIT, "Gargalo de memória",
                    "RAM livre em " + Format.one(ram) + "%" + (p.lowMem ? " e o Android sinalizou memória baixa" : "")
                            + ". Jogos recarregam texturas e podem fechar sozinhos.")
                    .action("Ver apps instalados", new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS), Nav.settings()));
        } else if (ram < 20f) {
            out.add(new Analyzer.Finding(Analyzer.WARN, "Memória apertada",
                    "RAM livre em " + Format.one(ram) + "%. Feche apps na tela de recentes antes de jogar."));
        } else {
            out.add(new Analyzer.Finding(Analyzer.OK, "Memória sem gargalo", "RAM livre em " + Format.one(ram) + "%."));
        }

        // Temperatura / limitação
        if (p.thermal >= PowerManager.THERMAL_STATUS_SEVERE) {
            out.add(new Analyzer.Finding(Analyzer.CRIT, "Limitação térmica ativa",
                    "O Android informa estado térmico severo: ele está reduzindo o desempenho para esfriar."));
        } else if (p.thermal >= PowerManager.THERMAL_STATUS_MODERATE) {
            out.add(new Analyzer.Finding(Analyzer.WARN, "Aquecimento moderado",
                    "O Android informa estado térmico moderado; a limitação pode começar."));
        }
        if (!Float.isNaN(p.headroom) && p.headroom >= 0.9f) {
            out.add(new Analyzer.Finding(Analyzer.WARN, "Perto do limite térmico",
                    String.format(Locale.getDefault(), "Previsão térmica do Android: %.2f (1,00 = início da limitação em ~10 s).", p.headroom)));
        }

        // CPU
        float cap = p.capRatio();
        if (cap > 0 && cap < 0.85f) {
            out.add(new Analyzer.Finding(Analyzer.WARN, "Teto da CPU reduzido",
                    "Os núcleos mais rápidos estão limitados a " + pct(cap) + " da frequência máxima ("
                            + Format.ghz(p.bigCap) + " de " + Format.ghz(p.bigHw) + "). Costuma ser limitação térmica ou de energia."));
        } else if (cap > 0) {
            out.add(new Analyzer.Finding(Analyzer.OK, "CPU sem limitação de teto",
                    "Núcleos rápidos podem chegar a " + Format.ghz(p.bigCap) + " (" + pct(cap) + " do máximo)."));
        } else if (p.bigHw <= 0) {
            out.add(new Analyzer.Finding(Analyzer.INFO, "Frequências da CPU indisponíveis",
                    "O sistema deste aparelho bloqueia a leitura do cpufreq; o diagnóstico de CPU fica limitado."));
        }

        if (p.powerSave) {
            out.add(new Analyzer.Finding(Analyzer.CRIT, "Economia de bateria ligada",
                    "Esse modo limita o processador e é um gargalo direto em jogos.")
                    .action("Abrir economia de bateria", new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS), Nav.settings()));
        }

        // Pulso do sistema
        if (live != null && live.frames > 0) {
            float st = live.stability();
            if (st < 85f || live.stalls > 0) {
                out.add(new Analyzer.Finding(Analyzer.WARN, "Sistema com engasgos agora",
                        String.format(Locale.getDefault(), "Pulso do sistema: %.0f%% dos quadros no tempo, %d travada(s) > 100 ms no último segundo. Algo está sobrecarregando o aparelho.",
                                st, live.stalls)));
            } else {
                out.add(new Analyzer.Finding(Analyzer.OK, "Pulso do sistema estável",
                        String.format(Locale.getDefault(), "%.0f%% dos quadros no tempo a ~%.0f Hz.", st, live.vsyncHz)));
            }
        }

        if (!Float.isNaN(p.storageFreePct) && p.storageFreePct < 10f) {
            out.add(new Analyzer.Finding(Analyzer.WARN, "Armazenamento quase cheio",
                    "Só " + Format.one(p.storageFreePct) + "% livre. Carregamentos e gravação de cache ficam mais lentos."));
        }

        Collections.sort(out, (a, b) -> Integer.compare(b.severity, a.severity));
        return out;
    }

    /** Diagnóstico de uma sessão gravada. */
    public static List<Analyzer.Finding> session(Session s) {
        List<Analyzer.Finding> out = new ArrayList<>();
        List<Session.Point> list = s.samples;
        if (list.isEmpty()) {
            out.add(new Analyzer.Finding(Analyzer.INFO, "Sessão curta demais",
                    "Não houve amostras suficientes (a primeira leitura acontece após 2 s)."));
            return out;
        }

        int n = list.size();
        int thermalMod = 0, thermalMax = -1, lowMem = 0, saturated = 0, freqKnown = 0;
        float minCap = 2f, maxHead = Float.NaN, minRam = 101f, maxTemp = Float.NaN;
        float stabSum = 0;
        int stabN = 0;
        for (Session.Point p : list) {
            if (p.thermal > thermalMax) thermalMax = p.thermal;
            if (p.thermal >= PowerManager.THERMAL_STATUS_MODERATE) thermalMod++;
            if (p.lowMem) lowMem++;
            float r = p.ramAvailPct();
            if (!Float.isNaN(r)) minRam = Math.min(minRam, r);
            if (!Float.isNaN(p.tempC)) maxTemp = Float.isNaN(maxTemp) ? p.tempC : Math.max(maxTemp, p.tempC);
            if (!Float.isNaN(p.headroom)) maxHead = Float.isNaN(maxHead) ? p.headroom : Math.max(maxHead, p.headroom);
            float cap = p.capRatio();
            if (cap > 0) {
                freqKnown++;
                minCap = Math.min(minCap, cap);
                if (p.loadRatio() >= 0.9f && p.stability >= 0 && p.stability < 90f) saturated++;
            }
            if (p.stability >= 0) {
                stabSum += p.stability;
                stabN++;
            }
        }

        // Térmico
        if (thermalMax >= PowerManager.THERMAL_STATUS_SEVERE) {
            out.add(new Analyzer.Finding(Analyzer.CRIT, "Limitação térmica durante o jogo",
                    "O estado térmico chegou a severo. " + Math.round(thermalMod * 100f / n)
                            + "% da sessão em alerta térmico. Jogue sem capa, longe do carregador e em local ventilado."));
        } else if (thermalMod > 0) {
            out.add(new Analyzer.Finding(Analyzer.WARN, "Aquecimento durante o jogo",
                    Math.round(thermalMod * 100f / n) + "% da sessão com estado térmico moderado ou pior."));
        }
        if (!Float.isNaN(maxHead) && maxHead >= 1f) {
            out.add(new Analyzer.Finding(Analyzer.WARN, "Atingiu o limite térmico previsto",
                    String.format(Locale.getDefault(), "Previsão térmica máxima: %.2f (≥ 1,00 indica limitação).", maxHead)));
        }
        if (!Float.isNaN(maxTemp) && maxTemp >= s.tempLimit) {
            out.add(new Analyzer.Finding(Analyzer.WARN, "Passou do seu limite de temperatura",
                    "Bateria chegou a " + Format.one(maxTemp) + " °C (limite: " + s.tempLimit + " °C)."));
        }

        // CPU
        if (freqKnown == 0) {
            out.add(new Analyzer.Finding(Analyzer.INFO, "Frequências da CPU indisponíveis",
                    "O aparelho bloqueia a leitura do cpufreq; não é possível medir limitação de CPU."));
        } else {
            if (minCap < 0.85f) {
                out.add(new Analyzer.Finding(Analyzer.WARN, "CPU limitada durante a sessão",
                        "O teto dos núcleos rápidos caiu para " + pct(minCap) + " do máximo em algum momento (limitação térmica ou de energia)."));
            }
            if (saturated * 100 / n >= 20) {
                out.add(new Analyzer.Finding(Analyzer.WARN, "Possível gargalo de CPU",
                        "Em " + (saturated * 100 / n) + "% das amostras os núcleos rápidos estavam ≥ 90% da frequência máxima ao mesmo tempo em que o pulso do sistema caiu. Indício de processador no limite (regra estimada, não medição do jogo)."));
            }
        }

        // Memória
        if (lowMem > 0 || minRam < 10f) {
            out.add(new Analyzer.Finding(Analyzer.CRIT, "Gargalo de memória",
                    "RAM livre mínima: " + Format.one(minRam) + "%" + (lowMem > 0 ? ", com " + lowMem + " leitura(s) de memória baixa" : "")
                            + ". Feche apps antes de jogar."));
        } else if (minRam < 20f) {
            out.add(new Analyzer.Finding(Analyzer.WARN, "Memória apertada", "RAM livre mínima: " + Format.one(minRam) + "%."));
        }

        // Energia
        if (s.before != null && s.before.powerSave) {
            out.add(new Analyzer.Finding(Analyzer.CRIT, "Começou com economia de bateria ligada",
                    "Esse modo limita o processador durante toda a sessão."));
        }

        // Pulso
        if (stabN > 0) {
            float avg = stabSum / stabN;
            if (avg < 90f || s.stalls > 5) {
                out.add(new Analyzer.Finding(Analyzer.WARN, "Sistema com engasgos",
                        String.format(Locale.getDefault(), "Pulso do sistema: %.0f%% dos quadros no tempo em média e %d travada(s) > 100 ms.", avg, s.stalls)));
            }
        }

        if (out.isEmpty()) {
            out.add(new Analyzer.Finding(Analyzer.OK, "Nenhum gargalo detectado",
                    "Temperatura, memória, CPU (quando legível) e pulso do sistema ficaram dentro do normal."));
        }
        Collections.sort(out, (a, b) -> Integer.compare(b.severity, a.severity));
        return out;
    }
}
