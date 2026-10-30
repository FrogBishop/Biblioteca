"""Prepare, validate and publish exact-version JAR pairs through Mod Publish Plugin.

Defaults to a real plugin dry run. Credentials live outside the checkout and are
passed only to the publishing process. The project page is synchronized separately
so a description-only change cannot create duplicate file releases.
"""
from contextlib import contextmanager
from datetime import datetime, timezone
from email.utils import parsedate_to_datetime
from pathlib import Path
import argparse
import hashlib
import http.client
import json
import math
import os
import queue
import re
import shutil
import subprocess
import sys
import tempfile
import threading
import time
import tomllib
import urllib.error
import urllib.request
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / 'build/publishing'
CREDENTIALS = ROOT.parent / 'biblioteca-publish.local.json'
USER_AGENT = 'FrogBishop/Biblioteca-publisher (https://github.com/FrogBishop/Biblioteca)'
PLATFORMS = ('modrinth', 'curseforge')
LOADERS = ('fabric', 'neoforge')
TOKEN_KEYS = ('MODRINTH_TOKEN', 'CURSEFORGE_TOKEN')
PROJECT_KEYS = ('MODRINTH_PROJECT_ID', 'CURSEFORGE_PROJECT_ID', 'MODRINTH_NORENDER_PROJECT_ID')
DEFAULT_RETRY = {'attempts': 4, 'delay': 5.0, 'maxDelay': 60.0, 'interval': 2.0}
_run_log = None


class PublishError(Exception):
    pass


def progress(message, stream=None, secrets=()):
    for secret in secrets:
        if secret:
            message = message.replace(secret, '[REDACTED]')
    line = message.rstrip() + '\n'
    if message.strip() and not re.match(r'^\[\d{2}:\d{2}:\d{2}\]', message):
        line = f'[{datetime.now().astimezone():%H:%M:%S}] ' + line
    print(line, end='', flush=True)
    for output in (stream, _run_log):
        if output is not None:
            output.write(line)
            output.flush()


@contextmanager
def session_log(run):
    global _run_log
    previous = _run_log
    with (run / 'publish.log').open('w', encoding='utf-8') as stream:
        _run_log = stream
        try:
            yield
        finally:
            _run_log = previous


def retry_delay(attempt, headers, policy):
    delay = min(policy['maxDelay'], policy['delay'] * 2 ** (attempt - 1))
    value = headers.get('Retry-After') if headers else None
    if value:
        try:
            requested = float(value)
        except ValueError:
            try:
                requested = parsedate_to_datetime(value).timestamp() - time.time()
            except (ValueError, TypeError, OverflowError):
                return delay
        if not math.isfinite(requested):
            return delay
        if requested > policy['maxDelay']:
            return None
        delay = max(delay, requested, 0)
    return delay


def wait_retry(seconds, label):
    while seconds > 0:
        chunk = min(seconds, 10)
        time.sleep(chunk)
        seconds -= chunk
        if seconds > 0:
            progress(f'WAIT {label}: retry in {seconds:g}s')


def http_request(request, label, retry=None):
    """Retry only reads and replacement-style description PATCHes, never upload POSTs."""
    assert request.get_method() in ('GET', 'PATCH')
    policy = retry or DEFAULT_RETRY
    for attempt in range(1, policy['attempts'] + 1):
        progress(f'REQUEST {label} attempt {attempt}/{policy["attempts"]}')
        try:
            with urllib.request.urlopen(request, timeout=45) as response:
                return response.read()
        except (urllib.error.URLError, OSError, http.client.HTTPException) as error:
            headers = getattr(error, 'headers', None) or {}
            code = error.code if isinstance(error, urllib.error.HTTPError) else None
            challenge = False
            if isinstance(error, urllib.error.HTTPError):
                body = error.read(65536)
                challenge = code == 403 and (headers.get('cf-mitigated') == 'challenge'
                                             or b'<title>Just a moment...</title>' in body)
                error.close()
            transient = code is None or code in (408, 429, 500, 502, 503, 504) or challenge
            detail = f'HTTP {code}' if code else 'network error'
            delay = retry_delay(attempt, headers, policy) if transient else None
            if transient and attempt < policy['attempts'] and delay is not None:
                progress(f'RETRY {label}: {detail}; waiting {delay:g}s')
                wait_retry(delay, label)
                continue
            suffix = ' Retry-After exceeds the wait limit; resume later.' if transient and delay is None else ''
            raise PublishError(f'{label} failed ({detail}).{suffix}') from None


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load_state():
    path = OUTPUT / 'state.json'
    state = json.loads(path.read_text(encoding='utf-8')) if path.exists() else {'schemaVersion': 1, 'uploads': {}}
    if state.get('schemaVersion') != 1 or not isinstance(state.get('uploads'), dict):
        raise PublishError('Invalid publishing state; preserve it and inspect before publishing.')
    return state


def reconcile_receipts():
    """Recover an upload whose worker succeeded before the process was interrupted."""
    state = load_state()
    changed = False
    for key, record in state['uploads'].items():
        if record.get('status') == 'published':
            continue
        platform, project, _ = key.split(':', 2)

        def receipt(field):
            if not record.get(field):
                return None
            path = Path(record[field]).resolve()
            if not path.is_relative_to((OUTPUT / 'runs').resolve()) or not path.is_file():
                return None
            value = json.loads(path.read_text(encoding='utf-8'))
            return value if value.get('type') == platform and value.get('projectId') == project else None

        if platform == 'curseforge':
            for step in ('primary', 'additional'):
                result = receipt(step + 'ResultFile')
                valid = (result and result.get('step') == step and result.get('fileId')
                         and result.get('fingerprint') == record.get('fingerprint'))
                if step == 'additional':
                    valid = valid and result.get('parentFileId') == record.get('primaryFileId')
                if valid and not record.get(step + 'FileId'):
                    record[step + 'FileId'] = result['fileId']
                    if step == 'primary':
                        record.update(status='partial', pendingStep='additional')
                    changed = True
            if record.get('primaryFileId') and record.get('additionalFileId'):
                record.update(status='published', result={'type': platform, 'projectId': project,
                              'fileId': record['primaryFileId'], 'title': 'Download from CurseForge'})
                record.pop('pendingStep', None)
                changed = True
                continue
        result = receipt('resultFile')
        if result and result.get('id' if platform == 'modrinth' else 'fileId'):
            record.update(status='published', result=result)
            changed = True
    if changed:
        write_json(OUTPUT / 'state.json', state)


def mark_not_uploaded(key):
    state = load_state()
    record = state['uploads'].get(key)
    if not record or record.get('status') != 'inflight':
        raise PublishError('Only an unconfirmed inflight upload can be marked not uploaded.')
    record['status'] = 'partial' if record.get('primaryFileId') else 'not-uploaded'
    record['outcome'] = 'user-confirmed-absent'
    write_json(OUTPUT / 'state.json', state)


def record_upload(key, remote_id):
    state = load_state()
    record = state['uploads'].get(key)
    if not record or record.get('status') not in ('inflight', 'partial', 'failed'):
        raise PublishError('Only an unfinished upload can be reconciled manually.')
    platform, project, _ = key.split(':', 2)
    pattern = r'[A-Za-z0-9]{8}' if platform == 'modrinth' else r'[1-9][0-9]*'
    if not re.fullmatch(pattern, remote_id):
        raise PublishError('Invalid remote version/file ID.')
    result = {'type': platform, 'projectId': project}
    result['id' if platform == 'modrinth' else 'fileId'] = remote_id if platform == 'modrinth' else int(remote_id)
    record.update(status='published', result=result, manuallyConfirmed=True)
    write_json(OUTPUT / 'state.json', state)


def record_file(key, remote_id, step):
    """Confirm just one CurseForge file after the user inspects the author dashboard."""
    state = load_state()
    record = state['uploads'].get(key)
    if not key.startswith('curseforge:') or not record or record.get('status') == 'published':
        raise PublishError('File-level reconciliation needs an unfinished CurseForge publication.')
    if not re.fullmatch(r'[1-9][0-9]*', remote_id):
        raise PublishError('Invalid CurseForge file ID.')
    if step == 'additional' and not record.get('primaryFileId'):
        raise PublishError('Confirm the primary file ID first with --record-primary.')
    if step == 'primary' and record.get('primaryFileId') and record.get('pendingStep') == 'additional':
        raise PublishError('The primary ID is already saved; confirm the additional file or mark that uncertain file absent.')
    field = step + 'FileId'
    if record.get(field) not in (None, int(remote_id)):
        raise PublishError('The stored file ID differs; inspect the existing receipt.')
    record[field] = int(remote_id)
    record['manuallyConfirmed'] = True
    if step == 'primary':
        record.update(status='partial', pendingStep='additional')
    else:
        record.update(status='published', result={'type': 'curseforge', 'projectId': key.split(':', 2)[1],
                      'fileId': record['primaryFileId']})
        record.pop('pendingStep', None)
    write_json(OUTPUT / 'state.json', state)


def status_report(manifest=None, run=None):
    uploads = load_state()['uploads']
    keys = ([f'{e["platform"]}:{e["projectId"]}:{e["version"]}' for e in manifest['publications']]
            if manifest else list(uploads))
    counts = {}
    pending = []
    for key in keys:
        record = uploads.get(key, {})
        status = record.get('status', 'not-started')
        counts[status] = counts.get(status, 0) + 1
        if status != 'published':
            item = {'key': key, 'status': status, 'step': record.get('pendingStep'),
                    'primaryFileId': record.get('primaryFileId'), 'error': record.get('lastError')}
            pending.append(item)
            progress('PENDING ' + json.dumps(item, ensure_ascii=False))
            if status == 'inflight' and record.get('pendingStep') == 'metadata':
                progress('  A read-only metadata request was interrupted; rerun the same command safely.')
            elif status == 'inflight':
                if record.get('primaryFileId'):
                    progress(f'CHECK https://www.curseforge.com/minecraft/mc-mods/biblioteca/files/{record["primaryFileId"]}')
                    progress(f'  If the additional file exists: --record-additional "{key}" ADDITIONAL_FILE_ID')
                    progress(f'  If it is absent: --mark-not-uploaded "{key}" (keeps the primary ID)')
                elif key.startswith('curseforge:'):
                    progress(f'  If only the primary file exists: --record-primary "{key}" PRIMARY_FILE_ID')
                    progress(f'  If neither file exists: --mark-not-uploaded "{key}"')
                else:
                    progress('  Rerun to check Modrinth version numbers and hashes before any further upload.')
    result = {'total': len(keys), 'counts': counts, 'pending': pending, 'complete': not pending}
    progress('SUMMARY ' + json.dumps(counts))
    if run:
        write_json(run / 'upload-summary.json', result)
    return result


def reconcile_modrinth(manifest, credentials, retry=None):
    """Check remote version numbers and hashes before creating any Modrinth version."""
    if 'modrinth' not in manifest['platforms']:
        return
    state = load_state()
    versions_by_project = {}
    for entry in manifest['publications']:
        if entry['platform'] != 'modrinth':
            continue
        project = entry['projectId']
        if project not in versions_by_project:
            request = urllib.request.Request('https://api.modrinth.com/v2/project/' + project + '/version',
                      headers={'Authorization': credentials['MODRINTH_TOKEN'], 'User-Agent': USER_AGENT})
            try:
                versions_by_project[project] = json.loads(http_request(request, 'Modrinth versions ' + project, retry))
            except (PublishError, ValueError):
                raise PublishError('Cannot check existing Modrinth versions; no new uploads will be started.') from None
        versions = versions_by_project[project]
        matches = [v for v in versions if v.get('version_number') == entry['version']]
        if not matches:
            continue
        if len(matches) != 1:
            raise PublishError('Multiple Modrinth releases already use ' + entry['version'])
        remote = matches[0]
        remote_files = {f['filename']: f for f in remote.get('files', [])}
        expected = entry['files']
        equal = (remote.get('game_versions') == [entry['minecraft']] and remote.get('loaders') == [entry['loader']]
                 and set(remote_files) == {f['name'] for f in expected}
                 and all(remote_files.get(f['name'], {}).get('hashes', {}).get('sha512') == f['sha512'] for f in expected)
                 and remote_files.get(expected[0]['name'], {}).get('primary') is True)
        if not equal:
            raise PublishError('Existing Modrinth release differs from this plan: ' + entry['version'])
        key = 'modrinth:' + project + ':' + entry['version']
        previous = state['uploads'].get(key)
        if previous and previous['fingerprint'] != entry['fingerprint']:
            raise PublishError('Release metadata changed for an existing version: ' + entry['version'])
        state['uploads'][key] = {'status': 'published', 'fingerprint': entry['fingerprint'],
            'result': {'type': 'modrinth', 'projectId': project, 'id': remote['id']}, 'verifiedRemotely': True}
    write_json(OUTPUT / 'state.json', state)


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + '.tmp')
    temporary.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    temporary.replace(path)


def csv(value, allowed, name):
    values = list(allowed) if value == 'all' else list(dict.fromkeys(value.split(',')))
    if not values or any(v not in allowed for v in values):
        raise PublishError(f'Invalid {name}; choose from: {", ".join(allowed)}')
    return values


def validate_project_ids(values):
    for platform, pattern in [('modrinth', r'[A-Za-z0-9]{8}'), ('curseforge', r'[1-9][0-9]*'),
                              ('modrinth_norender', r'[A-Za-z0-9]{8}')]:
        value = values.get(platform.upper() + '_PROJECT_ID', '')
        if value and not re.fullmatch(pattern, value):
            raise PublishError(f'Invalid {platform} project ID (use the stable project ID, not a token or URL).')
    if values.get('MODRINTH_NORENDER_PROJECT_ID') and values['MODRINTH_NORENDER_PROJECT_ID'] == values.get('MODRINTH_PROJECT_ID'):
        raise PublishError('Modrinth full and no-render variants must use different projects.')


def read_credentials(path, root=ROOT, environ=None):
    environ = os.environ if environ is None else environ
    path = path.expanduser().resolve()
    if path.is_relative_to(root.resolve()):
        raise PublishError('The credentials file must be outside the repository, including symlink targets.')
    data = {}
    if path.exists():
        try:
            data = json.loads(path.read_text(encoding='utf-8-sig'))
        except (ValueError, OSError):
            raise PublishError('Cannot read the external credentials JSON; values were not logged.') from None
    allowed = set(PROJECT_KEYS + TOKEN_KEYS)
    if not isinstance(data, dict) or set(data) - allowed or any(not isinstance(v, str) for v in data.values()):
        raise PublishError('Credentials must contain only the string fields in publishing/credentials.example.json.')
    result = {key: environ.get(key, data.get(key, '')).strip() for key in allowed}
    validate_project_ids(result)
    for key in TOKEN_KEYS:
        if result[key] and (not result[key].isascii() or any(c.isspace() for c in result[key])):
            raise PublishError(f'{key} contains whitespace or non-ASCII characters; paste only the token value.')
    return result


def child_environment(credentials=None):
    env = os.environ.copy()
    for key in TOKEN_KEYS:
        env.pop(key, None)
    if credentials:
        env.update({key: credentials[key] for key in TOKEN_KEYS if credentials.get(key)})
    env.update(PYTHONUNBUFFERED='1', PYTHONIOENCODING='utf-8')
    return env


def stop_child(process):
    """Stop only this invocation's process group; never leave an uploader running."""
    if process.poll() is not None:
        return
    if os.name == 'nt':
        import signal
        try:
            process.send_signal(signal.CTRL_BREAK_EVENT)
        except OSError:
            pass
    else:
        import signal
        os.killpg(process.pid, signal.SIGTERM)
    try:
        process.wait(timeout=10)
    except subprocess.TimeoutExpired:
        if os.name == 'nt':
            subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'],
                           stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=15)
        else:
            os.killpg(process.pid, signal.SIGKILL)
        process.wait(timeout=10)


def run_command(command, log, env=None, secrets=(), heartbeat=15):
    log.parent.mkdir(parents=True, exist_ok=True)
    with log.open('w', encoding='utf-8') as stream:
        group = {'creationflags': subprocess.CREATE_NEW_PROCESS_GROUP} if os.name == 'nt' else {'start_new_session': True}
        process = subprocess.Popen(command, cwd=ROOT, env=env or child_environment(), stdout=subprocess.PIPE,
                                   stderr=subprocess.STDOUT, text=True, encoding='utf-8', errors='replace', **group)
        events = queue.Queue()

        def read_output():
            try:
                for line in process.stdout:
                    events.put(line)
            except (OSError, ValueError):
                pass  # The controlling thread may close the pipe after cancellation.
            finally:
                events.put(None)

        reader = threading.Thread(target=read_output, daemon=True)
        reader.start()
        started = time.monotonic()
        try:
            while True:
                try:
                    line = events.get(timeout=heartbeat)
                except queue.Empty:
                    progress(f'WAIT {log.name}: process running, {int(time.monotonic() - started)}s elapsed', stream)
                    continue
                if line is None:
                    break
                progress(line, stream, secrets)
            code = process.wait()
        except BaseException:
            progress('STOPPING child process; confirmed upload receipts will be kept.', stream)
            stop_child(process)
            raise
        finally:
            reader.join(timeout=2)
            process.stdout.close()
        if code:
            raise PublishError(f'Command failed ({code}); see {log.relative_to(ROOT)}')


@contextmanager
def publication_lock():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    with (OUTPUT / 'publish.lock').open('a+b') as handle:
        handle.seek(0, 2)
        if handle.tell() == 0:
            handle.write(b'0')
            handle.flush()
        handle.seek(0)
        try:
            if os.name == 'nt':
                import msvcrt
                msvcrt.locking(handle.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError:
            raise PublishError('Another publisher is running in this checkout.') from None
        try:
            yield
        finally:
            handle.seek(0)
            if os.name == 'nt':
                msvcrt.locking(handle.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                fcntl.flock(handle.fileno(), fcntl.LOCK_UN)


def targets(config, versions, loaders):
    fabric = [v for v in config if re.fullmatch(r'\d+(?:\.\d+)+', v)]
    neo = {p.name.removeprefix('neoforge-') for p in (ROOT / 'platforms').glob('neoforge-*')
           if p.is_dir() and (p / 'build.gradle').is_file()}
    selected = csv(versions, fabric, 'Minecraft versions')
    pairs = [(loader, mc) for mc in selected for loader in loaders if loader == 'fabric' or mc in neo]
    if not pairs:
        raise PublishError('No configured targets match the requested versions and loaders.')
    for mc in selected:
        if versions != 'all' and not any(v == mc for _, v in pairs):
            raise PublishError(f'No selected loader target exists for Minecraft {mc}.')
    return pairs


def artifact_paths(mod_version, loader, mc):
    version = f'{mod_version}+{mc}' + ('-neoforge' if loader == 'neoforge' else '')
    return version, [ROOT / 'build/distributions' / f'biblioteca-{version}{suffix}.jar' for suffix in ('', '-norender')]


def inspect_artifact(path, version, loader, mc, variant):
    expected = version + ('-norender' if variant == 'norender' else '')
    if not path.is_file():
        raise PublishError(f'Missing {path.name}; use --build or select versions with current artifacts.')
    try:
        with zipfile.ZipFile(path) as jar:
            if loader == 'fabric':
                meta = json.loads(jar.read('fabric.mod.json'))
                valid = (meta['id'] == 'biblioteca' and meta['version'] == expected
                         and meta['depends']['minecraft'] == mc
                         and ('fabric-api' in meta['depends'] or 'fabric' in meta['depends'])
                         and 'META-INF/neoforge.mods.toml' not in jar.namelist())
            else:
                meta = tomllib.loads(jar.read('META-INF/neoforge.mods.toml').decode('utf-8'))
                valid = (meta['mods'][0]['modId'] == 'biblioteca' and meta['mods'][0]['version'] == expected
                         and any(d['modId'] == 'minecraft' and d['versionRange'] == f'[{mc}]'
                                 for d in meta['dependencies']['biblioteca'])
                         and 'fabric.mod.json' not in jar.namelist())
            if not valid:
                raise PublishError(f'Wrong mod, version, loader or Minecraft requirement in {path.name}.')
            bytecode = [int.from_bytes(jar.read(n)[6:8], 'big') - 44 for n in jar.namelist()
                        if n.startswith('babel/') and n.endswith('.class')]
            if not bytecode:
                raise PublishError(f'No mod classes in {path.name}.')
            return {'name': path.name, 'sha256': sha256(path), 'sha512': hashlib.sha512(path.read_bytes()).hexdigest(),
                    'java': max(bytecode), 'variant': variant}
    except (ValueError, KeyError, IndexError, zipfile.BadZipFile):
        raise PublishError(f'Invalid artifact metadata: {path.name}') from None


def release_notes(mod_version, path):
    text = path.read_text(encoding='utf-8')
    if path.resolve() == (ROOT / 'CHANGELOG.md').resolve():
        match = re.search(r'^##[ \t]+' + re.escape(mod_version) + r'(?:[ \t][^\n]*)?\n(.*?)(?=^##\s|\Z)', text, re.M | re.S)
        if not match:
            raise PublishError(f'No changelog section for {mod_version}; provide --changelog FILE.')
        text = match.group(1).strip()
    if not text.strip():
        raise PublishError('Release changelog is empty.')
    return re.sub(r'\]\((?![a-z]+:|#)([^)]+)\)',
                  r'](https://github.com/FrogBishop/Biblioteca/blob/multiversion/\1)', text)


def build_targets(pairs, run):
    wrapper = str(ROOT / ('gradlew.bat' if os.name == 'nt' else 'gradlew'))
    common = ['--console=plain', '--max-workers=1', '--no-daemon', '--no-configuration-cache']
    fabric = [mc for loader, mc in pairs if loader == 'fabric']
    if fabric:
        env = child_environment()
        env['SC_VERSIONS'] = ','.join(fabric)
        tasks = [f':{mc}:{task}' for mc in fabric for task in ('build', 'writeValidationConfig')]
        run_command([wrapper, *tasks, *common], run / 'fabric-build.log', env)
        run_command([sys.executable, 'tools/verify_matrix.py', '--versions', ','.join(fabric), '--checks', '--workers', '2'],
                    run / 'fabric-checks.log')
        run_command([sys.executable, 'tools/verify_artifacts.py', '--versions', ','.join(fabric)], run / 'fabric-artifacts.log')
    for loader, mc in pairs:
        if loader == 'neoforge':
            run_command([wrapper, '-p', f'platforms/neoforge-{mc}', 'build', *common], run / f'neoforge-{mc}-build.log')


def publication_fingerprint(entry, release_type, notes):
    identity = {'version': entry['version'], 'loader': entry['loader'], 'minecraft': entry['minecraft'],
                'releaseType': release_type, 'changelog': notes,
                'files': [{k: a[k] for k in ('name', 'sha256')} for a in entry['files']]}
    return hashlib.sha256(json.dumps(identity, sort_keys=True).encode()).hexdigest()


def make_publications(entries, platforms, projects, release_type, notes):
    publications = []
    for entry in entries:
        for platform in platforms:
            variants = ['full', 'norender'] if platform == 'modrinth' and projects.get('modrinthNorender') else ['full']
            for variant in variants:
                item = dict(entry, platform=platform, variant=variant, projectId=projects[platform])
                if platform == 'modrinth':
                    item['key'] += '_' + variant
                    item['files'] = [entry['files'][0 if variant == 'full' else 1]]
                    if variant == 'norender':
                        item['projectId'] = projects['modrinthNorender']
                        item['version'] += '-norender'
                        item['displayName'] += ' | No Render'
                item['fingerprint'] = publication_fingerprint(item, release_type, notes)
                publications.append(item)
    return publications


def prepare_descriptions(run, credentials, platforms):
    description = (ROOT / 'modrinth.md').read_bytes()
    (run / 'project-description.md').write_bytes(description)
    (run / 'curseforge-description.md').write_bytes((ROOT / 'curseforge.md').read_bytes())
    if 'modrinth' in platforms and credentials.get('MODRINTH_NORENDER_PROJECT_ID'):
        (run / 'modrinth-norender-description.md').write_bytes((ROOT / 'modrinth-norender.md').read_bytes())
    return hashlib.sha256(description).hexdigest()


def prepare_manifest(args, pairs, credentials, run):
    notes = release_notes(args.mod_version, args.changelog)
    files_dir = run / 'files'
    files_dir.mkdir()
    entries = []
    for loader, mc in pairs:
        version, paths = artifact_paths(args.mod_version, loader, mc)
        assets = [inspect_artifact(path, version, loader, mc, variant)
                  for path, variant in zip(paths, ('full', 'norender'))]
        for asset, source in zip(assets, paths):
            target = files_dir / source.name
            shutil.copyfile(source, target)
            if sha256(target) != asset['sha256']:
                raise PublishError('Artifact changed while preparing the release snapshot.')
            asset['path'] = str(target)
        loader_name = 'Fabric' if loader == 'fabric' else 'NeoForge'
        entries.append({'key': loader + '_' + mc.replace('.', '_'), 'version': version, 'minecraft': mc,
                        'loader': loader, 'displayName': f'Biblioteca {args.mod_version} | Minecraft {mc} | {loader_name}',
                        'java': max(a['java'] for a in assets), 'files': assets})
    projects = {p: credentials[p.upper() + '_PROJECT_ID'] or ('dryrun00' if p == 'modrinth' else '0') for p in args.platforms}
    projects['modrinthNorender'] = credentials.get('MODRINTH_NORENDER_PROJECT_ID', '')
    if projects['modrinthNorender'] and projects['modrinthNorender'] == projects.get('modrinth'):
        raise PublishError('Modrinth no-render must use a separate project.')
    publications = make_publications(entries, args.platforms, projects, args.release_type, notes)
    description_hash = prepare_descriptions(run, credentials, args.platforms)
    (run / 'changelog.md').write_text(notes + '\n', encoding='utf-8')
    manifest = {'schemaVersion': 2, 'dryRun': not args.publish, 'runDirectory': str(run),
                'modVersion': args.mod_version, 'releaseType': args.release_type, 'changelog': notes,
                'platforms': args.platforms, 'projects': projects,
                'descriptionSha256': description_hash, 'entries': entries, 'publications': publications,
                'modrinthNoRender': 'separate-project' if projects['modrinthNorender'] else 'not-configured',
                'validation': 'build-and-regression-checks' if args.build else 'artifact-metadata-and-hashes',
                'retry': {'attempts': args.attempts, 'delay': args.retry_delay,
                          'maxDelay': args.max_retry_delay, 'interval': args.upload_interval}}
    write_json(run / 'manifest.json', manifest)
    return manifest


def sync_description(credentials, run, live, platforms, retry=None):
    status = {'modrinth': 'not-selected', 'curseforge': 'not-selected'}
    if 'curseforge' in platforms:
        status['curseforge'] = 'manual: copy curseforge-description.md into the author dashboard'
    if 'modrinth' in platforms:
        pages = [('modrinth', credentials.get('MODRINTH_PROJECT_ID', ''), 'project-description.md')]
        if credentials.get('MODRINTH_NORENDER_PROJECT_ID'):
            pages.append(('modrinth-norender', credentials['MODRINTH_NORENDER_PROJECT_ID'], 'modrinth-norender-description.md'))
    else:
        pages = []
    for name, project, source in pages:
        payload = {'body': (run / source).read_bytes().decode('utf-8')}
        write_json(run / (name + '-description-request.json'), payload)
        status[name] = 'preview'
        if live:
            request = urllib.request.Request('https://api.modrinth.com/v2/project/' + project,
                data=json.dumps(payload, ensure_ascii=False).encode('utf-8'), method='PATCH',
                headers={'Authorization': credentials['MODRINTH_TOKEN'], 'Content-Type': 'application/json', 'User-Agent': USER_AGENT})
            try:
                http_request(request, name + ' description', retry)
            except PublishError as exc:
                status[name] = 'failed'
                write_json(run / 'description-result.json', status)
                raise PublishError(f'{exc} File upload receipts are preserved; retry --description-only.') from None
            status[name] = 'updated'
            progress('DESCRIPTION_UPDATED ' + name)
    write_json(run / 'description-result.json', status)
    return status


def parser():
    p = argparse.ArgumentParser(description=__doc__)
    mode = p.add_mutually_exclusive_group()
    mode.add_argument('--publish', action='store_true', help='Actually upload files and update the Modrinth project page')
    mode.add_argument('--dry-run', action='store_true', help='Preview using the real plugin (the default)')
    p.add_argument('--versions', default='all', help='Comma-separated exact Minecraft versions, or all')
    p.add_argument('--loaders', default='all', help='fabric, neoforge, or all')
    p.add_argument('--platforms', default='all', help='modrinth, curseforge, or all')
    p.add_argument('--mod-version', help='Defaults to the current version in stonecutter.properties.toml')
    p.add_argument('--credentials-file', type=Path, default=CREDENTIALS, help='JSON file outside the repository; environment values take precedence')
    p.add_argument('--modrinth-norender-project-id', help='Optional separate Modrinth project for no-render releases (public ID)')
    p.add_argument('--changelog', type=Path, default=ROOT / 'CHANGELOG.md')
    p.add_argument('--release-type', choices=['release', 'beta', 'alpha'], default='release')
    p.add_argument('--build', action='store_true', help='Build selected targets and run their existing regressions before publishing')
    p.add_argument('--plan-only', action='store_true', help='Prepare snapshots and metadata without starting Gradle')
    p.add_argument('--description-only', action='store_true', help='Update the project page without uploading JARs')
    p.add_argument('--attempts', type=int, default=4, help='Maximum attempts for safely retryable requests (default: 4)')
    p.add_argument('--retry-delay', type=float, default=5, help='Initial retry delay in seconds; doubles after each failure (default: 5)')
    p.add_argument('--max-retry-delay', type=float, default=60, help='Maximum automatic wait in seconds; longer Retry-After stops for a later resume (default: 60)')
    p.add_argument('--upload-interval', type=float, default=2, help='Pause between uploads in seconds (default: 2)')
    p.add_argument('--status', action='store_true', help='Show local upload progress and recovery commands without reading tokens or contacting platforms')
    recovery = p.add_mutually_exclusive_group()
    recovery.add_argument('--mark-not-uploaded', metavar='PLATFORM:PROJECT:VERSION',
                   help='After checking the website, mark an unconfirmed upload as absent so a later run may retry it')
    recovery.add_argument('--record-upload', nargs=2, metavar=('PLATFORM:PROJECT:VERSION', 'REMOTE_ID'),
                          help='After verifying both files on the website, record an unconfirmed upload as successful')
    recovery.add_argument('--record-primary', nargs=2, metavar=('CURSEFORGE:PROJECT:VERSION', 'FILE_ID'),
                          help='Confirm an existing primary file; the next normal run uploads only its missing additional file')
    recovery.add_argument('--record-additional', nargs=2, metavar=('CURSEFORGE:PROJECT:VERSION', 'FILE_ID'),
                          help='Confirm an additional file whose upload response was lost')
    return p


def run_release(args, credentials, run):
    progress('PUBLISH_RUN ' + str(run.relative_to(ROOT)))
    progress('LOG ' + str(run / 'publish.log'))
    policy = {'attempts': args.attempts, 'delay': args.retry_delay,
              'maxDelay': args.max_retry_delay, 'interval': args.upload_interval}
    if args.description_only:
        prepare_descriptions(run, credentials, args.platforms)
        status = sync_description(credentials, run, args.publish, args.platforms, policy)
        progress('DESCRIPTION ' + json.dumps(status))
        return
    pairs = targets(tomllib.loads((ROOT / 'stonecutter.properties.toml').read_text(encoding='utf-8')), args.versions, args.loaders)
    if args.build:
        progress('BUILD selected artifacts and regression checks')
        build_targets(pairs, run)
    manifest = prepare_manifest(args, pairs, credentials, run)
    progress(f'PLAN {len(manifest["entries"])} artifact pairs, {len(manifest["publications"])} platform releases, platforms={",".join(args.platforms)}')
    if 'modrinth' in args.platforms and not credentials.get('MODRINTH_NORENDER_PROJECT_ID'):
        progress('NOTE: Modrinth no-render is not selected; configure a separate MODRINTH_NORENDER_PROJECT_ID to publish it.')
    if args.publish:
        reconcile_receipts()
        reconcile_modrinth(manifest, credentials, policy)
    failure = None
    if not args.plan_only:
        wrapper = str(ROOT / ('gradlew.bat' if os.name == 'nt' else 'gradlew'))
        command = [wrapper, '-p', 'publishing', 'publishMods', '-PreleaseManifest=' + str(run / 'manifest.json'),
                   '-PpublishLive=' + str(args.publish).lower(), '--console=plain', '--max-workers=1', '--continue',
                   '--no-daemon', '--no-configuration-cache', '--no-build-cache']
        # Plugin tokens are task inputs. Keep even the task history outside the checkout.
        with tempfile.TemporaryDirectory(prefix='biblioteca-publish-') as cache:
            if (not Path(cache).resolve().is_relative_to(Path(tempfile.gettempdir()).resolve())
                    or Path(cache).resolve().is_relative_to(ROOT.resolve())):
                raise PublishError('Unexpected temporary cache location.')
            command += ['--project-cache-dir', cache]
            selected = {key: credentials[key] for key in TOKEN_KEYS if key.removesuffix('_TOKEN').lower() in args.platforms}
            try:
                run_command(command, run / 'plugin.log', child_environment(selected if args.publish else None),
                            [credentials[k] for k in TOKEN_KEYS])
            except PublishError as error:
                failure = str(error)
    summary = None
    if args.publish:
        selected_keys = {f'{e["platform"]}:{e["projectId"]}:{e["version"]}' for e in manifest['publications']}
        before_recovery = {k: v.get('status') for k, v in load_state()['uploads'].items() if k in selected_keys}
        reconcile_receipts()
        # A timed-out Modrinth POST may already exist. Verify filenames and hashes;
        # never turn an absent read into permission to resend an uncertain POST.
        keys = {f'modrinth:{e["projectId"]}:{e["version"]}' for e in manifest['publications'] if e['platform'] == 'modrinth'}
        for attempt in range(1, min(policy['attempts'], 3) + 1):
            if not any(load_state()['uploads'].get(k, {}).get('status') == 'inflight' for k in keys):
                break
            try:
                reconcile_modrinth(manifest, credentials, policy)
            except PublishError as error:
                progress(str(error))
                break
            if attempt < min(policy['attempts'], 3):
                if any(load_state()['uploads'].get(k, {}).get('status') == 'inflight' for k in keys):
                    wait_retry(min(policy['delay'], policy['maxDelay']), 'Modrinth receipt check')
        summary = status_report(manifest, run)
        recovered = any(before_recovery.get(k) == 'inflight' and v.get('status') == 'published'
                        for k, v in load_state()['uploads'].items())
        if summary['complete']:
            if failure and recovered:
                progress('RECOVERED: every upload is confirmed by a saved receipt or matching remote version.')
                failure = None
        else:
            failure = failure or 'Some uploads remain incomplete.'
    status = None
    try:
        # A failed destination must not prevent a completed Modrinth release's page update.
        platforms = args.platforms
        if summary and any(p['key'].startswith('modrinth:') for p in summary['pending']):
            platforms = [p for p in platforms if p != 'modrinth']
            progress('DESCRIPTION_DEFERRED: finish the pending Modrinth versions first.')
        status = sync_description(credentials, run, args.publish, platforms, policy)
    except PublishError as error:
        progress(str(error))
        failure = failure or str(error)
    write_json(run / 'result.json', {'passed': failure is None, 'dryRun': not args.publish,
                'releases': len(manifest['entries']), 'platformReleases': len(manifest['publications']),
                'modrinthNoRender': manifest['modrinthNoRender'], 'description': status, 'uploads': summary,
                'error': failure, 'manifest': str((run / 'manifest.json').relative_to(ROOT))})
    if failure:
        raise PublishError(f'Publishing incomplete; see {run.relative_to(ROOT)}/upload-summary.json and --status. {failure}')
    progress('DESCRIPTION ' + json.dumps(status))
    progress(('PUBLISH_OK ' if args.publish else 'PREVIEW_OK ') + str(run.relative_to(ROOT)))


def main():
    args = parser().parse_args()
    if args.publish and args.plan_only:
        raise PublishError('--plan-only cannot be combined with --publish.')
    if not 1 <= args.attempts <= 10:
        raise PublishError('--attempts must be between 1 and 10.')
    if any(not math.isfinite(v) or not 0 <= v <= 3600 for v in (args.retry_delay, args.max_retry_delay, args.upload_interval)):
        raise PublishError('Retry delays and upload interval must be finite seconds between 0 and 3600.')
    if args.status:
        if args.publish:
            raise PublishError('--status does not publish; run it separately.')
        status_report()
        return
    if args.mark_not_uploaded or args.record_upload or args.record_primary or args.record_additional:
        if args.publish:
            raise PublishError('Reconcile an unconfirmed upload separately from --publish.')
        with publication_lock():
            reconcile_receipts()
            if args.mark_not_uploaded:
                mark_not_uploaded(args.mark_not_uploaded)
            elif args.record_upload:
                record_upload(*args.record_upload)
            elif args.record_primary:
                record_file(*args.record_primary, 'primary')
            else:
                record_file(*args.record_additional, 'additional')
        progress('Publishing state reconciled; no network request was made.')
        return
    config = tomllib.loads((ROOT / 'stonecutter.properties.toml').read_text(encoding='utf-8'))
    args.mod_version = args.mod_version or config['mod']['version']
    if not re.fullmatch(r'[0-9A-Za-z][0-9A-Za-z._+-]*', args.mod_version):
        raise PublishError('Invalid mod version.')
    args.platforms = csv(args.platforms, PLATFORMS, 'platforms')
    args.loaders = csv(args.loaders, LOADERS, 'loaders')
    if args.build and args.mod_version != config['mod']['version']:
        raise PublishError('--build uses the version in stonecutter.properties.toml.')
    # Dry runs never open the credential file or forward account tokens.
    credentials = (read_credentials(args.credentials_file) if args.publish else
                   {key: (os.environ.get(key, '') if key in PROJECT_KEYS else '') for key in PROJECT_KEYS + TOKEN_KEYS})
    if args.modrinth_norender_project_id is not None:
        credentials['MODRINTH_NORENDER_PROJECT_ID'] = args.modrinth_norender_project_id
    validate_project_ids(credentials)
    if args.publish:
        required_platforms = [p for p in args.platforms if not args.description_only or p == 'modrinth']
        for platform in required_platforms:
            for suffix in ('_PROJECT_ID', '_TOKEN'):
                if not credentials[platform.upper() + suffix]:
                    raise PublishError(f'Missing {platform.upper() + suffix} in external credentials or environment.')
    with publication_lock():
        run = OUTPUT / 'runs' / (datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ-') + uuid.uuid4().hex[:8])
        run.mkdir(parents=True)
        with session_log(run):
            try:
                run_release(args, credentials, run)
            except KeyboardInterrupt:
                progress('INTERRUPTED: receipts preserved. Run --status, then rerun the same command to resume safely.')
                if args.publish:
                    reconcile_receipts()
                raise
            except (PublishError, OSError) as error:
                progress('PUBLISH_ERROR: ' + str(error))
                raise


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
    sys.stderr.reconfigure(encoding='utf-8', errors='replace')
    try:
        main()
    except (PublishError, OSError) as error:
        print('PUBLISH_ERROR: ' + str(error), file=sys.stderr)
        raise SystemExit(1)
    except KeyboardInterrupt:
        raise SystemExit(130)
