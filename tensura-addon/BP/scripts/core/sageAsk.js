// Perguntas ao Grande Sábio: respostas PROGRAMADAS a partir dos dados reais do addon.
// Não é inteligência artificial generativa: cada pergunta é classificada por palavras-chave
// numa "intenção" e a resposta é montada com os dados do jogador. Sem dados → "dados insuficientes".
//
// Expansão futura: `ask()` passa por `ANSWERERS` em ordem; um provedor externo (ex.: uma IA real,
// se um dia for possível no Bedrock) pode ser registrado com registerAnswerer() antes do padrão.
import { SKILLS, TREES, RACES } from '../data/registry.js';
import { GROUP_NAMES } from '../data/elements.js';
import { evaluate } from './progression.js';
import { epForLevel } from '../config.js';
import { nextEvolutions } from './menuView.js';
import {
  STAGES, CAPABILITIES, hasCapability, capabilityState, evolutionRequirements, levelCap, xpToNext, threatLevel, nextCapability,
} from './sage.js';

export const INSUFFICIENT = 'Dados insuficientes para responder.';

export function normalize(q) {
  return String(q ?? '').toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^a-z0-9 ]+/g, ' ').replace(/\s+/g, ' ').trim();
}

const has = (q, ...words) => words.some((w) => q.includes(w));
const pctList = (o) => Object.entries(o).filter(([, v]) => v > 0).map(([k, v]) => `${GROUP_NAMES[k] ?? k} ${Math.round(v * 100)}%`);
const locked = (capId) => {
  const c = CAPABILITIES.find((x) => x.id === capId);
  return `Ainda não tenho a capacidade "${c.name}" (${STAGES[c.stage].name}, nível ${c.level}). ${INSUFFICIENT}`;
};

/** Habilidades que podem ser desbloqueadas agora (requisitos e pontos atendidos). */
export function availableSkills(state, { magicOnly = false } = {}) {
  const out = [];
  for (const id in SKILLS) {
    const s = SKILLS[id];
    if (s.type === 'race_root' || state.unlocked.has(id)) continue;
    if (magicOnly && TREES[s.tree].kind !== 'universal') continue;
    const ev = evaluate(id, state);
    if (ev.ok) out.push({ s, cost: ev.cost });
  }
  return out.sort((a, b) => a.cost - b.cost);
}

/** Habilidades a um requisito de distância (o que falta para cada uma). */
export function nearSkills(state, limit = 5) {
  const out = [];
  for (const id in SKILLS) {
    const s = SKILLS[id];
    if (s.type === 'race_root' || state.unlocked.has(id)) continue;
    const ev = evaluate(id, state);
    if (ev.state === 'reachable' && ev.missing.length === 1) out.push({ s, missing: ev.missing[0] });
  }
  return out.slice(0, limit);
}

// ── Intenções ── (ordem importa: a primeira que reconhecer a pergunta responde)
const INTENTS = [
  {
    id: 'help', test: (q) => has(q, 'ajuda', 'o que posso perguntar', 'comandos', 'exemplo'),
    answer: () => [
      'Posso responder, com base nos seus dados:',
      '• Qual é minha raça atual? · Qual é meu nível?',
      '• Quais habilidades posso aprender? · Quais magias posso desbloquear?',
      '• Quais são minhas fraquezas / resistências?',
      '• Como posso evoluir? · O que falta para minha próxima evolução?',
      '• Qual é o nível de ameaça desta criatura? (mire nela ao perguntar)',
      '• Quais capacidades você possui? · Qual é o seu nível?',
      '• O que você recomenda?',
    ],
  },
  {
    id: 'synthesis', test: (q) => has(q, 'combin', 'sintes', 'fundir', 'juntar habilidade'),
    answer: () => [
      'A Síntese de habilidades ainda não existe neste addon.',
      `Não há regras de combinação cadastradas para eu consultar. ${INSUFFICIENT}`,
    ],
  },
  {
    id: 'sage_level', test: (q) => (has(q, 'seu nivel', 'voce esta', 'nivel do sabio', 'nivel do grande', 'seu estagio', 'sua experiencia')),
    answer: ({ sage }) => {
      const cap = levelCap(sage.stage);
      const next = nextCapability(sage);
      return [
        `Estágio: ${STAGES[sage.stage].name} · Nível ${sage.level}/${cap}`,
        sage.level >= cap ? 'Atingi o limite deste estágio. Só uma evolução permite continuar.' : `Experiência: ${Math.floor(sage.xp)}/${xpToNext(sage.level)}`,
        next ? `Próxima capacidade: ${next.name} (${STAGES[next.stage].name}, nível ${next.level}).` : 'Todas as capacidades foram desbloqueadas.',
      ];
    },
  },
  {
    id: 'capabilities', test: (q) => has(q, 'capacidade', 'o que voce faz', 'o que voce sabe', 'o que voce pode'),
    answer: ({ sage }) => {
      const on = CAPABILITIES.filter((c) => hasCapability(sage, c.id));
      const off = CAPABILITIES.length - on.length;
      return [`Capacidades ativas (${on.length}/${CAPABILITIES.length}):`, ...on.map((c) => `• ${c.name} — ${c.desc}`),
        off ? `${off} ainda bloqueadas (veja a aba Inteligência e capacidades).` : ''];
    },
  },
  {
    id: 'sage_evolution', test: (q) => has(q, 'evolu', 'raphael', 'ciel') && has(q, 'voce', 'sabio', 'raphael', 'ciel', 'sua'),
    answer: ({ sage, d }) => {
      const ev = evolutionRequirements(sage, d);
      if (!ev) return ['Já estou no estágio final (Ciel). Não há evolução cadastrada além deste.'];
      return [`Requisitos para eu evoluir para ${STAGES[ev.next].name}:`,
        ...ev.reqs.map((r) => `${r.ok ? '✔' : '✘'} ${r.label}`),
        ev.ready ? 'Todos os requisitos foram cumpridos. A evolução pode ser iniciada no painel Nível e evolução.' : `Progresso estimado: ${Math.round(ev.progress * 100)}%.`];
    },
  },
  {
    id: 'race', test: (q) => has(q, 'raca', 'especie', 'quem sou', 'o que sou'),
    answer: ({ d }) => {
      const r = RACES[d.race];
      return [`Sua raça atual: ${r.name} (${r.original}), estágio ${r.stage}.`, r.desc,
        `Magículas base ${r.mp} · regeneração ${r.regen}/s.`];
    },
  },
  {
    id: 'weakness', test: (q) => has(q, 'fraqueza', 'fraco', 'vulnera'),
    answer: ({ d }) => {
      const w = pctList(d.stats.weak);
      return [w.length ? `Suas fraquezas (dano extra recebido): ${w.join(', ')}.` : 'Sua raça não tem fraquezas registradas no addon.'];
    },
  },
  {
    id: 'resist', test: (q) => has(q, 'resisten', 'imun', 'forte contra'),
    answer: ({ d }) => {
      const r = pctList(d.stats.resist);
      return [r.length ? `Suas resistências (dano reduzido): ${r.join(', ')}.` : 'Você não possui resistências registradas.'];
    },
  },
  {
    id: 'level', test: (q) => has(q, 'meu nivel', 'nivel atual', 'qual nivel', 'qual e meu nivel', 'quanto ep', 'meu ep', 'ponto'),
    answer: ({ d }) => [`Você está no nível ${d.level}.`, `EP: ${Math.floor(d.ep)}/${epForLevel(d.level)} para o nível ${d.level + 1} · EP total ${Math.floor(d.epTotal)}.`,
      `Pontos de habilidade disponíveis: ${d.points}.`],
  },
  {
    id: 'mana', test: (q) => has(q, 'magicul', 'mana', 'aura', 'energia'),
    answer: ({ d }) => [`Magículas: ${Math.floor(d.mp)}/${d.stats.maxMp} (magias).`, `Aura: ${Math.floor(d.aura)}/${d.stats.maxAura} (habilidades raciais).`],
  },
  {
    id: 'player_evolution', test: (q) => has(q, 'evolu', 'proxima forma', 'requisito'),
    answer: ({ d, state }) => {
      const evos = nextEvolutions(d);
      if (!evos.length) return ['Não há evolução racial cadastrada a partir da sua raça atual.'];
      const lines = [];
      for (const s of evos) {
        const ev = evaluate(s.id, state);
        lines.push(`${ev.ok ? '★' : '•'} ${RACES[s.evolveTo].name} (${ev.cost} pts)`);
        if (ev.ok) lines.push('   Pronto: evolua pela aba Evolução do menu.');
        else for (const m of ev.missing) lines.push(`   Falta: ${m}`);
      }
      return lines;
    },
  },
  {
    id: 'magic', test: (q) => has(q, 'magia', 'feitic'),
    answer: ({ sage, state }) => {
      const list = availableSkills(state, { magicOnly: true });
      if (!hasCapability(sage, 'skill_query')) return [`Há ${list.length} magias que você pode desbloquear agora.`, locked('skill_query')];
      if (!list.length) return ['Nenhuma magia pode ser desbloqueada agora (pontos, nível ou requisitos).'];
      return [`Magias que você pode desbloquear agora (${list.length}):`, ...list.slice(0, 8).map(({ s, cost }) => `• ${s.name} — ${TREES[s.tree].short}, ${cost} pts`),
        list.length > 8 ? `…e mais ${list.length - 8}.` : ''];
    },
  },
  {
    id: 'skills', test: (q) => has(q, 'habilidade', 'aprender', 'desbloque', 'skill'),
    answer: ({ sage, state }) => {
      const list = availableSkills(state);
      if (!hasCapability(sage, 'skill_query')) return [`Há ${list.length} habilidades que você pode desbloquear agora.`, locked('skill_query')];
      if (!list.length) return ['Nenhuma habilidade pode ser desbloqueada agora.', ...nearSkills(state).map(({ s, missing }) => `• ${s.name}: falta ${missing}`)];
      return [`Você pode desbloquear agora (${list.length}):`, ...list.slice(0, 8).map(({ s, cost }) => `• ${s.name} — ${TREES[s.tree].short}, ${cost} pts`),
        list.length > 8 ? `…e mais ${list.length - 8} (veja a árvore).` : ''];
    },
  },
  {
    id: 'threat', test: (q) => has(q, 'ameaca', 'perigo', 'criatura', 'monstro', 'inimigo', 'chefe', 'boss', 'alvo'),
    answer: ({ sage, target }) => {
      if (!target) return ['Nenhuma criatura na mira e nenhuma análise recente.', INSUFFICIENT];
      const lines = [`Alvo: ${target.name} · Vida ${target.hp}/${target.maxHp}`];
      if (!hasCapability(sage, 'threat')) return [...lines, locked('threat')];
      lines.push(`Nível de ameaça: ${threatLevel(target).label}§r`);
      if (target.undead) lines.push('Morto-vivo: vulnerável a magia sagrada.');
      return lines;
    },
  },
  {
    id: 'recommend', test: (q) => has(q, 'recomend', 'o que devo', 'sugest', 'conselho', 'dica'),
    answer: ({ sage, state, d }) => {
      if (!hasCapability(sage, 'recommend')) return [locked('recommend')];
      const lines = [];
      const evos = nextEvolutions(d).filter((s) => evaluate(s.id, state).ok);
      if (evos.length) lines.push(`Prioridade: evoluir para ${RACES[evos[0].evolveTo].name}.`);
      const own = availableSkills(state).filter(({ s }) => TREES[s.tree].kind === 'racial' || (RACES[d.race].aff[s.tree] ?? 1) < 1);
      const pick = (own.length ? own : availableSkills(state)).slice(0, 3);
      if (pick.length) lines.push('Habilidades recomendadas (afinidade e custo):', ...pick.map(({ s, cost }) => `• ${s.name} (${cost} pts)`));
      if (!lines.length) lines.push('Nenhuma habilidade disponível agora: ganhe EP para obter pontos.', ...nearSkills(state, 3).map(({ s, missing }) => `• ${s.name}: falta ${missing}`));
      return lines;
    },
  },
];

const defaultAnswerer = (q, ctx) => {
  const intent = INTENTS.find((i) => i.test(q));
  if (!intent) return null;
  return { intent: intent.id, lines: intent.answer(ctx).filter((l) => l !== '') };
};

const ANSWERERS = [defaultAnswerer];

/** Registra um provedor de respostas (ex.: IA real) que é consultado antes do padrão. */
export function registerAnswerer(fn) {
  ANSWERERS.unshift(fn);
}

/**
 * @param {string} question
 * @param {{ d: object, sage: object, state: object, target?: object|null }} ctx
 * @returns {{ intent: string, text: string }}
 */
export function ask(question, ctx) {
  const q = normalize(question);
  if (!q) return { intent: 'empty', text: 'Nenhuma pergunta recebida.' };
  if (!hasCapability(ctx.sage, 'qa')) return { intent: 'locked', text: locked('qa') };
  for (const fn of ANSWERERS) {
    const r = fn(q, ctx);
    if (r) return { intent: r.intent, text: r.lines.join('\n') };
  }
  return { intent: 'unknown', text: `${INSUFFICIENT}\nNão reconheci essa pergunta. Toque em "Exemplos de perguntas" para ver o que sei responder.` };
}

export { capabilityState };
