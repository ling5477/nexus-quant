# Python Dependency Assessment

状态：`DRAFT / PRE-GATEX / NO INSTALL`（草案 / GateX 前准备 / 未安装）。

本评估只覆盖离线 research-to-artifact 工具链。本轮未修改 `research/py/pyproject.toml`、lock、Python source，
未安装任何包，也不允许依赖进入 Java、Shadow runner、private endpoint 或在线交易路径。

## 1. 当前基线

`research/py/pyproject.toml` 的 production `dependencies = []`；现有 dataset、parameter、evaluation artifact、
canonical JSON checksum 与敏感字段扫描主要使用 Python 标准库。Java 侧只接受明确 JSON contract，不嵌入 Python runtime。

许可证列采用这些项目的已知上游 SPDX 口径；本轮未联网。正式引入前必须对选定版本重新核对 LICENSE/NOTICE、
transitive dependencies、SBOM、漏洞、wheel 来源与平台支持。

## 2. 决策矩阵

| 依赖 | 用途 | 当前替代能力 | 维护成本 | 运行成本 | 许可证 | Java 边界 | 在线路径 | 推荐结论 |
|---|---|---|---|---|---|---|---|---|
| pandas | 离线表格清洗、时间序列对齐、feature/artifact 生成前的数据整形。 | 标准库 `csv/json/datetime/statistics` 与现有 typed records 可完成小规模流程，但复杂 join/window/缺失值处理代码量高。 | 中：引入 NumPy 等 transitive、binary wheel、版本兼容与数据类型规范。 | 中到高：DataFrame 常驻内存，需分块、列裁剪和输入行数上限。 | BSD-3-Clause（引入前复核选定版本） | 只输出 `strategy-release-manifest.v1` 与 allowlisted artifact files；Java 不加载 pickle、不嵌入 Python。 | 否；仅 offline CLI/build step。 | `INTRODUCE_IN_GATEX` |
| polars | 离线列式/惰性 DataFrame，适合大于内存友好扫描和并行表达式。 | GateX 若已引入 pandas，可覆盖首批表格需求；标准库继续覆盖小数据。 | 中到高：Rust binary、表达式 API、与 pandas 双栈语义/测试。 | 中：通常内存/CPU 效率较好，但默认并行需资源上限。 | MIT（引入前复核选定版本） | 同样只能产出 JSON/CSV/Parquet 候选文件；Java contract 不感知 DataFrame engine。 | 否。 | `DO_NOT_INTRODUCE` |
| DuckDB | 本地大文件 SQL、Parquet scan、bounded aggregation 与 reproducible research query。 | GateX 首批可用 pandas chunking / 标准库和现有 PostgreSQL 导出物；当前没有大规模 Parquet query 的硬需求。 | 中：native binary、SQL 版本、extension/文件访问策略、临时目录与资源治理。 | 中：in-process query 可能使用大量线程、内存和临时磁盘，必须 cap。 | MIT（引入前复核选定版本） | 只允许离线只读文件源；禁止连接 NQ DB、禁止自动 extension 下载；输出仍经 manifest。 | 否。 | `DEFER_TO_GATEY` |
| statsmodels | 离线统计检验、回归诊断、时间序列模型与置信区间。 | 现有确定性 metrics + 标准库可完成 GateX contract 验证，不需要统计建模才能进入 Shadow contract。 | 高：依赖 NumPy/SciPy/pandas，模型版本与数值回归基线需要长期维护。 | 中到高：拟合会占用 CPU/内存，部分算法随样本/特征非线性增长。 | BSD-3-Clause（引入前复核选定版本） | 只允许输出诊断 metrics/limitations；Java 不反序列化 model object，也不据此授权运行。 | 否。 | `DEFER_TO_GATEY` |
| scikit-learn | 离线 preprocessing、传统 ML、cross-validation 与可复现实验。 | GateX 目标是 contract/traceability，不是 ML；现有规则策略和评估 facts 足够。 | 高：NumPy/SciPy/joblib/threadpoolctl、模型版本兼容、随机种子和数据泄漏测试。 | 高：训练、CV、并行 worker 需要明确 CPU/memory/time budget。 | BSD-3-Clause（引入前复核选定版本） | 禁止 Java 加载 pickle/joblib；只接受经过 schema/digest 的可解释 metrics 与 bounded signal/weight summary。 | 否。 | `DEFER_TO_GATEZ` |
| PyPortfolioOpt | 离线组合优化、约束权重、efficient frontier。 | GateX 可由显式参数和 `riskBudget` snapshot 提供固定权重/约束；当前无 optimizer 必要性证据。 | 高：pandas/NumPy/SciPy/cvxpy/solver 组合、求解器许可与数值稳定性。 | 高：优化求解 CPU/内存不可预测，失败/不可行状态需显式建模。 | MIT（项目口径；transitive solver 许可证需逐项复核） | 仅可输出 bounded weight summary 与 limitations；优化结果不是风险批准或交易授权。 | 否。 | `DEFER_TO_GATEZ` |

## 3. GateX 中 pandas 的最小引入条件

`INTRODUCE_IN_GATEX` 是条件性建议，不是本任务授权。正式任务必须同时满足：

1. 只加入 production dependency 的明确版本范围，并生成/更新项目认可的 lock 或 hash evidence。
2. 记录 BSD-3-Clause、NumPy 等 transitive 许可证和 SBOM；只从受信 package index 获取 wheel，不执行来源不明脚本。
3. 输入必须有最大文件数、单文件大小、总行数/列数、时间窗口与内存预算；大输入分块或 fail-closed。
4. 禁止网络、NQ DB、credential、private endpoint、exchange adapter、Java process control 与在线 runner 依赖。
5. artifact 只能写入受控工作目录；路径归一化、symlink/reparse-point、TOCTOU、原子写与 cleanup 需要单独 review。
6. JSON/decimal/time canonicalization 由 contract tests 固定；DataFrame dtype 不得隐式改变金额、权重、UTC 或 ID。
7. 至少覆盖 empty、invalid schema、oversize、NaN/Infinity、timezone、precision、duplicate key 与敏感字段失败路径。

## 4. Java 边界

所有候选依赖共享同一边界：

```text
Python offline process
  -> allowlisted artifact files
  -> strategy-release-manifest.v1
  -> independent file/digest verification
  -> Java immutable facts / review only
```

禁止：Java in-process Python、任意 subprocess command string、pickle/joblib/model object 反序列化、任意路径读取、
自动联网下载模型/extension、写 NQ DB、启动 Shadow/Paper/LIVE、调用交易所或读取 credential。

## 5. 结论

- GateX 最多条件性引入一套 DataFrame stack：pandas。
- 不同时引入 polars；避免两套 dataframe 语义、类型转换与测试矩阵。
- DuckDB/statsmodels 延后 GateY；ML/optimizer 依赖延后 GateZ。
- 任何依赖决策都不改变 `Python ML readiness = NO`、`Python live execution readiness = NO` 和 `LIVE = DISABLED`。
