import { S, defineTree } from '../dsl.js';

// Efeitos: ver systems/effects.js. Campos comuns:
//   k = tipo, el = elemento, dmg = dano, range = alcance, r = raio, dur = duração (s),
//   eff = [[efeito, segundos, amplificador]], ignite = segundos em chamas.
const O = 'adaptado';

export default defineTree('elemental', {
  name: 'Árvore Elemental', original: 'Elemental Magic', short: 'Elem.', kind: 'universal',
  root: 'elem.core', category: 'Magia Elemental',
}, [
  S('elem.core', 'Fundamentos Elementais', 'Elemental Basics', 'common', {
    cost: 10, lvl: 1, origin: 'original', major: true, cat: 'Magia Elemental',
    desc: 'Base de todas as magias elementais. Reduz o custo de Magicules das magias desta árvore.',
    fx: { k: 'p_affinity', tree: 'elemental', mult: 0.9 },
  }),

  // ── FOGO (leste) ─────────────────────────────────────────────
  S('elem.fire', 'Fogo', 'Fire', 'common', {
    cost: 10, lvl: 1, req: ['elem.core'], arm: 'E', origin: O, mp: 6, cd: 1, cat: 'Elemental · Fogo',
    desc: 'Dispara uma chama simples que incendeia o alvo.',
    fx: { k: 'bolt', el: 'fire', dmg: 4, range: 16, speed: 1.0, ignite: 2 },
  }),
  S('elem.fireball', 'Bola de Fogo', 'Fireball', 'common', {
    cost: 20, lvl: 4, req: ['elem.fire'], origin: O, mp: 14, cd: 3, cat: 'Elemental · Fogo',
    desc: 'Esfera de fogo que explode em área no impacto.',
    fx: { k: 'bolt', el: 'fire', dmg: 7, range: 24, speed: 1.1, r: 2.5, ignite: 4 },
  }),
  S('elem.fire_lance', 'Lança de Fogo', 'Fire Lance', 'rare', {
    cost: 35, lvl: 10, req: ['elem.fireball'], origin: O, mp: 22, cd: 4, cat: 'Elemental · Fogo',
    desc: 'Lança flamejante instantânea que atravessa vários inimigos em linha.',
    fx: { k: 'beam', el: 'fire', dmg: 10, range: 22, pierce: true, ignite: 4 },
  }),
  S('elem.fire_wall', 'Parede de Fogo', 'Fire Wall', 'rare', {
    cost: 30, lvl: 8, req: ['elem.fireball'], origin: O, mp: 20, cd: 10, cat: 'Elemental · Fogo',
    desc: 'Ergue uma parede de chamas à sua frente que queima quem a atravessa.',
    fx: { k: 'wall', block: 'minecraft:fire', len: 7, h: 1, dist: 4, dur: 8 },
  }),
  S('elem.firestorm', 'Tempestade de Fogo', 'Firestorm', 'epic', {
    cost: 80, lvl: 20, req: ['elem.fire_lance'], origin: O, major: true, mp: 60, cd: 25, cat: 'Elemental · Fogo',
    desc: 'Chuva de meteoros flamejantes sobre a área mirada.',
    fx: { k: 'rain', el: 'fire', dmg: 8, r: 6, waves: 6, every: 10, range: 28, ignite: 5 },
  }),
  S('elem.explosion', 'Explosão', 'Explosion', 'rare', {
    cost: 50, lvl: 15, req: ['elem.fire_lance'], origin: O, mp: 35, cd: 8, cat: 'Elemental · Explosão',
    desc: 'Projétil instável que detona no impacto (não destrói blocos por padrão).',
    fx: { k: 'bolt', el: 'explosion', dmg: 8, range: 28, speed: 1.2, r: 3, explode: 2.5 },
  }),
  S('elem.chain_explosion', 'Explosão em Cadeia', 'Chain Explosion', 'epic', {
    cost: 100, lvl: 25, req: ['elem.explosion', 'elem.firestorm'], origin: O, major: true, mp: 80, cd: 30,
    cat: 'Elemental · Explosão',
    desc: 'Sequência de explosões que avança em linha reta a partir de você.',
    fx: { k: 'chain', el: 'explosion', dmg: 10, count: 6, step: 3, every: 4, explode: 2.5, r: 3 },
  }),

  // ── ÁGUA e GELO (oeste) ─────────────────────────────────────
  S('elem.water', 'Água', 'Water', 'common', {
    cost: 10, lvl: 1, req: ['elem.core'], arm: 'W', origin: O, mp: 5, cd: 1, cat: 'Elemental · Água',
    desc: 'Jato de água que empurra o alvo e apaga o fogo em você.',
    fx: [{ k: 'bolt', el: 'water', dmg: 3, range: 16, speed: 1.2, knock: 0.8 }, { k: 'cleanse', fire: true }],
  }),
  S('elem.water_cutter', 'Corte de Água', 'Water Cutter', 'rare', {
    cost: 35, lvl: 8, req: ['elem.water'], origin: O, mp: 18, cd: 3, cat: 'Elemental · Água',
    desc: 'Lâmina de água pressurizada que corta tudo em linha.',
    fx: { k: 'beam', el: 'water', dmg: 9, range: 18, pierce: true },
  }),
  S('elem.water_jail', 'Prisão de Água', 'Water Jail', 'rare', {
    cost: 45, lvl: 12, req: ['elem.water_cutter'], origin: O, mp: 30, cd: 16, cat: 'Elemental · Água',
    desc: 'Aprisiona o alvo em uma esfera de água, imobilizando e afogando.',
    fx: { k: 'bind', el: 'water', range: 16, dur: 6, dps: 2, cage: 'water' },
  }),
  S('elem.freeze', 'Congelamento', 'Freeze', 'common', {
    cost: 25, lvl: 6, req: ['elem.water'], origin: O, mp: 14, cd: 6, cat: 'Elemental · Gelo',
    desc: 'Congela o alvo mirado, deixando-o quase imóvel.',
    fx: { k: 'debuff', el: 'ice', range: 16, dmg: 3, eff: [['slowness', 5, 4], ['mining_fatigue', 5, 2]] },
  }),
  S('elem.icicle_lance', 'Lança de Gelo', 'Icicle Lance', 'rare', {
    cost: 35, lvl: 10, req: ['elem.freeze'], origin: O, mp: 18, cd: 3, cat: 'Elemental · Gelo',
    desc: 'Dispara uma lança de gelo que retarda o alvo.',
    fx: { k: 'bolt', el: 'ice', dmg: 9, range: 24, speed: 1.4, eff: [['slowness', 4, 2]] },
  }),
  S('elem.icicle_spear', 'Lança de Gelo Perfurante', 'Icicle Spear', 'rare', {
    cost: 45, lvl: 14, req: ['elem.icicle_lance'], origin: O, mp: 26, cd: 5, cat: 'Elemental · Gelo',
    desc: 'Versão perfurante da Lança de Gelo: atravessa todos os inimigos em linha.',
    fx: { k: 'beam', el: 'ice', dmg: 12, range: 26, pierce: true, eff: [['slowness', 4, 3]] },
  }),
  S('elem.ice_wall', 'Parede de Gelo', 'Ice Wall', 'rare', {
    cost: 30, lvl: 9, req: ['elem.freeze'], origin: O, mp: 20, cd: 12, cat: 'Elemental · Gelo',
    desc: 'Ergue uma muralha de gelo temporária.',
    fx: { k: 'wall', block: 'minecraft:packed_ice', len: 7, h: 3, dist: 4, dur: 12 },
  }),
  S('elem.icicle_rain', 'Chuva de Gelo', 'Icicle Rain', 'epic', {
    cost: 70, lvl: 18, req: ['elem.icicle_spear'], origin: O, mp: 50, cd: 20, cat: 'Elemental · Gelo',
    desc: 'Estacas de gelo caem do céu sobre a área mirada.',
    fx: { k: 'rain', el: 'ice', dmg: 6, r: 5, waves: 6, every: 8, range: 26, eff: [['slowness', 3, 2]] },
  }),
  S('elem.ice_blizzard', 'Nevasca', 'Ice Blizzard', 'epic', {
    cost: 110, lvl: 26, req: ['elem.icicle_rain', 'elem.ice_wall'], origin: O, major: true, mp: 85, cd: 40,
    cat: 'Elemental · Gelo',
    desc: 'Uma nevasca ao seu redor fere e congela os inimigos continuamente.',
    fx: { k: 'field', el: 'ice', r: 8, dur: 8, dps: 3, enemyEff: [['slowness', 2, 3], ['mining_fatigue', 2, 1]], self: true },
  }),

  // ── TERRA (sul) ─────────────────────────────────────────────
  S('elem.stone_shot', 'Projétil de Pedra', 'Stone Shot', 'common', {
    cost: 10, lvl: 1, req: ['elem.core'], arm: 'S', origin: O, mp: 6, cd: 1, cat: 'Elemental · Terra',
    desc: 'Arremessa uma pedra compactada com força.',
    fx: { k: 'bolt', el: 'earth', dmg: 5, range: 18, speed: 1.3, knock: 0.6 },
  }),
  S('elem.earth_wall', 'Parede de Terra', 'Earth Wall', 'common', {
    cost: 25, lvl: 6, req: ['elem.stone_shot'], origin: O, mp: 16, cd: 12, cat: 'Elemental · Terra',
    desc: 'Levanta uma parede de pedra temporária.',
    fx: { k: 'wall', block: 'minecraft:cobblestone', len: 7, h: 3, dist: 3, dur: 15 },
  }),
  S('elem.mud_hand', 'Mão de Lama', 'Mud Hand', 'rare', {
    cost: 30, lvl: 9, req: ['elem.stone_shot'], origin: O, mp: 18, cd: 8, cat: 'Elemental · Terra',
    desc: 'Mãos de lama agarram o alvo e o prendem ao chão.',
    fx: { k: 'bind', el: 'earth', range: 16, dur: 4, dps: 1 },
  }),
  S('elem.earth_lock', 'Prisão de Terra', 'Earth Lock', 'rare', {
    cost: 50, lvl: 14, req: ['elem.mud_hand', 'elem.earth_wall'], origin: O, mp: 30, cd: 18, cat: 'Elemental · Terra',
    desc: 'Encerra o alvo em uma jaula de pedra temporária.',
    fx: { k: 'bind', el: 'earth', range: 16, dur: 6, dps: 1, cage: 'minecraft:cobblestone' },
  }),

  // ── VENTO e RELÂMPAGO (norte) ────────────────────────────────
  S('elem.wind_cutter', 'Corte de Vento', 'Wind Cutter', 'common', {
    cost: 10, lvl: 1, req: ['elem.core'], arm: 'N', origin: O, mp: 6, cd: 1, cat: 'Elemental · Vento',
    desc: 'Lâmina de vento veloz.',
    fx: { k: 'bolt', el: 'wind', dmg: 4, range: 22, speed: 1.8 },
  }),
  S('elem.wind_gust', 'Rajada de Vento', 'Wind Gust', 'common', {
    cost: 20, lvl: 4, req: ['elem.wind_cutter'], origin: O, mp: 12, cd: 5, cat: 'Elemental · Vento',
    desc: 'Rajada em cone que arremessa os inimigos para longe.',
    fx: { k: 'cone', el: 'wind', dmg: 2, range: 8, knock: 2.2, lift: 0.5 },
  }),
  S('elem.wind_protection', 'Proteção de Vento', 'Wind Protection', 'rare', {
    cost: 35, lvl: 10, req: ['elem.wind_gust'], origin: O, mp: 25, cd: 30, cat: 'Elemental · Vento',
    desc: 'Um redemoinho ao seu redor repele inimigos próximos e amortece quedas.',
    fx: [{ k: 'buff', eff: [['slow_falling', 20, 0], ['resistance', 20, 0]] },
      { k: 'field', el: 'wind', r: 3, dur: 20, self: true, repel: 1.2 }],
  }),
  S('elem.tornado_blade', 'Lâmina de Tornado', 'Tornado Blade', 'epic', {
    cost: 75, lvl: 20, req: ['elem.wind_gust'], origin: O, major: true, mp: 45, cd: 12, cat: 'Elemental · Vento',
    desc: 'Três lâminas de tornado que perfuram e erguem os inimigos.',
    fx: { k: 'bolt', el: 'wind', dmg: 11, range: 26, speed: 1.6, count: 3, spread: 12, pierce: true, r: 2,
      eff: [['levitation', 1, 1]] },
  }),
  S('elem.thunder', 'Trovão', 'Thunder', 'common', {
    cost: 25, lvl: 6, req: ['elem.wind_cutter'], origin: O, mp: 15, cd: 3, cat: 'Elemental · Relâmpago',
    desc: 'Descarga elétrica instantânea no alvo mirado.',
    fx: { k: 'beam', el: 'lightning', dmg: 8, range: 20 },
  }),
  S('elem.thunder_lance', 'Lança de Relâmpago', 'Thunder Lance', 'rare', {
    cost: 40, lvl: 12, req: ['elem.thunder'], origin: O, mp: 25, cd: 5, cat: 'Elemental · Relâmpago',
    desc: 'Lança de relâmpago que atravessa todos em linha.',
    fx: { k: 'beam', el: 'lightning', dmg: 12, range: 28, pierce: true },
  }),
  S('elem.thunder_orb', 'Esfera de Relâmpago', 'Thunder Orb', 'rare', {
    cost: 45, lvl: 14, req: ['elem.thunder'], origin: O, mp: 30, cd: 10, cat: 'Elemental · Relâmpago',
    desc: 'Esfera lenta que eletrocuta tudo ao redor durante o trajeto.',
    fx: { k: 'bolt', el: 'lightning', dmg: 9, range: 24, speed: 0.5, r: 3, pulse: { every: 5, r: 2.5, dmg: 3 } },
  }),
  S('elem.thunder_rain', 'Chuva de Relâmpagos', 'Thunder Rain', 'epic', {
    cost: 100, lvl: 24, req: ['elem.thunder_lance', 'elem.thunder_orb'], origin: O, major: true, mp: 80, cd: 35,
    cat: 'Elemental · Relâmpago',
    desc: 'Invoca relâmpagos reais sobre a área mirada.',
    fx: { k: 'rain', el: 'lightning', dmg: 10, r: 6, waves: 6, every: 8, range: 28, lightning: true },
  }),
]);
