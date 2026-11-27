// Grande Sábio: progressão própria (estágio, nível, XP), capacidades e requisitos de evolução.
// Puro: recebe os dados do jogador e devolve resultados. Testável em Node.
import { CONFIG } from '../config.js';
import { RACES } from '../data/registry.js';

export const STAGES = {
  sage: { name: 'Grande Sábio', original: 'Great Sage', next: 'raphael', color: '§b', origin: 'obra' },
  raphael: { name: 'Raphael', original: 'Raphael, Lord of Wisdom', next: 'ciel', color: '§6', origin: 'obra' },
  ciel: { name: 'Ciel', original: 'Ciel', next: null, color: '§d', origin: 'obra' },
};
export const STAGE_ORDER = ['sage', 'raphael', 'ciel'];

export function freshSage() {
  return {
    stage: 'sage', level: 1, xp: 0, evolutions: [],
    analyses: {},        // typeId -> { name, count, first, hp, hostile, boss }
    items: {},           // typeId -> { count, first }
    kills: {},           // typeId -> true (primeira vitória registrada)
    qa: [],              // [{ q, a, day }]
    settings: { sound: true, notify: true, saveQa: true, frequency: 'normal' },
    daily: { day: -1, repeatAnalysis: 0, question: 0 },
    notified: {},        // chaves de notificações já enviadas (evita repetição)
    coreGiven: false,    // item Núcleo do Grande Sábio já entregue
  };
}

export function levelCap(stage) {
  return CONFIG.sage.levelCaps[stage];
}

/** XP para subir do nível `level` para o seguinte. */
export function xpToNext(level) {
  return CONFIG.sage.xpBase + (level - 1) * CONFIG.sage.xpPerLevel;
}

/**
 * Concede XP respeitando o limite do estágio. Retorna os níveis ganhos.
 * No limite, o XP excedente não acumula (incentiva evoluir).
 */
export function addXp(sage, amount) {
  if (amount <= 0) return 0;
  const cap = levelCap(sage.stage);
  if (sage.level >= cap) { sage.xp = 0; return 0; }
  sage.xp += amount;
  let ups = 0;
  while (sage.level < cap && sage.xp >= xpToNext(sage.level)) {
    sage.xp -= xpToNext(sage.level);
    sage.level++;
    ups++;
  }
  if (sage.level >= cap) sage.xp = 0;
  return ups;
}

/** Limite diário de recompensas repetitivas. `day` = dia do mundo. */
export function takeDaily(sage, kind, day) {
  if (sage.daily.day !== day) sage.daily = { day, repeatAnalysis: 0, question: 0 };
  const cap = CONFIG.sage.dailyCaps[kind];
  if (sage.daily[kind] >= cap) return false;
  sage.daily[kind]++;
  return true;
}

// ── Capacidades: cada uma tem efeito real no addon (ver systems/sage.js) ──
// effect: chave usada pelos sistemas; stage/level: quando desbloqueia.
export const CAPABILITIES = [
  // Estágio 1 — Grande Sábio
  { id: 'analysis', name: 'Análise', cat: 'Análise', stage: 'sage', level: 1, desc: 'Analisa a criatura mirada: nome, tipo, vida e hostilidade. Registra na memória.' },
  { id: 'qa', name: 'Responder perguntas', cat: 'Processamento', stage: 'sage', level: 1, desc: 'Responde perguntas sobre raça, nível, habilidades, fraquezas e evolução usando seus dados reais.' },
  { id: 'status', name: 'Gerenciamento de status', cat: 'Gerenciamento de status', stage: 'sage', level: 2, desc: 'Relatório completo de atributos, recursos e progresso.' },
  { id: 'item_analysis', name: 'Análise de itens', cat: 'Análise', stage: 'sage', level: 3, desc: 'Analisa o item na mão: tipo, durabilidade, encantamentos e categoria (arma, armadura, ferramenta).' },
  { id: 'memory', name: 'Memória', cat: 'Memória', stage: 'sage', level: 4, desc: 'Guarda o histórico de análises e descobertas para consulta.' },
  { id: 'skill_query', name: 'Consulta de habilidades e magias', cat: 'Gerenciamento de habilidades', stage: 'sage', level: 6, desc: 'Lista habilidades e magias disponíveis, bloqueadas e o que falta para cada uma.' },
  { id: 'threat', name: 'Avaliação de ameaça', cat: 'Avaliação', stage: 'sage', level: 8, desc: 'Classifica o nível de ameaça das criaturas analisadas e alerta sobre chefes por perto.' },
  { id: 'combat_hud', name: 'Assistência de combate I', cat: 'Assistência de combate', stage: 'sage', level: 12, desc: 'Ao golpear uma criatura, mostra a vida restante dela na tela.' },
  { id: 'thought_accel', name: 'Pensamento Acelerado', cat: 'Processamento', stage: 'sage', level: 16, desc: 'Regeneração de Magicules +10%.', bonus: { regenMult: 1.1 } },
  { id: 'recommend', name: 'Recomendações', cat: 'Previsões e recomendações', stage: 'sage', level: 20, desc: 'Recomenda as próximas habilidades com base na sua raça, pontos e requisitos.' },
  { id: 'evo_eval', name: 'Avaliação de evolução', cat: 'Evolução', stage: 'sage', level: 25, desc: 'Relatório do que falta para a próxima evolução racial e do próprio Grande Sábio.' },
  // Estágio 2 — Raphael
  { id: 'adv_analysis', name: 'Análise avançada', cat: 'Análise', stage: 'raphael', level: 1, desc: 'Análises mostram efeitos ativos, EP concedido e, em jogadores, raça e nível.' },
  { id: 'parallel', name: 'Processamento paralelo', cat: 'Processamento', stage: 'raphael', level: 5, desc: 'Recargas de todas as habilidades 10% mais curtas.', bonus: { cooldownMult: 0.9 } },
  { id: 'mp_opt', name: 'Otimização de Magicules', cat: 'Gerenciamento de magias', stage: 'raphael', level: 10, desc: 'Magias custam 8% menos Magicules.', bonus: { mpMult: 0.92 } },
  { id: 'combat_hud2', name: 'Assistência de combate II', cat: 'Assistência de combate', stage: 'raphael', level: 15, desc: 'O alerta de combate também mostra a fraqueza conhecida do alvo (mortos-vivos e sagrado).' },
  { id: 'lord_wisdom', name: 'Senhor da Sabedoria', cat: 'Processamento', stage: 'raphael', level: 30, desc: 'Regeneração de Magicules +15% (acumula com Pensamento Acelerado).', bonus: { regenMult: 1.15 } },
  // Estágio 3 — Ciel
  { id: 'prediction', name: 'Previsão', cat: 'Previsões e recomendações', stage: 'ciel', level: 1, desc: 'Avisa quando sua vida fica abaixo de 30% e indica a melhor habilidade de cura que você possui.' },
  { id: 'auto_heal', name: 'Gerenciamento autônomo', cat: 'Assistência de combate', stage: 'ciel', level: 10, desc: 'Com vida abaixo de 25%, usa automaticamente sua melhor habilidade de cura (recarga própria de 60 s).' },
  { id: 'manas', name: 'Manas', cat: 'Processamento', stage: 'ciel', level: 25, desc: 'Todas as magias causam 10% mais dano.', bonus: { power: 1.1 } },
];

export const CAPABILITY_CATEGORIES = ['Análise', 'Avaliação', 'Processamento', 'Gerenciamento de habilidades', 'Gerenciamento de magias',
  'Síntese', 'Assistência de combate', 'Previsões e recomendações', 'Gerenciamento de status', 'Memória', 'Evolução'];

function stageIndex(s) { return STAGE_ORDER.indexOf(s); }

export function hasCapability(sage, capId) {
  const c = CAPABILITIES.find((x) => x.id === capId);
  if (!c) return false;
  const si = stageIndex(sage.stage);
  const ci = stageIndex(c.stage);
  return si > ci || (si === ci && sage.level >= c.level);
}

export function capabilityState(sage, c) {
  if (hasCapability(sage, c.id)) return { unlocked: true, missing: [] };
  const missing = [];
  if (stageIndex(sage.stage) < stageIndex(c.stage)) missing.push(`Estágio ${STAGES[c.stage].name}`);
  missing.push(`Nível ${c.level} em ${STAGES[c.stage].name}`);
  return { unlocked: false, missing };
}

/** Bônus combinados das capacidades desbloqueadas (aplicados pelos sistemas). */
export function sageBonuses(sage) {
  const b = { regenMult: 1, cooldownMult: 1, mpMult: 1, power: 1 };
  for (const c of CAPABILITIES) {
    if (!c.bonus || !hasCapability(sage, c.id)) continue;
    for (const k in c.bonus) b[k] *= c.bonus[k];
  }
  return b;
}

/**
 * Requisitos para evoluir ao próximo estágio.
 * @param {object} sage dados do Grande Sábio
 * @param {object} d dados do jogador (race, level, flags, unlocked)
 */
export function evolutionRequirements(sage, d) {
  const next = STAGES[sage.stage].next;
  if (!next) return null;
  const species = Object.keys(sage.analyses).length;
  const raceStage = RACES[d.race]?.stage ?? 0;
  const reqs = next === 'raphael'
    ? [
      { label: `Grande Sábio no nível máximo (${levelCap('sage')})`, ok: sage.level >= levelCap('sage') },
      { label: 'Jogador no nível 35', ok: d.level >= 35, progress: Math.min(1, d.level / 35) },
      { label: `20 espécies analisadas (${species})`, ok: species >= 20, progress: Math.min(1, species / 20) },
      { label: 'Derrotar um chefe (Wither, Ender Dragon ou Warden)', ok: ['killed_wither', 'killed_ender_dragon', 'killed_warden'].some((f) => d.flags.has(f)) },
    ]
    : [
      { label: `Raphael no nível máximo (${levelCap('raphael')})`, ok: sage.level >= levelCap('raphael') },
      { label: 'Jogador no nível 60', ok: d.level >= 60, progress: Math.min(1, d.level / 60) },
      { label: `60 espécies analisadas (${species})`, ok: species >= 60, progress: Math.min(1, species / 60) },
      { label: 'Derrotar o Wither e o Ender Dragon', ok: d.flags.has('killed_wither') && d.flags.has('killed_ender_dragon') },
      { label: 'Raça no 3º estágio de evolução (ou superior)', ok: raceStage >= 2 },
    ];
  // Slime (Rimuru): a evolução do Sábio também exige o nó correspondente na árvore racial.
  if (RACES[d.race]?.lineage === 'slime') {
    const node = next === 'raphael' ? 'r.slime.raphael' : 'r.slime.ciel';
    reqs.push({ label: `Nó "${next === 'raphael' ? 'Raphael' : 'Ciel'}" desbloqueado na árvore do Slime`, ok: d.unlocked.has(node) });
  }
  for (const r of reqs) if (r.progress === undefined) r.progress = r.ok ? 1 : 0;
  return { next, reqs, ready: reqs.every((r) => r.ok), progress: reqs.reduce((a, r) => a + r.progress, 0) / reqs.length };
}

/** Evolui se — e somente se — os requisitos forem cumpridos. */
export function evolve(sage, d, day = 0) {
  const ev = evolutionRequirements(sage, d);
  if (!ev || !ev.ready) return null;
  sage.stage = ev.next;
  sage.level = 1;
  sage.xp = 0;
  sage.evolutions.push({ stage: ev.next, day });
  return ev.next;
}

/** Nível de ameaça (texto) a partir dos dados reais da criatura. */
export function threatLevel({ maxHp = 0, hostile = false, boss = false }) {
  if (boss) return { level: 5, label: '§4Calamidade (chefe)' };
  if (!hostile) return { level: 0, label: '§aInofensiva' };
  if (maxHp >= 80) return { level: 4, label: '§cMuito alta' };
  if (maxHp >= 40) return { level: 3, label: '§6Alta' };
  if (maxHp >= 20) return { level: 2, label: '§eModerada' };
  return { level: 1, label: '§2Baixa' };
}

// ── Salvamento (propriedade dinâmica própria: tensura:sage) ──
const MAX_CHARS = 30000; // limite da propriedade dinâmica é 32767

export function serializeSage(s) {
  const o = {
    stage: s.stage, level: s.level, xp: s.xp, evolutions: s.evolutions, analyses: s.analyses, items: s.items,
    kills: s.kills, qa: s.settings.saveQa ? s.qa : [], settings: s.settings, daily: s.daily, notified: s.notified, coreGiven: s.coreGiven,
  };
  let out = JSON.stringify(o);
  // Se crescer demais, descarta primeiro as conversas mais antigas, depois as análises mais antigas.
  while (out.length > MAX_CHARS && o.qa.length) { o.qa = o.qa.slice(1); out = JSON.stringify(o); }
  if (out.length > MAX_CHARS) {
    const keys = Object.keys(o.analyses).sort((a, b) => (o.analyses[a].first ?? 0) - (o.analyses[b].first ?? 0));
    while (out.length > MAX_CHARS && keys.length) { delete o.analyses[keys.shift()]; out = JSON.stringify(o); }
  }
  return out;
}

export function deserializeSage(raw) {
  const s = freshSage();
  if (typeof raw !== 'string') return s;
  try {
    const o = JSON.parse(raw);
    if (STAGES[o.stage]) s.stage = o.stage;
    if (Number.isFinite(o.level)) s.level = Math.max(1, Math.min(levelCap(s.stage), Math.floor(o.level)));
    if (Number.isFinite(o.xp)) s.xp = Math.max(0, o.xp);
    s.evolutions = Array.isArray(o.evolutions) ? o.evolutions : [];
    s.analyses = o.analyses && typeof o.analyses === 'object' ? o.analyses : {};
    s.items = o.items && typeof o.items === 'object' ? o.items : {};
    s.kills = o.kills && typeof o.kills === 'object' ? o.kills : {};
    s.qa = Array.isArray(o.qa) ? o.qa.slice(-CONFIG.sage.historyLimit) : [];
    s.settings = { ...s.settings, ...(o.settings ?? {}) };
    if (o.daily && Number.isFinite(o.daily.day)) s.daily = { ...s.daily, ...o.daily };
    s.notified = o.notified && typeof o.notified === 'object' ? o.notified : {};
    s.coreGiven = !!o.coreGiven;
  } catch {
    s.corrupted = true;
  }
  return s;
}

/** Capacidades que passaram a valer ao ir de (stage, level) antigos para os atuais. */
export function newlyUnlocked(before, sage) {
  return CAPABILITIES.filter((c) => !hasCapability(before, c.id) && hasCapability(sage, c.id));
}

/** Próxima capacidade a desbloquear (no estágio atual ou no próximo). */
export function nextCapability(sage) {
  const si = stageIndex(sage.stage);
  return CAPABILITIES.filter((c) => !hasCapability(sage, c.id))
    .sort((a, b) => stageIndex(a.stage) - stageIndex(b.stage) || a.level - b.level)
    .find((c) => stageIndex(c.stage) >= si) ?? null;
}

/** Melhor habilidade de cura ativa que o jogador possui (para Previsão / Gerenciamento autônomo). */
export function bestHealSkill(unlocked, SKILLS_) {
  let best = null;
  let bestVal = 0;
  for (const id of unlocked) {
    const s = SKILLS_[id];
    if (!s || s.type !== 'active') continue;
    const fx = Array.isArray(s.fx) ? s.fx : s.fx ? [s.fx] : [];
    for (const f of fx) {
      if (f.k !== 'heal') continue;
      const v = f.full ? 1000 : f.amount ?? 0;
      if (v > bestVal) { bestVal = v; best = id; }
    }
  }
  return best;
}
