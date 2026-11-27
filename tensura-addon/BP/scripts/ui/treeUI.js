// Telas principais: menu com abas (Status, Raça, Habilidades, Evolução, Registros)
// e a árvore de habilidades em mapa único.
//
// Cada toque devolve o índice do botão; o script atualiza o estado e reabre o
// formulário. O desenho é feito pelo resource pack (RP/ui/tensura_tree.json).
import { system, world } from '@minecraft/server';
import { ActionFormData, FormCancelationReason } from '@minecraft/server-ui';
import { CONFIG } from '../config.js';
import { barSlots } from '../core/stats.js';
import { SKILLS, RACES, TREES, SECTORS, initialSkillsFor } from '../data/registry.js';
import { unlock } from '../core/progression.js';
import { buildTreeForm, viewAt, centeredView, pan, ensureVisible, toOverview, fromOverview } from '../core/view.js';
import { evaluate } from '../core/progression.js';
import { buildMenuForm } from '../core/menuView.js';
import { getMap } from '../core/map.js';
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

export function formOf(model) {
  const form = new ActionFormData().title(model.title).body(model.body);
  for (const b of model.buttons) form.button(b.text, b.icon || undefined);
  return form;
}

/** Aplica uma evolução racial: nova raça, habilidades iniciais dela, atributos. */
export function applyEvolution(player, d, raceId) {
  const r = RACES[raceId];
  for (const id of initialSkillsFor(raceId)) d.unlocked.add(id);
  refreshStats(d);
  d.mp = d.stats.maxMp;
  d.aura = d.stats.maxAura;
  for (let i = 0; i < 30; i++) {
    particle(player.dimension, 'minecraft:totem_particle', V.add(player.location, { x: (Math.random() - 0.5) * 2, y: Math.random() * 2.5, z: (Math.random() - 0.5) * 2 }));
  }
  sound(player.dimension, 'random.totem', player.location);
  world.sendMessage(`§6§l${player.name} evoluiu para ${r.name}!`);
}

export function doUnlock(player, d, id) {
  const st = progressionState(d);
  const r = unlock(id, st);
  if (!r.ok) { msg(player, `§cNão foi possível desbloquear: ${r.missing.join('; ')}`); return false; }
  d.points = st.points;
  const s = SKILLS[id];
  if (r.evolvedTo) {
    d.race = st.race;
    applyEvolution(player, d, r.evolvedTo);
  } else {
    refreshStats(d);
    sound(player.dimension, 'random.levelup', player.location, { pitch: 1.4, volume: 0.7 });
    msg(player, `§aDesbloqueado: §l${s.name}§r §7(-${r.cost} pts, restam ${d.points})`);
    if (s.type === 'active' && d.equipped.length < barSlots(d) && !d.equipped.includes(id)) {
      d.equipped.push(id);
      msg(player, `§7${s.name} foi colocada na Barra de Habilidades (espaço ${d.equipped.length}).`);
    }
  }
  menusRef?.onSkillUnlocked?.(player);
  saveNow(player);
  return true;
}

// Ganchos externos (definidos em main.js) — evita importação circular com o Grande Sábio.
let menusRef = null;
export function setTreeHooks(h) { menusRef = h; }

function toggleEquip(player, d, id) {
  const i = d.equipped.indexOf(id);
  if (i >= 0) {
    d.equipped.splice(i, 1);
    if (d.slot >= d.equipped.length) d.slot = 0;
    msg(player, `§7${SKILLS[id].name} removida da barra.`);
  } else {
    if (d.equipped.length >= barSlots(d)) { msg(player, `§cA barra tem ${barSlots(d)} espaços. Remova uma habilidade antes.`); return; }
    d.equipped.push(id);
    msg(player, `§b${SKILLS[id].name} colocada no espaço ${d.equipped.length}.`);
  }
  saveNow(player);
}

// ── Menu principal ──
export async function openMenu(player, menus, startTab = 'status') {
  if (openFor.has(player.id)) return;
  let d = getData(player);
  if (!d.race) { await menus.chooseRace(player); d = getData(player); if (!d.race) return; }
  openFor.add(player.id);
  try {
    let tab = startTab;
    for (;;) {
      const model = buildMenuForm({ tab, d, state: progressionState(d) });
      const res = await showForm(player, formOf(model));
      if (!player.isValid) return;
      if (!res || res.canceled || res.selection === undefined) break;
      const act = model.hit[res.selection];
      if (!act) continue;
      if (act.type === 'close') break;
      if (act.type === 'tab') {
        if (act.tab === 'skills') { if ((await treeLoop(player, menus)) === 'close') break; continue; }
        tab = act.tab;
        continue;
      }
      if (act.type === 'skill') { if ((await treeLoop(player, menus, { key: act.id })) === 'close') break; continue; }
      if (act.type === 'tree') { if ((await treeLoop(player, menus, { pos: act.pos })) === 'close') break; continue; }
      if (act.type === 'slot' || act.type === 'grimoire') {
        const r = await menus.grimoire(player);
        if (r?.goto && (await treeLoop(player, menus, { key: r.goto })) === 'close') break;
        if (r?.close) break;
        continue;
      }
      if (act.type === 'unlock') { doUnlock(player, d, act.id); d = getData(player); continue; }
      if (act.type === 'sage') { if ((await menus.sage(player, 'home', true)) === 'close') break; d = getData(player); continue; }
      if (act.type === 'mimic') { await menus.mimic(player); continue; }
      if (act.type === 'storage') { await menus.storage(player); continue; }
    }
    markDirty(player);
  } finally {
    openFor.delete(player.id);
  }
}

// ── Árvore (mapa único) ──
// Retorna 'back' (voltar ao menu) ou 'close' (fechar tudo).
// Tocar num nó e rolar para cima/baixo acontecem só no celular (sem reabrir a tela).
// O script só recebe: ações do painel (desbloquear, barra, ir ao original), setas e categorias.
async function treeLoop(player, menus, focus = {}) {
  const d = getData(player);
  const map = getMap(RACES[d.race].lineage);
  let view = d.ui.view && Number.isFinite(d.ui.view.x) && Number.isFinite(d.ui.view.y) ? { x: d.ui.view.x, y: d.ui.view.y } : centeredView();
  let highlight = null;
  if (focus.key && map.pos[focus.key]) { highlight = focus.key; view = viewAt(map.pos[focus.key]); }
  if (focus.pos) view = viewAt(focus.pos);
  let result = 'back';
  let zoom = 1;
  for (;;) {
    const model = buildTreeForm({ state: progressionState(d), view, focus: highlight, mp: { cur: d.mp, max: d.stats.maxMp }, equipped: d.equipped, zoom });
    const res = await showForm(player, formOf(model));
    if (!player.isValid) return 'close';
    if (!res || res.canceled || res.selection === undefined) { result = 'close'; break; }
    const act = model.hit[res.selection];
    if (!act) continue;
    if (act.type === 'back') break;
    if (act.type === 'close') { result = 'close'; break; }
    switch (act.type) {
      case 'jump': {
        highlight = null;
        let pos = act.pos;
        // Árvore racial com setores: escolher o setor (ou a visão geral).
        if (act.cat === 'racial' && SECTORS[map.lineage ?? RACES[d.race].lineage]) {
          const r = await pickSector(player, d, map);
          if (r === 'overview') { zoom = 3; view = toOverview(viewAt([0, 0])); break; }
          if (r) pos = r;
        }
        if (zoom > 1) { zoom = 1; }
        view = viewAt(pos);
        break;
      }
      case 'pan': view = pan(view, act.dx, act.dy, map, zoom); break;
      case 'center': highlight = null; view = zoom > 1 ? toOverview(viewAt([0, 0])) : viewAt([0, 0]); break;
      case 'zoom': view = act.zoom > 1 ? toOverview(view) : fromOverview(view); zoom = act.zoom; break;
      case 'zoomin': zoom = 1; view = viewAt(act.pos); break;
      case 'unlock': doUnlock(player, d, act.id); highlight = act.id; break;
      case 'equip': toggleEquip(player, d, act.id); highlight = act.id; break;
      case 'goto': highlight = act.key; view = viewAt(map.pos[act.key]); break;
      default: break;
    }
  }
  d.ui.view = zoom > 1 ? fromOverview(view) : view;
  markDirty(player);
  return result;
}

/** Lista dos setores da árvore racial (com progresso) + visão geral. Retorna posição, 'overview' ou null. */
async function pickSector(player, d, map) {
  const lin = RACES[d.race].lineage;
  const sec = SECTORS[lin];
  const tree = TREES[`race.${lin}`];
  const st = progressionState(d);
  const form = new ActionFormData().title('§lÁrvore Racial — Setores')
    .body(`§7${tree.name}. Escolha um setor para ir até ele, ou a visão geral (zoom afastado).§r`);
  const targets = [];
  for (const id of sec.order) {
    const skills = tree.skills.filter((s) => s.sector === id);
    if (!skills.length) continue;
    const own = skills.filter((s) => d.unlocked.has(s.id)).length;
    const avail = skills.filter((s) => evaluate(s.id, st).ok).length;
    const head = skills.find((s) => map.pos[s.id]);
    targets.push(map.pos[head.id]);
    form.button(`§l${sec.names[id]}§r\n§a${own}/${skills.length}${avail ? `  §9${avail} disponível(is)` : ''}`);
  }
  form.button('§l§bVisão geral (zoom afastado)');
  const res = await showForm(player, form);
  if (!res || res.canceled || res.selection === undefined) return null;
  return res.selection === targets.length ? 'overview' : targets[res.selection];
}
