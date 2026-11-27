// Mapa único da árvore de habilidades, carregado do arquivo gerado no build.
// Uma instância por linhagem (a parte racial muda conforme a raça do jogador).
import { MAP } from '../data/generated/map.js';

export const DIRS8 = [[0, -1], [1, -1], [1, 0], [1, 1], [0, 1], [-1, 1], [-1, 0], [-1, -1]]; // n ne e se s sw w nw
const key = (x, y) => `${x},${y}`;
const cache = new Map();

function dirIndex(dx, dy) {
  return DIRS8.findIndex(([x, y]) => x === dx && y === dy);
}

/** Mapa racial da linhagem (tela própria): só a árvore da raça, com saltos por categoria. */
export function getRaceMap(lineage) {
  const ck = `race:${lineage}`;
  let m = cache.get(ck);
  if (m) return m;
  const R = MAP.racial[lineage];
  if (!R) throw new Error(`linhagem sem mapa: ${lineage}`);
  const skillOf = {};
  for (const k in R.pos) skillOf[k] = k;
  const nodeAt = new Map();
  for (const k in R.pos) nodeAt.set(key(...R.pos[k]), k);
  const edges = [];
  const stubs = new Map();
  for (const [a, b, flat] of R.edges) addEdge(edges, stubs, skillOf, a, b, flat);
  const centerId = `r.${lineage}.root`;
  const roots = { [centerId]: R.pos[centerId] };
  for (const sec in R.heads) roots[R.heads[sec]] = R.pos[R.heads[sec]];
  m = { lineage, race: true, centerId, pos: R.pos, skillOf, isLink: new Set(), nodeAt, edges, stubs, bounds: R.bounds, roots, heads: R.heads };
  cache.set(ck, m);
  return m;
}

/** Árvore Suprema da linhagem (tela própria), no mesmo formato do mapa principal. */
export function getSupremeMap(lineage) {
  const ck = `supreme:${lineage}`;
  let m = cache.get(ck);
  if (m) return m;
  const S = MAP.supreme?.[lineage];
  if (!S) return null;
  const skillOf = {};
  const isLink = new Set();
  for (const k in S.pos) { skillOf[k] = S.links[k] ?? k; if (S.links[k]) isLink.add(k); }
  const nodeAt = new Map();
  for (const k in S.pos) nodeAt.set(key(...S.pos[k]), k);
  const edges = [];
  const stubs = new Map();
  for (const [a, b, flat] of S.edges) addEdge(edges, stubs, skillOf, a, b, flat);
  const first = Object.keys(S.roots)[0];
  m = { lineage, supreme: true, centerId: first, pos: S.pos, skillOf, isLink, nodeAt, edges, stubs, bounds: S.bounds, roots: S.roots };
  cache.set(ck, m);
  return m;
}

function addEdge(edges, stubs, skillOf, from, b, flat) {
  /** @type {number[][]} */
  const path = [];
  for (let i = 0; i < flat.length; i += 2) path.push([flat[i], flat[i + 1]]);
  const idx = edges.length;
  edges.push({ from, to: b, fromSkill: skillOf[from] ?? from, toSkill: skillOf[b] ?? b, path });
  for (let i = 0; i + 1 < path.length; i++) {
    const [x1, y1] = path[i];
    const [x2, y2] = path[i + 1];
    const d = dirIndex(x2 - x1, y2 - y1);
    for (const [cx, cy, dd] of [[x1, y1, d], [x2, y2, (d + 4) % 8]]) {
      const k = key(cx, cy);
      let s = stubs.get(k);
      if (!s) { s = [[], [], [], [], [], [], [], []]; stubs.set(k, s); }
      if (!s[dd].includes(idx)) s[dd].push(idx);
    }
  }
}

/** Mapa completo para uma linhagem: nós, linhas e tocos por célula. */
export function getMap(lineage) {
  let m = cache.get(lineage);
  if (m) return m;
  const R = MAP.racial[lineage];
  if (!R) throw new Error(`linhagem sem mapa: ${lineage}`);
  const centerId = `r.${lineage}.root`;
  // Mapa principal: árvores universais em volta da raiz da raça (a árvore racial tem mapa próprio).
  const pos = { ...MAP.universal.pos, [centerId]: [0, 0] };
  const skillOf = {};
  const isLink = new Set();
  for (const k in pos) {
    const link = MAP.universal.links[k];
    skillOf[k] = link ?? k;
    if (link) isLink.add(k);
  }
  const nodeAt = new Map();
  for (const k in pos) nodeAt.set(key(...pos[k]), k);
  const edges = [];
  const stubs = new Map(); // célula -> { bits: number[8] de índices de aresta }
  for (const [a, b, flat] of MAP.universal.edges) addEdge(edges, stubs, skillOf, a === '@center' ? centerId : a, b, flat);
  m = { lineage, centerId, pos, skillOf, isLink, nodeAt, edges, stubs, bounds: MAP.bounds, roots: { ...MAP.roots, racial: [0, 0] } };
  cache.set(lineage, m);
  return m;
}
