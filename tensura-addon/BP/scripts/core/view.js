// Monta o formulário da árvore (mapa único) a partir do estado do jogador e da
// câmera. Puro: testável em Node.
import { SKILLS, TREES, RACES } from '../data/registry.js';
import { evaluate } from './progression.js';
import { getMap, DIRS8 } from './map.js';
import { describeFx } from './describe.js';
import { rarityInfo } from '../config.js';
import { ORIGINS } from '../data/dsl.js';
import {
  CTRL, CTRL_COUNT, CATEGORY_ORDER, zoomMark, gridOf, layerBase, hasLabels, frameCode, iconName, TEX, EDGE, SCREEN, ZOOMS,
} from './uiSpec.js';

const TYPE_NAMES = { active: 'Ativa', passive: 'Passiva', evolution: 'Evolução racial', race_root: 'Raça' };

export function clampZoom(z) {
  return Math.min(Math.max(z, ZOOMS[0]), ZOOMS[ZOOMS.length - 1]);
}

/** Câmera centrada num ponto do mapa. */
export function viewAt(pos, zoom) {
  const { cols, rows } = gridOf(zoom);
  return { x: pos[0] - Math.floor(cols / 2), y: pos[1] - Math.floor(rows / 2), zoom };
}

export function centeredView(zoom) {
  return viewAt([0, 0], zoom);
}

/** Troca o zoom mantendo a célula central no centro. */
export function rezoom(view, newZoom) {
  const z = clampZoom(newZoom);
  const a = gridOf(view.zoom);
  return viewAt([view.x + Math.floor(a.cols / 2), view.y + Math.floor(a.rows / 2)], z);
}

/** Move a câmera em passos de ~1/3 da tela, sem sair do mapa. */
export function pan(view, dx, dy, map) {
  const { cols, rows } = gridOf(view.zoom);
  const sx = Math.max(1, Math.floor(cols / 3));
  const sy = Math.max(1, Math.floor(rows / 2));
  const b = map.bounds;
  return {
    ...view,
    x: clamp(view.x + dx * sx, b.minX - Math.floor(cols / 2), b.maxX - Math.floor(cols / 2)),
    y: clamp(view.y + dy * sy, b.minY - Math.floor(rows / 2), b.maxY - Math.floor(rows / 2)),
  };
}

function clamp(v, a, b) { return Math.min(Math.max(v, a), b); }

/** Primeira coluna (parcialmente) coberta pelo painel de detalhes. */
export function detailsCoveredFrom(cols) {
  return Math.floor(cols * (1 - SCREEN.detailsWidth / 100));
}

/** Garante que o nó fique visível à esquerda do painel de detalhes. */
export function ensureVisible(view, pos) {
  const { cols, rows } = gridOf(view.zoom);
  const usable = Math.max(1, detailsCoveredFrom(cols) - 1);
  let { x, y } = view;
  const [px, py] = pos;
  if (px < x || px > x + usable) x = px - Math.floor(usable / 2);
  if (py < y || py >= y + rows) y = py - Math.floor(rows / 2);
  return { ...view, x, y };
}

function edgeState(edge, state) {
  return state.unlocked.has(edge.fromSkill) && state.unlocked.has(edge.toSkill) ? EDGE.lit : EDGE.dim;
}

function shortLabel(name, max = 13) {
  return name.length <= max ? name : `${name.slice(0, max - 1)}…`;
}

/** Nome da categoria de uma árvore (abas de salto). */
export function categoryName(cat, lineage) {
  if (cat === 'racial') return 'Racial';
  return TREES[cat].short;
}

/**
 * @param {object} p
 * @param {object} p.state      estado de progressão (race, level, points, unlocked, flags)
 * @param {object} p.view       { x, y, zoom }
 * @param {string|null} p.selected chave do nó selecionado (id ou "id@árvore" para nó compartilhado)
 * @param {{cur:number,max:number}} p.mp
 * @param {string[]} p.equipped
 */
export function buildTreeForm(p) {
  const { state, view, selected, mp, equipped = [] } = p;
  const lineage = RACES[state.race].lineage;
  const map = getMap(lineage);
  const { cols, rows, n } = gridOf(view.zoom);
  const base = layerBase(view.zoom);
  const buttons = new Array(base.total);
  const hit = {};

  const total = Object.keys(map.pos).filter((k) => !map.isLink.has(k)).length;
  const owned = Object.keys(map.pos).filter((k) => !map.isLink.has(k) && state.unlocked.has(k)).length;
  const race = RACES[state.race];
  const title = `${zoomMark(view.zoom)}§l§bÁrvore de Habilidades§r  §7${race.name}  §ePontos ${state.points}  §fNv ${state.level}  §a${owned}/${total}  §dMagículas ${Math.floor(mp.cur)}/${mp.max}`;

  const set = (i, text, icon = '') => { buttons[i] = { text, icon }; };
  for (let i = 0; i < CTRL_COUNT; i++) set(i, '');
  set(CTRL.back, 'Voltar', `${TEX.ui}btn_back`); hit[CTRL.back] = { type: 'back' };
  set(CTRL.close, '', `${TEX.ui}btn_close`); hit[CTRL.close] = { type: 'close' };
  CATEGORY_ORDER.forEach((cat, i) => {
    set(CTRL.cat0 + i, '', `${TEX.ui}tab_${cat}`);
    hit[CTRL.cat0 + i] = { type: 'jump', pos: map.roots[cat], cat };
  });
  set(CTRL.panLeft, '', `${TEX.ui}arrow_l`); hit[CTRL.panLeft] = { type: 'pan', dx: -1, dy: 0 };
  set(CTRL.panUp, '', `${TEX.ui}arrow_u`); hit[CTRL.panUp] = { type: 'pan', dx: 0, dy: -1 };
  set(CTRL.panDown, '', `${TEX.ui}arrow_d`); hit[CTRL.panDown] = { type: 'pan', dx: 0, dy: 1 };
  set(CTRL.panRight, '', `${TEX.ui}arrow_r`); hit[CTRL.panRight] = { type: 'pan', dx: 1, dy: 0 };
  const zmin = ZOOMS[0], zmax = ZOOMS[ZOOMS.length - 1];
  set(CTRL.zoomOut, '', `${TEX.ui}zoom_out${view.zoom <= zmin ? '_off' : ''}`); hit[CTRL.zoomOut] = { type: 'zoom', dz: -1 };
  set(CTRL.zoomIn, '', `${TEX.ui}zoom_in${view.zoom >= zmax ? '_off' : ''}`); hit[CTRL.zoomIn] = { type: 'zoom', dz: 1 };
  set(CTRL.center, '', `${TEX.ui}center`); hit[CTRL.center] = { type: 'center' };

  // ── Detalhes ──
  let body = '';
  const selSkill = selected ? map.skillOf[selected] : null;
  if (selSkill && SKILLS[selSkill]) {
    const s = SKILLS[selSkill];
    const ev = evaluate(selSkill, state);
    body = detailsText(s, ev, state, map.isLink.has(selected), equipped);
    set(CTRL.closeDetails, '', `${TEX.ui}btn_close`); hit[CTRL.closeDetails] = { type: 'deselect' };
    set(CTRL.detailIcon, '', `${TEX.icon}${iconName(s.id)}`);
    if (ev.ok) {
      set(CTRL.unlock, s.type === 'evolution' ? `§l§6Evoluir (${ev.cost} pts)` : `§l§aDesbloquear (${ev.cost} pts)`, `${TEX.ui}btn_unlock`);
      hit[CTRL.unlock] = { type: 'unlock', id: s.id };
    }
    if (ev.state === 'unlocked' && s.type === 'active') {
      const slot = equipped.indexOf(s.id);
      set(CTRL.equip, slot >= 0 ? `§bNa barra (espaço ${slot + 1}) · remover` : '§bColocar na barra', `${TEX.ui}btn_equip`);
      hit[CTRL.equip] = { type: 'equip', id: s.id };
    }
    if (map.isLink.has(selected)) {
      set(CTRL.gotoLink, `Ir para ${TREES[s.tree].short}`, `${TEX.ui}btn_link`);
      hit[CTRL.gotoLink] = { type: 'goto', key: s.id };
    }
  }

  // ── Grade ──
  const showLabels = hasLabels(view.zoom);
  const evalCache = new Map();
  const evalOf = (id) => {
    let e = evalCache.get(id);
    if (!e) { e = evaluate(id, state); evalCache.set(id, e); }
    return e;
  };
  const edgeStates = new Map();
  const stOf = (ei) => {
    let v = edgeStates.get(ei);
    if (v === undefined) { v = edgeState(map.edges[ei], state); edgeStates.set(ei, v); }
    return v;
  };
  let visibleNodes = 0;
  const coveredFrom = selSkill ? detailsCoveredFrom(cols) : cols;
  for (let r = 0; r < rows; r++) {
    for (let c = 0; c < cols; c++) {
      const i = r * cols + c;
      const empty = () => {
        buttons[base.pipe + i] = { text: '', icon: '' };
        buttons[base.node + i] = { text: '', icon: '' };
        if (showLabels) buttons[base.label + i] = { text: '', icon: '' };
      };
      if (c >= coveredFrom) { empty(); continue; }
      const k = `${view.x + c},${view.y + r}`;
      const nodeKey = map.nodeAt.get(k);
      const st = map.stubs.get(k);
      let code = '';
      if (st) {
        for (let d = 0; d < 8; d++) {
          code += !st[d].length ? '0' : st[d].some((ei) => stOf(ei) === EDGE.lit) ? '2' : '1';
        }
      }
      buttons[base.pipe + i] = { text: code, icon: '' };
      if (nodeKey) {
        visibleNodes++;
        const sid = map.skillOf[nodeKey];
        const s = SKILLS[sid];
        const ev = evalOf(sid);
        const major = s.major || nodeKey === map.centerId;
        buttons[base.node + i] = { text: frameCode(ev.state, s.rarity, major, map.isLink.has(nodeKey), nodeKey === selected), icon: `${TEX.icon}${iconName(sid)}` };
        hit[base.node + i] = { type: 'node', key: nodeKey };
        if (showLabels) {
          const color = ev.state === 'unlocked' ? '§f' : ev.state === 'available' ? '§e' : '§7';
          buttons[base.label + i] = { text: `${color}${shortLabel(s.name)}`, icon: '' };
        }
      } else {
        buttons[base.node + i] = { text: '', icon: '' };
        if (showLabels) buttons[base.label + i] = { text: '', icon: '' };
      }
    }
  }
  return { title, body, buttons, hit, map, visibleNodes, cells: n };
}

function detailsText(s, ev, state, isLink, equipped) {
  const ri = rarityInfo(s.rarity);
  const lines = [];
  lines.push(`§l${ri.color}${s.name}§r`);
  lines.push(`§7${s.original} · ${s.category}`);
  const status = { unlocked: '§aDesbloqueada', available: '§eDisponível', reachable: '§6Falta requisito', locked: '§8Bloqueada' }[ev.state];
  lines.push(`${status}§r · ${TYPE_NAMES[s.type]} · ${ri.color}${ri.name}§r`);
  const tree = TREES[s.tree];
  const res = tree.kind === 'racial' ? 'Aura' : 'Magículas';
  const costs = [`Custo: §e${ev.cost} pts§r`];
  if (s.mp) costs.push(`${res}: §d${s.mp}§r`);
  if (s.cooldown) costs.push(`Recarga: ${s.cooldown}s`);
  costs.push(`Nível mín.: ${s.minLevel}`);
  lines.push(costs.join(' · '));
  lines.push(s.requires.length
    ? `Pré-requisitos: ${s.requires.map((r) => `${state.unlocked.has(r) ? '§a✔' : '§c✘'} ${SKILLS[r]?.name ?? r}§r`).join(', ')}`
    : 'Pré-requisitos: nenhum (início do ramo)');
  if (s.children.length) lines.push(`Próxima evolução: ${s.children.map((c) => SKILLS[c].name).join(', ')}`);
  if (isLink) lines.push(`§9Nó compartilhado: pertence a ${tree.name}.§r`);
  lines.push('');
  if (s.desc) lines.push(s.desc);
  if (s.fx && s.type !== 'race_root') lines.push(`§7Efeito: ${describeFx(s.fx)}§r`);
  if (s.type === 'race_root') lines.push(raceSummary(state.race));
  if (equipped.includes(s.id)) lines.push(`§bNa Barra de Habilidades (espaço ${equipped.indexOf(s.id) + 1}).§r`);
  lines.push(`§8Origem: ${ORIGINS[s.origin] ?? s.origin}§r`);
  if (ev.missing.length) {
    lines.push('', '§c§lO que falta:§r');
    for (const m of ev.missing) lines.push(`§c• ${m}§r`);
  }
  return lines.join('\n');
}

export function raceSummary(raceId) {
  const r = RACES[raceId];
  const f = (o) => Object.entries(o).map(([k, v]) => `${k} ${Math.round(v * 100)}%`).join(', ') || 'nenhuma';
  return [
    `§bRaça: ${r.name} (${r.original})§r`,
    `Magículas base ${r.mp} · regeneração ${r.regen}/s`,
    `Resistências: ${f(r.resist)}`,
    `Fraquezas: ${f(r.weak)}`,
    `Afinidades (custo): ${Object.entries(r.aff).map(([k, v]) => `${k === '*' ? 'todas' : TREES[k]?.short ?? k} x${v}`).join(', ') || 'nenhuma'}`,
  ].join('\n');
}

export { DIRS8, CTRL };
