# NexusQuant Research Python

本目录是 NexusQuant 的离线研究工具链子工程。

当前定位：

- 只处理本地研究数据、特征实验、批量实验与离线回测辅助。
- 不接入 live trading / auth / recovery / ledger 主链。
- 不作为 Java / Python runtime bridge。
- 当前正式入口是 `py -m nq_research`；安装为本地包后可使用 `nq-research` script。

当前结构：

- `src/nq_research/data/`
- `src/nq_research/strategy/`
- `src/nq_research/backtest/`
- `tests/`
- `fixtures/`

## 验证状态

PRE-CLEAN-3B 已完成 Python 工具链闭环：

- `pytest`：已通过。
- `mypy`：已通过。
- `ruff`：已通过。
- `CLI smoke`：已通过。

## 运行方式

首次本地验证前，先安装 Python 子工程和 dev 质量工具：

```powershell
python -m pip install -e ".[dev]"
```

在 `research/py` 目录内可直接运行：

```powershell
python -m pytest -q
python -m mypy src
python -m ruff check .
python -m nq_research --bars-csv ..\fixtures\btcusdt_1m_sample.csv
```

安装为本地包后也可以使用脚本入口：

```powershell
nq-research --bars-csv ..\fixtures\btcusdt_1m_sample.csv
```
