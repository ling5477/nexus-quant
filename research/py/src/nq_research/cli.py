from __future__ import annotations

import argparse
import csv
import json
from collections.abc import Sequence
from pathlib import Path

from nq_research.data.models import Bar
from nq_research.strategy.sample_strategy import run_strategy


def main(argv: Sequence[str] | None = None) -> int:
    """Run the offline research CLI.

    Why:
    research/py is intentionally an offline subsystem. The CLI only reads local CSV bars
    and prints a deterministic summary, so it provides a stable executable entrypoint
    without coupling Python code to live trading, auth, recovery, or ledger runtime paths.
    """
    parser = argparse.ArgumentParser(prog="nq-research", description="NexusQuant offline research utilities.")
    parser.add_argument("--bars-csv", type=Path, help="Local CSV with bar columns produced by research fixtures.")
    args = parser.parse_args(argv)

    if args.bars_csv is None:
        parser.print_help()
        return 0

    bars = load_bars(resolve_bars_path(args.bars_csv))
    print(json.dumps(run_strategy(bars), ensure_ascii=False, sort_keys=True))
    return 0


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
        return [
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
