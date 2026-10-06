"""Baut einen Rezept-Index aus den Mod-Jars (fuer die Reihenfolge-Pruefung im Questbuch).

Aufruf:  python3 pack/quests/rezepte.py <mods-ordner> <ausgabe.json>
Ausgabe: {"rezepte": {item: [[zutat, ...], ...]}, "tags": {tag: [item, ...]}}
Zutaten-Tags sind aufgeloest. Erkannt wird grob: alles unter Ergebnis-Schluesseln
(result, output, ...) ist Ergebnis, alle anderen Item-IDs sind Zutaten.
"""
import json, os, re, sys, zipfile
from collections import defaultdict

AUS = {'result', 'results', 'output', 'outputs', 'main_output', 'item_output', 'secondary_output',
       'outputitem', 'output_item', 'secondary', 'byproduct', 'byproducts', 'item_outputs', 'outputs_item',
       'result_item', 'main', 'chanceoutput', 'chance_output', 'output_left', 'output_right', 'secondaryoutput'}
ID = re.compile(r'^[a-z0-9_.-]+:[a-z0-9_./-]+$')
REZ = re.compile(r'^data/([^/]+)/recipes?/(.+)\.json$')
TAG = re.compile(r'^data/([^/]+)/tags/items?/(.+)\.json$')


def sammeln(v, aus, ein, ist_aus=False):
    if isinstance(v, dict):
        for k, x in v.items():
            kl = k.lower()
            if kl in ('fluid', 'fluids', 'fluid_input', 'fluidinput', 'type', 'neoforge:conditions', 'conditions', 'group', 'category'):
                continue
            neu = ist_aus or kl in AUS
            if isinstance(x, str) and kl in ('item', 'id', 'tag', 'ingredient', 'base'):
                if ID.match(x):
                    (aus if neu else ein).add(('#' if kl == 'tag' else '') + x)
            elif isinstance(x, str) and kl in AUS and ID.match(x):
                aus.add(x)
            else:
                sammeln(x, aus, ein, neu)
    elif isinstance(v, list):
        for x in v:
            if isinstance(x, str) and ID.match(x.lstrip('#')):
                (aus if ist_aus else ein).add(x)
            else:
                sammeln(x, aus, ein, ist_aus)


def main():
    mods, ziel = sys.argv[1], sys.argv[2]
    rezepte = defaultdict(list)
    tags = defaultdict(set)
    for f in sorted(os.listdir(mods)):
        if not f.endswith('.jar'):
            continue
        try:
            z = zipfile.ZipFile(os.path.join(mods, f))
        except zipfile.BadZipFile:
            continue
        for n in z.namelist():
            m, t = REZ.match(n), TAG.match(n)
            if not (m or t):
                continue
            try:
                d = json.loads(z.read(n).decode('utf-8-sig'))
            except Exception:
                continue
            if t:
                name = f'{t.group(1)}:{t.group(2)}'
                for e in d.get('values', []):
                    e = e.get('id') if isinstance(e, dict) else e
                    if isinstance(e, str):
                        tags[name].add(e)
                continue
            aus, ein = set(), set()
            sammeln(d, aus, ein)
            for a in aus:
                if not a.startswith('#'):
                    rezepte[a].append(sorted(ein - aus))
    # Tags aufloesen (verschachtelt)
    def aufl(t, seen=()):
        r = set()
        for e in tags.get(t, ()):
            if e.startswith('#'):
                if e[1:] not in seen:
                    r |= aufl(e[1:], seen + (t,))
            else:
                r.add(e)
        return r
    tags_fertig = {t: sorted(aufl(t)) for t in tags}
    out = {'rezepte': {k: v for k, v in sorted(rezepte.items())}, 'tags': tags_fertig}
    json.dump(out, open(ziel, 'w'), separators=(',', ':'))
    print(f'{len(rezepte)} Items mit Rezept, {len(tags_fertig)} Tags -> {ziel}')


if __name__ == '__main__':
    main()
