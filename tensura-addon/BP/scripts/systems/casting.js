// Conjuração: valida desbloqueio, recarga, antimagia e Magicules; executa o efeito.
import { system } from '@minecraft/server';
import { SKILLS } from '../data/registry.js';
import { mpCost } from '../core/stats.js';
import { masteryMults } from '../core/mastery.js';
import { addMasteryUse } from './progressHooks.js';
import { TREES } from '../data/registry.js';
import { getData } from './storage.js';
import { spend, showHud } from './magicules.js';
import { antimagicBlocks } from './fields.js';
import { runFx } from './effects.js';
import { msg, sound, setReducedFx } from './util.js';

export function cast(player, skillId) {
  const d = getData(player);
  const s = SKILLS[skillId];
  if (!s || !d.stats) return false;
  if (!d.unlocked.has(skillId)) { msg(player, '§cHabilidade não desbloqueada.'); return false; }
  if (s.type !== 'active') { msg(player, '§7Habilidade passiva: já está sempre ativa.'); return false; }
  if (antimagicBlocks(player)) { msg(player, '§cUma Área Antimagia impede sua conjuração.'); sound(player.dimension, 'random.fizz', player.location); return false; }
  const now = system.currentTick;
  const ready = d.cooldowns[skillId] ?? 0;
  if (ready > now) { msg(player, `§7${s.name}: recarga ${Math.ceil((ready - now) / 20)}s.`); return false; }
  const mm = masteryMults(skillId, d.mastery[skillId] ?? 0);
  const cost = Math.round(mpCost(s, d.stats) * mm.cost);
  // Habilidades raciais gastam Aura; magias gastam Magículas.
  const aura = TREES[s.tree].kind === 'racial';
  const have = aura ? d.aura : d.mp;
  if (have < cost) { msg(player, `§c${aura ? 'Aura' : 'Magículas'} insuficiente: ${s.name} custa ${cost} (você tem ${Math.floor(have)}).`); sound(player.dimension, 'random.fizz', player.location); return false; }
  let ok;
  setReducedFx(d.settings?.fx === 'reduced');
  try {
    ok = runFx(player, s);
  } catch (e) {
    console.warn(`[Tensura] Erro ao conjurar ${skillId}: ${e}`);
    return false;
  } finally {
    setReducedFx(false);
  }
  if (ok === false) return false;
  if (aura) { d.aura -= cost; d.lastCast = now; } else spend(player, cost);
  if (s.cooldown) d.cooldowns[skillId] = now + Math.round(s.cooldown * 20 * (d.stats.cdMult ?? 1) * mm.cooldown);
  addMasteryUse(player, skillId);
  showHud(player);
  return true;
}
