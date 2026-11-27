// Painel individual do Grande Sábio: monta título, corpo e botões de cada categoria.
// Puro (testável em Node); o desenho é do resource pack (RP/ui/tensura_tree.json → sage_root).
import { SKILLS, TREES } from '../data/registry.js';
import { mpCost } from './stats.js';
import { missionProgress, missionsFor } from './missions.js';
import { RACES, ABSORPTION } from '../data/registry.js';
import { evaluate } from './progression.js';
import { describeFx } from './describe.js';
import { rarityInfo } from '../config.js';
import {
  STAGES, STAGE_ORDER, CAPABILITIES, CAPABILITY_CATEGORIES, hasCapability, capabilityState, evolutionRequirements,
  levelCap, xpToNext, threatLevel, nextCapability,
} from './sage.js';
import { SAGE, SAGE_CATS, SAGE_TILES, SAGE_COUNT, SAGE_ACTIONS, sageMark, TEX, iconName } from './uiSpec.js';

export const CAT_LABEL = {
  ask: 'Conversar', analyses: 'Analisar', level: 'Status', skills: 'Habilidades', magic: 'Magias',
  intel: 'Capacidades', next: 'Evolução', history: 'Memória', manifest: 'Manifestar', missions: 'Missões', settings: 'Configurações',
};
const CAT_TITLE = {
  home: 'Menu do Grande Sábio', ask: 'Perguntar ao Grande Sábio', analyses: 'Análises', level: 'Nível e evolução',
  skills: 'Habilidades do Grande Sábio', magic: 'Magias', intel: 'Inteligência e capacidades', next: 'Próximas evoluções',
  history: 'Histórico e memória', settings: 'Configurações', missions: 'Missões especiais',
};
const GREETING = {
  sage: ['Olá, viajante...', 'Eu sou o §bGrande Sábio§r.', '', 'Estou aqui para guiá-lo em sua jornada neste novo mundo.'],
  raphael: ['Confirmado.', 'Eu sou §6Raphael§r, Senhor da Sabedoria.', '', 'Minha capacidade de processamento foi ampliada. Como posso ajudar?'],
  ciel: ['Mestre, estou aqui.', 'Pode me chamar de §dCiel§r.', '', 'Cuidarei de tudo o que precisar.'],
};
export const ANALYSES_PAGE = 10;
const FREQ = { normal: 'Todas', important: 'Só as importantes', off: 'Desligadas' };

const stageName = (s) => STAGES[s].name;
const yes = (b) => (b ? '§aLigado' : '§cDesligado');

/** Texto de uma análise de criatura guardada (respeita as capacidades atuais). */
export function formatCreature(a, sage) {
  const lines = [`§l§b${a.name}§r §8${a.typeId}`];
  lines.push(`Vida: §c${a.hp}/${a.maxHp}§r · ${a.hostile ? '§cHostil' : '§aNão hostil'}§r${a.boss ? ' · §4Chefe' : ''}`);
  if (hasCapability(sage, 'threat')) lines.push(`Ameaça: ${threatLevel(a).label}§r`);
  if (a.undead) lines.push('Morto-vivo: §evulnerável ao Sagrado§r');
  if (hasCapability(sage, 'adv_analysis')) {
    if (a.ep !== undefined) lines.push(`EP ao derrotar: §e${a.ep}`);
    if (a.effects?.length) lines.push(`Efeitos: ${a.effects.join(', ')}`);
    if (a.race) lines.push(`Raça: ${a.race} · Nível ${a.level}`);
  }
  lines.push(`§7Analisada ${a.count}x${a.kills ? ` · derrotada ${a.kills}x` : ''}`);
  return lines.join('\n');
}

export function formatItem(it) {
  const lines = [`§l§b${it.name}§r §8${it.typeId}`, `Categoria: ${it.category}`];
  if (it.maxDurability) lines.push(`Durabilidade: ${it.maxDurability - it.damage}/${it.maxDurability}`);
  if (it.enchants?.length) lines.push(`Encantamentos: ${it.enchants.join(', ')}`);
  lines.push(`Quantidade: ${it.amount} (máx. ${it.maxAmount})`);
  return lines.join('\n');
}

export function formatSkill(s) {
  const ri = rarityInfo(s.rarity);
  const tree = TREES[s.tree];
  const lines = [`§l${ri.color}${s.name}§r §8${s.original}`,
    `Árvore: ${tree.name} · ${ri.name}§r · ${s.type === 'active' ? 'Ativa' : s.type === 'passive' ? 'Passiva' : 'Evolução'}`,
    `Custo ${s.cost} pts · Nível ${s.minLevel}${s.mp ? ` · ${tree.kind === 'racial' ? 'Aura' : 'Magículas'} ${s.mp}` : ''}${s.cooldown ? ` · recarga ${s.cooldown}s` : ''}`];
  if (s.requires.length) lines.push(`Requer: ${s.requires.map((r) => SKILLS[r]?.name ?? r).join(', ')}`);
  if (s.desc) lines.push(s.desc);
  if (s.fx) lines.push(`§7${describeFx(s.fx)}§r`);
  lines.push(`§8Origem: ${s.origin === 'obra' ? 'Tensura' : s.origin === 'adaptado' ? 'inspirada em Tensura' : 'original do addon'}`);
  return lines.join('\n');
}

function capLine(sage, c) {
  const st = capabilityState(sage, c);
  return `${st.unlocked ? '§a✔' : '§8✘'} §f${c.name}§r §7(${stageName(c.stage)}, nv ${c.level})§r`;
}

function reqLines(ev) {
  return ev.reqs.map((r) => `${r.ok ? '§a✔' : '§c✘'} ${r.label}${!r.ok && r.progress > 0 ? ` §7(${Math.round(r.progress * 100)}%)` : ''}§r`);
}

/**
 * @param {object} p
 * @param {string} p.cat     categoria (SAGE_CATS)
 * @param {object} p.d       dados do jogador
 * @param {object} p.sage    dados do Grande Sábio
 * @param {number} [p.page]  página (Análises)
 * @param {string} [p.note]  mensagem curta no topo do painel (resultado da última ação)
 */
export function buildSageForm({ cat, d, sage, page = 0, note = '' }) {
  const ci = cat === 'home' ? -1 : SAGE_CATS.indexOf(cat);
  const buttons = Array.from({ length: SAGE_COUNT }, () => ({ text: '', icon: '' }));
  const hit = {};
  const set = (i, text, icon = '') => { buttons[i] = { text, icon }; };
  const st = STAGES[sage.stage];
  const cap = levelCap(sage.stage);
  const atCap = sage.level >= cap;
  const need = xpToNext(sage.level);

  set(SAGE.close, '', `${TEX.menu}close`); hit[SAGE.close] = { type: 'close' };
  if (cat === 'home') {
    set(SAGE.back, '§fVoltar ao menu', `${TEX.menu}back`); hit[SAGE.back] = { type: 'back' };
    SAGE_TILES.forEach((c, i) => {
      set(SAGE.tile0 + i, `§l${CAT_LABEL[c]}`, `${TEX.sage}cat_${c}_on`);
      hit[SAGE.tile0 + i] = c === 'manifest' ? { type: 'manifest' } : { type: 'cat', cat: c };
    });
    set(SAGE.settings, '§lConfigurações', `${TEX.sage}cat_settings_on`); hit[SAGE.settings] = { type: 'cat', cat: 'settings' };
  } else {
    set(SAGE.back, '§fVoltar', `${TEX.menu}back`); hit[SAGE.back] = { type: 'cat', cat: 'home' };
  }
  set(SAGE.emblem, '', `${TEX.sage}icon_${sage.stage}`);
  set(SAGE.portrait, '', `${TEX.sage}portrait_${sage.stage}`);
  set(SAGE.stage, `${st.color}§l${st.name}`);
  set(SAGE.level, `§fNível §e${sage.level}§7/${cap}`);
  set(SAGE.xp, atCap ? '§6Limite do estágio' : `§bXP ${Math.floor(sage.xp)}/${need}`);
  set(SAGE.bar, '', `${TEX.menu}bar_${atCap ? 20 : Math.min(20, Math.floor((sage.xp / need) * 20))}`);
  set(SAGE.panelTitle, `§l${CAT_TITLE[cat]}`);

  /** @type {Array<[string, string, any]>} */
  const actions = [];
  const lines = [];
  if (note) lines.push(note, '');
  const ev = evolutionRequirements(sage, d);

  if (cat === 'home') {
    lines.push(...GREETING[sage.stage]);
    const caps = CAPABILITIES.filter((c) => hasCapability(sage, c.id)).length;
    lines.push('', `§7${caps} capacidades ativas · ${Object.keys(sage.analyses).length} espécies analisadas · ${sage.qa.length} conversas.§r`);
    if (ev?.ready) lines.push(`§6§lUma evolução está disponível: ${stageName(ev.next)}.§r Toque em Status.`);
  } else if (cat === 'missions') {
    const lin = RACES[d.race]?.lineage;
    const list = missionsFor(lin);
    if (!list.length) lines.push('Sua raça ainda não tem missões especiais cadastradas.');
    for (const m of list) {
      const done = d.missions?.has(m.id);
      const pr = missionProgress(m, d);
      lines.push(`${done ? '§a✔' : pr.done ? '§e★' : '§7•'} §l${m.name}§r ${done ? '§a(concluída)' : `§7${Math.round(pr.progress * 100)}%`}§r`, `  ${m.desc}`);
      if (!done) for (const p of pr.parts) lines.push(`  ${p.ok ? '§a✔' : '§c✘'} ${p.label}: ${p.cur}/${p.need}§r`);
      lines.push(`  §8Recompensa: ${m.reward} pontos · Origem: ${m.src === 'mod' ? 'missão do addon' : m.src}§r`, '');
    }
    const db = ABSORPTION[lin] ?? [];
    if (db.length) {
      lines.push('§l§bAbsorções conhecidas:§r');
      for (const e of db) {
        const got = e.skills.filter((id) => d.discovered?.has(id) || d.unlocked.has(id)).length;
        lines.push(`• ${e.work} §7(${e.mc.map((x) => x.replace('minecraft:', '')).join('/')})§r — ${got}/${e.skills.length} descobertas`);
      }
    }
    actions.push(['Manifestar o Sábio', `${TEX.sage}icon_${sage.stage}`, { type: 'manifest' }]);
  } else if (cat === 'magic') {
    const mine = [...d.unlocked].map((id) => SKILLS[id]).filter((s) => s && s.type === 'active' && TREES[s.tree].kind === 'universal');
    lines.push(`§lSuas magias (${mine.length}):§r`);
    if (!mine.length) lines.push('Nenhuma magia aprendida ainda. Desbloqueie pela árvore de habilidades.');
    for (const s of mine) {
      const cost = d.stats ? mpCost(s, d.stats) : s.mp;
      const cd = s.cooldown ? Math.round(s.cooldown * (d.stats?.cdMult ?? 1) * 10) / 10 : 0;
      const slot = d.equipped.indexOf(s.id);
      lines.push(`• §b${s.name}§r — ${TREES[s.tree].short} · Magículas §d${cost}§r${cd ? ` · ${cd}s` : ''}${slot >= 0 ? ` · §ebarra ${slot + 1}` : ''}§r`);
    }
    lines.push('');
    if (hasCapability(sage, 'skill_query')) {
      const state = { race: d.race, level: d.level, points: d.points, unlocked: d.unlocked, flags: d.flags };
      const avail = Object.values(SKILLS).filter((s) => !d.unlocked.has(s.id) && s.type === 'active' && TREES[s.tree].kind === 'universal')
        .map((s) => ({ s, ev: evaluate(s.id, state) })).filter((x) => x.ev.ok).sort((a, b) => a.ev.cost - b.ev.cost);
      lines.push(`§lPode aprender agora (${avail.length}):§r`, ...avail.slice(0, 10).map(({ s, ev: e }) => `• ${s.name} — ${TREES[s.tree].short}, ${e.cost} pts`));
      if (!avail.length) lines.push('Nenhuma magia disponível agora (pontos, nível ou requisitos).');
    } else lines.push('§8Consulta de magias disponíveis: capacidade "Consulta de habilidades e magias" (nível 6).§r');
    if (d.stats && (d.stats.sageMp ?? 1) < 1) lines.push('', `§7Otimização de Magicules ativa: custos -${Math.round((1 - d.stats.sageMp) * 100)}%.§r`);
    actions.push(['Analisar magia', `${TEX.sage}btn_skill`, { type: 'analyze_skill' }]);
  } else if (cat === 'ask') {
    lines.push('§7Respostas programadas a partir dos seus dados reais (não é IA generativa).§r', '');
    if (!sage.qa.length) lines.push('Nenhuma conversa ainda. Toque em §bEscrever pergunta§r.');
    for (const e of sage.qa.slice(-6).reverse()) lines.push(`§e» ${e.q}§r`, e.a, '');
    actions.push(['§bEscrever pergunta', `${TEX.sage}btn_ask`, { type: 'ask' }]);
    actions.push(['Exemplos de perguntas', `${TEX.sage}btn_examples`, { type: 'examples' }]);
    if (sage.qa.length) actions.push(['§cLimpar histórico', `${TEX.menu}close`, { type: 'clear' }]);
  } else if (cat === 'analyses') {
    const list = Object.values(sage.analyses).sort((a, b) => (b.last ?? 0) - (a.last ?? 0));
    const items = Object.values(sage.items);
    const pages = Math.max(1, Math.ceil(list.length / ANALYSES_PAGE));
    const pg = Math.min(page, pages - 1);
    lines.push(`Espécies analisadas: §b${list.length}§r · Itens: §b${items.length}§r · Chefes: §c${list.filter((a) => a.boss).length}`, '');
    if (!list.length) lines.push('Nenhuma criatura analisada. Mire numa criatura e toque em §bAnalisar criatura§r.');
    for (const a of list.slice(pg * ANALYSES_PAGE, (pg + 1) * ANALYSES_PAGE)) lines.push(formatCreature(a, sage), '');
    if (pages > 1) lines.push(`§7Página ${pg + 1}/${pages}`);
    actions.push(['§bAnalisar criatura', `${TEX.sage}btn_target`, { type: 'analyze_target' }]);
    actions.push([hasCapability(sage, 'item_analysis') ? 'Analisar item na mão' : '§8Item (nível 3)', `${TEX.sage}btn_item`, { type: 'analyze_item' }]);
    actions.push(['Habilidade ou magia', `${TEX.sage}btn_skill`, { type: 'analyze_skill' }]);
    actions.push(['Itens analisados', `${TEX.sage}btn_item`, { type: 'items' }]);
    if (pages > 1) actions.push([`Página ${((pg + 1) % pages) + 1}/${pages}`, `${TEX.ui}arrow_r`, { type: 'page', page: (pg + 1) % pages }]);
  } else if (cat === 'level') {
    lines.push(`§lEstágio atual:§r ${st.color}${st.name}§r §8(${st.original})`,
      `Nível: §e${sage.level}§r / ${cap} (máximo deste estágio)`,
      atCap ? '§6Limite atingido: o XP só volta a contar depois de evoluir.§r' : `Experiência: §b${Math.floor(sage.xp)}§r / ${need} · próximo nível: ${sage.level + 1}`,
      '§8O nível do Grande Sábio é separado do seu nível de jogador. Limites definidos pelo addon.§r', '');
    const on = CAPABILITIES.filter((c) => hasCapability(sage, c.id));
    const off = CAPABILITIES.filter((c) => !hasCapability(sage, c.id));
    lines.push(`§aCapacidades desbloqueadas (${on.length}):§r ${on.map((c) => c.name).join(', ') || 'nenhuma'}`);
    lines.push(`§8Bloqueadas (${off.length}):§r ${off.map((c) => c.name).join(', ') || 'nenhuma'}`, '');
    const nx = nextCapability(sage);
    if (nx) lines.push(`Próxima melhoria: §b${nx.name}§r (${stageName(nx.stage)}, nível ${nx.level})`, '');
    if (ev) {
      lines.push(`§lRequisitos para ${stageName(ev.next)}:§r`, ...reqLines(ev));
      lines.push(ev.ready ? '§a§lTodos os requisitos foram cumpridos.' : `§7Progresso: ${Math.round(ev.progress * 100)}%`);
      if (ev.ready) actions.push([`§6§lEvoluir para ${stageName(ev.next)}`, `${TEX.sage}icon_${ev.next}`, { type: 'evolve' }]);
    } else lines.push('§dEstágio final alcançado.');
    if (sage.evolutions.length) lines.push('', `Evoluções realizadas: ${sage.evolutions.map((e) => `${stageName(e.stage)} (dia ${e.day})`).join(', ')}`);
    actions.push(['Manifestar o Sábio', `${TEX.sage}icon_${sage.stage}`, { type: 'manifest' }]);
  } else if (cat === 'skills') {
    lines.push(`Capacidades do estágio §b${st.name}§r:`, '');
    for (const c of CAPABILITIES.filter((x) => x.stage === sage.stage)) {
      lines.push(capLine(sage, c), `  ${c.desc}`);
    }
    const earlier = CAPABILITIES.filter((x) => STAGE_ORDER.indexOf(x.stage) < STAGE_ORDER.indexOf(sage.stage));
    if (earlier.length) lines.push('', `§7Herdadas dos estágios anteriores: ${earlier.map((c) => c.name).join(', ')}`);
    actions.push(['Conversar', `${TEX.sage}btn_ask`, { type: 'cat', cat: 'ask' }]);
    actions.push(['Analisar criatura', `${TEX.sage}btn_target`, { type: 'analyze_target' }]);
  } else if (cat === 'intel') {
    const on = CAPABILITIES.filter((c) => hasCapability(sage, c.id)).length;
    lines.push(`§lCAPACIDADES§r — ${on}/${CAPABILITIES.length} desbloqueadas`, '');
    for (const group of CAPABILITY_CATEGORIES) {
      const list = CAPABILITIES.filter((c) => c.cat === group);
      lines.push(`§b§l${group}§r`);
      if (!list.length) { lines.push('  §8Nenhuma capacidade cadastrada ainda (sistema não implementado).§r', ''); continue; }
      list.forEach((c, i) => {
        const s = capabilityState(sage, c);
        const nextInGroup = list[i + 1];
        lines.push(capLine(sage, c), `  ${c.desc}`,
          `  §7Estado: ${s.unlocked ? '§adesbloqueada' : '§cbloqueada'}§7${s.missing.length ? ` · Requisitos: ${s.missing.join(', ')}` : ''}`,
          `  §7Próxima melhoria: ${nextInGroup ? `${nextInGroup.name} (${stageName(nextInGroup.stage)}, nv ${nextInGroup.level})` : 'nenhuma cadastrada'}§r`);
      });
      lines.push('');
    }
  } else if (cat === 'next') {
    const si = STAGE_ORDER.indexOf(sage.stage);
    for (let i = 0; i < STAGE_ORDER.length; i++) {
      const sid = STAGE_ORDER[i];
      const S = STAGES[sid];
      const mark = i < si ? '§a✔ concluído' : i === si ? '§b● atual' : '§7○ futuro';
      lines.push(`${S.color}§l${S.name}§r §8(${S.original})§r — ${mark}§r · limite nível ${levelCap(sid)}`);
      const caps = CAPABILITIES.filter((c) => c.stage === sid);
      lines.push(`  Capacidades: ${caps.map((c) => c.name).join(', ')}`);
      if (i > 0) lines.push(`  §7Melhorias em relação a ${STAGES[STAGE_ORDER[i - 1]].name}: ${caps.filter((c) => c.bonus || /avançada|II|Previsão|autônomo/.test(c.name)).map((c) => c.desc).join(' ') || '—'}§r`);
      if (i === si + 1 && ev) lines.push('  §lRequisitos:§r', ...reqLines(ev).map((l) => `  ${l}`), `  Experiência: nível ${levelCap(sage.stage)} em ${stageName(sage.stage)} (faltam ${Math.max(0, levelCap(sage.stage) - sage.level)} níveis)`);
      else if (i > si + 1) {
        // Requisitos especiais do estágio seguinte (o primeiro item depende de alcançar o estágio anterior).
        const future = evolutionRequirements({ ...sage, stage: STAGE_ORDER[i - 1], level: 0 }, d);
        lines.push('  §lCondições de desbloqueio:§r', `  §7• Alcançar ${STAGES[STAGE_ORDER[i - 1]].name}§r`, ...future.reqs.map((r) => `  §7• ${r.label}§r`));
      }
      lines.push('');
    }
    lines.push('§8Estágios e nomes são da obra; requisitos e limites são do balanceamento do addon.§r');
    if (ev?.ready) actions.push([`§6§lEvoluir para ${stageName(ev.next)}`, `${TEX.sage}icon_${ev.next}`, { type: 'evolve' }]);
  } else if (cat === 'history') {
    lines.push(`§lEvoluções:§r ${sage.evolutions.length ? sage.evolutions.map((e) => `${stageName(e.stage)} (dia ${e.day})`).join(', ') : 'nenhuma'}`);
    const kills = Object.keys(sage.kills);
    lines.push(`§lDescobertas (espécies derrotadas pela 1ª vez):§r ${kills.length}`);
    if (kills.length) lines.push(`  ${kills.slice(-12).map((k) => k.replace('minecraft:', '')).join(', ')}`);
    lines.push(`§lAnálises:§r ${Object.keys(sage.analyses).length} espécies, ${Object.keys(sage.items).length} itens`, '');
    lines.push(`§lConversas guardadas (${sage.qa.length}):§r${sage.settings.saveQa ? '' : ' §7(salvamento desligado)'}`);
    for (const e of [...sage.qa].reverse()) lines.push(`§e» ${e.q}§r §8(dia ${e.day})`, e.a, '');
    if (sage.qa.length) actions.push(['§cLimpar conversas', `${TEX.menu}close`, { type: 'clear' }]);
  } else if (cat === 'settings') {
    lines.push(`Sons: ${yes(sage.settings.sound)}§r`, `Notificações: ${yes(sage.settings.notify)}§r · Frequência: ${FREQ[sage.settings.frequency] ?? 'Todas'}`,
      `Salvar histórico de conversas: ${yes(sage.settings.saveQa)}§r`, '',
      '§7Os dados do Grande Sábio são salvos por jogador, no seu personagem (propriedade tensura:sage).§r');
    actions.push(['§bAlterar configurações', `${TEX.sage}cat_settings`, { type: 'settings' }]);
  }

  actions.slice(0, SAGE_ACTIONS).forEach(([text, icon, act], i) => {
    set(SAGE.action0 + i, text, icon);
    hit[SAGE.action0 + i] = act;
  });
  let body = lines.join('\n');
  if (body.length > 6000) body = `${body.slice(0, 5990)}…`;
  return { title: `${sageMark(ci + 1)}§lGRANDE SÁBIO`, body, buttons, hit };
}

export { iconName, FREQ };
