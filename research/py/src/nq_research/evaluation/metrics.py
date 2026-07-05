"""离线 evaluation metrics skeleton。

当前只计算本地 bars 可以直接支持的基础指标；缺少成交、仓位或交易明细时返回
`NOT_AVAILABLE`，避免把未具备的数据伪造成完整回测评估结果。
"""

from __future__ import annotations

from collections.abc import Iterable
from dataclasses import dataclass

from nq_research.data.models import Bar

NOT_AVAILABLE = "NOT_AVAILABLE"
MetricValue = float | str


@dataclass(frozen=True)
class EvaluationMetrics:
    """研究运行的基础评估指标。

    用途：为后续策略验证提供稳定字段集合。
    Why：当前 Python research 不是完整 backtest engine；只能从 bars 计算
    total_return、max_drawdown、bar_count 和时间范围，其余指标显式为 `NOT_AVAILABLE`。
    边界：这些指标不代表可交易、LIVE ready、风控通过或真实交易所执行能力。
    """

    total_return: MetricValue
    annualized_return: MetricValue
    max_drawdown: MetricValue
    win_rate: MetricValue
    profit_factor: MetricValue
    turnover: MetricValue
    exposure: MetricValue
    bar_count: int
    start_time: str | None
    end_time: str | None

    def to_dict(self) -> dict[str, object]:
        """返回稳定 summary 字段。

        Why：CLI JSON 与文本报告共用该结构，后续新增真实成交级指标时也能保持字段名稳定。
        """

        return {
            "total_return": self.total_return,
            "annualized_return": self.annualized_return,
            "max_drawdown": self.max_drawdown,
            "win_rate": self.win_rate,
            "profit_factor": self.profit_factor,
            "turnover": self.turnover,
            "exposure": self.exposure,
            "bar_count": self.bar_count,
            "start_time": self.start_time,
            "end_time": self.end_time,
        }


def evaluate_bars(bars: Iterable[Bar]) -> EvaluationMetrics:
    """从离线 bars 生成 evaluation skeleton。

    Why：当前输入缺少 trade list、fees、position 和 capital model，因此只计算 bars
    能支撑的最小指标；无法计算的指标返回 `NOT_AVAILABLE`，不静默填 0。
    失败模式：空 bars 返回 bar_count=0 且所有收益类指标 `NOT_AVAILABLE`。
    """

    materialized = list(bars)
    if not materialized:
        return EvaluationMetrics(
            total_return=NOT_AVAILABLE,
            annualized_return=NOT_AVAILABLE,
            max_drawdown=NOT_AVAILABLE,
            win_rate=NOT_AVAILABLE,
            profit_factor=NOT_AVAILABLE,
            turnover=NOT_AVAILABLE,
            exposure=NOT_AVAILABLE,
            bar_count=0,
            start_time=None,
            end_time=None,
        )

    total_return = _calculate_total_return(materialized)
    max_drawdown = _calculate_max_drawdown(materialized)
    return EvaluationMetrics(
        total_return=total_return,
        annualized_return=NOT_AVAILABLE,
        max_drawdown=max_drawdown,
        win_rate=NOT_AVAILABLE,
        profit_factor=NOT_AVAILABLE,
        turnover=NOT_AVAILABLE,
        exposure=NOT_AVAILABLE,
        bar_count=len(materialized),
        start_time=materialized[0].open_time,
        end_time=materialized[-1].close_time,
    )


def _calculate_total_return(bars: list[Bar]) -> MetricValue:
    first_open = bars[0].open_price
    if first_open == 0:
        return NOT_AVAILABLE
    return (bars[-1].close_price / first_open) - 1.0


def _calculate_max_drawdown(bars: list[Bar]) -> MetricValue:
    peak = bars[0].close_price
    if peak == 0:
        return NOT_AVAILABLE

    max_drawdown = 0.0
    for bar in bars:
        if bar.close_price > peak:
            peak = bar.close_price
        if peak == 0:
            return NOT_AVAILABLE
        drawdown = (bar.close_price / peak) - 1.0
        max_drawdown = min(max_drawdown, drawdown)
    return max_drawdown
