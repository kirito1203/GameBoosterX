// Apresentação da reencarnação: o Grande Sábio surge diante do jogador, fala em
// estilo de RPG (texto aparecendo letra a letra) e abre a escolha de raça.
//
// Recursos do Bedrock usados (API estável 2.0.0): fade colorido da câmera, câmera livre
// apontando para o Grande Sábio, bloqueio de movimento (inputPermissions), título +
// subtítulo atualizados a cada tick (efeito máquina de escrever) e uma entidade própria
// (tensura:great_sage) com modelo e animação do resource pack.
import { world, system, EasingType, InputPermissionCategory } from '@minecraft/server';
import { INTRO_LINES } from '../data/raceCards.js';
import { RACES } from '../data/registry.js';
import { getData, saveNow } from './storage.js';
import { V, particle } from './util.js';
import { selectRace } from '../ui/raceUI.js';

const running = new Set();

function lockInput(player, locked) {
  try {
    player.inputPermissions.setPermissionCategory(InputPermissionCategory.Movement, !locked);
    player.inputPermissions.setPermissionCategory(InputPermissionCategory.Camera, !locked);
  } catch { /* versões sem suporte: segue sem bloquear */ }
}

/** Garante que o jogador não fique preso se a apresentação foi interrompida. */
export function restoreControls(player) {
  if (running.has(player.id)) return;
  lockInput(player, false);
  try { player.camera.clear(); } catch { /* */ }
}

function wrap(text, max = 46) {
  const words = text.split(' ');
  const lines = [];
  let cur = '';
  for (const w of words) {
    if ((cur + ' ' + w).trim().length > max) { lines.push(cur.trim()); cur = w; } else cur += ` ${w}`;
  }
  if (cur.trim()) lines.push(cur.trim());
  return lines.join('\n');
}

function show(player, text) {
  try {
    player.onScreenDisplay.setTitle('§b§lGrande Sábio', { subtitle: `§f${text}`, fadeInDuration: 0, stayDuration: 400, fadeOutDuration: 0 });
  } catch { /* */ }
}

/** Escreve uma fala letra a letra. Agachar completa a fala na hora. */
async function typeLine(player, line) {
  const full = wrap(line);
  let wasSneaking = player.isSneaking;
  for (let k = 1; k <= full.length; k += 2) {
    if (!player.isValid) return;
    show(player, full.slice(0, k));
    if (k % 6 === 1) { try { player.playSound('random.click', { volume: 0.18, pitch: 1.9 }); } catch { /* */ } }
    const sneaking = player.isSneaking;
    if (sneaking && !wasSneaking) break;
    wasSneaking = sneaking;
    await system.waitTicks(1);
  }
  show(player, full);
}

/** Espera o jogador agachar (avançar) ou o tempo acabar. */
async function waitAdvance(player, ticks) {
  let wasSneaking = player.isSneaking;
  for (let t = 0; t < ticks; t++) {
    if (!player.isValid) return;
    if (t % 10 === 0) { try { player.onScreenDisplay.setActionBar('§7Agache para continuar  §b▼'); } catch { /* */ } }
    const s = player.isSneaking;
    if (s && !wasSneaking) return;
    wasSneaking = s;
    await system.waitTicks(1);
  }
}

function ringParticles(dim, center, r, n, id) {
  for (let i = 0; i < n; i++) {
    const a = (i / n) * Math.PI * 2;
    particle(dim, id, { x: center.x + Math.cos(a) * r, y: center.y, z: center.z + Math.sin(a) * r });
  }
}

export async function runIntro(player, onFinished) {
  if (running.has(player.id)) return;
  const d = getData(player);
  if (d.introDone || d.race) { d.introDone = true; return; }
  running.add(player.id);
  let sage = null;
  let aura = null;
  try {
    lockInput(player, true);
    // ETAPA 1 — início: tela tinge de azul/roxo, luz e o Grande Sábio surge.
    try {
      player.camera.fade({ fadeColor: { red: 0.16, green: 0.08, blue: 0.42 }, fadeTime: { fadeInTime: 0.6, holdTime: 1.2, fadeOutTime: 1.6 } });
    } catch { /* */ }
    try { player.playSound('beacon.activate', { volume: 0.8, pitch: 0.8 }); } catch { /* */ }
    await system.waitTicks(30);
    if (!player.isValid) return;
    const dir = V.flat(player.getViewDirection());
    const side = { x: -dir.z, y: 0, z: dir.x };
    const at = V.add(player.location, V.add(V.mul(dir, 4), { x: 0, y: 0.4, z: 0 }));
    const dim = player.dimension;
    sage = dim.spawnEntity('tensura:great_sage', at);
    sage.nameTag = '§bGrande Sábio';
    try { sage.teleport(at, { facingLocation: player.getHeadLocation() }); } catch { /* */ }
    ringParticles(dim, V.add(at, { x: 0, y: 0.2, z: 0 }), 1.6, 18, 'minecraft:endrod');
    particle(dim, 'minecraft:totem_particle', V.add(at, { x: 0, y: 1.5, z: 0 }));
    try { player.playSound('random.totem', { volume: 0.5, pitch: 1.4 }); } catch { /* */ }
    // Partículas leves enquanto ele estiver presente (a cada 6 ticks, 3 partículas).
    let t = 0;
    aura = system.runInterval(() => {
      if (!sage?.isValid) return;
      t++;
      const c = sage.location;
      const a = t * 0.6;
      particle(dim, 'minecraft:basic_portal_particle', { x: c.x + Math.cos(a) * 1.2, y: c.y + 0.4 + (t % 10) * 0.18, z: c.z + Math.sin(a) * 1.2 });
      particle(dim, 'minecraft:endrod', { x: c.x - Math.cos(a) * 1.0, y: c.y + 1.2, z: c.z - Math.sin(a) * 1.0 });
      if (t % 3 === 0) particle(dim, 'minecraft:electric_spark_particle', V.add(c, { x: 0, y: 2.3, z: 0 }));
    }, 6);
    // Câmera aproxima e enquadra o Grande Sábio.
    try {
      const cam = V.add(player.getHeadLocation(), V.add(V.mul(dir, 1.2), V.add(V.mul(side, 1.3), { x: 0, y: 0.2, z: 0 })));
      player.camera.setCamera('minecraft:free', {
        location: cam, facingLocation: V.add(at, { x: 0, y: 1.5, z: 0 }),
        easeOptions: { easeTime: 2.0, easeType: EasingType.InOutSine },
      });
    } catch { /* */ }
    await system.waitTicks(45);

    // ETAPA 2 — diálogo.
    for (let i = 0; i < INTRO_LINES.length - 1; i++) {
      if (!player.isValid) return;
      await typeLine(player, INTRO_LINES[i]);
      await waitAdvance(player, 110);
    }
    // ETAPA 3 — apresenta a escolha e abre a tela de raças.
    await typeLine(player, INTRO_LINES[INTRO_LINES.length - 1]);
    await system.waitTicks(25);
    try { player.onScreenDisplay.setTitle(' ', { fadeInDuration: 0, stayDuration: 1, fadeOutDuration: 5 }); } catch { /* */ }
    const race = await selectRace(player, { sageText: INTRO_LINES[INTRO_LINES.length - 1], mandatory: true });
    if (!race || !player.isValid) return;

    // Conclusão da reencarnação.
    try { player.camera.fade({ fadeColor: { red: 0.85, green: 0.95, blue: 1 }, fadeTime: { fadeInTime: 0.4, holdTime: 0.6, fadeOutTime: 1.4 } }); } catch { /* */ }
    for (let k = 0; k < 3; k++) ringParticles(player.dimension, V.add(player.location, { x: 0, y: 0.3 + k * 0.8, z: 0 }), 1.2, 14, 'minecraft:totem_particle');
    await system.waitTicks(10);
    try { player.onScreenDisplay.setTitle('§6§lReencarnação concluída', { subtitle: `§bVocê renasceu como ${RACES[race].name}`, fadeInDuration: 10, stayDuration: 60, fadeOutDuration: 20 }); } catch { /* */ }
    if (sage?.isValid) { ringParticles(dim, sage.location, 1.0, 16, 'minecraft:basic_portal_particle'); }
    d.introDone = true;
    saveNow(player);
    onFinished?.(player);
  } catch (e) {
    console.warn(`[Tensura] Apresentação interrompida: ${e}`);
  } finally {
    if (aura !== null) system.clearRun(aura);
    try { if (sage?.isValid) sage.remove(); } catch { /* */ }
    running.delete(player.id);
    if (player.isValid) {
      lockInput(player, false);
      try { player.camera.clear(); } catch { /* */ }
    }
  }
}

export function introRunning(player) {
  return running.has(player.id);
}

export { world };
