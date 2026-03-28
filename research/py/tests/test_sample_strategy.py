from nq_research.data.models import Bar
from nq_research.strategy.sample_strategy import run_strategy


def test_run_strategy_returns_summary() -> None:
    bars = [
        Bar("BTC-USDT", "1m", "2026-03-01T00:00:00Z", "2026-03-01T00:00:59Z", 1.0, 2.0, 0.5, 1.5, 10.0),
        Bar("BTC-USDT", "1m", "2026-03-01T00:01:00Z", "2026-03-01T00:01:59Z", 1.5, 2.1, 1.4, 2.0, 12.0),
    ]

    result = run_strategy(bars)

    assert result["bar_count"] == 2
    assert result["first_bar_time"] == "2026-03-01T00:00:00Z"
    assert result["last_bar_time"] == "2026-03-01T00:01:59Z"
