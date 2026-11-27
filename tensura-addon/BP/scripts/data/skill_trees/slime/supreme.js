// SETOR VII — HABILIDADES SUPREMAS: URIEL e VELDORA.
// Raphael (setor IV) e Beelzebuth (setor III) também são Supremas (moldura dourada) e ficam
// nos seus ramos para não duplicar habilidades.
import { K, P } from './common.js';

const k = K('supreme');

export default [
  k('infinity_prison', 'Prisão Ilimitada', 'Unlimited Imprisonment', 'S', 'ln', {
    req: [P('raphael')], lp: P('root'), mp: 80, cd: 120, major: true,
    desc: 'Aprisiona o alvo mirado (24 blocos) por 12 s. Base da evolução para Uriel.',
    fx: { k: 'bind', el: 'space', range: 24, dur: 12, dps: 0, eff: [['slowness', 12, 6], ['weakness', 12, 3]] } }),
  k('uriel', 'Uriel', 'Uriel, Lord of Vows', 'SS', 'ln', {
    req: [P('infinity_prison'), P('beelzebuth')], lp: P('infinity_prison'), needMp: 1200, needStage: 1, mp: 120, cd: 180, major: true,
    desc: 'Senhor dos Votos: defesa absoluta por 20 s para você e aliados (10 blocos).',
    fx: { k: 'buff', allies: 10, eff: [['absorption', 20, 5], ['resistance', 20, 3], ['fire_resistance', 20, 0]] } }),
  k('spatial_domination', 'Dominação Espacial', 'Spatial Domination', 'A', 'ln', {
    req: [P('uriel')], mp: 90, cd: 120, desc: 'Domina o espaço (raio 10) por 15 s: inimigos não saem e ficam lentos.',
    fx: { k: 'field', el: 'space', r: 10, dur: 15, self: true, confine: true, enemyEff: [['slowness', 2, 2]] } }),
  k('absolute_defense', 'Defesa Absoluta', 'Absolute Defense', 'S', 'ln', {
    req: [P('uriel')], mp: 100, cd: 150, desc: 'Por 6 s você praticamente não recebe dano (Resistência V).',
    fx: { k: 'buff', eff: [['resistance', 6, 4]] } }),
  k('law_manip', 'Manipulação de Leis', 'Law Manipulation', 'S', 'ln', {
    req: [P('uriel')], mp: 110, cd: 150, desc: 'Reescreve as leis ao redor (raio 12, 8 s): inimigos fracos, lentos e cansados.',
    fx: { k: 'field', el: 'space', r: 12, dur: 8, self: true, enemyEff: [['weakness', 2, 2], ['slowness', 2, 2], ['mining_fatigue', 2, 2]] } }),
  k('storm_bond', 'Vínculo com Veldora', 'Bond with Veldora', 'A', 'anime', {
    req: [P('stomach')], lp: P('root'), lvl: 20, major: true,
    desc: 'Veldora selado no seu Estômago: magias de raio e vento 20% mais fortes e +80 Magicules máximas.',
    fx: [{ k: 'p_power', el: 'lightning', mult: 1.2 }, { k: 'p_power', el: 'wind', mult: 1.2 }, { k: 'p_mp', max: 80 }] }),
  k('veldora', 'Veldora', 'Veldora, Lord of Storms', 'SS', 'ln', {
    req: [P('storm_bond'), P('raphael')], lp: P('storm_bond'), mission: 'm.slime.veldora', mp: 140, cd: 120, major: true,
    desc: 'Senhor da Tempestade: tempestade de raios sobre a área mirada (5 ondas, 16 de dano).',
    fx: { k: 'rain', el: 'lightning', dmg: 16, r: 6, waves: 5, every: 8, range: 32 } }),
  k('soul_corridor', 'Corredor de Almas', 'Soul Corridor', 'A', 'ln', {
    req: [P('veldora')], mp: 40, cd: 30, desc: 'Agachado: marca um ponto. Em pé: volta a ele por qualquer distância/dimensão.', fx: { k: 'warp' } }),
  k('storm_dragon', 'Invocação do Dragão da Tempestade', 'Storm Dragon Summon', 'S', 'ln', {
    req: [P('veldora')], mp: 160, cd: 240, desc: 'Invoca uma manifestação elemental da tempestade por 60 s (modelo: Grande Elemental).',
    fx: { k: 'summon', entity: 'tensura:greater_elemental', count: 1, dur: 60 } }),
  k('true_dragon_release', 'Liberação do Dragão Verdadeiro', 'True Dragon Release', 'SS', 'ln', {
    req: [P('storm_dragon')], mp: 250, cd: 300, desc: 'Liberta o poder de Veldora: explosão de raios (raio 8, 20 de dano) e força/velocidade/resistência por 40 s.',
    fx: [{ k: 'nova', el: 'lightning', r: 8, dmg: 20, self: true, knock: 2 }, { k: 'buff', eff: [['strength', 40, 3], ['speed', 40, 2], ['resistance', 40, 2]] }] }),
];
