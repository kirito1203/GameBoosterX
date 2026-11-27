import { S, defineTree } from '../dsl.js';

const O = 'adaptado';
const C = 'Magia de Ilusão';

export default defineTree('illusion', {
  name: 'Árvore de Ilusão', original: 'Illusion Magic', short: 'Ilusão', kind: 'universal',
  root: 'illu.mirage', category: C,
}, [
  S('illu.mirage', 'Miragem', 'Mirage', 'common', {
    cost: 20, lvl: 3, origin: O, major: true, mp: 12, cd: 10, cat: C,
    desc: 'Deixa uma imagem ilusória para trás: você recua alguns blocos e cega os inimigos próximos.',
    fx: [{ k: 'blink', range: -5 }, { k: 'debuff', el: 'illusion', r: 5, self: true, eff: [['blindness', 3, 0]] }],
  }),
  S('illu.invisible', 'Invisibilidade', 'Invisible', 'rare', {
    cost: 35, lvl: 8, req: ['illu.mirage'], arm: 'N', origin: O, mp: 20, cd: 40, cat: C,
    desc: 'Torna você invisível por 30 segundos.',
    fx: { k: 'buff', eff: [['invisibility', 30, 0]] },
  }),
  S('illu.confusion', 'Confusão', 'Confusion', 'common', {
    cost: 25, lvl: 6, req: ['illu.mirage'], arm: 'E', origin: O, mp: 16, cd: 12, cat: C,
    desc: 'Confunde as criaturas na área mirada (náusea, cegueira e lentidão).',
    fx: { k: 'debuff', el: 'illusion', range: 16, r: 6, eff: [['nausea', 8, 0], ['blindness', 3, 0], ['slowness', 5, 1]] },
  }),
  S('illu.hypnos', 'Sono', 'Hypnos', 'rare', {
    cost: 45, lvl: 12, req: ['illu.confusion'], origin: O, mp: 30, cd: 25, cat: C,
    desc: 'Faz as criaturas na área adormecerem: imóveis e sem força para atacar.',
    fx: { k: 'debuff', el: 'illusion', range: 16, r: 5, eff: [['slowness', 6, 255], ['weakness', 6, 255], ['blindness', 6, 0]] },
  }),
  S('illu.mental_crush', 'Esmagamento Mental', 'Mental Crush', 'epic', {
    cost: 90, lvl: 22, req: ['illu.hypnos'], origin: O, major: true, mp: 55, cd: 20, cat: C,
    desc: 'Ataque direto à mente do alvo: dano alto, náusea, fraqueza e perda de Magicules (jogadores).',
    fx: { k: 'beam', el: 'illusion', dmg: 14, range: 20, eff: [['nausea', 10, 0], ['weakness', 10, 2]], mpBurn: 40 },
  }),
]);
