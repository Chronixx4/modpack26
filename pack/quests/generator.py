"""Erzeugt das FTB-Quests-Buch aus inhalt/*.py.

Aufruf:  python3 pack/quests/generator.py [--ids ids.json] [--rezepte rezepte.json] [--bilder ordner]
Ausgabe: pack/config/ftbquests/quests/  (data.snbt, chapter_groups.snbt, chapters/*.snbt,
         reward_tables/*.snbt, lang/en_us.snbt)

--ids:     jede Item-/Mob-/Dimensions-/Struktur-/Biom-ID wird geprueft (Fehler brechen ab)
--rezepte: Reihenfolge-Pruefung: braucht das Rezept von Quest B ein Item aus Quest A
           (im selben Kapitel), muss A vor B liegen (Warnungen)
--bilder:  Vorschaubild (PNG) pro Kapitel

Quest-IDs sind Hashes aus Kapitel + Schluessel (stabil). FTB Quests liest IDs als
vorzeichenbehaftete 64-Bit-Zahl, deshalb hoechstens 63 Bit.
"""
import argparse, hashlib, importlib, json, os, shutil, sys
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import dsl  # noqa: E402

OUT = os.path.join(HERE, '..', 'config', 'ftbquests', 'quests')
TEILE = ['start', 'create', 'ae2_mekanism', 'technik_a', 'technik_b', 'magie_a', 'magie_b', 'abenteuer_a', 'abenteuer_b']
BUCH_TITEL = 'Unser Modpack'
BUCH_ICON = 'minecraft:writable_book'
FORMEN = {'circle', 'square', 'rsquare', 'diamond', 'pentagon', 'hexagon', 'octagon', 'heart', 'gear', 'star'}
DX, DY = 2.0, 1.5


class F(float):
    pass


class L(int):
    pass


def hid(*parts):
    n = int(hashlib.sha1('/'.join(parts).encode()).hexdigest()[:16], 16) & 0x7FFFFFFFFFFFFFFF
    n |= 0x1000000000000000  # keine fuehrende Null, nie 0
    return f'{n:016X}'


def snbt(v, ind=0):
    t = '\t' * ind
    if isinstance(v, dict):
        if not v:
            return '{ }'
        return '{\n' + ''.join(f'{t}\t{k}: {snbt(x, ind + 1)}\n' for k, x in v.items()) + t + '}'
    if isinstance(v, list):
        if not v:
            return '[ ]'
        return '[\n' + ''.join(f'{t}\t{snbt(x, ind + 1)}\n' for x in v) + t + ']'
    if isinstance(v, bool):
        return 'true' if v else 'false'
    if isinstance(v, F):
        return f'{float(v)}f'
    if isinstance(v, float):
        return f'{v}d'
    if isinstance(v, L):
        return f'{int(v)}L'
    if isinstance(v, int):
        return str(v)
    return json.dumps(v, ensure_ascii=False)


def schreib(pfad, daten):
    os.makedirs(os.path.dirname(pfad), exist_ok=True)
    with open(pfad, 'w', encoding='utf-8', newline='\n') as f:
        f.write(snbt(daten) + '\n')


class Pruefer:
    def __init__(self, path):
        self.ok = path is not None
        self.fehler, self.warnungen = [], []
        if self.ok:
            d = json.load(open(path, encoding='utf-8'))
            self.sets = {k: set(d.get(k, [])) for k in ('items', 'entities', 'dimensions', 'structures', 'biomes')}
            self.sets['structures'] |= {'#' + t for t in d.get('structure_tags', {})}

    def __call__(self, art, rid, wo):
        if self.ok and self.sets[art] and rid not in self.sets[art]:
            self.fehler.append(f'{wo}: {art[:-1]} {rid} unbekannt')


def task_nbt(t, tid, chk, wo, abgabe):
    if t == 'check':
        return {'id': tid, 'type': 'checkmark'}
    if isinstance(t, str):
        t = ('item', t, 1)
    art = t[0]
    if art == 'item':
        chk('items', t[1], wo)
        d = {}
        if abgabe:
            d['consume_items'] = True
        if len(t) > 2 and t[2] > 1:
            d['count'] = L(t[2])
        d.update({'id': tid, 'item': {'count': 1, 'id': t[1]}, 'type': 'item'})
        return d
    if art == 'kill':
        chk('entities', t[1], wo)
        return {'entity': t[1], 'id': tid, 'type': 'kill', 'value': L(t[2] if len(t) > 2 else 1)}
    if art == 'dim':
        chk('dimensions', t[1], wo)
        return {'dimension': t[1], 'id': tid, 'type': 'dimension'}
    if art == 'struct':
        chk('structures', t[1], wo)
        return {'id': tid, 'structure': t[1], 'type': 'structure'}
    if art == 'biome':
        chk('biomes', t[1], wo)
        return {'biome': t[1], 'id': tid, 'type': 'biome'}
    raise SystemExit(f'{wo}: unbekannte Aufgabe {t}')


def reward_nbt(r, rid, chk, wo, tabellen):
    if isinstance(r, int):
        return {'id': rid, 'type': 'xp_levels', 'xp_levels': r}
    if isinstance(r, str) and r in tabellen:
        return {'id': rid, 'table_id': L(int(tabellen[r], 16)), 'type': 'loot'}
    if isinstance(r, tuple) and r[0] == 'item':
        chk('items', r[1], wo)
        d = {}
        if len(r) > 2 and r[2] > 1:
            d['count'] = r[2]
        d.update({'id': rid, 'item': {'count': 1, 'id': r[1]}, 'type': 'item'})
        return d
    raise SystemExit(f'{wo}: unbekannte Belohnung {r!r}')


def layout(quests):
    """Spalte = Tiefe (laengster Pfad im Kapitel), Zeile nach Eltern (Schwerpunkt), Hauptlinie auf 0."""
    keys = {q['key'] for q in quests}
    nach = {q['key']: [d for d in q['nach'] if d in keys] for q in quests}
    tiefe = {}

    def t(k, stack=()):
        if k in stack:
            raise SystemExit(f'Kreis in Abhaengigkeiten bei {k}')
        if k not in tiefe:
            tiefe[k] = 1 + max([t(d, stack + (k,)) for d in nach[k]] or [-1])
        return tiefe[k]

    for q in quests:
        t(q['key'])
        if 'spalte' in q:
            tiefe[q['key']] = q['spalte']
    spalten = defaultdict(list)
    for q in quests:
        spalten[tiefe[q['key']]].append(q)
    y = {}
    for s in sorted(spalten):
        wunsch = []
        for i, q in enumerate(spalten[s]):
            if 'zeile' in q:
                w = q['zeile'] * DY
            elif q.get('haupt'):
                w = 0.0
            else:
                eltern = [y[d] for d in nach[q['key']] if d in y]
                w = sum(eltern) / len(eltern) if eltern else 0.0
            fest = 'zeile' in q or q.get('haupt')
            wunsch.append((w, 0 if fest else 1, i, q))
        # Raster-Slots (Vielfache von DY): feste zuerst, dann freie in den naechsten freien Slot
        belegt = {}
        for w, frei, i, q in sorted(wunsch, key=lambda x: (x[1], abs(x[0]), x[2])):
            ziel = round(w / DY)
            if frei == 0 and ziel in belegt:
                raise SystemExit(f"{q['key']}: Zeile {ziel} in Spalte {s} doppelt belegt")
            d = 0
            while True:
                kand = [ziel] if d == 0 else ([ziel - d, ziel + d] if w <= ziel * DY else [ziel + d, ziel - d])
                frei_slot = [c for c in kand if c not in belegt]
                if frei_slot:
                    belegt[frei_slot[0]] = q
                    break
                d += 1
        wunsch = [(0, 0, 0, belegt[c]) for c in sorted(belegt)]
        pos = [c * DY for c in sorted(belegt)]
        for p, (_, _, _, q) in zip(pos, wunsch):
            y[q['key']] = p
    return {q['key']: (tiefe[q['key']] * DX, y[q['key']]) for q in quests}


def items_von(q):
    a = q['aufgaben'] if isinstance(q['aufgaben'], list) else [q['aufgaben']]
    r = []
    for t in a:
        if isinstance(t, str) and t != 'check':
            r.append(t)
        elif isinstance(t, tuple) and t[0] == 'item':
            r.append(t[1])
    return r


def rezept_pruefung(k, rez, warn):
    """Zutat (auch indirekt, 3 Ebenen) eines Quest-Items ist Item einer anderen Quest -> die muss Vorfahr sein."""
    rezepte, tags = rez['rezepte'], rez['tags']

    def noetig(item, tiefe=3):
        # Zutaten, die in JEDEM Rezept des Items vorkommen
        rs = rezepte.get(item)
        if not rs or tiefe == 0:
            return set()
        menge = None
        for r in rs:
            z = set()
            for e in r:
                if e.startswith('#'):
                    its = tags.get(e[1:], [])
                    if 0 < len(its) <= 2:
                        z |= set(its)
                else:
                    z.add(e)
            menge = z if menge is None else menge & z
        voll = set(menge)
        for e in menge:
            voll |= noetig(e, tiefe - 1)
        return voll

    wem = {}
    for q in k['quests']:
        for it in items_von(q):
            wem.setdefault(it, q['key'])
    nach = {q['key']: [d for d in q['nach'] if '/' not in d] for q in k['quests']}

    def vorfahren(key, seen=None):
        seen = set() if seen is None else seen
        for d in nach[key]:
            if d not in seen:
                seen.add(d)
                vorfahren(d, seen)
        return seen

    for q in k['quests']:
        vf = vorfahren(q['key'])
        for it in items_von(q):
            for z in noetig(it):
                p = wem.get(z)
                if p and p != q['key'] and p not in vf:
                    warn.append(f"{k['datei']}/{q['key']}: braucht {z} aus Quest '{p}', die aber nicht davor liegt")


def bild(k, pos, ordner):
    from PIL import Image, ImageDraw
    xs = [p[0] for p in pos.values()]
    ys = [p[1] for p in pos.values()]
    S = 60
    x0, y0 = min(xs) - 1.5, min(ys) - 1.5
    w, h = int((max(xs) - x0 + 1.5) * S), int((max(ys) - y0 + 1.5) * S)
    im = Image.new('RGB', (max(w, 300), max(h, 200)), (30, 30, 36))
    d = ImageDraw.Draw(im)
    P = lambda p: ((p[0] - x0) * S, (p[1] - y0) * S)  # noqa: E731
    for q in k['quests']:
        for dep in q['nach']:
            if dep in pos:
                d.line([P(pos[dep]), P(pos[q['key']])], fill=(120, 120, 140), width=2)
    for q in k['quests']:
        cx, cy = P(pos[q['key']])
        r = 18 if q.get('gross') else 12
        farbe = (90, 160, 90) if q.get('optional') else (200, 170, 60) if q.get('haupt') else (80, 130, 200)
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=farbe)
        d.text((cx - 40, cy + r + 2), q['titel'][:18], fill=(230, 230, 230))
        ext = [x for x in q['nach'] if '/' in x]
        if ext:
            d.text((cx - 40, cy - r - 12), '<' + ext[0].split('/')[0][:14], fill=(220, 120, 120))
    d.text((5, 5), k['titel'].replace('&', '')[2:] if k['titel'].startswith('&') else k['titel'], fill=(255, 255, 255))
    im.save(os.path.join(ordner, k['datei'] + '.png'))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--ids')
    ap.add_argument('--rezepte')
    ap.add_argument('--bilder')
    ap.add_argument('--nur', help='nur diese Teile laden (Komma-Liste), zum Testen')
    ap.add_argument('--locker', action='store_true', help='fehlende Abhaengigkeiten in anderen Kapiteln nur warnen')
    ap.add_argument('--aus', help='Ausgabeordner (Standard: pack/config/ftbquests/quests)')
    a = ap.parse_args()
    global OUT
    if a.aus:
        OUT = a.aus
    teile = a.nur.split(',') if a.nur else TEILE
    for t in teile:
        importlib.import_module('inhalt.' + t)
    from inhalt.loot import TABELLEN
    chk = Pruefer(a.ids)
    warn = chk.warnungen

    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    lang = {'file.0000000000000001.title': BUCH_TITEL}
    alle_ids = set()

    # Loot-Tabellen
    tab_ids = {}
    for i, tb in enumerate(TABELLEN):
        tid = hid('loot', tb['key'])
        tab_ids[tb['key']] = tid
        lang[f'reward_table.{tid}.title'] = tb['titel']
        rewards = []
        for j, (item, n, gewicht) in enumerate(tb['items']):
            chk('items', item, 'loot/' + tb['key'])
            e = {}
            if n > 1:
                e['count'] = n
            e.update({'id': hid('loot', tb['key'], str(j)), 'item': {'count': 1, 'id': item}, 'weight': F(gewicht)})
            rewards.append(e)
        schreib(os.path.join(OUT, 'reward_tables', tb['key'] + '.snbt'), {
            'icon': {'id': 'ftbquests:lootcrate'},
            'id': tid,
            'loot_crate': {'color': tb['farbe'], 'drops': {'boss': 0, 'monster': 0, 'passive': 0},
                           'glow': tb.get('glow', False), 'string_id': tb['key']},
            'loot_size': tb.get('anzahl', 1),
            'order_index': i,
            'rewards': rewards,
            'use_title': True,
        })

    # Gruppen
    gruppen = []
    for g in dsl.GRUPPEN:
        gid = hid('gruppe', g['key'])
        g['id'] = gid
        lang[f'chapter_group.{gid}.title'] = g['titel']
        gruppen.append({'icon': {'id': g['icon']}, 'id': gid})
        chk('items', g['icon'], 'gruppe/' + g['key'])
    schreib(os.path.join(OUT, 'chapter_groups.snbt'), {'chapter_groups': gruppen})
    gid_von = {g['key']: g['id'] for g in dsl.GRUPPEN}

    # Quest-Schluessel aller Kapitel (fuer kapiteluebergreifende Abhaengigkeiten)
    qkeys = {}
    for k in dsl.KAPITEL:
        ks = [q['key'] for q in k['quests']]
        if len(ks) != len(set(ks)):
            dop = {x for x in ks if ks.count(x) > 1}
            raise SystemExit(f"{k['datei']}: doppelte Quest-Schluessel {dop}")
        qkeys[k['datei']] = set(ks)
    dateien = [k['datei'] for k in dsl.KAPITEL]
    if len(dateien) != len(set(dateien)):
        raise SystemExit('doppelte Kapitel-Dateinamen')

    rez = json.load(open(a.rezepte)) if a.rezepte else None
    if a.bilder:
        os.makedirs(a.bilder, exist_ok=True)

    anzahl = 0
    fremd = set()
    for idx, k in enumerate(dsl.KAPITEL):
        cid = hid(k['datei'])
        lang[f'chapter.{cid}.title'] = k['titel']
        if k.get('untertitel'):
            lang[f'chapter.{cid}.chapter_subtitle'] = [k['untertitel']]
        chk('items', k['icon'], k['datei'])
        for q in k['quests']:
            wo = f"{k['datei']}/{q['key']}"
            for d in q['nach']:
                if '/' in d:
                    kap, key = d.split('/', 1)
                    if key not in qkeys.get(kap, ()):
                        if a.locker and kap not in qkeys:
                            warn.append(f'{wo}: Abhaengigkeit {d} (anderer Teil, nicht geladen)')
                            fremd.add(d)
                        else:
                            raise SystemExit(f'{wo}: Abhaengigkeit {d} gibt es nicht')
                elif d not in qkeys[k['datei']]:
                    raise SystemExit(f'{wo}: Abhaengigkeit {d} gibt es nicht')
            if not q.get('titel'):
                raise SystemExit(f'{wo}: kein Titel')
            if not q.get('text'):
                warn.append(f'{wo}: kein Text')
            if q.get('form') and q['form'] not in FORMEN:
                raise SystemExit(f"{wo}: unbekannte Form {q['form']}")
        pos = layout(k['quests'])
        belegt = {}
        for q in k['quests']:
            p = (round(pos[q['key']][0], 2), round(pos[q['key']][1], 2))
            if p in belegt:
                raise SystemExit(f"{k['datei']}: {q['key']} und {belegt[p]} liegen aufeinander")
            belegt[p] = q['key']
        if rez:
            rezept_pruefung(k, rez, warn)
        if a.bilder:
            bild(k, pos, a.bilder)

        # Quest-Links fuer Abhaengigkeiten aus anderen Kapiteln: links neben die erste Quest, die sie braucht
        links, link_pos = [], {}
        ys_links = defaultdict(int)
        for q in k['quests']:
            for d in q['nach']:
                if '/' in d and d not in link_pos:
                    kap, key = d.split('/', 1)
                    x, y = pos[q['key']]
                    lx = x - DX
                    ly = y - DY * ys_links[(lx, y)]
                    while (round(lx, 2), round(ly, 2)) in belegt:
                        ly -= DY
                    belegt[(round(lx, 2), round(ly, 2))] = d
                    ys_links[(lx, y)] += 1
                    link_pos[d] = True
                    links.append({'id': hid(k['datei'], 'link', d), 'linked_quest': hid(kap, key),
                                  'shape': 'rsquare', 'size': 1.0, 'x': float(lx), 'y': float(ly)})

        quests = []
        for q in k['quests']:
            wo = f"{k['datei']}/{q['key']}"
            qid = hid(k['datei'], q['key'])
            tasks = q['aufgaben'] if isinstance(q['aufgaben'], list) else [q['aufgaben']]
            rewards = q.get('belohnung', [])
            rewards = rewards if isinstance(rewards, list) else [rewards]
            x, y = pos[q['key']]
            qn = {}
            if q['nach']:
                qn['dependencies'] = [hid(*d.split('/', 1)) if '/' in d else hid(k['datei'], d) for d in q['nach'] if d not in fremd]
                if q.get('eine') and len(q['nach']) > 1:
                    qn['dependency_requirement'] = 'one_completed'
            qn['id'] = qid
            if q.get('optional'):
                qn['optional'] = True
            # Kills, Dimensionen, Strukturen, Biome zaehlen sonst erst nach dem Freischalten
            if any(isinstance(t, tuple) and t[0] in ('kill', 'dim', 'struct', 'biome') for t in tasks):
                qn['progression_mode'] = 'flexible'
            qn['rewards'] = [reward_nbt(r, hid(k['datei'], q['key'], 'r', str(i)), chk, wo, tab_ids)
                             for i, r in enumerate(rewards)]
            if q.get('form'):
                qn['shape'] = q['form']
            if q.get('gross'):
                qn['size'] = 1.5
            qn['tasks'] = [task_nbt(t, hid(k['datei'], q['key'], 't', str(i)), chk, wo, q.get('abgabe'))
                           for i, t in enumerate(tasks)]
            qn['x'] = float(x)
            qn['y'] = float(y)
            quests.append(qn)
            for i in [qid] + [r['id'] for r in qn['rewards']] + [t['id'] for t in qn['tasks']]:
                if i in alle_ids:
                    raise SystemExit(f'{wo}: ID-Kollision {i}')
                alle_ids.add(i)
            lang[f'quest.{qid}.title'] = q['titel']
            if q.get('sub'):
                lang[f'quest.{qid}.quest_subtitle'] = q['sub']
            if q.get('text'):
                zeilen = []
                for i, absatz in enumerate(q['text'] if isinstance(q['text'], list) else [q['text']]):
                    if i:
                        zeilen.append('')
                    zeilen.append(absatz)
                lang[f'quest.{qid}.quest_desc'] = zeilen
            anzahl += 1
        schreib(os.path.join(OUT, 'chapters', k['datei'] + '.snbt'), {
            'default_hide_dependency_lines': False,
            'default_quest_shape': '',
            'filename': k['datei'],
            'group': gid_von.get(k['gruppe'], ''),
            'icon': {'id': k['icon']},
            'id': cid,
            'images': [],
            'order_index': idx,
            'quest_links': links,
            'quests': quests,
        })

    schreib(os.path.join(OUT, 'data.snbt'), {
        'default_autoclaim_rewards': 'disabled',
        'default_consume_items': False,
        'default_quest_disable_jei': False,
        'default_quest_shape': 'circle',
        'default_reward_team': False,
        'detection_delay': 20,
        'disable_gui': False,
        'drop_loot_crates': False,
        'emergency_items_cooldown': 300,
        'grid_scale': 0.5,
        'icon': {'id': BUCH_ICON},
        'lock_message': '',
        'loot_crate_no_drop': {'boss': 0, 'monster': 600, 'passive': 4000},
        'pause_game': False,
        'progression_mode': 'linear',
        'show_lock_icons': True,
        'version': 13,
    })
    schreib(os.path.join(OUT, 'lang', 'en_us.snbt'), dict(sorted(lang.items())))
    for w in warn:
        print('WARNUNG', w)
    if chk.fehler:
        print('\n'.join(chk.fehler))
        raise SystemExit(f'{len(chk.fehler)} unbekannte IDs')
    print(f'{len(dsl.GRUPPEN)} Gruppen, {len(dsl.KAPITEL)} Kapitel, {anzahl} Quests, '
          f'{len(TABELLEN)} Loot-Tabellen, {len(warn)} Warnungen -> {os.path.normpath(OUT)}')


if __name__ == '__main__':
    main()
