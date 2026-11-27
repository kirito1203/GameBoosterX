#!/usr/bin/env python3
"""Texturas da tela "Escolha sua Raça" e do Grande Sábio (pixel art gerada por código).

Uso: python3 tools/gen_race_textures.py <race_cards.json> <pasta RP>
"""
import json, os, random, sys
from PIL import Image, ImageDraw, ImageFilter

def hexc(h, a=255):
    h = h.lstrip('#'); return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)

def sh(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (c[3] if len(c) > 3 else 255,)

def up(im, s):
    return im.resize((im.width * s, im.height * s), Image.NEAREST)

def save(im, path):
    os.makedirs(os.path.dirname(path), exist_ok=True); im.save(path, optimize=True)

def nine(path, im, n):
    save(im, path + '.png')
    json.dump({'nineslice_size': n, 'base_size': [im.width, im.height]}, open(path + '.json', 'w'))

# ── Molduras em "pedra" (estilo dos menus do Minecraft) ──
def stone_frame(w, h, fill, edge_light, edge_dark, border=2, noise=True, seed=1):
    rnd = random.Random(seed)
    im = Image.new('RGBA', (w, h), fill)
    px = im.load()
    if noise:
        for y in range(h):
            for x in range(w):
                k = 1 + rnd.uniform(-0.08, 0.08)
                px[x, y] = sh(fill, k)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, w - 1, h - 1], outline=(16, 18, 24, 255))
    for i in range(1, border + 1):
        d.line([i, i, w - 1 - i, i], fill=edge_light); d.line([i, i, i, h - 1 - i], fill=edge_light)
        d.line([i, h - 1 - i, w - 1 - i, h - 1 - i], fill=edge_dark); d.line([w - 1 - i, i, w - 1 - i, h - 1 - i], fill=edge_dark)
    return im

# ── Retratos das raças (16x16 → 6x) ──
def portrait(spec):
    n = 16
    im = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    kind = spec['kind']
    skin = hexc(spec['skin'])
    eyes = hexc(spec.get('eyes', '#202020'))
    dark = sh(skin, 0.6)
    if kind == 'slime':
        d.ellipse([2, 5, 13, 15], fill=skin)
        d.rectangle([2, 11, 13, 15], fill=skin)
        d.ellipse([4, 6, 8, 9], fill=sh(skin, 1.35))
        d.point([(6, 10), (10, 10)], fill=(20, 40, 90, 255))
        d.line([6, 12, 9, 12], fill=(20, 40, 90, 255))
        return up(im, 6)
    if kind == 'flame':
        pts = [(8, 1), (11, 6), (13, 10), (11, 14), (5, 14), (3, 10), (5, 6), (7, 8)]
        d.polygon(pts, fill=skin)
        d.polygon([(8, 6), (10, 10), (9, 13), (6, 13), (6, 10)], fill=sh(skin, 1.5))
        d.point([(6, 11), (9, 11)], fill=(255, 255, 255, 255))
        return up(im, 6)
    if kind in ('lizard', 'dragon', 'wolf'):
        # cabeça de perfil em blocos
        d.rectangle([3, 4, 11, 12], fill=skin)
        d.rectangle([10, 7, 15, 12], fill=skin)  # focinho
        d.rectangle([3, 12, 13, 13], fill=dark)
        d.point((8, 7), fill=eyes); d.point((9, 7), fill=eyes)
        d.line([11, 11, 15, 11], fill=(30, 20, 20, 255))
        if kind == 'dragon':
            d.polygon([(4, 4), (3, 0), (6, 4)], fill=sh(skin, 1.4)); d.polygon([(7, 4), (8, 0), (9, 4)], fill=sh(skin, 1.4))
            for x in range(3, 11, 2): d.point((x, 9), fill=sh(skin, 0.75))
        if kind == 'lizard':
            for x in range(4, 10, 2): d.point((x, 5), fill=sh(skin, 1.3))
        if kind == 'wolf':
            d.polygon([(3, 4), (4, 0), (6, 4)], fill=dark); d.polygon([(7, 4), (8, 0), (9, 4)], fill=dark)
            d.point((14, 8), fill=(10, 10, 10, 255)); d.point((13, 12), fill=(240, 240, 240, 255))
        return up(im, 6)
    # rosto humanoide
    d.rectangle([4, 4, 11, 12], fill=skin)
    d.rectangle([5, 13, 10, 15], fill=dark)
    hair = spec.get('hair')
    if hair:
        hc = hexc(hair)
        d.rectangle([3, 2, 12, 4], fill=hc); d.rectangle([3, 4, 4, 9], fill=hc); d.rectangle([11, 4, 12, 9], fill=hc)
        if spec.get('feathers'):
            fc = hexc(spec['feathers']); d.polygon([(2, 3), (0, 9), (3, 8)], fill=fc); d.polygon([(13, 3), (15, 9), (12, 8)], fill=fc)
    d.rectangle([6, 7, 6, 8], fill=eyes); d.rectangle([9, 7, 9, 8], fill=eyes)
    d.point((6, 7), fill=sh(eyes, 1.4))
    d.line([7, 11, 8, 11], fill=sh(skin, 0.55))
    if spec.get('ears'):
        d.polygon([(4, 6), (1, 4), (4, 9)], fill=skin); d.polygon([(11, 6), (14, 4), (11, 9)], fill=skin)
    if spec.get('horns'):
        hc = hexc(spec['horns']); d.polygon([(4, 3), (3, 0), (6, 3)], fill=hc); d.polygon([(9, 3), (12, 0), (11, 3)], fill=hc)
    if spec.get('halo'):
        d.line([5, 0, 10, 0], fill=(255, 230, 100, 255)); d.point([(4, 1), (11, 1)], fill=(255, 230, 100, 255))
    if spec.get('teeth') or spec.get('fangs'):
        d.point([(6, 12), (9, 12)], fill=(250, 250, 240, 255))
    if spec.get('tusks'):
        d.line([5, 10, 5, 12], fill=(240, 235, 210, 255)); d.line([10, 10, 10, 12], fill=(240, 235, 210, 255))
    if spec.get('beard'):
        bc = hexc(spec['beard']); d.rectangle([4, 10, 11, 14], fill=bc); d.line([7, 11, 8, 11], fill=sh(bc, 0.6))
    if spec.get('leaves'):
        for (x, y) in ((3, 2), (6, 1), (10, 1), (12, 3)): d.point((x, y), fill=(120, 220, 80, 255))
    if spec.get('fins'):
        fc = sh(skin, 0.8); d.polygon([(4, 6), (0, 3), (2, 9)], fill=fc); d.polygon([(11, 6), (15, 3), (13, 9)], fill=fc)
    if spec.get('stitches'):
        d.line([5, 5, 7, 6], fill=(40, 40, 40, 255)); d.point((6, 5), fill=(200, 200, 200, 255))
    if spec.get('scar'):
        d.line([9, 5, 11, 9], fill=sh(skin, 0.5))
    return up(im, 6)

# ── Grande Sábio: núcleo mágico (mesma arte do painel e do modelo 3D, ver tools/sage_art.py) ──
def sage(w=40, h=64):
    import sys as _s, os as _o
    _s.path.insert(0, _o.path.dirname(_o.path.abspath(__file__)))
    from sage_art import draw_core
    return draw_core(w * 6, h * 6, 'sage', px=4)

def sage_glow(w=128, h=160):
    im = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse([w * .12, h * .08, w * .88, h * .92], fill=(60, 160, 255, 140))
    d.ellipse([w * .3, h * .3, w * .7, h * .7], fill=(150, 110, 255, 120))
    return im.filter(ImageFilter.GaussianBlur(18))

def cube():
    im = Image.new('RGBA', (12, 12), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    c1, c2, c3 = (120, 200, 255, 230), (70, 140, 240, 230), (40, 90, 200, 230)
    d.polygon([(6, 1), (11, 3), (6, 5), (1, 3)], fill=c1)
    d.polygon([(1, 3), (6, 5), (6, 11), (1, 9)], fill=c2)
    d.polygon([(11, 3), (6, 5), (6, 11), (11, 9)], fill=c3)
    return up(im, 4)

def background(W=320, H=180):
    rnd = random.Random(5)
    im = Image.new('RGBA', (W, H)); px = im.load()
    for y in range(H):
        for x in range(W):
            t = y / H
            px[x, y] = (int(12 + 18 * t), int(16 + 26 * t), int(40 + 40 * t), 255)
    d = ImageDraw.Draw(im)
    for _ in range(160):
        x, y = rnd.randrange(W), rnd.randrange(int(H * .7)); v = rnd.randint(140, 255)
        px[x, y] = (v, v, 255, 255)
    # terreno em blocos (silhueta pixelada)
    y0 = 140
    for x in range(0, W, 8):
        y0 = max(118, min(160, y0 + rnd.choice((-8, 0, 0, 8))))
        d.rectangle([x, y0, x + 7, H], fill=(18, 34, 30, 255))
        d.rectangle([x, y0, x + 7, y0 + 2], fill=(36, 70, 46, 255))
    for (tx, ty) in ((20, 112), (60, 104), (250, 108), (290, 100)):
        d.rectangle([tx + 6, ty + 18, tx + 9, ty + 34], fill=(30, 22, 16, 255))
        d.rectangle([tx, ty, tx + 15, ty + 18], fill=(16, 40, 26, 255))
    vig = Image.new('RGBA', (W, H), (0, 0, 0, 0)); vd = ImageDraw.Draw(vig)
    for i in range(16): vd.rectangle([i, i, W - 1 - i, H - 1 - i], outline=(0, 0, 0, 120 - i * 7))
    im.alpha_composite(vig)
    return up(im, 3)

def stars(k, total=5):
    im = Image.new('RGBA', (total * 10, 9), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    for i in range(total):
        col = (255, 205, 40, 255) if i < k else (80, 80, 96, 255)
        x = i * 10
        d.polygon([(x + 4, 0), (x + 5, 3), (x + 8, 3), (x + 6, 5), (x + 7, 8), (x + 4, 6), (x + 1, 8), (x + 2, 5), (x, 3), (x + 3, 3)], fill=col)
    return up(im, 4)

def arrow_down():
    im = Image.new('RGBA', (7, 5), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    d.polygon([(0, 0), (6, 0), (3, 4)], fill=(235, 240, 255, 255))
    return up(im, 6)

def lock_overlay(n=32):
    im = Image.new('RGBA', (n, n), (6, 8, 14, 165)); d = ImageDraw.Draw(im)
    d.rectangle([12, 15, 19, 21], fill=(200, 200, 210, 255)); d.rectangle([13, 11, 18, 15], outline=(200, 200, 210, 255))
    d.point((15, 18), fill=(40, 40, 50, 255))
    return im

def main():
    cards = json.load(open(sys.argv[1]))
    rp = sys.argv[2]
    R = os.path.join(rp, 'textures', 'tensura', 'race')
    for c in cards:
        save(portrait(c['portrait']), os.path.join(R, f"p_{c['id']}.png"))
    save(sage(), os.path.join(R, 'sage.png'))
    save(sage_glow(), os.path.join(R, 'sage_glow.png'))
    save(cube(), os.path.join(R, 'cube.png'))
    save(background(), os.path.join(R, 'bg.png'))
    save(arrow_down(), os.path.join(R, 'arrow_down.png'))
    for k in range(6): save(stars(k), os.path.join(R, f'stars_{k}.png'))
    stone = ((52, 58, 68, 255), (110, 118, 130, 255), (28, 32, 40, 255))
    nine(os.path.join(R, 'dialog'), stone_frame(24, 24, (20, 26, 40, 238), (80, 150, 220, 255), (30, 50, 80, 255), seed=2), 4)
    nine(os.path.join(R, 'panel'), stone_frame(24, 24, (18, 22, 32, 235), (70, 110, 160, 255), (26, 36, 56, 255), seed=3), 4)
    nine(os.path.join(R, 'title'), stone_frame(24, 24, (22, 30, 48, 245), (110, 200, 255, 255), (40, 70, 120, 255), seed=4), 4)
    nine(os.path.join(R, 'tab'), stone_frame(24, 16, (26, 40, 60, 250), (110, 200, 255, 255), (40, 70, 120, 255), seed=6), 4)
    nine(os.path.join(R, 'card'), stone_frame(24, 24, *stone, seed=7), 4)
    nine(os.path.join(R, 'card_hover'), stone_frame(24, 24, (70, 78, 92, 120), (200, 220, 240, 255), (60, 70, 90, 255), noise=False), 4)
    sel = Image.new('RGBA', (24, 24), (0, 0, 0, 0)); sd = ImageDraw.Draw(sel)
    sd.rectangle([0, 0, 23, 23], outline=(80, 220, 255, 255)); sd.rectangle([1, 1, 22, 22], outline=(160, 240, 255, 255)); sd.rectangle([2, 2, 21, 21], outline=(80, 200, 255, 200))
    nine(os.path.join(R, 'card_sel'), sel, 4)
    glow = Image.new('RGBA', (24, 24), (60, 170, 255, 90)); nine(os.path.join(R, 'card_glow'), glow, 2)
    nine(os.path.join(R, 'card_locked'), lock_overlay(), 1)
    nine(os.path.join(R, 'btn_green'), stone_frame(24, 16, (60, 140, 50, 255), (130, 210, 110, 255), (30, 80, 26, 255), seed=8), 4)
    nine(os.path.join(R, 'btn_green_hover'), stone_frame(24, 16, (80, 170, 66, 255), (180, 240, 160, 255), (40, 100, 34, 255), seed=8), 4)
    nine(os.path.join(R, 'btn_green_pressed'), stone_frame(24, 16, (46, 110, 40, 255), (30, 80, 26, 255), (130, 210, 110, 255), seed=8), 4)
    nine(os.path.join(R, 'btn_stone'), stone_frame(16, 16, *stone, seed=9), 4)
    nine(os.path.join(R, 'btn_stone_hover'), stone_frame(16, 16, (70, 78, 92, 255), (150, 220, 255, 255), (30, 34, 44, 255), seed=9), 4)
    save(Image.new('RGBA', (4, 2), (70, 110, 160, 255)), os.path.join(R, 'line.png'))
    # (o modelo 3D do Grande Sábio e sua textura vêm de tools/gen_sage_model.py)
    print(f'texturas da raça: {len(cards)} retratos')

if __name__ == '__main__':
    main()
