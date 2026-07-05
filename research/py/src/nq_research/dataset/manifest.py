"""离线 dataset manifest 生成。

本模块只从本地 CSV 文件与已经加载的 `Bar` 对象生成可复现 manifest。
它不访问网络、不读取 credential、不写 Java runtime 或数据库，也不把数据质量
诊断解释为 trading authorization。
"""

from __future__ import annotations

import json
from collections.abc import Iterable
from dataclasses import dataclass
from datetime import datetime, timedelta
from hashlib import sha256
from pathlib import Path

from nq_research.data.models import Bar

DATASET_SCHEMA_VERSION = "dataset-manifest.v1"
QUALITY_OK = "OK"
QUALITY_INCOMPLETE = "INCOMPLETE"
QUALITY_NOT_AVAILABLE = "NOT_AVAILABLE"


@dataclass(frozen=True)
class DatasetManifest:
    """离线研究数据集 manifest。

    用途：记录一次本地 CSV 数据输入的稳定身份、时间范围、checksum 和基础质量状态。
    Why：后续策略有效性验证需要能追溯“用的是哪份数据”，不能只靠文件名或人工描述。
    参数语义：`dataset_id` 与 `checksum` 基于本地文件内容和关键字段稳定生成；
    `quality_status` 只描述离线数据完整性诊断，不代表行情源、交易所或 LIVE 可用。
    幂等性：同一 CSV 内容、相同 manifest 参数和 schema_version 会生成相同 `dataset_id`。
    失败模式：空数据、混合 symbol/interval 或文件不存在会在上游 loader / builder 明确报错。
    """

    dataset_id: str
    source: str
    symbol: str
    exchange: str
    market_type: str
    interval: str
    start_time: str
    end_time: str
    row_count: int
    checksum: str
    created_at: str
    schema_version: str
    quality_status: str
    gap_count: int
    notes: tuple[str, ...]

    def to_dict(self) -> dict[str, object]:
        """返回稳定 JSON 友好的 manifest 结构。

        Why：CLI、测试和后续文件产物需要统一字段名；显式转换 `notes` 为 list，
        避免 tuple 在不同消费者里产生不必要的序列化差异。
        """

        return {
            "dataset_id": self.dataset_id,
            "source": self.source,
            "symbol": self.symbol,
            "exchange": self.exchange,
            "market_type": self.market_type,
            "interval": self.interval,
            "start_time": self.start_time,
            "end_time": self.end_time,
            "row_count": self.row_count,
            "checksum": self.checksum,
            "created_at": self.created_at,
            "schema_version": self.schema_version,
            "quality_status": self.quality_status,
            "gap_count": self.gap_count,
            "notes": list(self.notes),
        }


def build_dataset_manifest_from_csv(
    path: Path,
    bars: Iterable[Bar],
    *,
    source: str = "LOCAL_CSV",
    exchange: str = "UNKNOWN",
    market_type: str = "SPOT",
    created_at: str,
    schema_version: str = DATASET_SCHEMA_VERSION,
    notes: Iterable[str] = (),
) -> DatasetManifest:
    """从本地 CSV 与 bars summary 生成 dataset manifest。

    Why：manifest 必须绑定真实文件 checksum 和已加载数据范围，避免后续实验只记录
    “使用某个路径”而无法复现。该函数要求调用方传入 `created_at`，让测试和批处理
    可以固定时间戳；CLI 默认会传入当前 UTC 时间。
    边界：只读取本地文件 bytes 计算 checksum；不访问网络、不读 credential、不写外部系统。
    """

    materialized = list(bars)
    if not materialized:
        raise ValueError("dataset manifest requires at least one bar")

    symbol = _single_value(materialized, "symbol")
    interval = _single_value(materialized, "interval")
    checksum = calculate_file_checksum(path)
    gap_count, gap_check_available = detect_gap_count(materialized)
    manifest_notes = tuple(notes)
    quality_status = QUALITY_OK if gap_count == 0 and gap_check_available else QUALITY_INCOMPLETE
    if not gap_check_available:
        quality_status = QUALITY_NOT_AVAILABLE
        manifest_notes = (
            *manifest_notes,
            "gap_check_not_available: interval or timestamp could not be evaluated offline",
        )

    identity = {
        "checksum": checksum,
        "exchange": exchange,
        "interval": interval,
        "market_type": market_type,
        "row_count": len(materialized),
        "schema_version": schema_version,
        "source": source,
        "start_time": materialized[0].open_time,
        "end_time": materialized[-1].close_time,
        "symbol": symbol,
    }
    dataset_id = "ds_" + _stable_digest(identity)[:16]
    return DatasetManifest(
        dataset_id=dataset_id,
        source=source,
        symbol=symbol,
        exchange=exchange,
        market_type=market_type,
        interval=interval,
        start_time=materialized[0].open_time,
        end_time=materialized[-1].close_time,
        row_count=len(materialized),
        checksum=checksum,
        created_at=created_at,
        schema_version=schema_version,
        quality_status=quality_status,
        gap_count=gap_count,
        notes=manifest_notes,
    )


def calculate_file_checksum(path: Path) -> str:
    """计算本地文件 SHA-256 checksum。

    Why：checksum 是 dataset manifest 的可复现锚点，比文件名更可靠。函数只读取
    调用方指定的本地路径，不做路径枚举、网络访问或 credential 探测。
    """

    digest = sha256()
    with path.open("rb") as file:
        for chunk in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def detect_gap_count(bars: list[Bar]) -> tuple[int, bool]:
    """基于 `open_time` 和 `interval` 估算 gap 数量。

    Why：当前不是完整 data-quality engine，只提供离线 manifest 级别的基础 gap signal。
    如果 interval 或 timestamp 无法稳定解析，返回 `(0, False)`，由 manifest 标记
    `NOT_AVAILABLE`，避免伪造数据完整性结论。
    """

    if len(bars) < 2:
        return 0, True

    step = _interval_to_timedelta(bars[0].interval)
    if step is None:
        return 0, False

    gap_count = 0
    for previous, current in zip(bars, bars[1:]):
        previous_open = _parse_utc(previous.open_time)
        current_open = _parse_utc(current.open_time)
        if previous_open is None or current_open is None:
            return 0, False

        delta = current_open - previous_open
        if delta <= timedelta(0):
            gap_count += 1
            continue
        if delta > step:
            missing = int(delta / step) - 1
            gap_count += max(missing, 1)

    return gap_count, True


def _single_value(bars: list[Bar], attribute_name: str) -> str:
    values = {str(getattr(bar, attribute_name)).strip() for bar in bars}
    if len(values) != 1:
        raise ValueError(f"bars csv must contain a single {attribute_name}; found: {sorted(values)}")
    value = values.pop()
    if not value:
        raise ValueError(f"{attribute_name} must not be blank")
    return value


def _stable_digest(payload: dict[str, object]) -> str:
    encoded = json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return sha256(encoded).hexdigest()


def _interval_to_timedelta(interval: str) -> timedelta | None:
    unit = interval[-1:].lower()
    amount_text = interval[:-1]
    if not amount_text.isdigit():
        return None
    amount = int(amount_text)
    if amount <= 0:
        return None
    if unit == "m":
        return timedelta(minutes=amount)
    if unit == "h":
        return timedelta(hours=amount)
    if unit == "d":
        return timedelta(days=amount)
    return None


def _parse_utc(value: str) -> datetime | None:
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return None
