import { S, defineTree } from '../dsl.js';

const O = 'adaptado';
const C = 'Magia Sagrada';
// Raças que não podem aprender magia sagrada (fraqueza ao sagrado).
const UNHOLY = ['demon', 'vampire', 'ghoul'];

export default defineTree('holy', {
  name: 'Árvore Sagrada', original: 'Holy Magic', short: 'Sagrada', kind: 'universal',
  root: 'holy.prayer', category: C,
}, [
  S('holy.prayer', 'Oração Sagrada', 'Holy Prayer', 'common', {
    cost: 15, lvl: 5, origin: 'original', major: true, blocked: UNHOLY, cat: C,
    desc: 'Fé que fortalece a magia sagrada: menor custo e maior poder nesta árvore.',
    fx: [{ k: 'p_affinity', tree: 'holy', mult: 0.9 }, { k: 'p_power', el: 'holy', mult: 1.1 }],
  }),
  S('holy.holy_ray', 'Raio Sagrado', 'Holy Ray', 'rare', {
    cost: 35, lvl: 10, req: ['holy.prayer'], arm: 'N', origin: O, blocked: UNHOLY, mp: 22, cd: 4, cat: C,
    desc: 'Raio de luz sagrada. Dano dobrado contra mortos-vivos.',
    fx: { k: 'beam', el: 'holy', dmg: 10, range: 24 },
  }),
  S('holy.holy_cannon', 'Canhão Sagrado', 'Holy Cannon', 'epic', {
    cost: 80, lvl: 20, req: ['holy.holy_ray'], origin: O, blocked: UNHOLY, mp: 55, cd: 12, cat: C,
    desc: 'Disparo concentrado de energia sagrada que explode em área.',
    fx: { k: 'bolt', el: 'holy', dmg: 16, range: 30, speed: 1.5, r: 3 },
  }),
  S('holy.holy_field', 'Campo Sagrado', 'Holy Field', 'rare', {
    cost: 50, lvl: 14, req: ['holy.prayer'], arm: 'E', origin: O, blocked: UNHOLY, mp: 40, cd: 45, cat: C,
    desc: 'Campo que fere mortos-vivos e monstros e regenera aliados.',
    fx: { k: 'field', el: 'holy', r: 8, dur: 20, dps: 2, self: false, enemyEff: [['weakness', 2, 0]], allyEff: [['regeneration', 3, 0]] },
  }),
  S('holy.alignment_field', 'Campo de Alinhamento', 'Alignment Field', 'epic', {
    cost: 90, lvl: 22, req: ['holy.holy_field'], origin: O, blocked: UNHOLY, mp: 60, cd: 60, cat: C,
    desc: 'Alinha as forças do campo a seu favor: aliados mais fortes e resistentes, inimigos mais lentos.',
    fx: { k: 'field', el: 'holy', r: 10, dur: 20, self: true, enemyEff: [['slowness', 2, 1]], allyEff: [['strength', 3, 1], ['resistance', 3, 1], ['speed', 3, 0]] },
  }),
  S('holy.prison_field', 'Campo de Prisão', 'Prison Field', 'epic', {
    cost: 100, lvl: 24, req: ['holy.holy_field'], origin: O, blocked: UNHOLY, mp: 70, cd: 60, cat: C,
    desc: 'Aprisiona todas as criaturas hostis na área mirada.',
    fx: { k: 'bind', el: 'holy', range: 20, r: 6, dur: 8, dps: 1 },
  }),
  S('holy.great_high_heal', 'Grande Cura Superior', 'Great High Heal', 'rare', {
    cost: 55, lvl: 15, req: ['aspect.healing'], origin: O, blocked: UNHOLY, mp: 40, cd: 20, cat: C,
    desc: 'Cura poderosa em você e em todos os aliados próximos.',
    fx: { k: 'heal', amount: 20, allies: 8, allyFull: true },
  }),
  S('holy.great_regeneration', 'Grande Regeneração', 'Great Regeneration', 'epic', {
    cost: 85, lvl: 22, req: ['holy.great_high_heal'], origin: O, blocked: UNHOLY, mp: 55, cd: 60, cat: C,
    desc: 'Regeneração intensa e vida extra para você e aliados.',
    fx: { k: 'buff', allies: 8, eff: [['regeneration', 20, 2], ['health_boost', 60, 1]] },
  }),
  S('holy.disintegration', 'Desintegração', 'Disintegration', 'legendary', {
    cost: 180, lvl: 35, req: ['holy.great_high_heal', 'holy.holy_cannon'], origin: O, blocked: UNHOLY, major: true,
    mp: 150, cd: 90, cat: C,
    desc: 'O feitiço sagrado supremo: um feixe que desintegra tudo em linha.',
    fx: { k: 'beam', el: 'holy', dmg: 40, range: 32, pierce: true },
  }),
], [
  { id: 'aspect.healing', arm: 'W' },
]);
