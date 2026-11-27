// SETOR I — CORPO E NATUREZA DO SLIME
import { T, evo } from '../../raceDsl.js';
import { K, P, SECTORS } from './common.js';

const k = K('body');
const sec = { sector: 'body', cat: `Racial · ${SECTORS.body}` };

export default [
  k('absorb', 'Absorver', 'Absorb', 'F', 'anime', {
    req: [P('root')], lp: P('root'), initial: 'slime', mp: 10, cd: 4, cost: 10, lvl: 1, major: true, masteryMax: 5,
    desc: 'Absorve uma criatura enfraquecida (até 35% de vida): ela é devorada, você ganha +2 Magicules máximas permanentes (até +200), registra a espécie e pode DESCOBRIR habilidades dela.',
    masteryDesc: ['Absorção básica', 'Custo e recarga menores', 'Mais eficiente', 'Mais eficiente', 'Domínio completo'],
    fx: { k: 'absorb', range: 6, hpPct: 0.35, mpGain: 2, mpCap: 200 } }),
  k('dissolve', 'Dissolver', 'Dissolve', 'F', 'anime', {
    req: [P('root')], initial: 'slime', mp: 6, cd: 1, cost: 15, lvl: 1,
    desc: 'Dissolve o bloco mirado (até 5 blocos, exceto blocos indestrutíveis) ou corrói a criatura mirada com ácido.',
    fx: { k: 'dissolve', range: 5, dmg: 6, eff: [['poison', 4, 1]] } }),
  { ...T.selfRegen('r.slime', { req: ['r.slime.absorb'], initial: 'slime' }), rank: 'E', src: 'anime', ...sec },
  k('ultraspeed_regen', 'Regeneração Acelerada', 'Ultraspeed Regeneration', 'C', 'anime', {
    req: [P('self_regen')], lvl: 18,
    desc: 'Evolução da Autorregeneração: regeneração contínua nível II.', fx: { k: 'p_effect', eff: [['regeneration', 1]] } }),
  k('infinite_regen', 'Regeneração Infinita', 'Infinite Regeneration', 'A', 'ln', {
    req: [P('ultraspeed_regen')], needStage: 1, needMp: 600,
    desc: 'O corpo de Magicules se recompõe sem parar: regeneração nível III e resistência física extra.',
    fx: [{ k: 'p_effect', eff: [['regeneration', 2]] }, { k: 'p_resist', groups: { physical: 0.1 } }] }),
  k('magic_sense', 'Percepção Mágica', 'Magic Sense', 'E', 'anime', {
    req: [P('dissolve')], mp: 3, cd: 8,
    desc: 'Percebe o mundo pelas Magicules: mostra as criaturas num raio de 24 blocos.', fx: { k: 'sense', r: 24 } }),
  k('universal_perception', 'Percepção Universal', 'Universal Detection', 'B', 'ln', {
    req: [P('magic_sense')], mp: 15, cd: 15, needMp: 300,
    desc: 'Evolução da Percepção Mágica: percebe tudo num raio de 64 blocos e enxerga no escuro.',
    fx: [{ k: 'sense', r: 64 }, { k: 'buff', eff: [['night_vision', 60, 0]] }] }),
  k('human_form', 'Metamorfose (Forma Humana)', 'Human Transformation', 'D', 'anime', {
    req: [P('absorb')], mission: 'm.slime.shizu', mp: 15, cd: 70,
    desc: 'Assume a forma humana herdada de Shizu por 60 s: força, pressa e salto.',
    fx: { k: 'buff', eff: [['strength', 60, 0], ['haste', 60, 1], ['jump_boost', 60, 0]] } }),
  k('universal_shapeshift', 'Transformação Universal', 'Universal Shapeshift', 'A', 'ln', {
    req: [P('human_form'), P('shapeshift')], lp: P('human_form'), needStage: 1,
    desc: 'Domínio total da forma: as formas da Imitação ficam um nível mais fortes e não consomem Magicules.',
    fx: { k: 'p_mimic', amp: 1, noDrain: true } }),
  k('universal_thread', 'Fios Universais', 'Universal Thread', 'B', 'ln', { unverified: true,
    req: [P('sticky_thread'), P('steel_thread')], lp: P('universal_shapeshift'), mp: 30, cd: 12,
    desc: 'Combinação dos fios absorvidos: prende o alvo a até 24 blocos por 8 s e o corta continuamente.',
    fx: { k: 'bind', el: 'physical', range: 24, dur: 8, dps: 2, eff: [['slowness', 8, 3]] } }),
  k('demon_body', 'Corpo Demoníaco', 'Demonic Body', 'C', 'mod', {
    req: [P('evo_demon_slime')], lvl: 32,
    desc: 'Corpo de Magicules densas: +2 corações extras e resistência física.',
    fx: [{ k: 'p_effect', eff: [['health_boost', 1]] }, { k: 'p_resist', groups: { physical: 0.2 } }] }),
  { ...evo('r.slime', 'demon_slime', 'legendary', {
    cost: 150, lvl: 30, req: [P('infinite_regen')], lp: P('ultraspeed_regen'), needCount: 20, mission: 'm.slime.harvest' }),
  rank: 'S', src: 'anime', ...sec, rarity: 'legendary' },
  { ...evo('r.slime', 'god_slime', 'supreme', {
    cost: 300, lvl: 60, req: [P('evo_demon_slime')], needCount: 45, flags: ['killed_ender_dragon'] }), rank: 'EX', src: 'mod', ...sec },
];
