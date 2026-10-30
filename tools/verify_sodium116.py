"""Run a real 1.16.5 + Sodium 0.2 client against a copied world; preserve frames and draw evidence."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import time
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def quote(value):
    return '"' + str(value).replace('\\', '/').replace('"', '\\"') + '"'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--runtime-config', type=Path, default=ROOT / 'build/validation/runtime/1.16.5.json')
    parser.add_argument('--jar', type=Path, help='Use a remapped artifact; otherwise test compiled development classes')
    parser.add_argument('--sodium-jar', type=Path, required=True)
    parser.add_argument('--world-template', type=Path, required=True)
    parser.add_argument('--assets-dir', type=Path, required=True)
    parser.add_argument('--asset-index', default='1.16')
    parser.add_argument('--label', default='sodium')
    parser.add_argument('--baseline', action='store_true', help='Expect the original invisible-terrain defect')
    parser.add_argument('--oneshot', action='store_true', help='Use Sodium\'s non-indirect backend')
    parser.add_argument('--java', default='java')
    parser.add_argument('--javac', default='javac')
    parser.add_argument('--loader-jar', type=Path)
    args = parser.parse_args()
    if not args.label.replace('-', '').replace('_', '').isalnum():
        raise SystemExit('Label must contain only letters, digits, hyphens and underscores.')
    if not (args.world_template / 'level.dat').is_file():
        raise SystemExit('World template must contain level.dat.')
    cfg = json.loads(args.runtime_config.read_text(encoding='utf-8'))
    sodium_compile = cfg['optionalSodiumClasspath']
    if cfg['minecraft'] != '1.16.5' or len(sodium_compile) != 1:
        raise SystemExit('Export the 1.16.5 validation configuration including the optional Sodium compile dependency.')
    base = ROOT / 'build/validation/1.16.5/sodium' / args.label
    run = base / time.strftime('run-%Y%m%d-%H%M%S')
    run.mkdir(parents=True)
    shutil.copytree(args.world_template, run / 'saves/sodium-world', ignore=shutil.ignore_patterns('session.lock'))
    (run / 'mods').mkdir()
    shutil.copy2(args.sodium_jar, run / 'mods')
    (run / 'options.txt').write_text('renderDistance:8\npauseOnLostFocus:false\nmaxFps:60\nfullscreen:false\nfov:0.0\n', encoding='utf-8')
    (run / 'config').mkdir()
    (run / 'config/sodium-options.json').write_text(json.dumps({'advanced': {'use_chunk_multidraw': not args.oneshot}}))
    classes = run / 'probe-classes'
    classes.mkdir()
    sources = sorted((ROOT / 'tools/validation/sodium116').glob('*.java'))
    compile_args = ['-encoding', 'UTF-8', '--release', '17', '-cp', os.pathsep.join(cfg['classpath'] + sodium_compile), '-d', classes, *sources]
    argfile = run / 'compile.args'
    argfile.write_text('\n'.join(map(quote, compile_args)), encoding='utf-8')
    subprocess.run([args.javac, '@' + str(argfile)], check=True)
    probe = run / 'probe.jar'
    with zipfile.ZipFile(probe, 'w') as jar:
        for path in classes.rglob('*.class'):
            jar.write(path, path.relative_to(classes).as_posix())
        jar.writestr('fabric.mod.json', json.dumps({'schemaVersion': 1, 'id': 'biblioteca_sodium_probe', 'version': '1', 'environment': '*',
            'entrypoints': {'main': ['biblioteca.sodiumtest.SodiumProbe'], 'client': ['biblioteca.sodiumtest.SodiumProbe']},
            'mixins': [{'config': 'sodium-probe.mixins.json', 'environment': 'client'}]}))
        jar.writestr('sodium-probe.mixins.json', json.dumps({'required': True, 'package': 'biblioteca.sodiumtest.mixin',
            'compatibilityLevel': 'JAVA_17', 'client': ['SodiumDrawObserver', 'SodiumOriginObserver'], 'injectors': {'defaultRequire': 1}}))
    cp = list(cfg['classpath'])
    if args.jar:
        cp = [p for p in cp if p not in (cfg['classes'], cfg['resources'])]
        shutil.copy2(args.jar, run / 'mods')
    if args.loader_jar:
        cp = [p for p in cp if '/net.fabricmc/fabric-loader/' not in p.replace('\\', '/')]
        cp.append(str(args.loader_jar.resolve()))
    cp.append(str(probe))
    options = ['-Xmx2G', '-Dfile.encoding=UTF-8', '-Duser.language=en', '-Dbiblioteca.sodiumTest.baseline=' + str(args.baseline).lower()]
    launch = Path(next(p.split('=', 1)[1] for p in cfg['clientJvmArgs'] if p.startswith('-Dfabric.dli.config=')))
    group = ''
    for line in launch.read_text(encoding='utf-8').splitlines():
        if not line.startswith('\t'):
            group = line.strip()
        elif group in ('commonProperties', 'clientProperties') and '=' in line:
            key, value = line.strip().split('=', 1)
            if key == 'log4j.configurationFile': value = Path(value).as_uri()
            options.append('-D' + key + '=' + value)
    command = options + ['-cp', os.pathsep.join(cp), 'net.fabricmc.loader.impl.launch.knot.KnotClient',
        '--username', 'SodiumProbe', '--gameDir', run, '--width', '960', '--height', '600', '--version', '1.16.5',
        '--assetsDir', args.assets_dir.resolve(), '--assetIndex', args.asset_index]
    argfile = run / 'client.args'
    argfile.write_text('\n'.join(map(quote, command)), encoding='utf-8')
    log = run / 'client.log'
    with log.open('w', encoding='utf-8') as output:
        proc = subprocess.Popen([args.java, '@' + str(argfile)], cwd=run, stdout=output, stderr=subprocess.STDOUT)
        try:
            code = proc.wait(timeout=240)
        except subprocess.TimeoutExpired:
            proc.terminate()
            try: code = proc.wait(timeout=15)
            except subprocess.TimeoutExpired: proc.kill(); code = proc.wait(timeout=15)
    text = log.read_text(encoding='utf-8', errors='replace')
    samples = [line for line in text.splitlines() if 'SODIUM_' in line]
    frames = [run / (name + '.png') for name in ('ordinary', 'loop-ground', 'loop-ceiling', 'restored')]
    result = {'passed': code == 0 and 'SODIUM_DONE passed=true' in text and 'SODIUM_FAILURE' not in text and all(p.is_file() for p in frames),
        'exitCode': code, 'log': str(log.relative_to(ROOT)), 'frames': [str(p.relative_to(ROOT)) for p in frames], 'samples': samples,
        'sodiumSha256': hashlib.sha256(args.sodium_jar.read_bytes()).hexdigest(),
        'artifactSha256': hashlib.sha256(args.jar.read_bytes()).hexdigest() if args.jar else None}
    (base / 'results.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print('\n'.join(samples))
    print(('SODIUM_OK ' if result['passed'] else 'SODIUM_FAILED ') + str(log), flush=True)
    raise SystemExit(0 if result['passed'] else 1)


if __name__ == '__main__':
    main()
