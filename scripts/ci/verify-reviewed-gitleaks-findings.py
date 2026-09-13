"""以原始受跟踪文件的完整字节和完整字段验证已审误报，不信任截断 Secret。"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import stat
import subprocess
import sys


class Rejected(Exception):
    pass


def require(condition, code):
    if not condition:
        raise Rejected(code)


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, "DUPLICATE_JSON_KEY")
        result[key] = value
    return result


def read_bytes(path, limit):
    # 拒绝符号链接及Windows重解析点；不能将批准路径重定向到其他输入。
    for part in (path, *path.parents):
        info = part.lstat()
        require(not stat.S_ISLNK(info.st_mode)
                and not (getattr(info, "st_file_attributes", 0) & 0x400), "UNSAFE_SOURCE_PATH")
    require(path.is_file(), "SOURCE_NOT_REGULAR")
    with path.open("rb") as stream:
        data = stream.read(limit + 1)
    require(len(data) <= limit, "INPUT_TOO_LARGE")
    return data


def read_json(path, limit):
    return json.loads(read_bytes(path, limit).decode("utf-8"), object_pairs_hook=unique_object,
                      parse_constant=lambda _: (_ for _ in ()).throw(Rejected("INVALID_JSON_NUMBER")))


def safe_relative(value):
    require(isinstance(value, str) and len(value) <= 1024, "UNSAFE_SOURCE_PATH")
    require(re.fullmatch(r"[A-Za-z0-9_.\-/]+", value) is not None, "UNSAFE_SOURCE_PATH")
    require(all(part not in ("", ".", "..") for part in value.split("/")), "UNSAFE_SOURCE_PATH")
    require(not Path(value).is_absolute(), "UNSAFE_SOURCE_PATH")
    return value


def report_path(value, source):
    require(isinstance(value, str), "UNSAFE_SOURCE_PATH")
    # gitleaks目录模式可返回绝对路径；只接受本次staging目录的直接规范映射。
    path = Path(value)
    if path.is_absolute():
        require(".." not in path.parts and "." not in value.replace("\\", "/").split("/"),
                "UNSAFE_SOURCE_PATH")
        try:
            return safe_relative(path.relative_to(source).as_posix())
        except ValueError:
            raise Rejected("FINDING_OUTSIDE_SOURCE") from None
    return safe_relative(value)


def identity(entry):
    return entry["path"], entry["ruleId"], entry["startLine"], entry["endLine"]


def verify(repository, source, report, registry):
    repository, source = repository.absolute(), source.absolute()
    data = read_json(registry, 128 * 1024)
    require(isinstance(data, dict) and set(data) == {"schemaVersion", "entries"}
            and type(data["schemaVersion"]) is int and data["schemaVersion"] == 1, "REGISTRY_SCHEMA")
    entries = data["entries"]
    require(isinstance(entries, list) and 1 <= len(entries) <= 64, "REGISTRY_SCHEMA")
    fields = {"path", "fileSha256", "ruleId", "startLine", "endLine", "fieldName",
              "fullValueSha256", "reason", "owner", "removalTrigger"}
    reviewed = {}
    for entry in entries:
        require(isinstance(entry, dict) and set(entry) == fields, "REGISTRY_SCHEMA")
        safe_relative(entry["path"])
        require(entry["ruleId"] == "generic-api-key" and entry["fieldName"] == "sampleToken",
                "UNSUPPORTED_REVIEW_KIND")
        require(type(entry["startLine"]) is int and type(entry["endLine"]) is int
                and 0 < entry["startLine"] == entry["endLine"] <= 1_000_000, "REGISTRY_LINE")
        for key in ("fileSha256", "fullValueSha256"):
            require(isinstance(entry[key], str) and re.fullmatch(r"[0-9a-f]{64}", entry[key]), "REGISTRY_HASH")
        for key in ("reason", "owner", "removalTrigger"):
            require(isinstance(entry[key], str) and 0 < len(entry[key].strip()) <= 2048, "REGISTRY_REASON")
        key = identity(entry)
        require(key not in reviewed, "DUPLICATE_REGISTRY_ENTRY")
        reviewed[key] = entry

    findings = read_json(report, 2 * 1024 * 1024)
    require(isinstance(findings, list) and 1 <= len(findings) <= 256, "REPORT_SCHEMA")
    tracked = set(subprocess.check_output(["git", "-C", str(repository), "ls-files", "-z"],
                                         stderr=subprocess.DEVNULL, timeout=30).decode("utf-8").split("\0"))
    seen, dispositions = set(), []
    for finding in findings:
        require(isinstance(finding, dict) and {"File", "RuleID", "StartLine", "EndLine"} <= set(finding),
                "REPORT_SCHEMA")
        require(type(finding["StartLine"]) is int and type(finding["EndLine"]) is int
                and isinstance(finding["RuleID"], str), "REPORT_SCHEMA")
        require(not finding.get("SymlinkFile") and not finding.get("Commit"), "UNEXPECTED_SCAN_MODEL")
        relative = report_path(finding["File"], source)
        key = (relative, finding["RuleID"], finding["StartLine"], finding["EndLine"])
        require(key not in seen, "DUPLICATE_REPORT_MAPPING")
        require(key in reviewed, "UNREVIEWED_FINDING")
        seen.add(key)
        entry = reviewed[key]
        require(relative in tracked, "SOURCE_NOT_TRACKED")
        original = read_bytes(repository / relative, 32 * 1024 * 1024)
        staged = read_bytes(source / relative, 32 * 1024 * 1024)
        require(original == staged and hashlib.sha256(original).hexdigest() == entry["fileSha256"],
                "FILE_SHA_MISMATCH")
        lines = original.decode("utf-8").splitlines()
        require(entry["startLine"] <= len(lines), "SOURCE_LINE_MISSING")
        # 当前批准值仅含ASCII字母、数字、连字符和冒号；转义、多字段及多行形式均拒绝。
        member = re.fullmatch(r'[ \t]*"sampleToken"[ \t]*:[ \t]*"([A-Za-z0-9:-]{1,256})"[ \t]*,?[ \t]*',
                              lines[entry["startLine"] - 1])
        require(member is not None, "REVIEWED_FINDING_SOURCE_AMBIGUOUS")
        require(hashlib.sha256(member.group(1).encode("utf-8")).hexdigest() == entry["fullValueSha256"],
                "FULL_VALUE_SHA_MISMATCH")
        dispositions.append({"ruleId": entry["ruleId"], "path": relative, "line": entry["startLine"],
                             "fieldName": entry["fieldName"], "status": "REVIEWED_FALSE_POSITIVE"})
    require(seen == set(reviewed), "UNMATCHED_REGISTRY_ENTRY")
    return dispositions


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for argument in ("repository-root", "source-root", "report", "registry"):
        parser.add_argument("--" + argument, type=Path, required=True)
    args = parser.parse_args()
    try:
        dispositions = verify(args.repository_root, args.source_root, args.report, args.registry)
    except Rejected as error:
        print("REVIEWED_FINDING_REJECTED / " + str(error))
        return 1
    except Exception:
        # 解析器和系统异常可能携带原文或路径；日志只保留固定失败类别。
        print("REVIEWED_FINDING_REJECTED / INVALID_INPUT_OR_RUNTIME_ERROR")
        return 1
    for disposition in dispositions:
        print(json.dumps(disposition, ensure_ascii=True))
    print("REVIEWED_FINDINGS_PASS reviewed=" + str(len(dispositions)) + " unknown=0")
    return 0


if __name__ == "__main__":
    sys.exit(main())
