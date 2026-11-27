import { S } from '../dsl.js';
import { RC, T, evo, lineageTree } from '../raceDsl.js';
import { SLIME_SKILLS, SLIME_LINKS } from '../skill_trees/slime/index.js';

// Árvores raciais: uma por linhagem. A raiz é o nó da raça base (concedido).
// Evoluções são nós do tipo 'evolution' no ramo leste. Habilidades marcadas com
// `initial: '<raça>'` são concedidas ao se tornar aquela raça; todas as outras
// precisam ser desbloqueadas na árvore.

const A = 'adaptado';
const OR = 'original';

export const RACIAL_TREES = [
  // ── SLIME ────────────────────────────────────────────────────
  // Árvore completa do Rimuru Tempest (9 setores): data/skill_trees/slime/.
  lineageTree('slime', SLIME_SKILLS, SLIME_LINKS),

  // ── HUMANO ───────────────────────────────────────────────────
  lineageTree('human', [
    S('r.human.magic_learning', 'Aprendizado Mágico', 'Magic Learning', 'common', {
      cost: 10, req: ['r.human.root'], arm: 'N', initial: 'human', origin: A, cat: `${RC} · Talento`,
      desc: 'Humanos aprendem magia com facilidade: habilidades das árvores universais custam 15% menos pontos.',
      fx: { k: 'p_points', mult: 0.85 } }),
    T.elemAffinity('r.human', { req: ['r.human.magic_learning'] }),
    T.spiritAffinity('r.human', { req: ['r.human.magic_learning'] }),
    T.magicResist('r.human', { req: ['r.human.root'], arm: 'S', amount: 0.25 }),
    S('r.human.enchantment', 'Encantamento', 'Enchantment', 'rare', {
      cost: 50, lvl: 12, req: ['r.human.magic_resist'], origin: A, mp: 60, cd: 300, cat: `${RC} · Talento`,
      desc: 'Encanta o item da mão principal com um encantamento compatível aleatório (nível conforme seu nível).',
      fx: { k: 'enchant' } }),
    S('r.human.path_arcane', 'Caminho Arcano', 'Arcane Path', 'rare', {
      cost: 40, lvl: 15, req: ['r.human.elemental_affinity'], excl: ['r.human.path_martial'], origin: OR, cat: `${RC} · Especialização`,
      desc: 'Especialização mágica: +80 Magicules máximas e +10% de poder em todas as magias. Exclusiva com o Caminho Marcial.',
      fx: [{ k: 'p_mp', max: 80 }, { k: 'p_power', el: '*', mult: 1.1 }] }),
    S('r.human.path_spirit', 'Caminho Espiritual', 'Spirit Path', 'rare', {
      cost: 40, lvl: 15, req: ['r.human.spirit_affinity'], origin: OR, cat: `${RC} · Especialização`,
      desc: 'Especialização espiritual: regeneração de Magicules +50% e invocações mais baratas.',
      fx: [{ k: 'p_mp', regenMult: 1.5 }, { k: 'p_affinity', tree: 'summon', mult: 0.85 }] }),
    S('r.human.path_martial', 'Caminho Marcial', 'Martial Path', 'rare', {
      cost: 40, lvl: 15, req: ['r.human.magic_resist'], excl: ['r.human.path_arcane'], origin: OR, cat: `${RC} · Especialização`,
      desc: 'Especialização guerreira: força e resistência permanentes. Exclusiva com o Caminho Arcano.',
      fx: { k: 'p_effect', eff: [['strength', 1], ['resistance', 0]] } }),
    evo('r.human', 'enlightened', 'legendary', { cost: 140, lvl: 30, req: ['r.human.root'], arm: 'E', needCount: 6 }),
    S('r.human.spirit_body', 'Corpo Espiritual', 'Spiritual Body', 'epic', {
      cost: 80, lvl: 32, req: ['r.human.evo_enlightened'], origin: A, cat: `${RC} · Corpo`,
      desc: 'Corpo parcialmente espiritual: regeneração constante e +100 Magicules máximas.',
      fx: [{ k: 'p_effect', eff: [['regeneration', 0]] }, { k: 'p_mp', max: 100 }] }),
    evo('r.human', 'saint', 'supreme', { cost: 280, lvl: 55, req: ['r.human.evo_enlightened'], needCount: 9, flags: ['killed_wither'] }),
  ]),

  // ── GOBLIN ───────────────────────────────────────────────────
  lineageTree('goblin', [
    T.thoughtComm('r.goblin', { req: ['r.goblin.root'], arm: 'N', initial: 'goblin' }),
    T.ultraSmell('r.goblin', { req: ['r.goblin.root'], arm: 'S' }),
    T.coercion('r.goblin', { req: ['r.goblin.thought_comm'] }),
    T.elemAffinity('r.goblin', { req: ['r.goblin.ultra_smell'] }),
    S('r.goblin.pack_tactics', 'Tática de Bando', 'Pack Tactics', 'common', {
      cost: 25, lvl: 6, req: ['r.goblin.coercion'], origin: OR, cat: `${RC} · Bando`,
      desc: 'Com outro jogador ou invocação a até 8 blocos, você recebe força e resistência.',
      fx: { k: 'p_race', pack: true } }),
    evo('r.goblin', 'hobgoblin', 'rare', { cost: 50, lvl: 12, req: ['r.goblin.root'], arm: 'E', needCount: 3 }),
    T.physEnhance('r.goblin', { req: ['r.goblin.evo_hobgoblin'], lvl: 14, initial: 'hobgoblin' }),
    S('r.goblin.goblin_rider', 'Cavaleiro Lupino', 'Goblin Rider', 'rare', {
      cost: 45, lvl: 16, req: ['r.goblin.evo_hobgoblin'], origin: A, mp: 30, cd: 90, cat: `${RC} · Bando`,
      desc: 'Invoca um lobo companheiro (Tempest Wolf) que luta ao seu lado por 90 s.',
      fx: { k: 'summon', entity: 'tensura:hound_dog', count: 1, dur: 90 } }),
    evo('r.goblin', 'goblin_lord', 'legendary', { cost: 140, lvl: 35, req: ['r.goblin.evo_hobgoblin'], needCount: 7 }),
    T.magicResist('r.goblin', { req: ['r.goblin.evo_goblin_lord'], lvl: 36, amount: 0.3 }),
  ]),

  // ── OGRO / ONI ───────────────────────────────────────────────
  lineageTree('ogre', [
    T.physEnhance('r.ogre', { req: ['r.ogre.root'], arm: 'N', initial: 'ogre' }),
    S('r.ogre.war_cry', 'Grito de Guerra', 'War Cry', 'common', {
      cost: 20, lvl: 5, req: ['r.ogre.physical_enhancement'], origin: OR, mp: 12, cd: 40, cat: `${RC} · Combate`,
      desc: 'Grito que fortalece você e aliados próximos.', fx: { k: 'buff', allies: 8, eff: [['strength', 20, 1]] } }),
    T.physResist('r.ogre', { req: ['r.ogre.root'], arm: 'S' }),
    evo('r.ogre', 'kijin', 'epic', { cost: 90, lvl: 18, req: ['r.ogre.root'], arm: 'E', needCount: 3 }),
    S('r.ogre.elemental_manipulation', 'Manipulação Elemental', 'Elemental Manipulation', 'rare', {
      cost: 50, lvl: 20, req: ['r.ogre.evo_kijin'], initial: 'kijin', cat: `${RC} · Oni`,
      desc: 'Controle refinado dos elementos: magias elementais 20% mais baratas e 15% mais fortes.',
      fx: [{ k: 'p_affinity', tree: 'elemental', mult: 0.8 }, { k: 'p_power', el: 'elemental', mult: 1.15 }] }),
    S('r.ogre.magicule_manipulation', 'Manipulação de Magicules', 'Magicule Manipulation', 'rare', {
      cost: 50, lvl: 20, req: ['r.ogre.evo_kijin'], cat: `${RC} · Oni`,
      desc: 'Controle fino das Magicules: +80 de capacidade e +2/s de regeneração.', fx: { k: 'p_mp', max: 80, regen: 2 } }),
    S('r.ogre.elemental_transformation', 'Transformação Elemental', 'Elemental Transformation', 'epic', {
      cost: 90, lvl: 26, req: ['r.ogre.elemental_manipulation'], mp: 50, cd: 90, cat: `${RC} · Oni`,
      desc: 'O corpo se funde ao fogo por 30 s: força, imunidade ao fogo e golpes flamejantes.',
      fx: [{ k: 'buff', eff: [['strength', 30, 2], ['fire_resistance', 30, 0], ['speed', 30, 1]] }, { k: 'imbue', el: 'fire', dur: 30, dmg: 4, ignite: 4 }] }),
    evo('r.ogre', 'mystic_oni', 'legendary', { cost: 160, lvl: 40, req: ['r.ogre.evo_kijin'], excl: ['r.ogre.evo_wicked_oni'], needCount: 7 }),
    evo('r.ogre', 'wicked_oni', 'legendary', { cost: 160, lvl: 40, req: ['r.ogre.evo_kijin'], excl: ['r.ogre.evo_mystic_oni'], needCount: 7 }),
    S('r.ogre.oni_spirit', 'Espírito Oni', 'Oni Spirit', 'epic', {
      cost: 100, lvl: 42, req: ['r.ogre.evo_mystic_oni'], origin: OR, cat: `${RC} · Oni`,
      desc: 'Corpo semiespiritual do Mystic Oni: resistência mágica e regeneração.',
      fx: [{ k: 'p_resist', groups: { magic: 0.25 } }, { k: 'p_effect', eff: [['regeneration', 0]] }] }),
    S('r.ogre.wicked_strength', 'Força Perversa', 'Wicked Strength', 'epic', {
      cost: 100, lvl: 42, req: ['r.ogre.evo_wicked_oni'], origin: OR, cat: `${RC} · Oni`,
      desc: 'Força bruta do Wicked Oni: seus golpes drenam 15% do dano como vida.',
      fx: [{ k: 'p_lifesteal', pct: 0.15 }, { k: 'p_effect', eff: [['strength', 1]] }] }),
  ]),

  // ── LIZARDMAN ────────────────────────────────────────────────
  lineageTree('lizardman', [
    S('r.lizardman.swim', 'Nadador Nato', 'Natural Swimmer', 'common', {
      cost: 10, req: ['r.lizardman.root'], arm: 'S', initial: 'lizardman', origin: OR, cat: `${RC} · Corpo`,
      desc: 'Na água você ganha velocidade e visão (Conduit Power).', fx: { k: 'p_regen_env', water: [['conduit_power', 0]] } }),
    T.physEnhance('r.lizardman', { req: ['r.lizardman.root'], arm: 'N' }),
    S('r.lizardman.dragon_scales', 'Escamas Dracônicas', 'Dragon Scales', 'rare', {
      cost: 40, lvl: 10, req: ['r.lizardman.physical_enhancement'], cat: `${RC} · Dragão`,
      desc: 'Escamas duras: resistência física e ao fogo.', fx: { k: 'p_resist', groups: { physical: 0.25, fire: 0.3 } } }),
    evo('r.lizardman', 'dragonewt', 'epic', { cost: 90, lvl: 18, req: ['r.lizardman.root'], arm: 'E', needCount: 3 }),
    S('r.lizardman.dragon_eye', 'Olho de Dragão', 'Dragon Eye', 'rare', {
      cost: 40, lvl: 20, req: ['r.lizardman.evo_dragonewt'], initial: 'dragonewt', cat: `${RC} · Dragão`,
      desc: 'Visão dracônica: enxerga no escuro e suas magias ficam 10% mais fortes.',
      fx: [{ k: 'p_effect', eff: [['night_vision', 0]] }, { k: 'p_power', el: '*', mult: 1.1 }] }),
    S('r.lizardman.dragon_body', 'Corpo Dracônico', 'Dragon Body', 'epic', {
      cost: 70, lvl: 22, req: ['r.lizardman.evo_dragonewt'], cat: `${RC} · Dragão`,
      desc: 'Corpo de dragão: +4 corações e resistência.', fx: { k: 'p_effect', eff: [['health_boost', 1], ['resistance', 0]] } }),
    T.breath('r.lizardman', 'fire', 'Sopro de Fogo', 'Flame Breath', { req: ['r.lizardman.dragon_eye'], lvl: 22 }),
    T.breath('r.lizardman', 'lightning', 'Sopro de Trovão', 'Thunder Breath', { req: ['r.lizardman.dragon_eye'], lvl: 24, dmg: 10 }),
    S('r.lizardman.dragon_change', 'Transformação Dracônica', 'Dragon Change', 'epic', {
      cost: 110, lvl: 28, req: ['r.lizardman.dragon_body'], mp: 60, cd: 120, cat: `${RC} · Dragão`,
      desc: 'Assume a forma dracônica por 30 s: força, resistência, velocidade, imunidade ao fogo e planagem.',
      fx: { k: 'buff', eff: [['strength', 30, 2], ['resistance', 30, 1], ['speed', 30, 1], ['fire_resistance', 30, 0], ['slow_falling', 30, 0]] } }),
    evo('r.lizardman', 'true_dragonewt', 'legendary', { cost: 170, lvl: 40, req: ['r.lizardman.evo_dragonewt'], needCount: 8 }),
  ]),

  // ── ORC ──────────────────────────────────────────────────────
  lineageTree('orc', [
    S('r.orc.starved', 'Fome', 'Starved', 'rare', {
      cost: 30, req: ['r.orc.root'], arm: 'N', initial: 'orc', cat: `${RC} · Fome`,
      desc: 'Devorar fortalece: ao derrotar criaturas você recupera fome e Magicules e ganha força por 10 s.',
      fx: { k: 'p_starved', mp: 8 } }),
    T.physResist('r.orc', { req: ['r.orc.root'], arm: 'S' }),
    T.regeneration('r.orc', { req: ['r.orc.physical_resist'] }),
    S('r.orc.energy_absorption', 'Absorção de Energia', 'Energy Absorption', 'rare', {
      cost: 50, lvl: 12, req: ['r.orc.starved'], origin: A, cat: `${RC} · Fome`,
      desc: 'Seus golpes absorvem 10% do dano causado como vida.', fx: { k: 'p_lifesteal', pct: 0.1 } }),
    evo('r.orc', 'high_orc', 'epic', { cost: 80, lvl: 16, req: ['r.orc.root'], arm: 'E', needCount: 3 }),
    T.physEnhance('r.orc', { req: ['r.orc.evo_high_orc'], lvl: 18, initial: 'high_orc' }),
    S('r.orc.ravenous', 'Voracidade', 'Ravenous', 'epic', {
      cost: 80, lvl: 24, req: ['r.orc.evo_high_orc'], origin: OR, mp: 40, cd: 30, cat: `${RC} · Fome`,
      desc: 'Devora a energia das criaturas ao redor: drena vida de todos num raio de 6 blocos.',
      fx: { k: 'drain', el: 'blood', r: 6, dmg: 6, heal: 0.5, mpGain: 5 } }),
    evo('r.orc', 'orc_disaster', 'legendary', { cost: 160, lvl: 38, req: ['r.orc.evo_high_orc'], needCount: 7 }),
  ]),

  // ── DEMÔNIO ──────────────────────────────────────────────────
  lineageTree('demon', [
    S('r.demon.dark_affinity', 'Magia das Trevas', 'Dark Magic', 'common', {
      cost: 15, req: ['r.demon.root'], arm: 'N', initial: 'lesser_demon', cat: `${RC} · Afinidade`,
      desc: 'Afinidade racial: magias da Árvore das Trevas custam 25% menos e causam 20% mais dano.',
      fx: [{ k: 'p_affinity', tree: 'dark', mult: 0.75 }, { k: 'p_power', el: 'dark', mult: 1.2 }] }),
    S('r.demon.illusion_affinity', 'Magia de Ilusão', 'Illusion Magic', 'common', {
      cost: 20, lvl: 4, req: ['r.demon.dark_affinity'], cat: `${RC} · Afinidade`,
      desc: 'Afinidade racial: magias da Árvore de Ilusão custam 25% menos.',
      fx: { k: 'p_affinity', tree: 'illusion', mult: 0.75 } }),
    T.magicResist('r.demon', { req: ['r.demon.root'], arm: 'S' }),
    S('r.demon.possess', 'Possessão', 'Possess', 'rare', {
      cost: 50, lvl: 12, req: ['r.demon.illusion_affinity'], mp: 30, cd: 30, cat: `${RC} · Demônio`,
      desc: 'Possui a criatura mirada: você fica invisível e drena a vida dela por 8 s.',
      fx: { k: 'possess', range: 12, dur: 8, dps: 3 } }),
    S('r.demon.material_creation', 'Criação de Materiais', 'Material Creation', 'rare', {
      cost: 55, lvl: 14, req: ['r.demon.magic_resist'], mp: 80, cd: 120, cat: `${RC} · Demônio`,
      desc: 'Converte Magicules em matéria: cria minérios (ferro, ouro, esmeralda e raramente diamante).',
      fx: { k: 'create' } }),
    evo('r.demon', 'greater_demon', 'epic', { cost: 80, lvl: 15, req: ['r.demon.root'], arm: 'E', needCount: 3 }),
    evo('r.demon', 'arch_demon', 'legendary', { cost: 150, lvl: 30, req: ['r.demon.evo_greater_demon'], needCount: 6 }),
    S('r.demon.demonic_aura', 'Aura Demoníaca', 'Demonic Aura', 'epic', {
      cost: 90, lvl: 32, req: ['r.demon.evo_arch_demon'], origin: OR, mp: 60, cd: 60, cat: `${RC} · Demônio`,
      desc: 'Aura opressora: criaturas ao redor ficam fracas, lentas e em trevas.',
      fx: { k: 'field', el: 'dark', r: 8, dur: 15, self: true, enemyEff: [['weakness', 2, 1], ['slowness', 2, 1], ['darkness', 2, 0]] } }),
    evo('r.demon', 'demon_peer', 'supreme', { cost: 260, lvl: 50, req: ['r.demon.evo_arch_demon'], needCount: 8, flags: ['killed_wither'] }),
  ]),

  // ── ANJO ─────────────────────────────────────────────────────
  lineageTree('angel', [
    S('r.angel.light_magic', 'Magia da Luz', 'Light Magic', 'common', {
      cost: 15, req: ['r.angel.root'], arm: 'N', initial: 'angel', cat: `${RC} · Luz`,
      desc: 'Afinidade racial: magias sagradas custam 20% menos e causam 15% mais dano.',
      fx: [{ k: 'p_affinity', tree: 'holy', mult: 0.8 }, { k: 'p_power', el: 'holy', mult: 1.15 }] }),
    S('r.angel.holy_protection', 'Proteção Sagrada', 'Holy Protection', 'rare', {
      cost: 40, lvl: 8, req: ['r.angel.root'], arm: 'S', cat: `${RC} · Luz`,
      desc: 'Proteção divina: reduz dano mágico, sombrio e de wither.',
      fx: { k: 'p_resist', groups: { magic: 0.2, dark: 0.4 } } }),
    S('r.angel.purification', 'Purificação', 'Purification', 'rare', {
      cost: 45, lvl: 10, req: ['r.angel.light_magic'], mp: 25, cd: 20, cat: `${RC} · Luz`,
      desc: 'Remove efeitos negativos seus e dos aliados e fere mortos-vivos ao redor.',
      fx: [{ k: 'cleanse', negative: true, allies: 8 }, { k: 'nova', el: 'holy', r: 6, dmg: 8, self: true, undeadOnly: true }] }),
    S('r.angel.heavenly_aura', 'Aura Celestial', 'Heavenly Aura', 'epic', {
      cost: 70, lvl: 16, req: ['r.angel.holy_protection'], mp: 40, cd: 60, cat: `${RC} · Luz`,
      desc: 'Aura que regenera aliados e enfraquece inimigos ao seu redor por 20 s.',
      fx: { k: 'field', el: 'holy', r: 8, dur: 20, self: true, allyEff: [['regeneration', 3, 0]], enemyEff: [['weakness', 2, 0]] } }),
    S('r.angel.celestial_wings', 'Asas Celestiais', 'Celestial Wings', 'epic', {
      cost: 70, lvl: 14, req: ['r.angel.light_magic'], mp: 8, cd: 3, cat: `${RC} · Asas`,
      desc: 'Liga/desliga o voo com asas (mais barato que o Voo universal). Olhe para cima para subir, agache para descer.',
      fx: { k: 'flight', drain: 1.5 } }),
    evo('r.angel', 'arch_angel', 'legendary', { cost: 150, lvl: 30, req: ['r.angel.root'], arm: 'E', excl: ['r.angel.evo_fallen_angel'], needCount: 4 }),
    evo('r.angel', 'fallen_angel', 'legendary', { cost: 150, lvl: 30, req: ['r.angel.root'], excl: ['r.angel.evo_arch_angel'], needCount: 4 }),
    S('r.angel.judgement', 'Julgamento Celestial', 'Celestial Judgement', 'epic', {
      cost: 110, lvl: 34, req: ['r.angel.evo_arch_angel'], origin: OR, mp: 70, cd: 45, cat: `${RC} · Luz`,
      desc: 'Pilares de luz caem sobre a área mirada.', fx: { k: 'rain', el: 'holy', dmg: 12, r: 5, waves: 5, every: 8, range: 26 } }),
    S('r.angel.fallen_wrath', 'Ira do Caído', 'Fallen Wrath', 'epic', {
      cost: 110, lvl: 34, req: ['r.angel.evo_fallen_angel'], origin: OR, mp: 60, cd: 45, cat: `${RC} · Trevas`,
      desc: 'Lâminas sombrias em cone que drenam vida.', fx: { k: 'cone', el: 'dark', dmg: 14, range: 9, eff: [['wither', 5, 1]], lifesteal: 0.3 } }),
  ]),

  // ── DRAGONOIDE ───────────────────────────────────────────────
  lineageTree('dragonoid', [
    S('r.dragonoid.dragon_scales', 'Escamas Dracônicas', 'Dragon Scales', 'rare', {
      cost: 30, req: ['r.dragonoid.root'], arm: 'S', initial: 'dragonoid', cat: `${RC} · Dragão`,
      desc: 'Escamas resistentes: menos dano físico e de fogo.', fx: { k: 'p_resist', groups: { physical: 0.2, fire: 0.3 } } }),
    T.breath('r.dragonoid', 'fire', 'Sopro de Fogo', 'Flame Breath', { req: ['r.dragonoid.root'], arm: 'N', lvl: 6, cost: 35 }),
    S('r.dragonoid.dragon_wings', 'Asas Dracônicas', 'Dragon Wings', 'epic', {
      cost: 70, lvl: 14, req: ['r.dragonoid.fire_breath'], origin: A, mp: 10, cd: 3, cat: `${RC} · Dragão`,
      desc: 'Liga/desliga o voo com asas de dragão.', fx: { k: 'flight', drain: 2 } }),
    S('r.dragonoid.dragon_body', 'Corpo Dracônico', 'Dragon Body', 'epic', {
      cost: 70, lvl: 16, req: ['r.dragonoid.dragon_scales'], cat: `${RC} · Dragão`,
      desc: 'Corpo de dragão: +4 corações e força.', fx: { k: 'p_effect', eff: [['health_boost', 1], ['strength', 0]] } }),
    evo('r.dragonoid', 'elder_dragonoid', 'legendary', { cost: 170, lvl: 35, req: ['r.dragonoid.root'], arm: 'E', needCount: 4 }),
    S('r.dragonoid.dragon_change', 'Transformação Dracônica', 'Dragon Change', 'epic', {
      cost: 110, lvl: 38, req: ['r.dragonoid.evo_elder_dragonoid'], mp: 60, cd: 120, cat: `${RC} · Dragão`,
      desc: 'Forma dracônica por 30 s: força, resistência, velocidade e imunidade ao fogo.',
      fx: { k: 'buff', eff: [['strength', 30, 2], ['resistance', 30, 1], ['speed', 30, 1], ['fire_resistance', 30, 0]] } }),
    S('r.dragonoid.dragon_roar', 'Rugido Dracônico', 'Dragon Roar', 'epic', {
      cost: 90, lvl: 38, req: ['r.dragonoid.evo_elder_dragonoid'], origin: OR, mp: 45, cd: 40, cat: `${RC} · Dragão`,
      desc: 'Rugido que arremessa e aterroriza as criaturas ao redor.',
      fx: { k: 'nova', el: 'dragon', r: 7, dmg: 8, self: true, knock: 2.5, eff: [['weakness', 6, 1]] } }),
  ]),

  // ── VAMPIRO ──────────────────────────────────────────────────
  lineageTree('vampire', [
    S('r.vampire.blood_raise', 'Manipulação de Sangue', 'Blood Raise', 'common', {
      cost: 15, req: ['r.vampire.root'], arm: 'N', initial: 'vampire', mp: 12, cd: 5, cat: `${RC} · Sangue`,
      desc: 'Arranca o sangue do alvo: dano e cura para você.', fx: { k: 'drain', el: 'blood', range: 12, dmg: 6, heal: 0.8, mpGain: 4 } }),
    T.selfRegen('r.vampire', { req: ['r.vampire.root'], arm: 'S' }),
    S('r.vampire.charm', 'Encanto', 'Charm', 'rare', {
      cost: 40, lvl: 10, req: ['r.vampire.blood_raise'], mp: 25, cd: 30, cat: `${RC} · Mente`,
      desc: 'Encanta a criatura mirada: ela para de atacar por 15 s.', fx: { k: 'dominate', range: 14, dur: 15 } }),
    S('r.vampire.paralyze', 'Paralisia', 'Paralyze', 'rare', {
      cost: 45, lvl: 12, req: ['r.vampire.self_regen'], cat: `${RC} · Sangue`,
      desc: 'Seus golpes têm 30% de chance de paralisar o alvo por 2 s.', fx: { k: 'p_onhit', chance: 0.3, eff: [['slowness', 2, 5], ['mining_fatigue', 2, 2]] } }),
    S('r.vampire.shadow_motion', 'Movimento nas Sombras', 'Shadow Motion', 'rare', {
      cost: 40, lvl: 10, req: ['r.vampire.self_regen'], mp: 15, cd: 6, cat: `${RC} · Sombras`,
      desc: 'Desliza pelas sombras até 12 blocos na direção que você olha.', fx: { k: 'blink', range: 12 } }),
    S('r.vampire.transform', 'Transformação', 'Transform', 'epic', {
      cost: 70, lvl: 16, req: ['r.vampire.charm'], mp: 30, cd: 60, cat: `${RC} · Sombras`,
      desc: 'Transforma-se numa nuvem de morcegos por 20 s: invisível, veloz e leve.',
      fx: { k: 'buff', eff: [['invisibility', 20, 0], ['speed', 20, 2], ['slow_falling', 20, 0], ['jump_boost', 20, 1]] } }),
    evo('r.vampire', 'vampire_lord', 'legendary', { cost: 160, lvl: 32, req: ['r.vampire.root'], arm: 'E', needCount: 5 }),
    S('r.vampire.blood_lord', 'Senhor do Sangue', 'Blood Lord', 'epic', {
      cost: 100, lvl: 34, req: ['r.vampire.evo_vampire_lord'], origin: OR, cat: `${RC} · Sangue`,
      desc: 'Seus golpes drenam 20% do dano como vida.', fx: { k: 'p_lifesteal', pct: 0.2 } }),
  ]),

  // ── ELEMENTAL ────────────────────────────────────────────────
  lineageTree('elemental', [
    S('r.elemental.elemental_manipulation', 'Manipulação Elemental', 'Elemental Manipulation', 'rare', {
      cost: 30, req: ['r.elemental.root'], arm: 'N', initial: 'elemental', cat: `${RC} · Elemental`,
      desc: 'Magias elementais 15% mais baratas e 15% mais fortes.',
      fx: [{ k: 'p_affinity', tree: 'elemental', mult: 0.85 }, { k: 'p_power', el: 'elemental', mult: 1.15 }] }),
    S('r.elemental.elemental_projection', 'Projeção Elemental', 'Elemental Projection', 'common', {
      cost: 20, lvl: 4, req: ['r.elemental.elemental_manipulation'], mp: 8, cd: 1, cat: `${RC} · Elemental`,
      desc: 'Projeta energia elemental pura em forma de projétil espiritual.', fx: { k: 'bolt', el: 'spirit', dmg: 6, range: 22, speed: 1.4 } }),
    S('r.elemental.spirit_protection', 'Proteção Espiritual', 'Spirit Protection', 'rare', {
      cost: 40, lvl: 8, req: ['r.elemental.root'], arm: 'S', cat: `${RC} · Espírito`,
      desc: 'Corpo espiritual: reduz dano mágico, de fogo e de gelo.', fx: { k: 'p_resist', groups: { magic: 0.2, fire: 0.2, freezing: 0.2 } } }),
    S('r.elemental.elemental_regeneration', 'Regeneração Elemental', 'Elemental Regeneration', 'rare', {
      cost: 45, lvl: 10, req: ['r.elemental.spirit_protection'], cat: `${RC} · Espírito`,
      desc: 'Regenera rapidamente quando está na água, na chuva ou em chamas.',
      fx: { k: 'p_regen_env', water: [['regeneration', 1]], fire: [['regeneration', 1]] } }),
    evo('r.elemental', 'greater_elemental_race', 'epic', { cost: 110, lvl: 22, req: ['r.elemental.root'], arm: 'E', needCount: 3 }),
    S('r.elemental.elemental_avatar', 'Avatar Elemental', 'Elemental Avatar', 'epic', {
      cost: 110, lvl: 26, req: ['r.elemental.evo_greater_elemental_race'], origin: OR, mp: 60, cd: 90, cat: `${RC} · Elemental`,
      desc: 'Assume a forma elemental pura por 25 s: imune ao fogo, respirando na água, força e golpes flamejantes.',
      fx: [{ k: 'buff', eff: [['fire_resistance', 25, 0], ['water_breathing', 25, 0], ['strength', 25, 1], ['resistance', 25, 1]] }, { k: 'imbue', el: 'fire', dur: 25, dmg: 3, ignite: 3 }] }),
    evo('r.elemental', 'spirit_lord', 'legendary', { cost: 200, lvl: 42, req: ['r.elemental.evo_greater_elemental_race'], needCount: 6 }),
  ]),

  // ── ELFO ─────────────────────────────────────────────────────
  lineageTree('elf', [
    T.spiritAffinity('r.elf', { req: ['r.elf.root'], arm: 'N', initial: 'elf', lvl: 1, cost: 30 }),
    S('r.elf.keen_eyes', 'Olhos Aguçados', 'Keen Eyes', 'common', {
      cost: 15, lvl: 3, req: ['r.elf.root'], arm: 'S', origin: OR, cat: `${RC} · Sentidos`,
      desc: 'Visão noturna e flechas mais precisas: +20% de dano com projéteis.', fx: [{ k: 'p_effect', eff: [['night_vision', 0]] }, { k: 'p_power', el: 'projectile', mult: 1.2 }] }),
    S('r.elf.forest_step', 'Passo da Floresta', 'Forest Step', 'common', {
      cost: 20, lvl: 5, req: ['r.elf.keen_eyes'], origin: OR, cat: `${RC} · Natureza`,
      desc: 'Velocidade extra e salto aprimorado permanentes.', fx: { k: 'p_effect', eff: [['jump_boost', 0], ['speed', 0]] } }),
    T.magiculeBoost('r.elf', 'Fonte de Mana Élfica', 'Elven Mana Spring', { req: ['r.elf.spirit_affinity'], origin: OR, max: 60, regen: 1 }),
    evo('r.elf', 'high_elf', 'legendary', { cost: 140, lvl: 30, req: ['r.elf.root'], arm: 'E', needCount: 4 }),
    S('r.elf.spirit_call', 'Chamado dos Espíritos', 'Spirit Call', 'epic', {
      cost: 90, lvl: 32, req: ['r.elf.evo_high_elf'], origin: OR, mp: 40, cd: 90, cat: `${RC} · Espírito`,
      desc: 'Invoca dois espíritos curandeiros por 90 s.', fx: { k: 'summon', entity: 'tensura:spirit', count: 2, dur: 90 } }),
  ]),

  // ── ANÃO ─────────────────────────────────────────────────────
  lineageTree('dwarf', [
    S('r.dwarf.master_smith', 'Mestre Ferreiro', 'Master Smith', 'rare', {
      cost: 30, req: ['r.dwarf.root'], arm: 'N', initial: 'dwarf', origin: OR, mp: 40, cd: 240, cat: `${RC} · Forja`,
      desc: 'Repara completamente o item da mão principal.', fx: { k: 'repair' } }),
    S('r.dwarf.stone_body', 'Corpo de Pedra', 'Stone Body', 'rare', {
      cost: 35, lvl: 6, req: ['r.dwarf.root'], arm: 'S', origin: OR, cat: `${RC} · Corpo`,
      desc: 'Resistência a explosões e dano físico.', fx: { k: 'p_resist', groups: { explosion: 0.35, physical: 0.15 } } }),
    S('r.dwarf.ore_sense', 'Faro de Minério', 'Ore Sense', 'common', {
      cost: 20, lvl: 4, req: ['r.dwarf.master_smith'], origin: OR, mp: 10, cd: 20, cat: `${RC} · Mineração`,
      desc: 'Detecta minérios valiosos num raio de 8 blocos e mostra quantidade e direção.', fx: { k: 'ore_sense', r: 8 } }),
    S('r.dwarf.material_creation', 'Criação de Materiais', 'Material Creation', 'rare', {
      cost: 55, lvl: 14, req: ['r.dwarf.ore_sense'], mp: 80, cd: 120, cat: `${RC} · Forja`,
      desc: 'Converte Magicules em minérios (ferro, ouro, esmeralda e raramente diamante).', fx: { k: 'create' } }),
    evo('r.dwarf', 'elder_dwarf', 'legendary', { cost: 130, lvl: 30, req: ['r.dwarf.root'], arm: 'E', needCount: 4 }),
    S('r.dwarf.earthshaker', 'Tremor de Terra', 'Earthshaker', 'epic', {
      cost: 90, lvl: 32, req: ['r.dwarf.evo_elder_dwarf'], origin: OR, mp: 50, cd: 40, cat: `${RC} · Terra`,
      desc: 'Golpeia o chão: dano e lentidão em todos ao redor.', fx: { k: 'nova', el: 'earth', r: 6, dmg: 12, self: true, eff: [['slowness', 4, 2]] } }),
  ]),

  // ── DRÍADE ───────────────────────────────────────────────────
  lineageTree('dryad', [
    S('r.dryad.nature_heal', 'Cura Natural', 'Nature Heal', 'common', {
      cost: 15, req: ['r.dryad.root'], arm: 'N', initial: 'dryad', origin: A, mp: 10, cd: 8, cat: `${RC} · Natureza`,
      desc: 'Cura você e aliados próximos com a força das plantas.', fx: { k: 'heal', amount: 6, allies: 6 } }),
    S('r.dryad.root_bind', 'Raízes', 'Root Bind', 'common', {
      cost: 25, lvl: 5, req: ['r.dryad.root'], arm: 'S', origin: A, mp: 15, cd: 10, cat: `${RC} · Natureza`,
      desc: 'Raízes prendem o alvo mirado.', fx: { k: 'bind', el: 'earth', range: 16, dur: 5, dps: 1 } }),
    T.spiritAffinity('r.dryad', { req: ['r.dryad.nature_heal'] }),
    S('r.dryad.bark_skin', 'Pele de Casca', 'Bark Skin', 'rare', {
      cost: 40, lvl: 10, req: ['r.dryad.root_bind'], origin: OR, cat: `${RC} · Corpo`,
      desc: 'Pele dura como madeira: resistência física.', fx: { k: 'p_resist', groups: { physical: 0.25 } } }),
    evo('r.dryad', 'spirit_dryad', 'legendary', { cost: 140, lvl: 30, req: ['r.dryad.root'], arm: 'E', needCount: 4 }),
    S('r.dryad.forest_sanctuary', 'Santuário da Floresta', 'Forest Sanctuary', 'epic', {
      cost: 100, lvl: 32, req: ['r.dryad.evo_spirit_dryad'], origin: OR, mp: 60, cd: 90, cat: `${RC} · Natureza`,
      desc: 'Campo que regenera aliados e prende inimigos com raízes por 20 s.',
      fx: { k: 'field', el: 'spirit', r: 8, dur: 20, self: false, allyEff: [['regeneration', 3, 1]], enemyEff: [['slowness', 2, 3]] } }),
  ]),

  // ── DIREWOLF ─────────────────────────────────────────────────
  lineageTree('direwolf', [
    T.ultraSmell('r.direwolf', { req: ['r.direwolf.root'], arm: 'S', initial: 'direwolf' }),
    T.thoughtComm('r.direwolf', { req: ['r.direwolf.root'], arm: 'N' }),
    S('r.direwolf.fang', 'Presa Feroz', 'Savage Fang', 'common', {
      cost: 20, lvl: 4, req: ['r.direwolf.ultra_smell'], origin: OR, cat: `${RC} · Caça`,
      desc: 'Mordidas brutais: seus golpes têm 25% de chance de causar sangramento (wither) por 3 s.', fx: { k: 'p_onhit', chance: 0.25, eff: [['wither', 3, 0]] } }),
    T.coercion('r.direwolf', { req: ['r.direwolf.thought_comm'] }),
    evo('r.direwolf', 'tempest_wolf', 'epic', { cost: 90, lvl: 18, req: ['r.direwolf.root'], arm: 'E', needCount: 3 }),
    S('r.direwolf.storm_dash', 'Investida da Tempestade', 'Storm Dash', 'rare', {
      cost: 50, lvl: 20, req: ['r.direwolf.evo_tempest_wolf'], initial: 'tempest_wolf', origin: OR, mp: 15, cd: 5, cat: `${RC} · Tempestade`,
      desc: 'Avança 10 blocos como um raio, ferindo quem estiver no caminho.', fx: [{ k: 'beam', el: 'lightning', dmg: 6, range: 10, pierce: true }, { k: 'blink', range: 10 }] }),
    S('r.direwolf.black_lightning', 'Relâmpago Negro', 'Black Lightning', 'epic', {
      cost: 100, lvl: 28, req: ['r.direwolf.evo_tempest_wolf'], mp: 60, cd: 30, cat: `${RC} · Tempestade`,
      desc: 'Relâmpagos negros caem sobre a área mirada.', fx: { k: 'rain', el: 'lightning', dmg: 12, r: 5, waves: 4, every: 6, range: 24, lightning: true } }),
    evo('r.direwolf', 'tempest_star_wolf', 'legendary', { cost: 180, lvl: 40, req: ['r.direwolf.evo_tempest_wolf'], needCount: 6 }),
  ]),

  // ── HARPIA ───────────────────────────────────────────────────
  lineageTree('harpy', [
    S('r.harpy.wings', 'Asas', 'Wings', 'rare', {
      cost: 30, req: ['r.harpy.root'], arm: 'N', initial: 'harpy', origin: A, mp: 6, cd: 3, cat: `${RC} · Asas`,
      desc: 'Liga/desliga o voo com suas asas. Olhe para cima para subir, agache para descer.', fx: { k: 'flight', drain: 1 } }),
    S('r.harpy.talon', 'Garras', 'Talons', 'common', {
      cost: 20, lvl: 4, req: ['r.harpy.root'], arm: 'S', origin: OR, cat: `${RC} · Caça`,
      desc: 'Seus golpes têm 25% de chance de rasgar (lentidão) o alvo.', fx: { k: 'p_onhit', chance: 0.25, eff: [['slowness', 3, 1]] } }),
    S('r.harpy.gale', 'Vendaval', 'Gale', 'rare', {
      cost: 40, lvl: 10, req: ['r.harpy.wings'], origin: A, mp: 15, cd: 6, cat: `${RC} · Vento`,
      desc: 'Bate as asas criando um vendaval em cone.', fx: { k: 'cone', el: 'wind', dmg: 4, range: 9, knock: 2.5, lift: 0.6 } }),
    evo('r.harpy', 'harpy_queen', 'legendary', { cost: 140, lvl: 30, req: ['r.harpy.root'], arm: 'E', needCount: 3 }),
    S('r.harpy.sky_dominion', 'Domínio dos Céus', 'Sky Dominion', 'epic', {
      cost: 100, lvl: 32, req: ['r.harpy.evo_harpy_queen'], origin: OR, mp: 70, cd: 45, cat: `${RC} · Vento`,
      desc: 'Invoca uma tempestade de penas cortantes sobre a área.', fx: { k: 'rain', el: 'wind', dmg: 10, r: 6, waves: 5, every: 8, range: 26 } }),
  ]),

  // ── MERFOLK ──────────────────────────────────────────────────
  lineageTree('merfolk', [
    S('r.merfolk.tide_heal', 'Maré Curativa', 'Tidal Heal', 'common', {
      cost: 15, req: ['r.merfolk.root'], arm: 'N', initial: 'merfolk', origin: OR, cat: `${RC} · Água`,
      desc: 'Regenera vida e ganha velocidade enquanto está na água.', fx: { k: 'p_regen_env', water: [['regeneration', 1], ['speed', 1]] } }),
    S('r.merfolk.water_lance', 'Lança das Marés', 'Tidal Lance', 'common', {
      cost: 25, lvl: 4, req: ['r.merfolk.root'], arm: 'S', origin: OR, mp: 10, cd: 2, cat: `${RC} · Água`,
      desc: 'Lança de água perfurante.', fx: { k: 'beam', el: 'water', dmg: 8, range: 18, pierce: true } }),
    S('r.merfolk.siren_song', 'Canto da Sereia', 'Siren Song', 'rare', {
      cost: 45, lvl: 10, req: ['r.merfolk.tide_heal'], origin: A, mp: 25, cd: 30, cat: `${RC} · Mente`,
      desc: 'Canto hipnótico que adormece as criaturas próximas.', fx: { k: 'debuff', el: 'water', r: 7, self: true, eff: [['slowness', 6, 255], ['weakness', 6, 255]] } }),
    evo('r.merfolk', 'merfolk_lord', 'legendary', { cost: 140, lvl: 30, req: ['r.merfolk.root'], arm: 'E', needCount: 3 }),
    S('r.merfolk.maelstrom', 'Redemoinho', 'Maelstrom', 'epic', {
      cost: 100, lvl: 32, req: ['r.merfolk.evo_merfolk_lord'], origin: OR, mp: 60, cd: 40, cat: `${RC} · Água`,
      desc: 'Redemoinho que fere e puxa as criaturas para o centro da área mirada.', fx: { k: 'gravity', el: 'water', range: 20, r: 6, dmg: 10, pull: true } }),
  ]),

  // ── GHOUL ────────────────────────────────────────────────────
  lineageTree('ghoul', [
    S('r.ghoul.devour', 'Devorar', 'Devour', 'common', {
      cost: 15, req: ['r.ghoul.root'], arm: 'N', initial: 'ghoul', origin: A, cat: `${RC} · Fome`,
      desc: 'Ao derrotar criaturas você recupera fome e Magicules.', fx: { k: 'p_starved', mp: 5, noStrength: true } }),
    S('r.ghoul.undead_body', 'Corpo Morto-vivo', 'Undead Body', 'rare', {
      cost: 35, lvl: 5, req: ['r.ghoul.root'], arm: 'S', origin: A, cat: `${RC} · Corpo`,
      desc: 'Imune a veneno (é removido automaticamente) e resistente a wither.', fx: [{ k: 'p_resist', groups: { dark: 0.5 } }, { k: 'p_race', poisonImmune: true }] }),
    S('r.ghoul.rotting_claw', 'Garra Pútrida', 'Rotting Claw', 'common', {
      cost: 25, lvl: 6, req: ['r.ghoul.devour'], origin: OR, cat: `${RC} · Fome`,
      desc: 'Seus golpes têm 30% de chance de envenenar o alvo.', fx: { k: 'p_onhit', chance: 0.3, eff: [['poison', 4, 1], ['hunger', 6, 1]] } }),
    evo('r.ghoul', 'ghoul_lord', 'legendary', { cost: 130, lvl: 28, req: ['r.ghoul.root'], arm: 'E', needCount: 3 }),
    S('r.ghoul.raise_dead', 'Erguer os Mortos', 'Raise Dead', 'epic', {
      cost: 90, lvl: 30, req: ['r.ghoul.evo_ghoul_lord'], origin: OR, mp: 40, cd: 60, cat: `${RC} · Necromancia`,
      desc: 'Ergue três mortos-vivos aliados por 60 s.', fx: { k: 'summon', entity: 'tensura:undead_warrior', count: 3, dur: 60 } }),
  ]),

  // ── GIGANTE ──────────────────────────────────────────────────
  lineageTree('giant', [
    S('r.giant.titan_strength', 'Força Titânica', 'Titan Strength', 'rare', {
      cost: 30, req: ['r.giant.root'], arm: 'N', initial: 'giant', origin: OR, cat: `${RC} · Corpo`,
      desc: 'Seus golpes arremessam os inimigos para longe.', fx: { k: 'p_onhit', chance: 1, knock: 1.2 } }),
    T.physResist('r.giant', { req: ['r.giant.root'], arm: 'S' }),
    S('r.giant.ground_slam', 'Pisão', 'Ground Slam', 'rare', {
      cost: 45, lvl: 10, req: ['r.giant.titan_strength'], origin: OR, mp: 25, cd: 15, cat: `${RC} · Terra`,
      desc: 'Pisa o chão com força: dano e lentidão ao redor.', fx: { k: 'nova', el: 'earth', r: 5, dmg: 9, self: true, eff: [['slowness', 3, 2]], knock: 1 } }),
    evo('r.giant', 'elder_giant', 'legendary', { cost: 140, lvl: 30, req: ['r.giant.root'], arm: 'E', needCount: 3 }),
    S('r.giant.mountain_heart', 'Coração da Montanha', 'Mountain Heart', 'epic', {
      cost: 90, lvl: 32, req: ['r.giant.evo_elder_giant'], origin: OR, cat: `${RC} · Corpo`,
      desc: '+4 corações e regeneração permanentes.', fx: { k: 'p_effect', eff: [['health_boost', 1], ['regeneration', 0]] } }),
  ]),
];
