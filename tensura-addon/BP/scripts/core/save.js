// Serialização dos dados do jogador (pura, testável). O systems/storage.js grava
// o resultado numa propriedade dinâmica do próprio jogador.
import { RACES, SKILLS, REPLACES } from '../data/registry.js';

export const VERSION = 1;

export function fresh() {
  return {
    v: VERSION,
    race: null,          // null = ainda não escolheu
    level: 1,
    ep: 0,
    points: 0,
    unlocked: new Set(),
    flags: new Set(),
    mp: 0,
    aura: 0,
    epTotal: 0,          // EP acumulado na vida (exibido no Status)
    mpBonus: 0,          // Magicules máximas extras (Absorção)
    equipped: [],        // ids no Cristal de Magicules
    slot: 0,
    absorbed: [],        // espécies absorvidas (Slime)
    mimic: null,
    flying: false,
    flightDrain: 3,
    warp: null,          // { x, y, z, dim }
    gifted: false,
    introDone: false,    // apresentação do Grande Sábio concluída
    kills: {},           // registros: criaturas derrotadas por tipo
    discovered: new Set(), // habilidades descobertas por absorção
    missions: new Set(),   // missões especiais concluídas (recompensa uma única vez)
    mastery: {},           // usos por habilidade (níveis de domínio)
    absorbLog: {},         // absorções por espécie
    absorbTotal: 0,        // absorções no total
    library: new Set(),    // Biblioteca (Shub-Niggurath): habilidades registradas por análise
    skillCopies: [],       // cópias feitas pela Duplicação (ids)
    loadouts: [],          // conjuntos de barra guardados (Armazenamento)
    cielOps: new Set(),    // operações concluídas por Ciel (ex.: 'azathoth')
    opDays: {},            // último dia do mundo em que cada operação diária foi usada
    ui: { tab: 'racial', views: {}, selected: null },
    // memória de execução (não salva)
    cooldowns: {},
    lastCast: 0,
    imbue: null,
    stats: null,
  };
}

/**
 * Troca habilidades universais pelas versões próprias da linhagem (ex.: Slime com "uni.predator"
 * passa a ter "r.slime.predator"). Sem perder progresso e sem efeito em dobro.
 */
export function migrateLineage(d) {
  const map = REPLACES[RACES[d.race]?.lineage];
  if (!map) return 0;
  let n = 0;
  for (const [uni, own] of Object.entries(map)) {
    if (!d.unlocked.has(uni)) continue;
    d.unlocked.delete(uni);
    d.unlocked.add(own);
    d.equipped = d.equipped.map((id) => (id === uni ? own : id));
    if (d.mastery[uni]) { d.mastery[own] = Math.max(d.mastery[own] ?? 0, d.mastery[uni]); delete d.mastery[uni]; }
    n++;
  }
  d.equipped = [...new Set(d.equipped)];
  return n;
}

export function serialize(d) {
  return JSON.stringify({
    v: VERSION, race: d.race, level: d.level, ep: d.ep, points: d.points,
    unlocked: [...d.unlocked], flags: [...d.flags], mp: Math.floor(d.mp), aura: Math.floor(d.aura), epTotal: d.epTotal, kills: d.kills, mpBonus: d.mpBonus,
    equipped: d.equipped, slot: d.slot, absorbed: d.absorbed, mimic: d.mimic, flying: d.flying, flightDrain: d.flightDrain,
    warp: d.warp, gifted: d.gifted, introDone: d.introDone,
    discovered: [...d.discovered], missions: [...d.missions], mastery: d.mastery, absorbLog: d.absorbLog, absorbTotal: d.absorbTotal,
    library: [...d.library], skillCopies: d.skillCopies, loadouts: d.loadouts, cielOps: [...d.cielOps], opDays: d.opDays, ui: { tab: d.ui.tab, views: d.ui.views, view: d.ui.view ?? null },
  });
}

/** Lê os dados salvos; ignora IDs que não existem mais (addon atualizado) e dados corrompidos. */
export function deserialize(raw) {
  const d = fresh();
  if (typeof raw !== 'string') return d;
  try {
    const o = JSON.parse(raw);
    Object.assign(d, {
      race: o.race && RACES[o.race] ? o.race : null,
      level: Number.isFinite(o.level) ? o.level : 1,
      ep: o.ep ?? 0,
      points: o.points ?? 0,
      unlocked: new Set((o.unlocked ?? []).filter((id) => SKILLS[id])),
      flags: new Set(o.flags ?? []),
      mp: o.mp ?? 0,
      aura: o.aura ?? 0,
      epTotal: o.epTotal ?? 0,
      kills: o.kills ?? {},
      mpBonus: o.mpBonus ?? 0,
      equipped: (o.equipped ?? []).filter((id) => SKILLS[id]),
      slot: o.slot ?? 0,
      absorbed: o.absorbed ?? [],
      mimic: o.mimic ?? null,
      flying: o.flying ?? false,
      flightDrain: o.flightDrain ?? 3,
      warp: o.warp ?? null,
      gifted: o.gifted ?? false,
      // quem já tinha raça (versões anteriores) não vê a apresentação
      introDone: o.introDone ?? !!(o.race && RACES[o.race]),
      discovered: new Set((o.discovered ?? []).filter((id) => SKILLS[id])),
      missions: new Set(o.missions ?? []),
      mastery: o.mastery && typeof o.mastery === 'object' ? o.mastery : {},
      absorbLog: o.absorbLog && typeof o.absorbLog === 'object' ? o.absorbLog : {},
      absorbTotal: Number.isFinite(o.absorbTotal) ? o.absorbTotal : 0,
      library: new Set((o.library ?? []).filter((id) => SKILLS[id])),
      skillCopies: (o.skillCopies ?? []).filter((id) => SKILLS[id]).slice(0, 3),
      loadouts: (o.loadouts ?? []).slice(0, 3).map((l) => ({ name: String(l.name ?? ''), ids: (l.ids ?? []).filter((id) => SKILLS[id]) })),
      cielOps: new Set(o.cielOps ?? []),
      opDays: o.opDays && typeof o.opDays === 'object' ? o.opDays : {},
    });
    migrateLineage(d);
    d.ui.tab = o.ui?.tab ?? 'racial';
    d.ui.views = o.ui?.views ?? {};
    d.ui.view = o.ui?.view ?? null;
  } catch {
    // dados corrompidos: começa do zero (o jogador recebe um aviso no log)
    d.corrupted = true;
  }
  return d;
}
