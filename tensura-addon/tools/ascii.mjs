// Desenha uma árvore em ASCII (nós e linhas) para inspeção rápida no terminal.
// Uso: node tools/ascii.mjs <treeId>
import { TREES, SKILLS } from '../BP/scripts/data/registry.js';
import { getLayout } from '../BP/scripts/core/layout.js';

const id = process.argv[2] ?? 'elemental';
const L = getLayout(TREES[id]);
const { minX, maxX, minY, maxY } = L.bounds;
const W = 7; // largura de cada célula em caracteres (coluna 0 = minX)
for (let y = minY; y <= maxY; y++) {
  let top = '', mid = '';
  for (let x = minX; x <= maxX; x++) {
    const k = `${x},${y}`;
    const st = L.stubs.get(k) ?? { n: [], e: [], s: [], w: [] };
    const node = L.nodeAt.get(k);
    const half = (W - 1) / 2;
    top += ' '.repeat(half) + (st.n.length ? '|' : ' ') + ' '.repeat(half);
    let cell;
    if (node) {
      const s = SKILLS[node];
      let lab = (s.original.replace(/[^A-Za-z]/g, '').slice(0, 5)).padEnd(5);
      if (L.isLink.has(node)) lab = '@' + lab.slice(0, 4);
      cell = (st.w.length ? '-' : ' ') + lab + (st.e.length ? '-' : ' ');
    } else {
      const c = (st.n.length || st.s.length) && (st.e.length || st.w.length) ? '+' : st.n.length || st.s.length ? '|' : st.e.length || st.w.length ? '-' : ' ';
      cell = (st.w.length ? '---' : '   ') + c + (st.e.length ? '---' : '   ');
    }
    mid += cell;
  }
  console.log(top.replace(/\s+$/, ''));
  console.log(mid.replace(/\s+$/, ''));
}
