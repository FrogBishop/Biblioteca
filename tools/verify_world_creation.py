"""Check native world-creation layouts and settings navigation in isolated graphical clients."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]


def quote(value):
    return '"' + str(value).replace('\\', '/').replace('"', '\\"') + '"'


def verify(args, version):
    cfg = json.loads((ROOT / 'build/validation/runtime' / (version + '.json')).read_text(encoding='utf-8'))
    base = ROOT / 'build/validation/world-creation' / args.label / version
    run = base / time.strftime('run-%Y%m%d-%H%M%S')
    run.mkdir(parents=True)
    classes = run / 'probe'
    classes.mkdir()
    compile_args = ['-encoding', 'UTF-8', '--release', cfg['java'], '-cp', os.pathsep.join(cfg['classpath']),
        '-d', classes, ROOT / 'tools/validation/world-creation/WorldCreationProbe.java']
    argfile = run / 'compile.args'
    argfile.write_text('\n'.join(map(quote, compile_args)), encoding='utf-8')
    with (run / 'compile.log').open('w', encoding='utf-8') as output:
        compiled = subprocess.run([args.javac, '@' + str(argfile)], stdout=output, stderr=subprocess.STDOUT)
    if compiled.returncode:
        print('WORLD_UI_COMPILE_FAILED ' + version + ': ' + str(run / 'compile.log'), flush=True)
        return {'minecraft': version, 'passed': False, 'log': str((run / 'compile.log').relative_to(ROOT))}
    (classes / 'fabric.mod.json').write_text(json.dumps({'schemaVersion': 1, 'id': 'biblioteca_world_ui_probe',
        'version': '1', 'environment': 'client', 'entrypoints': {'client': ['biblioteca.worldcreation.WorldCreationProbe']}}))
    jar = args.artifact_dir / f'biblioteca-{args.mod_version}+{version}.jar'
    (run / 'mods').mkdir()
    shutil.copy2(jar, run / 'mods')
    if version == '1.16.5' and args.sodium_jar:
        shutil.copy2(args.sodium_jar, run / 'mods')
    (run / 'options.txt').write_text('lang:zh_cn\nguiScale:4\nrenderDistance:8\npauseOnLostFocus:false\nmaxFps:60\nfullscreen:false\n', encoding='utf-8')
    cp = [p for p in cfg['classpath'] if p not in (cfg['classes'], cfg['resources'])]
    if args.loader_jar:
        cp = [p for p in cp if '/net.fabricmc/fabric-loader/' not in p.replace('\\', '/')]
        cp.append(str(args.loader_jar.resolve()))
    cp.append(str(classes))
    options = ['-Xmx2G', '-Dfile.encoding=UTF-8', '-Duser.language=en']
    launch = Path(next(p.split('=', 1)[1] for p in cfg['clientJvmArgs'] if p.startswith('-Dfabric.dli.config=')))
    group, game_args = '', []
    for line in launch.read_text(encoding='utf-8').splitlines():
        if not line.startswith('\t'):
            group = line.strip()
        elif group in ('commonProperties', 'clientProperties') and '=' in line:
            key, value = line.strip().split('=', 1)
            if key == 'log4j.configurationFile': value = Path(value).as_uri()
            options.append('-D' + key + '=' + value)
        elif group == 'clientArgs':
            game_args.append(line.strip())
    index = game_args[game_args.index('--assetIndex') + 1]
    installed_index = index.removeprefix(version + '-')
    if args.assets_dir and (args.assets_dir / 'indexes' / (installed_index + '.json')).is_file():
        game_args[game_args.index('--assetIndex') + 1] = installed_index
        game_args[game_args.index('--assetsDir') + 1] = str(args.assets_dir.resolve())
    command = options + ['-cp', os.pathsep.join(cp), 'net.fabricmc.loader.impl.launch.knot.KnotClient',
        '--username', 'WorldUiProbe', '--gameDir', run, '--width', '1920', '--height', '1000', '--version', version] + game_args
    argfile = run / 'client.args'
    argfile.write_text('\n'.join(map(quote, command)), encoding='utf-8')
    log = run / 'client.log'
    with log.open('w', encoding='utf-8') as output:
        proc = subprocess.Popen([args.java, '@' + str(argfile)], cwd=run, stdout=output, stderr=subprocess.STDOUT)
        try:
            code = proc.wait(timeout=180)
        except subprocess.TimeoutExpired:
            proc.terminate()
            try: code = proc.wait(timeout=15)
            except subprocess.TimeoutExpired: proc.kill(); code = proc.wait(timeout=15)
    text = log.read_text(encoding='utf-8', errors='replace')
    frames = [run / (name + '.png') for name in ('options-scale4', 'attributes', 'options-scale3')]
    result = {'minecraft': version, 'passed': code == 0 and 'WORLD_UI_DONE passed=true' in text
        and 'WORLD_UI_FAILURE' not in text and all(p.is_file() for p in frames), 'exitCode': code,
        'layout': 'tabs' if tuple(map(int, version.split('.'))) >= (1, 19, 4) else 'classic',
        'log': str(log.relative_to(ROOT)), 'frames': [str(p.relative_to(ROOT)) for p in frames],
        'samples': [line for line in text.splitlines() if 'WORLD_UI_' in line],
        'artifactSha256': hashlib.sha256(jar.read_bytes()).hexdigest()}
    (base / 'results.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(('WORLD_UI_OK ' if result['passed'] else 'WORLD_UI_FAILED ') + version + ': ' + str(log), flush=True)
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--versions', help='Comma-separated versions; default: every supported node through 1.20.4')
    parser.add_argument('--artifact-dir', type=Path, default=ROOT / 'build/distributions')
    parser.add_argument('--mod-version', required=True)
    parser.add_argument('--assets-dir', type=Path, help='Reuse launcher assets when the matching native index exists')
    parser.add_argument('--sodium-jar', type=Path, help='Optionally include Sodium in the 1.16.5 check')
    parser.add_argument('--loader-jar', type=Path)
    parser.add_argument('--label', default='matrix')
    parser.add_argument('--java', default='java')
    parser.add_argument('--javac', default='javac')
    args = parser.parse_args()
    if not args.label.replace('-', '').replace('_', '').isalnum():
        raise SystemExit('Label must contain only letters, digits, hyphens and underscores.')
    versions = args.versions.split(',') if args.versions else [p.stem for p in (ROOT / 'build/validation/runtime').glob('*.json')
        if (1, 16, 5) <= tuple(map(int, p.stem.split('.'))) <= (1, 20, 4)]
    versions.sort(key=lambda v: tuple(map(int, v.split('.'))))
    args.artifact_dir = args.artifact_dir.resolve()
    report = ROOT / 'build/validation/world-creation' / args.label / 'results.json'
    report.parent.mkdir(parents=True, exist_ok=True)
    previous = json.loads(report.read_text()) if report.exists() else []
    combined = {r['minecraft']: r for r in previous if r['minecraft'] not in versions}
    passed = True
    for version in versions:
        result = verify(args, version)
        passed &= result['passed']
        combined[version] = result
        report.write_text(json.dumps(sorted(combined.values(), key=lambda r: tuple(map(int, r['minecraft'].split('.')))), indent=2) + '\n', encoding='utf-8')
    raise SystemExit(0 if passed else 1)


if __name__ == '__main__':
    main()
