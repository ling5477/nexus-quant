"""离线 research run summary 输出。

该模块把 dataset manifest、experiment metadata、evaluation skeleton 和 sample
strategy summary 组合为一个稳定报告结构。报告是文件/控制台产物，不写 Java runtime、
不访问网络、不读取 credential，也不表达 LIVE 或交易授权。
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Mapping

from nq_research.dataset.manifest import DatasetManifest
from nq_research.evaluation.metrics import EvaluationMetrics
from nq_research.experiment.metadata import ExperimentMetadata

OFFLINE_BOUNDARY_NOTES = (
    "offline_research_only",
    "no_network_io",
    "no_credential_read",
    "no_java_runtime_write",
    "no_live_trading",
    "no_ai_runtime",
    "no_dh_runtime",
)


@dataclass(frozen=True)
class ResearchRunSummary:
    """一次离线研究运行的标准 summary。

    用途：给 CLI、测试和后续文件产物提供稳定输出格式。
    Why：dataset、experiment 和 evaluation 分散输出会降低复盘可读性；统一 summary
    可以同时满足可复现、可测试、可审计要求。
    边界：summary 中的 offline boundary notes 是保护性声明，不表示任何 runtime 授权。
    """

    dataset_manifest: DatasetManifest
    experiment_metadata: ExperimentMetadata
    evaluation: EvaluationMetrics
    strategy_summary: Mapping[str, object]
    offline_boundary: tuple[str, ...] = OFFLINE_BOUNDARY_NOTES

    def to_dict(self) -> dict[str, object]:
        """返回 CLI JSON summary。

        Why：保留历史顶层 `bar_count / first_bar_time / last_bar_time`，同时新增
        `dataset_id / experiment_id / evaluation`，保证旧 CLI smoke 的读取方式仍兼容。
        """

        strategy_summary = dict(self.strategy_summary)
        return {
            "bar_count": strategy_summary.get("bar_count"),
            "first_bar_time": strategy_summary.get("first_bar_time"),
            "last_bar_time": strategy_summary.get("last_bar_time"),
            "dataset_id": self.dataset_manifest.dataset_id,
            "experiment_id": self.experiment_metadata.experiment_id,
            "dataset_manifest": self.dataset_manifest.to_dict(),
            "experiment_metadata": self.experiment_metadata.to_dict(),
            "evaluation": self.evaluation.to_dict(),
            "strategy_summary": strategy_summary,
            "offline_boundary": list(self.offline_boundary),
        }

    def to_text(self) -> str:
        """返回稳定文本 summary。

        Why：有些人工审阅场景不需要 JSON；文本输出固定 key 顺序，便于 diff 和日志审计。
        """

        evaluation = self.evaluation.to_dict()
        lines = [
            "NexusQuant Research Run Summary",
            f"dataset_id: {self.dataset_manifest.dataset_id}",
            f"experiment_id: {self.experiment_metadata.experiment_id}",
            f"run_mode: {self.experiment_metadata.run_mode}",
            f"symbol: {self.dataset_manifest.symbol}",
            f"exchange: {self.dataset_manifest.exchange}",
            f"market_type: {self.dataset_manifest.market_type}",
            f"interval: {self.dataset_manifest.interval}",
            f"bar_count: {evaluation['bar_count']}",
            f"start_time: {evaluation['start_time']}",
            f"end_time: {evaluation['end_time']}",
            f"total_return: {evaluation['total_return']}",
            f"annualized_return: {evaluation['annualized_return']}",
            f"max_drawdown: {evaluation['max_drawdown']}",
            f"win_rate: {evaluation['win_rate']}",
            f"profit_factor: {evaluation['profit_factor']}",
            f"turnover: {evaluation['turnover']}",
            f"exposure: {evaluation['exposure']}",
            f"offline_boundary: {', '.join(self.offline_boundary)}",
        ]
        return "\n".join(lines)
