// Modelos e funções das árvores raciais (compartilhados por data/trees/racial.js e pelas
// árvores raciais completas em data/skill_trees/<linhagem>/).
import { S, defineTree } from './dsl.js';
import { RACES, LINEAGES } from './races.js';

export const RC = 'Racial';

// ── Modelos reutilizáveis (cada linhagem recebe IDs próprios) ──────────────
export const T = {
  selfRegen: (p, o = {}) => S(`${p}.self_regen`, 'Autorregeneração', 'Self-Regeneration', 'common', {
    cost: 25, lvl: 5, cat: `${RC} · Corpo`, ...o,
    desc: 'Regenera vida continuamente.', fx: { k: 'p_effect', eff: [['regeneration', o.amp ?? 0]] } }),
  regeneration: (p, o = {}) => S(`${p}.regeneration`, 'Regeneração', 'Regeneration', 'common', {
    cost: 25, lvl: 6, cat: `${RC} · Corpo`, ...o,
    desc: 'Corpo resistente que se recupera de ferimentos.', fx: { k: 'p_effect', eff: [['regeneration', 0]] } }),
  magicResist: (p, o = {}) => S(`${p}.magic_resist`, 'Resistência Mágica', 'Magic Resistance', 'rare', {
    cost: 40, lvl: 10, cat: `${RC} · Resistência`, ...o,
    desc: 'Reduz o dano mágico, de wither e de magias sombrias recebido.',
    fx: { k: 'p_resist', groups: { magic: o.amount ?? 0.3, dark: (o.amount ?? 0.3) * 0.7 } } }),
  physResist: (p, o = {}) => S(`${p}.physical_resist`, 'Resistência Física', 'Physical Resistance', 'rare', {
    cost: 40, lvl: 10, cat: `${RC} · Resistência`, ...o,
    desc: 'Reduz o dano físico recebido (golpes, flechas, quedas de blocos).',
    fx: { k: 'p_resist', groups: { physical: o.amount ?? 0.25 } } }),
  elemAffinity: (p, o = {}) => S(`${p}.elemental_affinity`, 'Afinidade Elemental', 'Elemental Affinity', 'rare', {
    cost: 35, lvl: 8, cat: `${RC} · Afinidade`, ...o,
    desc: 'Magias elementais custam menos e causam mais dano.',
    fx: [{ k: 'p_affinity', tree: 'elemental', mult: 0.85 }, { k: 'p_power', el: 'elemental', mult: 1.1 }] }),
  spiritAffinity: (p, o = {}) => S(`${p}.spirit_affinity`, 'Afinidade Espiritual', 'Spirit Affinity', 'rare', {
    cost: 35, lvl: 8, cat: `${RC} · Afinidade`, ...o,
    desc: 'Magias espirituais e de invocação custam menos.',
    fx: [{ k: 'p_affinity', tree: 'spirit', mult: 0.8 }, { k: 'p_affinity', tree: 'summon', mult: 0.9 }] }),
  thoughtComm: (p, o = {}) => S(`${p}.thought_comm`, 'Comunicação Mental', 'Thought Communication', 'common', {
    cost: 15, lvl: 2, mp: 5, cd: 10, cat: `${RC} · Mente`, ...o,
    desc: 'Contato telepático: mostra onde estão todos os jogadores do mundo e envia um aviso mental a eles.',
    fx: { k: 'comm' } }),
  ultraSmell: (p, o = {}) => S(`${p}.ultra_smell`, 'Olfato Aprimorado', 'Ultra Smell', 'common', {
    cost: 15, lvl: 3, mp: 4, cd: 15, cat: `${RC} · Sentidos`, ...o,
    desc: 'Fareja as criaturas ao redor e mostra direção e distância.', fx: { k: 'sense', r: 32 } }),
  coercion: (p, o = {}) => S(`${p}.coercion`, 'Intimidação', 'Coercion', 'common', {
    cost: 20, lvl: 5, mp: 10, cd: 20, cat: `${RC} · Mente`, ...o,
    desc: 'Intimida as criaturas próximas, enfraquecendo e retardando-as.',
    fx: { k: 'debuff', el: 'dark', r: 8, self: true, eff: [['weakness', 8, 1], ['slowness', 8, 1]] } }),
  physEnhance: (p, o = {}) => S(`${p}.physical_enhancement`, 'Fortalecimento Físico', 'Physical Enhancement', 'common', {
    cost: 25, lvl: 4, cat: `${RC} · Corpo`, ...o,
    desc: 'Músculos reforçados por Magicules: força e velocidade permanentes.',
    fx: { k: 'p_effect', eff: [['strength', o.amp ?? 0], ['speed', 0]] } }),
  nightVision: (p, name = 'Visão Noturna', original = 'Night Vision', o = {}) => S(`${p}.night_vision`, name, original, 'common', {
    cost: 10, lvl: 1, cat: `${RC} · Sentidos`, origin: 'adaptado', ...o,
    desc: 'Enxerga perfeitamente no escuro.', fx: { k: 'p_effect', eff: [['night_vision', 0]] } }),
  magiculeBoost: (p, name, original, o = {}) => S(`${p}.magicule_boost`, name, original, o.rarity ?? 'rare', {
    cost: 40, lvl: 10, cat: `${RC} · Magicules`, ...o,
    desc: `Aumenta a capacidade máxima de Magicules em ${o.max ?? 60} e a regeneração em ${o.regen ?? 1}/s.`,
    fx: { k: 'p_mp', max: o.max ?? 60, regen: o.regen ?? 1 } }),
  breath: (p, el, name, original, o = {}) => S(`${p}.${el}_breath`, name, original, o.rarity ?? 'rare', {
    cost: 45, lvl: 12, mp: 20, cd: 6, cat: `${RC} · Sopro`, ...o,
    desc: `Sopro de ${el === 'fire' ? 'fogo' : el === 'lightning' ? 'trovão' : 'gelo'} em cone à sua frente.`,
    fx: { k: 'cone', el, dmg: o.dmg ?? 8, range: o.range ?? 9, ignite: el === 'fire' ? 4 : 0,
      eff: el === 'ice' ? [['slowness', 4, 2]] : [] } }),
};

// Evolução: exige o nó anterior, nível e um número de habilidades da própria árvore.
export function evo(p, raceId, rarity, o) {
  const r = RACES[raceId];
  return S(`${p}.evo_${raceId}`, `Evolução: ${r.name}`, `Evolution: ${r.original}`, rarity, {
    major: true, type: 'evolution', evolveTo: raceId, origin: r.origin, cat: `${RC} · Evolução`, ...o,
    desc: `${r.desc}\nAo evoluir, sua raça passa a ser ${r.name}: Magicules ${r.mp}, regeneração ${r.regen}/s e novos atributos.`,
    fx: { k: 'evolve', race: raceId },
  });
}

export function root(p, raceId) {
  const r = RACES[raceId];
  return S(`${p}.root`, r.name, r.original, 'common', {
    cost: 0, lvl: 1, major: true, type: 'race_root', origin: r.origin, initial: raceId, cat: `${RC} · Raça`,
    desc: `${r.desc}\nNó da raça base: concedido ao escolher a raça.`, fx: { k: 'p_race' },
  });
}

export function lineageTree(lin, skills, links = []) {
  const L = LINEAGES[lin];
  const p = `r.${lin}`;
  return defineTree(`race.${lin}`, {
    name: `Árvore Racial · ${L.name}`, original: `Racial Tree · ${L.name}`, short: 'Racial', kind: 'racial',
    lineage: lin, root: `${p}.root`, category: RC,
  }, [root(p, L.base), ...skills], links);
}


