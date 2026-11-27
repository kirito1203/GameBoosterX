#!/usr/bin/env python3
"""Modelo 3D do Grande Sábio como NÚCLEO MÁGICO (sem corpo humano) — geometria, texturas dos 3
estágios, animações, retratos e ícones gerados juntos.

Uso: python3 tools/gen_sage_model.py <pasta RP>

Partes (ossos):
  core        núcleo em cruz (cubo central + braços + pontas), opaco e sem sombra (ignore_lighting)
  glow_core   casca translúcida em volta do núcleo (brilho)
  ring_top / ring_mid (inclinado) / ring_low   anéis horizontais tracejados que giram em sentidos opostos
  ring_base   círculo mágico no chão
  panel_orbit → panel_a/b/c   painéis holográficos orbitando
  bits        fragmentos flutuantes
Ossos ring*, panel* e glow* usam material translúcido (entity_alphablend).
"""
import json, math, os, sys
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sage_art import PALETTES, portrait, icon  # noqa: E402

TW, TH = 128, 128
CORE_Y = 24  # centro do núcleo (pixels de modelo acima dos pés)


def box_uv(u, v, w, h, d):
    """Regiões do box UV do Bedrock para um cubo w x h x d com UV em (u, v)."""
    return {'up': (u + d, v, w, d), 'down': (u + d + w, v, w, d),
            'east': (u, v + d, d, h), 'north': (u + d, v + d, w, h), 'west': (u + d + w, v + d, d, h), 'south': (u + 2 * d + w, v + d, w, h)}


def cube(origin, size, uv):
    return {'origin': origin, 'size': size, 'uv': uv}


def plane_h(y, s, uv, uvs):
    """Plano horizontal s x s na altura y (só faces de cima e de baixo)."""
    return {'origin': [-s / 2, y, -s / 2], 'size': [s, 0, s],
            'uv': {'up': {'uv': uv, 'uv_size': uvs}, 'down': {'uv': [uv[0], uv[1] + uvs[1]], 'uv_size': [uvs[0], -uvs[1]]}}}


def plane_v(origin, w, h, uv, uvs):
    """Plano vertical w x h voltado para ±Z (faces norte e sul)."""
    return {'origin': origin, 'size': [w, h, 0],
            'uv': {'north': {'uv': uv, 'uv_size': uvs}, 'south': {'uv': [uv[0] + uvs[0], uv[1]], 'uv_size': [uvs[0], uvs[1]]}}}


def geometry():
    y = CORE_Y
    core = [cube([-4, y - 4, -4], [8, 8, 8], [0, 0])]
    for o in ([4, y - 2.5, -2.5], [-9, y - 2.5, -2.5], [-2.5, y + 4, -2.5], [-2.5, y - 9, -2.5], [-2.5, y - 2.5, 4], [-2.5, y - 2.5, -9]):
        core.append(cube(o, [5, 5, 5], [0, 16]))
    for o in ([9, y - 1, -1], [-11, y - 1, -1], [-1, y + 9, -1], [-1, y - 11, -1], [-1, y - 1, 9], [-1, y - 1, -11]):
        core.append(cube(o, [2, 2, 2], [0, 28]))
    glow = [cube([-6, y - 6, -6], [12, 12, 12], [32, 0])]
    bits = [cube([bx, by, bz], [1, 1, 1], [0, 34]) for (bx, by, bz) in
            ((12, 8, 4), (-13, 12, -3), (9, 36, -8), (-8, 40, 7), (4, 4, -12), (-5, 30, -13), (14, 26, 10), (-14, 20, 9))]
    pw, ph, R = 9, 12, 20

    def panel(u):
        return [plane_v([-pw / 2, y - ph / 2, -R], pw, ph, [u, 40], [16, 22])]

    bones = [
        {'name': 'root', 'pivot': [0, 0, 0]},
        {'name': 'core', 'parent': 'root', 'pivot': [0, y, 0], 'cubes': core},
        {'name': 'glow_core', 'parent': 'root', 'pivot': [0, y, 0], 'cubes': glow},
        {'name': 'ring_top', 'parent': 'root', 'pivot': [0, y + 11, 0], 'cubes': [plane_h(y + 11, 24, [0, 64], [32, 32])]},
        {'name': 'ring_mid_tilt', 'parent': 'root', 'pivot': [0, y, 0], 'rotation': [8, 0, -6]},
        {'name': 'ring_mid', 'parent': 'ring_mid_tilt', 'pivot': [0, y, 0], 'cubes': [plane_h(y, 38, [32, 64], [32, 32])]},
        {'name': 'ring_low', 'parent': 'root', 'pivot': [0, y - 11, 0], 'cubes': [plane_h(y - 11, 30, [0, 96], [32, 32])]},
        {'name': 'ring_base', 'parent': 'root', 'pivot': [0, 0.5, 0], 'cubes': [plane_h(0.5, 38, [64, 64], [64, 64])]},
        {'name': 'panel_orbit', 'parent': 'root', 'pivot': [0, y, 0]},
        {'name': 'panel_a', 'parent': 'panel_orbit', 'pivot': [0, y, 0], 'rotation': [0, 60, 0], 'cubes': panel(64)},
        {'name': 'panel_b', 'parent': 'panel_orbit', 'pivot': [0, y, 0], 'rotation': [0, 180, 0], 'cubes': panel(96)},
        {'name': 'panel_c', 'parent': 'panel_orbit', 'pivot': [0, y, 0], 'rotation': [0, 300, 0], 'cubes': panel(64)},
        {'name': 'bits', 'parent': 'root', 'pivot': [0, y, 0], 'cubes': bits},
    ]
    return {
        'format_version': '1.12.0',
        'minecraft:geometry': [{
            'description': {
                'identifier': 'geometry.tensura.great_sage', 'texture_width': TW, 'texture_height': TH,
                'visible_bounds_width': 4, 'visible_bounds_height': 4, 'visible_bounds_offset': [0, 1.5, 0],
            },
            'bones': bones,
        }],
    }


def A(c, a=255):
    return (c[0], c[1], c[2], a)


def texture(stage):
    c, l, dk = PALETTES[stage]
    im = Image.new('RGBA', (TW, TH), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for _, (x, y, w, h) in box_uv(0, 0, 8, 8, 8).items():          # núcleo central: claro com centro branco
        d.rectangle([x, y, x + w - 1, y + h - 1], fill=A(l))
        d.rectangle([x + 1, y + 1, x + w - 2, y + h - 2], fill=(235, 250, 255, 255))
        d.rectangle([x + 2, y + 2, x + w - 3, y + h - 3], fill=(255, 255, 255, 255))
        d.point([(x, y), (x + w - 1, y), (x, y + h - 1), (x + w - 1, y + h - 1)], fill=A(c))
    for _, (x, y, w, h) in box_uv(0, 16, 5, 5, 5).items():         # braços
        d.rectangle([x, y, x + w - 1, y + h - 1], fill=A(c))
        d.rectangle([x + 1, y + 1, x + w - 2, y + h - 2], fill=A(l))
        d.point((x + w // 2, y + h // 2), fill=(255, 255, 255, 255))
    for _, (x, y, w, h) in box_uv(0, 28, 2, 2, 2).items():         # pontas
        d.rectangle([x, y, x + w - 1, y + h - 1], fill=A(l))
    for _, (x, y, w, h) in box_uv(0, 34, 1, 1, 1).items():         # fragmentos
        d.rectangle([x, y, x + w - 1, y + h - 1], fill=A(l))
    for _, (x, y, w, h) in box_uv(32, 0, 12, 12, 12).items():      # casca de brilho translúcida
        d.rectangle([x, y, x + w - 1, y + h - 1], fill=A(c, 70), outline=A(l, 120))

    def ring(x0, y0, n, dashes, inner):
        S = n * 8
        r = Image.new('RGBA', (S, S), (0, 0, 0, 0))
        g = ImageDraw.Draw(r)
        g.ellipse([12, 12, S - 13, S - 13], outline=A(c, 110), width=8)
        for i in range(dashes):
            a0 = 360 * i / dashes
            g.arc([3, 3, S - 4, S - 4], a0, a0 + 360 / dashes * 0.6, fill=A(l, 240), width=12)
        if inner:
            g.ellipse([S * .22, S * .22, S * .78, S * .78], outline=A(l, 150), width=6)
        im.alpha_composite(r.resize((n, n), Image.NEAREST), (x0, y0))

    ring(0, 64, 32, 14, False)
    ring(32, 64, 32, 20, True)
    ring(0, 96, 32, 16, False)
    S = 256                                                          # círculo mágico da base
    m = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    g = ImageDraw.Draw(m)
    g.ellipse([4, 4, S - 5, S - 5], outline=A(l, 230), width=8)
    g.ellipse([30, 30, S - 31, S - 31], outline=A(c, 200), width=6)
    g.ellipse([70, 70, S - 71, S - 71], outline=A(l, 180), width=5)
    pts = [(S / 2 + 96 * math.cos(math.radians(90 + k * 60)), S / 2 + 96 * math.sin(math.radians(90 + k * 60))) for k in range(6)]
    g.polygon(pts[0::2], outline=A(c, 200), width=4)
    g.polygon(pts[1::2], outline=A(c, 200), width=4)
    for k in range(24):
        a = math.radians(k * 15)
        g.line([S / 2 + 104 * math.cos(a), S / 2 + 104 * math.sin(a), S / 2 + 116 * math.cos(a), S / 2 + 116 * math.sin(a)], fill=A(l, 220), width=5)
    im.alpha_composite(m.resize((64, 64), Image.NEAREST), (64, 64))
    for x0, seed in ((64, 0), (96, 1)):                              # painéis 16x22 (frente e verso)
        for half in (0, 16):
            x = x0 + half
            d.rectangle([x, 40, x + 15, 61], fill=A(dk, 120), outline=A(l, 230))
            d.rectangle([x + 2, 42, x + 13, 45], fill=A(c, 200))
            for k in range(5):
                yy = 48 + k * 3
                d.line([x + 2, yy, x + (9 if (k + seed) % 2 else 13), yy], fill=A(l, 200))
    return im


def animations():
    lt = 'query.life_time'
    return {
        'format_version': '1.8.0',
        'animations': {
            'animation.tensura.great_sage.float': {
                'loop': True,
                'bones': {
                    'root': {'position': [0, f'2 + math.sin({lt} * 110) * 1.0', 0]},
                    'core': {'rotation': [0, f'{lt} * 40', 0], 'scale': f'1 + math.sin({lt} * 200) * 0.03'},
                    'glow_core': {'rotation': [f'{lt} * 25', f'-{lt} * 30', 0], 'scale': f'1 + math.sin({lt} * 200) * 0.08'},
                    'ring_top': {'rotation': [0, f'{lt} * 70', 0]},
                    'ring_mid': {'rotation': [0, f'-{lt} * 50', 0]},
                    'ring_low': {'rotation': [0, f'{lt} * 60', 0]},
                    'ring_base': {'rotation': [0, f'-{lt} * 20', 0], 'scale': f'1 + math.sin({lt} * 150) * 0.05'},
                    'panel_orbit': {'rotation': [0, f'{lt} * 25', 0]},
                    'panel_a': {'position': [0, f'math.sin({lt} * 120) * 1.2', 0]},
                    'panel_b': {'position': [0, f'math.sin({lt} * 120 + 120) * 1.2', 0]},
                    'panel_c': {'position': [0, f'math.sin({lt} * 120 + 240) * 1.2', 0]},
                    'bits': {'rotation': [0, f'-{lt} * 35', 0], 'position': [0, f'math.sin({lt} * 90) * 1.5', 0]},
                },
            },
            'animation.tensura.great_sage.talk': {
                'loop': True,
                'bones': {
                    'core': {'scale': f'1 + math.sin({lt} * 600) * 0.07'},
                    'glow_core': {'scale': f'1.1 + math.sin({lt} * 600) * 0.12'},
                    'ring_top': {'rotation': [0, f'{lt} * 90', 0]},
                    'ring_low': {'rotation': [0, f'-{lt} * 70', 0]},
                },
            },
            'animation.tensura.great_sage.appear': {
                'animation_length': 1.2,
                'loop': 'hold_on_last_frame',
                'bones': {
                    'root': {'scale': {'0.0': [0.01, 0.01, 0.01], '0.8': [1.1, 1.1, 1.1], '1.2': [1, 1, 1]},
                             'position': {'0.0': [0, -12, 0], '1.2': [0, 0, 0]}},
                    'ring_base': {'scale': {'0.0': [2.5, 2.5, 2.5], '1.0': [1, 1, 1]}},
                    'panel_orbit': {'scale': {'0.0': [0.01, 0.01, 0.01], '0.6': [0.01, 0.01, 0.01], '1.2': [1, 1, 1]}},
                },
            },
            'animation.tensura.great_sage.vanish': {
                'animation_length': 0.8,
                'loop': 'hold_on_last_frame',
                'bones': {
                    'root': {'scale': {'0.0': [1, 1, 1], '0.25': [1.15, 1.15, 1.15], '0.8': [0.01, 0.01, 0.01]},
                             'position': {'0.0': [0, 0, 0], '0.8': [0, 18, 0]}},
                    'core': {'rotation': {'0.0': [0, 0, 0], '0.8': [0, 540, 0]}},
                    'ring_base': {'scale': {'0.0': [1, 1, 1], '0.8': [3, 3, 3]}},
                },
            },
        },
    }


def build(rp):
    geo = geometry()
    os.makedirs(os.path.join(rp, 'models', 'entity'), exist_ok=True)
    with open(os.path.join(rp, 'models', 'entity', 'great_sage.geo.json'), 'w') as f:
        json.dump(geo, f, indent=1)
    os.makedirs(os.path.join(rp, 'animations'), exist_ok=True)
    with open(os.path.join(rp, 'animations', 'great_sage.animation.json'), 'w') as f:
        json.dump(animations(), f, indent=2)
    tdir = os.path.join(rp, 'textures', 'entity', 'tensura')
    os.makedirs(tdir, exist_ok=True)
    for stage in PALETTES:
        name = 'great_sage.png' if stage == 'sage' else f'great_sage_{stage}.png'
        texture(stage).save(os.path.join(tdir, name), optimize=True)
    udir = os.path.join(rp, 'textures', 'tensura', 'sage')
    os.makedirs(udir, exist_ok=True)
    for stage in PALETTES:
        portrait(stage).save(os.path.join(udir, f'portrait_{stage}.png'))
        icon(stage).save(os.path.join(udir, f'icon_{stage}.png'))
    return geo


if __name__ == '__main__':
    geo = build(sys.argv[1])
    bones = geo['minecraft:geometry'][0]['bones']
    n = sum(len(b.get('cubes', [])) for b in bones)
    print(f'Grande Sábio (núcleo mágico): {len(bones)} ossos, {n} cubos, textura {TW}x{TH}, 3 estágios')
