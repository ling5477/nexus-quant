"""独立数学 oracle、正式 Java 导出身份和时间因果反例。"""

from __future__ import annotations

import copy
import json
import math
import statistics
from dataclasses import replace
from decimal import Decimal, ROUND_DOWN, localcontext
from pathlib import Path
from typing import Any

import pytest

from nq_research.cli import main
from nq_research.dataset.frozen import FrozenDataset, compact, digest, load_frozen, strict_json, validate_export
from nq_research.factors.artifact import build_artifact
from nq_research.factors.calculation import KEYS, calculate
from nq_research.factors.statistics import evaluate

FIXTURE = Path(__file__).parents[1] / "fixtures" / "java-frozen-v2.json"


@pytest.fixture(scope="module")
def dataset() -> FrozenDataset:
    return load_frozen(FIXTURE)


def test_java_v2_exact_identity_and_distinct_observation(dataset: FrozenDataset) -> None:
    export = json.loads(FIXTURE.read_text(encoding="utf-8"))
    reference = export["dataset"]["frozenInput"]
    assert len(dataset.bars) == 721
    assert dataset.identity["contentSha256"] == reference["sha256"]
    assert dataset.identity["sourceSha256"] == reference["sourceSha256"]
    assert dataset.identity["contentSha256"] != dataset.identity["sourceSha256"]
    assert dataset.bars[0].source_observed_at == "2026-10-29T00:00:00Z"
    assert dataset.bars[0].replay_available_at == "2026-04-01T01:00:00Z"


@pytest.mark.parametrize("key", KEYS)
def test_independent_numeric_oracles(dataset: FrozenDataset, key: str) -> None:
    bars = tuple(replace(bar, close=Decimal(value), volume=Decimal(volume))
                 for bar, value, volume in zip(dataset.bars, [100, 105, 99, 110, 120], [1, 3, 2, 7, 4]))
    points = calculate(replace(dataset, bars=bars), key, 3)
    assert all(point.value is None for point in points[:3])
    if key == "RETURN_MOMENTUM":
        oracle = 110 / 100 - 1
    elif key == "REALIZED_VOLATILITY":
        oracle = statistics.stdev([math.log(105 / 100), math.log(99 / 105), math.log(110 / 99)])
    else:
        oracle = (7 - statistics.mean([1, 3, 2])) / statistics.stdev([1, 3, 2])
    assert points[3].value == pytest.approx(oracle, abs=1e-14)
    assert points[3].window_start == bars[0].open_time
    assert points[3].feature_available_at == bars[3].replay_available_at


@pytest.mark.parametrize("key", KEYS)
def test_future_perturbation_cannot_change_prefix(dataset: FrozenDataset, key: str) -> None:
    original = calculate(dataset, key, 24)
    changed = tuple(bar if i < 400 else replace(bar, close=bar.close * (3 + i % 7), volume=bar.volume * (10 + i % 7))
                    for i, bar in enumerate(dataset.bars))
    perturbed = calculate(replace(dataset, bars=changed), key, 24)
    assert original[:400] == perturbed[:400]
    assert original[450:] != perturbed[450:] or key == "REALIZED_VOLATILITY"


def test_zero_variance_volume_is_unavailable(dataset: FrozenDataset) -> None:
    constant = replace(dataset, bars=tuple(replace(bar, volume=Decimal(0)) for bar in dataset.bars[:40]))
    assert calculate(constant, "VOLUME_ANOMALY", 24)[24].quality == "ZERO_REFERENCE_VARIANCE"
    assert calculate(constant, "VOLUME_ANOMALY", 24)[24].value is None


def rehash_export(export: dict[str, Any]) -> None:
    manifest = strict_json(export["manifestJson"])
    reference = export["dataset"]["frozenInput"]
    rows, observations = [], []
    total = 0
    for raw, descriptor in zip(export["chunkJson"], manifest["chunks"], strict=True):
        descriptor["sha256"] = digest(raw)
        descriptor["bytes"] = len(raw.encode("utf-8"))
        total += descriptor["bytes"]
        chunk = strict_json(raw)
        rows.extend(chunk["bars"])
        observations.extend(chunk["sourceObservedAt"])
    reference["sha256"] = manifest["sha256"] = digest(compact(rows))
    reference["sourceSha256"] = manifest["sourceSha256"] = digest(compact(
        [dict(row, availableAt=at) for row, at in zip(rows, observations, strict=True)]))
    reference["totalBytes"] = manifest["totalBytes"] = total
    export["manifestJson"] = compact(manifest)
    reference["manifestSha256"] = digest(export["manifestJson"])


@pytest.mark.parametrize("mutation", ["digest", "manifest", "source", "identity", "missing", "reorder", "duplicate",
                                      "negative", "nan", "unclosed", "observation", "too_many", "scope", "chunk"])
def test_invalid_input_fails_without_repair(mutation: str) -> None:
    export = copy.deepcopy(json.loads(FIXTURE.read_text(encoding="utf-8")))
    chunk = strict_json(export["chunkJson"][0])
    if mutation == "digest":
        export["dataset"]["frozenInput"]["sha256"] = "0" * 64
    elif mutation == "manifest":
        export["manifestJson"] += " "
    elif mutation == "source":
        chunk["sourceObservedAt"][0] = "2026-10-28T00:00:00Z"
        export["chunkJson"][0] = compact(chunk)
    elif mutation == "identity":
        export["dataset"]["datasetId"] = "00000000-0000-0000-0000-000000000001"
    elif mutation == "scope":
        export["dataset"]["symbol"] = "SOL-USDT"
    elif mutation == "too_many":
        export["chunkJson"] *= 10
    else:
        if mutation == "missing":
            del chunk["bars"][1]
            del chunk["sourceObservedAt"][1]
        elif mutation == "reorder":
            chunk["bars"][1], chunk["bars"][2] = chunk["bars"][2], chunk["bars"][1]
        elif mutation == "duplicate":
            chunk["bars"][1] = chunk["bars"][0]
        elif mutation == "negative":
            chunk["bars"][1]["volume"] = "-1"
        elif mutation == "nan":
            chunk["bars"][1]["closePrice"] = "NaN"
        elif mutation == "unclosed":
            chunk["bars"][1]["rawPayloadJson"] = '["0","0","0","0","0","0","0","0","0"]'
        elif mutation == "observation":
            chunk["sourceObservedAt"][0] = "2026-04-01T00:00:00Z"
        elif mutation == "chunk":
            chunk["startBar"] = 2
        export["chunkJson"][0] = compact(chunk)
        rehash_export(export)
    with pytest.raises((ValueError, KeyError, TypeError)):
        validate_export(export)


def test_purged_training_labels_and_out_of_sample_statistics(dataset: FrozenDataset) -> None:
    points = calculate(dataset, "RETURN_MOMENTUM", 24)
    result = evaluate(dataset, points, horizon=6, window=24, seed=42, repetitions=100)
    train, validation = result["windows"]
    assert train["endIndexExclusive"] + 6 == validation["startIndex"]
    assert train["lastLabelAvailableAt"] < validation["featureFrom"]
    assert validation["effectiveSamples"] == 721 - result["splitIndex"] - 6
    pairs = [(p.value, float(dataset.bars[p.index + 6].close / dataset.bars[p.index].close - 1))
             for p in points[validation["startIndex"]:validation["endIndexExclusive"]] if p.value is not None]
    x, y = zip(*pairs, strict=True)
    assert float(validation["correlation"]) == pytest.approx(statistics.correlation(x, y), abs=1e-14)
    changed = replace(dataset, bars=tuple(bar if i < result["splitIndex"] else replace(bar, close=bar.close * 5)
                                        for i, bar in enumerate(dataset.bars)))
    assert evaluate(changed, calculate(changed, "RETURN_MOMENTUM", 24), horizon=6, window=24,
                    seed=42, repetitions=100)["windows"][0] == train


def test_insufficient_samples_and_bootstrap_reproducibility(dataset: FrozenDataset) -> None:
    points = calculate(dataset, "RETURN_MOMENTUM", 200)
    result = evaluate(dataset, points, horizon=48, window=200, seed=42, repetitions=100)
    assert all(window["status"] == "INSUFFICIENT_SAMPLES" for window in result["windows"])
    points = calculate(dataset, "RETURN_MOMENTUM", 24)
    first = evaluate(dataset, points, horizon=6, window=24, seed=42, repetitions=100)
    assert first == evaluate(dataset, points, horizon=6, window=24, seed=42, repetitions=100)
    assert first["windows"][1]["blockLength"] >= 24
    assert first["windows"][1]["confidenceInterval"] is not None


def test_full_artifact_and_cli_repeated_bytes(dataset: FrozenDataset, tmp_path: Path) -> None:
    first = build_artifact(dataset, generated_at="2026-10-30T23:59:59Z", repetitions=100)
    assert first == build_artifact(dataset, generated_at="2026-10-30T23:59:59Z", repetitions=100)
    unsigned = dict(first)
    assert unsigned.pop("checksum") == digest(compact(unsigned, sort=True))
    assert first["diagnosticOnly"] and first["notTradingAuthorization"] and not first["liveExecutionReady"]
    output = tmp_path / "artifact.json"
    args = ["--frozen-input", str(FIXTURE), "--factor-output", str(output), "--created-at", "2026-10-30T23:59:59Z",
            "--bootstrap-repetitions", "100"]
    assert main(args) == 0
    content = output.read_bytes()
    assert content.endswith(b"\n") and b"\r" not in content
    assert main(args) == 0
    assert content == output.read_bytes()
    assert main(["--frozen-input", str(FIXTURE), "--factor-output", str(FIXTURE)]) == 1


def test_bounded_json_and_duplicate_keys(tmp_path: Path) -> None:
    invalid = tmp_path / "invalid.json"
    invalid.write_text('{"a":1,"a":2}', encoding="utf-8")
    with pytest.raises(ValueError, match="DUPLICATE"):
        load_frozen(invalid)
    invalid.write_bytes(b" " * (8 * 1024 * 1024 + 1))
    with pytest.raises(ValueError, match="BYTE_LIMIT"):
        load_frozen(invalid)


def test_artifact_ignores_callers_decimal_context(dataset: FrozenDataset) -> None:
    original = build_artifact(dataset, generated_at="2026-10-30T23:59:59Z", repetitions=100)
    with localcontext() as context:
        context.prec = 6
        context.rounding = ROUND_DOWN
        changed = build_artifact(dataset, generated_at="2026-10-30T23:59:59Z", repetitions=100)
    assert original == changed
