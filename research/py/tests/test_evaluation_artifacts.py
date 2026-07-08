import socket
from pathlib import Path

import pytest

from nq_research.evaluation.artifacts import (
    EVALUATION_ARTIFACT_SCHEMA_VERSION,
    FAKE_METRICS_FIXTURE,
    build_evaluation_artifact,
    compute_checksum,
    contains_forbidden_sensitive_fields,
    read_evaluation_artifact,
    validate_artifact,
    write_evaluation_artifact,
)
from nq_research.evaluation.parameters import JsonValue, build_parameter_set_id, expand_parameter_grid

GENERATED_AT = "2026-07-08T00:00:00Z"


def fake_metric_summary() -> dict[str, JsonValue]:
    return {
        "totalReturn": 0.0123,
        "maxDrawdown": -0.02,
        "volatility": 0.15,
        "sharpe": 1.1,
        "winRate": 0.55,
        "turnover": 0.8,
        "tradeCount": 12,
        "feeCost": 1.25,
        "slippageCost": 0.75,
        "metricSource": FAKE_METRICS_FIXTURE,
        "realTradingPerformance": False,
    }


def build_valid_artifact_dict() -> dict[str, JsonValue]:
    artifact = build_evaluation_artifact(
        experiment_id="exp_fixture",
        strategy_id="sample_strategy",
        strategy_version="v1",
        dataset_id="ds_fixture",
        dataset_version="dataset.v1",
        parameters={"threshold": 0.2, "window": 20},
        metric_summary=fake_metric_summary(),
        cost_assumptions={"feeRateBps": 5, "source": "fixture"},
        slippage_assumptions={"slippageBps": 2, "source": "fixture"},
        validation_warnings=("fixture metrics only; not a real strategy performance claim",),
        limitations=("offline fixture only", "not connected to live execution"),
        generated_at=GENERATED_AT,
    )
    return artifact.to_dict()


def test_parameter_grid_expands_with_stable_key_order() -> None:
    parameter_sets = expand_parameter_grid({"window": [20, 50], "threshold": [0.1, 0.2]})

    assert [item.parameters for item in parameter_sets] == [
        {"threshold": 0.1, "window": 20},
        {"threshold": 0.1, "window": 50},
        {"threshold": 0.2, "window": 20},
        {"threshold": 0.2, "window": 50},
    ]
    assert [item.parameter_set_id for item in parameter_sets] == [
        build_parameter_set_id(item.parameters) for item in parameter_sets
    ]


def test_parameter_set_id_is_stable_for_equivalent_parameter_order() -> None:
    first = build_parameter_set_id({"window": 20, "threshold": 0.2})
    second = build_parameter_set_id({"threshold": 0.2, "window": 20})

    assert first == second
    assert first.startswith("pset_")
    assert expand_parameter_grid({})[0].parameters == {}


def test_artifact_can_be_written_and_read_back(tmp_path: Path) -> None:
    artifact = build_valid_artifact_dict()
    output_path = tmp_path / "artifact.json"

    write_evaluation_artifact(output_path, artifact)
    loaded = read_evaluation_artifact(output_path)

    assert loaded == artifact
    assert validate_artifact(loaded).is_valid
    assert output_path.read_text(encoding="utf-8").endswith("\n")


def test_checksum_is_stable_and_excludes_checksum_field() -> None:
    artifact = build_valid_artifact_dict()
    checksum = artifact["checksum"]

    assert checksum == compute_checksum(artifact)
    assert checksum == compute_checksum({key: value for key, value in artifact.items() if key != "checksum"})


def test_checksum_tampering_is_detected() -> None:
    artifact = build_valid_artifact_dict()
    metric_summary_value = artifact["metricSummary"]
    assert isinstance(metric_summary_value, dict)
    metric_summary = dict(metric_summary_value)
    metric_summary["tradeCount"] = 999
    artifact["metricSummary"] = metric_summary

    result = validate_artifact(artifact)

    assert not result.is_valid
    assert "checksum does not match artifact payload" in result.errors


@pytest.mark.parametrize("field_name", ["schemaVersion", "artifactId", "experimentId"])
def test_required_identity_fields_missing_fail_validation(field_name: str) -> None:
    artifact = build_valid_artifact_dict()
    artifact.pop(field_name)

    result = validate_artifact(artifact)

    assert not result.is_valid
    assert f"missing required field: {field_name}" in result.errors


def test_diagnostic_only_must_be_true() -> None:
    artifact = build_valid_artifact_dict()
    artifact["diagnosticOnly"] = False

    result = validate_artifact(artifact)

    assert not result.is_valid
    assert "diagnosticOnly must be true" in result.errors


def test_not_trading_authorization_must_be_true() -> None:
    artifact = build_valid_artifact_dict()
    artifact["notTradingAuthorization"] = False

    result = validate_artifact(artifact)

    assert not result.is_valid
    assert "notTradingAuthorization must be true" in result.errors


def test_live_execution_ready_must_be_false() -> None:
    artifact = build_valid_artifact_dict()
    artifact["liveExecutionReady"] = True

    result = validate_artifact(artifact)

    assert not result.is_valid
    assert "liveExecutionReady must be false" in result.errors


def test_forbidden_sensitive_fields_fail_validation() -> None:
    artifact = build_valid_artifact_dict()
    forbidden_key = "api" + "Key"
    artifact["parameters"] = {forbidden_key: "redacted-fixture"}

    result = validate_artifact(artifact)

    assert contains_forbidden_sensitive_fields(artifact)
    assert not result.is_valid
    assert any(error.startswith("forbidden sensitive field names found:") for error in result.errors)


def test_fake_metrics_cannot_be_marked_as_real_trading_performance() -> None:
    artifact = build_valid_artifact_dict()
    metric_summary_value = artifact["metricSummary"]
    assert isinstance(metric_summary_value, dict)
    metric_summary = dict(metric_summary_value)
    metric_summary["realTradingPerformance"] = True
    artifact["metricSummary"] = metric_summary

    result = validate_artifact(artifact)

    assert not result.is_valid
    assert "metricSummary.realTradingPerformance must be false" in result.errors


def test_artifact_operations_do_not_create_network_connections(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    def fail_network(*args: object, **kwargs: object) -> socket.socket:
        raise AssertionError("network access is not allowed for offline evaluation artifacts")

    monkeypatch.setattr(socket, "create_connection", fail_network)
    artifact = build_valid_artifact_dict()
    output_path = tmp_path / "artifact.json"

    write_evaluation_artifact(output_path, artifact)
    loaded = read_evaluation_artifact(output_path)
    result = validate_artifact(loaded)

    assert loaded["schemaVersion"] == EVALUATION_ARTIFACT_SCHEMA_VERSION
    assert result.is_valid
