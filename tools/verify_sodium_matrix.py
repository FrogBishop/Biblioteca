"""Check packaged Biblioteca variants with official Sodium releases in the real Fabric transformer.

This is a strict class-transformation audit, not a graphical compatibility claim. Exact
download metadata, hashes and per-combination logs are preserved under build/sodium-matrix.
"""
from pathlib import Path
import argparse
import concurrent.futures
import hashlib
import json
import os
import re
import shutil
import subprocess
import time
import tomllib
import urllib.request
import zipfile

from verify_matrix import ROOT, quote

BASE = ROOT / 'build/sodium-matrix'
API = 'https://api.modrinth.com/v2/project/AANobbMI/version'


def download(url):
    request = urllib.request.Request(url, headers={'User-Agent': 'Biblioteca-Sodium-compatibility-audit/1.0'})
    with urllib.request.urlopen(request, timeout=60) as response:
        return response.read()


def select(refresh=False, include_prereleases=False):
    metadata = BASE / 'modrinth-versions.json'
    if refresh or not metadata.exists():
        metadata.write_bytes(download(API))
    releases = json.loads(metadata.read_text(encoding='utf-8'))
    versions = re.findall(r'"([^"]+)"', (ROOT / 'settings.gradle.kts').read_text().split('val allVersions = listOf(')[1].split(')')[0])
    rows = []
    for loader, nodes in [('fabric', versions), ('neoforge', ['1.21.11', '26.1', '26.1.1', '26.1.2', '26.2', '26.3'])]:
        for mc in nodes:
            matches = sorted((v for v in releases if mc in v['game_versions'] and loader in v['loaders']),
                             key=lambda v: v['date_published'], reverse=True)
            release = next(iter(matches), None) if include_prereleases else next(
                (v for v in matches if v['version_type'] == 'release'), next(iter(matches), None))
            rows.append({'minecraft': mc, 'loader': loader, 'release': release})
    (BASE / 'selected.json').write_text(json.dumps(rows, indent=2) + '\n', encoding='utf-8')
    return rows


def fetch(row):
    release = row['release']
    if not release:
        return None
    entry = next((f for f in release['files'] if f['primary']), release['files'][0])
    path = BASE / 'downloads' / release['id'] / entry['filename']
    path.parent.mkdir(parents=True, exist_ok=True)
    if not path.exists():
        path.write_bytes(download(entry['url']))
    data = path.read_bytes()
    if hashlib.sha512(data).hexdigest() != entry['hashes']['sha512']:
        raise ValueError('Sodium download hash mismatch: ' + str(path))
    return path


def verify(row, variant, args):
    mc = row['minecraft']
    loader = row['loader']
    out = BASE / args.label / loader / mc / variant
    out.mkdir(parents=True, exist_ok=True)
    sodium = fetch(row)
    artifact = ROOT / 'build/distributions' / ('biblioteca-' + args.mod_version + '+' + mc + ('-neoforge' if loader == 'neoforge' else '')
                                            + ('-norender' if variant == 'norender' else '') + '.jar')
    if not artifact.is_file():
        raise FileNotFoundError(artifact)
    runtime_file = (ROOT / 'build/validation/runtime' / (mc + '.json')) if loader == 'fabric' else (
        ROOT / ('platforms/neoforge-' + mc) / 'build/validation/runtime.json')
    runtime = json.loads(runtime_file.read_text(encoding='utf-8'))
    original_classes, original_resources = runtime['classes'], runtime['resources']
    cp = [p for p in runtime['classpath'] if p not in (runtime['classes'], runtime['resources'])]
    lwjgl_version = dict(value.split('=', 1) for value in args.lwjgl_override).get(mc)
    if lwjgl_version:
        replaced = []
        for p in cp:
            match = re.search(r'(lwjgl(?:-[a-z0-9]+)?)-([0-9.]+)(.*\.jar)$', Path(p).name)
            if match:
                name, _, suffix = match.groups()
                replacement = args.lwjgl_library_dir / name / lwjgl_version / (name + '-' + lwjgl_version + suffix)
                if not replacement.is_file():
                    raise FileNotFoundError(replacement)
                p = str(replacement.resolve())
            replaced.append(p)
        cp = replaced
    extracted = out / 'artifact'
    extracted.mkdir(exist_ok=True)
    with zipfile.ZipFile(artifact) as jar:
        jar.extractall(extracted)
    frozen = out / artifact.name
    shutil.copy2(artifact, frozen)
    runtime.update({'classes': str(extracted), 'resources': str(extracted),
                    'extraMods': ([str(frozen)] if not args.sodium_only else []) + [str(sodium)],
                    'sodiumOnly': args.sodium_only, 'requiredMods': ['sodium'], 'checkOptionalInjections': False})
    if loader == 'fabric' and not mc.startswith('26.'):
        runtime['targetNamespace'] = 'intermediary'
    if loader == 'neoforge':
        runtime.update({'side': 'client', 'variant': variant, 'artifactClasses': str(extracted),
                        'gameDir': str(out / 'run'), 'transformsOnly': True,
                        'classes': original_classes, 'resources': original_resources})
        mods = out / 'run/mods'
        mods.mkdir(parents=True, exist_ok=True)
        shutil.copy2(sodium, mods / sodium.name)
    config = out / 'runtime.json'
    config.write_text(json.dumps(runtime, indent=2), encoding='utf-8')
    verifier = out / 'verifier'
    verifier.mkdir(exist_ok=True)
    compile_args = ['-encoding', 'UTF-8', '--release', runtime['java'], '-cp', os.pathsep.join(cp),
                    '-d', str(verifier), str(ROOT / ('tools/MatrixMixinVerifier.java' if loader == 'fabric' else 'tools/NeoForgeMixinVerifier.java'))]
    argfile = out / 'compile.args'
    argfile.write_text('\n'.join(map(quote, compile_args)), encoding='utf-8')
    with (out / 'compile.log').open('w', encoding='utf-8') as log:
        subprocess.run([args.javac, '@' + str(argfile)], cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, check=True)
    command = ['-Djava.awt.headless=true', '-Duser.language=en', '-Dfile.encoding=UTF-8', '--add-modules=jdk.zipfs']
    if loader == 'neoforge':
        command += ['--enable-native-access=ALL-UNNAMED', '--add-opens=java.base/java.lang.invoke=ALL-UNNAMED',
                    '-Dmixin.debug.countInjections=false']
        if not args.sodium_only:
            command += ['-Dfml.modFolders=biblioteca%%' + str(frozen)]
    command += ['--class-path', os.pathsep.join(cp + [str(verifier)]),
                'MatrixMixinVerifier' if loader == 'fabric' else 'NeoForgeMixinVerifier', str(config)]
    if loader == 'fabric':
        command += ['client', str(out / 'run')]
    argfile = out / 'client.args'
    argfile.write_text('\n'.join(map(quote, command)), encoding='utf-8')
    logpath = out / 'client.log'
    with logpath.open('w', encoding='utf-8') as log:
        try:
            code = subprocess.run([args.java, '@' + str(argfile)], cwd=out, stdout=log, stderr=subprocess.STDOUT, timeout=240).returncode
        except subprocess.TimeoutExpired:
            code = -1
    text = logpath.read_text(encoding='utf-8', errors='replace')
    passed = code == 0 and ('MATRIX_OK ' if loader == 'fabric' else 'NEOFORGE_OK ') in text and 'EXTRA_MOD_OK sodium ' in text
    result = {'minecraft': mc, 'loader': loader, 'variant': variant, 'sodium': row['release']['version_number'],
              'bibliotecaLoaded': not args.sodium_only,
              'sodiumReleaseType': row['release']['version_type'],
              'sodiumReleaseUrl': 'https://modrinth.com/mod/sodium/version/' + row['release']['id'],
              'runtimeConfig': str(runtime_file.relative_to(ROOT)),
              'passed': passed, 'exitCode': code, 'targets': len(re.findall(r'^TARGET_OK ', text, re.M)),
              'artifactSha256': hashlib.sha256(frozen.read_bytes()).hexdigest(),
              'sodiumSha256': hashlib.sha256(sodium.read_bytes()).hexdigest(),
              'lwjglOverride': lwjgl_version,
              'log': str(logpath.relative_to(ROOT)),
              'errors': [line for line in text.splitlines() if any(key in line for key in
                         ['TARGET_FAILED ', 'Exception in thread', 'Caused by:', 'Incompatible mods', 'conflict was detected',
                          'java.util.NoSuchElementException', 'Mixin apply for mod', 'game failed to start', 'Required version:'])]}
    (out / 'result.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(('SODIUM_TRANSFORM_OK ' if passed else 'SODIUM_TRANSFORM_FAILED ') + mc + ' ' + variant + ' ' + result['sodium'], flush=True)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--versions', default='')
    parser.add_argument('--mod-version', default=tomllib.loads((ROOT / 'stonecutter.properties.toml').read_text(encoding='utf-8'))['mod']['version'])
    parser.add_argument('--variants', default='full,norender')
    parser.add_argument('--loaders', default='fabric,neoforge')
    parser.add_argument('--label', default=time.strftime('audit-%Y%m%d-%H%M%S'))
    parser.add_argument('--workers', type=int, default=2)
    parser.add_argument('--java', default='java')
    parser.add_argument('--javac', default='javac')
    parser.add_argument('--lwjgl-override', action='append', default=[], metavar='MC=LWJGL',
                        help='Use the exact game library version required by Sodium, e.g. 1.20.1=3.3.1')
    parser.add_argument('--lwjgl-library-dir', type=Path, help='Existing launcher libraries/org/lwjgl directory; read only')
    parser.add_argument('--download-only', action='store_true')
    parser.add_argument('--refresh-metadata', action='store_true', help='Refresh the saved official release list before selecting versions')
    parser.add_argument('--include-prereleases', action='store_true', help='Select the latest release even when it is alpha/beta')
    parser.add_argument('--sodium-only', action='store_true', help='Control run without Biblioteca; still force the same native target classes')
    args = parser.parse_args()
    if args.lwjgl_override and not args.lwjgl_library_dir:
        parser.error('--lwjgl-override requires --lwjgl-library-dir')
    if any(not re.fullmatch(r'[0-9.]+=[0-9.]+', value) for value in args.lwjgl_override):
        parser.error('--lwjgl-override must have the form MC=LWJGL')
    if not re.fullmatch(r'[\w-]+', args.label):
        raise SystemExit('Invalid label')
    if not set(args.variants.split(',')) <= {'full', 'norender'} or not set(args.loaders.split(',')) <= {'fabric', 'neoforge'}:
        parser.error('Unknown variant or loader')
    BASE.mkdir(parents=True, exist_ok=True)
    selected = set(filter(None, args.versions.split(',')))
    rows = [r for r in select(args.refresh_metadata, args.include_prereleases)
            if (not selected or r['minecraft'] in selected) and r['loader'] in args.loaders.split(',')]
    if not rows or selected - {r['minecraft'] for r in rows}:
        parser.error('No matching supported versions')
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as pool:
        unique = {r['release']['id']: r for r in rows if r['release']}
        futures = [pool.submit(fetch, r) for r in unique.values()]
        for future in concurrent.futures.as_completed(futures):
            future.result()
    for row in rows:
        print('SELECTED', row['loader'], row['minecraft'], (row['release'] or {}).get('version_number', 'UNAVAILABLE'), flush=True)
    if args.download_only:
        return
    results = []
    report = BASE / args.label / 'results.json'
    report.parent.mkdir(parents=True, exist_ok=True)
    (report.parent / 'selected.json').write_text(json.dumps(rows, indent=2) + '\n', encoding='utf-8')
    report.write_text('[]\n', encoding='utf-8')
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = [pool.submit(verify, r, variant, args)
                   for r in rows if r['release'] for variant in args.variants.split(',')]
        for future in concurrent.futures.as_completed(futures):
            results.append(future.result())
            report.write_text(json.dumps(results, indent=2) + '\n', encoding='utf-8')
    raise SystemExit(0 if all(r['passed'] for r in results) else 1)


if __name__ == '__main__':
    main()
