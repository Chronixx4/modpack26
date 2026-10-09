"""Erzeugt das Modonomicon-Buch "Bossfuehrer" aus den Boss-Quests des Questbuchs.
(Patchouli geht nicht: es liest Buecher nur aus Mod-Jars, nicht aus KubeJS-Datenpaketen.)

Aufruf: python3 pack/quests/bossbuch.py
Schreibt pack/kubejs/data/chronicle/modonomicon/books/bossfuehrer und pack/kubejs/assets/chronicle/lang/en_us.json (greift als Rueckfall in jeder Sprache).
Quelle der Texte sind die Quests (kill-Aufgaben), Gruppen = Mod der Kreatur.
"""
import importlib
import json
import os
import re
import shutil
import sys

HIER = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HIER)
import generator as g  # noqa: E402
import dsl  # noqa: E402

OUT = os.path.join(HIER, '..', 'kubejs', 'data', 'chronicle', 'modonomicon', 'books', 'bossfuehrer')
ALT = os.path.join(HIER, '..', 'kubejs', 'data', 'chronicle', 'patchouli_books')
LANG = os.path.join(HIER, '..', 'kubejs', 'assets', 'chronicle', 'lang')

# Mod -> (Kategorie, Anzeigename, Symbol, Beschreibung)
MODS = [
    ('minecraft', 'Minecraft', 'minecraft:nether_star', 'Die Klassiker: Wächter, Wither, Enderdrache und der Wärter.'),
    ('twilightforest', 'Twilight Forest', 'twilightforest:naga_trophy', 'Feste Reihenfolge, jeder Boss öffnet das nächste Gebiet.'),
    ('cataclysm', 'Cataclysm', 'cataclysm:ignitium_ingot', 'Die härtesten Bosse des Packs. Spezialaugen zeigen den Weg.'),
    ('mowziesmobs', "Mowzie's Mobs", 'mowziesmobs:sol_visage', 'Kleine Kämpfe mit viel Ausweichen.'),
    ('born_in_chaos_v1', 'Born in Chaos', 'born_in_chaos_v1:dire_hound_leader_spawn_egg', 'Finstere Gegner und Kürbisköpfe.'),
    ('iceandfire', 'Eis und Feuer', 'iceandfire:fire_dragon_heart', 'Drachen, Zyklopen und Gorgonen.'),
    ('goety', 'Goety', 'goety:dark_wand', 'Dunkle Magie und ihre Meister.'),
    ('aether', 'Aether', 'aether:gold_dungeon_key', 'Die Bosse der Aether-Dungeons.'),
    ('deep_aether', 'Deep Aether', 'deep_aether:skyjade', 'Der Boss aus den Sturmwolken des Aether-Addons.'),
    ('aquamirae', 'Aquamirae', 'aquamirae:abyssal_amethyst', 'Gefahren aus der Tiefe des Meeres.'),
    ('block_factorys_bosses', "Block Factory's Bosses", 'minecraft:totem_of_undying', 'Sechs eigene Bosse in verschiedenen Biomen.'),
    ('irons_spellbooks', "Iron's Spells 'n Spellbooks", 'irons_spellbooks:arcane_essence', 'Magier und die Gegner der Strukturen.'),
    ('ars_nouveau', 'Ars Nouveau', 'ars_nouveau:wilden_horn', 'Die Wilden und ihre Chimäre.'),
    ('mutantmonsters', 'Mutantenmonster', 'mutantmonsters:hulk_hammer', 'Vergrößerte Versionen bekannter Monster.'),
    ('friendsandfoes', 'Friends and Foes', 'friendsandfoes:wildfire_crown_fragment', 'Magier und Feuergeister.'),
    ('illagerinvasion', 'Illager Invasion', 'minecraft:totem_of_undying', 'Der Invoker und seine Illager.'),
    ('sonstige', 'Sonstige', 'minecraft:skeleton_skull', 'Weitere Gegner aus anderen Mods.'),
]
NAME = {m[0]: m for m in MODS}
UEBERSPRINGEN = {('ars_nouveau', 'wilden'), ('evilcraft', 'geist'), ('bosse', 'goety_apostel'), ('bosse', 'goety_wesir')}

FARBE = re.compile(r'&([0-9a-fk-or])')
BUCH = 'chronicle:bossfuehrer'


def md(s):
    """Quest-Farbcodes (&e ... &r) in Markdown (fett) fuer Modonomicon."""
    out, offen = '', False
    for teil in re.split(r'(&[0-9a-fk-or])', s):
        m = FARBE.fullmatch(teil)
        if not m:
            out += teil
        elif m.group(1) == 'r':
            if offen:
                out += '**'
                offen = False
        elif not offen:
            out += '**'
            offen = True
    return out + ('**' if offen else '')


def teile(text, grenze=380):
    absaetze = [a for a in re.split(r'\n\n', text) if a.strip()]
    seiten, akt = [], ''
    for a in absaetze:
        if akt and len(akt) + len(a) > grenze:
            seiten.append(akt)
            akt = a
        else:
            akt = (akt + '\n\n' + a) if akt else a
    if akt:
        seiten.append(akt)
    return seiten


def lade():
    for t in g.TEILE:
        importlib.import_module('inhalt.' + t)
    gefunden = {}
    for k in dsl.KAPITEL:
        for q in k['quests']:
            if (k['datei'], q['key']) in UEBERSPRINGEN:
                continue
            tasks = q['aufgaben'] if isinstance(q['aufgaben'], list) else [q['aufgaben']]
            kills = [t for t in tasks if isinstance(t, tuple) and t[0] == 'kill']
            if not kills:
                continue
            text = q.get('text') or ''
            text = '\n\n'.join(text) if isinstance(text, list) else text
            items = [t for t in tasks if isinstance(t, str) and ':' in t]
            ent = kills[0][1]
            alt = gefunden.get(ent)
            if alt and len(alt['text']) >= len(text):
                continue
            gefunden[ent] = {'entity': ent, 'titel': q['titel'], 'text': text, 'items': items,
                             'anzahl': kills[0][2], 'kapitel': k['titel'], 'reihe': len(gefunden)}
    return sorted(gefunden.values(), key=lambda b: b['reihe'])


def schreibe(pfad, daten):
    os.makedirs(os.path.dirname(pfad), exist_ok=True)
    with open(pfad, 'w', encoding='utf-8') as f:
        json.dump(daten, f, ensure_ascii=False, indent=2)
        f.write('\n')


def main():
    bosse = lade()
    for d in (OUT, ALT, LANG):
        if os.path.isdir(d):
            shutil.rmtree(d)
    lang = {}
    pre = 'book.chronicle.bossfuehrer.'
    schreibe(os.path.join(OUT, 'book.json'), {
        'allow_open_book_with_invalid_links': True,
        'auto_add_read_conditions': True,
        'book_content_texture': 'modonomicon:textures/gui/book_content.png',
        'book_overview_texture': 'modonomicon:textures/gui/book_overview.png',
        'crafting_texture': 'modonomicon:textures/gui/crafting_textures.png',
        'creative_tab': 'modonomicon:modonomicon',
        'default_title_color': 0,
        'description': '',
        'display_mode': 'node',
        'font': 'modonomicon:default',
        'frame_texture': 'modonomicon:textures/gui/book_frame.png',
        'generate_book_item': True,
        'name': pre + 'name',
        'page_display_mode': 'double_page',
        'single_page_texture': 'modonomicon:textures/gui/single_page_entry.png',
        'tooltip': pre + 'tooltip',
        'turn_page_sound': 'modonomicon:turn_page',
    })
    lang[pre + 'name'] = 'Chronicle Bossführer'
    lang[pre + 'tooltip'] = 'Alle Bosse aus dem Questbuch: Fundort, Beschwörung und Beute.'
    benutzt = []
    for b in bosse:
        mod = b['entity'].split(':')[0]
        b['mod'] = mod if mod in NAME else 'sonstige'
        if b['mod'] not in benutzt:
            benutzt.append(b['mod'])
    reihenfolge = [m[0] for m in MODS if m[0] in benutzt]
    for i, mid in enumerate(reihenfolge):
        _, name, icon, beschr = NAME[mid]
        schreibe(os.path.join(OUT, 'categories', mid + '.json'), {
            'background': 'modonomicon:textures/gui/dark_slate_seamless.png',
            'background_height': 512,
            'background_width': 512,
            'background_parallax_layers': [],
            'background_texture_zoom_multiplier': 1.0,
            'description': '',
            'display_mode': 'node',
            'entry_textures': 'modonomicon:textures/gui/entry_textures.png',
            'icon': {'item': icon},
            'max_scroll_x': 2048,
            'max_scroll_y': 2048,
            'name': pre + mid + '.name',
            'show_category_button': True,
            'sort_number': i,
        })
        lang[pre + mid + '.name'] = name
    zaehler, vorher = {}, {}
    for b in bosse:
        mid = b['mod']
        n = zaehler[mid] = zaehler.get(mid, 0)
        zaehler[mid] += 1
        eid = b['entity'].split(':')[1]
        k = pre + mid + '.' + eid + '.'
        seiten = teile(md(b['text'])) or ['Keine Hinweise.']
        kopf = '**' + NAME[mid][1] + '**' + (' (' + str(b['anzahl']) + 'x besiegen)' if b['anzahl'] > 1 else '')
        lang[k + 'name'] = b['titel']
        lang[k + 'description'] = NAME[mid][1]
        pages = []
        for i, t in enumerate(seiten):
            pages.append({'type': 'modonomicon:text', 'anchor': '', 'condition': {'type': 'modonomicon:none'},
                          'show_title_separator': True, 'text': k + f'p{i}.text',
                          'title': k + 'name' if i == 0 else '', 'use_markdown_in_title': False})
            lang[k + f'p{i}.text'] = (kopf + '\n\n' if i == 0 else '') + t
        for it in b['items'][:1]:
            pages.append({'type': 'modonomicon:spotlight', 'anchor': '', 'condition': {'type': 'modonomicon:none'},
                          'item': {'item': it}, 'text': k + 'beute.text', 'title': ''})
            lang[k + 'beute.text'] = 'Das gibt es als Beute oder Trophäe.'
        icon = b['items'][0] if b['items'] else NAME[mid][2]
        eintrag = {
            'background_u_index': 0, 'background_v_index': 0,
            'category': mid,
            'condition': {'type': 'modonomicon:none'},
            'description': k + 'description',
            'hide_while_locked': False,
            'icon': {'item': icon},
            'name': k + 'name',
            'pages': pages,
            'parents': [],
            'show_when_any_parent_unlocked': False,
            'sort_number': n,
            'x': (n % 6) * 2,
            'y': (n // 6) * 2,
        }
        if n % 6 and vorher.get(mid):
            eintrag['parents'] = [{'draw_arrow': True, 'entry': mid + '/' + vorher[mid], 'line_enabled': True, 'line_reversed': False}]
        vorher[mid] = eid
        schreibe(os.path.join(OUT, 'entries', mid, eid + '.json'), eintrag)
    schreibe(os.path.join(LANG, 'en_us.json'), lang)
    print(f'{len(bosse)} Bosse, {len(reihenfolge)} Kategorien -> {os.path.normpath(OUT)}')


if __name__ == '__main__':
    main()
