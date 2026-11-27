// SETOR III — PREDADOR E EVOLUÇÕES (Predador → Glutonaria → Beelzebuth)
import { K, P } from './common.js';

const k = K('predator');

export default [
  k('predator', 'Predador', 'Predator', 'S', 'anime', {
    req: [P('absorb')], lp: P('root'), cost: 150, lvl: 10, major: true, mission: 'm.slime.first_meals',
    desc: 'Habilidade Única do Rimuru. Aumenta em 25% as Magicules obtidas em absorções e +40 Magicules máximas. Abre Predação, Estômago, Análise, Isolamento e Imitação.',
    fx: [{ k: 'p_absorb', mult: 1.25 }, { k: 'p_mp', max: 40 }] }),
  k('predation', 'Predação', 'Predation', 'C', 'anime', {
    req: [P('predator')], mp: 15, cd: 4, masteryMax: 5,
    masteryDesc: ['Absorção básica', 'Melhoria na eficiência', 'Maior capacidade de absorção', 'Novas possibilidades de análise', 'Libera a evolução (Glutonaria/Beelzebuth), se os requisitos forem cumpridos'],
    desc: 'Devora a criatura mirada com até 40% de vida a 8 blocos: +3 Magicules máximas (até +300), registra e descobre habilidades.',
    fx: { k: 'absorb', range: 8, hpPct: 0.4, mpGain: 3, mpCap: 300 } }),
  k('stomach', 'Estômago', 'Stomach', 'C', 'anime', {
    req: [P('predator')], mp: 5, cd: 2, desc: 'Guarda itens num espaço interno (armazenamento pessoal).', fx: { k: 'storage' } }),
  k('analyze', 'Análise', 'Analysis', 'D', 'anime', {
    req: [P('predator')], mp: 2, cd: 1,
    desc: 'Analisa a criatura mirada (vida, tipo, efeitos, EP) e registra na memória do Grande Sábio.', fx: { k: 'analyze', range: 32 } }),
  k('isolation', 'Isolamento', 'Isolation', 'D', 'anime', {
    req: [P('predator')], mp: 10, cd: 20, desc: 'Isola e neutraliza o que é nocivo: remove todos os efeitos negativos de você.',
    fx: { k: 'cleanse', negative: true } }),
  k('gluttony', 'Glutonaria', 'Gluttony', 'S', 'anime', {
    req: [P('predation')], lvl: 22, cost: 170, mission: 'm.slime.orc_disaster', needMastery: { [P('predation')]: 4 }, mp: 25, cd: 6, major: true,
    desc: 'Evolução do Predador após devorar o Desastre Orc. Absorve criaturas com até 50% de vida a 10 blocos (+4 Magicules, até +400).',
    fx: { k: 'absorb', range: 10, hpPct: 0.5, mpGain: 4, mpCap: 400 } }),
  k('corrosion', 'Corrosão', 'Corrosion', 'B', 'anime', {
    req: [P('gluttony')], mp: 25, cd: 12, desc: 'Corrói tudo ao redor (raio 6): dano ácido, veneno e wither.',
    fx: { k: 'nova', el: 'acid', r: 6, dmg: 10, self: true, eff: [['poison', 5, 1], ['wither', 3, 0]] } }),
  k('receive', 'Receber', 'Receive', 'B', 'anime', {
    req: [P('gluttony')], desc: 'Recebe parte do poder dos vencidos: +15% de EP e +1 de regeneração de Magicules.',
    fx: [{ k: 'p_xp', mult: 1.15 }, { k: 'p_mp', regen: 1 }] }),
  k('provide', 'Fornecer', 'Provide', 'B', 'anime', {
    req: [P('gluttony')], mp: 40, cd: 60, desc: 'Fornece poder aos aliados próximos (10 blocos) por 30 s: força, resistência e regeneração.',
    fx: { k: 'buff', allies: 10, eff: [['strength', 30, 0], ['resistance', 30, 0], ['regeneration', 30, 0]] } }),
  k('beelzebuth', 'Beelzebuth', 'Beelzebuth, Lord of Gluttony', 'SS', 'ln', {
    req: [P('gluttony'), P('evo_demon_slime')], needMastery: { [P('predation')]: 5 }, needMp: 900, mp: 40, cd: 5, major: true,
    desc: 'Habilidade Suprema, Senhor da Gula: devora criaturas com até 70% de vida a 16 blocos (+8 Magicules, até +800).',
    fx: { k: 'absorb', range: 16, hpPct: 0.7, mpGain: 8, mpCap: 800 } }),
  k('devourer', 'Devorador (Cadeia Alimentar)', 'Food Chain', 'A', 'pendente', {
    req: [P('beelzebuth')], desc: 'Ao derrotar criaturas: recupera fome e 20 Magicules; 5% do dano corpo a corpo vira vida.',
    fx: [{ k: 'p_starved', mp: 20, noStrength: true }, { k: 'p_lifesteal', pct: 0.05 }] }),
  k('soul_consume', 'Consumo de Almas', 'Soul Consumption', 'SS', 'pendente', {
    req: [P('beelzebuth')], mp: 120, cd: 90, desc: 'Consome as almas num raio de 10: dano de alma, cura e +40 Magicules.',
    fx: { k: 'drain', el: 'soul', r: 10, dmg: 20, heal: 0.6, mpGain: 40 } }),
];
