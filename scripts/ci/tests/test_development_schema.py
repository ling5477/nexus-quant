"""证明新增迁移自动发现，同时拒绝历史改写、运行漂移和失败后资源泄漏。"""
import copy
import importlib.util
import json
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location("dynamic_schema", ROOT / "scripts/ci/development_schema.py")
DYNAMIC = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(DYNAMIC)


class DevelopmentSchemaTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.directory = self.root / DYNAMIC.DIRECTORY
        shutil.copytree(ROOT / DYNAMIC.DIRECTORY, self.directory)
        (self.root / 'scripts/ci').mkdir(parents=True)
        shutil.copyfile(ROOT / 'scripts/ci/development-schema-manifest.json',
                        self.root / 'scripts/ci/development-schema-manifest.json')
        for args in [('init', '-q'), ('add', '.'), ('-c', 'user.name=CI Fixture', '-c', 'user.email=fixture@example.invalid',
                                                 'commit', '-qm', 'fixture baseline')]:
            subprocess.run(['git', '-C', str(self.root), *args], capture_output=True, check=True, timeout=10)
        self.rows = DYNAMIC.inventory(self.root, 'HEAD')

    def test_next_migration_is_discovered_without_editing_either_snapshot(self):
        before = (self.root / 'scripts/ci/development-schema-manifest.json').read_bytes()
        version = len(self.rows) + 1
        (self.directory / f'V{version}__automatic_extension.sql').write_text('CREATE TABLE new_fact(id bigint PRIMARY KEY);')
        output = self.root / 'generated/source.json'
        rows = DYNAMIC.write_source(self.root, output, 'HEAD')
        self.assertEqual(str(version), rows[-1]['version'])
        self.assertEqual(len(self.rows) + 1, len(rows))
        self.assertEqual(before, (self.root / 'scripts/ci/development-schema-manifest.json').read_bytes())
        self.assertEqual(len(rows), len(json.loads(output.read_bytes())['groups'][0]['paths']))

    def test_changed_or_removed_historical_migration_is_rejected(self):
        path = self.directory / self.rows[-1]['script']
        path.write_bytes(path.read_bytes() + b'\nSELECT 1;\n')
        with self.assertRaisesRegex(ValueError, 'Existing migration'):
            DYNAMIC.inventory(self.root, 'HEAD')
        path.unlink()
        with self.assertRaises(ValueError):
            DYNAMIC.inventory(self.root, 'HEAD')

    def test_baseline_hash_is_checked_even_if_git_base_also_contains_the_edit(self):
        path = self.directory / self.rows[0]['script']
        path.write_bytes(path.read_bytes() + b'\nSELECT 1;\n')
        with self.assertRaisesRegex(ValueError, 'Frozen migration'):
            DYNAMIC.inventory(self.root, 'HEAD')

    def test_duplicate_gap_and_unexpected_file_are_rejected(self):
        for name in ['V2__duplicate.sql', f'V{len(self.rows) + 2}__gap.sql', 'unexpected.sql']:
            path = self.directory / name
            path.write_text('SELECT 1;')
            with self.subTest(name=name), self.assertRaises(ValueError):
                DYNAMIC.inventory(self.root, 'HEAD')
            path.unlink()

    def test_missing_git_base_cannot_silently_disable_history_protection(self):
        with self.assertRaisesRegex(ValueError, 'unavailable'):
            DYNAMIC.inventory(self.root, 'refs/heads/missing')

    def test_multi_commit_push_checks_before_sha_instead_of_only_last_parent(self):
        before = DYNAMIC.git(self.root, 'rev-parse', 'HEAD').decode().strip()
        path = self.directory / self.rows[-1]['script']
        path.write_bytes(path.read_bytes() + b'\nSELECT 1;\n')
        for args in [('add', '.'), ('-c', 'user.name=CI Fixture', '-c', 'user.email=fixture@example.invalid',
                                  'commit', '-qm', 'rewrite existing migration'),
                     ('-c', 'user.name=CI Fixture', '-c', 'user.email=fixture@example.invalid',
                      'commit', '--allow-empty', '-qm', 'unrelated last commit')]:
            subprocess.run(['git', '-C', str(self.root), *args], capture_output=True, check=True, timeout=10)
        self.assertEqual(len(self.rows), len(DYNAMIC.inventory(self.root, 'HEAD^1')))
        with self.assertRaisesRegex(ValueError, 'Existing migration'):
            DYNAMIC.inventory(self.root, before)
        workflow = (ROOT / '.github/workflows/ci.yml').read_text(encoding='utf-8')
        self.assertIn('github.event.pull_request.base.sha || github.event.before', workflow)
        self.assertIn('os.environ.get("NQ_MIGRATION_BASE_REF"',
                      (ROOT / 'scripts/ci/development_schema.py').read_text(encoding='utf-8'))

    def test_line_endings_preserve_identity(self):
        path = self.directory / self.rows[-1]['script']
        path.write_bytes(path.read_bytes().replace(b'\r\n', b'\n').replace(b'\n', b'\r\n'))
        self.assertEqual(self.rows, DYNAMIC.inventory(self.root, 'HEAD'))

    def test_nonempty_new_business_table_and_changed_control_are_rejected(self):
        baseline = json.loads((ROOT / 'scripts/ci/development-schema-manifest.json').read_bytes())
        facts = copy.deepcopy(baseline['freshInstallFacts'])
        facts['rowCounts']['new_business_table'] = 0
        DYNAMIC.assert_fresh_facts(facts, baseline)
        facts['rowCounts']['new_business_table'] = 1
        with self.assertRaisesRegex(ValueError, 'business rows'):
            DYNAMIC.assert_fresh_facts(facts, baseline)
        facts = copy.deepcopy(baseline['freshInstallFacts'])
        facts['facts']['scheduled_job_controls'][0]['enabled'] = True
        with self.assertRaisesRegex(ValueError, 'seed/control'):
            DYNAMIC.assert_fresh_facts(facts, baseline)

    def test_failed_reference_migration_drops_only_its_own_database(self):
        with patch.dict(DYNAMIC.os.environ, {'NQ_FLYWAY_DB_URL': 'jdbc:postgresql://localhost/actual'}), \
                patch.object(DYNAMIC, 'psql') as sql, \
                patch.object(DYNAMIC.subprocess, 'run', return_value=subprocess.CompletedProcess([], 1)):
            with self.assertRaisesRegex(ValueError, 'reference migration'):
                DYNAMIC.verify_database(self.root, 'host=localhost dbname=actual', 'classpath',
                                        self.root / 'source.json', self.rows, 'HEAD')
            self.assertEqual(2, sql.call_count)
            created = sql.call_args_list[0].args[1].removeprefix('CREATE DATABASE ')
            self.assertRegex(created, '^nq_ci_reference_[a-f0-9]{32}$')
            self.assertEqual('DROP DATABASE ' + created + ' WITH (FORCE)', sql.call_args_list[1].args[1])

    def test_connection_replacement_does_not_reuse_the_actual_database(self):
        self.assertEqual('host=localhost dbname=actual dbname=reference',
                         DYNAMIC.database_connection('host=localhost dbname=actual', 'reference'))
        self.assertEqual('postgresql://localhost/reference',
                         DYNAMIC.database_connection('postgresql://localhost/actual', 'reference'))


if __name__ == '__main__':
    unittest.main()
