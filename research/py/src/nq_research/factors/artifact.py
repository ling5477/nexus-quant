"""独立于交易评价合同的诊断产物，固定时间和种子时完整字节可重复。"""

from __future__ import annotations

from typing import Any

import numpy as np

from nq_research.dataset.frozen import FrozenDataset, compact, digest, timestamp
from nq_research.factors.calculation import FACTOR_VERSION, KEYS, calculate, parameters
from nq_research.factors.statistics import evaluate

SCHEMA = "python-factor-research-artifact.v1"


def build_artifact(dataset: FrozenDataset, *, generated_at: str, window: int = 24, horizon: int = 6,
                   seed: int = 42, repetitions: int = 200) -> dict[str, Any]:
    if timestamp(generated_at) < timestamp(dataset.frozen_at):
        raise ValueError("ARTIFACT_PRECEDES_FROZEN_INPUT")
    factors = []
    for key in KEYS:
        points = calculate(dataset, key, window)
        factors.append({"factorKey": key, "factorVersion": FACTOR_VERSION, "parameters": parameters(key, window),
                        "datasetVersionId": dataset.identity["datasetVersionId"],
                        "sourceChecksum": dataset.identity["sourceSha256"],
                        "points": [p.to_dict() for p in points],
                        "statistics": evaluate(dataset, points, horizon=horizon, window=window,
                                               seed=seed, repetitions=repetitions)})
    artifact: dict[str, Any] = {
        "schemaVersion": SCHEMA, "dataset": dataset.identity, "barCount": len(dataset.bars), "factors": factors,
        "observationAssumptions": {"sourceObservedAt": "ACTUAL_COLLECTION_OBSERVATION",
                                    "featureAvailableAt": "CLOSED_HOURLY_BOUNDARY_V1",
                                    "replayVisibilitySource": "EXPERIMENT_ASSUMPTION",
                                    "frozenAt": dataset.frozen_at},
        "limitations": ["Exploratory single-asset hourly sample; no trading advantage or profitability claim.",
                        "Replay visibility is an assumption, not historical point-in-time publication evidence.",
                        "Block bootstrap assumes local dependence and stationarity; regime changes may invalidate intervals.",
                        "No multiple-testing correction, costs, fills, strategy returns or automatic strategy approval."],
        "warnings": ["DIAGNOSTIC_ONLY", "NOT_TRADING_AUTHORIZATION", "NO_AUTOMATIC_STRATEGY_APPROVAL"],
        "source": "PYTHON_OFFLINE_RESEARCH", "generatedAt": generated_at,
        "implementationVersion": "factor-research-1.0.0", "numericalRuntime": {"numpy": np.__version__},
        "diagnosticOnly": True, "notTradingAuthorization": True, "liveExecutionReady": False}
    artifact["artifactId"] = "factor-" + digest(compact(artifact, sort=True))
    artifact["checksum"] = digest(compact(artifact, sort=True))
    return artifact
