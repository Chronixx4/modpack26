"""Erzeugt das FTB-Quests-Buch aus kapitel.py.

Aufruf:  python3 pack/quests/generator.py [ids.json]
Ausgabe: pack/config/ftbquests/quests/  (data.snbt, chapters/*.snbt, lang/en_us.snbt)

Mit ids.json (Item-/Mob-/Dimensions-IDs aus den Mod-Jars) wird jede ID geprueft;
unbekannte IDs brechen den Lauf ab. Die Quest-IDs sind stabil (Hash aus Kapitel + Schluessel),
damit der Fortschritt der Spieler bei Aenderungen erhalten bleibt.
"""
import hashlib, json, os, shutil, sys
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from kapitel import BUCH_TITEL, BUCH_ICON, KAPITEL  # noqa: E402

OUT = os.path.join(HERE, '..', 'config', 'ftbquests', 'quests')


def hid(*parts):
    h = hashlib.sha1('/'.join(parts).encode()).hexdigest()[:16].upper()
    return h if h[0] not in '0' else 'A' + h[1:]


def s(v):
    return json.dumps(v, ensure_ascii=False)


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
    if isinstance(v, float):
        return f'{v}d'
    if isinstance(v, int):
        return str(v)
    return s(v)


class Pruefer:
    def __init__(self, path):
        self.ok = path is not None
        self.fehler = []
        if self.ok:
            d = json.load(open(path, encoding='utf-8'))
            self.sets = {k: set(d[k]) for k in ('items', 'entities', 'dimensions', 'structures')}
            self.sets['biomes'] = set(d.get('biomes', []))

    def __call__(self, art, rid, wo):
        if self.ok and self.sets[art] and rid not in self.sets[art]:
            self.fehler.append(f'{wo}: {art[:-1]} {rid} unbekannt')


def task_nbt(t, tid, chk, wo):
    """t: 'item:id' | ('item', id, n) | ('kill', entity, n) | ('dim', dim) | ('struct', id) | ('biome', id) | 'check'"""
    if t == 'check':
        return {'id': tid, 'type': 'checkmark'}
    if isinstance(t, str):
        t = ('item', t, 1)
    art = t[0]
    if art == 'item':
        chk('items', t[1], wo)
        d = {'id': tid, 'item': {'count': 1, 'id': t[1]}, 'type': 'item'}
        if len(t) > 2 and t[2] > 1:
            d['count'] = t[2]
        return d
    if art == 'kill':
        chk('entities', t[1], wo)
        return {'entity': t[1], 'id': tid, 'type': 'kill', 'value': t[2] if len(t) > 2 else 1}
    if art == 'dim':
        chk('dimensions', t[1], wo)
        return {'dimension': t[1], 'id': tid, 'type': 'dimension'}
    if art == 'struct':
        chk('structures', t[1], wo)
        return {'id': tid, 'structure': t[1], 'type': 'structure'}
    if art == 'biome':
        chk('biomes', t[1], wo)
        return {'biome': t[1], 'id': tid, 'type': 'biome'}
    raise ValueError(f'{wo}: unbekannte Aufgabe {t}')


def reward_nbt(r, rid, chk, wo):
    """r: int (XP-Level) | ('item', id, n)"""
    if isinstance(r, int):
        return {'id': rid, 'type': 'xp_levels', 'xp_levels': r}
    chk('items', r[1], wo)
    d = {'id': rid, 'item': {'count': 1, 'id': r[1]}, 'type': 'item'}
    if len(r) > 2 and r[2] > 1:
        d['count'] = r[2]
    return d


def layout(quests):
    """Spalte = Tiefe im Abhaengigkeitsbaum, Zeilen pro Spalte zentriert."""
    tiefe = {}
    for q in quests:
        tiefe[q['key']] = 1 + max([tiefe[d] for d in q.get('nach', []) if d in tiefe] or [-1])
    spalten = defaultdict(list)
    for q in quests:
        spalten[tiefe[q['key']]].append(q['key'])
    pos = {}
    for t, keys in spalten.items():
        n = len(keys)
        for i, k in enumerate(keys):
            pos[k] = (t * 2.0, (i - (n - 1) / 2) * 1.5)
    return pos


def main():
    chk = Pruefer(sys.argv[1] if len(sys.argv) > 1 else None)
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    os.makedirs(os.path.join(OUT, 'chapters'))
    os.makedirs(os.path.join(OUT, 'lang'))
    lang = {'file.0000000000000001.title': BUCH_TITEL}
    alle_ids = set()
    anzahl = 0
    for idx, k in enumerate(KAPITEL):
        cid = hid(k['datei'])
        lang[f'chapter.{cid}.title'] = k['titel']
        if k.get('untertitel'):
            lang[f'chapter.{cid}.chapter_subtitle'] = [k['untertitel']]
        chk('items', k['icon'], k['datei'])
        keys = [q['key'] for q in k['quests']]
        if len(keys) != len(set(keys)):
            raise SystemExit(f"{k['datei']}: doppelter Quest-Schluessel")
        pos = layout(k['quests'])
        quests = []
        for q in k['quests']:
            wo = f"{k['datei']}/{q['key']}"
            qid = hid(k['datei'], q['key'])
            tasks = q['aufgaben'] if isinstance(q['aufgaben'], list) else [q['aufgaben']]
            rewards = q.get('belohnung', [])
            rewards = rewards if isinstance(rewards, list) else [rewards]
            for d in q.get('nach', []):
                if d not in keys:
                    raise SystemExit(f'{wo}: Abhaengigkeit {d} gibt es nicht')
            x, y = q.get('pos', pos[q['key']])
            qn = {}
            if q.get('nach'):
                qn['dependencies'] = [hid(k['datei'], d) for d in q['nach']]
            qn['id'] = qid
            if q.get('optional'):
                qn['optional'] = True
            qn['rewards'] = [reward_nbt(r, hid(k['datei'], q['key'], 'r', str(i)), chk, wo) for i, r in enumerate(rewards)]
            if q.get('form'):
                qn['shape'] = q['form']
            if q.get('gross'):
                qn['size'] = 1.5
            qn['tasks'] = [task_nbt(t, hid(k['datei'], q['key'], 't', str(i)), chk, wo) for i, t in enumerate(tasks)]
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
        chap = {
            'default_hide_dependency_lines': False,
            'default_quest_shape': '',
            'filename': k['datei'],
            'group': '',
            'icon': {'id': k['icon']},
            'id': cid,
            'images': [],
            'order_index': idx,
            'quest_links': [],
            'quests': quests,
        }
        with open(os.path.join(OUT, 'chapters', k['datei'] + '.snbt'), 'w', encoding='utf-8', newline='\n') as f:
            f.write(snbt(chap) + '\n')
    data = {
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
        'progression_mode': 'flexible',
        'show_lock_icons': True,
        'version': 13,
    }
    with open(os.path.join(OUT, 'data.snbt'), 'w', encoding='utf-8', newline='\n') as f:
        f.write(snbt(data) + '\n')
    with open(os.path.join(OUT, 'lang', 'en_us.snbt'), 'w', encoding='utf-8', newline='\n') as f:
        f.write(snbt(dict(sorted(lang.items()))) + '\n')
    if chk.fehler:
        print('\n'.join(chk.fehler))
        raise SystemExit(f'{len(chk.fehler)} unbekannte IDs')
    print(f'{len(KAPITEL)} Kapitel, {anzahl} Quests -> {os.path.normpath(OUT)}')


if __name__ == '__main__':
    main()
