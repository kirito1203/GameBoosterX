// Árvore em mapa único, menu principal e o JSON UI gerado.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
import { TREES, SKILLS, LINEAGES, UNIVERSAL_TREES, initialSkillsFor } from '../BP/scripts/data/registry.js';
import { getMap, DIRS8 } from '../BP/scripts/core/map.js';
import { computeStats } from '../BP/scripts/core/stats.js';
import { buildTreeForm, centeredView, viewAt, pan, rezoom, ensureVisible, detailsCoveredFrom } from '../BP/scripts/core/view.js';
import { buildMenuForm, evolutionProgress } from '../BP/scripts/core/menuView.js';
import { CTRL, CTRL_COUNT, CAT_COUNT, ZOOMS, gridOf, layerBase, allFrameCodes, MARK, MENU_MARK, MENU, MENU_COUNT, BAR_SLOTS } from '../BP/scripts/core/uiSpec.js';
import { previewHtml, scenario, modelFor } from '../tools/preview.mjs';

const RP = new URL('../RP/', import.meta.url);

test('mapa: cada habilidade aparece uma única vez (por linhagem) e nada se sobrepõe', () => {
  for (const lin in LINEAGES) {
    const m = getMap(lin);
    assert.equal(m.nodeAt.size, Object.keys(m.pos).length, `${lin}: nós sobrepostos`);
    const expected = new Set([...UNIVERSAL_TREES.flatMap((t) => t.skills.map((s) => s.id)), ...TREES[`race.${lin}`].skills.map((s) => s.id)]);
    const own = Object.keys(m.pos).filter((k) => !m.isLink.has(k));
    assert.equal(own.length, expected.size, `${lin}: ${own.length} nós para ${expected.size} habilidades`);
    for (const k of own) assert.ok(expected.has(k), `${lin}: nó estranho ${k}`);
    assert.deepEqual(m.pos[m.centerId], [0, 0], 'a raça fica no centro');
  }
});

test('mapa: toda linha liga dois nós, em passos de 8 direções, sem atravessar outros nós', () => {
  for (const lin of ['slime', 'human', 'ogre', 'demon']) {
    const m = getMap(lin);
    for (const e of m.edges) {
      assert.deepEqual(e.path[0], m.pos[e.from], `${lin}: ${e.from}→${e.to} não começa no nó`);
      assert.deepEqual(e.path.at(-1), m.pos[e.to], `${lin}: ${e.from}→${e.to} não termina no nó`);
      for (let i = 1; i < e.path.length; i++) {
        const [x1, y1] = e.path[i - 1];
        const [x2, y2] = e.path[i];
        assert.ok(Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)) === 1, `${lin}: salto em ${e.from}→${e.to}`);
        if (i < e.path.length - 1) assert.ok(!m.nodeAt.has(`${x2},${y2}`), `${lin}: ${e.from}→${e.to} atravessa ${m.nodeAt.get(`${x2},${y2}`)}`);
      }
    }
  }
});

test('mapa: as linhas são os pré-requisitos reais (ou a ligação da raça com cada categoria)', () => {
  for (const lin of Object.keys(LINEAGES)) {
    const m = getMap(lin);
    const set = new Set(m.edges.map((e) => `${e.from}>${e.to}`));
    for (const k of Object.keys(m.pos)) {
      if (m.isLink.has(k)) continue;
      const s = SKILLS[k];
      for (const r of s.requires) {
        const ok = set.has(`${r}>${k}`) || set.has(`${r}@${s.tree}>${k}`);
        assert.ok(ok, `${lin}: falta linha ${r} → ${k}`);
      }
    }
    for (const e of m.edges) {
      if (e.from === m.centerId && TREES[e.toSkill]?.root === e.toSkill) continue;
      const isRoot = UNIVERSAL_TREES.some((t) => t.root === e.to);
      if (e.from === m.centerId && isRoot) continue;
      assert.ok(SKILLS[e.toSkill].requires.includes(e.fromSkill), `${lin}: linha sem pré-requisito ${e.from} → ${e.to}`);
    }
    for (const t of UNIVERSAL_TREES) assert.ok(set.has(`${m.centerId}>${t.root}`), `${lin}: categoria ${t.id} sem ligação`);
  }
});

test('mapa: tocos simétricos (as linhas continuam de uma célula para a outra)', () => {
  const m = getMap('slime');
  for (const [k, st] of m.stubs) {
    const [x, y] = k.split(',').map(Number);
    for (let d = 0; d < 8; d++) {
      if (!st[d].length) continue;
      const nb = m.stubs.get(`${x + DIRS8[d][0]},${y + DIRS8[d][1]}`);
      assert.ok(nb && nb[(d + 4) % 8].length, `ponta solta em ${k} (${d})`);
    }
  }
});

function demo() {
  const unlocked = new Set([...initialSkillsFor('slime'), 'elem.core', 'elem.fire', 'uni.soul']);
  const d = { race: 'slime', level: 20, points: 100, flags: new Set(), unlocked, mp: 100, aura: 50, epTotal: 1250, ep: 10, mpBonus: 0,
    equipped: ['r.slime.absorb'], absorbed: [], kills: {}, mimic: null };
  d.stats = computeStats(d);
  const state = { race: d.race, level: d.level, points: d.points, unlocked: d.unlocked, flags: d.flags };
  return { d, state };
}

test('formulário da árvore: botões, marcador, categorias e índice ↔ posição', () => {
  const { state } = demo();
  const m = getMap('slime');
  for (const z of ZOOMS) {
    for (const v of [centeredView(z), viewAt(m.roots.elemental, z), viewAt(m.roots.ultimate, z)]) {
      const f = buildTreeForm({ state, view: v, selected: null, mp: { cur: 1, max: 1 } });
      assert.equal(f.buttons.length, layerBase(z).total);
      assert.ok(f.title.startsWith(`${MARK}§${z}`));
      assert.equal(Object.values(f.hit).filter((h) => h.type === 'jump').length, CAT_COUNT);
      const { cols } = gridOf(z);
      const base = layerBase(z);
      for (const [idx, h] of Object.entries(f.hit)) {
        if (h.type !== 'node') continue;
        const i = Number(idx) - base.node;
        assert.deepEqual([v.x + (i % cols), v.y + Math.floor(i / cols)], m.pos[h.key]);
      }
    }
  }
});

test('todas as texturas usadas existem (ícones, molduras, peças de linha, botões)', () => {
  const frames = new Set(allFrameCodes());
  const { state } = demo();
  const m = getMap('slime');
  const keys = Object.keys(m.pos);
  for (let k = 0; k < keys.length; k += 3) {
    const f = buildTreeForm({ state, view: ensureVisible(centeredView(3), m.pos[keys[k]]), selected: keys[k], mp: { cur: 1, max: 1 } });
    const base = layerBase(3);
    f.buttons.forEach((b, i) => {
      if (b.icon) assert.ok(existsSync(new URL(`${b.icon}.png`, RP)), `textura ausente: ${b.icon}`);
      if (i >= base.node && i < base.pipe && b.text) assert.ok(frames.has(b.text), `moldura ${b.text}`);
      if (i >= base.pipe && i < base.pipe + gridOf(3).n && b.text) assert.ok(existsSync(new URL(`textures/tensura/ui/line/${b.text}.png`, RP)), `linha ${b.text}`);
    });
  }
  for (const tab of ['status', 'race', 'evolution', 'records']) {
    const { d, state: s } = demo();
    for (const b of buildMenuForm({ tab, d, state: s }).buttons) if (b.icon) assert.ok(existsSync(new URL(`${b.icon}.png`, RP)), `textura ausente: ${b.icon}`);
  }
});

test('navegação: setas nas 4 direções, zoom mantendo o centro, centralizar e limites do mapa', () => {
  const m = getMap('slime');
  const v = centeredView(2);
  assert.ok(pan(v, 1, 0, m).x > v.x && pan(v, -1, 0, m).x < v.x && pan(v, 0, 1, m).y > v.y && pan(v, 0, -1, m).y < v.y);
  const center = (w) => [w.x + Math.floor(gridOf(w.zoom).cols / 2), w.y + Math.floor(gridOf(w.zoom).rows / 2)];
  assert.deepEqual(center(rezoom(v, 1)), center(v));
  assert.deepEqual(center(rezoom(v, 3)), center(v));
  assert.equal(rezoom(v, 99).zoom, ZOOMS.at(-1));
  let far = v;
  for (let i = 0; i < 100; i++) far = pan(far, 1, 1, m);
  assert.ok(center(far)[0] <= m.bounds.maxX && center(far)[1] <= m.bounds.maxY);
});

test('detalhes: nada clicável sob o painel; desbloquear só quando permitido; nó compartilhado leva ao original', () => {
  const { state } = demo();
  const m = getMap('slime');
  const v = viewAt(m.roots.elemental, 1);
  const f = buildTreeForm({ state, view: v, selected: 'elem.fire', mp: { cur: 1, max: 1 } });
  const { cols } = gridOf(1);
  for (const [idx, h] of Object.entries(f.hit)) if (h.type === 'node') assert.ok((Number(idx) - layerBase(1).node) % cols < detailsCoveredFrom(cols));
  assert.equal(buildTreeForm({ state, view: v, selected: 'elem.fireball', mp: { cur: 1, max: 1 } }).hit[CTRL.unlock]?.type, 'unlock');
  const no = buildTreeForm({ state, view: v, selected: 'elem.firestorm', mp: { cur: 1, max: 1 } });
  assert.equal(no.hit[CTRL.unlock], undefined);
  assert.ok(no.body.includes('O que falta'));
  const link = Object.keys(m.pos).find((k) => m.isLink.has(k));
  const lf = buildTreeForm({ state, view: ensureVisible(v, m.pos[link]), selected: link, mp: { cur: 1, max: 1 } });
  assert.equal(lf.hit[CTRL.gotoLink]?.type, 'goto');
  assert.ok(m.pos[lf.hit[CTRL.gotoLink].key], 'o original existe no mapa');
});

test('menu: Status mostra EP, Aura, Magículas, nível, progresso, habilidades e a barra de 5 espaços', () => {
  const { d, state } = demo();
  const f = buildMenuForm({ tab: 'status', d, state });
  assert.equal(f.buttons.length, MENU_COUNT);
  assert.ok(f.title.startsWith(`${MENU_MARK}§1`));
  assert.ok(f.buttons[MENU.stat0].text.includes('1.250'));
  assert.ok(f.buttons[MENU.stat0 + 1].text.includes(`/ ${d.stats.maxAura}`));
  assert.ok(f.buttons[MENU.stat0 + 2].text.includes(`/ ${d.stats.maxMp}`));
  assert.ok(f.buttons[MENU.stat0 + 3].text.includes('20'));
  assert.match(f.buttons[MENU.stat0 + 4].text, /\d+%/);
  assert.match(f.buttons[MENU.progressBar].icon, /bar_\d+$/);
  assert.equal(f.buttons[MENU.raceName].text, '§bSlime');
  for (let i = 0; i < BAR_SLOTS; i++) assert.equal(f.hit[MENU.slot0 + i].type, 'slot');
  assert.ok(f.buttons[MENU.active0].text.length > 2);
  assert.equal(f.hit[MENU.tab0 + 2].tab, 'skills', 'aba Habilidades abre a árvore');
  const p = evolutionProgress(d, state);
  assert.ok(p >= 0 && p <= 1);
});

test('menu: aba Evolução oferece "Evoluir" quando os requisitos são atendidos', () => {
  const { d, state } = demo();
  for (const id of TREES['race.slime'].skills.map((s) => s.id).filter((id) => !id.includes('evo_'))) d.unlocked.add(id);
  d.level = 40; d.points = 999; state.level = 40; state.points = 999;
  const f = buildMenuForm({ tab: 'evolution', d, state });
  assert.ok(Object.values(f.hit).some((h) => h.type === 'unlock' && h.id === 'r.slime.evo_demon_slime'));
  assert.ok(f.body.includes('Demon Slime'));
});

test('JSON UI gerado: cada célula fica na posição cujo índice (linha*colunas+coluna) é o do botão certo', () => {
  const ui = JSON.parse(readFileSync(new URL('ui/tensura_tree.json', RP), 'utf8').replace(/^\/\/.*$/m, ''));
  assert.ok(!JSON.stringify(ui).includes('"collection_index"'), 'collection_index não funciona em formulários (visto no jogo)');
  for (const z of ZOOMS) {
    const { cols, rows, n } = gridOf(z);
    const base = layerBase(z);
    assert.equal(base.node % cols, 0, 'camadas começam no início de uma linha');
    const panel = ui[`grid_z${z}`];
    const grids = Object.fromEntries(panel.controls.map((c) => { const [k] = Object.keys(c); return [k, c[k]]; }));
    for (const [gname, start] of [['grid_n', base.node], ['grid_p', base.pipe]]) {
      const g = grids[gname];
      assert.equal(g.grid_dimensions[0], cols);
      const seen = new Set();
      for (const c of g.controls) {
        const def = Object.values(c)[0];
        const [gx, gy] = def.grid_position;
        const idx = gy * cols + gx;
        assert.ok(idx >= start && idx < start + n, `${gname} z${z}: índice ${idx} fora da camada`);
        seen.add(idx);
      }
      assert.equal(seen.size, n);
      // a primeira linha visível da grade coincide com o topo da área da árvore
      assert.ok(Math.abs(parseFloat(g.offset[1]) + (start / cols / rows) * 100) < 0.01);
    }
  }
  // botões fixos: grade de 1 linha com a coluna = índice
  const close = JSON.stringify(ui.menu_root);
  assert.ok(close.includes('"grid_position":[0,0]') || close.includes('"grid_position": [0, 0]') || /grid_position":\[0,0\]/.test(close.replace(/\s/g, '')));
  assert.ok(ui.menu_root, 'tela do menu existe');
  const sf = JSON.parse(readFileSync(new URL('ui/server_form.json', RP), 'utf8').replace(/^\/\/.*$/m, ''));
  const names = sf.tensura_long_form_router.controls.map((c) => Object.keys(c)[0]);
  assert.deepEqual(names, ['vanilla_wrap', 'tensura_tree_wrap', 'tensura_menu_wrap']);
  assert.equal(sf.main_screen_content.controls[0].server_form_factory.control_ids.custom_form, '@server_form.custom_form');
});

test('simulação do JSON UI: nós quadrados alinhados com as linhas; menu com todos os campos', () => {
  for (const name of ['tree-z1', 'tree-z2-sel', 'tree-z3', 'tree-z1-pan']) {
    const sc = scenario(name);
    const model = modelFor(sc);
    const { stats } = previewHtml(model);
    assert.equal(stats.nodes, model.visibleNodes, name);
    const base = layerBase(sc.p.view.zoom);
    for (const [idx, r] of stats.rects.node) {
      assert.ok(Math.abs(r.w - r.h) < 0.01, `${name}: nó não quadrado`);
      const pr = stats.rects.pipe.get(base.pipe + (idx - base.node));
      if (pr) assert.ok(Math.abs(r.x + r.w / 2 - (pr.x + pr.w / 2)) < 0.01 && Math.abs(r.y + r.h / 2 - (pr.y + pr.h / 2)) < 0.01, `${name}: desalinhado`);
    }
  }
  const sc = scenario('menu-status');
  const { html } = { html: previewHtml(modelFor(sc), { root: 'menu_root' }).page };
  for (const s of ['EP:', 'Aura:', 'Magículas:', 'Nível:', 'Progresso de evolução:', 'Habilidades Ativas', 'Barra de Habilidades', 'live_player_renderer', 'Voltar',
    // valores vindos de botões diferentes (o bug visto no jogo mostrava o botão 0 em todos)
    '1.250', 'STATUS', 'RAÇA', 'HABILIDADES', 'EVOLUÇÃO', 'REGISTROS', 'Predador', 'Grande Sábio', 'Slime', 'bar_']) {
    assert.ok(html.includes(s), `menu sem "${s}"`);
  }
  // nenhum botão do menu pode mostrar o ícone de fechar, exceto o próprio fechar
  assert.equal((html.match(/menu\/close\.png/g) ?? []).length, 1, 'ícone de fechar repetido (todos lendo o botão 0)');
  {
  }
});

test('desempenho: montar a árvore e o menu é rápido', () => {
  const { d, state } = demo();
  const m = getMap('slime');
  const t0 = performance.now();
  let n = 0;
  for (const z of ZOOMS) for (const cat of Object.keys(m.roots)) { buildTreeForm({ state, view: viewAt(m.roots[cat], z), selected: null, mp: { cur: 1, max: 1 } }); n++; }
  for (let i = 0; i < 10; i++) { buildMenuForm({ tab: 'status', d, state }); n++; }
  const per = (performance.now() - t0) / n;
  assert.ok(per < 20, `${per.toFixed(2)} ms por tela`);
});
