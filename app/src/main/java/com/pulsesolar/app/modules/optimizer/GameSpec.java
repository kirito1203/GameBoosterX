package com.pulsesolar.app.modules.optimizer;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;

import com.pulsesolar.app.core.Analyzer;
import com.pulsesolar.app.core.Format;
import com.pulsesolar.app.core.Nav;
import com.pulsesolar.app.modules.fps.Bottleneck;
import com.pulsesolar.app.modules.fps.GameProfiles;
import com.pulsesolar.app.modules.fps.Session;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Definição de um perfil do Solar Game Optimizer. As recomendações são calculadas a
 * partir do estado real do aparelho; o Pulse Solar nunca altera arquivos ou configurações
 * internas dos jogos — o usuário aplica as recomendações no menu do jogo.
 */
public abstract class GameSpec {

    public static final String ROBLOX = "roblox";
    public static final String RHYTHM = "rhythm";
    public static final String CRAFT = "craft";

    public abstract String id();

    /** Nome do perfil (ex.: SOLAR ROBLOX). */
    public abstract String title();

    /** Nome do jogo. */
    public abstract String gameName();

    public abstract String tagline();

    /** Pacotes conhecidos do jogo (para detectar automaticamente). */
    public abstract String[] knownPackages();

    /** Palavras procuradas no nome dos apps instalados quando o pacote não é conhecido. */
    public abstract String[] labelKeywords();

    /** Onde ficam as configurações dentro do jogo. */
    public abstract String graphicsWhere();

    /** Diagnóstico específico do jogo (além do diagnóstico geral). */
    protected abstract void gameDiagnose(Context c, DeviceState d, List<Analyzer.Finding> out);

    /** Linhas {configuração, valor recomendado}. */
    public abstract List<String[]> graphics(Context c, DeviceState d);

    /** Dicas fixas para reduzir travamentos neste jogo. */
    protected abstract void gameTips(Context c, DeviceState d, OptimizerStore.AppStorage st, List<Analyzer.Finding> out);

    /** Perfil seguro padrão aplicado ao jogar pelo Optimizer. */
    public GameProfiles.Profile defaultProfile() {
        GameProfiles.Profile p = new GameProfiles.Profile();
        p.dnd = true;
        p.bright = false;
        p.timeout = true;
        p.monitor = true;
        p.tempLimit = 42;
        return p;
    }

    // ------------------------------------------------------------ Comum

    public static List<GameSpec> all() {
        return Arrays.asList(new RobloxSpec(), new RhythmSpec(), new CraftSpec());
    }

    public static GameSpec get(String id) {
        for (GameSpec s : all()) if (s.id().equals(id)) return s;
        return new RobloxSpec();
    }

    /** Pacote escolhido pelo usuário, um pacote conhecido instalado, ou um app cujo nome combine. */
    public String detectPackage(Context c) {
        String chosen = OptimizerStore.chosenPackage(c, id());
        if (OptimizerStore.isInstalled(c, chosen)) return chosen;
        for (String p : knownPackages()) if (OptimizerStore.isInstalled(c, p)) return p;
        PackageManager pm = c.getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(launcher, 0)) {
            String label = ri.loadLabel(pm).toString().toLowerCase(Locale.ROOT);
            for (String k : labelKeywords()) {
                if (label.contains(k)) return ri.activityInfo.packageName;
            }
        }
        return null;
    }

    public List<Analyzer.Finding> diagnose(Context c, DeviceState d) {
        List<Analyzer.Finding> out = new ArrayList<>();
        out.add(new Analyzer.Finding(Analyzer.INFO,
                "Classe de desempenho estimada: " + DeviceState.tierName(d.tier),
                d.tierReason + (d.limited
                        ? " Agora as recomendações usam um nível abaixo (" + DeviceState.tierName(d.effectiveTier())
                        + ") porque o aparelho está quente ou em economia de bateria."
                        : "")));
        gameDiagnose(c, d, out);
        for (Analyzer.Finding f : Bottleneck.now(d.point, null)) {
            if (f.severity != Analyzer.OK) out.add(f);
        }
        Collections.sort(out, (a, b) -> Integer.compare(b.severity, a.severity));
        return out;
    }

    public List<Analyzer.Finding> tips(Context c, DeviceState d, OptimizerStore.AppStorage st, Session last) {
        List<Analyzer.Finding> out = new ArrayList<>();
        if (last != null) {
            for (Analyzer.Finding f : Bottleneck.session(last)) {
                if (f.severity >= Analyzer.WARN) {
                    out.add(new Analyzer.Finding(f.severity, "Última sessão: " + f.title, f.detail));
                }
            }
        }
        if (d.point.powerSave) {
            out.add(new Analyzer.Finding(Analyzer.CRIT, "Desligue a economia de bateria antes de jogar",
                    "Ela limita o processador durante todo o jogo.")
                    .action("Abrir economia de bateria", new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS), Nav.settings()));
        }
        if (d.hot) {
            out.add(new Analyzer.Finding(Analyzer.WARN, "Espere o aparelho esfriar",
                    "Começar quente (" + (Float.isNaN(d.point.tempC) ? "estado térmico elevado" : Format.one(d.point.tempC) + " °C")
                            + ") faz o sistema limitar o desempenho mais cedo. Tire a capa e não jogue carregando."));
        }
        float ramPct = d.point.ramAvailPct();
        if (!Float.isNaN(ramPct) && ramPct < 25f) {
            out.add(new Analyzer.Finding(Analyzer.WARN, "Feche apps antes de abrir o jogo",
                    "Só " + Format.one(ramPct) + "% da RAM está livre. Feche apps na tela de recentes."));
        }
        gameTips(c, d, st, out);
        Collections.sort(out, (a, b) -> Integer.compare(b.severity, a.severity));
        return out;
    }

    protected static String[] row(String k, String v) {
        return new String[]{k, v};
    }
}
