// Interface principal: a árvore de habilidades em tela cheia.
//
// Cada toque (nó, aba, seta, zoom) devolve o índice do botão; o script atualiza o
// estado e reabre o formulário já com a nova câmera/seleção. O desenho em si
// (posições, linhas, molduras) é feito pelo resource pack (RP/ui/tensura_tree.json).
import { system, world } from '@minecraft/server';
import { ActionFormData, FormCancelationReason } from '@minecraft/server-ui';
import { CONFIG } from '../config.js';
import { SKILLS, TREES, RACES, initialSkillsFor } from '../data/registry.js';
import { unlock } from '../core/progression.js';
import { buildTreeForm, centeredView, pan, rezoom, ensureVisible } from '../core/view.js';
import { getLayout } from '../core/layout.js';
import { getData, progressionState, saveNow, refreshStats, markDirty } from '../systems/storage.js';
import { particle, sound, msg, V } from '../systems/util.js';

const openFor = new Set(); // evita abrir duas vezes para o mesmo jogador

export async function showForm(player, form) {
  for (let i = 0; i < 40; i++) {
    let r;
    try { r = await form.show(player); } catch (e) { console.warn(`[Tensura] Falha ao mostrar formulário: ${e}`); return null; }
    if (r.canceled && r.cancelationReason === FormCancelationReason.UserBusy) {
      await system.waitTicks(5);
      continue;
    }
    return r;
  }
  return null;
}

function viewFor(d, tab) {
  const v = d.ui.views[tab];
  if (v && CONFIG.ui.zoomGrids[v.zoom]) return v;
  return centeredView(CONFIG.ui.defaultZoom);
}

function treeIdForTab(d, tab) {
  return tab === 'racial' ? `race.${RACES[d.race].lineage}` : tab;
}

function tabForTree(treeId) {
  return TREES[treeId]?.kind === 'racial' ? 'racial' : treeId;
}

/** Aplica uma evolução racial: nova raça, habilidades iniciais dela, atributos. */
export function applyEvolution(player, d, raceId) {
  const r = RACES[raceId];
  for (const id of initialSkillsFor(raceId)) d.unlocked.add(id);
  refreshStats(d);
  d.mp = d.stats.maxMp;
  for (let i = 0; i < 30; i++) {
    particle(player.dimension, 'minecraft:totem_particle', V.add(player.location, { x: (Math.random() - 0.5) * 2, y: Math.random() * 2.5, z: (Math.random() - 0.5) * 2 }));
  }
  sound(player.dimension, 'random.totem', player.location);
  world.sendMessage(`§6§l${player.name} evoluiu para ${r.name}!`);
}

export async function openTree(player, menus) {
  if (openFor.has(player.id)) return;
  const d = getData(player);
  if (!d.race) { await menus.chooseRace(player); if (!getData(player).race) return; }
  openFor.add(player.id);
  try {
    let tab = d.ui.tab ?? 'racial';
    let selected = null;
    let view = viewFor(d, tab);
    for (;;) {
      const model = buildTreeForm({
        state: progressionState(d), tab, view, selected,
        mp: { cur: d.mp, max: d.stats.maxMp }, equipped: d.equipped,
      });
      const form = new ActionFormData().title(model.title).body(model.body);
      for (const b of model.buttons) form.button(b.text, b.icon || undefined);
      const res = await showForm(player, form);
      if (!player.isValid) return;
      if (!res || res.canceled || res.selection === undefined) break;
      const act = model.hit[res.selection];
      if (!act) continue;
      if (act.type === 'close') break;
      switch (act.type) {
        case 'grimoire': {
          const r = await menus.grimoire(player);
          if (r?.goto) { tab = tabForTree(SKILLS[r.goto].tree); selected = r.goto; view = focus(d, tab, r.goto, viewFor(d, tab)); }
          if (r?.close) { d.ui.tab = tab; d.ui.views[tab] = view; markDirty(player); return; } // conjurou: volta ao jogo
          break;
        }
        case 'status': await menus.status(player); break;
        case 'tab': d.ui.views[tab] = view; tab = act.tab; selected = null; view = viewFor(d, tab); break;
        case 'pan': view = pan(view, act.dx, act.dy, model.layout); break;
        case 'zoom': view = rezoom(view, view.zoom + act.dz); break;
        case 'center': view = centeredView(view.zoom); break;
        case 'node':
          selected = selected === act.id ? null : act.id;
          if (selected) view = ensureVisible(view, model.layout.pos[act.id]);
          break;
        case 'deselect': selected = null; break;
        case 'unlock': doUnlock(player, d, act.id); break;
        case 'equip': toggleEquip(player, d, act.id); break;
        case 'goto': d.ui.views[tab] = view; tab = tabForTree(act.tree); selected = act.id; view = focus(d, tab, act.id, viewFor(d, tab)); break;
        default: break;
      }
    }
    d.ui.tab = tab;
    d.ui.views[tab] = view;
    markDirty(player);
  } finally {
    openFor.delete(player.id);
  }
}

function focus(d, tab, skillId, view) {
  const tree = TREES[treeIdForTab(d, tab)];
  const L = getLayout(tree);
  const pos = L.pos[skillId];
  return pos ? ensureVisible(view, pos) : view;
}

function doUnlock(player, d, id) {
  const st = progressionState(d);
  const r = unlock(id, st);
  if (!r.ok) { msg(player, `§cNão foi possível desbloquear: ${r.missing.join('; ')}`); return; }
  d.points = st.points;
  const s = SKILLS[id];
  if (r.evolvedTo) {
    d.race = st.race;
    applyEvolution(player, d, r.evolvedTo);
  } else {
    refreshStats(d);
    sound(player.dimension, 'random.levelup', player.location, { pitch: 1.4, volume: 0.7 });
    msg(player, `§aDesbloqueado: §l${s.name}§r §7(-${r.cost} pts, restam ${d.points})`);
    if (s.type === 'active' && d.equipped.length < 9 && !d.equipped.includes(id)) {
      d.equipped.push(id);
      msg(player, `§7${s.name} foi equipada no Cristal de Magicules (slot ${d.equipped.length}).`);
    }
  }
  saveNow(player);
}

function toggleEquip(player, d, id) {
  const i = d.equipped.indexOf(id);
  if (i >= 0) {
    d.equipped.splice(i, 1);
    if (d.slot >= d.equipped.length) d.slot = 0;
    msg(player, `§7${SKILLS[id].name} removida do Cristal.`);
  } else {
    if (d.equipped.length >= 9) { msg(player, '§cO Cristal comporta no máximo 9 magias. Remova uma antes.'); return; }
    d.equipped.push(id);
    msg(player, `§b${SKILLS[id].name} equipada no slot ${d.equipped.length}.`);
  }
  saveNow(player);
}
