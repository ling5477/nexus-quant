"""从明确 Git 身份与逐文件清单导出发布源码；不读取工作区源码。"""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import unicodedata

MANIFEST = "release/source-manifest.json"
METADATA = ".release-source.json"
INCLUDES = {"INCLUDE_RUNTIME", "INCLUDE_BUILD_REQUIRED", "INCLUDE_RELEASE_DOC", "INCLUDE_RELEASE_OPERATIONS"}
EXCLUDES = {"EXCLUDE_TEST", "EXCLUDE_DEV_ONLY", "EXCLUDE_HISTORY", "EXCLUDE_GOVERNANCE",
            "EXCLUDE_AGENT_TOOLING", "EXCLUDE_EVIDENCE", "EXCLUDE_CI_ONLY", "REVIEW_REQUIRED"}
MAX_FILE = 32 * 1024 * 1024
MAX_TOTAL = 512 * 1024 * 1024


def sha(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def canonical(value: object) -> bytes:
    # 固定 UTF-8、键排序与紧凑分隔符；不含时间戳或平台换行。
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode("utf-8")


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("DUPLICATE_JSON_KEY")
        result[key] = value
    return result


def git(repo: Path, *args: str, data: bytes | None = None) -> bytes:
    return subprocess.run(["git", "--no-replace-objects", "-C", str(repo), *args], input=data, capture_output=True,
                          timeout=120, check=True).stdout


def safe_path(path: str) -> str:
    if (not isinstance(path, str) or not path or len(path) > 240 or "\\" in path
            or unicodedata.normalize("NFC", path) != path):
        raise ValueError("UNSAFE_PATH")
    for part in path.split("/"):
        if (not part or part in {".", ".."} or part.endswith((" ", "."))
                or any(ord(c) < 32 or c in '<>:"|?*' for c in part)
                or part.casefold() == ".git"
                or re.fullmatch(r"(?i:CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\..*)?", part)):
            raise ValueError("UNSAFE_PATH: " + path)
    return path


def check_names(paths: list[str], *, allow_parent: bool = False) -> None:
    seen = set()
    nodes = {}
    files = set(paths)
    for path in paths:
        safe_path(path)
        if path in seen:
            raise ValueError("DUPLICATE_PATH: " + path)
        seen.add(path)
        parts = path.split("/")
        for i in range(1, len(parts) + 1):
            node = "/".join(parts[:i])
            if nodes.setdefault(node.casefold(), node) != node:
                raise ValueError("CASE_COLLISION: " + path)
            if not allow_parent and i < len(parts) and node in files:
                raise ValueError("FILE_DIRECTORY_COLLISION: " + path)


def tree(repo: Path, commit: str) -> dict[str, tuple[str, str]]:
    if not re.fullmatch("[0-9a-f]{40}", commit):
        raise ValueError("EXACT_COMMIT_REQUIRED")
    if git(repo, "cat-file", "-t", commit).strip() != b"commit":
        raise ValueError("COMMIT_OBJECT_REQUIRED")
    raw = git(repo, "ls-tree", "-rz", "--full-tree", commit)
    records = raw.rstrip(b"\0").split(b"\0")
    if len(raw) > 16 * 1024 * 1024 or len(records) > 50000:
        raise ValueError("TREE_LIMIT")
    result, paths = {}, []
    for record in records:
        header, name = record.split(b"\t", 1)
        mode, kind, oid = header.decode("ascii").split()
        path = name.decode("utf-8")
        paths.append(path)
        if kind != "blob" or mode not in {"100644", "100755"}:
            raise ValueError("SPECIAL_GIT_FILE: " + path)
        result[path] = (mode, oid)
    check_names(paths)
    return result


def blobs(repo: Path, ids: list[str]) -> dict[str, bytes]:
    ids = sorted(set(ids))
    request = ("\n".join(ids) + "\n").encode("ascii")
    sizes = git(repo, "cat-file", "--batch-check=%(objectname) %(objecttype) %(objectsize)", data=request).splitlines()
    expected = {}
    for oid, line in zip(ids, sizes, strict=True):
        returned, kind, size = line.decode("ascii").split()
        if returned != oid or kind != "blob" or not 0 <= int(size) <= MAX_FILE:
            raise ValueError("BLOB_LIMIT_OR_TYPE")
        expected[oid] = int(size)
    if sum(expected.values()) > MAX_TOTAL:
        raise ValueError("TOTAL_SIZE_LIMIT")
    raw = git(repo, "cat-file", "--batch", data=request)
    offset, result = 0, {}
    for oid in ids:
        end = raw.index(b"\n", offset)
        if raw[offset:end] != f"{oid} blob {expected[oid]}".encode("ascii"):
            raise ValueError("BLOB_HEADER_MISMATCH")
        offset = end + 1
        content = raw[offset:offset + expected[oid]]
        offset += expected[oid]
        if raw[offset:offset + 1] != b"\n":
            raise ValueError("BLOB_LENGTH_MISMATCH")
        offset += 1
        identity = hashlib.sha1(f"blob {len(content)}\0".encode("ascii") + content).hexdigest()
        if identity != oid:
            raise ValueError("BLOB_ID_MISMATCH")
        result[oid] = content
    if offset != len(raw):
        raise ValueError("UNEXPECTED_BATCH_DATA")
    return result


def forbidden(path: str, exceptions: set[str]) -> bool:
    parts = path.casefold().split("/")
    bad = {".agents", ".github", ".git", "agents.md", "claude.md", "codeowners", "tests", "test",
           "target", "dist", "node_modules", "coverage", "evidence", "artifacts"}
    return (any(part in bad for part in parts)
            or path.casefold().startswith(("docs/current/", "docs/gates/", "docs/audit/", "docs/archive/",
                                           "scripts/docs/", "scripts/ci/", "scripts/java-standard/"))
            or "playwright" in path.casefold() or ("fixtures" in parts and path not in exceptions)
            or path.endswith((".class", ".tsbuildinfo")) or path == METADATA)


def plan(repo: Path, commit: str) -> tuple[dict, dict[str, bytes], dict]:
    entries = tree(repo, commit)
    if MANIFEST not in entries:
        raise ValueError("MANIFEST_NOT_IN_SOURCE_COMMIT")
    raw = blobs(repo, [entries[MANIFEST][1]])[entries[MANIFEST][1]]
    policy = json.loads(raw.decode("utf-8"), object_pairs_hook=unique_object)
    fields = {"schemaVersion", "targetVersion", "sourceIdentity", "treeHashAlgorithm", "groups",
              "excludedPaths", "resourceExceptions", "forbiddenCategories", "requiredFinalAssets"}
    if (set(policy) != fields or policy["schemaVersion"] != 1 or policy["targetVersion"] != "1.0.0"
            or policy["sourceIdentity"] != "exact-40-hex-git-commit;git-blobs-only"
            or policy["treeHashAlgorithm"] != "sha256-canonical-json-v1"
            or set(policy["forbiddenCategories"]) != EXCLUDES):
        raise ValueError("INVALID_MANIFEST")
    included, classifications = [], {}
    for group in policy["groups"]:
        if set(group) != {"category", "purpose", "paths"} or group["category"] not in INCLUDES or not group["purpose"]:
            raise ValueError("INVALID_INCLUDE_GROUP")
        for path in group["paths"]:
            included.append(path)
            classifications[path] = group["category"]
    check_names(included)
    if not included:
        raise ValueError("EMPTY_RELEASE_SOURCE")
    excluded = []
    for category, paths in policy["excludedPaths"].items():
        if category not in EXCLUDES:
            raise ValueError("INVALID_EXCLUDE_CATEGORY")
        for path in paths:
            excluded.append(path)
            if path in classifications:
                raise ValueError("CONFLICTING_CLASSIFICATION")
            classifications[path] = category
    check_names(excluded)
    exceptions = policy["resourceExceptions"]
    if any(set(e) != {"path", "consumer", "reason"} or e["path"] not in included
           or e["consumer"] not in included or not e["reason"] for e in exceptions):
        raise ValueError("INVALID_RESOURCE_EXCEPTION")
    exception_paths = {e["path"] for e in exceptions}
    for path in included:
        if path not in entries or forbidden(path, exception_paths):
            raise ValueError("MISSING_OR_FORBIDDEN_INCLUDE: " + path)
    if any(path not in entries for path in excluded):
        raise ValueError("STALE_EXCLUDED_PATH")
    content = blobs(repo, [entries[path][1] for path in included])
    files = [{"path": path, "gitBlobId": entries[path][1], "sha256": sha(content[entries[path][1]]),
              "executable": entries[path][0] == "100755"} for path in sorted(included)]
    payload = {"sourceCommit": commit, "targetVersion": policy["targetVersion"],
               "manifestSha256": sha(raw), "files": files}
    metadata = {**payload, "schemaVersion": 1, "fileCount": len(files),
                "releaseTreeSha256": sha(canonical(payload)), "treeHashAlgorithm": policy["treeHashAlgorithm"]}
    # 未审查的新 tracked 文件始终排除；必须显式加入清单才能发布。
    inventory = {path: classifications.get(path, "REVIEW_REQUIRED") for path in sorted(entries)}
    audit = {"sourceCommit": commit, "devTrackedFiles": len(entries), "unclassified": 0,
             "counts": dict(sorted(Counter(inventory.values()).items())), "paths": inventory,
             "requiredFinalAssets": policy["requiredFinalAssets"]}
    return metadata, content, audit


def regular_parents(path: Path) -> None:
    if ".." in path.parts:
        raise ValueError("UNSAFE_OUTPUT_TRAVERSAL")
    for parent in [path, *path.parents]:
        if parent.exists() or parent.is_symlink():
            mode = parent.lstat().st_mode
            if stat.S_ISLNK(mode) or not stat.S_ISDIR(mode) or getattr(parent, "is_junction", lambda: False)():
                raise ValueError("UNSAFE_OUTPUT_PARENT")


def export(repo: Path, commit: str, output: Path) -> tuple[dict, dict]:
    metadata, content, audit = plan(repo, commit)
    output = output.absolute()
    regular_parents(output.parent)
    if output.exists() or output.is_symlink():
        raise ValueError("OUTPUT_MUST_NOT_EXIST")
    if output == repo.resolve() or repo.resolve() in output.parents:
        raise ValueError("OUTPUT_MUST_BE_OUTSIDE_REPOSITORY")
    output.mkdir(parents=True)
    for entry in metadata["files"]:
        dest = output / entry["path"]
        dest.parent.mkdir(parents=True, exist_ok=True)
        with dest.open("xb") as stream:
            stream.write(content[entry["gitBlobId"]])
        if os.name != "nt":
            dest.chmod(0o755 if entry["executable"] else 0o644)
    (output / METADATA).write_bytes(canonical(metadata) + b"\n")
    return metadata, audit


def verify(repo: Path, commit: str, output: Path) -> tuple[dict, dict]:
    metadata, _, audit = plan(repo, commit)
    output = output.absolute()
    regular_parents(output)
    found, found_dirs = [], []
    for base, dirs, names in os.walk(output, followlinks=False):
        for name in dirs + names:
            item = Path(base) / name
            if name in dirs:
                found_dirs.append(item.relative_to(output).as_posix())
            mode = item.lstat().st_mode
            if stat.S_ISLNK(mode) or getattr(item, "is_junction", lambda: False)():
                raise ValueError("SPECIAL_OUTPUT_FILE")
            if name in names:
                if not stat.S_ISREG(mode):
                    raise ValueError("SPECIAL_OUTPUT_FILE")
                found.append(item.relative_to(output).as_posix())
    check_names(found)
    expected_dirs = {str(parent).replace("\\", "/") for entry in metadata["files"]
                     for parent in Path(entry["path"]).parents if str(parent) != "."}
    check_names(found_dirs, allow_parent=True)
    if set(found_dirs) != expected_dirs:
        raise ValueError("OUTPUT_DIRECTORY_SET_MISMATCH")
    if sorted(found) != sorted([e["path"] for e in metadata["files"]] + [METADATA]):
        raise ValueError("OUTPUT_FILE_SET_MISMATCH")
    if (output / METADATA).read_bytes() != canonical(metadata) + b"\n":
        raise ValueError("OUTPUT_METADATA_MISMATCH")
    for entry in metadata["files"]:
        path = output / entry["path"]
        if path.stat().st_size > MAX_FILE or sha(path.read_bytes()) != entry["sha256"]:
            raise ValueError("OUTPUT_CONTENT_MISMATCH: " + entry["path"])
        if os.name != "nt" and bool(path.stat().st_mode & 0o111) != entry["executable"]:
            raise ValueError("OUTPUT_EXECUTABLE_MISMATCH")
    return metadata, audit


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("export", "verify"))
    parser.add_argument("--repo", type=Path, required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--audit", type=Path)
    args = parser.parse_args()
    try:
        if args.audit:
            dest = args.audit.absolute()
            if dest == args.output.absolute() or args.output.absolute() in dest.parents or args.repo.resolve() in dest.parents:
                raise ValueError("UNSAFE_AUDIT_DESTINATION")
        metadata, audit = (export if args.action == "export" else verify)(args.repo, args.commit, args.output)
        if args.audit:
            with args.audit.open("xb") as stream:
                stream.write(canonical(audit) + b"\n")
        print(json.dumps({k: v for k, v in metadata.items() if k != "files"}, sort_keys=True))
        return 0
    except (OSError, ValueError, TypeError, KeyError, UnicodeError, subprocess.SubprocessError) as exc:
        print(f"RELEASE_SOURCE_FAILED_CLOSED: {type(exc).__name__}: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
