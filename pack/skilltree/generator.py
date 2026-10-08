"""Schreibt den Chronicle-Skilltree fuer Pufferfish's Skills aus spec.py.

Aufruf: python3 pack/skilltree/generator.py
Ergebnis: pack/kubejs/data/puffish_skills/puffish_skills/ (mit committen).
Aenderungen nur in spec.py, nie von Hand in den JSONs. Knoten-IDs nicht umbenennen (Fortschritt).
"""
import json
import math
import os
import shutil
import sys

HIER = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HIER)
import spec  # noqa: E402

OUT = os.path.join(HIER, '..', 'kubejs', 'data', 'puffish_skills', 'puffish_skills')
KAT = 'chronicle'
R0, SCHRITT, FAECHER, SKALA = 120, 56, (-30, -10, 10, 30), 0.8


def icon(item):
    return {'type': 'item', 'data': {'item': item}}


def fmt(w, einheit):
    v = round(w * 10) / 10
    v = int(v) if v == int(v) else v
    return ('−' if v < 0 else '+') + str(abs(v)) + einheit


def reward(e, nid, wert):
    if isinstance(e, spec.S):
        return {'type': 'puffish_skills:tag', 'data': {'tag': f'chr.{e.name}.{nid}.{round(wert * 10) / 10}'}}
    v = (wert / 100 if e.einheit == '%' else wert) * e.roh
    return {'type': 'puffish_skills:attribute',
            'data': {'attribute': e.attr, 'value': round(v, 4), 'operation': e.op}}


def definition(nid, titel, symbol, effekte, werte, kosten, rahmen, extra=None):
    d = {
        'title': titel,
        'description': '\n'.join(f'{fmt(w, e.einheit)} {e.label}' for e, w in zip(effekte, werte)) or 'Startpunkt',
        'icon': icon(symbol),
        'frame': {'type': 'advancement', 'data': {'frame': rahmen}},
        'cost': kosten,
        'rewards': [reward(e, nid, w) for e, w in zip(effekte, werte)],
    }
    if extra:
        d.update(extra)
    return d


def schreibe(pfad, daten):
    pfad = os.path.join(OUT, pfad)
    os.makedirs(os.path.dirname(pfad), exist_ok=True)
    with open(pfad, 'w', encoding='utf-8') as f:
        json.dump(daten, f, ensure_ascii=False, indent=2)
        f.write('\n')


def erfahrung():
    deaktiviert = {'limit_per_chunk': 15, 'reset_after_seconds': 300}
    ore = {'operations': [{'type': 'get_mined_block_state'}, {'type': 'puffish_skills:test', 'data': {'block': '#c:ores'}}]}
    tief = {'operations': [{'type': 'get_mined_block_state'},
                           {'type': 'puffish_skills:test', 'data': {'block': '#c:ores_in_ground/deepslate'}}]}
    seide = {'operations': [{'type': 'get_tool_item_stack'},
                            {'type': 'puffish_skills:test', 'data': {'nbt': '{Enchantments:[{id:"minecraft:silk_touch"}]}'}}]}
    leben = {'operations': [{'type': 'get_killed_living_entity'}, {'type': 'get_max_health'}]}
    return {
        'level_limit': 130,
        'experience_per_level': {'type': 'expression', 'data': {'expression': 'min(30 + level * 6, 400)'}},
        'sources': [
            {'type': 'puffish_skills:kill_entity', 'data': {
                'variables': {'max_health': leben},
                'experience': [
                    {'condition': 'max_health >= 100', 'expression': 'max_health * 1.5'},
                    {'condition': 'max_health < 100', 'expression': 'max_health / 4'},
                ],
                'anti_farming': deaktiviert}},
            {'type': 'puffish_skills:mine_block', 'data': {
                'variables': {'silk_touch': seide, 'erz': ore, 'tief': tief},
                'experience': [
                    {'condition': '!silk_touch & erz & tief', 'expression': '12'},
                    {'condition': '!silk_touch & erz & !tief', 'expression': '10'},
                ]}},
        ],
    }


def main():
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    defs, skills, kanten = {}, {}, []

    def knoten(nid, x, y, d, root=False):
        defs[nid] = d
        skills[nid] = {'x': round(x * SKALA), 'y': round(y * SKALA), 'definition': nid}
        if root:
            skills[nid]['root'] = True

    defs_kern = {'title': 'Chronicle', 'description': 'Der Ausgangspunkt. Von hier führen fünf Wege ab.',
                 'icon': icon('kubejs:chronicle_core'),
                 'frame': {'type': 'advancement', 'data': {'frame': 'challenge'}}, 'cost': 0, 'rewards': []}
    knoten('kern', 0, 0, defs_kern, root=True)

    for a in spec.AESTE:
        rad = math.radians(a['winkel'])
        wx, wy = math.cos(rad) * R0, math.sin(rad) * R0
        # Wurzel
        n, t, sym, eff = a['wurzel']
        wid = f"{a['id']}_w"
        knoten(wid, wx, wy, definition(wid, n, sym, eff, [e.faktor for e in eff], 1, 'goal',
                                       {'extra_description': a['name']}))
        kanten.append(['kern', wid])
        ende = {}
        for li, (lname, lsym, werte, text, eff) in enumerate(a['linien']):
            d = math.radians(a['winkel'] + FAECHER[li])
            vor = wid
            for k in range(5):
                s = (k + 2) * SCHRITT
                nid = f"{a['id']}_{li}_{k}"
                titel = f'{lname} {spec.STUFEN[k]}'
                rahmen = 'goal' if k == 4 else 'task'
                knoten(nid, wx + math.cos(d) * s, wy + math.sin(d) * s,
                       definition(nid, titel, lsym, eff, [werte[k] * e.faktor for e in eff], spec.KOSTEN[k], rahmen,
                                  {'extra_description': text}))
                kanten.append([vor, nid])
                vor = nid
            ende[li] = vor
        # Gipfel
        n, t, sym, eff = a['gipfel']
        gid = f"{a['id']}_g"
        s = 7.6 * SCHRITT
        knoten(gid, wx + math.cos(rad) * s, wy + math.sin(rad) * s,
               definition(gid, n, sym, eff, [e.faktor for e in eff], spec.GIPFEL_KOSTEN, 'challenge',
                          {'extra_description': t + ' Dafür müssen die beiden mittleren Linien voll ausgebaut sein.',
                           'required_skills': 2}))
        kanten.append([ende[1], gid])
        kanten.append([ende[2], gid])

    schreibe('config.json', {'version': 3, 'categories': [KAT]})
    schreibe(f'categories/{KAT}/category.json', {
        'unlocked_by_default': True,
        'starting_points': 3,
        'title': 'Chronicle',
        'description': 'Dein Weg durch Chronicle. Punkte bekommst du durch Kämpfe, Erze und Quests.',
        'icon': icon('kubejs:chronicle_core'),
        'background': 'textures/gui/advancements/backgrounds/stone.png',
    })
    schreibe(f'categories/{KAT}/definitions.json', defs)
    schreibe(f'categories/{KAT}/skills.json', skills)
    schreibe(f'categories/{KAT}/connections.json', {'normal': {'bidirectional': kanten}})
    schreibe(f'categories/{KAT}/experience.json', erfahrung())
    gesamt = sum(d['cost'] for d in defs.values())
    skripte = sorted({e.name for a in spec.AESTE for l in a['linien'] for e in l[4] if isinstance(e, spec.S)}
                     | {e.name for a in spec.AESTE for e in a['gipfel'][3] if isinstance(e, spec.S)})
    unsicher = sorted({e.attr for a in spec.AESTE
                       for e in [x for l in a['linien'] for x in l[4]] + a['gipfel'][3] + a['wurzel'][3]
                       if isinstance(e, spec.E) and e.pruefen})
    print(f'{len(skills)} Knoten, {len(kanten)} Verbindungen, {gesamt} Punkte fuer alles -> {os.path.normpath(OUT)}')
    print('Skript-Werte (Tag chr.<name>.*):', ', '.join(skripte))
    print('Noch zu pruefende Attribute:', len(unsicher))


if __name__ == '__main__':
    main()
