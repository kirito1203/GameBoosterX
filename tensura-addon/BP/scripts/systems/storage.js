// Salvamento individual: tudo do jogador fica em propriedades dinâmicas da própria
// entidade do jogador (persistem no mundo, por jogador, entre sessões).
import { world, system } from '@minecraft/server';
import { computeStats } from '../core/stats.js';
import { CONFIG } from '../config.js';
import { serialize, deserialize } from '../core/save.js';
import { serializeSage, deserializeSage } from '../core/sage.js';

const KEY = 'tensura:data';
// Grande Sábio: propriedade separada (pode crescer com análises e conversas). Mesmo objeto para
// o menu, o item Núcleo e a entidade: d.sage.
const SAGE_KEY = 'tensura:sage';
const cache = new Map(); // player.id -> data em memória
const dirty = new Set();

/** Dados do jogador (carrega na primeira chamada). */
export function getData(player) {
  let d = cache.get(player.id);
  if (!d) {
    d = deserialize(player.getDynamicProperty(KEY));
    if (d.corrupted) console.warn(`[Tensura] Dados de ${player.name} estavam corrompidos e foram reiniciados.`);
    d.sage = deserializeSage(player.getDynamicProperty(SAGE_KEY));
    if (d.sage.corrupted) console.warn(`[Tensura] Dados do Grande Sábio de ${player.name} estavam corrompidos e foram reiniciados.`);
    d.sageDirty = false;
    cache.set(player.id, d);
    refreshStats(d);
  }
  return d;
}

export function refreshStats(d) {
  d.stats = d.race ? computeStats(d) : null;
  if (d.stats && d.mp > d.stats.maxMp) d.mp = d.stats.maxMp;
}

export function markDirty(player) {
  dirty.add(player.id);
}

/** Salva imediatamente (desbloqueios, evoluções, nível). */
export function saveNow(player) {
  const d = cache.get(player.id);
  if (!d || !player.isValid) return;
  try {
    player.setDynamicProperty(KEY, serialize(d));
    if (d.sageDirty) { player.setDynamicProperty(SAGE_KEY, serializeSage(d.sage)); d.sageDirty = false; }
    dirty.delete(player.id);
  } catch (e) {
    console.warn(`[Tensura] Falha ao salvar ${player.name}: ${e}`);
  }
}

export function forget(playerId) {
  cache.delete(playerId);
  dirty.delete(playerId);
}

export function progressionState(d) {
  return {
    race: d.race, level: d.level, points: d.points, unlocked: d.unlocked, flags: d.flags,
    maxMp: d.stats?.maxMp ?? 0, absorbed: new Set(d.absorbed), discovered: d.discovered, missions: d.missions, mastery: d.mastery,
  };
}

// Armazenamento Espacial: chave separada (pode crescer).
const STORAGE_KEY = 'tensura:storage';
export function getStorage(player) {
  try { return JSON.parse(String(player.getDynamicProperty(STORAGE_KEY) ?? '[]')); } catch { return []; }
}
export function setStorage(player, items) {
  player.setDynamicProperty(STORAGE_KEY, JSON.stringify(items));
}

// Salvamento periódico (mana atual, posição da câmera da árvore etc.).
export function startAutosave() {
  system.runInterval(() => {
    if (!dirty.size) return;
    for (const p of world.getAllPlayers()) if (dirty.has(p.id)) saveNow(p);
  }, 100);
}

export { CONFIG };
