// Gera docs/CATALOGO.md: todas as raças e habilidades, com origem, raridade, custos e pré-requisitos.
import { writeFileSync, mkdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { TREES, SKILLS, RACES, LINEAGES, UNIVERSAL_TREES } from '../BP/scripts/data/registry.js';
import { CONFIG } from '../BP/scripts/config.js';
import { describeFx } from '../BP/scripts/core/describe.js';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const O = { obra: 'Tensura', adaptado: 'Adaptado', original: '**Original do addon**' };
const T = { active: 'Ativa', passive: 'Passiva', evolution: 'Evolução', race_root: 'Raça' };
const strip = (s) => s.replace(/§./g, '').replace(/\|/g, '/').replace(/\n/g, ' ');
const out = ['# Catálogo — Tensura RPG', '', `Gerado por \`tools/catalog.mjs\`. ${Object.keys(SKILLS).length} habilidades, ${Object.keys(RACES).length} raças, ${Object.keys(TREES).length} árvores.`, '',
  '**Origem:** *Tensura* = nome presente na obra (efeito adaptado ao Minecraft); *Adaptado* = conceito da obra adaptado pelo addon; *Original* = criado pelo addon.', '',
  '> A classificação foi feita com base no conhecimento disponível sobre a obra e deve ser revisada por quem acompanha a série.', ''];

out.push('## Raças', '', '| Linhagem | Raça | Original | Estágio | Origem | Magicules | Regen/s | Resistências | Fraquezas |', '|---|---|---|---|---|---|---|---|---|');
for (const lin in LINEAGES) for (const r of Object.values(RACES).filter((x) => x.lineage === lin).sort((a, b) => a.stage - b.stage)) {
  const f = (o) => Object.entries(o).map(([k, v]) => `${k} ${Math.round(v * 100)}%`).join(', ') || '—';
  out.push(`| ${LINEAGES[lin].name} | ${r.name} | ${r.original} | ${r.stage} | ${O[r.origin]} | ${r.mp} | ${r.regen} | ${f(r.resist)} | ${f(r.weak)} |`);
}
out.push('');

function treeSection(t) {
  out.push(`### ${t.name}`, '', '| Habilidade | Original | Tipo | Raridade | Pontos | MP | Recarga | Nível | Pré-requisitos | Origem | Efeito |', '|---|---|---|---|---|---|---|---|---|---|---|');
  for (const s of t.skills) {
    const req = s.requires.map((r) => SKILLS[r].name).join(', ') || '—';
    const extra = [s.needCount ? `${s.needCount} hab. da árvore` : '', s.needEpic ? `${s.needEpic} épicas+` : '', ...s.needFlags].filter(Boolean).join(', ');
    out.push(`| ${s.major ? '**' : ''}${s.name}${s.major ? '**' : ''}${s.initial ? ' *(inicial)*' : ''} | ${s.original} | ${T[s.type]} | ${CONFIG.rarities[s.rarity].name} | ${s.cost} | ${s.mp || '—'} | ${s.cooldown ? `${s.cooldown}s` : '—'} | ${s.minLevel} | ${req}${extra ? ` + ${extra}` : ''} | ${O[s.origin]} | ${strip(describeFx(s.fx))} |`);
  }
  if (t.links?.length) out.push('', `Nós compartilhados exibidos aqui: ${t.links.map((l) => `${SKILLS[l.id].name} (de ${TREES[SKILLS[l.id].tree].name})`).join(', ')}.`);
  out.push('');
}
out.push('## Árvores universais', '');
for (const t of UNIVERSAL_TREES) treeSection(t);
out.push('## Árvores raciais', '');
for (const t of Object.values(TREES).filter((x) => x.kind === 'racial')) treeSection(t);
mkdirSync(join(ROOT, 'docs'), { recursive: true });
writeFileSync(join(ROOT, 'docs', 'CATALOGO.md'), out.join('\n'));
console.log('docs/CATALOGO.md gerado');
