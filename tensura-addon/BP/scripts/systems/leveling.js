// EP (Existence Points), níveis, pontos de habilidade e conquistas.
import { world, system } from '@minecraft/server';
import { CONFIG, epForLevel } from '../config.js';
import { getData, saveNow, refreshStats } from './storage.js';
import { ownerOfSummon, onSummonDied } from './summons.js';
import { addEffects, msg, sound, heal } from './util.js';

const BOSS_FLAGS = { 'minecraft:wither': 'killed_wither', 'minecraft:ender_dragon': 'killed_ender_dragon', 'minecraft:warden': 'killed_warden' };

export function mobEp(e) {
  const h = e.getComponent('minecraft:health');
  const max = h?.effectiveMax ?? 10;
  let ep = max * CONFIG.leveling.epPerMobHealth;
  try { if (e.matches({ families: ['monster'] })) ep *= CONFIG.leveling.hostileMultiplier; } catch { /* */ }
  return Math.max(1, Math.round(ep));
}

export function grantEp(player, amount) {
  const d = getData(player);
  if (!d.race) return;
  d.ep += amount;
  let ups = 0;
  while (d.level < CONFIG.leveling.maxLevel && d.ep >= epForLevel(d.level)) {
    d.ep -= epForLevel(d.level);
    d.level++;
    d.points += CONFIG.leveling.pointsPerLevel;
    ups++;
  }
  if (d.level >= CONFIG.leveling.maxLevel) d.ep = Math.min(d.ep, epForLevel(d.level));
  if (ups) {
    refreshStats(d);
    d.mp = d.stats.maxMp;
    saveNow(player);
    sound(player.dimension, 'random.levelup', player.location);
    msg(player, `§6§lNível ${d.level}!§r §e+${ups * CONFIG.leveling.pointsPerLevel} pontos de habilidade§r (total ${d.points}). Magicules restauradas.`);
  }
}

function killerOf(ev) {
  const src = ev.damageSource?.damagingEntity;
  if (!src?.isValid) return null;
  if (src.typeId === 'minecraft:player') return src;
  const ownerId = ownerOfSummon(src.id);
  if (ownerId) return world.getEntity(ownerId) ?? null;
  return null;
}

export function startLeveling() {
  world.afterEvents.entityDie.subscribe((ev) => {
    const dead = ev.deadEntity;
    if (dead.typeId.startsWith('tensura:')) { onSummonDied(dead); return; }
    if (dead.typeId === 'minecraft:player') return;
    const killer = killerOf(ev);
    if (!killer || killer.typeId !== 'minecraft:player') return;
    const d = getData(killer);
    if (!d.race) return;
    let ep = 0;
    try { ep = mobEp(dead); } catch { ep = 5; }
    const flag = BOSS_FLAGS[dead.typeId];
    if (flag && !d.flags.has(flag)) {
      d.flags.add(flag);
      d.points += CONFIG.leveling.bossBonusPoints;
      msg(killer, `§c§lConquista!§r ${flag === 'killed_wither' ? 'Wither derrotado' : flag === 'killed_ender_dragon' ? 'Ender Dragon derrotado' : 'Warden derrotado'}: +${CONFIG.leveling.bossBonusPoints} pontos e novos requisitos atendidos.`);
      saveNow(killer);
    }
    // Fome / Devorar: recupera fome e Magicules.
    const st = d.stats?.starved;
    if (st) {
      addEffects(killer, [['saturation', 1, 1]]);
      if (!st.noStrength) addEffects(killer, [['strength', 10, 0]]);
      d.mp = Math.min(d.stats.maxMp, d.mp + st.mp);
      heal(killer, 2);
    }
    grantEp(killer, ep);
  });
}

export { system };
