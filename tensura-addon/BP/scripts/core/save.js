// Serialização dos dados do jogador (pura, testável). O systems/storage.js grava
// o resultado numa propriedade dinâmica do próprio jogador.
import { RACES, SKILLS } from '../data/registry.js';

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
    mpBonus: 0,          // Magicules máximas extras (Absorção)
    equipped: [],        // ids no Cristal de Magicules
    slot: 0,
    absorbed: [],        // espécies absorvidas (Slime)
    mimic: null,
    flying: false,
    flightDrain: 3,
    warp: null,          // { x, y, z, dim }
    gifted: false,
    ui: { tab: 'racial', views: {}, selected: null },
    // memória de execução (não salva)
    cooldowns: {},
    lastCast: 0,
    imbue: null,
    stats: null,
  };
}

export function serialize(d) {
  return JSON.stringify({
    v: VERSION, race: d.race, level: d.level, ep: d.ep, points: d.points,
    unlocked: [...d.unlocked], flags: [...d.flags], mp: Math.floor(d.mp), mpBonus: d.mpBonus,
    equipped: d.equipped, slot: d.slot, absorbed: d.absorbed, mimic: d.mimic, flying: d.flying, flightDrain: d.flightDrain,
    warp: d.warp, gifted: d.gifted, ui: { tab: d.ui.tab, views: d.ui.views },
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
      mpBonus: o.mpBonus ?? 0,
      equipped: (o.equipped ?? []).filter((id) => SKILLS[id]),
      slot: o.slot ?? 0,
      absorbed: o.absorbed ?? [],
      mimic: o.mimic ?? null,
      flying: o.flying ?? false,
      flightDrain: o.flightDrain ?? 3,
      warp: o.warp ?? null,
      gifted: o.gifted ?? false,
    });
    d.ui.tab = o.ui?.tab ?? 'racial';
    d.ui.views = o.ui?.views ?? {};
  } catch {
    // dados corrompidos: começa do zero (o jogador recebe um aviso no log)
    d.corrupted = true;
  }
  return d;
}
