// Implementação dos efeitos ativos. Cada habilidade declara `fx` (ou uma lista)
// em data/trees/*.js; aqui está o que cada tipo realmente faz no mundo.
import { world, system, ItemStack, EquipmentSlot, EnchantmentTypes, EntityDamageCause } from '@minecraft/server';
import { ELEMENTS } from '../data/elements.js';
import { CONFIG, epForLevel } from '../config.js';
import { RACES } from '../data/registry.js';
import {
  V, dealDamage, addEffects, particle, sound, lookTarget, lookPoint, enemiesAround, alliesAround, heal,
  knockFrom, msg, isBoss, isAlly, isUndead, blockDistance, QUERY_EXCLUDE,
} from './util.js';
import { launchBolt } from './bolts.js';
import { addField } from './fields.js';
import { placeTemp } from './tempBlocks.js';
import { summon } from './summons.js';
import { getData, markDirty, refreshStats, saveNow } from './storage.js';
import { fxList } from '../core/progression.js';
import { mimicTraits } from '../core/stats.js';
import { effectName } from '../core/describe.js';
import { recordAnalysis } from './sage.js';
import { mobEp } from './leveling.js';

const NEGATIVE = ['poison', 'wither', 'nausea', 'blindness', 'weakness', 'slowness', 'hunger', 'darkness', 'mining_fatigue', 'fatal_poison', 'levitation', 'bad_omen'];
const UNBREAKABLE = /(bedrock|barrier|command_block|structure_block|jigsaw|end_portal|portal|end_gateway|reinforced_deepslate|light_block|allow|deny|border_block)/;

/**
 * Executa todos os efeitos de uma habilidade.
 * @returns {boolean} false se a magia não teve alvo/efeito (não consome Magicules)
 */
export function runFx(player, skill) {
  let ok = true;
  for (const f of fxList(skill.fx)) {
    const fn = HANDLERS[f.k];
    if (!fn) continue;
    const r = fn(player, f, skill);
    if (r === false) ok = false;
  }
  return ok;
}

function elOf(f) { return ELEMENTS[f.el] ?? ELEMENTS.spirit; }

function lineParticles(dim, from, dir, len, id) {
  const n = Math.min(48, Math.ceil(len));
  for (let i = 1; i <= n; i++) particle(dim, id, V.add(from, V.mul(dir, i)));
}

function targetsOf(player, f) {
  // Alvo único (mirado), área no ponto mirado, ou área ao redor de si.
  if (f.r && f.self) return enemiesAround(player, player.location, f.r);
  if (f.r) {
    const p = lookPoint(player, f.range ?? 16);
    return enemiesAround(player, p, f.r);
  }
  const t = lookTarget(player, f.range ?? 16);
  return t ? [t] : [];
}

function noTarget(player) {
  msg(player, '§7Nenhum alvo na mira.');
  return false;
}

const HANDLERS = {
  bolt(player, f) {
    const n = f.count ?? 1;
    const base = player.getViewDirection();
    for (let i = 0; i < n; i++) {
      let dir = base;
      if (n > 1) {
        const ang = ((i - (n - 1) / 2) * (f.spread ?? 10) * Math.PI) / 180;
        dir = V.norm({ x: base.x * Math.cos(ang) - base.z * Math.sin(ang), y: base.y, z: base.x * Math.sin(ang) + base.z * Math.cos(ang) });
      }
      launchBolt(player, f, dir);
    }
    sound(player.dimension, elOf(f).sound, player.location);
  },

  beam(player, f) {
    const dim = player.dimension;
    const from = player.getHeadLocation();
    const dir = player.getViewDirection();
    const range = Math.min(f.range, blockDistance(player, f.range));
    let hits = [];
    try { hits = dim.getEntitiesFromRay(from, dir, { maxDistance: range, ...QUERY_EXCLUDE }); } catch { /* */ }
    let n = 0;
    for (const h of hits) {
      if (isAlly(player, h.entity)) continue;
      if (dealDamage(player, h.entity, f.dmg, f.el) > 0) {
        n++;
        if (f.ignite) { try { h.entity.setOnFire(f.ignite, true); } catch { /* */ } }
        if (f.eff) addEffects(h.entity, f.eff);
        if (f.mpBurn && h.entity.typeId === 'minecraft:player') {
          const td = getData(h.entity);
          td.mp = Math.max(0, td.mp - f.mpBurn);
        }
      }
      if (!f.pierce) break;
    }
    lineParticles(dim, from, dir, range, elOf(f).particle);
    if (f.explodeEvery) {
      for (let d = f.explodeEvery; d <= range; d += f.explodeEvery) {
        const p = V.add(from, V.mul(dir, d));
        try { dim.createExplosion(p, f.explode, { breaksBlocks: CONFIG.explosionsBreakBlocks, causesFire: false, source: player }); } catch { /* */ }
      }
    }
    sound(dim, elOf(f).sound, player.location);
    return true;
  },

  cone(player, f) {
    const dir = V.flat(player.getViewDirection());
    const origin = player.location;
    let n = 0;
    for (const e of enemiesAround(player, origin, f.range)) {
      const to = V.flat(V.sub(e.location, origin));
      if (V.dot(dir, to) < 0.62) continue; // ~52° de abertura
      if (f.execute && !isBoss(e)) {
        const h = e.getComponent('minecraft:health');
        if (h && h.effectiveMax <= f.execute) { try { e.applyDamage(h.currentValue + 100, { cause: EntityDamageCause.magic, damagingEntity: player }); } catch { /* */ } n++; continue; }
      }
      const dealt = dealDamage(player, e, f.dmg, f.el);
      if (dealt > 0) {
        n++;
        if (f.ignite) { try { e.setOnFire(f.ignite, true); } catch { /* */ } }
        if (f.eff?.length) addEffects(e, f.eff);
        if (f.knock) { try { e.applyKnockback({ x: dir.x * f.knock, z: dir.z * f.knock }, f.lift ?? 0.3); } catch { /* */ } }
        if (f.lifesteal) heal(player, dealt * f.lifesteal);
      }
    }
    const el = elOf(f);
    for (let d = 1; d <= f.range; d += 1.5) {
      for (const s of [-0.5, 0, 0.5]) {
        const side = { x: -dir.z * s * d * 0.6, y: 0, z: dir.x * s * d * 0.6 };
        particle(player.dimension, el.particle, V.add(V.add(player.getHeadLocation(), V.mul(dir, d)), side));
      }
    }
    sound(player.dimension, el.sound, player.location);
  },

  nova(player, f) {
    const center = f.self ? player.location : lookPoint(player, f.range ?? 16);
    const el = elOf(f);
    for (const e of enemiesAround(player, center, f.r)) {
      if (f.undeadOnly && !isUndead(e)) continue;
      if (dealDamage(player, e, f.dmg, f.el) > 0) {
        if (f.eff) addEffects(e, f.eff);
        if (f.knock) knockFrom(e, center, f.knock, 0.4);
      }
    }
    for (let i = 0; i < 16; i++) {
      const a = (i / 16) * Math.PI * 2;
      particle(player.dimension, el.particle, { x: center.x + Math.cos(a) * f.r, y: center.y + 0.5, z: center.z + Math.sin(a) * f.r });
    }
    particle(player.dimension, el.impact, center);
    sound(player.dimension, el.sound, center);
  },

  rain(player, f) {
    const center = lookPoint(player, f.range);
    const dim = player.dimension;
    const el = elOf(f);
    for (let w = 0; w < f.waves; w++) {
      system.runTimeout(() => {
        if (!player.isValid) return;
        const a = Math.random() * Math.PI * 2;
        const rr = Math.random() * f.r;
        const p = { x: center.x + Math.cos(a) * rr, y: center.y, z: center.z + Math.sin(a) * rr };
        for (let h = 8; h > 0; h -= 2) particle(dim, el.particle, { x: p.x, y: p.y + h, z: p.z });
        particle(dim, el.impact, p);
        if (f.lightning) { try { dim.spawnEntity('minecraft:lightning_bolt', p); } catch { /* */ } }
        for (const e of enemiesAround(player, p, 2.5, dim)) {
          if (dealDamage(player, e, f.dmg, f.el) > 0) {
            if (f.ignite) { try { e.setOnFire(f.ignite, true); } catch { /* */ } }
            if (f.eff) addEffects(e, f.eff);
          }
        }
        sound(dim, el.sound, p, { volume: 0.6 });
      }, w * (f.every ?? 10));
    }
  },

  chain(player, f) {
    const dir = V.flat(player.getViewDirection());
    const start = player.location;
    const dim = player.dimension;
    for (let i = 1; i <= f.count; i++) {
      system.runTimeout(() => {
        if (!player.isValid) return;
        const p = V.add(start, V.mul(dir, i * f.step));
        try { dim.createExplosion(p, f.explode, { breaksBlocks: CONFIG.explosionsBreakBlocks, causesFire: false, source: player }); } catch { /* */ }
        for (const e of enemiesAround(player, p, f.r, dim)) dealDamage(player, e, f.dmg, f.el);
      }, i * f.every);
    }
  },

  wall(player, f) {
    const dir = V.flat(player.getViewDirection());
    const side = { x: -dir.z, y: 0, z: dir.x };
    const base = V.add(player.location, V.mul(dir, f.dist));
    let placed = 0;
    const half = Math.floor(f.len / 2);
    for (let i = -half; i <= half; i++) {
      for (let h = 0; h < f.h; h++) {
        const p = V.add(base, { x: side.x * i, y: h, z: side.z * i });
        if (placeTemp(player.dimension, p, f.block, f.dur)) placed++;
      }
    }
    if (!placed) { msg(player, '§7Sem espaço livre para erguer a parede.'); return false; }
    sound(player.dimension, 'dig.stone', base);
  },

  bind(player, f) {
    const targets = targetsOf(player, f).filter((e) => !isBoss(e) || !f.cage);
    if (!targets.length) return noTarget(player);
    const until = system.currentTick + f.dur * 20;
    for (const e of targets) {
      const lock = e.location;
      addEffects(e, [['slowness', f.dur, 255], ['weakness', f.dur, 1], ...(f.eff ?? [])]);
      if (f.cage === 'water') {
        for (const dy of [0, 1]) placeTemp(e.dimension, V.add(lock, { x: 0, y: dy, z: 0 }), 'minecraft:water', f.dur, true);
      } else if (f.cage) {
        for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) for (const dy of [0, 1]) placeTemp(e.dimension, V.add(lock, { x: dx, y: dy, z: dz }), f.cage, f.dur);
        placeTemp(e.dimension, V.add(lock, { x: 0, y: 2, z: 0 }), f.cage, f.dur);
      }
      const id = system.runInterval(() => {
        if (!e.isValid || system.currentTick >= until || !player.isValid) { system.clearRun(id); return; }
        try { e.teleport(lock, { keepVelocity: false }); } catch { /* */ }
        if (f.dps) dealDamage(player, e, f.dps, f.el);
        particle(e.dimension, elOf(f).particle, V.add(lock, { x: 0, y: 1, z: 0 }));
      }, 20);
    }
    sound(player.dimension, 'random.glass', player.location);
  },

  debuff(player, f) {
    const targets = targetsOf(player, f);
    if (!targets.length) return f.self ? true : noTarget(player);
    for (const e of targets) {
      if (f.dmg) dealDamage(player, e, f.dmg, f.el);
      addEffects(e, f.eff);
      particle(e.dimension, elOf(f).impact, V.add(e.location, { x: 0, y: 1, z: 0 }));
    }
    sound(player.dimension, elOf(f).sound, player.location);
  },

  field(player, f) {
    const center = f.self ? player.location : lookPoint(player, 12);
    addField(player, f, center);
    sound(player.dimension, 'beacon.activate', center);
  },

  buff(player, f) {
    const list = f.allies ? alliesAround(player, f.allies) : [player];
    if (!list.some((e) => e.id === player.id)) list.push(player);
    for (const e of list) addEffects(e, f.eff);
    particle(player.dimension, 'minecraft:totem_particle', V.add(player.location, { x: 0, y: 1, z: 0 }));
    sound(player.dimension, 'random.orb', player.location);
  },

  heal(player, f) {
    const h = player.getComponent('minecraft:health');
    if (f.full) h?.resetToMaxValue();
    else heal(player, f.amount);
    if (f.allies) {
      for (const a of alliesAround(player, f.allies)) {
        if (a.id === player.id) continue;
        heal(a, f.allyFull ? f.amount : f.amount / 2);
        particle(a.dimension, 'minecraft:heart_particle', V.add(a.location, { x: 0, y: 2, z: 0 }));
      }
    }
    particle(player.dimension, 'minecraft:heart_particle', V.add(player.location, { x: 0, y: 2, z: 0 }));
    sound(player.dimension, 'random.orb', player.location);
  },

  cleanse(player, f) {
    const list = f.allies ? alliesAround(player, f.allies) : [player];
    if (!list.some((e) => e.id === player.id)) list.push(player);
    for (const e of list) {
      if (f.fire) { try { e.extinguishFire(true); } catch { /* */ } }
      if (f.negative) for (const id of NEGATIVE) { try { e.removeEffect(id); } catch { /* */ } }
    }
  },

  drain(player, f) {
    const targets = f.r ? enemiesAround(player, player.location, f.r) : [lookTarget(player, f.range)].filter(Boolean);
    if (!targets.length) return noTarget(player);
    let total = 0;
    for (const e of targets) {
      const dealt = dealDamage(player, e, f.dmg, f.el);
      total += dealt;
      if (dealt) lineParticles(player.dimension, e.location, V.norm(V.sub(player.location, e.location)), V.dist(e.location, player.location), elOf(f).particle);
    }
    heal(player, total * (f.heal ?? 0));
    const d = getData(player);
    if (d.stats) d.mp = Math.min(d.stats.maxMp, d.mp + (f.mpGain ?? 0) * targets.length);
    sound(player.dimension, elOf(f).sound, player.location);
  },

  summon(player, f) {
    summon(player, f.entity, f.count, f.dur, { revive: f.revive });
    for (const [type, n] of f.extra ?? []) summon(player, type, n, f.dur, { revive: f.revive });
  },

  blink(player, f) {
    const back = f.range < 0;
    const dir = back ? V.mul(V.flat(player.getViewDirection()), -1) : player.getViewDirection();
    const range = Math.abs(f.range);
    let d = range;
    if (!back) d = Math.max(0, blockDistance(player, range) - 1.2);
    const to = V.add(player.location, V.mul(dir, d));
    particle(player.dimension, 'minecraft:basic_portal_particle', player.location);
    try { player.tryTeleport(to, { checkForBlocks: true, keepVelocity: false }) || player.teleport(V.add(player.location, V.mul(dir, d * 0.5))); } catch { /* */ }
    particle(player.dimension, 'minecraft:basic_portal_particle', to);
    sound(player.dimension, 'mob.endermen.portal', to);
  },

  strike(player, f) {
    const t = lookTarget(player, f.range);
    if (!t) return noTarget(player);
    const behind = V.add(t.location, V.mul(V.flat(V.sub(t.location, player.location)), 1.5));
    try { player.teleport(behind, { facingLocation: t.location }); } catch { /* */ }
    dealDamage(player, t, f.dmg, f.el);
    particle(player.dimension, elOf(f).impact, t.location);
    sound(player.dimension, elOf(f).sound, t.location);
  },

  streak(player, f) {
    const list = enemiesAround(player, player.location, f.r)
      .sort((a, b) => V.dist(a.location, player.location) - V.dist(b.location, player.location)).slice(0, f.count);
    if (!list.length) return noTarget(player);
    list.forEach((e, i) => system.runTimeout(() => {
      if (!player.isValid || !e.isValid) return;
      const to = V.add(e.location, V.mul(V.flat(V.sub(player.location, e.location)), 1.2));
      try { player.teleport(to, { facingLocation: e.location }); } catch { /* */ }
      dealDamage(player, e, f.dmg, f.el);
      particle(player.dimension, elOf(f).impact, e.location);
      sound(player.dimension, 'mob.wither.shoot', e.location, { volume: 0.5 });
    }, i * 5));
  },

  gravity(player, f) {
    const center = lookPoint(player, f.range);
    const list = enemiesAround(player, center, f.r).filter((e) => !isBoss(e));
    if (!list.length) return noTarget(player);
    for (const e of list) {
      if (f.pull) {
        const dir = V.flat(V.sub(center, e.location));
        try { e.applyKnockback({ x: dir.x * 1.5, z: dir.z * 1.5 }, 0.2); } catch { /* */ }
        dealDamage(player, e, f.dmg, f.el);
      } else {
        addEffects(e, [['levitation', 1.5, 3]]);
      }
    }
    if (!f.pull) {
      system.runTimeout(() => {
        for (const e of list) {
          if (!e.isValid) continue;
          try { e.removeEffect('levitation'); } catch { /* */ }
          try { e.applyKnockback({ x: 0, z: 0 }, -2.5); } catch { /* */ }
          if (player.isValid) dealDamage(player, e, f.dmg, f.el);
          addEffects(e, [['slowness', 4, 2]]);
          particle(e.dimension, 'minecraft:smash_ground_particle_center', e.location);
        }
      }, 32);
    }
    particle(player.dimension, elOf(f).impact, center);
    sound(player.dimension, elOf(f).sound, center);
  },

  dominate(player, f) {
    const targets = (f.r ? enemiesAround(player, lookPoint(player, f.range), f.r) : [lookTarget(player, f.range)].filter(Boolean))
      .filter((e) => !isBoss(e) && e.typeId !== 'minecraft:player');
    if (!targets.length) return noTarget(player);
    let tamed = 0;
    for (const e of targets) {
      const tame = e.getComponent('minecraft:tameable');
      if (tame && !tame.isTamed) {
        try { if (tame.tame(player)) { tamed++; continue; } } catch { /* */ }
      }
      addEffects(e, [['weakness', f.dur, 255], ['slowness', f.dur, 1]]);
      particle(e.dimension, 'minecraft:villager_angry', V.add(e.location, { x: 0, y: 2, z: 0 }));
    }
    msg(player, `§dDominação: ${targets.length} criatura(s) pacificada(s)${tamed ? `, ${tamed} domada(s)` : ''}.`);
  },

  marionette(player, f) {
    let list = f.r ? enemiesAround(player, player.location, f.r) : [lookTarget(player, f.range)].filter(Boolean);
    list = list.filter((e) => {
      if (isBoss(e) || e.typeId === 'minecraft:player') return false;
      const h = e.getComponent('minecraft:health');
      return h && h.currentValue <= h.effectiveMax * f.hpPct;
    }).slice(0, f.count);
    if (!list.length) { msg(player, `§7Nenhuma criatura enfraquecida o bastante (até ${Math.round(f.hpPct * 100)}% de vida).`); return false; }
    for (const e of list) {
      const loc = e.location;
      particle(e.dimension, 'minecraft:soul_particle', loc);
      try { e.remove(); } catch { continue; }
      const [m] = summon(player, 'tensura:undead_warrior', 1, f.dur, {});
      if (m) { try { m.teleport(loc); m.nameTag = '§5Marionete'; } catch { /* */ } }
    }
  },

  possess(player, f) {
    const t = lookTarget(player, f.range);
    if (!t) return noTarget(player);
    addEffects(player, [['invisibility', f.dur, 0]]);
    addEffects(t, [['slowness', f.dur, 3], ['weakness', f.dur, 255]]);
    let n = 0;
    const id = system.runInterval(() => {
      n++;
      if (!player.isValid || !t.isValid || n > f.dur) { system.clearRun(id); return; }
      const dealt = dealDamage(player, t, f.dps, 'dark');
      heal(player, dealt * 0.5);
      particle(t.dimension, 'minecraft:soul_particle', V.add(t.location, { x: 0, y: 1, z: 0 }));
    }, 20);
  },

  swap(player, f) {
    const t = lookTarget(player, f.range);
    if (!t || isBoss(t)) return noTarget(player);
    const a = player.location;
    const b = t.location;
    try { player.teleport(b); t.teleport(a); } catch { /* */ }
    addEffects(t, [['slowness', f.stun, 255], ['blindness', f.stun, 0], ['nausea', f.stun + 2, 0]]);
    addEffects(player, [['invisibility', 3, 0]]);
    sound(player.dimension, 'mob.shulker.teleport', b);
  },

  absorb(player, f) {
    const t = lookTarget(player, f.range);
    if (!t || t.typeId === 'minecraft:player' || isBoss(t)) return noTarget(player);
    const h = t.getComponent('minecraft:health');
    if (!h || h.currentValue > h.effectiveMax * f.hpPct) {
      msg(player, `§7O alvo precisa estar com até ${Math.round(f.hpPct * 100)}% da vida para ser absorvido.`);
      return false;
    }
    const d = getData(player);
    const type = t.typeId;
    const loc = t.location;
    try { t.applyDamage(h.currentValue + 100, { cause: EntityDamageCause.magic, damagingEntity: player }); } catch { /* */ }
    if (d.mpBonus < f.mpCap) d.mpBonus = Math.min(f.mpCap, d.mpBonus + f.mpGain);
    const isNew = !d.absorbed.includes(type);
    if (isNew) d.absorbed.push(type);
    refreshStats(d);
    saveNow(player);
    for (let i = 0; i < 8; i++) particle(player.dimension, 'minecraft:oozing_emitter', V.add(loc, { x: Math.random() - 0.5, y: Math.random(), z: Math.random() - 0.5 }));
    sound(player.dimension, 'mob.slime.squish', loc);
    msg(player, `§bAbsorção: +${f.mpGain} Magicules máximas (bônus ${d.mpBonus}/${f.mpCap}).${isNew ? ` Nova forma registrada: §e${mimicTraits(type).name}` : ''}`);
  },

  dissolve(player, f) {
    const t = lookTarget(player, f.range);
    if (t) {
      dealDamage(player, t, f.dmg, 'acid');
      addEffects(t, f.eff);
      particle(player.dimension, 'minecraft:oozing_emitter', t.location);
      return true;
    }
    let hit;
    try { hit = player.getBlockFromViewDirection({ maxDistance: f.range }); } catch { /* */ }
    if (!hit) return noTarget(player);
    const b = hit.block;
    if (UNBREAKABLE.test(b.typeId)) { msg(player, '§7Este bloco não pode ser dissolvido.'); return false; }
    const { x, y, z } = b.location;
    try { player.dimension.runCommand(`setblock ${x} ${y} ${z} air destroy`); } catch { return false; }
    particle(player.dimension, 'minecraft:oozing_emitter', b.location);
    sound(player.dimension, 'mob.slime.squish', b.location);
  },

  mimic(player) {
    openMimicMenu(player);
  },

  flight(player, f) {
    const d = getData(player);
    d.flying = !d.flying;
    d.flightDrain = f.drain;
    if (!d.flying) { try { player.removeEffect('levitation'); } catch { /* */ } }
    addEffects(player, [['slow_falling', 6, 0]]);
    msg(player, d.flying ? '§bVoo ativado: olhe para cima para subir, à frente para planar, agache para descer.' : '§7Voo desativado.');
    markDirty(player);
  },

  analyze(player, f) {
    const t = lookTarget(player, f.range) ?? (() => {
      try { return player.getEntitiesFromViewDirection({ maxDistance: f.range, ...QUERY_EXCLUDE })[0]?.entity; } catch { return null; }
    })();
    if (!t) return noTarget(player);
    const h = t.getComponent('minecraft:health');
    const lines = [`§b§lAnálise: §r${t.nameTag || t.typeId.replace('minecraft:', '')}`];
    if (h) lines.push(`Vida: §c${Math.ceil(h.currentValue)}/${Math.ceil(h.effectiveMax)}`);
    const effs = t.getEffects().map((e) => `${effectName(e.typeId)} ${e.amplifier + 1}`);
    if (effs.length) lines.push(`Efeitos: ${effs.join(', ')}`);
    if (t.typeId === 'minecraft:player') {
      const td = getData(t);
      if (td.race) lines.push(`Raça: ${RACES[td.race].name} · Nível ${td.level}`);
    } else {
      lines.push(`EP ao derrotar: §e${mobEp(t)}`);
      lines.push(`Morto-vivo: ${isUndead(t) ? 'sim (fraco ao sagrado)' : 'não'}`);
    }
    msg(player, lines.join('\n'));
    try { recordAnalysis(player, t); } catch (e) { console.warn(`[Tensura] Grande Sábio (análise): ${e}`); }
  },

  sense(player, f) {
    let list = [];
    try { list = player.dimension.getEntities({ location: player.location, maxDistance: f.r, ...QUERY_EXCLUDE }); } catch { /* */ }
    list = list.filter((e) => e.id !== player.id);
    if (!list.length) { msg(player, '§7Nenhuma criatura por perto.'); return true; }
    const counts = {};
    for (const e of list) counts[e.typeId] = (counts[e.typeId] ?? 0) + 1;
    const nearest = list.sort((a, b) => V.dist(a.location, player.location) - V.dist(b.location, player.location)).slice(0, 5);
    const lines = [`§a§lCriaturas num raio de ${f.r}:§r ${Object.entries(counts).map(([k, v]) => `${k.replace('minecraft:', '')} x${v}`).join(', ')}`];
    for (const e of nearest) lines.push(`§7• ${e.typeId.replace('minecraft:', '')}: ${Math.round(V.dist(e.location, player.location))} blocos ${compass(player.location, e.location)}`);
    for (const e of list.slice(0, 24)) particle(player.dimension, 'minecraft:endrod', V.add(e.location, { x: 0, y: 2.2, z: 0 }));
    msg(player, lines.join('\n'));
  },

  ore_sense(player, f) {
    const ores = {};
    const p = player.location;
    const dim = player.dimension;
    for (let x = -f.r; x <= f.r; x++) for (let y = -f.r; y <= f.r; y++) for (let z = -f.r; z <= f.r; z++) {
      let b;
      try { b = dim.getBlock({ x: Math.floor(p.x) + x, y: Math.floor(p.y) + y, z: Math.floor(p.z) + z }); } catch { continue; }
      if (b && /_ore$|ancient_debris/.test(b.typeId)) {
        const k = b.typeId.replace('minecraft:', '').replace('deepslate_', '');
        if (!ores[k]) ores[k] = { n: 0, loc: b.location };
        ores[k].n++;
      }
    }
    const keys = Object.keys(ores);
    if (!keys.length) { msg(player, '§7Nenhum minério por perto.'); return true; }
    msg(player, `§6§lMinérios (raio ${f.r}):§r ${keys.map((k) => `${k} x${ores[k].n} (${compass(p, ores[k].loc)})`).join(', ')}`);
  },

  comm(player) {
    const others = world.getAllPlayers().filter((o) => o.id !== player.id);
    if (!others.length) { msg(player, '§7Nenhum outro jogador conectado.'); return true; }
    const lines = ['§d§lComunicação Mental:'];
    for (const o of others) {
      const l = o.location;
      lines.push(`§7• ${o.name}: ${Math.round(l.x)}, ${Math.round(l.y)}, ${Math.round(l.z)} (${o.dimension.id.replace('minecraft:', '')})`);
      msg(o, `§d${player.name} entrou em contato mental com você (${Math.round(player.location.x)}, ${Math.round(player.location.y)}, ${Math.round(player.location.z)}).`);
    }
    msg(player, lines.join('\n'));
  },

  storage(player) {
    openStorageMenu(player);
  },

  warp(player) {
    const d = getData(player);
    if (player.isSneaking) {
      const l = player.location;
      d.warp = { x: l.x, y: l.y, z: l.z, dim: player.dimension.id };
      saveNow(player);
      msg(player, `§bPonto de retorno marcado em ${Math.round(l.x)}, ${Math.round(l.y)}, ${Math.round(l.z)}.`);
      return true;
    }
    if (!d.warp) { msg(player, '§7Nenhum ponto marcado. Use agachado para marcar.'); return false; }
    particle(player.dimension, 'minecraft:huge_explosion_emitter', player.location);
    try { player.teleport({ x: d.warp.x, y: d.warp.y, z: d.warp.z }, { dimension: world.getDimension(d.warp.dim) }); } catch { return false; }
    system.runTimeout(() => { if (player.isValid) sound(player.dimension, 'mob.shulker.teleport', player.location); }, 2);
  },

  timeflip(player) {
    world.setTimeOfDay((world.getTimeOfDay() + 12000) % 24000);
    world.sendMessage(`§5${player.name} inverteu o dia e a noite.`);
  },

  create(player) {
    const inv = player.getComponent('minecraft:inventory')?.container;
    if (!inv) return false;
    const roll = Math.random();
    const [id, min, max] = roll < 0.08 ? ['minecraft:diamond', 1, 1] : roll < 0.25 ? ['minecraft:emerald', 1, 2]
      : roll < 0.5 ? ['minecraft:gold_ingot', 1, 3] : ['minecraft:iron_ingot', 2, 5];
    const n = min + Math.floor(Math.random() * (max - min + 1));
    const left = inv.addItem(new ItemStack(id, n));
    if (left) player.dimension.spawnItem(left, player.location);
    msg(player, `§eCriação de Materiais: ${n}x ${id.replace('minecraft:', '')}.`);
    sound(player.dimension, 'random.anvil_land', player.location, { volume: 0.4 });
  },

  enchant(player) {
    const eq = player.getComponent('minecraft:equippable');
    const item = eq?.getEquipment(EquipmentSlot.Mainhand);
    const ench = item?.getComponent('minecraft:enchantable');
    if (!item || !ench) { msg(player, '§7Segure um item encantável na mão principal.'); return false; }
    const d = getData(player);
    const options = EnchantmentTypes.getAll().filter((t) => {
      const level = Math.min(t.maxLevel, 1 + Math.floor(d.level / 15));
      try { return ench.canAddEnchantment({ type: t, level }) && !ench.hasEnchantment(t); } catch { return false; }
    });
    if (!options.length) { msg(player, '§7Nenhum encantamento compatível disponível para este item.'); return false; }
    const t = options[Math.floor(Math.random() * options.length)];
    const level = Math.min(t.maxLevel, 1 + Math.floor(d.level / 15));
    ench.addEnchantment({ type: t, level });
    eq.setEquipment(EquipmentSlot.Mainhand, item);
    msg(player, `§dEncantamento aplicado: ${t.id.replace('minecraft:', '')} ${level}.`);
    sound(player.dimension, 'block.enchanting_table.use', player.location);
  },

  repair(player) {
    const eq = player.getComponent('minecraft:equippable');
    const item = eq?.getEquipment(EquipmentSlot.Mainhand);
    const dur = item?.getComponent('minecraft:durability');
    if (!item || !dur || dur.damage === 0) { msg(player, '§7Segure um item danificado na mão principal.'); return false; }
    dur.damage = 0;
    eq.setEquipment(EquipmentSlot.Mainhand, item);
    msg(player, '§aItem reparado.');
    sound(player.dimension, 'random.anvil_land', player.location, { volume: 0.5 });
  },

  imbue(player, f) {
    const d = getData(player);
    d.imbue = { el: f.el, dmg: f.dmg, ignite: f.ignite ?? 0, eff: f.eff ?? [], until: system.currentTick + f.dur * 20 };
  },
};

function compass(from, to) {
  const dx = to.x - from.x;
  const dz = to.z - from.z;
  const ang = (Math.atan2(dx, -dz) * 180) / Math.PI;
  const dirs = ['N', 'NE', 'L', 'SE', 'S', 'SO', 'O', 'NO'];
  return dirs[Math.round(((ang + 360) % 360) / 45) % 8];
}

// Menus usados por efeitos (injeção para evitar dependência circular com ui/).
/** @type {(p: import('@minecraft/server').Player) => void} */
let openMimicMenu = () => {};
/** @type {(p: import('@minecraft/server').Player) => void} */
let openStorageMenu = () => {};
export function setEffectMenus(m) {
  openMimicMenu = m.mimic;
  openStorageMenu = m.storage;
}

export { epForLevel };
