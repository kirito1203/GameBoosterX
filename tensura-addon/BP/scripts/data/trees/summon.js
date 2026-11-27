import { S, defineTree } from '../dsl.js';

const O = 'adaptado';
const C = 'Invocação';

export default defineTree('summon', {
  name: 'Árvore de Invocação', original: 'Summoning Magic', short: 'Invoc.', kind: 'universal',
  root: 'summon.familiar', category: C,
}, [
  S('summon.familiar', 'Invocar Familiar', 'Summon Familiar', 'common', {
    cost: 15, lvl: 3, origin: O, major: true, mp: 15, cd: 60, cat: C,
    desc: 'Invoca um familiar voador por 120 s. Enquanto ele existir, você tem visão noturna e é avisado de monstros próximos.',
    fx: { k: 'summon', entity: 'tensura:familiar', count: 1, dur: 120 },
  }),
  S('summon.hound_dog', 'Invocar Cão', 'Summon Hound Dog', 'common', {
    cost: 25, lvl: 6, req: ['summon.familiar'], arm: 'E', origin: O, mp: 25, cd: 45, cat: C,
    desc: 'Invoca dois cães de caça que seguem você e atacam seus inimigos por 60 s.',
    fx: { k: 'summon', entity: 'tensura:hound_dog', count: 2, dur: 60 },
  }),
  S('summon.basilisk', 'Invocar Basilisco', 'Summon Basilisk', 'rare', {
    cost: 50, lvl: 14, req: ['summon.hound_dog'], origin: O, mp: 45, cd: 75, cat: C,
    desc: 'Invoca um basilisco por 60 s. Suas mordidas petrificam (lentidão e fadiga).',
    fx: { k: 'summon', entity: 'tensura:basilisk', count: 1, dur: 60 },
  }),
  S('summon.daemon', 'Invocar Demônio', 'Summon Daemon', 'epic', {
    cost: 90, lvl: 20, req: ['summon.familiar'], arm: 'W', origin: O, mp: 70, cd: 100, cat: C,
    desc: 'Invoca um demônio menor poderoso por 60 s.',
    fx: { k: 'summon', entity: 'tensura:daemon', count: 1, dur: 60 },
  }),
  S('summon.spirit', 'Invocar Espírito', 'Summon Spirit', 'rare', {
    cost: 35, lvl: 8, req: ['summon.familiar'], arm: 'N', origin: O, mp: 30, cd: 60, cat: C,
    desc: 'Invoca um espírito por 90 s que cura você periodicamente.',
    fx: { k: 'summon', entity: 'tensura:spirit', count: 1, dur: 90 },
  }),
  S('summon.medium_elemental', 'Invocar Elemental Médio', 'Summon Medium Elemental', 'rare', {
    cost: 55, lvl: 15, req: ['summon.spirit'], origin: O, mp: 50, cd: 75, cat: C,
    desc: 'Invoca um elemental de fogo por 60 s que dispara bolas de fogo nos inimigos.',
    fx: { k: 'summon', entity: 'tensura:medium_elemental', count: 1, dur: 60 },
  }),
  S('summon.greater_elemental', 'Invocar Elemental Superior', 'Summon Greater Elemental', 'epic', {
    cost: 110, lvl: 26, req: ['summon.medium_elemental'], origin: O, major: true, mp: 90, cd: 120, cat: C,
    desc: 'Invoca um elemental de terra colossal por 90 s.',
    fx: { k: 'summon', entity: 'tensura:greater_elemental', count: 1, dur: 90 },
  }),
], [
  { id: 'necro.summon_undead', arm: 'S' },
]);
