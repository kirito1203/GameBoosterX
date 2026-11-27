import { S, defineTree } from '../dsl.js';

// Habilidades Extras (Extra Skills) — nomes da obra; efeitos adaptados.
const C = 'Habilidade Extra';

export default defineTree('extra', {
  name: 'Habilidades Extras', original: 'Extra Skills', short: 'Extras', kind: 'universal',
  root: 'ext.magic_aura', category: C,
}, [
  S('ext.magic_aura', 'Aura Mágica', 'Magic Aura', 'common', {
    cost: 10, lvl: 2, origin: 'adaptado', major: true, cat: C,
    desc: 'Controle básico da aura: +30 de Aura máxima.', fx: { k: 'p_aura', max: 30 } }),
  // Movimento
  S('ext.shadow_motion', 'Movimento das Sombras', 'Shadow Motion', 'rare', {
    cost: 40, lvl: 8, req: ['ext.magic_aura'], arm: 'N', mp: 12, cd: 5, cat: `${C} · Movimento`,
    desc: 'Mergulha nas sombras e reaparece até 14 blocos à frente.', fx: { k: 'blink', range: 14 } }),
  S('ext.spatial_motion', 'Movimento Espacial', 'Spatial Motion', 'epic', {
    cost: 80, lvl: 22, req: ['ext.shadow_motion'], mp: 25, cd: 6, cat: `${C} · Movimento`,
    desc: 'Teleporte instantâneo até 30 blocos à frente.', fx: { k: 'blink', range: 30 } }),
  S('ext.spatial_domination', 'Domínio Espacial', 'Spatial Domination', 'legendary', {
    cost: 140, lvl: 34, req: ['ext.spatial_motion'], mp: 90, cd: 120, cat: `${C} · Espaço`,
    desc: 'Domina o espaço ao redor por 15 s: inimigos não conseguem sair e ficam lentos.',
    fx: { k: 'field', el: 'space', r: 10, dur: 15, self: true, confine: true, enemyEff: [['slowness', 2, 2]] } }),
  S('ext.ultrasonic', 'Ondas Ultrassônicas', 'Ultrasonic Waves', 'common', {
    cost: 20, lvl: 4, req: ['ext.shadow_motion'], mp: 10, cd: 8, cat: `${C} · Sentidos`,
    desc: 'Emite ondas que atordoam as criaturas próximas.', fx: { k: 'debuff', el: 'wind', r: 6, self: true, dmg: 2, eff: [['nausea', 6, 0], ['slowness', 3, 1]] } }),
  S('ext.universal_detection', 'Detecção Universal', 'Universal Detection', 'epic', {
    cost: 70, lvl: 18, req: ['ext.ultrasonic'], mp: 15, cd: 15, cat: `${C} · Sentidos`,
    desc: 'Percebe tudo num raio de 64 blocos.', fx: [{ k: 'sense', r: 64 }, { k: 'buff', eff: [['night_vision', 60, 0]] }] }),
  // Corpo
  S('ext.body_armor', 'Armadura Corporal', 'Body Armor', 'rare', {
    cost: 40, lvl: 8, req: ['ext.magic_aura'], arm: 'E', cat: `${C} · Corpo`,
    desc: 'A aura endurece o corpo: resistência física permanente.', fx: { k: 'p_resist', groups: { physical: 0.2 } } }),
  S('ext.instant_regen', 'Regeneração Instantânea', 'Instant Regeneration', 'rare', {
    cost: 50, lvl: 12, req: ['ext.body_armor'], mp: 20, cd: 20, cat: `${C} · Corpo`,
    desc: 'Recupera 12 de vida na hora.', fx: { k: 'heal', amount: 12 } }),
  S('ext.ultraspeed_regen', 'Regeneração Ultra-veloz', 'Ultraspeed Regeneration', 'epic', {
    cost: 90, lvl: 24, req: ['ext.instant_regen'], cat: `${C} · Corpo`,
    desc: 'Regeneração II permanente.', fx: { k: 'p_effect', eff: [['regeneration', 1]] } }),
  S('ext.multilayer_barrier', 'Barreira Multicamadas', 'Multilayer Barrier', 'epic', {
    cost: 80, lvl: 20, req: ['ext.body_armor'], mp: 35, cd: 40, cat: `${C} · Defesa`,
    desc: 'Várias camadas de barreira: absorção IV e resistência II por 30 s.', fx: { k: 'buff', eff: [['absorption', 30, 3], ['resistance', 30, 1]] } }),
  S('ext.dragon_haki', 'Haki do Espírito do Dragão', 'Dragon Spirit Haki', 'legendary', {
    cost: 150, lvl: 32, req: ['ext.multilayer_barrier'], mp: 70, cd: 90, cat: `${C} · Pressão`,
    desc: 'Pressão esmagadora: criaturas ao redor ficam fracas, lentas e aterrorizadas.',
    fx: { k: 'debuff', el: 'dragon', r: 12, self: true, dmg: 6, eff: [['weakness', 10, 2], ['slowness', 10, 2], ['nausea', 8, 0]] } }),
  // Elementos
  S('ext.flame_manipulation', 'Manipulação de Chamas', 'Flame Manipulation', 'rare', {
    cost: 40, lvl: 10, req: ['ext.magic_aura'], arm: 'S', cat: `${C} · Fogo`,
    desc: 'Domínio das chamas: magias de fogo 20% mais fortes e imunidade a queimaduras leves.',
    fx: [{ k: 'p_power', el: 'fire', mult: 1.2 }, { k: 'p_resist', groups: { fire: 0.3 } }] }),
  S('ext.black_flame', 'Chama Negra', 'Black Flame', 'epic', {
    cost: 90, lvl: 22, req: ['ext.flame_manipulation'], mp: 35, cd: 8, cat: `${C} · Fogo`,
    desc: 'Chamas negras que queimam com wither.', fx: { k: 'cone', el: 'dark', dmg: 14, range: 10, ignite: 6, eff: [['wither', 5, 1]] } }),
  S('ext.water_manipulation', 'Manipulação da Água', 'Water Manipulation', 'rare', {
    cost: 40, lvl: 8, req: ['ext.magic_aura'], arm: 'W', cat: `${C} · Água`,
    desc: 'Domínio da água: magias de água e gelo 20% mais fortes e respiração aquática na água.',
    fx: [{ k: 'p_power', el: 'water', mult: 1.2 }, { k: 'p_power', el: 'ice', mult: 1.2 }, { k: 'p_regen_env', water: [['water_breathing', 0]] }] }),
  S('ext.water_blade', "Lâmina d'Água", 'Water Blade', 'common', {
    cost: 25, lvl: 6, req: ['ext.water_manipulation'], mp: 8, cd: 2, cat: `${C} · Água`,
    desc: 'Lâmina de água em alta pressão que corta em linha.', fx: { k: 'beam', el: 'water', dmg: 9, range: 16, pierce: true } }),
  S('ext.water_bullet', "Bala d'Água", 'Water Bullet', 'common', {
    cost: 20, lvl: 4, req: ['ext.water_manipulation'], mp: 5, cd: 1, cat: `${C} · Água`,
    desc: 'Dispara projéteis de água comprimida.', fx: { k: 'bolt', el: 'water', dmg: 5, range: 20, speed: 1.6, count: 3, spread: 6, knock: 0.4 } }),
  S('ext.steel_thread', 'Fio de Aço', 'Steel Thread', 'rare', {
    cost: 35, lvl: 10, req: ['ext.water_blade'], mp: 12, cd: 4, cat: `${C} · Fios`,
    desc: 'Fio afiado como aço que corta tudo em linha.', fx: { k: 'beam', el: 'physical', dmg: 10, range: 14, pierce: true } }),
  S('ext.sticky_thread', 'Fio de Aço Pegajoso', 'Sticky Steel Thread', 'rare', {
    cost: 40, lvl: 12, req: ['ext.steel_thread'], mp: 15, cd: 12, cat: `${C} · Fios`,
    desc: 'Fios pegajosos prendem o alvo mirado.', fx: { k: 'bind', el: 'physical', range: 16, dur: 6, dps: 0 } }),
  S('ext.black_lightning', 'Relâmpago Negro', 'Black Lightning', 'epic', {
    cost: 100, lvl: 24, req: ['ext.flame_manipulation'], mp: 50, cd: 15, cat: `${C} · Relâmpago`,
    desc: 'Relâmpago negro devastador no alvo mirado.', fx: { k: 'beam', el: 'lightning', dmg: 22, range: 28, pierce: true } }),
  S('ext.poison_breath', 'Sopro Venenoso', 'Poisonous Breath', 'common', {
    cost: 25, lvl: 6, req: ['ext.water_manipulation'], mp: 10, cd: 6, cat: `${C} · Sopro`,
    desc: 'Sopro de névoa venenosa em cone.', fx: { k: 'cone', el: 'acid', dmg: 3, range: 7, eff: [['poison', 6, 1]] } }),
  S('ext.paralyze_breath', 'Sopro Paralisante', 'Paralyzing Breath', 'rare', {
    cost: 40, lvl: 12, req: ['ext.poison_breath'], mp: 15, cd: 10, cat: `${C} · Sopro`,
    desc: 'Sopro que paralisa as criaturas à frente.', fx: { k: 'cone', el: 'acid', dmg: 2, range: 7, eff: [['slowness', 4, 6], ['mining_fatigue', 4, 3]] } }),
  S('ext.self_replication', 'Autorreplicação', 'Self-Replication', 'epic', {
    cost: 90, lvl: 22, req: ['ext.spatial_motion'], mp: 50, cd: 60, cat: `${C} · Movimento`,
    desc: 'Cria uma cópia sua que distrai os monstros por 40 s.', fx: { k: 'summon', entity: 'tensura:doppelganger', count: 1, dur: 40 } }),
]);
