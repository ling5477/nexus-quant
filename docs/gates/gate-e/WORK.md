# GateE WORK
# GateE 工作台账

---

## 1. 2026-03-15：GateE 文档启动批

- 目标：
  - 建立 GateE 最小骨架
  - 完成阶段切换
- 结果：
  - GateD 冻结
  - GateE 入口建立

---

## 2. 2026-03-16：GateE 文档完善批

- 目标：
  - 补齐 CONTRACTS / DB_SCHEMA / STATE_MACHINE / TEST_CASES / SOURCES
- 已确认事实：
  - `strategy_runs` 已存在
  - `orders.strategy_run_id` 已存在
  - `StrategyScheduler / NoopStrategyScheduler / GateBDemoStrategyRunner` 已存在
- 当时结论：
  - GateE 需要先把语义收口

---

## 3. 2026-03-23：GateE 开工基线收口

- 本批归属：`GateE-DOC-2`
- 本批目标：
  - 基于仓库真实代码与 schema，完成 GateE 可开工基线
  - 把 current 入口与 GateE 卷宗口径对齐
  - 写死 `strategyId / strategyRunId / requestId / dedupKey / scheduleJobId` 语义
  - 固定 GateE-0、GateE-1、GateE-2 的执行顺序

- 本批实际核对的仓库事实：
  - `backend/nq-infra/.../V1__init.sql` 已有 `strategy_runs`
  - `backend/nq-infra/.../V2__gate_b_schema_hardening.sql` 已有 `idx_orders_strategy_run_id`
  - `backend/nq-core/.../PlaceOrderRequest.java` 使用 `strategyRunId`
  - `backend/nq-adapter-api/.../AdapterOrderRequest.java` 使用 `strategyRunId`
  - `backend/nq-contracts/.../PlaceOrderCommand.java` 仍使用 `strategyId`，属于兼容债务
  - `backend/nq-scheduler/.../StrategyScheduler.java` 仅有 `start/stop/restart`
  - `backend/nq-scheduler/.../GateBDemoStrategyRunner.java` 是历史 demo runner
  - 当前不存在正式策略定义表、调度作业表、策略查询 API

- 本批输出结论：
  - GateE 当前不是“代码已开工”，而是“文档基线可直接指导开工”
  - GateE-0 只做清场治理
  - GateE-1 先做定义与手动 trigger
  - GateE-2 再做 schedule、窗口、去重、串行化、结果查询

- 本批后续待办顺序：
  1. GateE-0.1 Binance background reconcile 噪音治理
  2. GateE-0.2 schema / metadata / contract 收口
  3. GateE-0.3 adapter 返回模型一致性
  4. GateE-1.1 策略定义与注册模型
  5. GateE-1.2 策略运行主链与手动 trigger
  6. GateE-2.1 调度任务与计划配置
  7. GateE-2.2 窗口 / 去重 / 串行化
  8. GateE-2.3 运行结果回传与查询面

- 风险记录：
  - `PlaceOrderCommand.strategyId` 兼容债务如果不先收口，后续实现会继续混淆定义 ID 与运行 ID
  - 当前没有正式 trigger 持久化对象，GateE-2 需要明确是否继续仅依赖 `requestId`

---

## 4. 2026-03-23：GateE-0.1 Binance background reconcile 噪音治理

- 本批归属：`GateE-0.1`
- 本批目标：
  - 识别 Binance background reconcile 链路中的高频噪音
  - 区分“真实故障信号”和“正常退化 / 重复观察”
  - 在不改写 GateE 主边界的前提下完成最小治理

- 本批实际核对的链路：
  - `BinanceWsClient` 负责连接、listenKey 续期、心跳与重连日志
  - `BinanceWsDegradeReconcileCoordinator` 负责在断线 / listenKey 失效时触发 REST reconcile
  - `BinanceRestReconcileService` 负责扫描 BINANCE 非终态订单、查单、拉 fills、写 trades / ledger
  - `BinanceRecoveryService` 会复用 `BinanceRestReconcileService`
  - `BinanceSynchronizedTimestampProvider` 负责签名时间同步，`-1021` 相关噪音会从这里放大

- 本批确认的噪音来源：
  - `BinanceWsClient` 中 `listenkey_refresh_success`、`subscribe_sent`、`session_subscriptions_checked`、`disconnect_suppressed` 等高频 `info`
  - `BinanceWsClient` 本地主动 close / reconnect 也打 `warn`，把正常退化路径误报成异常
  - `BinanceWsDegradeReconcileCoordinator` 对“阈值内 connect_failed 观察”和“cooldown 内跳过”持续写审计，形成重复噪音
  - `BinanceRestReconcileService` 对已存在成交的重复命中持续写 `BINANCE_FILL_DEDUP_HIT` 审计
  - `BinanceRestReconcileService` 对 Binance `-2013 order not found` 这类短暂可见性问题缺少显式分类，容易被当成失败
  - `BinanceSynchronizedTimestampProvider` 每次校时成功打 `info`，校时失败可按固定周期持续 `warn`

- 本批治理结果：
  - 将 WS 高噪音成功日志下调为 `debug`
  - 将本地主动 close / reconnect 的 close 日志从 `warn` 改为受控 `info`
  - 将 `connect_failed` 阈值内观察与 cooldown 内跳过改为 `debug`，不再写审计事件
  - 将重复成交命中视为正常幂等现象，不再写 `BINANCE_FILL_DEDUP_HIT`
  - 将 Binance `-2013` 识别为远端短暂不可见，按 deferred 处理，不再写失败审计
  - 保留真正有价值的信号：降级触发、降级完成、降级失败、逐单 reconcile 失败、ledger post 失败

- 本批验证：
  - `BinanceRestReconcileServiceTest`
  - `BinanceWsDegradeReconcileCoordinatorTest`
  - `BinanceRecoveryServiceTest`
  - 命令：`mvn --% -q -f backend/pom.xml -pl nq-scheduler -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=BinanceRestReconcileServiceTest,BinanceWsDegradeReconcileCoordinatorTest,BinanceRecoveryServiceTest test`

- 当前结论：
  - GateE-0.1 已完成最小落地治理
  - GateE-0.2 尚未开始
  - 当前剩余前置治理项仅包括 `schema / metadata / contract 收口` 与 `adapter 返回模型一致性`

---

## 5. 2026-03-23：GateE-0.2 schema / metadata / contract 收口

- 本批归属：`GateE-0.2`
- 本批目标：
  - 新增 GateE 最小定义层与调度层表
  - 收口 `strategy_runs`、`orders`、`trades` 的身份与维度字段
  - 用数据库注释把字段语义一次写死

- 本批实际核对的仓库事实：
  - migration 基线为 `V1 -> V4`
  - `strategy_runs` 已存在，但主键列仍叫 `run_id`
  - `orders.strategy_run_id` 已存在并被 `JdbcOrderRepository` 使用
  - `orders` 当前使用 `venue` / `external_order_id`
  - `trades` 当前使用 `exchange` / `external_order_id`
  - Java DTO 仍普遍使用 `venue`
  - `PlaceOrderCommand.strategyId` 仍是兼容债务

- 本批新增 / 收口结果：
  - 新增 `strategy_definitions`
  - 新增 `strategy_schedules`
  - `strategy_runs` 收口为 `strategy_run_id / finished_at / trigger_type / exchange_code / trade_env / request_id`
  - `orders` 收口为 `request_id / dedup_key / exchange_code / trade_env / exchange_order_id`
  - `trades` 收口为 `strategy_run_id / exchange_code / trade_env / exchange_order_id`
  - 通过 trigger 保留 `venue` / `exchange` / `external_order_id` 兼容写法
  - 对相关表和字段补齐 `COMMENT ON TABLE / COMMENT ON COLUMN`

- 本批明确不做：
  - 不进入 adapter 返回模型统一
  - 不实现策略注册 API
  - 不进入手动 trigger / schedule 主逻辑
  - 不新增 `strategy_instances` / `strategy_triggers`

- 本批后续待办顺序：
  1. GateE-0.3 adapter 返回模型一致性
  2. GateE-1.1 策略定义与注册模型
  3. GateE-1.2 策略运行主链与手动 trigger
  4. GateE-2.1 调度任务与计划配置

- 风险记录：
  - Java 代码当前仍广泛使用 `venue`，后续需要逐步迁移到 `exchange_code`
  - Java 代码当前仍广泛使用 `external_order_id`，后续需要逐步迁移到 `exchange_order_id`
  - `PlaceOrderCommand.strategyId` 的兼容债务仍需在 GateE-1 收口

---

## 6. 2026-03-23：GateE-0.2-comment-fix 注释复核

- 本批归属：`GateE-0.2-comment-fix`
- 本批目标：
  - 复核 `strategy_definitions`、`strategy_schedules`、`strategy_runs`、`orders`、`trades` 的表注释与字段注释
  - 若有缺失则补一条纯注释 migration

- 复核结果：
  - 5 张目标表全部存在 `COMMENT ON TABLE`
  - 本批要求检查的关键字段全部存在 `COMMENT ON COLUMN`
  - 不存在实际缺失项
  - 因此不新增 `V6` migration

- 本批结论：
  - GateE-0.2 的注释范围已经闭合
  - 本次仅完成复核与台账回填，不包含业务逻辑改动

---

## 7. 2026-03-23：GateE-0.2-full-schema-comment-backfill 整库注释回补

- 本批归属：`GateE-0.2-full-schema-comment-backfill`
- 本批目标：
  - 按 migration 实际扫描整库表清单
  - 为 GateE-0.2 之外仍缺注释的基础表补齐 `COMMENT ON TABLE / COMMENT ON COLUMN`

- 本批实际核对结果：
  - 当前 migration 实际表清单为 16 张，不是 15 张
  - 已完成注释收口的核心 5 张表：`strategy_definitions`、`strategy_schedules`、`strategy_runs`、`orders`、`trades`
  - 剩余需要回补注释的基础表为 11 张：`users`、`roles`、`user_roles`、`accounts`、`positions`、`account_snapshots`、`ledger_entries`、`ledger_events`、`risk_events`、`event_store`、`audit_logs`

- 本批输出结果：
  - 新增 `V6__schema_comments_backfill.sql`
  - 为剩余 11 张表补齐 `COMMENT ON TABLE`
  - 为这些表的关键字段补齐 `COMMENT ON COLUMN`
  - 未改动任何字段、索引、trigger、service、scheduler、controller

- 本批结论：
  - GateE-0.2 的整库注释收口已闭合
  - 下一步仍然是 GateE-0.3 adapter 返回模型一致性

---

## 8. 2026-03-23：GateE-0.3 adapter 返回模型一致性

- 本批归属：`GateE-0.3`
- 本批目标：
  - 统一 adapter 返回层 canonical 字段
  - 统一 adapter 结果分类与异常分类
  - 让 reconcile / recovery / query-confirm 共享同一套解释

- 本批实际核对的现状：
  - `AdapterOrderAck / AdapterCancelAck / AdapterOrderSnapshot / AdapterTradeReport / AccountSnapshot / PositionSnapshot` 仍以 `venue / externalOrderId` 为主口径
  - `AdapterError` 只有 `code / message / retryable`
  - Binance 侧主要按 `BinanceApiException.errorCode()` 做隐式分流
  - OKX 侧主要按 `OkxApiException.errorKind()` 做隐式分流
  - reconcile / recovery / query-confirm 之前各自解释 not_found / deferred / timeout

- 本批收口结果：
  - adapter 返回层新增 `AdapterResultCategory`
  - `AdapterError` 增加 `category`
  - `AdapterOrderAck / AdapterCancelAck / AdapterOrderSnapshot / AdapterTradeReport` 改为 canonical 字段输出
  - 保留 `venue()` / `externalOrderId()` 兼容访问器，避免扩大战线
  - Binance / OKX 异常统一映射到 canonical 分类
  - Binance / OKX fills 统一映射到 `AdapterTradeReport`
  - `OrderCommandService` 对 `DEFERRED / RETRYABLE_FAILURE / THROTTLED / REMOTE_UNAVAILABLE` 不再直接按 reject 处理
  - `BinanceRestReconcileService / OkxRestReconcileService / OkxRecoveryService` 改为消费统一分类

- 本批明确不做：
  - 不改 schema
  - 不改 strategy 主链
  - 不改 scheduler 主逻辑
  - 不进入 GateE-1 / GateE-2

- 本批验证：
  - `BinanceErrorClassifierTest`
  - `OkxErrorClassifierTest`
  - `BinanceExchangeAdapterTest`
  - `OrderCommandServiceTest`
  - `BinanceRestReconcileServiceTest`
  - `OkxRestReconcileServiceTest`
  - `OkxRecoveryServiceTest`

- 本批后续待办：
  - GateE-1.1 策略定义 / 注册 / 启停最小模型
  - `PlaceOrderCommand.strategyId` 兼容债务仍待后续迁移
