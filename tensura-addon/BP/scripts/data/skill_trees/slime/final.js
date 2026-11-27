// SETOR VIII — PODERES FINAIS (bloqueados até o fim da progressão).
// Azathoth é da Light Novel. Os nomes internos e Shub-Niggurath/Ultimate Slime não puderam ser
// confirmados nesta versão: marcados como PENDENTE DE VERIFICAÇÃO.
import { K, P } from './common.js';

const k = K('final');

export default [
  k('azathoth', 'Azathoth', 'Azathoth, God of the Void', 'EX', 'ln', {
    req: [P('beelzebuth'), P('uriel'), P('ciel')], lp: P('root'), mission: 'm.slime.void', needMp: 2000, major: true,
    desc: 'Deus do Vazio: +400 Magicules máximas, +5/s de regeneração e todas as magias 20% mais fortes.',
    fx: [{ k: 'p_mp', max: 400, regen: 5 }, { k: 'p_power', el: '*', mult: 1.2 }] }),
  k('nihility_collapse', 'Colapso da Nihilidade', 'Nihility Collapse', 'SS', 'pendente', {
    req: [P('azathoth')], mp: 300, cd: 240, desc: 'Feixe do vazio que atravessa tudo em linha (60 de dano, 48 blocos).',
    fx: { k: 'beam', el: 'soul', dmg: 60, range: 48, pierce: true } }),
  k('imaginary_space', 'Espaço Imaginário', 'Imaginary Space', 'SS', 'pendente', {
    req: [P('azathoth')], mp: 250, cd: 240, desc: 'Isola a área (raio 14) num espaço à parte por 10 s: inimigos congelados.',
    fx: { k: 'field', el: 'space', r: 14, dur: 10, self: true, freeze: true, enemyEff: [['slowness', 1, 255], ['weakness', 1, 255]] } }),
  k('parallel_existence', 'Existência Paralela Múltipla', 'Multiple Parallel Existence', 'SS', 'pendente', {
    req: [P('azathoth'), P('clone')], lp: P('azathoth'), mp: 220, cd: 240, desc: 'Manifesta 4 existências paralelas suas por 60 s.',
    fx: { k: 'summon', entity: 'tensura:doppelganger', count: 4, dur: 60 } }),
  k('spacetime', 'Domínio Espaçotemporal', 'Spacetime Domination', 'EX', 'pendente', {
    req: [P('imaginary_space')], mp: 350, cd: 300, desc: 'Para o tempo ao redor (raio 16) por 8 s.',
    fx: { k: 'field', el: 'space', r: 16, dur: 8, self: true, freeze: true, enemyEff: [['slowness', 1, 255], ['weakness', 1, 255], ['mining_fatigue', 1, 4]] } }),
  k('multidim_barrier', 'Barreira Multidimensional', 'Multidimensional Barrier', 'SS', 'pendente', {
    req: [P('azathoth'), P('multilayer_barrier')], lp: P('azathoth'), mp: 200, cd: 240, desc: 'Barreira para você e aliados (12 blocos) por 30 s: absorção e Resistência IV.',
    fx: { k: 'buff', allies: 12, eff: [['absorption', 30, 8], ['resistance', 30, 3]] } }),
  k('dragon_release', 'Liberação de Dragões Verdadeiros', 'True Dragon Liberation', 'SS', 'pendente', {
    req: [P('azathoth'), P('true_dragon_release')], lp: P('multidim_barrier'), mp: 300, cd: 300, desc: 'Liberta 3 manifestações dracônicas elementais por 60 s.',
    fx: { k: 'summon', entity: 'tensura:greater_elemental', count: 3, dur: 60 } }),
  k('dragon_nucleation', 'Nucleação de Dragões Verdadeiros', 'True Dragon Nucleation', 'EX', 'pendente', {
    req: [P('dragon_release')], desc: 'Núcleo dracônico: +300 Magicules máximas e magias dracônicas/de raio 30% mais fortes.',
    fx: [{ k: 'p_mp', max: 300 }, { k: 'p_power', el: 'dragon', mult: 1.3 }, { k: 'p_power', el: 'lightning', mult: 1.3 }] }),
  k('shub', 'Shub-Niggurath', 'Shub-Niggurath, Lord of Abundance', 'EX', 'pendente', {
    req: [P('ciel')], lp: P('root'), mission: 'm.slime.abundance', needMp: 1800, major: true,
    desc: 'Senhor da Abundância: habilidades custam 20% menos pontos (todas as árvores).', fx: { k: 'p_points', mult: 0.8 } }),
  k('skill_creation', 'Criação de Habilidades', 'Skill Creation', 'SS', 'pendente', {
    req: [P('shub')], desc: '+20% de EP (mais pontos para criar novas habilidades).', fx: { k: 'p_xp', mult: 1.2 } }),
  k('skill_duplication', 'Duplicação de Habilidades', 'Skill Duplication', 'SS', 'pendente', {
    req: [P('shub')], desc: 'Recarga de todas as habilidades 15% menor.', fx: { k: 'p_cd', mult: 0.85 } }),
  k('skill_grant', 'Concessão de Habilidades', 'Skill Bestowal', 'SS', 'pendente', {
    req: [P('shub')], mp: 150, cd: 180, desc: 'Concede poder aos aliados (16 blocos) por 60 s: força III, resistência II e regeneração II.',
    fx: { k: 'buff', allies: 16, eff: [['strength', 60, 2], ['resistance', 60, 1], ['regeneration', 60, 1]] } }),
  k('skill_storage', 'Armazenamento de Habilidades', 'Skill Storage', 'SS', 'pendente', {
    req: [P('shub')], desc: '+3 espaços na Barra de Habilidades (até 8).', fx: { k: 'p_slots', n: 3 } }),
  k('ultimate_slime', 'Forma Ultimate Slime', 'Ultimate Slime', 'EX', 'pendente', {
    req: [P('azathoth'), P('shub'), P('evo_god_slime')], lp: P('shub'), needMp: 2500,
    desc: 'Forma final do Slime: +8 corações, Resistência II e +500 Magicules máximas.',
    fx: [{ k: 'p_effect', eff: [['health_boost', 3], ['resistance', 1]] }, { k: 'p_mp', max: 500 }] }),
];
