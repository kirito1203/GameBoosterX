import { S, defineTree } from '../dsl.js';

// Habilidades Únicas (Unique Skills) — nomes da obra; efeitos adaptados ao Minecraft.
const C = 'Habilidade Única';
const A = 'adaptado';

export default defineTree('unique', {
  name: 'Habilidades Únicas', original: 'Unique Skills', short: 'Únicas', kind: 'universal',
  root: 'uni.soul', category: C,
}, [
  S('uni.soul', 'Alma Desperta', 'Awakened Soul', 'rare', {
    cost: 30, lvl: 10, origin: 'original', major: true, cat: C,
    desc: 'Sua alma desperta para habilidades únicas: +40 Magicules máximas.',
    fx: { k: 'p_mp', max: 40 } }),
  S('uni.great_sage', 'Grande Sábio', 'Great Sage', 'epic', {
    cost: 90, lvl: 12, req: ['uni.soul'], arm: 'N', cat: `${C} · Sabedoria`,
    desc: 'Pensamento acelerado e análise total: +25% de regeneração de Magicules, +20% de EP e todas as magias 5% mais baratas.',
    fx: [{ k: 'p_mp', regenMult: 1.25 }, { k: 'p_xp', mult: 1.2 }, { k: 'p_affinity', tree: '*', mult: 0.95 }] }),
  S('uni.mathematician', 'Matemático', 'Mathematician', 'epic', {
    cost: 80, lvl: 16, req: ['uni.great_sage'], cat: `${C} · Sabedoria`,
    desc: 'Cálculo perfeito das trajetórias: +15% de dano em todas as magias.',
    fx: { k: 'p_power', el: '*', mult: 1.15 } }),
  S('uni.analyst', 'Analista', 'Analyst', 'rare', {
    cost: 50, lvl: 12, req: ['uni.great_sage'], mp: 3, cd: 1, cat: `${C} · Sabedoria`,
    desc: 'Analisa a criatura mirada a grande distância.', fx: { k: 'analyze', range: 48 } }),
  S('uni.creator', 'Criador', 'Creator', 'epic', {
    cost: 90, lvl: 20, req: ['uni.analyst'], mp: 70, cd: 90, cat: `${C} · Criação`,
    desc: 'Cria materiais a partir de Magicules.', fx: { k: 'create' } }),
  S('uni.predator', 'Predador', 'Predator', 'epic', {
    cost: 100, lvl: 15, req: ['uni.soul'], arm: 'E', mp: 15, cd: 4, cat: `${C} · Predação`,
    desc: 'Devora a criatura mirada com até 40% de vida: +3 Magicules máximas (até +300) e registra a espécie.',
    fx: { k: 'absorb', range: 8, hpPct: 0.4, mpGain: 3, mpCap: 300 } }),
  S('uni.gluttony', 'Gula', 'Gluttony', 'legendary', {
    cost: 150, lvl: 28, req: ['uni.predator'], mp: 30, cd: 6, cat: `${C} · Predação`,
    desc: 'Predação devastadora: alcance 12, criaturas com até 55% de vida, +5 Magicules máximas (até +450).',
    fx: { k: 'absorb', range: 12, hpPct: 0.55, mpGain: 5, mpCap: 450 } }),
  S('uni.starved', 'Faminto', 'Starved', 'rare', {
    cost: 50, lvl: 14, req: ['uni.predator'], cat: `${C} · Predação`,
    desc: 'Ao derrotar criaturas: recupera fome, +10 Magicules e força por 10 s.',
    fx: { k: 'p_starved', mp: 10 } }),
  S('uni.cook', 'Cozinheiro', 'Cook', 'epic', {
    cost: 80, lvl: 18, req: ['uni.soul'], arm: 'S', cat: `${C} · Destino`,
    desc: 'Reescreve o resultado dos golpes: 25% de chance de causar fraqueza e lentidão, e +10% de roubo de vida.',
    fx: [{ k: 'p_onhit', chance: 0.25, eff: [['weakness', 4, 1], ['slowness', 3, 1]] }, { k: 'p_lifesteal', pct: 0.1 }] }),
  S('uni.degenerate', 'Degenerado', 'Degenerate', 'epic', {
    cost: 90, lvl: 20, req: ['uni.cook'], mp: 45, cd: 45, cat: `${C} · Destino`,
    desc: 'Funde o espírito do fogo ao corpo por 30 s: força, imunidade ao fogo e golpes flamejantes.',
    fx: [{ k: 'buff', eff: [['strength', 30, 2], ['fire_resistance', 30, 0]] }, { k: 'imbue', el: 'fire', dur: 30, dmg: 4, ignite: 4 }] }),
  S('uni.usurper', 'Usurpador', 'Usurper', 'legendary', {
    cost: 140, lvl: 30, req: ['uni.cook'], mp: 60, cd: 60, cat: `${C} · Destino`,
    desc: 'Rouba a força vital e mágica do alvo mirado.', fx: { k: 'drain', el: 'soul', range: 16, dmg: 14, heal: 1, mpGain: 40 } }),
  S('uni.wrath', 'Ira', 'Wrath', 'legendary', {
    cost: 150, lvl: 30, req: ['uni.soul'], arm: 'W', mp: 70, cd: 90, cat: `${C} · Pecado`,
    desc: 'A fúria liberta o poder: força III, velocidade e dano mágico elevado por 30 s.',
    fx: { k: 'buff', eff: [['strength', 30, 3], ['speed', 30, 2], ['resistance', 30, 1]] } }),
  S('uni.greed', 'Ganância', 'Greed', 'legendary', {
    cost: 140, lvl: 30, req: ['uni.wrath'], origin: A, cat: `${C} · Pecado`,
    desc: 'Tudo o que você derrota vale mais: +40% de EP.', fx: { k: 'p_xp', mult: 1.4 } }),
  S('uni.envy', 'Inveja', 'Envy', 'epic', {
    cost: 100, lvl: 24, req: ['uni.wrath'], origin: A, mp: 40, cd: 40, cat: `${C} · Pecado`,
    desc: 'Enfraquece todas as criaturas ao redor e drena um pouco de vida de cada uma.',
    fx: { k: 'drain', el: 'dark', r: 8, dmg: 5, heal: 0.5, mpGain: 3 } }),
  S('uni.sloth', 'Preguiça', 'Sloth', 'epic', {
    cost: 100, lvl: 24, req: ['uni.wrath'], origin: A, mp: 45, cd: 45, cat: `${C} · Pecado`,
    desc: 'Torna as criaturas ao redor quase imóveis por 8 s.', fx: { k: 'debuff', el: 'dark', r: 9, self: true, eff: [['slowness', 8, 5], ['mining_fatigue', 8, 3]] } }),
  S('uni.lust', 'Luxúria', 'Lust', 'epic', {
    cost: 100, lvl: 24, req: ['uni.wrath'], origin: A, mp: 40, cd: 45, cat: `${C} · Pecado`,
    desc: 'Encanta as criaturas ao redor: elas param de atacar por 15 s.', fx: { k: 'dominate', range: 4, r: 8, dur: 15 } }),
  S('uni.pride', 'Orgulho', 'Pride', 'legendary', {
    cost: 150, lvl: 34, req: ['uni.wrath'], origin: A, cat: `${C} · Pecado`,
    desc: 'Orgulho inabalável: +4 corações e resistência permanentes.', fx: { k: 'p_effect', eff: [['health_boost', 1], ['resistance', 0]] } }),
]);
