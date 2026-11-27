// Monta o "modelo" do formulário da árvore (título, corpo e botões codificados)
// a partir do estado do jogador e da câmera da árvore. Puro: testável em Node.
import { SKILLS, TREES, RACES, TAB_ORDER, treeForTab } from '../data/registry.js';
import { evaluate, fxList } from './progression.js';
import { getLayout, DIRS } from './layout.js';
import { describeFx } from './describe.js';
import { CONFIG, rarityInfo } from '../config.js';
import { ORIGINS } from '../data/dsl.js';
import {
  CTRL, CTRL_COUNT, TAB_COUNT, zoomMark, gridOf, layerBase, hasLabels, frameCode, iconName, TEX, EDGE, SCREEN, ZOOMS,
} from './uiSpec.js';

const TYPE_NAMES = { active: 'Ativa', passive: 'Passiva', evolution: 'Evolução racial', race_root: 'Raça' };

/** Câmera padrão: raiz no centro da área visível. */
export function centeredView(zoom) {
  const { cols, rows } = gridOf(zoom);
  return { x: -Math.floor(cols / 2), y: -Math.floor(rows / 2), zoom };
}

export function clampZoom(z) {
  return Math.min(Math.max(z, ZOOMS[0]), ZOOMS[ZOOMS.length - 1]);
}

/** Troca o zoom mantendo a célula central no centro. */
export function rezoom(view, newZoom) {
  const z = clampZoom(newZoom);
  const a = gridOf(view.zoom);
  const b = gridOf(z);
  const cx = view.x + Math.floor(a.cols / 2);
  const cy = view.y + Math.floor(a.rows / 2);
  return { x: cx - Math.floor(b.cols / 2), y: cy - Math.floor(b.rows / 2), zoom: z };
}

/** Move a câmera em passos proporcionais ao tamanho da grade, sem sair muito da árvore. */
export function pan(view, dx, dy, layout) {
  const { cols, rows } = gridOf(view.zoom);
  const sx = Math.max(1, Math.floor(cols / 3));
  const sy = Math.max(1, Math.floor(rows / 2));
  const b = layout.bounds;
  const x = clamp(view.x + dx * sx, b.minX - cols + 2, b.maxX - 1);
  const y = clamp(view.y + dy * sy, b.minY - rows + 2, b.maxY - 1);
  return { ...view, x, y };
}

function clamp(v, a, b) { return Math.min(Math.max(v, a), b); }

/** Primeira coluna (parcialmente) coberta pelo painel de detalhes. */
export function detailsCoveredFrom(cols) {
  return Math.floor(cols * (1 - SCREEN.detailsWidth / 100));
}

/** Garante que o nó selecionado fique visível à esquerda do painel de detalhes. */
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
  const fromOn = state.unlocked.has(edge.from);
  const toOn = state.unlocked.has(edge.to);
  if (fromOn && toOn) return EDGE.lit;
  if (fromOn) return EDGE.open;
  return EDGE.dim;
}

function shortLabel(name, max = 14) {
  return name.length <= max ? name : `${name.slice(0, max - 1)}…`;
}

/**
 * @param {object} p
 * @param {object} p.state      estado de progressão (race, level, points, unlocked, flags)
 * @param {string} p.tab        aba atual ('racial' ou id da árvore universal)
 * @param {object} p.view       { x, y, zoom }
 * @param {string|null} p.selected id da habilidade selecionada
 * @param {{cur:number,max:number}} p.mp
 * @param {string[]} p.equipped ids equipados no Cristal de Magicules
 */
export function buildTreeForm(p) {
  const { state, tab, view, selected, mp, equipped = [] } = p;
  const lineage = RACES[state.race].lineage;
  const tree = treeForTab(tab, lineage);
  const layout = getLayout(tree);
  const { cols, rows, n } = gridOf(view.zoom);
  const base = layerBase(view.zoom);
  const buttons = new Array(base.total);
  const hit = {};

  // ── Cabeçalho ──
  const homeSkills = tree.skills.filter((s) => s.type !== 'race_root');
  const unlockedHere = homeSkills.filter((s) => state.unlocked.has(s.id)).length;
  const race = RACES[state.race];
  const title = `${zoomMark(view.zoom)}§l§6${tree.name}§r\n§ePontos: ${state.points}§r  §fNv ${state.level}§r  §bRaça: ${race.name}§r  §aDesbloq.: ${unlockedHere}/${homeSkills.length}§r  §dMagicules: ${Math.floor(mp.cur)}/${mp.max}`;

  // ── Controles fixos ──
  const set = (i, text, icon = '') => { buttons[i] = { text, icon }; };
  for (let i = 0; i < CTRL_COUNT; i++) set(i, '');
  set(CTRL.close, '', `${TEX.ui}btn_close`); hit[CTRL.close] = { type: 'close' };
  set(CTRL.grimoire, 'Grimório', `${TEX.ui}btn_grimoire`); hit[CTRL.grimoire] = { type: 'grimoire' };
  set(CTRL.status, 'Status', `${TEX.ui}btn_status`); hit[CTRL.status] = { type: 'status' };
  TAB_ORDER.slice(0, TAB_COUNT).forEach((t, i) => {
    const tr = treeForTab(t, lineage);
    const active = t === tab;
    set(CTRL.tab0 + i, active ? `§e${tr.short}` : `§7${tr.short}`, `${TEX.ui}tab_${t === 'racial' ? 'racial' : t}${active ? '_on' : ''}`);
    hit[CTRL.tab0 + i] = { type: 'tab', tab: t };
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
  if (selected && SKILLS[selected]) {
    const s = SKILLS[selected];
    const ev = evaluate(selected, state);
    body = detailsText(s, ev, state, tree, equipped);
    set(CTRL.closeDetails, '', `${TEX.ui}btn_close`); hit[CTRL.closeDetails] = { type: 'deselect' };
    set(CTRL.detailIcon, '', `${TEX.icon}${iconName(s.id)}`);
    if (ev.ok) {
      set(CTRL.unlock, s.type === 'evolution' ? `§l§6Evoluir (${ev.cost} pts)` : `§l§aDesbloquear (${ev.cost} pts)`, `${TEX.ui}btn_unlock`);
      hit[CTRL.unlock] = { type: 'unlock', id: s.id };
    }
    if (ev.state === 'unlocked' && s.type === 'active') {
      const slot = equipped.indexOf(s.id);
      set(CTRL.equip, slot >= 0 ? `§bEquipada (slot ${slot + 1}) · remover` : '§bEquipar no Cristal', `${TEX.ui}btn_equip`);
      hit[CTRL.equip] = { type: 'equip', id: s.id };
    }
    if (s.tree !== tree.id) {
      set(CTRL.gotoLink, `Ir para ${TREES[s.tree].short}`, `${TEX.ui}btn_link`);
      hit[CTRL.gotoLink] = { type: 'goto', tree: s.tree, id: s.id };
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
  const edgeStates = layout.edges.map((e) => edgeState(e, state));
  let visibleNodes = 0;
  // Colunas cobertas pelo painel de detalhes ficam vazias (evita toques em nós escondidos).
  const coveredFrom = selected ? detailsCoveredFrom(cols) : cols;
  for (let r = 0; r < rows; r++) {
    for (let c = 0; c < cols; c++) {
      const i = r * cols + c;
      if (c >= coveredFrom) {
        buttons[base.pipe + i] = { text: '', icon: '' };
        buttons[base.node + i] = { text: '', icon: '' };
        if (showLabels) buttons[base.label + i] = { text: '', icon: '' };
        continue;
      }
      const wx = view.x + c;
      const wy = view.y + r;
      const k = `${wx},${wy}`;
      const nodeId = layout.nodeAt.get(k);
      // Camada de linhas.
      const st = layout.stubs.get(k);
      let code = '';
      if (st) {
        const d = DIRS.map((dir) => st[dir].reduce((m, ei) => Math.max(m, edgeStates[ei]), 0));
        if (d.some((v) => v > 0)) code = d.join('');
      }
      buttons[base.pipe + i] = { text: code, icon: '' };
      // Camada de nós.
      if (nodeId) {
        visibleNodes++;
        const s = SKILLS[nodeId];
        const ev = evalOf(nodeId);
        const fc = frameCode(ev.state, s.rarity, s.major, layout.isLink.has(nodeId), nodeId === selected);
        buttons[base.node + i] = { text: fc, icon: `${TEX.icon}${iconName(nodeId)}` };
        hit[base.node + i] = { type: 'node', id: nodeId };
        if (showLabels) {
          const color = ev.state === 'unlocked' ? '§f' : ev.state === 'available' ? '§e' : '§8';
          buttons[base.label + i] = { text: `${color}${shortLabel(s.name)}`, icon: '' };
        }
      } else {
        buttons[base.node + i] = { text: '', icon: '' };
        if (showLabels) buttons[base.label + i] = { text: '', icon: '' };
      }
    }
  }
  return { title, body, buttons, hit, tree, layout, visibleNodes, cells: n };
}

function detailsText(s, ev, state, viewTree, equipped) {
  const ri = rarityInfo(s.rarity);
  const lines = [];
  lines.push(`§l${ri.color}${s.name}§r`);
  lines.push(`§7${s.original} · ${s.category}`);
  const status = { unlocked: '§aDesbloqueada', available: '§eDisponível', reachable: '§6Falta requisito', locked: '§8Bloqueada' }[ev.state];
  lines.push(`${status}§r · ${TYPE_NAMES[s.type]} · Raridade: ${ri.color}${ri.name}§r`);
  const costs = [`Custo: §e${ev.cost} pts§r`];
  if (s.mp) costs.push(`Magicules: §d${s.mp}§r`);
  if (s.cooldown) costs.push(`Recarga: ${s.cooldown}s`);
  costs.push(`Nível mín.: ${s.minLevel}`);
  lines.push(costs.join(' · '));
  if (s.requires.length) {
    lines.push(`Pré-requisitos: ${s.requires.map((r) => `${state.unlocked.has(r) ? '§a✔' : '§c✘'} ${SKILLS[r]?.name ?? r}§r`).join(', ')}`);
  } else {
    lines.push('Pré-requisitos: nenhum (raiz da árvore)');
  }
  if (s.children.length) {
    lines.push(`Próxima evolução: ${s.children.map((c) => SKILLS[c].name).join(', ')}`);
  }
  if (s.tree !== viewTree.id) lines.push(`§9Nó compartilhado: pertence à ${TREES[s.tree].name}.§r`);
  lines.push('');
  if (s.desc) lines.push(s.desc);
  if (s.fx && s.type !== 'race_root') lines.push(`§7Efeito: ${describeFx(s.fx)}§r`);
  if (s.type === 'race_root') lines.push(raceSummary(state.race));
  if (equipped.includes(s.id)) lines.push(`§bEquipada no Cristal de Magicules (slot ${equipped.indexOf(s.id) + 1}).§r`);
  lines.push(`§8Origem: ${ORIGINS[s.origin] ?? s.origin}§r`);
  if (ev.missing.length) {
    lines.push('');
    lines.push('§c§lO que falta:§r');
    for (const m of ev.missing) lines.push(`§c• ${m}§r`);
  }
  return lines.join('\n');
}

export function raceSummary(raceId) {
  const r = RACES[raceId];
  const f = (o) => Object.entries(o).map(([k, v]) => `${k} ${Math.round(v * 100)}%`).join(', ') || 'nenhuma';
  return [
    `§bRaça atual: ${r.name} (${r.original})§r`,
    `Magicules base ${r.mp} · regeneração ${r.regen}/s`,
    `Resistências: ${f(r.resist)}`,
    `Fraquezas: ${f(r.weak)}`,
    `Afinidades (custo): ${Object.entries(r.aff).map(([k, v]) => `${k === '*' ? 'todas' : TREES[k]?.short ?? k} x${v}`).join(', ') || 'nenhuma'}`,
  ].join('\n');
}

export { fxList };
