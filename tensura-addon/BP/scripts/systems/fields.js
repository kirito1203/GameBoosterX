// Campos mágicos com duração (Nevasca, Campo Sagrado, Área Antimagia, Tempo...).
// Um único intervalo de 10 ticks processa todos os campos ativos.
import { world, system } from '@minecraft/server';
import { ELEMENTS } from '../data/elements.js';
import { V, dealDamage, addEffects, enemiesAround, alliesAround, particle, knockFrom, isBoss } from './util.js';

const fields = []; // { owner (Player), center, follow, dim, r, until, f }

export function addField(player, f, center) {
  fields.push({ owner: player, ownerId: player.id, follow: !!f.self, center, dim: player.dimension, r: f.r,
    until: system.currentTick + Math.round(f.dur * 20), f, frozen: new Map() });
}

/** Algum campo antimagia de outro jogador cobre esta posição? */
export function antimagicBlocks(player) {
  for (const fd of fields) {
    if (!fd.f.antimagic || fd.ownerId === player.id) continue;
    if (fd.dim.id !== player.dimension.id) continue;
    const c = fd.follow && fd.owner.isValid ? fd.owner.location : fd.center;
    if (V.dist(c, player.location) <= fd.r) return true;
  }
  return false;
}

function ring(fd, c) {
  const el = ELEMENTS[fd.f.el] ?? ELEMENTS.spirit;
  const n = 10;
  const t = system.currentTick / 10;
  for (let i = 0; i < n; i++) {
    const a = (i / n) * Math.PI * 2 + t * 0.3;
    particle(fd.dim, el.particle, { x: c.x + Math.cos(a) * fd.r, y: c.y + 0.3, z: c.z + Math.sin(a) * fd.r });
  }
}

export function startFields() {
  system.runInterval(() => {
    if (!fields.length) return;
    const now = system.currentTick;
    for (let i = fields.length - 1; i >= 0; i--) {
      const fd = fields[i];
      if (now >= fd.until || !fd.owner.isValid) { fields.splice(i, 1); continue; }
      const c = fd.follow ? fd.owner.location : fd.center;
      const f = fd.f;
      ring(fd, c);
      const enemies = enemiesAround(fd.owner, c, fd.r + (f.confine ? 6 : 0), fd.dim);
      for (const e of enemies) {
        const d = V.dist(e.location, c);
        if (f.confine && d > fd.r - 0.5) {
          // Campo de Batalha Isolado: empurra de volta para dentro.
          const dir = V.norm(V.sub(c, e.location));
          try { e.applyKnockback({ x: dir.x * 1.2, z: dir.z * 1.2 }, 0.2); } catch { /* */ }
          continue;
        }
        if (d > fd.r) continue;
        if (f.dps) dealDamage(fd.owner, e, f.dps / 2, f.el);
        if (f.enemyEff) addEffects(e, f.enemyEff.map(([id, s, a]) => [id, Math.max(s, 1), a]));
        if (f.repel && d < fd.r) knockFrom(e, c, f.repel, 0.15);
        if (f.freeze && !isBoss(e)) {
          // Tempo congelado: a criatura fica presa onde está.
          if (!fd.frozen.has(e.id)) fd.frozen.set(e.id, e.location);
          try { e.teleport(fd.frozen.get(e.id), { keepVelocity: false }); } catch { /* */ }
        }
      }
      if (f.allyEff) {
        for (const a of alliesAround(fd.owner, fd.r)) {
          if (V.dist(a.location, c) <= fd.r) addEffects(a, f.allyEff);
        }
      }
    }
  }, 10);
}

export { world };
