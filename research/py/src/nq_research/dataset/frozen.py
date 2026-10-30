"""只消费 Java 已认证入口导出的不可变 V2 原字节，不访问网络或数据库。"""

from __future__ import annotations

import json
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from decimal import Decimal, InvalidOperation
from hashlib import sha256
from pathlib import Path
from typing import Any
from uuid import UUID

MAX_BYTES = 8 * 1024 * 1024
VERSION = "HISTORICAL_RESEARCH_INPUT_V2"


def digest(text: str) -> str:
    return sha256(text.encode("utf-8")).hexdigest()


def compact(value: Any, *, sort: bool = False) -> str:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"), sort_keys=sort, allow_nan=False)


def timestamp(value: str) -> datetime:
    if not isinstance(value, str) or not value.endswith("Z"):
        raise ValueError("UTC_TIMESTAMP_REQUIRED")
    return datetime.fromisoformat(value.replace("Z", "+00:00")).astimezone(UTC)


def strict_json(text: str) -> Any:
    def unique(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
        result: dict[str, Any] = {}
        for key, value in pairs:
            if key in result:
                raise ValueError("DUPLICATE_JSON_KEY")
            result[key] = value
        return result

    def invalid(value: str) -> None:
        raise ValueError("NON_FINITE_JSON")

    return json.loads(text, object_pairs_hook=unique, parse_constant=invalid)


@dataclass(frozen=True)
class FrozenBar:
    open_time: str
    close_time: str
    source_observed_at: str
    replay_available_at: str
    close: Decimal
    volume: Decimal
    open: Decimal
    high: Decimal
    low: Decimal


@dataclass(frozen=True)
class FrozenDataset:
    identity: dict[str, str]
    bars: tuple[FrozenBar, ...]
    frozen_at: str


def load_frozen(path: Path) -> FrozenDataset:
    with path.open("rb") as stream:
        data = stream.read(MAX_BYTES + 1)
    if len(data) > MAX_BYTES:
        raise ValueError("RESEARCH_EXPORT_BYTE_LIMIT_EXCEEDED")
    try:
        return validate_export(strict_json(data.decode("utf-8")))
    except (KeyError, TypeError, InvalidOperation, AttributeError, OverflowError) as error:
        raise ValueError("FROZEN_EXPORT_INVALID") from error


def validate_export(export: dict[str, Any]) -> FrozenDataset:
    if set(export) != {"schemaVersion", "dataset", "manifestJson", "chunkJson"}:
        raise ValueError("EXPORT_FIELDS_INVALID")
    if export["schemaVersion"] != "frozen-research-export.v1":
        raise ValueError("EXPORT_VERSION_INVALID")
    dataset = export["dataset"]
    reference = dataset["frozenInput"]
    manifest = strict_json(export["manifestJson"])
    if reference["version"] != VERSION or manifest["version"] != VERSION:
        raise ValueError("V2_REQUIRED")
    for field in ("datasetVersionId", "sha256", "sourceSha256", "frozenAt", "source",
                  "barCount", "totalBytes", "replayVisibilityVersion", "replayVisibilitySource"):
        if reference[field] != manifest[field]:
            raise ValueError("FROZEN_IDENTITY_MISMATCH")
    UUID(reference["datasetVersionId"])
    UUID(dataset["datasetId"])
    if digest(export["manifestJson"]) != reference["manifestSha256"]:
        raise ValueError("MANIFEST_DIGEST_MISMATCH")
    if any(dataset[key] != value for key, value in manifest["dataset"].items()):
        raise ValueError("DATASET_IDENTITY_MISMATCH")
    count = dataset["barCount"]
    if (type(count) is not int or not 721 <= count <= 4320 or dataset["provider"] != "db"
            or dataset["exchangeCode"] != "OKX" or dataset["marketType"] != "SPOT"
            or dataset["symbol"] not in {"BTC-USDT", "ETH-USDT"} or dataset["interval"] != "1h"
            or dataset["status"] != "READY" or dataset["qualityStatus"] != "OK" or dataset["gapCount"] != 0
            or reference["replayVisibilityVersion"] != "CLOSED_HOURLY_BOUNDARY_V1"
            or reference["replayVisibilitySource"] != "EXPERIMENT_ASSUMPTION"
            or dataset["snapshotAt"] != reference["frozenAt"]):
        raise ValueError("DATASET_SCOPE_INVALID")
    chunks = export["chunkJson"]
    descriptors = manifest["chunks"]
    if not 1 <= len(chunks) <= 9 or len(chunks) != len(descriptors) or len(chunks) != reference["chunkCount"]:
        raise ValueError("CHUNK_COVERAGE_INVALID")
    rows: list[dict[str, Any]] = []
    observations: list[str] = []
    total = 0
    for ordinal, (raw, descriptor) in enumerate(zip(chunks, descriptors, strict=True)):
        body = strict_json(raw)
        size = len(raw.encode("utf-8"))
        n = body["barCount"]
        if (not 1 <= n <= 500 or size > 512 * 1024
                or body["ordinal"] != ordinal or body["startBar"] != len(rows)
                or descriptor != {"ordinal": ordinal, "startBar": len(rows), "barCount": n,
                                  "bytes": size, "sha256": digest(raw)}
                or len(body["bars"]) != n or len(body["sourceObservedAt"]) != n):
            raise ValueError("CHUNK_IDENTITY_MISMATCH")
        total += size
        rows.extend(body["bars"])
        observations.extend(body["sourceObservedAt"])
    if len(rows) != count or total != reference["totalBytes"] or total > MAX_BYTES:
        raise ValueError("UNDECLARED_TRUNCATION")
    if digest(compact(rows)) != reference["sha256"]:
        raise ValueError("CONTENT_DIGEST_MISMATCH")
    original = [dict(row, availableAt=observed) for row, observed in zip(rows, observations, strict=True)]
    if digest(compact(original)) != reference["sourceSha256"]:
        raise ValueError("SOURCE_DIGEST_MISMATCH")
    frozen = timestamp(reference["frozenAt"])
    start = timestamp(dataset["startTime"])
    if (start.minute or start.second or start.microsecond
            or timestamp(dataset["endTime"]) != start + timedelta(hours=count, milliseconds=-1)
            or start + timedelta(hours=count) > frozen):
        raise ValueError("COVERAGE_INVALID")
    bars = []
    for i, (row, observed) in enumerate(zip(rows, observations, strict=True)):
        opening = timestamp(row["openTime"])
        closing = opening + timedelta(hours=1)
        if (opening != start + timedelta(hours=i) or timestamp(row["closeTime"]) != closing - timedelta(milliseconds=1)
                or timestamp(row["availableAt"]) != closing or not closing <= timestamp(observed) <= frozen
                or row["qualityStatus"] != "OK"
                or any(row[k] != dataset[k] for k in ("exchangeCode", "marketType", "symbol", "interval"))):
            raise ValueError("BAR_TIME_OR_IDENTITY_INVALID")
        o, h, low, c, v = (Decimal(row[k]) for k in ("openPrice", "highPrice", "lowPrice", "closePrice", "volume"))
        if (not all(x.is_finite() for x in (o, h, low, c, v)) or min(o, h, low, c) <= 0
                or h < max(o, low, c) or low > min(o, h, c) or v < 0):
            raise ValueError("OHLCV_INVALID")
        if row["quoteVolume"] is not None:
            quote = Decimal(row["quoteVolume"])
            if not quote.is_finite() or quote < 0:
                raise ValueError("QUOTE_VOLUME_INVALID")
        if row["tradeCount"] is not None and (type(row["tradeCount"]) is not int or row["tradeCount"] < 0):
            raise ValueError("TRADE_COUNT_INVALID")
        raw_payload = strict_json(row["rawPayloadJson"])
        if isinstance(raw_payload, list) and (len(raw_payload) < 9 or raw_payload[8] != "1"):
            raise ValueError("SOURCE_UNCLOSED")
        bars.append(FrozenBar(row["openTime"], row["closeTime"], observed, row["availableAt"], c, v, o, h, low))
    identity = {"datasetId": dataset["datasetId"], "datasetVersionId": reference["datasetVersionId"],
                "contentSha256": reference["sha256"], "sourceSha256": reference["sourceSha256"],
                "manifestSha256": reference["manifestSha256"], "exchange": dataset["exchangeCode"],
                "symbol": dataset["symbol"], "interval": dataset["interval"], "qualityStatus": dataset["qualityStatus"]}
    return FrozenDataset(identity, tuple(bars), reference["frozenAt"])
