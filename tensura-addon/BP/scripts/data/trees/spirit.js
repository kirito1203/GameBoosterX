import { S, defineTree } from '../dsl.js';

const O = 'adaptado';
const C = 'Magia Espiritual';

export default defineTree('spirit', {
  name: 'Árvore Espiritual', original: 'Spirit Magic', short: 'Espírito', kind: 'universal',
  root: 'spirit.communion', category: C,
}, [
  S('spirit.communion', 'Comunhão Espiritual', 'Spirit Communion', 'common', {
    cost: 15, lvl: 5, origin: 'original', major: true, cat: C,
    desc: 'Vínculo com os espíritos: +20% de regeneração de Magicules e menor custo nesta árvore.',
    fx: [{ k: 'p_mp', regenMult: 1.2 }, { k: 'p_affinity', tree: 'spirit', mult: 0.9 }],
  }),
  S('spirit.fire', 'Magia Espiritual do Fogo', 'Fire Spirit Magic', 'rare', {
    cost: 35, lvl: 10, req: ['spirit.communion'], arm: 'E', origin: O, mp: 25, cd: 40, cat: `${C} · Fogo`,
    desc: 'Um espírito do fogo imbui seus golpes: ataques corpo a corpo incendeiam e causam dano extra por 30 s.',
    fx: [{ k: 'imbue', el: 'fire', dur: 30, dmg: 3, ignite: 3 }, { k: 'buff', eff: [['fire_resistance', 60, 0]] }],
  }),
  S('spirit.light', 'Magia Espiritual da Luz', 'Light Spirit Magic', 'rare', {
    cost: 50, lvl: 16, req: ['spirit.fire'], origin: O, mp: 35, cd: 40, cat: `${C} · Luz`,
    desc: 'Explosão de luz: cura aliados e cega inimigos ao redor.',
    fx: [{ k: 'heal', amount: 8, allies: 6 }, { k: 'debuff', el: 'holy', r: 6, self: true, eff: [['blindness', 4, 0]] }],
  }),
  S('spirit.water', 'Magia Espiritual da Água', 'Water Spirit Magic', 'rare', {
    cost: 35, lvl: 10, req: ['spirit.communion'], arm: 'W', origin: O, mp: 20, cd: 40, cat: `${C} · Água`,
    desc: 'Um espírito da água concede respiração aquática, regeneração e apaga o fogo.',
    fx: [{ k: 'buff', eff: [['water_breathing', 120, 0], ['regeneration', 10, 1]] }, { k: 'cleanse', fire: true }],
  }),
  S('spirit.earth', 'Magia Espiritual da Terra', 'Earth Spirit Magic', 'rare', {
    cost: 35, lvl: 10, req: ['spirit.communion'], arm: 'S', origin: O, mp: 25, cd: 40, cat: `${C} · Terra`,
    desc: 'Um espírito da terra endurece seu corpo e acelera a mineração.',
    fx: { k: 'buff', eff: [['resistance', 30, 1], ['haste', 30, 1]] },
  }),
  S('spirit.dark', 'Magia Espiritual das Trevas', 'Dark Spirit Magic', 'rare', {
    cost: 50, lvl: 16, req: ['spirit.earth'], origin: O, mp: 35, cd: 40, cat: `${C} · Trevas`,
    desc: 'Um espírito das trevas oculta você e envolve os inimigos na escuridão; seus golpes aplicam wither.',
    fx: [{ k: 'buff', eff: [['invisibility', 10, 0]] }, { k: 'debuff', el: 'dark', r: 8, self: true, eff: [['darkness', 8, 0]] },
      { k: 'imbue', el: 'dark', dur: 20, dmg: 2, eff: [['wither', 3, 1]] }],
  }),
  S('spirit.wind', 'Magia Espiritual do Vento', 'Wind Spirit Magic', 'rare', {
    cost: 35, lvl: 10, req: ['spirit.communion'], arm: 'N', origin: O, mp: 20, cd: 40, cat: `${C} · Vento`,
    desc: 'Um espírito do vento torna você veloz e leve.',
    fx: { k: 'buff', eff: [['speed', 30, 2], ['jump_boost', 30, 2], ['slow_falling', 30, 0]] },
  }),
]);
