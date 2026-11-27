// Magicules: regeneração, gasto e HUD (barra na action bar).
import { world, system } from '@minecraft/server';
import { CONFIG } from '../config.js';
import { SKILLS } from '../data/registry.js';
import { getData, markDirty } from './storage.js';

const HUD_ITEMS = new Set(['tensura:great_sage_tome', 'tensura:magic_crystal']);

export function spend(player, amount) {
  const d = getData(player);
  if (d.mp < amount) return false;
  d.mp -= amount;
  d.lastCast = system.currentTick;
  markDirty(player);
  return true;
}

function bar(cur, max, n = 20) {
  const f = Math.round((cur / Math.max(1, max)) * n);
  return `§d${'|'.repeat(f)}§8${'|'.repeat(n - f)}`;
}

function holdingTensura(player) {
  try {
    const it = player.getComponent('minecraft:inventory')?.container?.getItem(player.selectedSlotIndex);
    return it && HUD_ITEMS.has(it.typeId);
  } catch { return false; }
}

export function hudLine(player) {
  const d = getData(player);
  if (!d.stats) return '';
  let s = `§dMagicules ${bar(d.mp, d.stats.maxMp)} §f${Math.floor(d.mp)}/${d.stats.maxMp}`;
  if (d.equipped.length) {
    const id = d.equipped[d.slot % d.equipped.length];
    const sk = SKILLS[id];
    const cd = Math.max(0, Math.ceil(((d.cooldowns[id] ?? 0) - system.currentTick) / 20));
    s += `  §7| §b[${(d.slot % d.equipped.length) + 1}/${d.equipped.length}] ${sk.name}${cd ? ` §c(${cd}s)` : ' §a✓'}`;
  }
  if (d.flying) s += '  §7| §bVoando';
  if (d.mimic) s += '  §7| §eForma ativa';
  return s;
}

export function showHud(player) {
  const line = hudLine(player);
  if (line) { try { player.onScreenDisplay.setActionBar(line); } catch { /* */ } }
}

export function startMagicules() {
  const every = CONFIG.magicules.regenTickSeconds * 20;
  system.runInterval(() => {
    const now = system.currentTick;
    for (const p of world.getAllPlayers()) {
      const d = getData(p);
      if (!d.stats) continue;
      if (d.mp < d.stats.maxMp) {
        let r = d.stats.regen * d.stats.regenMult * CONFIG.magicules.regenTickSeconds;
        if (now - d.lastCast < 100) r *= CONFIG.magicules.combatRegenFactor;
        d.mp = Math.min(d.stats.maxMp, d.mp + r);
        markDirty(p);
      }
      if (holdingTensura(p) || now - d.lastCast < CONFIG.magicules.hudSeconds * 20 || d.flying) showHud(p);
    }
  }, every);
}
