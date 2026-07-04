"""离线 experiment metadata 生成。

该模块只描述一次研究运行的可审计 metadata，不触碰 Java runtime、credential、
真实交易所 SDK 或 LIVE execution。
"""

from __future__ import annotations

import json
from collections.abc import Iterable, Mapping
from dataclasses import dataclass
from hashlib import sha256

RUN_MODE_OFFLINE = "OFFLINE"
EXPERIMENT_SCHEMA_VERSION = "experiment-metadata.v1"


@dataclass(frozen=True)
class ExperimentMetadata:
    """离线研究实验 metadata。

    用途：绑定 dataset、strategy、feature、evaluation、parameters_hash 和执行命令。
    Why：后续复盘需要知道“同一份数据用哪版策略和参数跑出结果”，不能只保留指标。
    幂等性：`experiment_id` 基于稳定身份字段生成；`created_at` 用于审计时间，不参与 ID。
    边界：`run_mode` 当前只能是 `OFFLINE`，不得扩展为 LIVE、runtime bridge 或 Java write path。
    """

    experiment_id: str
    dataset_id: str
    strategy_id: str
    strategy_version: str
    feature_version: str
    evaluation_version: str
    parameters_hash: str
    created_at: str
    command: str
    git_commit: str | None
    run_mode: str
    notes: tuple[str, ...]

    def to_dict(self) -> dict[str, object]:
        """返回稳定 JSON 友好的 metadata 结构。"""

        return {
            "experiment_id": self.experiment_id,
            "dataset_id": self.dataset_id,
            "strategy_id": self.strategy_id,
            "strategy_version": self.strategy_version,
            "feature_version": self.feature_version,
            "evaluation_version": self.evaluation_version,
            "parameters_hash": self.parameters_hash,
            "created_at": self.created_at,
            "command": self.command,
            "git_commit": self.git_commit,
            "run_mode": self.run_mode,
            "notes": list(self.notes),
        }


def build_experiment_metadata(
    *,
    dataset_id: str,
    strategy_id: str,
    strategy_version: str,
    feature_version: str,
    evaluation_version: str,
    parameters: Mapping[str, str],
    created_at: str,
    command: str,
    git_commit: str | None = None,
    run_mode: str = RUN_MODE_OFFLINE,
    notes: Iterable[str] = (),
) -> ExperimentMetadata:
    """构建离线实验 metadata。

    Why：metadata builder 统一生成 `parameters_hash` 和 `experiment_id`，避免 CLI、
    测试或后续批处理各自拼接导致不可复现。当前只允许 `OFFLINE`，如果调用方传入
    其他 run mode，立即 fail-fast，防止 Python research 被误接成 runtime 执行链路。
    """

    if run_mode != RUN_MODE_OFFLINE:
        raise ValueError("experiment run_mode must be OFFLINE")

    parameters_hash = build_parameters_hash(parameters)
    identity = {
        "dataset_id": dataset_id,
        "evaluation_version": evaluation_version,
        "feature_version": feature_version,
        "parameters_hash": parameters_hash,
        "run_mode": run_mode,
        "strategy_id": strategy_id,
        "strategy_version": strategy_version,
    }
    experiment_id = "exp_" + _stable_digest(identity)[:16]
    return ExperimentMetadata(
        experiment_id=experiment_id,
        dataset_id=dataset_id,
        strategy_id=strategy_id,
        strategy_version=strategy_version,
        feature_version=feature_version,
        evaluation_version=evaluation_version,
        parameters_hash=parameters_hash,
        created_at=created_at,
        command=command,
        git_commit=git_commit,
        run_mode=run_mode,
        notes=tuple(notes),
    )


def build_parameters_hash(parameters: Mapping[str, str]) -> str:
    """生成稳定参数哈希。

    Why：实验追溯只需要记录参数集合的稳定身份，不需要把所有参数展开到顶层字段。
    参数值按 key 排序并使用紧凑 JSON 编码，保证相同参数在不同调用顺序下哈希一致。
    """

    return "params_" + _stable_digest(dict(parameters))[:16]


def _stable_digest(payload: Mapping[str, object]) -> str:
    encoded = json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return sha256(encoded).hexdigest()
