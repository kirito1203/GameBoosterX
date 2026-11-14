package com.gameboosterx.rog.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Perfis pré-definidos com recomendações específicas por jogo.
 * As recomendações se referem a opções que existem DENTRO de cada jogo
 * ou no sistema — o app não altera arquivos internos dos jogos.
 */
public final class GameCatalog {

    public static final class Entry {
        public final String id;
        public final String title;
        public final String[] packages;
        public final String[] nameKeywords;
        public final int suggestedBrightness; // % (0-100)
        public final boolean suggestDnd;
        public final int suggestedFpsCap;     // limite sugerido dentro do jogo
        public final String stability;
        public final String temperature;
        public final String graphics;
        public final String battery;

        Entry(String id, String title, String[] packages, String[] nameKeywords,
              int suggestedBrightness, boolean suggestDnd, int suggestedFpsCap,
              String stability, String temperature, String graphics, String battery) {
            this.id = id;
            this.title = title;
            this.packages = packages;
            this.nameKeywords = nameKeywords;
            this.suggestedBrightness = suggestedBrightness;
            this.suggestDnd = suggestDnd;
            this.suggestedFpsCap = suggestedFpsCap;
            this.stability = stability;
            this.temperature = temperature;
            this.graphics = graphics;
            this.battery = battery;
        }
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();

    static {
        ENTRIES.add(new Entry("roblox", "Roblox",
                new String[]{"com.roblox.client"},
                new String[]{"roblox"},
                70, true, 60,
                "Use o Modo Gráfico Manual (Menu ▸ Configurações) e mantenha a qualidade entre 1 e 4. "
                        + "Servidores lotados e mapas grandes derrubam o FPS mais do que o aparelho; "
                        + "prefira servidores com menos jogadores.",
                "Partidas longas aquecem o aparelho. Faça pausas a cada 40–60 min se a bateria passar de 41 °C. "
                        + "Evite jogar carregando.",
                "Qualidade gráfica 1–4, sem sombras. Experiências muito pesadas podem exigir qualidade 1.",
                "Brilho ~70%, Wi-Fi em vez de dados móveis e Não Perturbe ativo reduzem o consumo."));

        ENTRIES.add(new Entry("minecraft", "Minecraft",
                new String[]{"com.mojang.minecraftpe"},
                new String[]{"minecraft"},
                70, true, 60,
                "Distância de renderização entre 6 e 10 chunks. Desative \"Gráficos sofisticados\" e "
                        + "\"Céu bonito\". Mundos com muitos add-ons e redstone pesam mais.",
                "Mantenha o limite de FPS em 60 para reduzir o aquecimento em sessões longas.",
                "Partículas reduzidas, nuvens desligadas, \"Folhas bonitas\" desligadas. Texturas acima de 16x aumentam o uso de RAM.",
                "Diminuir a distância de renderização é o ajuste que mais economiza bateria."));

        ENTRIES.add(new Entry("fnf", "Friday Night Funkin'",
                new String[]{"me.funkin.fnf", "com.ninjamuffin99.funkin", "com.funkin.fnf"},
                new String[]{"funkin", "fnf"},
                75, true, 60,
                "Jogo de ritmo depende de toque estável: feche apps em segundo plano e use o Não Perturbe "
                        + "para evitar notificações durante as músicas.",
                "Consome pouco processamento; aquecimento costuma vir do brilho alto e do carregador.",
                "Se houver opção de \"Low Quality\"/antialiasing, desative-a em mods pesados. Ajuste o offset de áudio/entrada se as notas parecerem atrasadas.",
                "Use fones com fio: Bluetooth adiciona atraso de áudio e consome bateria."));

        ENTRIES.add(new Entry("genshin", "Genshin Impact",
                new String[]{"com.miHoYo.GenshinImpact"},
                new String[]{"genshin"},
                65, true, 30,
                "É o jogo mais pesado da lista para este aparelho. Use a predefinição \"Mais baixa\" ou \"Baixa\" "
                        + "e FPS em 30 para uma experiência estável. Áreas abertas e cidades exigem mais.",
                "Aquece rápido: monitore a temperatura e pause se o estado térmico chegar a \"Moderado\". "
                        + "Remova a capa em sessões longas.",
                "Resolução baixa, sombras e efeitos desativados, oclusão e reflexos desligados, multidão baixa.",
                "Consumo alto: espere uma queda rápida da bateria. 30 FPS e brilho ~65% fazem diferença real."));

        ENTRIES.add(new Entry("codm", "Call of Duty Mobile",
                new String[]{"com.activision.callofduty.shooter", "com.garena.game.codm"},
                new String[]{"call of duty", "codm"},
                70, true, 60,
                "Qualidade gráfica \"Baixa\" com taxa de quadros \"Alta\" (ou a maior disponível) prioriza fluidez. "
                        + "Ping estável importa tanto quanto FPS: prefira Wi-Fi 5 GHz.",
                "Partidas em sequência aquecem; observe o estado térmico entre partidas.",
                "Desative sombras em tempo real, profundidade de campo e ragdoll. Baixe somente os pacotes de recursos necessários.",
                "Modo Battle Royale consome mais que Multijogador. Brilho ~70% e Não Perturbe ajudam."));
    }

    private GameCatalog() {}

    public static List<Entry> all() { return ENTRIES; }

    public static Entry find(String pkg, String label) {
        String l = label == null ? "" : label.toLowerCase(Locale.ROOT);
        for (Entry e : ENTRIES) {
            for (String p : e.packages) if (p.equalsIgnoreCase(pkg)) return e;
        }
        for (Entry e : ENTRIES) {
            for (String k : e.nameKeywords) if (l.contains(k)) return e;
        }
        return null;
    }

    public static Entry byId(String id) {
        for (Entry e : ENTRIES) if (e.id.equals(id)) return e;
        return null;
    }

    /** Recomendações genéricas para jogos sem perfil dedicado. */
    public static final String GENERIC_STABILITY =
            "Limite o FPS dentro do jogo a 60 (ou 30 em jogos pesados). Um FPS limitado e estável é mais fluido do que um FPS alto que oscila.";
    public static final String GENERIC_TEMPERATURE =
            "Evite jogar carregando, remova a capa em sessões longas e pause se a bateria passar do limite configurado.";
    public static final String GENERIC_GRAPHICS =
            "Reduza sombras, efeitos e resolução antes de reduzir a distância de visão.";
    public static final String GENERIC_BATTERY =
            "Brilho moderado, Wi-Fi em vez de dados móveis e Não Perturbe ativo.";
}
