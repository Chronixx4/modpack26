"""Schreibt site/bosse.json fuer die Boss-Datenbank der Webseite.
Quelle ist dieselbe wie beim Bossfuehrer-Buch (kill-Quests, pack/quests/bossbuch.py).
Aufruf: python3 tools/bosse_json.py   (nach Aenderungen an den Boss-Quests, Ergebnis mit committen)"""
import json
import pathlib
import re
import sys

root = pathlib.Path(__file__).resolve().parent.parent
sys.path.insert(0, str(root / 'pack' / 'quests'))
import bossbuch as bb  # noqa: E402

FARBE = re.compile(r'&[0-9a-fk-or]')
mods = [{'id': m[0], 'name': m[1], 'text': m[3]} for m in bb.MODS]
out = []
for b in bb.lade():
    mod = b['entity'].split(':')[0]
    mod = mod if mod in bb.NAME else 'sonstige'
    text = FARBE.sub('', b['text']).strip()
    out.append({'name': b['titel'], 'id': b['entity'], 'mod': mod, 'anzahl': b['anzahl'],
                'text': [a.strip() for a in text.split('\n\n') if a.strip()],
                'beute': b['items']})
benutzt = {b['mod'] for b in out}
daten = {'mods': [m for m in mods if m['id'] in benutzt], 'bosse': out}
(root / 'site' / 'bosse.json').write_text(json.dumps(daten, ensure_ascii=False, separators=(',', ':')), encoding='utf-8')
print(len(out), 'Bosse in', len(daten['mods']), 'Gruppen')
