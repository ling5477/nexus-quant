# NexusQuant V1 产品收敛计划

> 规划基线：`dev=origin/dev=7cbc7a96a1f7ea2c587c562dba8e7bc503312858`，2026-09-29。本文是待交付、待接受的产品计划；当前机器状态、CI 与授权仍以 [STATUS.md](STATUS.md) 为准。GateZ 已冻结于 `d106775b6c8a9e17cddb6b99e96c9420fa46df63`，tag `nq-gatez-freeze` peeled commit 与之相同。本文不声明 fresh install、服务器连续运行或 V1 发布已通过。

## 1. WHY、范围与唯一主链

V1 要让个人开发者用正式账户上下文完成 OKX Spot 的可解释研究与隔离 SIM 经济闭环，并在停止、重启后继续观察同一组事实。当前代码已经分别具备账户、行情、研究、canonical 交易和调度部件，但它们的正式入口、身份连接与最终用户视图尚未经空库全链验证。后续修改只针对主链的具体 `X → Y` 断点，在 Y 的 canonical owner 内完成。

唯一产品主链：

```text
Login → Exchange Account → Marketdata → Dataset → Strategy Definition
→ Strategy Version → Backtest → Evaluation → Publish → Continuous SIM
→ Strategy Decision → Risk → Order → Paper Matching → Trade → Ledger
→ Position / Cash / PnL → Monitoring / Audit → Frontend
                                      ↘ Stop → Restart → Resume → Recovery
```

V1 范围限正式 UI/API、单用户可操作的 OKX Spot BTC-USDT 1h、一个受支持的冻结策略、公开已收盘行情、回测/评价/发布、隔离 Continuous SIM 与 canonical 经济事实。V1 的 `LIVE=DISABLED`、kill engaged、`private_trading=NOT_IMPLEMENTED`；已有只读账户事实不构成自动真实交易要求。策略盈利不是验收条件。

V1 明确不含全系统 `accounts → exchange_accounts` ID 迁移、完整 Factor Library、VectorBT/Freqtrade、Trial Ledger、Random Baseline Engine、DSR/PBO/Holm/FDR、复杂组合优化、第二交易所、Binance、期货/杠杆/期权、链上、AI/DH/Agent/MCP 交易、Polymarket execution、HFT。只在空库主链证明它们之一实际成为阻塞时才重新审议。V1.1 仅记录真实账户持续同步、private API runtime、自动 LIVE 下单、小额实盘、LIVE 对账与恢复；本计划不实施这些能力。

判定优先级：完整业务链一致性 > canonical authority 唯一 > 模块边界 > 状态与数据唯一性 > 最小改动 > 单个任务方便程度。页面缺事实时先修 canonical projection，不创建页面专属经济事实。`CONSOLIDATE_BEFORE_V1` 只用于正式主链双来源、同一事实双生产 writer、正式调用 ID 歧义、空库依赖 SQL/fixture/hidden endpoint、正式页面同状态不一致或重启不能恢复。其余一律选择本表列出的明确 disposition；缺少运行证明的项目记为验收待证，不伪称已失败。

## 2. Canonical Fact Authority Matrix

表中 `legacy` 指读取/写入兼容，不授予它新的 V1 authority。路径均为本基线的代码或 Flyway 入口；`待证` 表示还没有空库端到端运行结论。

| Fact | Canonical owner | Canonical table/model | Writer | Reader / projection | Legacy compatibility | V1 disposition |
| --- | --- | --- | --- | --- | --- | --- |
| User | `nq-auth` | `users` (V1) | `JdbcAuthUserRepository`、显式 `AuthBootstrapAdminConfiguration` | `DbAuthService`、`/api/auth/me`、Login | local/test seed 非正式验收路径 | KEEP_CANONICAL |
| Exchange Account | `nq-core/account` | `exchange_accounts` (V12) | `ExchangeAccountCommandService` / `JdbcExchangeAccountRepository` | account API、AccountsPage、current user | `accounts` 由事务化 `CanonicalLegacyAccountBridgeService` 绑定 | KEEP_COMPATIBILITY_FOR_V1 |
| Strategy Definition | `nq-core/strategy` | `strategy_definitions` (V5) | `StrategyDefinitionService`；发布 `JdbcExecutionStrategyDefinitionWriter`；SIM `StrategySimRunService` | strategy API、StrategiesPage、dispatch | 三种写入分别生成用户、发布、隔离 SIM 行；须验证 source lineage 与同一 invariant | NEEDS_C1_USAGE_VERIFICATION |
| Strategy Version | `nq-core/strategy` | `strategy_versions` (V19) | `StrategyVersionService` / `JdbcStrategyVersionRepository` | StrategiesPage、回测和发布快照 | `strategy_definitions.version` 是执行定义版本字段，非同一 `strategy_code` 下的冻结版本序号 | KEEP_CANONICAL |
| Dataset | `nq-core/marketdata` | `marketdata_datasets`、coverage (V18) | `MarketdataDatasetService` / JDBC repository；公开捕获服务 | MarketdataPage、backtest config | 研究配置引用不成为数据源 | KEEP_CANONICAL |
| Market Bar | `nq-core/marketdata` | `marketdata_bars` (V13/V52)、`public_market_captures` (V53) | ingest / `PublicMarketReplayCaptureService` | dataset quality、closed-bar feed、backtest | capture 为来源及可重放证据，不是第二套已消费 bar | KEEP_SEPARATE_DOMAIN |
| Backtest Run | `nq-backtest` / `nq-research` | `backtest_configs`、`backtest_runs`、`sim_*` (V7/V8) | `BacktestExecutionService` / persistence | BacktestsPage、evaluation、publish | `sim_*` 仅研究执行事实，不是运行中的 canonical Order/Trade | KEEP_SEPARATE_DOMAIN |
| Evaluation | `nq-eval` | `backtest_eval_reports` (V9) | `BacktestEvaluationService` | EvaluationsPage、publish admission | 无第二评价 authority 证据 | KEEP_CANONICAL |
| Publish | `nq-research` | `backtest_publish_records` (V10/V19) | `BacktestPublishService` | PublishesPage、Strategy SIM admission | 可空旧版本绑定、legacy artifact locator 仅保历史 | KEEP_COMPATIBILITY_FOR_V1 |
| Continuous SIM Run | `nq-scheduler/paper` | `continuous_sim_runs/bars` (V56) + `paper_trading_runs` | `ContinuousSimRunService` | `StrategySimPanel`、job status | V56 游标与 Paper run 是运行控制/容器，不是交易事实 | KEEP_CANONICAL |
| Strategy Run | `nq-core/strategy` | `strategy_runs` (V1/V5)、durable dispatch (V51) | strategy trigger / dispatch | RunsPage、Order lineage、recovery | Paper run ID 与 strategy run ID 保持类型区分 | KEEP_CANONICAL |
| Decision | `nq-scheduler/paper` | `strategy_sim_decisions` (V52) | `StrategySimRunService` | StrategySimPanel、Order lineage | 决策记录不是 Order/Trade | KEEP_CANONICAL |
| Risk Decision | `nq-risk` | `risk_events` (V1) / `PreTradeRiskService` | canonical pretrade risk | trading queries、Paper diagnostics | `paper_risk_check_results` 只属历史研究 Paper | KEEP_SEPARATE_DOMAIN |
| Order | `nq-core/trading` | `orders` (V1/V5) | `OrderCommandWriteService` / `JdbcOrderRepository` | trading API、Strategy SIM canonical facts、Paper UI | `paper_trading_orders` 只读历史 Paper；SIM 不可写第二订单 | KEEP_COMPATIBILITY_FOR_V1 |
| Trade | `nq-core/trading` + `nq-scheduler/paper` matching | `trades` (V1/V5) | `PaperMatchingService` 经 canonical 成交路径 | trading API、Strategy SIM canonical facts | `paper_trading_trades` 只读历史 Paper | KEEP_COMPATIBILITY_FOR_V1 |
| Ledger | `nq-ledger` | `ledger_entries/events` (V1) | `TradeLedgerPostingService`、SIM funding | cash/PnL projection、audit/reconcile | `sim_*` 研究账不代表运行账 | KEEP_CANONICAL |
| Position | `nq-core/trading` projection | `positions` (V1) | canonical fill/ledger projection | Paper canonical facts、Trading/Paper views | `paper_trading_positions` 只读历史 Paper | KEEP_COMPATIBILITY_FOR_V1 |
| Account Snapshot | trading/account projection | `account_snapshots` (V1/V55) | snapshot projection/readonly observer | Trading/account read models | 旧 NULL provenance 保持 UNKNOWN，不重分类；不得等同 whole-venue balance | KEEP_COMPATIBILITY_FOR_V1 |
| Scheduler Job | `nq-scheduler/control` | `scheduled_job_controls` (V57) + static registry | `SchedulerDispatcher` / management service | SchedulerJobsPage、status API | `paper_run_schedules` 是历史研究 Paper 日程 | KEEP_SEPARATE_DOMAIN |
| Paper Run | `nq-research/paper` | `paper_trading_runs` (V21/V52) | `PaperTradingRunService`、Strategy SIM | PaperTradingRunsPage、StrategySimPanel | 旧 Paper economic tables 保留历史读；新 Strategy SIM 读 canonical facts | KEEP_COMPATIBILITY_FOR_V1 |
| Audit | `nq-core/audit` | `audit_logs` (V1) 及专域审计 | `JdbcAuditLogRepository` 等专域 writer | audit/diagnostics views | 专域审计不取代 Order/Trade/Ledger | KEEP_SEPARATE_DOMAIN |
| Alert / Monitor | monitoring + Paper research | `paper_run_alerts` (V24)、job status/health | monitor services / dispatcher | PaperDiagnosticsPage、RuntimeReadinessPage、SchedulerJobsPage | 历史 Paper alert 与运行健康分域 | KEEP_SEPARATE_DOMAIN |

## 3. 重复 authority 与主链缺口

| 发现与代码依据 | 对主链的影响 | Disposition / 收敛边界 |
| --- | --- | --- |
| `exchange_accounts` 是用户账户；`accounts` 仍被 Strategy/Order/Ledger FK 使用。SIM 创建时 `ExchangeAccountCommandService` 同事务调用 `CanonicalLegacyAccountBridgeService`，写 `legacy_account_id`。 | 当前有明确单向 identity bridge；需在空库证明正式 UI 创建后可一路使用，禁止前端猜 `3001`。 | KEEP_COMPATIBILITY_FOR_V1；全系统 ID 迁移 DEFER_POST_V1。若正式路径出现两份账户状态才升级 C1。 |
| `orders.venue` / `trades.exchange` 与 `exchange_code`；`external_order_id` 与 `exchange_order_id` 在 V5 有回填/同步，`JdbcTradingQueryFacade` 仍读旧列。 | 同一外部身份有兼容列，SIM 无真实交易所订单号；不可凭 NULL 判成 V1 交易阻塞。 | KEEP_COMPATIBILITY_FOR_V1；C1 固定 API 语义与一致性断言，全面列迁移 DEFER_POST_V1。 |
| `JdbcStrategyDefinitionRepository.insert`、`JdbcExecutionStrategyDefinitionWriter.publish`、`StrategySimRunService.create` 均写 `strategy_definitions`。`ResearchToExecutionMapper` 为发布行分配 `str-pub-*` / `pub-*`，SIM 行分配 `sim-sim-*`，并在快照中保存来源身份。 | 确有多个 production INSERT 入口，但目前分别写不同用途的行；未证实同一事实被双写或同一 invariant 被绕过。正式链必须用来源 ID/版本快照证明关系。 | NEEDS_C1_USAGE_VERIFICATION；只有 fresh-install 主链实际证明同一业务 identity 被多入口创建或 invariant 被绕过，才升为 CONSOLIDATE_BEFORE_V1；否则按领域职责保留或做普通维护清理。 |
| V5 `strategy_definitions.version` 随执行定义创建为 1，V19 `strategy_versions.version` 按 `strategy_code` 唯一并冻结参数/来源 checksum。 | 字段同名但语义不同；前端/发布必须用 `strategy_version_id` 指向不可变策略输入。 | KEEP_SEPARATE_DOMAIN；C1 以契约与页面说明消除误用，若正式 API 混 ID 才更改调用。 |
| V21 历史 `paper_trading_orders/trades/positions` 仍有 research Paper writer；V52 以不可空 `canonical_account_id` 区分新 Strategy SIM，`PaperTradingRunService` 为该类 run 调 `JdbcPaperRunCanonicalFactsRepository` 读 `orders/trades/positions`。 | 两套 Paper 经济表存在，但新 SIM 已读 canonical；历史页面必须按 run 类型分流，不能合并计数。 | KEEP_COMPATIBILITY_FOR_V1；旧 Paper 禁止进入新 SIM writer；空库 UI 一致性验证在 C1/C3。 |
| V57 `scheduled_job_controls` 默认全 disabled；`ScheduledJobRegistry` 固定登记 `CONTINUOUS_SIM_POLL` 与 `PAPER_MATCHING`；`SchedulerDispatcher` 的 `@Scheduled` 是单一业务 dispatch。其他 `ScheduledExecutorService` 命中 OKX/Binance WS client 与 smoke runner，不承担这两个 job。 | 正式 UI/运维 API 必须启用两个 job 并证明自动运行与重启恢复；无生产可达的重复 SIM loop 证据。 | KEEP_CANONICAL；C1 验证自动化，C4 长时观察；WS 基础设施 KEEP_SEPARATE_DOMAIN。 |
| 公开捕获 API `PublicMarketReplayCaptureController` 仅在 `public-marketdata-manual` profile + outbound 开关装配；MarketdataPage 创建 ingestion/dataset 的默认值仍是 `BINANCE` / `1m`，前端无 public-captures consumer。 | 已知第一断点是正式前端没有 OKX public capture 操作入口；正式 API 已存在，C1 应先在 fresh install 中重现并定向补齐，再继续整条主链。 | KEEP_CANONICAL；复用现有 public marketdata owner/受控操作，由 MarketdataPage 消费；不创建新表、子系统或调度器。 |
| `AuthBootstrapAdminConfiguration` 只在显式配置下用外部 password hash bootstrap；local/test `AuthSeedConfiguration` 不是正式路径。 | 空库 Login 应先按受控正式 bootstrap 流程验证；不使用 fixture 或隐含账户。 | KEEP_CANONICAL；C1 形成可重复的安装步骤和正式登录证据。 |
| 回测实现有 fee/slippage 模型和 public replay 假设；本次生产代码搜索未发现可消费的 Cash/Buy & Hold benchmark 或显式 OOS 结果。 | 不能从成本模型推导最小研究有效性已通过。 | KEEP_CANONICAL；C2 仅在既有 backtest/evaluation owner 补简单 benchmark、单一时间前后切分与结果展示。 |

上述结论是静态代码与 schema 盘点。**已确认的实现缺口**是正式 benchmark/OOS 结果及 OKX public 捕获的 UI 入口；**待验收门槛**是空库全链、跨页一致性、自动调度、重启与 48–72h 服务器运行。它们在运行前不能写成已失败或已通过。基线 `StrategySimRunService.create` 每 run 建隔离 `accounts` 行；V52 对 `paper_trading_runs.canonical_account_id` 加唯一约束，因此 canonical facts 按账户关联具备 run 隔离，C1 仍须用两次 run 实证。

审计锚点：[账户命令](../../backend/nq-core/src/main/java/com/guidinglight/nexusquant/account/application/service/ExchangeAccountCommandService.java)、[账户 bridge](../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/jdbc/CanonicalLegacyAccountBridgeService.java)、[策略发布 mapper](../../backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/application/ResearchToExecutionMapper.java)、[Strategy SIM](../../backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/paper/StrategySimRunService.java)、[canonical Paper 读模型](../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcPaperRunCanonicalFactsRepository.java)、[调度注册表](../../backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/control/ScheduledJobRegistry.java)、[公开捕获 API](../../backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/marketdata/PublicMarketReplayCaptureController.java)、[Marketdata 页面](../../frontend/src/pages/marketdata/MarketdataPage.tsx)。

### V1 blockers 与延期残余

| 项目 | 当前判断 | 关闭证据 |
| --- | --- | --- |
| 正式 UI 缺 OKX public 捕获/来源入口 | 已确认产品缺口；现有 API owner 保留 | C1 正式 UI/API 连到同一 capture/dataset identity |
| benchmark 与显式 OOS 结果 | 已确认研究最低要求缺口 | C2 同数据、版本、成本假设下可重算与展示 |
| 空库自然全链、自动调度、跨页一致、重启恢复 | 当前未验证的 V1 验收门槛，不能记成已失败 | C1/C3 的 fresh install 与重启证据 |
| 48–72h server RC | 当前未运行的 V1 验收门槛 | C4 不间断观察、恢复与资源记录 |

`DEFER_POST_V1` 残余：全系统账户 ID 迁移、`venue/exchange/exchange_code` 与外部订单号兼容列清理、旧 Paper 经济表物理退休、完整量化研究框架、第二交易所及所有真实自动交易。`exchange_accounts ↔ accounts` 的正式 SIM bridge 保持 `KEEP_COMPATIBILITY_FOR_V1`；只有 C1 证实 ambiguous identity、wrong account、manual repair 或 cross-run mismatch 才升级为阻塞。`RETIRE_BEFORE_V1` 目前无已证实对象；若 fresh install 证明旧路径被新 SIM 错读，再依主链条件重新分类，不预设删库。

## 4. Fresh Install 与研究、UX、监控验收

用新 PostgreSQL 16、当前 Flyway、新 backend/frontend、正式初始化管理员配置及正式 UI/API，记录每一步输入 ID、响应、可见页面和重启前后事实指纹。禁止手写 SQL/发布行/账务行、fixture account、硬编码 `3001`、`manual matchOnce` 或测试 endpoint。顺序为 Login → OKX SIM Exchange Account → 公开已收盘 bar → Dataset → Definition/Version → Backtest/Evaluation/Publish → Continuous SIM → 自然决策 → Risk/Order → scheduler 自动 Paper fill → Trade/Ledger → Position/Cash/PnL/UI → Stop/Restart/Resume。此为未来 C1/C4 验收程序，不是本次已执行结果。任何断点先定位 X→Y，再修 Y owner；不加第二套事实。

研究最小值：闭合 bar 与可见时间、下一执行时点、数据集和策略版本 identity、确定性重放、显式 fee/slippage；增加 Cash 或 Buy & Hold 简单基准，并在同一时间轴上标明一个先 In-Sample 后 Out-of-Sample 的观察区间及各自结果。禁止随机 K-fold 作为最终时间序列验证。评价须显示假设、输入、成本和 OOS 局限，不宣称盈利或统计显著性。

正式 UI 必须回答：当前账户与版本/数据集、回测和发布理由、SIM/调度状态、最新 bar/决策及无订单原因、Order/Trade、持仓/现金/PnL、错误/告警、重启后恢复。复用 Accounts、Marketdata、Strategies、Backtests、Evaluations、Publishes、Paper、SchedulerJobs、RuntimeReadiness 页面和 API；跨页数字要按同一 canonical run/account 与同一估值时点核对，UNKNOWN 不显示为 0。

监控最小值：health、scheduler job 状态、Continuous SIM 游标/阻塞原因、最新决策、Order/Trade、cash/position/PnL、错误/告警、重启恢复时间与状态。现有 job status、PaperDiagnostics、RuntimeReadiness 优先；不创建新 observability 平台。

## 5. 产品包与验收顺序

每包开始时固定候选和允许修改范围；回滚仅针对该包新增的配置或实现，保留已生成的事实/失败证据，不对生产业务数据做破坏性恢复。

### V1-C1 — Fresh Install Golden Path Closure（首个实现任务）

- WHY / Business problem：当前 V1 已有大量独立能力，但尚未证明普通用户从 fresh install 开始，只通过正式 UI/API 能完成整条产品主链。C1 是一个连续的产品闭环工作流；OKX public capture 是已知第一个断点，不是 C1 的产品目标。
- Canonical chain segment：Fresh PostgreSQL → Backend/Frontend → Login → SIM Exchange Account → Public Marketdata → Dataset → Strategy → StrategyVersion → Backtest → Evaluation → Publish → Continuous SIM → Decision → Risk → Order → Paper Matching → Trade → Ledger → Position/Cash/PnL → UI → Stop → Restart → Resume。
- Current implementation / Gap：正式账户 UI/bridge、公开捕获 API、SIM、V57 job 均已有；正式前端缺 OKX public capture 操作入口，MarketdataPage 默认 Binance/1m，V57 job 默认 disabled；全链未验收。多策略定义 INSERT 入口用途不同，需以主链验证 source lineage 与 invariant，不能直接定为双 authority。
- Minimal implementation / likely files：从 fresh install 自然执行；每遇断点，定位根因 → 确定 canonical owner → 在现有架构内修复 → 验证 → 继续同一 Golden Path。首个预计在 Marketdata；复用 `nq-app`/marketdata 的受控 public API，由 `frontend/src/pages/marketdata` 消费。后续只按实证断点触达既有 API、投影、配置或相关 owner。
- What must NOT change：私有 OKX、LIVE、手工 SQL、fixture、第二撮合/账务路径、默认打开出站权限、既有冻结版本与历史 Paper 事实；不做全账户 ID/订单列迁移。
- Acceptance：新 PG 与正式 UI/API 完成第 4 节全链；用户定义/版本/发布/SIM 的来源 ID 可追；自动 `CONTINUOUS_SIM_POLL`/`PAPER_MATCHING`，不调用 manual match；两 run 隔离、重复 tick 不增 Trade/Ledger、Stop/Restart/Resume 成功。不得对单个断点另建平行子系统/事实源后宣布局部成功。
- Rollback / Dependencies / Estimated effort：先停并禁用公开出站与两个 job，再回退新增 UI/API 适配；保留 SIM 事实供审计；依赖当前基线，无前包；7–10 工作日。

### V1-C2 — Minimum Research Validity

- WHY / Business problem：只有策略收益和费用模型，用户不能判断其相对简单持有的表现或样本外表现。
- Canonical chain segment：Dataset → Backtest → Evaluation → Publish。
- Current implementation / Gap：已有 fee/slippage、冻结输入和 deterministic replay；未找到正式 benchmark/OOS 结果消费者。
- Minimal implementation / likely files：在 `nq-backtest`/`nq-eval` 的既有 run/report 中计算 Cash 或 Buy & Hold 与单一按时间前后切分；受影响 `nq-api`/Backtests/Evaluations 只展示这些字段。
- What must NOT change：策略执行算法、look-ahead/closed-bar 约束、发布身份、研究平台扩展。
- Acceptance：同一 dataset/version/cost 下可重算基准和 OOS；时序无重叠/无未来 bar；UI/API 显示 timing、fee、slippage、区间与缺数据状态。
- Rollback / Dependencies / Estimated effort：回退新增 report/展示契约并保留旧报告可读；依赖 C1 的 dataset/version 身份稳定，可与 C1 后半段并行；3–5 工作日。

### V1-C3 — Product UX Closure

- WHY / Business problem：主链虽可能执行，跨页若以不同 run/账户或历史 Paper 表展示经济事实，用户仍无法复盘。
- Canonical chain segment：Decision → Order/Trade/Ledger → Position/Cash/PnL → Monitoring/Audit → Frontend。
- Current implementation / Gap：StrategySimPanel 已读 canonical facts；Paper/Trading/Runtime/Scheduler 页面已有局部消费者；完整一致性和无订单理由未验收。C1 负责能触发主链必需动作，C3 才负责跨页解释、导航和用户可见状态收口。
- Minimal implementation / likely files：修现有 `nq-infra` canonical query/projection、`nq-api` read DTO、相关 Paper/Trading/Scheduler 页面；不加 page-specific fact。
- What must NOT change：交易 writer、账本语义、历史 Paper 原始记录、未知值语义。
- Acceptance：同一 run 的订单、成交、现金、仓位、PnL 与 canonical 查询一致；NO_SIGNAL/RISK_REJECTED 等原因可见；异常、job 与恢复可见，重启后相同。
- Rollback / Dependencies / Estimated effort：回退展示与新只读投影，保留 canonical facts；依赖 C1 可产生真实全链样本，3–5 工作日。

### V1-C4 — Server Continuous SIM RC

- WHY / Business problem：本地回放和 CI 不能证明公开行情、调度、撮合、数据库和前端连续运行或恢复。
- Canonical chain segment：完整 Golden Path + Stop/Restart/Resume/Recovery。
- Current implementation / Gap：默认关闭的公开出站/Continuous SIM 和调度控制已有；无本 V1 候选的 48–72h 连续服务器观察。
- Minimal implementation / likely files：主要是受控 RC 配置、部署与验收记录；仅对观察到的 canonical owner 缺口另开修复候选。需另获生产部署明确授权及 current authority。
- What must NOT change：LIVE disabled、kill engaged、private trading 未实现；不调用 OKX private、PLACE/CANCEL、transfer/withdraw。
- Acceptance：已识别 RC、exact-head CI 与发布校验；服务器 48–72h OKX public closed bars 持续、决策/自动匹配持续、Trade/Ledger 无重复；故障与重启恢复可见；记录 CPU/内存/DB/错误并归零 V1 阻塞 P0/P1。
- Rollback / Dependencies / Estimated effort：停止 RC 的公开轮询/两个 job，恢复前一已验证应用版本；保留数据库事实与观测证据；依赖 C1/C2/C3，通过后才考虑 `v1.0.0`，准备与连续观察合计 5–7 日（其中 2–3 日不可压缩）。

## 6. Critical path、时间与 V1 DoD

关键路径是 **C1 空库自然闭环与恢复 → C3 同源 UX → C4 服务器 48–72h**；C2 在 dataset/version 身份固定后可与 C1 后半段并行，但须在 C4 的 RC 冻结前完成。按代码已具备的大部分部件估算：best case 约 3–4 周（C1 无额外断点），expected 约 4–5 周，risk case 约 7–8 周（空库身份/行情接入或重启暴露需多个修复候选、RC 观察重置）。这是工作量窗口，不是发布承诺；任一新候选都需重新绑定相应证据。

V1 Definition of Done（全部为未来验收门槛）：

1. 新 PG + 正式 UI/API 的全 Golden Path PASS，无手动 SQL/fixture/隐藏 endpoint。
2. canonical Order/Trade/Ledger authority 唯一；正式页面经济事实一致；无双 writer 或 ID 歧义触达主链。
3. 简单 benchmark、显式 OOS、timing/fee/slippage 与 dataset/version identity 可见且可重算。
4. Stop/Restart/Resume/Recovery、scheduler 自动轮询和撮合 PASS；重复执行无重复 Trade/Ledger。
5. 服务器 48–72h Continuous SIM RC PASS，健康、异常、资源与恢复记录完整。
6. V1 阻塞 P0/P1=0，其他 residual 各有 `DEFER_POST_V1` 处置；CI GREEN，RC 身份明确，获授权后发布 `v1.0.0`。Gate freeze tag 不作为产品版本。

本计划的第一实现任务定义为 **`NQ-V1-C1-FRESH-INSTALL-GOLDEN-PATH-CLOSURE`**，不是单独交付 public capture UI。C1 在本地/隔离 PostgreSQL 完成全链闭环；benchmark/OOS 归 C2，跨页解释和导航归 C3，48–72h server RC 归 C4。若 C1 发现正式 UI/API 无法触发主链必需动作，该断点仍由 C1 修复。本次只交付规划，不启动 C1 实现。计划正式接受后，`next_action=NQ-V1-C1-FRESH-INSTALL-GOLDEN-PATH-CLOSURE-IMPLEMENTATION`；commit/CI 字段必须绑定当次真实身份。保留 `last_frozen_gate=GateZ`，无需创造新 Gate 或修改治理合同。
