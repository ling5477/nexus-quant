"""对实际导出树运行默认 gitleaks；仅识别精确的无密钥 PEM 协议常量。"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import subprocess
import tempfile

import release_source as source

SIGNER = "backend/nq-adapter-binance/src/main/java/com/guidinglight/nexusquant/adapter/binance/signing/BinanceEd25519RequestSigner.java"
# 原因：默认 private-key 规则跨越两个 Java delimiter 常量产生误报；完整文件摘要防止夹带材料。
SIGNER_SHA256 = "a463d9b8773dde43340b92fbbc87f530386526bd13baf6a8c83950853d69a166"
DELIMITERS = (b'    private static final String PRIVATE_KEY_BEGIN = "-----BEGIN PRIVATE KEY-----";',
              b'    private static final String PRIVATE_KEY_END = "-----END PRIVATE KEY-----";')


def protocol_only(finding: dict, output: Path) -> bool:
    path = Path(finding.get("File", "")).absolute()
    expected = (output / SIGNER).absolute()
    if path != expected or finding.get("RuleID") != "private-key":
        return False
    raw = expected.read_bytes()
    return (source.sha(raw) == SIGNER_SHA256 and finding.get("StartLine") == 22
            and finding.get("EndLine") == 23 and tuple(raw.splitlines()[21:23]) == DELIMITERS)


def scan(repo: Path, commit: str, output: Path, binary: Path) -> dict:
    metadata, _ = source.verify(repo, commit, output)
    if not binary.is_absolute() or not binary.is_file() or binary.is_symlink():
        raise ValueError("VERIFIED_GITLEAKS_BINARY_REQUIRED")
    version = subprocess.run([str(binary), "version"], capture_output=True, check=True, timeout=15).stdout.strip()
    if version != b"8.18.4":
        raise ValueError("UNSUPPORTED_GITLEAKS_VERSION")
    with tempfile.TemporaryDirectory(prefix="nq-release-secret-") as folder:
        root = Path(folder)
        config, report = root / "scan.toml", root / "report.json"
        config.write_text("[extend]\nuseDefault = true\n", encoding="utf-8")
        proc = subprocess.run([str(binary), "detect", "--no-git", "--source", str(output.absolute()),
                               "--config", str(config), "--redact", "--report-format", "json", "--report-path", str(report)],
                              capture_output=True, timeout=180)
        if proc.returncode not in {0, 1} or not report.is_file():
            raise ValueError("GITLEAKS_SCANNER_ERROR")
        findings = json.loads(report.read_bytes())
        if not isinstance(findings, list) or (proc.returncode == 0) != (len(findings) == 0):
            raise ValueError("GITLEAKS_RESULT_MISMATCH")
        accepted = [f for f in findings if protocol_only(f, output)]
        rejected = [f for f in findings if not protocol_only(f, output)]
        # 永不打印 Match、Secret 或其它扫描内容，失败输出仅含规则与位置。
        return {"sourceCommit": commit, "releaseTreeSha256": metadata["releaseTreeSha256"],
                "scanner": version.decode(), "binarySha256": source.sha(binary.read_bytes()),
                "historicalAllowlistUsed": False, "rawFindings": len(findings),
                "protocolDelimiterFalsePositives": len(accepted), "secretFindings": len(rejected),
                "rejected": [{k: f.get(k) for k in ("RuleID", "File", "StartLine", "EndLine")} for f in rejected]}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", type=Path, required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--gitleaks", type=Path, required=True)
    args = parser.parse_args()
    try:
        result = scan(args.repo, args.commit, args.output, args.gitleaks)
        print(json.dumps(result, sort_keys=True))
        return 1 if result["secretFindings"] else 0
    except (OSError, ValueError, TypeError, KeyError, subprocess.SubprocessError) as exc:
        print("RELEASE_SECRET_FAILED_CLOSED: " + type(exc).__name__)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
