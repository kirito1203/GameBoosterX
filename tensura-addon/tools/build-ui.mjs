// Gera RP/ui/*.json a partir de BP/scripts/core/uiSpec.js (fonte única da verdade).
//
// Técnica (padrões do próprio jogo, ver resource_pack/ui do bedrock-samples):
//  - server_form.json: o formulário ActionFormData ("long_form") passa por um
//    roteador. Se o título contém o marcador §t§n§s, mostra a árvore; senão, o
//    formulário padrão do jogo (nada muda para outros addons/menus).
//  - Cada botão do formulário tem posição fixa via `collection_index`
//    (mesmo padrão de pdp_screenshots_section.json).
//  - Texturas de nós e linhas são escolhidas por concatenação de string
//    ('textures/...' + #form_button_text), como em game_tip_screen.json.
import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { MARK, ZOOMS, CTRL, TAB_COUNT, gridOf, layerBase, hasLabels, SCREEN, TEX } from '../BP/scripts/core/uiSpec.js';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const NS = 'tensura_tree';
const COL = 'form_buttons';

const pct = (v) => `${+v.toFixed(4)}%`;
const colText = { binding_name: '#form_button_text', binding_type: 'collection', binding_collection_name: COL };
const colTex = { binding_name: '#form_button_texture', binding_type: 'collection', binding_collection_name: COL };
const colTexFs = { binding_name: '#form_button_texture_file_system', binding_type: 'collection', binding_collection_name: COL };
const view = (src, target) => ({ binding_type: 'view', source_property_name: src, target_property_name: target });
const hasMark = `(not ((#title_text - '${MARK}') = #title_text))`;

function stripTitle() {
  let e = '#title_text';
  for (const z of ZOOMS) e = `(${e} - '${MARK}§${z}')`;
  return `(${e} - '${MARK}')`;
}

function buttonBody(extra = {}) {
  return {
    $pressed_button_name: 'button.form_button_click',
    size: ['100%', '100%'],
    default_control: 'default',
    hover_control: 'hover',
    pressed_control: 'pressed',
    bindings: [{ binding_type: 'collection_details', binding_collection_name: COL }],
    ...extra,
  };
}

function templates() {
  return {
    // ── Células da grade ──
    pipe: {
      type: 'image', layer: 2, keep_ratio: false, bilinear: true,
      anchor_from: 'top_left', anchor_to: 'top_left',
      bindings: [colText, view(`('${TEX.pipe}' + #form_button_text)`, '#texture'), view("(not (#form_button_text = ''))", '#visible')],
    },
    node: {
      type: 'panel', anchor_from: 'top_left', anchor_to: 'top_left',
      bindings: [colText, view("(not (#form_button_text = ''))", '#visible')],
      controls: [
        { icon: { type: 'image', layer: 3, size: ['100%y', '56%'], bilinear: true, bindings: [
          { ...colTex, binding_name_override: '#texture' }, { ...colTexFs, binding_name_override: '#texture_file_system' }] } },
        { frame: { type: 'image', layer: 4, size: ['100%y', '88%'], bilinear: true, bindings: [
          colText, view(`('${TEX.node}' + #form_button_text)`, '#texture')] } },
        { 'hit@common.button': { layer: 5, ...buttonBody({ size: ['100%y', '80%'] }), controls: [
          { default: { type: 'panel' } },
          { hover: { type: 'image', texture: `${TEX.ui}hover`, size: ['100%', '100%'], bilinear: true } },
          { pressed: { type: 'image', texture: `${TEX.ui}hover`, size: ['100%', '100%'], alpha: 0.6, bilinear: true } },
        ] } },
      ],
    },
    label: {
      type: 'label', layer: 6, anchor_from: 'bottom_middle', anchor_to: 'bottom_middle',
      size: ['140%', 'default'], max_size: ['140%', '30%'], text_alignment: 'center', shadow: true,
      font_scale_factor: 0.75, color: [1, 1, 1], text: '#form_button_text',
      bindings: [colText, view("(not (#form_button_text = ''))", '#visible')],
    },

    // ── Botões de controle ──
    ctl: {
      type: 'panel', anchor_from: 'top_left', anchor_to: 'top_left',
      bindings: [colText, colTex, view("((not (#form_button_text = '')) or (not (#form_button_texture = '')))", '#visible')],
      controls: [
        { 'btn@common.button': { layer: 1, ...buttonBody(), controls: [
          { default: { type: 'image', texture: `${TEX.ui}ctl`, size: ['100%', '100%'], keep_ratio: false, bilinear: true } },
          { hover: { type: 'image', texture: `${TEX.ui}ctl_hover`, size: ['100%', '100%'], keep_ratio: false, bilinear: true } },
          { pressed: { type: 'image', texture: `${TEX.ui}ctl_pressed`, size: ['100%', '100%'], keep_ratio: false, bilinear: true } },
        ] } },
        { icon: { type: 'image', layer: 2, size: '$ctl_icon_size', anchor_from: '$ctl_icon_anchor', anchor_to: '$ctl_icon_anchor',
          offset: '$ctl_icon_offset', bilinear: true, bindings: [
            { ...colTex, binding_name_override: '#texture' }, { ...colTexFs, binding_name_override: '#texture_file_system' },
            view("(not (#texture = ''))", '#visible')] } },
        { text: { type: 'label', layer: 3, text: '#form_button_text', text_alignment: 'center', shadow: true,
          size: '$ctl_text_size', anchor_from: '$ctl_text_anchor', anchor_to: '$ctl_text_anchor', offset: '$ctl_text_offset',
          font_scale_factor: '$ctl_font', color: [1, 1, 1], bindings: [colText] } },
      ],
      '$ctl_icon_size|default': ['100%y', '70%'],
      '$ctl_icon_anchor|default': 'center',
      '$ctl_icon_offset|default': [0, 0],
      '$ctl_text_size|default': ['100%', 'default'],
      '$ctl_text_anchor|default': 'center',
      '$ctl_text_offset|default': [0, 0],
      '$ctl_font|default': 0.8,
    },
  };
}

// Variáveis por estilo de botão de controle.
const ICON_ONLY = {};
const ICON_LEFT_TEXT = {
  $ctl_icon_size: ['100%y', '70%'], $ctl_icon_anchor: 'left_middle', $ctl_icon_offset: ['6%', 0],
  $ctl_text_size: ['62%', 'default'], $ctl_text_anchor: 'right_middle', $ctl_text_offset: ['-4%', 0], $ctl_font: 0.75,
};
const TAB_STYLE = {
  $ctl_icon_size: ['100%y', '62%'], $ctl_icon_anchor: 'top_middle', $ctl_icon_offset: [0, '4%'],
  $ctl_text_size: ['100%', 'default'], $ctl_text_anchor: 'bottom_middle', $ctl_text_offset: [0, '-6%'], $ctl_font: 0.6,
};

function ctl(name, index, x, y, w, h, style = ICON_ONLY) {
  return { [`${name}@${NS}.ctl`]: { collection_index: index, offset: [pct(x), pct(y)], size: [pct(w), pct(h)], ...style } };
}

function gridPanel(zoom) {
  const { cols, rows } = gridOf(zoom);
  const base = layerBase(zoom);
  const cw = 100 / cols;
  const ch = 100 / rows;
  const controls = [];
  for (let r = 0; r < rows; r++) {
    for (let c = 0; c < cols; c++) {
      const i = r * cols + c;
      const pos = { offset: [pct(c * cw), pct(r * ch)], size: [pct(cw), pct(ch)] };
      controls.push({ [`p${i}@${NS}.pipe`]: { collection_index: base.pipe + i, ...pos } });
      controls.push({ [`n${i}@${NS}.node`]: { collection_index: base.node + i, ...pos } });
      if (hasLabels(zoom)) {
        // Painel da célula com o rótulo ancorado embaixo (o índice fica no próprio rótulo).
        controls.push({ [`c${i}`]: { type: 'panel', anchor_from: 'top_left', anchor_to: 'top_left', collection_name: COL, ...pos,
          controls: [{ [`l${i}@${NS}.label`]: { collection_index: base.label + i } }] } });
      }
    }
  }
  return {
    type: 'panel', size: ['100%', '100%'], collection_name: COL,
    bindings: [{ binding_name: '#title_text' }, view(`(not ((#title_text - '${MARK}§${zoom}') = #title_text))`, '#visible')],
    controls,
  };
}

function rootDef() {
  const top = SCREEN.topBar;
  const bottom = SCREEN.bottomBar;
  const area = 100 - top - bottom;

  // Barra superior: título + Grimório, Status, Fechar.
  const topBar = {
    type: 'panel', size: ['100%', pct(top)], anchor_from: 'top_left', anchor_to: 'top_left', layer: 20, collection_name: COL,
    controls: [
      { bg: { type: 'image', texture: `${TEX.ui}bar`, size: ['100%', '100%'], keep_ratio: false, layer: 0 } },
      { header: { type: 'label', layer: 2, anchor_from: 'left_middle', anchor_to: 'left_middle', offset: ['1%', 0],
        size: ['68%', 'default'], max_size: ['68%', '100%'], shadow: true, font_scale_factor: 0.85, color: [1, 1, 1],
        text: '#tensura_header', bindings: [{ binding_name: '#title_text' }, view(stripTitle(), '#tensura_header')] } },
      ctl('grimoire', CTRL.grimoire, 70, 10, 11, 80, ICON_LEFT_TEXT),
      ctl('status', CTRL.status, 81.5, 10, 10, 80, ICON_LEFT_TEXT),
      ctl('close', CTRL.close, 92.5, 10, 6.5, 80, ICON_ONLY),
    ],
  };

  // Barra inferior: 10 abas + navegação (setas, zoom, centralizar).
  const tabW = 61 / TAB_COUNT;
  const nav = [];
  for (let i = 0; i < TAB_COUNT; i++) nav.push(ctl(`tab${i}`, CTRL.tab0 + i, 0.5 + i * tabW, 6, tabW - 0.4, 88, TAB_STYLE));
  const navBtns = [['pan_l', CTRL.panLeft], ['pan_u', CTRL.panUp], ['pan_d', CTRL.panDown], ['pan_r', CTRL.panRight],
    ['zoom_out', CTRL.zoomOut], ['zoom_in', CTRL.zoomIn], ['center', CTRL.center]];
  const navW = (100 - 62.5 - 0.5) / navBtns.length;
  navBtns.forEach(([n, idx], k) => nav.push(ctl(n, idx, 62.5 + k * navW, 6, navW - 0.5, 88, ICON_ONLY)));
  const bottomBar = {
    type: 'panel', size: ['100%', pct(bottom)], anchor_from: 'bottom_left', anchor_to: 'bottom_left', layer: 20, collection_name: COL,
    controls: [{ bg: { type: 'image', texture: `${TEX.ui}bar`, size: ['100%', '100%'], keep_ratio: false, layer: 0 } }, ...nav],
  };

  // Painel de detalhes (sobre a parte direita da árvore; só aparece com um nó selecionado).
  const details = {
    type: 'panel', size: [pct(SCREEN.detailsWidth), pct(area)], offset: [0, pct(top)],
    anchor_from: 'top_right', anchor_to: 'top_right', layer: 30, collection_name: COL,
    bindings: [{ binding_name: '#form_text' }, view("(not (#form_text = ''))", '#visible')],
    controls: [
      { bg: { type: 'image', texture: `${TEX.ui}panel`, size: ['100%', '100%'], keep_ratio: false, layer: 0 } },
      { icon_slot: { type: 'panel', size: ['100%y', '24%'], offset: ['4%', '2%'], anchor_from: 'top_left', anchor_to: 'top_left', layer: 2,
        collection_name: COL, controls: [
          { icon_bg: { type: 'image', texture: `${TEX.ui}icon_slot`, size: ['100%', '100%'], layer: 0 } },
          { [`detail_icon@${NS}.ctl`]: { collection_index: CTRL.detailIcon, size: ['100%', '100%'], ...ICON_ONLY, $ctl_icon_size: ['100%y', '86%'] } },
        ] } },
      ctl('close_details', CTRL.closeDetails, 86, 1.5, 12, 9, ICON_ONLY),
      { text_area: { type: 'panel', size: ['94%', '45%'], offset: ['3%', '27%'], anchor_from: 'top_left', anchor_to: 'top_left', layer: 2,
        controls: [{ 'scroll@common.scrolling_panel': {
          anchor_to: 'top_left', anchor_from: 'top_left', size: ['100%', '100%'], $show_background: false,
          $scrolling_content: `${NS}.details_text`, $scroll_size: [4, '100% - 2px'], $scrolling_pane_size: ['100% - 6px', '100%'],
          $scrolling_pane_offset: [0, 0], $scroll_bar_right_padding_size: [0, 0],
        } }] } },
      ctl('goto_link', CTRL.gotoLink, 4, 73.5, 92, 7.5, ICON_LEFT_TEXT),
      ctl('equip', CTRL.equip, 4, 81.5, 92, 7.5, ICON_LEFT_TEXT),
      ctl('unlock', CTRL.unlock, 4, 89.5, 92, 9, ICON_LEFT_TEXT),
    ],
  };

  const treeArea = {
    type: 'panel', size: ['100%', pct(area)], offset: [0, pct(top)], anchor_from: 'top_left', anchor_to: 'top_left',
    clips_children: true, layer: 5,
    controls: ZOOMS.map((z) => ({ [`grid_z${z}@${NS}.grid_z${z}`]: {} })),
  };

  return {
    type: 'panel', size: ['100%', '100%'], collection_name: COL,
    controls: [
      { bg: { type: 'image', texture: `${TEX.ui}bg`, size: ['100%', '100%'], keep_ratio: false, layer: 0 } },
      { tree_area: treeArea },
      { top_bar: topBar },
      { bottom_bar: bottomBar },
      { details },
    ],
  };
}

export function buildUi(outDir = join(ROOT, 'RP', 'ui')) {
  mkdirSync(outDir, { recursive: true });
  const tree = { namespace: NS, ...templates(), root: rootDef(),
    details_text: {
      type: 'stack_panel', size: ['100% - 4px', '100%c'], orientation: 'vertical', anchor_from: 'top_left', anchor_to: 'top_left',
      controls: [{ body: { type: 'label', size: ['100%', 'default'], anchor_from: 'top_left', anchor_to: 'top_left',
        text: '#form_text', color: [1, 1, 1], font_scale_factor: 0.8, shadow: false } }],
    },
  };
  for (const z of ZOOMS) tree[`grid_z${z}`] = gridPanel(z);

  const serverForm = {
    namespace: 'server_form',
    // Substitui o conteúdo da tela de formulários: mesmo factory do jogo, mas o
    // "long_form" passa pelo roteador abaixo. Formulários sem o marcador não mudam.
    main_screen_content: {
      type: 'panel', size: ['100%', '100%'],
      controls: [{ server_form_factory: { type: 'factory', control_ids: {
        long_form: '@server_form.tensura_long_form_router', custom_form: '@server_form.custom_form' } } }],
    },
    tensura_long_form_router: {
      type: 'panel', size: ['100%', '100%'],
      controls: [
        { vanilla_wrap: { type: 'panel', size: ['100%', '100%'],
          bindings: [{ binding_name: '#title_text' }, view(`((#title_text - '${MARK}') = #title_text)`, '#visible')],
          controls: [{ 'long_form@server_form.long_form': {} }] } },
        { tensura_wrap: { type: 'panel', size: ['100%', '100%'],
          bindings: [{ binding_name: '#title_text' }, view(hasMark, '#visible')],
          controls: [{ [`tree@${NS}.root`]: {} }] } },
      ],
    },
  };

  const header = '// Gerado por tools/build-ui.mjs — não edite à mão.\n';
  writeFileSync(join(outDir, 'tensura_tree.json'), header + JSON.stringify(tree, null, 1));
  writeFileSync(join(outDir, 'server_form.json'), header + JSON.stringify(serverForm, null, 1));
  writeFileSync(join(outDir, '_ui_defs.json'), JSON.stringify({ ui_defs: ['ui/tensura_tree.json'] }, null, 2));
  let controls = 0;
  for (const z of ZOOMS) controls += tree[`grid_z${z}`].controls.length;
  return { controls };
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const r = buildUi();
  console.log(`UI gerada: ${r.controls} células de grade.`);
}
