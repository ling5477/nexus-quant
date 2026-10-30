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

Python 工具链覆盖以下验证项；本页的历史通过记录不替代当前执行结果：

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

## 冻结 V2 因子研究

在 NQ 的 `/research` 页面选择自己已经冻结的 Dataset，下载研究输入，再人工执行：

```powershell
python -m nq_research --frozen-input frozen-input.json --factor-output factor-artifact.json --factor-window 24 --horizon-bars 6 --seed 42 --bootstrap-repetitions 200 --created-at 2026-10-30T23:59:59Z
```

固定时间必须不早于输入的 `frozenAt`；实际使用可更换固定 UTC 时间，或省略 `--created-at` 使用当前时间。省略时统计值仍确定，但生成时间和 Artifact 身份会变化。CLI 不访问网络、数据库、凭据或 Java 运行时；下载认证在浏览器完成。然后在相同页面选择本地 Artifact，由 Java 核验身份、摘要和格式后只读显示，不保存为策略或交易绩效。旧 CSV CLI 保持兼容。

### 数据和身份

- `GET /api/marketdata/datasets/{datasetId}/factor-research/input` 只读取已存在的原 V2；未冻结、非所有者、其他租户或损坏输入不可导出。下载不会创建版本。原 Dataset 目录允许共享不代表允许读取其他用户的冻结版本。
- `frozen-research-export.v1` 包含 `dataset`、原 `manifestJson` 字符串、按原序排列的 `chunkJson` 字符串。保留原规范字节以复核 Manifest 和分块 SHA256，随后分别重算回放内容 SHA256 与实际来源 SHA256，不能用整个导出文件的字节摘要替代它们。
- 仅 OKX SPOT BTC-USDT/ETH-USDT、1h、721～4320 根；最多9块，每块500根/512KiB，原总块及导出文件均最多8MiB。拒绝缺失、重复、乱序、未收盘、非法OHLCV、未知版本、质量异常和身份漂移，不静默修复或裁切。
- `sourceObservedAt` 是实际采集观察时间。`availableAt`/因子 `featureAvailableAt` 是 `CLOSED_HOURLY_BOUNDARY_V1 / EXPERIMENT_ASSUMPTION`，不代表取得了历史 point-in-time 发布时间；Source SHA256 与 Content SHA256 因此可能不同。

### 因子与统计

三个因子版本均为 `1.0.0`，窗口为2～200根，默认24根。第t点仅消费截至t的回放可见历史，前window点为 `INSUFFICIENT_HISTORY`。

| factorKey | 定义 |
| --- | --- |
| RETURN_MOMENTUM | close[t] / close[t-window] - 1 |
| REALIZED_VOLATILITY | 最近window个对数收益的样本标准差，ddof=1，不年化 |
| VOLUME_ANOMALY | 当前volume相对前window根（不含当前）的均值及样本标准差的Z分数；零参考方差返回不可用 |

Decimal 明确固定40位精度与 HALF_EVEN 舍入，不继承调用方全局上下文；最终统计使用固定 NumPy 2.3.5 的 float64。Artifact记录数值运行时版本。

`PURGED_CHRONOLOGICAL_PEARSON_MOVING_BLOCK_V1` 使用70/30时间先后切分。horizon为1～48根；未来简单收盘收益标签为close[t+h]/close[t]-1。训练尾部剔除h根，使全部训练标签终点严格早于验证首个特征时点；最后h根没有标签。报告逐窗候选、有效、缺失、最低样本数、UTC特征及标签截止、Pearson相关系数与样本外结果。

置信区间采用成对非循环 moving-block percentile Bootstrap：块长=max(window,horizon,ceil(windowSamples^(1/3)))，PCG64独立种子，100～1000次（默认200），线性分位数，95%区间。最低有效样本为max(60,5×块长)，不足返回 `INSUFFICIENT_SAMPLES`。常量序列、有效Bootstrap不足90%或区间包含0均不作明确关系结论。排除0仅标记 `EXPLORATORY_RELATIONSHIP`，不表达可交易优势。方法保留局部自相关，但平稳性和块长选择仍是假设，没有多重检验修正或跨市场泛化证明。

### 独立 Artifact 合同

`python-factor-research-artifact.v1` 的 Dataset 对象包含原Dataset/Version、Content/Source/Manifest SHA256及市场/质量身份。每个factor包含key/version/parameters、datasetVersionId/sourceChecksum、完整points和statistics；父factor的身份和参数适用于其中全部点，点保存windowStart/windowEnd/featureAvailableAt/value/quality。最多3×4320点，UTF-8 JSON最多16MiB，不接收压缩或执行内容。

Artifact另含观察假设、统计方法/窗口/样本/置信区间、限制/警告、来源、生成时间及实现版本。强制 `diagnosticOnly=true / notTradingAuthorization=true / liveExecutionReady=false`。规范编码为UTF-8、键排序、紧凑分隔符、原始Unicode；统计实数以有限十进制字符串保存，避免Java/Python浮点JSON编码差异。artifactId=`factor-`+不含artifactId/checksum的规范SHA256；checksum是含artifactId、不含checksum的规范SHA256。固定数据/参数/方法/seed/时间/数值运行时产生相同完整文件。

`POST .../factor-research/preview` 仅核验当前owner的V2数据身份、上述摘要和格式/范围，不重算Python统计，不持久化、不授予交易能力；沿用既有HTTP写方法角色门禁。Java拒绝未知字段、重复字段、尾随JSON、超字节/超点数、非法时间和身份/摘要冲突。统计来自用户选择的本地文件，摘要匹配不证明报告的科学结论或作者可信度。

旧 `python-evaluation-artifact.v1` 要求策略版本、成交/收益/费用等字段；因子研究不伪造这些字段，不替换原合同。共享样本 `fixtures/java-frozen-v2.json` 由真实隔离PostgreSQL中的Java冻结器及导出器生成，行情标注为合成受控数据；`java-factor-artifact.json` 是同输入经真实CLI产生的固定时间产物，供Java/Python共同测试。
