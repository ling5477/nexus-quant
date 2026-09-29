# NQ V1 当前能力盘点与下一任务决策

> 2026-09-24 CST 的只读代码、远端证据及用户补充研究输入快照。本文是业务决策报告，不修改 `docs/current/STATUS.md` 的机器状态，不表示下一任务已经启动、接受、部署或取得交易授权。研究报告中的现状、优先级、类名和阈值均按待核对设计输入处理。

## 1. 固定基线和证据范围

- 仓库：`https://github.com/ling5477/nexus-quant`；本地 `E:\Project\nexus-quant`；查询前 `git status --short` 为空，分支 `dev`。`git fetch origin` 成功；`HEAD` 与 `origin/dev` 同为 `76caf387a2b1179bcf6501cee2cbb9203167696f`。因此本轮读取的是合并后的工作树，不混入本地业务改动。
- 最近 12 条 `origin/dev` 提交以 `76caf387`（PR #22 Jev advisory 路由合并）、`6bf01b69`（PR #21 post-GateAUDIT consolidation 合并）开头；GateAUDIT post-tag authority sync 是 PR #20，2026-09-23 09:51:50 UTC 合并到 `dev`，merge SHA `e09e755c97a6ed572b53874b8e3816e42445c823`。PR #21 于 2026-09-24 09:03:08 UTC 合并，PR #22 于 10:08:17 UTC 合并。这是 GitHub PR readback，不从旧计划推断合并。
- 本次基线的 GitHub Actions `NQ CI Baseline` [run 35985470980](https://github.com/ling5477/nexus-quant/actions/runs/35985470980)：`push`，`headSha=76caf387…`，2026-09-24 10:08:19–10:14:34 UTC，`completed/success`，9/9 check runs success（后端回归、PostgreSQL/Flyway、前端构建及 critical E2E、Research quality、运行安全等）。这是**本 SHA 的仓库 CI**；它没有证明本文所要求的具体业务样例已在当前环境端到端运行。
- [STATUS](../STATUS.md) 的当前机器区块仍写 `GateAUDIT=FROZEN|ACCEPTED|TAGGED`、`LIVE=DISABLED`、kill switch `ENGAGED`、Shadow `NOT_ENABLED`，历史 accepted technical pair 为 `c91f2648… / 35832860696`；其 `work_batch/next_action` 仍为未开始的 branch cleanup。本文提出的业务下一任务与该**现存机器状态**分开记录，未擅自改写阶段。GateAUDIT [21 行接受矩阵](../../gates/gate-audit/GATEAUDIT_EVIDENCE_MATRIX.md)是审计/资格 owner 矩阵，不是“21 种业务闭环”；[17 行 residual](../../gates/gate-audit/GATEAUDIT_KNOWN_LIMITATIONS_AND_RESIDUALS.md)也没有因为合并而消失。
- 历史 GateY 最小真实 pilot、GateAUDIT 冻结、Phase6 L4/L5/L6 资格与 Phase5B 部署/恢复，分别按 [STATUS](../STATUS.md)、[冻结 archive](../../gates/gate-audit/README.md) 和接受记录解释；本次合并、CI 通过、当前服务器部署、通用 LIVE/自动交易授权是四种不同事实。未查询服务器、生产 schema、私有账户、实际余额、费率或真实交易；本次基线没有相应现时证据。
- 代码核对覆盖 `backend` 各主模块的 controller→service→port/adapter、`frontend/src/pages` 与 `features`、`research/py`，并复用 current API/schema/architecture/modules/testing/worklog、GateAUDIT archive。未做第二轮全仓审计；以下“未找到”仅限所述源码调用链/检索范围。
- 用户补充文件：`D:\Downloads\deep-research-report_0924.md`，标题《NQ 生产级 Quant Operating System 能力缺口与补全路线深度研究》，40758 bytes，文件修改时间 2026-09-24 11:02:28 UTC，SHA-256 `CBF70B97F57D7A8FF6FC84AE5C9EEBC08B0244F723B8122ED22A8F46071B08FA`。文件本身没有声明版本号，故只以路径和内容哈希标识。已读取执行摘要、能力缺口地图、七项核心设计、生产补全/Agent 安全及实施顺序/PoC/里程碑；其内论文与外部项目引用未在本轮重新查证，也不作为 `dev` 事实。

## 2. 当前能力矩阵

状态用“代码/入口/验证/缺口”分列；本 SHA 的 9/9 CI 只给相应模块的构建与回归证据，不能替代特定策略、数据范围或真实交易的验收。

| 能力域 | 代码与当前入口/开关 | 验证及实际缺口 | 对下一成果的影响 |
| --- | --- | --- | --- |
| 行情与数据集 | OKX/Binance SPOT 历史 K 线适配，`MarketdataIngestionService.createJob/runOnce` 限 BTC/ETH/SOL-USDT，1m–1d；`MarketdataController` 的 job、bars、dataset/quality API；前端 Marketdata 页可创建/查询。`MarketdataDatasetService` 生成范围和质量快照（`backend/nq-core/.../marketdata/application/service/MarketdataIngestionService.java:39-42,129-164`、`MarketdataDatasetService.java:34-37,126-171`、`backend/nq-api/.../marketdata/api/web/MarketdataController.java:169-305`）。 | V16/V18 有 bars 唯一 scope、job/run 和 coverage；dataset ID/JSON 是范围与统计快照，非不可变 bar 字节版本或每条 bar 的“当时可见时间”证明（`backend/nq-infra/src/main/resources/db/migration/V16__gate_h2_marketdata_ingestion.sql:4-26`、`V18__gate_h3_marketdata_dataset_binding.sql:4-81`）。当前本地/生产可用 bar 数量与日期范围未查库，未知；历史/实时数据不可混称。 | 可复用公共现货数据入口；下一任务须固定可重放输入及可见时点，拒绝缺口/迟到回填造成的事后信息。 |
| 指标与因子 | Java 回测仅有 `BacktestSignalPolicy` 与内建 `BuiltinFixtureSignalPolicy`；Python CLI 另有本地 CSV 特征/实验摘要入口（`backend/nq-backtest/.../BacktestDomainConfiguration.java:32-39`、`.../BuiltinFixtureSignalPolicy.java:18-62`、`research/py/src/nq_research/cli.py:28-120`）。 | 内建策略只在首 bar 买 1 单位、末 bar 平仓，未消费通用因子、参数或 warm-up。Python 不连接 Java 运行时，因而不能把离线特征当作 Java 策略已接通。当前 CI 的 Research quality 成功只证明 Python gate。 | 应为一个被实际消费者使用的策略补最小指标定义与 warm-up；此时无需独立 Factor Library。 |
| 策略与研究 | 策略定义/版本、研究配置、dataset/backtest config 绑定、snapshot、发布记录均有 API 与持久化；前端策略/研究/回测页可创建与绑定（`docs/current/API.md` 的 GateI-1/2；`frontend/src/pages/strategies/StrategiesPage.tsx:423-503`、`frontend/src/pages/backtests/BacktestsPage.tsx:195-278`）。 | 回测执行请求从 `researchConfig.strategySnapshot()` 解析 `strategyType`，并不从绑定的 `strategyVersionSnapshotJson` 装载可执行策略（`BacktestExecutionService.java:363-380,441-447`）。版本追溯字段存在，但同版执行语义未得到证明。 | 下一任务必须使版本/参数/执行定义真实一致，保留旧记录可读。 |
| 回测与评价 | `BacktestRunController` 提供创建、start、evaluate、publish、sim order/trade/position/PnL 查询；`BacktestExecutionService` 生成 `sim_*` 事实；`nq-eval` 计算净值、回撤、Sharpe、交易结果（`.../BacktestRunController.java:58-231`、`.../BacktestExecutionService.java:165-340`、`backend/nq-eval/.../EvaluationMetricCalculator.java:48-106`）。 | 当前 policy 在 bar closeTime 产信号，同一 bar closePrice 成交；没有信号可见后下一可交易时点的因果证明（`BuiltinFixtureSignalPolicy.java:30-62`、`ExecutionPricingPolicy.java:10-20`）。fee/slippage 从现金各扣一次，净 PnL 来自 equity；TradeOutcome 单独按开平仓成本归因，未见此路径重复从 equity 扣费（`BacktestExecutionContext.java:69-86`、`TradeOutcomeCalculator.java:25-63`）。滑点被记为费用而成交价仍是 close，真实成交偏差及 benchmark/OOS 尚无代表性业务验收。 | 当前是可运行的 fixture 回测，不足以把普通策略结果视为可用现货研究。 |
| Paper / Shadow | 研究侧 `PaperTradingController` 有 create/start/stop、订单/成交/持仓、日报与诊断读取；`PaperRunScheduleService.runScheduleOnce` 写 fire，heartbeat 写状态；前端 `/paper-trading/runs` 可操作；另有 canonical SIM `PaperTradingAdapter` 与 `PaperMatchingService`（`.../PaperTradingController.java:62-179`、`.../PaperRunScheduleService.java:127-184`、`backend/nq-scheduler/.../PaperMatchingService.java:101-228`）。 | 研究侧 `PaperTradingRunService.start` 仅状态迁移；对 `PaperTradingOrder/Trade/PositionRepository` 的 main-source 使用检索只见读取服务和 JDBC 映射，未见策略驱动写入。`runScheduleOnce` 只记录成功 fire，没有执行策略。canonical SIM 撮合的是普通 Order，不是上述 paper_run 的订单事实。Shadow 有只读/preview/runner 结构，但 current authority 明确 `NOT_ENABLED`。 | 这是真实连接阻断；不能以“Paper 页面可启动”或“SIM 撮合可用”宣布同策略 Paper 已闭环。 |
| 资金与组合 | 回测有 `initialCapital`、cash/position/PnL；Risk 有 `MinNotionalRule`、`OrderPrecisionRule`，OKX public venue-rule reader 可读 tick/lot/minSz（`BacktestExecutionContext.java:19-86`、`backend/nq-risk/.../MinNotionalRule.java:16-70`、`OrderPrecisionRule.java:48-54`、`backend/nq-adapter-okx/.../OkxVenueRuleFactsReader.java:20-97`）。 | fixture 买 1 BTC 与 10/100/1000 USDT 不匹配。研究 Paper 未见以同一账户 cash、在途订单和策略竞争为输入的下单规模计算；Risk 常规限额不等于组合级预占。实际余额、账户费率和 venue 规则现时值未知。 | 下一任务至少让一条现货 SIM 策略按资金预算、精度、最小量/名义额 fail-closed，不能用固定 1 单位演示替代。 |
| 交易与对账 | `StrategyManualTriggerService` 接受显式 side/qty，`OrderCommandStrategyExecutionGateway` 进入 canonical Order/Risk/Execution；SIM adapter/撮合写 Trade/Ledger；OKX/Binance recovery/reconcile 对本地订单做 query-confirm、fill 同步和恢复（`StrategyManualTriggerService.java:51-104`、`OrderCommandStrategyExecutionGateway.java:25-96`、`PaperMatchingService.java:174-221`、`backend/nq-scheduler/.../OkxRestReconcileService.java:405-498`）。 | Phase6 L4/L5/L6 与 GateAUDIT 已接受其指定资格边界；这不证明策略自动实盘。OKX `OkxRecoveryService.rebuild` 从本地非终态订单出发并按 clientOrderId 关联 open orders（`:139-198`）；外部手工交易若无本地 Order，未见可直接导入普通 Ledger/策略持仓的入口，不能假定策略会感知。 | 下一任务只用隔离 SIM 事实；真实账户持仓差异要作为未来 LIVE 前置校验，而非在本轮补交易导入。 |
| 风控与运行 | 订单 Risk、精度/限额、kill/环境开关、run admission/recovery、观测与 stop 均存在；Phase6 资格及 F007 观测接受记录见 [STATUS](../STATUS.md)。 | `StrategyScheduleScanService` 从 definition JSON 的 `side/orderType/quantity/price` 建显式订单，不是指标驱动的目标仓位（`:218-229`）；研究侧 Paper 的 stop/heartbeat 与真实信号处理未接通。过期 bar/规则或未知余额不得静默可交易。 | 下一任务要限定自动 SIM 入口的幂等、过期数据停止、重复触发与恢复语义。 |
| 绩效与复盘 | 回测 `sim_pnl_snapshots` 与 eval；Paper portfolio/diagnostics/strategy evaluation/auto review 有只读 API 和独立前端页面（`frontend/src/pages/paper-trading/PaperTradingRouteShell.tsx:9-24`、`.../PaperReviewsPage.tsx`；`PaperTradingController.java:141-179`）。 | 研究侧 Paper order/trade 输入未形成，汇总页可以诚实显示无数据/无订单，却不能据此给出经过成交、费用、资金流验证的策略绩效。实际外部现金流与持仓口径未知。 | 下一任务用同一模拟事实生成可追溯的 run 结果；收益不是代码通过条件。 |
| 前端业务入口 | Marketdata、Strategies、Research、Backtests、Evaluations、Publishes、Paper 与 Trading 工作台均有路由及 API 调用；Backtests 页能绑定 dataset/version、创建 run，Paper 页能 create/start/stop（`frontend/src/pages/backtests/BacktestsPage.tsx:195-278,483-534`、`frontend/src/pages/paper-trading/PaperTradingRunsPage.tsx:172-236`）。 | 操作性表单存在，但当前被消费的执行策略仍是 fixture，Paper start 只变状态。当前 CI critical E2E 通过不等于一份有实际成交与绩效的同版本业务样例。 | 复用现有页面，只补阻断操作的状态和结果入口，不做全站视觉改版。 |
| V1 后续产品 | instrument/venue 抽象和后端模块边界可作为扩展基础；OKX/Binance 历史行情适配当前显式 `SPOT`（`backend/nq-adapter-okx/.../OkxHistoricalKlineAdapter.java:143-144`、`backend/nq-adapter-binance/.../BinanceHistoricalKlineAdapter.java:127-128`）。 | 在 adapter、marketdata、Risk/Execution 的本轮代表性检索中未建立永续、交割、杠杆、期权或链上各自的数据、资金、风险和结算闭环。V1 仍包含这些后续产品，现货里程碑不以全部产品完成为前提。 | 等现货研究/SIM 结果可操作后，再选下一产品增量；不把抽象接口当已支持。 |

上表为便于阅读缩写的关键源码完整路径如下；行号均对应本次 `76caf387…` 工作树：

- 回测：`backend/nq-backtest/src/main/java/com/guidinglight/nexusquant/research/backtest/config/BacktestDomainConfiguration.java`、`backend/nq-backtest/src/main/java/com/guidinglight/nexusquant/research/domain/backtest/BuiltinFixtureSignalPolicy.java`、`backend/nq-backtest/src/main/java/com/guidinglight/nexusquant/research/application/backtest/BacktestExecutionService.java`、`backend/nq-backtest/src/main/java/com/guidinglight/nexusquant/research/domain/backtest/ExecutionPricingPolicy.java`、`backend/nq-backtest/src/main/java/com/guidinglight/nexusquant/research/domain/backtest/BacktestExecutionContext.java`。
- Paper 与运行：`backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/application/paper/service/PaperTradingRunService.java`、`backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/application/paper/service/PaperRunScheduleService.java`、`backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/paper/PaperMatchingService.java`、`backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/StrategyManualTriggerService.java`、`backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/StrategyScheduleScanService.java`。
- 评价、交易和恢复：`backend/nq-eval/src/main/java/com/guidinglight/nexusquant/research/domain/eval/EvaluationMetricCalculator.java`、`backend/nq-eval/src/main/java/com/guidinglight/nexusquant/research/domain/eval/TradeOutcomeCalculator.java`、`backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/port/OrderCommandStrategyExecutionGateway.java`、`backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/recovery/OkxRecoveryService.java`、`backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/recovery/OkxRestReconcileService.java`。
- 正式入口：`backend/nq-api/src/main/java/com/guidinglight/nexusquant/marketdata/api/web/MarketdataController.java`、`backend/nq-api/src/main/java/com/guidinglight/nexusquant/research/api/web/BacktestRunController.java`、`backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/web/PaperTradingController.java`。

## 3. 代表性链与断点

代表对象选择 **OKX SPOT / BTC-USDT / 1h** 的公开历史 bars、已存在的 dataset→research/backtest config→strategy version→publish→Paper 页面路径；这里只检查实现，不声称数据库中已经存在这样的实测记录。Binance 与其他币对的公共入口同属支持范围，但不作为本次样例替身。

1. **数据→研究→回测**：`MarketdataController` job/run-once → `MarketdataIngestionService` → `marketdata_bars` → `MarketdataDatasetService.refreshQuality/buildDatasetSnapshot` → backtest config 绑定与 `BacktestRunController.start` → `JdbcHistoricalMarketDataPort`/`BacktestExecutionService` → `sim_*` 与 eval。版本字段和 snapshot 存在；bar 可后续 upsert，snapshot 不冻结内容哈希或可见时点。`BacktestExecutionService.buildExecutionRequest` 消费 research config 的 `strategyType`，Bean 仅装配 fixture policy。因此“策略版本已绑定”与“该版本的参数/因子被实际执行”不能划等号。
2. **同版→Paper/Shadow**：`BacktestPublishRecord` → `PaperTradingRunService.create` 固化 publish/version/dataset/config JSON → `start` 改 RUNNING → `PaperRunScheduleService.runScheduleOnce` 记 fire → 页面读取 Paper orders/trades/positions。main Java 源码中未检出研究 Paper order/trade/position 的策略写入调用；该链在 start→策略求值→风控→事实之间断开。`StrategyManualTriggerService`/`PaperTradingAdapter`/`PaperMatchingService` 是另一个 canonical SIM Order/Trade/Ledger 路径，当前要求用户提供 side/qty，未消费上述 publish 的策略信号。Shadow read-only/无副作用预览并非 Shadow trading 已启用。
3. **交易所→账务**：OKX/Binance adapter 对订单/成交有查询和对账基础，canonical `Order`→`Trade`→`Ledger`/Position/Snapshot 及恢复资格已有接受证据；当前 `LIVE=DISABLED` 且真实 private trading 未获通用授权。外部手工交易缺少本地订单时，不应凭对账入口推断自动纳入本地策略资金与持仓；必须在未来真实运行前单独处理外部账户差异。
4. **用户入口与经济口径**：前端可按现有表单准备数据、绑定版本、运行 fixture 回测、查看报告并创建 Paper run；不依赖直接改库完成这些表面操作。但当前不能通过这些操作得到**同一可执行策略定义**下的可信回测→Paper 成交结果。回测 `BUY/SELL/HOLD/CLOSE` 是方向和固定数量，不是目标仓位；scheduler 的 side/qty 更是显式订单。回测用当前 bar 的 closeTime 发信号并按同一 closePrice 成交；缺少可用时点约束。fee/slippage 进入现金/PnL，未见本路径对 equity 二次扣费，但滑点没有体现在成交价，需要下一任务统一成交与绩效解释。10U/100U/1000U 现在可填初始资金，不代表可按最小量、手续费、精度成交。

## 4. 近期研究与当前代码的差距映射

| 候选 | 决定 | 依据 |
| --- | --- | --- |
| A 数据与因子基础 | **本轮应补最小消费者；其余先取得证据** | 复用已有 bars/dataset/quality；为代表性策略补确定的 bar 时序、一个指标、warm-up、输入身份与批量/逐步一致性。完整 Factor Library 与多引擎同名指标待实际消费者出现。 |
| B 可复现研究与验证 | **已有并复用；本轮应补最小结果** | 已有 config/run/version snapshot、eval 与 Python metadata；补可重放 bar 内容身份、参数、成本与一条 benchmark/时间切分样例。DSR/PBO 等只在具体假设、样本和决策需要时选择，不作统一门禁。 |
| C 资金配置与小资金可交易性 | **本轮应补** | 1 BTC fixture 对 10U/100U/1000U 无意义；按预算、现金/在途、最小量/名义额、手续费与零头给出可执行或拒绝原因。真实交易所当前费率/账户余额未知，先用显式有版本的 SIM 假设。 |
| D 执行与运行反馈 | **本轮应补最小 SIM 连接；真实部分等待触发** | 研究 Paper run 无策略订单写入；canonical SIM 路径已有 Order/Risk/Execution/Trade/Ledger。用其事实产生 run 反馈，不新增并行订单/账本 authority；真实对账、外部手工交易与 LIVE 需以后单独立项和授权。 |
| E 业务工作台 | **已有并复用；仅本轮补阻断操作** | 数据准备、回测、Paper、诊断已有页面。增加必要的版本/数据/资金拒绝原因与事实链接即可；没有全站改版需求。 |
| F 后续扩展 | **等待明确触发；当前不需要** | Funding/OI/Basis、永续/交割/杠杆/期权/链上、Agent/MCP、预测市场、复杂组合、高级图表均不是这条现货 SIM 闭环的前置。V1 产品路线保留，不以现货能力外推。 |

补充研究报告的“Production P0”描述长期生产目标的重要性，**不等于 GateAUDIT 缺陷 P0，也不自动成为本轮开发优先级**。下表逐项核对其七项核心能力。表中处置是对**当前单策略现货 SIM 业务切片**的决定；完整平台能力的后置触发另写在同一格。`35985470980` 是当前 SHA 的仓库 CI，历史接受身份见第 1 节，均没有单独证明报告中的七项完整能力已交付。

| 报告章节/结论 | 当前 `dev` 对应实现与消费者 | 已有验证证据 | 当前 V1 场景最小能力 | 真实差距 | 处置与触发条件 |
| --- | --- | --- | --- | --- | --- |
| **Factor Library**（报告约第 190–294 行：PIT、warm-up、批量/增量） | `MarketdataDatasetService` 供回测选 bars；`BacktestSignalPolicy` 只有 `BuiltinFixtureSignalPolicy`，Python `nq_research.strategy.sample_strategy` 独立离线消费 CSV，暂无共用生产因子消费者。 | 当前 SHA Research quality、Backend regression CI 成功；`BacktestExecutionService.java:291-299` 和 `BacktestDomainConfiguration.java:32-39` 证明实际装配是 fixture，不证明通用 Factor。 | 代表性 BTC-USDT 策略的一种有参数、warm-up、缺失/迟到数据语义、固定输入身份的指标及批量/逐步 golden。 | 现有信号没有真实指标或版本执行绑定；dataset snapshot 不冻结 bar 可见性/内容。 | **IMPLEMENT** 一个被同版回测与 SIM 使用的窄策略计算契约。完整 library、六因子、Java/Python 一致性仅在跨引擎同算法有实际消费者时再触发。 |
| **Trial Ledger**（约第 295–381 行：family 与输家留痕） | Java `backtest_runs`/strategy version/publish 与 Python `ExperimentMetadata` 有身份、参数哈希；Python CLI 输出本地摘要，未进入 Java 运行。 | `research/py/src/nq_research/experiment/metadata.py:15-108`、`BacktestRunController.java:58-182`；当前 CI 证明代码回归，未证明完整搜索 family/失败 trial 留痕。 | 此次一条策略版本的输入/参数/代码/结果身份和失败原因可重放。 | 跨多次参数搜索的 family、输家/超时全集未形成正式 ledger。 | **DEFER** 完整 Trial Ledger；出现实际参数扫描/候选筛选、需要调整搜索偏差时定义 family 与失败记录。当前复用已有 run/metadata，不伪称已完成 ledger。 |
| **Random Baseline Engine**（约第 382–449 行：null process） | `nq-eval` 有回测收益/回撤/Sharpe，未在 `backend/**/src/main` 或 `research/py/src` 找到报告所述 null distribution/seed/重采样消费者。 | `EvaluationMetricCalculator.java:48-106` 与当前 CI；没有随机基准业务验收。 | 一条清楚口径的 buy-and-hold/现金基准与时间切分，用于解释单策略结果。 | Null 分布及受时序约束的随机对照缺失，但不阻止先完成实际 SIM 事实。 | **DEFER**；多个候选或“优于随机过程”成为正式研究决策时选合适 null，不能机械 iid shuffle 或预设 100+ trials。 |
| **Statistical Verifier**（约第 450–546 行：可组合检查、OOS/lockbox） | 回测 `sim_*` 与 `BacktestEvaluationService` 已有绩效；`StrategyEvaluationGateService` 聚合数据/发布/Paper 证据，但不是 DSR/PBO/OOS 统计服务。 | `BacktestEvaluationService.java:30`、`StrategyEvaluationGateService.java:40-165`，当前 CI 与历史 GateAUDIT API/前端资格；无报告中统计协议的验收。 | 数据完整性、无前视、成本、基准与一次明确的时间序列样本外区间；结果不可解释时拒绝发布为“已验证 Alpha”。 | 现有评价有指标，缺因果时点及可声明的 OOS/基准协议；没有 lockbox 权限语义。 | **INTEGRATE** 现有评价/门禁与本次必要的因果及时间切分证据；完整 verifier、DSR/PBO/Holm/FDR 按实际假设、family 和权限需求再触发。 |
| **Portfolio Construction**（约第 547–634 行：目标仓位与资本竞争） | 回测有单 run 现金/持仓；`PaperPortfolioService` 汇总 Paper 事实；canonical Risk 有单笔限制。`StrategyScheduleScanService.java:218-229` 从静态 JSON 读 side/qty，无多策略 target allocation。 | `BacktestExecutionContext.java:19-86`、`PaperPortfolioService.java:49`、Risk 单测/本 SHA backend CI；未证明研究 Paper 的竞争资金分配。 | 单账户单策略的预算→目标敞口→增量订单、现金和在途预占，10/100/1000U 的可交易/拒绝解释；双策略竞争作并发边界测试。 | 小额资金配置与订单竞争缺连接；完整风险平价/优化器目前无输入。 | **IMPLEMENT** 最小资金/目标敞口约束；多 Alpha allocator、vol targeting/ERC 仅在两个可用策略真实争用资金并有风险估计时触发。 |
| **Execution / TCA**（约第 635–743 行：预期成本与实际执行差异） | `FeeModel`/`SlippageModel`、SIM `PaperMatchingService`、Order/Trade/Ledger 与对账已存在；Paper diagnostics 是规则化只读归因。 | `BacktestExecutionContext.java:69-86`、`PaperMatchingService.java:174-228`、历史 Phase6 L4/L5/L6 接受及当前 CI；真实 TCA 没有现时订单样本/接受证据。 | 明确决策时价、下一可交易时点、SIM fill/fee/slippage、成本一次性进入权益；同一 run 能对比回测假设与模拟事实。 | 回测同 bar close 成交，研究 Paper 无执行事实；尚无真实 arrival/fill/impact 数据。 | **INTEGRATE** 现有执行与成本口径，先形成 SIM expected-vs-simulated 反馈；真实 TCA/容量估计待受控真实成交且明确授权及可比较样本后启动。Paper/Shadow 不能冒充真实 TCA。 |
| **Decay / Regime / Capacity Monitor**（约第 744–879 行：退化与生命周期） | Paper `MonitorRunService`、日报/告警、诊断/复盘与运行 stop/recovery 已有；没有在代表性主链看到以多期净收益/成交退化驱动的正式统计生命周期。 | `PaperRunMonitorService.java:32`、`PaperTradingController.java:285-520`、历史 F007 观测及当前 CI；不证明 HMM/CUSUM/容量监控。 | 本次仅保证 run 可停止、阻断、恢复、复盘，状态与异常有事实依据。 | 尚无真实、连续策略样本可判断 decay/regime/capacity。 | **DEFER**；重复 Paper/真实运行形成足量时序和明确风险动作后再选检测方法。暂停、减仓、撤单、恢复必须分别核对在途订单/敞口语义，不能以降低资金推断整体风险必降；不热改旧版本。 |

**严重度、优先级、长期重要性分开记录**：同 bar 信号/成交与研究 Paper 无事实写入是“当前目标的业务正确性阻断”，下一任务优先级为**现在**，不擅自改写 GateAUDIT 的 P0/P1；资金预算/在途冲突是同一最小闭环的验收边界。完整 Trial Ledger、随机基准、TCA、退化监控在报告的长期生产目标中重要，但当前无真实消费者或可比较样本，开发优先级为**触发后**。报告对 Agent 安全、权限和人审的观点与当前 Java 控制面边界相符，但 AI/DH 未启用，不新增 Agent 权限或交易入口。

## 5. 唯一下一阶段主线

**选定：NQ-V1-SPOT-ONE-STRATEGY-BACKTEST-TO-SIM-CLOSED-LOOP（单策略现货研究到 SIM 运行闭环）。**

它解决的用户问题是：用户能否从一个明确的数据集与策略版本出发，得到因果正确、成本可解释的回测，然后在隔离 SIM 中由**同一策略**产生命令和成交/持仓/资金结果，并在现有页面追溯，而非只能得到 fixture 回测和空 Paper run。关键缺口由第 3 节的实际调用链与唯一 Bean、同 bar 成交、Paper start/fire、缺失写入调用共同证明。可复用 public bars/dataset、config/version/publish、`sim_*`/eval、canonical SIM Risk/Execution/Order/Trade/Ledger、现有 UI 和 CI。完成后新增一个可操作、可反复验证的 OKX SPOT BTC-USDT 样例；10/100/1000 USDT 是独立测试预算，不是收益承诺或真实资金授权。

比较过的另外两项：按研究报告立即建设完整 PIT/Factor/Trial/Null/Verifier 平台，会先产生多数无当前消费者的能力，且不修复 Paper 断点；直接做合约/杠杆增量会叠加新市场的结算和风险语义，却留下现货研究→SIM 断点。仅做一次现有链验证也不够：fixture 唯一装配与 Paper 写入断点是静态确定事实，重复运行不能使连接出现。研究报告的 BTC 1h PoC、六因子、100+ trials、HMM 和周数均是设计参考而非硬门槛。现阶段不需查询交易所官方规则来决定主线；下轮若验收具体量/精度依赖最新规则，仅定向读取 OKX 官方 public instruments 与显式费率假设，不访问私有账户。

当前已具备本地源码、CI 与可构造的隔离测试输入；**未证实**当前数据库有足量真实 bars、当前服务器部署了本 SHA、真实账户余额/费率/权限，也没有生产或 LIVE 授权。下一任务的实现与隔离验收不依赖这些真实私有数据；若没有合格历史 bars，使用可标识的合成 fixture 验证算法，再以公开数据在非生产环境做业务 smoke，并明确区分两类结果。

## 6. 下一轮可直接执行的任务书（拟议，未启动）

### 名称、目标和前置

`NQ-V1-SPOT-ONE-STRATEGY-BACKTEST-TO-SIM-CLOSED-LOOP`。目标：用户通过现有正式 API/前端完成 OKX SPOT BTC-USDT 单策略版本的数据准备→回测/评价→发布→隔离 SIM run→订单/成交/持仓/现金/PnL 复盘；相同输入的重复触发保持幂等，不向真实交易所发送 private mutation。

实施时重新读取 `origin/dev`、工作区和 current authority；本报告的 `76caf387…` 仅是分析基线，不自动成为未来实现基线。先选择有足够连续 bars 的非生产 dataset，并固定来源、范围、原始 bar 内容 digest/采集批次与每条 bar 的可见时间；若数据不足，任务先在可审计的合成 fixture 上完成算法证明，不把它标成交易所历史实测。公开市场数据请求必须有超时和有界重试。SIM 费用、滑点、预算和规则使用显式参数/版本；真实费率、余额和最新 venue rules 缺失时输出 `UNKNOWN/NOT_TRADABLE`，不得默认为 0 或合规。

### 唯一实现范围与 owner

1. `nq-backtest` 的 `BacktestSignalPolicy`/`BuiltinFixtureSignalPolicy`、`BacktestExecutionService`、`ExecutionPricingPolicy`：保留历史 fixture 兼容，新增**一个**可复现的现货样例策略（建议收盘后才可计算的短/长均线目标敞口，参数写入策略版本）；warm-up、缺 bar、相同时间戳、迟到补采、下一可交易 bar 价格及 fee/slippage 一次性入账均给出确定语义。信号使用目标敞口/目标数量的明确含义，执行侧从当前现金、持仓、在途单算增量；零头和无法交易要可解释。新策略定义/计算器归 `nq-backtest` 或已存在 `nq-core` 策略 owner，按真实共享消费者选择，禁止在 Python/前端另造交易决策器。
2. `nq-research` 的 research/backtest config、strategy version/publish 与 `PaperTradingRunService`：确保真正执行的是绑定且冻结的同版参数、指标和 dataset 身份；研究侧 `paper_trading_*` 如需展示，只能作为 canonical SIM fact 的只读投影/关联，不能成为第二套订单、成交、持仓或账本 authority。先明确历史 Paper run 兼容读取与新增 run 身份映射；不回填或改写历史事实来制造成交。
3. `nq-core`/`nq-risk`/`nq-scheduler`/`nq-ledger`：用既有 `StrategyManualTriggerService`、`OrderCommandStrategyExecutionGateway`、SIM adapter/撮合、Trade/Ledger 与恢复接口完成同策略版本的一次受控 SIM 决策。新增的自动驱动只在隔离 SIM profile/显式开关下装配；禁止直接写 Order/Trade/Ledger 表。限定账户+策略+bar 窗口的稳定幂等身份、资金预占/在途订单、最小量/名义额/精度、过期数据 fail-closed、stop 后不再发单；异常与重复执行须可恢复。真实外部手工交易的账户差异属于未来 LIVE 前置，不用本任务的 SIM 结果代替。
4. `nq-api` 和 `frontend/src/features/backtests`、`features/paper-trading`、现有页面：只补用户完成上述链的最小创建/启动/停止、阻断原因、事实与版本跳转。正式入口不得要求改库或临时脚本。`research/py` 本轮不接 Java runtime；若只作为离线参考，保持独立，不要求同指标跨语言重复实现。
5. 存储先复用已存在的 dataset、strategy version、backtest/publish、strategy run、canonical Order/Trade/Ledger 和 Paper 查询结构。确需记录不可变输入 digest、bar 可见时点或投影关联时，由 `nq-infra`/Flyway owner 提出**新的 forward-only migration**，不得修改旧 migration。新增文件必须落在上述业务 owner 目录；不新增通用 Factor/Portfolio/Agent 框架。

### 必须通过的验收

- 定向单测：warm-up 前无信号；缺 bar/迟到数据 fail-closed；仅已收盘 bar 产生信号，下一可交易时点才可模拟成交；相同版本/参数/数据字节批量和逐步计算一致；不同版本或数据 digest 不共享旧结果；fee/slippage/现金/PnL 不重复扣费。
- PostgreSQL/Flyway 隔离集成：一条 OKX SPOT BTC-USDT 连续公开或标记为 synthetic 的数据集，经正式 API/页面到回测、评价、发布和 SIM run，检查 version/dataset digest、订单→成交→持仓/账本→报告链；重复触发、并发窗口、部分失败与重启恢复不增加第二份 accepted side effect。若设计只产生无订单/拒绝，必须显示具体业务原因，不把它计为“成交通过”。
- 预算样例：10U/100U/1000U 各单独配置；可交易则正确折算数量、费用与余额，低于规则则稳定拒绝并解释，不能固定买 1 BTC。测试覆盖最小量、精度、手续费吃掉可用余额、在途订单/两个策略争用、零头与 stop/过期数据。收益可正可负，不是验收门槛。
- 独立于历史 GateAUDIT 的**本次候选**审查必需，因为会触及策略时点、资金、并发和 canonical SIM 订单/账务连接。审查只覆盖本次差异与相关不变量；定向测试/集成成功后，再按有效交付合同取得候选对应 CI，不能复用本报告的 35985470980 冒充新实现 CI。测试不得访问生产 DB、真实 credential 或外部交易 mutation。

### 回退、边界和后续授权

实现失败时停止 SIM driver，撤销本轮代码变更并保留错误 run/evidence 供诊断；已写入的隔离 SIM 事实按身份清理测试资源，不静默删除用户历史数据。若用了新 Flyway migration，代码回退与数据恢复分别设计：生产已执行的 migration 不可逆改写，须以新 forward migration/备份恢复处理兼容问题，且本任务默认不执行生产迁移。

本报告**不授权**本地 commit、push、PR、merge/tag、生产部署、真实 private read、LIVE、真实 PLACE/CANCEL、真实资金或外部通知。下轮若需这些 Git 发布动作、生产写入或真实交易，按动作取得明确授权；隔离实现与测试不能自动扩成上线。

## 7. 后置事项及触发

- 完整 Factor Library、跨 Java/Python 一致性：出现两个以上真实共享消费者或正式离线→运行导入合同时再立项。
- 更复杂的统计检验、OOS/DSR/PBO、组合优化：出现具体策略假设、样本量与决策用途后按需选用；基础 benchmark 与时间切分可在下一任务最小加入。
- 外部手工交易纳入账户/策略、真实费率与资金预占、第二交易所自动 LIVE、合约/杠杆/期权/链上：需明确产品边界、账户事实与授权后再做，不能由 Paper/历史 pilot 推导。
- AI/DH/Agent/MCP、预测市场、复杂图表、全站视觉优化、P2/P3 治理残余：目前不阻断这条现货 SIM 业务结果；仅在对应消费者、故障或性能证据出现时重评。

## 8. 本轮验证记录与限制

执行：`git status --short`、`git branch --show-current`、`git fetch origin`、`git rev-parse HEAD`、`git rev-parse origin/dev`、`git log origin/dev -n 12 --oneline`；GitHub PR #20–22 readback、`gh run view 35985470980` 与 commit check-runs（9/9 success）；定向 `rg` 与源码/文档读取；补充研究文件的路径、大小、mtime、SHA-256 核验及指定章节通读。未运行新的 Maven、Playwright、Python 或数据库 smoke：静态调用链已经明确决定下一任务，当前 exact-head CI 可复用为仓库回归证据；缺乏隔离数据库的当前业务样例，运行普通测试也不能关闭该连接缺口。未读取真实密钥或私有账户，未触发交易、调度或生产操作；研究文件内引文没有二次外部核验。
