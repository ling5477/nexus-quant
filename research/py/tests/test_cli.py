import json

from nq_research.cli import main


def test_cli_reads_local_bars_and_prints_summary(capsys) -> None:
    exit_code = main(["--bars-csv", "fixtures/btcusdt_1m_sample.csv"])

    captured = capsys.readouterr()
    result = json.loads(captured.out)

    assert exit_code == 0
    assert result["bar_count"] == 6
    assert result["first_bar_time"] == "2025-01-01T00:00:00Z"
    assert result["last_bar_time"] == "2025-01-01T00:05:59Z"
