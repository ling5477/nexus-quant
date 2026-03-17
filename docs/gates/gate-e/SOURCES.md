# GateE SOURCES
# GateE 权威依据索引

> 本文件用于维护 GateE 的实现依据与追溯关系，避免“明明是当前代码现状，却写得像凭空想象”。

## 1. Current Source of Truth

以下文档是 GateE 当前实施与验收的唯一事实来源：

- `AGENTS.md`
- `README.md`
- `docs/current/README.md`
- `docs/current/GATE_CHECKLIST.md`
- `docs/gates/gate-e/README.md`
- `docs/gates/gate-e/GATE_E_CHECKLIST.md`
- `docs/gates/gate-e/PR_SPLIT_PLAN.md`
- `docs/gates/gate-e/WORK.md`
- `docs/gates/gate-e/DECISIONS.md`
- `docs/gates/gate-e/ARCHITECTURE.md`
- `docs/gates/gate-e/MODULES.md`
- `docs/gates/gate-e/CONTRACTS.md`
- `docs/gates/gate-e/DB_SCHEMA.md`
- `docs/gates/gate-e/STATE_MACHINE.md`
- `docs/gates/gate-e/TEST_CASES.md`
- `docs/gates/gate-e/EVOLUTION_RULES.md`
- `docs/gates/gate-e/GATE_E_CANDIDATES.md`

## 2. Top-Level Navigation

以下文档保留顶层导航摘要角色：

- `docs/README.md`
- `docs/ARCHITECTURE.md`
- `docs/MODULES.md`
- `docs/ROADMAP.md`

## 3. 历史冻结参考

以下内容只作参考，不代表 GateE 当前事实：

- `docs/gates/gate-d/**`
- `docs/gates/gate-c/**`
- `docs/gates/gate-b/**`
- `docs/gates/gate-a/**`
- 根级 `docs/CONTRACTS.md`
- 根级 `docs/DB_SCHEMA.md`
- 根级 `docs/DECISIONS.md`
- 根级 `docs/WORK.md`

## 4. Code and Script Evidence

以下代码与脚本用于核对 GateE 当前起点：

### 4.1 策略 / 运行相关
- `backend/nq-scheduler/src/main/java/.../StrategyScheduler.java`
- `backend/nq-scheduler/src/main/java/.../NoopStrategyScheduler.java`
- `backend/nq-scheduler/src/main/java/.../GateBDemoStrategyRunner.java`

### 4.2 执行链路中的策略血缘字段
- `backend/nq-core/src/main/java/.../PlaceOrderRequest.java`
- `backend/nq-core/src/main/java/.../ExecutionCommandMapper.java`
- `backend/nq-contracts/src/main/java/.../PlaceOrderCommand.java`
- `backend/nq-contracts/src/main/java/.../OrderCreated.java`
- `backend/nq-adapter-api/src/main/java/.../AdapterOrderRequest.java`

### 4.3 查询与持久化基线
- `backend/nq-api/src/main/java/.../CoreTradingQueryFacade.java`
- `backend/nq-core/src/main/java/.../JdbcOrderRepository.java`
- `backend/nq-infra/src/main/resources/db/migration/V1__init.sql`
- `backend/nq-infra/src/main/resources/db/migration/V2__gate_b_schema_hardening.sql`
- `backend/nq-infra/src/main/resources/db/migration/V3__gate_c_adapter_router.sql`
- `backend/nq-infra/src/main/resources/db/migration/V4__gate_c_trade_external_order_id_index.sql`

## 5. 结论

GateE 当前文档不是拍脑袋生出来的，而是基于：
- 现有表结构
- 现有占位接口
- 现有执行闭环血缘字段
- 现有查询面缺口

这套依据已经足够支撑 GateE 开工，不需要再假装自己还在“完全不确定”的迷雾里打转。
