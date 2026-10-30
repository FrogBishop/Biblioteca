import io
import json
from pathlib import Path
import sys
import threading
import time
from unittest.mock import patch
import urllib.error
import urllib.request

from test_publish import PublisherFixture, publish


class RecoveryTests(PublisherFixture):
    def test_read_retries_and_honors_retry_after_without_logging_response_body(self):
        responses = [urllib.error.HTTPError('https://example.invalid', 503, 'Unavailable', {}, io.BytesIO(b'SECRET_CANARY')),
                     urllib.error.HTTPError('https://example.invalid', 429, 'Limited', {'Retry-After': '3'}, io.BytesIO(b'')),
                     io.BytesIO(b'complete')]
        with patch.object(publish.urllib.request, 'urlopen', side_effect=responses) as send, \
             patch.object(publish.time, 'sleep') as sleep, patch('sys.stdout', new_callable=io.StringIO) as output:
            data = publish.http_request(urllib.request.Request('https://example.invalid'), 'fixture',
                                        {'attempts': 3, 'delay': 1, 'maxDelay': 10})
        self.assertEqual(data, b'complete')
        self.assertEqual(send.call_count, 3)
        self.assertEqual([c.args[0] for c in sleep.call_args_list], [1, 3])
        self.assertIn('attempt 3/3', output.getvalue())
        self.assertNotIn('SECRET_CANARY', output.getvalue())

    def test_auth_failure_does_not_retry(self):
        error = urllib.error.HTTPError('https://example.invalid', 401, 'Unauthorized', {}, io.BytesIO(b''))
        with patch.object(publish.urllib.request, 'urlopen', side_effect=error) as send:
            with self.assertRaisesRegex(publish.PublishError, 'HTTP 401'):
                publish.http_request(urllib.request.Request('https://example.invalid'), 'fixture')
        self.assertEqual(send.call_count, 1)

    def test_long_retry_after_stops_instead_of_retrying_too_early(self):
        error = urllib.error.HTTPError('https://example.invalid', 429, 'Limited', {'Retry-After': '120'}, io.BytesIO(b''))
        with patch.object(publish.urllib.request, 'urlopen', side_effect=error) as send, patch.object(publish.time, 'sleep') as sleep:
            with self.assertRaisesRegex(publish.PublishError, 'wait limit'):
                publish.http_request(urllib.request.Request('https://example.invalid'), 'fixture',
                                     {'attempts': 3, 'delay': 1, 'maxDelay': 10})
        self.assertEqual(send.call_count, 1)
        sleep.assert_not_called()

    def test_upload_post_cannot_enter_generic_retry_loop(self):
        with patch.object(publish.urllib.request, 'urlopen') as send:
            with self.assertRaises(AssertionError):
                publish.http_request(urllib.request.Request('https://example.invalid', data=b'file'), 'upload')
        send.assert_not_called()

    def test_individual_receipts_survive_interruption_before_state_update(self):
        key = 'curseforge:123:1.0.2+26.3'
        primary = self.output / 'runs/interrupted/plugin/primary.json'
        additional = primary.with_name('additional.json')
        record = {'status': 'inflight', 'fingerprint': 'files', 'pendingStep': 'primary',
                  'primaryResultFile': str(primary), 'additionalResultFile': str(additional)}
        publish.write_json(self.output / 'state.json', {'schemaVersion': 1, 'uploads': {key: record}})
        publish.write_json(primary, {'type': 'curseforge', 'projectId': '123', 'step': 'primary', 'fileId': 42, 'fingerprint': 'files'})
        publish.reconcile_receipts()
        stored = publish.load_state()['uploads'][key]
        self.assertEqual((stored['status'], stored['primaryFileId'], stored['pendingStep']), ('partial', 42, 'additional'))
        publish.write_json(additional, {'type': 'curseforge', 'projectId': '123', 'step': 'additional',
                                       'fileId': 43, 'parentFileId': 42, 'fingerprint': 'files'})
        publish.reconcile_receipts()
        stored = publish.load_state()['uploads'][key]
        self.assertEqual((stored['status'], stored['additionalFileId']), ('published', 43))

    def test_mismatched_file_receipt_does_not_confirm_an_upload(self):
        key = 'curseforge:123:1.0.2+26.3'
        receipt = self.output / 'runs/old/primary.json'
        publish.write_json(receipt, {'type': 'curseforge', 'projectId': '123', 'step': 'primary', 'fileId': 42, 'fingerprint': 'other-files'})
        publish.write_json(self.output / 'state.json', {'schemaVersion': 1, 'uploads': {
            key: {'status': 'inflight', 'fingerprint': 'current-files', 'primaryResultFile': str(receipt)}}})
        publish.reconcile_receipts()
        self.assertNotIn('primaryFileId', publish.load_state()['uploads'][key])

    def test_confirming_absent_additional_file_keeps_its_primary(self):
        key = 'curseforge:123:1.0.2+26.3'
        publish.write_json(self.output / 'state.json', {'schemaVersion': 1, 'uploads': {
            key: {'status': 'inflight', 'primaryFileId': 42, 'pendingStep': 'additional'}}})
        publish.mark_not_uploaded(key)
        stored = publish.load_state()['uploads'][key]
        self.assertEqual((stored['status'], stored['primaryFileId'], stored['pendingStep']), ('partial', 42, 'additional'))

    def test_manual_primary_and_additional_confirmation_is_local(self):
        key = 'curseforge:123:1.0.2+26.3'
        publish.write_json(self.output / 'state.json', {'schemaVersion': 1, 'uploads': {key: {'status': 'inflight'}}})
        with patch.object(publish.urllib.request, 'urlopen', side_effect=AssertionError('No HTTP')):
            publish.record_file(key, '42', 'primary')
            self.assertEqual(publish.load_state()['uploads'][key]['status'], 'partial')
            publish.record_file(key, '43', 'additional')
        self.assertEqual(publish.load_state()['uploads'][key]['status'], 'published')

    def test_reconfirming_primary_cannot_release_an_uncertain_additional_upload(self):
        key = 'curseforge:123:1.0.2+26.3'
        publish.write_json(self.output / 'state.json', {'schemaVersion': 1, 'uploads': {
            key: {'status': 'inflight', 'primaryFileId': 42, 'pendingStep': 'additional'}}})
        with self.assertRaisesRegex(publish.PublishError, 'already saved'):
            publish.record_file(key, '42', 'primary')
        self.assertEqual(publish.load_state()['uploads'][key]['status'], 'inflight')

    def test_status_accepts_manually_completed_legacy_entry_without_remote_id(self):
        publish.write_json(self.output / 'state.json', {'schemaVersion': 1, 'uploads': {
            'curseforge:123:1.0.3+1.20': {'status': 'published', 'manuallyConfirmed': True}}})
        with patch.object(sys, 'argv', ['publish.py', '--status']), \
             patch.object(publish, 'read_credentials', side_effect=AssertionError('No credentials')), \
             patch.object(publish.urllib.request, 'urlopen', side_effect=AssertionError('No network')):
            publish.main()
        self.assertTrue(publish.status_report()['complete'])

    def test_log_is_visible_and_flushed_while_child_is_running(self):
        log = self.root / 'stream.log'
        release = self.root / 'release-child'
        child = 'import time\nfrom pathlib import Path\nprint("first SYNTHETIC_CANARY", flush=True)\nwhile not Path(' + repr(str(release)) + ').exists(): time.sleep(0.01)\n'
        failures = []

        def run():
            try:
                publish.run_command([sys.executable, '-u', '-c', child], log,
                                    secrets=['SYNTHETIC_CANARY'], heartbeat=0.1)
            except BaseException as error:
                failures.append(error)

        with patch('sys.stdout', new_callable=io.StringIO) as output:
            worker = threading.Thread(target=run)
            worker.start()
            try:
                deadline = time.monotonic() + 5
                while time.monotonic() < deadline:
                    if log.exists() and 'WAIT' in log.read_text(encoding='utf-8'):
                        break
                    time.sleep(0.02)
                self.assertTrue(worker.is_alive())
                text = log.read_text(encoding='utf-8')
                self.assertIn('first [REDACTED]', text)
                self.assertIn('WAIT', output.getvalue())
                self.assertNotIn('SYNTHETIC_CANARY', text)
            finally:
                release.touch()
                worker.join(timeout=5)
        self.assertFalse(worker.is_alive())
        self.assertFalse(failures, failures)

    def test_bad_retry_options_fail_before_reading_credentials(self):
        for option, value in [('--attempts', '0'), ('--retry-delay', '-1'), ('--max-retry-delay', 'nan')]:
            with self.subTest(option=option), patch.object(sys, 'argv', ['publish.py', '--publish', option, value]), \
                 patch.object(publish, 'read_credentials', side_effect=AssertionError('No credentials')):
                with self.assertRaises(publish.PublishError):
                    publish.main()

    def test_interrupt_stops_the_owned_child_process(self):
        processes = []
        original_popen = publish.subprocess.Popen
        original_queue = publish.queue.Queue

        def start(*args, **kwargs):
            process = original_popen(*args, **kwargs)
            processes.append(process)
            return process

        class InterruptAfterOutput(original_queue):
            def get(self, *args, **kwargs):
                super().get(*args, **kwargs)
                raise KeyboardInterrupt

        with patch.object(publish.subprocess, 'Popen', side_effect=start), \
             patch.object(publish.queue, 'Queue', InterruptAfterOutput), \
             patch('sys.stdout', new_callable=io.StringIO):
            with self.assertRaises(KeyboardInterrupt):
                publish.run_command([sys.executable, '-u', '-c', 'import time; print("ready", flush=True); time.sleep(60)'],
                                    self.root / 'interrupted.log')
        self.assertTrue(processes)
        self.assertIsNotNone(processes[0].poll(), 'An interrupted upload process must not remain running')

    def test_lost_modrinth_response_is_reconciled_without_repeating_upload(self):
        self.manifest()
        self.write('stonecutter.properties.toml', 'mod.version="1.0.2"\n["26.3"]\n')
        args = publish.parser().parse_args(['--publish', '--versions', '26.3', '--retry-delay', '0'])
        args.mod_version, args.platforms, args.loaders = '1.0.2', ['modrinth'], ['fabric']
        args.changelog = self.root / 'CHANGELOG.md'
        credentials = {'MODRINTH_PROJECT_ID': 'TEST0001', 'MODRINTH_NORENDER_PROJECT_ID': '',
                       'CURSEFORGE_PROJECT_ID': '123', 'MODRINTH_TOKEN': 'synthetic', 'CURSEFORGE_TOKEN': ''}
        run = self.output / 'runs/recovered'
        run.mkdir(parents=True)
        remote = []

        def send(request, timeout):
            return io.BytesIO(json.dumps(remote).encode() if request.get_method() == 'GET' else b'')

        def interrupted(command, log, env, secrets):
            manifest = json.loads((run / 'manifest.json').read_text(encoding='utf-8'))
            e = manifest['publications'][0]
            key = 'modrinth:TEST0001:' + e['version']
            publish.write_json(self.output / 'state.json', {'schemaVersion': 1, 'uploads': {
                key: {'status': 'inflight', 'pendingStep': 'primary', 'fingerprint': e['fingerprint']}}})
            remote.append({'id': 'VERSION1', 'version_number': e['version'], 'game_versions': ['26.3'], 'loaders': ['fabric'],
                           'files': [{'filename': f['name'], 'hashes': {'sha512': f['sha512']}, 'primary': True} for f in e['files']]})
            raise publish.PublishError('Upload response lost')

        with patch.object(publish.urllib.request, 'urlopen', side_effect=send), \
             patch.object(publish, 'run_command', side_effect=interrupted) as upload:
            publish.run_release(args, credentials, run)
        self.assertEqual(upload.call_count, 1)
        self.assertTrue(json.loads((run / 'result.json').read_text(encoding='utf-8'))['passed'])
        failed_run = self.output / 'runs/invalid-snapshot'
        failed_run.mkdir()
        with patch.object(publish.urllib.request, 'urlopen', side_effect=send), \
             patch.object(publish, 'run_command', side_effect=publish.PublishError('Snapshot validation failed')):
            with self.assertRaisesRegex(publish.PublishError, 'incomplete'):
                publish.run_release(args, credentials, failed_run)
        self.assertFalse(json.loads((failed_run / 'result.json').read_text(encoding='utf-8'))['passed'])
