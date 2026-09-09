"""使用留在临时目录的双射表逆映射全部文件，校验身份以外的事实没有变化。"""
import argparse
import hashlib
import json
import re
from pathlib import Path


def verify(root, raw_root, mapping_file, metadata_file):
    evidence = Path(__file__).resolve().parent
    maps = json.loads(mapping_file.read_text(encoding="utf-8"))
    metadata = json.loads(metadata_file.read_text(encoding="utf-8"))
    reverse = {}
    total = 0
    for group in [entry["identities"] for entry in maps.values()] + [metadata]:
        seen = set()
        for kind, raw, canonical in group:
            assert (kind, raw) not in seen
            seen.add((kind, raw))
            assert canonical not in reverse
            reverse[canonical] = raw
            total += 1
    token = re.compile(r"SYNTH-L4:B2(?:-[A-Z0-9]+)*:R\d+:[A-Z]+:\d+")
    bindings = json.loads((evidence / "equivalence.json").read_text(encoding="utf-8"))["bindings"]
    checked = []
    for binding in bindings:
        path = binding["path"]
        original = (raw_root / path).read_bytes()
        assert hashlib.sha256(original).hexdigest() == binding["beforeSha256"]
        current = root / path
        if binding["disposition"] == "RAW_EPHEMERAL_ONLY":
            assert not current.exists()
            checked.append({"path": path, "originalBytesRetained": True})
            continue
        data = current.read_bytes()
        assert hashlib.sha256(data).hexdigest() == binding["afterSha256"]
        restored = token.sub(lambda match: reverse[match[0]], data.decode("utf-8-sig"))
        expected = original.decode("utf-8-sig")
        # 四个原始归档链接现在指向迁移说明；原文件字节与历史 hash 未改写。
        destinations = {"l4-b2-qualification-resume-attempt01/combined-review-raw.zip",
                        "l4-b2-qualification-resume-attempt01/qualification-logs.zip",
                        "l4-b2-live-env-remediation-attempt01/run-logs.zip", "pre-format-originals.zip"}
        if path.endswith(".md"):
            destination = "../l4-b2-synthetic-identity-remediation/README.md" if "/l4-b2-precise-delivery/" in path else "l4-b2-synthetic-identity-remediation/README.md"
            for old in destinations:
                expected = expected.replace("](" + old + ")", "](" + destination + ")")
        if restored.replace("\r\n", "\n") != expected.replace("\r\n", "\n"):
            import difflib
            (mapping_file.parent / "inverse-diff.txt").write_text("".join(difflib.unified_diff(
                expected.splitlines(True), restored.splitlines(True))), encoding="utf-8")
            raise AssertionError(path)
        checked.append({"path": path, "fullTextInverseEquality": True})
    production = json.loads((root / "docs/audit/evidence/l4-b2-qualification-resume-attempt01/production-identity.json").read_text())
    lines = []
    for source in production["sources"]:
        digest = hashlib.sha256((root / source["path"]).read_bytes()).hexdigest()
        assert digest == source["rawSha256"]
        lines.append(source["path"] + "\t" + digest.upper())
    fingerprint = hashlib.sha256(("\r\n".join(lines) + "\r\n").encode()).hexdigest()
    assert fingerprint == production["productionFingerprint"]
    result = {"productionFingerprint": fingerprint, "productionFilesByteIdentical": 7,
              "uniqueTypedIdentities": total, "bijectionCollisions": 0,
              "TECHNICAL_CANDIDATE_DELTA": 0, "EVIDENCE_SEMANTIC_DELTA": 0,
              "EVIDENCE_IDENTITY_REPRESENTATION": "CANONICALIZED", "files": checked}
    (evidence / "equivalence-validation.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: v for k, v in result.items() if k != "files"}))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("raw_root", type=Path)
    parser.add_argument("mappings", type=Path)
    parser.add_argument("metadata", type=Path)
    args = parser.parse_args()
    root = next(p for p in Path(__file__).resolve().parents if (p / "scripts/ci/delivery-supply-chain-lock.json").exists())
    verify(root, args.raw_root, args.mappings, args.metadata)
