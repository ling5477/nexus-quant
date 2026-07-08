"""离线 evaluation artifact 结构、读写与校验。

该模块只处理本地 JSON artifact。它不访问网络、不读取 credential、不写 Java runtime、
不创建 evaluation / publish / Paper / Shadow run，也不表达 LIVE 或交易授权。
"""

from __future__ import annotations

import json
from collections.abc import Mapping, Sequence
from dataclasses import dataclass, replace
from hashlib import sha256
from pathlib import Path
from typing import cast

from nq_research.evaluation.parameters import JsonValue, build_parameter_set_id, ensure_json_serializable

EVALUATION_ARTIFACT_SCHEMA_VERSION = "python-evaluation-artifact.v1"
DEFAULT_ARTIFACT_SOURCE = "PYTHON_OFFLINE"
FAKE_METRICS_FIXTURE = "FAKE_METRICS_FIXTURE"

REQUIRED_ARTIFACT_FIELDS = (
    "schemaVersion",
    "artifactId",
    "experimentId",
    "strategyId",
    "strategyVersion",
    "datasetId",
    "datasetVersion",
    "parameterSetId",
    "parameters",
    "metricSummary",
    "costAssumptions",
    "slippageAssumptions",
    "validationWarnings",
    "limitations",
    "generatedAt",
    "source",
    "checksum",
    "diagnosticOnly",
    "notTradingAuthorization",
    "liveExecutionReady",
)

REQUIRED_METRIC_FIELDS = (
    "totalReturn",
    "maxDrawdown",
    "volatility",
    "sharpe",
    "winRate",
    "turnover",
    "tradeCount",
    "feeCost",
    "slippageCost",
    "metricSource",
    "realTradingPerformance",
)

FORBIDDEN_SENSITIVE_FIELD_MARKERS = (
    "apikey",
    "secret",
    "passphrase",
    "token",
    "privatekey",
    "credentialmaterial",
    "exchangesecret",
    "realorderid",
    "realaccountbalance",
    "realposition",
    "withdraw",
    "transfer",
)


@dataclass(frozen=True)
class EvaluationArtifact:
    """离线 evaluation artifact。

    用途：把 experiment metadata、dataset / strategy reference、参数集、指标摘要和边界声明
    固化为一个可写入本地 JSON 的研究产物。
    Why：GateS-4 需要最小可复现 artifact baseline；checksum 和强制安全字段可防止后续把
    Python research 误接成 LIVE execution、交易授权或 Java production fact write。
    幂等：调用方固定 generated_at 和内容时，artifact_id、parameter_set_id 与 checksum 稳定。
    副作用：仅在 writer 被显式调用时写本地 JSON 文件；不访问网络或外部系统。
    """

    schema_version: str
    artifact_id: str
    experiment_id: str
    strategy_id: str
    strategy_version: str
    dataset_id: str
    dataset_version: str
    parameter_set_id: str
    parameters: Mapping[str, JsonValue]
    metric_summary: Mapping[str, JsonValue]
    cost_assumptions: Mapping[str, JsonValue]
    slippage_assumptions: Mapping[str, JsonValue]
    validation_warnings: Sequence[str]
    limitations: Sequence[str]
    generated_at: str
    source: str
    checksum: str
    diagnostic_only: bool = True
    not_trading_authorization: bool = True
    live_execution_ready: bool = False

    def to_dict(self, *, include_checksum: bool = True) -> dict[str, JsonValue]:
        """返回稳定 camelCase JSON artifact。

        Why：历史 Java binding preview 使用 camelCase 字段；Python 内部保持 snake_case，
        输出层保持 camelCase 可减少后续只读 contract 对齐成本。
        """

        artifact: dict[str, JsonValue] = {
            "schemaVersion": self.schema_version,
            "artifactId": self.artifact_id,
            "experimentId": self.experiment_id,
            "strategyId": self.strategy_id,
            "strategyVersion": self.strategy_version,
            "datasetId": self.dataset_id,
            "datasetVersion": self.dataset_version,
            "parameterSetId": self.parameter_set_id,
            "parameters": dict(self.parameters),
            "metricSummary": dict(self.metric_summary),
            "costAssumptions": dict(self.cost_assumptions),
            "slippageAssumptions": dict(self.slippage_assumptions),
            "validationWarnings": list(self.validation_warnings),
            "limitations": list(self.limitations),
            "generatedAt": self.generated_at,
            "source": self.source,
            "diagnosticOnly": self.diagnostic_only,
            "notTradingAuthorization": self.not_trading_authorization,
            "liveExecutionReady": self.live_execution_ready,
        }
        if include_checksum:
            artifact["checksum"] = self.checksum
        return artifact


@dataclass(frozen=True)
class ArtifactValidationResult:
    """artifact validation result。

    `is_valid` 只表示 artifact schema/checksum/boundary 自洽，不表示策略收益真实、
    不表示交易批准、不表示 Python ML ready 或 live execution ready。
    """

    errors: tuple[str, ...]

    @property
    def is_valid(self) -> bool:
        """返回是否无校验错误。"""

        return not self.errors


def build_evaluation_artifact(
    *,
    experiment_id: str,
    strategy_id: str,
    strategy_version: str,
    dataset_id: str,
    dataset_version: str,
    parameters: Mapping[str, JsonValue],
    metric_summary: Mapping[str, JsonValue],
    cost_assumptions: Mapping[str, JsonValue],
    slippage_assumptions: Mapping[str, JsonValue],
    validation_warnings: Sequence[str],
    limitations: Sequence[str],
    generated_at: str,
    source: str = DEFAULT_ARTIFACT_SOURCE,
    schema_version: str = EVALUATION_ARTIFACT_SCHEMA_VERSION,
) -> EvaluationArtifact:
    """构建带 checksum 的离线 evaluation artifact。

    Why：artifact_id、parameter_set_id 和 checksum 必须统一生成，避免调用方手写造成
    不可复现或把 checksum 字段自身纳入 checksum。
    """

    parameter_set_id = build_parameter_set_id(parameters)
    artifact_id = build_artifact_id(
        schema_version=schema_version,
        experiment_id=experiment_id,
        strategy_id=strategy_id,
        strategy_version=strategy_version,
        dataset_id=dataset_id,
        dataset_version=dataset_version,
        parameter_set_id=parameter_set_id,
        generated_at=generated_at,
        source=source,
    )
    artifact = EvaluationArtifact(
        schema_version=schema_version,
        artifact_id=artifact_id,
        experiment_id=experiment_id,
        strategy_id=strategy_id,
        strategy_version=strategy_version,
        dataset_id=dataset_id,
        dataset_version=dataset_version,
        parameter_set_id=parameter_set_id,
        parameters=dict(parameters),
        metric_summary=dict(metric_summary),
        cost_assumptions=dict(cost_assumptions),
        slippage_assumptions=dict(slippage_assumptions),
        validation_warnings=tuple(validation_warnings),
        limitations=tuple(limitations),
        generated_at=generated_at,
        source=source,
        checksum="",
    )
    return replace(artifact, checksum=compute_checksum(artifact.to_dict(include_checksum=False)))


def build_artifact_id(
    *,
    schema_version: str,
    experiment_id: str,
    strategy_id: str,
    strategy_version: str,
    dataset_id: str,
    dataset_version: str,
    parameter_set_id: str,
    generated_at: str,
    source: str,
) -> str:
    """生成稳定 artifact id。"""

    identity: dict[str, JsonValue] = {
        "datasetId": dataset_id,
        "datasetVersion": dataset_version,
        "experimentId": experiment_id,
        "generatedAt": generated_at,
        "parameterSetId": parameter_set_id,
        "schemaVersion": schema_version,
        "source": source,
        "strategyId": strategy_id,
        "strategyVersion": strategy_version,
    }
    return "eval_artifact_" + _stable_digest(identity)[:16]


def write_evaluation_artifact(path: Path, artifact: EvaluationArtifact | Mapping[str, JsonValue]) -> None:
    """把 artifact 写入稳定 JSON 文件。

    写入前会执行 schema/checksum/boundary validation。失败时抛出 `ValueError`，避免把
    无效或误带 LIVE / credential / trading-authorization 语义的 artifact 落盘。
    """

    artifact_dict = _artifact_to_dict(artifact)
    validation = validate_artifact(artifact_dict)
    if not validation.is_valid:
        raise ValueError("evaluation artifact validation failed: " + "; ".join(validation.errors))

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(_pretty_json(artifact_dict) + "\n", encoding="utf-8")


def read_evaluation_artifact(path: Path) -> dict[str, JsonValue]:
    """读取本地 JSON evaluation artifact。"""

    with path.open("r", encoding="utf-8") as file:
        loaded = json.load(file)
    if not isinstance(loaded, dict):
        raise ValueError("evaluation artifact JSON must be an object")
    return cast(dict[str, JsonValue], loaded)


def compute_checksum(artifact_without_checksum: Mapping[str, JsonValue]) -> str:
    """计算 artifact checksum。

    `checksum` 字段自身永远不参与 digest，即使调用方传入的 mapping 已包含 checksum。
    """

    payload = {key: value for key, value in artifact_without_checksum.items() if key != "checksum"}
    return sha256(_canonical_json(payload).encode("utf-8")).hexdigest()


def validate_artifact(artifact: Mapping[str, JsonValue]) -> ArtifactValidationResult:
    """校验离线 evaluation artifact。

    校验结论仅用于 artifact 自洽性；即使通过，也不表示收益真实、策略批准、
    LIVE ready、Python ML ready、Python live execution ready 或交易授权。
    """

    errors: list[str] = []
    artifact_dict = dict(artifact)

    for field_name in REQUIRED_ARTIFACT_FIELDS:
        if field_name not in artifact_dict:
            errors.append(f"missing required field: {field_name}")

    for field_name in (
        "schemaVersion",
        "artifactId",
        "experimentId",
        "strategyId",
        "strategyVersion",
        "datasetId",
        "datasetVersion",
        "parameterSetId",
        "generatedAt",
        "source",
        "checksum",
    ):
        value = artifact_dict.get(field_name)
        if field_name in artifact_dict and not _is_non_blank_string(value):
            errors.append(f"{field_name} must be a non-blank string")

    if artifact_dict.get("schemaVersion") != EVALUATION_ARTIFACT_SCHEMA_VERSION:
        errors.append(f"schemaVersion must be {EVALUATION_ARTIFACT_SCHEMA_VERSION}")
    if artifact_dict.get("diagnosticOnly") is not True:
        errors.append("diagnosticOnly must be true")
    if artifact_dict.get("notTradingAuthorization") is not True:
        errors.append("notTradingAuthorization must be true")
    if artifact_dict.get("liveExecutionReady") is not False:
        errors.append("liveExecutionReady must be false")
    if artifact_dict.get("source") != DEFAULT_ARTIFACT_SOURCE:
        errors.append(f"source must be {DEFAULT_ARTIFACT_SOURCE}")

    _validate_json_mapping_field(artifact_dict, "parameters", errors)
    _validate_json_mapping_field(artifact_dict, "costAssumptions", errors)
    _validate_json_mapping_field(artifact_dict, "slippageAssumptions", errors)
    _validate_string_list_field(artifact_dict, "validationWarnings", errors)
    _validate_string_list_field(artifact_dict, "limitations", errors)
    _validate_metric_summary(artifact_dict.get("metricSummary"), errors)

    checksum = artifact_dict.get("checksum")
    if isinstance(checksum, str) and checksum:
        expected_checksum = compute_checksum(artifact_dict)
        if checksum != expected_checksum:
            errors.append("checksum does not match artifact payload")

    forbidden_paths = find_forbidden_sensitive_fields(artifact_dict)
    if forbidden_paths:
        errors.append("forbidden sensitive field names found: " + ", ".join(forbidden_paths))

    return ArtifactValidationResult(errors=tuple(errors))


def contains_forbidden_sensitive_fields(payload: object) -> bool:
    """返回 payload 是否包含禁止的敏感字段名。"""

    return bool(find_forbidden_sensitive_fields(payload))


def find_forbidden_sensitive_fields(payload: object) -> tuple[str, ...]:
    """递归查找 artifact 中禁止的敏感字段名。

    只检查 key/path，不检查环境变量、文件系统或外部来源。命中说明 artifact schema
    存在敏感字段形状，必须在写入前失败。
    """

    findings: list[str] = []

    def walk(value: object, path: str) -> None:
        if isinstance(value, Mapping):
            for raw_key, nested_value in value.items():
                key = str(raw_key)
                nested_path = f"{path}.{key}"
                if _is_forbidden_sensitive_field_name(key):
                    findings.append(nested_path)
                walk(nested_value, nested_path)
        elif isinstance(value, list):
            for index, nested_value in enumerate(value):
                walk(nested_value, f"{path}[{index}]")

    walk(payload, "$")
    return tuple(findings)


def _artifact_to_dict(artifact: EvaluationArtifact | Mapping[str, JsonValue]) -> dict[str, JsonValue]:
    if isinstance(artifact, EvaluationArtifact):
        return artifact.to_dict()
    return dict(artifact)


def _validate_json_mapping_field(payload: Mapping[str, JsonValue], field_name: str, errors: list[str]) -> None:
    value = payload.get(field_name)
    if field_name not in payload:
        return
    if not isinstance(value, dict):
        errors.append(f"{field_name} must be a JSON object")
        return
    try:
        ensure_json_serializable(value)
    except TypeError:
        errors.append(f"{field_name} must be JSON serializable")


def _validate_string_list_field(payload: Mapping[str, JsonValue], field_name: str, errors: list[str]) -> None:
    value = payload.get(field_name)
    if field_name not in payload:
        return
    if not isinstance(value, list) or not all(isinstance(item, str) for item in value):
        errors.append(f"{field_name} must be a list of strings")


def _validate_metric_summary(value: JsonValue | None, errors: list[str]) -> None:
    if not isinstance(value, dict):
        errors.append("metricSummary must be a JSON object")
        return

    for metric_name in REQUIRED_METRIC_FIELDS:
        if metric_name not in value:
            errors.append(f"metricSummary missing required field: {metric_name}")

    try:
        ensure_json_serializable(value)
    except TypeError:
        errors.append("metricSummary must be JSON serializable")

    metric_source = value.get("metricSource")
    if not _is_non_blank_string(metric_source):
        errors.append("metricSummary.metricSource must be a non-blank string")
    elif "LIVE" in str(metric_source).upper() or "REAL_TRADING" in str(metric_source).upper():
        errors.append("metricSummary.metricSource must not claim live or real trading performance")

    if value.get("realTradingPerformance") is not False:
        errors.append("metricSummary.realTradingPerformance must be false")


def _is_forbidden_sensitive_field_name(field_name: str) -> bool:
    normalized = "".join(character for character in field_name.lower() if character.isalnum())
    return any(marker in normalized for marker in FORBIDDEN_SENSITIVE_FIELD_MARKERS)


def _is_non_blank_string(value: JsonValue | None) -> bool:
    return isinstance(value, str) and bool(value.strip())


def _stable_digest(payload: Mapping[str, JsonValue]) -> str:
    return sha256(_canonical_json(payload).encode("utf-8")).hexdigest()


def _canonical_json(payload: Mapping[str, JsonValue]) -> str:
    ensure_json_serializable(dict(payload))
    return json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def _pretty_json(payload: Mapping[str, JsonValue]) -> str:
    ensure_json_serializable(dict(payload))
    return json.dumps(payload, ensure_ascii=False, sort_keys=True, indent=2)
