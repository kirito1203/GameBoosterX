// Monta o formulário da árvore (mapa único) a partir do estado do jogador e da
// câmera. Puro: testável em Node.
import { SKILLS, TREES, RACES, SECTORS, ABSORPTION, MISSIONS } from '../data/registry.js';
import { masteryLevel, masteryMax, usesForNext } from './mastery.js';
import { evaluate } from './progression.js';
import { getMap, getSupremeMap, getRaceMap, DIRS8 } from './map.js';
import { fxOf } from './progression.js';
import { describeFx } from './describe.js';
import { rarityInfo, CONFIG } from '../config.js';
import { ORIGINS, SOURCES, TIERS } from '../data/dsl.js';
import {
  CTRL, CATEGORY_ORDER, MARK, gridOf, layerBase, frameCode, iconName, TEX, EDGE, TREE_COLS, TREE_ROWS, ZOOM_OUT,
} from './uiSpec.js';

const TYPE_NAMES = { active: 'Ativa', passive: 'Passiva', evolution: 'Evolução racial', race_root: 'Raça' };

/** Câmera da árvore racial (vertical e estreita): coluna fixa, `pos` perto do topo. */
export function raceViewAt(pos) {
  return { x: -1, y: Math.max(-1, pos[1] - 1) };
}

/** Câmera com o ponto `pos` na coluna do meio e perto do topo da grade rolável. */
export function viewAt(pos) {
  return { x: pos[0] - Math.floor(TREE_COLS / 2), y: pos[1] - 2 };
}

export function centeredView() {
  return { x: -Math.floor(TREE_COLS / 2), y: -Math.floor(TREE_ROWS / 2) };
}

/** Visão geral centrada no mesmo ponto da visão normal (e vice-versa). */
export function toOverview(view) {
  const cx = view.x + Math.floor(TREE_COLS / 2);
  const cy = view.y + Math.floor(TREE_ROWS / 2);
  return { x: cx - Math.floor((TREE_COLS * ZOOM_OUT) / 2), y: cy - Math.floor((TREE_ROWS * ZOOM_OUT) / 2) };
}
export function fromOverview(ov) {
  const cx = ov.x + Math.floor((TREE_COLS * ZOOM_OUT) / 2);
  const cy = ov.y + Math.floor((TREE_ROWS * ZOOM_OUT) / 2);
  return { x: cx - Math.floor(TREE_COLS / 2), y: cy - Math.floor(TREE_ROWS / 2) };
}

/** Move a câmera: lados em passos de ~1/3 da largura; páginas verticais quase inteiras. */
export function pan(view, dx, dy, map, zoom = 1) {
  const sx = Math.max(1, Math.floor(TREE_COLS / 3)) * zoom;
  const sy = (TREE_ROWS - 3) * zoom;
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
 * @param {number} [p.zoom]  1 = normal; ZOOM_OUT = visão geral
 * @param {boolean} [p.supreme] Árvore Suprema (mapa próprio)
 * @param {boolean} [p.race]    Árvore Racial (mapa próprio da raça)
 */
export function buildTreeForm(p) {
  if (p.zoom && p.zoom > 1) return buildOverviewForm(p);
  const { state, view, focus = null, mp, equipped = [] } = p;
  const lineage = RACES[state.race].lineage;
  const supreme = !!p.supreme && !!getSupremeMap(lineage);
  const raceMode = !supreme && !!p.race;
  const map = supreme ? getSupremeMap(lineage) : raceMode ? getRaceMap(lineage) : getMap(lineage);
  const { cols, rows, n } = gridOf();
  const base = layerBase();
  const buttons = new Array(base.total);
  const hit = {};

  const total = Object.keys(map.pos).filter((k) => !map.isLink.has(k)).length;
  const owned = Object.keys(map.pos).filter((k) => !map.isLink.has(k) && state.unlocked.has(k)).length;
  const race = RACES[state.race];
  const bar = (k) => `§a${'▮'.repeat(Math.round(k * 10))}§8${'▮'.repeat(10 - Math.round(k * 10))}`;
  const title = supreme
    ? `${MARK}§l§6Árvore Suprema§r  §7${race.name}  §ePontos ${state.points}  ${bar(owned / Math.max(1, total))} §a${owned}/${total}  §dMagículas ${Math.floor(mp.cur)}/${mp.max}`
    : raceMode ? `${MARK}§l§aÁrvore Racial§r  §7${race.name}  §ePontos ${state.points}  §fNv ${state.level}  ${bar(owned / Math.max(1, total))} §a${owned}/${total}  §dMagículas ${Math.floor(mp.cur)}/${mp.max}`
      : `${MARK}§l§bÁrvores Universais§r  §7${race.name}  §ePontos ${state.points}  §fNv ${state.level}  §a${owned}/${total}  §dMagículas ${Math.floor(mp.cur)}/${mp.max}`;

  const set = (i, text, icon = '') => { buttons[i] = { text, icon }; };
  for (let i = 0; i < base.node; i++) set(i, ''); // controles + preenchimento
  set(CTRL.back, 'Voltar', `${TEX.ui}btn_back`); hit[CTRL.back] = { type: 'back' };
  set(CTRL.close, '', `${TEX.ui}btn_close`); hit[CTRL.close] = { type: 'close' };
  if (raceMode) {
    // Saltos por categoria (ícone-cristal da cabeça da categoria) + botão das árvores universais.
    Object.keys(map.roots).slice(0, CATEGORY_ORDER.length - 1).forEach((sid, i) => {
      set(CTRL.cat0 + i, '', `${TEX.icon}${iconName(sid)}`);
      hit[CTRL.cat0 + i] = { type: 'jump', pos: map.roots[sid], cat: sid };
    });
    const last = CTRL.cat0 + CATEGORY_ORDER.length - 1;
    set(last, '', `${TEX.ui}tab_elemental`); hit[last] = { type: 'universal' };
  } else if (supreme) {
    Object.keys(map.roots).forEach((sid, i) => {
      set(CTRL.cat0 + i, '', `${TEX.icon}${iconName(sid)}`);
      hit[CTRL.cat0 + i] = { type: 'jump', pos: map.roots[sid], cat: sid };
    });
  } else {
    CATEGORY_ORDER.forEach((cat, i) => {
      set(CTRL.cat0 + i, '', `${TEX.ui}tab_${cat}`);
      hit[CTRL.cat0 + i] = { type: 'jump', pos: map.roots[cat], cat };
    });
  }
  set(CTRL.panLeft, '', `${TEX.ui}arrow_l`); hit[CTRL.panLeft] = { type: 'pan', dx: -1, dy: 0 };
  set(CTRL.panRight, '', `${TEX.ui}arrow_r`); hit[CTRL.panRight] = { type: 'pan', dx: 1, dy: 0 };
  set(CTRL.pageUp, '', `${TEX.ui}arrow_u`); hit[CTRL.pageUp] = { type: 'pan', dx: 0, dy: -1 };
  set(CTRL.pageDown, '', `${TEX.ui}arrow_d`); hit[CTRL.pageDown] = { type: 'pan', dx: 0, dy: 1 };
  set(CTRL.center, '', `${TEX.ui}center`); hit[CTRL.center] = { type: 'center' };
  if (!supreme) { set(CTRL.zoom, '', `${TEX.ui}zoom_out`); hit[CTRL.zoom] = { type: 'zoom', zoom: ZOOM_OUT }; }

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
      // Ilustração da habilidade (em cima do painel), nome e descrição embaixo.
      buttons[base.detail + i] = { text: detailsText(s, ev, state, isLink, equipped, supreme), icon: `${TEX.art}${iconName(sid)}` };
      // Ação do painel: a única parte que volta ao script.
      if (isLink) {
        set(base.action + i, supreme ? '§9Ir para o nó principal' : `§9Ir para o original (${TREES[s.tree].short})`, `${TEX.ui}btn_link`);
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
  return { title, body: '', buttons, hit, map, visibleNodes, cells: n, supreme };
}

function cut(text, max) {
  return text.length <= max ? text : `${text.slice(0, max - 1)}…`;
}

/** Texto compacto do painel de detalhes (cabe no painel sem rolagem). */
function detailsText(s, ev, state, isLink, equipped, full = false) {
  const ri = rarityInfo(s.rarity);
  const tree = TREES[s.tree];
  const res = tree.kind === 'racial' ? 'Aura' : 'Magículas';
  const status = { unlocked: '§aDesbloqueada', available: '§9Disponível', reachable: '§7Falta nível/pontos', special: '§cRequisito especial pendente', locked: '§8Bloqueada' }[ev.state];
  const lines = [
    `§l${ri.color}${s.name}§r §8${s.original}${s.tier ? ` · ${s.tier === 'manas' ? '§d' : s.tier === 'ultimate' ? '§6' : '§5'}${TIERS[s.tier]}§r` : ''}`,
    `${status}§r · ${TYPE_NAMES[s.type]} · ${s.rank ? `§eRank ${s.rank}§r` : `${ri.color}${ri.name}§r`}`,
    `§e${ev.cost} pts§r${s.mp ? ` · ${res} §d${s.mp}§r` : ''}${s.cooldown ? ` · ${s.cooldown}s` : ''} · Nv ${s.minLevel}`,
  ];
  if (s.src) lines.push(`§7Origem: ${SOURCES[s.src]}${s.verified ? '' : ' §c(não verificada)§7'}${s.first ? ` · 1ª aparição: ${s.first}` : ''}${s.sector ? ` · ${SECTORS[RACES[state.race].lineage]?.names[s.sector] ?? ''}` : ''}§r`);
  const acq = acquisition(s);
  if (acq) lines.push(`§7Aquisição: ${full ? cut(acq, 70) : acq}§r`);
  const mmax = masteryMax(s);
  if (mmax && state.unlocked.has(s.id)) {
    const uses = state.mastery?.[s.id] ?? 0;
    const lvl = masteryLevel(s.id, uses);
    const nx = usesForNext(s.id, uses);
    lines.push(`§bDomínio ${lvl}/${mmax}§r${nx ? ` §7(${uses}/${nx} usos)` : ' §7(máximo)'}${s.masteryDesc?.[lvl - 1] ? ` · ${s.masteryDesc[lvl - 1]}` : ''}§r`);
  }
  if (s.requires.length) lines.push(`Requer: ${s.requires.map((r) => `${state.unlocked.has(r) ? '§a✔' : '§c✘'} ${SKILLS[r]?.name ?? r}§r`).join(', ')}`);
  if (s.subs.length) lines.push(`§6Sub-habilidades: ${s.subs.map((c) => `${state.unlocked.has(c) ? '§a' : '§7'}${SKILLS[c].name}`).join('§7, ')}§r`);
  if (s.children.length && !full) lines.push(`§7Evoluções/leva a: ${s.children.map((c) => SKILLS[c].name).join(', ')}§r`);
  if (s.evolvedBy) lines.push(`${state.unlocked.has(s.evolvedBy) ? '§a✔ Evoluída' : '§7Evolui'} com ${SKILLS[s.evolvedBy].name}: ${s.evolvedDesc}§r`);
  if (isLink) lines.push(`§9Nó compartilhado de ${tree.name}.§r`);
  if (s.type === 'race_root') lines.push(cut(raceSummary(state.race).split('\n').slice(1, 4).join(' · '), 160));
  else {
    if (s.desc && !(full && s.subs.length)) lines.push(cut(s.desc, 150));
    const fx = fxOf(s, state.unlocked);
    if (fx) lines.push(`§7Efeito: ${cut(describeFx(fx), full ? 110 : 130)}§r`);
    if (full && s.limits) lines.push(`§eLimitações: ${cut(s.limits, 110)}§r`);
    if (full && s.lore) lines.push(`§dRelações: ${cut(s.lore, 110)}§r`);
  }
  if (equipped.includes(s.id)) lines.push(`§bNa barra (espaço ${equipped.indexOf(s.id) + 1})§r`);
  if (ev.missing.length && ev.state !== 'unlocked') lines.push(`§cFalta: ${ev.missing.join('; ')}§r`);
  return lines.join('\n');
}

/** Texto do método de aquisição (pontos, evolução, absorção, missão, domínio). */
export function acquisition(s) {
  if (s.acquire) return s.acquire;
  const parts = [];
  if (s.initial) return `concedida ao se tornar ${RACES[s.initial]?.name ?? s.initial}`;
  if (s.type === 'evolution') parts.push('evolução racial');
  if (s.discover) {
    const lin = TREES[s.tree].lineage;
    const e = (ABSORPTION[lin] ?? []).find((x) => x.skills.includes(s.id));
    parts.push(e ? `absorver ${e.mc.map((m) => m.replace('minecraft:', '')).join('/')} (${e.work})` : 'absorção');
  }
  if (s.needMission) parts.push(`missão "${MISSIONS[s.needMission]?.name ?? s.needMission}"`);
  if (s.needMastery) parts.push('domínio de habilidade anterior');
  if (!parts.length && TREES[s.tree].kind === 'racial') return '';
  parts.push('pontos de habilidade');
  return parts.join(' + ');
}

/**
 * Visão geral (zoom afastado): cada célula mostra o nó mais importante de um bloco ZOOM_OUT x ZOOM_OUT.
 * Tocar abre um resumo do bloco; "Ampliar aqui" volta à visão normal naquele ponto.
 */
function buildOverviewForm(p) {
  const { state, view, mp } = p;
  const Z = ZOOM_OUT;
  const lineage = RACES[state.race].lineage;
  const map = p.race ? getRaceMap(lineage) : getMap(lineage);
  const { cols, rows, n } = gridOf();
  const base = layerBase();
  const buttons = Array.from({ length: base.total }, () => ({ text: '', icon: '' }));
  const hit = {};
  const set = (i, text, icon = '') => { buttons[i] = { text, icon }; };
  const race = RACES[state.race];
  const title = `${MARK}§l§bVisão Geral§r  §7${race.name}  §ePontos ${state.points}  §fNv ${state.level}  §dMagículas ${Math.floor(mp.cur)}/${mp.max}`;
  set(CTRL.back, 'Voltar', `${TEX.ui}btn_back`); hit[CTRL.back] = { type: 'back' };
  set(CTRL.close, '', `${TEX.ui}btn_close`); hit[CTRL.close] = { type: 'close' };
  CATEGORY_ORDER.forEach((cat, i) => { set(CTRL.cat0 + i, '', `${TEX.ui}tab_${cat}`); hit[CTRL.cat0 + i] = { type: 'jump', pos: map.roots[cat], cat }; });
  set(CTRL.panLeft, '', `${TEX.ui}arrow_l`); hit[CTRL.panLeft] = { type: 'pan', dx: -1, dy: 0 };
  set(CTRL.panRight, '', `${TEX.ui}arrow_r`); hit[CTRL.panRight] = { type: 'pan', dx: 1, dy: 0 };
  set(CTRL.pageUp, '', `${TEX.ui}arrow_u`); hit[CTRL.pageUp] = { type: 'pan', dx: 0, dy: -1 };
  set(CTRL.pageDown, '', `${TEX.ui}arrow_d`); hit[CTRL.pageDown] = { type: 'pan', dx: 0, dy: 1 };
  set(CTRL.center, '', `${TEX.ui}center`); hit[CTRL.center] = { type: 'center' };
  set(CTRL.zoom, '', `${TEX.ui}zoom_in`); hit[CTRL.zoom] = { type: 'zoom', zoom: 1 };
  const rank = (st) => ({ unlocked: 4, available: 3, special: 2, reachable: 1, locked: 0 }[st]);
  let visible = 0;
  for (let r = 0; r < rows; r++) {
    for (let c = 0; c < cols; c++) {
      const i = r * cols + c;
      const keys = [];
      for (let yy = 0; yy < Z; yy++) for (let xx = 0; xx < Z; xx++) {
        const k = map.nodeAt.get(`${view.x + c * Z + xx},${view.y + r * Z + yy}`);
        if (k && !map.isLink.has(k)) keys.push(k);
      }
      if (!keys.length) continue;
      visible++;
      const items = keys.map((k) => ({ k, s: SKILLS[map.skillOf[k]], ev: evaluate(map.skillOf[k], state) }));
      items.sort((a, b) => (b.s.major - a.s.major) || (rank(b.ev.state) - rank(a.ev.state)));
      const top = items[0];
      set(base.node + i, frameCode(top.ev.state, top.s.rarity, top.s.major || keys.length > 1, false, false), `${TEX.icon}${iconName(top.s.id)}`);
      const sector = top.s.sector ? SECTORS[lineage]?.names[top.s.sector] : TREES[top.s.tree].name;
      const owned = items.filter((x) => x.ev.state === 'unlocked').length;
      const lines = [`§l§b${sector ?? ''}§r`, `${keys.length} habilidade(s) · §a${owned} desbloqueada(s)`,
        ...items.slice(0, 6).map((x) => `${{ unlocked: '§a✔', available: '§9●', special: '§c●', reachable: '§7●', locked: '§8●' }[x.ev.state]} ${x.s.name}§r`)];
      if (items.length > 6) lines.push(`§7…e mais ${items.length - 6}`);
      set(base.detail + i, lines.join('\n'));
      const pos = map.pos[top.k];
      set(base.action + i, '§l§bAmpliar aqui', `${TEX.ui}zoom_in`); hit[base.action + i] = { type: 'zoomin', pos };
    }
  }
  return { title, body: '', buttons, hit, map, visibleNodes: visible, cells: n, zoom: Z };
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
