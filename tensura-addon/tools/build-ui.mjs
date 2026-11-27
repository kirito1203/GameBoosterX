// Gera RP/ui/*.json a partir de BP/scripts/core/uiSpec.js (fonte única da verdade).
//
// Duas telas por cima do ActionFormData (o título decide qual):
//   §t§n§m… → menu principal com abas (Status, Raça, Habilidades, Evolução, Registros)
//   §t§n§s… → árvore de habilidades (mapa único, nós quadrados, linhas em 8 direções)
// Formulários sem marcador continuam idênticos aos do jogo.
//
// Técnicas vindas dos arquivos do próprio jogo (bedrock-samples): grid + grid_position para
// ligar cada elemento ao botão certo (crafting_grid_3x3 em inventory_screen.json), textura por concatenação de string
// (game_tip_screen.json), "%y" = porcentagem da própria altura (ui_purchase_common.json),
// live_player_renderer (inventory_screen.json).
import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  MARK, MENU_MARK, CTRL, CAT_COUNT, layerBase, TEX, TREE_COLS, TREE_ROWS, DETAIL_ROWS, NODE_TOGGLE_GROUP, detailPlacement,
  MENU, MENU_TABS, ACTIVE_ROWS, BAR_SLOTS, ACTION_COUNT,
  RACE_MARK, RACE, RACE_CARD_COLS, RACE_CARD_ROWS, CARD_SELECTED, CARD_LOCKED,
  SAGE_MARK, SAGE, SAGE_TILES, SAGE_ACTIONS,
  CATALOG_MARK, CATALOG, CATALOG_CATS, CATALOG_COLS, CATALOG_ROWS, TILE_STATE,
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
  return `(#title_text - '${MARK}')`;
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
        // sombra no "chão" (profundidade): levemente abaixo e à direita do nó
        { shadow: { type: 'image', layer: 2, texture: `${TEX.ui}node_shadow`, size: ['100%y', '80%'], offset: ['4%', '12%'], keep_ratio: false } },
        // brilho pulsante do cristal (cor do estado / aura dourada ou roxa nas raras e supremas)
        { glow: { type: 'image', layer: 2, size: ['100%y', '100%'], bilinear: true, anims: [`@${NS}.anim_glow_a`], bindings: [
          colText, view(`('${TEX.ui}glow/g_' + #form_button_text)`, '#texture')] } },
        // explosão de luz (uma vez) no nó em foco: recém-desbloqueado ou escolhido no menu
        { burst: { type: 'image', layer: 7, size: ['100%y', '100%'], bilinear: true,
          anims: [`@${NS}.anim_burst_size`, `@${NS}.anim_burst_alpha`], bindings: [
            colText, view(`('${TEX.ui}burst/b_' + #form_button_text)`, '#texture')] } },
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
    label_cell: { type: 'panel', layer: 6, controls: [{ [`lbl@${NS}.label`]: {} }] },
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
      type: 'label', ...TL, layer: 3, size: ['100%', 'default'], text: '#form_button_text', shadow: true, color: [1, 1, 1],
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
    // Item do Catálogo: moldura pelo estado, emblema neon em cima, nome e descrição curta embaixo.
    cat_tile: {
      type: 'panel', ...TL,
      bindings: [colText, view("(not (#form_button_text = ''))", '#visible')],
      controls: [
        { bg_u: img(`${TEX.ui}tile_u`, { layer: 1, bindings: [colText, view(`(not ((#form_button_text - '${TILE_STATE.unlocked}') = #form_button_text))`, '#visible')] }) },
        { bg_a: img(`${TEX.ui}tile_a`, { layer: 1, bindings: [colText, view(`(not ((#form_button_text - '${TILE_STATE.available}') = #form_button_text))`, '#visible')] }) },
        { bg_l: img(`${TEX.ui}tile_l`, { layer: 1, bindings: [colText, view(`(not ((#form_button_text - '${TILE_STATE.locked}') = #form_button_text))`, '#visible')] }) },
        { art: { type: 'image', layer: 2, anchor_from: 'top_middle', anchor_to: 'top_middle', offset: [0, '5%'], size: ['100%y', '42%'], bilinear: false,
          bindings: [{ ...colTex, binding_name_override: '#texture' }, { ...colTexFs, binding_name_override: '#texture_file_system' }] } },
        { name: { type: 'label', layer: 3, anchor_from: 'top_middle', anchor_to: 'top_middle', offset: [0, '50%'], size: ['94%', 'default'], max_size: ['94%', '46%'],
          text: '#tile_text', text_alignment: 'center', font_scale_factor: 0.52, shadow: true, color: [1, 1, 1],
          bindings: [colText, view(`(((#form_button_text - '${TILE_STATE.unlocked}') - '${TILE_STATE.available}') - '${TILE_STATE.locked}')`, '#tile_text')] } },
        { 'hit@common.button': { layer: 5, ...buttonBody(), controls: [
          { default: { type: 'panel' } },
          { hover: img(`${TEX.ui}hover`) },
          { pressed: img(`${TEX.ui}hover`, { alpha: 0.6 }) },
        ] } },
      ],
    },
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
// Categoria da barra lateral do menu: ícone à esquerda, nome à direita.
const MENU_SIDE = {
  $ctl_bg: `${TEX.menu}tab_bg`, $ctl_bg_hover: `${TEX.menu}tab_bg_hover`, $ctl_bg_pressed: `${TEX.menu}button_pressed`,
  $ctl_icon_size: ['100%y', '68%'], $ctl_icon_anchor: 'left_middle', $ctl_icon_offset: ['4%', 0],
  $ctl_text_size: ['72%', 'default'], $ctl_text_anchor: 'right_middle', $ctl_text_offset: ['-2%', 0], $ctl_align: 'left', $ctl_font: 0.8,
};
const SAGE_BTN = {
  ...ICON_LEFT_TEXT, $ctl_bg: `${TEX.sage}button`, $ctl_bg_hover: `${TEX.sage}button_hover`, $ctl_bg_pressed: `${TEX.sage}button_pressed`,
};
// Bloco da tela inicial: ícone grande em cima, nome embaixo.
const SAGE_TILE = {
  $ctl_bg: `${TEX.sage}cat`, $ctl_bg_hover: `${TEX.sage}cat_hover`, $ctl_bg_pressed: `${TEX.sage}cat_hover`,
  $ctl_icon_size: ['100%y', '52%'], $ctl_icon_anchor: 'top_middle', $ctl_icon_offset: [0, '9%'],
  $ctl_text_size: ['94%', 'default'], $ctl_text_anchor: 'bottom_middle', $ctl_text_offset: [0, '-10%'], $ctl_font: 1.0,
};
const SAGE_CAT = {
  $ctl_bg: `${TEX.sage}cat`, $ctl_bg_hover: `${TEX.sage}cat_hover`, $ctl_bg_pressed: `${TEX.sage}cat_hover`,
  $ctl_icon_size: ['100%y', '70%'], $ctl_icon_anchor: 'left_middle', $ctl_icon_offset: ['4%', 0],
  $ctl_text_size: ['74%', 'default'], $ctl_text_anchor: 'right_middle', $ctl_text_offset: ['-3%', 0], $ctl_align: 'left', $ctl_font: 0.78,
};
const SLOT = {
  $ctl_bg: `${TEX.menu}slot`, $ctl_bg_hover: `${TEX.menu}slot_hover`, $ctl_bg_pressed: `${TEX.menu}slot_hover`,
  $ctl_icon_size: ['100%y', '82%'],
};
const PLAIN = { $ctl_bg: `${TEX.menu}none`, $ctl_bg_hover: `${TEX.menu}none`, $ctl_bg_pressed: `${TEX.menu}none`, $ctl_font: 1.1 };

// Coloca o botão `index` do formulário na posição (x,y,w,h) em %.
// O JSON UI liga um elemento ao botão certo pela POSIÇÃO numa grade (grid_position →
// índice = linha*colunas + coluna, como na grade de crafting do jogo). Por isso cada elemento é
// uma grade de 1 linha com index+1 colunas, deslocada para que a coluna `index` caia em (x,y).
function slot(name, index, x, y, w, h, template, extra = {}, childSize = null) {
  const cols = index + 1;
  return { [`g_${name}`]: {
    type: 'grid', ...TL, collection_name: COL, grid_dimensions: [cols, 1], layer: 1,
    size: [pct(cols * w), pct(h)], offset: [pct(x - index * w), pct(y)],
    controls: [{ [`${name}@${NS}.${template}`]: {
      grid_position: [index, 0], ...TL, size: childSize ?? [pct(100 / cols), '100%'], ...extra } }],
  } };
}

function ctl(name, index, x, y, w, h, style = ICON_ONLY, childSize = null) {
  return slot(name, index, x, y, w, h, 'ctl', style, childSize);
}

// ── Árvore ──
// ── Árvore (grade rolável) ──
const TOP = 8;      // barra superior (%)
const BOTTOM = 11;  // barra inferior (%)

/**
 * Uma camada da grade: cada célula fica na grid_position cujo índice é o do seu botão.
 * `firstRow` linhas "fantasmas" acima (índices anteriores) ficam fora da área (deslocadas para cima).
 * `cell(i, c, r, totalRows)` devolve [nome, definição] do filho.
 */
function layer(name, firstRow, cell, extra = {}) {
  const totalRows = firstRow + TREE_ROWS;
  const controls = [];
  for (let r = 0; r < TREE_ROWS; r++) {
    for (let c = 0; c < TREE_COLS; c++) {
      const i = r * TREE_COLS + c;
      const [key, def] = cell(i, c, r, totalRows);
      controls.push({ [key]: { grid_position: [c, firstRow + r], ...TL, size: [pct(100 / TREE_COLS), pct(100 / totalRows)], ...def } });
    }
  }
  return { [`grid_${name}`]: {
    type: 'grid', ...TL, collection_name: COL, grid_dimensions: [TREE_COLS, totalRows], layer: extra.layer ?? 1,
    size: ['100%', pct((totalRows / TREE_ROWS) * 100)], offset: [0, pct((-firstRow / TREE_ROWS) * 100)], controls,
  } };
}

const toggleWatch = (i) => ({ binding_type: 'view', source_control_name: `tg_${i}`, source_property_name: '#toggle_state', target_property_name: '#visible' });

function treeContent() {
  const base = layerBase();
  const s0 = base.startRow;
  const R = TREE_ROWS;
  const lines = layer('lines', s0 + R, (i) => [`p${i}@${NS}.line`, {}], { layer: 1 });
  // Nós: ícone + moldura + toggle do grupo de rádio (seleção instantânea, só no cliente).
  const nodes = layer('nodes', s0, (i) => [`n${i}`, {
    type: 'panel', layer: 2,
    bindings: [colText, view("(not (#form_button_text = ''))", '#visible')],
    controls: [
      { icon: { type: 'image', layer: 3, size: ['100%y', '54%'], bilinear: false, bindings: [
        { ...colTex, binding_name_override: '#texture' }, { ...colTexFs, binding_name_override: '#texture_file_system' }] } },
      { frame: { type: 'image', layer: 4, size: ['100%y', '78%'], bilinear: false, bindings: [
        colText, view(`('${TEX.node}' + #form_button_text)`, '#texture')] } },
      { [`tg_${i}@common.toggle`]: {
        layer: 5, size: ['100%y', '82%'],
        $toggle_name: NODE_TOGGLE_GROUP, $radio_toggle_group: true, $toggle_group_forced_index: i, $toggle_default_state: false,
        $toggle_binding_type: 'none', $toggle_enabled_binding_type: 'none', $toggle_property_bag: {},
        $unchecked_control: `${NS}.tg_off`, $checked_control: `${NS}.tg_on`,
        $unchecked_hover_control: `${NS}.tg_hover`, $checked_hover_control: `${NS}.tg_on`,
        $unchecked_locked_control: `${NS}.tg_off`, $checked_locked_control: `${NS}.tg_on`,
        $unchecked_locked_hover_control: `${NS}.tg_off`, $checked_locked_hover_control: `${NS}.tg_on`,
      } },
    ],
  }], { layer: 2 });
  // Painel de detalhes de cada célula: aparece quando o toggle dela está marcado.
  // O elemento da grade ocupa só a célula; o painel é um FILHO dele, deslocado em relação à
  // célula (offset em % da célula), o que funciona como em qualquer painel comum.
  const relX = (x, c) => pct((x / 100 * TREE_COLS - c) * 100);  // posição x (% do conteúdo) → % da célula
  const relW = (w) => pct(w / 100 * TREE_COLS * 100);
  const details = layer('details', s0 + 2 * R, (i, c, r) => {
    const pl = detailPlacement(c, r);
    return [`d${i}`, {
      type: 'panel', layer: 10,
      controls: [{ panel: {
        type: 'panel', ...TL, layer: 10,
        size: [relW(pl.w), pct(DETAIL_ROWS * 100)], offset: [relX(pl.x, c), pct((pl.row - r) * 100)],
        bindings: [toggleWatch(i)],
        controls: [
          { bg: img(`${TEX.ui}panel`, { layer: 0 }) },
          // Ilustração da habilidade em destaque, com moldura; nome e descrição embaixo.
          { art_frame: { type: 'image', layer: 1, texture: `${TEX.ui}art_frame`, anchor_from: 'top_middle', anchor_to: 'top_middle',
            offset: [0, '3%'], size: ['100%y', '33%'], keep_ratio: false, bilinear: false } },
          { art: { type: 'image', layer: 2, anchor_from: 'top_middle', anchor_to: 'top_middle', offset: [0, '4.2%'], size: ['100%y', '30.6%'],
            bilinear: false, bindings: [{ ...colTex, binding_name_override: '#texture' }, { ...colTexFs, binding_name_override: '#texture_file_system' }] } },
          { txt: { type: 'label', ...TL, offset: ['3%', '38%'], size: ['94%', 'default'], max_size: ['94%', '48%'], layer: 2,
            text: '#form_button_text', color: [1, 1, 1], font_scale_factor: 0.58, shadow: true, bindings: [colText] } },
        ],
      } }],
    }];
  }, { layer: 10 });
  // Botão de ação do painel (desbloquear / barra / ir ao original): a única parte que vai ao script.
  const actions = layer('actions', s0 + 3 * R, (i, c, r) => {
    const pl = detailPlacement(c, r);
    return [`a${i}`, {
      type: 'panel', layer: 12,
      controls: [{ panel: {
        type: 'panel', ...TL, layer: 12,
        size: [relW(pl.w - 4), pct(78)], offset: [relX(pl.x + 2, c), pct((pl.row + DETAIL_ROWS - 0.95 - r) * 100)],
        bindings: [toggleWatch(i)],
        controls: [{ [`b@${NS}.ctl`]: { size: ['100%', '100%'], ...ICON_LEFT_TEXT } }],
      } }],
    }];
  }, { layer: 12 });
  return {
    type: 'panel', ...TL, size: ['100%', `${+((TREE_ROWS / TREE_COLS) * 100).toFixed(4)}%x`],
    controls: [lines, nodes, details, actions],
  };
}

function treeRoot() {
  const area = 100 - TOP - BOTTOM;
  const topBar = {
    type: 'panel', size: ['100%', pct(TOP)], ...TL, layer: 20,
    controls: [
      { bg: img(`${TEX.ui}bar`, { layer: 0 }) },
      { header: { type: 'label', layer: 2, anchor_from: 'left_middle', anchor_to: 'left_middle', offset: ['1%', 0],
        size: ['62%', 'default'], max_size: ['62%', '100%'], shadow: true, font_scale_factor: 0.85, color: [1, 1, 1],
        text: '#tensura_header', bindings: [{ binding_name: '#title_text' }, view(stripTitle(), '#tensura_header')] } },
      // "Fechar detalhes": um toggle do mesmo grupo de rádio (desmarca o nó selecionado, sem ir ao script).
      { tg_none_panel: { type: 'panel', ...at(63.5, 8, 14, 84), controls: [
        { 'tg_none@common.toggle': {
          size: ['100%', '100%'], $toggle_name: NODE_TOGGLE_GROUP, $radio_toggle_group: true,
          $toggle_group_forced_index: TREE_COLS * TREE_ROWS, $toggle_default_state: false,
          $toggle_binding_type: 'none', $toggle_enabled_binding_type: 'none', $toggle_property_bag: {},
          $unchecked_control: `${NS}.close_details_off`, $checked_control: `${NS}.close_details_off`,
          $unchecked_hover_control: `${NS}.close_details_hover`, $checked_hover_control: `${NS}.close_details_hover`,
          $unchecked_locked_control: `${NS}.close_details_off`, $checked_locked_control: `${NS}.close_details_off`,
          $unchecked_locked_hover_control: `${NS}.close_details_off`, $checked_locked_hover_control: `${NS}.close_details_off`,
        } }] } },
      ctl('back', CTRL.back, 78.5, 8, 13, 84, ICON_LEFT_TEXT),
      ctl('close', CTRL.close, 92.3, 8, 7, 84),
    ],
  };
  const catW = 64 / CAT_COUNT;
  const nav = [];
  for (let i = 0; i < CAT_COUNT; i++) nav.push(ctl(`cat${i}`, CTRL.cat0 + i, 0.5 + i * catW, 8, catW - 0.3, 84, { $ctl_icon_size: ['100%y', '78%'] }));
  const navBtns = [['pan_l', CTRL.panLeft], ['page_up', CTRL.pageUp], ['page_down', CTRL.pageDown], ['pan_r', CTRL.panRight], ['center', CTRL.center], ['zoom', CTRL.zoom]];
  const navW = (100 - 65 - 0.5) / navBtns.length;
  navBtns.forEach(([n, idx], k) => nav.push(ctl(n, idx, 65 + k * navW, 8, navW - 0.4, 84)));
  const bottomBar = {
    type: 'panel', size: ['100%', pct(BOTTOM)], anchor_from: 'bottom_left', anchor_to: 'bottom_left', layer: 20,
    controls: [{ bg: img(`${TEX.ui}bar`, { layer: 0 }) }, ...nav],
  };
  return {
    type: 'panel', size: ['100%', '100%'],
    controls: [
      { bg: img(`${TEX.ui}bg`, { layer: 0, bilinear: true }) },
      ...sparkles('tree', 9),
      // Área da árvore: rolagem vertical nativa (arrastar o dedo).
      { tree_area: { type: 'panel', size: ['100%', pct(area)], offset: [0, pct(TOP)], ...TL, layer: 5, controls: [
        { 'scroll@common.scrolling_panel': {
          ...TL, size: ['100%', '100%'], $show_background: false, $allow_scrolling_even_when_content_fits: false,
          $scrolling_content: `${NS}.tree_content`, $scroll_size: [5, '100% - 4px'], $scrolling_pane_size: ['100% - 7px', '100%'],
          $scrolling_pane_offset: [0, 0], $scroll_bar_right_padding_size: [0, 0],
        } }] } },
      { top_bar: topBar }, { bottom_bar: bottomBar },
    ],
  };
}

function menuRoot() {
  const st = (text, x, y, w, extra = {}) => ({ [`t_${Math.round(x * 10)}_${Math.round(y * 10)}@${NS}.static_text`]: {
    $st_text: text, ...TL, offset: [pct(x), pct(y)], size: [pct(w), 'default'], ...extra } });
  const value = (name, idx, x, y, w, extra = {}) => slot(name, idx, x, y, w, 7, 'value', extra);
  const box = (name, x, y, w, h, tex = `${TEX.menu}box`) => ({ [name]: { ...at(x, y, w, h), type: 'image', layer: 1, texture: tex, keep_ratio: false } });
  const isTab = (n) => has(`${MENU_MARK}§${n}`);

  // Página "Personagem": boneco 3D, nome da raça, atributos, habilidades em destaque e barra.
  const charPage = {
    type: 'panel', ...at(23, 12, 76, 86), collection_name: COL,
    bindings: [{ binding_name: '#title_text' }, view(isTab(1), '#visible')],
    controls: [
      box('doll_box', 0, 0, 27, 66),
      { player: { type: 'custom', renderer: 'live_player_renderer', property_bag: { '#look_at_cursor': true },
        // O modelo é desenhado com ~1,9x a altura do controle, a partir do topo (medido no jogo).
        layer: 8, ...at(0, 4, 27, 30), anchor_from: 'top_left', anchor_to: 'top_left' } },
      ctl('race_name', MENU.raceName, 0, 68, 27, 11, { ...PLAIN, $ctl_bg: `${TEX.menu}row`, $ctl_bg_hover: `${TEX.menu}row`, $ctl_bg_pressed: `${TEX.menu}row`, $ctl_font: 1.1 }),
      box('stats_box', 29, 0, 38, 79),
      st('EP:', 31, 5, 16), value('v_ep', MENU.stat0, 45, 5, 20),
      st('Aura:', 31, 15, 16), value('v_aura', MENU.stat0 + 1, 45, 15, 20),
      st('Magículas:', 31, 25, 16), value('v_mp', MENU.stat0 + 2, 45, 25, 20),
      { sep: { ...at(31, 36, 34, 0.6), type: 'image', layer: 2, texture: `${TEX.menu}line`, keep_ratio: false } },
      st('Nível:', 31, 41, 16), value('v_lvl', MENU.stat0 + 3, 45, 41, 20),
      st('Evolução:', 31, 52, 20), value('v_prog', MENU.stat0 + 4, 52, 52, 13),
      slot('bar', MENU.progressBar, 31, 62, 34, 6, 'picture'),
      box('skills_box', 69, 0, 31, 79),
      st('§bEm destaque', 70, 3, 29, { $st_align: 'center', $st_font: 0.95 }),
      ...Array.from({ length: ACTIVE_ROWS }, (_, i) => ctl(`active${i}`, MENU.active0 + i, 70, 11 + i * 16.5, 29, 15, ROW)),
      st('§bBarra de Habilidades', 0, 82, 40, { $st_font: 0.9 }),
      ...Array.from({ length: BAR_SLOTS }, (_, i) => ctl(`slot${i}`, MENU.slot0 + i, 29 + i * 9, 81, 8.5, 18, SLOT, ['100%y', '100%'])),
    ],
  };

  // Páginas de texto (Raça, Inventário, Missões, Status, Evolução, Configurações).
  const textPage = {
    type: 'panel', ...at(23, 12, 76, 86), collection_name: COL,
    bindings: [{ binding_name: '#title_text' }, view(`(not ${isTab(1)})`, '#visible')],
    controls: [
      box('text_box', 0, 0, 100, 76),
      { text_area: { type: 'panel', ...at(1.5, 2, 97, 72), layer: 2, controls: [{ 'scroll@common.scrolling_panel': {
        ...TL, size: ['100%', '100%'], $show_background: false,
        $scrolling_content: `${NS}.details_text`, $scroll_size: [4, '100% - 2px'], $scrolling_pane_size: ['100% - 6px', '100%'],
        $scrolling_pane_offset: [0, 0], $scroll_bar_right_padding_size: [0, 0],
      } }] } },
      ...Array.from({ length: ACTION_COUNT }, (_, i) => ctl(`action${i}`, MENU.action0 + i, (i % 3) * 33.6, 79 + Math.floor(i / 3) * 10.5, 32.6, 9.5, { ...MENU_BTN, $ctl_font: 0.85 })),
    ],
  };

  const side = MENU_TABS.length + 1;
  const sideH = (86 - (side - 1) * 0.8) / side;
  const windowCtrls = [
    { bg: img(`${TEX.menu}window`, { layer: 0 }) },
    // Cabeçalho: título da página + fechar
    { header: { ...at(1, 1.5, 98, 9), type: 'image', layer: 1, texture: `${TEX.menu}tab_bg`, keep_ratio: false } },
    { brand: { type: 'label', ...TL, offset: ['2.5%', '4%'], size: ['24%', 'default'], layer: 3, text: '§l§bTENSURA RPG', shadow: true, font_scale_factor: 1.15 } },
    slot('page_title', MENU.pageTitle, 25, 3.8, 60, 6, 'value', { $v_align: 'left', $v_font: 1.2 }),
    ctl('close', MENU.close, 89, 2.3, 9.5, 7.5, { ...ICON_ONLY, $ctl_bg: `${TEX.menu}button`, $ctl_bg_hover: `${TEX.menu}button_hover`, $ctl_bg_pressed: `${TEX.menu}button_pressed` }),
    // Barra lateral: 8 categorias + Grande Sábio
    ...MENU_TABS.map((t, i) => ctl(`tab_${t}`, MENU.tab0 + i, 1, 12 + i * (sideH + 0.8), 21, sideH, MENU_SIDE)),
    ctl('sage', MENU.sage, 1, 12 + MENU_TABS.length * (sideH + 0.8), 21, sideH, { ...MENU_SIDE, $ctl_bg: `${TEX.sage}button`, $ctl_bg_hover: `${TEX.sage}button_hover`, $ctl_bg_pressed: `${TEX.sage}button_pressed` }),
    { char: charPage },
    { text: textPage },
  ];
  return {
    type: 'panel', size: ['100%', '100%'], collection_name: COL,
    controls: [
      { shade: img(`${TEX.menu}shade`, { layer: 0 }) },
      ...sparkles('menu', 8),
      { window: { type: 'panel', size: ['92%', '92%'], layer: 2, collection_name: COL, controls: windowCtrls,
        anims: [`@${NS}.anim_rise_in`, `@${NS}.anim_open_fade`] } },
    ],
  };
}

// ── Catálogo de Habilidades ──
function catalogRoot() {
  const sideH = (86 - (CATALOG_CATS.length - 1) * 0.8) / CATALOG_CATS.length;
  const tw = (76 - (CATALOG_COLS - 1) * 1) / CATALOG_COLS;
  const th = (74 - (CATALOG_ROWS - 1) * 1.2) / CATALOG_ROWS;
  const tiles = [];
  for (let r = 0; r < CATALOG_ROWS; r++) for (let c = 0; c < CATALOG_COLS; c++) {
    const i = r * CATALOG_COLS + c;
    tiles.push(slot(`tile${i}`, CATALOG.tile0 + i, 23 + c * (tw + 1), 12 + r * (th + 1.2), tw, th, 'cat_tile'));
  }
  const ctrls = [
    { bg: img(`${TEX.menu}window`, { layer: 0 }) },
    { header: { ...at(1, 1.5, 98, 9), type: 'image', layer: 1, texture: `${TEX.menu}tab_bg`, keep_ratio: false } },
    { brand: { type: 'label', ...TL, offset: ['2.5%', '4%'], size: ['20%', 'default'], layer: 3, text: '§l§bCATÁLOGO', shadow: true, font_scale_factor: 1.15 } },
    slot('title', CATALOG.title, 23, 3.8, 62, 6, 'value', { $v_align: 'left', $v_font: 1.05 }),
    ctl('close', CATALOG.close, 89, 2.3, 9.5, 7.5, { ...ICON_ONLY, $ctl_bg: `${TEX.menu}button`, $ctl_bg_hover: `${TEX.menu}button_hover`, $ctl_bg_pressed: `${TEX.menu}button_pressed` }),
    ...CATALOG_CATS.map((c, i) => ctl(`cat_${c}`, CATALOG.cat0 + i, 1, 12 + i * (sideH + 0.8), 21, sideH, MENU_SIDE)),
    ...tiles,
    ctl('prev', CATALOG.prev, 23, 88, 10, 9.5, { ...ICON_ONLY, $ctl_bg: `${TEX.menu}button`, $ctl_bg_hover: `${TEX.menu}button_hover`, $ctl_bg_pressed: `${TEX.menu}button_pressed` }),
    slot('page', CATALOG.page, 34, 90.5, 20, 6, 'value', { $v_align: 'center' }),
    ctl('next', CATALOG.next, 55, 88, 10, 9.5, { ...ICON_ONLY, $ctl_bg: `${TEX.menu}button`, $ctl_bg_hover: `${TEX.menu}button_hover`, $ctl_bg_pressed: `${TEX.menu}button_pressed` }),
    ctl('back', CATALOG.back, 77, 88, 21, 9.5, MENU_BTN),
  ];
  return {
    type: 'panel', size: ['100%', '100%'], collection_name: COL,
    controls: [
      { shade: img(`${TEX.menu}shade`, { layer: 0 }) },
      ...sparkles('catalog', 8),
      { window: { type: 'panel', size: ['94%', '94%'], layer: 2, collection_name: COL, controls: ctrls,
        anims: [`@${NS}.anim_rise_in`, `@${NS}.anim_open_fade`] } },
    ],
  };
}

// ── Painel do Grande Sábio ──
// Tela inicial (§0): grade 3x3 de blocos, barra Configurações e caixa de diálogo do Sábio.
// Páginas (§1..§9): conteúdo com rolagem e botões de ação; "Voltar" leva à tela inicial.
function sageRoot() {
  const box = (name, x, y, w, h, tex = `${TEX.sage}box`) => ({ [name]: { ...at(x, y, w, h), type: 'image', layer: 1, texture: tex, keep_ratio: false, bilinear: false } });
  const val = (name, idx, x, y, w, extra = {}) => slot(name, idx, x, y, w, 6, 'value', { $v_align: 'center', ...extra });
  const scroll = (name, x, y, w, h) => ({ [name]: { type: 'panel', ...at(x, y, w, h), layer: 2, controls: [{ 'scroll@common.scrolling_panel': {
    ...TL, size: ['100%', '100%'], $show_background: false,
    $scrolling_content: `${NS}.details_text`, $scroll_size: [4, '100% - 2px'], $scrolling_pane_size: ['100% - 6px', '100%'],
    $scrolling_pane_offset: [0, 0], $scroll_bar_right_padding_size: [0, 0],
  } }] } });
  const home = `(not ((#title_text - '${SAGE_MARK}§0') = #title_text))`;
  const group = (name, visibleWhen, controls) => ({ [name]: {
    type: 'panel', size: ['100%', '100%'], collection_name: COL,
    bindings: [{ binding_name: '#title_text' }, view(visibleWhen, '#visible')], controls } });
  const tileW = (74.5 - 2 * 1.2) / 3;
  const hub = group('hub', home, [
    ...SAGE_TILES.map((c, i) => ctl(`tile_${c}`, SAGE.tile0 + i, 24 + (i % 3) * (tileW + 1.2), 13.5 + Math.floor(i / 3) * 18, tileW, 17, SAGE_TILE)),
    ctl('settings', SAGE.settings, 36, 68.3, 50.5, 8.5, { ...SAGE_BTN, $ctl_font: 1.0, $ctl_text_size: ['60%', 'default'], $ctl_align: 'center' }),
    box('dialog_box', 24, 78.5, 74.5, 19, `${TEX.sage}header`),
    scroll('dialog', 25.5, 79.8, 71.5, 16.5),
  ]);
  const page = group('page', `(not ${home})`, [
    box('content_box', 24, 13.5, 74.5, 68),
    scroll('content', 25.5, 14.5, 72, 66),
    ...Array.from({ length: SAGE_ACTIONS }, (_, i) => ctl(`action${i}`, SAGE.action0 + i, 24 + (i % 3) * 25.1, 83 + Math.floor(i / 3) * 7.6, 24.4, 7, { ...SAGE_BTN, $ctl_font: 0.8 })),
  ]);
  const ctrls = [
    { bg: img(`${TEX.sage}window`, { layer: 0 }) },
    box('header', 1, 1.5, 98, 10.5, `${TEX.sage}header`),
    slot('emblem', SAGE.emblem, 2, 2.5, 5, 8.5, 'picture', {}, ['100%y', '100%']),
    slot('panel_title', SAGE.panelTitle, 9, 4.4, 76, 6, 'value', { $v_align: 'left', $v_font: 1.25 }),
    ctl('close', SAGE.close, 88.5, 2.5, 9.5, 8.5, { ...ICON_ONLY, $ctl_bg: `${TEX.sage}button`, $ctl_bg_hover: `${TEX.sage}button_hover`, $ctl_bg_pressed: `${TEX.sage}button_pressed` }),
    // Coluna esquerda: núcleo do estágio, nome, nível, XP e barra (todas as telas).
    box('portrait_box', 1.5, 13.5, 21, 45),
    slot('portrait', SAGE.portrait, 2.5, 14.5, 19, 43, 'picture', {}, ['100%y', '100%']),
    val('stage', SAGE.stage, 1.5, 60, 21, { $v_font: 1.1 }),
    val('level', SAGE.level, 1.5, 67, 21),
    val('xp', SAGE.xp, 1.5, 73.5, 21, { $v_font: 0.85 }),
    slot('bar', SAGE.bar, 2.5, 80, 19, 4.5, 'picture'),
    ctl('back', SAGE.back, 1.5, 87, 21, 10, { ...SAGE_BTN, $ctl_font: 0.8 }),
    hub,
    page,
  ];
  return {
    type: 'panel', size: ['100%', '100%'], collection_name: COL,
    controls: [
      { shade: img(`${TEX.menu}shade`, { layer: 0 }) },
      ...sparkles('sage', 8),
      { window: { type: 'panel', size: ['92%', '92%'], layer: 2, collection_name: COL, controls: ctrls,
        anims: [`@${NS}.anim_rise_in`, `@${NS}.anim_open_fade`] } },
    ],
  };
}

// Partículas mágicas discretas no fundo (poucas imagens, animações nativas, sem partículas do mundo).
function sparkles(name, n) {
  const out = [];
  for (let i = 0; i < n; i++) {
    const x = (i * 37 + 11) % 96 + 1;
    const y = (i * 53 + 17) % 88 + 6;
    const s = 0.8 + ((i * 7) % 5) * 0.25;
    out.push({ [`${name}_spark${i}`]: { type: 'panel', ...at(x, y, s, s * 2), layer: 1,
      anims: [`@${NS}.anim_drift_a`],
      controls: [{ s: { type: 'image', texture: `${TEX.ui}spark`, size: ['100%x', '100%x'], bilinear: true,
        anims: [i % 2 ? `@${NS}.anim_spark_a` : `@${NS}.anim_spark2_a`] } }] } });
  }
  return out;
}

// ── Animações (cadeias com "next", como as do próprio jogo) ──
function animations() {
  const loop = (a, b, type, from, to, dur, easing = 'in_out_sine') => ({
    [a]: { anim_type: type, easing, duration: dur, from, to, next: `@${NS}.${b}` },
    [b]: { anim_type: type, easing, duration: dur, from: to, to: from, next: `@${NS}.${a}` },
  });
  return {
    ...loop('anim_float_a', 'anim_float_b', 'offset', [0, 0], [0, '-3%'], 1.6),
    ...loop('anim_float2_a', 'anim_float2_b', 'offset', [0, 0], [0, '-9%'], 2.3),
    ...loop('anim_float3_a', 'anim_float3_b', 'offset', [0, '-6%'], [0, '4%'], 1.9),
    ...loop('anim_pulse_a', 'anim_pulse_b', 'alpha', 0.35, 1.0, 1.1),
    ...loop('anim_glow_a', 'anim_glow_b', 'alpha', 0.55, 0.95, 1.8),
    ...loop('anim_blink_a', 'anim_blink_b', 'alpha', 1.0, 0.15, 0.55, 'linear'),
    anim_fade_in: { anim_type: 'alpha', easing: 'out_cubic', duration: 0.7, from: 0.0, to: 1.0 },
    // Entrada das janelas: sobe um pouco e aparece (sensação de profundidade).
    anim_rise_in: { anim_type: 'offset', easing: 'out_back', duration: 0.45, from: [0, '4%'], to: [0, 0] },
    anim_open_fade: { anim_type: 'alpha', easing: 'out_cubic', duration: 0.35, from: 0.0, to: 1.0 },
    // Linhas: pulso lento de energia.
    ...loop('anim_line_a', 'anim_line_b', 'alpha', 0.72, 1.0, 1.6),
    // Explosão de seleção/desbloqueio (uma vez ao abrir).
    anim_burst_size: { anim_type: 'size', easing: 'out_cubic', duration: 0.8, from: ['100%y', '60%'], to: ['100%y', '170%'] },
    anim_burst_alpha: { anim_type: 'alpha', easing: 'in_quad', duration: 0.9, from: 1.0, to: 0.0 },
    // Partículas mágicas do fundo.
    ...loop('anim_spark_a', 'anim_spark_b', 'alpha', 0.1, 0.9, 1.3, 'in_out_sine'),
    ...loop('anim_spark2_a', 'anim_spark2_b', 'alpha', 0.0, 0.7, 2.1, 'in_out_sine'),
    ...loop('anim_drift_a', 'anim_drift_b', 'offset', [0, 0], [0, '-30%'], 3.2),
    anim_slide_in: { anim_type: 'offset', easing: 'out_cubic', duration: 0.6, from: ['-8%', 0], to: [0, 0] },
  };
}

function raceTemplates() {
  const hasTok = (tok) => `(not ((#form_button_text - '${tok}') = #form_button_text))`;
  return {
    race_card: {
      type: 'panel', ...TL,
      bindings: [colText, colTex, view("(not (#form_button_text = ''))", '#visible')],
      controls: [
        { bg: img(`${TEX.race}card`, { layer: 1 }) },
        { glow: { ...img(`${TEX.race}card_glow`, { layer: 2 }), anims: [`@${NS}.anim_pulse_a`],
          bindings: [colText, view(hasTok(CARD_SELECTED), '#visible')] } },
        { portrait: { type: 'image', layer: 3, size: ['100%y', '54%'], anchor_from: 'top_middle', anchor_to: 'top_middle', offset: [0, '8%'],
          bilinear: false, bindings: [{ ...colTex, binding_name_override: '#texture' }, { ...colTexFs, binding_name_override: '#texture_file_system' }] } },
        { name: { type: 'label', layer: 4, anchor_from: 'bottom_middle', anchor_to: 'bottom_middle', offset: [0, '-5%'],
          size: ['96%', 'default'], max_size: ['96%', '30%'], text_alignment: 'center', shadow: true, color: [1, 1, 1], font_scale_factor: 0.62, text: '#card_name',
          bindings: [colText, view(`((#form_button_text - '${CARD_SELECTED}') - '${CARD_LOCKED}')`, '#card_name')] } },
        { locked: { ...img(`${TEX.race}card_locked`, { layer: 5 }), bindings: [colText, view(hasTok(CARD_LOCKED), '#visible')] } },
        { selected: { ...img(`${TEX.race}card_sel`, { layer: 6 }), bindings: [colText, view(hasTok(CARD_SELECTED), '#visible')] } },
        { 'hit@common.button': { layer: 7, ...buttonBody(), controls: [
          { default: { type: 'panel' } },
          { hover: img(`${TEX.race}card_hover`) },
          { pressed: img(`${TEX.race}card_hover`, { alpha: 0.6 }) },
        ] } },
      ],
    },
    race_text: {
      type: 'label', ...TL, layer: 3, size: ['100%', 'default'], max_size: ['100%', '100%'], text: '#form_button_text', shadow: true,
      color: [1, 1, 1], text_alignment: '$rt_align', font_scale_factor: '$rt_font', bindings: [colText],
      '$rt_align|default': 'left', '$rt_font|default': 0.75,
    },
    race_stars: {
      type: 'panel', ...TL,
      controls: [
        { lbl: { type: 'label', ...TL, size: ['100%', 'default'], text: '#form_button_text', shadow: true, color: [1, 1, 1], font_scale_factor: 0.72, bindings: [colText] } },
        { stars: { type: 'image', anchor_from: 'bottom_left', anchor_to: 'bottom_left', size: ['100%', '50%'], keep_ratio: true, bilinear: false,
          bindings: [{ ...colTex, binding_name_override: '#texture' }, { ...colTexFs, binding_name_override: '#texture_file_system' }] } },
      ],
    },
  };
}

function raceRoot() {
  const stxt = (key, text, x, y, w, font = 1.0, align = 'left', extra = {}) => ({ [key]: {
    type: 'label', ...TL, offset: [pct(x), pct(y)], size: [pct(w), 'default'], text, shadow: true, color: [1, 1, 1],
    font_scale_factor: font, text_alignment: align, layer: 6, ...extra } });
  const GREEN = { $ctl_bg: `${TEX.race}btn_green`, $ctl_bg_hover: `${TEX.race}btn_green_hover`, $ctl_bg_pressed: `${TEX.race}btn_green_pressed`, $ctl_font: 1.15 };
  const STONE = { $ctl_bg: `${TEX.race}btn_stone`, $ctl_bg_hover: `${TEX.race}btn_stone_hover`, $ctl_bg_pressed: `${TEX.race}btn_stone_hover`, $ctl_font: 1.3 };
  const cardW = 50 / RACE_CARD_COLS;
  const cardH = 46 / RACE_CARD_ROWS;
  const cards = [];
  for (let i = 0; i < RACE_CARD_COLS * RACE_CARD_ROWS; i++) {
    const c = i % RACE_CARD_COLS, r = Math.floor(i / RACE_CARD_COLS);
    cards.push(slot(`card${i}`, RACE.card0 + i, 49.2 + c * cardW, 16.5 + r * cardH, cardW - 0.6, cardH - 1, 'race_card'));
  }
  const cube = (k, x, y, s, anim) => ({ [`cube${k}`]: { type: 'image', texture: `${TEX.race}cube`, ...TL, offset: [pct(x), pct(y)],
    size: ['100%y', pct(s)], layer: 4, bilinear: false, alpha: 0.9, anims: [`@${NS}.${anim}`] } });
  return {
    type: 'panel', size: ['100%', '100%'],
    controls: [
      { bg: img(`${TEX.race}bg`, { layer: 0 }) },
      // Grande Sábio (esquerda): brilho pulsante, figura flutuando e cubos mágicos.
      { sage_glow: { type: 'image', texture: `${TEX.race}sage_glow`, ...TL, offset: ['2%', '4%'], size: ['42%', '58%'], layer: 1,
        keep_ratio: false, bilinear: true, anims: [`@${NS}.anim_glow_a`] } },
      { sage_box: { type: 'panel', ...TL, offset: ['10%', '5%'], size: ['26%', '56%'], layer: 3, anims: [`@${NS}.anim_slide_in`],
        controls: [{ sage: { type: 'image', texture: `${TEX.race}sage`, size: ['100%', '100%'], keep_ratio: true, bilinear: false,
          anims: [`@${NS}.anim_float_a`] } }] } },
      cube(1, 6, 14, 7, 'anim_float2_a'), cube(2, 36, 10, 5, 'anim_float3_a'), cube(3, 38, 40, 6, 'anim_float2_b'), cube(4, 5, 44, 5, 'anim_float3_b'),
      // Caixa de diálogo.
      { dialog: { ...img(`${TEX.race}dialog`, { layer: 5 }), ...at(1, 63, 44, 34) } },
      { name_tab: { ...img(`${TEX.race}tab`, { layer: 6 }), ...at(2.5, 58.5, 17, 7.5) } },
      stxt('name', '§bGrande Sábio', 3.5, 60.2, 15, 1.05, 'center', { layer: 7 }),
      slot('sage_text', RACE.sageText, 3.5, 68, 39, 24, 'race_text', { $rt_font: 0.85, layer: 7 }),
      { blink: { type: 'image', texture: `${TEX.race}arrow_down`, ...TL, offset: ['41%', '91%'], size: ['100%y', '3.5%'], layer: 7,
        anims: [`@${NS}.anim_blink_a`] } },
      // Título.
      { title_box: { ...img(`${TEX.race}title`, { layer: 5 }), ...at(52, 1, 44, 9.5) } },
      stxt('title', '§lESCOLHA SUA RAÇA', 52, 3.4, 44, 1.6, 'center'),
      stxt('subtitle', '§7Cada raça possui habilidades únicas, afinidades e caminhos de evolução diferentes.', 48, 11.2, 52, 0.7, 'center'),
      // Cards.
      { cards_bg: { ...img(`${TEX.race}panel`, { layer: 1 }), ...at(48.5, 15.5, 51, 47.5) } },
      ...cards,
      // Painel de informações.
      { info_bg: { ...img(`${TEX.race}panel`, { layer: 1 }), ...at(48.5, 63.5, 51, 27) } },
      ctl('info_portrait', RACE.infoPortrait, 49.5, 65, 8, 13, { $ctl_bg: `${TEX.race}card`, $ctl_bg_hover: `${TEX.race}card`, $ctl_bg_pressed: `${TEX.race}card`, $ctl_icon_size: ['100%y', '86%'] }),
      slot('info_name', RACE.infoName, 58.5, 64.5, 22, 5, 'race_text', { $rt_font: 1.0 }),
      slot('info_desc', RACE.infoDesc, 58.5, 69.5, 22, 10, 'race_text', { $rt_font: 0.62 }),
      slot('info_initial', RACE.infoInitial, 81.5, 64.5, 17.5, 15, 'race_text', { $rt_font: 0.65 }),
      { sep: { ...img(`${TEX.race}line`, { layer: 2 }), ...at(49.5, 79.5, 49, 0.5) } },
      slot('info_aff', RACE.infoAffinity, 49.5, 80.5, 17, 10, 'race_text', { $rt_font: 0.6 }),
      slot('info_evo', RACE.infoEvolutions, 67, 80.5, 18, 10, 'race_text', { $rt_font: 0.6 }),
      slot('info_diff', RACE.infoDifficulty, 85.5, 80.5, 13, 9, 'race_stars'),
      // Botões.
      ctl('confirm', RACE.confirm, 60, 91.3, 28, 8, GREEN),
      ctl('prev', RACE.prev, 54, 91.3, 5, 8, STONE),
      ctl('next', RACE.next, 89, 91.3, 5, 8, STONE),
      slot('page', RACE.page, 94.5, 93.5, 5.5, 5, 'race_text', { $rt_font: 0.55 }),
    ],
  };
}

export function buildUi(outDir = join(ROOT, 'RP', 'ui')) {
  mkdirSync(outDir, { recursive: true });
  const tree = {
    namespace: NS, ...templates(), ...raceTemplates(), ...animations(), root: treeRoot(), menu_root: menuRoot(), race_root: raceRoot(), sage_root: sageRoot(), catalog_root: catalogRoot(),
    details_text: {
      type: 'stack_panel', size: ['100% - 4px', '100%c'], orientation: 'vertical', ...TL,
      controls: [{ body: { type: 'label', size: ['100%', 'default'], ...TL,
        text: '#form_text', color: [1, 1, 1], font_scale_factor: 0.8, shadow: false } }],
    },
  };
  tree.tree_content = treeContent();
  tree.tg_off = { type: 'panel' };
  tree.tg_on = img(`${TEX.ui}selected`, { anims: [`@${NS}.anim_pulse_a`] });
  tree.tg_hover = img(`${TEX.ui}hover`);
  tree.close_details_off = { type: 'panel', controls: [{ bg: img(`${TEX.ui}ctl`) }, { t: { type: 'label', text: '§fFechar detalhes', font_scale_factor: 0.75, shadow: true, layer: 2 } }] };
  tree.close_details_hover = { type: 'panel', controls: [{ bg: img(`${TEX.ui}ctl_hover`) }, { t: { type: 'label', text: '§fFechar detalhes', font_scale_factor: 0.75, shadow: true, layer: 2 } }] };

  const notMarked = `((((((#title_text - '${MARK}') = #title_text) and ((#title_text - '${MENU_MARK}') = #title_text)) and ((#title_text - '${RACE_MARK}') = #title_text)) and ((#title_text - '${SAGE_MARK}') = #title_text)) and ((#title_text - '${CATALOG_MARK}') = #title_text))`;
  const serverForm = {
    namespace: 'server_form',
    main_screen_content: {
      type: 'panel', size: ['100%', '100%'],
      controls: [{ server_form_factory: { type: 'factory', control_ids: {
        long_form: '@server_form.tensura_long_form_router', custom_form: '@server_form.custom_form' } } }],
    },
    // O formulário padrão fica oculto atrás das telas do addon e o jogo cria TODOS os botões dele.
    // Limitamos essa lista (como o chat do jogo faz com max_children_size) para não criar centenas
    // de botões invisíveis a cada abertura. Formulários comuns têm bem menos de 64 botões.
    long_form_dynamic_buttons_panel: {
      factory: { name: 'buttons', max_children_size: 64, control_ids: {
        button: 'server_form.dynamic_button', label: '@server_form.dynamic_label', header: '@server_form.dynamic_header',
        divider: '@settings_common.option_group_section_divider' } },
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
        { tensura_race_wrap: { type: 'panel', size: ['100%', '100%'],
          bindings: [{ binding_name: '#title_text' }, view(has(RACE_MARK), '#visible')],
          controls: [{ [`race@${NS}.race_root`]: {} }] } },
        { tensura_sage_wrap: { type: 'panel', size: ['100%', '100%'],
          bindings: [{ binding_name: '#title_text' }, view(has(SAGE_MARK), '#visible')],
          controls: [{ [`sage@${NS}.sage_root`]: {} }] } },
        { tensura_catalog_wrap: { type: 'panel', size: ['100%', '100%'],
          bindings: [{ binding_name: '#title_text' }, view(has(CATALOG_MARK), '#visible')],
          controls: [{ [`catalog@${NS}.catalog_root`]: {} }] } },
      ],
    },
  };

  const header = '// Gerado por tools/build-ui.mjs — não edite à mão.\n';
  writeFileSync(join(outDir, 'tensura_tree.json'), header + JSON.stringify(tree, null, 1));
  writeFileSync(join(outDir, 'server_form.json'), header + JSON.stringify(serverForm, null, 1));
  writeFileSync(join(outDir, '_ui_defs.json'), JSON.stringify({ ui_defs: ['ui/tensura_tree.json'] }, null, 2));
  let controls = 0;
  for (const g of tree.tree_content.controls) controls += Object.values(g)[0].controls.length;
  return { controls };
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  const r = buildUi();
  console.log(`UI gerada: ${r.controls} controles de grade.`);
}
