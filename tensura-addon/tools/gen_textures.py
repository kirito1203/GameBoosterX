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
    'unique': '#40d8ff', 'ultimate': '#ffd000', 'extra': '#78c8ff', 'resist': '#c8c8d0',
}
RARITY_COLORS = {1: '#9aa4b8', 2: '#3fa9ff', 3: '#b860ff', 4: '#ffb020', 5: '#ff3050'}
TAB_GLYPH = {'racial': 'race', 'elemental': 'bolt', 'aspect': 'buff', 'holy': 'heal', 'summon': 'summon', 'illusion': 'debuff',
             'spirit': 'flight', 'necromancy': 'bind', 'dark': 'drain', 'advanced': 'nova', 'unique': 'analyze',
             'ultimate': 'evolve', 'extra': 'blink', 'resist': 'passive'}


def pixel(img_small, scale):
    return img_small.resize((img_small.width * scale, img_small.height * scale), Image.NEAREST)


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (c[3] if len(c) > 3 else 255,)


# ── Ícones: azulejo quadrado em pixel art (24x24 ampliado 4x) ──
def icon(skill, n=24, scale=4):
    base = hexc(skill['color'])
    im = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, n - 1, n - 1], fill=shade(base, 0.22))
    for i in range(n // 2):  # gradiente em degraus
        d.rectangle([i, i, n - 1 - i, n - 1 - i], fill=shade(base, 0.22 + 0.025 * i))
    light = mix(base, (255, 255, 255, 255), 0.5)
    glyph(d, skill['glyph'], n / 2, n / 2, n * 0.36, light, shade(base, 0.15))
    # contorno escuro do glifo (efeito pixel art)
    px = im.load()
    out = im.copy()
    po = out.load()
    for y in range(n):
        for x in range(n):
            if px[x, y][:3] == light[:3]:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    xx, yy = x + dx, y + dy
                    if 0 <= xx < n and 0 <= yy < n and px[xx, yy][:3] != light[:3]:
                        po[xx, yy] = shade(base, 0.12)
    d2 = ImageDraw.Draw(out)
    d2.rectangle([0, 0, n - 1, n - 1], outline=(10, 10, 16, 255))
    return pixel(out, scale)


# ── Molduras quadradas (32x32 ampliado 2x) ──
def frame(code, n=32, scale=2):
    st, rar, major, link, sel = code[0], int(code[1]), code[2] == 'm', code[3] == 'k', code[4] == 's'
    im = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    rc = hexc(RARITY_COLORS[rar])
    # o ícone ocupa 54/78 do quadro → borda interna em ~4.5 px de cada lado
    inner = round(n * (1 - 54 / 78) / 2)
    if st == 'l':
        ring = shade(rc, 0.45)
    elif st == 'r':
        ring = shade(rc, 0.75)
    elif st == 'a':
        ring = (255, 214, 70, 255)
    else:
        ring = rc
    w = 3 if major else 2
    if st in ('a', 'u'):  # halo de brilho em volta da borda
        gc = (255, 214, 70) if st == 'a' else rc[:3]
        for k, a in ((1, 170), (2, 110), (3, 55)):
            d.rectangle([inner - w - k, inner - w - k, n - 1 - inner + w + k, n - 1 - inner + w + k], outline=gc + (a,))
    d.rectangle([inner - w, inner - w, n - 1 - inner + w, n - 1 - inner + w], outline=ring, width=w)
    d.rectangle([inner, inner, n - 1 - inner, n - 1 - inner], outline=(8, 8, 14, 255))
    if st in ('l', 'r'):  # véu sobre o ícone
        d.rectangle([inner + 1, inner + 1, n - 2 - inner, n - 2 - inner], fill=(4, 4, 10, 170 if st == 'l' else 100))
    if major:  # cantos ornamentados
        o = inner - w - 2
        for (cx, cy, sx, sy) in ((o, o, 1, 1), (n - 1 - o, o, -1, 1), (o, n - 1 - o, 1, -1), (n - 1 - o, n - 1 - o, -1, -1)):
            d.line([cx, cy, cx + 4 * sx, cy], fill=mix(ring, (255, 255, 255, 255), 0.4), width=1)
            d.line([cx, cy, cx, cy + 4 * sy], fill=mix(ring, (255, 255, 255, 255), 0.4), width=1)
    if rar >= 4 and st != 'l':  # bordas especiais (lendária/suprema)
        for x in range(inner, n - inner, 3):
            d.point((x, inner - w - 1), fill=mix(rc, (255, 255, 255, 255), 0.5))
            d.point((x, n - inner + w), fill=mix(rc, (255, 255, 255, 255), 0.5))
    if link:  # selo azul de nó compartilhado
        d.rectangle([n - inner - 3, n - inner - 3, n - inner + 3, n - inner + 3], fill=(40, 110, 255, 255), outline=(220, 235, 255, 255))
    if sel:
        L = 5
        for (x, y, sx, sy) in ((0, 0, 1, 1), (n - 1, 0, -1, 1), (0, n - 1, 1, -1), (n - 1, n - 1, -1, -1)):
            d.line([x, y, x + L * sx, y], fill=(255, 255, 255, 255), width=2)
            d.line([x, y, x, y + L * sy], fill=(255, 255, 255, 255), width=2)
    return pixel(im, scale)


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
    for state in ('1', '2'):  # apagadas por baixo, acesas por cima
        for ch, (dx, dy) in zip(code, DIR8):
            if ch == state:
                col, w = (dim, wd) if state == '1' else (lit, wl)
                d.line([c, c, c + dx * c * 1.02, c + dy * c * 1.02], fill=col, width=w)
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


def nine(path, im, n=3):
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
    U = os.path.join(T, 'ui')
    nine(os.path.join(U, 'bar'), box(16, (8, 10, 20, 200), (60, 120, 170, 255)))
    nine(os.path.join(U, 'panel'), box(16, (10, 12, 24, 238), (60, 170, 230, 255), (20, 40, 70, 255)))
    nine(os.path.join(U, 'ctl'), box(16, (24, 30, 48, 225), (80, 100, 140, 255)))
    nine(os.path.join(U, 'ctl_hover'), box(16, (40, 52, 82, 240), (100, 210, 255, 255)))
    nine(os.path.join(U, 'ctl_pressed'), box(16, (18, 22, 36, 255), (160, 230, 255, 255)))
    for n in ('btn_close', 'btn_back', 'btn_grimoire', 'btn_status', 'btn_unlock', 'btn_equip', 'btn_link', 'arrow_l', 'arrow_u', 'arrow_d',
              'arrow_r', 'zoom_in', 'zoom_out', 'zoom_in_off', 'zoom_out_off', 'center', 'hover', 'icon_slot'):
        save(ui_icon(n), os.path.join(U, n + '.png'))
    for tid in TAB_GLYPH:
        save(tab_icon(tid), os.path.join(U, 'tab_' + tid + '.png'))
    # Menu principal
    M = os.path.join(T, 'menu')
    nine(os.path.join(M, 'window'), box(24, (22, 27, 36, 245), (95, 105, 120, 255), (40, 48, 60, 255), 2), 4)
    nine(os.path.join(M, 'box'), box(16, (17, 21, 29, 255), (58, 66, 80, 255)))
    nine(os.path.join(M, 'row'), box(16, (28, 33, 43, 255), (70, 80, 96, 255)))
    nine(os.path.join(M, 'row_hover'), box(16, (36, 44, 60, 255), (90, 200, 255, 255)))
    nine(os.path.join(M, 'slot'), box(16, (14, 17, 24, 255), (78, 88, 104, 255), (30, 36, 46, 255), 2), 4)
    nine(os.path.join(M, 'slot_hover'), box(16, (20, 26, 36, 255), (90, 200, 255, 255), (30, 36, 46, 255), 2), 4)
    nine(os.path.join(M, 'button'), box(16, (40, 46, 56, 255), (110, 120, 136, 255), (60, 68, 80, 255), 2), 4)
    nine(os.path.join(M, 'button_hover'), box(16, (52, 60, 74, 255), (90, 200, 255, 255), (60, 68, 80, 255), 2), 4)
    nine(os.path.join(M, 'button_pressed'), box(16, (30, 36, 46, 255), (140, 220, 255, 255), (60, 68, 80, 255), 2), 4)
    nine(os.path.join(M, 'tab_bg'), box(16, (34, 40, 50, 255), (80, 90, 104, 255), (48, 56, 68, 255), 2), 4)
    nine(os.path.join(M, 'tab_bg_hover'), box(16, (40, 50, 66, 255), (90, 200, 255, 255), (48, 56, 68, 255), 2), 4)
    save(Image.new('RGBA', (4, 4), (0, 0, 0, 0)), os.path.join(M, 'none.png'))
    save(Image.new('RGBA', (4, 4), (0, 0, 0, 110)), os.path.join(M, 'shade.png'))
    save(Image.new('RGBA', (4, 2), (60, 70, 86, 255)), os.path.join(M, 'line.png'))
    save(Image.new('RGBA', (4, 2), (60, 190, 255, 255)), os.path.join(M, 'tab_line.png'))
    save(box(16, (14, 17, 24, 255), (60, 68, 82, 255), (24, 28, 38, 255), 2), os.path.join(M, 'slot_empty.png'))
    for k in ('status', 'race', 'skills', 'evolution', 'records'):
        save(menu_tab_icon(k, False), os.path.join(M, f'tab_{k}.png'))
        save(menu_tab_icon(k, True), os.path.join(M, f'tab_{k}_on.png'))
    save(ui_icon('close', 48), os.path.join(M, 'close.png'))
    save(ui_icon('back', 48), os.path.join(M, 'back.png'))
    for k in range(21):
        save(progress_bar(k), os.path.join(M, f'bar_{k}.png'))
    save(item_tome(), os.path.join(rp, 'textures', 'items', 'tensura_tome.png'))
    save(item_crystal(), os.path.join(rp, 'textures', 'items', 'tensura_crystal.png'))
    pi = pack_icon()
    save(pi, os.path.join(rp, 'pack_icon.png'))
    if bp_icon:
        save(pi, bp_icon)
    print(f"texturas: {len(spec['skills'])} ícones, {len(spec['frames'])} molduras, {len(spec['lines'])} linhas")


if __name__ == '__main__':
    main()
