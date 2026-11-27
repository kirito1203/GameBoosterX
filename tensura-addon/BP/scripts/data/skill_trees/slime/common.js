// Fábrica de habilidades da árvore do Rimuru (linhagem Slime).
// Todas as IDs ficam em `r.slime.*` (únicas no registro; o registro recusa duplicatas).
import { SR } from '../../dsl.js';
import { CONFIG } from '../../../config.js';
import { RC } from '../../raceDsl.js';

export const SECTORS = {
  body: 'Corpo e Natureza',
  resist: 'Resistências e Imunidades',
  predator: 'Predador e Evoluções',
  sage: 'Grande Sábio, Raphael e Ciel',
  absorbed: 'Habilidades por Absorção',
  tech: 'Técnicas Pessoais',
  supreme: 'Habilidades Supremas',
  final: 'Poderes Finais',
  mod: 'Expansões do Mod',
};
export const SECTOR_ORDER = Object.keys(SECTORS);

/** Cria uma habilidade do setor `sector`. */
export function K(sector) {
  return (id, name, original, rank, src, o = {}) => SR(`r.slime.${id}`, name, original, rank, src, CONFIG.ranks, {
    sector, cat: `${RC} · ${SECTORS[sector]}`, ...o,
  });
}
export const P = (id) => `r.slime.${id}`;
