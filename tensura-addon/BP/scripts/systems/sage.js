// Grande Sábio (sistema): experiência, análises, notificações, evolução e capacidades com efeito
// no jogo. Os dados ficam em d.sage (systems/storage.js → propriedade tensura:sage) e são os
// mesmos para o menu principal, o item Núcleo do Grande Sábio e a entidade espiritual.
import { world, system } from '@minecraft/server';
import { CONFIG } from '../config.js';
import { SKILLS, RACES, TREES } from '../data/registry.js';
import {
  STAGES, addXp, takeDaily, newlyUnlocked, evolutionRequirements, evolve, hasCapability, bestHealSkill, threatLevel,
} from '../core/sage.js';
import { effectName } from '../core/describe.js';
import { getData, saveNow, refreshStats } from './storage.js';
import { lookTarget, isBoss, isUndead, msg, particle, sound, V, QUERY_EXCLUDE } from './util.js';
import { mobEp } from './leveling.js';
import { spawnSage, sageTalk, vanishSage, sageAura, setSageStage } from './sageEntity.js';
import { cast } from './casting.js';
import { checkMissions } from './progressHooks.js';

const R = () => CONFIG.sage.rewards;
const PREFIX = (s) => `${STAGES[s.stage].color}§l[${STAGES[s.stage].name}]§r `;

function day() {
  try { return world.getDay(); } catch { return 0; }
}

function sageOf(player) {
  return getData(player).sage;
}

function changed(player) {
  const d = getData(player);
  d.sageDirty = true;
  saveNow(player);
}

/**
 * Notificação do Grande Sábio (chat + som). `important` passa mesmo na frequência reduzida.
 * Desligada nas configurações → nada é mostrado.
 */
export function notify(player, text, { important = false, snd = 'random.orb', pitch = 1.6 } = {}) {
  const s = sageOf(player);
  const f = s.settings.frequency;
  if (!s.settings.notify || f === 'off' || (f === 'important' && !important)) return;
  msg(player, `${PREFIX(s)}${text}`);
  if (s.settings.sound) sound(player.dimension, snd, player.location, { pitch, volume: 0.8 });
}

export function sageSound(player, id, pitch = 1.4) {
  if (sageOf(player).settings.sound) sound(player.dimension, id, player.location, { pitch, volume: 0.8 });
}

/** Verifica e anuncia uma só vez: perto de evoluir, requisito cumprido, evolução disponível. */
function checkEvolution(player) {
  const d = getData(player);
  const s = d.sage;
  if (!d.race) return;
  const ev = evolutionRequirements(s, d);
  if (!ev) return;
  const once = (key, text, opts) => {
    if (s.notified[key]) return;
    s.notified[key] = true;
    d.sageDirty = true;
    notify(player, text, opts);
  };
  ev.reqs.forEach((r, i) => { if (r.ok) once(`req_${ev.next}_${i}`, `§aRequisito cumprido:§r ${r.label}.`); });
  if (ev.ready) once(`ready_${ev.next}`, `§6§lUma nova evolução está disponível: ${STAGES[ev.next].name}.§r Abra o painel Nível e evolução.`, { important: true, snd: 'beacon.activate', pitch: 1.2 });
  else if (ev.progress >= 0.75) once(`near_${ev.next}`, 'Os requisitos para uma evolução foram identificados. Estou próximo de evoluir.', { important: true });
}

/**
 * Concede XP ao Grande Sábio. `kind` é uma chave de CONFIG.sage.rewards; `daily` aplica o limite diário.
 * Retorna o XP efetivamente concedido.
 */
export function award(player, kind, { daily = null, amount = null } = {}) {
  const d = getData(player);
  const s = d.sage;
  if (!d.race) return 0;
  if (daily && !takeDaily(s, daily, day())) { d.sageDirty = true; return 0; }
  const before = { stage: s.stage, level: s.level };
  const capped = s.level >= CONFIG.sage.levelCaps[s.stage];
  const amt = amount ?? R()[kind] ?? 0;
  const ups = addXp(s, amt);
  d.sageDirty = true;
  if (ups) {
    notify(player, `Meu nível de processamento foi aprimorado: §enível ${s.level}§r.`, { snd: 'random.levelup', pitch: 1.8 });
    for (const c of newlyUnlocked(before, s)) {
      notify(player, `§bUma nova capacidade foi desbloqueada: §l${c.name}§r. ${c.desc}`, { important: true, snd: 'random.levelup', pitch: 1.2 });
    }
    if (s.level >= CONFIG.sage.levelCaps[s.stage]) notify(player, `Alcancei o limite do estágio ${STAGES[s.stage].name}. Só uma evolução permitirá continuar.`, { important: true });
    refreshStats(d); // capacidades com bônus de atributos
    checkMissions(player);
  }
  checkEvolution(player);
  if (ups) saveNow(player);
  return capped ? 0 : amt;
}

// ── Análises ──
function familiesOf(e) {
  try { return e.getComponent('minecraft:type_family')?.getTypeFamilies() ?? []; } catch { return []; }
}

/** Dados reais de uma entidade (o que o jogo informa). */
export function readEntity(e) {
  const h = e.getComponent('minecraft:health');
  const fam = familiesOf(e);
  const a = {
    typeId: e.typeId, name: e.nameTag || e.typeId.replace(/^.*:/, '').replace(/_/g, ' '),
    hp: Math.ceil(h?.currentValue ?? 0), maxHp: Math.ceil(h?.effectiveMax ?? 0),
    hostile: fam.includes('monster') || isBoss(e), boss: isBoss(e), undead: isUndead(e),
    effects: (() => { try { return e.getEffects().map((x) => `${effectName(x.typeId)} ${x.amplifier + 1}`); } catch { return []; } })(),
  };
  if (e.typeId === 'minecraft:player') {
    const td = getData(/** @type {any} */ (e));
    if (td.race) { a.race = RACES[td.race].name; a.level = td.level; }
    a.skills = [...td.unlocked].filter((id) => TREES[SKILLS[id]?.tree]?.kind === 'universal');
  } else {
    try { a.ep = mobEp(e); } catch { /* */ }
  }
  return a;
}

/** Criatura na mira (ou null). */
export function targetOf(player, range = 24) {
  let t = lookTarget(player, range);
  if (!t) { try { t = player.getEntitiesFromViewDirection({ maxDistance: range, ...QUERY_EXCLUDE })[0]?.entity; } catch { t = null; } }
  return t?.isValid && t.id !== player.id ? t : null;
}

/**
 * Registra uma análise (pelo painel ou pelas habilidades Análise/Analista). Retorna o registro.
 * XP: primeira análise de uma espécie vale muito; repetir vale pouco e tem limite diário.
 */
export function recordAnalysis(player, e) {
  const d = getData(player);
  const s = d.sage;
  if (!hasCapability(s, 'analysis')) return null;
  const a = readEntity(e);
  const key = e.typeId === 'minecraft:player' ? 'minecraft:player' : e.typeId;
  const old = s.analyses[key];
  const now = system.currentTick;
  const rec = { ...a, count: (old?.count ?? 0) + 1, first: old?.first ?? day(), last: now, kills: d.kills[key] ?? 0 };
  // Analisar um jogador registra as habilidades universais dele na sua Biblioteca.
  if (a.skills) { for (const id of a.skills) d.library.add(id); delete (/** @type {any} */ (rec)).skills; }
  // Guarda só o que cabe: efeitos não são persistidos (mudam a cada momento).
  s.analyses[key] = { ...rec, effects: undefined };
  const keys = Object.keys(s.analyses);
  if (keys.length > CONFIG.sage.analysisLimit) {
    const oldest = keys.sort((x, y) => (s.analyses[x].last ?? 0) - (s.analyses[y].last ?? 0))[0];
    delete s.analyses[oldest];
  }
  d.sageDirty = true;
  if (!old) {
    notify(player, `Nova espécie registrada na memória: §b${a.name}§r.`, { snd: 'random.orb' });
    award(player, 'newSpecies', { amount: R().newSpecies + (a.boss ? R().bossFirstKill / 3 : 0) });
  } else award(player, 'repeatAnalysis', { daily: 'repeatAnalysis' });
  if (s.settings.sound) sound(player.dimension, 'beacon.power', player.location, { pitch: 1.8, volume: 0.5 });
  return rec;
}

function itemCategory(id) {
  if (/_(sword|axe|mace)$|^minecraft:(trident|bow|crossbow|mace)$/.test(id)) return 'Arma';
  if (/_(helmet|chestplate|leggings|boots)$|^minecraft:(shield|elytra|turtle_helmet)$/.test(id)) return 'Armadura';
  if (/_(pickaxe|shovel|hoe)$|^minecraft:(shears|fishing_rod|flint_and_steel|brush)$/.test(id)) return 'Ferramenta';
  if (id.startsWith('tensura:')) return 'Item do Tensura RPG';
  return 'Item / bloco';
}

/** Analisa o item na mão principal. */
export function analyzeHeld(player) {
  const d = getData(player);
  const s = d.sage;
  if (!hasCapability(s, 'item_analysis')) return { error: 'Capacidade "Análise de itens" bloqueada (Grande Sábio, nível 3).' };
  let it;
  try { it = player.getComponent('minecraft:inventory')?.container?.getItem(player.selectedSlotIndex); } catch { it = undefined; }
  if (!it) return { error: 'Nenhum item na mão. Segure o item e toque em analisar.' };
  const rec = { typeId: it.typeId, name: it.nameTag || it.typeId.replace(/^.*:/, '').replace(/_/g, ' '), amount: it.amount, maxAmount: it.maxAmount, category: itemCategory(it.typeId), damage: 0, maxDurability: 0, enchants: [] };
  try { const dur = it.getComponent('minecraft:durability'); if (dur) { rec.damage = dur.damage; rec.maxDurability = dur.maxDurability; } } catch { /* */ }
  try { rec.enchants = it.getComponent('minecraft:enchantable')?.getEnchantments().map((e) => `${e.type.id.replace('minecraft:', '')} ${e.level}`) ?? []; } catch { /* */ }
  const first = !s.items[it.typeId];
  s.items[it.typeId] = { name: rec.name, category: rec.category, count: (s.items[it.typeId]?.count ?? 0) + 1, first: s.items[it.typeId]?.first ?? day() };
  d.sageDirty = true;
  if (first) award(player, 'itemAnalysis'); else award(player, 'repeatAnalysis', { daily: 'repeatAnalysis' });
  sageSound(player, 'beacon.power', 1.8);
  return { rec };
}

// ── Ganchos chamados pelos outros sistemas ──
export function onKill(player, dead) {
  const s = sageOf(player);
  if (s.kills[dead.typeId]) return;
  s.kills[dead.typeId] = true;
  getData(player).sageDirty = true;
  if (isBoss(dead)) {
    notify(player, `Vitória registrada contra §c${dead.typeId.replace('minecraft:', '')}§r. Dados de combate valiosos foram obtidos.`, { important: true });
    award(player, 'bossFirstKill');
  } else award(player, 'newKillSpecies');
}

export function onSkillUnlocked(player) { award(player, 'skillUnlocked'); }
export function onPlayerLevel(player, ups) { award(player, 'playerLevelUp', { amount: R().playerLevelUp * ups }); }

// ── Evolução ──
export async function evolveSage(player) {
  const d = getData(player);
  const s = d.sage;
  const next = evolve(s, d, day());
  if (!next) { notify(player, '§cOs requisitos de evolução ainda não foram cumpridos.', { important: true }); return false; }
  s.notified = Object.fromEntries(Object.entries(s.notified).filter(([k]) => !k.endsWith(`_${next}`)));
  d.sageDirty = true;
  refreshStats(d);
  saveNow(player);
  try { player.onScreenDisplay.setTitle(`${STAGES[next].color}§l${STAGES[next].name}`, { subtitle: '§fO Grande Sábio evoluiu', fadeInDuration: 10, stayDuration: 60, fadeOutDuration: 20 }); } catch { /* */ }
  for (let i = 0; i < 40; i++) particle(player.dimension, 'minecraft:totem_particle', V.add(player.location, { x: (Math.random() - 0.5) * 3, y: Math.random() * 3, z: (Math.random() - 0.5) * 3 }));
  sageSound(player, 'random.totem', 1);
  await manifest(player, next === 'raphael'
    ? 'Confirmado. Evolução concluída: agora sou Raphael, Senhor da Sabedoria.'
    : 'Evolução concluída. Pode me chamar de Ciel.');
  return true;
}

// ── Manifestação no mundo (mesmo estado: estágio → aparência) ──
const manifesting = new Set();
export async function manifest(player, line) {
  if (manifesting.has(player.id)) return;
  manifesting.add(player.id);
  try {
    const s = sageOf(player);
    const dir = V.flat(player.getViewDirection());
    const at = V.add(player.location, { x: dir.x * 2.2, y: 0.3, z: dir.z * 2.2 });
    const e = spawnSage(player, at, `${STAGES[s.stage].color}${STAGES[s.stage].name}`);
    setSageStage(e, s.stage);
    const aura = sageAura(e);
    await system.waitTicks(20);
    sageTalk(e, true);
    msg(player, `${PREFIX(s)}${line}`);
    sageSound(player, 'mob.allay.item_given', 1.2);
    await system.waitTicks(80);
    sageTalk(e, false);
    await system.waitTicks(20);
    system.clearRun(aura);
    await vanishSage(e);
  } catch (err) {
    console.warn(`[Tensura] Manifestação do Grande Sábio falhou: ${err}`);
  } finally {
    manifesting.delete(player.id);
  }
}

// ── Capacidades com efeito contínuo ──
const lastHud = new Map();
/** Assistência de combate: vida do alvo após um golpe do jogador. */
export function onPlayerHit(player, victim) {
  const s = sageOf(player);
  if (!hasCapability(s, 'combat_hud') || !victim?.isValid) return;
  const now = system.currentTick;
  if (now - (lastHud.get(player.id) ?? 0) < 8) return;
  lastHud.set(player.id, now);
  system.run(() => {
    if (!victim.isValid || !player.isValid) return;
    const h = victim.getComponent('minecraft:health');
    if (!h) return;
    let line = `${STAGES[s.stage].color}[${STAGES[s.stage].name}] §f${victim.nameTag || victim.typeId.replace(/^.*:/, '')} §c♥ ${Math.max(0, Math.ceil(h.currentValue))}/${Math.ceil(h.effectiveMax)}`;
    if (hasCapability(s, 'threat')) line += `  §7Ameaça ${threatLevel({ maxHp: h.effectiveMax, hostile: familiesOf(victim).includes('monster'), boss: isBoss(victim) }).label}`;
    if (hasCapability(s, 'combat_hud2') && isUndead(victim)) line += '  §eFraco ao Sagrado';
    try { player.onScreenDisplay.setActionBar(line); } catch { /* */ }
  });
}

const warned = new Map(); // player.id → tick do último aviso de vida baixa
const autoHealAt = new Map();
const bossSeen = new Map(); // player.id → Set de ids de chefes já avisados
const BOSS_TYPES = ['minecraft:wither', 'minecraft:ender_dragon', 'minecraft:warden', 'minecraft:elder_guardian'];

export function startSage() {
  // Vida baixa (Previsão / Gerenciamento autônomo) — a cada 1 s.
  system.runInterval(() => {
    const now = system.currentTick;
    for (const p of world.getAllPlayers()) {
      const d = getData(p);
      if (!d.race || !hasCapability(d.sage, 'prediction')) continue;
      const h = p.getComponent('minecraft:health');
      if (!h || h.currentValue <= 0) continue;
      const ratio = h.currentValue / h.effectiveMax;
      const healId = bestHealSkill(d.unlocked, SKILLS);
      if (ratio < 0.25 && healId && hasCapability(d.sage, 'auto_heal') && now >= (autoHealAt.get(p.id) ?? 0)) {
        if (cast(p, healId)) {
          autoHealAt.set(p.id, now + 1200);
          notify(p, `Vida crítica. Usei §b${SKILLS[healId].name}§r automaticamente.`, { important: true, snd: 'random.orb', pitch: 1 });
          continue;
        }
      }
      if (ratio < 0.3 && now - (warned.get(p.id) ?? -9999) > 600) {
        warned.set(p.id, now);
        notify(p, `§cPrevisão: sua vida está em ${Math.round(ratio * 100)}%.§r ${healId ? `Recomendo usar ${SKILLS[healId].name}.` : 'Você não possui habilidade de cura: recue.'}`, { important: true, snd: 'note.bass', pitch: 1 });
      }
    }
  }, 20);
  // Avaliação de ameaça: avisa uma vez quando um chefe aparece por perto — a cada 5 s.
  system.runInterval(() => {
    for (const p of world.getAllPlayers()) {
      const d = getData(p);
      if (!d.race || !hasCapability(d.sage, 'threat')) continue;
      let seen = bossSeen.get(p.id);
      if (!seen) { seen = new Set(); bossSeen.set(p.id, seen); }
      for (const type of BOSS_TYPES) {
        let list = [];
        try { list = p.dimension.getEntities({ type, location: p.location, maxDistance: 64 }); } catch { /* */ }
        for (const b of list) {
          if (seen.has(b.id)) continue;
          seen.add(b.id);
          notify(p, `§4Alerta: ameaça de nível Calamidade detectada§r (${b.typeId.replace('minecraft:', '')}, ${Math.round(V.dist(b.location, p.location))} blocos).`, { important: true, snd: 'note.bass', pitch: 0.6 });
        }
      }
    }
  }, 100);
}

export { changed, sageOf };
