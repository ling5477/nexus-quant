import json
import socket
import subprocess
from pathlib import Path
from typing import Any

import pytest

from nq_research.cli import load_bars, main
from nq_research.dataset.manifest import build_dataset_manifest_from_csv
from nq_research.evaluation.metrics import NOT_AVAILABLE, evaluate_bars
from nq_research.experiment.metadata import build_experiment_metadata

FIXTURE = Path(__file__).resolve().parents[1] / "fixtures" / "btcusdt_1m_sample.csv"
CREATED_AT = "2026-07-04T00:00:00Z"


def test_dataset_manifest_from_local_csv_has_stable_identity() -> None:
    bars = load_bars(FIXTURE)

    first = build_dataset_manifest_from_csv(
        FIXTURE,
        bars,
        source="LOCAL_CSV",
        exchange="OKX",
        market_type="SPOT",
        created_at=CREATED_AT,
    )
    second = build_dataset_manifest_from_csv(
        FIXTURE,
        bars,
        source="LOCAL_CSV",
        exchange="OKX",
        market_type="SPOT",
        created_at=CREATED_AT,
    )

    assert first.dataset_id == second.dataset_id
    assert first.checksum == second.checksum
    assert len(first.checksum) == 64
    assert first.source == "LOCAL_CSV"
    assert first.symbol == "BTCUSDT"
    assert first.exchange == "OKX"
    assert first.market_type == "SPOT"
    assert first.interval == "1m"
    assert first.row_count == 6
    assert first.gap_count == 0
    assert first.quality_status == "OK"


def test_experiment_metadata_binds_dataset_strategy_version_and_parameters_hash() -> None:
    first = build_experiment_metadata(
        dataset_id="ds_test",
        strategy_id="sample_strategy",
        strategy_version="v1",
        feature_version="features.v1",
        evaluation_version="eval.v1",
        parameters={"threshold": "0.1", "window": "20"},
        created_at=CREATED_AT,
        command="nq-research --bars-csv fixture.csv",
    )
    second = build_experiment_metadata(
        dataset_id="ds_test",
        strategy_id="sample_strategy",
        strategy_version="v1",
        feature_version="features.v1",
        evaluation_version="eval.v1",
        parameters={"window": "20", "threshold": "0.1"},
        created_at=CREATED_AT,
        command="nq-research --bars-csv fixture.csv",
    )

    assert first.experiment_id == second.experiment_id
    assert first.dataset_id == "ds_test"
    assert first.strategy_version == "v1"
    assert first.parameters_hash == second.parameters_hash
    assert first.parameters_hash.startswith("params_")
    assert first.run_mode == "OFFLINE"


def test_evaluation_skeleton_outputs_bar_range_and_not_available_metrics() -> None:
    metrics = evaluate_bars(load_bars(FIXTURE))

    assert metrics.bar_count == 6
    assert metrics.start_time == "2025-01-01T00:00:00Z"
    assert metrics.end_time == "2025-01-01T00:05:59Z"
    assert metrics.annualized_return == NOT_AVAILABLE
    assert metrics.win_rate == NOT_AVAILABLE
    assert metrics.profit_factor == NOT_AVAILABLE
    assert metrics.turnover == NOT_AVAILABLE
    assert metrics.exposure == NOT_AVAILABLE
    assert isinstance(metrics.total_return, float)
    assert isinstance(metrics.max_drawdown, float)


def test_cli_outputs_json_summary_with_dataset_experiment_and_evaluation(capsys: Any) -> None:
    exit_code = main(
        [
            "--bars-csv",
            "fixtures/btcusdt_1m_sample.csv",
            "--created-at",
            CREATED_AT,
            "--exchange",
            "OKX",
            "--market-type",
            "SPOT",
            "--strategy-version",
            "v1",
            "--parameter",
            "window=20",
        ]
    )

    captured = capsys.readouterr()
    result = json.loads(captured.out)

    assert exit_code == 0
    assert result["dataset_id"] == result["dataset_manifest"]["dataset_id"]
    assert result["experiment_id"] == result["experiment_metadata"]["experiment_id"]
    assert result["dataset_manifest"]["created_at"] == CREATED_AT
    assert result["experiment_metadata"]["dataset_id"] == result["dataset_id"]
    assert result["experiment_metadata"]["strategy_version"] == "v1"
    assert result["experiment_metadata"]["run_mode"] == "OFFLINE"
    assert result["evaluation"]["bar_count"] == 6
    assert result["evaluation"]["max_drawdown"] <= 0


def test_cli_outputs_stable_text_summary(capsys: Any) -> None:
    exit_code = main(
        [
            "--bars-csv",
            "fixtures/btcusdt_1m_sample.csv",
            "--summary-format",
            "text",
            "--created-at",
            CREATED_AT,
        ]
    )

    captured = capsys.readouterr()

    assert exit_code == 0
    assert "NexusQuant Research Run Summary" in captured.out
    assert "dataset_id: ds_" in captured.out
    assert "experiment_id: exp_" in captured.out
    assert "run_mode: OFFLINE" in captured.out
    assert "win_rate: NOT_AVAILABLE" in captured.out


def test_cli_missing_required_csv_field_returns_clear_error(tmp_path: Path, capsys: Any) -> None:
    bad_csv = tmp_path / "missing_close_time.csv"
    bad_csv.write_text(
        "symbol,interval,open_time,open,high,low,close,volume\n"
        "BTCUSDT,1m,2025-01-01T00:00:00Z,1,2,1,2,10\n",
        encoding="utf-8",
    )

    exit_code = main(["--bars-csv", str(bad_csv)])

    captured = capsys.readouterr()
    assert exit_code == 1
    assert "bars csv missing required columns: close_time" in captured.err
    assert captured.out == ""


def test_cli_empty_csv_returns_clear_error(tmp_path: Path, capsys: Any) -> None:
    empty_csv = tmp_path / "empty.csv"
    empty_csv.write_text("symbol,interval,open_time,close_time,open,high,low,close,volume\n", encoding="utf-8")

    exit_code = main(["--bars-csv", str(empty_csv)])

    captured = capsys.readouterr()
    assert exit_code == 1
    assert "bars csv has no data rows" in captured.err
    assert captured.out == ""


def test_cli_does_not_require_network_credentials_or_java_runtime(monkeypatch: pytest.MonkeyPatch, capsys: Any) -> None:
    monkeypatch.setenv("NQ_TEST_SECRET", "should-not-appear")

    def fail_network(*args: object, **kwargs: object) -> socket.socket:
        raise AssertionError("network access is not allowed for offline research CLI")

    def fail_subprocess(*args: object, **kwargs: object) -> subprocess.CompletedProcess[str]:
        raise AssertionError("Java runtime subprocess is not allowed for offline research CLI")

    monkeypatch.setattr(socket, "create_connection", fail_network)
    monkeypatch.setattr(subprocess, "run", fail_subprocess)

    exit_code = main(["--bars-csv", "fixtures/btcusdt_1m_sample.csv", "--created-at", CREATED_AT])

    captured = capsys.readouterr()
    assert exit_code == 0
    assert "should-not-appear" not in captured.out
    assert "no_network_io" in captured.out
    assert "no_credential_read" in captured.out
    assert "no_java_runtime_write" in captured.out
