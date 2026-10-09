"""Zieht die Webseite nach einem Build nach: schreibt site/modliste.json aus MODLISTE.txt und setzt
die Mod-Anzahl in site/index.html (Kachel und Splash-Text).
Aufruf: python3 tools/seite_aktualisieren.py   (nach jedem Build, Ergebnis mit committen)
Von Hand bleiben: Changelog, Questzahlen und die Rezepte im Guide (site/index.html)."""
import json, re, pathlib
root = pathlib.Path(__file__).resolve().parent.parent
kopf, cats = [], []
for z in (root / 'MODLISTE.txt').read_text(encoding='utf-8-sig').splitlines():
    m = re.match(r'== (.+) \((\d+)\) ==', z)
    if m:
        cats.append({'name': m.group(1), 'mods': []}); continue
    if not cats or not z.strip():
        continue
    t = re.match(r'\s+(.+?)\s{2,}(\S.*)$', z)
    if t:
        name, rest = t.groups()
    else:  # sehr langer Name: nur ein Leerzeichen vor der Version
        name, rest = z.strip().rsplit(None, 1)
    tag = ''
    for k, v in (('(nur Client)', 'client'), ('(nur Server)', 'server'), ('(CurseForge)', 'cf')):
        if k in rest:
            rest = rest.replace(k, '').strip(); tag = v
    cats[-1]['mods'].append({'n': name.strip(), 'v': rest.strip(), 't': tag})
titel = (root / 'MODLISTE.txt').read_text(encoding='utf-8-sig').splitlines()[0]
out = {'titel': titel, 'kategorien': cats}
(root / 'site' / 'modliste.json').write_text(json.dumps(out, ensure_ascii=False, separators=(',', ':')), encoding='utf-8')
n = sum(len(c['mods']) for c in cats)
import re as _re
idx = root / 'site' / 'index.html'
h = idx.read_text(encoding='utf-8')
h2 = _re.sub(r'(<b id="statMods">)\d+(</b>)', r'\g<1>%d\2' % n, h)
h2 = _re.sub(r"'\d+ Mods!'", "'%d Mods!'" % n, h2)
if h2 != h:
    idx.write_text(h2, encoding='utf-8')
print(n, 'Mods in', len(cats), 'Kategorien;', 'index.html angepasst' if h2 != h else 'index.html unveraendert')
