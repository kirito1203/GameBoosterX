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
import { msg, sound } from '../systems/util.js';

const icon = (id) => `${TEX.icon}${iconName(id)}`;

export async function chooseRace(player) {
  const starts = Object.values(RACES).filter((r) => r.start);
  const f = new ActionFormData().title('§lEscolha sua raça')
    .body('Escolha a raça com que você renasceu neste mundo. A escolha é §lpermanente§r: depois você só muda evoluindo pela Árvore Racial.\n\n§7Raças marcadas como (original) são criações do addon.');
  for (const r of starts) f.button(`${r.name}${r.origin === 'original' ? ' §8(original)' : ''}\n§8MP ${r.mp} · ${LINEAGES[r.lineage].name}`, icon(`r.${r.lineage}.root`));
  const res = await showForm(player, f);
  if (!res || res.canceled || res.selection === undefined) return;
  const race = starts[res.selection];
  const initial = initialSkillsFor(race.id).map((id) => `• ${SKILLS[id].name}`).join('\n');
  const evos = TREES[`race.${race.lineage}`].skills.filter((s) => s.type === 'evolution').map((s) => RACES[s.evolveTo].name).join(', ');
  const m = new MessageFormData().title(`§l${race.name}`)
    .body(`${race.desc}\n\n${raceSummary(race.id)}\n\n§eHabilidades iniciais:§r\n${initial}\n\n§eEvoluções possíveis:§r ${evos}\n\n§7Origem: ${race.origin === 'obra' ? 'Tensura' : race.origin === 'adaptado' ? 'inspirada em Tensura' : 'original do addon'}`)
    .button1('§cVoltar').button2('§aEscolher esta raça');
  const ok = await showForm(player, m);
  if (!ok || ok.canceled || ok.selection !== 1) return chooseRace(player);
  const d = getData(player);
  d.race = race.id;
  d.level = 1;
  d.ep = 0;
  d.points = CONFIG.leveling.startingPoints;
  for (const id of initialSkillsFor(race.id)) d.unlocked.add(id);
  refreshStats(d);
  d.mp = d.stats.maxMp;
  d.ui.tab = 'racial';
  for (const id of d.unlocked) if (SKILLS[id].type === 'active' && !d.equipped.includes(id)) d.equipped.push(id);
  saveNow(player);
  sound(player.dimension, 'random.totem', player.location);
  msg(player, `§6§lVocê renasceu como ${race.name}!§r Você tem §e${d.points} pontos§r. Toque nos nós da árvore para desbloquear habilidades.`);
}

export async function grimoire(player) {
  for (;;) {
    const d = getData(player);
    const actives = [...d.unlocked].map((id) => SKILLS[id]).filter((s) => s.type === 'active');
    const passives = [...d.unlocked].map((id) => SKILLS[id]).filter((s) => s.type === 'passive');
    const f = new ActionFormData().title('§lGrimório · Magias aprendidas')
      .body(`§dMagicules: ${Math.floor(d.mp)}/${d.stats.maxMp}§r\nCristal de Magicules: ${d.equipped.length}/9 magias equipadas.\nUse o Cristal para conjurar; agachado + usar troca de magia.\n\n§ePassivas ativas (${passives.length}):§r\n${passives.map((s) => `• ${s.name}`).join('\n') || '§7nenhuma'}`);
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
      else if (d.equipped.length < 9) d.equipped.push(s.id);
      else msg(player, '§cO Cristal comporta no máximo 9 magias.');
      if (d.slot >= d.equipped.length) d.slot = 0;
      saveNow(player);
      return null;
    case 2:
      if (slot >= 0) d.equipped.splice(slot, 1);
      d.equipped.unshift(s.id);
      if (d.equipped.length > 9) d.equipped.length = 9;
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

export const MENUS = { chooseRace, grimoire, status };
export { system };
