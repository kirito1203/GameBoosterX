// Árvore visual: layout, conexões, codificação do formulário e o JSON UI gerado.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
import { TREES, SKILLS, initialSkillsFor } from '../BP/scripts/data/registry.js';
import { getLayout, DIRS } from '../BP/scripts/core/layout.js';
import { buildTreeForm, centeredView, pan, rezoom, ensureVisible, detailsCoveredFrom } from '../BP/scripts/core/view.js';
import { CTRL, CTRL_COUNT, ZOOMS, gridOf, layerBase, allFrameCodes, allPipeCodes, MARK, TAB_COUNT } from '../BP/scripts/core/uiSpec.js';
import { previewHtml, scenario } from '../tools/preview.mjs';

const RP = new URL('../RP/', import.meta.url);
const OPP = { n: 's', s: 'n', e: 'w', w: 'e' };
const DV = { n: [0, -1], e: [1, 0], s: [0, 1], w: [-1, 0] };

test('nenhuma árvore tem nós sobrepostos e todo nó tem posição', () => {
  for (const t of Object.values(TREES)) {
    const L = getLayout(t);
    for (const id of L.ids) assert.ok(L.pos[id], `${t.id}: ${id} sem posição`);
    assert.equal(L.nodeAt.size, L.ids.length, `${t.id}: sobreposição`);
  }
});

test('as linhas correspondem aos pré-requisitos reais e encostam nos nós', () => {
  for (const t of Object.values(TREES)) {
    const L = getLayout(t);
    const inTree = new Set(L.ids);
    const edgeSet = new Set(L.edges.map((e) => `${e.from}>${e.to}`));
    for (const id of L.ids) {
      for (const r of SKILLS[id].requires) if (inTree.has(r)) assert.ok(edgeSet.has(`${r}>${id}`), `${t.id}: falta linha ${r} → ${id}`);
    }
    for (const e of L.edges) {
      assert.ok(SKILLS[e.to].requires.includes(e.from), `${t.id}: linha sem pré-requisito real ${e.from} → ${e.to}`);
      assert.deepEqual(e.path[0], L.pos[e.from], `${t.id}: linha não começa no nó ${e.from}`);
      assert.deepEqual(e.path.at(-1), L.pos[e.to], `${t.id}: linha não termina no nó ${e.to}`);
      for (let i = 1; i < e.path.length; i++) {
        const [x1, y1] = e.path[i - 1];
        const [x2, y2] = e.path[i];
        assert.equal(Math.abs(x2 - x1) + Math.abs(y2 - y1), 1, `${t.id}: linha com salto`);
        if (i < e.path.length - 1) assert.ok(!L.nodeAt.has(`${x2},${y2}`), `${t.id}: linha ${e.from}→${e.to} atravessa o nó ${L.nodeAt.get(`${x2},${y2}`)}`);
      }
    }
  }
});

test('tocos de linha são simétricos entre células vizinhas (sem pontas soltas)', () => {
  for (const t of Object.values(TREES)) {
    const L = getLayout(t);
    for (const [k, st] of L.stubs) {
      const [x, y] = k.split(',').map(Number);
      for (const d of DIRS) {
        if (!st[d].length) continue;
        const nb = L.stubs.get(`${x + DV[d][0]},${y + DV[d][1]}`);
        assert.ok(nb && nb[OPP[d]].length, `${t.id}: ponta solta em ${k} (${d})`);
      }
    }
  }
});

test('árvores ramificam em várias direções', () => {
  for (const t of Object.values(TREES)) {
    const L = getLayout(t);
    const b = L.bounds;
    assert.ok(b.maxX - b.minX >= 2 && b.maxY - b.minY >= 2, `${t.id} é uma linha reta (${b.maxX - b.minX + 1}x${b.maxY - b.minY + 1})`);
  }
});

function st() {
  return { race: 'slime', level: 20, points: 100, flags: new Set(), unlocked: new Set([...initialSkillsFor('slime'), 'elem.core', 'elem.fire']) };
}

test('formulário: quantidade de botões, marcador, controles e índices de célula corretos', () => {
  for (const z of ZOOMS) {
    const view = centeredView(z);
    const m = buildTreeForm({ state: st(), tab: 'elemental', view, selected: null, mp: { cur: 10, max: 100 } });
    assert.equal(m.buttons.length, layerBase(z).total);
    assert.ok(m.title.startsWith(`${MARK}§${z}`));
    assert.equal(Object.values(m.hit).filter((h) => h.type === 'tab').length, TAB_COUNT);
    const { cols } = gridOf(z);
    const base = layerBase(z);
    const L = getLayout(TREES.elemental);
    for (const [idx, h] of Object.entries(m.hit)) {
      if (h.type !== 'node') continue;
      const i = Number(idx) - base.node;
      const c = i % cols;
      const r = Math.floor(i / cols);
      assert.deepEqual([view.x + c, view.y + r], L.pos[h.id], `índice ${idx} não corresponde à posição de ${h.id}`);
      const pipeCode = m.buttons[base.pipe + i].text;
      if (L.stubs.has(L.pos[h.id].join(','))) assert.ok(pipeCode.length === 4, 'nó com linhas precisa de código de linha');
    }
    assert.ok(m.visibleNodes > 0);
  }
});

test('todas as texturas usadas pelo formulário existem (ícones, molduras, linhas, botões)', () => {
  const frames = new Set(allFrameCodes());
  const pipes = new Set(allPipeCodes());
  for (const t of Object.values(TREES)) {
    const L = getLayout(t);
    const tab = t.kind === 'racial' ? 'racial' : t.id;
    const race = t.kind === 'racial' ? Object.keys(TREES).length && t.skills[0].initial : 'slime';
    const state = { race, level: 30, points: 50, flags: new Set(), unlocked: new Set([t.root]) };
    for (const id of L.ids) {
      const v = ensureVisible(centeredView(3), L.pos[id]);
      const m = buildTreeForm({ state, tab, view: v, selected: id, mp: { cur: 1, max: 1 } });
      const base = layerBase(3);
      for (let i = 0; i < m.buttons.length; i++) {
        const b = m.buttons[i];
        if (b.icon) assert.ok(existsSync(new URL(`${b.icon}.png`, RP)), `textura ausente: ${b.icon}`);
        if (i >= base.node && i < base.pipe && b.text) assert.ok(frames.has(b.text), `moldura inválida ${b.text}`);
        if (i >= base.pipe && i < base.pipe + gridOf(3).n && b.text) assert.ok(pipes.has(b.text), `linha inválida ${b.text}`);
      }
    }
  }
});

test('navegação: arrastar (setas) nas 4 direções, zoom e centralizar', () => {
  const L = getLayout(TREES.elemental);
  const v = centeredView(2);
  assert.ok(pan(v, 1, 0, L).x > v.x);
  assert.ok(pan(v, -1, 0, L).x < v.x);
  assert.ok(pan(v, 0, 1, L).y > v.y);
  assert.ok(pan(v, 0, -1, L).y < v.y);
  const z1 = rezoom(v, 1);
  const z3 = rezoom(v, 3);
  assert.equal(z1.zoom, 1);
  assert.equal(z3.zoom, 3);
  assert.equal(rezoom(z3, 9).zoom, 3, 'zoom limitado');
  // o centro permanece o mesmo após o zoom
  const center = (w) => [w.x + Math.floor(gridOf(w.zoom).cols / 2), w.y + Math.floor(gridOf(w.zoom).rows / 2)];
  assert.deepEqual(center(z1), center(v));
  assert.deepEqual(center(z3), center(v));
  // panorâmica não foge infinitamente da árvore
  let far = v;
  for (let i = 0; i < 50; i++) far = pan(far, 1, 0, L);
  assert.ok(far.x <= L.bounds.maxX);
});

test('com detalhes abertos, nada fica clicável por baixo do painel', () => {
  const v = centeredView(1);
  const m = buildTreeForm({ state: st(), tab: 'elemental', view: v, selected: 'elem.fire', mp: { cur: 1, max: 1 } });
  const { cols } = gridOf(1);
  const from = detailsCoveredFrom(cols);
  for (const [idx, h] of Object.entries(m.hit)) {
    if (h.type !== 'node') continue;
    const c = (Number(idx) - CTRL_COUNT) % cols;
    assert.ok(c < from, 'nó clicável sob o painel');
  }
  assert.ok(m.body.includes('Fogo'));
  assert.equal(m.hit[CTRL.closeDetails].type, 'deselect');
});

test('botão de desbloqueio só aparece quando os requisitos são atendidos', () => {
  const s = st();
  const ok = buildTreeForm({ state: s, tab: 'elemental', view: centeredView(2), selected: 'elem.fireball', mp: { cur: 1, max: 1 } });
  assert.equal(ok.hit[CTRL.unlock]?.type, 'unlock');
  const no = buildTreeForm({ state: s, tab: 'elemental', view: centeredView(2), selected: 'elem.firestorm', mp: { cur: 1, max: 1 } });
  assert.equal(no.hit[CTRL.unlock], undefined);
  assert.ok(no.body.includes('O que falta'));
});

test('JSON UI gerado: cada índice de célula está na posição certa e cobre a grade inteira', () => {
  const ui = JSON.parse(readFileSync(new URL('ui/tensura_tree.json', RP), 'utf8').replace(/^\/\/.*$/m, ''));
  for (const z of ZOOMS) {
    const { cols, rows, n } = gridOf(z);
    const base = layerBase(z);
    const grid = ui[`grid_z${z}`];
    const seen = { node: new Set(), pipe: new Set(), label: new Set() };
    for (const ctrl of grid.controls) {
      const [k] = Object.keys(ctrl);
      const def = ctrl[k];
      const i = Number(k.slice(1).split('@')[0]);
      const c = i % cols;
      const r = Math.floor(i / cols);
      const ox = parseFloat(def.offset[0]);
      const oy = parseFloat(def.offset[1]);
      assert.ok(Math.abs(ox - (c * 100) / cols) < 0.01 && Math.abs(oy - (r * 100) / rows) < 0.01, `${k} fora de posição`);
      if (k.startsWith('n')) { assert.equal(def.collection_index, base.node + i); seen.node.add(i); }
      if (k.startsWith('p')) { assert.equal(def.collection_index, base.pipe + i); seen.pipe.add(i); }
      if (k.startsWith('c')) { assert.equal(Object.values(def.controls[0])[0].collection_index, base.label + i); seen.label.add(i); }
    }
    assert.equal(seen.node.size, n);
    assert.equal(seen.pipe.size, n);
    if (base.label >= 0) assert.equal(seen.label.size, n);
  }
  const sf = JSON.parse(readFileSync(new URL('ui/server_form.json', RP), 'utf8').replace(/^\/\/.*$/m, ''));
  const factory = sf.main_screen_content.controls[0].server_form_factory;
  assert.equal(factory.control_ids.long_form, '@server_form.tensura_long_form_router');
  assert.equal(factory.control_ids.custom_form, '@server_form.custom_form', 'formulários personalizados do jogo intactos');
});

test('simulação do JSON UI: nós e linhas desenhados nas mesmas células', () => {
  for (const sc of ['elemental-z1', 'elemental-z2-sel', 'racial-z3', 'aspect-z1-pan']) {
    const model = buildTreeForm(scenario(sc));
    const { stats } = previewHtml(model);
    assert.equal(stats.nodes, model.visibleNodes, `${sc}: nós desenhados ≠ nós do formulário`);
    for (const [idx, rect] of stats.rects.node) {
      const i = idx - CTRL_COUNT;
      const pipeRect = stats.rects.pipe.get(layerBase(scenario(sc).view.zoom).pipe + i);
      if (!pipeRect) continue;
      const cx = rect.x + rect.w / 2, cy = rect.y + rect.h / 2;
      const px = pipeRect.x + pipeRect.w / 2, py = pipeRect.y + pipeRect.h / 2;
      assert.ok(Math.abs(cx - px) < 0.01 && Math.abs(cy - py) < 0.01, `${sc}: nó ${idx} desalinhado da linha`);
      assert.ok(Math.abs(rect.w - rect.h) < 0.01, `${sc}: nó não é circular (${rect.w}x${rect.h})`);
    }
  }
});

test('desempenho: montar o formulário de qualquer árvore é rápido', () => {
  const t0 = performance.now();
  let n = 0;
  for (const t of Object.values(TREES)) {
    const tab = t.kind === 'racial' ? 'racial' : t.id;
    const race = t.kind === 'racial' ? t.skills[0].initial : 'slime';
    for (const z of ZOOMS) { buildTreeForm({ state: { race, level: 50, points: 500, flags: new Set(), unlocked: new Set([t.root]) }, tab, view: centeredView(z), selected: t.root, mp: { cur: 1, max: 1 } }); n++; }
  }
  const per = (performance.now() - t0) / n;
  assert.ok(per < 20, `${per.toFixed(2)} ms por formulário`);
});
