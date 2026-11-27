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
import { TREES, SKILLS, UNIVERSAL_TREES, LINEAGES, SUPREME_ORDER } from '../BP/scripts/data/registry.js';
import { computeLayout } from '../BP/scripts/core/layout.js';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const OUT = join(ROOT, 'BP/scripts/data/generated/map.js');

// Ordem/ângulo das categorias (graus; 270 = norte/topo, 0 = leste, 90 = sul).
const PLACEMENT = [
  ['elemental', 270], ['unique', 235], ['dark', 205], ['illusion', 180], ['necromancy', 150], ['summon', 125],
  ['ultimate', 305], ['aspect', 335], ['holy', 0], ['spirit', 25], ['extra', 55],
  ['advanced', 250], ['resist', 290], ['combat', 90],
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

// ── Árvores raciais (mapa próprio de cada raça) ──
// Formato de ÁRVORE: a raiz da raça no alto à esquerda; um TRONCO desce pela coluna 0 e solta um
// GALHO para cada categoria. Dentro da categoria cada habilidade fica à direita do seu pré-requisito
// (2 colunas por nível); o 1º filho na mesma linha e os outros nas linhas de baixo, ligados por um
// cotovelo (desce e vira). Cabe em 13 colunas e é percorrida arrastando para cima/baixo.
// Pré-requisito de OUTRA categoria vira nó vinculado ("id@categoria"), como nas árvores universais.
const racial = {};
const racialLinks = {};
const racialPlan = {};
let reserved = { minX: 0, maxX: 0, minY: 1, maxY: 1 };
for (const lin in LINEAGES) {
  const tree = TREES[`race.${lin}`];
  const root = tree.root;
  const secOf = (id) => SKILLS[id].sector ?? '_';
  const ids = tree.skills.map((s) => s.id).filter((id) => id !== root);
  const inTree = new Set(ids);
  const secs = [...new Set(ids.map(secOf))];
  const inSec = (id) => SKILLS[id].requires.filter((r) => inTree.has(r) && secOf(r) === secOf(id));
  const depth = {};
  const dep = (id) => (depth[id] ??= inSec(id).length ? 1 + Math.max(...inSec(id).map(dep)) : 0);
  const kids = {};
  const links = {};
  const tops = {}; // categoria → raízes do galho (na ordem)
  const extra = []; // pré-requisitos secundários da mesma categoria (linhas roteadas)
  for (const id of ids) {
    const sec = secOf(id);
    const same = inSec(id).sort((x, y) => dep(y) - dep(x));
    let parent = same[0];
    if (!parent) {
      const cross = SKILLS[id].requires.find((r) => inTree.has(r));
      if (cross) { parent = `${cross}@${sec}`; if (!links[parent]) { links[parent] = cross; (tops[sec] ??= []).push(parent); } }
    }
    if (parent) (kids[parent] ??= []).push(id); else (tops[sec] ??= []).push(id);
    for (const r of same.slice(1)) extra.push([r, id]);
  }
  const pos = { [root]: [0, 0] };
  const tree_ = [];
  let y = 0;
  // 2 colunas por nível; nos níveis muito fundos (raros) os nós ficam colados para caber na tela.
  const X = (d) => (d <= 5 ? 1 + 2 * d : 11 + (d - 5));
  const place = (n, d) => {
    pos[n] = [X(d), y];
    (kids[n] ?? []).forEach((c, i) => {
      if (i) y++;
      const yc = y;
      place(c, d + 1);
      tree_.push([n, c, pos[n], [X(d + 1), yc]]);
    });
  };
  secs.forEach((sec, si) => {
    if (si) y += 2; // corredor de 1 linha entre categorias
    (tops[sec] ?? []).forEach((t, i) => { if (i) y++; place(t, 0); tree_.push([root, t, [0, 0], pos[t]]); });
  });
  const maxX = Math.max(...Object.values(pos).map((p) => p[0]));
  if (maxX > 12) throw new Error(`árvore racial ${lin} larga demais (${maxX + 1} colunas)`);
  racial[lin] = pos;
  racialLinks[lin] = links;
  racialPlan[lin] = { tree: tree_, extra };
}
// Cada raça tem MAPA PRÓPRIO (abaixo); no mapa principal fica só a raiz da raça, no centro.
reserved = { minX: -3, maxX: 3, minY: 1, maxY: 3 };

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

// Mapas raciais (um por linhagem, coordenadas próprias): galhos em cotovelo (tronco e categorias)
// e, por cima, os pré-requisitos secundários da mesma categoria roteados por A*.
const elbow = ([px, py], [cx, cy]) => {
  const path = [];
  for (let yy = py; yy <= cy; yy++) path.push([px, yy]);
  for (let xx = px + 1; xx <= cx; xx++) path.push([xx, cy]);
  return path;
};
const racialOut = {};
for (const lin in racial) {
  const pos = racial[lin];
  const nodes = new Set(Object.values(pos).map(([x, y]) => key(x, y)));
  const u = new Map();
  const rb = bboxOf(pos);
  const RB = { minX: rb.minX - 1, maxX: Math.max(12, rb.maxX + 1), minY: rb.minY - 1, maxY: rb.maxY + 2 };
  const edges = [];
  for (const [a, b, pa, pb] of racialPlan[lin].tree) {
    const path = elbow(pa, pb);
    for (const [x, y] of path.slice(1, -1)) if (nodes.has(key(x, y))) throw new Error(`galho atravessa nó ${lin}: ${a} → ${b}`);
    addUsage(u, path);
    edges.push([a, b, flat(path)]);
  }
  const route = makeRouter(nodes, () => false, u, RB);
  for (const [a, b] of racialPlan[lin].extra) {
    const path = route(pos[a], pos[b]);
    // Só as curtas: as longas cruzariam os galhos. O requisito continua valendo (e aparece no painel).
    if (!path || path.length > 5) continue;
    addUsage(u, path);
    edges.push([a, b, flat(path)]);
  }
  // Cabeças das categorias (saltos): primeiro nó de cada categoria.
  const heads = {};
  for (const id of Object.keys(pos).sort((x, y) => pos[x][1] - pos[y][1])) {
    const sid = racialLinks[lin][id] ?? id;
    const sec = SKILLS[sid].sector;
    const own = id.includes('@') ? id.split('@')[1] : sec;
    if (own && own !== '_' && !heads[own]) heads[own] = id;
  }
  racialOut[lin] = { pos, links: racialLinks[lin], edges, bounds: RB, heads };
}

// ── Árvores Supremas (tela própria por linhagem): uma coluna por Suprema, sub-habilidades abaixo
// alternando os lados. Sub-habilidade compartilhada aparece como nó vinculado ("id@suprema").
const supremeOut = {};
for (const lin in SUPREME_ORDER) {
  const order = SUPREME_ORDER[lin];
  const pos = {};
  const links = {};
  const roots = {};
  const wanted = [];
  order.forEach((sid, i) => {
    const x0 = i * 6;
    pos[sid] = [x0, 0];
    roots[sid] = [x0, 0];
    SKILLS[sid].subs.forEach((sub, j) => {
      const k = pos[sub] ? `${sub}@${sid}` : sub;
      const p = [x0 + (j % 2 ? 1 : -1), 3 + j * 2];
      if (k !== sub) links[k] = sub;
      pos[k] = p;
      wanted.push([sid, k]);
    });
  });
  // Requisitos entre Supremas (ex.: Azathoth exige Raphael, Beelzebuth, Ciel, Veldora, Velgrynd).
  for (const sid of order) for (const r of SKILLS[sid].requires) if (order.includes(r)) wanted.push([r, sid]);
  const sb = bboxOf(pos);
  const SB = { minX: sb.minX - 3, maxX: sb.maxX + 3, minY: sb.minY - 4, maxY: sb.maxY + 2 };
  const nodes = new Set(Object.values(pos).map(([x, y]) => key(x, y)));
  const u = new Map();
  const route = makeRouter(nodes, () => false, u, SB);
  const edges = [];
  for (const [a, b] of wanted) {
    const path = route(pos[a], pos[b]);
    if (!path) throw new Error(`sem rota suprema ${lin}: ${a} → ${b}`);
    addUsage(u, path);
    edges.push([a, b, flat(path)]);
  }
  supremeOut[lin] = { pos, links, edges, roots, bounds: SB };
}

const data = { version: 1, bounds: B, roots, universal: { pos: uniPos, links: uniLink, edges: uniEdges }, racial: racialOut, supreme: supremeOut };
mkdirSync(dirname(OUT), { recursive: true });
writeFileSync(OUT, `// Gerado por tools/gen-map.mjs — não edite à mão.\nexport const MAP = ${JSON.stringify(data)};\n`);
console.log(`mapa: ${Object.keys(uniPos).length} nós universais, ${uniEdges.length} linhas, área ${B.maxX - B.minX + 1}x${B.maxY - B.minY + 1}`);
