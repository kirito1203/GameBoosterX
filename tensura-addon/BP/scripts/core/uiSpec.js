// Especificação compartilhada da interface da árvore.
// Usada pelo script do jogo (para montar o formulário) e por tools/build-ui.mjs
// (para gerar o JSON UI do resource pack). Mudou algo aqui? Rode `npm run build`.
import { CONFIG } from '../config.js';

// Marcador no título que faz o resource pack trocar o formulário padrão pela árvore.
export const MARK = '§t§n§s';
export const zoomMark = (z) => `${MARK}§${z}`;
export const ZOOMS = Object.keys(CONFIG.ui.zoomGrids).map(Number).sort();

// Botões fixos (índices do formulário). A ordem importa: o JSON UI posiciona
// cada índice num lugar fixo da tela.
export const CTRL = {
  close: 0,
  grimoire: 1,
  status: 2,
  tab0: 3, // 3..12 = 10 abas
  panLeft: 13,
  panUp: 14,
  panDown: 15,
  panRight: 16,
  zoomOut: 17,
  zoomIn: 18,
  center: 19,
  unlock: 20,
  equip: 21,
  closeDetails: 22,
  detailIcon: 23,
  gotoLink: 24,
};
export const TAB_COUNT = 10;
export const CTRL_COUNT = 25;

export function gridOf(zoom) {
  const [cols, rows] = CONFIG.ui.zoomGrids[zoom];
  return { cols, rows, n: cols * rows };
}

export function hasLabels(zoom) {
  return zoom >= CONFIG.ui.labelsFromZoom;
}

/** Índices das camadas da grade para um zoom: nó, linhas (tocos) e rótulo. */
export function layerBase(zoom) {
  const { n } = gridOf(zoom);
  return {
    node: CTRL_COUNT,
    pipe: CTRL_COUNT + n,
    label: hasLabels(zoom) ? CTRL_COUNT + 2 * n : -1,
    total: CTRL_COUNT + n * (hasLabels(zoom) ? 3 : 2),
  };
}

// Geometria da tela (porcentagens). A área da árvore ocupa quase toda a tela.
export const SCREEN = {
  topBar: 9,        // altura da barra superior (%)
  bottomBar: 12,    // altura da barra inferior (%)
  detailsWidth: 36, // largura do painel de detalhes sobre a árvore (%)
};

// Códigos de textura.
export const RARITY_INDEX = { common: 1, rare: 2, epic: 3, legendary: 4, supreme: 5 };
export const NODE_STATES = { locked: 'l', reachable: 'r', available: 'a', unlocked: 'u' };
export const EDGE = { none: 0, dim: 1, open: 2, lit: 3 };

export const TEX = {
  node: 'textures/tensura/ui/node/f_',
  pipe: 'textures/tensura/ui/pipe/p',
  icon: 'textures/tensura/icons/',
  ui: 'textures/tensura/ui/',
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

export function allPipeCodes() {
  const out = [];
  for (let i = 1; i < 256; i++) {
    const d = [(i >> 6) & 3, (i >> 4) & 3, (i >> 2) & 3, i & 3];
    out.push(d.join(''));
  }
  return out;
}

export function iconName(skillId) {
  return skillId.replace(/[^a-z0-9]+/gi, '_').toLowerCase();
}
