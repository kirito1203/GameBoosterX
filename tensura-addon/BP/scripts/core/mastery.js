// Domínio de habilidades: nível por uso real (cada conjuração bem-sucedida). Puro.
import { CONFIG } from '../config.js';
import { SKILLS } from '../data/registry.js';

/** Nível máximo de domínio da habilidade (0 = sem domínio: passivas e evoluções). */
export function masteryMax(skill) {
  if (!skill || skill.type !== 'active') return 0;
  return Math.min(1 + CONFIG.mastery.uses.length, skill.masteryMax ?? CONFIG.ranks[skill.rank]?.mastery ?? 3);
}

/** Nível atual a partir dos usos acumulados (1 ao desbloquear). */
export function masteryLevel(skillId, uses = 0) {
  const max = masteryMax(SKILLS[skillId]);
  if (!max) return 0;
  let lvl = 1;
  for (const need of CONFIG.mastery.uses) if (uses >= need && lvl < max) lvl++;
  return lvl;
}

/** Usos necessários para o próximo nível (null no máximo). */
export function usesForNext(skillId, uses = 0) {
  const lvl = masteryLevel(skillId, uses);
  if (!lvl || lvl >= masteryMax(SKILLS[skillId])) return null;
  return CONFIG.mastery.uses[lvl - 1];
}

/** Multiplicadores de custo e recarga pelo domínio. */
export function masteryMults(skillId, uses = 0) {
  const lvl = masteryLevel(skillId, uses);
  const k = Math.max(0, lvl - 1);
  return { cost: 1 - CONFIG.mastery.costCutPerLevel * k, cooldown: 1 - CONFIG.mastery.cooldownCutPerLevel * k };
}
