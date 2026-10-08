"""验证导出树的开发边界、业务语义与静态依赖；构建和 secret scan 单独执行。"""
from __future__ import annotations

import argparse
import ast
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET

import release_source as source

RULES = "scripts/docs/check-stage-semantic-leakage.py"
POLICY = "scripts/docs/stage-semantic-allowlist.json"
TEXT = {".java", ".ts", ".tsx", ".js", ".css", ".json", ".xml", ".yml", ".yaml", ".sql", ".csv", ".html", ".md", ".ps1", ".psm1", ".sh"}
RELEASE_DOCS = {"README.md", "INSTALL.md", "CHANGELOG.md", "LICENSE", "DISCLAIMER.md", "THIRD_PARTY_NOTICES.md", "VERSION"}
# 官方 https://www.apache.org/licenses/LICENSE-2.0.txt 的完整原始字节摘要；禁止混入领域风险条款。
APACHE_LICENSE_SHA256 = "cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30"
# v5.2.0 tag 与其 peeled commit 的官方资产逐字节一致；保留原文中的 Unicode 字符。
LIGHTWEIGHT_CHARTS_NOTICE_SHA256 = "f76c6afab94884448f0426e30d6e9d555ca7247894cd3484e477d2f87513036e"
LIGHTWEIGHT_CHARTS_LICENSE_SHA256 = "70c9d5382506dd184465425c08a99ad9bd6d9ac1313c252968ba0b585e5ef823"


def legal_errors(output: Path, names: set[str]) -> list[str]:
    errors = ["MISSING_RELEASE_DOC: " + path for path in sorted(RELEASE_DOCS - names)]
    if "LICENSE" in names and source.sha((output / "LICENSE").read_bytes()) != APACHE_LICENSE_SHA256:
        errors.append("NONSTANDARD_APACHE_LICENSE")
    if "THIRD_PARTY_NOTICES.md" in names:
        raw = (output / "THIRD_PARTY_NOTICES.md").read_bytes()
        blocks = re.findall(rb"```text\n(.*?)```", raw, re.DOTALL)
        if len(blocks) != 2 or source.sha(blocks[0]) != LIGHTWEIGHT_CHARTS_NOTICE_SHA256:
            errors.append("LIGHTWEIGHT_CHARTS_NOTICE_MISMATCH")
        if len(blocks) != 2 or source.sha(blocks[1]) != LIGHTWEIGHT_CHARTS_LICENSE_SHA256:
            errors.append("LIGHTWEIGHT_CHARTS_LICENSE_MISMATCH")
        for required in (b"`lightweight-charts`", b"`5.2.0`", b"`Apache-2.0`", b"`v5.2.0`",
                         b"https://github.com/tradingview/lightweight-charts"):
            if required not in raw:
                errors.append("LIGHTWEIGHT_CHARTS_PROVENANCE_MISSING")
    if "frontend/package-lock.json" in names:
        lock = json.loads((output / "frontend/package-lock.json").read_bytes())
        package = lock.get("packages", {}).get("node_modules/lightweight-charts", {})
        if (package.get("version") != "5.2.0" or package.get("license") != "Apache-2.0"
                or package.get("resolved") != "https://registry.npmjs.org/lightweight-charts/-/lightweight-charts-5.2.0.tgz"):
            errors.append("LIGHTWEIGHT_CHARTS_LOCK_IDENTITY_MISMATCH")
    for path in ("README.md", "INSTALL.md"):
        if path in names:
            text = (output / path).read_text(encoding="utf-8-sig")
            for target in ("LICENSE", "DISCLAIMER.md", "THIRD_PARTY_NOTICES.md"):
                if not re.search(r"\[[^\]\n]+\]\(" + re.escape(target) + r"\)", text) or target not in names:
                    errors.append(f"MISSING_LEGAL_LINK: {path}:{target}")
    # 扫描全部发布文件，包括无扩展名文件；开发树中的历史证据不影响发布结论。
    for path in sorted(names):
        raw = (output / path).read_bytes()
        if b"LICENSE_PENDING_USER_DECISION" in raw or b"PENDING_USER_DECISION" in raw:
            errors.append("PENDING_LICENSE: " + path)
    return errors


def check(repo: Path, commit: str, output: Path) -> dict:
    metadata, audit = source.verify(repo, commit, output)
    entries = source.tree(repo, commit)
    policy_raw = source.blobs(repo, [entries[POLICY][1], entries[RULES][1]])
    # 只解析既有 checker 的正则字面量，不执行候选脚本或继承历史文档豁免。
    syntax = ast.parse(policy_raw[entries[RULES][1]].decode("utf-8-sig"))
    expr = next(node.value.args[0] for node in syntax.body if isinstance(node, ast.Assign)
                and any(isinstance(t, ast.Name) and t.id == "STAGE" for t in node.targets))
    stage = re.compile(ast.literal_eval(expr))
    policy = json.loads(policy_raw[entries[POLICY][1]])
    allowed = {(e["path"], e["token"], e["lineSha256"]): e for e in policy["exceptions"]
               if e["category"] in {"BUSINESS_DOMAIN_TERM", "RUNTIME_COMPATIBILITY_IDENTIFIER"}}
    extra_stage = re.compile(r"\b(?:Gate\s+[A-Z](?:[- ]\d+)?|Phase\s+\d+|Attempt\s+\d+|Stage[- ]QDR|RC\d+(?:-[A-Z0-9]+)*)\b|后续 Gate|当前 Gate")
    dev_reference = re.compile(r"(?:docs/(?:current|gate[s]|audit|archive)/|research/py/|scripts/(?:docs|ci|java-standard)/|\.agents/|AGENTS\.md|CLAUDE\.md|src/test/)")
    errors, retained = [], set()
    names = {e["path"] for e in metadata["files"]}
    errors.extend(legal_errors(output, names))
    for path in ("LICENSE", "DISCLAIMER.md", "THIRD_PARTY_NOTICES.md"):
        if audit["paths"].get(path) != "INCLUDE_RELEASE_DOC":
            errors.append("LEGAL_DOC_CLASSIFICATION: " + path)
    if any(p.startswith("frontend/src/pages/dev/") for p in names):
        errors.append("DEVELOPMENT_PAGE_INCLUDED")
    for entry in metadata["files"]:
        path = entry["path"]
        if Path(path).suffix.lower() not in TEXT:
            continue
        raw = (output / path).read_bytes()
        if b"\0" in raw:
            errors.append("UNSCANNABLE_TEXT: " + path)
            continue
        for number, line in enumerate(raw.decode("utf-8-sig").splitlines(), 1):
            if dev_reference.search(line):
                errors.append(f"DEV_REFERENCE: {path}:{number}")
            for match in list(stage.finditer(line)) + list(extra_stage.finditer(line)):
                key = (path, match.group(), source.sha(line.encode("utf-8")))
                if key in allowed:
                    retained.add(key)
                else:
                    errors.append(f"STAGE_LEAKAGE: {path}:{number}:{match.group()}")
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    for path in sorted(p for p in names if p.endswith("pom.xml")):
        doc = ET.parse(output / path)
        for module in doc.findall("m:modules/m:module", ns):
            target = (Path(path).parent / module.text / "pom.xml").as_posix()
            if target not in names:
                errors.append("MISSING_MAVEN_MODULE: " + target)
    pkg = json.loads((output / "frontend/package.json").read_bytes())
    lock = json.loads((output / "frontend/package-lock.json").read_bytes())["packages"][""]
    for key in ("dependencies", "devDependencies"):
        if pkg.get(key) != lock.get(key):
            errors.append("PACKAGE_LOCK_MISMATCH: " + key)
    if "prebuild" in pkg["scripts"]:
        errors.append("UNREVIEWED_PRODUCTION_PREBUILD")
    node = json.loads((output / "frontend/tsconfig.node.json").read_bytes())
    if any("playwright" in item for item in node["include"]):
        errors.append("PLAYWRIGHT_BUILD_DEPENDENCY")
    # 资源路径按 JVM classpath 联合检查；运行 profile 由 installer 显式选择 prod。
    resources = {p.split("/src/main/resources/", 1)[1] for p in names if "/src/main/resources/" in p}
    for path in sorted(p for p in names if p.endswith((".java", ".yml", ".xml"))):
        text = (output / path).read_text(encoding="utf-8-sig")
        for ref in re.findall(r'["\s](backtest/fixtures/[^"\s]+\.csv)', text):
            if ref not in resources:
                errors.append("MISSING_RUNTIME_RESOURCE: " + ref)
        for ref in re.findall(r'classpath:([A-Za-z0-9_./-]+)', text):
            if not any(r == ref or r.startswith(ref.rstrip("/") + "/") for r in resources):
                errors.append("MISSING_CLASSPATH_RESOURCE: " + ref)
    stale = [key for key in allowed if key[0] in names and key not in retained]
    errors.extend("STALE_BUSINESS_EXCEPTION: " + key[0] for key in stale)
    return {"sourceCommit": commit, "releaseTreeSha256": metadata["releaseTreeSha256"],
            "filesScanned": len(names), "retainedBusinessOrCompatibility": len(retained),
            "unclassified": audit["unclassified"], "errors": errors,
            "javaAndFrontendImportClosure": "REQUIRES_EXPORTED_TREE_CLEAN_BUILD",
            "deployClosure": "DOCKER_LOCAL_IMAGE_PACKAGE;CLEAN_INSTALL_QUALIFICATION_REQUIRED"}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        result = check(args.repo, args.commit, args.output)
        print(json.dumps(result, sort_keys=True))
        return 1 if result["errors"] else 0
    except (OSError, ValueError, TypeError, KeyError, StopIteration, SyntaxError) as exc:
        print("RELEASE_BOUNDARY_FAILED_CLOSED: " + type(exc).__name__)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
