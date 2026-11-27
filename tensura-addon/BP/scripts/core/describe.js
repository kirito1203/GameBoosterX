// Texto do efeito gerado a partir dos parâmetros reais (o que está aqui é o que o jogo executa).
import { ELEMENTS, GROUP_NAMES } from '../data/elements.js';
import { fxList } from './progression.js';
import { TREES, RACES } from '../data/registry.js';

const EFFECT_NAMES = {
  speed: 'Velocidade', slowness: 'Lentidão', haste: 'Pressa', mining_fatigue: 'Fadiga',
  strength: 'Força', jump_boost: 'Salto', nausea: 'Náusea', regeneration: 'Regeneração',
  resistance: 'Resistência', fire_resistance: 'Resist. ao fogo', water_breathing: 'Respiração aquática',
  invisibility: 'Invisibilidade', blindness: 'Cegueira', night_vision: 'Visão noturna', hunger: 'Fome',
  weakness: 'Fraqueza', poison: 'Veneno', wither: 'Wither', health_boost: 'Vida extra',
  absorption: 'Absorção', levitation: 'Levitação', slow_falling: 'Queda lenta', darkness: 'Escuridão',
  conduit_power: 'Poder do conduíte', saturation: 'Saturação',
};

export function effectName(id) { return EFFECT_NAMES[id] ?? id; }

function roman(n) { return ['I', 'II', 'III', 'IV', 'V', 'VI'][n] ?? `${n + 1}`; }

function effList(eff) {
  if (!eff?.length) return '';
  return eff.map(([id, secs, amp]) => {
    const lv = amp >= 250 ? 'máx.' : roman(amp);
    return secs === undefined ? `${effectName(id)} ${roman(secs ?? 0)}` : `${effectName(id)} ${lv} (${secs}s)`;
  }).join(', ');
}

function passiveEff(eff) {
  return eff.map(([id, amp]) => `${effectName(id)} ${roman(amp)}`).join(', ');
}

function el(e) { return e && ELEMENTS[e] ? ` de ${ELEMENTS[e].name.toLowerCase()}` : ''; }
function pct(v) { return `${Math.round(v * 100)}%`; }
function treeName(t) { return t === '*' ? 'todas as árvores' : (TREES[t]?.name ?? t); }

export function describeFx(fx) {
  const out = [];
  for (const f of fxList(fx)) {
    const parts = [];
    switch (f.k) {
      case 'bolt': parts.push(`Projétil${el(f.el)}: ${f.dmg} de dano, alcance ${f.range}`);
        if (f.r) parts.push(`área ${f.r}`); if (f.count > 1) parts.push(`${f.count} projéteis`);
        if (f.pierce) parts.push('perfura'); if (f.explode) parts.push(`explosão ${f.explode}`); break;
      case 'beam': parts.push(`Feixe${el(f.el)}: ${f.dmg} de dano, alcance ${f.range}`); if (f.pierce) parts.push('atravessa alvos'); break;
      case 'cone': parts.push(`Cone${el(f.el)}: ${f.dmg} de dano, alcance ${f.range}`); if (f.knock) parts.push(`empurrão ${f.knock}`);
        if (f.execute) parts.push(`executa criaturas com até ${f.execute} de vida máx.`); break;
      case 'nova': parts.push(`Explosão de área${el(f.el)}: ${f.dmg} de dano, raio ${f.r}${f.self ? ' ao seu redor' : `, alcance ${f.range}`}`); break;
      case 'rain': parts.push(`Chuva${el(f.el)}: ${f.waves} ondas de ${f.dmg} de dano, raio ${f.r}, alcance ${f.range}`); break;
      case 'chain': parts.push(`${f.count} explosões em linha, ${f.dmg} de dano cada`); break;
      case 'wall': parts.push(`Parede ${f.len}x${f.h} por ${f.dur}s`); break;
      case 'bind': parts.push(`Prende ${f.r ? `todos num raio ${f.r}` : 'o alvo'} por ${f.dur}s${f.dps ? `, ${f.dps} de dano/s` : ''}, alcance ${f.range}`); break;
      case 'debuff': parts.push(`${f.r ? `Área raio ${f.r}` : 'Alvo'}${f.self ? ' ao seu redor' : f.range ? `, alcance ${f.range}` : ''}`); if (f.dmg) parts.push(`${f.dmg} de dano`); break;
      case 'field': parts.push(`Campo raio ${f.r} por ${f.dur}s${f.self ? ' que acompanha você' : ''}`); if (f.dps) parts.push(`${f.dps} de dano/s`);
        if (f.antimagic) parts.push('bloqueia magia de outros jogadores'); if (f.confine) parts.push('inimigos não saem');
        if (f.repel) parts.push('repele inimigos'); if (f.freeze) parts.push('congela o tempo'); break;
      case 'buff': if (f.allies) parts.push(`Você e aliados (raio ${f.allies})`); break;
      case 'heal': parts.push(f.full ? 'Cura total' : `Cura ${f.amount} de vida`); if (f.allies) parts.push(`aliados raio ${f.allies}`); break;
      case 'cleanse': parts.push(f.fire ? 'Apaga o fogo' : 'Remove efeitos negativos'); if (f.allies) parts.push(`aliados raio ${f.allies}`); break;
      case 'drain': parts.push(`Drena ${f.dmg} de vida${f.r ? ` de todos num raio ${f.r}` : `, alcance ${f.range}`}, cura ${pct(f.heal)} do dano, +${f.mpGain} Magicules`); break;
      case 'summon': parts.push(`Invoca ${f.count}x por ${f.dur}s`); if (f.revive) parts.push('ressurgem ao cair'); break;
      case 'blink': parts.push(f.range < 0 ? `Recua ${-f.range} blocos` : `Teleporte de até ${f.range} blocos`); break;
      case 'strike': parts.push(`Teleporta atrás do alvo (alcance ${f.range}) e causa ${f.dmg} de dano`); break;
      case 'streak': parts.push(`Golpeia até ${f.count} inimigos (raio ${f.r}) com ${f.dmg} de dano cada`); break;
      case 'gravity': parts.push(`${f.pull ? 'Puxa' : 'Ergue e esmaga'} criaturas (raio ${f.r}), ${f.dmg} de dano`); break;
      case 'dominate': parts.push(`Pacifica ${f.r ? `criaturas num raio ${f.r}` : 'o alvo'} por ${f.dur}s`); break;
      case 'marionette': parts.push(`Converte até ${f.count} criatura(s) com até ${pct(f.hpPct)} de vida em aliado(s) por ${f.dur}s`); break;
      case 'possess': parts.push(`Drena ${f.dps}/s por ${f.dur}s enquanto você fica invisível`); break;
      case 'swap': parts.push(`Troca de lugar (alcance ${f.range}), atordoa ${f.stun}s`); break;
      case 'absorb': parts.push(`Absorve alvo com até ${pct(f.hpPct)} de vida (alcance ${f.range}): +${f.mpGain} Magicules máx. (limite +${f.mpCap})`); break;
      case 'dissolve': parts.push(`Dissolve bloco ou causa ${f.dmg} de dano ácido (alcance ${f.range})`); break;
      case 'mimic': parts.push(`Transformação: consome ${f.drain} Magicule/s`); break;
      case 'flight': parts.push(`Voo: consome ${f.drain} Magicules/s`); break;
      case 'analyze': parts.push(`Analisa alvo até ${f.range} blocos`); break;
      case 'sense': parts.push(`Detecta criaturas num raio de ${f.r}`); break;
      case 'ore_sense': parts.push(`Detecta minérios num raio de ${f.r}`); break;
      case 'comm': parts.push('Mostra a posição de todos os jogadores'); break;
      case 'storage': parts.push(`${f.slots} espaços dimensionais`); break;
      case 'warp': parts.push('Marca (agachado) e retorna ao ponto marcado'); break;
      case 'timeflip': parts.push('Inverte o dia e a noite'); break;
      case 'create': parts.push('Converte Magicules em minérios'); break;
      case 'enchant': parts.push('Encanta o item da mão'); break;
      case 'repair': parts.push('Repara o item da mão'); break;
      case 'imbue': parts.push(`Golpes${el(f.el)}: +${f.dmg} de dano por ${f.dur}s`); if (f.ignite) parts.push('incendeiam'); break;
      case 'evolve': parts.push(`Evolui para ${RACES[f.race]?.name ?? f.race}`); break;
      case 'p_effect': parts.push(`Passiva: ${passiveEff(f.eff)}`); break;
      case 'p_mp': { const a = []; if (f.max) a.push(`+${f.max} Magicules máx.`); if (f.regen) a.push(`+${f.regen}/s regeneração`);
        if (f.regenMult) a.push(`regeneração x${f.regenMult}`); parts.push(`Passiva: ${a.join(', ')}`); break; }
      case 'p_resist': parts.push(`Passiva: resistência ${Object.entries(f.groups).map(([g, v]) => `${GROUP_NAMES[g] ?? g} ${pct(v)}`).join(', ')}`); break;
      case 'p_lifesteal': parts.push(`Passiva: roubo de vida ${pct(f.pct)} nos golpes`); break;
      case 'p_onhit': parts.push(`Passiva: ${pct(f.chance)} de chance por golpe`); if (f.knock) parts.push('arremesso'); break;
      case 'p_affinity': parts.push(`Passiva: custo de Magicules x${f.mult} em ${treeName(f.tree)}`); break;
      case 'p_power': parts.push(`Passiva: dano x${f.mult} (${f.el === '*' ? 'todas as magias' : f.el === 'elemental' ? 'magias elementais' : ELEMENTS[f.el]?.name ?? f.el})`); break;
      case 'p_starved': parts.push(`Passiva: ao derrotar criaturas, recupera fome e +${f.mp} Magicules`); break;
      case 'p_points': parts.push(`Passiva: custo em pontos x${f.mult} nas árvores universais`); break;
      case 'p_regen_env': parts.push('Passiva: bônus na água' + (f.fire ? ' e em chamas' : '')); break;
      case 'p_race': parts.push(f.pack ? 'Passiva: força e resistência perto de aliados' : f.poisonImmune ? 'Passiva: imunidade a veneno' : 'Atributos da raça'); break;
      default: parts.push(f.k);
    }
    const effs = f.eff && !f.k.startsWith('p_') ? effList(f.eff) : '';
    if (effs) parts.push(effs);
    if (f.enemyEff) parts.push(`inimigos: ${effList(f.enemyEff)}`);
    if (f.allyEff) parts.push(`aliados: ${effList(f.allyEff)}`);
    if (f.ignite) parts.push(`incendeia ${f.ignite}s`);
    out.push(parts.filter(Boolean).join(', '));
  }
  return out.join(' + ');
}
