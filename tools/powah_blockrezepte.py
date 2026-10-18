#!/usr/bin/env python3
"""Erzeugt Block-Varianten fuer Powah-Energizing-Orb-Rezepte.

Pro Rezept mit Barren- oder Edelstein-Zutaten (Tag c:ingots/X oder c:gems/X)
entsteht ein Rezept mit dem Block (c:storage_blocks/X): Zutaten, Ergebnis-
Menge und RF je x9. Rezepte ohne solche Zutaten werden nicht angefasst.

Aufruf: python3 tools/powah_blockrezepte.py <powah.jar>
Schreibt nach pack/kubejs/data/powah/recipe/energizing/block_*.json
"""
import json, re, sys, zipfile
from pathlib import Path

ZIEL = Path(__file__).resolve().parent.parent / 'pack/kubejs/data/powah/recipe/energizing'
TAG = re.compile(r'^c:(ingots|gems)/(\w+)$')


def block_variante(rezept):
    zutaten = []
    for z in rezept['ingredients']:
        m = TAG.match(z.get('tag', ''))
        if not m:
            return None
        zutaten.append({'tag': f'c:storage_blocks/{m.group(2)}'})
    return {
        'type': rezept['type'],
        'energy': rezept['energy'] * 9,
        'ingredients': zutaten,
        'result': {'count': rezept['result']['count'] * 9, 'id': rezept['result']['id']},
    }


def main(jar):
    ZIEL.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(jar) as z:
        for name in sorted(z.namelist()):
            if not re.match(r'data/powah/recipe/energizing/[^/]+\.json$', name):
                continue
            neu = block_variante(json.loads(z.read(name)))
            if neu:
                ziel = ZIEL / ('block_' + Path(name).name)
                ziel.write_text(json.dumps(neu, indent=2) + '\n')
                print('geschrieben:', ziel.relative_to(ZIEL.parent.parent.parent.parent.parent.parent))


if __name__ == '__main__':
    main(sys.argv[1])
