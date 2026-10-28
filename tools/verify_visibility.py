"""Run a controlled 26.3 graphical-client comparison on a copied world.

Requires exported loader runtime configuration and cached game assets. Produces actual
draw-list samples and six screenshots; it does not modify installed instances or saves.
"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import time
import zipfile

from verify_neoforge import ROOT, compile_java, quote


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--loader', required=True, choices=['fabric', 'neoforge'])
    parser.add_argument('--runtime-config', required=True, type=Path)
    parser.add_argument('--jar', required=True, type=Path)
    parser.add_argument('--world-template', required=True, type=Path)
    parser.add_argument('--assets-dir', type=Path)
    parser.add_argument('--asset-index')
    parser.add_argument('--java', default='java')
    parser.add_argument('--javac', default='javac')
    args = parser.parse_args()
    runtime = json.loads(args.runtime_config.read_text(encoding='utf-8'))
    if runtime['minecraft'] != '26.3':
        raise SystemExit('This diagnostic targets the 26.3 renderer.')
    if not (args.world_template / 'level.dat').is_file():
        raise SystemExit('World template must contain level.dat.')
    args.jar = args.jar.resolve()
    output = ROOT / 'build/validation/26.3/visibility' / args.loader
    output.mkdir(parents=True, exist_ok=True)
    sources = ROOT / 'tools/validation/visibility'
    adapter = 'FabricVisibilityProbe' if args.loader == 'fabric' else 'NeoForgeVisibilityProbe'
    compile_java(args.javac, runtime['classpath'], output / 'probe-classes',
                 [sources / name for name in ['VisibilityChecks.java', 'VisibilityObserver.java', adapter + '.java']],
                 output / 'probe-compile.log')
    probe = output / 'visibility-probe.jar'
    with zipfile.ZipFile(probe, 'w', zipfile.ZIP_DEFLATED) as z:
        for file in (output / 'probe-classes').rglob('*.class'):
            z.write(file, file.relative_to(output / 'probe-classes').as_posix())
        if args.loader == 'fabric':
            z.writestr('fabric.mod.json', json.dumps({'schemaVersion': 1, 'id': 'biblioteca_visibility_probe',
                'version': '1', 'environment': 'client', 'entrypoints': {'client': ['biblioteca.visibility.' + adapter]},
                'mixins': ['visibility.mixins.json']}))
        else:
            z.writestr('META-INF/neoforge.mods.toml', 'license="GPLv3"\n[[mods]]\nmodId="biblioteca_visibility_probe"\n'
                       'version="1"\ndisplayName="Visibility diagnostics"\n[[mixins]]\nconfig="visibility.mixins.json"\n')
        z.writestr('visibility.mixins.json', json.dumps({'required': True, 'package': 'biblioteca.visibility.mixin',
                    'compatibilityLevel': 'JAVA_25', 'client': ['VisibilityObserver'], 'injectors': {'defaultRequire': 1}}))
    run = output / ('run-' + time.strftime('%Y%m%d-%H%M%S'))
    (run / 'saves').mkdir(parents=True)
    shutil.copytree(args.world_template, run / 'saves/visibility-world')
    (run / 'options.txt').write_text('pauseOnLostFocus:false\nmaxFps:30\nfullscreen:false\nrenderDistance:12\n'
                                    'simulationDistance:12\nfov:0.0\nfovEffectScale:0.0\nlang:en_us\n', encoding='utf-8')
    (run / 'config').mkdir()
    (run / 'config/neoforge-client.toml').write_text('showLoadWarnings=false\nenhancedLighting=true\n', encoding='utf-8')
    cp = [p for p in runtime['classpath'] if p not in (runtime['classes'], runtime['resources'])]
    options = ['-Xmx2G', '-XX:StackShadowPages=32', '--sun-misc-unsafe-memory-access=allow',
               '--enable-native-access=ALL-UNNAMED', '--add-opens=java.base/java.lang.invoke=ALL-UNNAMED',
               '-Dfile.encoding=UTF-8', '-Duser.language=en', '-Duser.country=US']
    launch_args = []
    if args.loader == 'fabric':
        cp += [str(args.jar), str(probe)]
        launch = next(v.split('=', 1)[1] for v in runtime['clientJvmArgs'] if v.startswith('-Dfabric.dli.config='))
        group = ''
        for line in Path(launch).read_text(encoding='utf-8').splitlines():
            if not line.startswith('\t'):
                group = line.strip()
            elif group in ('commonProperties', 'clientProperties') and '=' in line:
                key, value = line.strip().split('=', 1)
                if key == 'log4j.configurationFile':
                    value = Path(value).resolve().as_uri()
                options.append('-D' + key + '=' + value)
            elif group in ('commonArgs', 'clientArgs'):
                launch_args.append(line.strip())
        main_class = 'net.fabricmc.loader.impl.launch.knot.KnotClient'
    else:
        if not args.assets_dir or not args.asset_index:
            raise SystemExit('NeoForge requires --assets-dir and --asset-index from its cached runtime.')
        options.append('-Dfml.modFolders=' + os.pathsep.join(['biblioteca%%' + str(args.jar),
                                                             'biblioteca_visibility_probe%%' + str(probe)]))
        main_class = 'net.neoforged.fml.startup.Client'
        launch_args = ['--assetIndex', args.asset_index, '--assetsDir', str(args.assets_dir.resolve())]
    launch_args += ['--version', '26.3-VisibilityProbe', '--gameDir', str(run), '--username', 'VisibilityProbe',
                   '--accessToken', '0', '--quickPlaySingleplayer', 'visibility-world', '--width', '960', '--height', '600']
    argfile = run / 'client.args'
    argfile.write_text('\n'.join(map(quote, options + ['--class-path', os.pathsep.join(cp), main_class] + launch_args)) + '\n',
                       encoding='utf-8')
    log = run / 'client.log'
    print('VISIBILITY_RUN ' + str(run), flush=True)
    with log.open('w', encoding='utf-8') as stream:
        try:
            code = subprocess.run([args.java, '@' + str(argfile)], cwd=run, stdout=stream,
                                  stderr=subprocess.STDOUT, timeout=360).returncode
        except subprocess.TimeoutExpired:
            code = -1
    text = log.read_text(encoding='utf-8', errors='replace')
    samples = [line for line in text.splitlines() if 'VISIBILITY_SAMPLE' in line]
    screenshots = sorted(str(file.relative_to(ROOT)) for file in (run / 'screenshots').glob('*.png'))
    passed = code == 0 and 'VISIBILITY_DONE failed=false cases=74' in text and len(screenshots) == 6
    report = {'jar': str(args.jar), 'sha256': hashlib.sha256(args.jar.read_bytes()).hexdigest(),
              'loader': args.loader, 'passed': passed, 'exitCode': code, 'log': str(log.relative_to(ROOT)),
              'samples': samples, 'screenshots': screenshots}
    for target in [run / 'result.json', output / 'results.json']:
        target.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(('VISIBILITY_OK ' if passed else 'VISIBILITY_FAILED ') + args.loader
          + ': ' + str(len(samples)) + ' poses, ' + str(len(screenshots)) + ' screenshots; ' + str(log), flush=True)
    raise SystemExit(0 if passed else 1)


if __name__ == '__main__':
    main()
