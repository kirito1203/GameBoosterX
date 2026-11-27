// Absorção com descoberta de habilidades, missões especiais e domínio (sistema).
// Tudo com dados reais e proteção contra recompensa duplicada (conjuntos salvos por jogador).
import { CONFIG } from '../config.js';
import { SKILLS, RACES, ABSORPTION, MISSIONS } from '../data/registry.js';
import { missionProgress, missionsFor } from '../core/missions.js';
import { masteryLevel, masteryMax } from '../core/mastery.js';
import { getData, saveNow, refreshStats } from './storage.js';
import { notify } from './sage.js';

/** Registra uma absorção (qualquer habilidade de absorção) e descobre habilidades compatíveis. */
export function onAbsorbed(player, typeId) {
  const d = getData(player);
  d.absorbLog[typeId] = (d.absorbLog[typeId] ?? 0) + 1;
  d.absorbTotal = (d.absorbTotal ?? 0) + 1;
  const lin = RACES[d.race]?.lineage;
  const db = ABSORPTION[lin] ?? [];
  const found = [];
  const blocked = [];
  for (const e of db) {
    if (!e.mc.includes(typeId)) continue;
    for (const id of e.skills) {
      if (d.discovered.has(id) || d.unlocked.has(id)) continue;
      const need = e.needs[id] ?? e.needs['*'] ?? [];
      if (need.every((n) => d.unlocked.has(n))) { d.discovered.add(id); found.push(id); } else blocked.push({ id, need });
    }
  }
  for (const id of found) notify(player, `Uma nova habilidade foi identificada: §b${SKILLS[id].name}§r (${SKILLS[id].original}). Ela já pode ser desbloqueada na árvore, se os requisitos forem cumpridos.`, { important: true, snd: 'random.levelup', pitch: 1.5 });
  for (const b of blocked) {
    notify(player, `Análise concluída: ${SKILLS[b.id].name} foi detectada, mas exige ${b.need.map((n) => SKILLS[n].name).join(' + ')} para ser assimilada.`);
  }
  checkMissions(player);
  saveNow(player);
  return found;
}

/** Conclui missões cujas condições foram cumpridas (uma única vez cada). */
export function checkMissions(player) {
  const d = getData(player);
  const lin = RACES[d.race]?.lineage;
  if (!lin) return [];
  const done = [];
  for (const m of missionsFor(lin)) {
    if (d.missions.has(m.id)) continue;
    if (!missionProgress(m, d).done) continue;
    d.missions.add(m.id);
    const pts = m.reward ?? CONFIG.missions.defaultRewardPoints;
    d.points += pts;
    done.push(m.id);
    notify(player, `§6§lMissão concluída: ${m.name}§r (+${pts} pontos). Os requisitos de novas habilidades foram cumpridos.`, { important: true, snd: 'random.totem', pitch: 1.3 });
  }
  if (done.length) saveNow(player);
  return done;
}

/** Conta um uso real da habilidade (conjuração bem-sucedida) e avisa ao subir de domínio. */
export function addMasteryUse(player, skillId) {
  const d = getData(player);
  const s = SKILLS[skillId];
  if (!masteryMax(s)) return;
  const before = masteryLevel(skillId, d.mastery[skillId] ?? 0);
  if (before >= masteryMax(s)) return; // sem progressão infinita
  d.mastery[skillId] = (d.mastery[skillId] ?? 0) + 1;
  const after = masteryLevel(skillId, d.mastery[skillId]);
  if (after > before) {
    const text = s.masteryDesc?.[after - 1];
    notify(player, `Domínio de §b${s.name}§r: nível ${after}/${masteryMax(s)}${text ? ` — ${text}` : ''}.`, { snd: 'random.levelup', pitch: 1.7 });
    // Evoluções que dependiam deste domínio podem ter sido liberadas.
    for (const x of Object.values(SKILLS)) {
      if (x.needMastery?.[skillId] === after && !d.unlocked.has(x.id)) notify(player, `Os requisitos de domínio para §e${x.name}§r foram cumpridos.`, { important: true });
    }
    refreshStats(d);
    saveNow(player);
  }
}

export { MISSIONS };
