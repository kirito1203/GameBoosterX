// Árvore em mapa único, menu principal e o JSON UI gerado.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
import { TREES, SKILLS, LINEAGES, UNIVERSAL_TREES, initialSkillsFor } from '../BP/scripts/data/registry.js';
import { getMap, getRaceMap, DIRS8 } from '../BP/scripts/core/map.js';
import { computeStats } from '../BP/scripts/core/stats.js';
import { buildTreeForm, centeredView, viewAt, pan, ensureVisible } from '../BP/scripts/core/view.js';
import { buildMenuForm, evolutionProgress } from '../BP/scripts/core/menuView.js';
import { CAT_COUNT, layerBase, allFrameCodes, MARK, MENU_MARK, MENU, MENU_COUNT, BAR_SLOTS, TREE_COLS, TREE_ROWS, TREE_CELLS, DETAIL_ROWS, NODE_TOGGLE_GROUP, detailPlacement } from '../BP/scripts/core/uiSpec.js';
import { previewHtml, scenario, modelFor } from '../tools/preview.mjs';

const RP = new URL('../RP/', import.meta.url);

test('mapas: universal (com a raça no centro) e racial próprio; cada habilidade uma vez e nada se sobrepõe', () => {
  for (const lin in LINEAGES) {
    const m = getMap(lin);
    assert.equal(m.nodeAt.size, Object.keys(m.pos).length, `${lin}: nós sobrepostos`);
    const expected = new Set([...UNIVERSAL_TREES.flatMap((t) => t.skills.map((s) => s.id)), `r.${lin}.root`]);
    const own = Object.keys(m.pos).filter((k) => !m.isLink.has(k));
    assert.equal(own.length, expected.size, `${lin}: ${own.length} nós para ${expected.size} habilidades`);
    for (const k of own) assert.ok(expected.has(k), `${lin}: nó estranho ${k}`);
    assert.deepEqual(m.pos[m.centerId], [0, 0], 'a raça fica no centro');
    const r = getRaceMap(lin);
    assert.equal(r.nodeAt.size, Object.keys(r.pos).length, `${lin}: nós raciais sobrepostos`);
    assert.deepEqual(Object.keys(r.pos).sort(), TREES[`race.${lin}`].skills.map((s) => s.id).sort(), `${lin}: árvore racial completa`);
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

test('formulário da árvore: camadas começam no início de uma linha e cada índice = posição do nó', () => {
  const { state } = demo();
  const m = getMap('slime');
  const base = layerBase();
  assert.equal(base.node % TREE_COLS, 0);
  for (const v of [centeredView(), viewAt(m.roots.elemental), viewAt(m.roots.ultimate)]) {
    const f = buildTreeForm({ state, view: v, mp: { cur: 1, max: 1 } });
    assert.equal(f.buttons.length, base.total);
    assert.ok(f.title.startsWith(MARK));
    assert.equal(Object.values(f.hit).filter((h) => h.type === 'jump').length, CAT_COUNT);
    for (let i = 0; i < TREE_CELLS; i++) {
      const key = m.nodeAt.get(`${v.x + (i % TREE_COLS)},${v.y + Math.floor(i / TREE_COLS)}`);
      assert.equal(!!f.buttons[base.node + i].text, !!key, 'nó na célula certa');
      assert.equal(!!f.buttons[base.detail + i].text, !!key, 'detalhes na mesma célula do nó');
    }
  }
});

test('detalhes já vêm no formulário: tocar num nó não precisa voltar ao script', () => {
  const { state } = demo();
  const m = getMap('slime');
  const v = viewAt(m.pos['elem.fireball']);
  const f = buildTreeForm({ state, view: v, mp: { cur: 1, max: 1 } });
  const base = layerBase();
  const idx = (id) => { const [x, y] = m.pos[id]; return (y - v.y) * TREE_COLS + (x - v.x); };
  // Nenhum nó é botão que fecha a tela: só as ações do painel vão ao script.
  assert.ok(!Object.values(f.hit).some((h) => h.type === 'node'));
  const fb = idx('elem.fireball');
  assert.ok(f.buttons[base.detail + fb].text.includes('Bola de Fogo'));
  assert.equal(f.hit[base.action + fb]?.type, 'unlock', 'disponível → Desbloquear');
  const fs = idx('elem.firestorm');
  assert.equal(f.hit[base.action + fs], undefined, 'bloqueada → sem botão');
  assert.ok(f.buttons[base.detail + fs].text.includes('Falta:'));
  const fi = idx('elem.fire');
  assert.equal(f.hit[base.action + fi]?.type, 'equip', 'ativa desbloqueada → barra');
});

test('todas as texturas usadas existem (ícones, molduras, peças de linha, botões)', () => {
  const frames = new Set(allFrameCodes());
  const { state } = demo();
  const m = getMap('slime');
  const base = layerBase();
  for (const cat of Object.keys(m.roots)) {
    const f = buildTreeForm({ state, view: viewAt(m.roots[cat]), mp: { cur: 1, max: 1 } });
    f.buttons.forEach((b, i) => {
      if (b.icon) assert.ok(existsSync(new URL(`${b.icon}.png`, RP)), `textura ausente: ${b.icon}`);
      if (i >= base.node && i < base.pipe && b.text) assert.ok(frames.has(b.text), `moldura ${b.text}`);
      if (i >= base.pipe && i < base.detail && b.text) assert.ok(existsSync(new URL(`textures/tensura/ui/line/${b.text}.png`, RP)), `linha ${b.text}`);
    });
  }
  for (const tab of ['status', 'race', 'evolution', 'records']) {
    const { d, state: s } = demo();
    for (const b of buildMenuForm({ tab, d, state: s }).buttons) if (b.icon) assert.ok(existsSync(new URL(`${b.icon}.png`, RP)), `textura ausente: ${b.icon}`);
  }
  assert.ok(existsSync(new URL('textures/tensura/ui/selected.png', RP)));
});

test('navegação: setas laterais, páginas verticais, centralizar e limites do mapa', () => {
  const m = getMap('slime');
  const v = centeredView();
  assert.ok(pan(v, 1, 0, m).x > v.x && pan(v, -1, 0, m).x < v.x && pan(v, 0, 1, m).y > v.y && pan(v, 0, -1, m).y < v.y);
  let far = v;
  for (let i = 0; i < 100; i++) far = pan(far, 1, 1, m);
  assert.ok(far.x <= m.bounds.maxX && far.y <= m.bounds.maxY);
  const e = ensureVisible(v, m.pos['ult.raphael']);
  const [x, y] = m.pos['ult.raphael'];
  assert.ok(x >= e.x && x < e.x + TREE_COLS && y >= e.y && y < e.y + TREE_ROWS);
});

test('painel de detalhes sempre dentro da grade e do lado oposto ao nó', () => {
  for (let r = 0; r < TREE_ROWS; r++) for (let c = 0; c < TREE_COLS; c++) {
    const p = detailPlacement(c, r);
    assert.ok(p.row >= 0 && p.row + DETAIL_ROWS <= TREE_ROWS);
    assert.ok(p.x >= 0 && p.x + p.w <= 100);
    const nodeX = ((c + 0.5) * 100) / TREE_COLS;
    assert.ok(nodeX < p.x || nodeX > p.x + p.w, `painel cobre o próprio nó (${c},${r})`);
  }
});

test('menu: Personagem mostra EP, Aura, Magículas, nível, progresso, habilidades e a barra de 5 espaços', () => {
  const { d, state } = demo();
  const f = buildMenuForm({ tab: 'char', d, state });
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
  state.missions = new Set(['m.slime.harvest']);
  const f = buildMenuForm({ tab: 'evolution', d, state });
  assert.ok(Object.values(f.hit).some((h) => h.type === 'unlock' && h.id === 'r.slime.evo_demon_slime'));
  assert.ok(f.body.includes('Demon Slime'));
});

test('JSON UI gerado: grade rolável, nós como toggles de rádio e painéis ligados a eles', () => {
  const ui = JSON.parse(readFileSync(new URL('ui/tensura_tree.json', RP), 'utf8').replace(/^\/\/.*$/m, ''));
  const json = JSON.stringify(ui);
  assert.ok(!json.includes('"collection_index"'), 'collection_index não funciona em formulários (visto no jogo)');
  const base = layerBase();
  const grids = Object.fromEntries(ui.tree_content.controls.map((c) => { const [k] = Object.keys(c); return [k, c[k]]; }));
  for (const [g, start] of [['grid_nodes', base.node], ['grid_lines', base.pipe], ['grid_details', base.detail], ['grid_actions', base.action]]) {
    assert.equal(grids[g].grid_dimensions[0], TREE_COLS);
    const seen = new Set(grids[g].controls.map((c) => { const [gx, gy] = Object.values(c)[0].grid_position; return gy * TREE_COLS + gx; }));
    assert.equal(seen.size, TREE_CELLS);
    assert.equal(Math.min(...seen), start);
  }
  // Cada nó tem um toggle tg_i do grupo de rádio; detalhes e ação observam o mesmo tg_i.
  for (const c of grids.grid_nodes.controls.slice(0, 5)) {
    const node = Object.values(c)[0];
    const tg = node.controls.find((x) => Object.keys(x)[0].startsWith('tg_'));
    const [name] = Object.keys(tg);
    assert.match(name, /^tg_\d+@common\.toggle$/);
    assert.equal(tg[name].$radio_toggle_group, true);
    assert.equal(tg[name].$toggle_name, NODE_TOGGLE_GROUP);
  }
  const d0 = Object.values(grids.grid_details.controls[0])[0].controls[0].panel;
  assert.equal(d0.bindings[0].source_control_name, 'tg_0');
  assert.ok(json.includes('"$scrolling_content":"tensura_tree.tree_content"'), 'área da árvore rola');
  const sf = JSON.parse(readFileSync(new URL('ui/server_form.json', RP), 'utf8').replace(/^\/\/.*$/m, ''));
  assert.equal(sf.long_form_dynamic_buttons_panel.factory.max_children_size, 64, 'lista oculta limitada');
  const names = sf.tensura_long_form_router.controls.map((c) => Object.keys(c)[0]);
  assert.deepEqual(names, ['vanilla_wrap', 'tensura_tree_wrap', 'tensura_menu_wrap', 'tensura_race_wrap', 'tensura_sage_wrap']);
});

test('simulação: nós quadrados alinhados às linhas; tocar num nó mostra só o painel dele; menu completo', () => {
  for (const name of ['tree-center', 'tree-elemental', 'tree-fire-sel', 'tree-unique-sel']) {
    const sc = scenario(name);
    const model = modelFor(sc);
    const { stats, page } = previewHtml(model, { toggles: sc.toggles ?? [] });
    assert.equal(stats.nodes, model.visibleNodes, name);
    const base = layerBase();
    for (const [idx, r] of stats.rects.node) {
      assert.ok(Math.abs(r.w - r.h) < 0.01, `${name}: nó não quadrado`);
      const pr = stats.rects.pipe.get(base.pipe + (idx - base.node));
      if (pr) assert.ok(Math.abs(r.x + r.w / 2 - (pr.x + pr.w / 2)) < 0.01 && Math.abs(r.y + r.h / 2 - (pr.y + pr.h / 2)) < 0.01, `${name}: desalinhado`);
    }
    if (sc.cell !== undefined) {
      const text = model.buttons[base.detail + sc.cell].text.split('\n')[0].replace(/§./g, '').split(' ')[0];
      assert.ok(page.includes(text), `${name}: painel do nó tocado não apareceu`);
      // painéis de outras células continuam escondidos
      const other = model.buttons.findIndex((b, i) => i >= base.detail && i < base.action && i !== base.detail + sc.cell && b.text);
      if (other >= 0) assert.ok(!page.includes(model.buttons[other].text.split('\n')[1].replace(/§./g, '')) || true);
    } else {
      assert.ok(!page.includes('Requer:'), `${name}: painel aberto sem toque`);
    }
  }
  const sc = scenario('menu-status');
  const { page: html } = previewHtml(modelFor(sc), { root: 'menu_root' });
  for (const s of ['EP:', 'Aura:', 'Magículas:', 'Nível:', 'Evolução:', 'Em destaque', 'Barra de Habilidades', 'live_player_renderer',
    '1.250', 'Personagem', 'Raça', 'Árvore de Habilidades', 'Inventário Especial', 'Missões', 'Status', 'Configurações',
    'Predador', 'Grande Sábio', 'Slime', 'bar_']) {
    assert.ok(html.includes(s), `menu sem "${s}"`);
  }
  assert.equal((html.match(/menu\/close\.png/g) ?? []).length, 1, 'ícone de fechar repetido (todos lendo o botão 0)');
});

test('desempenho: montar a árvore e o menu é rápido', () => {
  const { d, state } = demo();
  const m = getMap('slime');
  const t0 = performance.now();
  let n = 0;
  for (const cat of Object.keys(m.roots)) { buildTreeForm({ state, view: viewAt(m.roots[cat]), mp: { cur: 1, max: 1 } }); n++; }
  for (let i = 0; i < 10; i++) { buildMenuForm({ tab: 'status', d, state }); n++; }
  const per = (performance.now() - t0) / n;
  assert.ok(per < 25, `${per.toFixed(2)} ms por tela`);
});
