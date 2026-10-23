"""Audit exact-version deliverables, including remapped metadata and both variants."""
from pathlib import Path
import argparse
import hashlib
import json
import re
import struct
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CONFIG = tomllib.loads((ROOT / 'stonecutter.properties.toml').read_text(encoding='utf-8'))
VERSIONS = [key for key in CONFIG if re.fullmatch(r'\d+(?:\.\d+)+', key)]
UI_AUDIO = {'ClientLevelDirtyMixin', 'OptionsSubScreenAccessor', 'VideoSettingsScreenMixin',
            'ClientLevelSoundYLoopMixin', 'SoundEngineYLoopMixin', 'SubtitleDirectionYLoopMixin'}


def inspect(version, no_render, mod_version):
    suffix = '-norender' if no_render else ''
    file = ROOT / f'build/distributions/biblioteca-{mod_version}+{version}{suffix}.jar'
    node = tuple(map(int, version.split('.')))
    java = 25 if node >= (26, 1) else 21 if node >= (1, 20, 5) else 17
    with zipfile.ZipFile(file) as jar:
        names = set(jar.namelist())
        meta = json.loads(jar.read('fabric.mod.json'))
        assert meta['version'] == f'{mod_version}+{version}{suffix}', (file, 'version')
        assert meta['depends']['minecraft'] == version, (file, 'unsafe Minecraft range')
        assert meta['depends']['java'] == f'>={java}', (file, 'Java version')
        api_version = CONFIG[version]['deps']['fabric_api']
        with zipfile.ZipFile(ROOT / '.gradle/fabric-api' / api_version / 'fabric-api.jar') as api:
            api_id = json.loads(api.read('fabric.mod.json'))['id']
        assert api_id in meta['depends'], (file, 'Fabric API dependency id', api_id)
        client = json.loads(jar.read('biblioteca.client.mixins.json'))
        common = json.loads(jar.read('biblioteca.mixins.json'))
        for config, key in [(client, 'client'), (common, 'mixins')]:
            assert config['required'] and config['injectors']['defaultRequire'] == 1, (file, 'non-strict mixins')
            for mixin in config[key]:
                assert config['package'].replace('.', '/') + '/' + mixin + '.class' in names, (file, 'missing mixin', mixin)
            if java < 25:
                assert config.get('refmap') == 'biblioteca.refmap.json', (file, 'missing refmap declaration')
        if java < 25:
            refmap = json.loads(jar.read('biblioteca.refmap.json'))
            assert refmap['mappings'], (file, 'empty refmap')
            assert 'net/minecraft/class_' in json.dumps(refmap), (file, 'refmap not intermediary')
        if no_render:
            core = UI_AUDIO | {'ProjectileUtilYLoopMixin'}
            if node >= (1, 19, 4):
                core.update({'CreateWorldScreenMoreTabMixin', 'GridLayoutTabAccessor'})
            else:
                core.add('LegacyCreateWorldScreenMixin')
            if node >= (1, 20, 3):
                core.add('SubtitleDistanceYLoopMixin')
            if node >= (1, 21, 2):
                core.add('ClientPacketListenerYLoopMixin')
            if node < (26, 1):
                core.add('LegacyInteractionLocationYLoopMixin')
            assert set(client['client']) == core, (file, 'no-render hook drift')
        else:
            visibility = 'SectionOcclusionGraphLoopMixin' if node >= (1, 20, 2) else 'LegacyTerrainVisibilityMixin'
            assert {'RainImpactsYLoopMixin', visibility, 'BlockEntityRenderRangeYLoopMixin'} <= set(client['client']), (file, 'missing visuals')
            expected = ('LevelRendererYLoopMixin' if node >= (26, 3) else 'GpuTerrainYLoopMixin' if node >= (1, 21, 6)
                        else 'GpuLayerTerrainYLoopMixin' if node >= (1, 21, 5) else 'ClassicTerrainYLoopMixin' if node >= (1, 17)
                        else 'FixedTerrainYLoopMixin')
            assert expected in client['client'], (file, 'wrong terrain backend')
        assert 'ImprovedTransparencyYLoopMixin' not in client['client'], (file, 'forced OIT')
        if 'accessWidener' in meta:
            header = ' '.join(jar.read(meta['accessWidener']).decode().splitlines()[0].split())
            assert header == ('accessWidener v2 official' if java == 25 else 'accessWidener v2 intermediary'), (file, header)
        for name in names:
            if name.startswith('babel/') and name.endswith('.class'):
                assert struct.unpack('>H', jar.read(name)[6:8])[0] <= java + 44, (file, 'class bytecode exceeds Java target', name)
    print(f'ARTIFACT_OK {file.name}', flush=True)
    return {'minecraft': version, 'variant': 'norender' if no_render else 'full', 'file': str(file.relative_to(ROOT)),
            'sha256': hashlib.sha256(file.read_bytes()).hexdigest(), 'java': java}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--versions', default='')
    args = parser.parse_args()
    versions = args.versions.split(',') if args.versions else VERSIONS
    assert set(versions) <= set(VERSIONS), 'Unknown Minecraft version'
    mod_version = CONFIG['mod']['version']
    result = [inspect(v, no_render, mod_version) for v in versions for no_render in (False, True)]
    output = ROOT / 'build/validation/artifacts.json'
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(f'ARTIFACTS_OK {len(result)} exact-version deliverables')


if __name__ == '__main__':
    main()
