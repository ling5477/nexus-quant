"""独立离线研究资产：准确 Git 投影、冻结依赖、Wheel 和发行摘要。"""
from __future__ import annotations

import argparse
import email
import json
import os
from pathlib import Path
import re
import shutil
import stat
import subprocess
import sys
import tempfile
import tomllib
import zipfile

# 入口可直接位于干净源码投影；导入本地辅助模块也不能生成未声明文件。
sys.dont_write_bytecode = True
import release_source as source

POLICY = "release/python-companion-source-manifest.json"
IDENTITY = ".python-companion-source.json"
LOCK = "release/python-companion-dependencies.json"
MAX_ASSET = 96 * 1024 * 1024


def read_json(raw: bytes) -> dict:
    return json.loads(raw, object_pairs_hook=source.unique_object)


def plan(repo: Path, commit: str) -> tuple[dict, dict[str, bytes]]:
    entries = source.tree(repo, commit)
    raw = source.blobs(repo, [entries[POLICY][1]])[entries[POLICY][1]]
    policy = read_json(raw)
    if set(policy) != {"schemaVersion", "productVersion", "companionVersion", "paths"} or policy["schemaVersion"] != 1:
        raise ValueError("INVALID_COMPANION_POLICY")
    paths = policy["paths"]
    source.check_names(paths)
    if paths != sorted(paths) or not paths or any(p not in entries for p in paths):
        raise ValueError("INVALID_COMPANION_PATHS")
    required = {p for p in entries if p.startswith("research/py/src/")}
    if not required.issubset(paths):
        raise ValueError("COMPANION_RUNTIME_NOT_INCLUDED")
    for path in paths:
        if not (path.startswith("research/py/src/") or path in {
            "VERSION", "LICENSE", "DISCLAIMER.md", "THIRD_PARTY_NOTICES.md", POLICY, LOCK,
            "research/py/README.md", "research/py/INSTALL.md", "research/py/pyproject.toml",
            "research/py/fixtures/java-frozen-v2.json", "research/py/fixtures/java-factor-artifact.json",
            "scripts/release/python_companion.py", "scripts/release/release_source.py",
            "release/build-python-companion.ps1",
        }):
            raise ValueError("FORBIDDEN_COMPANION_PATH: " + path)
    content = source.blobs(repo, [entries[p][1] for p in paths])
    by_path = {p: content[entries[p][1]] for p in paths}
    project = tomllib.loads(by_path["research/py/pyproject.toml"].decode())["project"]
    version = by_path["VERSION"].decode("ascii").strip()
    if (not re.fullmatch(r"\d+\.\d+\.\d+", version) or policy["productVersion"] != version
            or policy["companionVersion"] != version or project["version"] != version
            or project["name"] != "nexus-quant-research" or project["dependencies"] != ["numpy==2.3.5"]):
        raise ValueError("COMPANION_VERSION_OR_DEPENDENCY_MISMATCH")
    records = [{"path": p, "gitBlobId": entries[p][1], "sha256": source.sha(by_path[p]),
                "executable": entries[p][0] == "100755"} for p in paths]
    payload = {"sourceCommit": commit, "sourceTree": source.git(repo, "rev-parse", commit + "^{tree}").decode().strip(),
               "productVersion": version, "companionVersion": version, "manifestSha256": source.sha(raw), "files": records}
    return {**payload, "schemaVersion": 1, "sourceHash": source.sha(source.canonical(payload))}, by_path


def export(repo: Path, commit: str, output: Path) -> dict:
    metadata, content = plan(repo, commit)
    output = output.absolute()
    source.regular_parents(output.parent)
    if output.exists() or output.is_symlink() or repo.resolve() in output.parents:
        raise ValueError("FRESH_EXTERNAL_OUTPUT_REQUIRED")
    output.mkdir(parents=True)
    for record in metadata["files"]:
        dest = output / record["path"]
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(content[record["path"]])
        if os.name != "nt":
            dest.chmod(0o755 if record["executable"] else 0o644)
    (output / IDENTITY).write_bytes(source.canonical(metadata) + b"\n")
    return metadata


def verify(repo: Path, commit: str, output: Path) -> dict:
    expected, _ = plan(repo, commit)
    source.regular_parents(output.absolute())
    found = []
    for path in output.rglob("*"):
        if path.is_symlink() or getattr(path, "is_junction", lambda: False)():
            raise ValueError("COMPANION_SOURCE_LINK")
        if path.is_file():
            found.append(path.relative_to(output).as_posix())
        elif not path.is_dir():
            raise ValueError("COMPANION_SOURCE_SPECIAL_FILE")
    if set(found) != {r["path"] for r in expected["files"]} | {IDENTITY}:
        raise ValueError("COMPANION_SOURCE_FILE_SET_MISMATCH")
    if (output / IDENTITY).read_bytes() != source.canonical(expected) + b"\n":
        raise ValueError("COMPANION_SOURCE_IDENTITY_MISMATCH")
    for record in expected["files"]:
        path = output / record["path"]
        if path.stat().st_size > source.MAX_FILE or source.sha(path.read_bytes()) != record["sha256"]:
            raise ValueError("COMPANION_SOURCE_CONTENT_MISMATCH: " + record["path"])
        if os.name != "nt" and bool(path.stat().st_mode & 0o111) != record["executable"]:
            raise ValueError("COMPANION_SOURCE_MODE_MISMATCH")
    return expected


def wheel_metadata(path: Path) -> email.message.Message:
    with zipfile.ZipFile(path) as archive:
        members = archive.infolist()
        names = [i.filename for i in members if not i.is_dir()]
        source.check_names([i.filename.rstrip("/") for i in members], allow_parent=True)
        source.check_names(names)
        for member in members:
            kind = stat.S_IFMT(member.external_attr >> 16)
            if kind not in ((0, stat.S_IFDIR) if member.is_dir() else (0, stat.S_IFREG)):
                raise ValueError("SPECIAL_WHEEL_MEMBER")
        metadata = [n for n in names if n.endswith(".dist-info/METADATA")]
        if len(metadata) != 1 or len(members) > 10000 or sum(i.file_size for i in members) > MAX_ASSET:
            raise ValueError("INVALID_WHEEL_METADATA")
        return email.message_from_bytes(archive.read(metadata[0]))


def dependencies(lock: dict, wheelhouse: Path) -> list[dict]:
    if set(lock) != {"schemaVersion", "target", "buildTools", "runtime"} or lock["schemaVersion"] != 1:
        raise ValueError("INVALID_DEPENDENCY_LOCK")
    if lock["target"] != {"os": "windows", "architecture": "amd64", "python": "3.11", "implementation": "cpython"}:
        raise ValueError("INVALID_DEPENDENCY_TARGET")
    records = lock["runtime"]
    if len(records) != 1 or records[0]["name"] != "numpy" or records[0]["version"] != "2.3.5":
        raise ValueError("INVALID_RUNTIME_CLOSURE")
    for record in records:
        if set(record) != {"name", "version", "filename", "sha256", "url"}:
            raise ValueError("INVALID_DEPENDENCY_RECORD")
        source.safe_path(record["filename"])
        if "/" in record["filename"] or not record["filename"].endswith("-cp311-cp311-win_amd64.whl"):
            raise ValueError("INVALID_DEPENDENCY_PLATFORM")
        if not re.fullmatch(r"[a-f0-9]{64}", record["sha256"]):
            raise ValueError("INVALID_DEPENDENCY_HASH")
        path = wheelhouse / record["filename"]
        if path.is_symlink() or not path.is_file() or path.stat().st_size > MAX_ASSET or source.sha(path.read_bytes()) != record["sha256"]:
            raise ValueError("DEPENDENCY_HASH_MISMATCH")
        meta = wheel_metadata(path)
        if meta["Name"] != record["name"] or meta["Version"] != record["version"] or meta.get_all("Requires-Dist", []):
            raise ValueError("DEPENDENCY_METADATA_MISMATCH")
    return records


def build(repo: Path, commit: str, exported: Path, wheelhouse: Path, output: Path) -> dict:
    metadata = verify(repo, commit, exported)
    # 构建控制器也绑定源码提交，不能用未提交的脚本制造正式来源声明。
    for name in ("python_companion.py", "release_source.py"):
        if Path(__file__).with_name(name).read_bytes() != (exported / "scripts/release" / name).read_bytes():
            raise ValueError("BUILDER_SOURCE_MISMATCH")
    if sys.version_info[:2] != (3, 11) or sys.platform != "win32" or sys.maxsize <= 2**32:
        raise ValueError("WINDOWS_AMD64_CPYTHON311_BUILDER_REQUIRED")
    lock = read_json((exported / LOCK).read_bytes())
    runtime = dependencies(lock, wheelhouse)
    from importlib.metadata import version
    for name, pinned in lock["buildTools"].items():
        if version(name) != pinned:
            raise ValueError("BUILD_TOOL_VERSION_MISMATCH: " + name)
    output = output.absolute()
    source.regular_parents(output.parent)
    if output.exists() or repo.resolve() in output.parents or exported.absolute() in output.parents:
        raise ValueError("FRESH_EXTERNAL_OUTPUT_REQUIRED")
    output.mkdir(parents=True)
    payload = output / "payload"
    payload.mkdir()
    dest_wheels = payload / "wheelhouse"
    dest_wheels.mkdir()
    with tempfile.TemporaryDirectory(prefix="companion-build-", dir=output) as temporary:
        project = Path(temporary) / "project"
        shutil.copytree(exported / "research/py", project)
        env = {**os.environ, "PIP_NO_INDEX": "1", "PIP_NO_CACHE_DIR": "1", "PIP_DISABLE_PIP_VERSION_CHECK": "1",
               "PYTHONNOUSERSITE": "1", "SOURCE_DATE_EPOCH": "1704067200"}
        result = subprocess.run([sys.executable, "-m", "pip", "--isolated", "wheel", "--no-deps", "--no-build-isolation",
                                 "--no-index", "--no-cache-dir", "--wheel-dir", str(dest_wheels), str(project)],
                                capture_output=True, timeout=180, env=env)
        (output / "wheel-build.log").write_bytes(result.stdout + result.stderr)
        if result.returncode:
            raise ValueError("WHEEL_BUILD_FAILED")
    wheels = list(dest_wheels.glob("*.whl"))
    if len(wheels) != 1:
        raise ValueError("UNEXPECTED_WHEEL_COUNT")
    own = wheels[0]
    info = wheel_metadata(own)
    if (info["Name"] != "nexus-quant-research" or info["Version"] != metadata["companionVersion"]
            or info.get_all("Requires-Dist", []).count("numpy==2.3.5") != 1):
        raise ValueError("BUILT_WHEEL_IDENTITY_MISMATCH")
    for record in runtime:
        shutil.copyfile(wheelhouse / record["filename"], dest_wheels / record["filename"])
    requirements = [f'nexus-quant-research=={metadata["companionVersion"]} --hash=sha256:{source.sha(own.read_bytes())}']
    requirements.extend(f'{r["name"]}=={r["version"]} --hash=sha256:{r["sha256"]}' for r in runtime)
    (payload / "requirements.lock").write_text("\n".join(requirements) + "\n", encoding="utf-8", newline="\n")
    for name in ("LICENSE", "DISCLAIMER.md", "THIRD_PARTY_NOTICES.md"):
        shutil.copyfile(exported / name, payload / name)
    shutil.copyfile(exported / "research/py/INSTALL.md", payload / "INSTALL.md")
    shutil.copyfile(exported / LOCK, payload / "dependencies.json")
    shutil.copyfile(exported / IDENTITY, payload / "source.json")
    # 原始投影随资产保留，用户不需要 checkout 开发分支或联网解析构建依赖。
    shutil.copytree(exported, payload / "source")
    mappings = {"frozenExport": "frozen-research-export.v1", "artifact": "python-factor-research-artifact.v1",
                "implementation": "factor-research-1.0.0", "factorVersion": "1.0.0",
                "method": "PURGED_CHRONOLOGICAL_PEARSON_MOVING_BLOCK_V1"}
    inventory = [{"path": p.relative_to(payload).as_posix(), "sha256": source.sha(p.read_bytes()), "bytes": p.stat().st_size}
                 for p in sorted(payload.rglob("*")) if p.is_file()]
    manifest = {"schemaVersion": 1, "productVersion": metadata["productVersion"], "companionVersion": metadata["companionVersion"],
                "sourceCommit": commit, "sourceTree": metadata["sourceTree"], "sourceHash": metadata["sourceHash"],
                "sourceManifestSha256": metadata["manifestSha256"], "pythonArtifactSha256": source.sha(own.read_bytes()),
                "pythonArtifact": own.name, "dependencyManifestSha256": source.sha((exported / LOCK).read_bytes()),
                "target": lock["target"], "contracts": mappings, "files": inventory,
                "provenance": "UNSIGNED_RELEASE_SOURCE_MAPPING", "qualification": "NOT_RUN"}
    (payload / "companion-manifest.json").write_bytes(source.canonical(manifest) + b"\n")
    checksum_lines = [f'{source.sha(p.read_bytes())}  {p.relative_to(payload).as_posix()}'
                      for p in sorted(payload.rglob("*")) if p.is_file()]
    (payload / "SHA256SUMS.txt").write_text("\n".join(checksum_lines) + "\n", encoding="utf-8", newline="\n")
    archive = output / f'NexusQuant-Python-Research-Companion-{metadata["productVersion"]}-windows-amd64-py311.zip'
    with zipfile.ZipFile(archive, "x", zipfile.ZIP_DEFLATED) as zipped:
        for path in sorted(payload.rglob("*")):
            if path.is_file():
                zi = zipfile.ZipInfo(path.relative_to(payload).as_posix(), (2024, 1, 1, 0, 0, 0))
                zi.external_attr = 0o100644 << 16
                zipped.writestr(zi, path.read_bytes(), compress_type=zipfile.ZIP_DEFLATED)
    archive_hash = source.sha(archive.read_bytes())
    archive.with_suffix(".zip.sha256").write_text(archive_hash + "  " + archive.name + "\n", encoding="ascii")
    return {"sourceCommit": commit, "productVersion": metadata["productVersion"], "companionVersion": metadata["companionVersion"],
            "sourceHash": metadata["sourceHash"], "pythonArtifactSha256": manifest["pythonArtifactSha256"],
            "manifestSha256": source.sha((payload / "companion-manifest.json").read_bytes()),
            "archive": str(archive), "archiveSha256": archive_hash, "bytes": archive.stat().st_size}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("export", "verify", "build"))
    parser.add_argument("--repo", type=Path, required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--source", type=Path)
    parser.add_argument("--wheelhouse", type=Path)
    args = parser.parse_args()
    try:
        if args.action == "build":
            if args.source is None or args.wheelhouse is None:
                parser.error("build requires --source and --wheelhouse")
            result = build(args.repo, args.commit, args.source, args.wheelhouse, args.output)
        else:
            result = (export if args.action == "export" else verify)(args.repo, args.commit, args.output)
            result = {k: v for k, v in result.items() if k != "files"}
        print(json.dumps(result, sort_keys=True))
        return 0
    except (OSError, ValueError, KeyError, TypeError, subprocess.SubprocessError) as exc:
        print("COMPANION_FAILED_CLOSED: " + type(exc).__name__ + ": " + str(exc), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
