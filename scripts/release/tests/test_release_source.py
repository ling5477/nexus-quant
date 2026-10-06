"""使用独立 Git fixture 证明身份隔离、默认排除、确定性与路径拒绝。"""
from pathlib import Path
import copy
import importlib.util
import json
import os
import subprocess
import sys
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("release_source", Path(__file__).resolve().parents[1] / "release_source.py")
source = importlib.util.module_from_spec(spec)
spec.loader.exec_module(source)
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import scan_release_secrets as secrets


class ReleaseSourceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.repo = self.root / "repo"
        self.repo.mkdir()
        self.run_git("init", "-q")
        self.run_git("config", "user.name", "Release Fixture")
        self.run_git("config", "user.email", "fixture@example.invalid")
        self.run_git("config", "core.autocrlf", "false")
        self.policy = {"schemaVersion": 1, "targetVersion": "1.0.0",
                       "sourceIdentity": "exact-40-hex-git-commit;git-blobs-only",
                       "treeHashAlgorithm": "sha256-canonical-json-v1",
                       "groups": [{"category": "INCLUDE_RUNTIME", "purpose": "fixture", "paths": ["app.txt"]}],
                       "excludedPaths": {"EXCLUDE_DEV_ONLY": [source.MANIFEST]}, "resourceExceptions": [],
                       "forbiddenCategories": sorted(source.EXCLUDES), "requiredFinalAssets": []}
        self.write("app.txt", b"commit A\n")
        self.save_policy()
        self.a = self.commit("A", ["app.txt", source.MANIFEST])

    def tearDown(self):
        self.temp.cleanup()

    def run_git(self, *args, data=None):
        return subprocess.run(["git", "-C", str(self.repo), *args], input=data,
                              stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True, timeout=30).stdout

    def write(self, path, raw):
        dest = self.repo / path
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(raw)

    def save_policy(self):
        self.write(source.MANIFEST, source.canonical(self.policy) + b"\n")

    def commit(self, message, paths):
        self.run_git("add", "--", *paths)
        self.run_git("commit", "-qm", message)
        return self.run_git("rev-parse", "HEAD").decode().strip()

    def test_exact_commit_ignores_dirty_untracked_and_working_b(self):
        self.write("app.txt", b"working B\n")
        self.write("secret-untracked.txt", b"SYNTHETIC_SECRET_NOT_A_CREDENTIAL")
        self.write(".gitignore", b"ignored-secret.txt\n")
        self.write("ignored-secret.txt", b"SYNTHETIC_IGNORED_SECRET")
        self.policy["groups"][0]["paths"].append("secret-untracked.txt")
        self.save_policy()
        out = self.root / "out"
        source.export(self.repo, self.a, out)
        self.assertEqual((out / "app.txt").read_bytes(), b"commit A\n")
        self.assertFalse((out / "secret-untracked.txt").exists())
        self.assertFalse((out / "ignored-secret.txt").exists())
        source.verify(self.repo, self.a, out)

    def test_new_tracked_test_history_and_runtime_default_excluded(self):
        new = ["docs/gates/fake.md", "backend/nq-app/src/test/FakeTest.java", "new-runtime.txt"]
        for path in new:
            self.write(path, b"excluded")
        b = self.commit("B", new)
        out = self.root / "out"
        _, audit = source.export(self.repo, b, out)
        self.assertTrue(all(not (out / p).exists() for p in new))
        self.assertTrue(all(audit["paths"][p] == "REVIEW_REQUIRED" for p in new))
        self.assertEqual(audit["unclassified"], 0)

    def test_reproducible_and_fixed_tree_hash(self):
        a, _ = source.export(self.repo, self.a, self.root / "a")
        b, _ = source.export(self.repo, self.a, self.root / "b")
        self.assertEqual(a, b)
        self.assertEqual((self.root / "a" / source.METADATA).read_bytes(), (self.root / "b" / source.METADATA).read_bytes())
        self.assertEqual(source.canonical({"z": 1, "a": "值"}), '{"a":"值","z":1}'.encode())
        payload = {k: a[k] for k in ["sourceCommit", "targetVersion", "manifestSha256", "files"]}
        self.assertEqual(a["releaseTreeSha256"], source.sha(source.canonical(payload)))

    def test_manifest_drift_changes_both_hashes(self):
        a, _, _ = source.plan(self.repo, self.a)
        self.policy["groups"][0]["purpose"] = "reviewed purpose changed"
        self.save_policy()
        b = self.commit("manifest", [source.MANIFEST])
        changed, _, _ = source.plan(self.repo, b)
        self.assertNotEqual(a["manifestSha256"], changed["manifestSha256"])
        self.assertNotEqual(a["releaseTreeSha256"], changed["releaseTreeSha256"])

    def test_tampered_extra_or_metadata_fail(self):
        out = self.root / "out"
        source.export(self.repo, self.a, out)
        (out / "extra.txt").write_bytes(b"extra")
        with self.assertRaisesRegex(ValueError, "OUTPUT_FILE_SET"):
            source.verify(self.repo, self.a, out)
        (out / "extra.txt").unlink()
        (out / "app.txt").write_bytes(b"tampered")
        with self.assertRaisesRegex(ValueError, "CONTENT_MISMATCH"):
            source.verify(self.repo, self.a, out)
        (out / "app.txt").write_bytes(b"commit A\n")
        (out / source.METADATA).write_bytes(b"{}")
        with self.assertRaisesRegex(ValueError, "METADATA_MISMATCH"):
            source.verify(self.repo, self.a, out)

    def test_unexpected_empty_directory_rejected(self):
        out = self.root / "out"
        source.export(self.repo, self.a, out)
        (out / "unexpected-empty").mkdir()
        with self.assertRaisesRegex(ValueError, "DIRECTORY_SET_MISMATCH"):
            source.verify(self.repo, self.a, out)

    def test_nested_export_directories_are_valid(self):
        self.policy["groups"][0]["paths"] = ["nested/deep/app.txt"]
        self.write("nested/deep/app.txt", b"nested\n")
        self.policy["excludedPaths"]["EXCLUDE_DEV_ONLY"].append("app.txt")
        self.save_policy()
        b = self.commit("nested", ["nested/deep/app.txt", source.MANIFEST])
        out = self.root / "out"
        source.export(self.repo, b, out)
        source.verify(self.repo, b, out)

    def test_commit_identity_and_existing_output_rejected(self):
        for bad in ["HEAD", self.a[:12], self.a.upper(), self.run_git("rev-parse", "HEAD^{tree}").decode().strip()]:
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                source.plan(self.repo, bad)
        with self.assertRaisesRegex(ValueError, "MUST_NOT_EXIST"):
            source.export(self.repo, self.a, self.repo)

    def test_traversal_duplicate_case_and_windows_unsafe(self):
        for bad in ["/absolute", "../escape", "a/../b", "a//b", "C:/x", "a\\b", "a/CON.txt", "a.", "e\u0301.txt"]:
            with self.subTest(path=bad), self.assertRaises(ValueError):
                source.safe_path(bad)
        for paths in [["a", "a"], ["A/x", "a/y"], ["x", "x/y"]]:
            with self.subTest(paths=paths), self.assertRaises(ValueError):
                source.check_names(paths)

    def test_replace_ref_cannot_override_exact_source(self):
        self.write("app.txt", b"replaced B\n")
        b = self.commit("B", ["app.txt"])
        self.run_git("replace", self.a, b)
        out = self.root / "out"
        source.export(self.repo, self.a, out)
        self.assertEqual((out / "app.txt").read_bytes(), b"commit A\n")

    def test_protocol_marker_exception_does_not_hide_added_material(self):
        runtime = Path(__file__).resolve().parents[3] / secrets.SIGNER
        raw = runtime.read_bytes().replace(b"\r\n", b"\n")
        out = self.root / "scan"
        path = out / secrets.SIGNER
        path.parent.mkdir(parents=True)
        path.write_bytes(raw)
        finding = {"File": str(path.absolute()), "RuleID": "private-key", "StartLine": 22, "EndLine": 23}
        self.assertTrue(secrets.protocol_only(finding, out))
        for changed in [raw + b"SYNTHETIC_KEY_MATERIAL", raw.replace(secrets.DELIMITERS[0], secrets.DELIMITERS[0] + b"\nSYNTHETIC_KEY_MATERIAL")]:
            path.write_bytes(changed)
            self.assertFalse(secrets.protocol_only(finding, out))
        path.write_bytes(raw)
        for key, value in [("RuleID", "generic-api-key"), ("StartLine", 21), ("EndLine", 24), ("File", str(out / "other.java"))]:
            self.assertFalse(secrets.protocol_only({**finding, key: value}, out))

    def test_forbidden_and_duplicate_manifest_fail(self):
        self.write("docs/gates/fake.md", b"history")
        self.policy["groups"][0]["paths"].append("docs/gates/fake.md")
        self.save_policy()
        b = self.commit("forbidden", ["docs/gates/fake.md", source.MANIFEST])
        with self.assertRaisesRegex(ValueError, "FORBIDDEN_INCLUDE"):
            source.plan(self.repo, b)
        self.policy["groups"][0]["paths"] = ["app.txt", "app.txt"]
        self.save_policy()
        b = self.commit("duplicate", [source.MANIFEST])
        with self.assertRaisesRegex(ValueError, "DUPLICATE_PATH"):
            source.plan(self.repo, b)

    def test_git_symlink_mode_rejected_without_os_symlink_permission(self):
        oid = self.run_git("hash-object", "-w", "--stdin", data=b"outside").decode().strip()
        self.run_git("update-index", "--add", "--cacheinfo", "120000," + oid + ",link")
        self.run_git("commit", "-qm", "symlink")
        b = self.run_git("rev-parse", "HEAD").decode().strip()
        with self.assertRaisesRegex(ValueError, "SPECIAL_GIT_FILE"):
            source.plan(self.repo, b)

    def test_missing_include_and_classification_overlap_fail(self):
        self.policy["groups"][0]["paths"].append("missing.txt")
        self.save_policy()
        b = self.commit("missing", [source.MANIFEST])
        with self.assertRaisesRegex(ValueError, "MISSING_OR_FORBIDDEN"):
            source.plan(self.repo, b)
        self.policy["groups"][0]["paths"] = ["app.txt"]
        self.policy["excludedPaths"]["EXCLUDE_TEST"] = ["app.txt"]
        self.save_policy()
        b = self.commit("overlap", [source.MANIFEST])
        with self.assertRaisesRegex(ValueError, "CONFLICTING_CLASSIFICATION"):
            source.plan(self.repo, b)


if __name__ == "__main__":
    unittest.main()
