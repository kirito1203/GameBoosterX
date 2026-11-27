// Árvore Suprema do Slime: dados, requisitos, evolução de sub-habilidades, Ciel (Manas),
// Shub-Niggurath (biblioteca/criação/duplicação/concessão), mapa próprio, salvamento e dragões.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
import { SKILLS, MISSIONS, SUPREME_ORDER, initialSkillsFor } from '../BP/scripts/data/registry.js';
import { SOURCES, TIERS } from '../BP/scripts/data/dsl.js';
import { CONFIG } from '../BP/scripts/config.js';
import { evaluate, fxOf } from '../BP/scripts/core/progression.js';
import { computeStats } from '../BP/scripts/core/stats.js';
import { fresh, serialize, deserialize } from '../BP/scripts/core/save.js';
import { freshSage } from '../BP/scripts/core/sage.js';
import { getSupremeMap } from '../BP/scripts/core/map.js';
import { buildTreeForm, viewAt } from '../BP/scripts/core/view.js';
import { buildSageForm } from '../BP/scripts/core/sageView.js';
import { CTRL, SAGE, layerBase } from '../BP/scripts/core/uiSpec.js';
import { hasCiel, opAllowed, azathothSynthesis, creatable, duplicable, grantCheck, dailyFree, nearRequirements, syntheses } from '../BP/scripts/core/manas.js';

const P = (x) => `r.slime.${x}`;
const ROOT = new URL('../', import.meta.url);
const ULT = ['raphael', 'beelzebuth', 'uriel', 'veldora', 'velgrynd', 'azathoth', 'shub'].map(P);

function slime(extra = {}) {
  const d = fresh();
  d.race = 'demon_slime'; d.level = 100; d.points = 99999;
  for (const id of initialSkillsFor('slime')) d.unlocked.add(id);
  d.sage = freshSage();
  Object.assign(d, extra);
  d.stats = computeStats(d);
  return d;
}
const st = (d) => ({ race: d.race, level: d.level, points: d.points, unlocked: d.unlocked, flags: d.flags, maxMp: 99999,
  absorbed: new Set(d.absorbed), discovered: d.discovered, missions: d.missions, mastery: d.mastery });
const withReqs = (d, id, seen = new Set()) => { for (const r of SKILLS[id].requires) if (!seen.has(r)) { seen.add(r); withReqs(d, r, seen); d.unlocked.add(r); } };

test('as 7 Supremas existem com todos os campos; Ciel é MANAS, não Suprema', () => {
  assert.deepEqual(SUPREME_ORDER.slime, [...ULT, P('ciel')]);
  for (const id of ULT) {
    const s = SKILLS[id];
    assert.equal(s.tier, 'ultimate', id);
    assert.ok(s.name && s.original && s.desc && s.limits !== undefined, id);
    assert.ok(['SS', 'EX', 'EX+'].includes(s.rank), `${id} rank ${s.rank}`);
    assert.ok(SOURCES[s.src], id);
    assert.ok(s.requires.length && s.subs.length, `${id} requisitos/sub-habilidades`);
    for (const sub of s.subs) assert.ok(SKILLS[sub], `${id}: sub ${sub}`);
  }
  assert.equal(SKILLS[P('ciel')].tier, 'manas');
  assert.equal(TIERS.manas, 'MANAS');
  assert.ok(CONFIG.ranks['EX+']);
});

test('sub-habilidades pedidas para cada Suprema (nomes originais)', () => {
  const want = {
    raphael: ['Thought Acceleration', 'Analytical Appraisal', 'Parallel Processing', 'Chant Annulment', 'All of Creation', 'Alteration', 'Synthesis', 'Separation'],
    beelzebuth: ['Predation', 'Imaginary Space', 'Mimicry', 'Corrosion', 'Soul Consumption', 'Food Chain'],
    uriel: ['Infinity Prison', 'Law Manipulation', 'Spatial Domination', 'Universal Barrier'],
    veldora: ['Storm Dragon Release', 'Storm Dragon Restoration'],
    velgrynd: ['Scorch Dragon Release', 'Scorch Dragon Restoration'],
    azathoth: ['Soul Gluttony', 'Nihility Collapse', 'Imaginary Space', 'Multiple Parallel Existence', 'True Dragon Release', 'True Dragon Nucleation', 'Space-Time Domination', 'Multi-Dimensional Barrier'],
    shub: ['Skill Creation', 'Skill Duplication', 'Skill Gifting', 'Skill Storage'],
  };
  for (const [k, names] of Object.entries(want)) {
    const got = new Set(SKILLS[P(k)].subs.map((id) => SKILLS[id].original));
    for (const n of names) assert.ok(got.has(n), `${k}: ${n}`);
  }
  assert.notEqual(SKILLS[P('veldora')].fx.el, SKILLS[P('velgrynd')].fx.el, 'Velgrynd (chama) ≠ Veldora (tempestade)');
});

test('Uriel: domínio defensivo e espacial + missão; sem evolução direta Uriel → Shub-Niggurath', () => {
  const d = slime();
  withReqs(d, P('uriel'));
  const ev = evaluate(P('uriel'), st(d));
  assert.ok(ev.missing.some((m) => m.includes('defensiva')) && ev.missing.some((m) => m.includes('espacial')));
  assert.ok(ev.missing.some((m) => m.includes('Voto de Proteção')));
  d.unlocked.add(P('barrier_control')); d.unlocked.add(P('shadow_motion'));
  d.mastery[P('barrier_control')] = 30; d.mastery[P('shadow_motion')] = 30;
  d.missions.add('m.slime.uriel_vow');
  assert.ok(evaluate(P('uriel'), st(d)).ok);
  assert.ok(!SKILLS[P('shub')].requires.includes(P('uriel')));
  assert.ok(!SKILLS[P('uriel')].children.includes(P('shub')));
  assert.match(SKILLS[P('uriel')].lore, /Raguel/);
});

test('Azathoth: não sai só com pontos — exige a síntese feita por Ciel', () => {
  const d = slime({ flags: new Set(['killed_wither', 'killed_warden']) });
  withReqs(d, P('azathoth'));
  for (const id of SKILLS[P('azathoth')].requires) d.unlocked.add(id);
  d.absorbed = Array.from({ length: 40 }, (_, i) => `minecraft:m${i}`);
  let ev = evaluate(P('azathoth'), st(d));
  assert.equal(ev.ok, false);
  assert.ok(ev.missing.some((m) => m.includes('Síntese de Azathoth')));
  const az = azathothSynthesis(d);
  assert.equal(az.ready, true, az.missing.join());
  d.cielOps.add('azathoth');
  d.missions.add('m.slime.void'); // concluída pelo sistema de missões após a síntese
  ev = evaluate(P('azathoth'), st(d));
  assert.ok(ev.ok, ev.missing.join());
  assert.equal(MISSIONS['m.slime.void'].cond.at(-1).cielOp, 'azathoth');
});

test('evolução de sub-habilidades: com Raphael/Beelzebuth o efeito real muda', () => {
  const d = slime();
  d.unlocked.add(P('parallel'));
  const before = computeStats(d).cdMult;
  d.unlocked.add(P('raphael'));
  assert.ok(computeStats(d).cdMult < before);
  assert.equal(fxOf(SKILLS[P('predation')], new Set()).range, 8);
  assert.equal(fxOf(SKILLS[P('predation')], new Set([P('beelzebuth')])).range, 12);
});

test('Ciel (Manas): requisitos, sínteses e operações liberadas só com os nós', () => {
  const d = slime();
  assert.equal(hasCiel(d), false);
  d.unlocked.add(P('ciel'));
  assert.ok(hasCiel(d));
  assert.equal(opAllowed(d, 'create'), false);
  d.unlocked.add(P('skill_creation'));
  assert.ok(opAllowed(d, 'create'));
  assert.ok(Array.isArray(nearRequirements(d, st(d))));
  assert.ok(syntheses(d, st(d)).every((x) => x.s.requires.length >= 2));
  const home = buildSageForm({ cat: 'home', d, sage: d.sage });
  assert.ok(Object.values(home.hit).some((h) => h.cat === 'manas'), 'bloco Ciel (Manas) na tela inicial');
  const page = buildSageForm({ cat: 'manas', d, sage: d.sage });
  assert.ok(Object.values(page.hit).some((h) => h.type === 'supreme_tree'));
  assert.ok(Object.values(page.hit).some((h) => h.type === 'ciel' && h.op === 'synthesis'));
  assert.match(page.body, /não é IA/);
});

test('Shub-Niggurath: só catálogo; criação/duplicação limitadas; concessão verifica compatibilidade', () => {
  const d = slime();
  d.library.add('elem.fireball'); d.library.add('ult.michael'); d.library.add('r.human.magic_learning'); d.library.add('uni.predator');
  const c = creatable(d, st(d)).map((s) => s.id);
  assert.ok(c.includes('elem.fireball'));
  assert.ok(!c.includes('ult.michael'), 'Supremas não são criadas');
  assert.ok(!c.includes('r.human.magic_learning'), 'raciais de outra raça não');
  assert.ok(!c.includes('uni.predator'), 'universal proibida para o Slime não');
  d.unlocked.add('elem.fire'); d.unlocked.add(P('great_sage'));
  const dup = duplicable(d).map((s) => s.id);
  assert.ok(dup.includes('elem.fire') && !dup.includes(P('great_sage')));
  const human = fresh(); human.race = 'human'; human.level = 1; human.unlocked = new Set(['r.human.root']);
  assert.equal(grantCheck(human, 'elem.fireball').ok, false, 'nível insuficiente');
  human.level = 99;
  assert.equal(grantCheck(human, 'elem.fireball').ok, true);
  human.unlocked.add('elem.fireball');
  assert.equal(grantCheck(human, 'elem.fireball').ok, false, 'já tem');
  assert.equal(grantCheck(human, P('predator')).ok, false, 'racial de outra linhagem');
  d.opDays.create = 5;
  assert.equal(dailyFree(d, 'create', 5), false);
  assert.equal(dailyFree(d, 'create', 6), true);
});

test('Árvore Suprema: mapa próprio com todas as Supremas, sub-habilidades e detalhes completos', () => {
  const m = getSupremeMap('slime');
  for (const id of SUPREME_ORDER.slime) assert.ok(m.pos[id], id);
  for (const id of ULT) for (const sub of SKILLS[id].subs) {
    assert.ok(m.pos[sub] || m.pos[`${sub}@${id}`], `${id}: ${sub}`);
  }
  const keys = Object.values(m.pos).map((p) => p.join(','));
  assert.equal(new Set(keys).size, keys.length, 'sem sobreposição');
  const d = slime();
  d.unlocked.add(P('raphael'));
  const f = buildTreeForm({ state: st(d), view: viewAt(m.pos[P('raphael')]), mp: { cur: 1, max: 1 }, supreme: true });
  assert.equal(f.buttons.length, layerBase().total);
  assert.ok(f.title.includes('Árvore Suprema'));
  for (let i = 0; i < SUPREME_ORDER.slime.length; i++) assert.equal(f.hit[CTRL.cat0 + i].type, 'jump');
  assert.equal(f.hit[CTRL.zoom], undefined, 'sem visão geral na Árvore Suprema');
  const det = f.buttons.slice(layerBase().detail, layerBase().action).map((b) => b.text).find((t) => t.includes('Raphael'));
  for (const field of ['Sub-habilidades', 'Origem', 'Efeito', 'Limitações']) assert.ok(det.includes(field), field);
});

test('salvamento: Biblioteca, cópias, conjuntos de barra e operações de Ciel voltam iguais', () => {
  const d = slime();
  d.library.add('elem.fireball'); d.skillCopies.push('elem.fire'); d.loadouts.push({ name: 'A', ids: ['elem.fire'] });
  d.cielOps.add('azathoth'); d.opDays.create = 3;
  const b = deserialize(serialize(d));
  assert.ok(b.library.has('elem.fireball') && b.cielOps.has('azathoth'));
  assert.deepEqual(b.skillCopies, ['elem.fire']);
  assert.deepEqual(b.loadouts, [{ name: 'A', ids: ['elem.fire'] }]);
  assert.equal(b.opDays.create, 3);
});

test('dragões: entidades com invocação segura (dono, timer, sem spawn natural) e cliente próprio', () => {
  for (const n of ['storm_dragon', 'scorch_dragon']) {
    const bp = JSON.parse(readFileSync(new URL(`BP/entities/${n}.json`, ROOT), 'utf8'))['minecraft:entity'];
    assert.equal(bp.description.is_spawnable, false);
    assert.ok(bp.components['minecraft:timer'], 'desaparece sozinho');
    assert.ok(bp.components['minecraft:behavior.owner_hurt_target']);
    assert.ok(existsSync(new URL(`RP/entity/${n}.entity.json`, ROOT)));
    assert.ok(existsSync(new URL(`RP/textures/entity/tensura/${n}.png`, ROOT)));
  }
  assert.equal(SKILLS[P('storm_dragon')].fx.entity, 'tensura:storm_dragon');
  assert.equal(SKILLS[P('scorch_dragon')].fx.entity, 'tensura:scorch_dragon');
  const src = readFileSync(new URL('BP/scripts/systems/effects.js', ROOT), 'utf8');
  assert.ok(/absorb\(player, f\)[\s\S]{0,400}isAlly/.test(src), 'absorção recusa aliados');
});
