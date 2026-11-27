// Projéteis mágicos simulados pelo script (trajetória, colisão, impacto).
// Um único intervalo de 1 tick processa todos; limitado por CONFIG.perf.maxActiveBolts.
import { system } from '@minecraft/server';
import { ELEMENTS } from '../data/elements.js';
import { CONFIG } from '../config.js';
import { V, dealDamage, addEffects, enemiesAround, particle, sound, knockFrom, QUERY_EXCLUDE, isAlly } from './util.js';

const bolts = [];
// Ar e líquidos são tratados por isAir/isLiquid; aqui só blocos atravessáveis sólidos-falsos.
const PASSABLE = /^minecraft:(short_grass|tall_grass|fern|large_fern|\w*_flower|\w*torch|\w*_sapling|vine|\w*_vines?|snow_layer|\w*carpet|\w*_button|lever|\w*rail|\w*_sign|\w*_banner|\w*bush|deadbush|dandelion|poppy|\w*tulip|blue_orchid|allium|azure_bluet|oxeye_daisy|cornflower|lily_of_the_valley|wither_rose|peony|lilac|sunflower|kelp|seagrass|\w*fire|light_block\w*|structure_void|\w*_mushroom|tripwire|redstone_wire|\w*_pressure_plate)$/;

export function launchBolt(player, f, dirOverride) {
  if (bolts.length >= CONFIG.perf.maxActiveBolts) bolts.shift();
  const dir = dirOverride ?? player.getViewDirection();
  const start = V.add(player.getHeadLocation(), V.mul(dir, 0.8));
  bolts.push({ owner: player, dim: player.dimension, pos: start, dir, f, traveled: 0, hit: new Set(), age: 0 });
}

function blocked(dim, pos) {
  try {
    const b = dim.getBlock(pos);
    if (!b) return true;
    if (b.isAir || b.isLiquid) return false;
    return !PASSABLE.test(b.typeId);
  } catch {
    return true;
  }
}

function impact(b, pos) {
  const f = b.f;
  const el = ELEMENTS[f.el] ?? ELEMENTS.spirit;
  particle(b.dim, el.impact, pos);
  sound(b.dim, el.sound, pos, { volume: 0.8 });
  if (f.explode) {
    try { b.dim.createExplosion(pos, f.explode, { breaksBlocks: CONFIG.explosionsBreakBlocks, causesFire: false, source: b.owner }); } catch { /* */ }
  }
  if (f.r) {
    for (const e of enemiesAround(b.owner, pos, f.r, b.dim)) hitEntity(b, e, pos);
  }
}

function hitEntity(b, e, from) {
  if (b.hit.has(e.id)) return;
  b.hit.add(e.id);
  const f = b.f;
  if (dealDamage(b.owner, e, f.dmg, f.el) > 0) {
    if (f.ignite) { try { e.setOnFire(f.ignite, true); } catch { /* */ } }
    if (f.eff) addEffects(e, f.eff);
    if (f.knock) knockFrom(e, from, f.knock, 0.3);
  }
}

export function startBolts() {
  system.runInterval(() => {
    for (let i = bolts.length - 1; i >= 0; i--) {
      const b = bolts[i];
      if (!b.owner.isValid) { bolts.splice(i, 1); continue; }
      const f = b.f;
      const el = ELEMENTS[f.el] ?? ELEMENTS.spirit;
      const speed = f.speed ?? 1;
      const steps = Math.max(1, Math.ceil(speed / 0.5));
      const step = speed / steps;
      let done = false;
      b.age++;
      for (let s = 0; s < steps && !done; s++) {
        b.pos = V.add(b.pos, V.mul(b.dir, step));
        b.traveled += step;
        if (blocked(b.dim, b.pos)) { impact(b, b.pos); done = true; break; }
        let near = [];
        try { near = b.dim.getEntities({ location: b.pos, maxDistance: 1.3, ...QUERY_EXCLUDE }); } catch { /* */ }
        for (const e of near) {
          if (isAlly(b.owner, e) || b.hit.has(e.id)) continue;
          hitEntity(b, e, b.pos);
          if (!f.pierce) { impact(b, b.pos); done = true; break; }
        }
        if (b.traveled >= (f.range ?? 20)) { if (f.r) impact(b, b.pos); done = true; }
      }
      particle(b.dim, el.particle, b.pos);
      if (f.pulse && b.age % f.pulse.every === 0) {
        for (const e of enemiesAround(b.owner, b.pos, f.pulse.r, b.dim)) dealDamage(b.owner, e, f.pulse.dmg, f.el);
        particle(b.dim, el.impact, b.pos);
      }
      if (done) bolts.splice(i, 1);
    }
  }, 1);
}
