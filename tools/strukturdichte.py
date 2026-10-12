"""Erzeugt/prueft Chronicle-Overrides aus den im Index gepinnten Mod-JARs.

python3 tools/strukturdichte.py --mods /pfad/zum/cache --write
python3 tools/strukturdichte.py --mods /pfad/zum/cache --check
Keine Downloads, keine Serverstarts. Ausgabe: pack/kubejs/data/*/worldgen/structure_set/.
"""
import argparse
import copy
import hashlib
import json
import math
import re
import zipfile
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
HALF = math.sqrt(2)
BOSS = 1.35

# None = alle Structure-Sets dieses Namensraums; sonst nur die genannten Sets.
# Andere Dimensionen, Doerfer und Vanilla-Ersatzstrukturen bleiben erhalten.
RULES = {
    'dungeons_arise': (2, None),
    'dungeons_arise_seven_seas': (2, None),
    'nova_structures': (1.5, None),
    'idas': (1.5, {'idas_common', 'idas_ocean', 'idas_rare', 'idas_small'}),
    'repurposed_structures': (1.5, {
        'cities_overworld', 'fortresses_overworld', 'igloos_overworld',
        'mansions_mangrove', 'mansions_overworld', 'monuments_overworld',
        'outposts_ocean', 'outposts_overworld', 'pyramids_mushroom',
        'pyramids_overworld', 'ruins_overworld', 'temples_overworld',
        'witch_huts_overworld',
    }),
    'towns_and_towers': (HALF, {'towers', 'other'}),
    'born_in_chaos_v1': (HALF, None),
    'goety': (HALF, None),
    'irons_spellbooks': (HALF, {
        'catacombs', 'evoker_fort', 'ice_spider_den', 'impaled_icebreaker',
        'mangrove_hut', 'mountain_tower', 'pyromancer_tower',
    }),
    'hexerei': (HALF, None),
    'ars_additions': (HALF, {'arcane_library', 'ruined_portal'}),
    'ars_elemental': (HALF, {'starbuncle_shrine'}),
    'ars_nouveau': (BOSS, {'wilden_den_set'}),
    'cataclysm': (BOSS, {
        'abandoned_structures', 'acropolis', 'amethyst_nest', 'ancient_factory',
        'cursed_pyramid', 'desert_structures', 'frosted_prison',
        'small_integrated_cataclysm', 'sunken_city',
    }),
    'iceandfire': (BOSS, {
        'cyclops_cave', 'dragon_cave', 'dragon_roost', 'gorgon_temple',
        'graveyard', 'hydra_cave', 'mausoleum', 'siren_island',
    }),
    'mowziesmobs': (BOSS, None),
    'block_factorys_bosses': (BOSS, {
        'sandworm_nest', 'dragon_tower', 'yeti_hideout', 'kraken_ship',
    }),
    'aquamirae': (HALF, {
        'pirate_outpost', 'pirate_shelter', 'pirate_ship', 'shipwreck',
    }),
    'illagerinvasion': (HALF, None),
    'friendsandfoes': (HALF, {
        'iceologer_cabin', 'illusioner_shack', 'illusioner_training_grounds',
    }),
    'alshanex_familiars': (HALF, {'hunter_campsite'}),
    'apotheosis': (HALF, {'towers'}),
    'evilcraft': (HALF, {'dark_temples'}),
    'supplementaries': (HALF, {'galleons'}),
    'terralith': (HALF, {'mage', 'regular', 'rubble'}),
}


def selected(namespace, name):
    if namespace not in RULES:
        return False
    if namespace == 'nova_structures' and (
        name.startswith(('nether_', 'end_', 'villages_'))
    ):
        return False
    if namespace == 'goety' and name == 'final_terminal':
        return False
    names = RULES[namespace][1]
    return names is None or name in names


def read_json(raw):
    # Einige gepinnte Mods verwenden vollstaendige //-Kommentarzeilen (Gson).
    text = re.sub(r'(?m)^\s*//.*$', '', raw.decode('utf-8-sig'))
    return json.loads(text)


def sha512(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha512').hexdigest()


def overrides(cache):
    index = json.loads((ROOT / 'pack/modrinth.index.json').read_text())
    jars = []
    for entry in index['files']:
        path = cache / Path(entry['path']).name
        # Nur JARs mit relevanten Sets werden gelesen; alle benoetigten muessen vorliegen.
        if not path.is_file():
            raise SystemExit(f'Gepinnte Mod fehlt: {path}')
        if sha512(path) != entry['hashes']['sha512']:
            raise SystemExit(f'SHA-512 stimmt nicht: {path}')
        jars.append(path)
    jars.extend(sorted((ROOT / 'curseforge-mods').glob('*.jar')))
    # Diese Addons ersetzen gleichnamige Basis-Sets. Ihre Regeln als Vorlage nehmen.
    jars.sort(key=lambda p: (
        p.name.startswith(('integrated_cataclysm-', 'IMM ')), p.name
    ))
    result = {}
    sources = {}
    for jar in jars:
        with zipfile.ZipFile(jar) as archive:
            for path in archive.namelist():
                match = re.fullmatch(r'data/([^/]+)/worldgen/structure_set/([^/]+)\.json', path)
                if not match or not selected(*match.groups()):
                    continue
                namespace, name = match.groups()
                original = read_json(archive.read(path))
                updated = copy.deepcopy(original)
                placement = updated['placement']
                factor = RULES[namespace][0]
                if placement['type'] == 'aquamirae:configurable_random_spread':
                    # Der Codec unterstuetzt die gemeinsame Placement-Frequenz.
                    # Die @Config-Referenzen bleiben intakt; Zahlen dort sind ungueltig.
                    placement['frequency'] = placement.get('frequency', 1.0) / 2
                else:
                    placement['spacing'] = math.ceil(placement['spacing'] * factor)
                    placement['separation'] = math.ceil(placement['separation'] * factor)
                    if not 0 <= placement['separation'] < placement['spacing'] <= 4096:
                        raise SystemExit(f'Ungueltige Platzierung: {path}')
                result[path.removeprefix('data/')] = updated
                sources[path.removeprefix('data/')] = (jar.name, original)
    found = Counter(path.split('/')[0] for path in result)
    missing = set(RULES) - set(found)
    if missing:
        raise SystemExit(f'Keine Sets fuer: {sorted(missing)}')
    # Nicht unbemerkt einzelne konfigurierte Sets bei Mod-Updates verlieren.
    for namespace, (_, names) in RULES.items():
        if names is not None:
            absent = names - {Path(p).stem for p in result if p.startswith(namespace + '/')}
            if absent:
                raise SystemExit(f'Sets fehlen in {namespace}: {sorted(absent)}')
    return result, sources


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--mods', required=True, type=Path)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument('--write', action='store_true')
    mode.add_argument('--check', action='store_true')
    args = parser.parse_args()
    result, sources = overrides(args.mods)
    output = ROOT / 'pack/kubejs/data'
    # Ein veralteter Override darf bei geaendertem Profil nicht weiterwirken.
    existing = {
        p.relative_to(output).as_posix()
        for namespace in RULES
        for p in (output / namespace / 'worldgen/structure_set').glob('*.json')
    }
    stale = existing - set(result)
    if stale:
        raise SystemExit(f'Veraltete/unerwartete Overrides zuerst pruefen: {sorted(stale)}')
    for path, data in sorted(result.items()):
        target = output / path
        if args.write:
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(json.dumps(data, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
        elif not target.is_file() or json.loads(target.read_text()) != data:
            raise SystemExit(f'Override fehlt oder weicht vom Profil ab: {target}')
        original = copy.deepcopy(sources[path][1])
        normalized = copy.deepcopy(data)
        for key in ('spacing', 'separation', 'frequency'):
            normalized['placement'].pop(key, None)
            original['placement'].pop(key, None)
        if normalized != original:
            raise SystemExit(f'Strukturinhalt/Platzierungsregeln veraendert: {target}')
    # Gekoppelte Goety-Anbauten muessen dasselbe Raster wie ihr Hauptschrein behalten.
    shrine = result['goety/worldgen/structure_set/wind_shrine.json']['placement']
    for letter in 'abcdefgh':
        expansion = result[f'goety/worldgen/structure_set/wind_shrine_expansion_{letter}.json']['placement']
        if any(shrine[key] != expansion[key] for key in ('spacing', 'separation', 'salt')):
            raise SystemExit('Goety-Windschrein und Anbauten haben verschiedene Raster')
    print(f'{len(result)} Structure-Sets aus {len(RULES)} Namensraeumen ' +
          ('geschrieben' if args.write else 'geprueft') + '; Mod-Pruefsummen und Originalregeln erhalten.')
    for namespace, count in sorted(Counter(p.split('/')[0] for p in result).items()):
        print(f'  {namespace}: {count}')


if __name__ == '__main__':
    main()
