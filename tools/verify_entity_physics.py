"""Run actual server movement/falling-block regression cases in a disposable world."""
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
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--version', required=True)
    p.add_argument('--label', default='physics')
    p.add_argument('--jar', type=Path)
    p.add_argument('--short', action='store_true')
    p.add_argument('--eula-file', type=Path, default=ROOT / 'run/eula.txt')
    p.add_argument('--java', default='java')
    p.add_argument('--javac', default='javac')
    a = p.parse_args()
    cfg = json.loads((ROOT / 'build/validation/runtime' / f'{a.version}.json').read_text(encoding='utf-8'))
    if not a.eula_file.exists() or 'eula=true' not in a.eula_file.read_text(encoding='utf-8').replace(' ', '').lower():
        raise SystemExit('An existing accepted EULA is required')
    mod_version = tomllib.loads((ROOT / 'stonecutter.properties.toml').read_text(encoding='utf-8'))['mod']['version']
    jar = (a.jar or ROOT / 'build/distributions' / f'biblioteca-{mod_version}+{a.version}.jar').resolve()
    out = ROOT / 'build/validation' / a.version / 'entity-physics' / a.label / time.strftime('run-%Y%m%d-%H%M%S')
    out.mkdir(parents=True)
    (out / 'mods').mkdir()
    frozen = out / 'mods' / jar.name
    shutil.copy2(jar, frozen)
    classes = out / 'probe-classes'
    compile_java(a.javac, cfg['classpath'], classes, [ROOT / 'tools/validation/entity-physics/EntityPhysicsProbe.java'], out / 'compile.log')
    (classes / 'fabric.mod.json').write_text(json.dumps({'schemaVersion': 1, 'id': 'biblioteca_physics_probe', 'version': '1.0.0', 'environment': 'server', 'entrypoints': {'main': ['biblioteca.physicsprobe.EntityPhysicsProbe']}}), encoding='utf-8')
    probe = out / 'probe.jar'
    with zipfile.ZipFile(probe, 'w') as z:
        for file in classes.rglob('*'):
            if file.is_file():
                z.write(file, file.relative_to(classes).as_posix())
    shutil.copy2(a.eula_file, out / 'eula.txt')
    props = 'server-port=0\nonline-mode=false\nview-distance=2\nsimulation-distance=2\nspawn-chunk-radius=0\nmax-tick-time=120000\nlevel-type=minecraft:flat\ngenerate-structures=false\nspawn-monsters=false\nspawn-animals=false\n'
    (out / 'server.properties').write_text(props, encoding='utf-8')
    if a.short:
        jar_mc = next((x for x in cfg['classpath'] if 'minecraft-merged' in x and x.endswith('.jar')))
        with zipfile.ZipFile(jar_mc) as z:
            dim = json.loads(z.read('data/minecraft/dimension_type/overworld.json'))
            version = json.loads(z.read('version.json'))['pack_version']
        dim.update(min_y=0, height=16, logical_height=16)
        pack = out / 'world/datapacks/physics'
        (pack / 'data/minecraft/dimension_type').mkdir(parents=True)
        meta = {'description': 'Disposable height-16 physics world'}
        if 'data' in version:
            meta['pack_format'] = version['data']
        else:
            meta.update(min_format=[version['data_major'], version['data_minor']], max_format=[version['data_major'], version['data_minor']])
        (pack / 'pack.mcmeta').write_text(json.dumps({'pack': meta}), encoding='utf-8')
        (pack / 'data/minecraft/dimension_type/overworld.json').write_text(json.dumps(dim), encoding='utf-8')
    cp = [s for s in cfg['serverClasspath'] if s not in (cfg['classes'], cfg['resources'])] + [str(probe)]
    opts = ['-Xmx1G', '-Xss4M', '--enable-native-access=ALL-UNNAMED', '--add-opens=java.base/java.lang.invoke=ALL-UNNAMED', '-Djava.awt.headless=true', '-Dfile.encoding=UTF-8', '-Duser.language=en', '-Duser.country=US']
    launch = next((v.split('=', 1)[1] for v in cfg['serverJvmArgs'] if v.startswith('-Dfabric.dli.config=')))
    group = ''
    for line in Path(launch).read_text(encoding='utf-8').splitlines():
        if not line.startswith('\t'):
            group = line.strip()
        elif group in ('commonProperties', 'serverProperties') and '=' in line:
            key, val = line.strip().split('=', 1)
            if key == 'log4j.configurationFile':
                val = Path(val).resolve().as_uri()
            opts.append('-D' + key + '=' + val)
    arg = out / 'server.args'
    arg.write_text('\n'.join(map(quote, opts + ['--class-path', os.pathsep.join(cp), 'net.fabricmc.loader.impl.launch.knot.KnotServer', '--nogui'])), encoding='utf-8')
    print('PHYSICS_RUN', out, flush=True)
    with (out / 'server.log').open('w', encoding='utf-8') as log:
        try:
            code = subprocess.run([a.java, '@' + str(arg)], cwd=out, stdout=log, stderr=subprocess.STDOUT, timeout=200).returncode
        except subprocess.TimeoutExpired:
            code = -1
    text = (out / 'server.log').read_text(errors='replace', encoding='utf-8')
    passed = code == 0 and 'PHYSICS_OK ' in text and ('PHYSICS_FAILED' not in text)
    result = {'minecraft': a.version, 'passed': passed, 'exitCode': code, 'artifactSha256': hashlib.sha256(frozen.read_bytes()).hexdigest(), 'short': a.short, 'cases': [s for s in text.splitlines() if 'PHYSICS_' in s]}
    (out / 'result.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
    print('\n'.join(result['cases']), flush=True)
    raise SystemExit(0 if passed else 1)
if __name__ == '__main__':
    main()
