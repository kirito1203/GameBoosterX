import { S, defineTree } from '../dsl.js';

const O = 'adaptado';
const C = 'Magia das Trevas';

export default defineTree('dark', {
  name: 'Árvore das Trevas', original: 'Dark Magic', short: 'Trevas', kind: 'universal',
  root: 'dark.dark_magic', category: C,
}, [
  S('dark.dark_magic', 'Magia das Trevas', 'Dark Magic', 'common', {
    cost: 25, lvl: 8, origin: O, major: true, mp: 12, cd: 2, cat: C,
    desc: 'Projétil de energia sombria que aplica wither.',
    fx: { k: 'bolt', el: 'dark', dmg: 6, range: 22, speed: 1.2, eff: [['wither', 3, 0]] },
  }),
  S('dark.darkness_manipulation', 'Manipulação das Trevas', 'Darkness Manipulation', 'rare', {
    cost: 40, lvl: 12, req: ['dark.dark_magic'], arm: 'E', origin: O, mp: 25, cd: 12, cat: C,
    desc: 'Mergulha a área mirada em trevas que ferem e cegam.',
    fx: { k: 'nova', el: 'dark', range: 20, r: 5, dmg: 6, eff: [['darkness', 6, 0], ['blindness', 6, 0]] },
  }),
  S('dark.shadow_manipulation', 'Manipulação das Sombras', 'Shadow Manipulation', 'rare', {
    cost: 45, lvl: 14, req: ['dark.dark_magic'], arm: 'N', origin: O, mp: 20, cd: 8, cat: C,
    desc: 'Atravessa as sombras até o ponto mirado e fica invisível por instantes.',
    fx: [{ k: 'blink', range: 16 }, { k: 'buff', eff: [['invisibility', 4, 0]] }],
  }),
  S('dark.cursed_poison', 'Veneno Amaldiçoado', 'Cursed Poison', 'rare', {
    cost: 35, lvl: 10, req: ['dark.dark_magic'], arm: 'W', origin: O, mp: 18, cd: 8, cat: C,
    desc: 'Veneno amaldiçoado que corrói o alvo mirado.',
    fx: { k: 'debuff', el: 'dark', range: 16, dmg: 2, eff: [['poison', 8, 2], ['wither', 8, 0], ['weakness', 8, 0]] },
  }),
  S('dark.locking_curse', 'Maldição de Aprisionamento', 'Locking Curse', 'epic', {
    cost: 70, lvl: 18, req: ['dark.cursed_poison'], origin: O, mp: 45, cd: 30, cat: C,
    desc: 'Maldição que prende o alvo, drena suas forças e impede a regeneração.',
    fx: { k: 'bind', el: 'dark', range: 18, dur: 8, dps: 1, eff: [['weakness', 8, 2], ['mining_fatigue', 8, 2], ['wither', 8, 1]] },
  }),
  S('dark.death_streak', 'Sequência da Morte', 'Death Streak', 'epic', {
    cost: 110, lvl: 26, req: ['dark.shadow_manipulation', 'dark.locking_curse'], origin: O, mp: 80, cd: 45, cat: C,
    desc: 'Salta pelas sombras golpeando em sequência até 5 inimigos próximos.',
    fx: { k: 'streak', el: 'dark', r: 12, count: 5, dmg: 10 },
  }),
  S('dark.abyss_annihilation', 'Aniquilação do Abismo', 'Abyss Annihilation', 'legendary', {
    cost: 200, lvl: 36, req: ['dark.darkness_manipulation', 'dark.death_streak'], origin: O, major: true,
    mp: 160, cd: 120, cat: C,
    desc: 'Abre o abismo sobre a área mirada: dano devastador e wither intenso.',
    fx: { k: 'nova', el: 'dark', range: 24, r: 10, dmg: 30, eff: [['wither', 8, 2], ['darkness', 8, 0]] },
  }),
]);
