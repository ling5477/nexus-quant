"""Regression tests for the long-lived source stage-semantic guard."""

from __future__ import annotations

import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


GUARD_PATH = Path(__file__).resolve().parents[1] / "check-stage-semantic-leakage.py"
SPEC = importlib.util.spec_from_file_location("stage_semantic_guard", GUARD_PATH)
assert SPEC and SPEC.loader
guard = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(guard)


class StageSemanticGuardTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "backend/sample/src/main/java/sample").mkdir(parents=True)
        self.policy = self.root / "policy.json"
        self.write_policy([], [])

    def write_policy(self, exceptions: list[dict], regions: list[dict]) -> None:
        self.policy.write_text(json.dumps({"schemaVersion": 1, "exceptions": exceptions,
                                           "historyRegions": regions}), encoding="utf-8")

    def write(self, relative: str, content: str) -> None:
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")

    def test_stage_comment_rejected_and_business_gate_kept(self) -> None:
        path = "backend/sample/src/main/java/sample/Policy.java"
        self.write(path, "class Policy { // RiskGate enforces GateD release scope\n}\n")
        errors, _ = guard.check(self.root, self.policy)
        self.assertTrue(any("STAGE_LEAKAGE:" in error and ":GateD" in error for error in errors))
        self.write(path, "class Policy { // RiskGate enforces order safety\n}\n")
        self.assertEqual([], guard.check(self.root, self.policy)[0])

    def test_allowlist_binds_exact_line_and_reports_stale_entry(self) -> None:
        path = "backend/sample/src/main/java/sample/Policy.java"
        line = 'class Policy { String version = "gate-r-4-shadow-decision-trace.v1"; }'
        self.write(path, line + "\n")
        entry = {"path": path, "token": "gate-r-4", "category": "RUNTIME_COMPATIBILITY_IDENTIFIER",
                 "reason": "Persisted policy version used by existing shadow decision trace readers.",
                 "lineSha256": guard.digest(line)}
        self.write_policy([entry], [])
        self.assertEqual([], guard.check(self.root, self.policy)[0])
        self.write(path, line.replace("; }", "; // GateD }" ) + "\n")
        errors, _ = guard.check(self.root, self.policy)
        self.assertTrue(any(error.startswith("STAGE_LEAKAGE:") for error in errors))
        self.assertTrue(any(error.startswith("STALE_EXCEPTION:") for error in errors))

    def test_historical_region_is_pinned_to_reviewed_content(self) -> None:
        path = "docs/current/API.md"
        content = guard.START + "\nGateD accepted historical scope\n" + guard.END + "\n"
        self.write(path, content)
        region = {"path": path, "token": "nq-stage-history", "regionIndex": 1,
                  "category": "HISTORICAL_REFERENCE",
                  "reason": "Historical accepted API scope retained under an exact content digest.",
                  "sha256": guard.digest("GateD accepted historical scope")}
        self.write_policy([], [region])
        self.assertEqual([], guard.check(self.root, self.policy)[0])
        self.write(path, content.replace("accepted", "pending"))
        self.assertTrue(any(error.startswith("UNREVIEWED_HISTORY_REGION:")
                            for error in guard.check(self.root, self.policy)[0]))

    def test_unpaired_history_marker_fails_closed(self) -> None:
        self.write("docs/current/API.md", guard.START + "\nGateD\n")
        self.assertTrue(any(error.startswith("UNPAIRED_HISTORY_START:")
                            for error in guard.check(self.root, self.policy)[0]))


if __name__ == "__main__":
    unittest.main()
