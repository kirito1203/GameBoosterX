// BANCO DE ABSORÇÃO (Rimuru / Slime)
// As criaturas da obra não existem no Minecraft: a ligação com os mobs do jogo é uma ADAPTAÇÃO do
// addon (campo `mc`). `work` é a criatura da obra. Absorver o mob DESCOBRE as habilidades listadas
// (não desbloqueia: ainda custa pontos e exige os requisitos de cada uma).
// `needs`: habilidades que o jogador precisa ter para conseguir descobrir (ex.: evolução do Predador).
import { P } from './common.js';

export const ABSORPTION = [
  { id: 'abs.spider', work: 'Aranha Negra (Black Spider)', mc: ['minecraft:spider', 'minecraft:cave_spider'],
    skills: [P('sticky_thread'), P('steel_thread')], method: 'Absorção', needs: {}, evolutions: [P('universal_thread')],
    compat: 'Slime', src: 'anime' },
  { id: 'abs.armorsaurus', work: 'Armorsaurus', mc: ['minecraft:armadillo', 'minecraft:turtle'],
    skills: [P('body_armor')], method: 'Absorção', needs: {}, evolutions: [], compat: 'Slime', src: 'anime' },
  { id: 'abs.serpent', work: 'Serpente da Tempestade (Tempest Serpent)', mc: ['minecraft:pufferfish', 'minecraft:bee'],
    skills: [P('poison_breath'), P('poison_resist'), P('heat_sense')], method: 'Absorção', needs: {}, evolutions: [P('abnormal_null')],
    compat: 'Slime', src: 'anime' },
  { id: 'abs.centipede', work: 'Centopeia Maligna (Evil Centipede)', mc: ['minecraft:silverfish', 'minecraft:endermite'],
    skills: [P('paralyzing_breath')], method: 'Absorção', needs: {}, evolutions: [], compat: 'Slime', src: 'anime' },
  { id: 'abs.bat', work: 'Morcego Gigante (Giant Bat)', mc: ['minecraft:bat'],
    skills: [P('ultrasonic'), P('vampirism')], method: 'Absorção', needs: {}, evolutions: [], compat: 'Slime', src: 'anime' },
  { id: 'abs.ifrit', work: 'Ifrit (espírito de fogo de Shizu)', mc: ['minecraft:blaze'],
    skills: [P('flame_manip'), P('flame_body'), P('clone')], method: 'Absorção com Predador', needs: { [P('clone')]: [P('gluttony')], '*': [P('predator')] },
    evolutions: [P('black_flame'), P('fire_null')], compat: 'Slime', src: 'anime', unverified: true,
    note: 'A obtenção de Anulação de Ataques de Fogo por Ifrit é da obra; os nomes das demais precisam de verificação.' },
  { id: 'abs.ranga', work: 'Linhagem de Ranga (Lobos da Tempestade)', mc: ['minecraft:wolf'],
    skills: [P('shadow_motion'), P('thought_comm'), P('ultra_smell'), P('coercion'), P('black_lightning')], method: 'Absorção',
    needs: { [P('black_lightning')]: [P('gluttony')] }, evolutions: [P('black_thunder'), P('spatial_motion')], compat: 'Slime', src: 'anime',
    note: 'A criatura de origem exata de cada habilidade do lobo precisa de verificação.' },
];

/** Entradas do banco que correspondem a uma espécie absorvida. */
export function entriesFor(typeId) {
  return ABSORPTION.filter((e) => e.mc.includes(typeId));
}

/** Habilidades que absorver `typeId` descobre, dadas as habilidades que o jogador já tem. */
export function discoverable(typeId, unlocked) {
  const out = [];
  for (const e of entriesFor(typeId)) {
    for (const id of e.skills) {
      const need = e.needs[id] ?? e.needs['*'] ?? [];
      out.push({ id, entry: e, ok: need.every((n) => unlocked.has(n)), need });
    }
  }
  return out;
}
