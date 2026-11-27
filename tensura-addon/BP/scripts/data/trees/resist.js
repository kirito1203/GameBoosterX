import { S, defineTree } from '../dsl.js';

// Resistências e Anulações — nomes da obra; efeitos: redução de dano por tipo.
const C = 'Resistência';

export default defineTree('resist', {
  name: 'Resistências', original: 'Resistances', short: 'Resist.', kind: 'universal',
  root: 'res.tolerance', category: C,
}, [
  S('res.tolerance', 'Tolerância', 'Tolerance', 'common', {
    cost: 10, lvl: 2, origin: 'original', major: true, cat: C,
    desc: 'Base das resistências: 5% de redução de todo dano físico e mágico.', fx: { k: 'p_resist', groups: { physical: 0.05, magic: 0.05 } } }),
  S('res.heat', 'Resistência a Variações de Calor', 'Thermal Fluctuation Resistance', 'common', {
    cost: 20, lvl: 4, req: ['res.tolerance'], arm: 'N', cat: `${C} · Calor`,
    desc: 'Reduz em 30% o dano de fogo, lava e congelamento.', fx: { k: 'p_resist', groups: { fire: 0.3, freezing: 0.3 } } }),
  S('res.heat_null', 'Anulação de Variações de Calor', 'Thermal Fluctuation Nullification', 'epic', {
    cost: 90, lvl: 24, req: ['res.heat'], cat: `${C} · Calor`,
    desc: 'Imunidade ao fogo e redução de 80% do dano de calor e frio.', fx: [{ k: 'p_resist', groups: { fire: 0.8, freezing: 0.8 } }, { k: 'p_effect', eff: [['fire_resistance', 0]] }] }),
  S('res.pain', 'Resistência à Dor', 'Pain Resistance', 'common', {
    cost: 20, lvl: 4, req: ['res.tolerance'], arm: 'E', cat: `${C} · Corpo`,
    desc: 'Reduz em 15% o dano físico e de queda.', fx: { k: 'p_resist', groups: { physical: 0.15, fall: 0.15 } } }),
  S('res.physical', 'Resistência a Ataques Físicos', 'Physical Attack Resistance', 'rare', {
    cost: 50, lvl: 12, req: ['res.pain'], cat: `${C} · Corpo`,
    desc: 'Reduz em 30% o dano físico e de explosões.', fx: { k: 'p_resist', groups: { physical: 0.3, explosion: 0.2 } } }),
  S('res.pain_null', 'Anulação de Dor', 'Pain Nullification', 'epic', {
    cost: 90, lvl: 26, req: ['res.physical'], cat: `${C} · Corpo`,
    desc: 'Reduz em 50% o dano físico e de queda.', fx: { k: 'p_resist', groups: { physical: 0.5, fall: 0.5 } } }),
  S('res.electric', 'Resistência a Corrente Elétrica', 'Electric Current Resistance', 'common', {
    cost: 20, lvl: 6, req: ['res.tolerance'], arm: 'S', cat: `${C} · Elementos`,
    desc: 'Reduz em 50% o dano de relâmpagos.', fx: { k: 'p_resist', groups: { lightning: 0.5 } } }),
  S('res.paralysis', 'Resistência a Paralisia', 'Paralysis Resistance', 'common', {
    cost: 25, lvl: 6, req: ['res.electric'], cat: `${C} · Estados`,
    desc: 'Imunidade a lentidão e fadiga (removidas automaticamente).', fx: { k: 'p_race', cleanse: ['slowness', 'mining_fatigue'] } }),
  S('res.poison', 'Resistência a Veneno', 'Poison Resistance', 'common', {
    cost: 25, lvl: 6, req: ['res.tolerance'], arm: 'W', cat: `${C} · Estados`,
    desc: 'Imunidade a veneno (removido automaticamente).', fx: { k: 'p_race', poisonImmune: true } }),
  S('res.corrosion', 'Resistência à Corrosão', 'Corrosion Resistance', 'rare', {
    cost: 40, lvl: 12, req: ['res.poison'], cat: `${C} · Estados`,
    desc: 'Imunidade a wither e fome (removidos automaticamente) e 30% menos dano de wither.',
    fx: [{ k: 'p_race', cleanse: ['wither', 'hunger'] }, { k: 'p_resist', groups: { dark: 0.3 } }] }),
  S('res.holy', 'Resistência a Ataques Sagrados', 'Holy Attack Resistance', 'epic', {
    cost: 80, lvl: 22, req: ['res.corrosion'], cat: `${C} · Espírito`,
    desc: 'Reduz em 50% o dano sagrado.', fx: { k: 'p_resist', groups: { holy: 0.5 } } }),
  S('res.spiritual', 'Resistência a Ataques Espirituais', 'Spiritual Attack Resistance', 'epic', {
    cost: 80, lvl: 22, req: ['res.corrosion'], cat: `${C} · Espírito`,
    desc: 'Reduz em 40% o dano mágico e sombrio e remove cegueira e náusea.',
    fx: [{ k: 'p_resist', groups: { magic: 0.4, dark: 0.4 } }, { k: 'p_race', cleanse: ['blindness', 'nausea', 'darkness'] }] }),
  S('res.natural_null', 'Anulação de Efeitos Naturais', 'Natural Effect Nullification', 'legendary', {
    cost: 150, lvl: 34, req: ['res.heat_null', 'res.electric'], cat: `${C} · Anulação`,
    desc: 'Anula afogamento, sufocamento e quase todo dano de relâmpago, fogo e frio.',
    fx: { k: 'p_resist', groups: { drowning: 1, suffocation: 0.8, lightning: 0.8, fire: 0.5, freezing: 0.5 } } }),
]);
