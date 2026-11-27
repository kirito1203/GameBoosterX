// Operações de Ciel (MANAS) chamadas pela página "Ciel — Manas" do painel do Grande Sábio.
// Cada operação usa dados reais e respeita as regras de core/manas.js. Retorna um texto curto
// que o painel mostra no topo da página.
import { world } from '@minecraft/server';
import { ActionFormData, MessageFormData } from '@minecraft/server-ui';
import { SKILLS, TREES } from '../data/registry.js';
import { evaluate } from '../core/progression.js';
import { libraryOf } from '../core/missions.js';
import {
  opAllowed, OP_NODES, LIMITS, nearRequirements, syntheses, azathothSynthesis, creatable, createCost, duplicable, grantCheck,
  dailyFree, compatibility,
} from '../core/manas.js';
import { getData, progressionState, saveNow, refreshStats } from '../systems/storage.js';
import { notify } from '../systems/sage.js';
import { checkMissions } from '../systems/progressHooks.js';
import { showForm, doUnlock } from './treeUI.js';
import { V } from '../systems/util.js';

const day = () => { try { return world.getDay(); } catch { return 0; } };
const needNode = (op) => `§cRequer o nó "${SKILLS[OP_NODES[op]].name}" (árvore do Slime).`;

async function pick(player, title, body, items, label) {
  if (!items.length) return null;
  const f = new ActionFormData().title(title).body(body);
  for (const it of items) f.button(label(it));
  const r = await showForm(player, f);
  if (!r || r.canceled || r.selection === undefined) return null;
  return items[r.selection];
}

export async function cielOp(player, op) {
  const d = getData(player);
  const st = progressionState(d);
  switch (op) {
    case 'requirements': {
      const near = nearRequirements(d, st);
      const rec = await cielOp(player, 'recommend');
      if (!near.length) return `§7Ciel: nenhuma habilidade da árvore está a 2 requisitos ou menos.§r\n\n${rec}`;
      return ['§d§lCiel — requisitos mais próximos:§r', ...near.map(({ s, ev }) => (ev.ok ? `§a★ ${s.name}: disponível (${ev.cost} pts)` : `§e• ${s.name}: ${ev.missing.join('; ')}`)), '', rec].join('\n');
    }
    case 'recommend': {
      const near = nearRequirements(d, st, 0, 50).filter((x) => x.ev.ok);
      const sup = near.filter((x) => x.s.tier === 'ultimate' || x.s.major);
      const list = (sup.length ? sup : near).slice(0, 4);
      if (!list.length) return '§7Ciel: nada disponível agora. Recomendo concluir missões e subir o domínio das habilidades ativas.';
      return ['§d§lCiel recomenda:§r', ...list.map(({ s, ev }) => `• ${s.name} (${ev.cost} pts)${s.tier === 'ultimate' ? ' §6— Suprema' : ''}`)].join('\n');
    }
    case 'synthesis': {
      const az = azathothSynthesis(d);
      const items = [];
      if (!az.done) items.push({ az: true });
      for (const x of syntheses(d, st).slice(0, 8)) items.push(x);
      const it = await pick(player, '§lCiel — Sínteses', 'Combinações reais da sua árvore (duas ou mais habilidades anteriores).', items,
        (x) => (x.az ? `§l§5Síntese de Azathoth§r\n${az.ready ? '§apronta' : '§cfaltam requisitos'}` : `§l${x.s.name}§r\n${x.ev.ok ? `§adisponível (${x.ev.cost} pts)` : `§7${x.have}/${x.s.requires.length} habilidades`}`));
      if (!it) return items.length ? '' : '§7Ciel: nenhuma síntese próxima.';
      if (it.az) {
        if (!az.ready) return `§cSíntese de Azathoth indisponível:§r\n${az.missing.join('\n')}`;
        d.cielOps.add('azathoth');
        saveNow(player);
        notify(player, '§5§lSíntese executada.§r Os requisitos da evolução para Azathoth foram cumpridos por Ciel.', { important: true, snd: 'random.totem', pitch: 0.9 });
        checkMissions(player);
        return '§aSíntese de Azathoth concluída. Desbloqueie Azathoth na Árvore Suprema.';
      }
      if (!it.ev.ok) return `§c${it.s.name}: ${it.ev.missing.join('; ')}`;
      return doUnlock(player, d, it.s.id) ? `§aSíntese concluída: ${it.s.name}.` : '';
    }
    case 'library': {
      if (!opAllowed(d, 'library')) return needNode('library');
      const lib = [...libraryOf(d)].map((id) => SKILLS[id]).filter(Boolean);
      const byTree = {};
      for (const s of lib) (byTree[TREES[s.tree].short] ??= []).push(s.name);
      return [`§d§lBiblioteca de Habilidades (${lib.length})§r`, '§7Registre mais analisando habilidades (Analisar → Habilidade ou magia) e jogadores.§r',
        ...Object.entries(byTree).map(([t, n]) => `§b${t}:§r ${n.slice(0, 12).join(', ')}${n.length > 12 ? '…' : ''}`)].join('\n');
    }
    case 'compat': {
      const lib = [...libraryOf(d)].map((id) => SKILLS[id]).filter((s) => s && !d.unlocked.has(s.id)).slice(0, 30);
      const s = await pick(player, '§lCiel — Compatibilidade', 'Escolha uma habilidade da Biblioteca.', lib, (x) => `${x.name}\n§7${TREES[x.tree].short}`);
      return s ? compatibility(d, st, s.id).join('\n') : '';
    }
    case 'shub': {
      const f = new ActionFormData().title('§lShub-Niggurath — operações').body('Operações limitadas ao catálogo do addon.');
      const ops = ['create', 'duplicate', 'grant', 'store', 'restore'];
      const labels = { create: 'Criar habilidade', duplicate: 'Duplicar habilidade', grant: 'Conceder cópia', store: 'Guardar barra atual', restore: 'Recuperar barra' };
      for (const o of ops) f.button(labels[o]);
      const r = await showForm(player, f);
      if (!r || r.canceled || r.selection === undefined) return '';
      return shubOp(player, ops[r.selection]);
    }
    default: return '';
  }
}

async function shubOp(player, op) {
  const d = getData(player);
  const st = progressionState(d);
  const today = day();
  if (op === 'create') {
    if (!opAllowed(d, 'create')) return needNode('create');
    if (!dailyFree(d, 'create', today)) return '§7Criação já usada hoje (uma por dia do mundo).';
    const s = await pick(player, '§lCriação de Habilidades', `Custa 1,5x os pontos e ${LIMITS.createMp} Magicules.`, creatable(d, st).slice(0, 30),
      (x) => `${x.name}\n§7${createCost(x, st)} pts · ${TREES[x.tree].short}`);
    if (!s) return '§7Nada criado (Biblioteca sem habilidades compatíveis ou operação cancelada).';
    const cost = createCost(s, st);
    if (d.points < cost) return `§cPontos insuficientes (${cost}).`;
    if (d.mp < LIMITS.createMp) return `§cMagicules insuficientes (${LIMITS.createMp}).`;
    d.points -= cost; d.mp -= LIMITS.createMp; d.unlocked.add(s.id); d.opDays.create = today;
    refreshStats(d); saveNow(player);
    notify(player, `Habilidade criada: §b${s.name}§r.`, { important: true, snd: 'random.levelup' });
    return `§aCriada: ${s.name} (-${cost} pts, -${LIMITS.createMp} Magicules).`;
  }
  if (op === 'duplicate') {
    if (!opAllowed(d, 'duplicate')) return needNode('duplicate');
    if (d.skillCopies.length >= LIMITS.copies) return `§7Limite de ${LIMITS.copies} cópias atingido. Conceda uma antes.`;
    if (!dailyFree(d, 'duplicate', today)) return '§7Duplicação já usada hoje.';
    if (d.mp < LIMITS.duplicateMp) return `§cMagicules insuficientes (${LIMITS.duplicateMp}).`;
    const s = await pick(player, '§lDuplicação', 'Escolha uma habilidade universal sua.', duplicable(d).slice(0, 30), (x) => x.name);
    if (!s) return '';
    d.mp -= LIMITS.duplicateMp; d.skillCopies.push(s.id); d.opDays.duplicate = today; saveNow(player);
    return `§aCópia guardada: ${s.name} (${d.skillCopies.length}/${LIMITS.copies}).`;
  }
  if (op === 'grant') {
    if (!opAllowed(d, 'grant')) return needNode('grant');
    if (!d.skillCopies.length) return '§7Nenhuma cópia guardada (use Duplicar).';
    const others = world.getAllPlayers().filter((p) => p.id !== player.id && p.dimension.id === player.dimension.id && V.dist(p.location, player.location) <= LIMITS.grantRange);
    if (!others.length) return `§7Nenhum jogador a até ${LIMITS.grantRange} blocos.`;
    const target = await pick(player, '§lConcessão', 'Escolha o jogador.', others, (p) => p.name);
    if (!target) return '';
    const id = await pick(player, '§lConcessão', 'Escolha a cópia.', d.skillCopies, (x) => SKILLS[x].name);
    if (!id) return '';
    const td = getData(target);
    const chk = grantCheck(td, id);
    if (!chk.ok) return `§c${chk.why}`;
    const ask = new MessageFormData().title('§lHabilidade oferecida').body(`${player.name} quer conceder a você: §b${SKILLS[id].name}§r.\nAceitar?`).button1('Recusar').button2('§aAceitar');
    const ans = await showForm(target, ask);
    if (!ans || ans.canceled || ans.selection !== 1) return `§7${target.name} recusou.`;
    if (!grantCheck(getData(target), id).ok) return '§cNão é mais possível conceder.';
    td.unlocked.add(id); refreshStats(td); saveNow(target);
    d.skillCopies.splice(d.skillCopies.indexOf(id), 1); saveNow(player);
    try { target.sendMessage(`§b${player.name} concedeu a você: ${SKILLS[id].name}.`); } catch { /* */ }
    return `§aConcedida a ${target.name}: ${SKILLS[id].name}.`;
  }
  if (op === 'store' || op === 'restore') {
    if (!opAllowed(d, 'storage')) return needNode('storage');
    if (op === 'store') {
      if (!d.equipped.length) return '§7A barra está vazia.';
      if (d.loadouts.length >= LIMITS.loadouts) d.loadouts.shift();
      d.loadouts.push({ name: `Conjunto ${d.loadouts.length + 1}`, ids: [...d.equipped] });
      saveNow(player);
      return `§aBarra guardada (${d.loadouts.length}/${LIMITS.loadouts}).`;
    }
    const l = await pick(player, '§lRecuperar barra', 'Escolha um conjunto.', d.loadouts, (x) => `${x.name}\n§7${x.ids.map((i) => SKILLS[i].name).join(', ')}`);
    if (!l) return '';
    d.equipped = l.ids.filter((i) => d.unlocked.has(i));
    d.slot = 0; saveNow(player);
    return `§aBarra recuperada: ${d.equipped.map((i) => SKILLS[i].name).join(', ') || 'vazia'}.`;
  }
  return '';
}

export { evaluate };
