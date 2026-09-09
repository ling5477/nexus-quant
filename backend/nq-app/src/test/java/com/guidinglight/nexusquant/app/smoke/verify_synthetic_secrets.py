"""用当前 CI 的配置、固定版本和参数验证导出物；不修改 scanner 或 allowlist。"""
import argparse
import fnmatch
import hashlib
import json
import re
import shutil
import subprocess
import tarfile
import tempfile
import textwrap
from pathlib import Path
from synthetic_evidence import IdentityMapper, export


def verify(root, archive, output, git):
    lock = json.loads((root / "scripts/ci/delivery-supply-chain-lock.json").read_text(encoding="utf-8"))
    tool = next(item for item in lock["tools"] if item["name"] == "gitleaks")
    assert hashlib.sha256(archive.read_bytes()).hexdigest() == tool["sha256"]
    workflow = (root / ".github/workflows/ci.yml").read_text(encoding="utf-8")
    config = textwrap.dedent(workflow.split('cat > "${config}" <<\'TOML\'\n', 1)[1].split('\n          TOML', 1)[0])
    # 从当前 canonical safe-file case 提取排除规则，避免维护第二份 policy。
    case = workflow.split('case "${f}" in', 1)[1].split('esac', 1)[0]
    excluded = [pattern for line in case.splitlines() if ') continue;;' in line
                for pattern in line.strip().split(') continue;;')[0].split('|')]
    output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="nq-l4-secret-export-") as temporary:
        temp = Path(temporary)
        binary = temp / "gitleaks"
        with tarfile.open(archive) as package:
            binary.write_bytes(package.extractfile("gitleaks").read())
        binary.chmod(0o700)
        assert subprocess.check_output([str(binary), "version"], text=True).strip() == tool["version"]
        configuration = temp / "ci.toml"
        configuration.write_text(config, encoding="utf-8")
        results = []

        def scan(name, source, expected):
            report = temp / (name + ".json")
            args = [str(binary), "detect", "--source", str(source), "--no-git", "--config", str(configuration),
                    "--redact", "--report-format", "json", "--report-path", str(report), "--exit-code", "2", "--no-banner"]
            completed = subprocess.run(args, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            findings = json.loads(report.read_text()) if report.exists() else []
            item = {"case": name, "exit": completed.returncode, "findings": len(findings), "expectedExit": expected}
            results.append(item)
            print(json.dumps(item), flush=True)
            if completed.returncode != expected:
                # 只输出字段元数据，不输出源行或 secret。
                (output / "unexpected-findings.json").write_text(json.dumps([
                    {"rule": f["RuleID"], "file": f["File"], "line": f["StartLine"]} for f in findings], indent=2))
                raise AssertionError("pinned scan result mismatch: " + name)
            if expected == 2:
                assert findings

        probes = temp / "probes"
        probes.mkdir()
        sentinel = "AbCdEfGhIjKlMnOp" + "QrStUvWxYz0192837465"
        # 复用已有 B1 三种兼容边界；值从当前精确 allowlist 读取，不复制到源码。
        legacy = re.search(r'b0[0-9a-f]{30}', config)[0]
        cases = {"legacy-new-value": {"api_key": "b0" + "0123456789abcdef0123456789abcd"},
                 "legacy-real-shaped": {"api_key": sentinel},
                 "legacy-extended-value": {"api_key": legacy + "AbCdEfGhIjKlMnOp"},
                 "apiKey": {"apiKey": sentinel}, "token": {"token": sentinel},
                 "Authorization": {"Authorization": sentinel}}
        for name, original in cases.items():
            mapped = export(original, IdentityMapper("B2", 1))
            assert mapped == original
            (probes / "candidate.json").write_text(json.dumps(mapped), encoding="utf-8")
            scan(name, probes, 2)

        tracked = set(subprocess.check_output([git, "ls-files", "-z"], cwd=root).decode().split('\0')) - {""}
        # 仅纳入本任务新增的 harness 和 evidence，不扫描不相关未跟踪数据。
        untracked = set(subprocess.check_output([git, "ls-files", "--others", "--exclude-standard", "-z"], cwd=root).decode().split('\0'))
        tracked.update(p for p in untracked if p.startswith((
            "backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/",
            "docs/audit/evidence/l4-b2-synthetic-identity-remediation/")))
        stage = temp / "tracked"
        stage.mkdir()
        count = 0
        for name in sorted(tracked):
            source = root / name
            if not source.is_file() or any(fnmatch.fnmatchcase(name, pattern) for pattern in excluded):
                continue
            destination = stage / name
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, destination)
            count += 1
        scan("tracked-working-tree", stage, 0)
        summary = {"version": tool["version"], "archiveSha256": tool["sha256"],
                   "workflowSha256": hashlib.sha256((root / ".github/workflows/ci.yml").read_bytes()).hexdigest(),
                   "safeFiles": count, "results": results, "secretFieldsUnchanged": True}
        (output / "secret-validation.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("archive", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--git", default="git")
    args = parser.parse_args()
    repository = next(p for p in Path(__file__).resolve().parents if (p / "scripts/ci/delivery-supply-chain-lock.json").exists())
    verify(repository, args.archive.resolve(), args.output.resolve(), args.git)
