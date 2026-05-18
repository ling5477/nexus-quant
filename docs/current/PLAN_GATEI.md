# GateI Plan

## GateI 背景

GateH 已完成交易工作台、OKX / Binance SPOT 历史 OHLCV K 线接入、marketdata dataset 管理、backtest config dataset 绑定和 backtest run dataset snapshot。NexusQuant 当前已经具备交易、行情、dataset、回测基础链路，但尚未形成虚拟币量化 V1 的完整产品闭环。

GateI 的定位是虚拟币量化 V1 完整闭环阶段。它承接 GateH 的真实历史行情和 dataset 能力，把策略版本、回测配置、评估报告、策略发布、SIM / Paper 运行、风控回写、资金曲线、持仓曲线、交易复盘和异常停机串成可验证的主链。

## GateI 目标

- 策略版本可管理，并能作为回测和发布的稳定输入。
- 策略发布版本可进入 SIM / Paper Trading。
- 回测配置可绑定 dataset、策略版本和参数快照。
- 评估报告具备收益率、最大回撤、胜率、盈亏比、交易次数等核心指标。
- SIM / Paper 运行结果可回写。
- 风控检查结果可回写并可追溯。
- 资金曲线、持仓曲线可查询。
- 单次交易可复盘。
- 异常停机机制具备最小闭环。
- E2E 覆盖虚拟币量化 V1 主链。

## GateI 不做范围

- AI 接入。
- AI 信号。
- AI 自动交易。
- 多 Agent 决策。
- 美股适配。
- A 股适配。
- 合约全量。
- 高频交易。
- 复杂因子平台。
- 新闻资讯系统。
- 链上数据系统。

## GateI 拆分

### GateI-1：策略版本与发布链路正式化

输入：

- 当前 strategy、research、backtest、evaluation、publish 基础链路。
- GateH-3 的 dataset binding 和 run snapshot。
- 当前 SIM / LIVE 环境口径。

输出：

- `strategy_versions` 规划落地为可实现的版本管理主链。
- publish version 或 publish record 能引用策略版本、参数快照和评估结果。
- 策略发布版本进入 SIM / Paper 前有明确冻结状态和审计信息。

验收标准：

- 策略版本可创建、查询、冻结、归档。
- 发布版本能指向确定的策略版本和参数快照。
- 不允许发布未冻结或不可追溯的策略版本。
- `strategy-version-smoke`、`publish-version-smoke` 后续实现后通过。

### GateI-2：回测配置、评估指标、结果追溯增强

输入：

- GateH-3 的 dataset 与 backtest config 绑定。
- 当前 backtest run、evaluation、publish 最小链路。
- GateI-1 的策略版本和发布版本。

输出：

- 回测配置绑定 dataset、策略版本、参数快照。
- 回测运行结果记录配置快照、dataset snapshot、策略版本快照。
- 评估报告输出核心指标并可追溯到输入数据和策略版本。

验收标准：

- 回测配置可查看 dataset、strategy version、parameter snapshot。
- 回测 run 记录完整输入快照。
- 评估报告包含收益率、最大回撤、胜率、盈亏比、交易次数等核心指标。
- `backtest-config-enhanced-smoke`、`evaluation-report-smoke` 后续实现后通过。

### GateI-3：SIM / Paper Trading 运行闭环

输入：

- GateI-1 的发布版本。
- GateI-2 的回测和评估结果。
- 当前 trading、risk、ledger、scheduler 基线。

输出：

- Paper Trading run 可创建、启动、暂停、停止、查询。
- Paper 订单和成交结果可回写。
- run 过程引用发布版本、账户上下文、SIM / Paper 环境和风控策略。

验收标准：

- 发布版本可进入 Paper Trading run。
- run 状态流转可追踪。
- Paper 订单、成交、持仓和资金变化可查询。
- `paper-trading-run-smoke` 后续实现后通过。

### GateI-4：风控回写、资金曲线、持仓曲线、复盘与异常停机

输入：

- GateI-3 的 Paper Trading run、订单、成交和持仓事实。
- 当前 risk、ledger、observability 基线。

输出：

- 风控检查结果可回写到 run/order 维度。
- 资金曲线和持仓曲线可查询。
- 单次交易复盘可串联策略信号、风控结果、订单、成交、持仓和资金变化。
- 异常停机事件可记录、触发、解除和审计。

验收标准：

- 风控结果可按 run/order 查询。
- 资金曲线、持仓曲线可按 run 和时间范围查询。
- trade replay 能追溯单笔交易链路。
- emergency stop 最小闭环可验证。
- `risk-result-smoke`、`equity-curve-smoke`、`position-curve-smoke`、`trade-replay-smoke`、`emergency-stop-smoke` 后续实现后通过。

## API 规划入口

GateI API 只在本轮规划，不实现 controller。正式规划入口为 [GATEI_API_PLAN.md](./GATEI_API_PLAN.md)。

GateI API 分类：

- Strategy Version API。
- Publish Version API。
- Backtest Config Enhanced API。
- Evaluation Report API。
- Paper Trading Run API。
- Risk Result API。
- Equity Curve API。
- Position Curve API。
- Trade Replay API。
- Emergency Stop API。

## DB 规划入口

GateI DB 只在本轮规划，不新增 migration。正式规划入口为 [GATEI_DB_PLAN.md](./GATEI_DB_PLAN.md)。

GateI DB 重点：

- `strategy_versions`。
- `strategy_publish_versions` 或 `publish_records` 增强。
- `backtest_configs`、`backtest_runs`、`backtest_eval_reports` 增强。
- `paper_trading_runs`、`paper_trading_orders`、`paper_trading_trades`。
- `risk_check_results`。
- `equity_curve_snapshots`、`position_curve_snapshots`。
- `trade_replay_records`。
- `emergency_stop_events`。

## 前端规划入口

GateI 前端只在本轮规划，不实现页面。正式规划入口为 [GATEI_FRONTEND_PLAN.md](./GATEI_FRONTEND_PLAN.md)。

规划页面：

- `/strategies` 版本管理增强。
- `/publishes` 发布版本管理增强。
- `/backtests` 配置与结果增强。
- `/evaluations` 评估报告增强。
- `/paper-trading` 或 `/runs` Paper Trading 运行入口。
- `/risk` 风控结果。
- `/portfolio/equity-curve` 资金曲线。
- `/portfolio/position-curve` 持仓曲线。
- `/replay` 交易复盘。
- `/emergency-stop` 异常停机。

## 测试规划入口

GateI 测试只在本轮规划。正式规划入口为 [GATEI_TEST_PLAN.md](./GATEI_TEST_PLAN.md)。

当前保留基线：

- 后端 `mvn -f backend/pom.xml test` 已通过。
- 前端 `npm run build` 已通过。
- E2E `npm run test:e2e` 已通过，结果为 10 passed / 4 skipped。
- Python `pytest`、`mypy`、`ruff` 已通过。

## 风险与回滚策略

风险：

- 策略版本、发布版本、回测配置和 Paper run 若缺少快照，会导致结果不可复现。
- Paper Trading 如果复用真实交易状态机，需要明确 SIM / LIVE 隔离，避免误用实盘链路。
- 评估指标若口径不固定，会导致报告之间不可比。
- 风控回写与异常停机涉及状态流转，需要保证幂等和可审计。
- 资金曲线和持仓曲线如果过度依赖实时计算，可能影响查询性能和回放稳定性。

回滚策略：

- GateI 每个子 Gate 使用独立 work order 和独立提交。
- DB migration 后续必须增量新增，避免破坏 GateH 已有 dataset/backtest 绑定。
- 新 API 默认新增或兼容扩展，不替换 GateH 已验证入口。
- Paper Trading 入口默认限定 SIM / Paper，不接 LIVE 自动交易。
- 异常停机机制先只作用于 Paper run，再评估是否扩展到后续 Gate。

## GateI 完成后进入 GateJ 的条件

- GateI-1 / GateI-2 / GateI-3 / GateI-4 全部通过验收。
- 策略版本、发布版本、回测配置、评估报告、Paper run 形成完整主链。
- Paper Trading 运行结果、风控结果、资金曲线、持仓曲线和复盘记录可追溯。
- 异常停机机制具备最小闭环。
- GateI E2E 主链通过。
- 文档、API、DB、前端、测试结果完成冻结归档。
- AI 仍未进入交易主链；GateJ 只做 Paper Trading 稳定运行，不做 AI 接入。
