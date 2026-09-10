"""为现有注册生成可审查的候选提案；只有外部审查固定摘要后才应用。

唯一持久注册仍为 validator 的 POLICY_PATH。提案是临时审查制品，不是另一份
运行时 inventory。摘要参数是调用者提供的审查身份，不是工具自动授予的授权。
"""
from __future__ import annotations

import argparse
import copy
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile

SPEC = importlib.util.spec_from_file_location("stage_guard", Path(__file__).with_name("check-stage-assets.py"))
guard = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(guard)


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def encode(value: dict) -> bytes:
    return (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def bound_inputs(root: Path, safe_inputs: list[dict]) -> tuple[list[str], list[dict]]:
    # 与 validator 共用动态文件发现；新增、删除以及实现工具变化均使提案失效。
    paths = guard.sources(root)
    errors, executable = guard.executable_inputs(root, paths, safe_inputs)
    if errors:
        raise ValueError("EXECUTABLE_INPUT_REQUIRES_SEPARATE_POLICY_REVIEW")
    paths = sorted(set(paths) | executable)
    return paths, [{"path": p, "sha256": digest((root / p).read_bytes())} for p in paths]


def propose(root: Path) -> dict:
    policy_bytes = (root / guard.POLICY_PATH).read_bytes()
    policy = json.loads(policy_bytes)
    entries, retired, contracts, safe_inputs = guard.load_policy(root, policy)
    # 外部发现的可执行输入也必须冻结，不能只绑定命令调用者。
    paths, inputs = bound_inputs(root, safe_inputs)
    updated = copy.deepcopy(policy)
    retained = []
    for p, entry in entries.items():
        risk = guard.inspect(root, p, retired) if p in paths else None
        if risk is None:
            # 无匹配语义或已删除的非兼容注册不再是例外；新语义仍需单独注册。
            if entry["kind"] == "WIRE_COMPATIBILITY":
                raise ValueError("CONTRACT_RETIREMENT_REQUIRES_SEPARATE_POLICY_REVIEW")
            continue
        if risk["kind"] != "STAGE_SEMANTICS":
            raise ValueError("PROHIBITED_ACTIVE_SEMANTICS")
        retained.append(dict(entry, sha256=risk["sha256"]))
    updated["exceptions"] = retained
    edges = guard.compatibility_edges(root, paths, contracts)
    for contract in updated["compatibilityContracts"]:
        contract["approvedCallers"] = [
            {"path": p, "callerMember": caller, "contractMember": member}
            for identity, p, caller, member in sorted(edges) if identity == contract["identity"]]
    errors, _, _ = guard.check(root, updated)
    if errors:
        # 生成器不能添加例外、允许新阶段资产或降低 validator 严格度。
        raise ValueError("CANDIDATE_REQUIRES_SEPARATE_POLICY_REVIEW")
    if inputs != bound_inputs(root, safe_inputs)[1] or policy_bytes != (root / guard.POLICY_PATH).read_bytes():
        raise ValueError("CANDIDATE_CHANGED_DURING_PROPOSAL")
    return {"schemaVersion": 1, "beforePolicySha256": digest(policy_bytes),
            "candidate": inputs, "policy": updated}


def apply_reviewed(root: Path, proposal: Path, reviewed_sha256: str) -> None:
    raw = proposal.read_bytes()
    if digest(raw) != reviewed_sha256:
        raise ValueError("REVIEWED_PROPOSAL_DIGEST_MISMATCH")
    plan = json.loads(raw)
    # 完整重建比较包含旧注册、动态候选与生成结果；拒绝伪造输出、旧提案和遗漏。
    if plan != propose(root):
        raise ValueError("REVIEWED_PROPOSAL_CANDIDATE_MISMATCH")
    target = root / guard.POLICY_PATH
    output = encode(plan["policy"])
    # 临时文件放在同一文件系统但不属于 active inventory，避免自身触发未知输入。
    descriptor, temporary = tempfile.mkstemp(prefix=".asset-registration-", suffix=".tmp", dir=root)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(output)
            stream.flush()
            os.fsync(stream.fileno())
        # 保持工作区静止是调用者合同；最后一次比较提供 expected-state 防护。
        if plan["candidate"] != bound_inputs(root, plan["policy"]["safeControlPlaneInputs"])[1]:
            raise ValueError("CANDIDATE_CHANGED_BEFORE_APPLY")
        os.replace(temporary, target)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    commands = parser.add_subparsers(dest="command", required=True)
    create = commands.add_parser("propose")
    create.add_argument("--output", type=Path, required=True)
    apply = commands.add_parser("apply")
    apply.add_argument("--proposal", type=Path, required=True)
    apply.add_argument("--reviewed-proposal-sha256", required=True)
    args = parser.parse_args()
    try:
        root = args.root.resolve()
        if args.command == "propose":
            raw = encode(propose(root))
            # 使用排他创建，禁止覆盖既有审查制品。
            with args.output.open("xb") as stream:
                stream.write(raw)
            print("PROPOSAL_ONLY sha256=" + digest(raw))
        else:
            apply_reviewed(root, args.proposal, args.reviewed_proposal_sha256)
            print("REVIEWED_REGISTRATION_APPLIED")
        return 0
    except (OSError, ValueError, TypeError, KeyError, subprocess.SubprocessError) as error:
        print("ASSET_LIFECYCLE_FAILED_CLOSED: " + type(error).__name__)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
