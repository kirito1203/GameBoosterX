// Mapa único da árvore de habilidades, carregado do arquivo gerado no build.
// Uma instância por linhagem (a parte racial muda conforme a raça do jogador).
import { MAP } from '../data/generated/map.js';

export const DIRS8 = [[0, -1], [1, -1], [1, 0], [1, 1], [0, 1], [-1, 1], [-1, 0], [-1, -1]]; // n ne e se s sw w nw
const key = (x, y) => `${x},${y}`;
const cache = new Map();

function dirIndex(dx, dy) {
  return DIRS8.findIndex(([x, y]) => x === dx && y === dy);
}

/** Mapa completo para uma linhagem: nós, linhas e tocos por célula. */
export function getMap(lineage) {
  let m = cache.get(lineage);
  if (m) return m;
  const R = MAP.racial[lineage];
  if (!R) throw new Error(`linhagem sem mapa: ${lineage}`);
  const centerId = `r.${lineage}.root`;
  const pos = { ...MAP.universal.pos, ...R.pos };
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
  for (const [a, b, flat] of [...MAP.universal.edges, ...R.edges]) {
    const from = a === '@center' ? centerId : a;
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
  m = { lineage, centerId, pos, skillOf, isLink, nodeAt, edges, stubs, bounds: MAP.bounds, roots: { ...MAP.roots, racial: [0, 0] } };
  cache.set(lineage, m);
  return m;
}
