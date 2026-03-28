from collections.abc import Iterable

from nq_research.data.models import Bar


def run_strategy(bars: Iterable[Bar]) -> dict[str, object]:
    materialized = list(bars)
    return {
        "bar_count": len(materialized),
        "first_bar_time": materialized[0].open_time if materialized else None,
        "last_bar_time": materialized[-1].close_time if materialized else None,
    }
