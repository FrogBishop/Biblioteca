"""Build selected NeoForge projects in isolated Gradle projects and preserve per-version outcomes."""
from pathlib import Path
import argparse
import concurrent.futures
import json
import os
import re
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--versions', required=True)
    parser.add_argument('--tasks', default='build')
    parser.add_argument('--workers', type=int, default=1)
    parser.add_argument('--label', default=time.strftime('matrix-%Y%m%d-%H%M%S'))
    parser.add_argument('--offline', action='store_true')
    parser.add_argument('--property', action='append', default=[], help='Supported build override: neo_version or bibliotecaVersion')
    args = parser.parse_args()
    versions = list(dict.fromkeys(args.versions.split(',')))
    if not all(re.fullmatch(r'\d+(?:\.\d+)+', value) and (ROOT / f'platforms/neoforge-{value}/build.gradle').is_file() for value in versions):
        raise SystemExit('Expected existing exact-version NeoForge projects')
    if not re.fullmatch(r'[A-Za-z0-9_-]+', args.label):
        raise SystemExit('Invalid report label')
    tasks = args.tasks.split(',')
    for value in args.property:
        key, separator, setting = value.partition('=')
        if not separator or key not in ('neo_version', 'bibliotecaVersion') or not re.fullmatch(r'[0-9A-Za-z][0-9A-Za-z._+-]*', setting):
            raise SystemExit('Invalid build property override')
    if not all(re.fullmatch(r'[A-Za-z][A-Za-z0-9]*', task) for task in tasks):
        raise SystemExit('Expected plain Gradle task names')
    out = ROOT / 'build/neoforge-backport' / args.label
    out.mkdir(parents=True, exist_ok=True)
    toolchains = [str(path.parent.parent) for path in (ROOT / '.gradle/toolchains').glob('*/bin/java.exe')]
    toolchains += [os.environ[key] for key in ['JAVA_HOME_17_X64', 'JAVA_HOME_21_X64', 'JAVA_HOME_25_X64'] if key in os.environ]
    results = []
    (out / 'results.json').write_text('[]\n', encoding='utf-8')

    def run(version):
        log = out / (version + '.log')
        env = dict(os.environ)
        properties = (ROOT / f'platforms/neoforge-{version}/gradle.properties').read_text(encoding='utf-8')
        gradle8 = re.search(r'(?m)^gradle_line=8\s*$', properties) is not None
        wrapper = ROOT / ('platforms/legacy-gradle' if gradle8 else '') / ('gradlew.bat' if os.name == 'nt' else 'gradlew')
        cached_gradle = ROOT / '.gradle/toolchains/gradle-8.14.5/bin' / ('gradle.bat' if os.name == 'nt' else 'gradle')
        if gradle8 and cached_gradle.is_file():
            wrapper = cached_gradle
        if gradle8 and env.get('JAVA_HOME_21_X64'):
            env['JAVA_HOME'] = env['JAVA_HOME_21_X64']
        elif gradle8 and os.name == 'nt':
            homes = sorted(Path(os.environ.get('ProgramFiles', 'C:/Program Files'), 'Java').glob('jdk-21*'))
            if homes:
                env['JAVA_HOME'] = str(homes[-1])
        # Keep optional toolchains' native-library caches inside the project as well.
        native = ROOT / '.gradle' / ('native-neoforge-' + version)
        temporary = ROOT / '.gradle' / ('tmp-neoforge-' + version)
        temporary.mkdir(parents=True, exist_ok=True)
        env['JAVA_TOOL_OPTIONS'] = (env.get('JAVA_TOOL_OPTIONS', '') + ' -Dorg.gradle.native.dir="' + native.as_posix() + '"').strip()
        env['JAVA_TOOL_OPTIONS'] += ' -Djava.io.tmpdir="' + temporary.as_posix() + '"'
        if gradle8 and os.name == 'nt':
            env['JAVA_TOOL_OPTIONS'] += ' -Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE'
        command = ([str(wrapper)] if os.name == 'nt' else ['bash', str(wrapper)]) + ['-p', str(ROOT / ('platforms/neoforge-' + version)),
                   *tasks, '--console=plain', '--max-workers=1', '--no-daemon']
        if toolchains:
            command.append('-Porg.gradle.java.installations.paths=' + ','.join(toolchains))
        if args.offline:
            command.append('--offline')
        command.extend('-P' + value for value in args.property)
        print('NEOFORGE_BUILD_START', version, ','.join(tasks), flush=True)
        started = time.monotonic()
        with log.open('w', encoding='utf-8') as stream:
            code = subprocess.run(command, cwd=ROOT, env=env, stdout=stream, stderr=subprocess.STDOUT).returncode
        result = {'minecraft': version, 'tasks': tasks, 'passed': code == 0, 'exitCode': code,
                  'seconds': round(time.monotonic() - started, 2), 'log': str(log.relative_to(ROOT))}
        print('NEOFORGE_BUILD_' + ('OK' if code == 0 else 'FAILED'), version, result['seconds'], flush=True)
        return result

    with concurrent.futures.ThreadPoolExecutor(max_workers=max(1, min(args.workers, 2))) as pool:
        for result in pool.map(run, versions):
            results.append(result)
            (out / 'results.json').write_text(json.dumps(results, indent=2) + '\n', encoding='utf-8')
    raise SystemExit(0 if all(result['passed'] for result in results) else 1)


if __name__ == '__main__':
    main()
