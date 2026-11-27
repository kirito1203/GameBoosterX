// Tela "Escolha sua Raça" (modelo puro: título, botões e ações).
import { RACES, TREES, SKILLS, initialSkillsFor } from '../data/registry.js';
import { CARD_ORDER, CARD_META } from '../data/raceCards.js';
import {
  RACE, RACE_COUNT, RACE_MARK, RACE_PAGE_SIZE, CARD_SELECTED, CARD_LOCKED, TEX,
} from './uiSpec.js';

const GROUP = {
  fire: 'Fogo', fall: 'Queda', physical: 'Físico', explosion: 'Explosão', magic: 'Mágico', dark: 'Trevas',
  holy: 'Sagrado', drowning: 'Afogamento', freezing: 'Gelo', lightning: 'Relâmpago', starve: 'Fome', suffocation: 'Sufocamento',
};

export const PAGE_COUNT = Math.ceil(CARD_ORDER.length / RACE_PAGE_SIZE);

export function isSelectable(raceId) {
  return !!RACES[raceId]?.start;
}

/** Raça base da qual uma raça evoluída descende (para cards bloqueados). */
export function baseOf(raceId) {
  const r = RACES[raceId];
  return Object.values(RACES).find((x) => x.lineage === r.lineage && x.stage === 0);
}

export function portraitTexture(raceId) {
  return `${TEX.race}p_${raceId}`;
}

/** Texto descritivo completo (também usado nos testes e no catálogo). */
export function raceInfo(raceId) {
  const r = RACES[raceId];
  const meta = CARD_META[raceId];
  const tree = TREES[`race.${r.lineage}`];
  const initial = initialSkillsFor(isSelectable(raceId) ? raceId : baseOf(raceId).id)
    .filter((id) => SKILLS[id].type !== 'race_root').map((id) => SKILLS[id].name);
  const evolutions = tree.skills.filter((s) => s.type === 'evolution' && RACES[s.evolveTo].stage > r.stage)
    .map((s) => RACES[s.evolveTo].name + (s.needFlags.length ? ' (requisitos especiais)' : ''));
  const specs = tree.skills.filter((s) => s.category.includes('Especialização') || s.excludes.length)
    .map((s) => s.type === 'evolution' ? RACES[s.evolveTo].name : s.name);
  const fmt = (o) => Object.entries(o).filter(([, v]) => v > 0).map(([k, v]) => `${GROUP[k] ?? k} ${Math.round(v * 100)}%`).join(', ') || 'nenhuma';
  return {
    name: r.name,
    original: r.original,
    desc: r.desc,
    initial,
    affinity: meta?.affinity ?? '—',
    evolutions,
    specs,
    resist: fmt(r.resist),
    weak: fmt(r.weak),
    difficulty: meta?.difficulty ?? 3,
  };
}

/**
 * @param {{ page: number, selected: string, sageText: string }} p
 */
export function buildRaceForm({ page, selected, sageText }) {
  const buttons = Array.from({ length: RACE_COUNT }, () => ({ text: '', icon: '' }));
  const hit = {};
  const set = (i, text, icon = '') => { buttons[i] = { text, icon }; };

  // Cards da página.
  const ids = CARD_ORDER.slice(page * RACE_PAGE_SIZE, (page + 1) * RACE_PAGE_SIZE);
  ids.forEach((id, i) => {
    const token = id === selected ? CARD_SELECTED : !isSelectable(id) ? CARD_LOCKED : '';
    // Um card pode estar selecionado e bloqueado: os dois marcadores entram no texto.
    const both = id === selected && !isSelectable(id) ? CARD_SELECTED + CARD_LOCKED : token;
    set(RACE.card0 + i, `${both}${RACES[id].name}`, portraitTexture(id));
    hit[RACE.card0 + i] = { type: 'select', race: id };
  });

  if (PAGE_COUNT > 1) {
    set(RACE.prev, '§l«', '');
    hit[RACE.prev] = { type: 'page', page: (page - 1 + PAGE_COUNT) % PAGE_COUNT };
    set(RACE.next, '§l»', '');
    hit[RACE.next] = { type: 'page', page: (page + 1) % PAGE_COUNT };
    set(RACE.page, `§7Página ${page + 1}/${PAGE_COUNT}`);
  }
  set(RACE.sageText, sageText);

  const info = raceInfo(selected);
  const selectable = isSelectable(selected);
  set(RACE.infoName, `§b${info.name} §8(${info.original})`);
  set(RACE.infoPortrait, '', portraitTexture(selected));
  set(RACE.infoDesc, selectable ? info.desc : `§7${info.desc}\n§6Evolução de ${baseOf(selected).name}: escolha ${baseOf(selected).name} e evolua pela Árvore Racial.`);
  set(RACE.infoInitial, `§b${selectable ? 'Habilidades iniciais' : `Começa como ${baseOf(selected).name} com`}:§r\n${info.initial.map((n) => `§b✦§r ${n}`).join('\n') || '—'}`);
  set(RACE.infoAffinity, `§bAfinidade:§r ${info.affinity}\n§7Resist.: ${info.resist}\n§7Fraquezas: ${info.weak}`);
  set(RACE.infoEvolutions, `§bEvoluções:§r ${info.evolutions.join(', ') || 'nenhuma'}${info.specs.length ? `\n§bEspecializações:§r ${info.specs.join(', ')}` : ''}`);
  set(RACE.infoDifficulty, '§bDificuldade:', `${TEX.race}stars_${info.difficulty}`);
  set(RACE.infoTraits, '');

  if (selectable) {
    set(RACE.confirm, '§l§fCONFIRMAR RAÇA', '');
    hit[RACE.confirm] = { type: 'confirm', race: selected };
  } else {
    set(RACE.confirm, '§l§7BLOQUEADA', '');
    hit[RACE.confirm] = { type: 'locked', race: selected };
  }
  return { title: `${RACE_MARK}§lEscolha sua Raça`, body: '', buttons, hit };
}

/** Página onde a raça aparece. */
export function pageOf(raceId) {
  return Math.max(0, Math.floor(CARD_ORDER.indexOf(raceId) / RACE_PAGE_SIZE));
}
