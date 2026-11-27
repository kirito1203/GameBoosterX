import { SR, defineTree } from '../dsl.js';
import { CONFIG } from '../../config.js';

// Combate e Armas (árvore UNIVERSAL, aberta a todas as raças). Sistema de RPG do addon:
// origem MOD_EXPANSION (técnicas genéricas, não são habilidades nomeadas da obra).
const C = 'Combate e Armas';
const k = (id, name, original, rank, o = {}) => SR(`cbt.${id}`, name, original, rank, 'mod', CONFIG.ranks, { cat: `${C} · ${o.sub ?? 'Geral'}`, ...o });

export default defineTree('combat', {
  name: 'Combate e Armas', original: 'Combat & Weapons', short: 'Combate', kind: 'universal', root: 'cbt.training', category: C,
}, [
  k('training', 'Treinamento de Combate', 'Combat Training', 'F', { major: true, lvl: 1, sub: 'Base',
    desc: 'Base de todas as técnicas: 5% mais dano físico.', fx: { k: 'p_power', el: 'physical', mult: 1.05 } }),
  // ── Corpo ──
  k('strength', 'Aumento de Força', 'Strength Boost', 'E', { req: ['cbt.training'], arm: 'N', sub: 'Corpo',
    desc: 'Força I permanente.', fx: { k: 'p_effect', eff: [['strength', 0]] } }),
  k('speed', 'Aumento de Velocidade', 'Speed Boost', 'E', { req: ['cbt.training'], sub: 'Corpo',
    desc: 'Velocidade I permanente.', fx: { k: 'p_effect', eff: [['speed', 0]] } }),
  k('toughness', 'Aumento de Resistência', 'Toughness', 'D', { req: ['cbt.strength'], sub: 'Corpo',
    desc: '10% menos dano físico e de explosões.', fx: { k: 'p_resist', groups: { physical: 0.1, explosion: 0.1 } } }),
  k('enhancement', 'Aprimoramento Físico', 'Physical Enhancement', 'C', { req: ['cbt.toughness', 'cbt.speed'], lp: 'cbt.toughness', sub: 'Corpo',
    desc: '+1 coração e Pressa I permanentes.', fx: { k: 'p_effect', eff: [['health_boost', 0], ['haste', 0]] } }),
  k('regen', 'Regeneração Básica', 'Basic Regeneration', 'D', { req: ['cbt.toughness'], sub: 'Corpo',
    desc: 'Regeneração I permanente.', fx: { k: 'p_effect', eff: [['regeneration', 0]] } }),
  k('iron_body', 'Corpo de Ferro', 'Iron Body', 'B', { req: ['cbt.enhancement'], sub: 'Corpo',
    desc: 'Resistência I permanente e imunidade a Fraqueza.', fx: [{ k: 'p_effect', eff: [['resistance', 0]] }, { k: 'p_cleanse', eff: ['weakness'] }] }),
  // ── Percepção ──
  k('perception', 'Percepção Aprimorada', 'Enhanced Perception', 'E', { req: ['cbt.training'], arm: 'W', sub: 'Percepção', mp: 3, cd: 10,
    desc: 'Percebe as criaturas num raio de 24 blocos.', fx: { k: 'sense', r: 24 } }),
  k('night_eyes', 'Visão Noturna', 'Night Vision', 'F', { req: ['cbt.perception'], sub: 'Percepção',
    desc: 'Enxerga no escuro.', fx: { k: 'p_effect', eff: [['night_vision', 0]] } }),
  k('battle_sense', 'Sentido de Batalha', 'Battle Sense', 'C', { req: ['cbt.perception'], sub: 'Percepção',
    desc: '10% de chance de esquivar completamente de um ataque.', fx: { k: 'p_dodge', chance: 0.1 } }),
  // ── Esquiva e defesa ──
  k('dodge', 'Técnica de Esquiva', 'Evasion Technique', 'D', { req: ['cbt.speed'], arm: 'E', sub: 'Defesa',
    desc: '8% de chance de esquivar de um ataque (acumula com Sentido de Batalha).', fx: { k: 'p_dodge', chance: 0.08 } }),
  k('step', 'Passo Rápido', 'Quick Step', 'D', { req: ['cbt.dodge'], sub: 'Defesa', mp: 6, cd: 4,
    desc: 'Investida curta de 4 blocos (sem dano), para sair de perigo.', fx: { k: 'dash', power: 2.2, dmg: 0, r: 0 } }),
  k('guard', 'Postura de Defesa', 'Defensive Stance', 'C', { req: ['cbt.dodge'], sub: 'Defesa', mp: 12, cd: 20,
    desc: 'Por 4 s: Resistência III (bloqueio).', fx: { k: 'buff', eff: [['resistance', 4, 2]] } }),
  k('counter', 'Contra-ataque', 'Counterattack', 'B', { req: ['cbt.guard'], sub: 'Defesa',
    desc: '25% de chance de devolver 50% do dano corpo a corpo recebido.', fx: { k: 'p_counter', chance: 0.25, mult: 0.5 } }),
  k('perfect_guard', 'Defesa Perfeita', 'Perfect Guard', 'A', { req: ['cbt.counter', 'cbt.iron_body'], lp: 'cbt.counter', sub: 'Defesa', mp: 40, cd: 90,
    desc: 'Por 3 s praticamente não recebe dano (Resistência V).', fx: { k: 'buff', eff: [['resistance', 3, 4]] } }),
  // ── Técnicas de combate ──
  k('charge', 'Investida', 'Charge', 'D', { req: ['cbt.training'], arm: 'S', sub: 'Técnicas', mp: 10, cd: 6,
    desc: 'Avança ~6 blocos e golpeia quem estiver no caminho (6 de dano, arremesso).', fx: { k: 'dash', power: 3, dmg: 6, r: 2, knock: 1.2 } }),
  k('whirlwind', 'Golpe Giratório', 'Whirlwind Strike', 'C', { req: ['cbt.charge'], sub: 'Técnicas', mp: 15, cd: 8,
    desc: 'Golpeia todos ao redor (raio 4, 9 de dano).', fx: { k: 'nova', el: 'physical', r: 4, dmg: 9, self: true, knock: 1 } }),
  k('flurry', 'Golpes Sucessivos', 'Flurry', 'B', { req: ['cbt.whirlwind'], sub: 'Técnicas', mp: 25, cd: 15,
    desc: 'Golpeia até 4 inimigos próximos (raio 8), 8 de dano cada.', fx: { k: 'streak', el: 'physical', r: 8, count: 4, dmg: 8 } }),
  k('bleed', 'Golpe Hemorrágico', 'Bleeding Strike', 'C', { req: ['cbt.charge'], sub: 'Técnicas',
    desc: '15% de chance por golpe de causar Wither e Lentidão (sangramento).', fx: { k: 'p_onhit', chance: 0.15, eff: [['wither', 3, 0], ['slowness', 3, 0]] } }),
  k('vital_strike', 'Golpe Vital', 'Vital Strike', 'A', { req: ['cbt.flurry', 'cbt.bleed'], lp: 'cbt.flurry', sub: 'Técnicas', mp: 35, cd: 20,
    desc: 'Aparece atrás do alvo (24 blocos) e golpeia um ponto vital (20 de dano).', fx: { k: 'strike', el: 'physical', range: 24, dmg: 20 } }),
  k('berserk', 'Fúria de Combate', 'Battle Fury', 'A', { req: ['cbt.vital_strike'], sub: 'Técnicas', mp: 50, cd: 120,
    desc: 'Por 30 s: Força III, Pressa II e Velocidade II.', fx: { k: 'buff', eff: [['strength', 30, 2], ['haste', 30, 1], ['speed', 30, 1]] } }),
  // ── Armas ──
  k('weapon_art', 'Técnica com Armas', 'Weapon Art', 'D', { req: ['cbt.training'], sub: 'Armas',
    desc: '10% mais dano físico com qualquer arma.', fx: { k: 'p_power', el: 'physical', mult: 1.1 } }),
  k('sword_aura', 'Aura da Lâmina', 'Blade Aura', 'C', { req: ['cbt.weapon_art'], sub: 'Armas', mp: 20, cd: 45,
    desc: 'Envolve a arma em energia por 30 s: +5 de dano por golpe.', fx: { k: 'imbue', el: 'physical', dur: 30, dmg: 5 } }),
  k('slash_wave', 'Onda Cortante', 'Slash Wave', 'B', { req: ['cbt.sword_aura'], sub: 'Armas', mp: 25, cd: 6,
    desc: 'Corte de energia em linha (24 blocos, 12 de dano, atravessa).', fx: { k: 'beam', el: 'physical', dmg: 12, range: 24, pierce: true } }),
  k('archery', 'Técnica com Arco', 'Archery', 'D', { req: ['cbt.weapon_art'], sub: 'Armas',
    desc: 'Flechas causam 20% mais dano.', fx: { k: 'p_power', el: 'projectile', mult: 1.2 } }),
  k('arrow_rain', 'Chuva de Flechas', 'Arrow Rain', 'B', { req: ['cbt.archery'], sub: 'Armas', mp: 30, cd: 20,
    desc: 'Dispara 5 projéteis de energia em leque (7 de dano cada).', fx: { k: 'bolt', el: 'physical', dmg: 7, count: 5, spread: 9 } }),
  k('weapon_master', 'Mestre de Armas', 'Weapon Master', 'S', { req: ['cbt.slash_wave', 'cbt.arrow_rain', 'cbt.berserk'], lp: 'cbt.slash_wave', sub: 'Armas', lvl: 40,
    desc: 'Domínio total das armas: 20% mais dano físico e de projéteis e 5% de roubo de vida.',
    fx: [{ k: 'p_power', el: 'physical', mult: 1.2 }, { k: 'p_power', el: 'projectile', mult: 1.2 }, { k: 'p_lifesteal', pct: 0.05 }] }),
  // ── Energia ──
  k('energy_control', 'Controle de Energia', 'Energy Control', 'D', { req: ['cbt.training'], sub: 'Energia',
    desc: '+40 de Aura máxima e +1/s de regeneração de Aura.', fx: { k: 'p_aura', max: 40, regen: 1 } }),
  k('mana_control', 'Controle de Mana', 'Mana Control', 'D', { req: ['cbt.energy_control'], sub: 'Energia',
    desc: '+30 Magicules máximas e +0,5/s de regeneração.', fx: { k: 'p_mp', max: 30, regen: 0.5 } }),
  k('aura_burst', 'Explosão de Aura', 'Aura Burst', 'B', { req: ['cbt.energy_control', 'cbt.whirlwind'], lp: 'cbt.energy_control', sub: 'Energia', mp: 40, cd: 40,
    desc: 'Libera a aura (raio 6): 12 de dano e arremesso.', fx: { k: 'nova', el: 'physical', r: 6, dmg: 12, self: true, knock: 2.5 } }),
]);
