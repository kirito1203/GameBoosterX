// Layout automático das árvores em grade + roteamento das conexões.
//
// A raiz fica em (0,0). Os filhos diretos da raiz formam "braços" nas quatro
// direções (E, W, N, S). Cada braço é uma árvore em camadas: a profundidade
// cresce na direção do braço (passo 2) e os irmãos se empilham no eixo
// perpendicular (passo 1). Entre camadas fica a linha de barramento (cotovelos).
//
// As linhas representam os pré-requisitos reais (`requires`):
//   - a aresta do pai de layout usa o barramento ortogonal;
//   - pré-requisitos adicionais são roteados por busca de menor custo na grade,
//     desviando dos nós.
// Tudo é calculado uma única vez por árvore e cacheado.
import { SKILLS } from '../data/registry.js';

export const DIRS = ['n', 'e', 's', 'w'];
const DV = { n: [0, -1], e: [1, 0], s: [0, 1], w: [-1, 0] };
const OPP = { n: 's', s: 'n', e: 'w', w: 'e' };
const key = (x, y) => `${x},${y}`;

const ARM_AXES = {
  // depth vector, breadth vector
  E: { d: [1, 0], b: [0, 1] },
  W: { d: [-1, 0], b: [0, 1] },
  N: { d: [0, -1], b: [1, 0] },
  S: { d: [0, 1], b: [1, 0] },
};

const cache = new Map();

export function getLayout(tree) {
  let l = cache.get(tree.id);
  if (!l) {
    l = computeLayout(tree);
    cache.set(tree.id, l);
  }
  return l;
}

export function computeLayout(tree) {
  const rootId = tree.root;
  const ids = [...tree.skills.map((s) => s.id)];
  const linkArm = {};
  for (const lk of tree.links ?? []) {
    if (!SKILLS[lk.id]) throw new Error(`Link inexistente em ${tree.id}: ${lk.id}`);
    if (!ids.includes(lk.id)) ids.push(lk.id);
    if (lk.arm) linkArm[lk.id] = lk.arm;
  }
  const inTree = new Set(ids);
  const isLink = new Set((tree.links ?? []).map((l) => l.id));
  if (!inTree.has(rootId)) throw new Error(`Raiz ausente na árvore ${tree.id}`);

  // 1) Pai de layout de cada nó.
  const parent = {};
  const hiddenParentEdge = new Set();
  for (const id of ids) {
    if (id === rootId) continue;
    const s = SKILLS[id];
    let p = s.layoutParent && inTree.has(s.layoutParent) ? s.layoutParent : null;
    if (!p) p = s.requires.find((r) => inTree.has(r)) ?? null;
    if (!p) {
      p = rootId; // nó compartilhado sem pré-requisito nesta árvore: flutua perto da raiz
      hiddenParentEdge.add(id);
    }
    parent[id] = p;
  }
  // Evita ciclos de layout.
  for (const id of ids) {
    const seen = new Set();
    let c = id;
    while (c !== rootId) {
      if (seen.has(c)) throw new Error(`Ciclo de pré-requisitos em ${tree.id} envolvendo ${id}`);
      seen.add(c);
      c = parent[c];
      if (c === undefined) break;
    }
  }
  const children = {};
  for (const id of ids) children[id] = [];
  for (const id of ids) if (id !== rootId) children[parent[id]].push(id);

  // 2) Distribui os filhos da raiz em braços.
  const arms = { E: [], W: [], N: [], S: [] };
  const sizeOf = (id) => 1 + children[id].reduce((a, c) => a + sizeOf(c), 0);
  const unassigned = [];
  for (const c of children[rootId]) {
    const a = isLink.has(c) ? (linkArm[c] ?? null) : SKILLS[c].arm;
    if (a && arms[a]) arms[a].push(c);
    else unassigned.push(c);
  }
  for (const c of unassigned) {
    const best = Object.keys(arms).sort((a, b) => armWeight(arms[a]) - armWeight(arms[b]))[0];
    arms[best].push(c);
  }
  function armWeight(list) { return list.reduce((a, c) => a + sizeOf(c), 0); }

  // 3) Layout local de cada braço: (depth, breadth).
  function layoutArm(rootsOfArm) {
    const local = {};
    let cursor = 0;
    function place(id, depth) {
      const kids = children[id];
      if (!kids.length) {
        local[id] = [depth, cursor++];
        return local[id][1];
      }
      const bs = kids.map((k) => place(k, depth + 2));
      const b = Math.round((Math.min(...bs) + Math.max(...bs)) / 2);
      // Mantém o pai alinhado a um filho quando possível (linhas mais retas).
      const aligned = bs.reduce((best, v) => (Math.abs(v - b) < Math.abs(best - b) ? v : best), bs[0]);
      local[id] = [depth, aligned];
      return aligned;
    }
    for (const r of rootsOfArm) place(r, 0);
    const bVals = Object.values(local).map((v) => v[1]);
    const dVals = Object.values(local).map((v) => v[0]);
    return {
      local,
      bMin: bVals.length ? Math.min(...bVals) : 0,
      bMax: bVals.length ? Math.max(...bVals) : 0,
      dMax: dVals.length ? Math.max(...dVals) : 0,
    };
  }

  const pos = { [rootId]: [0, 0] };
  const armOf = {};
  const laid = {};
  for (const a of Object.keys(arms)) laid[a] = layoutArm(arms[a]);

  // Braços horizontais (E/W) ficam numa faixa centrada em y=0.
  let bandMin = 0;
  let bandMax = 0;
  for (const a of ['E', 'W']) {
    const L = laid[a];
    if (!arms[a].length) continue;
    const span = L.bMax - L.bMin;
    const off = -L.bMin - Math.floor(span / 2);
    L.bOff = off;
    L.dStart = 2;
    bandMin = Math.min(bandMin, L.bMin + off);
    bandMax = Math.max(bandMax, L.bMax + off);
  }
  // Braços verticais (N/S) começam depois da faixa horizontal.
  for (const a of ['N', 'S']) {
    const L = laid[a];
    if (!arms[a].length) continue;
    const span = L.bMax - L.bMin;
    L.bOff = -L.bMin - Math.floor(span / 2);
    L.dStart = a === 'N' ? Math.max(2, -bandMin + 2) : Math.max(2, bandMax + 2);
  }
  for (const a of Object.keys(arms)) {
    const L = laid[a];
    if (!arms[a].length) continue;
    const ax = ARM_AXES[a];
    for (const id in L.local) {
      const [d, b] = L.local[id];
      const dd = d + L.dStart;
      const bb = b + L.bOff;
      pos[id] = [ax.d[0] * dd + ax.b[0] * bb, ax.d[1] * dd + ax.b[1] * bb];
      armOf[id] = a;
    }
  }

  // 4) Células ocupadas por nós.
  const nodeAt = new Map();
  for (const id of ids) {
    const k = key(...pos[id]);
    if (nodeAt.has(k)) throw new Error(`Sobreposição de nós em ${tree.id}: ${id} e ${nodeAt.get(k)} em ${k}`);
    nodeAt.set(k, id);
  }

  // 5) Arestas.
  const edges = [];
  const usedPipe = new Map(); // key -> count
  function addEdge(from, to, path) {
    const idx = edges.length;
    edges.push({ from, to, path });
    for (const [x, y] of path) usedPipe.set(key(x, y), (usedPipe.get(key(x, y)) ?? 0) + 1);
    return idx;
  }

  // 5a) Arestas do pai de layout (barramento ortogonal).
  for (const id of ids) {
    if (id === rootId || hiddenParentEdge.has(id)) continue;
    const p = parent[id];
    const a = armOf[id];
    const ax = ARM_AXES[a];
    const [px, py] = pos[p];
    const [cx, cy] = pos[id];
    // Cotovelo: uma célula antes do filho, no eixo de profundidade.
    const ex = cx - ax.d[0];
    const ey = cy - ax.d[1];
    const path = [];
    line(path, px, py, ...projectOnDepth(px, py, ex, ey, ax));
    line(path, ...last(path), ex, ey);
    line(path, ex, ey, cx, cy);
    addEdge(p, id, dedupe(path));
  }

  // 5b) Pré-requisitos adicionais: busca de menor custo.
  for (const id of ids) {
    const s = SKILLS[id];
    for (const r of s.requires) {
      if (!inTree.has(r)) continue;
      if (r === parent[id] && !hiddenParentEdge.has(id)) continue;
      const path = route(pos[r], pos[id], nodeAt, usedPipe);
      if (path) addEdge(r, id, path);
    }
  }

  // 6) Tocos por célula.
  const stubs = new Map(); // key -> { n: [edgeIdx], e: [...], s: [...], w: [...] }
  edges.forEach((e, idx) => {
    for (let i = 0; i + 1 < e.path.length; i++) {
      const [x1, y1] = e.path[i];
      const [x2, y2] = e.path[i + 1];
      const d = dirOf(x2 - x1, y2 - y1);
      addStub(stubs, key(x1, y1), d, idx);
      addStub(stubs, key(x2, y2), OPP[d], idx);
    }
  });

  const xs = Object.values(pos).map((p) => p[0]);
  const ys = Object.values(pos).map((p) => p[1]);
  return {
    treeId: tree.id,
    rootId,
    ids,
    isLink,
    pos,
    nodeAt,
    edges,
    stubs,
    bounds: { minX: Math.min(...xs), maxX: Math.max(...xs), minY: Math.min(...ys), maxY: Math.max(...ys) },
  };
}

function projectOnDepth(px, py, ex, ey, ax) {
  // Ponto no eixo de profundidade do pai alinhado ao cotovelo.
  return ax.d[0] !== 0 ? [ex, py] : [px, ey];
}

function line(path, x1, y1, x2, y2) {
  if (!path.length) path.push([x1, y1]);
  const dx = Math.sign(x2 - x1);
  const dy = Math.sign(y2 - y1);
  let x = x1;
  let y = y1;
  while (x !== x2 || y !== y2) {
    if (x !== x2) x += dx;
    else y += dy;
    path.push([x, y]);
  }
}

function last(path) { return path[path.length - 1]; }

function dedupe(path) {
  const out = [];
  for (const p of path) {
    const l = out[out.length - 1];
    if (!l || l[0] !== p[0] || l[1] !== p[1]) out.push(p);
  }
  return out;
}

function dirOf(dx, dy) {
  if (dx === 1) return 'e';
  if (dx === -1) return 'w';
  if (dy === 1) return 's';
  return 'n';
}

function addStub(stubs, k, d, idx) {
  let c = stubs.get(k);
  if (!c) { c = { n: [], e: [], s: [], w: [] }; stubs.set(k, c); }
  if (!c[d].includes(idx)) c[d].push(idx);
}

// Dijkstra com penalidade por curva e por reutilizar células já usadas.
function route(from, to, nodeAt, usedPipe) {
  const [sx, sy] = from;
  const [tx, ty] = to;
  const pad = 4;
  const minX = Math.min(sx, tx) - pad, maxX = Math.max(sx, tx) + pad;
  const minY = Math.min(sy, ty) - pad, maxY = Math.max(sy, ty) + pad;
  const start = `${sx},${sy},-`;
  const dist = new Map([[start, 0]]);
  const prev = new Map();
  const open = [[0, sx, sy, '-']];
  while (open.length) {
    open.sort((a, b) => a[0] - b[0]);
    const [c, x, y, d] = open.shift();
    const sk = `${x},${y},${d}`;
    if (c > (dist.get(sk) ?? Infinity)) continue;
    if (x === tx && y === ty) {
      const path = [];
      let cur = sk;
      while (cur) {
        const [px, py] = cur.split(',');
        path.push([Number(px), Number(py)]);
        cur = prev.get(cur);
      }
      return path.reverse();
    }
    for (const nd of DIRS) {
      const [vx, vy] = DV[nd];
      const nx = x + vx;
      const ny = y + vy;
      if (nx < minX || nx > maxX || ny < minY || ny > maxY) continue;
      const k = key(nx, ny);
      if (nodeAt.has(k) && !(nx === tx && ny === ty)) continue;
      let cost = c + 1;
      if (d !== '-' && d !== nd) cost += 2;
      cost += (usedPipe.get(k) ?? 0) * 3;
      const nk = `${nx},${ny},${nd}`;
      if (cost < (dist.get(nk) ?? Infinity)) {
        dist.set(nk, cost);
        prev.set(nk, sk);
        open.push([cost, nx, ny, nd]);
      }
    }
  }
  return null;
}
