// Tela de escolha de raça, apresentação do Grande Sábio e salvamento da primeira entrada.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
import { RACES, initialSkillsFor } from '../BP/scripts/data/registry.js';
import { RACE_CARDS, CARD_ORDER, INTRO_LINES } from '../BP/scripts/data/raceCards.js';
import { buildRaceForm, isSelectable, raceInfo, pageOf, PAGE_COUNT } from '../BP/scripts/core/raceView.js';
import { RACE, RACE_COUNT, RACE_MARK, RACE_PAGE_SIZE, CARD_SELECTED, CARD_LOCKED } from '../BP/scripts/core/uiSpec.js';
import { serialize, deserialize, fresh } from '../BP/scripts/core/save.js';
import { previewHtml, scenario, modelFor } from '../tools/preview.mjs';

const RP = new URL('../RP/', import.meta.url);

test('os cards pedidos existem, na ordem, e só raças iniciais podem ser escolhidas', () => {
  const names = CARD_ORDER.map((id) => RACES[id].original);
  assert.deepEqual(names, ['Slime', 'Human', 'Goblin', 'Hobgoblin', 'Ogre', 'Kijin', 'Mystic Oni', 'Wicked Oni', 'Lizardman', 'Dragonewt',
    'Orc', 'High Orc', 'Lesser Demon', 'Angel', 'Fallen', 'Dragonoid', 'Vampire', 'Elemental', 'Elf', 'Dwarf', 'Dryad', 'Direwolf', 'Harpy',
    'Merfolk', 'Ghoul', 'Giant', 'Fairy', 'Beastman', 'Lycanthrope', 'Skeleton', 'Wight King', 'Golem', 'Dragon', 'True Dragon']);
  const locked = CARD_ORDER.filter((id) => !isSelectable(id));
  assert.deepEqual(locked, ['hobgoblin', 'kijin', 'mystic_oni', 'wicked_oni', 'dragonewt', 'high_orc', 'fallen_angel', 'lycanthrope', 'wight_king', 'true_dragon'], 'evoluções não são escolha inicial');
  for (const id of locked) assert.ok(RACES[id].stage > 0);
  for (const [id, diff] of RACE_CARDS) assert.ok(diff >= 1 && diff <= 5, id);
});

test('formulário: card selecionado destacado, bloqueados marcados, painel e botão de confirmar', () => {
  const f = buildRaceForm({ page: 0, selected: 'slime', sageText: 'Olá' });
  assert.equal(f.buttons.length, RACE_COUNT);
  assert.ok(f.title.startsWith(RACE_MARK));
  assert.equal(f.buttons[RACE.card0].text, `${CARD_SELECTED}Slime`);
  assert.ok(f.buttons[RACE.card0 + 3].text.startsWith(CARD_LOCKED), 'Hobgoblin bloqueado');
  assert.equal(f.hit[RACE.confirm].type, 'confirm');
  assert.ok(f.buttons[RACE.infoInitial].text.includes('Absorver'));
  assert.ok(f.buttons[RACE.infoInitial].text.includes('Dissolver'));
  assert.ok(f.buttons[RACE.infoInitial].text.includes('Autorregeneração'));
  assert.ok(f.buttons[RACE.infoEvolutions].text.includes('Demon Slime'));
  assert.ok(f.buttons[RACE.infoAffinity].text.includes('Adaptabilidade'));
  assert.match(f.buttons[RACE.infoDifficulty].icon, /stars_2$/);
  assert.equal(f.buttons[RACE.sageText].text, 'Olá');
  const k = buildRaceForm({ page: 0, selected: 'kijin', sageText: '' });
  assert.equal(k.hit[RACE.confirm].type, 'locked', 'Kijin não pode ser confirmado');
  assert.ok(k.buttons[RACE.infoDesc].text.includes('Evolução de Ogro'));
});

test('todas as raças aparecem em alguma página e a navegação percorre as páginas', () => {
  const seen = new Set();
  for (let p = 0; p < PAGE_COUNT; p++) {
    const f = buildRaceForm({ page: p, selected: 'slime', sageText: '' });
    for (const h of Object.values(f.hit)) if (h.type === 'select') seen.add(h.race);
    assert.equal(f.hit[RACE.next].page, (p + 1) % PAGE_COUNT);
  }
  assert.equal(seen.size, CARD_ORDER.length);
  for (const id of CARD_ORDER) assert.ok(pageOf(id) < PAGE_COUNT && CARD_ORDER.slice(pageOf(id) * RACE_PAGE_SIZE).includes(id));
});

test('informações completas para cada raça (descrição, iniciais, afinidade, evoluções, dificuldade)', () => {
  for (const id of CARD_ORDER) {
    const i = raceInfo(id);
    assert.ok(i.desc.length > 10 && i.affinity && i.difficulty, id);
    if (isSelectable(id)) assert.ok(i.initial.length >= 1, `${id} sem habilidades iniciais`);
  }
  assert.deepEqual(initialSkillsFor('slime').filter((s) => s !== 'r.slime.root').length, 3);
});

test('texturas da tela existem (retratos, molduras, estrelas, Grande Sábio)', () => {
  for (const id of CARD_ORDER) assert.ok(existsSync(new URL(`textures/tensura/race/p_${id}.png`, RP)), id);
  for (const n of ['bg', 'sage', 'sage_glow', 'cube', 'dialog', 'panel', 'title', 'tab', 'card', 'card_sel', 'card_glow', 'card_locked',
    'card_hover', 'btn_green', 'btn_stone', 'arrow_down', 'stars_0', 'stars_5']) assert.ok(existsSync(new URL(`textures/tensura/race/${n}.png`, RP)), n);
  assert.ok(existsSync(new URL('textures/entity/tensura/great_sage.png', RP)));
});

test('Grande Sábio: modelo, textura, animações, controlador e propriedade consistentes', () => {
  const read = (u) => JSON.parse(readFileSync(new URL(u, RP), 'utf8'));
  const bp = JSON.parse(readFileSync(new URL('../BP/entities/great_sage.json', import.meta.url), 'utf8'))['minecraft:entity'];
  const cl = read('entity/great_sage.entity.json')['minecraft:client_entity'].description;
  const geo = read('models/entity/great_sage.geo.json')['minecraft:geometry'][0];
  const anims = read('animations/great_sage.animation.json').animations;
  const ctrls = read('animation_controllers/great_sage.animation_controllers.json').animation_controllers;
  const rc = read('render_controllers/great_sage.render_controllers.json').render_controllers;
  assert.equal(bp.description.identifier, cl.identifier);
  assert.equal(cl.geometry.default, geo.description.identifier);
  assert.ok(rc[cl.render_controllers[0]], 'render controller existe');
  // Núcleo mágico (sem corpo humano): núcleo, brilho, anéis, círculo da base, painéis e fragmentos.
  const bones = new Map(geo.bones.map((b) => [b.name, b]));
  for (const b of ['core', 'glow_core', 'ring_top', 'ring_mid', 'ring_low', 'ring_base', 'panel_orbit', 'panel_a', 'panel_b', 'panel_c', 'bits']) {
    assert.ok(bones.has(b), `osso ${b}`);
  }
  for (const b of ['head', 'body', 'rightArm', 'leftLeg']) assert.ok(!bones.has(b), `sem corpo humano: ${b}`);
  // UVs dentro da textura 128x128.
  for (const b of geo.bones) for (const c of b.cubes ?? []) {
    const faces = Array.isArray(c.uv) ? [{ uv: c.uv, uv_size: [2 * (c.size[0] + c.size[2]), c.size[1] + c.size[2]] }] : Object.values(c.uv);
    for (const f of faces) {
      const xs = [f.uv[0], f.uv[0] + f.uv_size[0]];
      const ys = [f.uv[1], f.uv[1] + f.uv_size[1]];
      assert.ok(Math.min(...xs) >= 0 && Math.max(...xs) <= 128 && Math.min(...ys) >= 0 && Math.max(...ys) <= 128, `${b.name} UV fora`);
    }
  }
  for (const b of geo.bones) if (b.parent) assert.ok(bones.has(b.parent), `pai ${b.parent}`);
  // Toda animação referenciada existe, e todo osso animado existe na geometria.
  for (const [short, full] of Object.entries(cl.animations)) {
    const def = anims[full] ?? ctrls[full];
    assert.ok(def, `animação ${short} → ${full}`);
    for (const b of Object.keys(def.bones ?? {})) assert.ok(bones.has(b), `${full}: osso ${b}`);
  }
  const ctrl = ctrls[cl.animations.controller];
  const propValues = bp.description.properties['tensura:state'].values;
  for (const st of Object.values(ctrl.states)) {
    for (const a of st.animations) assert.ok(cl.animations[a], `controlador usa ${a}`);
    for (const tr of st.transitions ?? []) for (const cond of Object.values(tr)) {
      for (const m of cond.matchAll(/'([a-z]+)'/g)) assert.ok(propValues.includes(m[1]), `estado ${m[1]} não existe na propriedade`);
    }
  }
  const script = readFileSync(new URL('../BP/scripts/systems/sageEntity.js', import.meta.url), 'utf8');
  for (const v of ["'talk'", "'idle'", "'vanish'"]) assert.ok(script.includes(v));
  for (const m of rc[cl.render_controllers[0]].materials) for (const k of Object.values(m)) assert.ok(cl.materials[k.replace('Material.', '')], k);
  // Sem colisão e sem dano (não atrapalha combate nem bloqueia passagens).
  assert.ok(bp.components['minecraft:collision_box'].width <= 0.05);
  assert.equal(bp.components['minecraft:physics'].has_collision, false);
  assert.equal(bp.components['minecraft:damage_sensor'].triggers[0].deals_damage, 'no');
  // Textura cobre todas as UVs.
  assert.equal(geo.description.texture_width, 128);
  assert.ok(existsSync(new URL('textures/entity/tensura/great_sage.png', RP)));
  assert.equal(INTRO_LINES.length, 6);
});

test('primeira entrada: apresentação salva; quem já tinha raça não a vê de novo', () => {
  const d = fresh();
  assert.equal(d.introDone, false);
  d.introDone = true;
  assert.equal(deserialize(serialize(d)).introDone, true);
  const old = deserialize(JSON.stringify({ race: 'slime', unlocked: ['r.slime.root'] }));
  assert.equal(old.introDone, true, 'jogador de versão anterior não repete a apresentação');
  const interrupted = deserialize(JSON.stringify({ race: null, gifted: false }));
  assert.equal(interrupted.introDone, false, 'interrompida: recomeça na próxima entrada');
});

test('simulação: tela de raças com cards, painel e textos de botões diferentes', () => {
  for (const name of ['race-slime', 'race-kijin', 'race-page2']) {
    const sc = scenario(name);
    const { page } = previewHtml(modelFor(sc), { root: 'race_root' });
    for (const s of ['ESCOLHA SUA RAÇA', 'Grande Sábio', 'Dificuldade', 'stars_']) assert.ok(page.includes(s), `${name}: falta ${s}`);
  }
  const { page } = previewHtml(modelFor(scenario('race-slime')), { root: 'race_root' });
  for (const s of ['Slime', 'Humano', 'Goblin', 'Elemental', 'CONFIRMAR RAÇA', 'Absorver', 'Demon Slime', 'p_slime', 'p_goblin', 'card_sel', 'card_locked']) {
    assert.ok(page.includes(s), `falta ${s}`);
  }
});
