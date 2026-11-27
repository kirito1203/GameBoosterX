// Ciel — sistema MANAS (lógica pura, testável). NÃO é inteligência artificial: cada resposta e
// operação é calculada a partir do catálogo e dos dados do jogador.
// Administra: requisitos, recomendações, sínteses (combinações e Azathoth), Biblioteca de Habilidades
// e as operações de Shub-Niggurath (criar, duplicar, conceder, armazenar).
import { SKILLS, TREES, RACES, MISSIONS } from '../data/registry.js';
import { evaluate, raceAllowed } from './progression.js';
import { missionProgress, libraryOf } from './missions.js';

export const CIEL_NODE = 'r.slime.ciel';
export const OP_NODES = {
  library: 'r.slime.shub',
  create: 'r.slime.skill_creation',
  duplicate: 'r.slime.skill_duplication',
  grant: 'r.slime.skill_grant',
  storage: 'r.slime.skill_storage',
};
export const LIMITS = { copies: 3, loadouts: 3, createMp: 300, duplicateMp: 200, createCostMult: 1.5, grantRange: 8 };
const MAX_RARITY = new Set(['common', 'rare', 'epic']);

export function hasCiel(d) {
  return d.unlocked.has(CIEL_NODE);
}

export function opAllowed(d, op) {
  return hasCiel(d) && d.unlocked.has(OP_NODES[op]);
}

/** Habilidades a no máximo `max` requisitos de distância (o que falta para cada uma). */
export function nearRequirements(d, state, max = 2, limit = 8) {
  const tree = TREES[`race.${RACES[d.race].lineage}`];
  const out = [];
  for (const s of tree.skills) {
    if (d.unlocked.has(s.id)) continue;
    const ev = evaluate(s.id, state);
    if (ev.state === 'locked') continue;
    if (ev.ok || ev.missing.length <= max) out.push({ s, ev });
  }
  return out.sort((a, b) => a.ev.missing.length - b.ev.missing.length || a.ev.cost - b.ev.cost).slice(0, limit);
}

/** Sínteses: habilidades da árvore racial que combinam duas ou mais anteriores, com estado. */
export function syntheses(d, state) {
  const tree = TREES[`race.${RACES[d.race].lineage}`];
  return tree.skills.filter((s) => s.requires.length >= 2 && !d.unlocked.has(s.id))
    .map((s) => ({ s, ev: evaluate(s.id, state), have: s.requires.filter((r) => d.unlocked.has(r)).length }))
    .filter((x) => x.have >= x.s.requires.length - 1)
    .sort((a, b) => Number(b.ev.ok) - Number(a.ev.ok) || b.have - a.have);
}

/** Síntese de Azathoth (missão administrada por Ciel): Ciel só executa com o resto pronto. */
export function azathothSynthesis(d) {
  const m = MISSIONS['m.slime.void'];
  const pr = missionProgress(m, d);
  const others = pr.parts.filter((p) => !p.label.startsWith('Operação de Ciel'));
  const nodes = ['r.slime.raphael', 'r.slime.beelzebuth', 'r.slime.veldora', 'r.slime.velgrynd'];
  const missingNodes = nodes.filter((n) => !d.unlocked.has(n));
  return {
    done: d.cielOps.has('azathoth'),
    ready: hasCiel(d) && others.every((p) => p.ok) && !missingNodes.length,
    missing: [...others.filter((p) => !p.ok).map((p) => `${p.label} ${p.cur}/${p.need}`), ...missingNodes.map((n) => `Requer: ${SKILLS[n].name}`)],
  };
}

/** Habilidades que a Criação pode gerar a partir da Biblioteca. */
export function creatable(d, state) {
  return [...libraryOf(d)].map((id) => SKILLS[id]).filter((s) => s && !d.unlocked.has(s.id)
    && TREES[s.tree].kind === 'universal' && MAX_RARITY.has(s.rarity) && raceAllowed(s, state) && state.level >= s.minLevel);
}

export function createCost(s, state) {
  return Math.ceil(evaluate(s.id, state).cost * LIMITS.createCostMult);
}

/** Habilidades que podem ser copiadas (universais comuns/raras/épicas que você tem). */
export function duplicable(d) {
  return [...d.unlocked].map((id) => SKILLS[id]).filter((s) => s && TREES[s.tree].kind === 'universal' && MAX_RARITY.has(s.rarity));
}

/** Pode conceder a cópia `id` ao jogador com dados `td`? */
export function grantCheck(td, id) {
  const s = SKILLS[id];
  if (!s || !td.race) return { ok: false, why: 'Jogador sem raça.' };
  if (td.unlocked.has(id)) return { ok: false, why: 'Ele já tem esta habilidade.' };
  const st = { race: td.race, level: td.level, points: td.points, unlocked: td.unlocked, flags: td.flags };
  if (!raceAllowed(s, st)) return { ok: false, why: 'Raça incompatível.' };
  if (td.level < s.minLevel) return { ok: false, why: `Nível ${s.minLevel} necessário (ele tem ${td.level}).` };
  return { ok: true, why: '' };
}

/** Operação diária: true se ainda não foi usada no dia do mundo `day`. */
export function dailyFree(d, op, day) {
  return d.opDays[op] !== day;
}

/** Relatório de compatibilidade de uma habilidade (para você). */
export function compatibility(d, state, id) {
  const s = SKILLS[id];
  const ev = evaluate(id, state);
  const lines = [`${s.name} (${s.original})`, `Árvore: ${TREES[s.tree].name}`];
  lines.push(raceAllowed(s, state) ? 'Compatível com a sua raça.' : 'INCOMPATÍVEL com a sua raça.');
  if (ev.state === 'unlocked') lines.push('Você já possui.');
  else if (ev.ok) lines.push(`Pode desbloquear agora (${ev.cost} pts).`);
  else lines.push(...ev.missing.map((m) => `Falta: ${m}`));
  return lines;
}
