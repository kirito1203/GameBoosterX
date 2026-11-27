// SETOR V — HABILIDADES OBTIDAS POR ABSORÇÃO
// Todas exigem DESCOBERTA: absorver a criatura certa (ver absorption.js). A descoberta não
// desbloqueia: depois ainda é preciso pagar os pontos e cumprir os requisitos.
import { K, P } from './common.js';

const k = K('absorbed');
const d = { discover: true };

export default [
  k('shapeshift', 'Imitação (Mimetismo)', 'Mimicry', 'C', 'anime', {
    req: [P('predator')], lp: P('root'), lvl: 10, mp: 10, cd: 3, major: true,
    desc: 'Assume a forma de uma espécie absorvida e ganha suas características (ex.: lobo = velocidade, blaze = imunidade ao fogo). Base de todas as habilidades absorvidas. Consome 1 Magicule/s.',
    fx: { k: 'mimic', drain: 1 } }),
  k('sticky_thread', 'Fios Pegajosos', 'Sticky Thread', 'D', 'anime', {
    req: [P('shapeshift')], ...d, mp: 10, cd: 8, desc: 'Prende a criatura mirada (16 blocos) por 6 s.',
    fx: { k: 'bind', el: 'physical', range: 16, dur: 6, dps: 0, eff: [['slowness', 6, 3]] } }),
  k('steel_thread', 'Fios de Aço', 'Steel Thread', 'C', 'anime', {
    req: [P('sticky_thread')], ...d, mp: 14, cd: 5, desc: 'Fio cortante: atinge o alvo a até 20 blocos (12 de dano).',
    fx: { k: 'strike', el: 'physical', range: 20, dmg: 12 } }),
  k('body_armor', 'Armadura Corporal', 'Body Armor', 'D', 'anime', {
    req: [P('shapeshift')], ...d, desc: 'Escamas do Armorsaurus: Resistência I permanente e 10% menos dano físico.',
    fx: [{ k: 'p_effect', eff: [['resistance', 0]] }, { k: 'p_resist', groups: { physical: 0.1 } }] }),
  k('poison_breath', 'Sopro Venenoso', 'Poisonous Breath', 'D', 'anime', {
    req: [P('shapeshift')], ...d, mp: 15, cd: 8, desc: 'Cone venenoso de 8 blocos.',
    fx: { k: 'cone', el: 'acid', dmg: 6, range: 8, eff: [['poison', 6, 2]] } }),
  k('poison_resist', 'Resistência a Veneno', 'Poison Resistance', 'E', 'anime', {
    req: [P('poison_breath')], ...d, desc: 'Imunidade a veneno.', fx: { k: 'p_cleanse', eff: ['poison'] } }),
  k('heat_sense', 'Sensor de Calor', 'Heat Sense', 'E', 'anime', {
    req: [P('poison_breath')], ...d, mp: 4, cd: 10, desc: 'Detecta o calor dos seres vivos num raio de 32 blocos.', fx: { k: 'sense', r: 32 } }),
  k('paralyzing_breath', 'Sopro Paralisante', 'Paralyzing Breath', 'D', 'anime', {
    req: [P('shapeshift')], ...d, mp: 15, cd: 10, desc: 'Cone paralisante de 7 blocos: lentidão forte e cansaço.',
    fx: { k: 'cone', el: 'physical', dmg: 2, range: 7, eff: [['slowness', 5, 4], ['mining_fatigue', 5, 2]] } }),
  k('ultrasonic', 'Ondas Ultrassônicas', 'Ultrasound Waves', 'D', 'anime', {
    req: [P('shapeshift')], ...d, mp: 10, cd: 8, desc: 'Ondas que atordoam as criaturas num raio de 7.',
    fx: { k: 'debuff', el: 'wind', r: 7, self: true, dmg: 3, eff: [['nausea', 6, 0], ['slowness', 3, 1]] } }),
  k('vampirism', 'Vampirismo', 'Vampirism', 'C', 'anime', {
    req: [P('ultrasonic')], ...d, desc: '8% do dano corpo a corpo vira vida.', fx: { k: 'p_lifesteal', pct: 0.08 } }),
  k('flame_manip', 'Manipulação das Chamas', 'Flame Manipulation', 'C', 'pendente', {
    req: [P('shapeshift')], ...d, mp: 18, cd: 4, desc: 'Três rajadas de fogo herdadas de Ifrit.',
    fx: { k: 'bolt', el: 'fire', dmg: 9, ignite: 4, count: 3, spread: 12 } }),
  k('flame_body', 'Corpo de Chamas', 'Body of Flame', 'B', 'pendente', {
    req: [P('flame_manip')], ...d, mp: 30, cd: 60, desc: 'Por 30 s seus golpes queimam (+4 de dano) e você fica resistente ao fogo.',
    fx: [{ k: 'imbue', el: 'fire', dur: 30, dmg: 4, ignite: 4 }, { k: 'buff', eff: [['fire_resistance', 30, 0]] }] }),
  k('clone', 'Clonagem', 'Multiplicity', 'A', 'pendente', {
    req: [P('flame_body'), P('gluttony')], lp: P('flame_body'), ...d, mp: 60, cd: 90, desc: 'Cria 2 cópias suas que lutam por 30 s.',
    fx: { k: 'summon', entity: 'tensura:doppelganger', count: 2, dur: 30 } }),
  k('shadow_motion', 'Movimento das Sombras', 'Shadow Motion', 'C', 'anime', {
    req: [P('shapeshift')], ...d, mp: 12, cd: 5, desc: 'Mergulha nas sombras e reaparece até 14 blocos à frente.', fx: { k: 'blink', range: 14 } }),
  k('thought_comm', 'Comunicação Mental', 'Thought Communication', 'E', 'anime', {
    req: [P('shadow_motion')], ...d, mp: 5, cd: 10, desc: 'Contato telepático: mostra onde estão os jogadores e envia um aviso mental.', fx: { k: 'comm' } }),
  k('ultra_smell', 'Superolfato', 'Super Sense of Smell', 'E', 'anime', {
    req: [P('shadow_motion')], ...d, mp: 4, cd: 15, desc: 'Fareja as criaturas num raio de 32 blocos.', fx: { k: 'sense', r: 32 } }),
  k('coercion', 'Intimidação', 'Coercion', 'D', 'anime', {
    req: [P('shadow_motion')], ...d, mp: 10, cd: 20, desc: 'Intimida as criaturas num raio de 8: fraqueza e lentidão.',
    fx: { k: 'debuff', el: 'dark', r: 8, self: true, eff: [['weakness', 8, 1], ['slowness', 8, 1]] } }),
  k('black_lightning', 'Relâmpago Negro', 'Black Lightning', 'B', 'anime', {
    req: [P('shadow_motion'), P('gluttony')], lp: P('shadow_motion'), ...d, mp: 30, cd: 8, desc: 'Raio negro sobre o alvo a até 28 blocos (16 de dano).',
    fx: { k: 'strike', el: 'lightning', range: 28, dmg: 16 } }),
];
