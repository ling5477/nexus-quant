"""研究域本地行情数据模型。

本模块只描述离线 CSV / fixture 读入后的内存结构，不包含网络访问、凭证读取、
Java runtime 写入或交易动作。后续 dataset manifest、evaluation 和 reporting
均以这些纯数据对象为输入，避免 Python research 越过 offline boundary。
"""

from dataclasses import dataclass


@dataclass(frozen=True)
class Bar:
    """单根本地 K 线 bar。

    用途：承载离线研究 CSV 中的 OHLCV 数据。
    Why：Python research 当前只允许处理可复现的本地文件产物，因此这里保留
    字符串时间与浮点 OHLCV，不绑定 Java 数据库实体、交易所 SDK 或 runtime API。
    边界：该模型不表达交易授权、LIVE 状态、凭证状态或真实交易所可用性。
    """

    symbol: str
    interval: str
    open_time: str
    close_time: str
    open_price: float
    high_price: float
    low_price: float
    close_price: float
    volume: float
