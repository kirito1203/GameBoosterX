// Gera RP/ui/*.json a partir de BP/scripts/core/uiSpec.js (fonte única da verdade).
//
// Duas telas por cima do ActionFormData (o título decide qual):
//   §t§n§m… → menu principal com abas (Status, Raça, Habilidades, Evolução, Registros)
//   §t§n§s… → árvore de habilidades (mapa único, nós quadrados, linhas em 8 direções)
// Formulários sem marcador continuam idênticos aos do jogo.
//
// Técnicas vindas dos arquivos do próprio jogo (bedrock-samples): collection_index para
// posições fixas (pdp_screenshots_section.json), textura por concatenação de string
// (game_tip_screen.json), "%y" = porcentagem da própria altura (ui_purchase_common.json),
// live_player_renderer (inventory_screen.json).
import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  MARK, MENU_MARK, ZOOMS, CTRL, CAT_COUNT, gridOf, layerBase, hasLabels, SCREEN, TEX,
  MENU, MENU_TABS, ACTIVE_ROWS, BAR_SLOTS, ACTION_COUNT,
} from '../BP/scripts/core/uiSpec.js';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const NS = 'tensura_tree';
const COL = 'form_buttons';

const pct = (v) => `${+v.toFixed(4)}%`;
const colText = { binding_name: '#form_button_text', binding_type: 'collection', binding_collection_name: COL };
const colTex = { binding_name: '#form_button_texture', binding_type: 'collection', binding_collection_name: COL };
const colTexFs = { binding_name: '#form_button_texture_file_system', binding_type: 'collection', binding_collection_name: COL };
const view = (src, target) => ({ binding_type: 'view', source_property_name: src, target_property_name: target });
const has = (mark) => `(not ((#title_text - '${mark}') = #title_text))`;
const TL = { anchor_from: 'top_left', anchor_to: 'top_left' };
const at = (x, y, w, h) => ({ ...TL, offset: [pct(x), pct(y)], size: [pct(w), pct(h)] });
const img = (texture, extra = {}) => ({ type: 'image', texture, size: ['100%', '100%'], keep_ratio: false, bilinear: false, ...extra });

function stripTitle() {
  let e = '#title_text';
  for (const z of ZOOMS) e = `(${e} - '${MARK}§${z}')`;
  return `(${e} - '${MARK}')`;
}

function buttonBody(extra = {}) {
  return {
    $pressed_button_name: 'button.form_button_click',
    size: ['100%', '100%'],
    default_control: 'default', hover_control: 'hover', pressed_control: 'pressed',
    bindings: [{ binding_type: 'collection_details', binding_collection_name: COL }],
    ...extra,
  };
}

function templates() {
  return {
    // ── Árvore ──
    line: {
      type: 'image', layer: 2, keep_ratio: false, bilinear: false, ...TL,
      bindings: [colText, view(`('${TEX.line}' + #form_button_text)`, '#texture'), view("(not (#form_button_text = ''))", '#visible')],
    },
    node: {
      type: 'panel', ...TL,
      bindings: [colText, view("(not (#form_button_text = ''))", '#visible')],
      controls: [
        { icon: { type: 'image', layer: 3, size: ['100%y', '54%'], bilinear: false, bindings: [
          { ...colTex, binding_name_override: '#texture' }, { ...colTexFs, binding_name_override: '#texture_file_system' }] } },
        { frame: { type: 'image', layer: 4, size: ['100%y', '78%'], bilinear: false, bindings: [
          colText, view(`('${TEX.node}' + #form_button_text)`, '#texture')] } },
        { 'hit@common.button': { layer: 5, ...buttonBody({ size: ['100%y', '78%'] }), controls: [
          { default: { type: 'panel' } },
          { hover: { type: 'image', texture: `${TEX.ui}hover`, size: ['100%', '100%'], bilinear: false } },
          { pressed: { type: 'image', texture: `${TEX.ui}hover`, size: ['100%', '100%'], alpha: 0.6, bilinear: false } },
        ] } },
      ],
    },
    label: {
      type: 'label', layer: 6, anchor_from: 'bottom_middle', anchor_to: 'bottom_middle',
      size: ['150%', 'default'], max_size: ['150%', '30%'], text_alignment: 'center', shadow: true,
      font_scale_factor: 0.7, color: [1, 1, 1], text: '#form_button_text',
      bindings: [colText, view("(not (#form_button_text = ''))", '#visible')],
    },

    // ── Botão genérico (fundo próprio + ícone + texto) ──
    ctl: {
      type: 'panel', ...TL,
      bindings: [colText, colTex, view("((not (#form_button_text = '')) or (not (#form_button_texture = '')))", '#visible')],
      controls: [
        { 'btn@common.button': { layer: 1, ...buttonBody(), controls: [
          { default: img('$ctl_bg') }, { hover: img('$ctl_bg_hover') }, { pressed: img('$ctl_bg_pressed') },
        ] } },
        { icon: { type: 'image', layer: 2, size: '$ctl_icon_size', anchor_from: '$ctl_icon_anchor', anchor_to: '$ctl_icon_anchor',
          offset: '$ctl_icon_offset', bilinear: false, keep_ratio: '$ctl_icon_keep', bindings: [
            { ...colTex, binding_name_override: '#texture' }, { ...colTexFs, binding_name_override: '#texture_file_system' },
            view("(not (#texture = ''))", '#visible')] } },
        { text: { type: 'label', layer: 3, text: '#form_button_text', text_alignment: '$ctl_align', shadow: true,
          size: '$ctl_text_size', anchor_from: '$ctl_text_anchor', anchor_to: '$ctl_text_anchor', offset: '$ctl_text_offset',
          font_scale_factor: '$ctl_font', color: [1, 1, 1], bindings: [colText] } },
      ],
      '$ctl_bg|default': `${TEX.ui}ctl`,
      '$ctl_bg_hover|default': `${TEX.ui}ctl_hover`,
      '$ctl_bg_pressed|default': `${TEX.ui}ctl_pressed`,
      '$ctl_icon_size|default': ['100%y', '70%'],
      '$ctl_icon_anchor|default': 'center',
      '$ctl_icon_offset|default': [0, 0],
      '$ctl_icon_keep|default': true,
      '$ctl_text_size|default': ['100%', 'default'],
      '$ctl_text_anchor|default': 'center',
      '$ctl_text_offset|default': [0, 0],
      '$ctl_align|default': 'center',
      '$ctl_font|default': 0.8,
    },
    // Texto só leitura vindo do formulário (valores do Status).
    value: {
      type: 'label', ...TL, layer: 3, text: '#form_button_text', shadow: true, color: [1, 1, 1],
      text_alignment: '$v_align', font_scale_factor: '$v_font', bindings: [colText],
      '$v_align|default': 'right', '$v_font|default': 1.0,
    },
    // Imagem só leitura vinda do formulário (barra de progresso).
    picture: {
      type: 'image', ...TL, layer: 3, keep_ratio: false, bilinear: false,
      bindings: [{ ...colTex, binding_name_override: '#texture' }, { ...colTexFs, binding_name_override: '#texture_file_system' }],
    },
    static_text: {
      type: 'label', ...TL, layer: 3, shadow: true, color: [1, 1, 1], text: '$st_text', font_scale_factor: '$st_font',
      text_alignment: '$st_align', '$st_font|default': 1.0, '$st_align|default': 'left',
    },
    box: { type: 'image', ...TL, layer: 1, texture: `${TEX.menu}box`, keep_ratio: false, bilinear: false },
  };
}

// Estilos de botão.
const ICON_ONLY = {};
const ICON_LEFT_TEXT = {
  $ctl_icon_size: ['100%y', '70%'], $ctl_icon_anchor: 'left_middle', $ctl_icon_offset: ['5%', 0],
  $ctl_text_size: ['66%', 'default'], $ctl_text_anchor: 'right_middle', $ctl_text_offset: ['-4%', 0], $ctl_font: 0.75,
};
const MENU_BTN = {
  ...ICON_LEFT_TEXT, $ctl_bg: `${TEX.menu}button`, $ctl_bg_hover: `${TEX.menu}button_hover`, $ctl_bg_pressed: `${TEX.menu}button_pressed`, $ctl_font: 0.95,
};
// Aba: a imagem da aba (com ícone e estado) é o próprio fundo; o texto vai à direita.
const MENU_TAB = {
  $ctl_bg: `${TEX.menu}tab_bg`, $ctl_bg_hover: `${TEX.menu}tab_bg_hover`, $ctl_bg_pressed: `${TEX.menu}tab_bg_hover`,
  $ctl_icon_size: ['100%y', '72%'], $ctl_icon_anchor: 'left_middle', $ctl_icon_offset: ['6%', 0],
  $ctl_text_size: ['70%', 'default'], $ctl_text_anchor: 'right_middle', $ctl_text_offset: ['-4%', 0], $ctl_font: 0.95,
};
const ROW = {
  $ctl_bg: `${TEX.menu}row`, $ctl_bg_hover: `${TEX.menu}row_hover`, $ctl_bg_pressed: `${TEX.menu}row_hover`,
  $ctl_icon_size: ['100%y', '80%'], $ctl_icon_anchor: 'left_middle', $ctl_icon_offset: ['3%', 0],
  $ctl_text_size: ['70%', 'default'], $ctl_text_anchor: 'left_middle', $ctl_text_offset: ['28%', 0], $ctl_align: 'left', $ctl_font: 1.0,
};
const SLOT = {
  $ctl_bg: `${TEX.menu}slot`, $ctl_bg_hover: `${TEX.menu}slot_hover`, $ctl_bg_pressed: `${TEX.menu}slot_hover`,
  $ctl_icon_size: ['100%y', '82%'],
};
const PLAIN = { $ctl_bg: `${TEX.menu}none`, $ctl_bg_hover: `${TEX.menu}none`, $ctl_bg_pressed: `${TEX.menu}none`, $ctl_font: 1.1 };

function ctl(name, index, x, y, w, h, style = ICON_ONLY) {
  return { [`${name}@${NS}.ctl`]: { collection_index: index, ...at(x, y, w, h), ...style } };
}

// ── Árvore ──
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
      controls.push({ [`p${i}@${NS}.line`]: { collection_index: base.pipe + i, ...pos } });
      controls.push({ [`n${i}@${NS}.node`]: { collection_index: base.node + i, ...pos } });
      if (hasLabels(zoom)) {
        controls.push({ [`c${i}`]: { type: 'panel', ...TL, collection_name: COL, ...pos,
          controls: [{ [`l${i}@${NS}.label`]: { collection_index: base.label + i } }] } });
      }
    }
  }
  return {
    type: 'panel', size: ['100%', '100%'], collection_name: COL,
    bindings: [{ binding_name: '#title_text' }, view(has(`${MARK}§${zoom}`), '#visible')],
    controls,
  };
}

function treeRoot() {
  const top = SCREEN.topBar;
  const bottom = SCREEN.bottomBar;
  const area = 100 - top - bottom;
  const topBar = {
    type: 'panel', size: ['100%', pct(top)], ...TL, layer: 20, collection_name: COL,
    controls: [
      { bg: img(`${TEX.ui}bar`, { layer: 0 }) },
      { header: { type: 'label', layer: 2, anchor_from: 'left_middle', anchor_to: 'left_middle', offset: ['1%', 0],
        size: ['76%', 'default'], max_size: ['76%', '100%'], shadow: true, font_scale_factor: 0.85, color: [1, 1, 1],
        text: '#tensura_header', bindings: [{ binding_name: '#title_text' }, view(stripTitle(), '#tensura_header')] } },
      ctl('back', CTRL.back, 78, 8, 13, 84, ICON_LEFT_TEXT),
      ctl('close', CTRL.close, 92, 8, 7, 84),
    ],
  };
  // Barra inferior: saltos de categoria + navegação.
  const catW = 64 / CAT_COUNT;
  const nav = [];
  for (let i = 0; i < CAT_COUNT; i++) nav.push(ctl(`cat${i}`, CTRL.cat0 + i, 0.5 + i * catW, 8, catW - 0.3, 84, { $ctl_icon_size: ['100%y', '78%'] }));
  const navBtns = [['pan_l', CTRL.panLeft], ['pan_u', CTRL.panUp], ['pan_d', CTRL.panDown], ['pan_r', CTRL.panRight],
    ['zoom_out', CTRL.zoomOut], ['zoom_in', CTRL.zoomIn], ['center', CTRL.center]];
  const navW = (100 - 65 - 0.5) / navBtns.length;
  navBtns.forEach(([n, idx], k) => nav.push(ctl(n, idx, 65 + k * navW, 8, navW - 0.4, 84)));
  const bottomBar = {
    type: 'panel', size: ['100%', pct(bottom)], anchor_from: 'bottom_left', anchor_to: 'bottom_left', layer: 20, collection_name: COL,
    controls: [{ bg: img(`${TEX.ui}bar`, { layer: 0 }) }, ...nav],
  };
  const details = {
    type: 'panel', size: [pct(SCREEN.detailsWidth), pct(area)], offset: [0, pct(top)],
    anchor_from: 'top_right', anchor_to: 'top_right', layer: 30, collection_name: COL,
    bindings: [{ binding_name: '#form_text' }, view("(not (#form_text = ''))", '#visible')],
    controls: [
      { bg: img(`${TEX.ui}panel`, { layer: 0 }) },
      { icon_slot: { type: 'panel', size: ['100%y', '22%'], offset: ['4%', '2%'], ...TL, layer: 2, collection_name: COL, controls: [
        { icon_bg: img(`${TEX.ui}icon_slot`, { layer: 0 }) },
        { [`detail_icon@${NS}.ctl`]: { collection_index: CTRL.detailIcon, size: ['100%', '100%'], ...PLAIN, $ctl_icon_size: ['100%y', '84%'] } },
      ] } },
      ctl('close_details', CTRL.closeDetails, 87, 1.5, 11, 9),
      { text_area: { type: 'panel', size: ['94%', '44%'], offset: ['3%', '26%'], ...TL, layer: 2,
        controls: [{ 'scroll@common.scrolling_panel': {
          ...TL, size: ['100%', '100%'], $show_background: false,
          $scrolling_content: `${NS}.details_text`, $scroll_size: [4, '100% - 2px'], $scrolling_pane_size: ['100% - 6px', '100%'],
          $scrolling_pane_offset: [0, 0], $scroll_bar_right_padding_size: [0, 0],
        } }] } },
      ctl('goto_link', CTRL.gotoLink, 4, 72, 92, 8, ICON_LEFT_TEXT),
      ctl('equip', CTRL.equip, 4, 81, 92, 8, ICON_LEFT_TEXT),
      ctl('unlock', CTRL.unlock, 4, 90, 92, 9, ICON_LEFT_TEXT),
    ],
  };
  return {
    type: 'panel', size: ['100%', '100%'], collection_name: COL,
    controls: [
      { bg: img(`${TEX.ui}bg`, { layer: 0, bilinear: true }) },
      { tree_area: { type: 'panel', size: ['100%', pct(area)], offset: [0, pct(top)], ...TL, clips_children: true, layer: 5,
        controls: ZOOMS.map((z) => ({ [`grid_z${z}@${NS}.grid_z${z}`]: {} })) } },
      { top_bar: topBar }, { bottom_bar: bottomBar }, { details },
    ],
  };
}

// ── Menu principal ──
function menuRoot() {
  const st = (text, x, y, w, extra = {}) => ({ [`t_${Math.round(x * 10)}_${Math.round(y * 10)}@${NS}.static_text`]: {
    $st_text: text, ...TL, offset: [pct(x), pct(y)], size: [pct(w), 'default'], ...extra } });
  const value = (name, idx, x, y, w, extra = {}) => ({ [`${name}@${NS}.value`]: { collection_index: idx, offset: [pct(x), pct(y)], size: [pct(w), 'default'], ...extra } });

  // Coluna esquerda (comum às abas): boneco do jogador + nome da raça.
  const left = [
    { doll_box: { ...at(2, 16, 22, 52), type: 'image', layer: 1, texture: `${TEX.menu}box`, keep_ratio: false, controls: [
      { player: { type: 'custom', renderer: 'live_player_renderer', property_bag: { '#look_at_cursor': true },
        layer: 8, size: ['100%', '92%'], anchor_from: 'bottom_middle', anchor_to: 'bottom_middle', offset: [0, '-4%'] } },
    ] } },
    ctl('race_name', MENU.raceName, 2, 70, 22, 9.5, { ...PLAIN, $ctl_bg: `${TEX.menu}box`, $ctl_bg_hover: `${TEX.menu}box`, $ctl_bg_pressed: `${TEX.menu}box`, $ctl_font: 1.2 }),
  ];

  const status = {
    type: 'panel', size: ['100%', '100%'], collection_name: COL,
    bindings: [{ binding_name: '#title_text' }, view(has(`${MENU_MARK}§1`), '#visible')],
    controls: [
      { stats_box: { ...at(26, 16, 41, 63), type: 'image', layer: 1, texture: `${TEX.menu}box`, keep_ratio: false } },
      st('EP:', 29, 22, 15), value('v_ep', MENU.stat0, 44, 22, 20),
      st('Aura:', 29, 32, 15), value('v_aura', MENU.stat0 + 1, 44, 32, 20),
      st('Magículas:', 29, 41, 15), value('v_mp', MENU.stat0 + 2, 44, 41, 20),
      { sep: { ...at(28, 50, 37, 0.6), type: 'image', layer: 2, texture: `${TEX.menu}line`, keep_ratio: false } },
      st('Nível:', 29, 55, 15), value('v_lvl', MENU.stat0 + 3, 44, 55, 20),
      st('Progresso de evolução:', 29, 64, 26, { $st_font: 0.9 }), value('v_prog', MENU.stat0 + 4, 55, 64, 9),
      { [`bar@${NS}.picture`]: { collection_index: MENU.progressBar, ...at(28.5, 71, 36, 5) } },
      { skills_box: { ...at(69, 16, 29, 63), type: 'image', layer: 1, texture: `${TEX.menu}box`, keep_ratio: false } },
      st('§bHabilidades Ativas', 71, 19, 25, { $st_align: 'center', $st_font: 1.0 }),
      ...Array.from({ length: ACTIVE_ROWS }, (_, i) => ctl(`active${i}`, MENU.active0 + i, 70.5, 26 + i * 13, 26, 11.5, ROW)),
      st('§bBarra de Habilidades', 3, 81.5, 30, { $st_font: 0.9 }),
      ...Array.from({ length: BAR_SLOTS }, (_, i) => ({ [`slot${i}@${NS}.ctl`]: {
        collection_index: MENU.slot0 + i, ...TL, offset: [pct(3 + i * 8.6), '87%'], size: ['100%y', '11%'], ...SLOT } })),
    ],
  };

  const info = {
    type: 'panel', size: ['100%', '100%'], collection_name: COL,
    bindings: [{ binding_name: '#title_text' },
      view(`(${has(`${MENU_MARK}§2`)} or ${has(`${MENU_MARK}§4`)} or ${has(`${MENU_MARK}§5`)})`, '#visible')],
    controls: [
      { text_box: { ...at(26, 16, 72, 63), type: 'image', layer: 1, texture: `${TEX.menu}box`, keep_ratio: false } },
      { text_area: { type: 'panel', ...at(27.5, 18, 69, 59), layer: 2, controls: [{ 'scroll@common.scrolling_panel': {
        ...TL, size: ['100%', '100%'], $show_background: false,
        $scrolling_content: `${NS}.details_text`, $scroll_size: [4, '100% - 2px'], $scrolling_pane_size: ['100% - 6px', '100%'],
        $scrolling_pane_offset: [0, 0], $scroll_bar_right_padding_size: [0, 0],
      } }] } },
      ...Array.from({ length: ACTION_COUNT }, (_, i) => ctl(`action${i}`, MENU.action0 + i, 3 + (i % 3) * 21.5, 82 + Math.floor(i / 3) * 8.5, 20.5, 7.5, { ...MENU_BTN, $ctl_font: 0.8 })),
    ],
  };

  const tabW = 86 / MENU_TABS.length;
  const windowCtrls = [
    { bg: img(`${TEX.menu}window`, { layer: 0 }) },
    ...MENU_TABS.map((t, i) => ctl(`tab_${t}`, MENU.tab0 + i, 1.2 + i * tabW, 2, tabW - 0.8, 11, MENU_TAB)),
    ctl('close', MENU.close, 88.3, 2, 10.5, 11, { ...ICON_ONLY, $ctl_bg: `${TEX.menu}button`, $ctl_bg_hover: `${TEX.menu}button_hover`, $ctl_bg_pressed: `${TEX.menu}button_pressed` }),
    { tab_line: { ...at(1, 13.4, 98, 0.8), type: 'image', layer: 2, texture: `${TEX.menu}tab_line`, keep_ratio: false } },
    ...left,
    { status },
    { info },
    ctl('back', MENU.back, 72, 84, 26, 12, MENU_BTN),
  ];
  return {
    type: 'panel', size: ['100%', '100%'], collection_name: COL,
    controls: [
      { shade: img(`${TEX.menu}shade`, { layer: 0 }) },
      { window: { type: 'panel', size: ['88%', '88%'], layer: 2, collection_name: COL, controls: windowCtrls } },
    ],
  };
}

export function buildUi(outDir = join(ROOT, 'RP', 'ui')) {
  mkdirSync(outDir, { recursive: true });
  const tree = {
    namespace: NS, ...templates(), root: treeRoot(), menu_root: menuRoot(),
    details_text: {
      type: 'stack_panel', size: ['100% - 4px', '100%c'], orientation: 'vertical', ...TL,
      controls: [{ body: { type: 'label', size: ['100%', 'default'], ...TL,
        text: '#form_text', color: [1, 1, 1], font_scale_factor: 0.8, shadow: false } }],
    },
  };
  for (const z of ZOOMS) tree[`grid_z${z}`] = gridPanel(z);

  const notMarked = `(((#title_text - '${MARK}') = #title_text) and ((#title_text - '${MENU_MARK}') = #title_text))`;
  const serverForm = {
    namespace: 'server_form',
    main_screen_content: {
      type: 'panel', size: ['100%', '100%'],
      controls: [{ server_form_factory: { type: 'factory', control_ids: {
        long_form: '@server_form.tensura_long_form_router', custom_form: '@server_form.custom_form' } } }],
    },
    tensura_long_form_router: {
      type: 'panel', size: ['100%', '100%'],
      controls: [
        { vanilla_wrap: { type: 'panel', size: ['100%', '100%'],
          bindings: [{ binding_name: '#title_text' }, view(notMarked, '#visible')],
          controls: [{ 'long_form@server_form.long_form': {} }] } },
        { tensura_tree_wrap: { type: 'panel', size: ['100%', '100%'],
          bindings: [{ binding_name: '#title_text' }, view(has(MARK), '#visible')],
          controls: [{ [`tree@${NS}.root`]: {} }] } },
        { tensura_menu_wrap: { type: 'panel', size: ['100%', '100%'],
          bindings: [{ binding_name: '#title_text' }, view(has(MENU_MARK), '#visible')],
          controls: [{ [`menu@${NS}.menu_root`]: {} }] } },
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
  console.log(`UI gerada: ${r.controls} controles de grade.`);
}
