// Tensura RPG — ponto de entrada do Behavior Pack.
//
// Acesso sem comandos:
//   • Grimório do Grande Sábio (tensura:great_sage_tome): usar = abre a árvore.
//   • Cristal de Magicules (tensura:magic_crystal): usar = conjura a magia do slot
//     atual; agachado + usar = próxima magia equipada.
// Ambos são entregues no primeiro login e também podem ser fabricados.
import { world, system, ItemStack } from '@minecraft/server';
import { SKILLS } from './data/registry.js';
import { getData, saveNow, forget, startAutosave, markDirty } from './systems/storage.js';
import { startMagicules, showHud } from './systems/magicules.js';
import { startPassives } from './systems/passives.js';
import { startCombat } from './systems/combat.js';
import { startLeveling } from './systems/leveling.js';
import { startSummons } from './systems/summons.js';
import { startFields } from './systems/fields.js';
import { startBolts } from './systems/bolts.js';
import { startTempBlocks } from './systems/tempBlocks.js';
import { setEffectMenus } from './systems/effects.js';
import { cast } from './systems/casting.js';
import { openMenu } from './ui/treeUI.js';
import { MENUS, mimicMenu, storageMenu } from './ui/menus.js';
import { runIntro, restoreControls, introRunning } from './systems/intro.js';

const TOME = 'tensura:great_sage_tome';
const CRYSTAL = 'tensura:magic_crystal';

setEffectMenus({ mimic: mimicMenu, storage: storageMenu });

world.afterEvents.itemUse.subscribe((ev) => {
  const player = ev.source;
  const id = ev.itemStack?.typeId;
  if (introRunning(player)) return;
  if (id === TOME) {
    openMenu(player, MENUS);
  } else if (id === CRYSTAL) {
    const d = getData(player);
    if (!d.race) { openMenu(player, MENUS); return; }
    if (!d.equipped.length) { player.sendMessage('§7Nenhuma magia equipada. Abra o Grimório, desbloqueie magias ativas e equipe-as no Cristal.'); return; }
    if (player.isSneaking) {
      d.slot = (d.slot + 1) % d.equipped.length;
      markDirty(player);
      showHud(player);
      return;
    }
    cast(player, d.equipped[d.slot % d.equipped.length]);
  }
});

function giveStarterItems(player) {
  const d = getData(player);
  if (d.gifted) return;
  const inv = player.getComponent('minecraft:inventory')?.container;
  if (!inv) return;
  for (const id of [TOME, CRYSTAL]) {
    const left = inv.addItem(new ItemStack(id, 1));
    if (left) player.dimension.spawnItem(left, player.location);
  }
  d.gifted = true;
  saveNow(player);
  player.sendMessage('§6§lTensura RPG:§r você recebeu o §bGrimório do Grande Sábio§r (abre o menu e a árvore de habilidades) e o §dCristal de Magicules§r (usa as habilidades da barra; agachado + usar troca de espaço).');
}

// Primeira entrada: apresentação do Grande Sábio → escolha de raça → itens iniciais.
// Se o jogador sair no meio, a apresentação recomeça na próxima entrada (sem duplicar itens).
function onJoin(player) {
  if (!player.isValid) return;
  const d = getData(player);
  restoreControls(player);
  if (!d.introDone && !d.race) runIntro(player, giveStarterItems);
  else giveStarterItems(player);
}

world.afterEvents.playerSpawn.subscribe((ev) => {
  if (ev.initialSpawn) system.runTimeout(() => onJoin(ev.player), 60);
});

world.beforeEvents.playerLeave.subscribe((ev) => {
  const id = ev.player.id;
  // Salvar não é permitido dentro de um evento "before": o salvamento periódico
  // (a cada 5 s) e os salvamentos imediatos já cobriram o estado; só liberamos a memória.
  system.run(() => forget(id));
});

world.afterEvents.worldLoad.subscribe(() => {
  startTempBlocks();
  startAutosave();
  startMagicules();
  startPassives();
  startSummons();
  startFields();
  startBolts();
  for (const p of world.getAllPlayers()) system.runTimeout(() => onJoin(p), 60);
  console.log(`[Tensura] Carregado: ${Object.keys(SKILLS).length} habilidades.`);
});

startCombat();
startLeveling();
