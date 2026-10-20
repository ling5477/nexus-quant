"""历史 EOF 例外不能掩盖新空白问题、源漂移或 Git 失败。"""
import importlib.util
import subprocess
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location("whitespace", ROOT / "scripts/ci/check-whitespace.py")
CONTRACT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CONTRACT)


class WhitespaceContractTests(unittest.TestCase):
    def result(self, lines, code=2, stderr=b""):
        return subprocess.CompletedProcess([], code, ("\n".join(lines) + "\n").encode(), stderr)

    def blob(self, path):
        self.assertEqual(CONTRACT.HISTORICAL_PATH, path)
        return (ROOT / path).read_bytes()

    def test_only_exact_historical_blob_and_diagnostic_are_accepted(self):
        result = CONTRACT.review(self.result([CONTRACT.HISTORICAL_DIAGNOSTIC]), self.blob)
        self.assertEqual({"rawExit": 2, "acceptedHistoricalDiagnostics": 1}, result)

    def test_other_file_line_diagnostic_and_additional_problem_are_rejected(self):
        diagnostic = CONTRACT.HISTORICAL_DIAGNOSTIC
        for lines in ([diagnostic, "other.sql:3: trailing whitespace."],
                      [diagnostic.replace(":57:", ":58:")],
                      [diagnostic.replace("V24__", "V25__")],
                      [diagnostic.replace("new blank line at EOF.", "trailing whitespace.")]):
            with self.subTest(lines=lines), self.assertRaises(ValueError):
                CONTRACT.review(self.result(lines), self.blob)

    def test_changed_blob_or_git_errors_are_rejected(self):
        diagnostic = [CONTRACT.HISTORICAL_DIAGNOSTIC]
        with self.assertRaises(ValueError):
            CONTRACT.review(self.result(diagnostic), lambda _: self.blob(CONTRACT.HISTORICAL_PATH) + b"\n")
        for result in (self.result([], 128, b"fatal"), self.result(diagnostic, 1),
                       self.result(diagnostic, 2, b"error"), self.result([], 2)):
            with self.assertRaises(ValueError):
                CONTRACT.review(result, self.blob)

    def test_clean_check_has_no_exception(self):
        self.assertEqual(0, CONTRACT.review(self.result([], 0), self.blob)["acceptedHistoricalDiagnostics"])

    def test_real_dev_diff_preserves_raw_failure(self):
        result = CONTRACT.check(ROOT, "064b6801f08486943c6e4691537de641dcd25c4d",
                                "5f119a274c057966da0db1af6128aaf639569615")
        self.assertEqual(2, result["rawExit"])


if __name__ == "__main__":
    unittest.main()
