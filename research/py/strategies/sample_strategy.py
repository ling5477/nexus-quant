from __future__ import annotations

from dataclasses import dataclass
from typing import Iterable


@dataclass(frozen=True)
class Bar:
    symbol: str
    interval: str
    open_time: str
    close_time: str
    open_price: float
    high_price: float
    low_price: float
    close_price: float
    volume: float


def run_strategy(bars: Iterable[Bar]) -> dict:
    """返回最小输入摘要，供 GateF-2 研究骨架验证使用。"""
    materialized = list(bars)
    return {
        "bar_count": len(materialized),
        "first_bar_time": materialized[0].open_time if materialized else None,
        "last_bar_time": materialized[-1].close_time if materialized else None,
    }
