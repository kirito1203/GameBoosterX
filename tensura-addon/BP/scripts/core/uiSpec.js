// Especificação compartilhada das telas (script do jogo + tools/build-ui.mjs).
// Mudou algo aqui? Rode `npm run build`.
import { CONFIG } from '../config.js';

// Marcadores no título que fazem o resource pack trocar o formulário padrão.
export const MARK = '§t§n§s';          // árvore de habilidades
export const MENU_MARK = '§t§n§m';     // menu principal (abas)
export const menuMark = (tab) => `${MENU_MARK}§${tab}`;

// ── Árvore ──
// A área da árvore é uma grade de TREE_COLS x TREE_ROWS células dentro de um painel com
// rolagem vertical (arrastar o dedo para cima/baixo, sem recarregar). Tocar num nó abre os
// detalhes na hora: cada nó é um "toggle" de um grupo de rádio que existe só no cliente.
// Só ações que mudam o progresso (desbloquear, barra) e a troca de região voltam ao script.
export const CATEGORY_ORDER = ['racial', 'elemental', 'aspect', 'holy', 'summon', 'illusion', 'spirit', 'necromancy', 'dark',
  'advanced', 'unique', 'ultimate', 'extra', 'resist'];
export const CTRL = {
  back: 0,
  close: 1,
  cat0: 2, // 2..15 = 14 categorias (leva a câmera até elas)
  panLeft: 16,
  panRight: 17,
  pageUp: 18,
  pageDown: 19,
  center: 20,
  zoom: 21, // visão geral (zoom afastado) ⇄ visão normal
};
export const CAT_COUNT = CATEGORY_ORDER.length;
export const CTRL_COUNT = 22;
// Visão geral: cada célula representa um bloco ZOOM_OUT x ZOOM_OUT do mapa.
export const ZOOM_OUT = 3;
export const TREE_COLS = CONFIG.ui.treeGrid[0];
export const TREE_ROWS = CONFIG.ui.treeGrid[1];
export const TREE_CELLS = TREE_COLS * TREE_ROWS;
export const DETAIL_ROWS = 3;           // altura do painel de detalhes (em linhas da grade)
export const NODE_TOGGLE_GROUP = 'tensura_node';

export function gridOf() {
  return { cols: TREE_COLS, rows: TREE_ROWS, n: TREE_CELLS };
}

/**
 * Índices das camadas: nó, linhas, detalhes e ação. Cada camada começa no início de uma
 * linha da grade (botões vazios de preenchimento), porque no JSON UI o índice de cada célula
 * vem da posição dela numa grade (grid_position): índice = linha * colunas + coluna.
 */
export function layerBase() {
  const start = Math.ceil(CTRL_COUNT / TREE_COLS) * TREE_COLS;
  const n = TREE_CELLS;
  return { startRow: start / TREE_COLS, node: start, pipe: start + n, detail: start + 2 * n, action: start + 3 * n, total: start + 4 * n };
}

/** Painel de detalhes da célula (c, r): lado oposto ao nó e preso dentro da grade. */
export function detailPlacement(c, r) {
  const right = c < TREE_COLS / 2;
  const row = Math.max(0, Math.min(r - 1, TREE_ROWS - DETAIL_ROWS));
  return { x: right ? 51 : 2, w: 47, row };
}

// ── Menu principal ──
export const MENU_TABS = ['status', 'race', 'skills', 'evolution', 'records'];
export const MENU = {
  close: 0,
  tab0: 1, // 1..5
  back: 6,
  active0: 7, // 7..10 habilidades ativas (4 linhas)
  slot0: 11, // 11..15 barra de habilidades (5 espaços)
  raceName: 16,
  stat0: 17, // 17..21: EP, Aura, Magículas, Nível, Progresso
  progressBar: 22,
  action0: 23, // 23..28 botões de ação das outras abas
  sage: 29, // botão GRANDE SÁBIO (coluna esquerda, todas as abas)
};
export const MENU_COUNT = 30;
export const ACTIVE_ROWS = 4;
export const BAR_SLOTS = 5;
export const ACTION_COUNT = 6;

// ── Texturas ──
export const RARITY_INDEX = { common: 1, rare: 2, epic: 3, legendary: 4, supreme: 5 };
export const NODE_STATES = { locked: 'l', reachable: 'r', special: 's', available: 'a', unlocked: 'u' };
export const EDGE = { none: 0, dim: 1, lit: 2 };

export const TEX = {
  node: 'textures/tensura/ui/node/f_',
  line: 'textures/tensura/ui/line/',
  icon: 'textures/tensura/icons/',
  ui: 'textures/tensura/ui/',
  menu: 'textures/tensura/menu/',
  race: 'textures/tensura/race/',
  sage: 'textures/tensura/sage/',
};

export function frameCode(state, rarity, major, link, selected) {
  return `${NODE_STATES[state]}${RARITY_INDEX[rarity]}${major ? 'm' : 'n'}${link ? 'k' : 'n'}${selected ? 's' : 'n'}`;
}

export function allFrameCodes() {
  const out = [];
  for (const st of Object.values(NODE_STATES)) for (const r of Object.values(RARITY_INDEX))
    for (const m of 'mn') for (const k of 'kn') for (const s of 'sn') out.push(`${st}${r}${m}${k}${s}`);
  return out;
}

/**
 * Código de linha: 8 caracteres (n ne e se s sw w nw), cada um 0 (sem linha), 1 (apagada) ou 2 (acesa).
 * Só as combinações que existem no mapa são geradas (ver lineCodesForMaps).
 */
export function lineCodesForCell(dirs) {
  // dirs: índices (0..7) das direções com linha nesta célula → todas as combinações de estado
  const out = [];
  for (let m = 0; m < 1 << dirs.length; m++) {
    const c = Array(8).fill('0');
    dirs.forEach((d, i) => { c[d] = m & (1 << i) ? '2' : '1'; });
    out.push(c.join(''));
  }
  return out;
}

export function iconName(skillId) {
  return skillId.replace(/[^a-z0-9]+/gi, '_').toLowerCase();
}

// ── Tela "Escolha sua Raça" ──
export const RACE_MARK = '§t§n§r';
export const RACE = {
  confirm: 0,
  prev: 1,
  next: 2,
  sageText: 3,
  infoName: 4,
  infoPortrait: 5,
  infoDesc: 6,
  infoInitial: 7,
  infoAffinity: 8,
  infoEvolutions: 9,
  infoDifficulty: 10,
  infoTraits: 11,
  page: 12,
  card0: 13, // 13..30 = 18 cards (6 x 3)
};
export const RACE_CARD_COLS = 6;
export const RACE_CARD_ROWS = 3;
export const RACE_PAGE_SIZE = RACE_CARD_COLS * RACE_CARD_ROWS;
export const RACE_COUNT = RACE.card0 + RACE_PAGE_SIZE;
// Marcadores dentro do texto do card (removidos na exibição do nome).
export const CARD_SELECTED = '§t§c§s';
export const CARD_LOCKED = '§t§c§l';

// ── Painel do Grande Sábio ──
// Tela inicial (marcador §0): grade 3x3 de blocos + barra Configurações + caixa de diálogo.
// Cada bloco abre a página da categoria (marcador §1..§9), com "Voltar" para a tela inicial.
export const SAGE_MARK = '§t§n§g';
export const sageMark = (cat) => `${SAGE_MARK}§${cat}`;
export const SAGE_TILES = ['ask', 'analyses', 'level', 'skills', 'magic', 'intel', 'next', 'history', 'missions'];
export const SAGE_CATS = ['ask', 'analyses', 'level', 'skills', 'magic', 'intel', 'next', 'history', 'settings', 'missions'];
export const SAGE = {
  close: 0,
  back: 1,
  tile0: 2, // 2..10 = 9 blocos da tela inicial
  settings: 11,
  portrait: 12,
  stage: 13,
  level: 14,
  xp: 15,
  bar: 16,
  panelTitle: 17,
  action0: 18, // 18..23
  emblem: 24,
};
export const SAGE_ACTIONS = 6;
export const SAGE_COUNT = 25;
