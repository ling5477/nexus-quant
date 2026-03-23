# GateE DECISIONS

记录 GateE 中粒度工程决策。只写已经决定的事，不写“可能以后再说”。

---

## E-001：GateE 主定义固定为策略接入与调度编排

- 日期：2026-03-15
- 状态：已决定
- 决策：GateE 不再承接 GateD 新定义，也不提前承接 GateF 研究能力。

---

## E-002：GateE-0 只是前置治理，不改写主目标

- 日期：2026-03-15
- 状态：已决定
- 决策：
  - Binance background reconcile 噪音治理
  - schema / metadata / contract 收口
  - 返回模型一致性收尾
  统一归入 GateE-0。

---

## E-003：以仓库现状而不是想象设计 GateE

- 日期：2026-03-23
- 状态：已决定
- 决策：GateE 文档必须以现有 `strategy_runs`、`orders.strategy_run_id`、`StrategyScheduler`、`GateBDemoStrategyRunner`、`PlaceOrderRequest` 等真实落点为依据，不另起脱离现状的体系。

---

## E-004：`strategyId` 与 `strategyRunId` 严格分义

- 日期：2026-03-16
- 状态：已决定
- 决策：
  - `strategyId` 表示策略定义身份
  - `strategyRunId` 表示一次运行身份

---

## E-005：当前阶段不引入独立 `strategyInstanceId`

- 日期：2026-03-23
- 状态：已决定
- 决策：在当前仓库还没有“定义层 / 实例层”分离事实之前，不人为新增 `strategyInstanceId`。
- 原因：避免制造空抽象和额外迁移成本。

---

## E-006：当前阶段不单独持久化 `triggerId`

- 日期：2026-03-23
- 状态：已决定
- 决策：触发请求统一使用 `requestId`；只有被接受后才生成 `strategyRunId`。
- 原因：当前仓库无 trigger 表，过早引入会与 `requestId` 重叠。

---

## E-007：`scheduleJobId` 保留为 GateE-2 对象，不提前假落库

- 日期：2026-03-23
- 状态：已决定
- 决策：`scheduleJobId` 是调度配置身份，但只有在正式引入 `strategy_schedules` 后才持久化。

---

## E-008：`strategy_runs` 继续作为最小运行事实表

- 日期：2026-03-16
- 状态：已决定
- 决策：GateE-1 / GateE-2 继续复用现有 `strategy_runs`，按需要增强字段，不新造平行运行表。

---

## E-009：`ExecutionRequest` 在当前仓库中映射为既有下单链

- 日期：2026-03-23
- 状态：已决定
- 决策：GateE 文档中的 `ExecutionRequest` 对应当前仓库的 `PlaceOrderRequest -> PlaceOrderCommand -> AdapterOrderRequest`，不新增第四套重复模型。

---

## E-010：调度默认按 `strategyId + accountId` 串行

- 日期：2026-03-23
- 状态：已决定
- 决策：GateE 默认不允许同一策略在同一账户上并发双跑，除非后续文档明确放开。

---

## E-011：去重键与订单幂等键分离

- 日期：2026-03-23
- 状态：已决定
- 决策：
  - 编排去重使用 `dedupKey`
  - 下单幂等继续使用 `clientOrderId / idempotencyKey`

---

## E-012：`GateBDemoStrategyRunner` 仅保留历史演示角色

- 日期：2026-03-16
- 状态：已决定
- 决策：该类可作为事实依据和最小链路参考，不作为正式 GateE 编排主链。

---

## E-013：scheduler 只负责编排，不接管执行域业务

- 日期：2026-03-16
- 状态：已决定
- 决策：`nq-scheduler` 只能做触发、窗口、去重、串行化、状态推进，不直接改订单、账本、持仓投影。

---

## E-014：GateE-0.1 中阈值内观察和 cooldown 跳过不再写审计事件

- 日期：2026-03-23
- 状态：已决定
- 决策：
  - `connect_failed` 在未达到降级阈值前，只记 `debug`
  - cooldown 内跳过 reconcile，只记 `debug`
- 原因：这两类事件没有新增业务判断价值，但会持续制造 audit/event_store 噪音。

---

## E-015：Binance background reconcile 只保留动作级信号日志

- 日期：2026-03-23
- 状态：已决定
- 决策：
  - 降级触发、完成、失败保留 `info/warn`
  - 高频成功细节如 listenKey refresh、subscribe sent、session check 改为 `debug`
  - 本地主动 close / reconnect 不再打 `warn`
- 原因：后续 GateE 主链开发需要清晰看到“真的出了问题”而不是被正常背景动作淹没。

---

## E-016：Binance `-2013` 在 reconcile 中视为短暂可见性缺口

- 日期：2026-03-23
- 状态：已决定
- 决策：`BinanceRestReconcileService` 遇到 `-2013 order not found` 时按 deferred 处理，不写失败审计。
- 原因：这是 query 时序和远端可见性问题，不应与真实 reconcile 失败混为一类。

---

## E-017：GateE-0.2 以 `exchange_code` 与 `trade_env` 作为数据库 canonical 口径

- 日期：2026-03-23
- 状态：已决定
- 决策：
  - 数据库存储层 canonical 统一为 `exchange_code`
  - 环境维度统一为 `trade_env`
- 兼容策略：
  - `orders.venue`、`trades.exchange` 作为兼容列保留
  - 后续代码分批迁移，不在本批重构 service 主逻辑

---

## E-018：`external_order_id` 进入兼容期，canonical 字段改为 `exchange_order_id`

- 日期：2026-03-23
- 状态：已决定
- 决策：
  - `orders`、`trades` 新增 `exchange_order_id`
  - `external_order_id` 保留为兼容列
  - 通过数据库 trigger 双向补齐

---

## E-019：`strategy_runs` 保留原表并重命名主键列，不新建平行运行表

- 日期：2026-03-23
- 状态：已决定
- 决策：
  - `run_id -> strategy_run_id`
  - `ended_at -> finished_at`
  - 保留 `strategy_runs` 作为唯一运行事实表

---

## E-020：GateE-0.2 新增 `strategy_definitions` 与 `strategy_schedules`

- 日期：2026-03-23
- 状态：已决定
- 决策：
  - GateE-1.1 使用 `strategy_definitions`
  - GateE-2.1 使用 `strategy_schedules`
  - 本批不引入 `strategy_instances`、`strategy_triggers`

---

## E-021：整库实际表数按 migration 扫描为 16 张，不是 15 张

- 日期：2026-03-23
- 状态：已决定
- 决策：整库注释回补按 migration 实际表清单执行，当前实际表数为 16 张。
- 原因：以仓库事实为准，不按口头数量假设省略表。

---

## E-022：GateE-0.2-comment-fix 通过 V6 回补剩余基础表注释

- 日期：2026-03-23
- 状态：已决定
- 决策：
  - `strategy_definitions`、`strategy_schedules`、`strategy_runs`、`orders`、`trades` 保持原有注释
  - 通过 `V6__schema_comments_backfill.sql` 回补剩余基础表注释
  - 本批只补 `COMMENT ON TABLE / COMMENT ON COLUMN`，不改字段、索引、trigger、业务逻辑
- 原因：GateE-0.2 需要做到整库 metadata 可读可追踪，不能只完成 5 张核心表就提前归档。

---

## E-023：GateE-0.3 以 adapter canonical 字段替代返回层旧口径

- 日期：2026-03-23
- 状态：已决定
- 决策：
  - adapter 返回层 canonical 字段统一为 `exchange_code / exchange_order_id / client_order_id / exchange_trade_id / account_id / trade_env`
  - `venue` / `external_order_id` 仅保留兼容访问器，不允许继续扩散为新主输出语义

---

## E-024：GateE-0.3 统一 adapter 结果分类

- 日期：2026-03-23
- 状态：已决定
- 决策：
  - 统一结果分类为 `SUCCESS / ACCEPTED / NOT_FOUND / DEFERRED / RETRYABLE_FAILURE / FATAL_FAILURE / THROTTLED / AUTH_FAILURE / REMOTE_UNAVAILABLE`
  - reconcile / recovery / query-confirm 使用同一套分类解释

---

## E-025：Binance / OKX fills 统一映射到 `AdapterTradeReport`

- 日期：2026-03-23
- 状态：已决定
- 决策：
  - adapter 内部可保留交易所私有 fill DTO
  - 对 scheduler/reconcile 暴露统一 `AdapterTradeReport`
- 原因：避免下游继续按交易所私货写双套解释逻辑。

---

## E-026：GateE-1.1 将“策略注册”固定为定义级管理，不提前进入 trigger

- 日期：2026-03-23
- 状态：已决定
- 决策：
  - GateE-1.1 只提供 create / list / detail / enable / disable
  - 启停粒度固定为策略定义级
  - 不提前实现手动 trigger、strategyRun、schedule job

---

## E-027：GateE-1.1 继续不引入 `strategyInstanceId`

- 日期：2026-03-23
- 状态：已决定
- 决策：当前“注册项”的唯一识别口径继续以 `strategy_id / strategy_code` 为核心，不扩展到实例层。

---

## E-028：GateE-1.2 手动 trigger 只做 definition -> run -> order 最小血缘

- 日期：2026-03-23
- 状态：已决定
- 决策：
  - 以已启用的 `strategy_definitions` 作为 trigger 起点
  - 每次手动 trigger 生成一个新的 `strategy_run_id`
  - 通过现有 `PlaceOrderRequest -> OrderCommandService` 进入执行链
  - `orders.strategy_run_id` 必须绑定本次运行

---

## E-029：GateE-1.2 不做 schedule / window / dedup / serialization

- 日期：2026-03-23
- 状态：已决定
- 决策：GateE-1.2 只做手动 trigger，不提前做 GateE-2 的调度编排能力。

---

## E-030：`PlaceOrderCommand.strategyId` 在 GateE-1.2 继续按运行级兼容桥接

- 日期：2026-03-23
- 状态：已决定
- 决策：
  - 现阶段仍不重写 contracts 主结构
  - 继续由 `ExecutionCommandMapper` 把 `strategyRunId` 传入该兼容位
  - 不允许新增把 `strategyId` 当运行 ID 的新调用点
