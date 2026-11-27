// Menus auxiliares (formulários padrão do jogo): escolha de raça, Grimório
// (magias aprendidas), Status, Transformação e Armazenamento Espacial.
import { ItemStack, EquipmentSlot, EnchantmentTypes, system } from '@minecraft/server';
import { ActionFormData, MessageFormData } from '@minecraft/server-ui';
import { CONFIG, epForLevel } from '../config.js';
import { SKILLS, TREES, RACES, LINEAGES, initialSkillsFor } from '../data/registry.js';
import { mpCost, mimicTraits } from '../core/stats.js';
import { describeFx } from '../core/describe.js';
import { raceSummary } from '../core/view.js';
import { TEX, iconName } from '../core/uiSpec.js';
import { getData, saveNow, refreshStats, getStorage, setStorage, markDirty } from '../systems/storage.js';
import { cast } from '../systems/casting.js';
import { showForm } from './treeUI.js';
import { selectRace } from './raceUI.js';
import { msg, sound } from '../systems/util.js';

const icon = (id) => `${TEX.icon}${iconName(id)}`;

// A escolha de raça agora é a tela própria (ui/raceUI.js), também usada pela apresentação.
export async function chooseRace(player) {
  return selectRace(player, {});
}

export async function grimoire(player) {
  for (;;) {
    const d = getData(player);
    const actives = [...d.unlocked].map((id) => SKILLS[id]).filter((s) => s.type === 'active');
    const passives = [...d.unlocked].map((id) => SKILLS[id]).filter((s) => s.type === 'passive');
    const f = new ActionFormData().title('§lGrimório · Magias aprendidas')
      .body(`§dMagicules: ${Math.floor(d.mp)}/${d.stats.maxMp}§r\nBarra de Habilidades: ${d.equipped.length}/${CONFIG.ui.barSlots} espaços.\nUse o Cristal de Magicules para conjurar a habilidade da barra; agachado + usar troca de espaço.\nMagias gastam Magículas; habilidades raciais gastam Aura (${Math.floor(d.aura)}/${d.stats.maxAura}).\n\n§ePassivas ativas (${passives.length}):§r\n${passives.map((s) => `• ${s.name}`).join('\n') || '§7nenhuma'}`);
    f.button('§l« Voltar à árvore');
    for (const s of actives) {
      const slot = d.equipped.indexOf(s.id);
      f.button(`${slot >= 0 ? `§b[${slot + 1}] ` : ''}§r${s.name}\n§8${mpCost(s, d.stats)} MP · ${s.cooldown}s · ${TREES[s.tree].short}`, icon(s.id));
    }
    const res = await showForm(player, f);
    if (!res || res.canceled || !res.selection) return null;
    const s = actives[res.selection - 1];
    const r = await skillMenu(player, s);
    if (r?.goto || r?.close) return r;
  }
}

async function skillMenu(player, s) {
  const d = getData(player);
  const slot = d.equipped.indexOf(s.id);
  const f = new ActionFormData().title(`§l${s.name}`)
    .body(`§7${s.original} · ${s.category}\n\n${s.desc}\n\n§7Efeito: ${describeFx(s.fx)}\nCusto: ${mpCost(s, d.stats)} Magicules · Recarga: ${s.cooldown}s`)
    .button('§aConjurar agora', icon(s.id))
    .button(slot >= 0 ? '§cRemover do Cristal' : '§bEquipar no Cristal', `${TEX.ui}btn_equip`)
    .button('Tornar a primeira do Cristal')
    .button('Ver na árvore', `${TEX.ui}btn_link`)
    .button('« Voltar');
  const res = await showForm(player, f);
  if (!res || res.canceled) return null;
  switch (res.selection) {
    case 0: cast(player, s.id); return { close: true };
    case 1:
      if (slot >= 0) d.equipped.splice(slot, 1);
      else if (d.equipped.length < CONFIG.ui.barSlots) d.equipped.push(s.id);
      else msg(player, `§cA barra tem ${CONFIG.ui.barSlots} espaços. Remova uma habilidade antes.`);
      if (d.slot >= d.equipped.length) d.slot = 0;
      saveNow(player);
      return null;
    case 2:
      if (slot >= 0) d.equipped.splice(slot, 1);
      d.equipped.unshift(s.id);
      if (d.equipped.length > CONFIG.ui.barSlots) d.equipped.length = CONFIG.ui.barSlots;
      d.slot = 0;
      saveNow(player);
      return null;
    case 3: return { goto: s.id };
    default: return null;
  }
}

export async function status(player) {
  const d = getData(player);
  const r = RACES[d.race];
  const s = d.stats;
  const pct = (o) => Object.entries(o).filter(([, v]) => v > 0).map(([k, v]) => `${k} ${Math.round(v * 100)}%`).join(', ') || 'nenhuma';
  const flags = [...d.flags].map((f) => ({ killed_wither: 'Wither', killed_ender_dragon: 'Ender Dragon', killed_warden: 'Warden' }[f] ?? f)).join(', ') || 'nenhuma';
  const body = [
    `§b§l${player.name}§r — ${r.name} §7(${r.original})`,
    `Nível §e${d.level}§r · EP ${Math.floor(d.ep)}/${epForLevel(d.level)} · Pontos §e${d.points}`,
    `§dMagicules ${Math.floor(d.mp)}/${s.maxMp}§r · regeneração ${(s.regen * s.regenMult).toFixed(1)}/s${d.mpBonus ? ` · bônus de absorção +${d.mpBonus}` : ''}`,
    `Habilidades desbloqueadas: ${d.unlocked.size}`,
    `Resistências: ${pct(s.resist)}`,
    `Fraquezas: ${pct(s.weak)}`,
    `Efeitos permanentes: ${[...s.effects.keys()].join(', ') || 'nenhum'}`,
    `Roubo de vida: ${Math.round(s.lifesteal * 100)}%`,
    `Conquistas: ${flags}`,
    d.absorbed.length ? `Espécies absorvidas: ${d.absorbed.map((t) => mimicTraits(t).name).join(', ')}` : '',
    d.mimic ? `§eForma atual: ${mimicTraits(d.mimic).name}` : '',
    '',
    `§7${r.desc}`,
  ].filter(Boolean).join('\n');
  await showForm(player, new ActionFormData().title('§lStatus').body(body).button('« Voltar'));
}

export async function mimicMenu(player) {
  const d = getData(player);
  if (!d.absorbed.length) { msg(player, '§7Nenhuma espécie absorvida ainda. Use Absorção em criaturas enfraquecidas.'); return; }
  const f = new ActionFormData().title('§lTransformação').body('Escolha uma forma absorvida. A forma ativa consome 1 Magicule por segundo.');
  f.button('§lForma original');
  for (const t of d.absorbed) {
    const m = mimicTraits(t);
    f.button(`${d.mimic === t ? '§a» ' : ''}${m.name}\n§8${m.eff.map(([e]) => e).join(', ')}`);
  }
  const res = await showForm(player, f);
  if (!res || res.canceled || res.selection === undefined) return;
  d.mimic = res.selection === 0 ? null : d.absorbed[res.selection - 1];
  refreshStats(d);
  markDirty(player);
  msg(player, d.mimic ? `§eTransformado: ${mimicTraits(d.mimic).name}` : '§7Forma original restaurada.');
}

// ── Armazenamento Espacial ──
const NO_STORE = /(shulker_box|bundle)/;

function serializeItem(item) {
  const o = { id: item.typeId, n: item.amount };
  if (item.nameTag) o.name = item.nameTag;
  const lore = item.getLore();
  if (lore.length) o.lore = lore;
  const ench = item.getComponent('minecraft:enchantable');
  if (ench) {
    const list = ench.getEnchantments().map((e) => [e.type.id, e.level]);
    if (list.length) o.ench = list;
  }
  const dur = item.getComponent('minecraft:durability');
  if (dur?.damage) o.dmg = dur.damage;
  return o;
}

function deserializeItem(o) {
  const item = new ItemStack(o.id, o.n);
  if (o.name) item.nameTag = o.name;
  if (o.lore) item.setLore(o.lore);
  if (o.ench) {
    const ench = item.getComponent('minecraft:enchantable');
    for (const [id, level] of o.ench) { try { const type = EnchantmentTypes.get(id); if (type) ench?.addEnchantment({ type, level }); } catch { /* */ } }
  }
  if (o.dmg) { const dur = item.getComponent('minecraft:durability'); if (dur) dur.damage = o.dmg; }
  return item;
}

export async function storageMenu(player) {
  const slots = 18;
  for (;;) {
    const items = getStorage(player);
    const f = new ActionFormData().title('§lArmazenamento Espacial').body(`Espaços usados: ${items.length}/${slots}. Toque num item para retirá-lo.`);
    f.button('§a+ Guardar item da mão');
    for (const o of items) f.button(`${o.name ?? o.id.replace('minecraft:', '')} x${o.n}${o.ench ? ' §d(encantado)' : ''}`);
    f.button('« Fechar');
    const res = await showForm(player, f);
    if (!res || res.canceled || res.selection === undefined || res.selection === items.length + 1) return;
    const eq = player.getComponent('minecraft:equippable');
    if (res.selection === 0) {
      const item = eq?.getEquipment(EquipmentSlot.Mainhand);
      if (!item) { msg(player, '§7Sua mão está vazia.'); continue; }
      if (NO_STORE.test(item.typeId)) { msg(player, '§cItens com conteúdo (caixas, sacos) não podem ser guardados: o conteúdo seria perdido.'); continue; }
      if (item.typeId.startsWith('tensura:')) { msg(player, '§7Itens do addon não podem ser guardados.'); continue; }
      if (items.length >= slots) { msg(player, '§cArmazenamento cheio.'); continue; }
      items.push(serializeItem(item));
      eq.setEquipment(EquipmentSlot.Mainhand, undefined);
      setStorage(player, items);
      sound(player.dimension, 'mob.shulker.teleport', player.location, { volume: 0.5 });
    } else {
      const [o] = items.splice(res.selection - 1, 1);
      const inv = player.getComponent('minecraft:inventory')?.container;
      let item;
      try { item = deserializeItem(o); } catch (e) { msg(player, `§cFalha ao recuperar ${o.id}: ${e}`); items.splice(res.selection - 1, 0, o); continue; }
      const left = inv?.addItem(item);
      if (left) player.dimension.spawnItem(left, player.location);
      setStorage(player, items);
    }
  }
}

export const MENUS = { chooseRace, grimoire, status, mimic: mimicMenu, storage: storageMenu };
export { system };
