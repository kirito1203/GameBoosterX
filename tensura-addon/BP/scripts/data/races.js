// Raças e linhagens.
//
// Cada raça pertence a uma linhagem (que tem uma árvore racial própria) e a um
// estágio de evolução. As evoluções são nós da árvore racial (tipo 'evolution').
//
// Campos:
//   mp      capacidade base de Magicules       regen  Magicules por segundo
//   hp      nível de health_boost permanente (-1 = nenhum)
//   eff     efeitos permanentes [[efeito, amplificador]]
//   resist  redução de dano por grupo (0..1)   weak   dano extra por grupo (0..1)
//   aff     multiplicador de custo por árvore  (0.8 = 20% mais barato)
//   power   multiplicador de dano por elemento
//   special mecânicas próprias (ver systems/passives.js)
//   origin  'obra' | 'adaptado' | 'original'  (ver data/dsl.js)

export const RACES = {
  // ── SLIME ──
  slime: { name: 'Slime', original: 'Slime', lineage: 'slime', stage: 0, origin: 'obra', start: true,
    desc: 'Corpo gelatinoso sem forma fixa. Fraco no início, mas capaz de absorver e copiar o que devora.',
    mp: 120, regen: 3, hp: -1, eff: [], resist: { fall: 1, physical: 0.2 }, weak: {}, aff: {}, power: {} },
  demon_slime: { name: 'Demon Slime', original: 'Demon Slime', lineage: 'slime', stage: 1, origin: 'obra',
    desc: 'Slime que despertou como Lorde Demônio. Corpo de Magicules densas e regeneração extrema.',
    mp: 320, regen: 6, hp: 1, eff: [['strength', 0]], resist: { fall: 1, physical: 0.35, magic: 0.3, fire: 0.3 }, weak: {}, aff: { '*': 0.9 }, power: {} },
  god_slime: { name: 'God Slime', original: 'God Slime', lineage: 'slime', stage: 2, origin: 'original',
    desc: 'Estágio final criado pelo addon (nome sugerido pelo projeto, não confirmado na obra).',
    mp: 700, regen: 12, hp: 3, eff: [['strength', 1], ['resistance', 0]], resist: { fall: 1, physical: 0.5, magic: 0.5, fire: 0.5, dark: 0.5, holy: 0.3 }, weak: {}, aff: { '*': 0.8 }, power: { '*': 1.2 } },

  // ── HUMANO ──
  human: { name: 'Humano', original: 'Human', lineage: 'human', stage: 0, origin: 'obra', start: true,
    desc: 'Versátil e adaptável. Aprende magia universal com facilidade.',
    mp: 80, regen: 2, hp: -1, eff: [], resist: {}, weak: {}, aff: {}, power: {} },
  enlightened: { name: 'Iluminado', original: 'Enlightened', lineage: 'human', stage: 1, origin: 'obra',
    desc: 'Humano que despertou o corpo espiritual (Sennin). Envelhecimento detido e grande poder mágico.',
    mp: 220, regen: 5, hp: 1, eff: [], resist: { magic: 0.2 }, weak: {}, aff: { '*': 0.9 }, power: {} },
  saint: { name: 'Santo', original: 'Saint', lineage: 'human', stage: 2, origin: 'obra',
    desc: 'O ápice humano (Seijin): corpo semiespiritual, imune a quase todas as doenças e venenos.',
    mp: 450, regen: 9, hp: 2, eff: [['resistance', 0]], resist: { magic: 0.35, dark: 0.4, holy: 0.5 }, weak: {}, aff: { '*': 0.85, holy: 0.8 }, power: { holy: 1.25 } },

  // ── GOBLIN ──
  goblin: { name: 'Goblin', original: 'Goblin', lineage: 'goblin', stage: 0, origin: 'obra', start: true,
    desc: 'Pequeno e frágil, mas leal e numeroso. Evolui rápido quando recebe um nome.',
    mp: 60, regen: 2, hp: -1, eff: [['speed', 0]], resist: {}, weak: { physical: 0.1 }, aff: {}, power: {} },
  hobgoblin: { name: 'Hobgoblin', original: 'Hobgoblin', lineage: 'goblin', stage: 1, origin: 'obra',
    desc: 'Goblin evoluído: mais alto, forte e inteligente.',
    mp: 150, regen: 4, hp: 1, eff: [['speed', 0]], resist: { physical: 0.1 }, weak: {}, aff: {}, power: {} },
  goblin_lord: { name: 'Goblin Lorde', original: 'Goblin Lord', lineage: 'goblin', stage: 2, origin: 'original',
    desc: 'Evolução superior criada pelo addon: líder nato dos goblins.',
    mp: 300, regen: 6, hp: 2, eff: [['speed', 1], ['strength', 0]], resist: { physical: 0.2, magic: 0.15 }, weak: {}, aff: { elemental: 0.9 }, power: {} },

  // ── OGRO / ONI ──
  ogre: { name: 'Ogro', original: 'Ogre', lineage: 'ogre', stage: 0, origin: 'obra', start: true,
    desc: 'Guerreiro de força bruta. Pouca magia, muita resistência física.',
    mp: 70, regen: 2, hp: 1, eff: [['strength', 0]], resist: { physical: 0.15 }, weak: { magic: 0.1 }, aff: {}, power: {} },
  kijin: { name: 'Kijin', original: 'Kijin', lineage: 'ogre', stage: 1, origin: 'obra',
    desc: 'Ogro que recebeu um nome e evoluiu: domínio elemental e físico superior.',
    mp: 220, regen: 5, hp: 2, eff: [['strength', 1]], resist: { physical: 0.25, fire: 0.2 }, weak: {}, aff: { elemental: 0.85 }, power: { fire: 1.1 } },
  mystic_oni: { name: 'Mystic Oni', original: 'Mystic Oni', lineage: 'ogre', stage: 2, origin: 'obra',
    desc: 'Oni místico: corpo semiespiritual e magia refinada.',
    mp: 450, regen: 9, hp: 2, eff: [['strength', 1]], resist: { physical: 0.3, fire: 0.3, magic: 0.3 }, weak: {}, aff: { elemental: 0.8, spirit: 0.85 }, power: { '*': 1.15 } },
  wicked_oni: { name: 'Wicked Oni', original: 'Wicked Oni', lineage: 'ogre', stage: 2, origin: 'obra',
    desc: 'Oni perverso: força física devastadora e poder das trevas.',
    mp: 380, regen: 7, hp: 3, eff: [['strength', 2]], resist: { physical: 0.4, fire: 0.2, dark: 0.3 }, weak: { holy: 0.2 }, aff: { dark: 0.8 }, power: { physical: 1.25, dark: 1.2 } },

  // ── LIZARDMAN ──
  lizardman: { name: 'Lizardman', original: 'Lizardman', lineage: 'lizardman', stage: 0, origin: 'obra', start: true,
    desc: 'Guerreiro anfíbio das terras pantanosas. Respira debaixo d’água.',
    mp: 80, regen: 2, hp: -1, eff: [['water_breathing', 0]], resist: { drowning: 1 }, weak: { freezing: 0.2 }, aff: {}, power: {} },
  dragonewt: { name: 'Dragonewt', original: 'Dragonewt', lineage: 'lizardman', stage: 1, origin: 'obra',
    desc: 'Lizardman evoluído com sangue de dragão: escamas, asas e sopro.',
    mp: 230, regen: 5, hp: 1, eff: [['water_breathing', 0]], resist: { drowning: 1, fire: 0.3, physical: 0.15 }, weak: { freezing: 0.15 }, aff: {}, power: { fire: 1.1, lightning: 1.1 } },
  true_dragonewt: { name: 'Dragonewt Verdadeiro', original: 'True Dragonewt', lineage: 'lizardman', stage: 2, origin: 'original',
    desc: 'Evolução superior criada pelo addon: o sangue dracônico desperta por completo.',
    mp: 450, regen: 9, hp: 2, eff: [['water_breathing', 0], ['strength', 0]], resist: { drowning: 1, fire: 0.5, physical: 0.3, lightning: 0.3 }, weak: {}, aff: { advanced: 0.9 }, power: { fire: 1.2, lightning: 1.2, dragon: 1.25 } },

  // ── ORC ──
  orc: { name: 'Orc', original: 'Orc', lineage: 'orc', stage: 0, origin: 'obra', start: true,
    desc: 'Robusto e faminto. Resiste a danos físicos e cresce devorando.',
    mp: 60, regen: 2, hp: 1, eff: [], resist: { physical: 0.15, starve: 0.5 }, weak: { fire: 0.1 }, aff: {}, power: {} },
  high_orc: { name: 'High Orc', original: 'High Orc', lineage: 'orc', stage: 1, origin: 'obra',
    desc: 'Orc evoluído e disciplinado, com corpo poderoso.',
    mp: 170, regen: 4, hp: 2, eff: [['strength', 0]], resist: { physical: 0.25, starve: 1 }, weak: {}, aff: {}, power: {} },
  orc_disaster: { name: 'Orc Calamidade', original: 'Orc Disaster', lineage: 'orc', stage: 2, origin: 'original',
    desc: 'Evolução superior criada pelo addon: um orc de força e apetite calamitosos.',
    mp: 380, regen: 7, hp: 4, eff: [['strength', 1], ['resistance', 0]], resist: { physical: 0.4, starve: 1, fire: 0.2 }, weak: {}, aff: {}, power: { physical: 1.25 } },

  // ── DEMÔNIO ──
  lesser_demon: { name: 'Demônio Menor', original: 'Lesser Demon', lineage: 'demon', stage: 0, origin: 'obra', start: true,
    desc: 'Demônio de baixo nível. Ser espiritual com afinidade pelas trevas. Fraco ao sagrado.',
    mp: 150, regen: 4, hp: -1, eff: [['fire_resistance', 0]], resist: { fire: 1, magic: 0.1 }, weak: { holy: 0.5 }, aff: { dark: 0.85, illusion: 0.9 }, power: { dark: 1.1 } },
  greater_demon: { name: 'Demônio Maior', original: 'Greater Demon', lineage: 'demon', stage: 1, origin: 'obra',
    desc: 'Demônio de nível intermediário com corpo material estável.',
    mp: 300, regen: 6, hp: 1, eff: [['fire_resistance', 0]], resist: { fire: 1, magic: 0.2 }, weak: { holy: 0.4 }, aff: { dark: 0.8, illusion: 0.85 }, power: { dark: 1.15 } },
  arch_demon: { name: 'Arquidemônio', original: 'Arch Demon', lineage: 'demon', stage: 2, origin: 'obra',
    desc: 'Demônio de alto nível, capaz de criar matéria e dominar mentes.',
    mp: 500, regen: 9, hp: 2, eff: [['fire_resistance', 0], ['strength', 0]], resist: { fire: 1, magic: 0.35, dark: 0.4 }, weak: { holy: 0.3 }, aff: { dark: 0.75, illusion: 0.8, aspect: 0.9 }, power: { dark: 1.25 } },
  demon_peer: { name: 'Demon Peer', original: 'Demon Peer', lineage: 'demon', stage: 3, origin: 'obra',
    desc: 'Nobreza demoníaca: poder comparável aos Primordiais.',
    mp: 800, regen: 13, hp: 3, eff: [['fire_resistance', 0], ['strength', 1], ['resistance', 0]], resist: { fire: 1, magic: 0.5, dark: 0.6, physical: 0.3 }, weak: { holy: 0.2 }, aff: { '*': 0.85, dark: 0.7 }, power: { dark: 1.35, '*': 1.1 } },

  // ── ANJO ──
  angel: { name: 'Anjo', original: 'Angel', lineage: 'angel', stage: 0, origin: 'obra', start: true,
    desc: 'Ser espiritual celestial. Afinidade com a luz, fraco às trevas.',
    mp: 150, regen: 4, hp: -1, eff: [['slow_falling', 0]], resist: { fall: 0.5, holy: 1 }, weak: { dark: 0.4 }, aff: { holy: 0.8 }, power: { holy: 1.15 } },
  arch_angel: { name: 'Arcanjo', original: 'Arch Angel', lineage: 'angel', stage: 1, origin: 'original',
    desc: 'Evolução celestial criada pelo addon: guardião da luz.',
    mp: 380, regen: 8, hp: 2, eff: [['slow_falling', 0], ['resistance', 0]], resist: { fall: 1, holy: 1, magic: 0.3 }, weak: { dark: 0.3 }, aff: { holy: 0.7 }, power: { holy: 1.3 } },
  fallen_angel: { name: 'Anjo Caído', original: 'Fallen', lineage: 'angel', stage: 1, origin: 'obra',
    desc: 'Anjo que caiu: troca a luz pelas trevas e ganha força bruta.',
    mp: 350, regen: 7, hp: 2, eff: [['slow_falling', 0], ['strength', 1]], resist: { fall: 1, dark: 0.5, magic: 0.2 }, weak: { holy: 0.3 }, aff: { dark: 0.75 }, power: { dark: 1.25 } },

  // ── DRAGONOIDE ──
  dragonoid: { name: 'Dragonoide', original: 'Dragonoid', lineage: 'dragonoid', stage: 0, origin: 'obra', start: true,
    desc: 'Descendente humanoide dos dragões. Corpo resistente e chamas no sangue.',
    mp: 140, regen: 3, hp: 1, eff: [], resist: { fire: 0.4, physical: 0.15 }, weak: { freezing: 0.2 }, aff: {}, power: { fire: 1.1, dragon: 1.15 } },
  elder_dragonoid: { name: 'Dragonoide Ancestral', original: 'Elder Dragonoid', lineage: 'dragonoid', stage: 1, origin: 'original',
    desc: 'Evolução criada pelo addon: o sangue dos dragões verdadeiros se manifesta.',
    mp: 420, regen: 8, hp: 3, eff: [['strength', 1], ['fire_resistance', 0]], resist: { fire: 1, physical: 0.3, magic: 0.25 }, weak: {}, aff: { advanced: 0.85 }, power: { fire: 1.25, dragon: 1.35 } },

  // ── VAMPIRO ──
  vampire: { name: 'Vampiro', original: 'Vampire', lineage: 'vampire', stage: 0, origin: 'obra', start: true,
    desc: 'Imortal da noite. Forte no escuro, queima sob o sol, fraco ao sagrado.',
    mp: 140, regen: 3, hp: -1, eff: [['night_vision', 0]], resist: { drowning: 1, dark: 0.4 }, weak: { holy: 0.5, fire: 0.2 }, aff: { dark: 0.85 }, power: { blood: 1.2 }, special: ['sunburn', 'night_power'] },
  vampire_lord: { name: 'Vampiro Ancião', original: 'Vampire Lord', lineage: 'vampire', stage: 1, origin: 'original',
    desc: 'Evolução criada pelo addon: vampiro antigo que tolera o sol por mais tempo.',
    mp: 380, regen: 8, hp: 2, eff: [['night_vision', 0], ['strength', 0]], resist: { drowning: 1, dark: 0.6, physical: 0.2 }, weak: { holy: 0.35 }, aff: { dark: 0.75 }, power: { blood: 1.35, dark: 1.15 }, special: ['sunburn_weak', 'night_power'] },

  // ── ELEMENTAL ──
  elemental: { name: 'Elemental', original: 'Elemental', lineage: 'elemental', stage: 0, origin: 'adaptado', start: true,
    desc: 'Espírito elemental com corpo de Magicules. Grande afinidade com elementos e espíritos.',
    mp: 160, regen: 4, hp: -1, eff: [], resist: { fire: 0.3, freezing: 0.3, lightning: 0.3, drowning: 1 }, weak: { physical: 0.15 }, aff: { elemental: 0.85, spirit: 0.8 }, power: {} },
  greater_elemental_race: { name: 'Elemental Superior', original: 'Greater Elemental', lineage: 'elemental', stage: 1, origin: 'adaptado',
    desc: 'Espírito elemental de alto nível.',
    mp: 380, regen: 8, hp: 1, eff: [], resist: { fire: 0.5, freezing: 0.5, lightning: 0.5, drowning: 1, magic: 0.2 }, weak: {}, aff: { elemental: 0.75, spirit: 0.7 }, power: { '*': 1.15 } },
  spirit_lord: { name: 'Senhor Espiritual', original: 'Spirit Lord', lineage: 'elemental', stage: 2, origin: 'original',
    desc: 'Evolução final criada pelo addon: soberano dos espíritos elementais.',
    mp: 650, regen: 12, hp: 2, eff: [['resistance', 0]], resist: { fire: 0.7, freezing: 0.7, lightning: 0.7, drowning: 1, magic: 0.4 }, weak: {}, aff: { elemental: 0.65, spirit: 0.6 }, power: { '*': 1.25 } },

  // ── ELFO ──
  elf: { name: 'Elfo', original: 'Elf', lineage: 'elf', stage: 0, origin: 'obra', start: true,
    desc: 'Longevo e ágil, ligado aos espíritos e à natureza.',
    mp: 110, regen: 3, hp: -1, eff: [['speed', 0]], resist: {}, weak: { dark: 0.1 }, aff: { spirit: 0.85 }, power: {} },
  high_elf: { name: 'Alto Elfo', original: 'High Elf', lineage: 'elf', stage: 1, origin: 'original',
    desc: 'Evolução criada pelo addon: elfo de linhagem antiga e grande poder espiritual.',
    mp: 330, regen: 7, hp: 1, eff: [['speed', 1]], resist: { magic: 0.25 }, weak: {}, aff: { spirit: 0.7, elemental: 0.9 }, power: { spirit: 1.2 } },

  // ── ANÃO ──
  dwarf: { name: 'Anão', original: 'Dwarf', lineage: 'dwarf', stage: 0, origin: 'obra', start: true,
    desc: 'Artesão robusto das montanhas. Minera e forja como ninguém.',
    mp: 70, regen: 2, hp: 1, eff: [['haste', 0]], resist: { physical: 0.1, fire: 0.2 }, weak: {}, aff: {}, power: {} },
  elder_dwarf: { name: 'Anão Ancião', original: 'Elder Dwarf', lineage: 'dwarf', stage: 1, origin: 'original',
    desc: 'Evolução criada pelo addon: mestre ferreiro com corpo de pedra.',
    mp: 220, regen: 5, hp: 2, eff: [['haste', 1], ['resistance', 0]], resist: { physical: 0.25, fire: 0.4 }, weak: {}, aff: { elemental: 0.9 }, power: { earth: 1.2 } },

  // ── DRYAD ──
  dryad: { name: 'Dríade', original: 'Dryad', lineage: 'dryad', stage: 0, origin: 'obra', start: true,
    desc: 'Espírito das árvores. Regenera ao sol, teme o fogo.',
    mp: 140, regen: 4, hp: -1, eff: [], resist: { magic: 0.1 }, weak: { fire: 0.35 }, aff: { spirit: 0.8, aspect: 0.9 }, power: {}, special: ['photosynthesis'] },
  spirit_dryad: { name: 'Dríade Espiritual', original: 'Spirit Dryad', lineage: 'dryad', stage: 1, origin: 'original',
    desc: 'Evolução criada pelo addon: guardiã espiritual da floresta.',
    mp: 380, regen: 8, hp: 1, eff: [], resist: { magic: 0.3 }, weak: { fire: 0.2 }, aff: { spirit: 0.7, aspect: 0.8, holy: 0.9 }, power: { spirit: 1.2 }, special: ['photosynthesis'] },

  // ── DIREWOLF ──
  direwolf: { name: 'Direwolf', original: 'Direwolf', lineage: 'direwolf', stage: 0, origin: 'obra', start: true,
    desc: 'Lobo feroz e veloz, caçador em matilha.',
    mp: 80, regen: 2, hp: -1, eff: [['speed', 1]], resist: { fall: 0.3 }, weak: {}, aff: {}, power: { physical: 1.1 } },
  tempest_wolf: { name: 'Tempest Wolf', original: 'Tempest Wolf', lineage: 'direwolf', stage: 1, origin: 'obra',
    desc: 'Lobo da tempestade: domina o vento e o relâmpago.',
    mp: 220, regen: 5, hp: 1, eff: [['speed', 2]], resist: { fall: 0.6, lightning: 0.4 }, weak: {}, aff: { elemental: 0.85 }, power: { lightning: 1.2, wind: 1.2 } },
  tempest_star_wolf: { name: 'Tempest Star Wolf', original: 'Tempest Star Wolf', lineage: 'direwolf', stage: 2, origin: 'obra',
    desc: 'O lobo estelar da tempestade, evolução suprema dos Direwolves.',
    mp: 450, regen: 9, hp: 2, eff: [['speed', 2], ['strength', 1]], resist: { fall: 1, lightning: 0.7, physical: 0.25 }, weak: {}, aff: { elemental: 0.75 }, power: { lightning: 1.35, wind: 1.3 } },

  // ── HARPIA ──
  harpy: { name: 'Harpia', original: 'Harpy', lineage: 'harpy', stage: 0, origin: 'obra', start: true,
    desc: 'Povo alado dos céus. Leve, não sofre dano de queda e domina o vento.',
    mp: 100, regen: 3, hp: -1, eff: [['slow_falling', 0], ['jump_boost', 1]], resist: { fall: 1 }, weak: { lightning: 0.2, physical: 0.1 }, aff: { elemental: 0.9 }, power: { wind: 1.15 } },
  harpy_queen: { name: 'Rainha Harpia', original: 'Harpy Queen', lineage: 'harpy', stage: 1, origin: 'original',
    desc: 'Evolução criada pelo addon: soberana dos céus.',
    mp: 330, regen: 7, hp: 1, eff: [['slow_falling', 0], ['jump_boost', 2], ['speed', 1]], resist: { fall: 1, physical: 0.15 }, weak: {}, aff: { elemental: 0.8 }, power: { wind: 1.3 } },

  // ── MERFOLK ──
  merfolk: { name: 'Merfolk', original: 'Merfolk', lineage: 'merfolk', stage: 0, origin: 'adaptado', start: true,
    desc: 'Povo do mar. Respira e nada livremente na água, mas fica lento em terra seca.',
    mp: 110, regen: 3, hp: -1, eff: [['water_breathing', 0]], resist: { drowning: 1 }, weak: { lightning: 0.3, fire: 0.1 }, aff: {}, power: { water: 1.2, ice: 1.1 }, special: ['aquatic'] },
  merfolk_lord: { name: 'Senhor das Marés', original: 'Merfolk Lord', lineage: 'merfolk', stage: 1, origin: 'original',
    desc: 'Evolução criada pelo addon: comanda as águas.',
    mp: 330, regen: 7, hp: 1, eff: [['water_breathing', 0]], resist: { drowning: 1, freezing: 0.4 }, weak: { lightning: 0.2 }, aff: { elemental: 0.85 }, power: { water: 1.35, ice: 1.25 }, special: ['aquatic'] },

  // ── GHOUL ──
  ghoul: { name: 'Ghoul', original: 'Ghoul', lineage: 'ghoul', stage: 0, origin: 'adaptado', start: true,
    desc: 'Morto-vivo devorador. Não teme venenos, mas o sagrado o fere e a fome o persegue.',
    mp: 90, regen: 2, hp: -1, eff: [['night_vision', 0]], resist: { drowning: 1, dark: 0.5 }, weak: { holy: 0.5, fire: 0.15 }, aff: { necromancy: 0.8 }, power: {}, special: ['undead', 'hungry'] },
  ghoul_lord: { name: 'Ghoul Lorde', original: 'Ghoul Lord', lineage: 'ghoul', stage: 1, origin: 'original',
    desc: 'Evolução criada pelo addon: senhor dos mortos-vivos.',
    mp: 300, regen: 6, hp: 2, eff: [['night_vision', 0], ['strength', 0]], resist: { drowning: 1, dark: 0.7, physical: 0.2 }, weak: { holy: 0.35 }, aff: { necromancy: 0.65, dark: 0.85 }, power: { dark: 1.2 }, special: ['undead'] },

  // ── GIGANTE ──
  giant: { name: 'Gigante', original: 'Giant', lineage: 'giant', stage: 0, origin: 'obra', start: true,
    desc: 'Enorme e resistente. Muita vida e força, porém mais lento.',
    mp: 70, regen: 2, hp: 3, eff: [['strength', 0], ['slowness', 0]], resist: { physical: 0.2, fall: 0.2 }, weak: { magic: 0.1 }, aff: {}, power: { physical: 1.15 } },
  elder_giant: { name: 'Gigante Ancestral', original: 'Elder Giant', lineage: 'giant', stage: 1, origin: 'original',
    desc: 'Evolução criada pelo addon: titã das montanhas, já sem a lentidão.',
    mp: 260, regen: 5, hp: 5, eff: [['strength', 1], ['resistance', 0]], resist: { physical: 0.35, fall: 0.4, explosion: 0.3 }, weak: {}, aff: {}, power: { physical: 1.3, earth: 1.2 } },
};

export const LINEAGES = {
  slime: { name: 'Slime', base: 'slime' },
  human: { name: 'Humano', base: 'human' },
  goblin: { name: 'Goblin', base: 'goblin' },
  ogre: { name: 'Ogro / Oni', base: 'ogre' },
  lizardman: { name: 'Lizardman', base: 'lizardman' },
  orc: { name: 'Orc', base: 'orc' },
  demon: { name: 'Demônio', base: 'lesser_demon' },
  angel: { name: 'Anjo', base: 'angel' },
  dragonoid: { name: 'Dragonoide', base: 'dragonoid' },
  vampire: { name: 'Vampiro', base: 'vampire' },
  elemental: { name: 'Elemental', base: 'elemental' },
  elf: { name: 'Elfo', base: 'elf' },
  dwarf: { name: 'Anão', base: 'dwarf' },
  dryad: { name: 'Dríade', base: 'dryad' },
  direwolf: { name: 'Direwolf', base: 'direwolf' },
  harpy: { name: 'Harpia', base: 'harpy' },
  merfolk: { name: 'Merfolk', base: 'merfolk' },
  ghoul: { name: 'Ghoul', base: 'ghoul' },
  giant: { name: 'Gigante', base: 'giant' },
};

for (const id in RACES) RACES[id].id = id;
