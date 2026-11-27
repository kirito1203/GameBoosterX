#!/usr/bin/env python3
"""Arte 2D do Grande Sábio como núcleo mágico (retratos do painel, ícones e a figura da tela de raças).

Mesma identidade do modelo 3D (tools/gen_sage_model.py): núcleo em cruz brilhante, anéis horizontais
tracejados, círculo mágico na base, painéis holográficos e fragmentos flutuantes. Desenhado em alta
resolução e reduzido com NEAREST para ficar pixelado.
"""
import math
from PIL import Image, ImageDraw, ImageFilter

# Paletas por estágio: (principal, clara, escura). Cores escolhidas pelo addon.
PALETTES = {
    'sage': ((70, 170, 255), (170, 235, 255), (20, 60, 160)),
    'raphael': ((255, 190, 70), (255, 238, 170), (150, 90, 20)),
    'ciel': ((190, 120, 255), (240, 205, 255), (90, 40, 170)),
}


def _a(c, a):
    return (c[0], c[1], c[2], a)


def _ring(d, cx, cy, rx, ry, col, width, dashes=18, part='all'):
    """Elipse tracejada. part: 'back' (metade de cima), 'front' (de baixo) ou 'all'."""
    for i in range(dashes):
        a0 = 360 * i / dashes
        a1 = a0 + 360 / dashes * 0.62
        mid = math.radians((a0 + a1) / 2)
        is_front = math.sin(mid) > 0
        if part == 'back' and is_front or part == 'front' and not is_front:
            continue
        d.arc([cx - rx, cy - ry, cx + rx, cy + ry], a0, a1, fill=col, width=width)


def draw_core(w, h, stage='sage', bg=None, panels=True, px=1):
    """Figura do núcleo mágico em w x h. `px` = tamanho final do "pixel" (reduz e amplia)."""
    S = 4
    W, H = w * S, h * S
    c, l, dk = PALETTES[stage]
    im = Image.new('RGBA', (W, H), bg or (0, 0, 0, 0))
    cx, cy = W / 2, H * 0.46
    R = min(W, H * 0.9) * 0.5

    glow = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    g = ImageDraw.Draw(glow)
    g.ellipse([cx - R * .8, cy - R * .8, cx + R * .8, cy + R * .8], fill=_a(c, 110))
    g.ellipse([cx - R * .35, cy - R * .35, cx + R * .35, cy + R * .35], fill=_a(l, 160))
    # coluna de luz
    g.rectangle([cx - R * .12, 0, cx + R * .12, H], fill=_a(c, 60))
    im.alpha_composite(glow.filter(ImageFilter.GaussianBlur(R * .22)))

    d = ImageDraw.Draw(im)
    lw = max(2, int(R * .045))
    # círculo mágico da base
    by = H * 0.9
    for k, aa in ((1.0, 220), (.78, 160), (.55, 120)):
        d.ellipse([cx - R * k, by - R * k * .22, cx + R * k, by + R * k * .22], outline=_a(l, aa), width=lw)
    rings = [(cy - R * .48, R * .52), (cy, R * .78), (cy + R * .46, R * .62)]
    for y, rx in rings:
        _ring(d, cx, y, rx, rx * .26, _a(c, 200), lw, part='back')

    # painéis holográficos nas laterais
    if panels:
        for sx in (-1, 1):
            px0 = cx + sx * R * .8
            pw, ph = R * .22, R * .36
            poly = [(px0 - pw / 2, cy - ph / 2 - sx * R * .04), (px0 + pw / 2, cy - ph / 2 + sx * R * .04),
                    (px0 + pw / 2, cy + ph / 2 + sx * R * .04), (px0 - pw / 2, cy + ph / 2 - sx * R * .04)]
            d.polygon(poly, fill=_a(dk, 150), outline=_a(l, 230), width=max(1, lw // 2))
            for k in range(4):
                yy = cy - ph * .3 + k * ph * .17
                d.line([px0 - pw * .3, yy, px0 + pw * (.1 if k % 2 else .3), yy], fill=_a(l, 200), width=max(1, lw // 2))

    # núcleo em cruz (degraus)
    u = R * .11
    def blk(x0, y0, x1, y1, col):
        d.rectangle([cx + x0 * u, cy + y0 * u, cx + x1 * u, cy + y1 * u], fill=col)
    blk(-3.2, -1, 3.2, 1, _a(c, 255)); blk(-1, -3.2, 1, 3.2, _a(c, 255))
    blk(-2.2, -2.2, 2.2, 2.2, _a(c, 255))
    blk(-4, -.5, -3.2, .5, _a(l, 255)); blk(3.2, -.5, 4, .5, _a(l, 255))
    blk(-.5, -4, .5, -3.2, _a(l, 255)); blk(-.5, 3.2, .5, 4, _a(l, 255))
    blk(-1.6, -1.6, 1.6, 1.6, _a(l, 255))
    blk(-.8, -.8, .8, .8, (255, 255, 255, 255))

    for y, rx in rings:
        _ring(d, cx, y, rx, rx * .26, _a(l, 240), lw, part='front')

    # fragmentos
    for i, (ax, ay, sz) in enumerate(((-.85, -.75, .07), (.8, -.85, .06), (-.95, .3, .05), (.95, .45, .06), (-.5, -1.05, .05), (.55, .9, .05), (0, -1.15, .06))):
        x, y, s = cx + ax * R, cy + ay * R, sz * R
        d.rectangle([x - s, y - s, x + s, y + s], outline=_a(l, 230), width=max(1, lw // 2))
        d.rectangle([x - s / 3, y - s / 3, x + s / 3, y + s / 3], fill=_a(l, 230))

    out = im.resize((w // px, h // px), Image.LANCZOS)
    return out.resize((w, h), Image.NEAREST) if px > 1 else out


def portrait(stage, size=128):
    """Retrato quadrado com moldura (painel do Grande Sábio)."""
    c, l, dk = PALETTES[stage]
    im = Image.new('RGBA', (size, size), (6, 12, 30, 255))
    im.alpha_composite(draw_core(size, size, stage, px=2))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, size - 1, size - 1], outline=_a(l, 255), width=2)
    d.rectangle([3, 3, size - 4, size - 4], outline=_a(c, 255), width=1)
    return im


def icon(stage, size=48):
    """Ícone redondo (botão do menu)."""
    c, l, dk = PALETTES[stage]
    im = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse([1, 1, size - 2, size - 2], fill=(8, 16, 40, 255), outline=_a(l, 255), width=2)
    core = draw_core(size, size, stage, panels=False, px=2)
    mask = Image.new('L', (size, size), 0)
    ImageDraw.Draw(mask).ellipse([3, 3, size - 4, size - 4], fill=255)
    layer = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    layer.paste(core, (0, 0), Image.composite(core.getchannel('A'), Image.new('L', (size, size), 0), mask))
    im.alpha_composite(layer)
    return im
