// Gera docs/CATALOGO_<LINHAGEM>.md a partir dos dados (fonte única). Uso: node tools/catalog-race.mjs slime
import { writeFileSync } from 'node:fs';
import { SKILLS, TREES, SECTORS, ABSORPTION, MISSIONS, RACES } from '../BP/scripts/data/registry.js';
import { SOURCES } from '../BP/scripts/data/dsl.js';
import { acquisition } from '../BP/scripts/core/view.js';

const lin = process.argv[2] ?? 'slime';
const tree = TREES[`race.${lin}`];
const sec = SECTORS[lin];
const strip = (s) => String(s).replace(/§./g, '').replace(/\|/g, '/').replace(/\n/g, ' ');
const out = [`# Catálogo da árvore racial — ${RACES[lin]?.name ?? lin}`, '',
  'Gerado por `tools/catalog-race.mjs` a partir dos dados do addon. Ranks são do balanceamento do Tensura RPG (não são oficiais).', '',
  '**Origens:** ' + Object.values(SOURCES).join(' · '), '',
  `Total: **${tree.skills.length} nós**.`, ''];
const count = {};
for (const s of tree.skills) if (s.src) count[s.src] = (count[s.src] ?? 0) + 1;
out.push('| Origem | Nós |', '|---|---|', ...Object.entries(count).map(([k, v]) => `| ${SOURCES[k]} | ${v} |`), '');
for (const id of sec.order) {
  out.push(`## ${sec.names[id]}`, '', '| Habilidade | Original | Rank | Origem | Custo | Nível | Pré-requisitos | Aquisição / condições | Efeito |', '|---|---|---|---|---|---|---|---|---|');
  for (const s of tree.skills.filter((x) => x.sector === id)) {
    const cond = [acquisition(s)];
    if (s.needMp) cond.push(`Magicules ${s.needMp}`);
    if (s.needStage) cond.push(`estágio ${s.needStage + 1}`);
    if (s.needMastery) cond.push(Object.entries(s.needMastery).map(([k, v]) => `domínio ${SKILLS[k].name} ${v}`).join(', '));
    if (s.needFlags.length) cond.push(s.needFlags.join(', '));
    out.push(`| ${strip(s.name)} | ${strip(s.original)} | ${s.rank} | ${SOURCES[s.src]} | ${s.cost} | ${s.minLevel} | ${s.requires.map((r) => strip(SKILLS[r].name)).join(', ') || '—'} | ${strip(cond.filter(Boolean).join('; '))} | ${strip(s.desc)} |`);
  }
  out.push('');
}
out.push('## Banco de absorção', '', '| Criatura da obra | Mob no addon (adaptação) | Habilidades | Método | Condições | Evoluções | Origem |', '|---|---|---|---|---|---|---|');
for (const e of ABSORPTION[lin] ?? []) {
  const needs = Object.entries(e.needs).map(([k, v]) => `${k === '*' ? 'todas' : SKILLS[k].name}: ${v.map((x) => SKILLS[x].name).join(' + ')}`).join('; ') || '—';
  out.push(`| ${e.work} | ${e.mc.join(', ')} | ${e.skills.map((x) => SKILLS[x].name).join(', ')} | ${e.method} | ${needs} | ${e.evolutions.map((x) => SKILLS[x].name).join(', ') || '—'} | ${SOURCES[e.src]}${e.note ? ` — ${e.note}` : ''} |`);
}
out.push('', '## Missões especiais', '', '| Missão | Condições | Recompensa | Libera |', '|---|---|---|---|');
for (const m of Object.values(MISSIONS).filter((x) => x.lineage === lin)) {
  const libera = tree.skills.filter((s) => s.needMission === m.id).map((s) => s.name).join(', ');
  out.push(`| ${m.name} | ${m.desc} | ${m.reward} pts | ${libera || '—'} |`);
}
const file = `docs/CATALOGO_${lin.toUpperCase()}.md`;
writeFileSync(new URL(`../${file}`, import.meta.url), out.join('\n') + '\n');
console.log(file);
