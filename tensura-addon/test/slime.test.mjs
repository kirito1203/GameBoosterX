// Árvore completa do Rimuru (Slime): catálogo, requisitos, absorção, missões, domínio,
// exclusividade por raça, salvamento, Grande Sábio e interface (visão geral).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { CONFIG } from '../BP/scripts/config.js';
import { SKILLS, TREES, RACES, SECTORS, MISSIONS, ABSORPTION, REPLACES, initialSkillsFor } from '../BP/scripts/data/registry.js';
import { SOURCES } from '../BP/scripts/data/dsl.js';
import { evaluate, unlock } from '../BP/scripts/core/progression.js';
import { computeStats, barSlots } from '../BP/scripts/core/stats.js';
import { masteryLevel, masteryMax, masteryMults } from '../BP/scripts/core/mastery.js';
import { missionProgress, missionsFor } from '../BP/scripts/core/missions.js';
import { discoverable } from '../BP/scripts/data/skill_trees/slime/absorption.js';
import { fresh, serialize, deserialize } from '../BP/scripts/core/save.js';
import { freshSage } from '../BP/scripts/core/sage.js';
import { ask } from '../BP/scripts/core/sageAsk.js';
import { buildTreeForm, toOverview, fromOverview, acquisition } from '../BP/scripts/core/view.js';
import { getMap } from '../BP/scripts/core/map.js';
import { layerBase, CTRL } from '../BP/scripts/core/uiSpec.js';

const TREE = TREES['race.slime'];
const ids = TREE.skills.map((s) => s.id);
const P = (x) => `r.slime.${x}`;

function slime(extra = {}) {
  const d = fresh();
  d.race = 'slime'; d.level = 100; d.points = 99999;
  for (const id of initialSkillsFor('slime')) d.unlocked.add(id);
  d.sage = freshSage();
  Object.assign(d, extra);
  d.stats = computeStats(d);
  return d;
}
const stateOf = (d) => ({ race: d.race, level: d.level, points: d.points, unlocked: d.unlocked, flags: d.flags,
  maxMp: d.stats?.maxMp ?? 0, absorbed: new Set(d.absorbed), discovered: d.discovered, missions: d.missions, mastery: d.mastery });

test('catálogo: 9 setores, ranks F..EX+, origens separadas, IDs únicas e todas as habilidades pedidas', () => {
  assert.ok(ids.length >= 110, `nós: ${ids.length}`);
  const sectors = new Set(TREE.skills.filter((s) => s.sector).map((s) => s.sector));
  assert.deepEqual([...sectors].sort(), [...SECTORS.slime.order].sort());
  for (const s of TREE.skills) {
    if (s.type === 'race_root') continue;
    assert.ok(CONFIG.ranks[s.rank], `${s.id} rank`);
    assert.ok(SOURCES[s.src], `${s.id} origem`);
    assert.ok(s.sector, `${s.id} setor`);
  }
  assert.equal(new Set(ids).size, ids.length);
  const names = new Set(TREE.skills.map((s) => s.name.split(' (')[0].split(' — ')[0]));
  for (const n of ['Dissolver', 'Absorver', 'Regeneração Acelerada', 'Regeneração Infinita', 'Transformação Universal', 'Fios Universais',
    'Percepção Universal', 'Anulação da Dor', 'Resistência Elétrica', 'Resistência à Paralisia', 'Resistência Térmica', 'Anulação de Ataques de Fogo',
    'Anulação de Ataques Físicos', 'Anulação de Efeitos Naturais', 'Anulação de Condições Anormais', 'Anulação de Ataques Mentais',
    'Resistência a Ataques Espirituais', 'Resistência a Ataques Sagrados e Demoníacos', 'Predador', 'Predação', 'Estômago', 'Análise', 'Isolamento',
    'Imitação', 'Glutonaria', 'Beelzebuth', 'Corrosão', 'Receber', 'Fornecer', 'Cadeia Alimentar', 'Consumo de Almas', 'Grande Sábio',
    'Aceleração do Pensamento', 'Análise e Avaliação', 'Processamento Paralelo', 'Anulação de Encantamento', 'Toda a Criação', 'Decompositor',
    'Síntese', 'Separação', 'Raphael', 'Ciel', 'Fios Pegajosos', 'Fios de Aço', 'Armadura Corporal', 'Sopro Venenoso', 'Ondas Ultrassônicas',
    'Manipulação das Chamas', 'Corpo de Chamas', 'Clonagem', 'Relâmpago Negro', 'Movimento das Sombras', 'Comunicação Mental', 'Superolfato',
    'Intimidação', 'Manipulação da Água', 'Lâmina de Água', 'Chama Negra', 'Trovão Negro', 'Manipulação Molecular', 'Megiddo', 'Controle Óptico',
    'Controle de Barreiras', 'Barreira Múltipla', 'Movimento Espacial', 'Síntese de Poções', 'Síntese de Poções Completas',
    'Transferência Espiritual', 'Controle de Magia', 'Controle de Aura', 'Alteração Elemental', 'Uriel', 'Prisão Infinita', 'Dominação Espacial',
    'Defesa Absoluta', 'Manipulação de Leis', 'Veldora', 'Corredor de Almas', 'Manifestação do Dragão da Tempestade', 'Liberação do Dragão da Tempestade',
    'Azathoth', 'Colapso da Nihilidade', 'Espaço Imaginário', 'Existência Paralela Múltipla', 'Domínio Espaçotemporal', 'Barreira Multidimensional',
    'Liberação de Dragões Verdadeiros', 'Nucleação de Dragões Verdadeiros', 'Shub-Niggurath', 'Criação de Habilidades', 'Duplicação de Habilidades',
    'Concessão de Habilidades', 'Armazenamento de Habilidades', 'Forma Ultimate Slime', 'Predador Absoluto', 'Análise Dimensional',
    'Regeneração de Nihilidade', 'Domínio de Magicules', 'Assimilação Suprema', 'Metamorfose Suprema', 'Percepção Multidimensional',
    'Barreira de Nihilidade']) assert.ok(names.has(n), `ausente: ${n}`);
  // Expansões só no setor do mod e só com origem 'mod'.
  for (const s of TREE.skills.filter((x) => x.sector === 'mod')) assert.equal(s.src, 'mod', s.id);
  // Habilidades sem certeza ficam marcadas como NÃO verificadas (aviso na tela), sem afirmar anime.
  assert.equal(SKILLS[P('shub')].verified, false);
  assert.equal(SKILLS[P('ultimate_slime')].verified, false);
  assert.equal(SKILLS[P('azathoth')].src, 'ln');
  assert.ok(!TREE.skills.some((s) => s.src === 'pendente'));
});

test('exclusividade: outra raça não aprende a árvore do Slime; Slime não aprende as universais duplicadas', () => {
  const human = { race: 'human', level: 100, points: 99999, unlocked: new Set(['r.human.root']), flags: new Set() };
  assert.equal(evaluate(P('predator'), human).state, 'locked');
  const d = slime();
  assert.match(evaluate('uni.predator', stateOf(d)).missing.join(), /raça não pode/);
  assert.ok(evaluate('uni.predator', { ...human, unlocked: new Set(['r.human.root', 'uni.soul']) }).missing.every((m) => !/raça/.test(m)));
  // Equivalência: a versão do Slime satisfaz pré-requisitos universais (ex.: Analista exige Grande Sábio).
  d.unlocked.add(P('great_sage')); d.unlocked.add('uni.soul');
  assert.ok(!evaluate('uni.analyst', stateOf(d)).missing.some((m) => m.includes('Grande Sábio')));
});

test('migração: Slime que já tinha a versão universal passa a ter a do Slime (sem perder nada)', () => {
  const d = fresh(); d.race = 'slime'; d.unlocked = new Set(['r.slime.root', 'uni.predator', 'ult.raphael']); d.equipped = ['ext.water_blade'];
  d.unlocked.add('ext.water_blade');
  const back = deserialize(serialize(d));
  assert.ok(back.unlocked.has(P('predator')) && !back.unlocked.has('uni.predator'));
  assert.ok(back.unlocked.has(P('raphael')));
  assert.deepEqual(back.equipped, [P('water_blade')]);
  assert.ok(Object.keys(REPLACES.slime).every((u) => SKILLS[u].blockedRaces.includes('slime')));
});

test('progressão difícil: não dá para pular para supremas/finais só com pontos', () => {
  const d = slime();
  const st = stateOf(d);
  for (const id of [P('beelzebuth'), P('raphael'), P('uriel'), P('veldora'), P('azathoth'), P('shub'), P('ciel')]) {
    assert.ok(!evaluate(id, st).ok, `${id} liberada cedo`);
    assert.equal(unlock(id, st).ok, false);
  }
  // Mesmo com todos os pré-requisitos, faltam os requisitos especiais (vermelho).
  const all = new Set(d.unlocked);
  for (const s of TREE.skills) for (const r of s.requires) all.add(r);
  all.delete(P('beelzebuth'));
  const ev = evaluate(P('beelzebuth'), { ...st, unlocked: all, race: 'demon_slime' });
  assert.equal(ev.state, 'special');
  assert.ok(ev.missing.some((m) => m.includes('Domínio de Predação')));
  assert.ok(ev.missing.some((m) => m.includes('Magicules máximas')));
});

test('requisitos especiais: missão, descoberta, absorção, domínio, Magicules e estágio', () => {
  const d = slime();
  d.unlocked.add(P('predator')); d.unlocked.add(P('predation'));
  let st = stateOf(d);
  let ev = evaluate(P('gluttony'), st);
  assert.equal(ev.state, 'special');
  assert.ok(ev.missing.some((m) => m.includes('Missão: O Desastre Orc')));
  d.missions.add('m.slime.orc_disaster');
  d.mastery[P('predation')] = 70; // domínio 4
  assert.equal(masteryLevel(P('predation'), 70), 4);
  assert.ok(evaluate(P('gluttony'), stateOf(d)).ok);
  // Descoberta por absorção
  d.unlocked.add(P('shapeshift'));
  assert.ok(evaluate(P('sticky_thread'), stateOf(d)).missing.some((m) => m.includes('Descobrir')));
  d.discovered.add(P('sticky_thread'));
  assert.ok(evaluate(P('sticky_thread'), stateOf(d)).ok);
  // Absorção de espécie específica (Ifrit → Blaze)
  for (const r of SKILLS[P('fire_null')].requires) d.unlocked.add(r);
  d.unlocked.add(P('electric_resist'));
  assert.ok(evaluate(P('fire_null'), stateOf(d)).missing.some((m) => m.includes('blaze')));
  d.absorbed.push('minecraft:blaze');
  assert.ok(evaluate(P('fire_null'), stateOf(d)).ok);
  // Estágio racial
  for (const r of SKILLS[P('infinite_regen')].requires) d.unlocked.add(r);
  st = stateOf(d);
  assert.ok(evaluate(P('infinite_regen'), st).missing.some((m) => m.includes('Evolução racial')));
});

test('absorção: banco registra origem, método e descobre só o que for compatível', () => {
  for (const e of ABSORPTION.slime) {
    assert.ok(e.work && e.mc.length && e.skills.length && e.method && e.compat, e.id);
    for (const id of [...e.skills, ...e.evolutions]) assert.ok(SKILLS[id], id);
    for (const id of e.skills) assert.ok(SKILLS[id].discover || id === P('fire_null'), `${id} deve exigir descoberta`);
  }
  const wolf = discoverable('minecraft:wolf', new Set());
  assert.ok(wolf.find((x) => x.id === P('shadow_motion')).ok);
  assert.equal(wolf.find((x) => x.id === P('black_lightning')).ok, false, 'Relâmpago Negro exige Glutonaria');
  const blaze = discoverable('minecraft:blaze', new Set());
  assert.ok(blaze.every((x) => !x.ok), 'Ifrit exige Predador');
  assert.equal(discoverable('minecraft:pig', new Set()).length, 0);
});

test('missões: progresso com dados reais e conclusão única', () => {
  const d = slime({ level: 20 });
  const m = MISSIONS['m.slime.orc_disaster'];
  assert.equal(missionProgress(m, d).done, false);
  d.kills['minecraft:piglin_brute'] = 1;
  assert.equal(missionProgress(m, d).done, true);
  assert.equal(missionsFor('slime').length, 11);
  assert.equal(missionsFor('human').length, 0);
  const ab = MISSIONS['m.slime.abundance'];
  assert.match(missionProgress(ab, d).parts[0].label, /Habilidades desbloqueadas/);
  for (const s of TREE.skills) if (s.needMission) assert.ok(MISSIONS[s.needMission], s.id);
});

test('domínio: níveis limitados, sem evolução infinita, custo e recarga menores', () => {
  const s = SKILLS[P('predation')];
  assert.equal(masteryMax(s), 5);
  assert.equal(masteryLevel(s.id, 0), 1);
  assert.equal(masteryLevel(s.id, 1e9), 5);
  assert.ok(masteryMults(s.id, 150).cost < 1);
  assert.equal(masteryMax(SKILLS[P('great_sage')]), 0, 'passiva sem domínio');
});

test('efeitos novos entram nos atributos (anulação, imunidades, recarga, espaços, absorção)', () => {
  const d = slime();
  for (const id of [P('fire_null'), P('abnormal_null'), P('parallel'), P('skill_storage'), P('predator'), P('universal_shapeshift')]) d.unlocked.add(id);
  const st = computeStats(d);
  d.stats = st;
  assert.equal(st.resist.fire, 1);
  assert.ok(st.cleanse.has('poison') && st.cleanse.has('wither'));
  assert.ok(st.cdMult < 1);
  assert.equal(barSlots(d), CONFIG.ui.barSlots + 3);
  assert.equal(st.absorbMult, 1.25);
  assert.ok(st.mimicNoDrain);
});

test('salvamento: descobertas, missões, domínio e absorções voltam iguais', () => {
  const d = slime();
  d.discovered.add(P('sticky_thread')); d.missions.add('m.slime.shizu'); d.mastery[P('predation')] = 12;
  d.absorbLog['minecraft:wolf'] = 3; d.absorbTotal = 3;
  const b = deserialize(serialize(d));
  assert.ok(b.discovered.has(P('sticky_thread')) && b.missions.has('m.slime.shizu'));
  assert.equal(b.mastery[P('predation')], 12);
  assert.equal(b.absorbTotal, 3);
  assert.equal(b.absorbLog['minecraft:wolf'], 3);
  assert.ok(serialize(d).length < 32000);
});

test('Grande Sábio conhece a árvore: absorções, missões, combinações e domínio', () => {
  const d = slime();
  d.sage.level = 10;
  const ctx = { d, sage: d.sage, state: stateOf(d), target: null };
  assert.match(ask('Quais criaturas posso absorver?', ctx).text, /Fios Pegajosos/);
  assert.match(ask('Quais missões faltam?', ctx).text, /Desastre Orc/);
  assert.match(ask('Quais habilidades podem ser combinadas?', ctx).text, /→/);
  assert.match(ask('Qual o domínio das minhas habilidades?', ctx).text, /domínio|Domínio/);
});

test('interface: setores separados, método de aquisição e visão geral (zoom)', () => {
  const map = getMap('slime');
  const xs = {};
  for (const s of TREE.skills) if (s.sector && map.pos[s.id]) (xs[s.sector] ??= []).push(map.pos[s.id][0]);
  const ranges = SECTORS.slime.order.map((k) => [Math.min(...xs[k]), Math.max(...xs[k])]);
  for (let i = 1; i < ranges.length; i++) assert.ok(ranges[i][0] > ranges[i - 1][1], 'setores lado a lado, sem sobrepor');
  assert.match(acquisition(SKILLS[P('sticky_thread')]), /absorver spider/);
  assert.match(acquisition(SKILLS[P('gluttony')]), /missão/);
  const d = slime();
  const v = { x: -20, y: -10 };
  assert.deepEqual(fromOverview(toOverview(v)), v);
  const f = buildTreeForm({ state: stateOf(d), view: toOverview(v), mp: { cur: 0, max: 1 }, zoom: 3 });
  assert.equal(f.buttons.length, layerBase().total);
  assert.deepEqual(f.hit[CTRL.zoom], { type: 'zoom', zoom: 1 });
  assert.ok(Object.values(f.hit).some((h) => h.type === 'zoomin'));
  const n = buildTreeForm({ state: stateOf(d), view: v, mp: { cur: 0, max: 1 } });
  assert.deepEqual(n.hit[CTRL.zoom], { type: 'zoom', zoom: 3 });
});
