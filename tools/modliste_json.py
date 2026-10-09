"""Wandelt MODLISTE.txt in site/modliste.json um (fuer die Modliste auf der Webseite).
Aufruf: python3 tools/modliste_json.py   (nach jedem Build mitlaufen lassen und committen)"""
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
print(sum(len(c['mods']) for c in cats), 'Mods in', len(cats), 'Kategorien')
