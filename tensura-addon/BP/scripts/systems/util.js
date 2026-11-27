// Utilidades de combate e geometria usadas pelos efeitos.
import { world, system, EntityDamageCause } from '@minecraft/server';
import { ELEMENTS } from '../data/elements.js';
import { powerFor } from '../core/stats.js';
import { getData } from './storage.js';
import { CONFIG } from '../config.js';

export const V = {
  add: (a, b) => ({ x: a.x + b.x, y: a.y + b.y, z: a.z + b.z }),
  sub: (a, b) => ({ x: a.x - b.x, y: a.y - b.y, z: a.z - b.z }),
  mul: (a, k) => ({ x: a.x * k, y: a.y * k, z: a.z * k }),
  len: (a) => Math.hypot(a.x, a.y, a.z),
  dist: (a, b) => Math.hypot(a.x - b.x, a.y - b.y, a.z - b.z),
  norm: (a) => { const l = Math.hypot(a.x, a.y, a.z) || 1; return { x: a.x / l, y: a.y / l, z: a.z / l }; },
  dot: (a, b) => a.x * b.x + a.y * b.y + a.z * b.z,
  flat: (a) => { const l = Math.hypot(a.x, a.z) || 1; return { x: a.x / l, y: 0, z: a.z / l }; },
};

const BOSSES = new Set(['minecraft:wither', 'minecraft:ender_dragon', 'minecraft:warden', 'minecraft:elder_guardian']);
const IGNORE_TYPES = ['minecraft:item', 'minecraft:xp_orb', 'minecraft:arrow', 'minecraft:armor_stand', 'minecraft:painting',
  'minecraft:leash_knot', 'minecraft:lightning_bolt', 'minecraft:fireworks_rocket', 'minecraft:small_fireball', 'minecraft:fireball',
  'minecraft:snowball', 'minecraft:egg', 'minecraft:ender_pearl', 'minecraft:xp_bottle', 'minecraft:thrown_trident',
  'minecraft:wind_charge_projectile', 'minecraft:boat', 'minecraft:chest_boat', 'minecraft:minecart', 'minecraft:falling_block', 'minecraft:tnt'];
export const QUERY_EXCLUDE = { excludeTypes: IGNORE_TYPES };

export function isBoss(e) { return BOSSES.has(e.typeId); }

export function ownerIdOf(e) {
  try { return e.getDynamicProperty('tensura:owner'); } catch { return undefined; }
}

/** Aliado do jogador? (ele mesmo, suas invocações e, sem PvP, outros jogadores e suas invocações). */
export function isAlly(player, e) {
  if (!e?.isValid) return false;
  if (e.id === player.id) return true;
  const owner = ownerIdOf(e);
  if (owner === player.id) return true;
  if (!CONFIG.pvp) {
    if (e.typeId === 'minecraft:player') return true;
    if (owner) return true;
  }
  try {
    const tame = e.getComponent('minecraft:tameable');
    if (tame?.isTamed && tame.tamedToPlayerId === player.id) return true;
  } catch { /* ignora */ }
  return false;
}

export function isUndead(e) {
  try { return e.matches({ families: ['undead'] }); } catch { return false; }
}

export function causeFor(el) {
  if (el === 'fire' || el === 'nuclear') return EntityDamageCause.fire;
  if (el === 'lightning') return EntityDamageCause.lightning;
  return EntityDamageCause.magic;
}

// Golpes de magia recentes: o handler de dano ignora estes (evita contar como golpe corpo a corpo).
export const recentSpellHits = new Map(); // entityId -> tick

/**
 * Dano mágico de `caster` em `target`, com poder do conjurador e fraquezas/resistências do alvo.
 * @returns {number} dano aplicado (0 se não atingiu)
 */
export function dealDamage(caster, target, base, el, opts = {}) {
  if (!target?.isValid || target.id === caster.id) return 0;
  if (!opts.force && isAlly(caster, target)) return 0;
  let dmg = base;
  const cd = caster.typeId === 'minecraft:player' ? getData(caster) : null;
  if (cd?.stats) dmg *= powerFor(el, cd.stats);
  if (el === 'holy' && isUndead(target)) dmg *= 2;
  if (target.typeId === 'minecraft:player') {
    const td = getData(target);
    if (td?.stats) {
      const g = ELEMENTS[el]?.group ?? 'magic';
      dmg *= (1 - (td.stats.resist[g] ?? 0)) * (1 + (td.stats.weak[g] ?? 0));
    }
  }
  if (isBoss(target) && opts.execute) return 0;
  dmg = Math.max(0, Math.round(dmg * 10) / 10);
  if (dmg <= 0) return 0;
  recentSpellHits.set(target.id, system.currentTick);
  try {
    target.applyDamage(dmg, { cause: causeFor(el), damagingEntity: caster });
  } catch {
    return 0;
  }
  return dmg;
}

export function addEffects(e, list, fallbackSecs = 5) {
  if (!list?.length || !e?.isValid) return;
  for (const [id, secs, amp] of list) {
    try { e.addEffect(id, Math.max(1, Math.round((secs ?? fallbackSecs) * 20)), { amplifier: Math.min(255, amp ?? 0), showParticles: true }); } catch { /* efeito indisponível */ }
  }
}

// Efeitos reduzidos (configuração do jogador): durante a conjuração dele, só 1 de cada 3 partículas.
let reduced = false;
let skip = 0;
export function setReducedFx(on) { reduced = on; skip = 0; }

export function particle(dim, id, loc) {
  if (reduced && (skip++ % 3) !== 0) return;
  try { dim.spawnParticle(id, loc); } catch { /* fora de chunk carregado */ }
}

export function sound(dim, id, loc, opts = {}) {
  try { dim.playSound(id, loc, opts); } catch { /* som indisponível */ }
}

export function head(player) {
  return player.getHeadLocation();
}

/** Primeira entidade não aliada na linha de visão. */
export function lookTarget(player, range) {
  let hits = [];
  try { hits = player.getEntitiesFromViewDirection({ maxDistance: range, ...QUERY_EXCLUDE }); } catch { /* */ }
  const block = blockDistance(player, range);
  for (const h of hits) {
    if (h.distance > block + 0.5) break;
    if (!isAlly(player, h.entity)) return h.entity;
  }
  return null;
}

export function blockDistance(player, range) {
  try {
    const b = player.getBlockFromViewDirection({ maxDistance: range, includeLiquidBlocks: false, includePassableBlocks: false });
    if (b) return V.dist(head(player), b.block.location);
  } catch { /* */ }
  return range;
}

/** Ponto mirado: entidade, bloco ou o limite do alcance. */
export function lookPoint(player, range) {
  const t = lookTarget(player, range);
  if (t) return t.location;
  const d = blockDistance(player, range);
  return V.add(head(player), V.mul(player.getViewDirection(), Math.max(1, d - 0.5)));
}

/** Entidades (não aliadas) num raio. */
export function enemiesAround(player, center, r, dim = player.dimension) {
  let list = [];
  try { list = dim.getEntities({ location: center, maxDistance: r, ...QUERY_EXCLUDE }); } catch { /* */ }
  return list.filter((e) => !isAlly(player, e));
}

export function alliesAround(player, r) {
  let list = [];
  try { list = player.dimension.getEntities({ location: player.location, maxDistance: r, ...QUERY_EXCLUDE }); } catch { /* */ }
  return list.filter((e) => isAlly(player, e));
}

export function heal(e, amount) {
  const h = e.getComponent('minecraft:health');
  if (!h) return;
  h.setCurrentValue(Math.min(h.effectiveMax, h.currentValue + amount));
}

export function knockFrom(e, origin, strength, lift = 0.3) {
  const d = V.flat(V.sub(e.location, origin));
  try { e.applyKnockback({ x: d.x * strength, z: d.z * strength }, lift); } catch { /* */ }
}

export function msg(player, text) {
  try { player.sendMessage(text); } catch { /* */ }
}

export { world, system };
