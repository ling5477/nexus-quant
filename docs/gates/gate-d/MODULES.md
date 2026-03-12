# GateD MODULES
# GateD 模块改造说明

## 1. nq-core（第一优先级）

### 当前状态
- 已有 `OrderCommandService`
- 已有 `AdapterRouter`
- 已有状态机与订单仓储端口

### GateD 改造目标
- 收敛成统一执行域应用服务
- 统一 place / cancel / acknowledge / reject / trade-report / query-confirm 入口
- 统一状态推进入口

### 必改项
- 保留 `OrderCommandService`，但职责必须收敛
- 新增或拆出：
  - `ExecutionApplicationService`
  - `OrderLifecycleService`
  - `ExecutionTraceService`
  - `ExternalOrderLinkService`
- scheduler / controller 不再各自推进状态

---

## 2. nq-risk（第二优先级）

### 当前状态
- `RiskGate`
- `NoopRiskGate`
- `KillSwitchService`

### GateD 改造目标
- 形成 pre-trade 硬风控规则链

### 必改项
- 新增：
  - `PreTradeRiskService`
  - `RiskRule`
  - `RiskRuleRegistry`
  - `SymbolEnabledRule`
  - `OrderPrecisionRule`
  - `MinNotionalRule`
  - `MaxOrderAmountRule`
  - `DuplicateRequestRule`
  - `RateLimitRule`
  - `AccountTradingEnabledRule`
- 返回标准化 `RiskCheckResult`
- 风控结果写审计与事件

---

## 3. nq-scheduler（第三优先级）

### 当前状态
- reconcile
- recovery
- ws 协调
- paper matching 相关能力分散在此

### GateD 改造目标
- 瘦身为 job 触发与窗口协调模块

### 必改项
- 保留：job 触发、定时扫描、恢复编排
- 移除或收敛：核心状态推进、重复业务决策
- paper executor 不继续作为 scheduler 的主要业务核心

---

## 4. nq-adapter-api（第四优先级）

### 当前状态
- 已有统一 adapter 抽象与模型

### GateD 改造目标
- 冻结执行域契约
- 明确 place / cancel / query / fills / snapshots 边界

### 必改项
- 完善统一模型最小字段
- 明确 status 映射规范
- 明确 raw payload 保留策略

---

## 5. nq-adapter-okx（第五优先级）

### 当前状态
- 接入成熟度较高

### GateD 改造目标
- 作为 GateD 主验证通道之一

### 必改项
- account / position snapshot 归一补齐
- order query 与 trade report 映射补齐
- query-confirm 行为文档化

---

## 6. nq-ledger（第六优先级）

### 当前状态
- 已有 trade posting 基础

### GateD 改造目标
- 与执行闭环正式咬合

### 必改项
- 明确 fills -> ledger -> position -> account 链路
- 幂等键与重复回报处理规范化
- 失败路径可见化

---

## 7. nq-app（第七优先级）

### 当前状态
- 仍带有 GateC 验收入口痕迹

### GateD 改造目标
- 提供 GateD 验收入口与运行 profile 门禁

### 必改项
- GateC 专用入口退场或转历史用途
- 新增 GateD 验收入口
- local only + feature gate 双门禁

---

## 8. nq-infra / nq-observability / nq-api

### nq-infra
- 新增 GateD Flyway 迁移
- 补索引、唯一键、版本字段

### nq-observability
- execution trace 规范
- metrics 补齐

### nq-api
- 提供 order / trade / position / account 的统一读视图

---

## 9. 暂不作为主改造对象
- nq-auth
- nq-security
- nq-gateway
- frontend
- research

