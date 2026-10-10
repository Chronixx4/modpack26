"""Schreibt site/arena.json (Arena-Tab der Webseite) aus den Datapack-Dateien der Arena-Mod.
Aufruf: python3 tools/arena_json.py [pfad/zu/data/chronicle_arena]
Standard: mod/chronicle-arena/src/main/resources/data/chronicle_arena. Die Modifikatoren stehen im Skript
(Quelle: ArenaManager.java), bei Aenderungen dort hier nachziehen."""
import json, pathlib, sys
root = pathlib.Path(__file__).resolve().parent.parent
d = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else root / 'mod/chronicle-arena/src/main/resources/data/chronicle_arena'
def load(sub):
    return [json.loads(f.read_text(encoding='utf-8')) for f in sorted((d / sub).glob('*.json'))]
MODNAMES = {'minecraft': 'Minecraft', 'cataclysm': "L_Ender's Cataclysm", 'mowziesmobs': "Mowzie's Mobs", 'goety': 'Goety',
            'twilightforest': 'Twilight Forest', 'aether': 'Aether', 'iceandfire': 'Ice and Fire', 'allthemodium': 'AllTheModium',
            'deep_aether': 'Deep Aether'}
tiers = sorted(load('arena_tiers'), key=lambda t: t['order'])
tiers = [{k: t.get(k) for k in ('name', 'description', 'health', 'damage', 'count', 'waves', 'xp', 'tokens', 'tokens_per_wave', 'boss_rolls')}
         | {'requires': (t.get('requires') or '').split(':')[-1]} for t in tiers]
enc = []
for e in sorted(load('arena_encounters'), key=lambda e: (e['type'] != 'boss', e['order'])):
    ent = (e.get('boss') or {}).get('entity', '')
    ns = ent.split(':')[0] if ent else ''
    enc.append({'typ': e['type'], 'name': e['name'], 'text': e.get('description', ''), 'theme': e.get('theme', 'default'),
                'mod': MODNAMES.get(ns, ns.replace('_', ' ').title()) if ns else '', 'adds': sum(a.get('count', 1) for a in e.get('adds', []))})
shop = load('arena_shop')
cats = {'material': 'Materialien', 'magie': 'Magie', 'werkzeug': 'Werkzeuge', 'ruestung': 'Rüstung', 'verbrauch': 'Verbrauch', 'waffen': 'Waffen', 'kosmetik': 'Kosmetik'}
out = {
    'tiers': tiers,
    'begegnungen': enc,
    'modifikatoren': [
        {'name': 'Elite-Horde', 'bonus': 25, 'text': '30 % der Gegner sind Elite (doppelte Gesundheit).'},
        {'name': 'Kein Heilen', 'bonus': 35, 'text': 'Keine Regeneration, auch nicht zwischen den Wellen.'},
        {'name': 'Glaskanone', 'bonus': 30, 'text': 'Du erleidest doppelten Schaden.'},
        {'name': 'Hetzjagd', 'bonus': 20, 'text': 'Alle Gegner sind 30 % schneller.'}],
    'shop': {'kategorien': [{'id': k, 'name': v, 'anzahl': sum(1 for s in shop if s['category'] == k)} for k, v in cats.items()],
             'angebote': [{'n': s['name'], 'k': s['category'], 'p': s['price'], 'c': s.get('count', 1)} for s in sorted(shop, key=lambda s: (s['category'], s.get('order', 0), s['name']))]},
}
(root / 'site' / 'arena.json').write_text(json.dumps(out, ensure_ascii=False, separators=(',', ':')), encoding='utf-8')
print(len(tiers), 'Stufen,', len(enc), 'Begegnungen,', len(shop), 'Angebote')
