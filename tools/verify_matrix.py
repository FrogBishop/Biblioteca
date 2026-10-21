"""Verify version-specific Fabric injection targets using exported Loom runtime configurations.

First run Gradle writeValidationConfig for the selected nodes, then invoke this script.
No Minecraft window, world or audio/GPU device is started by the verifier.
"""
from pathlib import Path
import argparse
import concurrent.futures
import json
import os
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]

def quote(value):
    return '"' + str(value).replace('\\', '/').replace('"', '\\"') + '"'

def verify(config_file, side, java, checks=False):
    config = json.loads(config_file.read_text(encoding='utf-8'))
    target = ROOT / 'build/validation' / config['minecraft'] / side
    target.mkdir(parents=True, exist_ok=True)
    cp = config['serverClasspath' if side == 'server' else 'classpath']
    if checks:
        cp = cp + [str(ROOT / 'build/validation' / config['minecraft'] / 'checks')]
    args = ['-Djava.awt.headless=true', '--add-modules=jdk.zipfs', '--class-path', quote(os.pathsep.join(cp)),
            quote(ROOT / 'tools/MatrixMixinVerifier.java'), quote(config_file), side, quote(target / 'run')]
    if checks:
        args.append(quote(ROOT / 'build/validation' / config['minecraft'] / 'checks'))
    argfile = target / 'java.args'
    argfile.write_text('\n'.join(args) + '\n', encoding='utf-8')
    log = target / 'loader.log'
    with log.open('w', encoding='utf-8') as stream:
        result = subprocess.run([java, '@' + str(argfile)], cwd=target, stdout=stream, stderr=subprocess.STDOUT)
    text = log.read_text(encoding='utf-8', errors='replace')
    match = re.search(r'\bMATRIX_OK [^\r\n]+', text)
    marker = match.group(0) if match else None
    record = {'minecraft': config['minecraft'], 'environment': side,
              'passed': result.returncode == 0 and marker is not None, 'log': str(log.relative_to(ROOT)),
              'targets': sum(line.startswith('TARGET_OK ') for line in text.splitlines()),
              'dataChecks': checks and result.returncode == 0,
              'checks': re.findall(r'\b(?:DATA_OK |PASS: )[^\r\n]+', text)}
    print(marker if record['passed'] else f"FAILED {config['minecraft']} {side}: {record['log']}", flush=True)
    return record

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--versions', default='', help='Comma-separated nodes; default: every exported configuration')
    parser.add_argument('--sides', default='server,client')
    parser.add_argument('--java', default='java')
    parser.add_argument('--workers', type=int, default=2)
    parser.add_argument('--checks', action='store_true', help='Also compile/run regression checks using each node\'s actual classes')
    parser.add_argument('--javac', default='javac')
    args = parser.parse_args()
    selected = set(filter(None, args.versions.split(',')))
    configs = [p for p in sorted((ROOT / 'build/validation/runtime').glob('*.json')) if not selected or p.stem in selected]
    if not configs:
        raise SystemExit('No runtime configurations; run Gradle writeValidationConfig first.')
    if selected - {p.stem for p in configs}:
        raise SystemExit('Missing runtime configurations for ' + ', '.join(sorted(selected - {p.stem for p in configs})))
    if args.checks:
        for config_file in configs:
            config = json.loads(config_file.read_text(encoding='utf-8'))
            target = ROOT / 'build/validation' / config['minecraft'] / 'checks'
            target.mkdir(parents=True, exist_ok=True)
            sources = list((ROOT / 'tools/validation').glob('*.java'))
            if config['minecraft'] == '26.3':
                sources.append(ROOT / 'tools/validation/renderpearl/RenderingChecks.java')
            compile_args = ['--release', config['java'], '-cp', quote(os.pathsep.join(config['classpath'])), '-d', quote(target)]
            compile_args.extend(map(quote, sources))
            argfile = target.parent / 'checks-compile.args'
            argfile.write_text('\n'.join(compile_args) + '\n', encoding='utf-8')
            with (target.parent / 'checks-compile.log').open('w', encoding='utf-8') as log:
                result = subprocess.run([args.javac, '@' + str(argfile)], cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
            if result.returncode:
                raise SystemExit(f"Check compilation failed: {target.parent / 'checks-compile.log'}")
            (target / 'fabric.mod.json').write_text(json.dumps({'schemaVersion': 1, 'id': 'biblioteca-validation',
                'version': '1.0.0', 'name': 'Biblioteca matrix checks', 'environment': '*'}), encoding='utf-8')
    results = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = [pool.submit(verify, config, side, args.java, args.checks) for config in configs for side in args.sides.split(',')]
        for future in concurrent.futures.as_completed(futures):
            results.append(future.result())
    report = ROOT / 'build/validation/results.json'
    previous = json.loads(report.read_text(encoding='utf-8')) if report.exists() else []
    combined = {(item['minecraft'], item['environment']): item for item in previous}
    combined.update({(item['minecraft'], item['environment']): item for item in results})
    report.write_text(json.dumps(sorted(combined.values(), key=lambda item: (item['minecraft'], item['environment'])), indent=2) + '\n', encoding='utf-8')
    raise SystemExit(0 if all(item['passed'] for item in results) else 1)

if __name__ == '__main__':
    main()
