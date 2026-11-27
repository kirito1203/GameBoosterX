// Regras de desbloqueio, evolução, compatibilidade racial e custos.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { evaluate, unlock, pointCost } from '../BP/scripts/core/progression.js';
import { initialSkillsFor, SKILLS } from '../BP/scripts/data/registry.js';
import { computeStats, mpCost, powerFor } from '../BP/scripts/core/stats.js';

function player(race, level = 1, points = 0, extra = []) {
  return { race, level, points, unlocked: new Set([...initialSkillsFor(race), ...extra]), flags: new Set() };
}

test('não desbloqueia sem o nó anterior; mostra exatamente o que falta', () => {
  const st = player('human', 50, 999);
  const ev = evaluate('elem.fireball', st);
  assert.equal(ev.state, 'locked');
  assert.ok(ev.missing.includes('Requer: Fogo'));
  assert.equal(unlock('elem.fireball', st).ok, false);
  assert.ok(!st.unlocked.has('elem.fireball'));
});

test('caminho completo Fogo → Bola de Fogo → Lança de Fogo → Tempestade de Fogo', () => {
  const st = player('human', 30, 1000);
  for (const id of ['elem.core', 'elem.fire', 'elem.fireball', 'elem.fire_lance', 'elem.firestorm']) {
    const r = unlock(id, st);
    assert.ok(r.ok, `${id}: ${r.missing}`);
  }
  assert.ok(st.unlocked.has('elem.firestorm'));
});

test('pontos são descontados e faltar pontos é informado', () => {
  const st = player('goblin', 5, 15);
  const r1 = unlock('elem.core', st);
  assert.ok(r1.ok);
  assert.equal(st.points, 5);
  const ev = evaluate('elem.fire', st);
  assert.equal(ev.state, 'reachable');
  assert.ok(ev.missing.some((m) => m.includes('pontos')));
});

test('nível mínimo bloqueia', () => {
  const st = player('elf', 2, 999, ['elem.core', 'elem.fire']);
  const ev = evaluate('elem.fireball', st);
  assert.equal(ev.state, 'reachable');
  assert.ok(ev.missing.some((m) => m.startsWith('Nível 4')));
});

test('evolução: muda a raça, exige estágio certo e quantidade de habilidades da árvore', () => {
  const st = player('ogre', 18, 999);
  let ev = evaluate('r.ogre.evo_kijin', st);
  assert.equal(ev.state, 'reachable');
  assert.ok(ev.missing.some((m) => m.includes('habilidades desta árvore')));
  for (const id of ['r.ogre.war_cry', 'r.ogre.physical_resist']) assert.ok(unlock(id, st).ok, id);
  const r = unlock('r.ogre.evo_kijin', st);
  assert.ok(r.ok, r.missing.join());
  assert.equal(r.evolvedTo, 'kijin');
  assert.equal(st.race, 'kijin');
  // Mystic e Wicked Oni são exclusivos.
  st.level = 40;
  for (const id of ['r.ogre.elemental_manipulation', 'r.ogre.magicule_manipulation', 'r.ogre.elemental_transformation']) assert.ok(unlock(id, st).ok, id);
  assert.ok(unlock('r.ogre.evo_wicked_oni', st).ok);
  assert.equal(st.race, 'wicked_oni');
  const ex = evaluate('r.ogre.evo_mystic_oni', st);
  assert.equal(ex.state, 'locked');
  assert.ok(ex.missing.some((m) => m.includes('Incompatível')));
});

test('não evolui sem a evolução anterior', () => {
  const st = player('slime', 99, 9999);
  const ev = evaluate('r.slime.evo_god_slime', st);
  assert.equal(ev.state, 'locked');
  assert.ok(ev.missing.includes('Requer: Evolução: Demon Slime'));
});

test('compatibilidade racial: árvore racial de outra linhagem e magia sagrada para demônios', () => {
  const demon = player('lesser_demon', 50, 999);
  assert.equal(evaluate('r.slime.dissolve', demon).state, 'locked');
  assert.equal(evaluate('holy.prayer', demon).state, 'locked');
  const human = player('human', 50, 999);
  assert.equal(evaluate('holy.prayer', human).state, 'available');
});

test('conquistas e habilidades épicas exigidas por magias avançadas', () => {
  const st = player('human', 60, 99999);
  const ev = evaluate('adv.primitive_magic', st);
  assert.ok(ev.missing.some((m) => m.includes('Épicas')));
  for (const id of ['elem.core', 'elem.fire', 'elem.fireball', 'elem.fire_lance', 'elem.firestorm', 'elem.explosion', 'elem.chain_explosion',
    'elem.water', 'elem.freeze', 'elem.icicle_lance', 'elem.icicle_spear', 'elem.icicle_rain']) assert.ok(unlock(id, st).ok, id);
  assert.ok(unlock('adv.primitive_magic', st).ok);
  assert.ok(unlock('adv.physics_magic', st).ok);
  assert.ok(unlock('adv.nuclear_flame', st).ok);
  const nc = evaluate('adv.nuclear_cannon', st);
  assert.ok(nc.missing.includes('Conquista: Derrotar o Wither'));
  st.flags.add('killed_wither');
  assert.ok(unlock('adv.nuclear_cannon', st).ok);
});

test('Aprendizado Mágico (Humano) reduz o custo em pontos das árvores universais', () => {
  const human = player('human', 10, 0);
  const goblin = player('goblin', 10, 0);
  assert.ok(pointCost(SKILLS['elem.fireball'], human) < pointCost(SKILLS['elem.fireball'], goblin));
  assert.equal(pointCost(SKILLS['r.human.enchantment'], human), SKILLS['r.human.enchantment'].cost);
});

test('atributos: passivas e afinidades alteram Magicules, custo e dano', () => {
  const base = computeStats({ race: 'ogre', level: 1, unlocked: new Set() });
  const kijin = computeStats({ race: 'kijin', level: 20, unlocked: new Set(['r.ogre.elemental_manipulation', 'r.ogre.magicule_manipulation']) });
  assert.ok(kijin.maxMp > base.maxMp + 80);
  assert.ok(mpCost(SKILLS['elem.firestorm'], kijin) < mpCost(SKILLS['elem.firestorm'], base));
  assert.ok(powerFor('fire', kijin) > powerFor('fire', base));
  const slime = computeStats({ race: 'slime', level: 1, unlocked: new Set(['r.slime.self_regen']) });
  assert.equal(slime.effects.get('regeneration'), 0);
  assert.equal(slime.resist.fall, 1);
  const mimic = computeStats({ race: 'slime', level: 1, unlocked: new Set(), mimic: 'minecraft:blaze' });
  assert.ok(mimic.effects.has('fire_resistance'));
});

test('Magicules crescem com o nível e com a Absorção', () => {
  const a = computeStats({ race: 'slime', level: 1, unlocked: new Set() });
  const b = computeStats({ race: 'slime', level: 11, unlocked: new Set(), mpBonus: 40 });
  assert.equal(b.maxMp - a.maxMp, 10 * 6 + 40);
});
