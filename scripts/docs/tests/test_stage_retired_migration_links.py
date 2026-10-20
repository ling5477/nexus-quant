"""冻结迁移源码引用必须绑定真实历史 Git blob，不能放宽未知断链。"""
import json
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]


class RetiredMigrationLinkTests(unittest.TestCase):
    def test_exact_history_resolves_and_tampered_or_unknown_source_fails(self):
        manifest = json.loads((ROOT / "docs/archive/db-migrations/retired-development-migrations.json").read_text(encoding="utf8"))
        filename = next(iter(manifest["migrations"]))
        object_directory = subprocess.check_output(["git", "-C", str(ROOT), "rev-parse", "--git-path", "objects"], text=True).strip()
        objects = (ROOT / object_directory).resolve()
        with tempfile.TemporaryDirectory() as temporary:
            fixture = Path(temporary)
            subprocess.run(["git", "init", "-q", str(fixture)], check=True, timeout=10)
            # 只读共享已有对象；测试不创建候选提交，也不复制退休 SQL 到任何 classpath。
            (fixture / ".git/objects/info/alternates").write_text(objects.as_posix() + "\n", encoding="utf8", newline="\n")
            checker = fixture / "scripts/docs/check-doc-links.ps1"
            checker.parent.mkdir(parents=True)
            shutil.copyfile(ROOT / "scripts/docs/check-doc-links.ps1", checker)
            manifest_path = fixture / "docs/archive/db-migrations/retired-development-migrations.json"
            manifest_path.parent.mkdir(parents=True)
            document = fixture / "docs/audit/retired-source.md"
            document.parent.mkdir(parents=True)

            def run(name):
                document.write_text("[source](../../" + manifest["pathPrefix"] + name + ")\n", encoding="utf8")
                manifest_path.write_text(json.dumps(manifest), encoding="utf8")
                return subprocess.run(["pwsh", "-NoProfile", "-File", str(checker), "-Roots", "docs/audit/retired-source.md"],
                                      capture_output=True, text=True, timeout=30)

            accepted = run(filename)
            self.assertEqual(0, accepted.returncode, accepted.stdout + accepted.stderr)
            self.assertIn("HISTORICAL_SOURCE_RESOLVED", accepted.stdout)
            manifest["migrations"][filename]["gitBlob"] = "0" * 40
            self.assertNotEqual(0, run(filename).returncode)
            self.assertNotEqual(0, run("V999999__unknown.sql").returncode)


if __name__ == "__main__":
    unittest.main()
