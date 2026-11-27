// Manifestação do Grande Sábio no mundo (entidade espiritual, só visual).
//
// A entidade não colide (caixa de 0,01), não recebe dano, não tem IA e some sozinha após
// 5 min se o script perder o controle dela. O estado (idle/talk/vanish) é uma propriedade
// sincronizada com o cliente: o controlador de animação do resource pack troca a animação.
import { system } from '@minecraft/server';
import { V, particle } from './util.js';

const STATE = 'tensura:state';
const STAGE = 'tensura:stage';

/** Aparência do estágio atual (Grande Sábio, Raphael ou Ciel). */
export function setSageStage(e, stage) {
  try { if (e?.isValid) e.setProperty(STAGE, stage); } catch { /* */ }
}

export function spawnSage(player, at, name = '§bGrande Sábio') {
  const dim = player.dimension;
  const e = dim.spawnEntity('tensura:great_sage', at);
  e.nameTag = name;
  try { e.teleport(at, { facingLocation: player.getHeadLocation() }); } catch { /* */ }
  for (let i = 0; i < 16; i++) {
    const a = (i / 16) * Math.PI * 2;
    particle(dim, 'minecraft:endrod', { x: at.x + Math.cos(a) * 1.5, y: at.y + 0.2, z: at.z + Math.sin(a) * 1.5 });
  }
  return e;
}

/** Liga/desliga a animação de fala. */
export function sageTalk(e, talking) {
  try { if (e?.isValid) e.setProperty(STATE, talking ? 'talk' : 'idle'); } catch { /* */ }
}

/** Animação de desaparecimento e remoção. */
export async function vanishSage(e) {
  if (!e?.isValid) return;
  try { e.setProperty(STATE, 'vanish'); } catch { /* */ }
  const dim = e.dimension;
  const c = e.location;
  for (let i = 0; i < 12; i++) particle(dim, 'minecraft:basic_portal_particle', V.add(c, { x: (Math.random() - 0.5) * 1.4, y: Math.random() * 2.4, z: (Math.random() - 0.5) * 1.4 }));
  await system.waitTicks(16);
  try { if (e.isValid) e.remove(); } catch { /* */ }
}

/** Partículas discretas em volta (3 a cada 6 ticks). Devolve o id do intervalo. */
export function sageAura(e) {
  let t = 0;
  return system.runInterval(() => {
    if (!e?.isValid) return;
    t++;
    const c = e.location;
    const a = t * 0.6;
    particle(e.dimension, 'minecraft:basic_portal_particle', { x: c.x + Math.cos(a) * 1.2, y: c.y + 0.6 + (t % 10) * 0.18, z: c.z + Math.sin(a) * 1.2 });
    particle(e.dimension, 'minecraft:endrod', { x: c.x - Math.cos(a) * 1.0, y: c.y + 1.4, z: c.z - Math.sin(a) * 1.0 });
    if (t % 3 === 0) particle(e.dimension, 'minecraft:electric_spark_particle', V.add(c, { x: 0, y: 2.6, z: 0 }));
  }, 6);
}
