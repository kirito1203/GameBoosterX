#!/usr/bin/env python3
"""Gera as entidades de invocação (comportamento + cliente).

Uso: python3 tools/gen_entities.py <pasta resource_pack/entity do bedrock-samples>

O visual reaproveita geometrias, animações e texturas do próprio jogo (referenciadas,
não copiadas). O cliente de cada invocação é derivado da definição vanilla do mob base,
trocando apenas o identificador e a escala.
"""
import json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
BP = os.path.join(HERE, '..', 'BP', 'entities')
RP = os.path.join(HERE, '..', 'RP', 'entity')

SUMMONS = {
    # id: base vanilla, vida, dano, velocidade, tipo, extras
    'hound_dog':         dict(base='wolf', hp=24, dmg=5, speed=0.34, mode='walk', box=(0.6, 0.85), scale=1.15),
    'basilisk':          dict(base='silverfish', hp=40, dmg=7, speed=0.28, mode='walk', box=(1.2, 0.8), scale=3.0),
    'daemon':            dict(base='vex', hp=60, dmg=10, speed=0.32, mode='hover', box=(0.7, 1.6), scale=2.0, fire_immune=True),
    'medium_elemental':  dict(base='blaze', hp=40, dmg=6, speed=0.28, mode='ranged', box=(0.6, 1.8), scale=1.0, projectile='minecraft:small_fireball', fire_immune=True),
    'greater_elemental': dict(base='iron_golem', hp=120, dmg=16, speed=0.25, mode='walk', box=(1.4, 2.9), scale=1.0, knockback_resist=True),
    'spirit':            dict(base='allay', hp=20, dmg=0, speed=0.3, mode='hover', box=(0.35, 0.6), scale=1.3, passive=True),
    'familiar':          dict(base='bat', hp=10, dmg=0, speed=0.3, mode='hover', box=(0.5, 0.9), scale=1.0, passive=True),
    'undead_warrior':    dict(base='zombie', hp=30, dmg=6, speed=0.27, mode='walk', box=(0.6, 1.9), scale=1.0, family_extra=['undead']),
    'undead_archer':     dict(base='skeleton', hp=24, dmg=4, speed=0.27, mode='ranged', box=(0.6, 1.9), scale=1.0, projectile='minecraft:arrow', family_extra=['undead']),
    'doppelganger':      dict(base='zombie', hp=30, dmg=0, speed=0.3, mode='decoy', box=(0.6, 1.8), scale=0.9375, texture='textures/entity/steve'),
}


def behavior(name, s):
    fam = ['tensura_summon', 'mob'] + s.get('family_extra', [])
    if s['mode'] == 'decoy':
        fam = ['tensura_summon', 'tensura_decoy', 'player', 'mob']  # 'player' faz os monstros mirarem a cópia
    c = {
        'minecraft:type_family': {'family': fam},
        'minecraft:health': {'value': s['hp'], 'max': s['hp']},
        'minecraft:movement': {'value': s['speed']},
        'minecraft:collision_box': {'width': s['box'][0], 'height': s['box'][1]},
        'minecraft:physics': {},
        'minecraft:pushable': {'is_pushable': True, 'is_pushable_by_piston': True},
        'minecraft:nameable': {},
        'minecraft:jump.static': {},
        'minecraft:breathable': {'total_supply': 15, 'suffocate_time': 0, 'breathes_water': True},
        'minecraft:follow_range': {'value': 32, 'max': 32},
        'minecraft:tameable': {'probability': 0.0, 'tame_items': 'minecraft:structure_void', 'tame_event': {'event': 'tensura:on_tame', 'target': 'self'}},
        # Segurança: a invocação some sozinha mesmo se o script perder o controle dela (chunk descarregado).
        'minecraft:timer': {'looping': False, 'time': 200, 'time_down_event': {'event': 'tensura:despawn', 'target': 'self'}},
        'minecraft:damage_sensor': {'triggers': [{'cause': 'fall', 'deals_damage': 'no'}]},
        'minecraft:behavior.float': {'priority': 0},
        'minecraft:behavior.follow_owner': {'priority': 4, 'speed_multiplier': 1.2, 'start_distance': 8, 'stop_distance': 3},
        'minecraft:behavior.look_at_player': {'priority': 9, 'look_distance': 6},
        'minecraft:behavior.random_look_around': {'priority': 10},
    }
    if s.get('fire_immune'):
        c['minecraft:fire_immune'] = {}
    if s.get('knockback_resist'):
        c['minecraft:knockback_resistance'] = {'value': 1.0}
    if s['mode'] in ('hover',):
        c.update({
            'minecraft:navigation.hover': {'avoid_damage_blocks': True, 'can_path_from_air': True, 'can_path_over_water': True, 'can_sink': False},
            'minecraft:movement.hover': {},
            'minecraft:can_fly': {},
            'minecraft:flying_speed': {'value': 0.12},
            'minecraft:behavior.random_hover': {'priority': 9, 'hover_height': [1, 3], 'y_dist': 4, 'xz_dist': 6, 'interval': 1, 'y_offset': -1},
            'minecraft:physics': {'has_gravity': False},
        })
    else:
        c.update({
            'minecraft:navigation.walk': {'can_path_over_water': True, 'avoid_damage_blocks': True, 'can_open_doors': False},
            'minecraft:movement.basic': {},
            'minecraft:can_climb': {},
            'minecraft:behavior.random_stroll': {'priority': 8, 'speed_multiplier': 0.8},
        })
    hostile_targets = {'priority': 3, 'must_see': True, 'reselect_targets': True, 'within_radius': 16,
                       'entity_types': [{'filters': {'test': 'is_family', 'subject': 'other', 'value': 'monster'}, 'max_dist': 16}]}
    hurt_by = {'priority': 1, 'entity_types': {'filters': {'all_of': [
        {'test': 'is_family', 'subject': 'other', 'operator': '!=', 'value': 'player'},
        {'test': 'is_family', 'subject': 'other', 'operator': '!=', 'value': 'tensura_summon'}]}}}
    if not s.get('passive') and s['mode'] != 'decoy':
        c['minecraft:attack'] = {'damage': s['dmg']}
        c['minecraft:behavior.owner_hurt_by_target'] = {'priority': 1}
        c['minecraft:behavior.owner_hurt_target'] = {'priority': 2}
        c['minecraft:behavior.hurt_by_target'] = hurt_by
        c['minecraft:behavior.nearest_attackable_target'] = hostile_targets
        if s['mode'] == 'ranged':
            c['minecraft:shooter'] = {'def': s['projectile']}
            c['minecraft:behavior.ranged_attack'] = {'priority': 3, 'attack_interval_min': 1.5, 'attack_interval_max': 2.5, 'attack_radius': 15}
        else:
            c['minecraft:behavior.melee_attack'] = {'priority': 3, 'speed_multiplier': 1.25, 'track_target': True}
    if s['mode'] == 'decoy':
        c['minecraft:behavior.random_stroll'] = {'priority': 5, 'speed_multiplier': 1.0}
        c.pop('minecraft:behavior.follow_owner', None)
    return {
        'format_version': '1.21.0',
        'minecraft:entity': {
            'description': {'identifier': f'tensura:{name}', 'is_spawnable': False, 'is_summonable': True},
            'component_groups': {
                'tensura:tamed': {'minecraft:is_tamed': {}},
                'tensura:despawn': {'minecraft:instant_despawn': {}},
            },
            'components': c,
            'events': {
                'tensura:on_tame': {'add': {'component_groups': ['tensura:tamed']}},
                'tensura:despawn': {'add': {'component_groups': ['tensura:despawn']}},
            },
        },
    }


def client(name, s, ref_dir):
    path = os.path.join(ref_dir, f"{s['base']}.entity.json")
    text = re.sub(r'//.*', '', open(path).read())
    data = json.loads(text)
    desc = data['minecraft:client_entity']['description']
    desc['identifier'] = f'tensura:{name}'
    desc.pop('spawn_egg', None)
    if s.get('texture'):
        desc['textures'] = {k: s['texture'] for k in desc['textures']}
    scripts = desc.setdefault('scripts', {})
    if s['scale'] != 1.0:
        base = scripts.get('scale', '1.0')
        scripts['scale'] = f"({base}) * {s['scale']}"  # Molang: preserva a escala original do mob base
    return {'format_version': data.get('format_version', '1.10.0'), 'minecraft:client_entity': {'description': desc}}


def main():
    ref = sys.argv[1]
    os.makedirs(BP, exist_ok=True)
    os.makedirs(RP, exist_ok=True)
    for name, s in SUMMONS.items():
        json.dump(behavior(name, s), open(os.path.join(BP, f'{name}.json'), 'w'), indent=2)
        json.dump(client(name, s, ref), open(os.path.join(RP, f'{name}.entity.json'), 'w'), indent=2)
    print(f'{len(SUMMONS)} entidades geradas')


if __name__ == '__main__':
    main()
