// Simulador de pré-visualização da árvore.
//
// Interpreta o RP/ui/tensura_tree.json GERADO (não recalcula o layout): resolve
// templates (@ns.nome), variáveis ($var|default), âncoras, tamanhos (%, %x, %y, px),
// collection_index e as expressões de binding (not/or/and/=/-/+). Alimenta tudo com
// o formulário real produzido por core/view.js e desenha em HTML com as texturas.
//
// IMPORTANTE: é uma interpretação da semântica do JSON UI do Bedrock feita para
// testes; não substitui o teste no jogo.
//
// Uso: node tools/preview.mjs [saida.html] [cenario]
import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { buildTreeForm, centeredView, ensureVisible, pan } from '../BP/scripts/core/view.js';
import { MARK } from '../BP/scripts/core/uiSpec.js';
import { getLayout } from '../BP/scripts/core/layout.js';
import { TREES, initialSkillsFor } from '../BP/scripts/data/registry.js';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const UI = JSON.parse(readFileSync(join(ROOT, 'RP/ui/tensura_tree.json'), 'utf8').replace(/^\/\/.*$/m, ''));
const NS = UI.namespace;

// ── Avaliador de expressões de binding ──
function tokenize(s) {
  const out = [];
  let i = 0;
  while (i < s.length) {
    const c = s[i];
    if (/\s/.test(c)) { i++; continue; }
    if (c === '(' || c === ')' || c === '=' || c === '-' || c === '+') { out.push(c); i++; continue; }
    if (c === "'") { const j = s.indexOf("'", i + 1); out.push({ str: s.slice(i + 1, j) }); i = j + 1; continue; }
    const m = /^(#?[a-zA-Z_][a-zA-Z0-9_]*)/.exec(s.slice(i));
    if (m) { out.push(m[1]); i += m[1].length; continue; }
    throw new Error(`Token inválido em ${s} @${i}`);
  }
  return out;
}
function evaluate(expr, vars) {
  const t = tokenize(expr);
  let p = 0;
  const peek = () => t[p];
  function primary() {
    const k = t[p++];
    if (k === '(') { const v = orExpr(); p++; return v; }
    if (k === 'not') return !primary();
    if (typeof k === 'object') return k.str;
    if (typeof k === 'string' && k.startsWith('#')) return vars[k] ?? '';
    throw new Error(`Inesperado ${JSON.stringify(k)} em ${expr}`);
  }
  function addExpr() {
    let v = primary();
    while (peek() === '-' || peek() === '+') {
      const op = t[p++];
      const r = primary();
      v = op === '+' ? String(v) + String(r) : String(v).split(String(r)).join('');
    }
    return v;
  }
  function eqExpr() { let v = addExpr(); while (peek() === '=') { p++; v = v === addExpr(); } return v; }
  function andExpr() { let v = eqExpr(); while (peek() === 'and') { p++; const r = eqExpr(); v = v && r; } return v; }
  function orExpr() { let v = andExpr(); while (peek() === 'or') { p++; const r = andExpr(); v = v || r; } return v; }
  return orExpr();
}

// ── Resolução de templates e variáveis ──
function lookup(ref) {
  const [ns, name] = ref.split('.');
  if (ns !== NS) return null; // controles do jogo (common.*) são simulados abaixo
  const key = Object.keys(UI).find((k) => k === name || k.startsWith(`${name}@`));
  return key ? UI[key] : null;
}
function resolve(name, def, vars) {
  const [, base] = name.split('@');
  let merged = { ...def };
  let kind = null;
  if (base) {
    const tpl = lookup(base);
    if (tpl) merged = { ...tpl, ...def };
    else kind = base; // ex.: common.button
  }
  const v = { ...vars };
  for (const k in merged) {
    if (k.startsWith('$')) {
      const [vn, isDef] = k.split('|');
      if (isDef) { if (!(vn in def) && !(vn in vars)) v[vn] = merged[k]; } else v[vn] = merged[k];
    }
  }
  const sub = (x) => (typeof x === 'string' && x.startsWith('$') && x in v ? v[x] : Array.isArray(x) ? x.map(sub) : x);
  const out = {};
  for (const k in merged) if (!k.startsWith('$')) out[k] = sub(merged[k]);
  return { def: out, vars: v, kind };
}

function dimOf(spec, parent, own, axis, auto) {
  if (spec === undefined) return parent[axis];
  if (typeof spec === 'number') return spec;
  if (spec === 'default') return auto;
  if (/%(c|cm|sm)$/.test(String(spec))) return parent[axis]; // tamanho pelos filhos: aproximação
  let total = 0;
  for (const part of String(spec).replace(/\s+/g, '').split(/(?=[+-])/)) {
    const m = /^([+-]?[\d.]+)(%x|%y|%|px)?$/.exec(part);
    if (!m) throw new Error(`Tamanho não suportado: ${spec}`);
    const n = Number(m[1]);
    if (m[2] === '%') total += (n / 100) * parent[axis];
    else if (m[2] === '%y') total += (n / 100) * own.h;
    else if (m[2] === '%x') total += (n / 100) * own.w;
    else total += n;
  }
  return total;
}

const ANCH = { top_left: [0, 0], top_middle: [0.5, 0], top_right: [1, 0], left_middle: [0, 0.5], center: [0.5, 0.5],
  right_middle: [1, 0.5], bottom_left: [0, 1], bottom_middle: [0.5, 1], bottom_right: [1, 1] };

function mc(text) {
  const colors = { 0: '#000', 1: '#00a', 2: '#0a0', 3: '#0aa', 4: '#a00', 5: '#a0a', 6: '#fa0', 7: '#aaa', 8: '#555', 9: '#55f', a: '#5f5', b: '#5ff', c: '#f55', d: '#f5f', e: '#ff5', f: '#fff' };
  let html = '';
  let open = 0;
  const parts = String(text).split('§');
  html += esc(parts[0]);
  for (const p of parts.slice(1)) {
    const code = p[0];
    if (colors[code]) { html += `<span style="color:${colors[code]}">`; open++; }
    else if (code === 'l') { html += '<span style="font-weight:bold">'; open++; }
    else if (code === 'r') { html += '</span>'.repeat(open); open = 0; }
    html += esc(p.slice(1));
  }
  return html.replace(/\n/g, '<br>') + '</span>'.repeat(open);
}
function esc(s) { return s.replace(/&/g, '&amp;').replace(/</g, '&lt;'); }

// ── Renderização ──
function render(model, W, H, scale) {
  const els = [];
  const stats = { nodes: 0, pipes: 0, ctl: 0, rects: { node: new Map(), pipe: new Map() } };
  const globals = { '#title_text': model.title, '#form_text': model.body };
  function walk(name, rawDef, parent, vars, ctx, depthLayer) {
    const { def, vars: v, kind } = resolve(name, rawDef, vars);
    let cidx = def.collection_index ?? ctx.cidx;
    const props = { ...globals };
    if (cidx !== undefined && model.buttons[cidx]) {
      props['#form_button_text'] = model.buttons[cidx].text;
      props['#form_button_texture'] = model.buttons[cidx].icon;
    }
    let visible = def.visible ?? true;
    let texture = def.texture;
    let text = def.text;
    for (const b of def.bindings ?? []) {
      if (b.binding_type === 'view') {
        const val = evaluate(b.source_property_name, props);
        props[b.target_property_name] = val;
        if (b.target_property_name === '#visible') visible = !!val;
        if (b.target_property_name === '#texture') texture = val;
      } else if (b.binding_name && b.binding_name_override) {
        props[b.binding_name_override] = props[b.binding_name] ?? '';
        if (b.binding_name_override === '#texture') texture = props[b.binding_name] ?? '';
      }
    }
    if (!visible) return;
    if (typeof texture === 'string' && texture.startsWith('#')) texture = props[texture];
    if (typeof text === 'string' && text.startsWith('#')) text = props[text] ?? '';
    const own = { w: 0, h: 0 };
    const size = def.size ?? ['100%', '100%'];
    own.h = dimOf(size[1], parent, own, 'h', 10);
    own.w = dimOf(size[0], parent, own, 'w', parent.w);
    if (typeof size[1] === 'string' && size[1].includes('%x')) own.h = dimOf(size[1], parent, own, 'h', 10);
    const [ax, ay] = ANCH[def.anchor_from ?? 'center'];
    const [bx, by] = ANCH[def.anchor_to ?? 'center'];
    const off = def.offset ?? [0, 0];
    const x = parent.x + ax * parent.w - bx * own.w + dimOf(off[0], parent, own, 'w', 0);
    const y = parent.y + ay * parent.h - by * own.h + dimOf(off[1], parent, own, 'h', 0);
    const box = { x, y, w: own.w, h: own.h };
    const layer = depthLayer + (def.layer ?? 0);
    const st = `left:${(x * scale).toFixed(1)}px;top:${(y * scale).toFixed(1)}px;width:${(own.w * scale).toFixed(1)}px;height:${(own.h * scale).toFixed(1)}px;z-index:${layer}`;
    if (def.type === 'image' && texture) {
      const src = pathToFileURL(join(ROOT, 'RP', `${texture}.png`)).href;
      const nine = join(ROOT, 'RP', `${texture}.json`);
      if (existsSync(nine)) {
        const n = JSON.parse(readFileSync(nine, 'utf8')).nineslice_size;
        els.push(`<div style="${st};border:${n * scale}px solid transparent;box-sizing:border-box;border-image:url('${src}') ${n} fill stretch;image-rendering:pixelated"></div>`);
      } else {
        els.push(`<img style="${st}" src="${src}">`);
      }
      if (texture.includes('/node/f_')) { stats.nodes++; stats.rects.node.set(cidx, box); }
      if (texture.includes('/pipe/p')) { stats.pipes++; stats.rects.pipe.set(cidx, box); }
    }
    if (def.type === 'label' && text) {
      els.push(`<div class="lbl" style="${st};height:auto;font-size:${(7 * (def.font_scale_factor ?? 1) * scale).toFixed(1)}px;text-align:${def.text_alignment ?? 'left'}">${mc(text)}</div>`);
    }
    if (def.clips_children) els.push(`<!-- clip ${name} -->`);
    let children = def.controls ?? [];
    if (kind === 'common.button') {
      stats.ctl++;
      children = children.filter((c) => Object.keys(c)[0] === 'default');
    }
    if (kind === 'common.scrolling_panel') {
      children = [{ [`content@${def.$scrolling_content ?? v.$scrolling_content}`]: {} }];
      els.push(`<div class="scroll" style="${st}"></div>`);
    }
    for (const c of children) {
      const k = Object.keys(c)[0];
      walk(k, c[k], box, v, { cidx: def.collection_index !== undefined ? def.collection_index : ctx.cidx }, layer + 1);
    }
  }
  walk('root', UI.root, { x: 0, y: 0, w: W, h: H }, {}, {}, 0);
  return { html: els.join('\n'), stats };
}

export function previewHtml(model, { W = 427, H = 192, scale = 3, label = '' } = {}) {
  const { html, stats } = render(model, W, H, scale);
  const page = `<!doctype html><meta charset="utf-8"><style>
body{margin:0;background:#000;font-family:'DejaVu Sans',sans-serif}
#s{position:relative;width:${W * scale}px;height:${H * scale}px;overflow:hidden}
#s img,#s div{position:absolute}
.lbl{color:#fff;text-shadow:1px 1px 0 #000;white-space:pre-wrap;line-height:1.15}
.scroll{overflow:hidden}
#cap{color:#9ab;font:12px monospace;padding:4px}
</style><div id="s">${html}</div><div id="cap">${label}</div>`;
  return { page, stats };
}

// ── Cenários de teste ──
export function scenario(name) {
  const base = { race: 'slime', level: 22, points: 140, flags: new Set() };
  const unlocked = new Set([...initialSkillsFor('slime'), 'r.slime.self_regen', 'r.slime.shapeshift', 'elem.core', 'elem.fire', 'elem.fireball',
    'elem.water', 'elem.freeze', 'elem.wind_cutter', 'elem.stone_shot', 'elem.fire_lance']);
  const state = { ...base, unlocked };
  const mp = { cur: 210, max: 252 };
  switch (name) {
    case 'elemental-z1': return { state, tab: 'elemental', view: centeredView(1), selected: null, mp };
    case 'elemental-z2-sel': {
      const L = getLayout(TREES.elemental);
      return { state, tab: 'elemental', view: ensureVisible(centeredView(2), L.pos['elem.firestorm']), selected: 'elem.firestorm', mp };
    }
    case 'racial-z3': return { state, tab: 'racial', view: centeredView(3), selected: null, mp };
    case 'racial-z2-sel': return { state, tab: 'racial', view: centeredView(2), selected: 'r.slime.evo_demon_slime', mp };
    case 'aspect-z1-pan': {
      const L = getLayout(TREES.aspect);
      return { state, tab: 'aspect', view: pan(centeredView(1), 0, -1, L), selected: null, mp };
    }
    default: throw new Error(`cenário desconhecido ${name}`);
  }
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const out = process.argv[2] ?? join(ROOT, 'dist', 'preview.html');
  const sc = process.argv[3] ?? 'elemental-z2-sel';
  const model = buildTreeForm(scenario(sc));
  if (!model.title.includes(MARK)) throw new Error('título sem marcador');
  const { page, stats } = previewHtml(model, { label: `cenário: ${sc} — simulação do JSON UI gerado (não é o jogo)` });
  mkdirSync(dirname(out), { recursive: true });
  writeFileSync(out, page);
  console.log(JSON.stringify({ out, nodes: stats.nodes, pipes: stats.pipes, buttons: stats.ctl }));
}
