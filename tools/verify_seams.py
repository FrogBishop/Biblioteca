"""Run the real Fabric transformer, seam and dimension-settings regressions using Loom's runtime.

Run with JDK 25: ./gradlew verifySeams (requires Python 3).
Logs and results stay under build/seam-regression, separate from multiversion's reports.
"""
import argparse
import concurrent.futures
import json
import os
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'build/seam-regression'


def quote(value):
    return '"' + str(value).replace('\\', '/').replace('"', '\\"') + '"'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--config', type=Path, default=OUTPUT / 'runtime.json')
    parser.add_argument('--output', type=Path, default=OUTPUT)
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    config = json.loads(args.config.read_text(encoding='utf-8'))
    java_bin = Path(os.environ['JAVA_HOME']) / 'bin' if os.environ.get('JAVA_HOME') else Path('')
    java, javac = str(java_bin / 'java'), str(java_bin / 'javac')
    classes = output / 'checks'
    classes.mkdir(exist_ok=True)
    sources = [ROOT / 'tools/SeamMixinVerifier.java', *sorted((ROOT / 'tools/validation').glob('*.java'))]
    compile_args = ['-encoding', 'UTF-8', '--release', '25', '-cp', quote(os.pathsep.join(config['classpath'])), '-d', quote(classes)]
    compile_args += list(map(quote, sources))
    argfile = output / 'compile.args'
    argfile.write_text('\n'.join(compile_args), encoding='utf-8')
    with (output / 'compile.log').open('w', encoding='utf-8') as log:
        result = subprocess.run([javac, '@' + str(argfile)], cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
    if result.returncode:
        raise SystemExit((output / 'compile.log').read_text(encoding='utf-8', errors='replace'))
    # Fabric must load the checks with the same transforming class loader as the mod/game.
    (classes / 'fabric.mod.json').write_text(json.dumps({
        'schemaVersion': 1, 'id': 'biblioteca-seam-checks', 'version': '1.0.0',
        'name': 'Biblioteca seam checks', 'environment': '*',
    }), encoding='utf-8')

    def verify(side):
        target = output / side
        target.mkdir(exist_ok=True)
        cp = config['serverClasspath' if side == 'server' else 'classpath'] + [str(classes)]
        command = ['-Djava.awt.headless=true', '-Duser.language=en', '-Dfile.encoding=UTF-8',
                   '--add-modules=jdk.zipfs', '--class-path', quote(os.pathsep.join(cp)),
                   'SeamMixinVerifier', quote(args.config.resolve()), side, quote(target / 'run')]
        argfile = target / 'java.args'
        argfile.write_text('\n'.join(command), encoding='utf-8')
        log = target / 'checks.log'
        with log.open('w', encoding='utf-8') as stream:
            result = subprocess.run([java, '@' + str(argfile)], cwd=target, stdout=stream, stderr=subprocess.STDOUT, timeout=120)
        text = log.read_text(encoding='utf-8', errors='replace')
        passed = result.returncode == 0 and 'SEAM_OK ' + side in text
        print(('PASS ' if passed else 'FAIL ') + side + ': ' + str(log), flush=True)
        if not passed:
            print('\n'.join(text.splitlines()[-65:]), flush=True)
        return {'environment': side, 'passed': passed, 'log': str(log),
                'targets': len(re.findall(r'^TARGET_OK ', text, re.MULTILINE)),
                'checks': re.findall(r'PASS: [^\r\n]+', text)}

    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(verify, ['server', 'client']))
    (output / 'results.json').write_text(json.dumps(results, indent=2) + '\n', encoding='utf-8')
    raise SystemExit(0 if all(result['passed'] for result in results) else 1)


if __name__ == '__main__':
    main()
