// Invocações: criação, vínculo com o dono, duração, ressurgimento (Legião Imortal)
// e comportamentos especiais (espírito cura, familiar vigia, basilisco petrifica).
import { world, system } from '@minecraft/server';
import { CONFIG } from '../config.js';
import { V, particle, sound, addEffects, heal, enemiesAround } from './util.js';

const tracked = new Map(); // entityId -> { entity, ownerId, typeId, until, revive, castId }

export function summon(player, typeId, count, seconds, opts = {}) {
  const made = [];
  const mine = [...tracked.values()].filter((t) => t.ownerId === player.id);
  const overflow = mine.length + count - CONFIG.perf.maxSummonsPerPlayer;
  for (let i = 0; i < overflow && i < mine.length; i++) dismiss(mine[i]);
  for (let i = 0; i < count; i++) {
    const ang = (i / Math.max(1, count)) * Math.PI * 2;
    const loc = V.add(player.location, { x: Math.cos(ang) * 2, y: 0.2, z: Math.sin(ang) * 2 });
    const e = spawnOne(player, typeId, loc, seconds, opts);
    if (e) made.push(e);
  }
  sound(player.dimension, 'mob.evocation_illager.prepare_summon', player.location);
  return made;
}

function spawnOne(player, typeId, loc, seconds, opts) {
  let e;
  try { e = player.dimension.spawnEntity(typeId, loc); } catch (err) {
    console.warn(`[Tensura] Falha ao invocar ${typeId}: ${err}`);
    return null;
  }
  try { e.setDynamicProperty('tensura:owner', player.id); } catch { /* */ }
  try { e.getComponent('minecraft:tameable')?.tame(player); } catch { /* */ }
  if (opts.name) e.nameTag = opts.name;
  for (let k = 0; k < 6; k++) particle(player.dimension, 'minecraft:basic_portal_particle', V.add(loc, { x: Math.random() - 0.5, y: Math.random() * 2, z: Math.random() - 0.5 }));
  const until = opts.until ?? system.currentTick + Math.round(seconds * 20);
  tracked.set(e.id, { entity: e, ownerId: player.id, typeId, until, revive: !!opts.revive, seconds });
  return e;
}

function dismiss(t) {
  tracked.delete(t.entity.id);
  try {
    if (t.entity.isValid) {
      particle(t.entity.dimension, 'minecraft:basic_portal_particle', t.entity.location);
      t.entity.remove();
    }
  } catch { /* */ }
}

export function summonsOf(playerId) {
  return [...tracked.values()].filter((t) => t.ownerId === playerId && t.entity.isValid).map((t) => t.entity);
}

export function ownerOfSummon(entityId) {
  return tracked.get(entityId)?.ownerId;
}

export function onSummonDied(entity) {
  const t = tracked.get(entity.id);
  if (!t) return;
  tracked.delete(entity.id);
  if (!t.revive || system.currentTick + 100 >= t.until) return;
  const owner = world.getAllPlayers().find((p) => p.id === t.ownerId);
  if (!owner?.isValid) return;
  // Legião Imortal: reergue após 5 s ao lado do dono, até o fim da magia.
  system.runTimeout(() => {
    if (!owner.isValid || system.currentTick >= t.until) return;
    spawnOne(owner, t.typeId, V.add(owner.location, { x: 1.5, y: 0.2, z: 0 }), 0, { until: t.until, revive: true });
  }, 100);
}

export function startSummons() {
  system.runInterval(() => {
    const now = system.currentTick;
    for (const t of [...tracked.values()]) {
      if (!t.entity.isValid) { tracked.delete(t.entity.id); continue; }
      if (now >= t.until) { dismiss(t); continue; }
      const owner = world.getAllPlayers().find((p) => p.id === t.ownerId);
      if (!owner?.isValid) { dismiss(t); continue; }
      if (owner.dimension.id !== t.entity.dimension.id || V.dist(owner.location, t.entity.location) > 48) {
        try { t.entity.teleport(owner.location, { dimension: owner.dimension }); } catch { /* */ }
      }
      if (t.typeId === 'tensura:spirit' && V.dist(owner.location, t.entity.location) < 16) {
        heal(owner, 2);
        particle(owner.dimension, 'minecraft:heart_particle', V.add(owner.location, { x: 0, y: 2, z: 0 }));
      }
      if (t.typeId === 'tensura:familiar') {
        addEffects(owner, [['night_vision', 15, 0]]);
        const threats = enemiesAround(owner, owner.location, 16).filter((e) => { try { return e.matches({ families: ['monster'] }); } catch { return false; } });
        if (threats.length) {
          try { owner.onScreenDisplay.setActionBar(`§c! Familiar: ${threats.length} monstro(s) por perto`); } catch { /* */ }
        }
      }
    }
  }, 60);
}

/** Basilisco petrifica ao morder. Chamado pelo handler de dano. */
export function onSummonHit(attacker, victim) {
  if (attacker.typeId === 'tensura:basilisk') addEffects(victim, [['slowness', 4, 3], ['mining_fatigue', 4, 2]]);
}
