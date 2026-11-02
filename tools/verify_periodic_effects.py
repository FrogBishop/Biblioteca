"""Verify living particles and native sculk sensors in an isolated 26.3 graphical client.

Requires exported loader runtime configuration and cached game assets. Produces actual
particle-identity samples, sensor checks and four screenshots; it does not modify installed instances or saves.
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
    parser.add_argument('--scenario', choices=['effects', 'visuals'], default='effects')
    parser.add_argument('--extra-mod', action='append', default=[], type=Path, help='Copy an additional mod into the isolated client')
    parser.add_argument('--label', help='Keep an extra-mod run separate from the native renderer baseline')
    args = parser.parse_args()
    runtime = json.loads(args.runtime_config.read_text(encoding='utf-8'))
    if runtime['minecraft'] != '26.3':
        raise SystemExit('This diagnostic targets the 26.3 renderer.')
    if not (args.world_template / 'level.dat').is_file():
        raise SystemExit('World template must contain level.dat.')
    args.jar = args.jar.resolve()
    output = ROOT / 'build/validation/26.3' / ('periodic-' + args.scenario) / args.loader
    if args.label:
        if not args.label.replace('-', '').replace('_', '').isalnum():
            parser.error('Label must contain only letters, digits, hyphens and underscores')
        output /= args.label
    output.mkdir(parents=True, exist_ok=True)
    sources = ROOT / 'tools/validation/periodic-effects'
    adapter = 'FabricPeriodicEffectsProbe' if args.loader == 'fabric' else 'NeoForgePeriodicEffectsProbe'
    if args.scenario == 'visuals':
        generated = output / 'generated'
        generated.mkdir(exist_ok=True)
        (generated / (adapter + '.java')).write_text((sources / (adapter + '.java')).read_text(encoding='utf-8')
            .replace('PeriodicEffectsChecks', 'PeriodicVisualChecks'), encoding='utf-8')
        probe_sources = [sources / name for name in ['PeriodicVisualChecks.java', 'VisualCloudObserver.java',
                         'VisualEntityObserver.java', 'VisualExtractionObserver.java', 'VisualWorldOpenObserver.java', 'VisualBlockObserver.java', 'VisualGuardianObserver.java']] + [generated / (adapter + '.java')]
        mixins = ['VisualCloudObserver', 'VisualEntityObserver', 'VisualExtractionObserver', 'VisualWorldOpenObserver', 'VisualBlockObserver', 'VisualGuardianObserver']
        if any('sodium' in mod.name.lower() for mod in args.extra_mod):
            probe_sources.append(sources / 'VisualSodiumTerrainObserver.java')
            mixins.append('VisualSodiumTerrainObserver')
    else:
        probe_sources = [sources / name for name in ['PeriodicEffectsChecks.java', 'PeriodicParticleObserver.java', adapter + '.java']]
        mixins = ['PeriodicParticleObserver']
    compile_classpath = list(runtime['classpath'])
    if args.loader == 'neoforge':
        # FML loads this jar-in-jar at runtime; javac needs the exact same bundled API explicitly.
        dependencies = output / 'compile-dependencies'
        dependencies.mkdir(exist_ok=True)
        for file in runtime['classpath']:
            if Path(file).is_file() and str(file).endswith('.jar'):
                with zipfile.ZipFile(file) as z:
                    for entry in z.namelist():
                        if entry.startswith('META-INF/jarjar/') and 'mixinextras' in entry.lower() and entry.endswith('.jar'):
                            dependency = dependencies / Path(entry).name
                            dependency.write_bytes(z.read(entry))
                            compile_classpath.append(str(dependency))
    compile_java(args.javac, compile_classpath, output / 'probe-classes', probe_sources, output / 'probe-compile.log')
    probe = output / 'periodic-effects-probe.jar'
    with zipfile.ZipFile(probe, 'w', zipfile.ZIP_DEFLATED) as z:
        for file in (output / 'probe-classes').rglob('*.class'):
            z.write(file, file.relative_to(output / 'probe-classes').as_posix())
        if args.loader == 'fabric':
            z.writestr('fabric.mod.json', json.dumps({'schemaVersion': 1, 'id': 'biblioteca_periodic_probe',
                'version': '1', 'environment': 'client', 'entrypoints': {'client': ['biblioteca.periodicprobe.' + adapter]},
                'mixins': ['periodic-effects.mixins.json']}))
        else:
            z.writestr('META-INF/neoforge.mods.toml', 'license="GPLv3"\n[[mods]]\nmodId="biblioteca_periodic_probe"\n'
                       'version="1"\ndisplayName="Periodic effects diagnostics"\n[[mixins]]\nconfig="periodic-effects.mixins.json"\n')
        z.writestr('periodic-effects.mixins.json', json.dumps({'required': True, 'package': 'biblioteca.periodicprobe.mixin',
                    'compatibilityLevel': 'JAVA_25', 'client': mixins, 'injectors': {'defaultRequire': 1}}))
    run = output / ('run-' + time.strftime('%Y%m%d-%H%M%S'))
    (run / 'saves').mkdir(parents=True)
    extra_mods = []
    for mod in args.extra_mod:
        (run / 'mods').mkdir(exist_ok=True)
        frozen_mod = run / 'mods' / mod.name
        shutil.copy2(mod, frozen_mod)
        extra_mods.append({'file': str(frozen_mod), 'sha256': hashlib.sha256(frozen_mod.read_bytes()).hexdigest()})
    source_jar = args.jar
    args.jar = run / source_jar.name
    shutil.copy2(source_jar, args.jar)
    shutil.copytree(args.world_template, run / 'saves/effects-world')
    if args.scenario == 'visuals':
        # Derive the fixture from the exact cached native schema, including attributes and clocks.
        dimension_type = None
        for file in runtime['classpath']:
            if Path(file).is_file() and 'minecraft' in Path(file).name:
                with zipfile.ZipFile(file) as z:
                    if 'data/minecraft/dimension_type/overworld.json' in z.namelist():
                        dimension_type = json.loads(z.read('data/minecraft/dimension_type/overworld.json'))
                        pack_version = json.loads(z.read('version.json'))['pack_version']
                        break
        if dimension_type is None:
            raise SystemExit('Cached native dimension schema missing')
        dimension_type.update(height=16, logical_height=16, min_y=0)
        dimension_type['attributes']['minecraft:visual/cloud_height'] = 8.0
        pack = run / 'saves/effects-world/datapacks/biblioteca-visuals'
        (pack / 'data/biblioteca/dimension_type').mkdir(parents=True)
        (pack / 'data/biblioteca/dimension').mkdir()
        version = [pack_version['data_major'], pack_version['data_minor']]
        (pack / 'pack.mcmeta').write_text(json.dumps({'pack': {'description': 'Isolated periodic visual regression',
                                      'min_format': version, 'max_format': version}}), encoding='utf-8')
        (pack / 'data/biblioteca/dimension_type/visuals.json').write_text(json.dumps(dimension_type), encoding='utf-8')
        (pack / 'data/biblioteca/dimension/visuals.json').write_text(json.dumps({'type': 'biblioteca:visuals',
            'generator': {'type': 'minecraft:flat', 'settings': {'biome': 'minecraft:plains', 'lakes': False,
             'features': False, 'structure_overrides': [], 'layers': [{'block': 'minecraft:air', 'height': 1}]}}}), encoding='utf-8')
    (run / 'options.txt').write_text('pauseOnLostFocus:false\nmaxFps:30\nfullscreen:false\nrenderDistance:8\n'
                                    'simulationDistance:8\nfov:0.0\nfovEffectScale:0.0\nlang:en_us\n', encoding='utf-8')
    (run / 'config').mkdir()
    (run / 'config/neoforge-client.toml').write_text('showLoadWarnings=false\nenhancedLighting=true\n', encoding='utf-8')
    cp = [p for p in runtime['classpath'] if p not in (runtime['classes'], runtime['resources'])]
    options = ['-Xmx2G', '-XX:StackShadowPages=32', '--sun-misc-unsafe-memory-access=allow',
               '--enable-native-access=ALL-UNNAMED', '--add-opens=java.base/java.lang.invoke=ALL-UNNAMED',
               '-Dfile.encoding=UTF-8', '-Duser.language=en', '-Duser.country=US']
    if args.scenario == 'visuals' and any('sodium' in mod.name.lower() for mod in args.extra_mod):
        options.append('-Dbiblioteca.visuals.sodiumTerrain=true')
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
                                                             'biblioteca_periodic_probe%%' + str(probe)]))
        main_class = 'net.neoforged.fml.startup.Client'
        launch_args = ['--assetIndex', args.asset_index, '--assetsDir', str(args.assets_dir.resolve())]
    launch_args += ['--version', '26.3-PeriodicProbe', '--gameDir', str(run), '--username', 'PeriodicProbe',
                   '--accessToken', '0', '--quickPlaySingleplayer', 'effects-world', '--width', '960', '--height', '600']
    argfile = run / 'client.args'
    argfile.write_text('\n'.join(map(quote, options + ['--class-path', os.pathsep.join(cp), main_class] + launch_args)) + '\n',
                       encoding='utf-8')
    log = run / 'client.log'
    print('PERIODIC_RUN ' + str(run), flush=True)
    with log.open('w', encoding='utf-8') as stream:
        try:
            code = subprocess.run([args.java, '@' + str(argfile)], cwd=run, stdout=stream,
                                  stderr=subprocess.STDOUT, timeout=360).returncode
        except subprocess.TimeoutExpired:
            code = -1
    text = log.read_text(encoding='utf-8', errors='replace')
    samples = [line for line in text.splitlines() if 'PERIODIC_' in line or 'VISUAL_' in line]
    screenshots = sorted(str(file.relative_to(ROOT)) for file in (run / 'screenshots').glob('*.png'))
    passed = code == 0 and 'PERIODIC_DONE failed=false' in text and 'PERIODIC_FAILURE' not in text and len(screenshots) == (9 if args.scenario == 'visuals' else 4)
    report = {'jar': str(args.jar), 'sourceJar': str(source_jar), 'sha256': hashlib.sha256(args.jar.read_bytes()).hexdigest(),
              'extraMods': extra_mods,
              'loader': args.loader, 'scenario': args.scenario, 'passed': passed, 'exitCode': code, 'log': str(log.relative_to(ROOT)),
              'samples': samples, 'screenshots': screenshots}
    for target in [run / 'result.json', output / 'results.json']:
        target.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(('PERIODIC_OK ' if passed else 'PERIODIC_FAILED ') + args.loader
          + ': ' + str(len(samples)) + ' samples, ' + str(len(screenshots)) + ' screenshots; ' + str(log), flush=True)
    raise SystemExit(0 if passed else 1)


if __name__ == '__main__':
    main()
