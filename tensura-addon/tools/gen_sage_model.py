#!/usr/bin/env python3
"""Modelo 3D do Grande Sábio (figura feminina voxelizada) — geometria + textura geradas juntas.

Uso: python3 tools/gen_sage_model.py <pasta RP>

Cada parte define o cubo (origem, tamanho, UV) e a pintura de cada face. Como a geometria e a
textura saem do mesmo lugar, as regiões pintadas caem sempre nas faces certas (box UV do Bedrock).
"""
import json, os, sys
from PIL import Image, ImageDraw

TW, TH = 128, 128

# ── Paleta ──
SKIN = (255, 228, 214, 255); SKIN_S = (240, 200, 188, 255)
HAIR = (168, 222, 255, 255); HAIR_S = (120, 186, 240, 255); HAIR_D = (86, 150, 220, 255); HAIR_L = (222, 246, 255, 255)
EYE = (40, 150, 255, 255); EYE_L = (190, 240, 255, 255); LASH = (22, 30, 60, 255); BLUSH = (255, 180, 190, 255)
WHITE = (242, 246, 255, 255); WHITE_S = (205, 216, 238, 255); WHITE_D = (170, 186, 220, 255)
BLACK = (28, 30, 44, 255); BLACK_S = (44, 48, 68, 255)
BLUE = (60, 170, 255, 255); CYAN = (120, 230, 255, 255)
CLEAR = (0, 0, 0, 0)


def faces(u, v, w, h, d):
    """Regiões da UV em caixa do Bedrock para um cubo (w,h,d) em (u,v)."""
    return {
        'up': (u + d, v, w, d), 'down': (u + d + w, v, w, d),
        'side_r': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
        'side_l': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h),
    }


def fill(im, rect, painter):
    x0, y0, w, h = rect
    px = im.load()
    for y in range(h):
        for x in range(w):
            c = painter(x, y, w, h)
            if c is not None:
                px[x0 + x, y0 + y] = c


def solid(c):
    return lambda x, y, w, h: c


def shaded(c1, c2, every=3):
    return lambda x, y, w, h: c2 if (x + y) % every == 0 else c1


# ── Pinturas por face ──
def head_front(x, y, w, h):
    if y in (4, 5) and x in (1, 2, 5, 6):          # olhos (2x2)
        if y == 4 and x in (1, 5):
            return EYE_L
        return EYE
    if y == 3 and x in (1, 2, 5, 6):
        return LASH
    if y == 6 and x in (1, 6):
        return BLUSH
    if y == 7 and x in (3, 4):
        return SKIN_S
    return SKIN


def hair_full(x, y, w, h):
    if y == 0 or x % 3 == 1:
        return HAIR_S if (x + y) % 4 else HAIR_D
    if (x + 2 * y) % 7 == 0:
        return HAIR_L
    return HAIR


def hair_front(x, y, w, h):
    # franja nas 3 primeiras linhas (bordas recortadas) e mechas emoldurando o rosto
    if y <= 1:
        return hair_full(x, y, w, h)
    if y == 2 and x not in (3,):
        return HAIR
    if y == 3 and x in (0, 2, 5, 7):
        return HAIR_S
    if x in (0, 7):
        return HAIR_S if y % 2 else HAIR
    return CLEAR


def hair_back_paint(x, y, w, h):
    base = hair_full(x, y, w, h)
    if y > h - 4:  # pontas mais escuras e recortadas
        if (x + y) % 3 == 0:
            return CLEAR
        return HAIR_D
    return base


def body_front(x, y, w, h):
    if y == 0 and 2 <= x <= 5:
        return SKIN  # pescoço
    if y == 2 and x in (3, 4):
        return CYAN  # cristal no peito
    if x in (0, 7):
        return BLUE if y % 3 else BLACK_S
    if y in (7, 8):
        return BLUE  # faixa na cintura
    return BLACK if (x + y) % 5 else BLACK_S


def coat_front(x, y, w, h):
    if 2 <= x <= 5:
        return CLEAR  # casaco aberto na frente
    if x in (2 - 1, 6) and y % 2 == 0:
        return BLUE
    return WHITE if (x + y) % 4 else WHITE_S


def coat_back(x, y, w, h):
    if y in (4, 5) and x in (3, 4):
        return BLUE  # emblema
    return WHITE if (x + y) % 4 else WHITE_S


def coat_lower_front(x, y, w, h):
    if 3 <= x <= 5:
        return CLEAR
    if y >= h - 2:
        return BLUE if y == h - 1 else WHITE_D
    return WHITE if (x + y) % 4 else WHITE_S


def coat_lower_side(x, y, w, h):
    if y >= h - 2:
        return BLUE if y == h - 1 else WHITE_D
    return WHITE if (x + y) % 5 else WHITE_S


def arm_paint(x, y, w, h):
    if y >= h - 3:
        return SKIN  # mãos
    return BLACK if y % 4 else BLACK_S


def sleeve_paint(x, y, w, h):
    if y >= h - 3:
        return CLEAR  # mão aparece
    if y in (h - 5, h - 4):
        return BLUE if y == h - 4 else BLACK  # punho
    return WHITE if (x + y) % 4 else WHITE_S


def leg_paint(x, y, w, h):
    if y <= 1:
        return SKIN  # coxa
    if y == 2:
        return BLUE  # faixa da meia
    if y >= h - 2:
        return WHITE if y == h - 1 else WHITE_S  # botas
    return BLACK if (x + y) % 6 else BLACK_S


def strand_paint(x, y, w, h):
    if y > h - 2 and x % 2:
        return CLEAR
    return HAIR if (x + y) % 3 else HAIR_S


def ring(cx, cy, r, thick, col, dashed=False):
    def p(x, y, w, h):
        dx, dy = x - cx + 0.5, y - cy + 0.5
        dist = (dx * dx + dy * dy) ** 0.5
        if abs(dist - r) <= thick / 2:
            if dashed and int((dx + 40) // 3 + (dy + 40) // 3) % 2:
                return None
            return col
        return None
    return p


# ── Partes: (osso, origem, tamanho, uv, pinturas{face: painter} ou 'plane', extras) ──
PARTS = [
    # cabeça
    dict(bone='head', origin=[-4, 24, -4], size=[8, 8, 8], uv=[0, 0],
         paint={'front': head_front, 'up': hair_full, 'side_r': hair_full, 'side_l': hair_full, 'back': hair_full, 'down': solid(SKIN_S)}),
    dict(bone='head', origin=[-4, 24, -4], size=[8, 8, 8], uv=[32, 0], inflate=0.5,
         paint={'front': hair_front, 'up': hair_full, 'side_r': hair_full, 'side_l': hair_full, 'back': hair_full, 'down': solid(CLEAR)}),
    dict(bone='head', origin=[-5, 15, -3], size=[1, 11, 3], uv=[96, 0],
         paint={k: strand_paint for k in ('front', 'back', 'side_r', 'side_l', 'up', 'down')}),
    dict(bone='head', origin=[4, 15, -3], size=[1, 11, 3], uv=[96, 0], mirror=True,
         paint={k: strand_paint for k in ('front', 'back', 'side_r', 'side_l', 'up', 'down')}),
    # cabelo longo (atrás)
    dict(bone='hair_back', origin=[-4.5, 12, 3.5], size=[9, 19, 2], uv=[64, 0],
         paint={k: hair_back_paint for k in ('front', 'back', 'side_r', 'side_l', 'up', 'down')}),
    # tronco e casaco
    dict(bone='body', origin=[-4, 12, -2], size=[8, 12, 4], uv=[0, 16],
         paint={'front': body_front, 'back': solid(BLACK), 'side_r': solid(BLACK_S), 'side_l': solid(BLACK_S), 'up': solid(BLACK), 'down': solid(BLACK)}),
    dict(bone='body', origin=[-4, 12, -2], size=[8, 12, 4], uv=[24, 16], inflate=0.45,
         paint={'front': coat_front, 'back': coat_back, 'side_r': solid(WHITE), 'side_l': solid(WHITE), 'up': solid(WHITE_S), 'down': solid(CLEAR)}),
    dict(bone='coat_lower', origin=[-4.5, 3, -2.5], size=[9, 9, 5], uv=[48, 16],
         paint={'front': coat_lower_front, 'back': coat_lower_side, 'side_r': coat_lower_side, 'side_l': coat_lower_side, 'up': solid(CLEAR), 'down': solid(CLEAR)}),
    # braços (finos) + mangas do casaco
    dict(bone='rightArm', origin=[-7, 12, -2], size=[3, 12, 4], uv=[0, 32], paint={k: arm_paint for k in ('front', 'back', 'side_r', 'side_l', 'up', 'down')}),
    dict(bone='rightArm', origin=[-7, 12, -2], size=[3, 12, 4], uv=[16, 32], inflate=0.5, paint={k: sleeve_paint for k in ('front', 'back', 'side_r', 'side_l', 'up')} | {'down': solid(CLEAR)}),
    dict(bone='leftArm', origin=[4, 12, -2], size=[3, 12, 4], uv=[0, 32], mirror=True, paint={}),
    dict(bone='leftArm', origin=[4, 12, -2], size=[3, 12, 4], uv=[16, 32], inflate=0.5, mirror=True, paint={}),
    # pernas
    dict(bone='rightLeg', origin=[-3.9, 0, -2], size=[4, 12, 4], uv=[32, 32], paint={k: leg_paint for k in ('front', 'back', 'side_r', 'side_l', 'up', 'down')}),
    dict(bone='leftLeg', origin=[-0.1, 0, -2], size=[4, 12, 4], uv=[32, 32], mirror=True, paint={}),
    # anéis mágicos (planos, translúcidos pelo material do render controller)
    dict(bone='ring_halo', origin=[-6, 34, -6], size=[12, 0, 12], plane=[0, 64, 32, 32],
         paint=[ring(16, 16, 13, 2.2, (150, 235, 255, 230)), ring(16, 16, 10, 1, (90, 190, 255, 170), dashed=True)]),
    dict(bone='ring_waist', origin=[-12, 14, -12], size=[24, 0, 24], plane=[32, 64, 32, 32],
         paint=[ring(16, 16, 15, 1.4, (120, 220, 255, 200)), ring(16, 16, 12.5, 1, (80, 170, 255, 140), dashed=True)]),
    dict(bone='ring_base', origin=[-14, -2, -14], size=[28, 0, 28], plane=[64, 64, 32, 32],
         paint=[ring(16, 16, 15, 1.6, (120, 230, 255, 220)), ring(16, 16, 11, 1, (90, 190, 255, 170)),
                ring(16, 16, 6, 1, (150, 240, 255, 150), dashed=True)]),
]

BONES = [
    {'name': 'root', 'pivot': [0, 0, 0]},
    {'name': 'body', 'parent': 'root', 'pivot': [0, 24, 0]},
    {'name': 'head', 'parent': 'body', 'pivot': [0, 24, 0]},
    {'name': 'hair_back', 'parent': 'head', 'pivot': [0, 30, 4]},
    {'name': 'coat_lower', 'parent': 'body', 'pivot': [0, 12, 0]},
    {'name': 'rightArm', 'parent': 'body', 'pivot': [-5, 22, 0]},
    {'name': 'leftArm', 'parent': 'body', 'pivot': [5, 22, 0]},
    {'name': 'rightLeg', 'parent': 'root', 'pivot': [-1.9, 12, 0]},
    {'name': 'leftLeg', 'parent': 'root', 'pivot': [1.9, 12, 0]},
    {'name': 'ring_halo', 'parent': 'head', 'pivot': [0, 34, 0]},
    {'name': 'ring_waist_tilt', 'parent': 'root', 'pivot': [0, 14, 0], 'rotation': [12, 0, -8]},
    {'name': 'ring_waist', 'parent': 'ring_waist_tilt', 'pivot': [0, 14, 0]},
    {'name': 'ring_base', 'parent': 'root', 'pivot': [0, -2, 0]},
]


def build(rp):
    im = Image.new('RGBA', (TW, TH), CLEAR)
    cubes = {b['name']: [] for b in BONES}
    for part in PARTS:
        cube = {'origin': part['origin'], 'size': part['size']}
        if part.get('inflate'):
            cube['inflate'] = part['inflate']
        if part.get('mirror'):
            cube['mirror'] = True
        if 'plane' in part:
            u, v, w, h = part['plane']
            cube['uv'] = {'up': {'uv': [u, v], 'uv_size': [w, h]}, 'down': {'uv': [u, v], 'uv_size': [w, h]}}
            for painter in part['paint']:
                fill(im, (u, v, w, h), painter)
        else:
            cube['uv'] = part['uv']
            w, h, d = part['size']
            rects = faces(part['uv'][0], part['uv'][1], int(w), int(h), int(d))
            for face, painter in part['paint'].items():
                fill(im, rects[face], painter)
        cubes[part['bone']].append(cube)
    bones = []
    for b in BONES:
        bone = dict(b)
        if cubes[b['name']]:
            bone['cubes'] = cubes[b['name']]
        bones.append(bone)
    geo = {
        'format_version': '1.12.0',
        'minecraft:geometry': [{
            'description': {
                'identifier': 'geometry.tensura.great_sage', 'texture_width': TW, 'texture_height': TH,
                'visible_bounds_width': 4, 'visible_bounds_height': 4, 'visible_bounds_offset': [0, 1.5, 0],
            },
            'bones': bones,
        }],
    }
    os.makedirs(os.path.join(rp, 'models', 'entity'), exist_ok=True)
    with open(os.path.join(rp, 'models', 'entity', 'great_sage.geo.json'), 'w') as f:
        json.dump(geo, f, indent=1)
    out = os.path.join(rp, 'textures', 'entity', 'tensura', 'great_sage.png')
    os.makedirs(os.path.dirname(out), exist_ok=True)
    im.save(out, optimize=True)
    # Aparência de cada estágio: mesma geometria, paleta trocada (cabelo, olhos e brilhos).
    variants = {}
    for stage, pal in STAGE_PALETTES.items():
        v = recolor(im, pal)
        v.save(os.path.join(os.path.dirname(out), f'great_sage_{stage}.png'), optimize=True)
        variants[stage] = v
    variants['sage'] = im
    portraits(rp, geo['minecraft:geometry'][0], variants)
    return geo, im


STAGE_PALETTES = {
    # Raphael: dourado (sabedoria). Ciel: prata e violeta. Cores escolhidas pelo addon.
    'raphael': {HAIR: (255, 236, 170, 255), HAIR_S: (240, 200, 110, 255), HAIR_D: (205, 160, 70, 255), HAIR_L: (255, 250, 222, 255),
                EYE: (255, 186, 50, 255), EYE_L: (255, 240, 190, 255), BLUE: (255, 196, 70, 255), CYAN: (255, 232, 150, 255)},
    'ciel': {HAIR: (236, 226, 255, 255), HAIR_S: (205, 190, 245, 255), HAIR_D: (166, 146, 228, 255), HAIR_L: (255, 250, 255, 255),
             EYE: (196, 100, 255, 255), EYE_L: (244, 214, 255, 255), BLUE: (190, 120, 255, 255), CYAN: (240, 186, 255, 255),
             BLACK: (44, 30, 66, 255), BLACK_S: (62, 46, 90, 255)},
}
STAGE_GLOW = {'sage': (90, 200, 255), 'raphael': (255, 200, 90), 'ciel': (210, 140, 255)}


def recolor(im, pal):
    out = im.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            c = px[x, y]
            if c in pal:
                px[x, y] = pal[c]
    return out


def portraits(rp, geo, variants):
    """Retratos do painel (busto de frente) e ícones pequenos, um por estágio."""
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from sage_preview import view, S
    from PIL import ImageFilter
    outdir = os.path.join(rp, 'textures', 'tensura', 'sage')
    os.makedirs(outdir, exist_ok=True)
    for stage, tex in variants.items():
        g = STAGE_GLOW[stage]
        fig = view(geo, tex, 'front', rings=False, bg=(0, 0, 0, 0))
        # busto: da cabeça até a cintura
        bust = fig.crop((11 * S, 0, 35 * S, 21 * S)).resize((120, 105), Image.NEAREST)
        W, H = 128, 128
        pic = Image.new('RGBA', (W, H), (8, 14, 34, 255))
        d = ImageDraw.Draw(pic)
        for r in range(58, 0, -3):
            k = 1 - r / 58
            d.ellipse([W / 2 - r, 50 - r, W / 2 + r, 50 + r], fill=(int(8 + g[0] * .35 * k), int(14 + g[1] * .35 * k), int(34 + g[2] * .35 * k), 255))
        glow = Image.new('RGBA', (W, H), (0, 0, 0, 0))
        ImageDraw.Draw(glow).ellipse([30, 8, 98, 20], outline=g + (220,), width=2)
        pic.alpha_composite(glow.filter(ImageFilter.GaussianBlur(1)))
        pic.alpha_composite(bust, (4, 21))
        d = ImageDraw.Draw(pic)
        d.rectangle([0, 0, W - 1, H - 1], outline=g + (255,), width=2)
        d.rectangle([3, 3, W - 4, H - 4], outline=(g[0] // 2, g[1] // 2, g[2] // 2, 255), width=1)
        pic.save(os.path.join(outdir, f'portrait_{stage}.png'))
        head = fig.crop((16 * S, 1 * S, 30 * S, 13 * S)).resize((42, 36), Image.NEAREST)
        ic = Image.new('RGBA', (48, 48), (0, 0, 0, 0))
        ImageDraw.Draw(ic).ellipse([2, 2, 45, 45], fill=(10, 18, 40, 255), outline=g + (255,), width=2)
        ic.alpha_composite(head, (3, 8))
        ic.save(os.path.join(outdir, f'icon_{stage}.png'))


if __name__ == '__main__':
    geo, _ = build(sys.argv[1])
    n = sum(len(b.get('cubes', [])) for b in geo['minecraft:geometry'][0]['bones'])
    print(f'Grande Sábio: {len(geo["minecraft:geometry"][0]["bones"])} ossos, {n} cubos, textura {TW}x{TH}')
