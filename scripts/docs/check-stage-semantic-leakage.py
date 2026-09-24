"""Reject development-stage wording in long-lived source and current facts."""

from __future__ import annotations

import hashlib
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
POLICY = ROOT / "scripts/docs/stage-semantic-allowlist.json"
DOCS = (
    "README.md",
    "docs/current/README.md",
    "docs/current/ARCHITECTURE.md",
    "docs/current/API.md",
    "docs/current/DB_SCHEMA.md",
    "docs/current/RUNBOOK.md",
    "docs/current/MODULES.md",
)
SUFFIXES = {".java", ".ts", ".tsx", ".js", ".css", ".py", ".md", ".json"}
START = "<!-- nq-stage-history:start -->"
END = "<!-- nq-stage-history:end -->"
STAGE = re.compile(
    r"(?<![A-Za-z0-9_])(?:Gate(?:AUDIT|[A-Z](?:[-_]?\d+[A-Z]?)?)|gate[-_][a-z](?:[-_]?\d+[a-z]?)?)(?![a-z])"
    r"|(?<![A-Za-z0-9_-])(?:PB|B|C|L)\d+(?:[-_.][A-Z0-9]+)?(?![A-Za-z0-9])"
    r"|(?<![A-Za-z0-9])(?:PRE-CLEAN(?:-\d+[A-Z]?)?|Phase\d+[A-Z]?(?:-[A-Z])?|Stage-QDR[-_A-Za-z0-9]*|QDR[-_A-Za-z0-9]+|Attempt[-_]\d+|Review[-_]\d+|REV\d+|PRETAG|PRE-TAG|FREEZE)(?![A-Za-z0-9])"
    r"|本轮|本阶段|当前阶段|下一阶段|后续阶段|这一轮|本批次|第二批|第三批|第[一二三四五六七八九十0-9]+批"
    r"|(?i:\bthis gate\b|\bthis phase\b|\bcurrent phase\b|\bnext phase\b|\bthis batch\b)",
)
ALLOWED_CATEGORIES = {
    "BUSINESS_DOMAIN_TERM", "RUNTIME_COMPATIBILITY_IDENTIFIER",
    "TEST_QUALIFICATION_IDENTIFIER", "HISTORICAL_REFERENCE",
}


def digest(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def sources(root: Path) -> list[Path]:
    result: list[Path] = []
    for module in (root / "backend").iterdir():
        source = module / "src/main/java"
        if source.is_dir():
            result.extend(p for p in source.rglob("*.java") if p.is_file())
    for source in (root / "frontend/src", root / "research/py/src"):
        if source.is_dir():
            result.extend(p for p in source.rglob("*") if p.is_file() and p.suffix in SUFFIXES)
    result.extend(root / rel for rel in DOCS if (root / rel).is_file())
    return sorted(set(result))


def read_policy(path: Path) -> tuple[dict[tuple[str, str, str], dict], dict[tuple[str, int], dict]]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if set(data) != {"schemaVersion", "exceptions", "historyRegions"} or data["schemaVersion"] != 1:
        raise ValueError("INVALID_STAGE_SEMANTIC_POLICY")
    exceptions = {}
    for entry in data["exceptions"]:
        if set(entry) != {"path", "token", "category", "reason", "lineSha256"}:
            raise ValueError("INVALID_EXCEPTION_FIELDS")
        if entry["category"] not in ALLOWED_CATEGORIES or len(entry["reason"].strip()) < 20:
            raise ValueError("INVALID_EXCEPTION_REASON")
        key = (entry["path"], entry["token"], entry["lineSha256"])
        if key in exceptions:
            raise ValueError("DUPLICATE_EXCEPTION")
        exceptions[key] = entry
    history = {}
    for entry in data["historyRegions"]:
        if set(entry) != {"path", "token", "regionIndex", "category", "reason", "sha256"}:
            raise ValueError("INVALID_HISTORY_FIELDS")
        if entry["token"] != "nq-stage-history" or entry["category"] != "HISTORICAL_REFERENCE" or len(entry["reason"].strip()) < 20:
            raise ValueError("INVALID_HISTORY_REASON")
        if entry["path"] not in DOCS or not isinstance(entry["regionIndex"], int) or entry["regionIndex"] < 1:
            raise ValueError("INVALID_HISTORY_PATH")
        key = (entry["path"], entry["regionIndex"])
        if key in history:
            raise ValueError("DUPLICATE_HISTORY")
        history[key] = entry
    return exceptions, history


def check(root: Path = ROOT, policy_path: Path = POLICY) -> tuple[list[str], dict[str, int]]:
    exceptions, history = read_policy(policy_path)
    used_exceptions: set[tuple[str, str, str]] = set()
    used_history: set[tuple[str, int]] = set()
    errors: list[str] = []
    scanned = 0
    for path in sources(root):
        rel = path.relative_to(root).as_posix()
        raw = path.read_bytes()
        if len(raw) > 4_000_000 or b"\0" in raw:
            errors.append("UNSCANNABLE_SOURCE: " + rel)
            continue
        lines = raw.decode("utf-8-sig").splitlines()
        scanned += 1
        active = False
        region_index = 0
        region_lines: list[str] = []
        for number, line in enumerate(lines, 1):
            if line.strip() == START:
                if rel not in DOCS or active:
                    errors.append(f"INVALID_HISTORY_START: {rel}:{number}")
                active = True
                region_index += 1
                region_lines = []
                continue
            if line.strip() == END:
                if not active:
                    errors.append(f"UNPAIRED_HISTORY_END: {rel}:{number}")
                else:
                    key = (rel, region_index)
                    entry = history.get(key)
                    if entry is None or entry["sha256"] != digest("\n".join(region_lines)):
                        errors.append(f"UNREVIEWED_HISTORY_REGION: {rel}:{region_index}")
                    else:
                        used_history.add(key)
                active = False
                region_lines = []
                continue
            if active:
                region_lines.append(line)
                continue
            # Markdown link targets identify immutable migrations and archives, not prose.
            checked = re.sub(r"\]\([^)]*\)", "](link)", line) if rel in DOCS else line
            for match in STAGE.finditer(checked):
                token = match.group()
                key = (rel, token, digest(line))
                if key in exceptions:
                    used_exceptions.add(key)
                else:
                    errors.append(f"STAGE_LEAKAGE: {rel}:{number}:{token}")
        if active:
            errors.append("UNPAIRED_HISTORY_START: " + rel)
    errors.extend("STALE_EXCEPTION: " + key[0] + ":" + key[1]
                  for key in sorted(set(exceptions) - used_exceptions))
    errors.extend("STALE_HISTORY_REGION: " + key[0] + ":" + str(key[1])
                  for key in sorted(set(history) - used_history))
    return errors, {"scanned": scanned, "retained": len(used_exceptions), "historyRegions": len(used_history)}


def main() -> int:
    try:
        errors, stats = check()
        for error in errors:
            print(error)
        print("STAGE_SEMANTIC_GUARD " + " ".join(f"{key}={value}" for key, value in stats.items())
              + f" errors={len(errors)}")
        return 1 if errors else 0
    except (OSError, ValueError, UnicodeError, TypeError, KeyError) as exc:
        print("STAGE_SEMANTIC_GUARD FAILED_CLOSED: " + type(exc).__name__, file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
