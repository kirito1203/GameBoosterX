import { S, defineTree } from '../dsl.js';

// Habilidades Supremas (Ultimate Skills) — nomes da obra; efeitos adaptados.
// Requisitos altíssimos: nível 45+, habilidades únicas e conquistas.
const C = 'Habilidade Suprema';

export default defineTree('ultimate', {
  name: 'Habilidades Supremas', original: 'Ultimate Skills', short: 'Supremas', kind: 'universal',
  root: 'ult.awakening', category: C,
}, [
  S('ult.awakening', 'Despertar Supremo', 'Ultimate Awakening', 'legendary', {
    cost: 150, lvl: 45, origin: 'original', major: true, needEpic: 6, cat: C,
    desc: 'Desperta o nível supremo da alma: +150 Magicules máximas e +2/s de regeneração. Exige 6 habilidades Épicas ou superiores.',
    fx: { k: 'p_mp', max: 150, regen: 2 } }),
  S('ult.raphael', 'Raphael', 'Raphael', 'supreme', {
    cost: 280, lvl: 50, req: ['ult.awakening', 'uni.great_sage'], arm: 'N', major: true, cat: `${C} · Sabedoria`,
    desc: 'Senhor da Sabedoria: todas as magias 20% mais baratas e 20% mais fortes, regeneração de Magicules x1,5.',
    fx: [{ k: 'p_affinity', tree: '*', mult: 0.8 }, { k: 'p_power', el: '*', mult: 1.2 }, { k: 'p_mp', regenMult: 1.5 }] }),
  S('ult.faust', 'Faust', 'Faust', 'supreme', {
    cost: 260, lvl: 50, req: ['ult.raphael'], mp: 30, cd: 30, cat: `${C} · Sabedoria`,
    desc: 'Senhor da Investigação: revela todas as criaturas num raio de 96 blocos e concede visão noturna.',
    fx: [{ k: 'sense', r: 96 }, { k: 'buff', eff: [['night_vision', 120, 0]] }] }),
  S('ult.beelzebuth', 'Beelzebuth', 'Beelzebuth', 'supreme', {
    cost: 300, lvl: 55, req: ['ult.awakening', 'uni.gluttony'], arm: 'E', major: true, mp: 40, cd: 5, cat: `${C} · Predação`,
    desc: 'Senhor da Gula: devora criaturas com até 70% de vida a 16 blocos; +8 Magicules máximas (até +800).',
    fx: { k: 'absorb', range: 16, hpPct: 0.7, mpGain: 8, mpCap: 800 } }),
  S('ult.mammon', 'Mammon', 'Mammon', 'supreme', {
    cost: 260, lvl: 52, req: ['ult.beelzebuth'], mp: 120, cd: 90, cat: `${C} · Predação`,
    desc: 'Senhor da Ganância: arranca vida e Magicules de todos num raio de 10.',
    fx: { k: 'drain', el: 'soul', r: 10, dmg: 16, heal: 0.6, mpGain: 25 } }),
  S('ult.uriel', 'Uriel', 'Uriel', 'supreme', {
    cost: 280, lvl: 50, req: ['ult.awakening'], arm: 'S', major: true, mp: 120, cd: 180, cat: `${C} · Defesa`,
    desc: 'Senhor dos Juramentos: defesa absoluta por 20 s para você e aliados próximos.',
    fx: { k: 'buff', allies: 10, eff: [['absorption', 20, 5], ['resistance', 20, 3], ['fire_resistance', 20, 0]] } }),
  S('ult.gabriel', 'Gabriel', 'Gabriel', 'supreme', {
    cost: 260, lvl: 52, req: ['ult.uriel'], mp: 150, cd: 180, cat: `${C} · Salvação`,
    desc: 'Senhor da Salvação: cura completa e regeneração intensa para você e aliados.',
    fx: [{ k: 'heal', full: true, amount: 40, allies: 12, allyFull: true }, { k: 'buff', allies: 12, eff: [['regeneration', 20, 2]] }, { k: 'cleanse', negative: true, allies: 12 }] }),
  S('ult.michael', 'Michael', 'Michael', 'supreme', {
    cost: 290, lvl: 55, req: ['ult.uriel'], mp: 160, cd: 240, cat: `${C} · Justiça`,
    desc: 'Senhor da Justiça: domina todas as criaturas num raio de 12 e fortalece seus aliados.',
    fx: [{ k: 'dominate', range: 4, r: 12, dur: 30 }, { k: 'buff', allies: 12, eff: [['strength', 30, 2], ['resistance', 30, 1]] }] }),
  S('ult.metatron', 'Metatron', 'Metatron', 'supreme', {
    cost: 280, lvl: 55, req: ['ult.gabriel'], mp: 180, cd: 180, cat: `${C} · Pureza`,
    desc: 'Senhor da Pureza: feixe sagrado que purifica tudo em linha.',
    fx: { k: 'beam', el: 'holy', dmg: 50, range: 40, pierce: true } }),
  S('ult.satanael', 'Satanael', 'Satanael', 'supreme', {
    cost: 300, lvl: 55, req: ['ult.awakening', 'uni.wrath'], arm: 'W', major: true, mp: 150, cd: 240, cat: `${C} · Ira`,
    desc: 'Senhor da Ira: poder destrutivo por 40 s — força IV, velocidade e golpes de chamas negras.',
    fx: [{ k: 'buff', eff: [['strength', 40, 4], ['speed', 40, 2], ['resistance', 40, 2]] }, { k: 'imbue', el: 'dark', dur: 40, dmg: 8, eff: [['wither', 3, 2]] }] }),
  S('ult.azathoth', 'Azathoth', 'Azathoth', 'supreme', {
    cost: 320, lvl: 60, req: ['ult.satanael', 'ult.beelzebuth'], flags: ['killed_ender_dragon'], mp: 250, cd: 300, cat: `${C} · Vazio`,
    desc: 'Senhor da Vaidade: o vazio devora a área mirada. Exige ter derrotado o Ender Dragon.',
    fx: { k: 'nova', el: 'space', range: 30, r: 10, dmg: 60, eff: [['wither', 6, 3]] } }),
  S('ult.yog_sothoth', 'Yog-Sothoth', 'Yog-Sothoth', 'supreme', {
    cost: 300, lvl: 60, req: ['ult.faust'], flags: ['killed_wither'], mp: 200, cd: 240, cat: `${C} · Espaço-tempo`,
    desc: 'Domínio do espaço-tempo: congela o tempo ao redor por 8 s. Exige ter derrotado o Wither.',
    fx: { k: 'field', el: 'space', r: 14, dur: 8, self: true, enemyEff: [['slowness', 1, 255], ['weakness', 1, 255]], freeze: true } }),
  S('ult.amaterasu', 'Amaterasu', 'Amaterasu', 'supreme', {
    cost: 260, lvl: 52, req: ['ult.gabriel'], mp: 140, cd: 120, cat: `${C} · Sol`,
    desc: 'O sol desce sobre a área mirada: chuva de luz e fogo.',
    fx: { k: 'rain', el: 'holy', dmg: 18, r: 7, waves: 8, every: 6, range: 32, ignite: 5 } }),
  S('ult.susanoo', 'Susanoo', 'Susanoo', 'supreme', {
    cost: 260, lvl: 52, req: ['ult.satanael'], mp: 140, cd: 120, cat: `${C} · Tempestade`,
    desc: 'Tempestade divina: relâmpagos reais caem sobre a área mirada.',
    fx: { k: 'rain', el: 'lightning', dmg: 18, r: 8, waves: 10, every: 5, range: 32, lightning: true } }),
]);
