import { S, defineTree } from '../dsl.js';

const O = 'adaptado';
const C = 'Necromancia';

export default defineTree('necromancy', {
  name: 'Árvore de Necromancia', original: 'Necromancy', short: 'Necro.', kind: 'universal',
  root: 'necro.summon_undead', category: C,
}, [
  S('necro.summon_undead', 'Invocar Mortos-vivos', 'Summon Undead', 'common', {
    cost: 25, lvl: 8, origin: O, major: true, mp: 25, cd: 50, cat: C,
    desc: 'Ergue dois guerreiros mortos-vivos aliados por 60 s.',
    fx: { k: 'summon', entity: 'tensura:undead_warrior', count: 2, dur: 60 },
  }),
  S('necro.curse_bind', 'Prisão Amaldiçoada', 'Curse Bind', 'rare', {
    cost: 45, lvl: 14, req: ['necro.summon_undead'], arm: 'E', origin: O, mp: 30, cd: 20, cat: C,
    desc: 'Correntes amaldiçoadas prendem o alvo e o consomem com wither.',
    fx: { k: 'bind', el: 'dark', range: 18, dur: 6, dps: 1, eff: [['wither', 6, 1]] },
  }),
  S('necro.day_night_inversion', 'Inversão Dia-Noite', 'Day-Night Inversion', 'epic', {
    cost: 90, lvl: 22, req: ['necro.curse_bind'], origin: O, mp: 100, cd: 600, cat: C,
    desc: 'Inverte o ciclo do dia: dia vira noite e noite vira dia.',
    fx: { k: 'timeflip' },
  }),
  S('necro.immortal_legion', 'Legião Imortal', 'Immortal Legion', 'legendary', {
    cost: 160, lvl: 32, req: ['necro.summon_undead', 'necro.curse_bind'], arm: 'N', origin: O, major: true,
    mp: 140, cd: 240, cat: C,
    desc: 'Invoca uma legião de guerreiros e arqueiros mortos-vivos por 90 s. Os que caem se reerguem enquanto a magia durar.',
    fx: { k: 'summon', entity: 'tensura:undead_warrior', count: 4, extra: [['tensura:undead_archer', 2]], dur: 90, revive: true },
  }),
]);
