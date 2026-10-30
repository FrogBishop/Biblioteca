"""Save/stop/reopen an isolated periodic/hybrid world using the actual selected loader.

Requires an existing accepted EULA file; copies it into the isolated server directory.
The supplied template is read-only. Test worlds, probe classes and logs stay under build/.
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

from verify_neoforge import ROOT, PROJECT, compile_java, quote


def run_server(args, runtime, run_dir, probe_jar, stage):
    cp = [p for p in runtime.get('serverClasspath', runtime['classpath']) if p not in (runtime['classes'], runtime['resources'])]
    options = ['-Xmx1G', '--enable-native-access=ALL-UNNAMED',
               '--add-opens=java.base/java.lang.invoke=ALL-UNNAMED', '-Djava.awt.headless=true',
               '-Dfile.encoding=UTF-8', '-Duser.language=en', '-Duser.country=US',
               '-Dbiblioteca.saveTest.stage=' + stage,
               '-Dbiblioteca.saveTest.newWorld=' + str(args.new_world).lower(),
               '-Dbiblioteca.saveTest.hybridDig=' + str(args.hybrid_dig).lower()]
    if int(runtime.get('java', 25)) >= 24:
        options.append('--sun-misc-unsafe-memory-access=allow')
    if args.loader == 'fabric':
        cp += [str(args.jar), str(probe_jar)]
        launch = next(v.split('=', 1)[1] for v in runtime['serverJvmArgs'] if v.startswith('-Dfabric.dli.config='))
        group = ''
        for line in Path(launch).read_text(encoding='utf-8').splitlines():
            if not line.startswith('\t'):
                group = line.strip()
            elif group in ('commonProperties', 'serverProperties') and '=' in line:
                key, value = line.strip().split('=', 1)
                # Java argument-file quoting normalizes Windows separators. Log4j can mistake
                # drive-letter paths for URL schemes, so give it an explicit file URI.
                if key == 'log4j.configurationFile':
                    value = Path(value).resolve().as_uri()
                options.append('-D' + key + '=' + value)
        main_class = 'net.fabricmc.loader.impl.launch.knot.KnotServer'
    else:
        folders = os.pathsep.join(['biblioteca%%' + str(args.jar), 'biblioteca_save_check%%' + str(probe_jar)])
        options.append('-Dfml.modFolders=' + folders)
        main_class = 'net.neoforged.fml.startup.Server'
    argfile = run_dir / (stage + '.args')
    argfile.write_text('\n'.join(map(quote, options)) + '\n--class-path\n' + quote(os.pathsep.join(cp))
                       + '\n' + main_class + '\n--nogui\n', encoding='utf-8')
    log = run_dir / (stage + '.log')
    stopped = False
    started = time.monotonic()
    deadline = started + 180
    with log.open('w', encoding='utf-8') as output:
        proc = subprocess.Popen([args.java, '@' + str(argfile)], cwd=run_dir, stdin=subprocess.PIPE,
                                stdout=output, stderr=subprocess.STDOUT, text=True)
        while proc.poll() is None:
            text = log.read_text(encoding='utf-8', errors='replace')
            if 'SAVE_TEST_FAILURE' in text or 'HYBRID_DIG_FAILURE' in text:
                proc.terminate()
                break
            if not stopped and 'SAVE_TEST_READY' in text:
                proc.stdin.write('stop\n')
                proc.stdin.flush()
                stopped = True
                deadline = time.monotonic() + 60
            if time.monotonic() > deadline:
                proc.terminate()
                break
            time.sleep(0.25)
        try:
            code = proc.wait(timeout=10)
        except subprocess.TimeoutExpired:
            proc.kill()
            code = proc.wait(timeout=10)
        proc.stdin.close()
    text = log.read_text(encoding='utf-8', errors='replace')
    passed = code == 0 and 'SAVE_TEST_OK stage=' + stage in text and 'SAVE_TEST_FAILURE' not in text and 'HYBRID_DIG_FAILURE' not in text
    if args.hybrid_dig:
        passed = passed and ('HYBRID_DIG_OK reloaded' in text if stage == 'read' else text.count('HYBRID_DIG_OK base=') == 3)
    record = {'stage': stage, 'passed': passed, 'exitCode': code, 'seconds': round(time.monotonic() - started, 2),
              'log': str(log.relative_to(ROOT)), 'heapLimit': '1G'}
    print(('SAVE_RELOAD_OK ' if passed else 'SAVE_RELOAD_FAILED ') + stage + ': ' + str(log), flush=True)
    return record


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--loader', choices=['neoforge', 'fabric'], default='neoforge')
    world = parser.add_mutually_exclusive_group(required=True)
    world.add_argument('--world-template', type=Path)
    world.add_argument('--new-world', action='store_true', help='Create a fresh flat world and persist all four mod attributes')
    parser.add_argument('--eula-file', type=Path, default=ROOT / 'run/eula.txt')
    parser.add_argument('--runtime-config', type=Path, default=PROJECT / 'build/validation/runtime.json')
    parser.add_argument('--label', default='save-reload')
    parser.add_argument('--hybrid-dig', action='store_true', help='Dig and re-roof 8x8 shafts through native block updates before saving')
    parser.add_argument('--java', default='java')
    parser.add_argument('--javac', default='javac')
    args = parser.parse_args()
    args.jar = args.jar.resolve()
    if args.world_template:
        args.world_template = args.world_template.resolve()
    if not args.eula_file.is_file() or 'eula=true' not in args.eula_file.read_text().lower().replace(' ', ''):
        raise SystemExit('An existing accepted EULA file is required via --eula-file.')
    if args.world_template and not (args.world_template / 'level.dat').is_file():
        raise SystemExit('World template must contain level.dat.')
    if not args.label.replace('-', '').replace('_', '').isalnum():
        raise SystemExit('Label must contain only letters, digits, hyphens and underscores.')
    runtime = json.loads(args.runtime_config.read_text(encoding='utf-8'))
    digest = hashlib.sha256(args.jar.read_bytes()).hexdigest()
    default_output = ROOT / 'build/validation' / runtime['minecraft'] / 'save-reload' if args.loader == 'fabric' else ROOT / 'build/neoforge-pilot/save-hang'
    output = Path(runtime.get('saveReports', default_output)) / args.label
    output.mkdir(parents=True, exist_ok=True)
    sources = sorted((ROOT / 'tools/validation/server-lifecycle').glob('*.java'))
    if args.loader == 'fabric':
        sources += sorted((ROOT / 'tools/fabric-save-validation').glob('*.java'))
        sources.append(ROOT / 'tools/neoforge-save-validation/SaveLifecycleMixin.java')
    else:
        sources += sorted((ROOT / 'tools/neoforge-save-validation').glob('*.java'))
    compile_java(args.javac, runtime['classpath'], output / 'probe-classes', sources, output / 'probe-compile.log', runtime.get('java', 25))
    probe_jar = output / 'save-probe.jar'
    with zipfile.ZipFile(probe_jar, 'w', zipfile.ZIP_DEFLATED) as z:
        for file in (output / 'probe-classes').rglob('*.class'):
            z.write(file, file.relative_to(output / 'probe-classes').as_posix())
        if args.loader == 'fabric':
            z.writestr('fabric.mod.json', json.dumps({'schemaVersion': 1, 'id': 'biblioteca_save_check', 'version': '1',
                        'environment': 'server', 'entrypoints': {'main': ['babel.biblioteca.validation.save.fabric.FabricSaveProbe']},
                        'mixins': ['biblioteca.save-check.mixins.json']}))
        else:
            z.writestr('META-INF/neoforge.mods.toml', 'license="GPLv3"\n[[mods]]\nmodId="biblioteca_save_check"\nversion="1"\ndisplayName="Biblioteca save regression probe"\n[[mixins]]\nconfig="biblioteca.save-check.mixins.json"\n')
        z.writestr('biblioteca.save-check.mixins.json', json.dumps({'required': True, 'package': 'babel.biblioteca.validation.save.mixin',
                    'compatibilityLevel': 'JAVA_' + str(runtime.get('java', 25)), 'mixins': ['SaveLifecycleMixin'], 'injectors': {'defaultRequire': 1}}))
    run_dir = output / ('run-' + time.strftime('%Y%m%d-%H%M%S'))
    run_dir.mkdir()
    if args.world_template:
        shutil.copytree(args.world_template, run_dir / 'world')
    shutil.copy2(args.eula_file, run_dir / 'eula.txt')
    (run_dir / 'server.properties').write_text('server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-name=world\n'
            'view-distance=4\nsimulation-distance=4\nspawn-protection=0\npause-when-empty-seconds=0\nmax-tick-time=60000\n'
            'level-type=minecraft:flat\ngenerate-structures=false\ngamemode=creative\n', encoding='utf-8')
    report = {'jar': str(args.jar), 'sha256': digest, 'loader': args.loader, 'minecraft': runtime['minecraft'], 'neoforge': runtime.get('neoforge'), 'results': []}
    for stage in ['write', 'read']:
        record = run_server(args, runtime, run_dir, probe_jar, stage)
        report['results'].append(record)
        (output / 'results.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
        if not record['passed']:
            raise SystemExit(1)


if __name__ == '__main__':
    main()
