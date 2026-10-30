"""用独立 Git 与 Wheel 样本验证发行信任边界，不重跑因子业务资格。"""
from pathlib import Path
import json
import subprocess
import sys
import tempfile
import tomllib
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import python_companion as companion
import release_source as source


class CompanionSourceTest(unittest.TestCase):
    def test_repository_component_version_matches_product(self):
        repo = Path(__file__).resolve().parents[3]
        project = tomllib.loads((repo / "research/py/pyproject.toml").read_text(encoding="utf-8"))["project"]
        self.assertEqual(project["version"], (repo / "VERSION").read_text().strip())
        self.assertEqual(project["dependencies"], ["numpy==2.3.5"])

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.repo = self.root / "repo"
        self.repo.mkdir()
        self.git("init", "-q")
        self.git("config", "user.name", "Companion Fixture")
        self.git("config", "user.email", "fixture@example.invalid")
        self.git("config", "core.autocrlf", "false")
        self.paths = ["VERSION", "research/py/pyproject.toml", "research/py/src/nq_research/__init__.py", companion.POLICY]
        self.write("VERSION", b"1.0.3\n")
        self.write("research/py/src/nq_research/__init__.py", b'"""fixture"""\n')
        self.write("research/py/pyproject.toml", b'[project]\nname="nexus-quant-research"\nversion="1.0.3"\ndependencies=["numpy==2.3.5"]\n')
        self.policy()
        self.head = self.commit(self.paths)

    def git(self, *args):
        return subprocess.check_output(["git", "-C", str(self.repo), *args], stderr=subprocess.PIPE, timeout=30)

    def write(self, path, raw):
        dest = self.repo / path
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(raw)

    def policy(self):
        self.write(companion.POLICY, source.canonical({"schemaVersion": 1, "productVersion": "1.0.3",
                   "companionVersion": "1.0.3", "paths": sorted(self.paths)}) + b"\n")

    def commit(self, paths):
        self.git("add", "--", *paths)
        self.git("commit", "-qm", "fixture")
        return self.git("rev-parse", "HEAD").decode().strip()

    def test_exact_git_source_ignores_worktree_and_repeats(self):
        self.write("research/py/src/nq_research/__init__.py", b"dirty worktree\n")
        a, b = self.root / "a", self.root / "b"
        first = companion.export(self.repo, self.head, a)
        second = companion.export(self.repo, self.head, b)
        self.assertEqual(first, second)
        self.assertEqual((a / "research/py/src/nq_research/__init__.py").read_bytes(), b'"""fixture"""\n')
        self.assertEqual(companion.verify(self.repo, self.head, a), first)
        with self.assertRaisesRegex(ValueError, "FRESH_EXTERNAL"):
            companion.export(self.repo, self.head, a)

    def test_corrupt_or_extra_export_is_rejected(self):
        out = self.root / "out"
        companion.export(self.repo, self.head, out)
        (out / "VERSION").write_bytes(b"1.0.4\n")
        with self.assertRaisesRegex(ValueError, "CONTENT_MISMATCH"):
            companion.verify(self.repo, self.head, out)
        (out / "extra.txt").write_bytes(b"extra")
        with self.assertRaisesRegex(ValueError, "FILE_SET_MISMATCH"):
            companion.verify(self.repo, self.head, out)

    def test_new_runtime_cannot_silently_disappear(self):
        path = "research/py/src/nq_research/new_runtime.py"
        self.write(path, b"value = 1\n")
        head = self.commit([path])
        with self.assertRaisesRegex(ValueError, "RUNTIME_NOT_INCLUDED"):
            companion.plan(self.repo, head)

    def test_package_version_mismatch_fails(self):
        self.write("VERSION", b"1.0.4\n")
        head = self.commit(["VERSION"])
        with self.assertRaisesRegex(ValueError, "VERSION_OR_DEPENDENCY_MISMATCH"):
            companion.plan(self.repo, head)

    def test_source_escape_and_test_inclusion_fail(self):
        self.paths.append("research/py/tests/test_private.py")
        self.write(self.paths[-1], b"pass\n")
        self.policy()
        head = self.commit(self.paths)
        with self.assertRaisesRegex(ValueError, "FORBIDDEN_COMPANION_PATH"):
            companion.plan(self.repo, head)
        with self.assertRaisesRegex(ValueError, "EXACT_COMMIT_REQUIRED"):
            companion.plan(self.repo, "HEAD")

    def test_locked_wheel_hash_metadata_platform_and_closure(self):
        name = "numpy-2.3.5-cp311-cp311-win_amd64.whl"
        wheel = self.root / name
        with zipfile.ZipFile(wheel, "w") as archive:
            archive.writestr("numpy/", "")
            archive.writestr("numpy/__init__.py", "")
            archive.writestr("numpy-2.3.5.dist-info/METADATA", "Name: numpy\nVersion: 2.3.5\n\n")
        lock = {"schemaVersion": 1, "target": {"os": "windows", "architecture": "amd64", "python": "3.11", "implementation": "cpython"},
                "buildTools": {}, "runtime": [{"name": "numpy", "version": "2.3.5", "filename": name,
                "sha256": source.sha(wheel.read_bytes()), "url": "https://files.pythonhosted.org/fixture.whl"}]}
        self.assertEqual(companion.dependencies(lock, self.root), lock["runtime"])
        wheel.write_bytes(wheel.read_bytes() + b"changed")
        with self.assertRaisesRegex(ValueError, "DEPENDENCY_HASH_MISMATCH"):
            companion.dependencies(lock, self.root)
        lock["runtime"][0]["filename"] = "../" + name
        with self.assertRaisesRegex(ValueError, "UNSAFE_PATH"):
            companion.dependencies(lock, self.root)

    def test_wheel_traversal_and_special_members_fail(self):
        wheel = self.root / "invalid.whl"
        with zipfile.ZipFile(wheel, "w") as archive:
            archive.writestr("../unsafe/", "")
        with self.assertRaisesRegex(ValueError, "UNSAFE_PATH"):
            companion.wheel_metadata(wheel)
        with zipfile.ZipFile(wheel, "w") as archive:
            link = zipfile.ZipInfo("unsafe")
            link.create_system = 3
            link.external_attr = 0o120777 << 16
            archive.writestr(link, "outside")
        with self.assertRaisesRegex(ValueError, "SPECIAL_WHEEL_MEMBER"):
            companion.wheel_metadata(wheel)

    def test_cli_from_clean_projection_does_not_create_bytecode(self):
        scripts = Path(__file__).resolve().parents[1]
        for name in ("python_companion.py", "release_source.py"):
            path = "scripts/release/" + name
            self.write(path, (scripts / name).read_bytes())
            self.paths.append(path)
        self.policy()
        head = self.commit(self.paths)
        output = self.root / "projection"
        companion.export(self.repo, head, output)
        command = [sys.executable, str(output / "scripts/release/python_companion.py"), "verify",
                   "--repo", str(self.repo), "--commit", head, "--output", str(output)]
        result = subprocess.run(command, capture_output=True, timeout=30)
        self.assertEqual(result.returncode, 0, result.stderr.decode(errors="replace"))
        self.assertFalse(list(output.rglob("*.pyc")))
        companion.verify(self.repo, head, output)


if __name__ == "__main__":
    unittest.main()
