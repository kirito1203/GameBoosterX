// Gera o MAPA ÚNICO da árvore de habilidades (BP/scripts/data/generated/map.js).
//
// - A raiz da raça do jogador fica no centro (0,0); a árvore racial cresce para o sul.
// - As 13 árvores universais são posicionadas em volta, cada uma num ângulo, empurradas
//   para fora até não colidirem.
// - Todas as linhas (pré-requisitos reais + ligação centro→raiz de cada categoria) são
//   roteadas em 8 direções (retas e diagonais) por A*, desviando dos nós.
// Rodado no build: o jogo só carrega o resultado (sem custo de cálculo no celular).
import { writeFileSync, mkdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { TREES, SKILLS, UNIVERSAL_TREES, LINEAGES } from '../BP/scripts/data/registry.js';
import { computeLayout } from '../BP/scripts/core/layout.js';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const OUT = join(ROOT, 'BP/scripts/data/generated/map.js');

// Ordem/ângulo das categorias (graus; 270 = norte/topo, 0 = leste, 90 = sul).
const PLACEMENT = [
  ['elemental', 270], ['unique', 235], ['dark', 205], ['illusion', 180], ['necromancy', 150], ['summon', 125],
  ['ultimate', 305], ['aspect', 335], ['holy', 0], ['spirit', 25], ['extra', 55],
  ['advanced', 250], ['resist', 290],
];

const DIRS8 = [[0, -1], [1, -1], [1, 0], [1, 1], [0, 1], [-1, 1], [-1, 0], [-1, -1]]; // n ne e se s sw w nw
const key = (x, y) => `${x},${y}`;

function bboxOf(pos) {
  const xs = Object.values(pos).map((p) => p[0]);
  const ys = Object.values(pos).map((p) => p[1]);
  return { minX: Math.min(...xs), maxX: Math.max(...xs), minY: Math.min(...ys), maxY: Math.max(...ys) };
}
function overlap(a, b, m) {
  return !(a.maxX + m < b.minX || b.maxX + m < a.minX || a.maxY + m < b.minY || b.maxY + m < a.minY);
}

// ── Árvores raciais: tudo cresce para o sul a partir do centro ──
const racial = {};
let reserved = { minX: 0, maxX: 0, minY: 1, maxY: 1 };
for (const lin in LINEAGES) {
  const L = computeLayout(TREES[`race.${lin}`], { noLinks: true, forceArm: 'S' });
  racial[lin] = L.pos;
  const b = bboxOf(L.pos);
  reserved = { minX: Math.min(reserved.minX, b.minX), maxX: Math.max(reserved.maxX, b.maxX), minY: 1, maxY: Math.max(reserved.maxY, b.maxY) };
}

// ── Posiciona as árvores universais ──
// Pré-requisitos de outra categoria viram "nós compartilhados" (cópia ao lado, com selo),
// em vez de linhas atravessando o mapa. A chave de um nó compartilhado é "id@árvore".
const uniPos = {};
const uniLink = {};
const roots = {};
const localEdges = [];
const placed = [reserved, { minX: -8, maxX: 8, minY: -7, maxY: 0 }]; // anel livre ao redor do centro para as linhas de ligação
function towardCenterArm(deg) {
  // Braço que apontaria para o centro: fica livre para a linha de ligação.
  const a = ((deg + 180) % 360 + 360) % 360;
  if (a >= 45 && a < 135) return 'S';
  if (a >= 135 && a < 225) return 'W';
  if (a >= 225 && a < 315) return 'N';
  return 'E';
}
for (const [tid, deg] of PLACEMENT) {
  const t = TREES[tid];
  if (!t) throw new Error(`árvore desconhecida ${tid}`);
  const cross = [];
  for (const s of t.skills) for (const r of s.requires) if (SKILLS[r].tree !== tid && !cross.includes(r)) cross.push(r);
  const L = computeLayout(t, { links: cross.map((id) => ({ id })), blockedArm: towardCenterArm(deg) });
  const lb = bboxOf(L.pos);
  const a = (deg * Math.PI) / 180;
  for (let d = 5; d < 400; d++) {
    const rx = Math.round(Math.cos(a) * d * 1.25);
    const ry = Math.round(Math.sin(a) * d);
    const bb = { minX: lb.minX + rx, maxX: lb.maxX + rx, minY: lb.minY + ry, maxY: lb.maxY + ry };
    if (placed.some((p) => overlap(p, bb, 2))) continue;
    placed.push(bb);
    roots[tid] = [rx, ry];
    const kOf = (id) => (L.isLink.has(id) ? `${id}@${tid}` : id);
    for (const id in L.pos) {
      uniPos[kOf(id)] = [L.pos[id][0] + rx, L.pos[id][1] + ry];
      if (L.isLink.has(id)) uniLink[kOf(id)] = id;
    }
    for (const id in L.pos) {
      for (const r of SKILLS[id].requires) if (L.pos[r] && !L.isLink.has(id)) localEdges.push([kOf(r), kOf(id)]);
    }
    break;
  }
}
for (const t of UNIVERSAL_TREES) if (!roots[t.id]) throw new Error(`árvore não posicionada: ${t.id}`);

// ── Roteamento em 8 direções ──
function makeRouter(nodeCells, blockedCells, usage, bounds) {
  return function route(from, to) {
    const [sx, sy] = from;
    const [tx, ty] = to;
    const h = (x, y) => { const dx = Math.abs(x - tx), dy = Math.abs(y - ty); return Math.max(dx, dy) + 0.4142 * Math.min(dx, dy); };
    const open = new Map();
    const g = new Map();
    const prev = new Map();
    const startK = `${sx},${sy},8`;
    g.set(startK, 0);
    open.set(startK, h(sx, sy));
    while (open.size) {
      let bk = null, bf = Infinity;
      for (const [k, f] of open) if (f < bf) { bf = f; bk = k; }
      open.delete(bk);
      const [x, y, d] = bk.split(',').map(Number);
      if (x === tx && y === ty) {
        const path = [];
        let k = bk;
        while (k) { const [px, py] = k.split(','); path.push([+px, +py]); k = prev.get(k); }
        return path.reverse();
      }
      const gc = g.get(bk);
      for (let nd = 0; nd < 8; nd++) {
        if (d !== 8) { const turn = Math.min((nd - d + 8) % 8, (d - nd + 8) % 8); if (turn > 2) continue; }
        const [vx, vy] = DIRS8[nd];
        const nx = x + vx, ny = y + vy;
        if (nx < bounds.minX || nx > bounds.maxX || ny < bounds.minY || ny > bounds.maxY) continue;
        const nk = key(nx, ny);
        const isEnd = nx === tx && ny === ty;
        if (!isEnd && (nodeCells.has(nk) || blockedCells(nx, ny))) continue;
        let c = gc + (nd % 2 ? 1.4142 : 1);
        if (d !== 8 && nd !== d) c += Math.min((nd - d + 8) % 8, (d - nd + 8) % 8) === 1 ? 0.35 : 0.9;
        c += (usage.get(nk) ?? 0) * 1.6;
        if (nd % 2 && (nodeCells.has(key(x + vx, y)) || nodeCells.has(key(x, y + vy)))) c += 0.6; // cantos de nós
        const sk = `${nx},${ny},${nd}`;
        if (c < (g.get(sk) ?? Infinity)) {
          g.set(sk, c);
          prev.set(sk, bk);
          open.set(sk, c + h(nx, ny));
        }
      }
    }
    return null;
  };
}

function flat(path) { return path.flat(); }
function addUsage(usage, path) { for (const [x, y] of path) usage.set(key(x, y), (usage.get(key(x, y)) ?? 0) + 1); }

const all = Object.values(uniPos);
const bounds = bboxOf({ ...uniPos, a: [reserved.minX, reserved.maxY], b: [reserved.maxX, reserved.minY] });
const B = { minX: bounds.minX - 4, maxX: bounds.maxX + 4, minY: bounds.minY - 4, maxY: bounds.maxY + 4 };

// Arestas universais.
const uniNodes = new Set(all.map(([x, y]) => key(x, y)));
uniNodes.add(key(0, 0));
const inReserved = (x, y) => x >= reserved.minX - 1 && x <= reserved.maxX + 1 && y >= 1 && y <= reserved.maxY + 1;
const usage = new Map();
const routeUni = makeRouter(uniNodes, inReserved, usage, B);
const uniEdgesWanted = [...localEdges];
const len = ([a, b]) => Math.hypot(uniPos[a][0] - uniPos[b][0], uniPos[a][1] - uniPos[b][1]);
uniEdgesWanted.sort((a, b) => len(a) - len(b));
for (const tid in roots) uniEdgesWanted.push(['@center', TREES[tid].root]);
const uniEdges = [];
for (const [a, b] of uniEdgesWanted) {
  const pa = a === '@center' ? [0, 0] : uniPos[a];
  const path = routeUni(pa, uniPos[b]);
  if (!path) throw new Error(`sem rota ${a} → ${b}`);
  addUsage(usage, path);
  uniEdges.push([a, b, flat(path)]);
}

// Arestas raciais (por linhagem).
const racialOut = {};
for (const lin in racial) {
  const pos = racial[lin];
  const nodes = new Set([...uniNodes, ...Object.values(pos).map(([x, y]) => key(x, y))]);
  const u = new Map(usage);
  const route = makeRouter(nodes, () => false, u, B);
  const edges = [];
  for (const id in pos) for (const r of SKILLS[id].requires) {
    if (!pos[r]) continue;
    const path = route(pos[r], pos[id]);
    if (!path) throw new Error(`sem rota ${lin}: ${r} → ${id}`);
    addUsage(u, path);
    edges.push([r, id, flat(path)]);
  }
  racialOut[lin] = { pos, edges };
}

const data = { version: 1, bounds: B, roots, universal: { pos: uniPos, links: uniLink, edges: uniEdges }, racial: racialOut };
mkdirSync(dirname(OUT), { recursive: true });
writeFileSync(OUT, `// Gerado por tools/gen-map.mjs — não edite à mão.\nexport const MAP = ${JSON.stringify(data)};\n`);
console.log(`mapa: ${Object.keys(uniPos).length} nós universais, ${uniEdges.length} linhas, área ${B.maxX - B.minX + 1}x${B.maxY - B.minY + 1}`);
