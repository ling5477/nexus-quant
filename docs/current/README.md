# docs/current/README.md
# Current Gate（当前阶段入口）

当前阶段：**GateD（统一执行闭环与执行域硬化）**。

本目录是当前 Gate 的唯一入口。切换 Gate 时，只更新本目录内容；历史 Gate 文档固定在 `docs/gates/` 下冻结保存。

---

## 1. 当前阶段目标

GateD 目标：在 GateC 已完成的多交易所适配、REST / WS 基座、reconcile / recovery、ledger posting 能力之上，收敛出一条**稳定统一的执行闭环**。

GateD 不是研究平台阶段，也不是再堆一个新交易所阶段。它做的是“把已经能跑的东西，收紧成能冻结、能审计、能补偿、能验收的执行域”。

核心目标：
- 统一执行入口：place / cancel / query / reconcile / recovery
- pre-trade 风控硬化：规则链、拒绝码、错误消息、幂等拦截
- 订单状态机收敛：本地状态与外部事实状态分层
- Paper / OKX / Binance 的统一执行抽象
- trade -> ledger -> position -> account 的投影联动
- WS 加速 + REST 兜底的补偿收敛
- GateD 验收入口、测试用例、文档与 ADR 冻结

---

## 2. GateD 的边界

### 2.1 GateD 包含
- 统一执行编排
- 统一 adapter 契约收敛
- 风控硬规则
- 订单状态机硬化
- 订单 / 成交 / 账本 / 持仓 / 账户快照联动
- reconcile / recovery / degrade / query-confirm
- Paper 与真实交易所的统一执行接口
- trace / audit / event_store / metrics 规范

### 2.2 GateD 不包含
- 回测系统
- 因子研究
- Alpha 研究平台
- 前端控制台扩建
- Kafka / Debezium / K8s / Grafana 等生产大基建
- 合约、杠杆、期货、期权执行域

---

## 3. 代码阶段对齐结论

根据当前仓库结构，代码已经具备以下 GateD 前置条件：

- `nq-adapter-api` 已有统一 adapter 抽象与归一模型
- `nq-core` 已有 `OrderCommandService`、`AdapterRouter`、状态机与订单仓储端口
- `nq-adapter-okx` / `nq-adapter-binance` 已具备真实 venue 接入能力
- `nq-scheduler` 已有 reconcile、recovery、WS 协调与 paper matching 能力
- `nq-ledger` 已有 trade posting 与投影链路基础
- `nq-app` 已有 GateC 验收入口与运行承载能力
- Flyway 已经演进到可继续承接 GateD 的版本节点

结论：**当前仓库不是“准备开始设计执行域”，而是“必须把执行域正式立卷并收敛边界”的阶段。**

---

## 4. GateD 主改模块

GateD 修改优先级：

1. `nq-core`
2. `nq-risk`
3. `nq-scheduler`
4. `nq-adapter-api`
5. `nq-adapter-okx`
6. `nq-ledger`
7. `nq-app`
8. `nq-infra`
9. `nq-observability`
10. `nq-adapter-binance`
11. `nq-api`

不作为 GateD 主改造对象：
- `nq-auth`
- `nq-security`
- `nq-gateway`
- `frontend`
- `research`

---

## 5. GateD 执行工作流（给 Codex / 开发者）

### 第一步：先读
按顺序读取：
1. `AGENTS.md`
2. `README.md`
3. `docs/current/README.md`
4. `docs/current/GATE_CHECKLIST.md`
5. 对应 GateD 文档
6. 再读目标代码文件

### 第二步：先改文档
- 先更新 `docs/current/*`
- 再更新 `docs/gates/gate-d/*`
- 最后再动代码

### 第三步：模块内最小修改
- 只围绕当前 checklist 条目做最小修改集
- 禁止顺手扩散到 research / frontend / infra 大基建

### 第四步：验证
必须给出：
- 修改文件清单
- 对应 checklist 条目
- 验证命令
- 未完成项 / 风险项

### 第五步：回填记录
更新：
- `docs/gates/gate-d/WORK.md`
- 必要时补 ADR

---

## 6. 当前 Gate 文档入口

- 总览：`docs/gates/gate-d/README.md`
- 架构：`docs/gates/gate-d/ARCHITECTURE.md`
- 契约：`docs/gates/gate-d/CONTRACTS.md`
- 模块边界：`docs/gates/gate-d/MODULES.md`
- 数据库：`docs/gates/gate-d/DB_SCHEMA.md`
- 状态机：`docs/gates/gate-d/STATE_MACHINE.md`
- 风控：`docs/gates/gate-d/RISK_RULES.md`
- 补偿：`docs/gates/gate-d/COMPENSATION_SYNC.md`
- 验收：`docs/gates/gate-d/TEST_CASES.md`
- 依据：`docs/gates/gate-d/SOURCES.md`
- 工作记录：`docs/gates/gate-d/WORK.md`

