// Missões especiais: progresso calculado a partir dos dados reais do jogador. Puro.
import { MISSIONS, TREES } from '../data/registry.js';

const FLAG = { killed_wither: 'Derrotar o Wither', killed_ender_dragon: 'Derrotar o Ender Dragon', killed_warden: 'Derrotar o Warden' };

function part(label, cur, need) {
  return { label, cur: Math.min(cur, need), need, ok: cur >= need };
}

/**
 * @param {object} m missão
 * @param {object} d dados do jogador (level, kills, flags, absorbLog, absorbTotal, absorbed, unlocked, sage)
 */
export function missionProgress(m, d) {
  const parts = [];
  for (const c of m.cond) {
    if (c.absorbTotal) parts.push(part('Criaturas absorvidas', d.absorbTotal ?? 0, c.absorbTotal));
    if (c.absorbType) parts.push(part(`Absorver ${c.absorbType.replace('minecraft:', '')}`, (d.absorbLog ?? {})[c.absorbType] ?? 0, 1));
    if (c.absorbSpecies) parts.push(part('Espécies diferentes absorvidas', (d.absorbed ?? []).length, c.absorbSpecies));
    if (c.kill) parts.push(part(`Derrotar ${c.kill.replace('minecraft:', '')}`, (d.kills ?? {})[c.kill] ?? 0, c.n ?? 1));
    if (c.killTotal) parts.push(part('Criaturas derrotadas', Object.values(d.kills ?? {}).reduce((a, b) => a + b, 0), c.killTotal));
    if (c.level) parts.push(part('Nível', d.level, c.level));
    if (c.flags) for (const f of c.flags) parts.push(part(FLAG[f] ?? f, d.flags.has(f) ? 1 : 0, 1));
    if (c.sage) {
      const order = ['sage', 'raphael', 'ciel'];
      const s = d.sage ?? { stage: 'sage', level: 1 };
      const si = order.indexOf(s.stage);
      const ti = order.indexOf(c.sage.stage);
      parts.push(part(`Grande Sábio: ${c.sage.stage} nível ${c.sage.level}`, si > ti ? c.sage.level : si === ti ? s.level : 0, c.sage.level));
    }
    if (c.analyzed) for (const t of c.analyzed) parts.push(part(`Analisar a essência: ${t.replace('minecraft:', '')}`, d.sage?.analyses?.[t] ? 1 : 0, 1));
    if (c.analyzedCount) parts.push(part('Espécies analisadas', Object.keys(d.sage?.analyses ?? {}).length, c.analyzedCount));
    if (c.library) parts.push(part('Habilidades na Biblioteca', libraryOf(d).size, c.library));
    if (c.cielOp) parts.push(part(`Operação de Ciel: síntese de ${c.cielOp}`, d.cielOps?.has(c.cielOp) ? 1 : 0, 1));
    if (c.treeCount) {
      const n = TREES[c.treeCount.tree].skills.filter((x) => x.type !== 'race_root' && d.unlocked.has(x.id)).length;
      parts.push(part('Habilidades desbloqueadas na árvore', n, c.treeCount.n));
    }
  }
  return { done: parts.every((p) => p.ok), parts, progress: parts.reduce((a, p) => a + p.cur / p.need, 0) / Math.max(1, parts.length) };
}

/** Biblioteca de habilidades: as que você tem + as registradas por análise. */
export function libraryOf(d) {
  return new Set([...(d.library ?? []), ...d.unlocked]);
}

/** Missões da linhagem do jogador. */
export function missionsFor(lineage) {
  return Object.values(MISSIONS).filter((m) => m.lineage === lineage);
}

export { MISSIONS };

/** Linhas de texto das missões (menu principal e painel do Sábio usam o mesmo texto). */
export function missionLines(d, lineage) {
  const list = missionsFor(lineage);
  if (!list.length) return ['Sua raça ainda não tem missões especiais cadastradas.'];
  const lines = [];
  for (const m of list) {
    const done = d.missions?.has(m.id);
    const pr = missionProgress(m, d);
    lines.push(`${done ? '§a✔' : pr.done ? '§e★' : '§7•'} §l${m.name}§r ${done ? '§a(concluída)' : `§7${Math.round(pr.progress * 100)}%`}§r`, `  ${m.desc}`);
    if (!done) for (const p of pr.parts) lines.push(`  ${p.ok ? '§a✔' : '§c✘'} ${p.label}: ${p.cur}/${p.need}§r`);
    lines.push(`  §8Recompensa: ${m.reward} pontos§r`, '');
  }
  return lines;
}
