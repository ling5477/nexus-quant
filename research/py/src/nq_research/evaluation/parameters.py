"""离线 evaluation parameter grid 工具。

本模块只展开调用方显式提供的本地参数集合，不启动回测 runner、不创建并发任务、
不访问网络/数据库，也不把参数组合解释为交易授权。
"""

from __future__ import annotations

import json
from collections.abc import Mapping, Sequence
from dataclasses import dataclass
from hashlib import sha256
from itertools import product
from typing import TypeAlias

JsonValue: TypeAlias = str | int | float | bool | None | list["JsonValue"] | dict[str, "JsonValue"]


@dataclass(frozen=True)
class ParameterSet:
    """单个离线参数组合。

    用途：为 evaluation artifact 提供稳定 `parameterSetId` 与 JSON 友好的参数映射。
    Why：参数扫描必须可复现，不能依赖调用方 dict 插入顺序或人工描述。
    边界：该结构只描述离线参数，不触发 runner、策略发布、Paper run、Shadow run 或 LIVE。
    """

    parameter_set_id: str
    parameters: dict[str, JsonValue]

    def to_dict(self) -> dict[str, JsonValue]:
        """返回 artifact 使用的 camelCase JSON 字段。"""

        return {
            "parameterSetId": self.parameter_set_id,
            "parameters": dict(self.parameters),
        }


def expand_parameter_grid(grid: Mapping[str, Sequence[JsonValue]]) -> list[ParameterSet]:
    """稳定展开离线参数网格。

    规则：
    - key 按字母序排序，确保不同 dict 插入顺序得到同样组合顺序。
    - value 列表保持调用方给定顺序，避免替调用方重排业务含义。
    - 空 grid 返回一个空参数集，便于“无参数实验”仍有稳定 `parameterSetId`。
    - 任一参数 value 列表为空时 fail-fast，避免静默生成 0 个实验。
    """

    if not grid:
        parameters: dict[str, JsonValue] = {}
        return [ParameterSet(parameter_set_id=build_parameter_set_id(parameters), parameters=parameters)]

    keys = sorted(grid)
    value_options: list[list[JsonValue]] = []
    for key in keys:
        if not key.strip():
            raise ValueError("parameter grid key must not be blank")
        raw_values = grid[key]
        if isinstance(raw_values, str):
            raise ValueError(f"parameter grid values must be a list for key: {key}")
        values = list(raw_values)
        if not values:
            raise ValueError(f"parameter grid values must not be empty for key: {key}")
        for value in values:
            ensure_json_serializable(value)
        value_options.append(values)

    parameter_sets: list[ParameterSet] = []
    for combination in product(*value_options):
        parameters = dict(zip(keys, combination, strict=True))
        parameter_sets.append(
            ParameterSet(parameter_set_id=build_parameter_set_id(parameters), parameters=parameters)
        )
    return parameter_sets


def build_parameter_set_id(parameters: Mapping[str, JsonValue]) -> str:
    """生成稳定 `parameterSetId`。

    Why：artifact 需要绑定参数集合身份；使用 canonical JSON digest 可保证 key 顺序不同但
    参数语义相同时 ID 一致。该 ID 不包含交易、账户或 credential 信息。
    """

    ensure_json_serializable(dict(parameters))
    return "pset_" + stable_digest(dict(parameters))[:16]


def stable_digest(payload: Mapping[str, JsonValue]) -> str:
    """对 JSON payload 生成稳定 SHA-256 digest。"""

    encoded = json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return sha256(encoded).hexdigest()


def ensure_json_serializable(value: object) -> None:
    """确认 value 可被 JSON 序列化。

    边界：只做内存序列化检查，不读取文件、不访问网络、不连接外部系统。
    """

    try:
        json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    except (TypeError, ValueError) as exc:
        raise TypeError("parameter value must be JSON serializable") from exc
