// Cards da tela "Escolha sua Raça": ordem, dificuldade, afinidade (texto curto)
// e aparência do retrato em pixel art (usada por tools/gen_textures.py).
//
// Só as raças com `start: true` (data/races.js) podem ser escolhidas. As demais aparecem
// como cards bloqueados ("evolução de ...") para o jogador conhecer o caminho.

export const RACE_CARDS = [
  // id da raça, dificuldade (1..5), afinidade, retrato { skin, hair, eyes, feature }
  ['slime', 2, 'Adaptabilidade', { kind: 'slime', skin: '#4fb8ff' }],
  ['human', 1, 'Magia universal', { kind: 'face', skin: '#c68a5a', hair: '#3b2416', eyes: '#2a1a10' }],
  ['goblin', 3, 'Elemental', { kind: 'face', skin: '#5aa83a', hair: null, eyes: '#ffd000', ears: true, teeth: true }],
  ['hobgoblin', 3, 'Elemental', { kind: 'face', skin: '#4e9a34', hair: '#2a2a2a', eyes: '#ffd000', ears: true }],
  ['ogre', 3, 'Força física', { kind: 'face', skin: '#7aa86a', hair: '#2b2b2b', eyes: '#ff3030', horns: '#e8e0c8', teeth: true }],
  ['kijin', 3, 'Elemental · Fogo', { kind: 'face', skin: '#f0c8a8', hair: '#d02020', eyes: '#ff4040', horns: '#2a2a2a' }],
  ['mystic_oni', 4, 'Elemental · Espírito', { kind: 'face', skin: '#f4d8c0', hair: '#f0a0c8', eyes: '#c040ff', horns: '#ffffff' }],
  ['wicked_oni', 4, 'Trevas · Força', { kind: 'face', skin: '#e8c8b0', hair: '#5a1a8a', eyes: '#ff2080', horns: '#3a0a3a' }],
  ['lizardman', 3, 'Água', { kind: 'lizard', skin: '#3a9a6a', eyes: '#ffe040' }],
  ['dragonewt', 4, 'Fogo · Relâmpago', { kind: 'dragon', skin: '#5a5ac8', eyes: '#ffe040' }],
  ['orc', 2, 'Resistência física', { kind: 'face', skin: '#6a8a3a', hair: '#1a1a1a', eyes: '#ff3030', tusks: true, ears: true }],
  ['high_orc', 3, 'Resistência física', { kind: 'face', skin: '#5a7a30', hair: '#1a1a1a', eyes: '#ffb020', tusks: true, ears: true }],
  ['lesser_demon', 4, 'Trevas · Ilusão', { kind: 'face', skin: '#c02020', hair: '#1a0a0a', eyes: '#ffd000', horns: '#2a1010', teeth: true }],
  ['angel', 3, 'Sagrada', { kind: 'face', skin: '#f8e0c8', hair: '#ffe070', eyes: '#40a0ff', halo: true }],
  ['fallen_angel', 4, 'Trevas', { kind: 'face', skin: '#e8d8e0', hair: '#e0e0f0', eyes: '#ff2040', horns: '#6a1a3a' }],
  ['dragonoid', 3, 'Dracônica · Fogo', { kind: 'dragon', skin: '#c82020', eyes: '#ffd000' }],
  ['vampire', 4, 'Sangue · Trevas', { kind: 'face', skin: '#e8e0f0', hair: '#14101c', eyes: '#ff2020', fangs: true }],
  ['elemental', 3, 'Elemental · Espírito', { kind: 'flame', skin: '#40c8ff' }],
  ['elf', 2, 'Espírito', { kind: 'face', skin: '#f8dcc0', hair: '#f0d070', eyes: '#30c060', ears: true }],
  ['dwarf', 2, 'Terra · Forja', { kind: 'face', skin: '#c08a60', hair: '#7a3a1a', eyes: '#2a1a10', beard: '#7a3a1a' }],
  ['dryad', 3, 'Espírito · Natureza', { kind: 'face', skin: '#d8e8c0', hair: '#30a040', eyes: '#20c060', leaves: true }],
  ['direwolf', 3, 'Vento · Relâmpago', { kind: 'wolf', skin: '#4a4a5a', eyes: '#ff3030' }],
  ['harpy', 3, 'Vento', { kind: 'face', skin: '#f0d0b0', hair: '#a06030', eyes: '#ffb020', feathers: '#c08040' }],
  ['merfolk', 3, 'Água · Gelo', { kind: 'face', skin: '#a0d8e8', hair: '#2060c0', eyes: '#40e0ff', fins: true }],
  ['ghoul', 4, 'Necromancia', { kind: 'face', skin: '#8a9a88', hair: '#3a3a3a', eyes: '#c0ff40', stitches: true }],
  ['giant', 2, 'Terra · Força', { kind: 'face', skin: '#b08060', hair: '#6a6a6a', eyes: '#2a1a10', beard: '#8a8a8a', scar: true }],
  ['fairy', 3, 'Espírito · Ilusão', { kind: 'face', skin: '#f8e0d0', hair: '#90e0ff', eyes: '#40c0ff', ears: true, halo: true }],
  ['beastman', 2, 'Força · Velocidade', { kind: 'wolf', skin: '#a07040', eyes: '#ffd040' }],
  ['lycanthrope', 3, 'Força · Velocidade', { kind: 'wolf', skin: '#6a4a30', eyes: '#ff8020' }],
  ['skeleton', 3, 'Trevas · Necromancia', { kind: 'face', skin: '#e8e4d8', hair: null, eyes: '#40ffd0', stitches: true, teeth: true }],
  ['wight_king', 5, 'Necromancia · Alma', { kind: 'face', skin: '#d8d4c8', hair: null, eyes: '#c040ff', horns: '#ffd060', teeth: true }],
  ['golem', 2, 'Terra · Defesa', { kind: 'face', skin: '#8a8a90', hair: null, eyes: '#40c0ff', scar: true }],
  ['dragon', 5, 'Fogo · Dracônica', { kind: 'dragon', skin: '#2a8a3a', eyes: '#ffb000' }],
  ['true_dragon', 5, 'Dracônica (forma final)', { kind: 'dragon', skin: '#d0a020', eyes: '#ffffff' }],
];

export const CARD_ORDER = RACE_CARDS.map(([id]) => id);
export const CARD_META = Object.fromEntries(RACE_CARDS.map(([id, difficulty, affinity, portrait]) => [id, { difficulty, affinity, portrait }]));

// Falas do Grande Sábio na apresentação (uma por etapa).
export const INTRO_LINES = [
  'Saudações, viajante.',
  'Sua existência foi transportada para este mundo.',
  'Sou o Grande Sábio, responsável por auxiliar sua jornada.',
  'Agora você poderá escolher sua raça, desenvolver suas habilidades e construir seu próprio destino.',
  'Prepare-se para sua reencarnação.',
  'Agora, escolha a raça que definirá sua existência neste mundo.',
];
