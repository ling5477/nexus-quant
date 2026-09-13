"""验证原文件完整身份、双向集合匹配和不泄漏原文的失败行为。"""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import uuid

SCRIPT = Path(__file__).resolve().parents[1] / "verify-reviewed-gitleaks-findings.py"
SPEC = importlib.util.spec_from_file_location("reviewed_findings", SCRIPT)
verifier = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(verifier)


class ReviewedFindingsTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="reviewed-findings-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        subprocess.run(["git", "init", "-q", str(self.root)], check=True)
        self.path = "evidence/samples.json"
        self.file = self.root / self.path
        self.file.parent.mkdir()
        self.values = [str(uuid.uuid4()) + ":" + str(i) for i in range(4)]
        self.lines = ['  "sampleToken": "' + value + '",' for value in self.values]
        self.file.write_text("\n".join(self.lines) + "\n", encoding="utf-8")
        subprocess.run(["git", "-C", str(self.root), "add", "--", self.path], check=True,
                       stderr=subprocess.DEVNULL)
        self.registry = {"schemaVersion": 1, "entries": [dict(
            path=self.path, fileSha256=self.digest(self.file.read_bytes()), ruleId="generic-api-key",
            startLine=i + 1, endLine=i + 1, fieldName="sampleToken",
            fullValueSha256=self.digest(value.encode()), reason="已审合成采样身份",
            owner="security test", removalTrigger="身份变化时复核") for i, value in enumerate(self.values)]}
        self.report = [dict(File=self.path, RuleID="generic-api-key", StartLine=i + 1, EndLine=i + 1,
                            Secret="REDACTED", Match="REDACTED") for i in range(4)]
        self.registry_file, self.report_file = self.root / "registry.json", self.root / "report.json"

    @staticmethod
    def digest(data):
        return hashlib.sha256(data).hexdigest()

    def save(self):
        self.registry_file.write_text(json.dumps(self.registry), encoding="utf-8")
        self.report_file.write_text(json.dumps(self.report), encoding="utf-8")

    def check(self):
        self.save()
        return verifier.verify(self.root, self.root, self.report_file, self.registry_file)

    def reject(self, code=None):
        with self.assertRaises(verifier.Rejected) as error:
            self.check()
        if code:
            self.assertEqual(code, str(error.exception))

    def rebind_file_hash(self):
        for entry in self.registry["entries"]:
            entry["fileSha256"] = self.digest(self.file.read_bytes())

    def test_four_findings_exactly_reconciled(self):
        result = self.check()
        self.assertEqual(4, len(result))
        self.assertTrue(all(x["status"] == "REVIEWED_FALSE_POSITIVE" for x in result))
        self.report[0]["File"] = str(self.file)
        self.assertEqual(4, len(self.check()))

    def test_prefix_suffix_mutation_rejected_even_with_same_scanner_prefix(self):
        prefix = self.values[0][:22]
        self.report[0]["Secret"] = prefix
        changed = self.values[0][:-1] + "9"
        self.file.write_bytes(self.file.read_bytes().replace(self.values[0].encode(), changed.encode()))
        self.reject("FILE_SHA_MISMATCH")
        # 单独证明完整值校验，避免仅靠文件hash的负例掩盖该合同缺失。
        self.rebind_file_hash()
        self.reject("FULL_VALUE_SHA_MISMATCH")

    def test_file_drift_and_staging_divergence(self):
        staging = self.root / "staging"
        staged = staging / self.path
        staged.parent.mkdir(parents=True)
        staged.write_bytes(self.file.read_bytes() + b" ")
        self.save()
        with self.assertRaisesRegex(verifier.Rejected, "FILE_SHA_MISMATCH"):
            verifier.verify(self.root, staging, self.report_file, self.registry_file)
        self.file.write_bytes(self.file.read_bytes() + b" ")
        self.reject("FILE_SHA_MISMATCH")

    def test_strict_source_line_rejects_other_fields_and_ambiguity(self):
        original = self.file.read_bytes()
        cases = [self.lines[0].replace('"sampleToken"', '"apiKey"'),
                 self.lines[0] + ' "apiKey": "another-value",',
                 self.lines[0].replace("sampleToken", "sample\\u0054oken"),
                 self.lines[0].replace(self.values[0], "\\u0031" + self.values[0][1:]),
                 self.lines[0].replace(self.values[0], self.values[0] + "\\n"),
                 '  "sampleToken":']
        for line in cases:
            with self.subTest(lineKind=cases.index(line)):
                self.file.write_bytes(original.replace(self.lines[0].encode(), line.encode()))
                self.rebind_file_hash()
                self.reject("REVIEWED_FINDING_SOURCE_AMBIGUOUS")

    def test_report_unknown_duplicate_extra_and_missing(self):
        baseline = copy.deepcopy(self.report)
        for field, value in [("RuleID", "unknown-rule"), ("File", "evidence/other.json"),
                             ("StartLine", 10), ("EndLine", 10), ("StartLine", True)]:
            with self.subTest(field=field):
                self.report = copy.deepcopy(baseline)
                self.report[0][field] = value
                self.reject()
        self.report = baseline + [baseline[0]]
        self.reject("DUPLICATE_REPORT_MAPPING")
        self.report = baseline + [dict(baseline[0], File="unknown.json")]
        self.reject("UNREVIEWED_FINDING")
        self.report = baseline[:-1]
        self.reject("UNMATCHED_REGISTRY_ENTRY")

    def test_registry_schema_and_identity_errors(self):
        baseline = copy.deepcopy(self.registry)
        changes = [("path", "../other.json"), ("path", "/tmp/other.json"),
                   ("path", "evidence\\samples.json"), ("path", "evidence/./samples.json"),
                   ("fileSha256", "invalid"), ("fullValueSha256", "0" * 64),
                   ("fieldName", "apiKey"), ("ruleId", "unknown"), ("startLine", 0),
                   ("endLine", 2), ("owner", "")]
        for field, value in changes:
            with self.subTest(field=field):
                self.registry = copy.deepcopy(baseline)
                self.registry["entries"][0][field] = value
                self.reject()
        self.registry = copy.deepcopy(baseline)
        del self.registry["entries"][0]["reason"]
        self.reject("REGISTRY_SCHEMA")
        self.registry = copy.deepcopy(baseline)
        self.registry["entries"].append(self.registry["entries"][0])
        self.reject("DUPLICATE_REGISTRY_ENTRY")

    def test_unsafe_report_path_and_nontracked_source(self):
        baseline = copy.deepcopy(self.report)
        for path in ("../outside.json", "evidence/../evidence/samples.json", "/outside/samples.json"):
            self.report = copy.deepcopy(baseline)
            self.report[0]["File"] = path
            self.reject()
        self.report = baseline
        subprocess.run(["git", "-C", str(self.root), "rm", "--cached", "--", self.path],
                       check=True, stdout=subprocess.DEVNULL)
        self.reject("SOURCE_NOT_TRACKED")

    def test_malformed_inputs_duplicate_keys_and_logging_safety(self):
        for malformed in ("{", '{"entries":[],"entries":[]}', "null", "[]", "NaN"):
            for destination in (self.registry_file, self.report_file):
                self.save()
                destination.write_text(malformed, encoding="utf-8")
                result = subprocess.run([sys.executable, str(SCRIPT), "--repository-root", str(self.root),
                                         "--source-root", str(self.root), "--report", str(self.report_file),
                                         "--registry", str(self.registry_file)], capture_output=True, text=True)
                self.assertNotEqual(0, result.returncode)
                self.assertNotIn("Traceback", result.stderr)
                self.assertTrue(all(v not in result.stdout + result.stderr for v in self.values))

    def test_symlink_source_rejected(self):
        target = self.root / "real.json"
        target.write_bytes(self.file.read_bytes())
        self.file.unlink()
        try:
            self.file.symlink_to(target)
        except OSError:
            self.skipTest("platform cannot create symbolic link")
        self.reject("UNSAFE_SOURCE_PATH")


if __name__ == "__main__":
    unittest.main()
