#!/usr/bin/env python3
"""Gera todas as texturas do addon proceduralmente (nada copiado de terceiros).

Uso: python3 tools/gen_textures.py <spec.json> <pasta RP>
O spec.json é escrito por tools/build.mjs (lista de habilidades, árvores, códigos).
"""
import json
import math
import os
import random
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

SS = 4  # supersampling para bordas suaves


def font(size):
    for p in ('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf',
              '/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf'):
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def hexc(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mix(c1, c2, t):
    return tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(4))


def canvas(size):
    return Image.new('RGBA', (size * SS, size * SS), (0, 0, 0, 0))


def finish(img, size):
    return img.resize((size, size), Image.LANCZOS)


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, optimize=True)


def glyph(d, kind, cx, cy, r, col, dark):
    w = max(2, int(r * 0.16))
    if kind in ('bolt', 'chain'):
        d.ellipse([cx - r * .35, cy - r * .35, cx + r * .35, cy + r * .35], fill=col)
        for i in range(3):
            o = r * (.45 + i * .18)
            d.line([cx - o, cy + o * .9, cx - r * .25, cy + r * .25], fill=col, width=max(1, w - i * 2))
    elif kind in ('beam', 'strike'):
        d.polygon([(cx - r * .8, cy + r * .8), (cx + r * .55, cy - r * .55), (cx + r * .8, cy - r * .8),
                   (cx + r * .55, cy - r * .2), (cx - r * .65, cy + r * .9)], fill=col)
        d.polygon([(cx + r * .8, cy - r * .8), (cx + r * .2, cy - r * .75), (cx + r * .75, cy - r * .2)], fill=col)
    elif kind in ('cone',):
        d.pieslice([cx - r * 1.4, cy - r * .9, cx + r * .6, cy + r * .9], -35, 35, fill=col)
        d.ellipse([cx - r * .95, cy - r * .2, cx - r * .55, cy + r * .2], fill=dark)
    elif kind in ('nova', 'field', 'gravity'):
        for i, s in enumerate((.85, .58, .3)):
            d.ellipse([cx - r * s, cy - r * s, cx + r * s, cy + r * s], outline=col, width=w if i < 2 else w * 2)
    elif kind in ('rain',):
        for (ox, oy) in ((-.5, -.4), (0, -.1), (.5, -.45), (-.25, .4), (.3, .35)):
            x, y = cx + ox * r, cy + oy * r
            d.polygon([(x, y - r * .3), (x - r * .12, y + r * .1), (x + r * .12, y + r * .1)], fill=col)
    elif kind in ('wall',):
        for row in range(3):
            y0 = cy - r * .7 + row * r * .5
            off = (row % 2) * r * .3
            for c in range(3):
                x0 = cx - r * .85 + off + c * r * .6
                d.rectangle([x0, y0, x0 + r * .5, y0 + r * .4], fill=col)
    elif kind in ('bind', 'possess'):
        for i in range(-2, 3):
            d.line([cx + i * r * .3, cy - r * .8, cx + i * r * .3, cy + r * .8], fill=col, width=w)
        d.line([cx - r * .8, cy - r * .35, cx + r * .8, cy - r * .35], fill=col, width=w)
        d.line([cx - r * .8, cy + r * .35, cx + r * .8, cy + r * .35], fill=col, width=w)
    elif kind in ('buff',):
        d.polygon([(cx, cy - r * .85), (cx + r * .7, cy - r * .55), (cx + r * .55, cy + r * .35), (cx, cy + r * .85),
                   (cx - r * .55, cy + r * .35), (cx - r * .7, cy - r * .55)], fill=col)
        d.polygon([(cx, cy - r * .5), (cx + r * .35, cy - r * .3), (cx, cy + r * .45), (cx - r * .35, cy - r * .3)], fill=dark)
    elif kind in ('heal', 'cleanse'):
        a = r * .25
        d.rectangle([cx - a, cy - r * .75, cx + a, cy + r * .75], fill=col)
        d.rectangle([cx - r * .75, cy - a, cx + r * .75, cy + a], fill=col)
    elif kind in ('debuff', 'dominate', 'marionette'):
        pts = []
        for i in range(60):
            t = i / 59 * math.pi * 4
            rr = r * .85 * (i / 59)
            pts.append((cx + math.cos(t) * rr, cy + math.sin(t) * rr))
        d.line(pts, fill=col, width=w)
    elif kind in ('summon',):
        pts = [(cx + math.cos(math.pi * 2 * i / 5 - math.pi / 2) * r * .8, cy + math.sin(math.pi * 2 * i / 5 - math.pi / 2) * r * .8) for i in range(5)]
        order = [0, 2, 4, 1, 3, 0]
        d.line([pts[i] for i in order], fill=col, width=w)
        d.ellipse([cx - r * .9, cy - r * .9, cx + r * .9, cy + r * .9], outline=col, width=max(1, w // 2))
    elif kind in ('evolve', 'race'):
        d.polygon([(cx - r * .8, cy + r * .5), (cx - r * .8, cy - r * .35), (cx - r * .4, cy + r * .05), (cx, cy - r * .7),
                   (cx + r * .4, cy + r * .05), (cx + r * .8, cy - r * .35), (cx + r * .8, cy + r * .5)], fill=col)
        d.rectangle([cx - r * .8, cy + r * .55, cx + r * .8, cy + r * .75], fill=col)
    elif kind in ('flight',):
        for s in (-1, 1):
            d.polygon([(cx, cy), (cx + s * r * .9, cy - r * .7), (cx + s * r * .75, cy - r * .1), (cx + s * r * .9, cy + r * .2),
                       (cx + s * r * .5, cy + r * .45)], fill=col)
    elif kind in ('blink', 'warp', 'swap', 'streak'):
        pts = []
        for i in range(70):
            t = i / 69 * math.pi * 3
            rr = r * .15 + r * .7 * (i / 69)
            pts.append((cx + math.cos(t) * rr, cy + math.sin(t) * rr))
        d.line(pts, fill=col, width=w)
        d.ellipse([cx - r * .15, cy - r * .15, cx + r * .15, cy + r * .15], fill=col)
    elif kind in ('analyze', 'sense', 'ore_sense', 'comm'):
        d.ellipse([cx - r * .85, cy - r * .45, cx + r * .85, cy + r * .45], fill=col)
        d.ellipse([cx - r * .35, cy - r * .35, cx + r * .35, cy + r * .35], fill=dark)
        d.ellipse([cx - r * .14, cy - r * .14, cx + r * .14, cy + r * .14], fill=col)
    elif kind in ('drain', 'absorb'):
        d.ellipse([cx - r * .5, cy - r * .05, cx + r * .5, cy + r * .85], fill=col)
        d.polygon([(cx, cy - r * .85), (cx - r * .47, cy + r * .3), (cx + r * .47, cy + r * .3)], fill=col)
    elif kind in ('dissolve',):
        for (ox, oy, s) in ((-.3, .2, .45), (.35, -.2, .35), (.1, .55, .25), (-.4, -.45, .2)):
            d.ellipse([cx + ox * r - s * r, cy + oy * r - s * r, cx + ox * r + s * r, cy + oy * r + s * r], fill=col)
    elif kind in ('mimic',):
        d.ellipse([cx - r * .8, cy - r * .3, cx + r * .1, cy + r * .6], fill=col)
        d.ellipse([cx - r * .1, cy - r * .7, cx + r * .8, cy + r * .2], outline=col, width=w)
    elif kind in ('storage', 'create', 'enchant', 'repair'):
        d.rectangle([cx - r * .65, cy - r * .35, cx + r * .65, cy + r * .7], fill=col)
        d.polygon([(cx - r * .75, cy - r * .35), (cx, cy - r * .8), (cx + r * .75, cy - r * .35)], fill=col)
        d.rectangle([cx - r * .15, cy - r * .1, cx + r * .15, cy + r * .45], fill=dark)
    elif kind in ('timeflip',):
        d.ellipse([cx - r * .8, cy - r * .8, cx + r * .8, cy + r * .8], fill=col)
        d.ellipse([cx - r * .4, cy - r * .9, cx + r * 1.0, cy + r * .5], fill=dark)
    elif kind in ('imbue',):
        d.polygon([(cx - r * .7, cy + r * .7), (cx + r * .45, cy - r * .45), (cx + r * .6, cy - r * .3), (cx - r * .55, cy + r * .85)], fill=col)
        d.line([cx + r * .1, cy - r * .7, cx + r * .7, cy - r * .1], fill=col, width=w)
    else:  # passivas: gema
        d.polygon([(cx, cy - r * .85), (cx + r * .7, cy - r * .2), (cx, cy + r * .85), (cx - r * .7, cy - r * .2)], fill=col)
        d.polygon([(cx, cy - r * .45), (cx + r * .3, cy - r * .1), (cx, cy + r * .35), (cx - r * .3, cy - r * .1)], fill=dark)



ELEMENT_COLORS = {}
TREE_COLORS = {
    'elemental': '#ff8a30', 'aspect': '#5aa8ff', 'holy': '#ffe27a', 'summon': '#b878ff', 'illusion': '#ff78d0',
    'spirit': '#50f0b8', 'necromancy': '#9ad070', 'dark': '#8a58ff', 'advanced': '#ff4060', 'racial': '#ffb040',
    'unique': '#40d8ff', 'ultimate': '#ffd000', 'extra': '#78c8ff', 'resist': '#c8c8d0', 'combat': '#e8a060',
}
RARITY_COLORS = {1: '#9aa4b8', 2: '#3fa9ff', 3: '#b860ff', 4: '#ffb020', 5: '#ff3050'}
TAB_GLYPH = {'racial': 'race', 'elemental': 'bolt', 'aspect': 'buff', 'holy': 'heal', 'summon': 'summon', 'illusion': 'debuff',
             'spirit': 'flight', 'necromancy': 'bind', 'dark': 'drain', 'advanced': 'nova', 'unique': 'analyze',
             'ultimate': 'evolve', 'extra': 'blink', 'resist': 'passive', 'combat': 'strike'}


def pixel(img_small, scale):
    return img_small.resize((img_small.width * scale, img_small.height * scale), Image.NEAREST)


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (c[3] if len(c) > 3 else 255,)


# ── Ícones: azulejo quadrado em pixel art (24x24 ampliado 4x) ──
def icon(skill, n=24, scale=4):
    """Cristal facetado na cor da categoria, com o símbolo do efeito. Supremas: núcleo exclusivo."""
    base = hexc(skill['color'])
    S = n * 8
    im = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    c = S / 2
    # fundo: brilho radial escuro na cor da categoria
    for r in range(int(S * 0.72), 0, -int(S * 0.04)):
        k = 1 - r / (S * 0.72)
        d.ellipse([c - r, c - r, c + r, c + r], fill=mix(shade(base, 0.12), shade(base, 0.45), k))
    if skill.get('core'):
        _core_icon(d, c, S, base, skill.get('manas'))
    else:
        top, bot, w = S * 0.10, S * 0.90, S * 0.30
        tip, shoulder = S * 0.10, S * 0.30
        P = {'t': (c, top), 'lt': (c - w, shoulder), 'rt': (c + w, shoulder), 'lb': (c - w, S - shoulder), 'rb': (c + w, S - shoulder), 'b': (c, bot),
             'm': (c, c)}
        light, mid, dark = mix(base, (255, 255, 255, 255), 0.45), base, shade(base, 0.5)
        d.polygon([P['t'], P['lt'], P['m']], fill=mix(base, (255, 255, 255, 255), 0.6))   # faceta superior esquerda (luz)
        d.polygon([P['t'], P['rt'], P['m']], fill=light)
        d.polygon([P['lt'], P['lb'], P['m']], fill=mid)
        d.polygon([P['rt'], P['rb'], P['m']], fill=dark)
        d.polygon([P['lb'], P['b'], P['m']], fill=shade(base, 0.7))
        d.polygon([P['rb'], P['b'], P['m']], fill=shade(base, 0.38))
        d.polygon([P['t'], P['rt'], P['rb'], P['b'], P['lb'], P['lt']], outline=mix(base, (255, 255, 255, 255), 0.7), width=int(S * 0.02))
        d.line([P['t'], (c - w * 0.35, shoulder * 1.1)], fill=(255, 255, 255, 220), width=int(S * 0.03))   # reflexo
        glyph(d, skill['glyph'], c, c * 1.04, S * 0.2, (255, 255, 255, 255), shade(base, 0.25))
    return pixel(im.resize((n, n), Image.LANCZOS), scale)


def _core_icon(d, c, S, base, manas=False):
    """Núcleo exclusivo das Supremas (cruz luminosa + anéis) na cor própria; Ciel (Manas) em losango duplo."""
    light = mix(base, (255, 255, 255, 255), 0.55)
    for r, a in ((0.46, 150), (0.34, 200)):
        d.ellipse([c - S * r, c - S * r * 0.34, c + S * r, c + S * r * 0.34], outline=light[:3] + (a,), width=int(S * 0.035))
    u = S * 0.095
    if manas:
        d.polygon([(c, c - u * 4), (c + u * 3, c), (c, c + u * 4), (c - u * 3, c)], fill=base, outline=light, width=int(S * 0.02))
        d.polygon([(c, c - u * 2.2), (c + u * 1.6, c), (c, c + u * 2.2), (c - u * 1.6, c)], fill=light)
    else:
        for (x0, y0, x1, y1) in ((-3.2, -1, 3.2, 1), (-1, -3.2, 1, 3.2), (-2.2, -2.2, 2.2, 2.2)):
            d.rectangle([c + x0 * u, c + y0 * u, c + x1 * u, c + y1 * u], fill=base)
        d.rectangle([c - 1.5 * u, c - 1.5 * u, c + 1.5 * u, c + 1.5 * u], fill=light)
    d.rectangle([c - 0.7 * u, c - 0.7 * u, c + 0.7 * u, c + 0.7 * u], fill=(255, 255, 255, 255))


# ── Molduras quadradas (32x32 ampliado 2x) ──
def frame(code, n=32, scale=2):
    """Nó circular. Cor do anel = estado (cinza bloqueado, azul disponível, verde desbloqueado,
    vermelho requisito especial pendente); anel interno roxo = habilidade única (lendária),
    dourado = suprema. O anel cobre os cantos do ícone quadrado (o ícone fica redondo)."""
    st, rar, major, link, sel = code[0], int(code[1]), code[2] == 'm', code[3] == 'k', code[4] == 's'
    SS = 8
    N = n * SS
    im = Image.new('RGBA', (N, N), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    STATE = {'l': (86, 90, 104), 'r': (150, 158, 178), 's': (235, 64, 64), 'a': (70, 165, 255), 'u': (70, 225, 115)}
    ring = STATE[st]
    c = N / 2
    R = N * 0.5 - SS * 0.5          # borda externa
    r_in = N * 0.37                  # o ícone aparece dentro deste raio
    glow = st in ('a', 'u', 's')
    if glow:
        for k, a in ((3, 50), (2, 90), (1, 140)):
            d.ellipse([c - R - k * SS * 0.0, c - R, c + R, c + R], outline=ring + (a,), width=int(SS * 1.2))
    # faixa do anel (cobre os cantos do ícone)
    d.ellipse([c - R, c - R, c + R, c + R], fill=(10, 12, 22, 255))
    d.ellipse([c - R, c - R, c + R, c + R], outline=ring + (255,), width=int(SS * (3.2 if major else 2.4)))
    acc = (176, 92, 255) if rar == 4 else (255, 204, 60) if rar == 5 else None
    if acc:
        d.ellipse([c - r_in - SS * 1.7, c - r_in - SS * 1.7, c + r_in + SS * 1.7, c + r_in + SS * 1.7], outline=acc + (255,), width=int(SS * 1.6))
    # janela do ícone (transparente)
    d.ellipse([c - r_in, c - r_in, c + r_in, c + r_in], fill=(0, 0, 0, 0))
    if st in ('l', 'r', 's'):  # véu sobre o ícone
        d.ellipse([c - r_in, c - r_in, c + r_in, c + r_in], fill=(4, 4, 10, 170 if st == 'l' else 110 if st == 's' else 90))
    if major:
        for ang in range(0, 360, 45):
            x = c + (R - SS * 1.2) * math.cos(math.radians(ang))
            y = c + (R - SS * 1.2) * math.sin(math.radians(ang))
            d.ellipse([x - SS * 0.9, y - SS * 0.9, x + SS * 0.9, y + SS * 0.9], fill=mix(ring + (255,), (255, 255, 255, 255), 0.5))
    if link:
        x, y = c + R * 0.62, c + R * 0.62
        d.ellipse([x - SS * 3, y - SS * 3, x + SS * 3, y + SS * 3], fill=(40, 110, 255, 255), outline=(220, 235, 255, 255), width=SS)
    if sel:
        for ang in range(0, 360, 30):
            x = c + (R + 0) * math.cos(math.radians(ang)) * 0.98
            y = c + (R + 0) * math.sin(math.radians(ang)) * 0.98
            d.ellipse([x - SS, y - SS, x + SS, y + SS], fill=(255, 255, 255, 255))
    im = _sphere_shade(im, c, R, r_in, ring)
    small = im.resize((n, n), Image.LANCZOS)
    return pixel(small, scale)


def _sphere_shade(im, c, R, r_in, ring):
    """Iluminação 3D: luz vinda de cima-esquerda no anel (tubo), brilho especular e sombra interna."""
    import numpy as np
    a = np.asarray(im).astype(np.float32)
    N = a.shape[0]
    yy, xx = np.mgrid[0:N, 0:N]
    dx, dy = (xx - c) / R, (yy - c) / R
    dist = np.sqrt(dx * dx + dy * dy)
    band = (dist * R >= r_in) & (dist <= 1.0) & (a[..., 3] > 0)
    # luz direcional + curvatura do tubo (meio do anel mais claro)
    mid = (1.0 + r_in / R) / 2
    tube = 1.0 - np.clip(np.abs(dist - mid) / (1.0 - r_in / R + 1e-6) * 1.6, 0, 1) * 0.45
    light = 1.0 + (-dx - dy) * 0.55
    k = np.where(band, tube * light, 1.0)[..., None]
    a[..., :3] = np.clip(a[..., :3] * k, 0, 255)
    # sombra interna na janela do ícone (parte de cima-esquerda)
    win = dist * R < r_in
    sh = np.clip((-dx - dy) * 0.9 + 0.15, 0, 1) * np.clip((r_in / R - dist) / 0.08, 0, 1)
    sh = np.where(win, np.clip(1 - (dist * R) / r_in, 0, 1) < 0.25, False) * sh
    a[..., 3] = np.where(win & (a[..., 3] < 255), np.maximum(a[..., 3], sh * 150), a[..., 3])
    out = Image.fromarray(a.astype(np.uint8), 'RGBA')
    d = ImageDraw.Draw(out)
    # brilho especular
    hx, hy = c - R * 0.62, c - R * 0.62
    s = R * 0.16
    d.ellipse([hx - s, hy - s * 0.6, hx + s, hy + s * 0.6], fill=(255, 255, 255, 170))
    return out


def node_glow(code, n=48):
    """Halo do nó: cor do estado; aura dourada (suprema) ou roxa (única/lendária). Bloqueado: sem halo."""
    st, rar = code[0], int(code[1])
    im = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    col = {'a': (70, 165, 255), 'u': (70, 225, 115), 's': (235, 64, 64)}.get(st)
    if rar == 5 and st != 'l':
        col = (255, 200, 60)
    elif rar == 4 and st != 'l' and st != 's':
        col = (180, 100, 255)
    if not col:
        return Image.new('RGBA', (1, 1), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for r, a in ((0.48, 40), (0.42, 70), (0.36, 90)):
        d.ellipse([n * (0.5 - r), n * (0.5 - r), n * (0.5 + r), n * (0.5 + r)], fill=col + (a,))
    return im.filter(ImageFilter.GaussianBlur(n * 0.06))


def node_burst(code, n=64):
    """Anel de luz da explosão de seleção/desbloqueio (só para o nó em foco)."""
    if code[4] != 's':
        return Image.new('RGBA', (1, 1), (0, 0, 0, 0))
    im = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    gold = int(code[1]) == 5
    col = (255, 215, 90) if gold else (150, 230, 255)
    d.ellipse([n * 0.08, n * 0.08, n * 0.92, n * 0.92], outline=col + (255,), width=int(n * 0.06))
    for k in range(8):
        import math
        a = k * math.pi / 4
        d.line([n / 2 + math.cos(a) * n * 0.28, n / 2 + math.sin(a) * n * 0.28, n / 2 + math.cos(a) * n * 0.46, n / 2 + math.sin(a) * n * 0.46], fill=col + (230,), width=int(n * 0.04))
    return im.filter(ImageFilter.GaussianBlur(0.6))


def spark(n=16):
    im = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    c = n / 2
    d.ellipse([c - 3, c - 3, c + 3, c + 3], fill=(140, 220, 255, 200))
    d.line([c, 1, c, n - 2], fill=(200, 240, 255, 160)); d.line([1, c, n - 2, c], fill=(200, 240, 255, 160))
    return im.filter(ImageFilter.GaussianBlur(0.5))


def node_shadow(n=64):
    im = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse([n * 0.12, n * 0.55, n * 0.88, n * 0.95], fill=(0, 0, 0, 150))
    return im.filter(ImageFilter.GaussianBlur(n * 0.06))


# ── Linhas: 8 direções (n ne e se s sw w nw), 2 estados ──
DIR8 = [(0, -1), (1, -1), (1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0), (-1, -1)]


def line_tile(code, n=48):
    # code: 8 caracteres (n ne e se s sw w nw): 0 sem linha, 1 apagada, 2 acesa
    im = Image.new('RGBA', (n * SS, n * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    c = n * SS / 2
    dim = (64, 70, 92, 235)
    lit = (150, 225, 255, 255)
    wd = int(n * SS * 0.05)
    wl = int(n * SS * 0.065)
    g = Image.new('RGBA', im.size, (0, 0, 0, 0))
    gd = ImageDraw.Draw(g)
    for ch, (dx, dy) in zip(code, DIR8):
        if ch == '2':
            gd.line([c, c, c + dx * c, c + dy * c], fill=(70, 170, 255, 110), width=wl * 3)
    im.alpha_composite(g.filter(ImageFilter.GaussianBlur(n * SS * 0.03)))
    sh = Image.new('RGBA', im.size, (0, 0, 0, 0))                     # sombra das linhas (profundidade)
    sd = ImageDraw.Draw(sh)
    off = n * SS * 0.03
    for ch, (dx, dy) in zip(code, DIR8):
        if ch != '0':
            sd.line([c + off, c + off * 1.4, c + dx * c + off, c + dy * c + off * 1.4], fill=(0, 0, 0, 120), width=wl)
    im.alpha_composite(sh.filter(ImageFilter.GaussianBlur(n * SS * 0.012)))
    for state in ('1', '2'):  # apagadas por baixo, acesas por cima
        for ch, (dx, dy) in zip(code, DIR8):
            if ch == state:
                col, w = (dim, wd) if state == '1' else (lit, wl)
                dark = tuple(int(v * 0.55) for v in col[:3]) + (col[3],)
                light = tuple(min(255, int(v * 1.35 + 30)) for v in col[:3]) + (col[3],)
                end = (c + dx * c * 1.02, c + dy * c * 1.02)
                d.line([c, c, *end], fill=dark, width=w)                          # borda escura do tubo
                d.line([c, c, *end], fill=col, width=max(1, int(w * 0.7)))
                d.line([c - w * 0.15, c - w * 0.15, end[0] - w * 0.15, end[1] - w * 0.15], fill=light, width=max(1, int(w * 0.25)))  # brilho
    top = '2' if '2' in code else '1'
    w = wl if top == '2' else wd
    d.ellipse([c - w / 2, c - w / 2, c + w / 2, c + w / 2], fill=lit if top == '2' else dim)
    return im.resize((n, n), Image.LANCZOS)


# ── Fundo: paisagem noturna em pixel art (320x180 ampliado 3x) ──
def landscape(W=320, H=180, scale=3):
    rnd = random.Random(11)
    im = Image.new('RGBA', (W, H), (0, 0, 0, 255))
    px = im.load()
    for y in range(H):
        t = y / H
        r = int(14 + 22 * t); g = int(22 + 30 * t); b = int(44 + 38 * t)
        for x in range(W):
            px[x, y] = (r, g, b, 255)
    d = ImageDraw.Draw(im)
    # via láctea
    for _ in range(900):
        u = rnd.random()
        x = int(u * W)
        y = int(10 + u * 60 + rnd.gauss(0, 9))
        if 0 <= y < H:
            c = px[x, y]
            px[x, y] = tuple(min(255, v + rnd.randint(10, 40)) for v in c[:3]) + (255,)
    for _ in range(170):
        x, y = rnd.randrange(W), rnd.randrange(int(H * 0.6))
        v = rnd.randint(150, 255)
        px[x, y] = (v, v, min(255, v + 20), 255)
    # montanhas em camadas
    def ridge(base, amp, col, seed):
        r2 = random.Random(seed)
        pts = []
        y = base
        for x in range(0, W + 8, 8):
            y = max(base - amp, min(base + amp, y + r2.randint(-6, 6)))
            pts.append((x, y))
        d.polygon(pts + [(W, H), (0, H)], fill=col)
    ridge(112, 18, (34, 44, 70, 255), 3)
    ridge(128, 14, (26, 34, 54, 255), 5)
    ridge(146, 10, (18, 24, 40, 255), 9)
    # castelo à direita
    tower = (12, 16, 28, 255)
    d.rectangle([W - 34, 70, W - 14, H], fill=tower)
    d.rectangle([W - 52, 96, W - 30, H], fill=tower)
    for x in range(W - 36, W - 12, 5):
        d.rectangle([x, 66, x + 2, 70], fill=tower)
    for x in range(W - 54, W - 30, 5):
        d.rectangle([x, 92, x + 2, 96], fill=tower)
    for (wx, wy) in ((W - 26, 84), (W - 20, 100), (W - 42, 110)):
        d.rectangle([wx, wy, wx + 1, wy + 2], fill=(255, 190, 90, 255))
    # fogueira e barraca
    d.polygon([(110, 168), (122, 150), (134, 168)], fill=(30, 30, 40, 255))
    glow = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse([140, 150, 176, 176], fill=(255, 140, 40, 90))
    im.alpha_composite(glow.filter(ImageFilter.GaussianBlur(6)))
    d.polygon([(156, 170), (153, 160), (157, 163), (158, 154), (161, 164), (163, 160), (161, 170)], fill=(255, 170, 60, 255))
    # vinheta
    vig = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    vd = ImageDraw.Draw(vig)
    for i in range(14):
        vd.rectangle([i, i, W - 1 - i, H - 1 - i], outline=(0, 0, 0, 120 - i * 8))
    im.alpha_composite(vig)
    return pixel(im, scale)


def box(n, fill, border, inner=None, bw=1):
    im = Image.new('RGBA', (n, n), fill)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, n - 1, n - 1], outline=border, width=bw)
    if inner:
        d.rectangle([bw, bw, n - 1 - bw, n - 1 - bw], outline=inner)
    return im


def ui_icon(name, size=64):
    img = canvas(size)
    d = ImageDraw.Draw(img)
    S = size * SS
    c = S / 2
    W = (235, 240, 255, 255)
    w = int(S * 0.09)
    if name in ('btn_close', 'close'):
        d.line([S * .27, S * .27, S * .73, S * .73], fill=W, width=w)
        d.line([S * .73, S * .27, S * .27, S * .73], fill=W, width=w)
    elif name in ('btn_back', 'back'):
        d.line([S * .22, c, S * .8, c], fill=W, width=w)
        d.line([S * .22, c, S * .45, S * .27], fill=W, width=w)
        d.line([S * .22, c, S * .45, S * .73], fill=W, width=w)
    elif name == 'btn_grimoire':
        d.rectangle([S * .2, S * .18, S * .8, S * .82], fill=(60, 90, 170, 255), outline=(255, 210, 90, 255), width=SS * 3)
        glyph(d, 'summon', S * .52, S * .5, S * .2, (150, 230, 255, 255), (0, 0, 0, 255))
    elif name == 'btn_status':
        d.ellipse([S * .35, S * .15, S * .65, S * .45], fill=W)
        d.pieslice([S * .2, S * .45, S * .8, S * 1.05], 180, 360, fill=W)
    elif name == 'btn_unlock':
        d.rectangle([S * .25, S * .45, S * .75, S * .85], fill=(255, 205, 90, 255))
        d.arc([S * .32, S * .12, S * .68, S * .6], 180, 330, fill=(255, 205, 90, 255), width=w)
    elif name == 'btn_equip':
        glyph(d, 'passive', c, c, S * .4, (120, 220, 255, 255), (0, 0, 0, 255))
    elif name == 'btn_link':
        d.ellipse([S * .12, S * .32, S * .55, S * .68], outline=(140, 190, 255, 255), width=w)
        d.ellipse([S * .45, S * .32, S * .88, S * .68], outline=(140, 190, 255, 255), width=w)
    elif name.startswith('arrow_'):
        dn = name[-1]
        pts = {'u': [(c, S * .22), (S * .78, S * .7), (S * .22, S * .7)], 'd': [(c, S * .78), (S * .78, S * .3), (S * .22, S * .3)],
               'l': [(S * .22, c), (S * .7, S * .22), (S * .7, S * .78)], 'r': [(S * .78, c), (S * .3, S * .22), (S * .3, S * .78)]}[dn]
        d.polygon(pts, fill=W)
    elif name.startswith('zoom_'):
        col = W if not name.endswith('_off') else (110, 110, 130, 255)
        d.ellipse([S * .15, S * .15, S * .65, S * .65], outline=col, width=w)
        d.line([S * .6, S * .6, S * .85, S * .85], fill=col, width=int(w * 1.3))
        d.line([S * .28, S * .4, S * .52, S * .4], fill=col, width=int(w * .8))
        if name.startswith('zoom_in'):
            d.line([S * .4, S * .28, S * .4, S * .52], fill=col, width=int(w * .8))
    elif name == 'center':
        d.ellipse([S * .2, S * .2, S * .8, S * .8], outline=W, width=w)
        d.ellipse([S * .43, S * .43, S * .57, S * .57], fill=W)
    elif name == 'hover':
        d.rectangle([S * .02, S * .02, S * .98, S * .98], outline=(255, 255, 255, 220), width=SS * 4)
    elif name == 'selected':
        L = S * 0.3
        for (x, y, sx, sy) in ((S * .03, S * .03, 1, 1), (S * .97, S * .03, -1, 1), (S * .03, S * .97, 1, -1), (S * .97, S * .97, -1, -1)):
            d.line([x, y, x + L * sx, y], fill=(120, 240, 255, 255), width=SS * 4)
            d.line([x, y, x, y + L * sy], fill=(120, 240, 255, 255), width=SS * 4)
        d.rectangle([S * .08, S * .08, S * .92, S * .92], outline=(120, 240, 255, 120), width=SS * 2)
    elif name == 'icon_slot':
        d.rectangle([S * .02, S * .02, S * .98, S * .98], fill=(10, 12, 22, 230), outline=(80, 200, 255, 255), width=SS * 3)
    return finish(img, size)


# Ícones das abas do menu (estilo da referência: glifo branco/azulado).
def menu_tab_icon(kind, on, size=48):
    img = canvas(size)
    d = ImageDraw.Draw(img)
    S = size * SS
    col = (90, 200, 255, 255) if on else (200, 210, 225, 255)
    if kind == 'status':
        for i, h in enumerate((.35, .55, .75)):
            d.rectangle([S * (.18 + i * .24), S * (.88 - h), S * (.34 + i * .24), S * .88], fill=col)
    elif kind == 'race':
        d.pieslice([S * .18, S * .12, S * .82, S * .9], 180, 360, fill=col)
        d.rectangle([S * .18, S * .5, S * .82, S * .62], fill=col)
        d.rectangle([S * .3, S * .62, S * .42, S * .88], fill=col)
        d.rectangle([S * .58, S * .62, S * .7, S * .88], fill=col)
        d.rectangle([S * .3, S * .3, S * .7, S * .42], fill=(0, 0, 0, 0))
    elif kind == 'skills':
        w = int(S * .08)
        d.line([S * .2, S * .2, S * .8, S * .8], fill=col, width=w)
        d.line([S * .8, S * .2, S * .2, S * .8], fill=col, width=w)
        d.ellipse([S * .4, S * .4, S * .6, S * .6], fill=col)
    elif kind == 'evolution':
        d.polygon([(S * .5, S * .1), (S * .85, S * .5), (S * .62, S * .5), (S * .62, S * .9), (S * .38, S * .9), (S * .38, S * .5), (S * .15, S * .5)], fill=col)
    elif kind == 'char':
        d.ellipse([S * .34, S * .1, S * .66, S * .42], fill=col)
        d.rounded_rectangle([S * .2, S * .46, S * .8, S * .92], radius=S * .14, fill=col)
    elif kind == 'chest':
        d.rectangle([S * .14, S * .4, S * .86, S * .86], fill=col)
        d.pieslice([S * .14, S * .14, S * .86, S * .66], 180, 360, fill=col)
        d.rectangle([S * .44, S * .44, S * .56, S * .6], fill=(30, 40, 60, 255))
        d.line([S * .14, S * .42, S * .86, S * .42], fill=(30, 40, 60, 255), width=SS * 2)
    elif kind == 'records':
        d.rectangle([S * .12, S * .2, S * .48, S * .82], fill=col)
        d.rectangle([S * .52, S * .2, S * .88, S * .82], fill=col)
        for k in range(3):
            y = S * (.34 + k * .14)
            d.line([S * .18, y, S * .42, y], fill=(30, 40, 60, 255), width=SS * 2)
            d.line([S * .58, y, S * .82, y], fill=(30, 40, 60, 255), width=SS * 2)
    return finish(img, size)


def progress_bar(k, steps=20, w=120, h=10):
    im = Image.new('RGBA', (w, h), (10, 14, 24, 255))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, w - 1, h - 1], outline=(90, 110, 140, 255))
    fill = int((w - 4) * k / steps)
    if fill > 0:
        for x in range(fill):
            t = x / max(1, w - 4)
            d.line([2 + x, 2, 2 + x, h - 3], fill=(int(40 + 40 * t), int(160 + 60 * t), 255, 255))
    return im


def tab_icon(tid, size=48):
    img = canvas(size)
    d = ImageDraw.Draw(img)
    S = size * SS
    col = hexc(TREE_COLORS[tid])
    d.rectangle([S * .06, S * .06, S * .94, S * .94], fill=shade(col, 0.25), outline=col, width=SS * 2)
    glyph(d, TAB_GLYPH[tid], S / 2, S / 2, S * .32, mix(col, (255, 255, 255, 255), 0.4), (10, 10, 24, 255))
    return finish(img, size)


def item_tome(size=32):
    img = canvas(size)
    d = ImageDraw.Draw(img)
    S = size * SS
    d.rounded_rectangle([S * .18, S * .1, S * .85, S * .9], radius=S * .06, fill=(40, 70, 160, 255), outline=(255, 210, 90, 255), width=SS * 2)
    d.rectangle([S * .18, S * .1, S * .28, S * .9], fill=(255, 210, 90, 255))
    glyph(d, 'summon', S * .56, S * .5, S * .22, (140, 230, 255, 255), (0, 0, 0, 255))
    return finish(img, size)


def item_crystal(size=32):
    img = canvas(size)
    d = ImageDraw.Draw(img)
    S = size * SS
    d.polygon([(S * .5, S * .06), (S * .8, S * .4), (S * .62, S * .94), (S * .38, S * .94), (S * .2, S * .4)], fill=(90, 200, 255, 255), outline=(230, 250, 255, 255))
    d.polygon([(S * .5, S * .06), (S * .62, S * .4), (S * .5, S * .94), (S * .38, S * .4)], fill=(170, 235, 255, 255))
    return finish(img, size)


def pack_icon(size=256):
    img = Image.new('RGBA', (size, size), (16, 12, 34, 255))
    d = ImageDraw.Draw(img)
    c = size / 2
    for rr, col in ((118, (90, 120, 255, 255)), (100, (60, 80, 200, 255)), (70, (120, 220, 255, 255))):
        d.ellipse([c - rr, c - rr, c + rr, c + rr], outline=col, width=5)
    glyph(d, 'summon', c, c, 90, (140, 230, 255, 255), (16, 12, 34, 255))
    f = font(34)
    d.text((c, size - 30), 'TENSURA RPG', font=f, fill=(255, 210, 90, 255), anchor='mm', stroke_width=3, stroke_fill=(0, 0, 0, 255))
    return img


def sage_icon(kind, on=False, size=48):
    """Ícones do painel do Grande Sábio (glifos pixelados azul-claros)."""
    img = canvas(size)
    d = ImageDraw.Draw(img)
    S = size * SS
    c = S / 2
    TILE_COL = {'ask': (90, 210, 255), 'analyses': (150, 225, 255), 'level': (70, 170, 255), 'skills': (255, 210, 60),
                'magic': (200, 130, 255), 'intel': (110, 240, 230), 'next': (90, 200, 255), 'history': (110, 220, 130),
                'manifest': (130, 220, 255), 'settings': (200, 215, 235), 'missions': (240, 225, 190), 'manas': (220, 160, 255)}
    col = (TILE_COL.get(kind, (120, 230, 255)) + (255,)) if on else (205, 225, 245, 255)
    w = int(S * 0.08)
    if kind == 'missions':
        d.rectangle([S * .22, S * .16, S * .78, S * .84], fill=col)
        d.ellipse([S * .14, S * .1, S * .3, S * .26], fill=col); d.ellipse([S * .7, S * .74, S * .86, S * .9], fill=col)
        for k in range(4):
            y = S * (.3 + k * .13)
            d.line([S * .32, y, S * (.68 if k % 2 else .6), y], fill=(90, 70, 40, 255), width=max(2, w // 2))
    elif kind == 'magic':
        import math
        pts = []
        for i in range(16):
            r = S * (.44 if i % 2 == 0 else .14)
            a = -math.pi / 2 + i * math.pi / 8
            pts.append((c + r * math.cos(a), c + r * math.sin(a)))
        d.polygon(pts, fill=col)
        d.ellipse([c - S * .1, c - S * .1, c + S * .1, c + S * .1], fill=(255, 255, 255, 255))
    elif kind in ('manifest', 'manas'):
        u = S * .1
        for (x0, y0, x1, y1) in ((-3, -1, 3, 1), (-1, -3, 1, 3), (-2, -2, 2, 2)):
            d.rectangle([c + x0 * u, c + y0 * u, c + x1 * u, c + y1 * u], fill=col)
        d.rectangle([c - u, c - u, c + u, c + u], fill=(255, 255, 255, 255))
        d.ellipse([S * .06, S * .38, S * .94, S * .62], outline=col, width=max(2, w // 2))
    elif kind == 'ask':
        d.rounded_rectangle([S * .12, S * .14, S * .88, S * .7], radius=S * .12, outline=col, width=w)
        d.polygon([(S * .3, S * .66), (S * .46, S * .66), (S * .26, S * .88)], fill=col)
        f = font(int(S * .42))
        d.text((c, S * .42), '?', font=f, fill=col, anchor='mm')
    elif kind in ('analyses', 'target'):
        d.ellipse([S * .14, S * .14, S * .62, S * .62], outline=col, width=w)
        d.line([S * .56, S * .56, S * .86, S * .86], fill=col, width=int(w * 1.4))
        if kind == 'target':
            d.ellipse([S * .32, S * .32, S * .44, S * .44], fill=col)
    elif kind == 'level':
        for i, h in enumerate((.3, .5, .7)):
            d.rectangle([S * (.14 + i * .26), S * (.9 - h), S * (.32 + i * .26), S * .9], fill=col)
        d.polygon([(S * .66, S * .08), (S * .9, S * .3), (S * .42, S * .3)], fill=col)
    elif kind in ('skills', 'skill'):
        pts = []
        import math
        for i in range(10):
            r = S * (.42 if i % 2 == 0 else .18)
            a = -math.pi / 2 + i * math.pi / 5
            pts.append((c + r * math.cos(a), c + r * math.sin(a)))
        d.polygon(pts, fill=col)
    elif kind == 'intel':
        d.polygon([(c, S * .1), (S * .88, c), (c, S * .9), (S * .12, c)], outline=col, width=w)
        d.ellipse([S * .36, S * .36, S * .64, S * .64], fill=col)
    elif kind == 'next':
        for y in (.52, .2):
            d.line([S * .18, S * (y + .3), c, S * y, S * .82, S * (y + .3)], fill=col, width=w, joint='curve')
    elif kind == 'history':
        d.ellipse([S * .12, S * .12, S * .88, S * .88], outline=col, width=w)
        d.line([c, c, c, S * .26], fill=col, width=w)
        d.line([c, c, S * .7, S * .6], fill=col, width=w)
    elif kind == 'settings':
        import math
        for i in range(8):
            a = i * math.pi / 4
            d.line([c + S * .2 * math.cos(a), c + S * .2 * math.sin(a), c + S * .42 * math.cos(a), c + S * .42 * math.sin(a)], fill=col, width=int(w * 1.6))
        d.ellipse([S * .24, S * .24, S * .76, S * .76], fill=col)
        d.ellipse([S * .4, S * .4, S * .6, S * .6], fill=(0, 0, 0, 0))
    elif kind == 'examples':
        for i in range(3):
            y = S * (.24 + i * .24)
            d.rectangle([S * .14, y - S * .05, S * .24, y + S * .05], fill=col)
            d.line([S * .32, y, S * .86, y], fill=col, width=w)
    elif kind == 'item':
        d.line([S * .2, S * .8, S * .78, S * .22], fill=col, width=int(w * 1.4))
        d.line([S * .14, S * .6, S * .4, S * .86], fill=col, width=w)
        d.polygon([(S * .7, S * .14), (S * .88, S * .12), (S * .86, S * .3)], fill=col)
    return finish(img, size)


def item_sage_core(size=32):
    img = canvas(size)
    d = ImageDraw.Draw(img)
    S = size * SS
    c = S / 2
    d.ellipse([S * .1, S * .1, S * .9, S * .9], fill=(20, 40, 90, 255), outline=(120, 220, 255, 255), width=SS * 2)
    d.ellipse([S * .28, S * .28, S * .72, S * .72], fill=(90, 200, 255, 255))
    d.ellipse([S * .38, S * .34, S * .52, S * .48], fill=(230, 250, 255, 255))
    d.arc([S * .02, S * .3, S * .98, S * .7], 0, 360, fill=(255, 220, 120, 255), width=SS)
    return finish(img, size)


def _shade(c, k):
    return (max(0, min(255, int(c[0] * k))), max(0, min(255, int(c[1] * k))), max(0, min(255, int(c[2] * k))), c[3])


def raised(face, border, n=24, depth=4, pressed=False):
    """Bloco em relevo (3D): face com degradê, luz em cima/esquerda, sombra e "espessura" embaixo.
    Pressionado: a face desce (sem espessura) e ganha sombra interna no topo."""
    im = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    dep = 1 if pressed else depth
    top = dep - 1 if pressed else 0
    bot = n - 1 - (0 if pressed else 0)
    # espessura (lado do bloco)
    d.rectangle([0, top + 2, n - 1, bot], fill=_shade(border, 0.45))
    d.rectangle([0, top, n - 1, bot - dep], fill=border)                       # contorno
    fh = bot - dep - 2 - (top + 2)
    for y in range(top + 2, bot - dep - 1):                                   # face com degradê vertical
        k = 1.45 - 0.7 * (y - top - 2) / max(1, fh)
        d.line([2, y, n - 3, y], fill=_shade(face, k))
    if pressed:
        d.line([2, top + 2, n - 3, top + 2], fill=_shade(face, 0.45))          # sombra interna
        d.line([2, top + 3, n - 3, top + 3], fill=_shade(face, 0.7))
    else:
        d.line([2, top + 2, n - 3, top + 2], fill=_shade(face, 2.4))           # brilho do topo
        d.line([3, top + 3, n - 4, top + 3], fill=_shade(face, 1.8))
        d.line([2, top + 2, 2, bot - dep - 2], fill=_shade(face, 1.4))         # luz à esquerda
        d.line([n - 3, top + 3, n - 3, bot - dep - 2], fill=_shade(face, 0.7)) # sombra à direita
    d.point([(0, top), (n - 1, top), (0, bot), (n - 1, bot)], fill=(0, 0, 0, 0))  # cantos arredondados
    return im, [4, 4 + top, 4, 4 + dep]


def inset(face, border, n=24):
    """Caixa rebaixada (conteúdo "dentro" da janela): sombra no topo/esquerda, luz embaixo/direita."""
    im = Image.new('RGBA', (n, n), face)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, n - 1, n - 1], outline=border)
    d.line([1, 1, n - 2, 1], fill=_shade(face, 0.35)); d.line([1, 2, n - 2, 2], fill=_shade(face, 0.6))
    d.line([1, 1, 1, n - 2], fill=_shade(face, 0.45))
    d.line([1, n - 2, n - 2, n - 2], fill=_shade(border, 1.3)); d.line([n - 2, 1, n - 2, n - 2], fill=_shade(border, 1.1))
    return im, [4, 4, 4, 4]


def window3d(face, border, glow=None, n=32):
    """Janela com sombra projetada (embaixo/direita), moldura em relevo e brilho interno."""
    im = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle([3, 3, n - 1, n - 1], fill=(0, 0, 0, 120))                    # sombra projetada
    d.rectangle([0, 0, n - 4, n - 4], fill=border)
    d.rectangle([1, 1, n - 5, n - 5], fill=_shade(border, 1.35))              # bisel claro
    d.rectangle([2, 2, n - 6, n - 6], fill=_shade(border, 0.6))               # bisel escuro
    d.rectangle([3, 3, n - 7, n - 7], fill=face)
    if glow:
        d.rectangle([3, 3, n - 7, n - 7], outline=glow[:3] + (150,))
    for y in range(4, 10):                                                     # luz suave no alto da janela
        d.line([4, y, n - 8, y], fill=(150, 200, 255, 10 - (y - 4) * 2))
    return im, [7, 7, 9, 9]


def nine(path, im, n=3):
    if isinstance(im, tuple):
        im, n = im
    save(im, path + '.png')
    with open(path + '.json', 'w') as fh:
        json.dump({'nineslice_size': n, 'base_size': [im.width, im.height]}, fh)


def main():
    spec = json.load(open(sys.argv[1]))
    rp = sys.argv[2]
    bp_icon = sys.argv[3] if len(sys.argv) > 3 else None
    T = os.path.join(rp, 'textures', 'tensura')
    import shutil
    for old in ('icons', 'ui', 'menu'):
        shutil.rmtree(os.path.join(T, old), ignore_errors=True)
    for s in spec['skills']:
        save(icon(s), os.path.join(T, 'icons', s['icon'] + '.png'))
    for code in spec['frames']:
        save(frame(code), os.path.join(T, 'ui', 'node', 'f_' + code + '.png'))
    for code in spec['lines']:
        save(line_tile(code), os.path.join(T, 'ui', 'line', code + '.png'))
    save(landscape(), os.path.join(T, 'ui', 'bg.png'))
    save(node_shadow(), os.path.join(T, 'ui', 'node_shadow.png'))
    save(spark(), os.path.join(T, 'ui', 'spark.png'))
    for code in spec['frames']:
        save(node_glow(code), os.path.join(T, 'ui', 'glow', 'g_' + code + '.png'))
        save(node_burst(code), os.path.join(T, 'ui', 'burst', 'b_' + code + '.png'))
    U = os.path.join(T, 'ui')
    nine(os.path.join(U, 'bar'), window3d((8, 10, 20, 230), (40, 70, 110, 255)))
    nine(os.path.join(U, 'panel'), window3d((10, 12, 24, 238), (40, 120, 180, 255), (60, 170, 230)))
    nine(os.path.join(U, 'ctl'), raised((34, 42, 66, 255), (70, 90, 130, 255)))
    nine(os.path.join(U, 'ctl_hover'), raised((46, 60, 96, 255), (100, 210, 255, 255)))
    nine(os.path.join(U, 'ctl_pressed'), raised((30, 38, 60, 255), (160, 230, 255, 255), pressed=True))
    for n in ('btn_close', 'btn_back', 'btn_grimoire', 'btn_status', 'btn_unlock', 'btn_equip', 'btn_link', 'arrow_l', 'arrow_u', 'arrow_d',
              'arrow_r', 'zoom_in', 'zoom_out', 'zoom_in_off', 'zoom_out_off', 'center', 'hover', 'icon_slot', 'selected'):
        save(ui_icon(n), os.path.join(U, n + '.png'))
    for tid in TAB_GLYPH:
        save(tab_icon(tid), os.path.join(U, 'tab_' + tid + '.png'))
    # Menu principal
    M = os.path.join(T, 'menu')
    nine(os.path.join(M, 'window'), window3d((22, 27, 36, 245), (95, 105, 120, 255)))
    nine(os.path.join(M, 'box'), inset((17, 21, 29, 255), (58, 66, 80, 255)))
    nine(os.path.join(M, 'row'), raised((40, 46, 58, 255), (80, 90, 108, 255), depth=3))
    nine(os.path.join(M, 'row_hover'), raised((48, 58, 78, 255), (90, 200, 255, 255), depth=3))
    nine(os.path.join(M, 'slot'), inset((14, 17, 24, 255), (78, 88, 104, 255)))
    nine(os.path.join(M, 'slot_hover'), inset((20, 26, 36, 255), (90, 200, 255, 255)))
    nine(os.path.join(M, 'button'), raised((52, 60, 74, 255), (110, 120, 136, 255)))
    nine(os.path.join(M, 'button_hover'), raised((62, 74, 94, 255), (90, 200, 255, 255)))
    nine(os.path.join(M, 'button_pressed'), raised((44, 52, 66, 255), (140, 220, 255, 255), pressed=True))
    nine(os.path.join(M, 'tab_bg'), raised((44, 52, 64, 255), (80, 90, 104, 255)))
    nine(os.path.join(M, 'tab_bg_hover'), raised((52, 64, 84, 255), (90, 200, 255, 255)))
    save(Image.new('RGBA', (4, 4), (0, 0, 0, 0)), os.path.join(M, 'none.png'))
    save(Image.new('RGBA', (4, 4), (0, 0, 0, 110)), os.path.join(M, 'shade.png'))
    save(Image.new('RGBA', (4, 2), (60, 70, 86, 255)), os.path.join(M, 'line.png'))
    save(Image.new('RGBA', (4, 2), (60, 190, 255, 255)), os.path.join(M, 'tab_line.png'))
    save(box(16, (14, 17, 24, 255), (60, 68, 82, 255), (24, 28, 38, 255), 2), os.path.join(M, 'slot_empty.png'))
    for k in ('status', 'race', 'skills', 'evolution', 'records'):
        save(menu_tab_icon(k, False), os.path.join(M, f'tab_{k}.png'))
        save(menu_tab_icon(k, True), os.path.join(M, f'tab_{k}_on.png'))
    # Categorias do menu (barra lateral)
    for k, (fn, kind) in {'char': (menu_tab_icon, 'char'), 'race': (menu_tab_icon, 'race'), 'tree': (menu_tab_icon, 'skills'),
                          'inventory': (menu_tab_icon, 'chest'), 'missions': (sage_icon, 'missions'), 'status': (menu_tab_icon, 'status'),
                          'evolution': (menu_tab_icon, 'evolution'), 'settings': (sage_icon, 'settings')}.items():
        save(fn(kind, False), os.path.join(M, f'cat_{k}.png'))
        save(fn(kind, True), os.path.join(M, f'cat_{k}_on.png'))
    save(ui_icon('close', 48), os.path.join(M, 'close.png'))
    save(ui_icon('back', 48), os.path.join(M, 'back.png'))
    for k in range(21):
        save(progress_bar(k), os.path.join(M, f'bar_{k}.png'))
    # Painel do Grande Sábio: azul-escuro com bordas luminosas.
    G = os.path.join(T, 'sage')
    os.makedirs(G, exist_ok=True)
    glow = (80, 200, 255, 255)
    nine(os.path.join(G, 'window'), window3d((10, 16, 36, 248), (50, 140, 230, 255), glow))
    nine(os.path.join(G, 'box'), inset((8, 14, 32, 255), (50, 120, 200, 255)))
    nine(os.path.join(G, 'button'), raised((28, 50, 98, 255), (70, 150, 230, 255)))
    nine(os.path.join(G, 'button_hover'), raised((38, 66, 126, 255), (140, 230, 255, 255)))
    nine(os.path.join(G, 'button_pressed'), raised((24, 42, 84, 255), (180, 240, 255, 255), pressed=True))
    nine(os.path.join(G, 'cat'), raised((22, 38, 76, 255), (60, 120, 200, 255), depth=5))
    nine(os.path.join(G, 'cat_hover'), raised((30, 54, 104, 255), (120, 220, 255, 255), depth=5))
    nine(os.path.join(G, 'header'), raised((22, 40, 86, 255), glow, depth=4))
    for k in ('ask', 'analyses', 'level', 'skills', 'magic', 'intel', 'next', 'history', 'manifest', 'settings', 'missions', 'manas'):
        save(sage_icon(k, False), os.path.join(G, f'cat_{k}.png'))
        save(sage_icon(k, True), os.path.join(G, f'cat_{k}_on.png'))
    for k in ('ask', 'examples', 'target', 'item', 'skill'):
        save(sage_icon(k, True), os.path.join(G, f'btn_{k}.png'))
    save(item_sage_core(), os.path.join(rp, 'textures', 'items', 'tensura_sage_core.png'))
    save(item_tome(), os.path.join(rp, 'textures', 'items', 'tensura_tome.png'))
    save(item_crystal(), os.path.join(rp, 'textures', 'items', 'tensura_crystal.png'))
    pi = pack_icon()
    save(pi, os.path.join(rp, 'pack_icon.png'))
    if bp_icon:
        save(pi, bp_icon)
    print(f"texturas: {len(spec['skills'])} ícones, {len(spec['frames'])} molduras, {len(spec['lines'])} linhas")


if __name__ == '__main__':
    main()
