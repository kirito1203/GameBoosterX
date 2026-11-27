// Regras de desbloqueio. Puro (sem Minecraft): recebe o estado do jogador.
//
// state = {
//   race, level, points, unlocked: Set<string>, flags: Set<string>,
//   (opcionais) maxMp, absorbed: Set, discovered: Set, missions: Set, mastery: { id: usos }
// }
import { SKILLS, TREES, RACES, REPLACES, MISSIONS } from '../data/registry.js';
import { masteryLevel } from './mastery.js';
import { CONFIG, rarityInfo } from '../config.js';

const EPIC_PLUS = new Set(['epic', 'legendary', 'supreme']);

export function lineageOf(state) {
  return RACES[state.race]?.lineage ?? null;
}

/** Custo efetivo em pontos (considera multiplicador global e passivas como Aprendizado Mágico). */
export function pointCost(skill, state) {
  let mult = CONFIG.costMultiplier;
  const tree = TREES[skill.tree];
  if (tree?.kind === 'universal') {
    for (const id of state.unlocked) {
      const fx = SKILLS[id]?.fx;
      for (const f of fxList(fx)) if (f.k === 'p_points') mult *= f.mult;
    }
  }
  return Math.max(0, Math.round(skill.cost * mult));
}

/** Efeito atual da habilidade: a versão evoluída quando o jogador tem o nó que a evolui. */
export function fxOf(skill, unlocked) {
  return skill.evolvedBy && skill.evolvedFx && unlocked?.has(skill.evolvedBy) ? skill.evolvedFx : skill.fx;
}

export function fxList(fx) {
  if (!fx) return [];
  return Array.isArray(fx) ? fx : [fx];
}

/** A habilidade pode ser aprendida pela linhagem atual? */
export function raceAllowed(skill, state) {
  const lin = lineageOf(state);
  const tree = TREES[skill.tree];
  if (tree?.kind === 'racial' && tree.lineage !== lin) return false;
  if (skill.races && !skill.races.includes(lin)) return false;
  if (skill.blockedRaces.includes(lin)) return false;
  return true;
}

function countUnlockedInTree(treeId, state) {
  let n = 0;
  for (const s of TREES[treeId].skills) if (state.unlocked.has(s.id) && s.type !== 'race_root') n++;
  return n;
}

function countEpicPlus(state) {
  let n = 0;
  for (const id of state.unlocked) if (EPIC_PLUS.has(SKILLS[id]?.rarity)) n++;
  return n;
}

const FLAG_NAMES = {
  killed_wither: 'Derrotar o Wither',
  killed_ender_dragon: 'Derrotar o Ender Dragon',
  killed_warden: 'Derrotar o Warden',
};

/**
 * Avalia o desbloqueio de uma habilidade.
 * @returns {{ ok: boolean, state: 'unlocked'|'available'|'reachable'|'special'|'locked', missing: string[], cost: number, special?: string[] }}
 *   - special  : nós anteriores ok, mas falta requisito especial (absorção, missão, domínio, Magicules, chefe, evolução)
 *   - unlocked : já desbloqueada
 *   - available: tudo atendido, pode desbloquear agora
 *   - reachable: pré-requisitos (nós anteriores) ok, mas falta nível/pontos/outro requisito
 *   - locked   : falta algum nó anterior
 */
export function evaluate(skillId, state) {
  const s = SKILLS[skillId];
  const cost = s ? pointCost(s, state) : 0;
  if (!s) return { ok: false, state: 'locked', missing: ['Habilidade inexistente'], cost };
  if (state.unlocked.has(skillId)) return { ok: false, state: 'unlocked', missing: [], cost };

  const missing = [];
  let prereqOk = true;

  if (!raceAllowed(s, state)) {
    prereqOk = false;
    const tree = TREES[s.tree];
    if (tree.kind === 'racial') missing.push('Pertence a outra linhagem racial');
    else missing.push('Sua raça não pode aprender esta habilidade');
  }
  const own = REPLACES[RACES[state.race]?.lineage] ?? {};
  for (const r of s.requires) {
    if (!state.unlocked.has(r) && !(own[r] && state.unlocked.has(own[r]))) {
      prereqOk = false;
      missing.push(`Requer: ${SKILLS[r]?.name ?? r}`);
    }
  }
  for (const x of s.excludes) {
    if (state.unlocked.has(x)) {
      prereqOk = false;
      missing.push(`Incompatível com: ${SKILLS[x]?.name ?? x} (escolha exclusiva)`);
    }
  }
  if (s.type === 'evolution') {
    // Só evolui a partir do estágio imediatamente anterior da mesma linhagem.
    const target = RACES[s.evolveTo];
    const cur = RACES[state.race];
    if (target && cur && target.stage <= cur.stage) {
      prereqOk = false;
      missing.push('Você já está neste estágio ou acima');
    }
  }

  const other = [];
  if (state.level < s.minLevel) other.push(`Nível ${s.minLevel} (atual: ${state.level})`);
  if (s.needCount > 0) {
    const n = countUnlockedInTree(s.tree, state);
    if (n < s.needCount) other.push(`${s.needCount} habilidades desta árvore (atual: ${n})`);
  }
  if (s.needEpic > 0) {
    const n = countEpicPlus(state);
    if (n < s.needEpic) other.push(`${s.needEpic} habilidades Épicas ou superiores (atual: ${n})`);
  }
  // Requisitos especiais (nó vermelho quando são o que falta).
  const special = [];
  for (const f of s.needFlags) {
    if (!state.flags.has(f)) special.push(`Conquista: ${FLAG_NAMES[f] ?? f}`);
  }
  if (s.needMp && (state.maxMp ?? 0) < s.needMp) special.push(`Magicules máximas ${s.needMp} (atual: ${Math.floor(state.maxMp ?? 0)})`);
  if (s.needStage !== null && s.needStage !== undefined && (RACES[state.race]?.stage ?? 0) < s.needStage) {
    special.push(`Evolução racial: ${s.needStage + 1}º estágio`);
  }
  if (s.discover && !state.discovered?.has(skillId)) special.push('Descobrir por absorção (absorva a criatura certa)');
  if (s.needAbsorb) {
    const abs = state.absorbed ?? new Set();
    if (s.needAbsorb.any && !s.needAbsorb.any.some((t) => abs.has(t))) special.push(`Absorver: ${s.needAbsorb.any.map((t) => t.replace('minecraft:', '')).join(' ou ')}`);
    if (s.needAbsorb.count && abs.size < s.needAbsorb.count) special.push(`${s.needAbsorb.count} espécies absorvidas (atual: ${abs.size})`);
  }
  if (s.needMission && !state.missions?.has(s.needMission)) special.push(`Missão: ${MISSIONS[s.needMission]?.name ?? s.needMission}`);
  if (s.needMasteryTag) {
    for (const [tag, lvl] of Object.entries(s.needMasteryTag)) {
      let best = 0;
      for (const id of state.unlocked) if (SKILLS[id]?.tags.includes(tag)) best = Math.max(best, masteryLevel(id, state.mastery?.[id] ?? 0));
      if (best < lvl) special.push(`Domínio ${lvl} numa habilidade ${tag === 'defense' ? 'defensiva' : tag === 'space' ? 'espacial' : tag} (atual: ${best})`);
    }
  }
  if (s.needUltimate) {
    let n = 0;
    for (const id of state.unlocked) if (SKILLS[id]?.tier === 'ultimate') n++;
    if (n < s.needUltimate) special.push(`${s.needUltimate} Habilidades Supremas (atual: ${n})`);
  }
  if (s.needMastery) {
    for (const [id, lvl] of Object.entries(s.needMastery)) {
      const cur = masteryLevel(id, state.mastery?.[id] ?? 0);
      if (cur < lvl) special.push(`Domínio de ${SKILLS[id]?.name ?? id}: nível ${lvl} (atual: ${cur})`);
    }
  }
  if (state.points < cost) other.push(`${cost} pontos (você tem ${state.points})`);

  missing.push(...other, ...special);
  if (!prereqOk) return { ok: false, state: 'locked', missing, cost };
  if (special.length) return { ok: false, state: 'special', missing, cost, special };
  if (other.length) return { ok: false, state: 'reachable', missing, cost };
  return { ok: true, state: 'available', missing: [], cost };
}

/**
 * Aplica o desbloqueio ao estado (mutável). Retorna o resultado de evaluate + `evolvedTo`.
 */
export function unlock(skillId, state) {
  const ev = evaluate(skillId, state);
  if (!ev.ok) return { ...ev, evolvedTo: null };
  const s = SKILLS[skillId];
  state.points -= ev.cost;
  state.unlocked.add(skillId);
  let evolvedTo = null;
  if (s.type === 'evolution' && s.evolveTo) {
    state.race = s.evolveTo;
    evolvedTo = s.evolveTo;
  }
  return { ...ev, ok: true, state: 'unlocked', evolvedTo };
}

export function rarityName(r) {
  return rarityInfo(r).name;
}
