// SETOR II — RESISTÊNCIAS E IMUNIDADES (todas aplicadas no sistema de combate real:
// redução por grupo de dano, anulação = imunidade ao grupo, e remoção contínua de efeitos).
import { K, P } from './common.js';

const k = K('resist');
const NEGATIVE = ['poison', 'wither', 'weakness', 'slowness', 'blindness', 'nausea', 'hunger', 'darkness', 'mining_fatigue'];

export default [
  k('physical_resist', 'Resistência Física', 'Physical Attack Resistance', 'E', 'anime', {
    req: [P('root')], lp: P('root'), lvl: 3, major: true,
    desc: 'Reduz em 15% o dano físico recebido.', fx: { k: 'p_resist', groups: { physical: 0.15 } } }),
  k('pain_null', 'Anulação da Dor', 'Pain Nullification', 'D', 'anime', {
    req: [P('physical_resist')],
    desc: 'Sem dor: imunidade a Fraqueza e Náusea e +5% de resistência física.',
    fx: [{ k: 'p_cleanse', eff: ['weakness', 'nausea'] }, { k: 'p_resist', groups: { physical: 0.05 } }] }),
  k('electric_resist', 'Resistência Elétrica', 'Electricity Resistance', 'E', 'anime', {
    req: [P('physical_resist')], desc: 'Reduz em 40% o dano de raios.', fx: { k: 'p_resist', groups: { lightning: 0.4 } } }),
  k('paralysis_resist', 'Resistência à Paralisia', 'Paralysis Resistance', 'E', 'anime', {
    req: [P('physical_resist')], desc: 'Imunidade a Lentidão e Cansaço (paralisia).', fx: { k: 'p_cleanse', eff: ['slowness', 'mining_fatigue'] } }),
  k('thermal_resist', 'Resistência Térmica', 'Thermal Fluctuation Resistance', 'D', 'anime', {
    req: [P('electric_resist')], desc: 'Reduz o dano de fogo (30%) e de congelamento (40%).',
    fx: { k: 'p_resist', groups: { fire: 0.3, freezing: 0.4 } } }),
  k('fire_null', 'Anulação de Ataques de Fogo', 'Flame Attack Nullification', 'B', 'anime', {
    req: [P('thermal_resist')], needAbsorb: { any: ['minecraft:blaze'] },
    desc: 'Obtida ao absorver Ifrit (no addon: um Blaze). Imunidade total a dano de fogo e lava.',
    fx: { k: 'p_resist', groups: { fire: 1 }, null: true } }),
  k('physical_null', 'Anulação de Ataques Físicos', 'Physical Attack Nullification', 'S', 'pendente', {
    req: [P('pain_null')], needStage: 1, needMp: 800,
    desc: 'Na obra é anulação total. No addon (balanceamento): 60% de redução de dano físico.',
    fx: { k: 'p_resist', groups: { physical: 0.6 } } }),
  k('natural_null', 'Anulação de Efeitos Naturais', 'Natural Effect Nullification', 'B', 'pendente', {
    req: [P('thermal_resist'), P('paralysis_resist')],
    desc: 'Imunidade a afogamento, sufocamento e congelamento.',
    fx: { k: 'p_resist', groups: { drowning: 1, suffocation: 1, freezing: 1 }, null: true } }),
  k('abnormal_null', 'Anulação de Condições Anormais', 'Abnormal Condition Nullification', 'A', 'pendente', {
    req: [P('natural_null'), P('poison_resist')], lp: P('natural_null'),
    desc: 'Imunidade a veneno, wither, fraqueza, lentidão, cegueira, náusea, fome, escuridão e cansaço.',
    fx: { k: 'p_cleanse', eff: NEGATIVE } }),
  k('mental_null', 'Anulação de Ataques Mentais', 'Mental Attack Nullification', 'B', 'pendente', {
    req: [P('pain_null')], desc: 'Imunidade a náusea, cegueira e escuridão, e 15% menos dano mágico.',
    fx: [{ k: 'p_cleanse', eff: ['nausea', 'blindness', 'darkness'] }, { k: 'p_resist', groups: { magic: 0.15 } }] }),
  k('spiritual_resist', 'Resistência a Ataques Espirituais', 'Spiritual Attack Resistance', 'A', 'pendente', {
    req: [P('mental_null')], needStage: 1, desc: 'Reduz em 35% o dano mágico/espiritual.', fx: { k: 'p_resist', groups: { magic: 0.35 } } }),
  k('holy_demon_resist', 'Resistência a Ataques Sagrados e Demoníacos', 'Holy & Demonic Attack Resistance', 'S', 'pendente', {
    req: [P('spiritual_resist')], needMp: 1000, desc: 'Reduz em 45% o dano sagrado e o de trevas/wither.',
    fx: { k: 'p_resist', groups: { holy: 0.45, dark: 0.45 } } }),
  k('magic_adaptation', 'Adaptação Mágica', 'Magic Adaptation', 'C', 'mod', {
    req: [P('physical_resist')], lvl: 12,
    desc: 'O corpo se adapta à magia: resistência a dano mágico, fogo e gelo, e todas as magias custam 5% menos.',
    fx: [{ k: 'p_resist', groups: { magic: 0.3, fire: 0.2, freezing: 0.3 } }, { k: 'p_affinity', tree: '*', mult: 0.95 }] }),
];
