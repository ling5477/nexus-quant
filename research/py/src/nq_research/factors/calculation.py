"""只读取当前及过去前缀；参考窗口与收益定义是版本合同的一部分。"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal, localcontext
from typing import Any

from nq_research.dataset.frozen import FrozenDataset
from nq_research.factors.numeric import numeric_context

KEYS = ("RETURN_MOMENTUM", "REALIZED_VOLATILITY", "VOLUME_ANOMALY")
FACTOR_VERSION = "1.0.0"


@dataclass(frozen=True)
class FactorPoint:
    index: int
    window_start: str
    window_end: str
    feature_available_at: str
    value: float | None
    quality: str

    def to_dict(self) -> dict[str, Any]:
        return {"index": self.index, "windowStart": self.window_start, "windowEnd": self.window_end,
                "featureAvailableAt": self.feature_available_at,
                "factorValue": None if self.value is None else format(self.value, ".17g"), "qualityStatus": self.quality}


def parameters(key: str, window: int) -> dict[str, Any]:
    if key not in KEYS or type(window) is not int or not 2 <= window <= 200:
        raise ValueError("FACTOR_PARAMETERS_INVALID")
    definitions = {"RETURN_MOMENTUM": "close[t]/close[t-window]-1",
                   "REALIZED_VOLATILITY": "sample_std(log(close[j]/close[j-1]),j=t-window+1..t)",
                   "VOLUME_ANOMALY": "(volume[t]-mean(volume[t-window:t]))/sample_std(volume[t-window:t])"}
    return {"window": window, "definition": definitions[key], "ddof": 1, "annualized": False}


def calculate(dataset: FrozenDataset, key: str, window: int) -> tuple[FactorPoint, ...]:
    parameters(key, window)
    points = []
    with localcontext(numeric_context()):
        for i, bar in enumerate(dataset.bars):
            value: Decimal | None = None
            quality = "INSUFFICIENT_HISTORY"
            if i >= window:
                quality = "AVAILABLE"
                if key == "RETURN_MOMENTUM":
                    value = bar.close / dataset.bars[i - window].close - 1
                else:
                    samples = ([dataset.bars[j].volume for j in range(i - window, i)] if key == "VOLUME_ANOMALY"
                               else [(dataset.bars[j].close / dataset.bars[j - 1].close).ln()
                                     for j in range(i - window + 1, i + 1)])
                    mean = sum(samples, Decimal(0)) / window
                    std = (sum(((x - mean) ** 2 for x in samples), Decimal(0)) / (window - 1)).sqrt()
                    if key == "VOLUME_ANOMALY":
                        if std == 0:
                            quality = "ZERO_REFERENCE_VARIANCE"
                        else:
                            value = (bar.volume - mean) / std
                    else:
                        value = std
            points.append(FactorPoint(i, dataset.bars[max(0, i - window)].open_time,
                                      bar.close_time, bar.replay_available_at,
                                      None if value is None else float(value), quality))
    return tuple(points)
