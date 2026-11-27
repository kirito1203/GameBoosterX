// Fábrica de habilidades da árvore do Rimuru (linhagem Slime).
// Todas as IDs ficam em `r.slime.*` (únicas no registro; o registro recusa duplicatas).
import { SR } from '../../dsl.js';
import { CONFIG } from '../../../config.js';
import { RC } from '../../raceDsl.js';

export const SECTORS = {
  body: 'Corpo de Slime',
  absorption: 'Absorção',
  sage: 'Análise e Grande Sábio',
  resist: 'Resistências',
  regen: 'Regeneração',
  bodymanip: 'Manipulação Corporal',
  magic: 'Magia',
  spirit: 'Energia Espiritual',
  unique: 'Habilidades Únicas',
  supreme: 'Habilidades Supremas',
  evolution: 'Evolução Racial',
  mod: 'Expansões do Mod',
};
// Cor de cada categoria (cristais da árvore e destaques).
export const SECTOR_COLORS = {
  body: '#7fd36a', absorption: '#c060ff', sage: '#4fc8ff', resist: '#c8c8d0', regen: '#ff7a9a', bodymanip: '#ffb347',
  magic: '#3aa0ff', spirit: '#5effc8', unique: '#40d8ff', supreme: '#ffd000', evolution: '#ff5a3a', mod: '#b0b0c0',
};
export const SECTOR_ORDER = Object.keys(SECTORS);

/** Cria uma habilidade do setor `sector`. */
export function K(sector) {
  return (id, name, original, rank, src, o = {}) => SR(`r.slime.${id}`, name, original, rank, src, CONFIG.ranks, {
    sector, cat: `${RC} · ${SECTORS[sector]}`, ...o,
  });
}
export const P = (id) => `r.slime.${id}`;
