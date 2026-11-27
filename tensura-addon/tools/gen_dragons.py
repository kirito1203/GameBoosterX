#!/usr/bin/env python3
"""Dragões invocáveis (manifestações de Veldora e Velgrynd): modelo voxel leve, texturas, animações,
cliente e comportamento. Comportamento reutiliza as regras seguras das invocações (gen_entities.behavior):
dono, alvo apenas monstros, despawn automático por timer, sem dano de queda.

Uso: python3 tools/gen_dragons.py <pasta RP> <pasta BP>
"""
import json, os, sys
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_entities import behavior  # noqa: E402

TW, TH = 128, 64
DRAGONS = {
    # id: (cor principal, escura, clara/brilho, imune ao fogo)
    'storm_dragon': ((40, 60, 80), (16, 22, 34), (90, 230, 255), False),
    'scorch_dragon': ((170, 40, 30), (70, 14, 10), (255, 190, 60), True),
}


def box_uv(u, v, w, h, d):
    return {'up': (u + d, v, w, d), 'down': (u + d + w, v, w, d), 'east': (u, v + d, d, h), 'north': (u + d, v + d, w, h),
            'west': (u + d + w, v + d, d, h), 'south': (u + 2 * d + w, v + d, w, h)}


# (osso, pai, pivô, cubos [(origem, tamanho, uv)])
PARTS = [
    ('root', None, [0, 0, 0], []),
    ('body', 'root', [0, 14, 0], [([-5, 9, -8], [10, 9, 18], [0, 0])]),
    ('neck', 'body', [0, 15, -8], [([-2.5, 13, -15], [5, 5, 8], [56, 0])]),
    ('head', 'neck', [0, 16, -15], [([-4, 13, -23], [8, 7, 9], [0, 28]), ([-3, 19.5, -18], [1, 3, 2], [56, 14]), ([2, 19.5, -18], [1, 3, 2], [56, 14])]),
    ('tail1', 'body', [0, 13, 10], [([-3, 10, 10], [6, 6, 9], [56, 20])]),
    ('tail2', 'tail1', [0, 13, 19], [([-2, 11, 19], [4, 4, 10], [34, 28])]),
    ('wing_l', 'body', [5, 17, -2], []),
    ('wing_r', 'body', [-5, 17, -2], []),
    ('leg_fl', 'body', [3, 10, -5], [([2, 4, -6], [3, 6, 3], [88, 0])]),
    ('leg_fr', 'body', [-3, 10, -5], [([-5, 4, -6], [3, 6, 3], [88, 0])]),
    ('leg_bl', 'body', [3, 10, 6], [([2, 4, 5], [3, 6, 3], [88, 0])]),
    ('leg_br', 'body', [-3, 10, 6], [([-5, 4, 5], [3, 6, 3], [88, 0])]),
]
WING = (100, 12, 26, 26)  # região da asa (plano)


def geometry():
    bones = []
    for name, parent, pivot, cubes in PARTS:
        b = {'name': name, 'pivot': pivot}
        if parent:
            b['parent'] = parent
        cs = [{'origin': o, 'size': s, 'uv': uv} for o, s, uv in cubes]
        if name in ('wing_l', 'wing_r'):
            sx = 1 if name == 'wing_l' else -1
            u, v, w, h = WING
            o = [5, 17, -8] if sx > 0 else [-23, 17, -8]
            cs.append({'origin': o, 'size': [18, 0, 16], 'uv': {'up': {'uv': [u, v], 'uv_size': [w, h]}, 'down': {'uv': [u, v + h], 'uv_size': [w, -h]}}})
        if cs:
            b['cubes'] = cs
        bones.append(b)
    return {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': 'geometry.tensura.dragon', 'texture_width': TW, 'texture_height': TH,
                        'visible_bounds_width': 5, 'visible_bounds_height': 4, 'visible_bounds_offset': [0, 1.2, 0]},
        'bones': bones}]}


def texture(main, dark, glow):
    im = Image.new('RGBA', (TW, TH), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    A = lambda c, a=255: (c[0], c[1], c[2], a)
    for _, _, _, cubes in PARTS:
        for o, s, uv in cubes:
            for f, (x, y, w, h) in box_uv(uv[0], uv[1], int(s[0]), int(s[1]), int(s[2])).items():
                d.rectangle([x, y, x + w - 1, y + h - 1], fill=A(dark if f == 'down' else main))
                for i in range(x, x + w, 2):  # escamas
                    d.point((i, y + (i % 3)), fill=A(dark))
                if f == 'up' and w >= 4:
                    d.line([x + w // 2, y, x + w // 2, y + h - 1], fill=A(glow))  # crista brilhante
    # olhos (frente da cabeça: north de (0,28) 8x7x9)
    _, (x, y, w, h) = 'north', box_uv(0, 28, 8, 7, 9)['north']
    d.rectangle([x + 1, y + 2, x + 2, y + 3], fill=A(glow)); d.rectangle([x + w - 3, y + 2, x + w - 2, y + 3], fill=A(glow))
    u, v, w, h = WING
    for half in (0, h):
        d.polygon([(u, v + half), (u + w - 1, v + half + 4), (u + w - 1, v + half + h - 1), (u, v + half + h - 6)], fill=A(dark, 230))
        for k in range(4):
            d.line([u, v + half, u + w - 1, v + half + 4 + k * 6], fill=A(glow, 220))
    return im


ANIM = {
    'format_version': '1.8.0',
    'animations': {
        'animation.tensura.dragon.fly': {
            'loop': True,
            'bones': {
                'root': {'position': [0, 'math.sin(query.life_time * 120) * 1.5', 0]},
                'wing_l': {'rotation': [0, 0, 'math.sin(query.life_time * 300) * 35']},
                'wing_r': {'rotation': [0, 0, '-math.sin(query.life_time * 300) * 35']},
                'tail1': {'rotation': [0, 'math.sin(query.life_time * 150) * 12', 0]},
                'tail2': {'rotation': [0, 'math.sin(query.life_time * 150 - 40) * 18', 0]},
                'neck': {'rotation': ['math.sin(query.life_time * 90) * 5', 0, 0]},
                'leg_fl': {'rotation': [30, 0, 0]}, 'leg_fr': {'rotation': [30, 0, 0]},
                'leg_bl': {'rotation': [40, 0, 0]}, 'leg_br': {'rotation': [40, 0, 0]},
            },
        },
    },
}


def client(name, glow):
    return {'format_version': '1.10.0', 'minecraft:client_entity': {'description': {
        'identifier': f'tensura:{name}',
        'materials': {'default': 'entity_alphatest'},
        'textures': {'default': f'textures/entity/tensura/{name}'},
        'geometry': {'default': 'geometry.tensura.dragon'},
        'render_controllers': ['controller.render.tensura.dragon'],
        'animations': {'fly': 'animation.tensura.dragon.fly'},
        'scripts': {'animate': ['fly'], 'scale': '1.6'},
    }}}


RC = {'format_version': '1.8.0', 'render_controllers': {'controller.render.tensura.dragon': {
    'geometry': 'Geometry.default', 'materials': [{'*': 'Material.default'}], 'textures': ['Texture.default'],
    'ignore_lighting': True}}}


def main(rp, bp):
    for sub in ('models/entity', 'animations', 'render_controllers', 'entity', 'textures/entity/tensura'):
        os.makedirs(os.path.join(rp, sub), exist_ok=True)
    os.makedirs(bp, exist_ok=True)
    json.dump(geometry(), open(os.path.join(rp, 'models/entity/tensura_dragon.geo.json'), 'w'), indent=1)
    json.dump(ANIM, open(os.path.join(rp, 'animations/tensura_dragon.animation.json'), 'w'), indent=1)
    json.dump(RC, open(os.path.join(rp, 'render_controllers/tensura_dragon.render_controllers.json'), 'w'), indent=1)
    for name, (main_c, dark, glow, fire) in DRAGONS.items():
        texture(main_c, dark, glow).save(os.path.join(rp, f'textures/entity/tensura/{name}.png'))
        json.dump(client(name, glow), open(os.path.join(rp, f'entity/{name}.entity.json'), 'w'), indent=2)
        spec = dict(hp=150, dmg=14, speed=0.3, mode='hover', box=(1.6, 1.4), fire_immune=fire, knockback_resist=True)
        json.dump(behavior(name, spec), open(os.path.join(bp, f'{name}.json'), 'w'), indent=2)
    print(f'dragões: {len(DRAGONS)} entidades')


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
