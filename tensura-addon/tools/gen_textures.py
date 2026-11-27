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


ELEMENT_COLORS = {
    'fire': '#ff6a2a', 'water': '#2a9dff', 'ice': '#9fe8ff', 'earth': '#c09050', 'wind': '#8cf0a8',
    'lightning': '#ffe14a', 'explosion': '#ff3b3b', 'holy': '#fff2a8', 'dark': '#9a5cff', 'spirit': '#5effc8',
    'illusion': '#ff7ae6', 'space': '#6a7bff', 'gravity': '#8a6cff', 'soul': '#4fd9d0', 'nuclear': '#8aff3a',
    'dragon': '#d050ff', 'physical': '#d8d8d8', 'acid': '#a0e040', 'blood': '#e0304a',
}
TREE_COLORS = {
    'elemental': '#ff9a40', 'aspect': '#6ab0ff', 'holy': '#ffe680', 'summon': '#c080ff', 'illusion': '#ff80d0',
    'spirit': '#60ffc0', 'necromancy': '#90e070', 'dark': '#9060ff', 'advanced': '#ff4060', 'racial': '#ffb040',
}
RARITY_COLORS = {1: '#b4bccc', 2: '#3fa9ff', 3: '#c060ff', 4: '#ffb020', 5: '#ff3050'}


# ── Glifos ────────────────────────────────────────────────────────────────
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


def icon(skill, size=96):
    img = canvas(size)
    d = ImageDraw.Draw(img)
    S = size * SS
    cx = cy = S / 2
    base = hexc(skill['color'])
    dark = (14, 12, 28, 255)
    # disco com gradiente radial
    for i in range(40, 0, -1):
        t = i / 40
        c = mix(base, dark, 0.55 + 0.45 * t)
        rr = S * 0.5 * t
        d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=c)
    d.ellipse([S * .06, S * .06, S * .94, S * .94], outline=mix(base, (255, 255, 255, 255), .3), width=SS * 2)
    glyph(d, skill['glyph'], cx, cy - S * .07, S * .3, mix(base, (255, 255, 255, 255), .45), dark)
    # abreviação
    f = font(int(S * 0.2))
    txt = skill['abbr']
    tb = d.textbbox((0, 0), txt, font=f)
    tw = tb[2] - tb[0]
    d.text((cx - tw / 2, S * 0.68), txt, font=f, fill=(255, 255, 255, 235), stroke_width=SS * 2, stroke_fill=(0, 0, 0, 200))
    return finish(img, size)


def frame(code, size=128):
    st, rar, major, link, sel = code[0], int(code[1]), code[2] == 'm', code[3] == 'k', code[4] == 's'
    img = canvas(size)
    S = size * SS
    c = S / 2
    rc = hexc(RARITY_COLORS[rar])
    icon_r = S * 0.318          # raio do ícone (56% / 88%)
    ring_in = icon_r * 1.0
    ring_out = icon_r * (1.18 if not major else 1.3)

    glow = Image.new('RGBA', img.size, (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    if st == 'u':
        gd.ellipse([c - ring_out * 1.35, c - ring_out * 1.35, c + ring_out * 1.35, c + ring_out * 1.35], fill=rc[:3] + (150,))
    elif st == 'a':
        gd.ellipse([c - ring_out * 1.35, c - ring_out * 1.35, c + ring_out * 1.35, c + ring_out * 1.35], fill=(255, 216, 74, 170))
    glow = glow.filter(ImageFilter.GaussianBlur(S * 0.05))
    img.alpha_composite(glow)
    d = ImageDraw.Draw(img)

    ring = rc
    if st == 'l':
        ring = mix(rc, (60, 60, 70, 255), 0.65)[:3] + (200,)
    elif st == 'r':
        ring = mix(rc, (90, 90, 100, 255), 0.35)
    elif st == 'a':
        ring = (255, 216, 74, 255)
    d.ellipse([c - ring_out, c - ring_out, c + ring_out, c + ring_out], fill=ring)
    # recorta o centro para o ícone aparecer
    mask = Image.new('L', img.size, 255)
    ImageDraw.Draw(mask).ellipse([c - ring_in, c - ring_in, c + ring_in, c + ring_in], fill=0)
    img = Image.composite(img, Image.new('RGBA', img.size, (0, 0, 0, 0)), mask)
    d = ImageDraw.Draw(img)
    # véu escuro sobre o ícone: bloqueado (forte) ou faltando requisito (leve)
    if st in ('l', 'r'):
        d.ellipse([c - icon_r, c - icon_r, c + icon_r, c + icon_r], fill=(5, 5, 12, 170 if st == 'l' else 100))

    # anel interno fino
    d.ellipse([c - ring_in, c - ring_in, c + ring_in, c + ring_in], outline=mix(ring, (255, 255, 255, 255), .35), width=SS * 2)

    if major:  # ornamentos: 8 pontas e anel externo
        orn = ring if st != 'l' else ring
        for i in range(8):
            ang = math.pi * 2 * i / 8
            r1, r2 = ring_out * 1.02, ring_out * 1.28
            w = 0.16
            p = [(c + math.cos(ang - w) * r1, c + math.sin(ang - w) * r1), (c + math.cos(ang) * r2, c + math.sin(ang) * r2),
                 (c + math.cos(ang + w) * r1, c + math.sin(ang + w) * r1)]
            d.polygon(p, fill=orn)
        d.ellipse([c - ring_out * 1.12, c - ring_out * 1.12, c + ring_out * 1.12, c + ring_out * 1.12], outline=orn, width=SS * 2)
    if rar >= 4 and st != 'l':  # bordas especiais para lendária/suprema
        for i in range(16):
            ang = math.pi * 2 * i / 16 + math.pi / 16
            rr = ring_out * (1.38 if major else 1.15)
            d.ellipse([c + math.cos(ang) * rr - SS * 2.5, c + math.sin(ang) * rr - SS * 2.5,
                       c + math.cos(ang) * rr + SS * 2.5, c + math.sin(ang) * rr + SS * 2.5], fill=rc)
    if link:  # nó compartilhado: selo azul com elo
        bx, by, br = c + ring_out * .78, c + ring_out * .78, S * .1
        d.ellipse([bx - br, by - br, bx + br, by + br], fill=(40, 110, 255, 255), outline=(220, 235, 255, 255), width=SS * 2)
        d.ellipse([bx - br * .55, by - br * .3, bx + br * .05, by + br * .3], outline=(255, 255, 255, 255), width=SS * 2)
        d.ellipse([bx - br * .05, by - br * .3, bx + br * .55, by + br * .3], outline=(255, 255, 255, 255), width=SS * 2)
    if sel:  # seleção: colchetes brancos
        r = S * 0.48
        L = S * 0.12
        w = SS * 3
        for sx in (-1, 1):
            for sy in (-1, 1):
                x, y = c + sx * r, c + sy * r
                d.line([x, y, x - sx * L, y], fill=(255, 255, 255, 255), width=w)
                d.line([x, y, x, y - sy * L], fill=(255, 255, 255, 255), width=w)
    return finish(img, size)


PIPE_COLORS = {1: (70, 76, 105, 230), 2: (214, 172, 74, 255), 3: (120, 230, 255, 255)}


def pipe(code, size=64):
    img = canvas(size)
    S = size * SS
    c = S / 2
    vals = [int(ch) for ch in code]  # n e s w
    glow = Image.new('RGBA', img.size, (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    d = ImageDraw.Draw(img)
    hw = S * 0.055
    dirs = [(0, -1), (1, 0), (0, 1), (-1, 0)]
    for (dx, dy), v in zip(dirs, vals):
        if not v:
            continue
        col = PIPE_COLORS[v]
        x2, y2 = c + dx * c, c + dy * c
        box = [min(c, x2) - hw, min(c, y2) - hw, max(c, x2) + hw, max(c, y2) + hw]
        if dx == 0:
            box[0], box[2] = c - hw, c + hw
        else:
            box[1], box[3] = c - hw, c + hw
        if v == 3:
            gbox = [box[0] - hw * 1.5, box[1] - hw * 1.5, box[2] + hw * 1.5, box[3] + hw * 1.5]
            if dx == 0:
                gbox[1], gbox[3] = box[1], box[3]
            else:
                gbox[0], gbox[2] = box[0], box[2]
            gd.rectangle(gbox, fill=(80, 200, 255, 120))
        d.rectangle(box, fill=col)
    m = max(vals)
    if m:
        d.ellipse([c - hw * 1.25, c - hw * 1.25, c + hw * 1.25, c + hw * 1.25], fill=PIPE_COLORS[m])
    glow = glow.filter(ImageFilter.GaussianBlur(S * 0.03))
    out = Image.new('RGBA', img.size, (0, 0, 0, 0))
    out.alpha_composite(glow)
    out.alpha_composite(img)
    return finish(out, size)


def background(w=512, h=256):
    img = Image.new('RGBA', (w, h), (0, 0, 0, 255))
    px = img.load()
    for y in range(h):
        for x in range(w):
            dx, dy = (x - w / 2) / (w / 2), (y - h / 2) / (h / 2)
            dist = min(1, math.sqrt(dx * dx * 0.7 + dy * dy))
            r = int(18 + 22 * (1 - dist))
            g = int(14 + 14 * (1 - dist))
            b = int(34 + 46 * (1 - dist))
            px[x, y] = (r, g, b, 255)
    d = ImageDraw.Draw(img)
    rnd = random.Random(7)
    for _ in range(260):
        x, y = rnd.random() * w, rnd.random() * h
        a = rnd.randint(40, 160)
        s = rnd.choice((0.5, 0.8, 1.2))
        d.ellipse([x - s, y - s, x + s, y + s], fill=(170, 190, 255, a))
    # círculo mágico sutil ao centro
    over = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    od = ImageDraw.Draw(over)
    for rr, a in ((110, 26), (86, 20), (60, 16)):
        od.ellipse([w / 2 - rr, h / 2 - rr, w / 2 + rr, h / 2 + rr], outline=(120, 140, 255, a), width=2)
    for i in range(6):
        ang = math.pi * 2 * i / 6
        od.line([w / 2 + math.cos(ang) * 110, h / 2 + math.sin(ang) * 110, w / 2 + math.cos(ang + 2.09) * 110, h / 2 + math.sin(ang + 2.09) * 110],
                fill=(120, 140, 255, 18), width=2)
    img.alpha_composite(over)
    return img


def box_texture(size, fill, border, bw=2, glow_top=None):
    img = Image.new('RGBA', (size, size), fill)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, size - 1, size - 1], outline=border, width=bw)
    if glow_top:
        d.line([bw, bw, size - bw - 1, bw], fill=glow_top, width=1)
    return img


def ui_icon(name, size=64):
    img = canvas(size)
    d = ImageDraw.Draw(img)
    S = size * SS
    c = S / 2
    W = (240, 236, 255, 255)
    G = (255, 205, 90, 255)
    w = int(S * 0.09)
    if name == 'btn_close':
        d.line([S * .25, S * .25, S * .75, S * .75], fill=W, width=w)
        d.line([S * .75, S * .25, S * .25, S * .75], fill=W, width=w)
    elif name == 'btn_grimoire':
        d.rectangle([S * .2, S * .2, S * .8, S * .82], fill=(110, 60, 30, 255), outline=G, width=SS * 3)
        d.rectangle([S * .27, S * .2, S * .3, S * .82], fill=G)
        glyph(d, 'summon', S * .55, S * .5, S * .2, G, (0, 0, 0, 255))
    elif name == 'btn_status':
        d.ellipse([S * .35, S * .15, S * .65, S * .45], fill=W)
        d.pieslice([S * .2, S * .45, S * .8, S * 1.05], 180, 360, fill=W)
    elif name == 'btn_unlock':
        d.rectangle([S * .25, S * .45, S * .75, S * .85], fill=G)
        d.arc([S * .32, S * .12, S * .68, S * .6], 180, 330, fill=G, width=w)
    elif name == 'btn_equip':
        glyph(d, 'passive', c, c, S * .4, (120, 220, 255, 255), (0, 0, 0, 255))
    elif name == 'btn_link':
        d.ellipse([S * .12, S * .32, S * .55, S * .68], outline=(140, 190, 255, 255), width=w)
        d.ellipse([S * .45, S * .32, S * .88, S * .68], outline=(140, 190, 255, 255), width=w)
    elif name.startswith('arrow_'):
        dirn = name[-1]
        pts = {'u': [(c, S * .22), (S * .78, S * .7), (S * .22, S * .7)], 'd': [(c, S * .78), (S * .78, S * .3), (S * .22, S * .3)],
               'l': [(S * .22, c), (S * .7, S * .22), (S * .7, S * .78)], 'r': [(S * .78, c), (S * .3, S * .22), (S * .3, S * .78)]}[dirn]
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
        for (x1, y1, x2, y2) in ((c, S * .05, c, S * .25), (c, S * .75, c, S * .95), (S * .05, c, S * .25, c), (S * .75, c, S * .95, c)):
            d.line([x1, y1, x2, y2], fill=W, width=int(w * .8))
    elif name == 'hover':
        d.ellipse([S * .02, S * .02, S * .98, S * .98], outline=(255, 255, 255, 200), width=SS * 4)
    elif name == 'icon_slot':
        d.ellipse([S * .02, S * .02, S * .98, S * .98], fill=(10, 10, 24, 220), outline=(255, 205, 90, 255), width=SS * 3)
    return finish(img, size)


TAB_GLYPH = {'racial': 'race', 'elemental': 'bolt', 'aspect': 'buff', 'holy': 'heal', 'summon': 'summon', 'illusion': 'debuff',
             'spirit': 'flight', 'necromancy': 'bind', 'dark': 'drain', 'advanced': 'nova'}


def tab_icon(tid, on, size=64):
    img = canvas(size)
    d = ImageDraw.Draw(img)
    S = size * SS
    col = hexc(TREE_COLORS[tid])
    if not on:
        col = mix(col, (120, 120, 140, 255), 0.45)
    else:
        d.ellipse([S * .04, S * .04, S * .96, S * .96], fill=col[:3] + (60,))
    glyph(d, TAB_GLYPH[tid], S / 2, S / 2, S * .38, col, (10, 10, 24, 255))
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
    d.polygon([(S * .5, S * .06), (S * .8, S * .4), (S * .62, S * .94), (S * .38, S * .94), (S * .2, S * .4)], fill=(90, 200, 255, 255),
              outline=(230, 250, 255, 255))
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


def main():
    spec = json.load(open(sys.argv[1]))
    rp = sys.argv[2]
    bp_icon = sys.argv[3] if len(sys.argv) > 3 else None
    T = os.path.join(rp, 'textures', 'tensura')
    for s in spec['skills']:
        save(icon(s), os.path.join(T, 'icons', s['icon'] + '.png'))
    for code in spec['frames']:
        save(frame(code), os.path.join(T, 'ui', 'node', 'f_' + code + '.png'))
    for code in spec['pipes']:
        save(pipe(code), os.path.join(T, 'ui', 'pipe', 'p' + code + '.png'))
    save(background(), os.path.join(T, 'ui', 'bg.png'))
    nines = {
        'bar': box_texture(16, (8, 8, 20, 225), (190, 150, 70, 255), 1),
        'panel': box_texture(16, (10, 10, 26, 236), (110, 130, 255, 255), 1, (190, 200, 255, 255)),
        'ctl': box_texture(16, (30, 30, 58, 235), (100, 110, 170, 255), 1),
        'ctl_hover': box_texture(16, (52, 52, 96, 245), (255, 210, 90, 255), 1),
        'ctl_pressed': box_texture(16, (20, 20, 40, 255), (255, 230, 150, 255), 1),
    }
    for n, im in nines.items():
        save(im, os.path.join(T, 'ui', n + '.png'))
        with open(os.path.join(T, 'ui', n + '.json'), 'w') as fh:
            json.dump({'nineslice_size': 2, 'base_size': [16, 16]}, fh)
    for n in ('btn_close', 'btn_grimoire', 'btn_status', 'btn_unlock', 'btn_equip', 'btn_link', 'arrow_l', 'arrow_u', 'arrow_d', 'arrow_r',
              'zoom_in', 'zoom_out', 'zoom_in_off', 'zoom_out_off', 'center', 'hover', 'icon_slot'):
        save(ui_icon(n), os.path.join(T, 'ui', n + '.png'))
    for tid in TAB_GLYPH:
        save(tab_icon(tid, False), os.path.join(T, 'ui', 'tab_' + tid + '.png'))
        save(tab_icon(tid, True), os.path.join(T, 'ui', 'tab_' + tid + '_on.png'))
    save(item_tome(), os.path.join(rp, 'textures', 'items', 'tensura_tome.png'))
    save(item_crystal(), os.path.join(rp, 'textures', 'items', 'tensura_crystal.png'))
    pi = pack_icon()
    save(pi, os.path.join(rp, 'pack_icon.png'))
    if bp_icon:
        save(pi, bp_icon)
    print(f"texturas: {len(spec['skills'])} ícones, {len(spec['frames'])} molduras, {len(spec['pipes'])} linhas")


if __name__ == '__main__':
    main()
