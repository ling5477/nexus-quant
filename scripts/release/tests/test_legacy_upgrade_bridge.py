"""桥接信任、历史合同与中断恢复的有界负例；真实数据库证明单独记录。"""
import argparse
import importlib.util
import json
from contextlib import nullcontext, ExitStack
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


REPO = Path(__file__).resolve().parents[3]
ENTRY = REPO / 'release/installers/legacy_upgrade_bridge.py'
BASELINE = REPO / 'scripts/verify/upgrade_business_baseline.py'
spec = importlib.util.spec_from_file_location('legacy_upgrade_bridge', ENTRY)
b = importlib.util.module_from_spec(spec)
spec.loader.exec_module(b)


class BridgeTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='nq-bridge-test-')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.home = self.root / 'installation'
        self.home.mkdir()
        (self.home / 'runtime').mkdir()
        self.trust = {'format': 'NQ_LEGACY_BRIDGE_V1', 'bridgeSourceCommit': '1' * 40,
            'bridgeSha256': b.sha(ENTRY), 'baselineToolSha256': b.sha(BASELINE),
            'sourceManifestSha256': '2' * 64, 'targetManifestSha256': '3' * 64,
            'targetArchiveSha256': '4' * 64, 'targetSourceCommit': '5' * 40,
            'architecture': 'amd64', 'installationConfigSha256': '6' * 64, 'projectName': 'nq-bridge-test'}
        self.transition = self.root / 'transition.json'
        self.transition.write_text(json.dumps(self.trust), encoding='utf-8')

    def args(self, **kwargs):
        values = dict(action='preflight', install_root=self.home, transition=self.transition,
            trusted_transition_sha256=b.sha(self.transition), baseline_tool=BASELINE,
            source_package=None, target_package=None, target_archive=None, transaction=None, confirm=None)
        values['local_candidate'] = False
        values.update(kwargs)
        return argparse.Namespace(**values)

    def bridge(self, **kwargs):
        return b.Bridge(self.args(**kwargs))

    def change_trust(self, key, value):
        self.trust[key] = value
        self.transition.write_text(json.dumps(self.trust), encoding='utf-8')

    def test_external_transition_digest_required(self):
        with self.assertRaisesRegex(b.Refused, 'UNTRUSTED_TRANSITION'):
            b.Bridge(self.args(trusted_transition_sha256='0' * 64))

    def test_bridge_artifact_digest_refused(self):
        self.change_trust('bridgeSha256', '0' * 64)
        with self.assertRaisesRegex(b.Refused, 'BRIDGE_ARTIFACT'):
            self.bridge()

    def test_tool_cannot_be_substituted(self):
        self.change_trust('baselineToolSha256', '0' * 64)
        with self.assertRaisesRegex(b.Refused, 'BASELINE_TOOL'):
            self.bridge()

    def test_unknown_transition_fields_refused(self):
        self.change_trust('ignoreSecurity', True)
        with self.assertRaisesRegex(b.Refused, 'CONTRACT'):
            self.bridge()

    def test_source_commit_must_be_exact(self):
        self.change_trust('bridgeSourceCommit', 'dev')
        with self.assertRaisesRegex(b.Refused, 'COMMIT'):
            self.bridge()

    def test_construction_is_read_only(self):
        before = {str(p): p.stat().st_mtime_ns for p in self.root.rglob('*')}
        self.bridge()
        self.assertEqual(before, {str(p): p.stat().st_mtime_ns for p in self.root.rglob('*')})

    def test_cancel_before_lock_or_mutation(self):
        bridge = self.bridge()
        with patch.object(b, 'installation_lock') as lock:
            with self.assertRaisesRegex(b.Refused, 'EXPLICIT_CONFIRMATION'):
                bridge.execute()
            lock.assert_not_called()

    def test_recover_requires_explicit_authorization(self):
        bridge = self.bridge()
        with self.assertRaisesRegex(b.Refused, 'RECOVERY_CONFIRMATION'):
            bridge.recover()

    def test_status_requires_stable_transaction(self):
        with self.assertRaisesRegex(b.Refused, 'TRANSACTION_REQUIRED'):
            self.bridge().load_transaction()

    def test_metadata_rejects_duplicate_and_crlf(self):
        path = self.root / 'bad.env'
        for data in (b'A=one\nA=two\n', b'A=one\r\n', b'A=one', b'\xef\xbb\xbfA=one\n'):
            path.write_bytes(data)
            with self.assertRaises(b.Refused):
                b.metadata(path)

    def test_atomic_retry_preserves_interrupted_temp_for_audit(self):
        file = self.root / 'state'
        file.write_bytes(b'original')
        file.with_suffix('.tmp').write_bytes(b'partial')
        b.atomic(file, b'new')
        self.assertEqual(file.read_bytes(), b'new')
        self.assertEqual(file.with_suffix('.tmp').read_bytes(), b'partial')

    def test_package_manifest_strict_fields(self):
        root = self.root / 'package'
        root.mkdir()
        path = root / 'package-amd64.env'
        path.write_bytes(b'FORMAT=1\nVERSION=1.0.1\n')
        with self.assertRaisesRegex(b.Refused, 'PACKAGE_CONTRACT'):
            b.package(root, b.sha(path), 'amd64')

    def test_image_must_match_declared_platform(self):
        bridge = self.bridge()
        m = {'POSTGRES_IMAGE': 'sha256:' + '1' * 64, 'POSTGRES_CONFIG_DIGEST': 'sha256:' + '2' * 64, 'ARCH': 'amd64'}
        with patch.object(bridge, 'docker', return_value=json.dumps([{'Id': m['POSTGRES_IMAGE'], 'Os': 'linux', 'Architecture': 'arm64'}])):
            with self.assertRaisesRegex(b.Refused, 'IMAGE_IDENTITY'):
                bridge.image(m, 'POSTGRES')

    def test_undeclared_image_cannot_fall_back_to_tag(self):
        bridge = self.bridge()
        m = {'POSTGRES_IMAGE': 'sha256:' + '1' * 64, 'POSTGRES_CONFIG_DIGEST': 'sha256:' + '2' * 64}
        with patch.object(bridge, 'docker', side_effect=b.Refused('missing')) as docker:
            with self.assertRaisesRegex(b.Refused, 'NOT_LOADED'):
                bridge.image(m, 'POSTGRES')
            self.assertEqual(docker.call_count, 2)

    def test_comparison_preserves_raw_different(self):
        bridge = self.bridge()
        raw = {'result': 'DIFFERENT', 'changes': [{'table': 'account_equity_measurements',
                        'missing': [], 'changed': [], 'added': ['new']}]}
        with patch.object(bridge.baseline, 'compare', return_value=raw):
            self.assertEqual(bridge.facts({}, {}, False)['result'], 'DIFFERENT')
            with self.assertRaisesRegex(b.Refused, 'BUSINESS_FACTS'):
                bridge.facts({}, {})

    def test_missing_keys_and_changed_economic_fields_always_refused(self):
        bridge = self.bridge()
        for change in ({'table': 'ledger_entries', 'missing': ['old'], 'changed': [], 'added': []},
                       {'table': 'account_equity_measurements', 'missing': [], 'changed': ['old'], 'added': ['new']}):
            with patch.object(bridge.baseline, 'compare', return_value={'result': 'DIFFERENT', 'changes': [change]}):
                with self.assertRaises(b.Refused):
                    bridge.facts({}, {}, False)

    def test_parent_compose_overrides_are_removed(self):
        with patch.dict('os.environ', {'NQ_HOME': '/wrong', 'DB_PASSWORD': 'wrong', 'COMPOSE_FILE': 'wrong'}):
            bridge = self.bridge()
        self.assertFalse({'NQ_HOME', 'DB_PASSWORD', 'COMPOSE_FILE'} & bridge.env.keys())

    def test_real_windows_lock_blocks_second_bridge(self):
        with b.installation_lock(self.home, 'a' * 64):
            with self.assertRaises((b.Refused, FileExistsError)):
                with b.installation_lock(self.home, 'a' * 64):
                    self.fail('concurrent bridge acquired lock')
        self.assertFalse((self.home / 'runtime/.operation-lock').exists())

    def test_orchestration_failures_keep_original_assets_or_durable_recovery_guard(self):
        # 明确是SIMULATED阶段注入：证明编排拒绝/持久化边界，不冒充PG恢复资格。
        for fault in ('stop', 'backup', 'image-switch', 'role-provisioning', 'flyway',
                      'application-start', 'before-commit', 'receipt'):
            with self.subTest(fault=fault):
                bridge = self.bridge(confirm='BRIDGE 1.0.0 TO 1.0.1')
                bridge.root = self.root / fault
                bridge.root.mkdir()
                bridge.runtime = bridge.root / 'runtime'
                bridge.runtime.mkdir()
                (bridge.root / 'config').mkdir()
                target = self.root / ('target-' + fault)
                target.mkdir()
                (target / 'runtime').mkdir()
                (target / 'installers').mkdir()
                m = dict(FORMAT='1', VERSION='1.0.1', SOURCE_SHA='a' * 40,
                         RELEASE_SOURCE_HASH='b' * 64, SCHEMA_VERSION='47', ARCH='amd64')
                for file, key in b.ASSETS.items():
                    (target / file).write_text('target-' + file)
                    m[key] = b.sha(target / file)
                b.write_env(target / 'package-amd64.env', m)
                bridge.args.target_package = target
                bridge.trust['targetManifestSha256'] = b.sha(target / 'package-amd64.env')
                for name in b.INSTALLED:
                    (bridge.runtime / name).write_text('old-' + name)
                b.write_env(bridge.runtime / 'package.env', {'VERSION': '1.0.0'})
                (bridge.runtime / 'VERSION').write_bytes(b'1.0.0\n')
                config = dict(ARCH='amd64', POSTGRES_IMAGE='old-image', DB_PASSWORD='1' * 64,
                              JWT_SECRET='2' * 64, CREDENTIALS_KEY='3' * 64)
                b.write_env(bridge.root / 'config/runtime.env', config)
                bridge.trust['installationConfigSha256'] = b.sha(bridge.root / 'config/runtime.env')
                events = []
                clone_image = ['old-image']
                def preflight():
                    bridge.config = config
                    bridge.source = {}
                    bridge.target = m
                def stop():
                    events.append('stop')
                    if fault == 'stop':
                        raise b.Refused('INJECTED_STOP')
                def snapshot(image, data, archive, restore=False):
                    if fault == 'backup':
                        raise b.Refused('INJECTED_BACKUP')
                    if not restore:
                        archive.write_bytes(b'cluster')
                def docker(*args, **kwargs):
                    if args[0] == 'cp':
                        Path(args[-1]).write_bytes(b'dump')
                    if 'dpkg-query' in args:
                        v = 'u1' if clone_image[0] == 'old-image' else 'u2'
                        return f'liblzma5:amd64 5.8.1-1+deb13{v}\nxz-utils 5.8.1-1+deb13{v}'
                    return 'same'
                def compose(*args):
                    events.append('compose:' + ' '.join(str(v) for v in args))
                    if ('maintenance' in args and fault in ('role-provisioning','flyway')):
                        raise b.Refused('INJECTED_MAINTENANCE')
                    if '--force-recreate' in args and 'postgres' in args and fault == 'image-switch':
                        raise b.Refused('INJECTED_IMAGE_SWITCH')
                    if 'backend' in args and fault == 'application-start':
                        raise b.Refused('INJECTED_START')
                    return ''
                def phase(value):
                    original_phase(value)
                    events.append(value)
                    if value == 'TARGET_RUNTIME_VERIFIED' and fault == 'before-commit':
                        raise b.Refused('INJECTED_INTERRUPT')
                original_phase = bridge.phase
                real_write = b.write_json
                def write(path, value):
                    if path.name == 'receipt.json' and fault == 'receipt':
                        raise OSError('INJECTED_RECEIPT')
                    real_write(path, value)
                patches = [patch.object(b, 'installation_lock', return_value=nullcontext()),
                     patch.object(b, 'protect'), patch.object(b, 'write_json', side_effect=write),
                     patch.object(bridge, 'preflight', side_effect=preflight),
                     patch.object(bridge, 'stop', side_effect=stop), patch.object(bridge, 'stop_services'),
                     patch.object(bridge, 'snapshot', side_effect=snapshot),
                     patch.object(bridge, 'docker', side_effect=docker), patch.object(bridge, 'compose', side_effect=compose),
                     patch.object(bridge, 'capture', return_value={'schema': [{'version':'34'}]}),
                     patch.object(bridge, 'pg_container', return_value='container'),
                     patch.object(bridge, 'database_identity', return_value='identity'),
                     patch.object(bridge, 'auth_hash', return_value='auth'),
                     patch.object(bridge, 'sql', side_effect=lambda c,q: '47' if 'SELECT version FROM' in q else '0'),
                     patch.object(bridge, 'image', return_value='new-image'),
                     patch.object(bridge, 'clone_start', side_effect=lambda n,i,d: clone_image.__setitem__(0,i)),
                     patch.object(bridge, 'stop_clone'), patch.object(bridge, 'verify_clone'),
                     patch.object(bridge, 'facts', return_value={'result':'MATCH'}),
                     patch.object(bridge, 'verify_runtime'), patch.object(bridge, 'phase', side_effect=phase)]
                with ExitStack() as stack:
                    for mocked in patches:
                        stack.enter_context(mocked)
                    with self.assertRaises((b.Refused, OSError)):
                        bridge.execute()
                self.assertIn('stop', events)
                if fault in ('stop', 'backup'):
                    self.assertEqual((bridge.runtime / 'compose.yml').read_text(), 'old-compose.yml')
                    self.assertNotIn('DB_TRANSITION_STARTED', events)
                else:
                    self.assertTrue((bridge.runtime / 'pending-update').exists())
                    self.assertTrue((bridge.runtime / 'pending-restore.env').exists())
                    record = json.loads((bridge.directory / 'phase.json').read_bytes())
                    self.assertTrue(record['databaseMayHaveChanged'])
                    self.assertEqual(record['phase'] == 'COMMITTED', fault == 'receipt')


if __name__ == '__main__':
    unittest.main()
