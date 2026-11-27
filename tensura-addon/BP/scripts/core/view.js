// Monta o formulário da árvore (mapa único) a partir do estado do jogador e da
// câmera. Puro: testável em Node.
import { SKILLS, TREES, RACES } from '../data/registry.js';
import { evaluate } from './progression.js';
import { getMap, DIRS8 } from './map.js';
import { describeFx } from './describe.js';
import { rarityInfo } from '../config.js';
import { ORIGINS } from '../data/dsl.js';
import {
  CTRL, CATEGORY_ORDER, MARK, gridOf, layerBase, frameCode, iconName, TEX, EDGE, TREE_COLS, TREE_ROWS,
} from './uiSpec.js';

const TYPE_NAMES = { active: 'Ativa', passive: 'Passiva', evolution: 'Evolução racial', race_root: 'Raça' };

/** Câmera com o ponto `pos` na coluna do meio e perto do topo da grade rolável. */
export function viewAt(pos) {
  return { x: pos[0] - Math.floor(TREE_COLS / 2), y: pos[1] - 2 };
}

export function centeredView() {
  return { x: -Math.floor(TREE_COLS / 2), y: -Math.floor(TREE_ROWS / 2) };
}

/** Move a câmera: lados em passos de ~1/3 da largura; páginas verticais quase inteiras. */
export function pan(view, dx, dy, map) {
  const sx = Math.max(1, Math.floor(TREE_COLS / 3));
  const sy = TREE_ROWS - 3;
  const b = map.bounds;
  return {
    x: clamp(view.x + dx * sx, b.minX - Math.floor(TREE_COLS / 2), b.maxX - Math.floor(TREE_COLS / 2)),
    y: clamp(view.y + dy * sy, b.minY - 2, b.maxY - 2),
  };
}

function clamp(v, a, b) { return Math.min(Math.max(v, a), b); }

/** Garante que o nó esteja dentro da grade enviada. */
export function ensureVisible(view, pos) {
  let { x, y } = view;
  if (pos[0] < x + 1 || pos[0] > x + TREE_COLS - 2) x = pos[0] - Math.floor(TREE_COLS / 2);
  if (pos[1] < y || pos[1] >= y + TREE_ROWS - 1) y = pos[1] - 2;
  return { x, y };
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
 * Formulário da árvore: botões fixos + 4 camadas por célula (nó, linhas, detalhes, ação).
 * Os detalhes de todos os nós visíveis já vão no formulário: tocar num nó só mostra o painel
 * dele (no próprio celular), sem voltar ao script.
 * @param {object} p
 * @param {object} p.state      estado de progressão (race, level, points, unlocked, flags)
 * @param {{x:number,y:number}} p.view  canto superior esquerdo da grade no mapa
 * @param {string|null} [p.focus] chave de um nó para destacar (vindo do menu)
 * @param {{cur:number,max:number}} p.mp
 * @param {string[]} [p.equipped]
 */
export function buildTreeForm(p) {
  const { state, view, focus = null, mp, equipped = [] } = p;
  const lineage = RACES[state.race].lineage;
  const map = getMap(lineage);
  const { cols, rows, n } = gridOf();
  const base = layerBase();
  const buttons = new Array(base.total);
  const hit = {};

  const total = Object.keys(map.pos).filter((k) => !map.isLink.has(k)).length;
  const owned = Object.keys(map.pos).filter((k) => !map.isLink.has(k) && state.unlocked.has(k)).length;
  const race = RACES[state.race];
  const title = `${MARK}§l§bÁrvore de Habilidades§r  §7${race.name}  §ePontos ${state.points}  §fNv ${state.level}  §a${owned}/${total}  §dMagículas ${Math.floor(mp.cur)}/${mp.max}`;

  const set = (i, text, icon = '') => { buttons[i] = { text, icon }; };
  for (let i = 0; i < base.node; i++) set(i, ''); // controles + preenchimento
  set(CTRL.back, 'Voltar', `${TEX.ui}btn_back`); hit[CTRL.back] = { type: 'back' };
  set(CTRL.close, '', `${TEX.ui}btn_close`); hit[CTRL.close] = { type: 'close' };
  CATEGORY_ORDER.forEach((cat, i) => {
    set(CTRL.cat0 + i, '', `${TEX.ui}tab_${cat}`);
    hit[CTRL.cat0 + i] = { type: 'jump', pos: map.roots[cat], cat };
  });
  set(CTRL.panLeft, '', `${TEX.ui}arrow_l`); hit[CTRL.panLeft] = { type: 'pan', dx: -1, dy: 0 };
  set(CTRL.panRight, '', `${TEX.ui}arrow_r`); hit[CTRL.panRight] = { type: 'pan', dx: 1, dy: 0 };
  set(CTRL.pageUp, '', `${TEX.ui}arrow_u`); hit[CTRL.pageUp] = { type: 'pan', dx: 0, dy: -1 };
  set(CTRL.pageDown, '', `${TEX.ui}arrow_d`); hit[CTRL.pageDown] = { type: 'pan', dx: 0, dy: 1 };
  set(CTRL.center, '', `${TEX.ui}center`); hit[CTRL.center] = { type: 'center' };

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
  for (let r = 0; r < rows; r++) {
    for (let c = 0; c < cols; c++) {
      const i = r * cols + c;
      const k = `${view.x + c},${view.y + r}`;
      const nodeKey = map.nodeAt.get(k);
      const st = map.stubs.get(k);
      let code = '';
      if (st) {
        for (let d = 0; d < 8; d++) code += !st[d].length ? '0' : st[d].some((ei) => stOf(ei) === EDGE.lit) ? '2' : '1';
      }
      buttons[base.pipe + i] = { text: code, icon: '' };
      buttons[base.node + i] = { text: '', icon: '' };
      buttons[base.detail + i] = { text: '', icon: '' };
      buttons[base.action + i] = { text: '', icon: '' };
      if (!nodeKey) continue;
      visibleNodes++;
      const sid = map.skillOf[nodeKey];
      const s = SKILLS[sid];
      const ev = evalOf(sid);
      const isLink = map.isLink.has(nodeKey);
      const major = s.major || nodeKey === map.centerId;
      buttons[base.node + i] = { text: frameCode(ev.state, s.rarity, major, isLink, nodeKey === focus), icon: `${TEX.icon}${iconName(sid)}` };
      buttons[base.detail + i] = { text: detailsText(s, ev, state, isLink, equipped), icon: '' };
      // Ação do painel: a única parte que volta ao script.
      if (isLink) {
        set(base.action + i, `§9Ir para o original (${TREES[s.tree].short})`, `${TEX.ui}btn_link`);
        hit[base.action + i] = { type: 'goto', key: sid };
      } else if (ev.ok) {
        set(base.action + i, s.type === 'evolution' ? `§l§6Evoluir (${ev.cost} pts)` : `§l§aDesbloquear (${ev.cost} pts)`, `${TEX.ui}btn_unlock`);
        hit[base.action + i] = { type: 'unlock', id: sid };
      } else if (ev.state === 'unlocked' && s.type === 'active') {
        const slot = equipped.indexOf(sid);
        set(base.action + i, slot >= 0 ? `§bNa barra (espaço ${slot + 1}) · remover` : '§bColocar na barra', `${TEX.ui}btn_equip`);
        hit[base.action + i] = { type: 'equip', id: sid };
      }
    }
  }
  return { title, body: '', buttons, hit, map, visibleNodes, cells: n };
}

function cut(text, max) {
  return text.length <= max ? text : `${text.slice(0, max - 1)}…`;
}

/** Texto compacto do painel de detalhes (cabe no painel sem rolagem). */
function detailsText(s, ev, state, isLink, equipped) {
  const ri = rarityInfo(s.rarity);
  const tree = TREES[s.tree];
  const res = tree.kind === 'racial' ? 'Aura' : 'Magículas';
  const status = { unlocked: '§aDesbloqueada', available: '§eDisponível', reachable: '§6Falta requisito', locked: '§8Bloqueada' }[ev.state];
  const lines = [
    `§l${ri.color}${s.name}§r §8${s.original}`,
    `${status}§r · ${TYPE_NAMES[s.type]} · ${ri.color}${ri.name}§r`,
    `§e${ev.cost} pts§r${s.mp ? ` · ${res} §d${s.mp}§r` : ''}${s.cooldown ? ` · ${s.cooldown}s` : ''} · Nv ${s.minLevel}`,
  ];
  if (s.requires.length) lines.push(`Requer: ${s.requires.map((r) => `${state.unlocked.has(r) ? '§a✔' : '§c✘'} ${SKILLS[r]?.name ?? r}§r`).join(', ')}`);
  if (s.children.length) lines.push(`§7Leva a: ${s.children.map((c) => SKILLS[c].name).join(', ')}§r`);
  if (isLink) lines.push(`§9Nó compartilhado de ${tree.name}.§r`);
  if (s.type === 'race_root') lines.push(cut(raceSummary(state.race).split('\n').slice(1, 4).join(' · '), 160));
  else {
    if (s.desc) lines.push(cut(s.desc, 150));
    if (s.fx) lines.push(`§7${cut(describeFx(s.fx), 130)}§r`);
  }
  if (equipped.includes(s.id)) lines.push(`§bNa barra (espaço ${equipped.indexOf(s.id) + 1})§r`);
  if (ev.missing.length && ev.state !== 'unlocked') lines.push(`§cFalta: ${ev.missing.join('; ')}§r`);
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

export { DIRS8, CTRL, ORIGINS };
