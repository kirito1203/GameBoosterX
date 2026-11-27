// Menu principal: Personagem, Raça, Árvore de Habilidades, Inventário especial, Missões, Status, Evolução,
// Configurações (+ Grande Sábio).
// Puro: monta título, corpo e botões; o desenho é do resource pack.
import { SKILLS, TREES, RACES, LINEAGES } from '../data/registry.js';
import { evaluate } from './progression.js';
import { raceSummary } from './view.js';
import { mimicTraits } from './stats.js';
import { epForLevel, CONFIG } from '../config.js';
import { missionLines } from './missions.js';
import { barSlots } from './stats.js';
import { GROUP_NAMES } from '../data/elements.js';
import {
  MENU, MENU_COUNT, MENU_TABS, ACTIVE_ROWS, BAR_SLOTS, ACTION_COUNT, menuMark, TEX, iconName,
} from './uiSpec.js';

export const TAB_LABEL = {
  char: 'Personagem', race: 'Raça', tree: 'Árvore de Habilidades', catalog: 'Catálogo', inventory: 'Inventário Especial', missions: 'Missões',
  status: 'Status', evolution: 'Evolução', settings: 'Configurações',
};
const HUD = { auto: 'Ao usar habilidades', always: 'Sempre', never: 'Nunca' };
const FXL = { normal: 'Normais', reduced: 'Reduzidos (celular)' };
const STORAGE_SKILLS = ['aspect.spatial_storage', 'r.slime.stomach'];

export function fmtNum(n) {
  return Math.floor(n).toString().replace(/\B(?=(\d{3})+(?!\d))/g, '.');
}

/** Evoluções da linhagem a partir da raça atual. */
export function nextEvolutions(d) {
  const lin = RACES[d.race].lineage;
  return TREES[`race.${lin}`].skills.filter((s) => s.type === 'evolution' && !d.unlocked.has(s.id)
    && RACES[s.evolveTo].stage === RACES[d.race].stage + 1);
}

/** Progresso (0..1) rumo à próxima evolução: média de nível, habilidades da árvore e pontos. */
export function evolutionProgress(d, state) {
  const evos = nextEvolutions(d);
  if (!evos.length) return 1;
  let best = 0;
  for (const s of evos) {
    const ev = evaluate(s.id, state);
    if (ev.ok) return 1;
    const tree = TREES[s.tree];
    const count = tree.skills.filter((x) => x.type !== 'race_root' && d.unlocked.has(x.id)).length;
    const parts = [Math.min(1, d.level / s.minLevel), Math.min(1, ev.cost ? d.points / ev.cost : 1)];
    if (s.needCount) parts.push(Math.min(1, count / s.needCount));
    if (s.needFlags.length) parts.push(s.needFlags.filter((f) => d.flags.has(f)).length / s.needFlags.length);
    best = Math.max(best, parts.reduce((a, b) => a + b, 0) / parts.length);
  }
  return best;
}

/** Habilidades em destaque no Status (únicas/supremas primeiro). */
export function featuredSkills(d) {
  const rank = (s) => (s.tree === 'ultimate' ? 0 : s.tree === 'unique' ? 1 : s.major ? 2 : s.type === 'active' ? 3 : 4);
  return [...d.unlocked].map((id) => SKILLS[id]).filter((s) => s && s.type !== 'race_root' && s.type !== 'evolution')
    .sort((a, b) => rank(a) - rank(b) || b.cost - a.cost).slice(0, ACTIVE_ROWS);
}

/**
 * @param {object} p
 * @param {string} p.tab  MENU_TABS (exceto 'tree', que abre a árvore direto)
 * @param {object} p.d    dados do jogador (storage)
 * @param {object} p.state estado de progressão
 */
export function buildMenuForm({ tab, d, state }) {
  if (tab === 'records') tab = 'status'; // compatibilidade: antiga aba Registros
  const tabIndex = MENU_TABS.indexOf(tab) + 1;
  const buttons = Array.from({ length: MENU_COUNT }, () => ({ text: '', icon: '' }));
  const hit = {};
  const set = (i, text, icon = '') => { buttons[i] = { text, icon }; };
  const race = RACES[d.race];
  const st = d.stats;

  set(MENU.close, '', `${TEX.menu}close`); hit[MENU.close] = { type: 'close' };
  MENU_TABS.forEach((t, i) => {
    const on = t === tab;
    set(MENU.tab0 + i, `${on ? '§b' : '§f'}${TAB_LABEL[t]}`, `${TEX.menu}cat_${t}${on ? '_on' : ''}`);
    hit[MENU.tab0 + i] = t === 'tree' ? { type: 'tab', tab: 'skills' } : t === 'catalog' ? { type: 'catalog' } : { type: 'tab', tab: t };
  });
  set(MENU.sage, '§bGrande Sábio', `${TEX.sage}icon_${d.sage?.stage ?? 'sage'}`); hit[MENU.sage] = { type: 'sage' };
  set(MENU.pageTitle, `§l${TAB_LABEL[tab]}`);

  let body = '';
  /** @type {Array<[string, string, any]>} */
  const actions = [];
  if (tab === 'char') {
    const prog = evolutionProgress(d, state);
    set(MENU.raceName, `§b${race.name}`);
    set(MENU.stat0 + 0, `§b${fmtNum(d.epTotal)}`);
    set(MENU.stat0 + 1, `§9${Math.floor(d.aura)} / ${st.maxAura}`);
    set(MENU.stat0 + 2, `§d${Math.floor(d.mp)} / ${st.maxMp}`);
    set(MENU.stat0 + 3, `§e${d.level}`);
    set(MENU.stat0 + 4, `§b${Math.floor(prog * 100)}%`);
    set(MENU.progressBar, '', `${TEX.menu}bar_${Math.round(prog * 20)}`);
    featuredSkills(d).forEach((s, i) => {
      set(MENU.active0 + i, `§f${s.name}`, `${TEX.icon}${iconName(s.id)}`);
      hit[MENU.active0 + i] = { type: 'skill', id: s.id };
    });
    for (let i = 0; i < BAR_SLOTS; i++) {
      const id = d.equipped[i];
      set(MENU.slot0 + i, '', id ? `${TEX.icon}${iconName(id)}` : `${TEX.menu}slot_empty`);
      hit[MENU.slot0 + i] = { type: 'slot', index: i };
    }
  } else if (tab === 'race') {
    const lin = LINEAGES[race.lineage];
    const chain = TREES[`race.${race.lineage}`].skills.filter((s) => s.type === 'evolution').map((s) => RACES[s.evolveTo].name);
    const traits = [...st.effects.keys()];
    body = [
      `§l§b${race.name}§r §7(${race.original}) — linhagem ${lin.name}`, race.desc, '',
      raceSummary(d.race),
      `Aura máx. ${st.maxAura} · Magículas máx. ${st.maxMp}`,
      `Efeitos permanentes: ${traits.join(', ') || 'nenhum'}`,
      `Evoluções da linhagem: ${chain.join(' → ') || 'nenhuma'}`,
      `§8Origem: ${race.origin === 'obra' ? 'Tensura' : race.origin === 'adaptado' ? 'inspirada em Tensura' : 'original do addon'}`,
    ].join('\n');
    actions.push(['Ver árvore racial', `${TEX.ui}tab_racial`, { type: 'tree', pos: [0, 0] }]);
  } else if (tab === 'inventory') {
    const hasStorage = STORAGE_SKILLS.some((id) => d.unlocked.has(id));
    const hasMimic = d.absorbed.length && d.unlocked.has('r.slime.shapeshift');
    body = [
      '§l§bInventário Especial§r',
      hasStorage ? '§aArmazenamento dimensional disponível§r (18 espaços, guardado com o seu personagem).'
        : `§7Armazenamento dimensional: bloqueado. Liberado por ${STORAGE_SKILLS.map((id) => SKILLS[id]?.name).filter(Boolean).join(' ou ')}.§r`,
      '',
      `Barra de Habilidades: ${d.equipped.length}/${barSlots(d)} espaços.`,
      d.absorbed.length ? `Formas absorvidas: ${d.absorbed.map((t) => mimicTraits(t).name).join(', ')}` : 'Formas absorvidas: nenhuma.',
      d.mimic ? `§eForma atual: ${mimicTraits(d.mimic).name}` : '',
    ].filter((x) => x !== '').join('\n');
    if (hasStorage) actions.push(['§bAbrir armazenamento', `${TEX.ui}btn_equip`, { type: 'storage' }]);
    if (hasMimic) actions.push(['Transformação', `${TEX.ui}btn_status`, { type: 'mimic' }]);
    actions.push(['Grimório (barra)', `${TEX.ui}btn_grimoire`, { type: 'grimoire' }]);
  } else if (tab === 'missions') {
    body = ['§l§bMissões especiais§r', '', ...missionLines(d, race.lineage)].join('\n');
  } else if (tab === 'status') {
    const kills = Object.entries(d.kills ?? {}).sort((a, b) => b[1] - a[1]);
    const flags = [...d.flags].map((f) => ({ killed_wither: 'Wither derrotado', killed_ender_dragon: 'Ender Dragon derrotado', killed_warden: 'Warden derrotado' }[f] ?? f));
    const pct = (o) => Object.entries(o).filter(([, v]) => v > 0).map(([k, v]) => `${GROUP_NAMES[k] ?? k} ${Math.round(v * 100)}%`).join(', ') || 'nenhuma';
    body = [
      '§l§bAtributos§r',
      `Nível ${d.level} · EP ${Math.floor(d.ep)}/${epForLevel(d.level)} · EP total ${fmtNum(d.epTotal)} · Pontos §e${d.points}`,
      `Magículas ${Math.floor(d.mp)}/${st.maxMp} (+${(st.regen * st.regenMult).toFixed(1)}/s) · Aura ${Math.floor(d.aura)}/${st.maxAura}`,
      `Resistências: ${pct(st.resist)}`,
      `Fraquezas: ${pct(st.weak)}`,
      `Recarga x${(st.cdMult ?? 1).toFixed(2)} · Barra ${barSlots(d)} espaços`,
      '', '§l§bRegistros§r',
      `Conquistas: ${flags.join(', ') || 'nenhuma'}`,
      `Habilidades: ${d.unlocked.size} · Absorções: ${d.absorbTotal ?? 0} (${d.absorbed.length} espécies)`,
      '§bCriaturas derrotadas:§r',
      ...(kills.length ? kills.slice(0, 12).map(([t, n]) => `• ${t.replace('minecraft:', '')}: ${n}`) : ['nenhuma ainda']),
    ].join('\n');
  } else if (tab === 'evolution') {
    const evs = TREES[`race.${race.lineage}`].skills.filter((s) => s.type === 'evolution');
    const lines = [`§l§bEvolução — ${race.name}§r (estágio ${race.stage})`, ''];
    for (const s of evs) {
      const ev = evaluate(s.id, state);
      const target = RACES[s.evolveTo];
      const mark = ev.state === 'unlocked' ? '§a✔' : ev.ok ? '§e★' : '§7•';
      lines.push(`${mark} ${target.name}§r §8(${target.original}, estágio ${target.stage})`);
      if (ev.state !== 'unlocked' && ev.missing.length) for (const m of ev.missing) lines.push(`   §c- ${m}`);
      if (ev.ok) {
        lines.push(`   §ePronto para evoluir (${ev.cost} pts)`);
        actions.push([`§6Evoluir: ${target.name}`, `${TEX.icon}${iconName(s.id)}`, { type: 'unlock', id: s.id }]);
      }
    }
    body = lines.join('\n');
    actions.push(['Ver na árvore', `${TEX.ui}tab_racial`, { type: 'tree', pos: [0, 0] }]);
  } else if (tab === 'settings') {
    const s = d.settings ?? { hud: 'auto', fx: 'normal' };
    body = [
      '§l§bConfigurações§r', '',
      `HUD de Magicules: §e${HUD[s.hud] ?? HUD.auto}`,
      `Efeitos visuais das habilidades: §e${FXL[s.fx] ?? FXL.normal}§r §7(reduzido = menos partículas, melhor para celular)`,
      `Notificações do Grande Sábio: §e${d.sage?.settings?.notify ? 'ligadas' : 'desligadas'}§r · Sons: §e${d.sage?.settings?.sound ? 'ligados' : 'desligados'}`,
      '', '§7Salvo no seu personagem; vale só para você.§r',
    ].join('\n');
    actions.push(['§bAlterar configurações', `${TEX.menu}cat_settings_on`, { type: 'settings' }]);
  }
  actions.slice(0, ACTION_COUNT).forEach(([text, icon, act], i) => {
    set(MENU.action0 + i, text, icon);
    hit[MENU.action0 + i] = act;
  });
  const title = `${menuMark(tabIndex)}§lTensura RPG`;
  return { title, body, buttons, hit };
}
