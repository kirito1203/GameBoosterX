// SETOR VII — HABILIDADES SUPREMAS: URIEL, VELDORA e VELGRYND.
// Raphael (setor IV) e Beelzebuth (setor III) também são Supremas (tier 'ultimate', moldura dourada) e
// ficam nos seus ramos para não duplicar IDs; a Árvore Suprema mostra todas juntas.
import { K, P } from './common.js';

const k = K('supreme');

export default [
  // ── Uriel — Lorde dos Votos ──
  k('infinity_prison', 'Prisão Infinita', 'Infinity Prison', 'S', 'ln', {
    req: [P('raphael')], lp: P('root'), mp: 80, cd: 120, major: true, tier: 'unique', tags: ['space'],
    desc: 'Aprisiona o alvo mirado (24 blocos) por 12 s. Base da evolução para Uriel.',
    fx: { k: 'bind', el: 'space', range: 24, dur: 12, dps: 0, eff: [['slowness', 12, 6], ['weakness', 12, 3]] },
    evolvedBy: P('uriel'), evolvedDesc: 'Com Uriel: alcance 32 e 20 s.',
    evolvedFx: { k: 'bind', el: 'space', range: 32, dur: 20, dps: 0, eff: [['slowness', 20, 6], ['weakness', 20, 3]] } }),
  k('uriel', 'Uriel — Lorde dos Votos', 'Uriel, Lord of Vows', 'SS', 'ln', {
    req: [P('infinity_prison'), P('beelzebuth')], lp: P('infinity_prison'), needMp: 1200, needStage: 1, mp: 120, cd: 180, major: true,
    tier: 'ultimate', mission: 'm.slime.uriel_vow', needMasteryTag: { defense: 3, space: 3 },
    subs: [P('infinity_prison'), P('law_manip'), P('spatial_domination'), P('universal_barrier'), P('absolute_defense')],
    lore: 'Relação histórica com Raguel e com Shub-Niggurath: A VERIFICAR (não confirmada nesta versão). O addon NÃO aplica evolução direta Uriel → Shub-Niggurath.',
    limits: 'Defesa em área por tempo limitado (não é invulnerabilidade permanente).',
    desc: 'Senhor dos Votos: defesa absoluta por 20 s para você e aliados (10 blocos).',
    fx: { k: 'buff', allies: 10, eff: [['absorption', 20, 5], ['resistance', 20, 3], ['fire_resistance', 20, 0]] } }),
  k('spatial_domination', 'Dominação Espacial', 'Spatial Domination', 'A', 'ln', {
    req: [P('uriel')], mp: 90, cd: 120, tags: ['space'], desc: 'Domina o espaço (raio 10) por 15 s: inimigos não saem e ficam lentos.',
    fx: { k: 'field', el: 'space', r: 10, dur: 15, self: true, confine: true, enemyEff: [['slowness', 2, 2]] } }),
  k('absolute_defense', 'Defesa Absoluta', 'Absolute Defense', 'S', 'ln', {
    req: [P('uriel')], mp: 100, cd: 150, tags: ['defense'], desc: 'Por 6 s você praticamente não recebe dano (Resistência V).',
    fx: { k: 'buff', eff: [['resistance', 6, 4]] } }),
  k('law_manip', 'Manipulação de Leis', 'Law Manipulation', 'S', 'ln', {
    req: [P('uriel')], mp: 110, cd: 150, desc: 'Reescreve as leis ao redor (raio 12, 8 s): inimigos fracos, lentos e cansados.',
    fx: { k: 'field', el: 'space', r: 12, dur: 8, self: true, enemyEff: [['weakness', 2, 2], ['slowness', 2, 2], ['mining_fatigue', 2, 2]] } }),
  k('universal_barrier', 'Barreira Universal', 'Universal Barrier', 'S', 'ln', {
    req: [P('uriel'), P('multilayer_barrier')], lp: P('uriel'), unverified: true, mp: 140, cd: 200, tags: ['defense'],
    desc: 'Barreira para você e aliados (12 blocos) por 15 s: Resistência IV, absorção e imunidade a fogo.',
    limits: 'Duração e raio limitados; não bloqueia comandos nem dano de /kill.',
    fx: { k: 'buff', allies: 12, eff: [['resistance', 15, 3], ['absorption', 15, 6], ['fire_resistance', 15, 0]] } }),

  // ── Veldora — Lorde da Tempestade ──
  k('storm_bond', 'Vínculo com Veldora', 'Bond with Veldora', 'A', 'anime', {
    req: [P('stomach')], lp: P('root'), lvl: 20, major: true,
    desc: 'Veldora selado no seu Estômago: magias de raio e vento 20% mais fortes e +80 Magicules máximas.',
    fx: [{ k: 'p_power', el: 'lightning', mult: 1.2 }, { k: 'p_power', el: 'wind', mult: 1.2 }, { k: 'p_mp', max: 80 }] }),
  k('veldora', 'Veldora — Lorde da Tempestade', 'Veldora, Lord of Storms', 'SS', 'ln', {
    req: [P('storm_bond'), P('raphael')], lp: P('storm_bond'), mission: 'm.slime.veldora', mp: 140, cd: 120, major: true, tier: 'ultimate',
    subs: [P('true_dragon_release'), P('storm_restoration'), P('storm_dragon'), P('soul_corridor')],
    limits: 'A "manifestação" de Veldora é uma invocação temporária (60 s, 1 por jogador).',
    desc: 'Senhor da Tempestade: tempestade de raios sobre a área mirada (5 ondas, 16 de dano).',
    fx: { k: 'rain', el: 'lightning', dmg: 16, r: 6, waves: 5, every: 8, range: 32 } }),
  k('soul_corridor', 'Corredor de Almas', 'Soul Corridor', 'A', 'ln', {
    req: [P('veldora')], mp: 40, cd: 30, tags: ['space'], desc: 'Agachado: marca um ponto. Em pé: volta a ele por qualquer distância/dimensão.', fx: { k: 'warp' } }),
  k('storm_dragon', 'Manifestação do Dragão da Tempestade', 'Storm Dragon Manifestation', 'S', 'ln', {
    req: [P('veldora')], unverified: true, mp: 160, cd: 240, desc: 'Manifesta o Dragão da Tempestade, que luta ao seu lado por 60 s.',
    limits: 'Forma reduzida de dragão (modelo do addon); some sozinha após 60 s.',
    fx: { k: 'summon', entity: 'tensura:storm_dragon', count: 1, dur: 60 } }),
  k('true_dragon_release', 'Liberação do Dragão da Tempestade', 'Storm Dragon Release', 'SS', 'ln', {
    req: [P('storm_dragon')], mp: 250, cd: 300, desc: 'Liberta o poder de Veldora: explosão de raios (raio 8, 20 de dano) e força/velocidade/resistência por 40 s.',
    fx: [{ k: 'nova', el: 'lightning', r: 8, dmg: 20, self: true, knock: 2 }, { k: 'buff', eff: [['strength', 40, 3], ['speed', 40, 2], ['resistance', 40, 2]] }] }),
  k('storm_restoration', 'Restauração do Dragão da Tempestade', 'Storm Dragon Restoration', 'SS', 'ln', {
    req: [P('veldora')], unverified: true, mp: 0, cd: 600,
    desc: 'Restaura o corpo com a essência de Veldora: vida cheia, 50% das Magicules e remove efeitos negativos.',
    limits: 'Recarga de 10 minutos.', fx: [{ k: 'heal', full: true, amount: 40 }, { k: 'mana', pct: 0.5 }, { k: 'cleanse', negative: true }] }),

  // ── Velgrynd — Lorde da Chama ──
  k('velgrynd', 'Velgrynd — Lorde da Chama', 'Velgrynd, Lord of Flame', 'SS', 'ln', {
    req: [P('raphael'), P('fire_null')], lp: P('root'), unverified: true, mission: 'm.slime.velgrynd', mp: 150, cd: 120, major: true, tier: 'ultimate',
    subs: [P('scorch_release'), P('scorch_restoration'), P('scorch_dragon'), P('scorch_breath')],
    lore: 'Velgrynd é a Dragão Abrasadora, irmã de Veldora: são habilidades diferentes.',
    limits: 'A "manifestação" é uma invocação temporária (60 s, 1 por jogador).',
    desc: 'Lorde da Chama: chuva de chamas abrasadoras sobre a área mirada (5 ondas, 16 de dano, queima).',
    fx: { k: 'rain', el: 'fire', dmg: 16, r: 6, waves: 5, every: 8, range: 32, ignite: 6 } }),
  k('scorch_breath', 'Calor Abrasador', 'Scorching Heat', 'A', 'ln', {
    req: [P('velgrynd')], unverified: true, mp: 60, cd: 15, desc: 'Cone de calor extremo (12 blocos, 18 de dano, queima por 8 s).',
    fx: { k: 'cone', el: 'fire', dmg: 18, range: 12, ignite: 8 } }),
  k('scorch_dragon', 'Manifestação do Dragão Abrasador', 'Scorch Dragon Manifestation', 'S', 'ln', {
    req: [P('velgrynd')], unverified: true, mp: 160, cd: 240, desc: 'Manifesta o Dragão Abrasador, que luta ao seu lado por 60 s.',
    limits: 'Forma reduzida de dragão (modelo do addon); some sozinha após 60 s.',
    fx: { k: 'summon', entity: 'tensura:scorch_dragon', count: 1, dur: 60 } }),
  k('scorch_release', 'Liberação do Dragão Abrasador', 'Scorch Dragon Release', 'SS', 'ln', {
    req: [P('scorch_dragon')], unverified: true, mp: 250, cd: 300,
    desc: 'Liberta o poder de Velgrynd: explosão de chamas (raio 8, 20 de dano, queima) e força/pressa/imunidade ao fogo por 40 s.',
    fx: [{ k: 'nova', el: 'fire', r: 8, dmg: 20, self: true, knock: 2, ignite: 6 }, { k: 'buff', eff: [['strength', 40, 3], ['haste', 40, 2], ['fire_resistance', 40, 0]] }] }),
  k('scorch_restoration', 'Restauração do Dragão Abrasador', 'Scorch Dragon Restoration', 'SS', 'ln', {
    req: [P('velgrynd')], unverified: true, mp: 0, cd: 600,
    desc: 'Restaura o corpo com a essência de Velgrynd: vida cheia, 50% das Magicules e imunidade ao fogo por 60 s.',
    limits: 'Recarga de 10 minutos.', fx: [{ k: 'heal', full: true, amount: 40 }, { k: 'mana', pct: 0.5 }, { k: 'buff', eff: [['fire_resistance', 60, 0]] }] }),
];
