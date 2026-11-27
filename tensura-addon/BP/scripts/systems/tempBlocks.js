// Blocos temporários (paredes, jaulas, prisão de água). São restaurados depois e,
// se o mundo fechar antes, a lista salva no mundo permite limpar ao reabrir.
import { world, system } from '@minecraft/server';
import { CONFIG } from '../config.js';

const KEY = 'tensura:temp_blocks';
const active = []; // { dim, x, y, z, until, type }
const REPLACEABLE = new Set(['minecraft:air', 'minecraft:short_grass', 'minecraft:tall_grass', 'minecraft:fern', 'minecraft:large_fern',
  'minecraft:snow_layer', 'minecraft:deadbush', 'minecraft:dandelion', 'minecraft:poppy', 'minecraft:fire', 'minecraft:water',
  'minecraft:flowing_water', 'minecraft:light_block_15']);

function canReplace(block, allowWater) {
  if (!block) return false;
  if (block.isAir) return true;
  const id = block.typeId;
  if (!allowWater && (id === 'minecraft:water' || id === 'minecraft:flowing_water')) return false;
  return !CONFIG.safeBlockPlacement || REPLACEABLE.has(id);
}

export function placeTemp(dim, loc, type, seconds, allowWater = false) {
  if (active.length >= CONFIG.perf.maxTempBlocks) return false;
  const x = Math.floor(loc.x), y = Math.floor(loc.y), z = Math.floor(loc.z);
  let block;
  try { block = dim.getBlock({ x, y, z }); } catch { return false; }
  if (!canReplace(block, allowWater)) return false;
  if (active.some((b) => b.x === x && b.y === y && b.z === z && b.dim === dim.id)) return false;
  try { block.setType(type); } catch { return false; }
  active.push({ dim: dim.id, x, y, z, type, until: system.currentTick + Math.round(seconds * 20) });
  persist();
  return true;
}

function restore(b) {
  try {
    const block = world.getDimension(b.dim).getBlock({ x: b.x, y: b.y, z: b.z });
    if (block && (block.typeId === b.type || b.type === 'minecraft:water' || b.type === 'minecraft:fire')) block.setType('minecraft:air');
    return true;
  } catch {
    return false; // chunk descarregado: tenta de novo depois
  }
}

let persistQueued = false;
function persist() {
  if (persistQueued) return;
  persistQueued = true;
  system.runTimeout(() => {
    persistQueued = false;
    try { world.setDynamicProperty(KEY, JSON.stringify(active)); } catch { /* */ }
  }, 20);
}

export function startTempBlocks() {
  // Limpa sobras de uma sessão anterior.
  try {
    const old = JSON.parse(String(world.getDynamicProperty(KEY) ?? '[]'));
    for (const b of old) b.until = 0, active.push(b);
  } catch { /* */ }
  system.runInterval(() => {
    if (!active.length) return;
    const now = system.currentTick;
    let changed = false;
    for (let i = active.length - 1; i >= 0; i--) {
      if (active[i].until <= now && restore(active[i])) {
        active.splice(i, 1);
        changed = true;
      }
    }
    if (changed) persist();
  }, 10);
}
