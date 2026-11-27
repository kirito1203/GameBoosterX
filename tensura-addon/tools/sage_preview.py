#!/usr/bin/env python3
"""Pré-visualização 3D (projeção ortográfica em 3/4) do modelo do Grande Sábio.

Lê a geometria e as texturas GERADAS e desenha cada face com a região de UV correspondente
(box UV e UV por face), aplicando as rotações de repouso dos ossos. Serve para conferir forma,
UVs e cores dos 3 estágios. Não substitui o teste no jogo (sem iluminação real, sem animação).
Uso: python3 tools/sage_preview.py <pasta RP> <saida.png>
"""
import json, math, sys
import numpy as np
from PIL import Image, ImageDraw

S = 9  # pixels por unidade do modelo


def box_uv(u, v, w, h, d):
    return {'up': (u + d, v, w, d), 'down': (u + d + w, v, w, d), 'east': (u, v + d, d, h), 'north': (u + d, v + d, w, h),
            'west': (u + d + w, v + d, d, h), 'south': (u + 2 * d + w, v + d, w, h)}


def corners(face, o, s):
    x0, y0, z0 = o
    x1, y1, z1 = o[0] + s[0], o[1] + s[1], o[2] + s[2]
    return {
        'north': [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        'south': [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        'east': [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        'west': [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        'up': [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        'down': [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }[face]


def rot_matrix(r):
    ax, ay, az = (math.radians(a) for a in r)
    rx = np.array([[1, 0, 0], [0, math.cos(ax), -math.sin(ax)], [0, math.sin(ax), math.cos(ax)]])
    ry = np.array([[math.cos(ay), 0, math.sin(ay)], [0, 1, 0], [-math.sin(ay), 0, math.cos(ay)]])
    rz = np.array([[math.cos(az), -math.sin(az), 0], [math.sin(az), math.cos(az), 0], [0, 0, 1]])
    return rz @ ry @ rx


def coeffs(dst, src):
    """Coeficientes de Image.PERSPECTIVE que levam cada ponto de saída (dst) ao ponto da textura (src)."""
    m = []
    for (x, y), (u, v) in zip(dst, src):
        m.append([x, y, 1, 0, 0, 0, -u * x, -u * y])
        m.append([0, 0, 0, x, y, 1, -v * x, -v * y])
    return np.linalg.solve(np.array(m, dtype=float), np.array(src, dtype=float).reshape(8)).tolist()


def render(geo, tex, yaw=-35, pitch=22, W=46, H=48, bg=(10, 16, 34, 255)):
    bones = {b['name']: b for b in geo['bones']}

    def world(name, p):
        p = np.array(p, dtype=float)
        while name:
            b = bones[name]
            if b.get('rotation'):
                pv = np.array(b['pivot'], dtype=float)
                p = rot_matrix(b['rotation']) @ (p - pv) + pv
            name = b.get('parent')
        return p

    view = rot_matrix([pitch, 0, 0]) @ rot_matrix([0, yaw, 0])
    faces = []
    for b in geo['bones']:
        for c in b.get('cubes', []):
            o, s = c['origin'], c['size']
            if isinstance(c['uv'], list):
                uvs = box_uv(c['uv'][0], c['uv'][1], int(s[0]), int(s[1]), int(s[2]))
            else:
                uvs = {f: (v['uv'][0], v['uv'][1], v['uv_size'][0], v['uv_size'][1]) for f, v in c['uv'].items()}
            for f, (u, v, w, h) in uvs.items():
                if w == 0 or h == 0:
                    continue
                pts = [view @ world(b['name'], p) for p in corners(f, o, s)]
                faces.append((sum(p[2] for p in pts) / 4, pts, (u, v, w, h)))
    im = Image.new('RGBA', (W * S, H * S), bg)
    for _, pts, (u, v, w, h) in sorted(faces, key=lambda f: -f[0]):
        dst = [((p[0] + W / 2) * S, (H - 4 - p[1]) * S) for p in pts]
        src = [(u, v), (u + w, v), (u + w, v + h), (u, v + h)]
        try:
            layer = tex.transform(im.size, Image.PERSPECTIVE, coeffs(dst, src), Image.NEAREST)
        except np.linalg.LinAlgError:
            continue
        mask = Image.new('L', im.size, 0)
        ImageDraw.Draw(mask).polygon(dst, fill=255)
        layer.putalpha(Image.composite(layer.getchannel('A'), mask, mask))
        im.alpha_composite(layer)
    return im


if __name__ == '__main__':
    rp, out = sys.argv[1], sys.argv[2]
    geo = json.load(open(f'{rp}/models/entity/great_sage.geo.json'))['minecraft:geometry'][0]
    names = ['great_sage', 'great_sage_raphael', 'great_sage_ciel']
    views = [render(geo, Image.open(f'{rp}/textures/entity/tensura/{n}.png').convert('RGBA')) for n in names]
    views.append(render(geo, Image.open(f'{rp}/textures/entity/tensura/great_sage.png').convert('RGBA'), yaw=0, pitch=4))
    sheet = Image.new('RGBA', (sum(v.width for v in views) + 10 * len(views), views[0].height), (0, 0, 0, 255))
    x = 0
    for v in views:
        sheet.alpha_composite(v, (x, 0))
        x += v.width + 10
    sheet.save(out)
    print(out)
