// Árvore racial completa do Slime (Rimuru Tempest): 9 setores.
// Reutilizável: outra raça cria data/skill_trees/<linhagem>/ com o mesmo formato.
import body from './body.js';
import resist from './resist.js';
import predator from './predator.js';
import sage from './sage.js';
import absorbed from './absorbed.js';
import techniques from './techniques.js';
import supreme from './supreme.js';
import final from './final.js';
import expansions from './expansions.js';

import { SECTORS as SECTORS_NAMES, SECTOR_ORDER as SECTOR_ORDER_LIST } from './common.js';
export { SECTORS, SECTOR_ORDER, SECTOR_COLORS } from './common.js';
export { ABSORPTION } from './absorption.js';
export { MISSIONS } from './missions.js';

const ALL = [...body, ...resist, ...predator, ...sage, ...absorbed, ...techniques, ...supreme, ...final, ...expansions];

// ── Organização visual em 12 categorias (só posição/categoria; IDs e requisitos não mudam) ──
const P = (x) => `r.slime.${x}`;
const GROUPS = {
  unique: ['predator', 'great_sage', 'degenerate', 'gluttony', 'infinity_prison'],
  absorption: ['absorb', 'predation', 'stomach', 'isolation', 'corrosion', 'receive', 'provide', 'devourer', 'soul_consume', 'shapeshift',
    'sticky_thread', 'steel_thread', 'body_armor', 'poison_breath', 'poison_resist', 'heat_sense', 'paralyzing_breath', 'ultrasonic',
    'vampirism', 'flame_manip', 'flame_body', 'clone', 'shadow_motion', 'thought_comm', 'ultra_smell', 'coercion', 'black_lightning'],
  sage: ['analyze', 'appraisal', 'thought_accel', 'parallel', 'chant_annul', 'all_creation', 'synthesis', 'separation', 'alteration', 'ciel'],
  regen: ['self_regen', 'ultraspeed_regen', 'infinite_regen', 'potion', 'full_potion'],
  bodymanip: ['human_form', 'universal_shapeshift', 'universal_thread'],
  body: ['dissolve', 'magic_sense', 'universal_perception'],
  magic: ['water_manip', 'water_blade', 'molecular', 'optical', 'megiddo', 'black_flame', 'black_thunder', 'barrier_control',
    'multilayer_barrier', 'spatial_motion', 'elemental_shift'],
  spirit: ['magic_control', 'aura_control', 'spirit_transfer', 'storm_bond'],
  supreme: ['raphael', 'beelzebuth', 'uriel', 'veldora', 'velgrynd', 'azathoth', 'shub', 'imaginary_space', 'spatial_domination',
    'absolute_defense', 'law_manip', 'universal_barrier', 'soul_corridor', 'storm_dragon', 'true_dragon_release', 'storm_restoration',
    'scorch_breath', 'scorch_dragon', 'scorch_release', 'scorch_restoration', 'soul_gluttony', 'nihility_collapse', 'parallel_existence',
    'spacetime', 'multidim_barrier', 'dragon_release', 'dragon_nucleation', 'skill_creation', 'skill_duplication', 'skill_grant', 'skill_storage'],
  evolution: ['evo_demon_slime', 'demon_body', 'evo_god_slime', 'ultimate_slime'],
};
const byId = new Map(ALL.map((s) => [s.id, s]));
for (const [sector, ids] of Object.entries(GROUPS)) {
  for (const x of ids) {
    const s = byId.get(P(x));
    if (!s) throw new Error(`categoria ${sector}: ${x} não existe`);
    s.sector = sector;
    if (!s.tier || s.tier !== 'manas') s.category = `Racial · ${SECTORS_NAMES[sector]}`;
  }
}
// Layout: cada nó fica embaixo de um nó da MESMA categoria (pré-requisito, se houver; senão a "cabeça").
// A cabeça de cada categoria pende da raiz. As linhas continuam mostrando os pré-requisitos reais.
const heads = {};
for (const s of ALL) if (s.sector && !heads[s.sector]) heads[s.sector] = s.id;
for (const s of ALL) {
  if (!s.sector) continue;
  if (heads[s.sector] === s.id) { s.layoutParent = 'r.slime.root'; continue; }
  const same = s.requires.find((r) => byId.get(r)?.sector === s.sector);
  if (same) s.layoutParent = same;
  else if (!s.layoutParent || byId.get(s.layoutParent)?.sector !== s.sector) s.layoutParent = heads[s.sector];
}

export const SLIME_SKILLS = SECTOR_ORDER_LIST.flatMap((sec) => ALL.filter((s) => s.sector === sec)).concat(ALL.filter((s) => !s.sector));
export const SLIME_LINKS = [];

// Árvore Suprema (tela própria): colunas na ordem abaixo; cada coluna mostra a Suprema e suas sub-habilidades.
// Ciel fica na última coluna, como categoria MANAS (não é Suprema).
// Cor/identidade de cada Suprema (núcleo exclusivo na árvore). Ciel: MANAS.
export const SUPREME_COLORS = {
  'r.slime.raphael': '#ffd34a', 'r.slime.beelzebuth': '#b0306a', 'r.slime.uriel': '#cfe8ff', 'r.slime.veldora': '#3ee0ff',
  'r.slime.velgrynd': '#ff6a20', 'r.slime.azathoth': '#7a3cff', 'r.slime.shub': '#62d060', 'r.slime.ciel': '#ff9ee8',
};
export const SLIME_SUPREME_ORDER = ['r.slime.raphael', 'r.slime.beelzebuth', 'r.slime.uriel', 'r.slime.veldora', 'r.slime.velgrynd',
  'r.slime.azathoth', 'r.slime.shub', 'r.slime.ciel'];

// Habilidades universais iguais às do Rimuru: proibidas para o Slime (ele tem as próprias) e
// convertidas nos dados de quem já as tinha (sem perder progresso e sem efeito em dobro).
export const SLIME_REPLACES = {
  'uni.great_sage': 'r.slime.great_sage',
  'uni.predator': 'r.slime.predator',
  'uni.gluttony': 'r.slime.gluttony',
  'uni.degenerate': 'r.slime.degenerate',
  'ult.raphael': 'r.slime.raphael',
  'ult.beelzebuth': 'r.slime.beelzebuth',
  'ult.uriel': 'r.slime.uriel',
  'ult.azathoth': 'r.slime.azathoth',
  'ext.shadow_motion': 'r.slime.shadow_motion',
  'ext.spatial_motion': 'r.slime.spatial_motion',
  'ext.spatial_domination': 'r.slime.spatial_domination',
  'ext.ultrasonic': 'r.slime.ultrasonic',
  'ext.universal_detection': 'r.slime.universal_perception',
  'ext.multilayer_barrier': 'r.slime.multilayer_barrier',
  'ext.water_manipulation': 'r.slime.water_manip',
  'ext.water_blade': 'r.slime.water_blade',
};
