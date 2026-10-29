"""Exercise the real pinned plugin against a loopback HTTP fixture, never a public API.

Run once after a normal dry run has cached the plugin. All credentials are synthetic,
and Gradle runs offline with external HTTPS requests redirected to an unused local port.
"""
from email import policy
from email.parser import BytesParser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import threading
import unittest
from unittest.mock import patch

from test_publish import REPOSITORY, make_jar, publish


class UploadHandler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def respond(self, value, status=200):
        content = json.dumps(value).encode()
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(content)))
        self.end_headers()
        self.wfile.write(content)

    def do_GET(self):
        if self.path.endswith('/version-types'):
            self.respond([{'id': i, 'name': name, 'slug': name} for i, name in
                          [(1, 'minecraft'), (2, 'modloader'), (3, 'environment'), (4, 'java')]])
        elif self.path.endswith('/game/versions'):
            self.respond([{'id': i, 'gameVersionTypeID': kind, 'name': name, 'slug': name.lower()} for i, kind, name in
                          [(101, 1, '26.3'), (201, 2, 'fabric'), (202, 2, 'neoforge'),
                           (301, 3, 'client'), (302, 3, 'server'), (401, 4, 'Java 25')]])
        elif self.path.endswith('/project/fabric-api/check'):
            self.respond({'id': 'P7dR8mSH'})
        else:
            self.respond({'error': 'unexpected fixture request'}, 404)

    def do_POST(self):
        body = self.rfile.read(int(self.headers['Content-Length']))
        message = BytesParser(policy=policy.default).parsebytes(
            ('Content-Type: ' + self.headers['Content-Type'] + '\r\nMIME-Version: 1.0\r\n\r\n').encode() + body)
        parts = {p.get_param('name', header='content-disposition'): p for p in message.iter_parts()}
        metadata = json.loads(parts['data' if 'data' in parts else 'metadata'].get_payload(decode=True))
        files = [p.get_filename() for p in parts.values() if p.get_filename()]
        self.server.uploads.append({'path': self.path, 'metadata': metadata, 'files': files})
        if self.path.endswith('/version') and len(files) != 1:
            self.respond({'error': 'invalid_input', 'description': 'One executable mod JAR per version'}, 400)
        elif self.server.fail_additional and metadata.get('parentFileID'):
            self.respond({'errorCode': 500, 'errorMessage': 'Synthetic additional-file failure'}, 500)
        elif self.path.endswith('/version'):
            self.respond({'id': 'VERSION1', 'project_id': metadata['project_id'], 'author_id': 'AUTHOR01'})
        else:
            self.respond({'id': 1000 + len(self.server.uploads)})


class PluginIntegrationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='biblioteca-plugin-test-')
        assert Path(self.temp.name).resolve().is_relative_to(Path(tempfile.gettempdir()).resolve())
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.root = self.base / 'repo'
        self.root.mkdir()
        self.run = self.root / 'build/publishing/runs/test'
        self.run.mkdir(parents=True)
        shutil.copytree(REPOSITORY / 'publishing', self.root / 'publishing', ignore=shutil.ignore_patterns('.gradle', 'build'))
        (self.root / 'CHANGELOG.md').write_text('## 1.0.2\n\nPublishing fixture.\n', encoding='utf-8')
        (self.root / 'modrinth.md').write_text('# Fixture\n', encoding='utf-8')
        with patch.object(publish, 'ROOT', self.root):
            for loader in publish.LOADERS:
                version, paths = publish.artifact_paths('1.0.2', loader, '26.3')
                for path, suffix in zip(paths, ('', '-norender')):
                    make_jar(path, version + suffix, loader)
            args = publish.parser().parse_args(['--publish', '--versions', '26.3', '--changelog', str(self.root / 'CHANGELOG.md')])
            args.mod_version = '1.0.2'
            args.platforms = list(publish.PLATFORMS)
            self.manifest = publish.prepare_manifest(args, {}, [(l, '26.3') for l in publish.LOADERS],
                {'MODRINTH_PROJECT_ID': 'TEST0001', 'CURSEFORGE_PROJECT_ID': '123'}, self.run)
        self.server = ThreadingHTTPServer(('127.0.0.1', 0), UploadHandler)
        self.server.uploads = []
        self.server.fail_additional = False
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.addCleanup(self.stop_server)
        port = self.server.server_address[1]
        self.init = self.base / 'local-api.gradle'
        self.init.write_text('gradle.projectsEvaluated {\n'
            '  rootProject.extensions.getByName("publishMods").platforms.each { platform ->\n'
            f'    platform.apiEndpoint.set("http://127.0.0.1:{port}/" + platform.name.split("_")[0])\n'
            '  }\n}\n', encoding='utf-8')

    def stop_server(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=3)

    def execute(self):
        publish.write_json(self.run / 'manifest.json', self.manifest)
        wrapper = str(REPOSITORY / ('gradlew.bat' if publish.os.name == 'nt' else 'gradlew'))
        command = [wrapper, '-p', str(self.root / 'publishing'), 'publishMods',
                   '-PreleaseManifest=' + str(self.run / 'manifest.json'), '-PpublishLive=true',
                   '--no-daemon', '--no-configuration-cache', '--no-build-cache', '--offline', '--max-workers=1',
                   '--project-cache-dir', str(self.base / 'external-cache'), '--init-script', str(self.init), '--console=plain',
                   '-Dhttps.proxyHost=127.0.0.1', '-Dhttps.proxyPort=9']
        credentials = {'MODRINTH_TOKEN': 'SYNTHETIC_MR_CANARY', 'CURSEFORGE_TOKEN': 'SYNTHETIC_CF_CANARY'}
        result = subprocess.run(command, cwd=self.root, env=publish.child_environment(credentials),
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, encoding='utf-8', errors='replace', timeout=90)
        self.last_output = result.stdout
        return result

    def state(self):
        return json.loads((self.root / 'build/publishing/state.json').read_text(encoding='utf-8'))['uploads']

    def test_upload_metadata_receipts_and_resume(self):
        result = self.execute()
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertEqual(len(self.server.uploads), 6)
        state = self.state()
        self.assertEqual(len(state), 4)
        self.assertTrue(all(v['status'] == 'published' for v in state.values()))
        mr = [r for r in self.server.uploads if r['path'].startswith('/modrinth/')]
        self.assertEqual(len(mr), 2)
        for request in mr:
            data = request['metadata']
            self.assertEqual(data['game_versions'], ['26.3'])
            self.assertEqual(data['environment'], 'client_and_server')
            self.assertEqual(data['primary_file'], 'primaryFile')
            self.assertEqual(len(request['files']), 1)
            self.assertFalse(request['files'][0].endswith('-norender.jar'))
            if data['loaders'] == ['fabric']:
                self.assertEqual(data['dependencies'][0]['project_id'], 'P7dR8mSH')
            else:
                self.assertEqual(data['loaders'], ['neoforge'])
                self.assertFalse(data['dependencies'])
        cf = [r for r in self.server.uploads if r['path'].startswith('/curseforge/')]
        self.assertEqual(len([r for r in cf if r['metadata'].get('parentFileID')]), 2)
        for request in cf:
            if not request['metadata'].get('parentFileID'):
                self.assertTrue({101, 301, 302, 401} <= set(request['metadata']['gameVersions']))
        again = self.execute()
        self.assertEqual(again.returncode, 0, again.stdout)
        self.assertEqual(len(self.server.uploads), 6, 'A resumed release must not upload again')
        for path in self.root.rglob('*'):
            if path.is_file():
                raw = path.read_bytes()
                self.assertNotIn(b'SYNTHETIC_MR_CANARY', raw, str(path))
                self.assertNotIn(b'SYNTHETIC_CF_CANARY', raw, str(path))
        self.manifest['publications'][0]['fingerprint'] = 'changed-release-metadata'
        conflict = self.execute()
        self.assertNotEqual(conflict.returncode, 0)
        self.assertEqual(len(self.server.uploads), 6)

    def test_partial_upload_is_not_blindly_retried(self):
        self.manifest['platforms'] = ['curseforge']
        self.manifest['entries'] = self.manifest['entries'][:1]
        self.manifest['publications'] = [p for p in self.manifest['publications'] if p['platform'] == 'curseforge'][:1]
        self.server.fail_additional = True
        result = self.execute()
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(len(self.server.uploads), 2, result.stdout)
        self.assertEqual(next(iter(self.state().values()))['status'], 'inflight')
        again = self.execute()
        self.assertNotEqual(again.returncode, 0)
        self.assertEqual(len(self.server.uploads), 2, 'No automatic retry after an ambiguous partial upload')

    def test_snapshot_change_stops_all_uploads(self):
        Path(self.manifest['entries'][-1]['files'][-1]['path']).write_bytes(b'changed after preview')
        result = self.execute()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Release snapshot missing or changed', result.stdout)
        self.assertEqual(self.server.uploads, [])

    def test_optional_norender_project_gets_separate_single_file_releases(self):
        self.manifest['projects']['modrinthNorender'] = 'NOREND01'
        self.manifest['platforms'] = ['modrinth']
        self.manifest['publications'] = publish.make_publications(self.manifest['entries'], ['modrinth'],
            self.manifest['projects'], self.manifest['releaseType'], self.manifest['changelog'])
        result = self.execute()
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertEqual(len(self.server.uploads), 4)
        for request in self.server.uploads:
            self.assertEqual(len(request['files']), 1)
            nr = request['files'][0].endswith('-norender.jar')
            self.assertEqual(request['metadata']['project_id'], 'NOREND01' if nr else 'TEST0001')
            self.assertEqual(request['metadata']['version_number'].endswith('-norender'), nr)

    def test_modrinth_two_mod_jars_are_blocked_before_any_request(self):
        mr = next(p for p in self.manifest['publications'] if p['platform'] == 'modrinth')
        mr['files'] = self.manifest['entries'][0]['files']
        result = self.execute()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('exactly one executable mod JAR', result.stdout)
        self.assertEqual(self.server.uploads, [])

    def test_legacy_manifest_is_rejected(self):
        self.manifest['schemaVersion'] = 1
        self.manifest.pop('publications')
        result = self.execute()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('obsolete release manifest', result.stdout)
        self.assertEqual(self.server.uploads, [])


if __name__ == '__main__':
    unittest.main(verbosity=2)
