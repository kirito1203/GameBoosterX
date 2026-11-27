// Especificação compartilhada das telas (script do jogo + tools/build-ui.mjs).
// Mudou algo aqui? Rode `npm run build`.
import { CONFIG } from '../config.js';

// Marcadores no título que fazem o resource pack trocar o formulário padrão.
export const MARK = '§t§n§s';          // árvore de habilidades
export const MENU_MARK = '§t§n§m';     // menu principal (abas)
export const zoomMark = (z) => `${MARK}§${z}`;
export const menuMark = (tab) => `${MENU_MARK}§${tab}`;
export const ZOOMS = Object.keys(CONFIG.ui.zoomGrids).map(Number).sort();

// ── Árvore: botões fixos ──
export const CATEGORY_ORDER = ['racial', 'elemental', 'aspect', 'holy', 'summon', 'illusion', 'spirit', 'necromancy', 'dark',
  'advanced', 'unique', 'ultimate', 'extra', 'resist'];
export const CTRL = {
  back: 0,
  close: 1,
  cat0: 2, // 2..15 = 14 categorias (centraliza a câmera nelas)
  panLeft: 16,
  panUp: 17,
  panDown: 18,
  panRight: 19,
  zoomOut: 20,
  zoomIn: 21,
  center: 22,
  unlock: 23,
  equip: 24,
  closeDetails: 25,
  detailIcon: 26,
  gotoLink: 27,
};
export const CAT_COUNT = CATEGORY_ORDER.length;
export const CTRL_COUNT = 28;

export function gridOf(zoom) {
  const [cols, rows] = CONFIG.ui.zoomGrids[zoom];
  return { cols, rows, n: cols * rows };
}

export function hasLabels(zoom) {
  return zoom >= CONFIG.ui.labelsFromZoom;
}

/**
 * Índices das camadas da grade: nó, linhas e rótulo.
 * As camadas começam num múltiplo do nº de colunas (botões vazios de preenchimento depois dos
 * controles), porque no JSON UI o índice de cada célula vem da posição dela numa grade
 * (grid_position): índice = linha * colunas + coluna.
 */
export function layerBase(zoom) {
  const { cols, n } = gridOf(zoom);
  const start = Math.ceil(CTRL_COUNT / cols) * cols;
  return {
    startRow: start / cols,
    node: start,
    pipe: start + n,
    label: hasLabels(zoom) ? start + 2 * n : -1,
    total: start + n * (hasLabels(zoom) ? 3 : 2),
  };
}

export const SCREEN = {
  topBar: 8,
  bottomBar: 11,
  detailsWidth: 36,
};

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
};
export const MENU_COUNT = 29;
export const ACTIVE_ROWS = 4;
export const BAR_SLOTS = 5;
export const ACTION_COUNT = 6;

// ── Texturas ──
export const RARITY_INDEX = { common: 1, rare: 2, epic: 3, legendary: 4, supreme: 5 };
export const NODE_STATES = { locked: 'l', reachable: 'r', available: 'a', unlocked: 'u' };
export const EDGE = { none: 0, dim: 1, lit: 2 };

export const TEX = {
  node: 'textures/tensura/ui/node/f_',
  line: 'textures/tensura/ui/line/',
  icon: 'textures/tensura/icons/',
  ui: 'textures/tensura/ui/',
  menu: 'textures/tensura/menu/',
  race: 'textures/tensura/race/',
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
