// SETOR VI — MAGIAS E TÉCNICAS PESSOAIS DO RIMURU
// Separada das árvores universais de magia (que continuam valendo para as outras raças).
// Fundidas para não duplicar: "Passo das Sombras" = Movimento das Sombras, "Comunicação Telepática" =
// Comunicação Mental, "Relâmpago Negro" e "Manipulação das Chamas" (setor V), "Controle de Leis" =
// Manipulação de Leis (setor VII).
import { K, P } from './common.js';

const k = K('tech');

export default [
  k('water_manip', 'Manipulação da Água', 'Water Manipulation', 'E', 'anime', {
    req: [P('root')], lp: P('root'), lvl: 3, mp: 4, cd: 1, major: true, desc: 'Projétil de água pressurizada (5 de dano).',
    fx: { k: 'bolt', el: 'water', dmg: 5 } }),
  k('water_blade', 'Lâmina de Água', 'Water Blade', 'D', 'anime', {
    req: [P('water_manip')], mp: 10, cd: 2, desc: 'Lâmina d\'água cortante em linha (9 de dano, 18 blocos).',
    fx: { k: 'beam', el: 'water', dmg: 9, range: 18 } }),
  k('molecular', 'Manipulação Molecular', 'Molecular Manipulation', 'C', 'anime', {
    req: [P('water_manip'), P('great_sage')], lp: P('water_manip'), mp: 20, cd: 12, desc: 'Agarra o alvo a 20 blocos e o imobiliza por 5 s.',
    fx: { k: 'bind', el: 'gravity', range: 20, dur: 5, dps: 1, eff: [['slowness', 5, 4]] } }),
  k('optical', 'Controle Óptico', 'Light Manipulation', 'C', 'pendente', {
    req: [P('molecular')], mp: 20, cd: 45, desc: 'Dobra a luz por 20 s: invisibilidade e visão noturna.',
    fx: { k: 'buff', eff: [['invisibility', 20, 0], ['night_vision', 20, 0]] } }),
  k('megiddo', 'Megiddo', "God's Wrath: Megiddo", 'A', 'anime', {
    req: [P('water_blade'), P('optical')], lp: P('optical'), needMp: 500, mp: 150, cd: 120,
    desc: 'Feixes de luz concentrados por gotas d\'água caem sobre a área mirada (8 ondas, 18 de dano).',
    fx: { k: 'rain', el: 'holy', dmg: 18, r: 4, waves: 8, every: 5, range: 40 } }),
  k('black_flame', 'Chama Negra', 'Black Flame', 'B', 'pendente', {
    req: [P('flame_manip'), P('water_blade')], lp: P('water_blade'), mp: 35, cd: 10, desc: 'Cone de chamas negras (14 de dano, queima e wither).',
    fx: { k: 'cone', el: 'dark', dmg: 14, range: 9, ignite: 6, eff: [['wither', 4, 1]] } }),
  k('black_thunder', 'Trovão Negro', 'Black Thunder', 'A', 'pendente', {
    req: [P('black_lightning'), P('black_flame')], lp: P('black_flame'), mp: 70, cd: 30, desc: 'Tempestade de raios negros na área mirada (3 ondas, 12 de dano).',
    fx: { k: 'rain', el: 'lightning', dmg: 12, r: 5, waves: 3, every: 10, range: 28 } }),
  k('barrier_control', 'Controle de Barreiras', 'Barrier Control', 'C', 'pendente', {
    req: [P('water_manip')], mp: 25, cd: 40, desc: 'Barreira pessoal por 20 s: absorção e resistência.',
    fx: { k: 'buff', eff: [['absorption', 20, 2], ['resistance', 20, 1]] } }),
  k('multilayer_barrier', 'Barreira Múltipla', 'Multilayer Barrier', 'B', 'anime', {
    req: [P('barrier_control')], mp: 50, cd: 90, desc: 'Barreira em camadas para você e aliados (8 blocos) por 30 s.',
    fx: { k: 'buff', allies: 8, eff: [['absorption', 30, 4], ['resistance', 30, 2]] } }),
  k('spatial_motion', 'Movimento Espacial', 'Spatial Motion', 'A', 'anime', {
    req: [P('shadow_motion'), P('barrier_control')], lp: P('barrier_control'), mp: 30, cd: 6, desc: 'Teleporte instantâneo até 40 blocos à frente.',
    fx: { k: 'blink', range: 40 } }),
  k('potion', 'Síntese de Poções', 'Recovery Potion Production', 'D', 'anime', {
    req: [P('synthesis')], lp: P('water_manip'), mp: 30, cd: 40, desc: 'Poção de recuperação aplicada na hora: cura 10 e regeneração por 10 s.',
    fx: [{ k: 'heal', amount: 10 }, { k: 'buff', eff: [['regeneration', 10, 1]] }] }),
  k('full_potion', 'Síntese de Poções Completas', 'Full Potion', 'B', 'anime', {
    req: [P('potion')], mp: 90, cd: 180, desc: 'Poção Completa: cura total, remove efeitos negativos de você e dos aliados (8 blocos).',
    fx: [{ k: 'heal', full: true, amount: 40, allies: 8, allyFull: true }, { k: 'cleanse', negative: true, allies: 8 }] }),
  k('spirit_transfer', 'Transferência Espiritual', 'Spirit Transfer', 'A', 'pendente', {
    req: [P('thought_comm'), P('molecular')], lp: P('molecular'), mp: 60, cd: 60, desc: 'Projeta o espírito no alvo (12 blocos): controla e fere por 8 s.',
    fx: { k: 'possess', range: 12, dur: 8, dps: 3 } }),
  k('magic_control', 'Controle de Magia', 'Magic Manipulation', 'B', 'pendente', {
    req: [P('chant_annul'), P('water_blade')], lp: P('water_blade'), desc: 'Magias 8% mais baratas e 5% mais fortes.',
    fx: [{ k: 'p_affinity', tree: '*', mult: 0.92 }, { k: 'p_power', el: '*', mult: 1.05 }] }),
  k('aura_control', 'Controle de Aura', 'Aura Manipulation', 'B', 'pendente', {
    req: [P('magic_control')], desc: '+120 de Aura máxima e +2/s de regeneração de Aura.', fx: { k: 'p_aura', max: 120, regen: 2 } }),
  k('elemental_shift', 'Alteração Elemental', 'Elemental Alteration', 'A', 'pendente', {
    req: [P('magic_control'), P('black_flame')], lp: P('magic_control'), mp: 40, cd: 45, desc: 'Por 30 s seus golpes carregam energia dracônica (+7 de dano).',
    fx: { k: 'imbue', el: 'dragon', dur: 30, dmg: 7 } }),
];
