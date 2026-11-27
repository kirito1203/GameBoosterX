// Integridade dos dados: raças, habilidades, árvores e efeitos.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { SKILLS, TREES, RACES, LINEAGES, UNIVERSAL_TREES, TAB_ORDER, initialSkillsFor } from '../BP/scripts/data/registry.js';
import { CONFIG } from '../BP/scripts/config.js';
import { fxList } from '../BP/scripts/core/progression.js';
import { isPassiveFx } from '../BP/scripts/data/dsl.js';
import { describeFx } from '../BP/scripts/core/describe.js';

const PASSIVES = new Set(['p_effect', 'p_mp', 'p_resist', 'p_lifesteal', 'p_onhit', 'p_affinity', 'p_power', 'p_starved', 'p_xp', 'p_points', 'p_regen_env', 'p_race', 'p_aura']);
// Tipos ativos implementados em systems/effects.js (lidos do código-fonte).
const effectsSrc = readFileSync(new URL('../BP/scripts/systems/effects.js', import.meta.url), 'utf8');
const handlersBlock = effectsSrc.slice(effectsSrc.indexOf('const HANDLERS = {'), effectsSrc.indexOf('function compass('));
const ACTIVE = new Set([...handlersBlock.matchAll(/^  ([a-z_]+)\(player/gm)].map((m) => m[1]));

test('há centenas de habilidades: as 10 árvores pedidas + Únicas, Supremas, Extras e Resistências', () => {
  assert.ok(Object.keys(SKILLS).length >= 320, `só ${Object.keys(SKILLS).length}`);
  assert.equal(TAB_ORDER.length, 14);
  assert.deepEqual(UNIVERSAL_TREES.map((t) => t.id), ['elemental', 'aspect', 'holy', 'summon', 'illusion', 'spirit', 'necromancy', 'dark', 'advanced',
    'unique', 'ultimate', 'extra', 'resist']);
});

test('habilidades do anime: Únicas, Supremas, Extras e Resistências', () => {
  const names = new Set(Object.values(SKILLS).map((s) => s.original));
  for (const w of ['Great Sage', 'Predator', 'Gluttony', 'Starved', 'Cook', 'Degenerate', 'Mathematician', 'Analyst', 'Creator', 'Usurper',
    'Wrath', 'Raphael', 'Beelzebuth', 'Uriel', 'Gabriel', 'Michael', 'Metatron', 'Satanael', 'Azathoth', 'Faust', 'Yog-Sothoth', 'Mammon',
    'Amaterasu', 'Susanoo', 'Magic Sense', 'Shadow Motion', 'Spatial Motion', 'Spatial Domination', 'Universal Detection', 'Body Armor',
    'Instant Regeneration', 'Ultraspeed Regeneration', 'Multilayer Barrier', 'Dragon Spirit Haki', 'Flame Manipulation', 'Black Flame',
    'Water Manipulation', 'Water Blade', 'Water Bullet', 'Steel Thread', 'Sticky Steel Thread', 'Black Lightning', 'Poisonous Breath',
    'Paralyzing Breath', 'Ultrasonic Waves', 'Self-Replication', 'Thermal Fluctuation Resistance', 'Pain Resistance',
    'Physical Attack Resistance', 'Electric Current Resistance', 'Paralysis Resistance', 'Poison Resistance', 'Corrosion Resistance',
    'Holy Attack Resistance', 'Spiritual Attack Resistance']) assert.ok(names.has(w), `ausente: ${w}`);
  for (const s of TREES.ultimate.skills) assert.ok(s.minLevel >= 45, `${s.id}: suprema com nível baixo`);
});

test('todas as 26 raças pedidas existem', () => {
  const wanted = ['Slime', 'Human', 'Goblin', 'Hobgoblin', 'Ogre', 'Kijin', 'Mystic Oni', 'Wicked Oni', 'Lizardman', 'Dragonewt', 'Orc', 'High Orc',
    'Lesser Demon', 'Angel', 'Fallen', 'Dragonoid', 'Vampire', 'Elemental', 'Elf', 'Dwarf', 'Dryad', 'Direwolf', 'Harpy', 'Merfolk', 'Ghoul', 'Giant'];
  const originals = new Set(Object.values(RACES).map((r) => r.original));
  for (const w of wanted) assert.ok(originals.has(w), `raça ausente: ${w}`);
});

test('todas as magias listadas no pedido existem com o nome original', () => {
  const names = new Set(Object.values(SKILLS).map((s) => s.original));
  const wanted = ['Fire', 'Fireball', 'Fire Lance', 'Firestorm', 'Fire Wall', 'Water', 'Water Cutter', 'Water Jail', 'Freeze', 'Icicle Lance',
    'Icicle Rain', 'Icicle Spear', 'Ice Wall', 'Ice Blizzard', 'Stone Shot', 'Earth Wall', 'Earth Lock', 'Mud Hand', 'Wind Cutter', 'Wind Gust',
    'Tornado Blade', 'Wind Protection', 'Thunder', 'Thunder Lance', 'Thunder Orb', 'Thunder Rain', 'Explosion', 'Chain Explosion',
    'Barrier', 'Reinforced Barrier', 'Anti-Magic Area', 'Magic Wall', 'Analyze', 'Clairvoyance', 'Invisible', 'Mirage', 'Flight',
    'Spatial Storage', 'Warp Portal', 'Healing', 'Full Recovery', 'Float', 'Drainage', 'Reinforcement', 'Protection', 'Confusion', 'Hypnos',
    'Dominate', 'Demon Dominate', 'Demon Marionette', 'Mental Crush', 'Antidote', 'Possession', 'Doppelganger',
    'Holy Cannon', 'Holy Ray', 'Holy Field', 'Alignment Field', 'Prison Field', 'Disintegration', 'Great High Heal', 'Great Regeneration',
    'Summon Hound Dog', 'Summon Basilisk', 'Summon Daemon', 'Summon Medium Elemental', 'Summon Greater Elemental', 'Summon Spirit',
    'Summon Familiar', 'Summon Undead', 'Fire Spirit Magic', 'Water Spirit Magic', 'Earth Spirit Magic', 'Wind Spirit Magic',
    'Light Spirit Magic', 'Dark Spirit Magic', 'Immortal Legion', 'Curse Bind', 'Day-Night Inversion', 'Dark Magic', 'Darkness Manipulation',
    'Shadow Manipulation', 'Cursed Poison', 'Locking Curse', 'Abyss Annihilation', 'Death Streak', 'Physics Magic', 'Primitive Magic',
    'Soul Magic', 'True Dragon Magic', 'Gravity Magic', 'Space Magic', 'Time Magic', 'Nuclear Flame', 'Nuclear Cannon',
    'Isolated Battlefield', 'Marionette Heart', 'Life-Exorcising Wind',
    'Absorption', 'Dissolve', 'Self-Regeneration', 'Shape-shifting', 'Magic Adaptation', 'Magic Resistance', 'Elemental Affinity',
    'Spirit Affinity', 'Enchantment', 'Magic Learning', 'Elemental Manipulation', 'Magicule Manipulation', 'Physical Enhancement',
    'Elemental Transformation', 'Dragon Scales', 'Dragon Eye', 'Dragon Body', 'Dragon Change', 'Flame Breath', 'Thunder Breath',
    'Thought Communication', 'Ultra Smell', 'Coercion', 'Starved', 'Physical Resistance', 'Regeneration', 'Energy Absorption',
    'Material Creation', 'Possess', 'Illusion Magic', 'Heavenly Aura', 'Celestial Wings', 'Holy Protection', 'Purification', 'Light Magic',
    'Blood Raise', 'Charm', 'Paralyze', 'Shadow Motion', 'Transform', 'Elemental Projection', 'Spirit Protection', 'Elemental Regeneration'];
  for (const w of wanted) assert.ok(names.has(w), `ausente: ${w}`);
});

test('cada habilidade tem campos completos, efeito implementado e custo dentro da faixa da raridade', () => {
  for (const s of Object.values(SKILLS)) {
    assert.ok(s.id && s.name && s.original && s.category, `campos: ${s.id}`);
    assert.ok(CONFIG.rarities[s.rarity], `raridade inválida: ${s.id}`);
    assert.ok(['obra', 'adaptado', 'original'].includes(s.origin), `origem: ${s.id}`);
    assert.ok(s.fx, `sem efeito: ${s.id}`);
    const kinds = fxList(s.fx).map((f) => f.k);
    for (const k of kinds) {
      if (s.type === 'evolution') { assert.equal(k, 'evolve'); continue; }
      assert.ok(PASSIVES.has(k) || ACTIVE.has(k), `efeito não implementado: ${s.id} → ${k}`);
    }
    if (s.type === 'passive') assert.ok(isPassiveFx(s.fx), `passiva com efeito ativo: ${s.id}`);
    if (s.type === 'active') assert.ok(s.mp > 0 || s.cooldown > 0, `ativa sem custo/recarga: ${s.id}`);
    if (s.type !== 'race_root') {
      const r = CONFIG.rarities[s.rarity];
      assert.ok(s.cost >= r.min && s.cost <= r.max, `${s.id}: custo ${s.cost} fora de ${r.min}-${r.max} (${s.rarity})`);
    }
    assert.ok(describeFx(s.fx).length > 0, `sem descrição de efeito: ${s.id}`);
    assert.ok(s.desc.length > 10, `descrição curta: ${s.id}`);
  }
});

test('pré-requisitos existem, não há ciclos e só a raiz de cada árvore não tem pré-requisito', () => {
  for (const s of Object.values(SKILLS)) for (const r of s.requires) assert.ok(SKILLS[r], `${s.id} requer inexistente ${r}`);
  const visiting = new Set();
  const done = new Set();
  const dfs = (id) => {
    if (done.has(id)) return;
    assert.ok(!visiting.has(id), `ciclo em ${id}`);
    visiting.add(id);
    for (const r of SKILLS[id].requires) dfs(r);
    visiting.delete(id);
    done.add(id);
  };
  Object.keys(SKILLS).forEach(dfs);
  for (const t of Object.values(TREES)) {
    for (const s of t.skills) {
      if (s.id === t.root) assert.equal(s.requires.length, 0, `raiz com pré-requisito: ${s.id}`);
      else assert.ok(s.requires.length > 0, `habilidade sem pré-requisito (desconectada): ${s.id}`);
    }
  }
});

test('magias avançadas exigem requisitos elevados (não só nível)', () => {
  for (const s of TREES.advanced.skills) {
    assert.ok(s.minLevel >= 30, `${s.id} nível baixo`);
    assert.ok(['legendary', 'supreme'].includes(s.rarity), `${s.id} raridade baixa`);
    const extra = s.requires.length + s.needEpic + s.needFlags.length;
    assert.ok(extra > 0, `${s.id} só exige nível`);
  }
});

test('cada linhagem tem árvore racial, raiz concedida e evoluções válidas; todas as raças são alcançáveis', () => {
  const reachable = new Set();
  for (const lin in LINEAGES) {
    const t = TREES[`race.${lin}`];
    assert.ok(t, `sem árvore: ${lin}`);
    const base = LINEAGES[lin].base;
    assert.equal(RACES[base].stage, 0);
    assert.ok(RACES[base].start, `raça base não inicial: ${base}`);
    reachable.add(base);
    const init = initialSkillsFor(base);
    assert.ok(init.includes(`r.${lin}.root`), `raiz não concedida: ${lin}`);
    assert.ok(init.length >= 2 && init.length < t.skills.length / 2, `${lin}: concede ${init.length} de ${t.skills.length} (não pode conceder tudo)`);
    for (const s of t.skills.filter((x) => x.type === 'evolution')) {
      assert.ok(RACES[s.evolveTo], `${s.id} evolui para raça inexistente`);
      assert.equal(RACES[s.evolveTo].lineage, lin, `${s.id} muda de linhagem`);
      reachable.add(s.evolveTo);
    }
  }
  for (const id in RACES) assert.ok(reachable.has(id), `raça inalcançável: ${id}`);
});

test('raças têm atributos coerentes', () => {
  for (const r of Object.values(RACES)) {
    assert.ok(r.mp > 0 && r.regen > 0, r.id);
    for (const v of [...Object.values(r.resist), ...Object.values(r.weak)]) assert.ok(v >= 0 && v <= 1, r.id);
    for (const v of Object.values(r.aff)) assert.ok(v > 0 && v <= 1, r.id);
    assert.ok(['obra', 'adaptado', 'original'].includes(r.origin), r.id);
  }
});

test('invocações referem entidades que existem no behavior pack e no resource pack', () => {
  const ents = new Set();
  for (const s of Object.values(SKILLS)) for (const f of fxList(s.fx)) {
    if (f.k === 'summon') { ents.add(f.entity); for (const [e] of f.extra ?? []) ents.add(e); }
    if (f.k === 'marionette') ents.add('tensura:undead_warrior');
  }
  for (const e of ents) {
    const name = e.split(':')[1];
    const bp = JSON.parse(readFileSync(new URL(`../BP/entities/${name}.json`, import.meta.url), 'utf8'));
    assert.equal(bp['minecraft:entity'].description.identifier, e);
    const rp = JSON.parse(readFileSync(new URL(`../RP/entity/${name}.entity.json`, import.meta.url), 'utf8'));
    assert.equal(rp['minecraft:client_entity'].description.identifier, e);
  }
  assert.ok(ents.size >= 8);
});
