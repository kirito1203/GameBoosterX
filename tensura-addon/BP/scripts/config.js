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

  // Interface da árvore.
  ui: {
    defaultZoom: 2,
    // Grade visível (colunas x linhas) por nível de zoom. Deve coincidir com a
    // interface gerada em RP/ui (ver tools/build-ui.mjs, que lê este mesmo arquivo).
    zoomGrids: { 1: [15, 6], 2: [11, 5], 3: [7, 4] },
    labelsFromZoom: 2,        // nomes dos nós aparecem a partir deste zoom
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
