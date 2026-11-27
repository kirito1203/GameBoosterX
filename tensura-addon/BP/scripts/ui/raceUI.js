// Tela "Escolha sua Raça": cards selecionáveis, painel de informações e confirmação.
import { system } from '@minecraft/server';
import { ActionFormData } from '@minecraft/server-ui';
import { CONFIG } from '../config.js';
import { RACES, SKILLS, initialSkillsFor } from '../data/registry.js';
import { CARD_META } from '../data/raceCards.js';
import { buildRaceForm, isSelectable, baseOf, pageOf } from '../core/raceView.js';
import { getData, saveNow, refreshStats } from '../systems/storage.js';
import { showForm } from './treeUI.js';
import { msg, sound } from '../systems/util.js';

export const DEFAULT_SAGE_TEXT = 'Agora, escolha a raça que definirá sua existência neste mundo.';

function sageComment(raceId) {
  const r = RACES[raceId];
  if (!isSelectable(raceId)) {
    return `${r.name} não pode ser escolhido agora. Essa forma é alcançada evoluindo a partir de ${baseOf(raceId).name}.`;
  }
  const m = CARD_META[raceId];
  return `${r.name}... Afinidade: ${m.affinity}. Dificuldade ${m.difficulty} de 5. Toque em CONFIRMAR RAÇA quando estiver decidido.`;
}

/** Aplica a raça escolhida: dados raciais, habilidades iniciais e progressão. */
export function applyRace(player, raceId) {
  const d = getData(player);
  if (d.race) return false; // escolha permanente: só muda por evolução
  d.race = raceId;
  d.level = 1;
  d.ep = 0;
  d.points = CONFIG.leveling.startingPoints;
  for (const id of initialSkillsFor(raceId)) d.unlocked.add(id);
  refreshStats(d);
  d.mp = d.stats.maxMp;
  d.aura = d.stats.maxAura;
  d.ui.tab = 'racial';
  for (const id of d.unlocked) {
    if (SKILLS[id].type === 'active' && !d.equipped.includes(id) && d.equipped.length < CONFIG.ui.barSlots) d.equipped.push(id);
  }
  saveNow(player);
  return true;
}

/**
 * Mostra a tela até o jogador confirmar (se `mandatory`) ou fechar.
 * @returns {Promise<string|null>} raça confirmada
 */
export async function selectRace(player, { sageText = DEFAULT_SAGE_TEXT, mandatory = false } = {}) {
  let selected = 'slime';
  let page = pageOf(selected);
  let text = sageText;
  for (;;) {
    if (!player.isValid) return null;
    if (getData(player).race) return getData(player).race;
    const model = buildRaceForm({ page, selected, sageText: text });
    const form = new ActionFormData().title(model.title).body(model.body);
    for (const b of model.buttons) form.button(b.text, b.icon || undefined);
    const res = await showForm(player, form);
    if (!player.isValid) return null;
    if (!res || res.canceled || res.selection === undefined) {
      if (!mandatory) return null;
      msg(player, '§bGrande Sábio: §fA escolha da raça é necessária para continuar sua reencarnação.');
      await system.waitTicks(20);
      continue;
    }
    const act = model.hit[res.selection];
    if (!act) continue;
    if (act.type === 'select') {
      selected = act.race;
      text = sageComment(selected);
      try { player.playSound('random.click', { volume: 0.5, pitch: 1.4 }); } catch { /* */ }
    } else if (act.type === 'page') {
      page = act.page;
    } else if (act.type === 'locked') {
      text = sageComment(act.race);
    } else if (act.type === 'confirm' && isSelectable(act.race)) {
      if (applyRace(player, act.race)) {
        sound(player.dimension, 'random.totem', player.location, { volume: 0.7 });
        return act.race;
      }
      return getData(player).race;
    }
  }
}
