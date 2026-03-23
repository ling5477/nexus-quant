# GateE SOURCES
# GateE 依据索引

本文档只记录本轮 GateE 结论实际依赖的仓库事实，避免文档看起来像凭空设计。

---

## 1. 当前入口文档

以下内容是当前阶段实施的主入口：

- `AGENTS.md`
- `README.md`
- `docs/README.md`
- `docs/current/README.md`
- `docs/current/GATE_CHECKLIST.md`
- `docs/current/WORK_TEMPLATE.md`

---

## 2. GateE 卷宗

本轮直接维护的 GateE 文档：

- `docs/gates/gate-e/README.md`
- `docs/gates/gate-e/GATE_E_CHECKLIST.md`
- `docs/gates/gate-e/ARCHITECTURE.md`
- `docs/gates/gate-e/MODULES.md`
- `docs/gates/gate-e/CONTRACTS.md`
- `docs/gates/gate-e/DB_SCHEMA.md`
- `docs/gates/gate-e/STATE_MACHINE.md`
- `docs/gates/gate-e/TEST_CASES.md`
- `docs/gates/gate-e/PR_SPLIT_PLAN.md`
- `docs/gates/gate-e/DECISIONS.md`
- `docs/gates/gate-e/WORK.md`
- `docs/gates/gate-e/EVOLUTION_RULES.md`

---

## 3. GateD 冻结参考

本轮用于确认边界的 GateD 文档：

- `docs/gates/gate-d/README.md`
- `docs/gates/gate-d/STATE_MACHINE.md`

用途：

- 确认 GateD 已冻结
- 确认订单状态机不由 GateE 改写

---

## 4. 代码与 schema 证据

### 4.1 调度与策略相关现有落点

- `backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/StrategyScheduler.java`
- `backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/NoopStrategyScheduler.java`
- `backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/GateBDemoStrategyRunner.java`
- `backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/config/SchedulerConfiguration.java`

结论：

- 当前有调度占位点
- 当前没有正式 GateE 调度主链

### 4.2 执行链中的运行血缘

- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/core/service/PlaceOrderRequest.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/core/service/ExecutionCommandMapper.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/core/service/OrderCommandService.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/core/repository/JdbcOrderRepository.java`
- `backend/nq-contracts/src/main/java/com/guidinglight/nexusquant/contracts/command/PlaceOrderCommand.java`
- `backend/nq-contracts/src/main/java/com/guidinglight/nexusquant/contracts/event/OrderCreated.java`
- `backend/nq-adapter-api/src/main/java/com/guidinglight/nexusquant/adapter/api/model/AdapterOrderRequest.java`
- `backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/web/GateDOrderHttpRequest.java`

结论：

- `strategyRunId` 已进入 core / adapter / event / http request 多层
- `PlaceOrderCommand.strategyId` 是当前主要兼容债务

### 4.3 数据库与 migration

- `backend/nq-infra/src/main/resources/db/migration/V1__init.sql`
- `backend/nq-infra/src/main/resources/db/migration/V2__gate_b_schema_hardening.sql`
- `backend/nq-infra/src/main/resources/db/migration/V3__gate_c_adapter_router.sql`
- `backend/nq-infra/src/main/resources/db/migration/V4__gate_c_trade_external_order_id_index.sql`

结论：

- 现有基线为 `V1 -> V4`
- `strategy_runs` 与 `orders.strategy_run_id` 已存在
- 当前还没有 GateE 专属 migration

### 4.4 查询与测试现状

- `backend/nq-api/src/test/java/com/guidinglight/nexusquant/api/service/CoreTradingQueryFacadeTest.java`
- `backend/nq-core/src/test/java/com/guidinglight/nexusquant/core/service/OrderCommandServiceTest.java`
- `backend/nq-scheduler/src/test/java/com/guidinglight/nexusquant/scheduler/service/*.java`

结论：

- 现有测试集中在执行、恢复、对账、账本
- 当前没有策略注册 / 调度主链 / 运行结果读侧测试

---

## 5. 搜索结论

本轮通过工程检索确认：

- `strategy_runs` 已落库
- `orders.strategy_run_id` 已落库并被 repository 使用
- `StrategyScheduler` 仅为占位接口
- `NoopStrategyScheduler` 仅为无副作用实现
- `GateBDemoStrategyRunner` 仅是历史 demo runner
- 仓库中已存在正式的 `strategy_definitions`、`strategy_schedules`
- 仓库中仍不存在 `strategyInstanceId` 实现落点

---

## 6. 文档使用结论

本轮 GateE 文档可以直接指导后续实施，但后续任何新增中粒度决策都必须继续回填：

- `docs/gates/gate-e/DECISIONS.md`
- `docs/gates/gate-e/WORK.md`
- 必要时同步 `docs/current/*`
