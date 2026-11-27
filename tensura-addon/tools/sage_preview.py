#!/usr/bin/env python3
"""Pré-visualização ortográfica (frente, lado e costas) do modelo do Grande Sábio.

Lê a geometria e a textura GERADAS e desenha cada cubo com a região de UV de cada face
(mesma regra de box UV do Bedrock). Serve para conferir proporções e se a pintura cai nas
faces certas. Não substitui o teste no jogo (sem iluminação, sem animação).
Uso: python3 tools/sage_preview.py <pasta RP> <saida.png>
"""
import json, sys
from PIL import Image, ImageDraw

rp, out = sys.argv[1], sys.argv[2]
geo = json.load(open(f'{rp}/models/entity/great_sage.geo.json'))['minecraft:geometry'][0]
tex = Image.open(f'{rp}/textures/entity/tensura/great_sage.png').convert('RGBA')
S = 10  # pixels por unidade


def regions(u, v, w, h, d):
    return {'side_r': (u, v + d, d, h), 'front': (u + d, v + d, w, h), 'side_l': (u + d + w, v + d, d, h),
            'back': (u + 2 * d + w, v + d, w, h)}


def view(face, flip_x):
    W, H = 46 * S, 42 * S
    im = Image.new('RGBA', (W, H), (14, 20, 40, 255))
    cubes = []
    for b in geo['bones']:
        for c in b.get('cubes', []):
            if 0 in c['size'] or isinstance(c['uv'], dict):
                continue
            cubes.append(c)
    # O rosto fica em -Z (como no jogador do jogo: a capa fica em +Z). Desenha do mais distante ao mais próximo.
    def depth(c):
        o, s = c['origin'], c['size']
        if face == 'front': return -o[2]            # observador em -Z: quem tem z menor fica na frente
        if face == 'back': return o[2]              # observador em +Z: menor z primeiro
        return o[0] + s[0]                          # observador em -X
    for c in sorted(cubes, key=depth):
        o, s = c['origin'], c['size']
        inf = c.get('inflate', 0)
        w, h, d = int(s[0]), int(s[1]), int(s[2])
        r = regions(c['uv'][0], c['uv'][1], w, h, d)
        if face in ('front', 'back'):
            rect = r[face]
            x0, ww = o[0] - inf, s[0] + 2 * inf
        else:
            rect = r['side_r']
            x0, ww = o[2] - inf, s[2] + 2 * inf
        crop = tex.crop((rect[0], rect[1], rect[0] + rect[2], rect[1] + rect[3]))
        if c.get('mirror') or flip_x:
            crop = crop.transpose(Image.FLIP_LEFT_RIGHT)
        sw, shh = max(1, int(ww * S)), max(1, int((s[1] + 2 * inf) * S))
        crop = crop.resize((sw, shh), Image.NEAREST)
        sx = int((x0 + 23) * S) if face != 'back' else int((23 - (x0 + ww)) * S)
        sy = int((38 - (o[1] + s[1] + inf)) * S)
        im.alpha_composite(crop, (sx, sy))
    d = ImageDraw.Draw(im)
    for (y, r, col) in ((34, 6, (150, 235, 255, 200)), (14, 12, (120, 220, 255, 160)), (-2, 14, (120, 230, 255, 200))):
        d.line([(23 - r) * S, (38 - y) * S, (23 + r) * S, (38 - y) * S], fill=col, width=3)
    return im


sheet = Image.new('RGBA', (46 * S * 3 + 20, 42 * S), (0, 0, 0, 255))
for i, (f, fl) in enumerate((('front', False), ('side', False), ('back', False))):
    sheet.alpha_composite(view(f, fl), (i * (46 * S + 10), 0))
sheet.save(out)
print(out)
