// Mini-DSL para declarar habilidades e árvores de forma compacta.
// Nenhum código aqui depende do Minecraft: tudo é testável em Node.

/**
 * Origem do conteúdo:
 *  - 'obra'     : nome presente em Tensura (efeito adaptado para o Minecraft).
 *  - 'adaptado' : conceito da obra com nome/efeito adaptado pelo addon.
 *  - 'original' : criado pelo addon (não existe em Tensura).
 */
export const ORIGINS = {
  obra: 'Tensura (efeito adaptado ao Minecraft)',
  adaptado: 'Inspirado em Tensura (adaptação do addon)',
  original: 'Conteúdo original do addon',
};

/**
 * Origem detalhada (catálogo do Rimuru e futuras árvores raciais completas).
 * Nunca misturar: uma habilidade só é 'anime' se aparece no anime; 'ln' se é da Light Novel etc.
 * Na dúvida: 'pendente'.
 */
export const SOURCES = {
  anime: 'ANIME',
  manga: 'MANGA',
  ln: 'LIGHT_NOVEL',
  mod: 'MOD_EXPANSION',
};
const SRC_TO_ORIGIN = { anime: 'obra', manga: 'obra', ln: 'obra', mod: 'original' };
// Classe da habilidade (como na obra): única, suprema, Manas…
export const TIERS = { unique: 'Habilidade Única', ultimate: 'Habilidade Suprema', manas: 'MANAS' };

/**
 * Cria a definição de uma habilidade.
 * @param {string} id  ID único (ex.: 'elem.fireball')
 * @param {string} name Nome em português
 * @param {string} original Nome original (inglês)
 * @param {'common'|'rare'|'epic'|'legendary'|'supreme'} rarity
 * @param {object} o opções (ver campos abaixo)
 */
export function S(id, name, original, rarity, o = {}) {
  return {
    id,
    name,
    original,
    rarity,
    desc: o.desc ?? '',
    cost: o.cost ?? 10,               // pontos de habilidade
    mp: o.mp ?? 0,                    // custo em Magicules por uso
    cooldown: o.cd ?? 0,              // segundos
    minLevel: o.lvl ?? 1,
    requires: o.req ?? [],            // pré-requisitos (todos obrigatórios) — são as linhas da árvore
    excludes: o.excl ?? [],           // escolhas exclusivas (ex.: Mystic Oni x Wicked Oni)
    needCount: o.needCount ?? 0,      // nº mínimo de habilidades desbloqueadas na mesma árvore
    needFlags: o.flags ?? [],         // conquistas (ex.: 'killed_wither')
    needEpic: o.needEpic ?? 0,        // nº mínimo de habilidades épicas+ desbloqueadas (qualquer árvore)
    races: o.races ?? null,           // linhagens permitidas (null = todas)
    blockedRaces: o.blocked ?? [],    // linhagens proibidas
    origin: o.origin ?? 'obra',
    major: o.major ?? false,          // nó maior / habilidade importante
    type: o.type ?? (o.fx && isPassiveFx(o.fx) ? 'passive' : 'active'),
    fx: o.fx ?? null,                 // efeito funcional (ver systems/effects.js)
    arm: o.arm ?? null,               // direção do ramo quando filho da raiz: N, S, E, W
    layoutParent: o.lp ?? null,       // pai de layout explícito (opcional)
    tree: null,                       // preenchido por defineTree
    icon: o.icon ?? null,
    category: o.cat ?? '',
    initial: o.initial ?? false,      // concedida ao se tornar a raça (árvores raciais)
    evolveTo: o.evolveTo ?? null,     // nós de evolução racial
    // ── Campos das árvores raciais completas (opcionais) ──
    rank: o.rank ?? null,             // F, E, D, C, B, A, S, SS, EX (CONFIG.ranks)
    src: o.src ?? null,               // SOURCES
    sector: o.sector ?? null,         // setor da árvore (ex.: 'body')
    needMp: o.needMp ?? 0,            // Magicules MÁXIMAS necessárias
    needAbsorb: o.needAbsorb ?? null, // { any: [typeId...] } e/ou { count: n } espécies absorvidas
    discover: o.discover ?? false,    // precisa ser descoberta (absorção) antes de desbloquear
    needMission: o.mission ?? null,   // id de missão especial concluída
    needMastery: o.needMastery ?? null, // { skillId: nível de domínio }
    needStage: o.needStage ?? null,   // estágio racial mínimo (0, 1, 2…)
    masteryMax: o.masteryMax ?? null, // nível máximo de domínio (padrão: pelo rank)
    masteryDesc: o.masteryDesc ?? null, // texto por nível de domínio
    acquire: o.acquire ?? null,       // texto do método de aquisição (gerado se ausente)
    evolvesTo: o.evolvesTo ?? [],     // evoluções possíveis (texto informativo)
    verified: o.unverified ? false : true, // origem/nomes confirmados? (false = aviso na tela)
    first: o.first ?? null,           // primeira aparição conhecida (só quando confirmada)
    limits: o.limits ?? '',           // limitações / diferenças da versão do addon
    lore: o.lore ?? '',               // relações com outras habilidades (texto)
    tier: o.tier ?? null,             // TIERS
    subs: o.subs ?? [],               // sub-habilidades (ids de outros nós)
    tags: o.tags ?? [],               // ex.: 'defense', 'space' (requisitos de domínio por categoria)
    evolvedBy: o.evolvedBy ?? null,   // quando o jogador tem este nó, o efeito evolui
    evolvedFx: o.evolvedFx ?? null,
    evolvedDesc: o.evolvedDesc ?? '',
    needMasteryTag: o.needMasteryTag ?? null, // { tag: nível } — algum nó com a tag nesse domínio
    needUltimate: o.needUltimate ?? 0,        // nº de Habilidades Supremas desbloqueadas
  };
}

/**
 * Habilidade com rank: custo, nível e raridade padrão vêm de CONFIG.ranks (sobrescrevíveis).
 * @param {string} rank
 * @param {string} src  chave de SOURCES
 */
export function SR(id, name, original, rank, src, ranks, o = {}) {
  const r = ranks[rank];
  if (!r) throw new Error(`Rank inválido em ${id}: ${rank}`);
  if (!SOURCES[src]) throw new Error(`Origem inválida em ${id}: ${src}`);
  return S(id, name, original, o.rarity ?? r.rarity, {
    cost: r.cost, lvl: r.lvl, origin: SRC_TO_ORIGIN[src], ...o, rank, src,
  });
}

const PASSIVE_KINDS = new Set(['p_effect', 'p_mp', 'p_resist', 'p_lifesteal', 'p_onhit', 'p_affinity',
  'p_power', 'p_starved', 'p_xp', 'p_points', 'p_regen_env', 'p_race', 'p_aura',
  'p_cleanse', 'p_cd', 'p_slots', 'p_absorb', 'p_mimic', 'p_op', 'p_dodge', 'p_counter']);

export function isPassiveFx(fx) {
  const list = Array.isArray(fx) ? fx : [fx];
  return list.every((f) => PASSIVE_KINDS.has(f.k));
}

/**
 * Define uma árvore.
 * @param {string} id
 * @param {object} meta { name, original, short, kind: 'universal'|'racial', root, color }
 * @param {object[]} skills
 * @param {object[]} links nós de outras árvores exibidos aqui: [{ id, arm? }]
 */
export function defineTree(id, meta, skills, links = []) {
  for (const s of skills) {
    s.tree = id;
    if (!s.category) s.category = meta.category ?? meta.name;
  }
  return { id, ...meta, skills, links };
}
