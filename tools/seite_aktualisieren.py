"""Zieht die Webseite nach einem Build nach: schreibt site/modliste.json aus MODLISTE.txt und setzt
die Mod-Anzahl in site/index.html (Kachel und Splash-Text).
Aufruf: python3 tools/seite_aktualisieren.py   (nach jedem Build, Ergebnis mit committen)
Setzt ausserdem die Version (aus pack/modrinth.index.json) und legt fehlende Changelog-Eintraege aus CLAUDE.md an (danach von Hand glaetten).
Von Hand bleiben: Questzahlen und die Rezepte im Guide (site/index.html)."""
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

# Version und Changelog
ver = json.loads((root / 'pack' / 'modrinth.index.json').read_text(encoding='utf-8'))['versionId']
h = idx.read_text(encoding='utf-8')
h3 = _re.sub(r'(<b id="statVer">)[^<]*(</b>)', r'\g<1>%s\2' % ver, h)
def vkey(v): return tuple(int(x) for x in v.split('.'))
have = set(_re.findall(r'<div class="ver"><b>([\d.]+)</b>', h3))
claude = (root / 'CLAUDE.md').read_text(encoding='utf-8')
neu = []
for v in sorted({ver} | set(_re.findall(r'^- (?:\*\*)?(0\.\d+\.\d+)\b', claude, _re.M)), key=vkey):
    if v in have or vkey(v) < vkey(sorted(have, key=vkey)[-3]):
        continue
    m = _re.search(r'^- (?:\*\*)?%s\b[^\n]*' % _re.escape(v), claude, _re.M)
    if not m:
        continue
    txt = _re.sub(r'^- [^:]*?:\s*', '', m.group(0))
    txt = _re.sub(r'[*`]', '', txt)
    txt = txt.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
    txt = _re.split(r'(?<=\.)\s', txt)[0][:300]
    li = '<li><div class="ver"><b>%s</b><span>Update</span></div><ul><li>%s</li></ul></li>' % (v, txt)
    # vor dem ersten Eintrag mit kleinerer Version einsetzen
    pos = None
    for mm in _re.finditer(r'<li><div class="ver"><b>([\d.]+)</b>', h3):
        if vkey(mm.group(1)) < vkey(v):
            pos = mm.start(); break
    if pos is None:
        continue
    h3 = h3[:pos] + li + h3[pos:]
    neu.append(v)
if h3 != h:
    idx.write_text(h3, encoding='utf-8')
print('Version', ver, '| neue Changelog-Eintraege:', ', '.join(neu) or 'keine')
