from __future__ import annotations

import argparse
import csv
import json
import sys
from collections.abc import Sequence
from datetime import UTC, datetime
from pathlib import Path

from nq_research.data.models import Bar
from nq_research.dataset.manifest import build_dataset_manifest_from_csv
from nq_research.evaluation.metrics import evaluate_bars
from nq_research.experiment.metadata import build_experiment_metadata
from nq_research.reporting.summary import ResearchRunSummary
from nq_research.strategy.sample_strategy import run_strategy

REQUIRED_BASE_COLUMNS = frozenset({"symbol", "interval", "open_time", "close_time", "volume"})
PRICE_COLUMN_GROUPS = (
    ("open_price", "open"),
    ("high_price", "high"),
    ("low_price", "low"),
    ("close_price", "close"),
)


def main(argv: Sequence[str] | None = None) -> int:
    """Run the offline research CLI.

    Why:
    research/py is intentionally an offline subsystem. The CLI only reads local CSV bars
    and prints a deterministic summary, so it provides a stable executable entrypoint
    without coupling Python code to live trading, auth, recovery, or ledger runtime paths.
    """
    parser = argparse.ArgumentParser(prog="nq-research", description="NexusQuant offline research utilities.")
    parser.add_argument("--bars-csv", type=Path, help="Local CSV with bar columns produced by research fixtures.")
    parser.add_argument("--summary-format", choices=("json", "text"), default="json", help="Run summary output format.")
    parser.add_argument("--dataset-source", default="LOCAL_CSV", help="Dataset source label stored in the manifest.")
    parser.add_argument("--exchange", default="UNKNOWN", help="Exchange label stored in the dataset manifest.")
    parser.add_argument("--market-type", default="SPOT", help="Market type label stored in the dataset manifest.")
    parser.add_argument("--strategy-id", default="sample_strategy", help="Strategy identifier stored in metadata.")
    parser.add_argument("--strategy-version", default="v0", help="Strategy version stored in metadata.")
    parser.add_argument("--feature-version", default="v0", help="Feature version stored in metadata.")
    parser.add_argument("--evaluation-version", default="v0", help="Evaluation version stored in metadata.")
    parser.add_argument("--parameter", action="append", default=[], help="Repeatable experiment parameter as key=value.")
    parser.add_argument("--git-commit", default=None, help="Optional git commit recorded in experiment metadata.")
    parser.add_argument("--created-at", default=None, help="Optional UTC timestamp used for reproducible tests.")
    parser.add_argument("--notes", action="append", default=[], help="Repeatable note stored in manifest and metadata.")
    args = parser.parse_args(argv)

    if args.bars_csv is None:
        parser.print_help()
        return 0

    try:
        bars_path = resolve_bars_path(args.bars_csv)
        bars = load_bars(bars_path)
        created_at = args.created_at or utc_now_text()
        summary = build_research_run_summary(
            bars_path=bars_path,
            bars=bars,
            created_at=created_at,
            command=build_command(argv),
            dataset_source=args.dataset_source,
            exchange=args.exchange,
            market_type=args.market_type,
            strategy_id=args.strategy_id,
            strategy_version=args.strategy_version,
            feature_version=args.feature_version,
            evaluation_version=args.evaluation_version,
            parameters=parse_parameters(args.parameter),
            git_commit=args.git_commit,
            notes=args.notes,
        )
    except (FileNotFoundError, ValueError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1

    if args.summary_format == "text":
        print(summary.to_text())
    else:
        print(json.dumps(summary.to_dict(), ensure_ascii=False, sort_keys=True))
    return 0


def build_research_run_summary(
    *,
    bars_path: Path,
    bars: Sequence[Bar],
    created_at: str,
    command: str,
    dataset_source: str,
    exchange: str,
    market_type: str,
    strategy_id: str,
    strategy_version: str,
    feature_version: str,
    evaluation_version: str,
    parameters: dict[str, str],
    git_commit: str | None,
    notes: Sequence[str],
) -> ResearchRunSummary:
    """构建标准化 research run summary。

    Why：CLI 需要同时输出旧 sample strategy summary、新 dataset manifest、
    experiment metadata 和 evaluation skeleton。集中在一个 builder 中可减少重复逻辑，
    并保持 offline/no-network/no-credential/no-Java-runtime 边界清晰。
    """

    manifest = build_dataset_manifest_from_csv(
        bars_path,
        bars,
        source=dataset_source,
        exchange=exchange,
        market_type=market_type,
        created_at=created_at,
        notes=notes,
    )
    metadata = build_experiment_metadata(
        dataset_id=manifest.dataset_id,
        strategy_id=strategy_id,
        strategy_version=strategy_version,
        feature_version=feature_version,
        evaluation_version=evaluation_version,
        parameters=parameters,
        created_at=created_at,
        command=command,
        git_commit=git_commit,
        notes=notes,
    )
    return ResearchRunSummary(
        dataset_manifest=manifest,
        experiment_metadata=metadata,
        evaluation=evaluate_bars(bars),
        strategy_summary=run_strategy(bars),
    )


def resolve_bars_path(path: Path) -> Path:
    """Resolve local fixture paths without relying on shell-level PYTHONPATH.

    Why:
    PRE-CLEAN-3B validates the command from `research/py` using a historical
    a parent-directory fixtures argument. The canonical fixture directory is now `research/py/fixtures`,
    so a missing historical fixture path is resolved by filename into the canonical local folder.
    """
    if path.exists():
        return path
    fallback = Path(__file__).resolve().parents[2] / "fixtures" / path.name
    if fallback.exists():
        return fallback
    return path


def load_bars(path: Path) -> list[Bar]:
    """Load local bar fixtures for offline research runs.

    Why:
    The CLI accepts only local files and performs no network/database writes. This keeps the
    Python subsystem firmly in the offline research boundary while still making execution
    repeatable from a single entrypoint.
    """
    if not path.exists():
        raise FileNotFoundError(f"bars csv not found: {path}")

    with path.open("r", encoding="utf-8", newline="") as file:
        reader = csv.DictReader(file)
        validate_bars_csv_schema(reader.fieldnames)
        bars = [
            Bar(
                symbol=require(row, "symbol"),
                interval=require(row, "interval"),
                open_time=require(row, "open_time"),
                close_time=require(row, "close_time"),
                open_price=float(require_any(row, "open_price", "open")),
                high_price=float(require_any(row, "high_price", "high")),
                low_price=float(require_any(row, "low_price", "low")),
                close_price=float(require_any(row, "close_price", "close")),
                volume=float(require(row, "volume")),
            )
            for row in reader
        ]
    if not bars:
        raise ValueError("bars csv has no data rows")
    return bars


def validate_bars_csv_schema(fieldnames: Sequence[str] | None) -> None:
    """校验离线 bars CSV 的最小 schema。

    Why：缺字段时必须明确失败，不能让 loader 返回空结果或在后续指标里伪造
    `NOT_AVAILABLE`。价格列支持历史 `open/high/low/close` 与显式
    `*_price` 两套命名，以保持现有 fixture 兼容。
    """

    normalized = {field.strip() for field in fieldnames or () if field is not None}
    missing = sorted(REQUIRED_BASE_COLUMNS - normalized)
    for primary, fallback in PRICE_COLUMN_GROUPS:
        if primary not in normalized and fallback not in normalized:
            missing.append(f"{primary}|{fallback}")
    if missing:
        raise ValueError(f"bars csv missing required columns: {', '.join(missing)}")


def require(row: dict[str, str], field_name: str) -> str:
    value = row.get(field_name)
    if value is None or not value.strip():
        raise ValueError(f"{field_name} must not be blank")
    return value.strip()


def require_any(row: dict[str, str], primary_field: str, fallback_field: str) -> str:
    value = row.get(primary_field)
    if value is not None and value.strip():
        return value.strip()
    return require(row, fallback_field)


def parse_parameters(entries: Sequence[str]) -> dict[str, str]:
    """解析 CLI `--parameter key=value` 参数。

    Why：参数哈希必须来自稳定、显式的键值集合；缺少 `=` 或空 key 会导致不可审计，
    因此直接返回明确错误。
    """

    parameters: dict[str, str] = {}
    for entry in entries:
        if "=" not in entry:
            raise ValueError(f"parameter must use key=value format: {entry}")
        key, value = entry.split("=", 1)
        normalized_key = key.strip()
        if not normalized_key:
            raise ValueError("parameter key must not be blank")
        parameters[normalized_key] = value.strip()
    return parameters


def build_command(argv: Sequence[str] | None) -> str:
    """构造可审计的 CLI command 字符串。

    Why：metadata 需要记录命令来源，但不能读取 shell history 或环境变量；这里只用
    本次 `main()` 收到的显式 argv，避免误收 credential-bearing 环境信息。
    """

    if argv is None:
        return "nq-research"
    return "nq-research " + " ".join(argv)


def utc_now_text() -> str:
    """返回当前 UTC `Z` timestamp。"""

    return datetime.now(UTC).replace(microsecond=0).isoformat().replace("+00:00", "Z")
