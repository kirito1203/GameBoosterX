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

export { SECTORS, SECTOR_ORDER } from './common.js';
export { ABSORPTION } from './absorption.js';
export { MISSIONS } from './missions.js';

export const SLIME_SKILLS = [...body, ...resist, ...predator, ...sage, ...absorbed, ...techniques, ...supreme, ...final, ...expansions];
export const SLIME_LINKS = [];

// Árvore Suprema (tela própria): colunas na ordem abaixo; cada coluna mostra a Suprema e suas sub-habilidades.
// Ciel fica na última coluna, como categoria MANAS (não é Suprema).
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
