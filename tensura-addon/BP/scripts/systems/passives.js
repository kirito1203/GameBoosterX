// Passivas contínuas: efeitos permanentes da raça e das habilidades, ambiente
// (água/fogo/sol), voo mágico, transformação e mecânicas raciais especiais.
import { world, system } from '@minecraft/server';
import { CONFIG } from '../config.js';
import { getData, markDirty, refreshStats } from './storage.js';
import { summonsOf } from './summons.js';
import { V } from './util.js';

function apply(player, id, amp, ticks) {
  try { player.addEffect(id, ticks, { amplifier: amp, showParticles: false }); } catch { /* */ }
}

function isDay() {
  const t = world.getTimeOfDay();
  return t < 12300 || t > 23850;
}

function skyExposed(player) {
  try {
    const hit = player.dimension.getBlockFromRay(player.getHeadLocation(), { x: 0, y: 1, z: 0 }, { maxDistance: 64, includeLiquidBlocks: true });
    return !hit;
  } catch { return false; }
}

function wearingHelmet(player) {
  try { return !!player.getComponent('minecraft:equippable')?.getEquipment('Head'); } catch { return false; }
}

function passiveTick() {
  const T = CONFIG.perf.passiveIntervalTicks;
  const day = player => player.dimension.id === 'minecraft:overworld' && isDay();
  for (const p of world.getAllPlayers()) {
    const d = getData(p);
    const s = d.stats;
    if (!s) continue;
    // Visão noturna precisa de mais de 10 s para não piscar.
    for (const [id, amp] of s.effects) apply(p, id, amp, id === 'night_vision' ? 320 : T + 30);
    if (p.isInWater) for (const [id, amp] of s.envWater) apply(p, id, amp, T + 30);
    let onFire = false;
    try { onFire = !!p.getComponent('minecraft:onfire'); } catch { /* */ }
    if (onFire) for (const [id, amp] of s.envFire) apply(p, id, amp, T + 30);
    if (s.pack) {
      const near = world.getAllPlayers().some((o) => o.id !== p.id && o.dimension.id === p.dimension.id && V.dist(o.location, p.location) < 8)
        || summonsOf(p.id).some((e) => V.dist(e.location, p.location) < 8);
      if (near) { apply(p, 'strength', 0, T + 30); apply(p, 'resistance', 0, T + 30); }
    }
    if (s.poisonImmune) { try { p.removeEffect('poison'); } catch { /* */ } }
    for (const e of s.cleanse) { try { p.removeEffect(e); } catch { /* */ } }
    const sp = s.special;
    if ((sp.has('sunburn') || sp.has('sunburn_weak')) && day(p) && !p.isInWater && skyExposed(p) && !wearingHelmet(p)) {
      if (sp.has('sunburn')) { try { p.setOnFire(3, true); } catch { /* */ } }
      else apply(p, 'weakness', 0, T + 30);
    }
    if (sp.has('night_power') && !isDay()) { apply(p, 'strength', 0, T + 30); apply(p, 'speed', 0, T + 30); }
    if (sp.has('photosynthesis') && day(p) && skyExposed(p)) apply(p, 'regeneration', 0, T + 30);
    if (sp.has('aquatic') && !p.isInWater) apply(p, 'slowness', 0, T + 30);
    if (sp.has('aquatic') && p.isInWater) { apply(p, 'conduit_power', 0, T + 30); apply(p, 'speed', 1, T + 30); }
    if (sp.has('hungry')) apply(p, 'hunger', 0, T + 30);
    // Transformação consome Magicules.
    if (d.mimic) {
      const cost = (T / 20) * 1;
      if (d.mp >= cost) { d.mp -= cost; markDirty(p); } else { d.mimic = null; refreshStats(d); try { p.sendMessage('§cMagicules insuficientes: Transformação desfeita.'); } catch { /* */ } }
    }
  }
}

// Voo: avaliado a cada 4 ticks para quem está voando.
function flightTick() {
  for (const p of world.getAllPlayers()) {
    const d = getData(p);
    if (!d.flying) continue;
    const cost = (d.flightDrain ?? 3) / 5;
    if (d.mp < cost) {
      d.flying = false;
      try { p.removeEffect('levitation'); p.sendMessage('§cMagicules insuficientes: voo encerrado.'); } catch { /* */ }
      apply(p, 'slow_falling', 0, 200);
      continue;
    }
    d.mp -= cost;
    apply(p, 'slow_falling', 0, 40);
    if (p.isSneaking) { try { p.removeEffect('levitation'); } catch { /* */ } continue; }
    const v = p.getViewDirection();
    if (v.y > 0.35) apply(p, 'levitation', Math.min(4, Math.round(v.y * 5)), 8);
    else { try { p.removeEffect('levitation'); } catch { /* */ } }
    apply(p, 'speed', 2, 10);
  }
}

export function startPassives() {
  system.runInterval(passiveTick, CONFIG.perf.passiveIntervalTicks);
  system.runInterval(flightTick, 4);
}
