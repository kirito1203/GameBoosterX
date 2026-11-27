// Todas as raças: catálogo, linhagens novas, rank/origem em todas as habilidades, mapas raciais próprios
// e a árvore universal de Combate e Armas.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { SKILLS, TREES, RACES, LINEAGES, UNIVERSAL_TREES, initialSkillsFor } from '../BP/scripts/data/registry.js';
import { SOURCES } from '../BP/scripts/data/dsl.js';
import { CONFIG } from '../BP/scripts/config.js';
import { computeStats } from '../BP/scripts/core/stats.js';
import { evaluate } from '../BP/scripts/core/progression.js';
import { getMap, getRaceMap } from '../BP/scripts/core/map.js';
import { buildTreeForm, viewAt } from '../BP/scripts/core/view.js';
import { CTRL, CATEGORY_ORDER } from '../BP/scripts/core/uiSpec.js';
import { isSelectable } from '../BP/scripts/core/raceView.js';

test('24 linhagens, com as 5 novas (Fada, Homem-Fera, Morto-vivo, Golem, Dragão) e evoluções', () => {
  assert.equal(Object.keys(LINEAGES).length, 24);
  for (const [lin, base, evo] of [['fairy', 'fairy', 'fairy_queen'], ['beastman', 'beastman', 'lycanthrope'], ['undead', 'skeleton', 'wight_king'],
    ['golem', 'golem', 'arch_doll'], ['dragon', 'dragon', 'true_dragon']]) {
    assert.equal(LINEAGES[lin].base, base);
    assert.ok(isSelectable(base), `${base} escolhível`);
    assert.ok(!isSelectable(evo), `${evo} só por evolução`);
    assert.ok(TREES[`race.${lin}`].skills.some((s) => s.evolveTo === evo), `${lin}: evolução para ${evo}`);
    assert.ok(initialSkillsFor(base).length >= 2, `${lin}: habilidades iniciais`);
  }
  // Dragão Verdadeiro jogável: estágio final muito restrito e marcado como criação do addon.
  const td = TREES['race.dragon'].skills.find((s) => s.evolveTo === 'true_dragon');
  assert.ok(td.needFlags.length >= 3 && td.minLevel >= 80);
  assert.equal(RACES.true_dragon.origin, 'original');
});

test('toda habilidade tem rank (F..EX+) e origem; "obra" não verificada fica marcada', () => {
  for (const s of Object.values(SKILLS)) {
    assert.ok(CONFIG.ranks[s.rank], `${s.id} rank ${s.rank}`);
    assert.ok(SOURCES[s.src], `${s.id} origem ${s.src}`);
    if (s.origin === 'obra' && s.tree !== 'race.slime') assert.equal(s.verified, false, `${s.id}: precisa de verificação canônica`);
    if (s.origin === 'original') assert.equal(s.src, 'mod', s.id);
  }
});

test('cada raça tem mapa racial próprio; o mapa universal tem só a raiz da raça no centro', () => {
  for (const lin in LINEAGES) {
    const r = getRaceMap(lin);
    for (const s of TREES[`race.${lin}`].skills) assert.ok(r.pos[s.id], `${lin}: ${s.id}`);
    const u = getMap(lin);
    assert.ok(!Object.keys(u.pos).some((k) => k.startsWith(`r.${lin}.`) && k !== `r.${lin}.root`));
  }
});

test('tela da árvore racial: saltos por categoria e botão das árvores universais', () => {
  const d = { race: 'slime', level: 10, points: 0, unlocked: new Set(initialSkillsFor('slime')), flags: new Set() };
  const m = getRaceMap('slime');
  const f = buildTreeForm({ state: d, view: viewAt([0, 0]), mp: { cur: 1, max: 1 }, race: true });
  assert.ok(f.title.includes('Árvore Racial'));
  assert.deepEqual(f.hit[CTRL.cat0 + CATEGORY_ORDER.length - 1], { type: 'universal' });
  assert.equal(f.hit[CTRL.cat0].type, 'jump');
  assert.ok(Object.keys(m.roots).length >= 12, 'raiz + 12 categorias do Slime');
  const g = buildTreeForm({ state: d, view: viewAt([0, 0]), mp: { cur: 1, max: 1 } });
  assert.ok(g.title.includes('Árvores Universais'));
});

test('Combate e Armas: árvore universal com efeitos reais (esquiva, contra-ataque, investida)', () => {
  const t = TREES.combat;
  assert.ok(UNIVERSAL_TREES.includes(t));
  assert.ok(t.skills.length >= 30);
  for (const s of t.skills) assert.equal(s.src, 'mod');
  const d = { race: 'human', level: 50, unlocked: new Set(['r.human.root', 'cbt.dodge', 'cbt.battle_sense', 'cbt.counter', 'cbt.archery']) };
  const st = computeStats(d);
  assert.ok(Math.abs(st.dodge - 0.18) < 1e-9);
  assert.deepEqual(st.counter, { chance: 0.25, mult: 0.5 });
  assert.equal(st.power.projectile, 1.2);
  assert.equal(SKILLS['cbt.charge'].fx.k, 'dash');
  // Aberta a todas as raças, com pré-requisitos.
  for (const race of ['slime', 'fairy', 'dragon', 'skeleton']) {
    const ev = evaluate('cbt.strength', { race, level: 10, points: 999, unlocked: new Set(['cbt.training']), flags: new Set() });
    assert.ok(ev.ok, race);
  }
  assert.equal(evaluate('cbt.whirlwind', { race: 'human', level: 99, points: 999, unlocked: new Set(['cbt.training']), flags: new Set() }).ok, false);
});

test('todas as peças de linha dos mapas raciais existem no resource pack', async () => {
  const { existsSync } = await import('node:fs');
  for (const lin in LINEAGES) {
    for (const st of getRaceMap(lin).stubs.values()) {
      const code = st.map((dirs) => (dirs.length ? '1' : '0')).join('');
      assert.ok(existsSync(new URL(`../RP/textures/tensura/ui/line/${code}.png`, import.meta.url)), `${lin}: linha ${code}`);
    }
  }
});

test('árvore racial em formato de árvore: todo nó ligado por uma linha, cabe em 13 colunas, vínculos válidos', () => {
  for (const lin in LINEAGES) {
    const r = getRaceMap(lin);
    const root = `r.${lin}.root`;
    assert.deepEqual(r.pos[root], [0, 0], `${lin}: raiz no topo`);
    const into = new Set(r.edges.map((e) => e.to));
    for (const k in r.pos) {
      if (k !== root) assert.ok(into.has(k), `${lin}: ${k} solto (sem linha chegando)`);
      assert.ok(r.pos[k][0] >= 0 && r.pos[k][0] <= 12, `${lin}: ${k} fora das 13 colunas`);
    }
    for (const k of r.isLink) {
      const [sid, sec] = k.split('@');
      assert.equal(r.skillOf[k], sid);
      assert.ok(TREES[`race.${lin}`].skills.some((s) => s.id === sid), `${lin}: vínculo para fora da raça ${k}`);
      assert.ok(TREES[`race.${lin}`].skills.some((s) => s.sector === sec && s.requires.includes(sid)), `${lin}: vínculo sem uso ${k}`);
    }
  }
});
