"""时间先后切分与标签 purge，采用成对移动块 Bootstrap 保留局部自相关。"""

from __future__ import annotations

from typing import Any
from decimal import localcontext

import numpy as np
from numpy.typing import NDArray

from nq_research.dataset.frozen import FrozenDataset
from nq_research.factors.calculation import FactorPoint
from nq_research.factors.numeric import numeric_context

METHOD = "PURGED_CHRONOLOGICAL_PEARSON_MOVING_BLOCK_V1"


def correlation(x: NDArray[np.float64], y: NDArray[np.float64]) -> float | None:
    mask = np.isfinite(x) & np.isfinite(y)
    x, y = x[mask], y[mask]
    if len(x) < 2:
        return None
    x, y = x - x.mean(), y - y.mean()
    denominator = float(np.linalg.norm(x) * np.linalg.norm(y))
    if denominator == 0:
        return None
    return max(-1.0, min(1.0, float(np.dot(x, y) / denominator)))


def evaluate(dataset: FrozenDataset, points: tuple[FactorPoint, ...], *, horizon: int,
             window: int, seed: int, repetitions: int) -> dict[str, Any]:
    if (type(horizon) is not int or not 1 <= horizon <= 48 or not 100 <= repetitions <= 1000
            or type(seed) is not int or not 0 <= seed <= 2**32 - 1):
        raise ValueError("STATISTICAL_PARAMETERS_INVALID")
    n = len(points)
    split = n * 7 // 10
    # 训练标签的终点必须严格落在验证首个特征窗口之前，边界重叠样本全部丢弃。
    windows = (("TRAIN", 0, split - horizon), ("OUT_OF_SAMPLE", split, n - horizon))
    x = np.array([np.nan if p.value is None else p.value for p in points], dtype=np.float64)
    y = np.full(n, np.nan, dtype=np.float64)
    with localcontext(numeric_context()):
        for i in range(n - horizon):
            y[i] = float(dataset.bars[i + horizon].close / dataset.bars[i].close - 1)
    results = []
    for offset, (name, start, end) in enumerate(windows):
        a, b = x[start:end], y[start:end]
        valid = int(np.count_nonzero(np.isfinite(a) & np.isfinite(b)))
        block = max(window, horizon, int(np.ceil(len(a) ** (1 / 3))))
        minimum = max(60, 5 * block)
        corr = correlation(a, b)
        status = "INSUFFICIENT_SAMPLES" if valid < minimum else "INCONCLUSIVE"
        interval = None
        accepted = 0
        if valid >= minimum and corr is not None:
            rng = np.random.Generator(np.random.PCG64(seed + offset))
            estimates = []
            for _ in range(repetitions):
                starts = rng.integers(0, len(a) - block + 1, size=(len(a) + block - 1) // block)
                indices = (starts[:, None] + np.arange(block)).ravel()[:len(a)]
                estimate = correlation(a[indices], b[indices])
                if estimate is not None:
                    estimates.append(estimate)
            accepted = len(estimates)
            if accepted >= repetitions * 9 // 10:
                lower, upper = np.quantile(estimates, [0.025, 0.975], method="linear")
                interval = {"lower": format(lower, ".17g"), "upper": format(upper, ".17g"), "level": "0.95"}
                if lower > 0 or upper < 0:
                    status = "EXPLORATORY_RELATIONSHIP"
        results.append({"name": name, "startIndex": start, "endIndexExclusive": end,
                        "featureFrom": dataset.bars[start].replay_available_at,
                        "featureTo": dataset.bars[end - 1].replay_available_at,
                        "lastLabelAvailableAt": dataset.bars[end - 1 + horizon].replay_available_at,
                        "candidateSamples": end - start, "effectiveSamples": valid,
                        "missingSamples": end - start - valid, "minimumSamples": minimum,
                        "correlation": None if corr is None else format(corr, ".17g"),
                        "confidenceInterval": interval, "status": status, "blockLength": block,
                        "bootstrapValidReplicates": accepted})
    return {"methodVersion": METHOD, "correlationStatistic": "PEARSON",
            "labelDefinition": "close[t+horizon]/close[t]-1", "horizonBars": horizon,
            "splitIndex": split, "purgedTrainingSamples": horizon, "unlabelledTailSamples": horizon,
            "bootstrap": {"method": "PAIRED_MOVING_BLOCK_PERCENTILE", "repetitions": repetitions,
                          "seed": seed, "rng": "PCG64", "quantileMethod": "linear"}, "windows": results}
