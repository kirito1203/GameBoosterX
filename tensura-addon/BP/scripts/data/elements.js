// Elementos usados pelas magias: partícula, som e grupo de resistência.
// As partículas listadas existem no pacote vanilla e não exigem variáveis Molang.

export const ELEMENTS = {
  fire:      { name: 'Fogo',       particle: 'minecraft:basic_flame_particle',    impact: 'minecraft:lava_particle',            sound: 'mob.blaze.shoot',        group: 'fire' },
  water:     { name: 'Água',       particle: 'minecraft:water_splash_particle',   impact: 'minecraft:water_splash_particle',    sound: 'bucket.empty_water',     group: 'magic' },
  ice:       { name: 'Gelo',       particle: 'minecraft:snowflake_particle',      impact: 'minecraft:snowflake_particle',       sound: 'random.glass',           group: 'freezing' },
  earth:     { name: 'Terra',      particle: 'minecraft:falling_dust_gravel_particle', impact: 'minecraft:smash_ground_particle', sound: 'dig.stone',            group: 'physical' },
  wind:      { name: 'Vento',      particle: 'minecraft:white_smoke_particle',    impact: 'minecraft:wind_explosion_emitter',   sound: 'wind_charge.burst',      group: 'physical' },
  lightning: { name: 'Relâmpago',  particle: 'minecraft:electric_spark_particle', impact: 'minecraft:electric_spark_particle',  sound: 'ambient.weather.thunder', group: 'lightning' },
  explosion: { name: 'Explosão',   particle: 'minecraft:basic_flame_particle',    impact: 'minecraft:huge_explosion_emitter',   sound: 'random.explode',         group: 'explosion' },
  holy:      { name: 'Sagrado',    particle: 'minecraft:endrod',                  impact: 'minecraft:totem_particle',           sound: 'beacon.activate',        group: 'holy' },
  dark:      { name: 'Trevas',     particle: 'minecraft:soul_particle',           impact: 'minecraft:sculk_soul_particle',      sound: 'mob.wither.shoot',       group: 'dark' },
  spirit:    { name: 'Espiritual', particle: 'minecraft:glow_particle',           impact: 'minecraft:villager_happy',           sound: 'mob.allay.idle',         group: 'magic' },
  illusion:  { name: 'Ilusão',     particle: 'minecraft:basic_portal_particle',   impact: 'minecraft:basic_portal_particle',    sound: 'mob.endermen.portal',    group: 'magic' },
  space:     { name: 'Espaço',     particle: 'minecraft:basic_portal_particle',   impact: 'minecraft:large_explosion',          sound: 'mob.shulker.teleport',   group: 'magic' },
  gravity:   { name: 'Gravidade',  particle: 'minecraft:obsidian_glow_dust_particle', impact: 'minecraft:smash_ground_particle_center', sound: 'mace.heavy_smash_ground', group: 'physical' },
  soul:      { name: 'Alma',       particle: 'minecraft:sculk_soul_particle',     impact: 'minecraft:soul_particle',            sound: 'mob.warden.sonic_boom',  group: 'dark' },
  nuclear:   { name: 'Nuclear',    particle: 'minecraft:green_flame_particle',    impact: 'minecraft:huge_explosion_emitter',   sound: 'random.explode',         group: 'fire' },
  dragon:    { name: 'Dracônico',  particle: 'minecraft:dragon_breath_trail',     impact: 'minecraft:dragon_breath_fire',       sound: 'mob.enderdragon.growl',  group: 'magic' },
  physical:  { name: 'Físico',     particle: 'minecraft:critical_hit_emitter',    impact: 'minecraft:critical_hit_emitter',     sound: 'random.anvil_land',      group: 'physical' },
  acid:      { name: 'Ácido',      particle: 'minecraft:oozing_emitter',          impact: 'minecraft:oozing_emitter',           sound: 'mob.slime.squish',       group: 'magic' },
  blood:     { name: 'Sangue',     particle: 'minecraft:redstone_ore_dust_particle', impact: 'minecraft:redstone_ore_dust_particle', sound: 'mob.bat.hurt',     group: 'dark' },
};

// Causas de dano do Bedrock agrupadas para resistências/fraquezas raciais.
export const CAUSE_GROUPS = {
  fire: ['fire', 'fireTick', 'lava', 'magma', 'campfire', 'soulCampfire'],
  fall: ['fall', 'flyIntoWall'],
  physical: ['entityAttack', 'projectile', 'ramAttack', 'maceSmash', 'thorns', 'contact', 'fallingBlock', 'anvil', 'stalactite', 'stalagmite'],
  explosion: ['blockExplosion', 'entityExplosion', 'fireworks'],
  magic: ['magic', 'sonicBoom'],
  dark: ['wither'],
  drowning: ['drowning'],
  freezing: ['freezing'],
  lightning: ['lightning'],
  starve: ['starve'],
  suffocation: ['suffocation'],
};

export const GROUP_NAMES = {
  fire: 'Fogo', fall: 'Queda', physical: 'Físico', explosion: 'Explosão', magic: 'Mágico',
  dark: 'Trevas/Wither', holy: 'Sagrado', drowning: 'Afogamento', freezing: 'Gelo',
  lightning: 'Relâmpago', starve: 'Fome', suffocation: 'Sufocamento',
};

export function causeGroup(cause) {
  for (const g in CAUSE_GROUPS) if (CAUSE_GROUPS[g].includes(cause)) return g;
  return null;
}
