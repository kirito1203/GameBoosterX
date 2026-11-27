// Painel individual do Grande Sábio: progressão própria, capacidades, requisitos de evolução,
// perguntas com dados reais, salvamento e a tela do resource pack.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
import { CONFIG } from '../BP/scripts/config.js';
import { initialSkillsFor, SKILLS, RACES } from '../BP/scripts/data/registry.js';
import { fresh, serialize, deserialize } from '../BP/scripts/core/save.js';
import { computeStats, mpCost } from '../BP/scripts/core/stats.js';
import {
  freshSage, addXp, xpToNext, levelCap, takeDaily, hasCapability, CAPABILITIES, CAPABILITY_CATEGORIES, evolutionRequirements, evolve,
  serializeSage, deserializeSage, newlyUnlocked, sageBonuses, threatLevel, bestHealSkill,
} from '../BP/scripts/core/sage.js';
import { ask, INSUFFICIENT } from '../BP/scripts/core/sageAsk.js';
import { buildSageForm } from '../BP/scripts/core/sageView.js';
import { buildMenuForm } from '../BP/scripts/core/menuView.js';
import { SAGE, SAGE_CATS, SAGE_TILES, SAGE_COUNT, SAGE_MARK, MENU } from '../BP/scripts/core/uiSpec.js';
import { previewHtml, scenario, modelFor } from '../tools/preview.mjs';

const RP = new URL('../RP/', import.meta.url);
const BP = new URL('../BP/', import.meta.url);

function player(race = 'slime', level = 10) {
  const d = fresh();
  d.race = race; d.level = level; d.points = 200;
  for (const id of initialSkillsFor(race)) d.unlocked.add(id);
  d.sage = freshSage();
  d.stats = computeStats(d);
  d.mp = 50; d.aura = 60;
  return d;
}
const stateOf = (d) => ({ race: d.race, level: d.level, points: d.points, unlocked: d.unlocked, flags: d.flags });
const toLevel = (s, lvl) => { while (s.level < lvl && s.level < levelCap(s.stage)) addXp(s, xpToNext(s.level)); };

test('nível do Sábio: XP, curva, limite do estágio configurável e XP excedente descartado no limite', () => {
  const s = freshSage();
  assert.equal(s.level, 1);
  assert.equal(addXp(s, xpToNext(1) - 1), 0);
  assert.equal(addXp(s, 1), 1);
  assert.equal(s.level, 2);
  assert.equal(s.xp, 0);
  addXp(s, 1e7);
  assert.equal(s.level, CONFIG.sage.levelCaps.sage, 'para no limite do estágio');
  assert.equal(s.xp, 0);
  assert.equal(addXp(s, 500), 0, 'no limite não sobe mais');
  assert.ok(xpToNext(10) > xpToNext(2));
});

test('anti-farm: limite diário de recompensas repetidas, reinicia no dia seguinte', () => {
  const s = freshSage();
  let ok = 0;
  for (let i = 0; i < 100; i++) if (takeDaily(s, 'repeatAnalysis', 5)) ok++;
  assert.equal(ok, CONFIG.sage.dailyCaps.repeatAnalysis);
  assert.equal(takeDaily(s, 'repeatAnalysis', 6), true, 'novo dia');
});

test('capacidades: 11 categorias, cada uma com estágio e nível; desbloqueio por nível e estágio', () => {
  assert.equal(CAPABILITY_CATEGORIES.length, 11);
  for (const c of CAPABILITIES) {
    assert.ok(c.name && c.desc && c.stage && c.level >= 1, c.id);
    assert.ok(CAPABILITY_CATEGORIES.includes(c.cat), `${c.id} categoria`);
  }
  const s = freshSage();
  assert.ok(hasCapability(s, 'analysis') && hasCapability(s, 'qa'));
  assert.ok(!hasCapability(s, 'threat'));
  const before = { stage: s.stage, level: s.level };
  toLevel(s, 8);
  assert.ok(hasCapability(s, 'threat'));
  assert.ok(newlyUnlocked(before, s).some((c) => c.id === 'threat'));
  assert.ok(!hasCapability(s, 'parallel'), 'capacidade de Raphael não vale no Grande Sábio');
});

test('bônus de capacidades entram nos atributos reais (regeneração, custo de Magicules, recarga)', () => {
  const d = player();
  const base = computeStats(d);
  d.sage.stage = 'raphael'; d.sage.level = 30;
  const b = sageBonuses(d.sage);
  assert.ok(b.regenMult > 1 && b.mpMult < 1 && b.cooldownMult < 1);
  const st = computeStats(d);
  assert.ok(st.regenMult > base.regenMult);
  assert.equal(st.cdMult, b.cooldownMult);
  const magic = Object.values(SKILLS).find((s) => s.tree === 'elemental' && s.mp >= 20);
  assert.ok(mpCost(magic, st) < mpCost(magic, base));
});

test('Raphael: evolui só com todos os requisitos (sem desbloqueio manual)', () => {
  const d = player('slime', 40);
  const s = d.sage;
  assert.equal(evolve(s, d), null, 'nível 1 não evolui');
  toLevel(s, 99);
  assert.equal(s.level, 30);
  let ev = evolutionRequirements(s, d);
  assert.equal(ev.next, 'raphael');
  assert.ok(!ev.ready);
  for (let i = 0; i < 20; i++) s.analyses[`minecraft:mob${i}`] = { typeId: `minecraft:mob${i}`, name: 'x', count: 1 };
  assert.ok(!evolutionRequirements(s, d).ready, 'falta chefe');
  d.flags.add('killed_wither');
  assert.ok(!evolutionRequirements(s, d).ready, 'Slime: falta o nó Raphael da árvore');
  d.unlocked.add('r.slime.raphael');
  ev = evolutionRequirements(s, d);
  assert.ok(ev.ready);
  assert.equal(evolve(s, d, 12), 'raphael');
  assert.equal(s.stage, 'raphael');
  assert.equal(s.level, 1);
  assert.deepEqual(s.evolutions, [{ stage: 'raphael', day: 12 }]);
});

test('Ciel: requisitos especiais (Raphael no máximo, nível 60, Wither + Dragon, 60 espécies, raça 3º estágio)', () => {
  const d = player('slime', 70);
  const s = d.sage;
  s.stage = 'raphael';
  toLevel(s, 99);
  for (let i = 0; i < 60; i++) s.analyses[`m${i}`] = { count: 1 };
  d.flags.add('killed_wither');
  let ev = evolutionRequirements(s, d);
  assert.equal(ev.next, 'ciel');
  assert.ok(!ev.ready);
  d.flags.add('killed_ender_dragon');
  assert.ok(!evolutionRequirements(s, d).ready, 'raça ainda no 1º estágio');
  const high = Object.keys(RACES).find((r) => RACES[r].lineage === 'slime' && RACES[r].stage >= 2);
  d.race = high;
  assert.ok(!evolutionRequirements(s, d).ready, 'Slime: falta o nó Ciel da árvore');
  d.unlocked.add('r.slime.ciel');
  ev = evolutionRequirements(s, d);
  assert.ok(ev.ready, JSON.stringify(ev.reqs));
  assert.equal(evolve(s, d), 'ciel');
  assert.equal(evolutionRequirements(s, d), null, 'estágio final');
});

test('perguntas: respostas com dados reais e "dados insuficientes" quando não há dados', () => {
  const d = player();
  const s = d.sage;
  toLevel(s, 10);
  const ctx = { d, sage: s, state: stateOf(d), target: null };
  assert.match(ask('Qual é minha raça atual?', ctx).text, /Slime/);
  assert.match(ask('Qual é meu nível atual?', ctx).text, /nível 10/);
  assert.match(ask('Quais habilidades posso aprender?', ctx).text, /desbloquear agora/);
  assert.match(ask('Como posso evoluir?', ctx).text, /Falta|Pronto/);
  assert.equal(ask('Qual é o nível de ameaça desta criatura?', ctx).intent, 'threat');
  assert.match(ask('Qual é o nível de ameaça desta criatura?', ctx).text, new RegExp(INSUFFICIENT));
  const t = ask('Qual é o nível de ameaça desta criatura?', { ...ctx, target: { name: 'zombie', hp: 20, maxHp: 20, hostile: true, boss: false, undead: true } });
  assert.match(t.text, /Moderada/);
  assert.match(ask('Quais habilidades podem ser combinadas?', ctx).text, /combina/i);
  assert.match(ask('Quais capacidades você possui atualmente?', ctx).text, /Capacidades ativas/);
  assert.equal(ask('xyzzy blorp', ctx).intent, 'unknown');
  assert.match(ask('xyzzy blorp', ctx).text, new RegExp(INSUFFICIENT));
  // sem a capacidade, não inventa a lista
  const low = { ...ctx, sage: freshSage() };
  assert.match(ask('Quais magias posso desbloquear?', low).text, /Ainda não tenho a capacidade/);
});

test('ameaça e melhor cura vêm dos dados', () => {
  assert.equal(threatLevel({ boss: true }).level, 5);
  assert.equal(threatLevel({ hostile: false, maxHp: 100 }).level, 0);
  assert.equal(threatLevel({ hostile: true, maxHp: 100 }).level, 4);
  const healer = Object.values(SKILLS).find((s) => s.type === 'active' && [].concat(s.fx ?? []).some((f) => f.k === 'heal'));
  assert.equal(bestHealSkill(new Set([healer.id]), SKILLS), healer.id);
  assert.equal(bestHealSkill(new Set(), SKILLS), null);
});

test('salvamento: tudo do Sábio volta igual, fica separado por jogador e cabe na propriedade', () => {
  const a = freshSage();
  toLevel(a, 7);
  a.xp = 5; a.analyses['minecraft:zombie'] = { typeId: 'minecraft:zombie', name: 'zombie', count: 2 };
  a.qa.push({ q: 'oi', a: 'resposta', day: 3 }); a.settings.sound = false; a.coreGiven = true; a.notified.near_raphael = true;
  const back = deserializeSage(serializeSage(a));
  assert.equal(back.level, 7); assert.equal(back.xp, 5); assert.equal(back.settings.sound, false); assert.equal(back.coreGiven, true);
  assert.equal(back.analyses['minecraft:zombie'].count, 2); assert.equal(back.qa.length, 1); assert.ok(back.notified.near_raphael);
  const b = freshSage();
  assert.equal(b.level, 1, 'outro jogador começa do zero');
  a.settings.saveQa = false;
  assert.equal(deserializeSage(serializeSage(a)).qa.length, 0, 'histórico não salvo quando desligado');
  // muito grande → corta conversas antigas e continua < 32767
  a.settings.saveQa = true;
  for (let i = 0; i < 400; i++) a.analyses[`minecraft:m${i}`] = { typeId: `minecraft:m${i}`, name: 'x'.repeat(40), count: 1, first: i };
  for (let i = 0; i < 30; i++) a.qa.push({ q: 'q'.repeat(100), a: 'a'.repeat(600), day: 1 });
  assert.ok(serializeSage(a).length < 32767);
  assert.equal(deserializeSage('{quebrado').corrupted, true);
  // dados do jogador continuam sem o Sábio dentro (propriedades separadas)
  const d = player();
  assert.ok(!('sage' in JSON.parse(serialize(d))));
  assert.equal(deserialize(serialize(d)).race, 'slime');
});

test('painel: tela inicial 3x3 + Configurações; páginas com Voltar; botão GRANDE SÁBIO no menu', () => {
  const d = player();
  const home = buildSageForm({ cat: 'home', d, sage: d.sage });
  assert.equal(home.buttons.length, SAGE_COUNT);
  assert.ok(home.title.startsWith(`${SAGE_MARK}§0`));
  assert.equal(SAGE_TILES.length, 9);
  SAGE_TILES.forEach((c, i) => {
    assert.deepEqual(home.hit[SAGE.tile0 + i], c === 'manifest' ? { type: 'manifest' } : { type: 'cat', cat: c });
    assert.ok(home.buttons[SAGE.tile0 + i].text, c);
  });
  assert.deepEqual(home.hit[SAGE.settings], { type: 'cat', cat: 'settings' });
  assert.deepEqual(home.hit[SAGE.back], { type: 'back' });
  assert.match(home.body, /Grande Sábio/);
  for (const cat of SAGE_CATS) {
    const m = buildSageForm({ cat, d, sage: d.sage });
    assert.equal(m.buttons.length, SAGE_COUNT);
    assert.ok(m.title.startsWith(`${SAGE_MARK}§${SAGE_CATS.indexOf(cat) + 1}`), cat);
    assert.deepEqual(m.hit[SAGE.back], { type: 'cat', cat: 'home' }, 'Voltar leva à tela inicial');
    assert.ok(!(SAGE.tile0 in m.hit), 'blocos só na tela inicial');
    assert.ok(m.body.length > 20, cat);
  }
  const lvl = buildSageForm({ cat: 'level', d, sage: d.sage });
  assert.match(lvl.body, /Requisitos para Raphael/);
  assert.ok(!Object.values(lvl.hit).some((a) => a.type === 'evolve'), 'sem botão de evoluir antes dos requisitos');
  assert.match(buildSageForm({ cat: 'next', d, sage: d.sage }).body, /Ciel[\s\S]*Condições de desbloqueio/);
  assert.match(buildSageForm({ cat: 'magic', d, sage: d.sage }).body, /Suas magias/);
  // Raphael muda a saudação, o retrato e o emblema
  const r = { ...d.sage, stage: 'raphael' };
  const rh = buildSageForm({ cat: 'home', d, sage: r });
  assert.match(rh.body, /Raphael/);
  assert.match(rh.buttons[SAGE.portrait].icon, /portrait_raphael/);
  assert.match(rh.buttons[SAGE.emblem].icon, /icon_raphael/);
  const menu = buildMenuForm({ tab: 'status', d, state: stateOf(d) });
  assert.deepEqual(menu.hit[MENU.sage], { type: 'sage' });
  assert.match(menu.buttons[MENU.sage].text, /GRANDE SÁBIO/);
});

test('resource pack: tela do Sábio no roteador, texturas de estágio, item Núcleo', () => {
  const ui = readFileSync(new URL('ui/tensura_tree.json', RP), 'utf8');
  assert.ok(ui.includes('"sage_root"'));
  const sf = readFileSync(new URL('ui/server_form.json', RP), 'utf8');
  assert.ok(sf.includes('tensura_sage_wrap'));
  for (const s of ['sage', 'raphael', 'ciel']) {
    assert.ok(existsSync(new URL(`textures/tensura/sage/portrait_${s}.png`, RP)), s);
    assert.ok(existsSync(new URL(`textures/tensura/sage/icon_${s}.png`, RP)), s);
  }
  for (const c of [...SAGE_CATS, ...SAGE_TILES]) assert.ok(existsSync(new URL(`textures/tensura/sage/cat_${c}_on.png`, RP)), c);
  assert.ok(existsSync(new URL('textures/entity/tensura/great_sage_raphael.png', RP)));
  assert.ok(existsSync(new URL('textures/entity/tensura/great_sage_ciel.png', RP)));
  const ent = JSON.parse(readFileSync(new URL('entities/great_sage.json', BP), 'utf8').replace(/^\/\/.*$/gm, ''));
  assert.deepEqual(ent['minecraft:entity'].description.properties['tensura:stage'].values, ['sage', 'raphael', 'ciel']);
  const rc = readFileSync(new URL('render_controllers/great_sage.render_controllers.json', RP), 'utf8');
  assert.ok(rc.includes('Array.skins'));
  assert.ok(existsSync(new URL('items/sage_core.json', BP)));
  assert.ok(readFileSync(new URL('textures/item_texture.json', RP), 'utf8').includes('tensura_sage_core'));
  // simulação da tela: os 8 botões de categoria aparecem
  const { page } = previewHtml(modelFor(scenario('sage-home')), { root: 'sage_root' });
  for (const label of ['Conversar', 'Analisar', 'Status', 'Magias', 'Evolução', 'Memória', 'Configurações']) assert.ok(page.includes(label), label);
});
