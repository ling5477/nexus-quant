"""保留原始严格检查，只接受固定历史 SQL 的唯一 EOF 诊断。"""
import argparse
import hashlib
import subprocess
from pathlib import Path

HISTORICAL_PATH = "backend/nq-infra/src/main/resources/db/migration/V24__event_store_business_identity_indexes.sql"
HISTORICAL_SHA256 = "5b39cf0fd1678002f5a47642e5a50ca3efddac05155ee228a817b7b40be7385e"
HISTORICAL_DIAGNOSTIC = HISTORICAL_PATH + ":57: new blank line at EOF."


def review(result, read_blob):
    if result.returncode == 0:
        return {"rawExit": 0, "acceptedHistoricalDiagnostics": 0}
    lines = result.stdout.decode("utf-8", errors="strict").splitlines()
    # 诊断、数量、退出码和目标提交的完整 blob 必须同时匹配，其他失败原样阻断。
    if (result.returncode != 2 or result.stderr or lines != [HISTORICAL_DIAGNOSTIC]
            or hashlib.sha256(read_blob(HISTORICAL_PATH)).hexdigest() != HISTORICAL_SHA256):
        raise ValueError("STRICT_WHITESPACE_CHECK_FAILED")
    return {"rawExit": 2, "acceptedHistoricalDiagnostics": 1}


def check(root, base=None, head="HEAD", merge_base=False):
    def git(*args):
        return subprocess.run(["git", "--no-replace-objects", "-C", str(root), *args],
                              capture_output=True, timeout=30, check=True).stdout
    target = git("rev-parse", "--verify", head + "^{commit}").decode().strip()
    if base:
        start = git("rev-parse", "--verify", base + "^{commit}").decode().strip()
        if merge_base:
            start = git("merge-base", start, target).decode().strip()
        args = ["diff", "--check", start, target, "--"]
    else:
        args = ["show", "--check", "--pretty=format:", target, "--"]
    result = subprocess.run(["git", "--no-replace-objects", "-C", str(root), *args],
                            capture_output=True, timeout=30)
    return review(result, lambda path: git("show", target + ":" + path))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base")
    parser.add_argument("--head", default="HEAD")
    parser.add_argument("--merge-base", action="store_true")
    args = parser.parse_args()
    try:
        result = check(Path.cwd(), args.base, args.head, args.merge_base)
        print("WHITESPACE_CONTRACT_PASS " + str(result))
        return 0
    except (OSError, ValueError, subprocess.SubprocessError) as error:
        print("WHITESPACE_CONTRACT_REJECTED " + type(error).__name__)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
