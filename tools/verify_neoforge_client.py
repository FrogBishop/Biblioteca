"""Exercise the 1.21.11 NeoForge renderer, live light packets, movement and creation UI."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import time
import zipfile
from verify_neoforge import ROOT, compile_java, quote


def main():
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    parser = argparse.ArgumentParser()
    parser.add_argument('--runtime-config', type=Path, required=True)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--world-template', type=Path)
    parser.add_argument('--ui-only', action='store_true')
    parser.add_argument('--assets-dir', type=Path, required=True)
    parser.add_argument('--asset-index', required=True)
    parser.add_argument('--java', default='java')
    parser.add_argument('--javac', default='javac')
    args = parser.parse_args()
    runtime = json.loads(args.runtime_config.read_text(encoding='utf-8'))
    if runtime['minecraft'] != '1.21.11':
        raise SystemExit('This client probe targets Minecraft 1.21.11.')
    if not args.ui_only and (not args.world_template or not (args.world_template / 'level.dat').is_file()):
        raise SystemExit('World mode requires a saved template containing the hybrid excavation fixtures.')
    output = ROOT / 'build/neoforge-1.21.11/client-validation' / ('ui' if args.ui_only else 'world')
    run = output / time.strftime('run-%Y%m%d-%H%M%S')
    run.mkdir(parents=True)
    source = ROOT / 'tools/neoforge-client-validation/NeoForgeClientProbe.java'
    compile_java(args.javac, runtime['classpath'], run / 'probe-classes', [source], run / 'compile.log', runtime['java'])
    probe = run / 'probe.jar'
    with zipfile.ZipFile(probe, 'w', zipfile.ZIP_DEFLATED) as jar:
        for file in (run / 'probe-classes').rglob('*.class'):
            jar.write(file, file.relative_to(run / 'probe-classes').as_posix())
        jar.writestr('META-INF/neoforge.mods.toml', 'license="GPLv3"\n[[mods]]\nmodId="biblioteca_client_check"\nversion="1"\ndisplayName="Biblioteca client validation"\n')
    if not args.ui_only:
        shutil.copytree(args.world_template, run / 'saves/check-world')
    (run / 'options.txt').write_text('pauseOnLostFocus:false\nmaxFps:30\nfullscreen:false\nrenderDistance:12\n'
        'simulationDistance:8\nfov:0.0\nfovEffectScale:0.0\nlang:zh_cn\n', encoding='utf-8')
    (run / 'config').mkdir()
    (run / 'config/neoforge-client.toml').write_text('showLoadWarnings=false\nenhancedLighting=true\n', encoding='utf-8')
    cp = [p for p in runtime['classpath'] if p not in (runtime['classes'], runtime['resources'])]
    jvm = ['-Xmx2G', '--enable-native-access=ALL-UNNAMED', '--add-opens=java.base/java.lang.invoke=ALL-UNNAMED',
        '-Dfile.encoding=UTF-8', '-Duser.language=en', '-Dbiblioteca.clientCheck.ui=' + str(args.ui_only).lower(),
        '-Dfml.modFolders=' + os.pathsep.join(['biblioteca%%' + str(args.jar.resolve()), 'biblioteca_client_check%%' + str(probe)])]
    game = ['--assetIndex', args.asset_index, '--assetsDir', args.assets_dir.resolve(), '--version', '1.21.11-ClientCheck',
        '--gameDir', run, '--username', 'ClientCheck', '--accessToken', '0', '--width', '960', '--height', '600']
    if not args.ui_only: game += ['--quickPlaySingleplayer', 'check-world']
    argfile = run / 'client.args'
    argfile.write_text('\n'.join(map(quote, jvm + ['--class-path', os.pathsep.join(cp), 'net.neoforged.fml.startup.Client'] + game)), encoding='utf-8')
    log = run / 'client.log'
    with log.open('w', encoding='utf-8') as stream:
        try:
            code = subprocess.run([args.java, '@' + str(argfile)], cwd=run, stdout=stream, stderr=subprocess.STDOUT, timeout=300).returncode
        except subprocess.TimeoutExpired: code = -1
    text = log.read_text(encoding='utf-8', errors='replace')
    frames = sorted(str(p.relative_to(ROOT)) for p in (run / 'screenshots').glob('*.png'))
    result = {'passed': code == 0 and 'CLIENT_CHECK_DONE passed=true' in text and 'CLIENT_CHECK_FAILURE' not in text
        and len(frames) == (2 if args.ui_only else 3), 'exitCode': code, 'mode': 'ui' if args.ui_only else 'world',
        'minecraft': runtime['minecraft'], 'neoforge': runtime['neoforge'], 'java': runtime['java'],
        'sha256': hashlib.sha256(args.jar.read_bytes()).hexdigest(), 'log': str(log.relative_to(ROOT)), 'screenshots': frames,
        'samples': [line for line in text.splitlines() if 'CLIENT_CHECK_' in line]}
    (output / 'results.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print('\n'.join(result['samples']))
    print(('CLIENT_CHECK_OK ' if result['passed'] else 'CLIENT_CHECK_FAILED ') + result['mode'] + ': ' + str(log), flush=True)
    raise SystemExit(0 if result['passed'] else 1)


if __name__ == '__main__':
    main()
