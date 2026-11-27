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
  };
}

const PASSIVE_KINDS = new Set(['p_effect', 'p_mp', 'p_resist', 'p_lifesteal', 'p_onhit', 'p_affinity',
  'p_power', 'p_starved', 'p_xp', 'p_points', 'p_regen_env', 'p_race']);

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
