# NexusQuant Research Python

本目录是 NexusQuant 的离线研究工具链子工程。

当前定位：

- 只处理本地研究数据、特征实验、批量实验与离线回测辅助。
- 不接入 live trading / auth / recovery / ledger 主链。
- 不作为 Java / Python runtime bridge。
- 正式安装包位于 `src/nq_research/`，通过 `python -m nq_research` 或安装后的 `nq-research` script 运行。
- 未安装时从本目录运行 `python -m nq_research`，由顶层 `nq_research/__main__.py` 兼容启动器转到同一个 CLI。

当前结构：

- `src/nq_research/data/`
- `src/nq_research/dataset/`：本地 CSV 的 dataset manifest 与稳定身份。
- `src/nq_research/strategy/`
- `src/nq_research/evaluation/`：离线指标、参数集合与诊断 artifact。
- `src/nq_research/experiment/`：实验元数据与参数身份。
- `src/nq_research/reporting/`：离线运行摘要。
- `src/nq_research/cli.py`：组合上述模块的命令入口；当前没有独立 backtest 实现包。
- `datasets/`：研究数据资产说明，不是 Python `dataset` package。
- `tests/`
- `fixtures/`：测试和本地 CLI smoke 使用的 CSV 样本。

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
python -m nq_research --bars-csv fixtures/btcusdt_1m_sample.csv
```

安装为本地包后也可以使用脚本入口：

```powershell
nq-research --bars-csv fixtures/btcusdt_1m_sample.csv
```
