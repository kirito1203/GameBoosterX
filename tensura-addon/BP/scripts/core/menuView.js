// Menu principal com abas (Status, Raça, Habilidades, Evolução, Registros).
// Puro: monta título, corpo e botões; o desenho é do resource pack.
import { SKILLS, TREES, RACES, LINEAGES } from '../data/registry.js';
import { evaluate } from './progression.js';
import { raceSummary } from './view.js';
import { mimicTraits } from './stats.js';
import { epForLevel } from '../config.js';
import {
  MENU, MENU_COUNT, MENU_TABS, ACTIVE_ROWS, BAR_SLOTS, ACTION_COUNT, menuMark, TEX, iconName,
} from './uiSpec.js';

const TAB_LABEL = { status: 'STATUS', race: 'RAÇA', skills: 'HABILIDADES', evolution: 'EVOLUÇÃO', records: 'REGISTROS' };

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
 * @param {string} p.tab  'status' | 'race' | 'evolution' | 'records'
 * @param {object} p.d    dados do jogador (storage)
 * @param {object} p.state estado de progressão
 */
export function buildMenuForm({ tab, d, state }) {
  const tabIndex = MENU_TABS.indexOf(tab) + 1;
  const buttons = Array.from({ length: MENU_COUNT }, () => ({ text: '', icon: '' }));
  const hit = {};
  const set = (i, text, icon = '') => { buttons[i] = { text, icon }; };
  const race = RACES[d.race];
  const st = d.stats;

  set(MENU.close, '', `${TEX.menu}close`); hit[MENU.close] = { type: 'close' };
  MENU_TABS.forEach((t, i) => {
    const on = t === tab;
    set(MENU.tab0 + i, `${on ? '§b' : '§7'}${TAB_LABEL[t]}`, `${TEX.menu}tab_${t}${on ? '_on' : ''}`);
    hit[MENU.tab0 + i] = { type: 'tab', tab: t };
  });
  set(MENU.back, '§fVoltar', `${TEX.menu}back`); hit[MENU.back] = { type: 'close' };
  set(MENU.raceName, `§b${race.name}`);
  set(MENU.sage, '§bGRANDE SÁBIO', `${TEX.sage}icon_${d.sage?.stage ?? 'sage'}`); hit[MENU.sage] = { type: 'sage' };

  let body = '';
  if (tab === 'status') {
    const prog = evolutionProgress(d, state);
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
    // Corpo vazio na aba Status (os dados estão nos campos acima).
    body = '';
  } else {
    /** @type {Array<[string, string, any]>} */
    const actions = [];
    if (tab === 'race') {
      const lin = LINEAGES[race.lineage];
      const chain = TREES[`race.${race.lineage}`].skills.filter((s) => s.type === 'evolution').map((s) => RACES[s.evolveTo].name);
      const traits = [...st.effects.keys()];
      body = [
        `§l§b${race.name}§r §7(${race.original}) — linhagem ${lin.name}`,
        race.desc, '',
        raceSummary(d.race),
        `Aura máx. ${st.maxAura} · Magículas máx. ${st.maxMp}`,
        `Efeitos permanentes: ${traits.join(', ') || 'nenhum'}`,
        `Evoluções da linhagem: ${chain.join(' → ') || 'nenhuma'}`,
        `§8Origem: ${race.origin === 'obra' ? 'Tensura' : race.origin === 'adaptado' ? 'inspirada em Tensura' : 'original do addon'}`,
      ].join('\n');
      actions.push(['Ver árvore racial', `${TEX.ui}tab_racial`, { type: 'tree', pos: [0, 0] }]);
    } else if (tab === 'evolution') {
      const lin = race.lineage;
      const evs = TREES[`race.${lin}`].skills.filter((s) => s.type === 'evolution');
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
    } else if (tab === 'records') {
      const kills = Object.entries(d.kills ?? {}).sort((a, b) => b[1] - a[1]);
      const flags = [...d.flags].map((f) => ({ killed_wither: 'Wither derrotado', killed_ender_dragon: 'Ender Dragon derrotado', killed_warden: 'Warden derrotado' }[f] ?? f));
      const byTree = {};
      for (const id of d.unlocked) { const t = TREES[SKILLS[id].tree]; byTree[t.short] = (byTree[t.short] ?? 0) + 1; }
      body = [
        '§l§bRegistros§r',
        `EP total: ${fmtNum(d.epTotal)} · Nível ${d.level} (EP ${Math.floor(d.ep)}/${epForLevel(d.level)} para o próximo)`,
        `Pontos disponíveis: §e${d.points}`,
        `Conquistas: ${flags.join(', ') || 'nenhuma'}`,
        `Habilidades: ${d.unlocked.size} — ${Object.entries(byTree).map(([k, v]) => `${k} ${v}`).join(', ')}`,
        d.absorbed.length ? `Espécies absorvidas: ${d.absorbed.map((t) => mimicTraits(t).name).join(', ')}` : 'Espécies absorvidas: nenhuma',
        '', '§bCriaturas derrotadas:',
        ...(kills.length ? kills.slice(0, 15).map(([t, n]) => `• ${t.replace('minecraft:', '')}: ${n}`) : ['nenhuma ainda']),
      ].join('\n');
      actions.push(['Grimório (magias)', `${TEX.ui}btn_grimoire`, { type: 'grimoire' }]);
      if (d.absorbed.length && d.unlocked.has('r.slime.shapeshift')) actions.push(['Transformação', `${TEX.ui}btn_status`, { type: 'mimic' }]);
      if (d.unlocked.has('aspect.spatial_storage')) actions.push(['Armazenamento', `${TEX.ui}btn_equip`, { type: 'storage' }]);
    }
    actions.slice(0, ACTION_COUNT).forEach(([text, icon, act], i) => {
      set(MENU.action0 + i, text, icon);
      hit[MENU.action0 + i] = act;
    });
  }
  const title = `${menuMark(tabIndex)}§lTensura RPG`;
  return { title, body, buttons, hit };
}
