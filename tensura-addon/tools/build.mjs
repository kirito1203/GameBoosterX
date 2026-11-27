// Build do addon:
//   node tools/build.mjs              → gera UI + textos e empacota dist/*.mcaddon
//   node tools/build.mjs --textures   → também (re)gera as texturas (requer Python 3 + Pillow)
import { readFileSync, writeFileSync, mkdirSync, readdirSync, statSync, existsSync } from 'node:fs';
import { join, dirname, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';
import { deflateRawSync, crc32 } from 'node:zlib';
import { SKILLS, TREES } from '../BP/scripts/data/registry.js';
import { fxList } from '../BP/scripts/core/progression.js';
import { allFrameCodes, lineCodesForCell, iconName } from '../BP/scripts/core/uiSpec.js';
import { buildUi } from './build-ui.mjs';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const BP = join(ROOT, 'BP');
const RP = join(ROOT, 'RP');
const DIST = join(ROOT, 'dist');

const ELEMENT_FROM_TREE = {
  elemental: '#ff9a40', aspect: '#6ab0ff', holy: '#ffe680', summon: '#c080ff', illusion: '#ff80d0',
  spirit: '#60ffc0', necromancy: '#90e070', dark: '#9060ff', advanced: '#ff4060',
  unique: '#40d8ff', ultimate: '#ffd000', extra: '#78c8ff', resist: '#c8c8d0',
};
const EL_COLORS = {
  fire: '#ff6a2a', water: '#2a9dff', ice: '#9fe8ff', earth: '#c09050', wind: '#8cf0a8', lightning: '#ffe14a',
  explosion: '#ff3b3b', holy: '#fff2a8', dark: '#9a5cff', spirit: '#5effc8', illusion: '#ff7ae6', space: '#6a7bff',
  gravity: '#8a6cff', soul: '#4fd9d0', nuclear: '#8aff3a', dragon: '#d050ff', physical: '#d8d8d8', acid: '#a0e040', blood: '#e0304a',
};

function iconSpec(s) {
  const fx = fxList(s.fx);
  const el = fx.find((f) => f.el && EL_COLORS[f.el])?.el;
  const tree = TREES[s.tree];
  const color = el ? EL_COLORS[el] : tree.kind === 'racial' ? '#ffb040' : ELEMENT_FROM_TREE[s.tree] ?? '#b0b0c0';
  let glyph = fx[0]?.k ?? 'passive';
  if (s.type === 'passive') glyph = 'passive';
  if (s.type === 'evolution') glyph = 'evolve';
  if (s.type === 'race_root') glyph = 'race';
  const words = s.original.replace(/[^A-Za-z ]/g, ' ').split(/\s+/).filter((w) => w && !['of', 'the', 'Evolution'].includes(w));
  const abbr = (words.length >= 2 ? words[0][0] + words[1][0] : (words[0] ?? '?').slice(0, 2)).toUpperCase();
  return { icon: iconName(s.id), color, glyph, abbr };
}

function writeTexts() {
  const items = {
    'item.tensura:great_sage_tome': ['Grimório do Grande Sábio', 'Great Sage Grimoire'],
    'item.tensura:magic_crystal': ['Cristal de Magicules', 'Magicule Crystal'],
    'entity.tensura:hound_dog.name': ['Cão Invocado', 'Summoned Hound Dog'],
    'entity.tensura:basilisk.name': ['Basilisco Invocado', 'Summoned Basilisk'],
    'entity.tensura:daemon.name': ['Demônio Invocado', 'Summoned Daemon'],
    'entity.tensura:medium_elemental.name': ['Elemental Médio', 'Medium Elemental'],
    'entity.tensura:greater_elemental.name': ['Elemental Superior', 'Greater Elemental'],
    'entity.tensura:spirit.name': ['Espírito Invocado', 'Summoned Spirit'],
    'entity.tensura:familiar.name': ['Familiar', 'Familiar'],
    'entity.tensura:undead_warrior.name': ['Guerreiro Morto-vivo', 'Undead Warrior'],
    'entity.tensura:undead_archer.name': ['Arqueiro Morto-vivo', 'Undead Archer'],
    'entity.tensura:doppelganger.name': ['Cópia', 'Doppelganger'],
    'pack.name': ['Tensura RPG', 'Tensura RPG'],
    'pack.description': ['Raças, magias e árvore de habilidades inspiradas em Tensura', 'Races, magic and skill trees inspired by Tensura'],
  };
  const pt = Object.entries(items).map(([k, v]) => `${k}=${v[0]}`).join('\n') + '\n';
  const en = Object.entries(items).map(([k, v]) => `${k}=${v[1]}`).join('\n') + '\n';
  for (const base of [RP, BP]) {
    mkdirSync(join(base, 'texts'), { recursive: true });
    writeFileSync(join(base, 'texts', 'pt_BR.lang'), pt);
    writeFileSync(join(base, 'texts', 'en_US.lang'), en);
    writeFileSync(join(base, 'texts', 'languages.json'), JSON.stringify(['en_US', 'pt_BR'], null, 2));
  }
}

// ── ZIP mínimo (deflate) para gerar .mcpack/.mcaddon sem dependências ──
function zip(entries) {
  const chunks = [];
  const central = [];
  let offset = 0;
  for (const { name, data } of entries) {
    const nameBuf = Buffer.from(name, 'utf8');
    const comp = deflateRawSync(data, { level: 9 });
    const crc = crc32(data) >>> 0;
    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50, 0); local.writeUInt16LE(20, 4); local.writeUInt16LE(0x0800, 6);
    local.writeUInt16LE(8, 8); local.writeUInt32LE(0, 10); local.writeUInt32LE(crc, 14);
    local.writeUInt32LE(comp.length, 18); local.writeUInt32LE(data.length, 22);
    local.writeUInt16LE(nameBuf.length, 26); local.writeUInt16LE(0, 28);
    chunks.push(local, nameBuf, comp);
    const cen = Buffer.alloc(46);
    cen.writeUInt32LE(0x02014b50, 0); cen.writeUInt16LE(20, 4); cen.writeUInt16LE(20, 6); cen.writeUInt16LE(0x0800, 8);
    cen.writeUInt16LE(8, 10); cen.writeUInt32LE(0, 12); cen.writeUInt32LE(crc, 16); cen.writeUInt32LE(comp.length, 20);
    cen.writeUInt32LE(data.length, 24); cen.writeUInt16LE(nameBuf.length, 28); cen.writeUInt32LE(offset, 42);
    central.push(cen, nameBuf);
    offset += 30 + nameBuf.length + comp.length;
  }
  const cenBuf = Buffer.concat(central);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0); end.writeUInt16LE(entries.length, 8); end.writeUInt16LE(entries.length, 10);
  end.writeUInt32LE(cenBuf.length, 12); end.writeUInt32LE(offset, 16);
  return Buffer.concat([...chunks, cenBuf, end]);
}

function walk(dir, base = dir, out = []) {
  for (const f of readdirSync(dir)) {
    const p = join(dir, f);
    if (statSync(p).isDirectory()) walk(p, base, out);
    else out.push({ name: relative(base, p).split('\\').join('/'), data: readFileSync(p) });
  }
  return out;
}

async function lineCodes() {
  const { getMap } = await import('../BP/scripts/core/map.js?' + Date.now());
  const { LINEAGES } = await import('../BP/scripts/data/registry.js');
  const set = new Set();
  for (const lin in LINEAGES) {
    for (const st of getMap(lin).stubs.values()) {
      const dirs = [];
      for (let d = 0; d < 8; d++) if (st[d].length) dirs.push(d);
      for (const c of lineCodesForCell(dirs)) set.add(c);
    }
  }
  return [...set].sort();
}

async function main() {
  const withTextures = process.argv.includes('--textures');
  // O mapa precisa existir antes de listar as peças de linha usadas.
  execFileSync(process.execPath, [join(ROOT, 'tools', 'gen-map.mjs')], { stdio: 'inherit' });
  const spec = {
    skills: Object.values(SKILLS).map(iconSpec),
    frames: allFrameCodes(),
    lines: await lineCodes(),
  };
  if (withTextures) {
    mkdirSync(DIST, { recursive: true });
    const specPath = join(DIST, 'texture_spec.json');
    writeFileSync(specPath, JSON.stringify(spec));
    execFileSync('python3', [join(ROOT, 'tools', 'gen_textures.py'), specPath, RP, join(BP, 'pack_icon.png')], { stdio: 'inherit' });
  }
  // Verifica se todas as texturas exigidas existem.
  const missing = [];
  for (const s of spec.skills) if (!existsSync(join(RP, 'textures/tensura/icons', `${s.icon}.png`))) missing.push(`icons/${s.icon}`);
  for (const c of spec.frames) if (!existsSync(join(RP, 'textures/tensura/ui/node', `f_${c}.png`))) missing.push(`node/f_${c}`);
  for (const c of spec.lines) if (!existsSync(join(RP, 'textures/tensura/ui/line', `${c}.png`))) missing.push(`line/${c}`);
  if (missing.length) {
    console.error(`Texturas ausentes (${missing.length}), rode com --textures. Ex.: ${missing.slice(0, 5).join(', ')}`);
    process.exit(1);
  }
  const ui = buildUi();
  writeTexts();
  mkdirSync(DIST, { recursive: true });
  const version = JSON.parse(readFileSync(join(BP, 'manifest.json'), 'utf8')).header.version.join('.');
  const bpFiles = walk(BP);
  const rpFiles = walk(RP);
  writeFileSync(join(DIST, `TensuraRPG_BP_v${version}.mcpack`), zip(bpFiles));
  writeFileSync(join(DIST, `TensuraRPG_RP_v${version}.mcpack`), zip(rpFiles));
  const addon = [
    ...bpFiles.map((f) => ({ name: `TensuraRPG_BP/${f.name}`, data: f.data })),
    ...rpFiles.map((f) => ({ name: `TensuraRPG_RP/${f.name}`, data: f.data })),
  ];
  const out = join(DIST, `TensuraRPG_v${version}.mcaddon`);
  writeFileSync(out, zip(addon));
  console.log(`OK: ${Object.keys(SKILLS).length} habilidades, ${ui.controls} células de UI, ${bpFiles.length}+${rpFiles.length} arquivos → ${relative(ROOT, out)}`);
}

await main();
