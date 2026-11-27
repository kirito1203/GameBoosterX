import { S, defineTree } from '../dsl.js';

const O = 'adaptado';
const C = 'Magia Avançada';

// Magias de altíssimo nível: exigem nível alto, magias de outras árvores e,
// em alguns casos, conquistas (derrotar o Wither ou o Ender Dragon).
export default defineTree('advanced', {
  name: 'Árvore Avançada', original: 'Advanced Magic', short: 'Avanç.', kind: 'universal',
  root: 'adv.primitive_magic', category: C,
}, [
  S('adv.primitive_magic', 'Magia Primitiva', 'Primitive Magic', 'legendary', {
    cost: 120, lvl: 30, origin: O, major: true, needEpic: 3, cat: C,
    desc: 'Domínio da magia em sua forma primordial: todas as magias custam 10% menos e ficam 15% mais fortes. Exige 3 habilidades épicas ou superiores.',
    fx: [{ k: 'p_affinity', tree: '*', mult: 0.9 }, { k: 'p_power', el: '*', mult: 1.15 }],
  }),
  S('adv.physics_magic', 'Magia Física', 'Physics Magic', 'legendary', {
    cost: 140, lvl: 34, req: ['adv.primitive_magic', 'elem.explosion'], arm: 'E', origin: O, mp: 90, cd: 40, cat: C,
    desc: 'Manipula diretamente a energia cinética: onda de impacto que esmaga e arremessa.',
    fx: { k: 'nova', el: 'physical', r: 6, dmg: 12, self: true, knock: 3 },
  }),
  S('adv.gravity_magic', 'Magia Gravitacional', 'Gravity Magic', 'legendary', {
    cost: 160, lvl: 38, req: ['adv.physics_magic'], origin: O, mp: 110, cd: 60, cat: C,
    desc: 'Ergue as criaturas na área mirada e as esmaga contra o chão.',
    fx: { k: 'gravity', el: 'gravity', range: 24, r: 7, dmg: 18 },
  }),
  S('adv.nuclear_flame', 'Chama Nuclear', 'Nuclear Flame', 'legendary', {
    cost: 220, lvl: 45, req: ['adv.physics_magic', 'elem.firestorm'], origin: O, mp: 180, cd: 120, cat: C,
    desc: 'Chama de temperatura extrema que detona com enorme poder (não destrói blocos por padrão).',
    fx: { k: 'bolt', el: 'nuclear', dmg: 35, range: 32, speed: 1.0, r: 5, explode: 4, ignite: 8 },
  }),
  S('adv.nuclear_cannon', 'Canhão Nuclear', 'Nuclear Cannon', 'supreme', {
    cost: 260, lvl: 52, req: ['adv.nuclear_flame'], flags: ['killed_wither'], origin: O, major: true, mp: 240, cd: 240, cat: C,
    desc: 'Feixe nuclear que atravessa tudo e detona ao longo do caminho. Exige ter derrotado o Wither.',
    fx: { k: 'beam', el: 'nuclear', dmg: 60, range: 48, pierce: true, explodeEvery: 8, explode: 3 },
  }),
  S('adv.space_magic', 'Magia Espacial', 'Space Magic', 'legendary', {
    cost: 160, lvl: 38, req: ['adv.primitive_magic', 'aspect.warp_portal'], arm: 'N', origin: O, mp: 80, cd: 20, cat: C,
    desc: 'Dobra o espaço: surge atrás do alvo mirado e o golpeia.',
    fx: { k: 'strike', el: 'space', range: 32, dmg: 15 },
  }),
  S('adv.time_magic', 'Magia Temporal', 'Time Magic', 'supreme', {
    cost: 280, lvl: 50, req: ['adv.space_magic', 'adv.gravity_magic'], origin: O, major: true, mp: 220, cd: 300, cat: C,
    desc: 'Congela o tempo ao seu redor por 6 s: tudo para, exceto você.',
    fx: [{ k: 'field', el: 'space', r: 12, dur: 6, self: true, enemyEff: [['slowness', 1, 255], ['weakness', 1, 255], ['mining_fatigue', 1, 4]], freeze: true },
      { k: 'buff', eff: [['speed', 6, 2], ['haste', 6, 2]] }],
  }),
  S('adv.isolated_battlefield', 'Campo de Batalha Isolado', 'Isolated Battlefield', 'legendary', {
    cost: 200, lvl: 44, req: ['adv.space_magic', 'aspect.anti_magic_area'], origin: O, mp: 160, cd: 300, cat: C,
    desc: 'Isola um espaço por 30 s: inimigos não conseguem sair e outros jogadores não conjuram dentro dele.',
    fx: { k: 'field', el: 'space', r: 10, dur: 30, self: false, antimagic: true, confine: true },
  }),
  S('adv.soul_magic', 'Magia das Almas', 'Soul Magic', 'legendary', {
    cost: 180, lvl: 40, req: ['adv.primitive_magic', 'dark.locking_curse'], arm: 'S', origin: O, mp: 120, cd: 60, cat: C,
    desc: 'Arranca a energia das almas ao redor: drena vida e Magicules de todos os inimigos próximos.',
    fx: { k: 'drain', el: 'soul', r: 8, dmg: 10, heal: 0.5, mpGain: 15 },
  }),
  S('adv.marionette_heart', 'Coração de Marionete', 'Marionette Heart', 'legendary', {
    cost: 200, lvl: 44, req: ['adv.soul_magic', 'aspect.demon_marionette'], origin: O, mp: 150, cd: 180, cat: C,
    desc: 'Transforma até 3 criaturas enfraquecidas (até 50% de vida) ao redor em marionetes aliadas por 120 s.',
    fx: { k: 'marionette', r: 8, hpPct: 0.5, dur: 120, count: 3 },
  }),
  S('adv.life_exorcising_wind', 'Vento Exorcizador da Vida', 'Life-Exorcising Wind', 'supreme', {
    cost: 270, lvl: 50, req: ['adv.soul_magic', 'elem.tornado_blade'], origin: O, mp: 200, cd: 240, cat: C,
    desc: 'Vento que arranca a vida: dano massivo em cone e morte instantânea de criaturas fracas (até 20 de vida máx., exceto chefes).',
    fx: { k: 'cone', el: 'soul', dmg: 25, range: 12, knock: 1, eff: [['wither', 6, 3]], execute: 20 },
  }),
  S('adv.true_dragon_magic', 'Magia dos Dragões Verdadeiros', 'True Dragon Magic', 'supreme', {
    cost: 300, lvl: 55, req: ['adv.soul_magic'], arm: 'W', flags: ['killed_ender_dragon'], origin: O, major: true,
    mp: 250, cd: 300, cat: C,
    desc: 'O poder dos Dragões Verdadeiros: sopro dracônico devastador. Exige ter derrotado o Ender Dragon.',
    fx: { k: 'beam', el: 'dragon', dmg: 45, range: 40, pierce: true, ignite: 6, width: 1.5 },
  }),
]);
