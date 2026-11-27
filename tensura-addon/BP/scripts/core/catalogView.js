// Catálogo de Habilidades: grade de emblemas (estilo catálogo) com nome e descrição curta, por categoria.
// Puro (testável). Tocar num item abre a habilidade na árvore.
import { SKILLS, TREES, RACES } from '../data/registry.js';
import { evaluate, raceAllowed } from './progression.js';
import {
  CATALOG, CATALOG_CATS, CATALOG_PAGE, CATALOG_COUNT, CATALOG_MARK, TILE_STATE, TEX, iconName,
} from './uiSpec.js';

export const CATALOG_LABEL = {
  race: 'Sua Raça', unique: 'Habilidades Únicas', ultimate: 'Supremas e Manas', magic: 'Magias e Elementos',
  combat: 'Combate e Técnicas', extra: 'Habilidades Extras', resist: 'Resistências',
};
const MAGIC = ['elemental', 'aspect', 'holy', 'summon', 'illusion', 'spirit', 'necromancy', 'dark', 'advanced'];

/** Habilidades de uma categoria visíveis para a raça do jogador (sem as proibidas para ela). */
export function catalogSkills(cat, state) {
  const lin = RACES[state.race].lineage;
  const racial = TREES[`race.${lin}`].skills;
  let list;
  if (cat === 'race') list = racial.filter((s) => s.type !== 'race_root' && !s.tier);
  else if (cat === 'unique') list = [...racial.filter((s) => s.tier === 'unique'), ...TREES.unique.skills];
  else if (cat === 'ultimate') list = [...racial.filter((s) => s.tier === 'ultimate' || s.tier === 'manas'), ...TREES.ultimate.skills];
  else if (cat === 'magic') list = MAGIC.flatMap((t) => TREES[t].skills);
  else if (cat === 'combat') list = TREES.combat.skills;
  else if (cat === 'extra') list = TREES.extra.skills;
  else list = [...racial.filter((s) => s.sector === 'resist'), ...TREES.resist.skills];
  return list.filter((s) => raceAllowed(s, state));
}

/** Descrição curta (primeira frase, até 24 caracteres). */
export function shortDesc(s) {
  const first = (s.desc || '').split(/(?<=[.!?:])\s/)[0].replace(/\s+/g, ' ');
  const t = first.replace(/[:.]$/, ''); return t.length <= 24 ? t : `${t.slice(0, 23).trimEnd()}…`;
}

export function buildCatalogForm({ cat = 'race', page = 0, state }) {
  const buttons = Array.from({ length: CATALOG_COUNT }, () => ({ text: '', icon: '' }));
  const hit = {};
  const set = (i, text, icon = '') => { buttons[i] = { text, icon }; };
  set(CATALOG.close, '', `${TEX.menu}close`); hit[CATALOG.close] = { type: 'close' };
  set(CATALOG.back, '§fVoltar', `${TEX.menu}back`); hit[CATALOG.back] = { type: 'back' };
  CATALOG_CATS.forEach((c, i) => {
    set(CATALOG.cat0 + i, `${c === cat ? '§b' : '§f'}${CATALOG_LABEL[c]}`, `${TEX.ui}${c === 'race' ? 'tab_racial' : `tab_${c === 'magic' ? 'elemental' : c}`}`);
    hit[CATALOG.cat0 + i] = { type: 'cat', cat: c };
  });
  const list = catalogSkills(cat, state);
  const pages = Math.max(1, Math.ceil(list.length / CATALOG_PAGE));
  const pg = Math.min(Math.max(0, page), pages - 1);
  const own = list.filter((s) => state.unlocked.has(s.id)).length;
  set(CATALOG.title, `§l${CATALOG_LABEL[cat]}§r  §7${own}/${list.length} desbloqueadas`);
  set(CATALOG.page, `§f${pg + 1}/${pages}`);
  if (pages > 1) {
    set(CATALOG.prev, '', `${TEX.ui}arrow_l`); hit[CATALOG.prev] = { type: 'page', page: (pg - 1 + pages) % pages };
    set(CATALOG.next, '', `${TEX.ui}arrow_r`); hit[CATALOG.next] = { type: 'page', page: (pg + 1) % pages };
  }
  list.slice(pg * CATALOG_PAGE, (pg + 1) * CATALOG_PAGE).forEach((s, i) => {
    const ev = evaluate(s.id, state);
    const mark = ev.state === 'unlocked' ? TILE_STATE.unlocked : ev.ok ? TILE_STATE.available : TILE_STATE.locked;
    const nameCol = ev.state === 'unlocked' ? '§a' : ev.ok ? '§b' : '§7';
    set(CATALOG.tile0 + i, `${mark}${nameCol}§l${s.name}§r\n§7${shortDesc(s)}`, `${TEX.art}${iconName(s.id)}`);
    hit[CATALOG.tile0 + i] = { type: 'skill', id: s.id };
  });
  return { title: `${CATALOG_MARK}§lCatálogo de Habilidades`, body: '', buttons, hit, pages, page: pg };
}
