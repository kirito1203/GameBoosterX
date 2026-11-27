// SETOR IX — EXPANSÕES EXCLUSIVAS DO MOD (criações do addon, NÃO são poderes da obra).
// Para acrescentar novas: adicione aqui com src 'mod' (o registro recusa IDs repetidas).
import { K, P } from './common.js';

const k = K('mod');

export default [
  k('absolute_predator', 'Predador Absoluto', 'Absolute Predator', 'SS', 'mod', {
    req: [P('beelzebuth')], lp: P('root'), mp: 60, cd: 6, major: true,
    desc: 'Devora criaturas com até 85% de vida a 20 blocos (+10 Magicules, até +1200).',
    fx: { k: 'absorb', range: 20, hpPct: 0.85, mpGain: 10, mpCap: 1200 } }),
  k('dimensional_analysis', 'Análise Dimensional', 'Dimensional Analysis', 'S', 'mod', {
    req: [P('raphael'), P('appraisal')], lp: P('absolute_predator'), mp: 20, cd: 10, desc: 'Analisa a 96 blocos e revela tudo num raio de 96.',
    fx: [{ k: 'analyze', range: 96 }, { k: 'sense', r: 96 }] }),
  k('nihility_regen', 'Regeneração de Nihilidade', 'Nihility Regeneration', 'SS', 'mod', {
    req: [P('infinite_regen'), P('azathoth')], lp: P('absolute_predator'), desc: 'Regeneração nível IV permanente.', fx: { k: 'p_effect', eff: [['regeneration', 3]] } }),
  k('magicule_dominion', 'Domínio de Magicules', 'Magicule Dominion', 'S', 'mod', {
    req: [P('raphael')], lp: P('dimensional_analysis'), desc: '+300 Magicules máximas e +4/s de regeneração.', fx: { k: 'p_mp', max: 300, regen: 4 } }),
  k('supreme_assimilation', 'Assimilação Suprema', 'Supreme Assimilation', 'SS', 'mod', {
    req: [P('absolute_predator')], desc: 'Absorções concedem o dobro de Magicules.', fx: { k: 'p_absorb', mult: 2 } }),
  k('supreme_metamorphosis', 'Metamorfose Suprema', 'Supreme Metamorphosis', 'SS', 'mod', {
    req: [P('universal_shapeshift'), P('absolute_predator')], lp: P('supreme_assimilation'), desc: 'Formas da Imitação mais dois níveis mais fortes.', fx: { k: 'p_mimic', amp: 2 } }),
  k('multidim_perception', 'Percepção Multidimensional', 'Multidimensional Perception', 'S', 'mod', {
    req: [P('universal_perception'), P('raphael')], lp: P('magicule_dominion'), mp: 25, cd: 20, desc: 'Percebe tudo num raio de 128 blocos e vê no escuro por 2 min.',
    fx: [{ k: 'sense', r: 128 }, { k: 'buff', eff: [['night_vision', 120, 0]] }] }),
  k('nihility_barrier', 'Barreira de Nihilidade', 'Nihility Barrier', 'EX', 'mod', {
    req: [P('multidim_barrier')], lp: P('nihility_regen'), mp: 300, cd: 300, desc: 'Por 20 s: absorção máxima e Resistência IV.',
    fx: { k: 'buff', eff: [['absorption', 20, 10], ['resistance', 20, 3]] } }),
];
