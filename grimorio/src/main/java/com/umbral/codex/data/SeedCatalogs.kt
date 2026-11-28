package com.umbral.codex.data

import com.umbral.codex.core.Ids
import com.umbral.codex.core.ResourceColors
import com.umbral.codex.core.SystemPreset
import com.umbral.codex.model.Ability
import com.umbral.codex.model.AbilityCategories
import com.umbral.codex.model.Attribute
import com.umbral.codex.model.Catalog
import com.umbral.codex.model.CatalogEntry
import com.umbral.codex.model.Character
import com.umbral.codex.model.CharacterTemplate
import com.umbral.codex.model.Item
import com.umbral.codex.model.LoreSection
import com.umbral.codex.model.Rarity
import com.umbral.codex.model.Resource
import com.umbral.codex.model.SheetSection
import com.umbral.codex.model.Spell
import com.umbral.codex.model.Story
import com.umbral.codex.model.Trait

/**
 * Catálogos que acompanham o app. São textos originais de ambientação genérica, criados para
 * servir de ponto de partida — o usuário pode editar, apagar ou substituir tudo.
 * Ids fixos permitem "restaurar o catálogo original" sem duplicar.
 */
object SeedCatalogs {

    const val SUPERNATURAL = "cat-sobrenatural"
    const val PARANORMAL = "cat-paranormal"
    const val FANTASY = "cat-fantasia"
    const val DARK_FANTASY = "cat-dark-fantasy"
    const val MEDIEVAL = "cat-medieval"
    const val HORROR = "cat-horror"
    const val OCCULT = "cat-ocultismo"
    const val MEDIEVAL_FANTASY = "cat-fantasia-medieval"

    fun all(now: Long = 0L): List<Catalog> = listOf(
        darkFantasy(), supernatural(), paranormal(), fantasy(), medieval(), horror(), occult(), medievalFantasy(),
    ).mapIndexed { i, c -> c.copy(order = i, builtIn = true, updatedAt = now) }

    // ---------------- helpers ----------------

    private fun e(name: String, desc: String) = CatalogEntry(Ids.new(), name, desc)
    private fun lore(title: String, body: String) = LoreSection(Ids.new(), title, body)
    private fun trait(name: String, type: String, desc: String, effect: String = "", limits: String = "") =
        Trait(Ids.new(), name, type, desc, effect, limitations = limits)
    private fun ability(name: String, cat: String, desc: String, effect: String = "", cost: String = "", cd: String = "", dice: String = "", icon: String = "bolt") =
        Ability(Ids.new(), name, cat, icon, description = desc, effect = effect, cost = cost, cooldown = cd, dice = dice)
    private fun spell(
        name: String, school: String, level: Int, desc: String, mana: Int = 0, damage: String = "",
        cast: String = "1 ação", range: String = "", duration: String = "Instantânea", type: String = "Arcana", icon: String = "auto_fix",
        effect: String = "",
    ) = Spell(Ids.new(), name, icon, null, desc, type, level, school, mana, 0, cast, range, duration, damage, effect)
    private fun item(
        name: String, cat: String, desc: String, qty: Int = 1, weight: Double = 0.0, value: String = "",
        rarity: Rarity = Rarity.COMMON, dice: String = "", bonus: String = "", equipped: Boolean = false, icon: String = "inventory",
    ) = Item(Ids.new(), name, icon, null, desc, qty, weight, value, rarity, cat, bonus = bonus, dice = dice, equipped = equipped)

    private fun Character.setAttr(abbr: String, value: Int) =
        copy(attributes = attributes.map { if (it.abbr == abbr) it.copy(value = value) else it })

    private fun Character.withResource(name: String, cur: Int, max: Int, color: Long) =
        copy(resources = resources + Resource(Ids.new(), name, cur, max, 0, color))

    private fun template(name: String, desc: String, c: Character) = CharacterTemplate(Ids.new(), name, desc, c.copy(name = ""))

    // ---------------- Dark Fantasy ----------------

    private fun darkFantasy() = Catalog(
        id = DARK_FANTASY,
        name = "Dark Fantasy",
        tagline = "Reinos em ruína, deuses silenciosos e lâminas que cobram em sangue.",
        description = "Fantasia sombria onde a magia corrompe, a fé falhou e cada vitória deixa cicatrizes. " +
            "Heróis são sobreviventes moralmente ambíguos em um mundo que apodrece lentamente.",
        glyph = "dangerous",
        accent = 0xFF8E2B4AL,
        setting = "Há três séculos o Sol Partido rachou o céu, e desde então a luz chega fraca e cinzenta. " +
            "Os antigos impérios caíram sob a Praga Cinérea, e os reinos que restaram sobrevivem atrás de muralhas, " +
            "governados por senhores que negociam com coisas que não deveriam ouvir. Fora das cidades, a Névoa Pálida " +
            "devora estradas inteiras, e o que volta dela raramente é o que entrou.",
        characteristics = listOf(
            "Tom sombrio e consequências permanentes",
            "Magia poderosa, perigosa e corruptora",
            "Moralidade cinzenta: não há lados puros",
            "Recursos escassos e sobrevivência difícil",
            "Horror cósmico e divindades indiferentes",
        ),
        lore = listOf(
            lore("História do mundo", "Antes do Sol Partido, o Império de Vaelor unia o continente sob uma igreja solar. A ruptura do céu matou o imperador e libertou a Praga Cinérea, que transforma os mortos em cascas de cinza que ainda caminham."),
            lore("Reinos", "• Morvhal — cidade-fortaleza governada por um conselho de inquisidores.\n• Baronato de Kesh — terras de pântano onde barões pactuam com bruxas.\n• As Cinzas de Vaelor — ruínas da antiga capital, ainda cheias de relíquias e horrores."),
            lore("Criaturas", "Cinéreos (mortos-vivos de cinza), Lobos-de-névoa, Carniçais de catedral, Arautos Vazios — figuras altas e sem rosto que precedem catástrofes."),
            lore("Maldições", "A Marca Cinérea cresce na pele de quem usa magia proibida. A Maldição do Juramento Quebrado impede o portador de morrer até que repare sua promessa."),
            lore("Religiões", "A Igreja do Sol Ferido, que ainda reza a um deus moribundo; o Culto da Névoa, que adora o esquecimento; e os Velhos Ritos, pagãos e sangrentos."),
            lore("Magias proibidas", "Hemomancia, necromancia de cinza e a Invocação dos Vazios. Praticá-las acumula Corrupção."),
            lore("Facções", "Ordem da Lâmina Cinzenta (caçadores de cinéreos), Inquisição de Morvhal, Companhia dos Sem-Nome (mercenários), Círculo de Cinza (magos renegados)."),
            lore("Personagens importantes", "A Rainha Vidrada de Morvhal, que não envelhece desde o Sol Partido; Irmão Calder, inquisidor-mor; e a Bruxa de Kesh, cujo nome ninguém pronuncia."),
        ),
        races = listOf(
            e("Humano", "Numerosos e teimosos; adaptam-se a qualquer ruína."),
            e("Meio-cinéreo", "Nascido de mãe tocada pela Praga. Pele acinzentada, resistência à morte."),
            e("Elfo crepuscular", "Remanescentes de um povo antigo que perdeu sua luz; vivem pouco agora."),
            e("Anão de forja-negra", "Mineradores que cavaram fundo demais e trouxeram algo de volta."),
            e("Revenante", "Morto que se recusou a partir, preso por um juramento."),
        ),
        classes = listOf(
            e("Cavaleiro maldito", "Guerreiro de armadura pesada que paga seus poderes com a própria alma."),
            e("Caçador de cinzas", "Especialista em rastrear e destruir mortos-vivos."),
            e("Hemomante", "Conjurador que usa o próprio sangue como combustível."),
            e("Inquisidor", "Juiz armado da fé, com orações de fogo e interrogatório."),
            e("Ladrão de relíquias", "Furtivo e oportunista, invade ruínas em busca de artefatos."),
            e("Bruxa do pântano", "Herbalista e amaldiçoadora, conhece os velhos pactos."),
        ),
        powers = listOf(
            e("Lâmina sangrenta", "Sacrifica vida para causar dano extra."),
            e("Juramento sombrio", "Promessa que concede força enquanto for mantida."),
            e("Olhos de cinza", "Enxerga através da Névoa Pálida."),
            e("Fúria do condenado", "Quanto menos vida, mais dano."),
            e("Sopro do vazio", "Magia proibida que apaga memórias."),
        ),
        templates = listOf(
            template(
                "Cavaleiro maldito", "Guerreiro de linha de frente com Corrupção e Juramento.",
                SystemPreset.CLASSIC.build(catalogId = DARK_FANTASY)
                    .setAttr("FOR", 16).setAttr("CON", 15).setAttr("SAB", 12).setAttr("DES", 10).setAttr("INT", 9).setAttr("CAR", 11)
                    .copy(
                        race = "Humano", charClass = "Cavaleiro maldito", subclass = "Juramento de Cinza",
                        alignment = "Neutro", armorClass = 17, speed = "7,5 m",
                        resources = listOf(
                            Resource(Ids.new(), "Vida", 14, 14, 0, ResourceColors.LIFE),
                            Resource(Ids.new(), "Vigor", 12, 12, 0, ResourceColors.STAMINA),
                            Resource(Ids.new(), "Corrupção", 0, 10, 0, ResourceColors.CORRUPTION),
                        ),
                        abilities = listOf(
                            ability("Lâmina sangrenta", AbilityCategories.COMBAT, "Gaste 1d6 de Vida para somar 2d6 de dano necrótico ao próximo ataque.", cost = "1d6 Vida", dice = "2d6", icon = "bloodtype"),
                            ability("Juramento de cinza", AbilityCategories.PASSIVE, "Enquanto cumprir seu juramento, +1 em testes de resistência.", icon = "shield"),
                        ),
                        traits = listOf(trait("Marca Cinérea", "Maldição", "Sinais de cinza que crescem com a Corrupção.", limits = "Ao chegar a 10 de Corrupção, o personagem se perde.")),
                        items = listOf(
                            item("Espada longa enegrecida", "Arma", "Lâmina pesada, marcada por runas apagadas.", weight = 1.5, value = "15 PO", dice = "1d8+3", equipped = true, icon = "hardware"),
                            item("Cota de malha", "Armadura", "Armadura pesada de anéis.", weight = 25.0, value = "75 PO", bonus = "CA 16", equipped = true, icon = "shield"),
                            item("Frasco de óleo santo", "Poção", "Queima mortos-vivos.", qty = 2, weight = 0.5, value = "5 PO", dice = "2d6", icon = "science"),
                        ),
                        abilityCategories = emptyList(),
                    )
            ),
        ),
    )

    // ---------------- Sobrenatural ----------------

    private fun supernatural() = Catalog(
        id = SUPERNATURAL,
        name = "Sobrenatural",
        tagline = "Predadores elegantes, caçadores cansados e pactos firmados à meia-noite.",
        description = "O mundo moderno esconde vampiros, lobisomens, demônios e anjos caídos. " +
            "Os personagens vivem entre a humanidade e aquilo que a caça.",
        glyph = "nights_stay",
        accent = 0xFF7A1F3DL,
        setting = "As criaturas sempre estiveram aqui. O Véu — um acordo antigo entre as Cortes da Noite e as " +
            "ordens de caçadores — mantém os humanos comuns na ignorância. Mas o Véu está se rompendo: " +
            "nascimentos estranhos, desaparecimentos em massa e uma guerra fria entre clãs ameaçam a ordem.",
        characteristics = listOf(
            "Cenário contemporâneo ou histórico com segredos ocultos",
            "Personagens podem ser criaturas sobrenaturais",
            "Conflito entre humanidade e natureza monstruosa",
            "Política entre clãs, cortes e ordens",
            "Recursos como Humanidade, Sangue e Essência",
        ),
        lore = listOf(
            lore("Origem das criaturas", "Diz-se que toda criatura descende de uma das Sete Fomes que caíram do céu antes da escrita. Cada linhagem herdou uma fome: sangue, carne, almas, segredos, medo, luz e tempo."),
            lore("Vampiros", "Organizados em Casas. Alimentam-se de sangue, temem o sol e envelhecem só quando perdem toda a Humanidade."),
            lore("Lobisomens", "Clãs ligados à lua e a territórios. A transformação é dolorosa e cada uma rouba um pouco da mente humana."),
            lore("Demônios", "Entidades presas por contratos. Precisam de permissão para agir e cobram juros em alma."),
            lore("Anjos", "Raros e assustadores. Os poucos que caminham entre humanos parecem ter esquecido para quem trabalham."),
            lore("Entidades", "Coisas sem nome que vivem entre as frestas do Véu: o Homem Sem Sombra, as Crianças do Poço."),
            lore("Caçadores", "Humanos treinados que usam prata, fé e conhecimento. Alguns herdam a vocação por sangue."),
            lore("Organizações sobrenaturais", "A Corte Carmesim (vampiros), o Pacto da Lua Rasgada (lobisomens), a Ordem de São Lázaro (caçadores) e o Escritório 13 (agência governamental secreta)."),
        ),
        races = listOf(
            e("Humano", "Mortal comum, frágil, mas imprevisível."),
            e("Vampiro", "Morto-vivo imortal que se alimenta de sangue."),
            e("Lobisomem", "Metamorfo ligado à lua."),
            e("Meio-demônio", "Filho de um pacto. Força infernal e uma voz que sussurra."),
            e("Nefilim", "Descendente de anjos, luminoso e perseguido."),
            e("Bruxo de sangue", "Humano com herança mágica antiga."),
        ),
        classes = listOf(
            e("Caçador", "Rastreador e matador de criaturas."),
            e("Aristocrata noturno", "Manipulador social das Cortes."),
            e("Fera", "Combatente instintivo, mestre da transformação."),
            e("Médium", "Comunica-se com mortos e entidades."),
            e("Exorcista", "Expulsa possessões com fé e ritual."),
        ),
        powers = listOf(
            e("Dominação", "Impõe a vontade através do olhar."),
            e("Forma de névoa", "Dissolve o corpo em névoa por instantes."),
            e("Fúria lunar", "Transformação parcial com garras e força."),
            e("Visão do Véu", "Enxerga criaturas disfarçadas."),
            e("Bênção da prata", "Armas causam dano extra a criaturas."),
        ),
        templates = listOf(
            template(
                "Vampiro aristocrata", "Predador social da Corte Carmesim, com Sangue e Humanidade.",
                SystemPreset.SUPERNATURAL.build(catalogId = SUPERNATURAL)
                    .setAttr("CAR", 17).setAttr("DES", 14).setAttr("INT", 13).setAttr("FOR", 12).setAttr("SAB", 12).setAttr("CON", 13)
                    .withResource("Sangue", 8, 10, ResourceColors.BLOOD)
                    .copy(
                        race = "Vampiro", charClass = "Aristocrata noturno", faction = "Corte Carmesim", age = "247 (aparenta 30)",
                        traits = listOf(
                            trait("Visão noturna", "Sobrenatural", "Enxerga perfeitamente no escuro."),
                            trait("Regeneração", "Sobrenatural", "Gasta 1 de Sangue para recuperar 1d8 de Vida.", limits = "Não funciona contra fogo ou luz solar."),
                            trait("Imortalidade", "Sobrenatural", "Não envelhece nem morre de causas naturais."),
                            trait("Fraqueza solar", "Maldição", "Sofre 2d6 de dano por rodada sob luz do sol."),
                        ),
                        abilities = listOf(
                            ability("Dominação", AbilityCategories.SUPERNATURAL, "Olhar que obriga um alvo a obedecer uma ordem simples.", cost = "1 Sangue", cd = "1 cena", icon = "visibility"),
                            ability("Forma de névoa", AbilityCategories.TRANSFORMATION, "Torna-se névoa por até 1 minuto.", cost = "2 Sangue", icon = "blur_on"),
                            ability("Mordida", AbilityCategories.COMBAT, "Ataque que drena sangue e recupera 1 de Sangue.", dice = "1d6+2", icon = "bloodtype"),
                        ),
                        items = listOf(
                            item("Anel de sinete da Casa", "Relíquia", "Prova de linhagem na Corte.", rarity = Rarity.RARE, icon = "diamond"),
                            item("Adaga ornamentada", "Arma", "Lâmina de prata enegrecida.", weight = 0.5, dice = "1d4+2", equipped = true, icon = "hardware"),
                        ),
                    )
            ),
            template(
                "Caçador da Ordem", "Humano treinado pela Ordem de São Lázaro.",
                SystemPreset.SUPERNATURAL.build(catalogId = SUPERNATURAL)
                    .setAttr("DES", 15).setAttr("SAB", 14).setAttr("CON", 14).setAttr("FOR", 13).setAttr("HUM", 10)
                    .copy(
                        race = "Humano", charClass = "Caçador", faction = "Ordem de São Lázaro",
                        abilities = listOf(ability("Bênção da prata", AbilityCategories.COMBAT, "Armas de prata causam +1d6 contra criaturas.", dice = "1d6", icon = "flare")),
                        items = listOf(
                            item("Besta de repetição", "Arma", "Dispara virotes de prata.", weight = 3.0, dice = "1d8", equipped = true, icon = "hardware"),
                            item("Virotes de prata", "Equipamento", "Munição consagrada.", qty = 20, weight = 0.05, icon = "inventory"),
                            item("Água benta", "Poção", "Queima criaturas profanas.", qty = 3, weight = 0.3, dice = "2d6", icon = "water_drop"),
                        ),
                    )
            ),
        ),
    )

    // ---------------- Paranormal ----------------

    private fun paranormal() = Catalog(
        id = PARANORMAL,
        name = "Paranormal",
        tagline = "Gravadores ligados, lanternas falhando e um arquivo que não deveria existir.",
        description = "Investigação de fenômenos inexplicáveis no mundo atual: aparições, possessões, " +
            "anomalias e conspirações. Personagens são pessoas comuns diante do impossível.",
        glyph = "visibility",
        accent = 0xFF2F7A8CL,
        setting = "Desde o Incidente de Ponte Alta, em 1987, uma rede discreta de investigadores independentes " +
            "registra casos que a polícia arquiva sem resposta. A Rede não tem sede, apenas um fórum antigo, " +
            "um cofre de evidências e uma regra: ninguém investiga sozinho.",
        characteristics = listOf(
            "Investigação, pistas e dedução",
            "Personagens humanos e vulneráveis",
            "Sanidade como recurso central",
            "Ameaças misteriosas, muitas vezes invencíveis em combate",
            "Tecnologia moderna contra o desconhecido",
        ),
        lore = listOf(
            lore("Fenômenos", "Aparições residuais, poltergeists, zonas de tempo perdido, ecos de vozes em gravações e objetos amaldiçoados."),
            lore("Organizações", "A Rede (investigadores), Fundação Aurora (empresa que compra artefatos), e a Seita do Sétimo Sinal."),
            lore("Casos célebres", "O Hospital Santa Clara, a Família Moreau, o Sinal de Rádio 77.3."),
            lore("Ferramentas", "Gravadores EVP, câmeras térmicas, sal, ferro, arquivos de jornal e muita paciência."),
        ),
        races = listOf(e("Humano", "Pessoa comum."), e("Sensitivo", "Humano com percepção extrassensorial."), e("Marcado", "Sobrevivente de um evento que deixou algo nele.")),
        classes = listOf(
            e("Investigador", "Detetive, jornalista ou pesquisador."),
            e("Médium", "Ouve os mortos — às vezes demais."),
            e("Técnico", "Especialista em equipamentos de detecção."),
            e("Ocultista acadêmico", "Professor de folclore e simbologia."),
            e("Ex-policial", "Treinado, armado e cético até ver."),
        ),
        powers = listOf(
            e("Clarividência", "Visões fragmentadas de passado e futuro."),
            e("Psicometria", "Lê memórias de objetos tocados."),
            e("Ancoragem", "Resiste melhor a perdas de Sanidade."),
        ),
        templates = listOf(
            template(
                "Investigador paranormal", "Sistema d100: perícias em porcentagem e Sanidade.",
                SystemPreset.INVESTIGATION.build(catalogId = PARANORMAL).copy(
                    race = "Humano", charClass = "Jornalista investigativo", age = "34",
                    abilities = listOf(ability("Psicometria", AbilityCategories.SUPERNATURAL, "Ao tocar um objeto, tem uma visão do último evento forte ligado a ele.", cost = "1d4 Sanidade", icon = "psychology")),
                    items = listOf(
                        item("Gravador EVP", "Equipamento", "Capta vozes inaudíveis.", weight = 0.3, icon = "inventory"),
                        item("Lanterna tática", "Equipamento", "Bateria para 6 horas.", weight = 0.4, icon = "flare"),
                        item("Revólver .38", "Arma", "Seis tiros.", weight = 1.0, dice = "1d10", icon = "hardware"),
                    ),
                )
            ),
        ),
    )

    // ---------------- Fantasia ----------------

    private fun fantasy() = Catalog(
        id = FANTASY,
        name = "Fantasia",
        tagline = "Florestas encantadas, dragões antigos e heróis destinados a lendas.",
        description = "Fantasia heroica clássica: magia abundante, raças diversas e aventuras épicas.",
        glyph = "auto_awesome",
        accent = 0xFF4F7BD1L,
        setting = "O continente de Aerinth é atravessado por linhas de energia chamadas Veios Estelares. " +
            "Onde se cruzam, nascem cidades mágicas, florestas que pensam e montanhas que guardam dragões adormecidos.",
        characteristics = listOf("Magia comum e maravilhosa", "Grande variedade de raças", "Heróis com destino", "Exploração e descoberta", "Bem contra mal mais definido"),
        lore = listOf(
            lore("Reinos", "Lysandor (élfico), Karaz-Dun (anão), a República de Velmar (humana) e as Ilhas Errantes."),
            lore("Criaturas", "Dragões, grifos, ents, fadas, elementais e trolls de ponte."),
            lore("Deuses", "O Panteão das Doze Estrelas, cada uma regendo um aspecto do mundo."),
        ),
        races = listOf(
            e("Humano", "Versáteis e ambiciosos."), e("Elfo", "Longevos e ligados à magia."), e("Anão", "Robustos artesãos das montanhas."),
            e("Halfling", "Pequenos, sortudos e corajosos."), e("Draconato", "Descendentes de dragões."), e("Feérico", "Seres das cortes encantadas."),
        ),
        classes = listOf(
            e("Guerreiro", "Mestre de armas."), e("Mago", "Estudioso arcano."), e("Clérigo", "Canal divino."),
            e("Ladino", "Especialista furtivo."), e("Patrulheiro", "Caçador e explorador."), e("Bardo", "Magia pela música."),
        ),
        powers = listOf(e("Bola de fogo", "Explosão de chamas."), e("Cura divina", "Restaura vida."), e("Passo feérico", "Teletransporte curto.")),
        templates = listOf(
            template(
                "Patrulheira élfica", "Arqueira e rastreadora com magia da natureza.",
                SystemPreset.CLASSIC.build(catalogId = FANTASY).setAttr("DES", 17).setAttr("SAB", 14).setAttr("CON", 13).setAttr("INT", 11).setAttr("FOR", 10).setAttr("CAR", 10).copy(
                    race = "Elfo", charClass = "Patrulheiro", subclass = "Caçadora", armorClass = 15,
                    traits = listOf(trait("Visão no escuro", "Racial", "Enxerga até 18 m no escuro."), trait("Transe élfico", "Racial", "Descansa meditando por 4 horas.")),
                    spells = listOf(spell("Marca do caçador", "Adivinhação", 1, "Marca um alvo; seus ataques causam +1d6.", mana = 2, damage = "1d6", type = "Natureza", range = "27 m", duration = "1 hora")),
                    items = listOf(item("Arco longo", "Arma", "Arco de teixo élfico.", weight = 1.0, dice = "1d8+3", equipped = true, icon = "hardware"), item("Flechas", "Equipamento", "Aljava com flechas.", qty = 20, weight = 0.05)),
                )
            ),
        ),
    )

    // ---------------- Medieval ----------------

    private fun medieval() = Catalog(
        id = MEDIEVAL,
        name = "Medieval",
        tagline = "Casas nobres, juramentos de aço e guerras decididas na lama.",
        description = "Ambientação histórica ou de baixa fantasia, com política feudal, cavalaria e guerra. A magia é rara ou inexistente.",
        glyph = "castle",
        accent = 0xFF9C7A3CL,
        setting = "O Reino de Altamar está dividido após a morte do rei sem herdeiros. Cinco casas nobres " +
            "disputam a coroa, mercenários vendem lealdade e os camponeses pagam o preço de cada batalha.",
        characteristics = listOf("Política e intriga entre nobres", "Combate realista e perigoso", "Magia rara ou ausente", "Honra, juramentos e hierarquia", "Equipamento faz diferença"),
        lore = listOf(
            lore("Reinos", "Altamar, o Ducado de Serravento e as Marcas do Norte."),
            lore("Casas nobres", "Casa Valcourt (leões), Casa Morrow (corvos), Casa Delacroix (rosas), Casa Thorne (javalis), Casa Ashby (torres)."),
            lore("Cavaleiros", "Ordem do Escudo Branco (guardiões do trono vazio) e Cavaleiros Errantes sem senhor."),
            lore("Guerras", "A Guerra das Cinco Coroas começou há dois invernos e já queimou metade das vilas do sul."),
            lore("Armas", "Espadas longas, lanças de cavalaria, bestas, machados de guerra e maças."),
            lore("Armaduras", "Gambeson, cota de malha, brigantina e armadura de placas."),
            lore("Culturas", "Os nortistas valorizam a palavra dada; os sulistas, o ouro; os ilhéus, o mar."),
        ),
        races = listOf(e("Humano do sul", "Comerciantes e diplomatas."), e("Nortista", "Resistentes ao frio, guerreiros de clã."), e("Ilhéu", "Marinheiros e piratas.")),
        classes = listOf(
            e("Cavaleiro", "Nobre guerreiro montado."), e("Soldado", "Infantaria disciplinada."), e("Arqueiro", "Atirador de longo alcance."),
            e("Espião", "Agente das casas nobres."), e("Clérigo", "Sacerdote e curandeiro."), e("Mercenário", "Lâmina de aluguel."),
        ),
        powers = listOf(e("Carga montada", "Ataque devastador a cavalo."), e("Grito de guerra", "Inspira aliados."), e("Parada perfeita", "Defesa contra um ataque.")),
        templates = listOf(
            template(
                "Cavaleiro de casa nobre", "Sem magia: aba Magia oculta, Vigor e Moral.",
                SystemPreset.CLASSIC.build(catalogId = MEDIEVAL).setAttr("FOR", 16).setAttr("CON", 14).setAttr("CAR", 13).setAttr("DES", 12).copy(
                    race = "Humano do sul", charClass = "Cavaleiro", faction = "Casa Valcourt", title = "Sor", armorClass = 18,
                    resources = listOf(
                        Resource(Ids.new(), "Vida", 13, 13, 0, ResourceColors.LIFE),
                        Resource(Ids.new(), "Vigor", 12, 12, 0, ResourceColors.STAMINA),
                        Resource(Ids.new(), "Moral", 5, 5, 0, ResourceColors.ENERGY),
                    ),
                    hiddenSections = setOf(SheetSection.MAGIC.key),
                    abilities = listOf(ability("Carga montada", AbilityCategories.COMBAT, "Após mover 6 m a cavalo, ataque causa +1d8.", dice = "1d8", icon = "sports_martial_arts")),
                    items = listOf(
                        item("Espada longa", "Arma", "Aço de Altamar.", weight = 1.5, dice = "1d8+3", equipped = true, icon = "hardware"),
                        item("Armadura de placas", "Armadura", "Brasonada com o leão Valcourt.", weight = 30.0, bonus = "CA 18", equipped = true, icon = "shield"),
                        item("Escudo heráldico", "Escudo", "Leão dourado sobre vermelho.", weight = 3.0, bonus = "+2 CA", icon = "shield"),
                    ),
                )
            ),
        ),
    )

    // ---------------- Horror ----------------

    private fun horror() = Catalog(
        id = HORROR,
        name = "Horror",
        tagline = "A porta range. Algo respira do outro lado. Ninguém vai ouvir você gritar.",
        description = "Sobrevivência contra o terror: monstros, cultos, cidades isoladas e a lenta perda da sanidade.",
        glyph = "mood_bad",
        accent = 0xFF6B2020L,
        setting = "Vale do Corvo é uma cidade mineradora isolada pela neblina. As rádios só pegam estática, " +
            "a estrada principal termina sempre no mesmo lugar e, toda noite, alguém some.",
        characteristics = listOf("Tensão e medo constantes", "Personagens frágeis", "Recursos escassos", "Ameaças que não podem ser vencidas facilmente", "Sanidade e trauma"),
        lore = listOf(
            lore("O lugar", "Vale do Corvo: mina fechada, igreja sem padre, hospital abandonado e uma floresta que muda de forma."),
            lore("Ameaças", "O Pálido (criatura alta vista na neblina), os Silentes (moradores que pararam de falar) e o Poço."),
            lore("Regras do medo", "Testes de Sanidade ao ver o sobrenatural. Ao zerar, o personagem ganha um trauma permanente."),
        ),
        races = listOf(e("Humano", "Só humanos — e o que eles se tornam.")),
        classes = listOf(e("Sobrevivente", "Pessoa comum no lugar errado."), e("Policial local", "Conhece a cidade."), e("Médico", "Mantém os outros vivos."), e("Adolescente", "Ágil e subestimado.")),
        powers = listOf(e("Adrenalina", "Uma ação extra uma vez por sessão."), e("Pressentimento", "Sente o perigo antes de vê-lo.")),
        templates = listOf(
            template(
                "Sobrevivente", "d100, Sanidade e Sorte. Sem magia.",
                SystemPreset.INVESTIGATION.build(catalogId = HORROR).copy(
                    race = "Humano", charClass = "Sobrevivente",
                    traits = listOf(trait("Trauma: escuridão", "Adquirida", "Perde 1 de Sanidade ao ficar no escuro total.")),
                    items = listOf(item("Lanterna", "Equipamento", "Pilhas fracas.", weight = 0.3, icon = "flare"), item("Kit de primeiros socorros", "Equipamento", "Recupera 1d3 de Vida.", qty = 2, dice = "1d3", icon = "healing")),
                )
            ),
        ),
    )

    // ---------------- Ocultismo ----------------

    private fun occult() = Catalog(
        id = OCCULT,
        name = "Ocultismo",
        tagline = "Círculos de giz, nomes verdadeiros e o preço de cada palavra dita ao escuro.",
        description = "Magia ritual, demonologia, sociedades secretas e conhecimento proibido. Poder vem de pactos e símbolos.",
        glyph = "hexagon",
        accent = 0xFF5E3A9CL,
        setting = "Por trás de bibliotecas, lojas maçônicas e leilões privados, existe a Grande Obra: uma corrida " +
            "secular entre sociedades ocultas para reunir os fragmentos do Livro de Asmodel e reescrever a realidade.",
        characteristics = listOf("Magia ritual lenta e poderosa", "Pactos com entidades", "Sociedades secretas", "Conhecimento custa sanidade e alma", "Símbolos, nomes e correspondências"),
        lore = listOf(
            lore("Sociedades", "A Ordem do Sol Negro, a Irmandade da Rosa Cinzenta e o Colégio Invisível."),
            lore("Entidades", "Os 72 Príncipes, os Guardiões dos Portões e os Anônimos — que não aceitam nomes."),
            lore("Rituais", "Círculos de proteção, evocação, banimento, consagração de objetos e viagem astral."),
            lore("Artefatos", "Fragmentos do Livro de Asmodel, espelhos negros, adagas athame, selos de chumbo."),
        ),
        races = listOf(e("Humano", "Iniciado mortal."), e("Demônio", "Entidade encarnada."), e("Possuído", "Humano dividindo o corpo.")),
        classes = listOf(e("Ocultista", "Estudioso de rituais."), e("Demonólogo", "Invoca e barganha."), e("Alquimista", "Transmuta matéria e espírito."), e("Médium", "Ponte com o outro lado.")),
        powers = listOf(e("Círculo de proteção", "Barreira contra entidades."), e("Evocação", "Chama uma entidade para negociar."), e("Viagem astral", "Deixa o corpo e explora planos.")),
        templates = listOf(
            template(
                "Demônio encarnado", "Poder demoníaco, Corrupção e poderes infernais.",
                SystemPreset.SUPERNATURAL.build(catalogId = OCCULT).setAttr("CAR", 16).setAttr("FOR", 15).setAttr("INT", 14).setAttr("HUM", 2).let { c ->
                    c.copy(
                        attributes = c.attributes + Attribute(Ids.new(), "Poder demoníaco", "PDM", 14, description = "Potência das habilidades infernais."),
                        race = "Demônio", charClass = "Príncipe menor", title = "O Contratante",
                        resources = c.resources + Resource(Ids.new(), "Corrupção", 6, 10, 0, ResourceColors.CORRUPTION),
                        abilities = listOf(
                            ability("Contrato infernal", AbilityCategories.DEMONIC, "Concede um desejo pequeno em troca de um favor futuro.", icon = "history_edu"),
                            ability("Chamas do abismo", AbilityCategories.DEMONIC, "Fogo negro em cone de 4,5 m.", cost = "3 Energia espiritual", dice = "3d6", icon = "local_fire_department"),
                            ability("Forma verdadeira", AbilityCategories.TRANSFORMATION, "Asas, chifres e +2 em Força por 1 minuto.", cd = "1 por dia", icon = "whatshot"),
                        ),
                        traits = listOf(
                            trait("Resistência ao fogo", "Sobrenatural", "Reduz à metade o dano de fogo."),
                            trait("Nome verdadeiro", "Maldição", "Quem souber seu nome pode comandá-lo.", limits = "Segredo absoluto."),
                        ),
                    )
                }
            ),
            template(
                "Ocultista ritualista", "Conjurador de rituais lentos com custo em Sanidade.",
                SystemPreset.SUPERNATURAL.build(catalogId = OCCULT).setAttr("INT", 16).setAttr("SAB", 14).copy(
                    race = "Humano", charClass = "Ocultista", faction = "Irmandade da Rosa Cinzenta",
                    spells = listOf(
                        spell("Círculo de proteção", "Abjuração", 1, "Entidades não atravessam o círculo.", mana = 2, cast = "10 minutos", range = "3 m", duration = "Até ser quebrado", type = "Ritual"),
                        spell("Banimento", "Abjuração", 3, "Expulsa uma entidade para seu plano.", mana = 5, cast = "1 minuto", range = "9 m", type = "Ritual"),
                    ),
                )
            ),
        ),
    )

    // ---------------- Fantasia medieval ----------------

    private fun medievalFantasy() = Catalog(
        id = MEDIEVAL_FANTASY,
        name = "Fantasia medieval",
        tagline = "Torres de magos, cavaleiros de ordens arcanas e reinos à beira da guerra.",
        description = "A mistura clássica: castelos e feudos com magia estabelecida, escolas arcanas e criaturas lendárias.",
        glyph = "fort",
        accent = 0xFF6D4AB8L,
        setting = "O Reino de Eldoria é governado por uma aliança frágil entre a Coroa e a Torre de Âmbar, " +
            "a academia de magos que mantém as Pedras-Guarda que protegem as fronteiras dos dragões do leste.",
        characteristics = listOf("Castelos, feudos e magia organizada", "Escolas de magia formais", "Cavaleiros e magos lado a lado", "Criaturas lendárias", "Intriga entre Coroa e Torre"),
        lore = listOf(
            lore("Reinos", "Eldoria, o Principado de Varn e as Terras Draconicas."),
            lore("Escolas de magia", "Evocação, Abjuração, Ilusão, Adivinhação, Transmutação, Necromancia (proibida), Encantamento e Conjuração."),
            lore("Ordens", "Cavaleiros do Grifo, Guardiões de Âmbar e a Mão Velada (assassinos)."),
        ),
        races = listOf(e("Humano", "Maioria em Eldoria."), e("Elfo", "Mestres antigos da Torre."), e("Anão", "Forjadores de runas."), e("Meio-elfo", "Entre dois mundos."), e("Gnomo", "Inventores e ilusionistas.")),
        classes = listOf(e("Mago", "Estudante da Torre de Âmbar."), e("Paladino", "Cavaleiro sagrado."), e("Guerreiro", "Soldado de carreira."), e("Bardo", "Cronista e encantador."), e("Feiticeiro", "Magia de sangue dracônico.")),
        powers = listOf(e("Mísseis arcanos", "Dardos de força infalíveis."), e("Escudo arcano", "+5 de CA por uma rodada."), e("Golpe divino", "Dano radiante extra.")),
        templates = listOf(
            template(
                "Mago da Torre de Âmbar", "Conjurador arcano com grimório inicial.",
                SystemPreset.CLASSIC.build(catalogId = MEDIEVAL_FANTASY).setAttr("INT", 17).setAttr("DES", 14).setAttr("CON", 13).setAttr("SAB", 12).setAttr("FOR", 8).copy(
                    race = "Humano", charClass = "Mago", subclass = "Escola de Evocação", faction = "Torre de Âmbar", armorClass = 12,
                    resources = listOf(
                        Resource(Ids.new(), "Vida", 8, 8, 0, ResourceColors.LIFE),
                        Resource(Ids.new(), "Mana", 20, 20, 0, ResourceColors.MANA),
                    ),
                    spells = listOf(
                        spell("Mísseis arcanos", "Evocação", 1, "Três dardos de força que nunca erram.", mana = 2, damage = "3d4+3", range = "36 m", icon = "auto_awesome"),
                        spell("Escudo arcano", "Abjuração", 1, "+5 na CA até o próximo turno.", mana = 2, cast = "1 reação", range = "Pessoal", duration = "1 rodada", icon = "shield"),
                        spell("Bola de fogo", "Evocação", 3, "Explosão de 6 m de raio.", mana = 6, damage = "8d6", range = "45 m", icon = "local_fire_department"),
                        spell("Luz", "Evocação", 0, "Um objeto brilha como uma tocha.", range = "Toque", duration = "1 hora", icon = "flare"),
                    ),
                    items = listOf(
                        item("Grimório", "Relíquia", "Contém as magias aprendidas.", weight = 1.5, rarity = Rarity.UNCOMMON, icon = "menu_book"),
                        item("Cajado de carvalho", "Arma", "Foco arcano.", weight = 2.0, dice = "1d6", equipped = true, icon = "auto_fix"),
                        item("Poção de cura", "Poção", "Recupera 2d4+2 de Vida.", qty = 2, weight = 0.5, value = "50 PO", rarity = Rarity.UNCOMMON, dice = "2d4+2", icon = "science"),
                    ),
                    story = Story(origin = "Aprendiz da Torre desde os nove anos."),
                )
            ),
        ),
    )
}
