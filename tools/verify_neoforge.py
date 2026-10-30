"""Validate packaged NeoForge JARs with the real FML transformer and version-specific gameplay checks.

Run Gradle writeValidationConfig and assemble first. No game window or user world is opened.
"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import re
import struct
import subprocess
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PROJECT = ROOT / 'platforms/neoforge-26.3'
REPORTS = ROOT / 'build/neoforge-pilot/validation'


def quote(value):
    return '"' + str(value).replace('\\', '/').replace('"', '\\"') + '"'


def compile_java(java, cp, target, sources, log, release=25):
    target.mkdir(parents=True, exist_ok=True)
    args = ['-encoding', 'UTF-8', '-proc:none', '--release', str(release), '-cp', quote(os.pathsep.join(cp)), '-d', quote(target)]
    args.extend(quote(source) for source in sources)
    argfile = target.parent / (target.name + '-compile.args')
    argfile.write_text('\n'.join(args) + '\n', encoding='utf-8')
    with log.open('w', encoding='utf-8') as stream:
        result = subprocess.run([java, '-J-Duser.language=en', '-J-Dfile.encoding=UTF-8', '@' + str(argfile)], cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT)
    if result.returncode:
        raise RuntimeError(f'Compilation failed: {log}\n{log.read_text(encoding="utf-8", errors="replace")[-6000:]}')


def inspect_jar(jar, selection, variant, runtime):
    expected_client = selection['client'] if variant == 'full' else selection['clientCore']
    with zipfile.ZipFile(jar) as z:
        names = set(z.namelist())
        metadata = tomllib.loads(z.read('META-INF/neoforge.mods.toml').decode())
        expected_version = runtime['modVersion'] + ('-norender' if variant == 'norender' else '')
        assert metadata['mods'][0]['modId'] == 'biblioteca'
        assert metadata['mods'][0]['version'] == expected_version
        assert any(d['modId'] == 'minecraft' and d['versionRange'] == '[' + runtime['minecraft'] + ']' for d in metadata['dependencies']['biblioteca'])
        assert 'fabric.mod.json' not in names and not any(n.endswith('.accesswidener') for n in names)
        for name, key, expected in [('biblioteca.mixins.json', 'mixins', selection['mixins']),
                                    ('biblioteca.client.mixins.json', 'client', expected_client)]:
            config = json.loads(z.read(name))
            assert set(config[key]) == set(expected), (jar, name, 'incorrect mixin selection')
            assert config['required'] and config['injectors']['defaultRequire'] == 1
            assert config['compatibilityLevel'] == 'JAVA_' + str(runtime.get('java', 25))
            for mixin in expected:
                assert config['package'].replace('.', '/') + '/' + mixin + '.class' in names
        if runtime.get('requiresAccessTransformer', runtime['minecraft'] == '26.3'):
            assert 'META-INF/accesstransformer.cfg' in names
        for resource in ['assets/biblioteca/icon.png', 'assets/biblioteca/lang/en_us.json', 'assets/biblioteca/lang/zh_cn.json']:
            assert resource in names
        for name in names:
            if name.endswith('.class'):
                assert b'net/fabricmc/' not in z.read(name), ('Fabric reference in NeoForge artifact', name)
                assert struct.unpack('>H', z.read(name)[6:8])[0] <= int(runtime.get('java', 25)) + 44, (jar, 'Java target', name)
        if variant == 'norender':
            for name in set(selection['client']) - set(selection['clientCore']):
                assert f'babel/biblioteca/client/mixin/{name}.class' not in names
    return hashlib.sha256(jar.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--java', default='java')
    parser.add_argument('--javac', default='javac')
    parser.add_argument('--sides', default='server,client', help='Comma-separated server,client')
    parser.add_argument('--variants', default='full,norender', help='Comma-separated full,norender')
    parser.add_argument('--runtime-config', type=Path, default=PROJECT / 'build/validation/runtime.json')
    args = parser.parse_args()
    assert set(args.sides.split(',')) <= {'server', 'client'}
    assert set(args.variants.split(',')) <= {'full', 'norender'}
    runtime = json.loads(args.runtime_config.read_text(encoding='utf-8'))
    selection = json.loads(Path(runtime['selection']).read_text(encoding='utf-8'))
    assert runtime['classpath'], 'Missing runtime classpath; regenerate writeValidationConfig'
    reports = Path(runtime.get('reports', REPORTS))
    reports.mkdir(parents=True, exist_ok=True)
    # A failed or interrupted compilation must not leave an earlier passing result visible.
    (reports / 'results.json').write_text('[]\n', encoding='utf-8')
    compile_cp = runtime['classpath']
    release = int(runtime.get('java', 25))
    compile_java(args.javac, compile_cp, reports / 'verifier', [ROOT / 'tools/NeoForgeMixinVerifier.java'], reports / 'verifier-compile.log', release)
    sources = sorted((ROOT / 'tools/validation').glob('*.java')) + sorted((ROOT / 'tools/neoforge-validation').glob('*.java'))
    if runtime['minecraft'] == '1.21.11':
        sources += sorted((ROOT / 'tools/validation/neoforge12111').glob('*.java'))
    if runtime['minecraft'] == '26.3':
        sources += sorted((ROOT / 'tools/validation/main100').glob('*.java'))
        sources += sorted((ROOT / 'tools/validation/renderpearl').glob('*.java'))
    compile_java(args.javac, compile_cp, reports / 'checks', sources, reports / 'checks-compile.log', release)
    results = []
    for variant in args.variants.split(','):
        suffix = '-norender' if variant == 'norender' else ''
        jar = Path(runtime['artifacts'][variant])
        digest = inspect_jar(jar, selection, variant, runtime)
        artifact_classes = reports / 'artifacts' / digest
        artifact_classes.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(jar) as z:
            z.extractall(artifact_classes)
        for side in args.sides.split(','):
            target = reports / variant / side
            target.mkdir(parents=True, exist_ok=True)
            game_dir = target / 'run'
            game_dir.mkdir(parents=True, exist_ok=True)
            config = dict(runtime, side=side, variant=variant, artifactClasses=str(artifact_classes), gameDir=str(game_dir))
            config_path = target / 'config.json'
            config_path.write_text(json.dumps(config, indent=2), encoding='utf-8')
            cp = [p for p in runtime['classpath'] if p not in (runtime['classes'], runtime['resources'])]
            cp += [str(reports / 'verifier')]
            # FML loads the actual packaged JAR; check classes are appended to the same test mod.
            jvm = ['--enable-native-access=ALL-UNNAMED',
                   '--add-opens=java.base/java.lang.invoke=ALL-UNNAMED', '-Djava.awt.headless=true',
                   '-Dfile.encoding=UTF-8', '-Duser.language=en', '-Duser.country=US',
                   '-Dmixin.debug.countInjections=true',
                   '-Dbiblioteca.validation.version=' + runtime['modVersion'] + suffix,
                   '-Dbiblioteca.validation.minecraft=' + runtime['minecraft'],
                   '-Dfml.modFolders=' + os.pathsep.join(['biblioteca%%' + str(jar), 'biblioteca%%' + str(reports / 'checks')])]
            if release >= 24:
                jvm.append('--sun-misc-unsafe-memory-access=allow')
            argfile = target / 'java.args'
            argfile.write_text('\n'.join(map(quote, jvm)) + '\n--class-path\n' + quote(os.pathsep.join(cp))
                               + '\nNeoForgeMixinVerifier\n' + quote(config_path) + '\n', encoding='utf-8')
            log = target / 'loader.log'
            with log.open('w', encoding='utf-8') as stream:
                try:
                    result = subprocess.run([args.java, '@' + str(argfile)], cwd=game_dir,
                                            stdout=stream, stderr=subprocess.STDOUT, timeout=240)
                    exit_code = result.returncode
                except subprocess.TimeoutExpired:
                    stream.write('\nVALIDATION_TIMEOUT after 240 seconds\n')
                    exit_code = -1
            text = log.read_text(encoding='utf-8', errors='replace')
            marker = re.search(r'NEOFORGE_OK [^\r\n]+', text)
            record = {'minecraft': runtime['minecraft'], 'neoforge': runtime['neoforge'], 'modVersion': runtime['modVersion'] + suffix, 'variant': variant, 'side': side,
                      'passed': exit_code == 0 and marker is not None, 'exitCode': exit_code, 'sha256': digest,
                      'jar': str(jar.relative_to(ROOT)), 'log': str(log.relative_to(ROOT)),
                      'targets': len(re.findall(r'^TARGET_OK ', text, re.MULTILINE)),
                      'checks': re.findall(r'(?:DATA_OK |PASS: )[^\r\n]+', text)}
            results.append(record)
            print(marker.group(0) if record['passed'] else f'FAILED {variant} {side}: {log}', flush=True)
            (reports / 'results.json').write_text(json.dumps(results, indent=2) + '\n', encoding='utf-8')
    raise SystemExit(0 if results and all(r['passed'] for r in results) else 1)


if __name__ == '__main__':
    main()
