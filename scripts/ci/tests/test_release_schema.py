"""验证正式安装合同对历史库、源漂移和危险初始事实按失败处理。"""
import copy
import importlib.util
import json
import re
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location("release_schema", ROOT / "scripts/ci/verify-release-schema.py")
CONTRACT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CONTRACT)
DYNAMIC_SPEC = importlib.util.spec_from_file_location("development_schema", ROOT / "scripts/ci/development_schema.py")
DYNAMIC = importlib.util.module_from_spec(DYNAMIC_SPEC)
DYNAMIC_SPEC.loader.exec_module(DYNAMIC)
MANIFEST = json.loads((ROOT / "scripts/ci/development-schema-manifest.json").read_text(encoding="utf8"))


class ReleaseSchemaContractTests(unittest.TestCase):
    def test_current_source_identity(self):
        rows = DYNAMIC.inventory(ROOT)
        self.assertEqual(list(range(1, len(rows) + 1)), [int(row['version']) for row in rows])

    def copy_source(self, root, crlf=False):
        directory = root / 'backend/nq-infra/src/main/resources/db/migration'
        directory.mkdir(parents=True)
        for row in MANIFEST['migrations']:
            raw = (ROOT / 'backend/nq-infra/src/main/resources/db/migration' / row['script']).read_bytes()
            if crlf:
                raw = raw.replace(b'\r\n', b'\n').replace(b'\n', b'\r\n')
            (directory / row['script']).write_bytes(raw)
        source = root / MANIFEST['sourceManifest']
        source.parent.mkdir(parents=True)
        source.write_bytes((ROOT / MANIFEST['sourceManifest']).read_bytes())
        return directory

    def test_modified_missing_duplicate_and_additional_sql_are_rejected(self):
        for mutation in ('modified', 'missing', 'duplicate', 'additional'):
            with self.subTest(mutation=mutation), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                directory = self.copy_source(root)
                target = directory / MANIFEST['migrations'][1]['script']
                if mutation == 'modified':
                    target.write_bytes(target.read_bytes() + b'\\nSELECT 1;\\n')
                elif mutation == 'missing':
                    target.unlink()
                else:
                    (directory / ('V2__duplicate.sql' if mutation == 'duplicate' else 'V999__unexpected.sql')).write_text('SELECT 1;', encoding='utf8')
                with self.assertRaises(ValueError):
                    CONTRACT.verify_source(root, MANIFEST)

    def test_line_endings_do_not_change_source_identity(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.copy_source(root, crlf=True)
            CONTRACT.verify_source(root, MANIFEST)

    def test_omitted_release_entry_and_manifest_gap_are_rejected(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.copy_source(root)
            policy_path = root / MANIFEST['sourceManifest']
            policy = json.loads(policy_path.read_bytes())
            name = MANIFEST['migrations'][-1]['script']
            for group in policy['groups']:
                group['paths'] = [p for p in group['paths'] if not p.endswith('/' + name)]
            policy_path.write_text(json.dumps(policy), encoding='utf8')
            with self.assertRaisesRegex(ValueError, 'coverage'):
                CONTRACT.verify_source(root, MANIFEST)
        damaged = copy.deepcopy(MANIFEST)
        damaged['migrations'].pop(1)
        with self.assertRaisesRegex(ValueError, 'sequence'):
            CONTRACT.verify_source(ROOT, damaged)

    def test_original_baseline_snapshot_is_preserved(self):
        baseline = json.loads((ROOT / 'scripts/ci/release-schema-baseline-manifest.json').read_bytes())
        self.assertEqual(baseline['baselineSqlSha256'], MANIFEST['migrations'][0]['sha256'])
        self.assertEqual('1', baseline['flywayVersion'])

    def test_formal_contract_remains_separate_and_rejects_new_migrations(self):
        formal = json.loads((ROOT / 'scripts/ci/release-schema-manifest.json').read_bytes())
        self.assertNotIn('sourceManifest', formal)
        self.assertEqual('34', formal['flywayVersion'])
        with self.assertRaisesRegex(ValueError, 'inventory'):
            CONTRACT.verify_source(ROOT, formal)
        # 正式清单继续匹配自己的完整来源集合；开发清单不得成为隐式 fallback。
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            directory = root / 'backend/nq-infra/src/main/resources/db/migration'
            directory.mkdir(parents=True)
            for row in formal['migrations']:
                (directory / row['script']).write_bytes(
                    (ROOT / 'backend/nq-infra/src/main/resources/db/migration' / row['script']).read_bytes())
            (root / 'release').mkdir()
            (root / 'release/source-manifest.json').write_bytes((ROOT / 'release/source-manifest.json').read_bytes())
            CONTRACT.verify_source(root, formal)

    def test_development_migration_inventory_is_complete_without_manual_paths(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'source.json'
            rows = DYNAMIC.write_source(ROOT, output, 'HEAD^1')
            source = json.loads(output.read_bytes())
            paths = [p for group in source['groups'] for p in group['paths']]
            self.assertEqual(len(paths), len(set(paths)))
            self.assertEqual({p.name for p in (ROOT / DYNAMIC.DIRECTORY).iterdir()},
                             {row['script'] for row in rows})
            self.assertTrue(all((ROOT / p).is_file() for p in paths))

    def test_missing_development_inventory_never_falls_back_to_formal(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.copy_source(root)
            (root / MANIFEST['sourceManifest']).unlink()
            (root / 'release').mkdir()
            (root / 'release/source-manifest.json').write_bytes((ROOT / 'release/source-manifest.json').read_bytes())
            with self.assertRaises(FileNotFoundError):
                CONTRACT.verify_source(root, MANIFEST)

    def test_tenant_install_identity_is_covered_by_seed_contract(self):
        self.assertEqual('DEVELOPMENT_ONLY', MANIFEST['contractKind'])
        self.assertIn('tenants', MANIFEST['seedTables'])
        self.assertIn('tenant_memberships', MANIFEST['seedTables'])
        self.assertEqual(1, MANIFEST['freshInstallFacts']['rowCounts']['tenants'])
        self.assertEqual(0, MANIFEST['freshInstallFacts']['rowCounts']['tenant_memberships'])
        tenant = MANIFEST['freshInstallFacts']['facts']['tenants'][0]
        self.assertEqual('NQ_LOCAL', tenant['tenant_code'])
        self.assertEqual('00000000-0000-4000-8000-000000000001', tenant['tenant_id'])

    def test_database_checksum_failure_or_missing_history_is_rejected(self):
        for mutation in ('checksum', 'failed', 'missing'):
            history = copy.deepcopy(MANIFEST['flywayHistory'])
            if mutation == 'checksum':
                history[0]['checksum'] += 1
            elif mutation == 'failed':
                history[-1]['success'] = False
            else:
                history.pop()
            def execute(query):
                return [{'version': 160015}] if 'server_version_num' in query else history
            with self.subTest(mutation=mutation), self.assertRaisesRegex(ValueError, 'successful'):
                CONTRACT.verify_database(execute, MANIFEST)

    def test_development_history_is_rejected_before_catalog_queries(self):
        history = copy.deepcopy(MANIFEST["flywayHistory"])
        history.append({"version": "59", "success": True})
        def execute(query):
            if "server_version_num" in query:
                return [{"version": 160015}]
            if "flyway_schema_history" in query:
                return history
            self.fail("Development database must fail before schema/seed inspection")
        with self.assertRaisesRegex(ValueError, "complete successful"):
            CONTRACT.verify_database(execute, MANIFEST)

    def test_wrong_postgres_major_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "PostgreSQL 16"):
            CONTRACT.verify_database(lambda query: [{"version": 170000}], MANIFEST)

    def test_control_and_fixture_drift_is_rejected(self):
        model = {"counts": MANIFEST["schemaCounts"]}
        def execute(query):
            return [{"version": 160015}] if "server_version_num" in query else MANIFEST["flywayHistory"]
        # schema 正确也不能掩盖 fixture 用户或默认开启 scheduler。
        for table in ("users", "orders"):
            facts = copy.deepcopy(MANIFEST["freshInstallFacts"])
            facts["rowCounts"][table] = 1
            with patch.object(CONTRACT, "catalog", return_value=model), \
                    patch.object(CONTRACT, "normalize", return_value=model), \
                    patch.object(CONTRACT, "sha", return_value=MANIFEST["normalizedSchemaSha256"]), \
                    patch.object(CONTRACT, "seeds", return_value=facts):
                with self.assertRaisesRegex(ValueError, "seed/control"):
                    CONTRACT.verify_database(execute, MANIFEST)
        facts = copy.deepcopy(MANIFEST["freshInstallFacts"])
        facts["facts"]["scheduled_job_controls"][0]["enabled"] = True
        with patch.object(CONTRACT, "catalog", return_value=model), \
                patch.object(CONTRACT, "normalize", return_value=model), \
                patch.object(CONTRACT, "sha", return_value=MANIFEST["normalizedSchemaSha256"]), \
                patch.object(CONTRACT, "seeds", return_value=facts):
            with self.assertRaisesRegex(ValueError, "seed/control"):
                CONTRACT.verify_database(execute, MANIFEST)

    def test_unknown_and_expression_is_not_silently_normalized(self):
        definition = "CHECK ((a > 0) AND (b < 0))"
        self.assertEqual(definition, CONTRACT.canonical_definition(definition))

    def test_public_execute_acl_drift_is_rejected(self):
        model = {"counts": MANIFEST["schemaCounts"], "effective_acl": [
            {"kind": "function", "identity": "nq_backfill_ordinary_place_authorities(p_limit integer)",
             "grantee": "PUBLIC", "grantor": "OWNER", "privilege_type": "EXECUTE", "is_grantable": False}
        ]}
        def execute(query):
            return [{"version": 160015}] if "server_version_num" in query else MANIFEST["flywayHistory"]
        with patch.object(CONTRACT, "catalog", return_value=model), \
                patch.object(CONTRACT, "normalize", return_value=model):
            with self.assertRaisesRegex(ValueError, "fingerprint"):
                CONTRACT.verify_database(execute, MANIFEST)

    def test_acl_is_part_of_normalized_schema_identity(self):
        model = {key: [] for key in CONTRACT.queries}
        model["counts"] = MANIFEST["schemaCounts"]
        before = CONTRACT.sha(CONTRACT.normalize(model))
        model["effective_acl"].append({"kind": "function", "identity": "controlled_entry(integer)",
                                      "grantee": "PUBLIC", "privilege_type": "EXECUTE"})
        self.assertNotEqual(before, CONTRACT.sha(CONTRACT.normalize(model)))


if __name__ == "__main__":
    unittest.main()
