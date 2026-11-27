// SETOR IV — GRANDE SÁBIO, DECOMPOSITOR, RAPHAEL E CIEL
// Raphael e Ciel também são exigidos pelo painel do Grande Sábio (estágios), para o Slime.
import { K, P } from './common.js';

const k = K('sage');

export default [
  k('great_sage', 'Grande Sábio', 'Great Sage', 'S', 'anime', {
    req: [P('root')], lp: P('root'), cost: 120, lvl: 6, major: true,
    desc: 'Habilidade Única: +20% de regeneração de Magicules e +15% de EP. Abre Aceleração do Pensamento, Análise e Avaliação, Processamento Paralelo, Anulação de Encantamento e Toda a Criação.',
    fx: [{ k: 'p_mp', regenMult: 1.2 }, { k: 'p_xp', mult: 1.15 }] }),
  k('thought_accel', 'Aceleração do Pensamento', 'Thought Acceleration', 'D', 'anime', {
    req: [P('great_sage')], mp: 15, cd: 40, desc: 'Percepção acelerada por 20 s: velocidade e pressa.',
    fx: { k: 'buff', eff: [['speed', 20, 1], ['haste', 20, 1]] } }),
  k('appraisal', 'Análise e Avaliação', 'Analytical Appraisal', 'D', 'anime', {
    req: [P('great_sage')], mp: 3, cd: 2, desc: 'Analisa a criatura mirada a até 64 blocos e registra na memória.', fx: { k: 'analyze', range: 64 } }),
  k('parallel', 'Processamento Paralelo', 'Parallel Processing', 'C', 'anime', {
    req: [P('thought_accel')], desc: 'Recarga de todas as habilidades 10% menor.', fx: { k: 'p_cd', mult: 0.9 } }),
  k('chant_annul', 'Anulação de Encantamento', 'Chant Annulment', 'C', 'anime', {
    req: [P('appraisal')], desc: 'Magias sem encantamento: todas custam 10% menos Magicules.', fx: { k: 'p_affinity', tree: '*', mult: 0.9 } }),
  k('all_creation', 'Toda a Criação', 'All of Creation', 'B', 'anime', {
    req: [P('parallel'), P('chant_annul')], lp: P('parallel'),
    desc: 'Conhecimento de tudo: habilidades universais custam 10% menos pontos e +10% de EP.',
    fx: [{ k: 'p_points', mult: 0.9 }, { k: 'p_xp', mult: 1.1 }] }),
  k('degenerate', 'Decompositor', 'Degenerate', 'S', 'anime', {
    req: [P('great_sage')], mission: 'm.slime.shizu', cost: 150, lvl: 16, major: true,
    desc: 'Habilidade Única herdada de Shizu. Abre Síntese e Separação; combinada ao Grande Sábio dá origem a Raphael.',
    fx: { k: 'p_power', el: '*', mult: 1.05 } }),
  k('synthesis', 'Síntese', 'Synthesis', 'B', 'anime', {
    req: [P('degenerate')], mp: 60, cd: 90, desc: 'Sintetiza materiais a partir de Magicules (ferro, ouro, esmeralda ou diamante).', fx: { k: 'create' } }),
  k('separation', 'Separação', 'Separation', 'B', 'anime', {
    req: [P('degenerate')], mp: 35, cd: 45, desc: 'Separa os efeitos nocivos de você e dos aliados num raio de 8 blocos.',
    fx: { k: 'cleanse', negative: true, allies: 8 } }),
  k('raphael', 'Raphael', 'Raphael, Lord of Wisdom', 'SS', 'anime', {
    req: [P('great_sage'), P('degenerate'), P('all_creation'), P('evo_demon_slime')], mission: 'm.slime.harvest', major: true,
    desc: 'Grande Sábio + Decompositor → Raphael, Senhor da Sabedoria (Festival da Colheita). Magias 20% mais baratas e fortes, regeneração x1,5. Permite evoluir o painel do Grande Sábio para Raphael.',
    fx: [{ k: 'p_affinity', tree: '*', mult: 0.8 }, { k: 'p_power', el: '*', mult: 1.2 }, { k: 'p_mp', regenMult: 1.5 }] }),
  k('ciel', 'Ciel', 'Ciel (Manas)', 'EX', 'ln', {
    req: [P('raphael')], mission: 'm.slime.ciel', lvl: 70, major: true,
    desc: 'Evolução especial do sistema de inteligência: Raphael desperta como Ciel. Recarga -15%, +200 Magicules, +3/s de regeneração e +10% de poder. Permite evoluir o painel do Grande Sábio para Ciel.',
    fx: [{ k: 'p_cd', mult: 0.85 }, { k: 'p_mp', max: 200, regen: 3 }, { k: 'p_power', el: '*', mult: 1.1 }] }),
];
