// Catálogo de Habilidades: grade por categoria, filtro por raça, marcadores de estado e paginação.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { SKILLS, initialSkillsFor } from '../BP/scripts/data/registry.js';
import { buildCatalogForm, catalogSkills, shortDesc } from '../BP/scripts/core/catalogView.js';
import { raceAllowed } from '../BP/scripts/core/progression.js';
import { CATALOG, CATALOG_CATS, CATALOG_COUNT, CATALOG_PAGE, CATALOG_MARK, TILE_STATE } from '../BP/scripts/core/uiSpec.js';

const stateOf = (race) => ({
  race, level: 10, points: 0, unlocked: new Set(initialSkillsFor(race)), flags: new Set(),
  discovered: new Set(), missions: new Set(), mastery: {}, maxMp: 900, absorbed: new Set(),
});

test('formulário do catálogo: marca, botões fixos e ação em cada item', () => {
  const st = stateOf('slime');
  const f = buildCatalogForm({ cat: 'race', state: st });
  assert.ok(f.title.startsWith(CATALOG_MARK));
  assert.equal(f.buttons.length, CATALOG_COUNT);
  assert.deepEqual(f.hit[CATALOG.close], { type: 'close' });
  assert.deepEqual(f.hit[CATALOG.back], { type: 'back' });
  CATALOG_CATS.forEach((c, i) => assert.deepEqual(f.hit[CATALOG.cat0 + i], { type: 'cat', cat: c }));
  const n = Math.min(CATALOG_PAGE, catalogSkills('race', st).length);
  for (let i = 0; i < n; i++) {
    const b = f.buttons[CATALOG.tile0 + i];
    const h = f.hit[CATALOG.tile0 + i];
    assert.equal(h.type, 'skill');
    assert.ok(SKILLS[h.id]);
    assert.ok(Object.values(TILE_STATE).some((m) => b.text.startsWith(m)), 'marcador de estado');
    assert.ok(b.text.includes(SKILLS[h.id].name));
    assert.ok(b.icon.startsWith('textures/tensura/art/'));
  }
  for (let i = n; i < CATALOG_PAGE; i++) assert.equal(f.hit[CATALOG.tile0 + i], undefined, 'item vazio sem ação');
});

test('itens desbloqueados usam o marcador de desbloqueado', () => {
  const st = stateOf('slime');
  const f = buildCatalogForm({ cat: 'race', state: st });
  for (let i = 0; i < CATALOG_PAGE; i++) {
    const h = f.hit[CATALOG.tile0 + i];
    if (!h) continue;
    assert.equal(f.buttons[CATALOG.tile0 + i].text.startsWith(TILE_STATE.unlocked), st.unlocked.has(h.id));
  }
});

test('paginação dá a volta e cada página tem no máximo 18 itens', () => {
  const st = stateOf('slime');
  const list = catalogSkills('magic', st);
  const f = buildCatalogForm({ cat: 'magic', state: st });
  assert.equal(f.pages, Math.ceil(list.length / CATALOG_PAGE));
  assert.ok(f.pages > 1);
  assert.deepEqual(f.hit[CATALOG.next], { type: 'page', page: 1 });
  assert.deepEqual(f.hit[CATALOG.prev], { type: 'page', page: f.pages - 1 });
  const last = buildCatalogForm({ cat: 'magic', page: 999, state: st });
  assert.equal(last.page, f.pages - 1);
});

test('catálogo respeita as restrições de raça e nunca mostra raiz de raça', () => {
  for (const race of ['slime', 'human', 'goblin']) {
    const st = stateOf(race);
    for (const c of CATALOG_CATS) {
      for (const s of catalogSkills(c, st)) {
        assert.ok(raceAllowed(s, st), `${race}/${c}: ${s.id}`);
        assert.notEqual(s.type, 'race_root');
      }
    }
  }
});

test('descrição curta cabe no item (até 24 caracteres)', () => {
  for (const s of Object.values(SKILLS)) assert.ok(shortDesc(s).length <= 24, s.id);
});
