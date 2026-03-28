from dataclasses import dataclass


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
