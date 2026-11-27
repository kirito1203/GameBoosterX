import { S, defineTree } from '../dsl.js';

const O = 'adaptado';
const C = 'Magia Aspectual';

export default defineTree('aspect', {
  name: 'Árvore Aspectual', original: 'Aspect Magic', short: 'Aspect.', kind: 'universal',
  root: 'aspect.magic_sense', category: C,
}, [
  S('aspect.magic_sense', 'Percepção Mágica', 'Magic Sense', 'common', {
    cost: 10, lvl: 1, origin: 'obra', major: true, cat: C,
    desc: 'Percebe o ambiente pelas Magicules: visão noturna permanente.',
    fx: { k: 'p_effect', eff: [['night_vision', 0]] },
  }),

  // ── DEFESA (leste) ──────────────────────────────────────────
  S('aspect.barrier', 'Barreira', 'Barrier', 'common', {
    cost: 20, lvl: 3, req: ['aspect.magic_sense'], arm: 'E', origin: O, mp: 15, cd: 20, cat: `${C} · Defesa`,
    desc: 'Envolve você em uma barreira que absorve dano.',
    fx: { k: 'buff', eff: [['absorption', 30, 1], ['resistance', 10, 0]] },
  }),
  S('aspect.reinforced_barrier', 'Barreira Reforçada', 'Reinforced Barrier', 'rare', {
    cost: 40, lvl: 10, req: ['aspect.barrier'], origin: O, mp: 30, cd: 30, cat: `${C} · Defesa`,
    desc: 'Barreira de múltiplas camadas: grande absorção e resistência ao fogo.',
    fx: { k: 'buff', eff: [['absorption', 30, 3], ['resistance', 15, 1], ['fire_resistance', 15, 0]] },
  }),
  S('aspect.magic_wall', 'Parede Mágica', 'Magic Wall', 'rare', {
    cost: 35, lvl: 12, req: ['aspect.reinforced_barrier'], origin: O, mp: 25, cd: 15, cat: `${C} · Defesa`,
    desc: 'Ergue uma parede translúcida de energia que bloqueia passagem e projéteis.',
    fx: { k: 'wall', block: 'minecraft:light_blue_stained_glass', len: 7, h: 3, dist: 3, dur: 15 },
  }),
  S('aspect.anti_magic_area', 'Área Antimagia', 'Anti-Magic Area', 'epic', {
    cost: 90, lvl: 22, req: ['aspect.reinforced_barrier'], origin: O, major: true, mp: 60, cd: 60, cat: `${C} · Defesa`,
    desc: 'Campo onde outros jogadores não conseguem conjurar e criaturas hostis ficam enfraquecidas.',
    fx: { k: 'field', el: 'space', r: 8, dur: 15, self: false, antimagic: true, enemyEff: [['weakness', 2, 2], ['slowness', 2, 1]] },
  }),
  S('aspect.protection', 'Proteção', 'Protection', 'common', {
    cost: 25, lvl: 5, req: ['aspect.barrier'], origin: O, mp: 18, cd: 25, cat: `${C} · Defesa`,
    desc: 'Concede resistência a você e aos aliados próximos.',
    fx: { k: 'buff', allies: 6, eff: [['resistance', 20, 1]] },
  }),

  // ── PERCEPÇÃO e MENTE (norte) ───────────────────────────────
  S('aspect.analyze', 'Análise', 'Analyze', 'common', {
    cost: 10, lvl: 1, req: ['aspect.magic_sense'], arm: 'N', origin: O, mp: 4, cd: 1, cat: `${C} · Percepção`,
    desc: 'Analisa a criatura mirada: vida, tipo, efeitos e EP que ela concede.',
    fx: { k: 'analyze', range: 24 },
  }),
  S('aspect.clairvoyance', 'Clarividência', 'Clairvoyance', 'rare', {
    cost: 35, lvl: 10, req: ['aspect.analyze'], origin: O, mp: 20, cd: 20, cat: `${C} · Percepção`,
    desc: 'Revela todas as criaturas num raio amplo e concede visão noturna.',
    fx: [{ k: 'sense', r: 48 }, { k: 'buff', eff: [['night_vision', 30, 0]] }],
  }),
  S('aspect.dominate', 'Dominação', 'Dominate', 'rare', {
    cost: 50, lvl: 15, req: ['aspect.analyze'], origin: O, mp: 35, cd: 30, cat: `${C} · Mente`,
    desc: 'Domina a criatura mirada: ela fica incapaz de atacar. Animais domesticáveis são domados.',
    fx: { k: 'dominate', range: 16, dur: 20 },
  }),
  S('aspect.demon_dominate', 'Dominação Demoníaca', 'Demon Dominate', 'epic', {
    cost: 90, lvl: 22, req: ['aspect.dominate'], origin: O, mp: 70, cd: 60, cat: `${C} · Mente`,
    desc: 'Domina todas as criaturas num raio ao redor do ponto mirado.',
    fx: { k: 'dominate', range: 16, r: 6, dur: 25 },
  }),
  S('aspect.demon_marionette', 'Marionete Demoníaca', 'Demon Marionette', 'epic', {
    cost: 110, lvl: 28, req: ['aspect.demon_dominate'], origin: O, major: true, mp: 80, cd: 60, cat: `${C} · Mente`,
    desc: 'Transforma uma criatura enfraquecida (até 40% de vida) em uma marionete aliada.',
    fx: { k: 'marionette', range: 16, hpPct: 0.4, dur: 60, count: 1 },
  }),
  S('aspect.possession', 'Possessão', 'Possession', 'epic', {
    cost: 80, lvl: 20, req: ['aspect.dominate'], origin: O, mp: 40, cd: 30, cat: `${C} · Mente`,
    desc: 'Troca de lugar com o alvo, que fica atordoado, enquanto você some por instantes.',
    fx: { k: 'swap', range: 20, stun: 4 },
  }),
  S('aspect.doppelganger', 'Cópia', 'Doppelganger', 'epic', {
    cost: 90, lvl: 22, req: ['illu.mirage'], origin: O, mp: 50, cd: 45, cat: `${C} · Mente`,
    desc: 'Cria uma cópia sua que atrai a atenção dos monstros.',
    fx: { k: 'summon', entity: 'tensura:doppelganger', count: 1, dur: 30 },
  }),

  // ── MOVIMENTO e ESPAÇO (oeste) ──────────────────────────────
  S('aspect.float', 'Flutuação', 'Float', 'common', {
    cost: 20, lvl: 4, req: ['aspect.magic_sense'], arm: 'W', origin: O, mp: 10, cd: 8, cat: `${C} · Movimento`,
    desc: 'Eleva você brevemente e suaviza a queda.',
    fx: { k: 'buff', eff: [['levitation', 2, 0], ['slow_falling', 20, 0]] },
  }),
  S('aspect.flight', 'Voo', 'Flight', 'epic', {
    cost: 70, lvl: 16, req: ['aspect.float'], origin: O, major: true, mp: 15, cd: 3, cat: `${C} · Movimento`,
    desc: 'Liga/desliga o voo mágico. Olhe para cima para subir, à frente para planar, agache para descer. Consome Magicules por segundo.',
    fx: { k: 'flight', drain: 3 },
  }),
  S('aspect.spatial_storage', 'Armazenamento Espacial', 'Spatial Storage', 'rare', {
    cost: 50, lvl: 14, req: ['aspect.float'], origin: O, mp: 5, cd: 1, cat: `${C} · Espaço`,
    desc: 'Guarda o item da mão em um espaço dimensional próprio (18 espaços) e permite retirá-lo depois.',
    fx: { k: 'storage', slots: 18 },
  }),
  S('aspect.warp_portal', 'Portal Espacial', 'Warp Portal', 'epic', {
    cost: 90, lvl: 20, req: ['aspect.spatial_storage', 'aspect.flight'], origin: O, mp: 50, cd: 60, cat: `${C} · Espaço`,
    desc: 'Agachado: marca um ponto de retorno. Em pé: abre um portal e teleporta você para o ponto marcado.',
    fx: { k: 'warp' },
  }),

  // ── CORPO e VIDA (sul) ──────────────────────────────────────
  S('aspect.healing', 'Cura', 'Healing', 'common', {
    cost: 15, lvl: 2, req: ['aspect.magic_sense'], arm: 'S', origin: O, mp: 12, cd: 6, cat: `${C} · Vida`,
    desc: 'Recupera vida sua e, com metade da força, dos aliados próximos.',
    fx: { k: 'heal', amount: 6, allies: 4 },
  }),
  S('aspect.antidote', 'Antídoto', 'Antidote', 'common', {
    cost: 20, lvl: 5, req: ['aspect.healing'], origin: O, mp: 8, cd: 10, cat: `${C} · Vida`,
    desc: 'Remove venenos, wither e outros efeitos negativos.',
    fx: { k: 'cleanse', negative: true, allies: 4 },
  }),
  S('aspect.full_recovery', 'Recuperação Completa', 'Full Recovery', 'epic', {
    cost: 100, lvl: 24, req: ['aspect.healing', 'aspect.antidote'], origin: O, major: true, mp: 80, cd: 90, cat: `${C} · Vida`,
    desc: 'Restaura toda a vida, remove efeitos negativos e concede regeneração.',
    fx: [{ k: 'heal', full: true }, { k: 'cleanse', negative: true }, { k: 'buff', eff: [['regeneration', 10, 1]] }],
  }),
  S('aspect.drainage', 'Drenagem', 'Drainage', 'rare', {
    cost: 40, lvl: 12, req: ['aspect.healing'], origin: O, mp: 18, cd: 6, cat: `${C} · Vida`,
    desc: 'Drena vida e Magicules do alvo mirado.',
    fx: { k: 'drain', el: 'dark', range: 12, dmg: 6, heal: 1.0, mpGain: 10 },
  }),
  S('aspect.reinforcement', 'Fortalecimento', 'Reinforcement', 'common', {
    cost: 20, lvl: 4, req: ['aspect.healing'], origin: O, mp: 15, cd: 30, cat: `${C} · Corpo`,
    desc: 'Reforça o corpo com Magicules: força e velocidade.',
    fx: { k: 'buff', eff: [['strength', 30, 1], ['speed', 30, 0]] },
  }),
], [
  // Nós compartilhados exibidos nesta árvore (pertencem a outras árvores).
  { id: 'elem.freeze', arm: 'S' },
  { id: 'illu.mirage', arm: 'N' },
  { id: 'illu.invisible', arm: 'N' },
  { id: 'illu.confusion', arm: 'N' },
  { id: 'illu.hypnos', arm: 'N' },
  { id: 'illu.mental_crush', arm: 'N' },
]);
