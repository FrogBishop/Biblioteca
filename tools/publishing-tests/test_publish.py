import io
import json
import os
from pathlib import Path
import sys
import tempfile
import tomllib
import unittest
from unittest.mock import patch
import urllib.error
import zipfile

REPOSITORY = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(REPOSITORY / 'tools'))
import publish


def make_jar(path, version, loader='fabric', mc='26.3', java=25):
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, 'w') as jar:
        if loader == 'fabric':
            jar.writestr('fabric.mod.json', json.dumps({'id': 'biblioteca', 'version': version,
                          'depends': {'minecraft': mc, 'fabric-api': '*', 'java': '>=' + str(java)}}))
        else:
            jar.writestr('META-INF/neoforge.mods.toml', f'[[mods]]\nmodId="biblioteca"\nversion="{version}"\n'
                          f'[[dependencies.biblioteca]]\nmodId="minecraft"\nversionRange="[{mc}]"\n')
        jar.writestr('babel/biblioteca/Example.class', b'\xca\xfe\xba\xbe\x00\x00' + (java + 44).to_bytes(2, 'big'))


class PublisherTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='biblioteca-publisher-test-')
        assert Path(self.temp.name).resolve().is_relative_to(Path(tempfile.gettempdir()).resolve())
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / 'repo'
        self.root.mkdir()
        self.output = self.root / 'build/publishing'
        self.addCleanup(patch.stopall)
        patch.object(publish, 'ROOT', self.root).start()
        patch.object(publish, 'OUTPUT', self.output).start()

    def write(self, name, content):
        p = self.root / name
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(content, encoding='utf-8')
        return p

    def manifest(self, norender_project=''):
        self.write('CHANGELOG.md', '## 1.0.2\nFirst paragraph.\n\n[Guide](README.md)\n\n## 1.0.0\nOld notes.')
        self.write('modrinth.md', '# Biblioteca\n\n项目介绍 — exact Unicode content.\n')
        self.write('modrinth-norender.md', '# Biblioteca No Render\n\nNo periodic visual rendering.\n')
        for loader in publish.LOADERS:
            version, paths = publish.artifact_paths('1.0.2', loader, '26.3')
            for path, suffix in zip(paths, ('', '-norender')):
                make_jar(path, version + suffix, loader)
        args = publish.parser().parse_args(['--versions', '26.3'])
        args.mod_version = '1.0.2'
        args.platforms = list(publish.PLATFORMS)
        run = self.output / 'runs/test'
        run.mkdir(parents=True)
        credentials = {'MODRINTH_PROJECT_ID': 'TEST0001', 'CURSEFORGE_PROJECT_ID': '123'}
        credentials['MODRINTH_NORENDER_PROJECT_ID'] = norender_project
        result = publish.prepare_manifest(args, [(l, '26.3') for l in publish.LOADERS], credentials, run)
        return result, run

    def test_external_credentials_and_environment_override(self):
        path = Path(self.temp.name) / 'credentials.json'
        path.write_text(json.dumps({'MODRINTH_PROJECT_ID': 'TEST0001', 'MODRINTH_TOKEN': 'file-value'}))
        credentials = publish.read_credentials(path, root=self.root, environ={'MODRINTH_TOKEN': 'environment-value'})
        self.assertEqual(credentials['MODRINTH_TOKEN'], 'environment-value')
        self.assertEqual(credentials['MODRINTH_PROJECT_ID'], 'TEST0001')

    def test_credentials_inside_checkout_are_rejected(self):
        with self.assertRaisesRegex(publish.PublishError, 'outside the repository'):
            publish.read_credentials(self.root / 'credentials.json', root=self.root, environ={})

    def test_invalid_credentials_do_not_echo_values(self):
        path = Path(self.temp.name) / 'credentials.json'
        path.write_text('{"MODRINTH_TOKEN": "not-for-error-output"', encoding='utf-8')
        with self.assertRaises(publish.PublishError) as error:
            publish.read_credentials(path, root=self.root, environ={})
        self.assertNotIn('not-for-error-output', str(error.exception))

    def test_only_publishing_environment_receives_selected_tokens(self):
        with patch.dict(os.environ, {'MODRINTH_TOKEN': 'old-mr', 'CURSEFORGE_TOKEN': 'old-cf'}):
            self.assertNotIn('MODRINTH_TOKEN', publish.child_environment())
            self.assertNotIn('CURSEFORGE_TOKEN', publish.child_environment())
            env = publish.child_environment({'MODRINTH_TOKEN': 'selected'})
            self.assertEqual(env['MODRINTH_TOKEN'], 'selected')
            self.assertNotIn('CURSEFORGE_TOKEN', env)

    def test_all_neoforge_selects_available_targets(self):
        self.write('platforms/neoforge-26.3/build.gradle', '')
        config = {'1.16.5': {}, '26.3': {}}
        self.assertEqual(publish.targets(config, 'all', ['neoforge']), [('neoforge', '26.3')])
        with self.assertRaises(publish.PublishError):
            publish.targets(config, '1.16.5', ['neoforge'])

    def test_real_repository_matrix_includes_supported_loaders(self):
        config = tomllib.loads((REPOSITORY / 'stonecutter.properties.toml').read_text())
        with patch.object(publish, 'ROOT', REPOSITORY):
            pairs = publish.targets(config, 'all', list(publish.LOADERS))
        self.assertGreaterEqual(len([p for p in pairs if p[0] == 'fabric']), 35)
        self.assertTrue({('neoforge', v) for v in ['26.1', '26.1.1', '26.1.2', '26.2', '26.3']} <= set(pairs))
        self.assertEqual(len(pairs), len(set(pairs)))

    def test_process_output_redacts_token_values(self):
        token = 'SYNTHETIC_OUTPUT_CANARY'
        log = self.root / 'redaction.log'
        with patch('sys.stdout', new_callable=io.StringIO) as output:
            publish.run_command([sys.executable, '-c', 'import os; print(os.environ["MODRINTH_TOKEN"])'],
                                log, publish.child_environment({'MODRINTH_TOKEN': token}), [token])
        self.assertNotIn(token, log.read_text(encoding='utf-8'))
        self.assertNotIn(token, output.getvalue())
        self.assertIn('[REDACTED]', output.getvalue())

    def test_wrong_game_version_and_wrong_variant_are_rejected(self):
        path = self.root / 'wrong.jar'
        make_jar(path, '1.0.2+26.3', mc='26.2')
        with self.assertRaises(publish.PublishError):
            publish.inspect_artifact(path, '1.0.2+26.3', 'fabric', '26.3', 'full')
        make_jar(path, '1.0.2+26.3')
        with self.assertRaises(publish.PublishError):
            publish.inspect_artifact(path, '1.0.2+26.3', 'fabric', '26.3', 'norender')

    def test_source_jar_without_mod_classes_is_rejected(self):
        path = self.root / 'sources.jar'
        with zipfile.ZipFile(path, 'w') as jar:
            jar.writestr('Example.java', 'class Example {}')
        with self.assertRaises(publish.PublishError):
            publish.inspect_artifact(path, '1.0.2+26.3', 'fabric', '26.3', 'full')

    def test_release_snapshot_pairs_variants_and_preserves_description(self):
        manifest, run = self.manifest()
        self.assertEqual(len(manifest['entries']), 2)
        for entry in manifest['entries']:
            self.assertEqual([a['variant'] for a in entry['files']], ['full', 'norender'])
            self.assertEqual(entry['minecraft'], '26.3')
            self.assertEqual(entry['java'], 25)
            for asset in entry['files']:
                self.assertEqual(publish.sha256(Path(asset['path'])), asset['sha256'])
        self.assertEqual((run / 'project-description.md').read_bytes(), (self.root / 'modrinth.md').read_bytes())
        self.assertTrue(manifest['changelog'].startswith('First paragraph.'))
        self.assertNotIn('Old notes', manifest['changelog'])
        self.assertIn('https://github.com/FrogBishop/Biblioteca/blob/multiversion/README.md', manifest['changelog'])
        self.assertEqual(manifest['schemaVersion'], 2)
        mr = [p for p in manifest['publications'] if p['platform'] == 'modrinth']
        cf = [p for p in manifest['publications'] if p['platform'] == 'curseforge']
        self.assertTrue(all(len(p['files']) == 1 and p['variant'] == 'full' for p in mr))
        self.assertTrue(all(len(p['files']) == 2 for p in cf))
        self.assertEqual(manifest['modrinthNoRender'], 'not-configured')

    def test_modrinth_norender_uses_a_distinct_project_and_one_jar(self):
        manifest, run = self.manifest('NOREND01')
        mr = [p for p in manifest['publications'] if p['platform'] == 'modrinth']
        self.assertEqual(len(mr), 4)
        for item in mr:
            self.assertEqual(len(item['files']), 1)
            self.assertEqual(item['files'][0]['variant'], item['variant'])
            self.assertEqual(item['projectId'], 'NOREND01' if item['variant'] == 'norender' else 'TEST0001')
            self.assertEqual(item['version'].endswith('-norender'), item['variant'] == 'norender')
        self.assertEqual((run / 'modrinth-norender-description.md').read_bytes(), (self.root / 'modrinth-norender.md').read_bytes())

    def test_modrinth_variants_cannot_use_the_same_project(self):
        with self.assertRaisesRegex(publish.PublishError, 'separate project'):
            self.manifest('TEST0001')

    def test_description_is_project_body_not_changelog(self):
        _, run = self.manifest()
        credentials = {'MODRINTH_PROJECT_ID': 'TEST0001', 'MODRINTH_TOKEN': 'synthetic-test-token'}
        with patch.object(publish.urllib.request, 'urlopen', return_value=io.BytesIO(b'')) as send:
            status = publish.sync_description(credentials, run, True, ['modrinth', 'curseforge'])
        request = send.call_args.args[0]
        self.assertEqual(request.method, 'PATCH')
        self.assertEqual(json.loads(request.data)['body'], (self.root / 'modrinth.md').read_bytes().decode())
        self.assertEqual(status['modrinth'], 'updated')
        self.assertTrue(status['curseforge'].startswith('manual:'))
        for p in run.glob('*.json'):
            self.assertNotIn(credentials['MODRINTH_TOKEN'], p.read_text(encoding='utf-8'))

    def test_description_failure_preserves_file_receipts(self):
        _, run = self.manifest()
        publish.write_json(self.output / 'state.json', {'schemaVersion': 1, 'uploads': {'example': {'status': 'published'}}})
        before = (self.output / 'state.json').read_bytes()
        error = urllib.error.HTTPError('https://api.modrinth.com', 401, 'Unauthorized', {}, None)
        with patch.object(publish.urllib.request, 'urlopen', side_effect=error):
            with self.assertRaisesRegex(publish.PublishError, 'description-only'):
                publish.sync_description({'MODRINTH_PROJECT_ID': 'TEST0001', 'MODRINTH_TOKEN': 'test'}, run, True, ['modrinth'])
        self.assertEqual((self.output / 'state.json').read_bytes(), before)

    def test_separate_modrinth_projects_get_their_own_description(self):
        _, run = self.manifest('NOREND01')
        credentials = {'MODRINTH_PROJECT_ID': 'TEST0001', 'MODRINTH_NORENDER_PROJECT_ID': 'NOREND01', 'MODRINTH_TOKEN': 'test'}
        with patch.object(publish.urllib.request, 'urlopen', side_effect=[io.BytesIO(b''), io.BytesIO(b'')]) as send:
            result = publish.sync_description(credentials, run, True, ['modrinth'])
        self.assertEqual(result, {'modrinth': 'updated', 'curseforge': 'not-selected', 'modrinth-norender': 'updated'})
        requests = [call.args[0] for call in send.call_args_list]
        self.assertTrue(requests[0].full_url.endswith('/TEST0001'))
        self.assertTrue(requests[1].full_url.endswith('/NOREND01'))
        self.assertEqual(json.loads(requests[1].data)['body'], (self.root / 'modrinth-norender.md').read_bytes().decode())

    def test_matching_remote_modrinth_release_is_reconciled(self):
        manifest, _ = self.manifest()
        manifest['platforms'] = ['modrinth']
        entry = next(p for p in manifest['publications'] if p['platform'] == 'modrinth')
        remote = [{'id': 'VERSION1', 'version_number': entry['version'], 'game_versions': ['26.3'], 'loaders': ['fabric'],
                   'files': [{'filename': f['name'], 'hashes': {'sha512': f['sha512']}, 'primary': i == 0}
                             for i, f in enumerate(entry['files'])]}]
        with patch.object(publish.urllib.request, 'urlopen', return_value=io.BytesIO(json.dumps(remote).encode())):
            publish.reconcile_modrinth(manifest, {'MODRINTH_PROJECT_ID': 'TEST0001', 'MODRINTH_TOKEN': 'test'})
        self.assertEqual(publish.load_state()['uploads']['modrinth:TEST0001:' + entry['version']]['status'], 'published')

    def test_conflicting_remote_version_is_rejected(self):
        manifest, _ = self.manifest()
        remote = [{'id': 'VERSION1', 'version_number': manifest['entries'][0]['version'],
                   'game_versions': ['26.3'], 'loaders': ['fabric'], 'files': []}]
        with patch.object(publish.urllib.request, 'urlopen', return_value=io.BytesIO(json.dumps(remote).encode())):
            with self.assertRaisesRegex(publish.PublishError, 'differs'):
                publish.reconcile_modrinth(manifest, {'MODRINTH_PROJECT_ID': 'TEST0001', 'MODRINTH_TOKEN': 'test'})

    def test_old_modrinth_two_mod_jar_release_is_not_accepted(self):
        manifest, _ = self.manifest()
        entry = manifest['entries'][0]
        remote = [{'id': 'VERSION1', 'version_number': entry['version'], 'game_versions': ['26.3'], 'loaders': ['fabric'],
                   'files': [{'filename': f['name'], 'hashes': {'sha512': f['sha512']}, 'primary': i == 0}
                             for i, f in enumerate(entry['files'])]}]
        with patch.object(publish.urllib.request, 'urlopen', return_value=io.BytesIO(json.dumps(remote).encode())):
            with self.assertRaisesRegex(publish.PublishError, 'differs'):
                publish.reconcile_modrinth(manifest, {'MODRINTH_PROJECT_ID': 'TEST0001', 'MODRINTH_TOKEN': 'test'})

    def test_worker_receipt_survives_interrupted_finalizer(self):
        result = self.output / 'runs/interrupted/plugin/result.json'
        publish.write_json(result, {'type': 'curseforge', 'projectId': '123', 'fileId': 42})
        key = 'curseforge:123:1.0.2+26.3'
        publish.write_json(self.output / 'state.json', {'schemaVersion': 1, 'uploads': {
            key: {'status': 'inflight', 'resultFile': str(result), 'fingerprint': 'same-files'}}})
        publish.reconcile_receipts()
        self.assertEqual(publish.load_state()['uploads'][key]['status'], 'published')
        with self.assertRaises(publish.PublishError):
            publish.mark_not_uploaded(key)

    def test_manual_reconciliation_is_explicit_and_local(self):
        key = 'curseforge:123:1.0.2+26.3'
        publish.write_json(self.output / 'state.json', {'schemaVersion': 1, 'uploads': {key: {'status': 'inflight'}}})
        with patch.object(publish.urllib.request, 'urlopen', side_effect=AssertionError('No HTTP expected')):
            publish.record_upload(key, '42')
        self.assertEqual(publish.load_state()['uploads'][key]['result']['fileId'], 42)

    def test_dry_run_never_opens_credentials(self):
        self.write('stonecutter.properties.toml', 'mod.version="1.0.2"\n["26.3"]\n')
        self.write('modrinth.md', '# Example\n')
        with patch.object(sys, 'argv', ['publish.py', '--description-only', '--dry-run']), \
             patch.object(publish, 'read_credentials', side_effect=AssertionError('Must not read tokens')):
            publish.main()


if __name__ == '__main__':
    unittest.main()
