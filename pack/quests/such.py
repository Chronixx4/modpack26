"""Nachschlagen beim Schreiben von Quests.

Daten-Ordner (ids.json + rezepte.json) per Umgebungsvariable QUEST_DATEN.
  such.py name <worte...>     IDs, deren Name oder ID alle Worte enthaelt (Items, Mobs, Dims, Strukturen, Biome)
  such.py mod <namespace>     alle Items einer Mod mit Namen
  such.py rezept <id>         Zutaten aller Rezepte fuer das Item
  such.py wofuer <id>         Items, deren Rezept dieses Item braucht
  such.py art <art> <namespace>   entities|dimensions|structures|biomes einer Mod
"""
import json, os, sys

D = os.environ.get('QUEST_DATEN', '.')
ids = json.load(open(os.path.join(D, 'ids.json')))
names = ids.get('names', {})
cmd, args = sys.argv[1], sys.argv[2:]
if cmd == 'name':
    w = [a.lower() for a in args]
    for art in ('items', 'entities', 'dimensions', 'structures', 'biomes'):
        for i in sorted(ids[art]):
            t = (i + ' ' + names.get(i, '')).lower()
            if all(x in t for x in w):
                print(f'{art[:-1]:10} {i:60} {names.get(i, "")}')
elif cmd == 'mod':
    for i in sorted(ids['items']):
        if i.split(':')[0] == args[0]:
            print(f'{i:60} {names.get(i, "")}')
elif cmd == 'art':
    for i in sorted(ids[args[0]]):
        if i.split(':')[0] == args[1]:
            print(i, names.get(i, ''))
else:
    rez = json.load(open(os.path.join(D, 'rezepte.json')))
    if cmd == 'rezept':
        for r in rez['rezepte'].get(args[0], []):
            print(' + '.join(r))
        if args[0] not in rez['rezepte']:
            print('(kein Rezept gefunden - evtl. Maschine/Mob-Drop/Code-Rezept)')
    elif cmd == 'wofuer':
        tags = {t for t, its in rez['tags'].items() if args[0] in its}
        for out, rs in rez['rezepte'].items():
            if any(args[0] in r or any(e[1:] in tags for e in r if e.startswith('#')) for r in rs):
                print(f'{out:60} {names.get(out, "")}')
