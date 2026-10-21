"""Audit exact-version deliverables, including remapped metadata and both variants."""
from pathlib import Path
import argparse
import hashlib
import json
import re
import struct
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VERSIONS = ['1.20.4', '1.20.5', '1.20.6', '1.21'] + [f'1.21.{i}' for i in range(1, 12)] + ['26.1', '26.1.1', '26.1.2', '26.2', '26.3']
UI_AUDIO = {'ClientLevelDirtyMixin', 'CreateWorldScreenMoreTabMixin', 'GridLayoutTabAccessor', 'OptionsSubScreenAccessor',
            'VideoSettingsScreenMixin', 'ClientLevelSoundYLoopMixin', 'SoundEngineYLoopMixin', 'SubtitleDirectionYLoopMixin', 'SubtitleDistanceYLoopMixin'}


def inspect(version, no_render, mod_version):
    suffix = '-norender' if no_render else ''
    file = ROOT / f'build/distributions/biblioteca-{mod_version}+{version}{suffix}.jar'
    java = 25 if version.startswith('26.') else 17 if version == '1.20.4' else 21
    with zipfile.ZipFile(file) as jar:
        names = set(jar.namelist())
        meta = json.loads(jar.read('fabric.mod.json'))
        assert meta['version'] == f'{mod_version}+{version}{suffix}', (file, 'version')
        assert meta['depends']['minecraft'] == version, (file, 'unsafe Minecraft range')
        assert meta['depends']['java'] == f'>={java}', (file, 'Java version')
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
            assert set(client['client']) == UI_AUDIO, (file, 'no-render hook drift')
        else:
            assert {'RainImpactsYLoopMixin', 'SectionOcclusionGraphLoopMixin', 'BlockEntityRenderRangeYLoopMixin'} <= set(client['client']), (file, 'missing visuals')
            expected = 'LevelRendererYLoopMixin' if version == '26.3' else 'GpuTerrainYLoopMixin' if version.startswith('26.') or version in [f'1.21.{i}' for i in range(6, 12)] else 'GpuLayerTerrainYLoopMixin' if version == '1.21.5' else 'ClassicTerrainYLoopMixin'
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
    mod_version = re.search(r'^mod.version\s*=\s*"([^"]+)"', (ROOT / 'stonecutter.properties.toml').read_text(), re.M)[1]
    result = [inspect(v, no_render, mod_version) for v in versions for no_render in (False, True)]
    output = ROOT / 'build/validation/artifacts.json'
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(f'ARTIFACTS_OK {len(result)} exact-version deliverables')


if __name__ == '__main__':
    main()
