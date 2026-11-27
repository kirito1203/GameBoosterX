// Configuração de balanceamento — todos os valores podem ser ajustados aqui
// sem tocar no restante do código.

export const CONFIG = {
  // Faixas de custo (pontos de habilidade) por raridade. Cada habilidade declara
  // seu custo; os testes garantem que ele esteja dentro da faixa da raridade.
  // costMultiplier escala todos os custos de uma vez (ex.: 0.5 = metade).
  rarities: {
    common:    { name: 'Comum',    min: 10,  max: 30,  color: '§f' },
    rare:      { name: 'Rara',     min: 30,  max: 60,  color: '§b' },
    epic:      { name: 'Épica',    min: 60,  max: 120, color: '§d' },
    legendary: { name: 'Lendária', min: 120, max: 250, color: '§6' },
    supreme:   { name: 'Suprema',  min: 250, max: 9999, color: '§c' },
  },
  costMultiplier: 1,

  // Progressão: EP (Existence Points) obtidos ao derrotar criaturas.
  leveling: {
    maxLevel: 100,
    pointsPerLevel: 20,       // pontos de habilidade por nível
    startingPoints: 30,       // pontos ao escolher a raça
    epBase: 40,               // EP para o nível 2
    epExponent: 1.45,         // curva: epBase * nivel^epExponent
    epPerMobHealth: 1.0,      // EP = vida máxima do mob * fator
    hostileMultiplier: 1.5,
    bossBonusPoints: 60,      // pontos extras ao derrotar Wither / Ender Dragon / Warden
  },

  // Magicules (energia mágica).
  magicules: {
    perLevel: 6,              // capacidade máxima extra por nível
    auraPerLevel: 5,          // Aura máxima extra por nível
    regenTickSeconds: 1,      // intervalo de regeneração
    combatRegenFactor: 0.5,   // regeneração reduzida até 5 s após conjurar
    hudSeconds: 4,            // tempo que a barra fica visível após usar magia
  },

  // Desempenho.
  perf: {
    passiveIntervalTicks: 40, // passivas reaplicadas a cada 2 s
    maxActiveBolts: 24,       // projéteis mágicos simultâneos no mundo
    maxSummonsPerPlayer: 8,
    maxTempBlocks: 400,       // blocos temporários (paredes/prisões) simultâneos
  },

  // Grande Sábio (assistente): progressão própria, separada do nível do jogador.
  // Os limites de nível de cada estágio são do balanceamento do addon (não são da obra).
  sage: {
    levelCaps: { sage: 30, raphael: 60, ciel: 100 },
    xpBase: 40,                // XP para o nível 2
    xpPerLevel: 12,            // XP a mais por nível
    rewards: {
      newSpecies: 30,          // primeira análise de uma espécie
      repeatAnalysis: 2,       // análises repetidas (limitadas por dia)
      newKillSpecies: 10,      // primeira vez que derrota uma espécie
      skillUnlocked: 8,        // cada habilidade desbloqueada pelo jogador
      playerLevelUp: 15,       // cada nível do jogador
      bossFirstKill: 150,      // primeira vitória contra um chefe
      itemAnalysis: 6,         // primeira análise de um tipo de item
      question: 1,             // pergunta respondida (limitada por dia)
    },
    dailyCaps: { repeatAnalysis: 20, question: 15 },
    historyLimit: 30,          // perguntas e respostas guardadas
    analysisLimit: 200,        // espécies guardadas na memória
  },

  // Interface da árvore.
  ui: {
    // Grade da árvore (colunas x linhas). As linhas a mais rolam arrastando o dedo.
    // Deve coincidir com a interface gerada em RP/ui (tools/build-ui.mjs lê este arquivo).
    treeGrid: [13, 12],
    barSlots: 5,              // espaços da Barra de Habilidades (Cristal)
  },

  // Protege construções: magias que colocam blocos só substituem ar e plantas.
  safeBlockPlacement: true,
  // Explosões mágicas quebram blocos? (padrão: não, para não destruir construções)
  explosionsBreakBlocks: false,
  // Magias atingem outros jogadores? (padrão: não — modo cooperativo)
  pvp: false,
};

export function rarityInfo(r) {
  return CONFIG.rarities[r] ?? CONFIG.rarities.common;
}

export function epForLevel(level) {
  // EP total necessário para sair do nível `level` para o seguinte.
  const L = CONFIG.leveling;
  return Math.round(L.epBase * Math.pow(level, L.epExponent));
}
