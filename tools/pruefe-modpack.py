"""Prueft die Arena und neue Mods in den drei gebauten Chronicle-Paketen."""
import argparse
import hashlib
import io
import json
import tomllib
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
NEW_MODS = {
    'toms_storage': '2.4.2',
    'mekanismaaa': '1.1.0',
    'mahoutsukai': '1.21.1-v1.36.27',
    'youkaisfeasts': '1.1.1',
    'buildinggadgets2': '1.3.9',
    'xycraft_core': '0.7.53',
    'xycraft_world': '0.7.53',
    'xycraft_machines': '0.7.53',
    'drones': '1.1.0',
    'bytebuddies': '0.9.2',
    'laserbridges': '6.1',
    'deimos': '2.7',
    'superbwarfare': '0.8.9.2',
    'vista': '1.21.1-5.5.7',
}


def mods(raw):
    with zipfile.ZipFile(io.BytesIO(raw)) as jar:
        data = tomllib.loads(jar.read('META-INF/neoforge.mods.toml').decode())
        return {mod['modId']: mod['version'] for mod in data['mods']}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('dist', type=Path)
    args = parser.parse_args()
    index = json.loads((ROOT / 'pack/modrinth.index.json').read_text())
    version = index['versionId']
    properties = dict(line.split('=', 1) for line in
                      (ROOT / 'mod/chronicle-arena/gradle.properties').read_text().splitlines()
                      if '=' in line)
    arena_version = properties['mod_version']
    arena_name = f'chronicle-arena-{arena_version}.jar'
    local = ROOT / 'pack/local-mods' / arena_name
    sums = (ROOT / 'pack/local-mods/SHA256SUMS.txt').read_text().splitlines()
    expected = next(line.split()[0] for line in sums if line.split()[-1] == arena_name)
    assert hashlib.sha256(local.read_bytes()).hexdigest() == expected, 'Lokale Arena-Pruefsumme falsch'
    assert mods(local.read_bytes()) == {'chronicle_arena': arena_version}, 'Lokale Arena-Version falsch'
    paths = [(ROOT / 'curseforge-mods' / name) for name in (
        'buildinggadgets2-1.3.9.jar', 'xycraft_core-0.7.53-all.jar',
        'xycraft_world-0.7.53.jar', 'xycraft_machines-0.7.53.jar',
        'bytebuddies-0.9.2.jar')]
    expected_cf = {p.name: p.read_bytes() for p in paths}
    config_path = 'defaultconfigs/superbwarfare-server.toml'
    expected_config = (ROOT / 'pack' / config_path).read_bytes()
    explosion = tomllib.loads(expected_config.decode())['explosion']
    assert explosion['explosion_destroy'] is False, 'Explosionen zerstoeren Bloecke'
    assert explosion['extra_explosion_effect'] is False, 'Zusaetzliche Sprengwirkung aktiv'
    for filename, prefix in [(f'Chronicle-{version}.mrpack', 'overrides/'),
                             (f'Chronicle-{version}-Client.zip', ''),
                             (f'Chronicle-{version}-Server.zip', '')]:
        with zipfile.ZipFile(args.dist / filename) as archive:
            assert archive.read(prefix + config_path) == expected_config, (filename, config_path)
            jar_paths = [name for name in archive.namelist()
                         if name.startswith(prefix + 'mods/chronicle-arena-') and name.endswith('.jar')]
            assert jar_paths == [prefix + 'mods/' + arena_name], (filename, 'Doppelte/alte Arena', jar_paths)
            assert mods(archive.read(jar_paths[0])) == {'chronicle_arena': arena_version}, filename
            for name, raw in expected_cf.items():
                assert archive.read(prefix + 'mods/' + name) == raw, (filename, name)
            if filename.endswith('.mrpack'):
                packaged_index = json.loads(archive.read('modrinth.index.json'))
                assert packaged_index == index, 'Modrinth-Index im Paket weicht ab'
                inventory = {}
                for entry in index['files']:
                    if any(name in entry['path'] for name in (
                        'toms_storage-', 'mekanism-ad-astra-ores-', 'mahoutsukai-', 'youkaisfeasts-',
                        'drones-', 'laserbridges-', 'deimos-', 'superbwarfare-', 'vista-')):
                        raw = (args.dist / 'cache' / Path(entry['path']).name).read_bytes()
                        assert hashlib.sha512(raw).hexdigest() == entry['hashes']['sha512']
                        inventory.update(mods(raw))
                for raw in expected_cf.values():
                    inventory.update(mods(raw))
            else:
                inventory = {}
                for name in archive.namelist():
                    if name.startswith('mods/') and name.endswith('.jar') and any(
                        marker in name for marker in (
                            'toms_storage-', 'mekanism-ad-astra-ores-', 'mahoutsukai-',
                            'youkaisfeasts-', 'buildinggadgets2-', 'xycraft_',
                            'drones-', 'bytebuddies-', 'laserbridges-', 'deimos-', 'superbwarfare-', 'vista-')):
                        inventory.update(mods(archive.read(name)))
            assert inventory == NEW_MODS, (filename, inventory)
        print(f'{filename}: Arena {arena_version} genau einmal und alle {len(NEW_MODS)} geprueften Mods enthalten')


if __name__ == '__main__':
    main()
