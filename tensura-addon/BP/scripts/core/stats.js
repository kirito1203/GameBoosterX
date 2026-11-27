// Atributos derivados do jogador: raça + passivas desbloqueadas (+ forma de Transformação).
// Puro: testável em Node.
import { SKILLS, RACES, TREES } from '../data/registry.js';
import { CONFIG } from '../config.js';
import { fxList } from './progression.js';
import { sageBonuses } from './sage.js';

export const ELEMENTAL_ELEMENTS = new Set(['fire', 'water', 'ice', 'earth', 'wind', 'lightning', 'explosion']);

// Características ganhas com a Transformação (Slime), por espécie absorvida.
export const MIMIC_TRAITS = {
  'minecraft:wolf': { name: 'Lobo', eff: [['speed', 1], ['strength', 0]] },
  'minecraft:fox': { name: 'Raposa', eff: [['speed', 1], ['night_vision', 0]] },
  'minecraft:spider': { name: 'Aranha', eff: [['night_vision', 0], ['jump_boost', 1]] },
  'minecraft:cave_spider': { name: 'Aranha das Cavernas', eff: [['night_vision', 0], ['jump_boost', 1]] },
  'minecraft:blaze': { name: 'Blaze', eff: [['fire_resistance', 0]] },
  'minecraft:magma_cube': { name: 'Cubo de Magma', eff: [['fire_resistance', 0], ['jump_boost', 2]] },
  'minecraft:slime': { name: 'Slime', eff: [['jump_boost', 2]] },
  'minecraft:cod': { name: 'Bacalhau', eff: [['water_breathing', 0], ['conduit_power', 0]] },
  'minecraft:salmon': { name: 'Salmão', eff: [['water_breathing', 0], ['conduit_power', 0]] },
  'minecraft:tropicalfish': { name: 'Peixe Tropical', eff: [['water_breathing', 0], ['conduit_power', 0]] },
  'minecraft:pufferfish': { name: 'Baiacu', eff: [['water_breathing', 0], ['resistance', 0]] },
  'minecraft:squid': { name: 'Lula', eff: [['water_breathing', 0], ['conduit_power', 0]] },
  'minecraft:glow_squid': { name: 'Lula Brilhante', eff: [['water_breathing', 0], ['night_vision', 0]] },
  'minecraft:dolphin': { name: 'Golfinho', eff: [['water_breathing', 0], ['conduit_power', 0], ['speed', 0]] },
  'minecraft:guardian': { name: 'Guardião', eff: [['water_breathing', 0], ['resistance', 0]] },
  'minecraft:turtle': { name: 'Tartaruga', eff: [['water_breathing', 0], ['resistance', 1]] },
  'minecraft:bat': { name: 'Morcego', eff: [['night_vision', 0], ['slow_falling', 0]] },
  'minecraft:phantom': { name: 'Phantom', eff: [['night_vision', 0], ['slow_falling', 0]] },
  'minecraft:chicken': { name: 'Galinha', eff: [['slow_falling', 0]] },
  'minecraft:parrot': { name: 'Papagaio', eff: [['slow_falling', 0], ['speed', 0]] },
  'minecraft:horse': { name: 'Cavalo', eff: [['speed', 2], ['jump_boost', 1]] },
  'minecraft:rabbit': { name: 'Coelho', eff: [['jump_boost', 2], ['speed', 0]] },
  'minecraft:goat': { name: 'Cabra', eff: [['jump_boost', 2]] },
  'minecraft:cat': { name: 'Gato', eff: [['speed', 0], ['night_vision', 0]] },
  'minecraft:ocelot': { name: 'Jaguatirica', eff: [['speed', 1]] },
  'minecraft:iron_golem': { name: 'Golem de Ferro', eff: [['resistance', 1], ['strength', 1], ['slowness', 0]] },
  'minecraft:strider': { name: 'Lavagante', eff: [['fire_resistance', 0]] },
  'minecraft:zombie': { name: 'Zumbi', eff: [['strength', 0]] },
  'minecraft:husk': { name: 'Zumbi do Deserto', eff: [['strength', 0], ['fire_resistance', 0]] },
  'minecraft:skeleton': { name: 'Esqueleto', eff: [['speed', 0]] },
  'minecraft:enderman': { name: 'Enderman', eff: [['speed', 1], ['jump_boost', 1]] },
  'minecraft:creeper': { name: 'Creeper', eff: [['resistance', 0]] },
  'minecraft:witch': { name: 'Bruxa', eff: [['regeneration', 0]] },
  'minecraft:warden': { name: 'Warden', eff: [['strength', 2], ['resistance', 1], ['night_vision', 0]] },
};
const MIMIC_DEFAULT = { eff: [['health_boost', 0]] };

export function mimicTraits(typeId) {
  return MIMIC_TRAITS[typeId] ?? { name: typeId.replace('minecraft:', ''), ...MIMIC_DEFAULT };
}

/**
 * @param {{ race: string, level: number, unlocked: Set<string>, mpBonus?: number, mimic?: string|null, sage?: any }} d
 */
export function computeStats(d) {
  const race = RACES[d.race];
  const s = {
    maxMp: race.mp + (d.level - 1) * CONFIG.magicules.perLevel + (d.mpBonus ?? 0),
    // Aura: energia de combate do corpo (habilidades raciais). Raças robustas têm mais.
    maxAura: 60 + Math.max(0, race.hp + 1) * 25 + (d.level - 1) * CONFIG.magicules.auraPerLevel,
    auraRegen: 2 + d.level * 0.05,
    xpMult: 1,
    cleanse: new Set(),
    regen: race.regen,
    regenMult: 1,
    resist: { ...race.resist },
    weak: { ...race.weak },
    affinity: { ...race.aff },      // treeId|'*' → multiplicador de custo
    power: { ...race.power },       // elemento|'elemental'|'*' → multiplicador de dano
    effects: new Map(),             // efeito → amplificador (permanentes)
    lifesteal: 0,
    onHit: [],
    pointMult: 1,
    starved: null,
    envWater: [],
    envFire: [],
    pack: false,
    poisonImmune: false,
    special: new Set(race.special ?? []),
  };
  const addEff = (id, amp) => s.effects.set(id, Math.max(s.effects.get(id) ?? -1, amp));
  if (race.hp >= 0) addEff('health_boost', race.hp);
  for (const [id, amp] of race.eff) addEff(id, amp);

  for (const id of d.unlocked) {
    const sk = SKILLS[id];
    if (!sk || (sk.type !== 'passive' && sk.type !== 'race_root')) continue;
    for (const f of fxList(sk.fx)) applyPassive(s, f, addEff);
  }
  if (d.mimic) for (const [id, amp] of mimicTraits(d.mimic).eff) addEff(id, amp);
  // Capacidades do Grande Sábio com efeito em atributos (ver core/sage.js).
  const sb = d.sage ? sageBonuses(d.sage) : null;
  s.cdMult = sb?.cooldownMult ?? 1;
  s.sageMp = sb?.mpMult ?? 1;
  if (sb) { s.regenMult *= sb.regenMult; s.power['*'] = (s.power['*'] ?? 1) * sb.power; }
  // Raças lentas perdem a lentidão se ganharem velocidade.
  if (s.effects.has('slowness') && s.effects.has('speed')) s.effects.delete('slowness');
  // Resistências limitadas a 90%, exceto imunidades raciais declaradas como 1 (ex.: queda do Slime).
  for (const k in s.resist) {
    const innate = race.resist[k] >= 1;
    s.resist[k] = innate ? 1 : Math.min(0.9, s.resist[k]);
  }
  return s;
}

function mulInto(obj, key, mult) {
  obj[key] = (obj[key] ?? 1) * mult;
}

function applyPassive(s, f, addEff) {
  switch (f.k) {
    case 'p_effect': for (const [id, amp] of f.eff) addEff(id, amp); break;
    case 'p_mp': s.maxMp += f.max ?? 0; s.regen += f.regen ?? 0; s.regenMult *= f.regenMult ?? 1; break;
    case 'p_resist': for (const g in f.groups) s.resist[g] = 1 - (1 - (s.resist[g] ?? 0)) * (1 - f.groups[g]); break;
    case 'p_lifesteal': s.lifesteal += f.pct; break;
    case 'p_onhit': s.onHit.push(f); break;
    case 'p_affinity': mulInto(s.affinity, f.tree, f.mult); break;
    case 'p_power': mulInto(s.power, f.el, f.mult); break;
    case 'p_points': s.pointMult *= f.mult; break;
    case 'p_starved': s.starved = f; break;
    case 'p_regen_env': if (f.water) s.envWater.push(...f.water); if (f.fire) s.envFire.push(...f.fire); break;
    case 'p_race': if (f.pack) s.pack = true; if (f.poisonImmune) s.poisonImmune = true; for (const e of f.cleanse ?? []) s.cleanse.add(e); break;
    case 'p_xp': s.xpMult *= f.mult; break;
    case 'p_aura': s.maxAura += f.max ?? 0; s.auraRegen += f.regen ?? 0; break;
    default: break;
  }
}

/** Custo de Magicules de uma habilidade, com afinidades. */
export function mpCost(skill, stats) {
  let m = stats.affinity['*'] ?? 1;
  m *= stats.affinity[skill.tree] ?? 1;
  const tree = TREES[skill.tree];
  if (tree?.kind === 'racial') m *= stats.affinity.racial ?? 1;
  else m *= stats.sageMp ?? 1;
  return Math.max(0, Math.round(skill.mp * m));
}

/** Multiplicador de dano para um elemento. */
export function powerFor(element, stats) {
  let m = stats.power['*'] ?? 1;
  m *= stats.power[element] ?? 1;
  if (ELEMENTAL_ELEMENTS.has(element)) m *= stats.power.elemental ?? 1;
  return m;
}
