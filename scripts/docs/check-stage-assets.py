"""Reject historical stage identities in active assets; immutable evidence is outside this contract.

The explicit, content-pinned exceptions are reviewed compatibility/test contracts, not
an automatically learned baseline. There is deliberately no update/accept switch.
"""
from __future__ import annotations

import argparse
from functools import lru_cache
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import subprocess

STAGE = re.compile(
    r"(?<![A-Za-z])(?i:gate)[-_ ]?(?:[A-Za-z])(?=$|[^A-Za-z]|[A-Z][a-z])"
    r"|(?<![A-Za-z])(?i:phase)[-_ ]?[0-9]+[A-Za-z]?"
    r"|(?i:\bfreeze(?:[-_][A-Za-z0-9]+)?)"
)
ROOTS = ("scripts", "deploy", ".github", "backend", ".agents/skills", "docs/current")
ROOT_FILES = ("AGENTS.md", "CLAUDE.md", "README.md")
GENERATED = {"target", "node_modules", "build", "dist", "coverage", "__pycache__", ".git"}
HISTORICAL = ("docs/current/evidence/", "docs/gates/", "docs/archive/", "docs/audit/evidence/")
HISTORICAL_FILES = {
    "docs/current/WORKLOG.md", "docs/current/TESTING.md",
    "docs/current/GATEW_PLAN.md", "docs/current/GATEV_PLAN.md",
    "docs/current/NQ_DOCS_ARCHIVE_RULE_HARDENING_AND_RESIDUAL_MOVE_PLAN.md",
}
EXTENSIONS = {".java", ".py", ".ps1", ".psm1", ".sh", ".yml", ".yaml", ".json",
              ".xml", ".service", ".conf", ".properties", ".md", ".toml", ".js", ".mjs", ".cjs", ".sql"}
EXECUTABLE_EXTENSIONS = {".ps1", ".psm1", ".sh", ".py", ".js", ".mjs", ".cjs", ".java"}
CONTROL_PLANE_ROOTS = ("scripts/", "deploy/", ".github/")
JAVA_REFERENCES = "scripts/docs/JavaCompatibilityReferences.java"
SENSITIVE = re.compile(
    r"(?i)(^|/)(?:\.env[^/]*|secrets?|credentials?|cookies?|tokens?)(?:/|$)"
    r"|\.(?:key|pem|p12|jks|keystore)$|\.env(?:\.|$)|pgpass"
)
POLICY_PATH = "scripts/docs/stage-asset-exceptions.json"
SELF_FILES = {"scripts/docs/check-stage-assets.py", "scripts/docs/tests/test_stage_assets.py", POLICY_PATH, JAVA_REFERENCES}
JAVA_LEXEMES = re.compile(r'""".*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|/\*.*?\*/|//[^\n]*', re.S)
KINDS = {"NEGATIVE_REGRESSION", "FIXTURE_IDENTITY", "WIRE_COMPATIBILITY", "HISTORICAL_METADATA",
         "GOVERNANCE_CONTRACT", "DOMAIN_VOCABULARY", "RETIRED_INPUT_REJECTION"}


def has_stage(text: str) -> bool:
    # "docs/gates" is an archive namespace, not the identifier of the S stage.
    normalized = text.replace("\\\\", "/").replace("\\", "/")
    return any(not (m.group() == "gates" and normalized[max(0, m.start() - 5):m.start()] == "docs/"
                       and (not normalized[m.end():m.end() + 1]
                            or normalized[m.end():m.end() + 1] in "/'\" )]"))
               for m in STAGE.finditer(normalized))


def historical(path: str) -> bool:
    return path.startswith(HISTORICAL) or path in HISTORICAL_FILES or "/db/migration/" in path


def sources(root: Path) -> list[str]:
    """Walk active filesystem namespaces, including untracked/ignored runtime inputs."""
    result = []
    for namespace in ROOTS:
        directory = root / namespace
        if not directory.is_dir() or directory.is_symlink():
            raise ValueError("ACTIVE_ROOT_MISSING_OR_LINKED: " + namespace)
        for parent, dirs, files in os.walk(directory, followlinks=False):
            relative_parent = Path(parent).relative_to(root).as_posix()
            dirs[:] = [d for d in dirs if d not in GENERATED and not historical(relative_parent + "/" + d + "/")]
            for name in dirs + files:
                if (Path(parent) / name).is_symlink():
                    raise ValueError("ACTIVE_SYMLINK: " + relative_parent + "/" + name)
            for name in files:
                path = (Path(parent) / name).relative_to(root).as_posix()
                if not historical(path):
                    result.append(path)
    for path in ROOT_FILES:
        if not (root / path).is_file() or (root / path).is_symlink():
            raise ValueError("ACTIVE_ROOT_FILE_MISSING_OR_LINKED: " + path)
        result.append(path)
    return sorted(set(result))


@lru_cache(maxsize=4)
def retired_caller_pattern(retired_paths: tuple[str, ...]) -> re.Pattern | None:
    """Compile the fixed retirement registry once, rather than once per source file."""
    names = set(retired_paths)
    for retired in retired_paths:
        if Path(retired).suffix in {".ps1", ".psm1", ".sh", ".service", ".yml", ".java"}:
            names.add(Path(retired).name)
        if Path(retired).suffix == ".java":
            names.add(Path(retired).stem)
    if not names:
        return None
    return re.compile(r"(?<![A-Za-z0-9_])(?:" + "|".join(re.escape(name) for name in sorted(names))
                      + r")(?![A-Za-z0-9_])")


def inspect(root: Path, path: str, retired_paths: tuple[str, ...] = ()) -> dict | None:
    # Stage-specific executable/configuration filenames can never be grandfathered.
    if path in retired_paths:
        return {"path": path, "kind": "RETIRED_ASSET_PATH", "sha256": ""}
    if path not in SELF_FILES and has_stage(path):
        return {"path": path, "kind": "STAGE_ASSET_PATH", "sha256": ""}
    if path in SELF_FILES or historical(path) or SENSITIVE.search(path) or Path(path).suffix.lower() not in EXTENSIONS:
        return None
    source = root / path
    if source.stat().st_size > 4_000_000:
        raise ValueError("SOURCE_TOO_LARGE: " + path)
    text = source.read_text(encoding="utf-8-sig")
    if Path(path).suffix == ".java":
        text = JAVA_LEXEMES.sub(lambda m: "" if m.group().startswith(("//", "/*")) else m.group(), text)
        # Active Spring selectors cannot be reclassified by an exception or a content hash.
        spring_semantics = re.sub(r'\\u+([0-9a-fA-F]{4})', lambda m: chr(int(m.group(1), 16)), text)
        if any(has_stage(m.group()) for m in re.finditer(r'@(?:[\w.]+\.)?Profile\s*\([^)]*\)', spring_semantics)):
            return {"path": path, "kind": "ACTIVE_SPRING_STAGE_SELECTOR", "sha256": ""}
    elif Path(path).suffix in {".ps1", ".psm1", ".py", ".sh", ".yml", ".yaml"}:
        text = re.sub(r"(?m)^\s*#.*$", "", text)
    elif Path(path).suffix == ".md":
        # Narrative acceptance/history references are legal. Commands and runtime paths are not.
        text = "\n".join(line for line in text.splitlines() if re.search(
            r"(?:scripts|deploy)/\S+|spring\.profiles|\b(?:pwsh|powershell|bash|systemctl|mvn|docker|java)\s", line))
    normalized_paths = text.replace("\\\\", "/").replace("\\", "/")
    retired_pattern = retired_caller_pattern(retired_paths)
    if retired_pattern is not None and retired_pattern.search(normalized_paths):
        return {"path": path, "kind": "RETIRED_ASSET_CALLER", "sha256": ""}
    lines = [line.strip() for line in text.splitlines() if has_stage(line)]
    if not lines:
        return None
    # Hash every non-comment source line, not just the matching token: an allowed fixture
    # cannot gain an executable caller while preserving its old literal.
    normalized = text.replace("\r\n", "\n").strip()
    return {"path": path, "kind": "STAGE_SEMANTICS", "sha256": hashlib.sha256(normalized.encode()).hexdigest(),
            "matches": len(lines)}


def load_policy(root: Path) -> tuple[dict[str, dict], tuple[str, ...], list[dict], list[dict]]:
    policy = json.loads((root / POLICY_PATH).read_text(encoding="utf-8"))
    if set(policy) != {"schemaVersion", "exceptions", "retiredPaths", "compatibilityContracts", "safeControlPlaneInputs"} or policy["schemaVersion"] != 2 or not isinstance(policy["exceptions"], list):
        raise ValueError("INVALID_EXCEPTION_SCHEMA")
    retired = policy["retiredPaths"]
    if not isinstance(retired, list) or any(not isinstance(p, str) or not p or Path(p).is_absolute()
            or ".." in Path(p).parts or "\\" in p for p in retired) or len(retired) != len(set(retired)):
        raise ValueError("INVALID_RETIRED_PATHS")
    entries = {}
    for entry in policy["exceptions"]:
        if not isinstance(entry, dict) or set(entry) != {"path", "sha256", "kind", "reason", "owner", "removalTrigger"}:
            raise ValueError("INVALID_EXCEPTION_FIELDS")
        path = entry["path"]
        if not isinstance(path, str) or path in entries or Path(path).is_absolute() or ".." in Path(path).parts or "\\" in path:
            raise ValueError("INVALID_EXCEPTION_PATH")
        if entry["kind"] not in KINDS or not re.fullmatch(r"[0-9a-f]{64}", entry["sha256"]):
            raise ValueError("INVALID_EXCEPTION_IDENTITY")
        if any(not isinstance(entry[key], str) or not entry[key].strip() for key in ("reason", "owner", "removalTrigger")):
            raise ValueError("MISSING_EXCEPTION_JUSTIFICATION")
        entries[path] = entry
    contracts = policy["compatibilityContracts"]
    if not isinstance(contracts, list):
        raise ValueError("INVALID_COMPATIBILITY_CONTRACTS")
    identities = set()
    for contract in contracts:
        if not isinstance(contract, dict) or set(contract) != {"identity", "path", "canonicalOwner", "migrationTrigger", "removalCondition", "members", "approvedCallers"}:
            raise ValueError("INVALID_COMPATIBILITY_FIELDS")
        for key in ("identity", "path", "canonicalOwner", "migrationTrigger", "removalCondition"):
            if not isinstance(contract[key], str) or not contract[key].strip():
                raise ValueError("MISSING_COMPATIBILITY_IDENTITY_OR_OWNER")
        if contract["identity"] in identities or not safe_path(contract["path"]) or not contract["path"].endswith(".java"):
            raise ValueError("INVALID_COMPATIBILITY_IDENTITY")
        identities.add(contract["identity"])
        if not (root / contract["path"]).is_file():
            raise ValueError("MISSING_COMPATIBILITY_SOURCE")
        source = (root / contract["path"]).read_text(encoding="utf-8-sig")
        package = re.search(r'(?m)^package\s+([\w.]+)\s*;', source)
        if not package or contract["identity"] != package.group(1) + "." + Path(contract["path"]).stem:
            raise ValueError("COMPATIBILITY_SOURCE_IDENTITY_MISMATCH")
        if not isinstance(contract["members"], list) or not contract["members"] or len(set(contract["members"])) != len(contract["members"]):
            raise ValueError("INVALID_COMPATIBILITY_MEMBERS")
        if any(not isinstance(m, str) or not re.fullmatch(r'[A-Za-z_$][\w$]*', m) for m in contract["members"]):
            raise ValueError("INVALID_COMPATIBILITY_MEMBER")
        if set(contract["members"]) != declared_compatibility_members(source):
            raise ValueError("COMPATIBILITY_MEMBER_COVERAGE_MISMATCH")
        seen = set()
        if not isinstance(contract["approvedCallers"], list):
            raise ValueError("INVALID_APPROVED_CALLERS")
        for edge in contract["approvedCallers"]:
            if not isinstance(edge, dict) or set(edge) != {"path", "callerMember", "contractMember"}:
                raise ValueError("INVALID_APPROVED_CALLER_FIELDS")
            if not safe_path(edge["path"]) or not edge["path"].endswith(".java") or historical(edge["path"]):
                raise ValueError("INVALID_APPROVED_CALLER_PATH")
            if not isinstance(edge["callerMember"], str) or not edge["callerMember"] or any(c in edge["callerMember"] for c in '*?\n\r\t'):
                raise ValueError("INVALID_APPROVED_CALLER_MEMBER")
            if edge["contractMember"] not in contract["members"] + ["<typed-use>"]:
                raise ValueError("INVALID_APPROVED_CONTRACT_MEMBER")
            key = tuple(edge[k] for k in ("path", "callerMember", "contractMember"))
            if key in seen:
                raise ValueError("DUPLICATE_APPROVED_CALLER")
            seen.add(key)
    if {c["path"] for c in contracts} != {p for p, e in entries.items() if e["kind"] == "WIRE_COMPATIBILITY"}:
        raise ValueError("COMPATIBILITY_EXCEPTION_COVERAGE_MISMATCH")
    safe_inputs = policy["safeControlPlaneInputs"]
    if not isinstance(safe_inputs, list):
        raise ValueError("INVALID_SAFE_INPUTS")
    seen = set()
    for item in safe_inputs:
        if not isinstance(item, dict) or set(item) != {"path", "sha256", "reason"} or not safe_path(item["path"]) or item["path"] in seen:
            raise ValueError("INVALID_SAFE_INPUT")
        if not re.fullmatch('[0-9a-f]{64}', item["sha256"]) or not item["reason"].strip():
            raise ValueError("INVALID_SAFE_INPUT_JUSTIFICATION")
        seen.add(item["path"])
    return entries, tuple(retired), contracts, safe_inputs


def safe_path(path: str) -> bool:
    return (isinstance(path, str) and bool(path) and not Path(path).is_absolute()
            and not re.search(r'[:\\*?\t\r\n]', path) and ".." not in Path(path).parts)


def declared_compatibility_members(source: str) -> set[str]:
    """Public constants, nested types and enum constants are source-owned identities.

    These declaration names supplement type/typed-use edges, so importing a nested
    type or static member cannot hide a new method behind an already approved import.
    """
    code = JAVA_LEXEMES.sub(" ", source)
    members = set(re.findall(r'\b(?:class|interface|record|enum)\s+([\w$]+)', code))
    members.update(re.findall(r'\bpublic\s+static\s+final\s+[\w.$<>?,\[\] ]+\s+([\w$]+)\s*=', code))
    for body in re.findall(r'\benum\s+[\w$]+\s*\{([^;{}]*)[;}]', code):
        members.update(re.findall(r'(?:^|,)\s*([A-Za-z_$][\w$]*)', body))
    return members


def compatibility_edges(root: Path, paths: list[str], contracts: list[dict]) -> set[tuple[str, str, str, str]]:
    """JDK AST references; no literal-stage prefilter and no generated/history sources.

    Type/member selectors and typed-variable uses are conservatively bound to enclosing
    declarations. A new method in an approved file is a new edge, not an implicit grant.
    Parser/tool failures abort the entire check. There is no fallback to regex or hashes.
    """
    if not contracts:
        return set()
    names = [(member, c["identity"]) for c in contracts for member in c["members"]]
    java_paths = [p for p in paths if p.endswith(".java") and p not in SELF_FILES and not SENSITIVE.search(p)]
    payload = "\n".join(m + "\t" + identity for m, identity in names) + "\n\n" + "\n".join(java_paths) + "\n"
    result = subprocess.run(["java", "-Dfile.encoding=UTF-8", str(Path(__file__).resolve().parent / "JavaCompatibilityReferences.java"), str(root.resolve())],
                            input=payload, text=True, encoding="utf-8", capture_output=True, timeout=120)
    if result.returncode:
        raise ValueError("JAVA_REFERENCE_SCANNER_FAILED")
    edges = set()
    for line in result.stdout.splitlines():
        edge = tuple(line.split("\t"))
        if len(edge) != 4 or edge[0] not in {c["identity"] for c in contracts} or edge[1] not in java_paths:
            raise ValueError("INVALID_JAVA_REFERENCE_OUTPUT")
        edges.add(edge)
    return edges


def javascript_dependencies(source: str, commonjs: bool = False) -> list[str]:
    """Parse a deliberately bounded, non-executing JS module grammar.

    Statements: static import (side effect/default/named/namespace), named/star
    export with optional aliases, and single-name const/let/var literal declarations
    (optionally exported). Comments, whitespace, line breaks and ASI at statement
    boundaries are supported. Every token must belong to that grammar: no skipped
    function bodies or expression islands. This is intentionally not a full JS parser.

    No active JS callers currently require expressions, require(), import(), templates,
    regex/division, escaped literals/identifiers or import attributes. Those constructs
    fail closed, including when nested. Supporting them needs an explicit grammar
    extension and tests; never fall back to regex discovery or execute input code.
    """
    tokens = []
    number_pattern = re.compile(r'(?:0|[1-9][0-9]*)(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?')
    i = 0
    while i < len(source):
        start = i
        ch = source[i]
        if ch in '\t\v\f \xa0\ufeff\r\n\u2028\u2029\u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200a\u202f\u205f\u3000':
            i += 1
            continue
        if source.startswith('//', i):
            i += 2
            while i < len(source) and source[i] not in '\r\n\u2028\u2029':
                i += 1
            continue
        if source.startswith('/*', i):
            end = source.find('*/', i + 2)
            if end < 0:
                raise ValueError('JS_UNTERMINATED_COMMENT')
            i = end + 2
            continue
        if ch in "\"'":
            i += 1
            while i < len(source) and source[i] != ch:
                if source[i] in '\\\r\n\u2028\u2029':
                    raise ValueError('JS_UNSUPPORTED_STRING_SYNTAX')
                i += 1
            if i == len(source):
                raise ValueError('JS_UNTERMINATED_STRING')
            i += 1
            tokens.append(('string', source[start + 1:i - 1], start, i))
            continue
        if ch.isascii() and (ch.isalpha() or ch in '_$'):
            i += 1
            while i < len(source) and source[i].isascii() and (source[i].isalnum() or source[i] in '_$'):
                i += 1
            tokens.append(('word', source[start:i], start, i))
            continue
        if ch in '0123456789':
            number = number_pattern.match(source, i)
            i += len(number.group())
            tokens.append(('number', number.group(), start, i))
            continue
        if ch in '{}*,;=-':
            i += 1
            tokens.append(('punct', ch, start, i))
            continue
        # Reject ambiguous lexical goals instead of mistaking regex/template text for
        # code, or hiding executable expressions inside apparently opaque literals.
        raise ValueError('JS_UNSUPPORTED_TOKEN')

    pos = 0
    dependencies = []
    bindings = set()
    exports = set()
    reserved = set(('await break case catch class const continue debugger default delete do else enum '
                    'export extends false finally for function if implements import in instanceof '
                    'interface let new null package private protected public return static super switch '
                    'this throw true try typeof var void while with yield eval arguments').split())

    def at(value):
        return pos < len(tokens) and tokens[pos][0] != 'string' and tokens[pos][1] == value

    def take(value):
        nonlocal pos
        if not at(value):
            raise ValueError('JS_UNSUPPORTED_MODULE_GRAMMAR')
        pos += 1

    def bind(value):
        if value in reserved or value in bindings:
            raise ValueError('JS_INVALID_OR_DUPLICATE_BINDING')
        bindings.add(value)

    def export_name(value):
        if value in exports:
            raise ValueError('JS_DUPLICATE_EXPORT')
        exports.add(value)

    def name(binding=True):
        nonlocal pos
        if pos == len(tokens) or tokens[pos][0] != 'word' or (binding and tokens[pos][1] in reserved):
            raise ValueError('JS_UNSUPPORTED_BINDING')
        value = tokens[pos][1]
        pos += 1
        if binding:
            bind(value)
        return value

    def named(importing):
        take('{')
        while not at('}'):
            original = name(False)
            if at('as'):
                take('as')
                alias = name(importing)
            else:
                alias = original
                if importing:
                    bind(original)
            if not importing:
                export_name(alias)
            if not at(','):
                break
            take(',')
        take('}')

    def dependency():
        nonlocal pos
        if pos == len(tokens) or tokens[pos][0] != 'string':
            raise ValueError('JS_NON_LITERAL_DEPENDENCY')
        dependencies.append(tokens[pos][1])
        pos += 1

    def finish():
        nonlocal pos
        if at(';'):
            pos += 1
        elif pos < len(tokens) and not any(c in source[tokens[pos - 1][3]:tokens[pos][2]] for c in '\r\n\u2028\u2029'):
            raise ValueError('JS_UNSUPPORTED_STATEMENT_CONTINUATION')

    while pos < len(tokens):
        if at(';'):
            take(';')
            continue
        exporting = at('export')
        if commonjs and (exporting or at('import')):
            raise ValueError('JS_UNSUPPORTED_COMMONJS_MODULE_DECLARATION')
        if exporting:
            take('export')
        if not exporting and at('import'):
            take('import')
            if pos < len(tokens) and tokens[pos][0] == 'string':
                dependency()
            else:
                if not at('{') and not at('*'):
                    name()
                    if at(','):
                        take(',')
                        if not at('{') and not at('*'):
                            raise ValueError('JS_UNSUPPORTED_IMPORT_CLAUSE')
                    elif not at('from'):
                        raise ValueError('JS_UNSUPPORTED_IMPORT_CLAUSE')
                if at('{'):
                    named(True)
                elif at('*'):
                    take('*')
                    take('as')
                    name()
                take('from')
                dependency()
        elif exporting and (at('{') or at('*')):
            star = at('*')
            if star:
                take('*')
                if at('as'):
                    take('as')
                    export_name(name(False))
            else:
                named(False)
            if star or at('from'):
                take('from')
                dependency()
            else:
                # Local export resolution would require binding analysis. It is not
                # needed by the static re-export contract and must not pass silently.
                raise ValueError('JS_UNSUPPORTED_LOCAL_EXPORT')
        elif any(at(kind) for kind in ('const', 'let', 'var')):
            pos += 1
            declared = name()
            if exporting:
                export_name(declared)
            take('=')
            if at('-'):
                take('-')
                if pos == len(tokens) or tokens[pos][0] != 'number':
                    raise ValueError('JS_UNSUPPORTED_LITERAL')
            if pos == len(tokens) or not (tokens[pos][0] in {'string', 'number'} or any(at(v) for v in ('true', 'false', 'null'))):
                raise ValueError('JS_UNSUPPORTED_LITERAL')
            pos += 1
        else:
            raise ValueError('JS_UNSUPPORTED_STATEMENT')
        finish()
    return dependencies


def executable_inputs(root: Path, paths: list[str], safe_inputs: list[dict]) -> tuple[list[str], set[str]]:
    """Inspect the complete active control-plane namespace, not just extension matches.

    This intentionally over-approximates reachability: every file under scripts/deploy/
    workflows must be inspectable or an exact reviewed data input. Static interpreter
    references additionally pull inputs outside those namespaces into the same contract.
    """
    errors = []
    safe = {item["path"]: item for item in safe_inputs}
    used = set()
    executable = set()
    pending = [p for p in paths if p.startswith(CONTROL_PLANE_ROOTS)]
    inspected = set()
    command = re.compile(r'\b(?:node|nodejs|python[\d.]*|pwsh|powershell|bash|sh|ruby|perl|lua|deno|bun)\s+(?:-[\w-]+\s+)*[\'"]?((?:\./)?[\w./-]+)')
    direct_command = re.compile(r'(?:\brun:\s*|\bExecStart(?:Pre|Post)?=\s*)'
                                r'(?:&\s*)?[\'"]?([A-Za-z_.][\w.-]*(?:/[\w.-]+)+)'
                                r'|(?m:^\s*)(?:&\s*)?[\'"]?(\./[\w./-]+)')
    while pending:
        path = pending.pop()
        if path in inspected:
            continue
        inspected.add(path)
        if (SENSITIVE.search(path) or not safe_path(path) or any(p in GENERATED for p in Path(path).parts)
                or not (root / path).is_file() or (root / path).is_symlink()):
            errors.append("UNSUPPORTED_ACTIVE_EXECUTABLE_INPUT: " + path)
            continue
        suffix = Path(path).suffix.lower()
        if (root / path).stat().st_size > 4_000_000:
            raise ValueError("SOURCE_TOO_LARGE: " + path)
        if suffix not in EXTENSIONS:
            item = safe.get(path)
            if item and hashlib.sha256((root / path).read_bytes()).hexdigest() == item["sha256"]:
                used.add(path)
                continue
            errors.append("UNSUPPORTED_ACTIVE_EXECUTABLE_INPUT: " + path)
            continue
        if suffix in EXECUTABLE_EXTENSIONS:
            executable.add(path)
        if path in SELF_FILES:
            continue
        content = (root / path).read_text(encoding="utf-8-sig")
        if suffix in {".js", ".mjs", ".cjs"}:
            # Relative JS module inputs can carry execution through an arbitrary suffix.
            # External packages are not implicitly trusted or traversed through node_modules.
            try:
                dependencies = javascript_dependencies(content, commonjs=suffix == '.cjs')
            except ValueError:
                # Keep other stage diagnostics, but never admit a graph with an
                # unparsed module. Report only the category/path, not source text.
                errors.append('UNSUPPORTED_ACTIVE_JS_SYNTAX: ' + path)
                continue
            for dependency in dependencies:
                target_path = (root / path).parent / dependency
                if (not dependency.startswith(("./", "../")) or any(c in dependency for c in '\\:%?#\x00')
                        or not target_path.resolve().is_relative_to(root.resolve())):
                    errors.append("UNSUPPORTED_ACTIVE_EXECUTABLE_INPUT: " + path + " module dependency")
                    continue
                target = target_path.resolve().relative_to(root.resolve()).as_posix()
                # Resolve only exact local JS module files, with no package/extension
                # guessing or symlink aliases (including symlinked parent directories).
                linked = any(parent.is_symlink() for parent in (target_path, *target_path.parents) if parent.is_relative_to(root))
                if linked or historical(target) or Path(target).suffix.lower() not in {'.js', '.mjs', '.cjs'}:
                    errors.append("UNSUPPORTED_ACTIVE_EXECUTABLE_INPUT: " + target)
                else:
                    pending.append(target)
        for match in list(command.finditer(content)) + list(direct_command.finditer(content)):
            target = next(value for value in match.groups() if value).removeprefix("./")
            if historical(target):
                errors.append("HISTORICAL_ACTIVE_EXECUTABLE_INPUT: " + target)
                continue
            if target in safe:
                errors.append("DATA_CLASSIFICATION_USED_AS_EXECUTABLE: " + target)
                continue
            # Commands in regression fixture strings may intentionally name nonexistent
            # inputs. Existing local inputs are always examined; unknown files in the
            # control-plane roots are already rejected independently of caller discovery.
            if (root / target).is_file():
                if Path(target).suffix.lower() not in EXECUTABLE_EXTENSIONS:
                    errors.append("UNSUPPORTED_ACTIVE_EXECUTABLE_INPUT: " + target)
                else:
                    pending.append(target)
            elif path.startswith(".github/workflows/") and "/" in target:
                errors.append("UNSUPPORTED_ACTIVE_EXECUTABLE_INPUT: " + target)
    errors.extend("STALE_SAFE_INPUT: " + p for p in sorted(set(safe) - used))
    return errors, executable


def check(root: Path) -> tuple[list[str], int, int]:
    entries, retired, contracts, safe_inputs = load_policy(root)
    used = set()
    errors = []
    paths = sources(root)
    input_errors, executable = executable_inputs(root, paths, safe_inputs)
    errors.extend(input_errors)
    paths = sorted(set(paths) | executable)
    actual = compatibility_edges(root, paths, contracts)
    approved = {(c["identity"], e["path"], e["callerMember"], e["contractMember"])
                for c in contracts for e in c["approvedCallers"]}
    errors.extend("UNAUTHORIZED_COMPATIBILITY_CALLER: " + " -> ".join(edge) for edge in sorted(actual - approved))
    errors.extend("STALE_COMPATIBILITY_CALLER: " + " -> ".join(edge) for edge in sorted(approved - actual))
    for path in paths:
        risk = inspect(root, path, retired)
        if risk is None:
            continue
        expected = entries.get(path)
        if risk["kind"] != "STAGE_SEMANTICS" or expected is None or expected["sha256"] != risk["sha256"]:
            errors.append(risk["kind"] + ": " + path)
        else:
            used.add(path)
    errors.extend("STALE_EXCEPTION: " + path for path in sorted(set(entries) - used))
    return errors, len(paths), len(used)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    args = parser.parse_args()
    try:
        errors, count, retained = check(args.root.resolve())
        for error in errors:
            print(error)
        print(f"STAGE_ASSET_CHECK scanned={count} reviewed_exceptions={retained} errors={len(errors)}")
        return 1 if errors else 0
    except (OSError, ValueError, TypeError, KeyError, subprocess.SubprocessError) as error:
        # Print failure category only: malformed input must never echo secret-bearing source text.
        print("STAGE_ASSET_CHECK FAILED_CLOSED: " + type(error).__name__, file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
