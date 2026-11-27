// MISSÕES ESPECIAIS (Slime). Condições verificadas com dados reais do jogador.
// Tipos de condição: absorbTotal, absorbType, absorbSpecies, kill, killTotal, level, flags, sage, treeCount.
// Recompensa: pontos concedidos UMA vez (d.missions guarda as concluídas).
export const MISSIONS = [
  { id: 'm.slime.first_meals', name: 'Primeiras Refeições', lineage: 'slime', src: 'mod',
    desc: 'Absorva 10 criaturas (qualquer espécie).', cond: [{ absorbTotal: 10 }], reward: 20 },
  { id: 'm.slime.shizu', name: 'A Herança de Shizu', lineage: 'slime', src: 'mod',
    desc: 'Absorva uma Bruxa (no addon, representa a herança de Shizu: forma humana e Decompositor).', cond: [{ absorbType: 'minecraft:witch' }], reward: 30 },
  { id: 'm.slime.orc_disaster', name: 'O Desastre Orc', lineage: 'slime', src: 'mod',
    desc: 'Derrote um Piglin Bruto (o "Desastre Orc" do addon) e chegue ao nível 20.', cond: [{ kill: 'minecraft:piglin_brute', n: 1 }, { level: 20 }], reward: 40 },
  { id: 'm.slime.harvest', name: 'Festival da Colheita', lineage: 'slime', src: 'mod',
    desc: 'Derrote 300 criaturas no total e alcance o nível 30 (o despertar como Lorde Demônio).', cond: [{ killTotal: 300 }, { level: 30 }], reward: 60 },
  { id: 'm.slime.veldora', name: 'Despertar da Tempestade', lineage: 'slime', src: 'mod',
    desc: 'Derrote o Ender Dragon para libertar o poder de Veldora.', cond: [{ flags: ['killed_ender_dragon'] }], reward: 60 },
  { id: 'm.slime.ciel', name: 'Manas', lineage: 'slime', src: 'mod',
    desc: 'Leve o Grande Sábio ao estágio Raphael, nível 60 (painel do Grande Sábio).', cond: [{ sage: { stage: 'raphael', level: 60 } }], reward: 80 },
  { id: 'm.slime.void', name: 'O Deus do Vazio', lineage: 'slime', src: 'mod',
    desc: 'Derrote o Wither e o Warden e absorva 40 espécies diferentes.', cond: [{ flags: ['killed_wither', 'killed_warden'] }, { absorbSpecies: 40 }], reward: 100 },
  { id: 'm.slime.abundance', name: 'Senhor da Abundância', lineage: 'slime', src: 'mod',
    desc: 'Desbloqueie 80 habilidades na árvore racial do Slime.', cond: [{ treeCount: { tree: 'race.slime', n: 80 } }], reward: 100 },
];
