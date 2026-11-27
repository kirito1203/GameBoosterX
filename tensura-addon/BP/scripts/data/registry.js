// Registro central: junta todas as árvores e indexa as habilidades.
import elemental from './trees/elemental.js';
import aspect from './trees/aspect.js';
import holy from './trees/holy.js';
import summon from './trees/summon.js';
import illusion from './trees/illusion.js';
import spirit from './trees/spirit.js';
import necromancy from './trees/necromancy.js';
import dark from './trees/dark.js';
import advanced from './trees/advanced.js';
import unique from './trees/unique.js';
import ultimate from './trees/ultimate.js';
import extra from './trees/extra.js';
import resist from './trees/resist.js';
import { RACIAL_TREES } from './trees/racial.js';
import { RACES, LINEAGES } from './races.js';
import { SLIME_REPLACES, MISSIONS as SLIME_MISSIONS, ABSORPTION as SLIME_ABSORPTION, SECTORS as SLIME_SECTORS, SECTOR_ORDER as SLIME_SECTOR_ORDER } from './skill_trees/slime/index.js';

// Ordem das abas na interface. A primeira aba é sempre a árvore racial do jogador.
export const UNIVERSAL_TREES = [elemental, aspect, holy, summon, illusion, spirit, necromancy, dark, advanced, unique, ultimate, extra, resist];
export const TAB_ORDER = ['racial', ...UNIVERSAL_TREES.map((t) => t.id)];

export const TREES = {};
export const SKILLS = {};

for (const t of [...UNIVERSAL_TREES, ...RACIAL_TREES]) {
  TREES[t.id] = t;
  for (const s of t.skills) {
    if (SKILLS[s.id]) throw new Error(`ID de habilidade duplicado: ${s.id}`);
    SKILLS[s.id] = s;
  }
}

// Filhos (evoluções/próximos nós) calculados a partir dos pré-requisitos.
for (const id in SKILLS) SKILLS[id].children = [];
for (const id in SKILLS) {
  for (const r of SKILLS[id].requires) {
    if (SKILLS[r]) SKILLS[r].children.push(id);
  }
}

// ── Árvores raciais completas ──
// Equivalências: habilidade universal → versão própria da linhagem. A universal fica proibida para
// essa linhagem e a versão própria conta como pré-requisito no lugar dela (sem duplicar efeitos).
export const REPLACES = { slime: SLIME_REPLACES };
for (const [lin, map] of Object.entries(REPLACES)) {
  for (const [uni, own] of Object.entries(map)) {
    if (!SKILLS[uni] || !SKILLS[own]) throw new Error(`Equivalência inválida: ${uni} → ${own}`);
    if (!SKILLS[uni].blockedRaces.includes(lin)) SKILLS[uni].blockedRaces.push(lin);
  }
}
// Setores visuais de cada árvore racial (ordem = botões de salto).
export const SECTORS = { slime: { names: SLIME_SECTORS, order: SLIME_SECTOR_ORDER } };
export const MISSIONS = {};
for (const m of SLIME_MISSIONS) MISSIONS[m.id] = m;
export const ABSORPTION = { slime: SLIME_ABSORPTION };

export function racialTreeId(lineage) {
  return `race.${lineage}`;
}

export function treeForTab(tab, lineage) {
  return tab === 'racial' ? TREES[racialTreeId(lineage)] : TREES[tab];
}

/** Habilidades a conceder quando o jogador se torna `raceId`. */
export function initialSkillsFor(raceId) {
  const lin = RACES[raceId].lineage;
  return TREES[racialTreeId(lin)].skills.filter((s) => s.initial === raceId).map((s) => s.id);
}

export { RACES, LINEAGES };
