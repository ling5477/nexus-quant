"""验证正式安装合同对历史库、源漂移和危险初始事实按失败处理。"""
import copy
import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location("release_schema", ROOT / "scripts/ci/verify-release-schema.py")
CONTRACT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CONTRACT)
MANIFEST = json.loads((ROOT / "scripts/ci/release-schema-manifest.json").read_text(encoding="utf8"))


class ReleaseSchemaContractTests(unittest.TestCase):
    def test_current_source_identity(self):
        CONTRACT.verify_source(ROOT, MANIFEST)

    def test_modified_or_duplicate_sql_is_rejected(self):
        source = ROOT / "backend/nq-infra/src/main/resources/db/migration" / MANIFEST["baselineFile"]
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            directory = root / "backend/nq-infra/src/main/resources/db/migration"
            directory.mkdir(parents=True)
            target = directory / MANIFEST["baselineFile"]
            target.write_bytes(source.read_bytes() + b"\nSELECT 1;\n")
            with self.assertRaisesRegex(ValueError, "checksum"):
                CONTRACT.verify_source(root, MANIFEST)
            target.write_bytes(source.read_bytes())
            (directory / "V2__unexpected.sql").write_text("SELECT 1;", encoding="utf8")
            with self.assertRaisesRegex(ValueError, "exactly"):
                CONTRACT.verify_source(root, MANIFEST)

    def test_line_endings_do_not_change_source_identity(self):
        source = ROOT / "backend/nq-infra/src/main/resources/db/migration" / MANIFEST["baselineFile"]
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            directory = root / "backend/nq-infra/src/main/resources/db/migration"
            directory.mkdir(parents=True)
            (directory / MANIFEST["baselineFile"]).write_bytes(source.read_bytes().replace(b"\r\n", b"\n").replace(b"\n", b"\r\n"))
            CONTRACT.verify_source(root, MANIFEST)

    def test_development_history_is_rejected_before_catalog_queries(self):
        history = copy.deepcopy(MANIFEST["flywayHistory"])
        history.append({"version": "59", "success": True})
        def execute(query):
            if "server_version_num" in query:
                return [{"version": 160015}]
            if "flyway_schema_history" in query:
                return history
            self.fail("Development database must fail before schema/seed inspection")
        with self.assertRaisesRegex(ValueError, "single successful"):
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
