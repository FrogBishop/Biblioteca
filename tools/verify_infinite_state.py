"""Exercise snapshot book dimensions, commands, packet/NBT codecs and a real server restart."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import time
import tomllib
import zipfile

from verify_neoforge import ROOT, compile_java, quote


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--eula-file', type=Path, required=True)
    parser.add_argument('--variant', choices=['full', 'norender'], default='full')
    parser.add_argument('--label', default='state')
    args = parser.parse_args()
    if 'eula=true' not in args.eula_file.read_text(encoding='utf-8').replace(' ', '').lower():
        raise SystemExit('An existing accepted EULA is required')
    cfg = json.loads((ROOT / 'build/validation/runtime/20w14infinite.json').read_text(encoding='utf-8'))
    version = tomllib.loads((ROOT / 'stonecutter.properties.toml').read_text(encoding='utf-8'))['mod']['version']
    suffix = '-norender' if args.variant == 'norender' else ''
    artifact = ROOT / 'build/distributions' / f'biblioteca-{version}+20w14infinite{suffix}.jar'
    run = ROOT / 'build/validation/20w14infinite/state' / args.label / time.strftime('run-%Y%m%d-%H%M%S')
    (run / 'mods').mkdir(parents=True)
    frozen = run / 'mods' / artifact.name
    shutil.copy2(artifact, frozen)
    api = ROOT / '.gradle/fabric-api/0.5.7+build.2-20w14infinite'
    lifecycle = next(api.glob('fabric-events-lifecycle-v0-*.jar'))
    shutil.copy2(lifecycle, run / 'mods' / lifecycle.name)
    classes = run / 'probe-classes'
    compile_java('javac', cfg['classpath'] + [str(lifecycle)], classes,
                 [ROOT / 'tools/validation/infinite/InfiniteStateProbe.java'], run / 'compile.log', release=17)
    (classes / 'fabric.mod.json').write_text(json.dumps({
        'schemaVersion': 1, 'id': 'biblioteca_infinite_state_probe', 'version': '1.0.0',
        'environment': 'server', 'entrypoints': {'main': ['biblioteca.infiniteprobe.InfiniteStateProbe']}
    }), encoding='utf-8')
    probe = run / 'probe.jar'
    with zipfile.ZipFile(probe, 'w') as jar:
        for file in classes.rglob('*'):
            if file.is_file():
                jar.write(file, file.relative_to(classes).as_posix())
    shutil.copy2(args.eula_file, run / 'eula.txt')
    (run / 'server.properties').write_text(
        'server-port=0\nonline-mode=false\nview-distance=2\nmax-tick-time=120000\n'
        'level-type=flat\ngenerate-structures=false\nspawn-monsters=false\nspawn-animals=false\n', encoding='utf-8')
    cp = [p for p in cfg['serverClasspath'] if p not in (cfg['classes'], cfg['resources'])] + [str(probe)]
    opts = ['-Xmx2G', '-Xss4M', '--enable-native-access=ALL-UNNAMED',
            '--add-opens=java.base/java.lang.invoke=ALL-UNNAMED', '-Djava.awt.headless=true', '-Dfile.encoding=UTF-8']
    launch = next(v.split('=', 1)[1] for v in cfg['serverJvmArgs'] if v.startswith('-Dfabric.dli.config='))
    group = ''
    for line in Path(launch).read_text(encoding='utf-8').splitlines():
        if not line.startswith('\t'):
            group = line.strip()
        elif group in ('commonProperties', 'serverProperties') and '=' in line:
            key, value = line.strip().split('=', 1)
            if key == 'log4j.configurationFile':
                value = Path(value).resolve().as_uri()
            opts.append('-D' + key + '=' + value)
    phases = []
    print('INFINITE_STATE_RUN', run, flush=True)
    for phase in ('save', 'reload'):
        arguments = opts + ['-Dbiblioteca.infinite.reload=' + str(phase == 'reload').lower(),
                            '--class-path', os.pathsep.join(cp), 'net.fabricmc.loader.impl.launch.knot.KnotServer', '--nogui']
        argfile = run / (phase + '.args')
        argfile.write_text('\n'.join(map(quote, arguments)), encoding='utf-8')
        log_path = run / (phase + '.log')
        with log_path.open('w', encoding='utf-8') as log:
            try:
                code = subprocess.run(['java', '@' + str(argfile)], cwd=run, stdout=log, stderr=subprocess.STDOUT, timeout=180).returncode
            except subprocess.TimeoutExpired:
                code = -1
        text = log_path.read_text(encoding='utf-8', errors='replace')
        passed = code == 0 and 'INFINITE_STATE_OK phase=' + phase in text and 'INFINITE_STATE_FAILED' not in text
        phases.append({'phase': phase, 'passed': passed, 'exitCode': code,
                       'cases': [line for line in text.splitlines() if 'INFINITE_STATE_' in line]})
        print('\n'.join(phases[-1]['cases']), flush=True)
        if not passed:
            break
    result = {'passed': len(phases) == 2 and all(p['passed'] for p in phases), 'variant': args.variant,
              'artifactSha256': hashlib.sha256(frozen.read_bytes()).hexdigest(), 'phases': phases}
    (run / 'result.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
    raise SystemExit(0 if result['passed'] else 1)


if __name__ == '__main__':
    main()
