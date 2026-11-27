// Salvamento individual: ida e volta, dados corrompidos e IDs removidos.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { serialize, deserialize, fresh } from '../BP/scripts/core/save.js';

test('salvar e carregar preserva o progresso (simula sair e entrar no mundo)', () => {
  const d = fresh();
  Object.assign(d, { race: 'kijin', level: 23, ep: 120, points: 55, mp: 87.6, mpBonus: 10, slot: 2, gifted: true, mimic: null });
  d.unlocked = new Set(['r.ogre.root', 'elem.core', 'elem.fire']);
  d.flags = new Set(['killed_wither']);
  d.equipped = ['elem.fire'];
  d.warp = { x: 1, y: 64, z: -3, dim: 'minecraft:overworld' };
  d.ui.tab = 'elemental';
  d.ui.views = { elemental: { x: -3, y: -2, zoom: 1 } };
  const raw = serialize(d);
  assert.ok(raw.length < 32767, 'cabe numa propriedade dinâmica');
  const back = deserialize(raw);
  assert.equal(back.race, 'kijin');
  assert.equal(back.level, 23);
  assert.equal(back.points, 55);
  assert.equal(back.mp, 87);
  assert.deepEqual([...back.unlocked], ['r.ogre.root', 'elem.core', 'elem.fire']);
  assert.ok(back.flags.has('killed_wither'));
  assert.deepEqual(back.equipped, ['elem.fire']);
  assert.deepEqual(back.warp, d.warp);
  assert.equal(back.ui.tab, 'elemental');
  assert.deepEqual(back.ui.views.elemental, { x: -3, y: -2, zoom: 1 });
});

test('IDs que deixaram de existir são descartados; dados corrompidos não travam', () => {
  const back = deserialize(JSON.stringify({ race: 'slime', unlocked: ['r.slime.root', 'nao.existe'], equipped: ['nao.existe'] }));
  assert.deepEqual([...back.unlocked], ['r.slime.root']);
  assert.deepEqual(back.equipped, []);
  const bad = deserialize('{quebrado');
  assert.equal(bad.race, null);
  assert.equal(bad.corrupted, true);
  assert.equal(deserialize(undefined).race, null);
});

test('progresso completo de todas as habilidades cabe no limite de 32767 caracteres', async () => {
  const { SKILLS } = await import('../BP/scripts/data/registry.js');
  const d = fresh();
  d.race = 'human';
  d.unlocked = new Set(Object.keys(SKILLS));
  assert.ok(serialize(d).length < 32767);
});
