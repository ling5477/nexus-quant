# GateD PR_SPLIT_PLAN

> GateD 提交拆分计划。  
> 原则：单个 PR 只解决一类边界问题，做到能 review、能回滚、能定位。
> 状态约定：`[x] 已完成`、`[~] 进行中`、`[ ] 未开始`。  
> 当前状态基线：**截至 2026-03-13 的已实现与已验证事实**。

---

## 当前推进状态

- [x] PR-1：文档与阶段入口对齐
- [~] PR-2：contracts / core 执行入口收敛
- [x] PR-3：pre-trade 风控规则链
- [~] PR-4：状态机、事件与执行回执收敛
- [~] PR-5：scheduler / recovery / reconcile / degrade 收敛
- [~] PR-6：ledger / projection / db schema
- [~] PR-7：app / api 验收入口与查询视图
- [ ] PR-8：integration tests / freeze docs

---

## PR-1：文档与阶段入口对齐（已完成）

### 目标
- 修正 GateD 阶段定义
- 建立 GateD 完整文档目录
- 同步更新 `AGENTS.md`、`README.md`、`docs/current/*`

### 涉及文件
- `AGENTS.md`
- `README.md`
- `docs/current/*`
- `docs/gates/gate-d/*`
- `docs/ROADMAP.md`
- `docs/gates/gate-b/ROADMAP.md`
- `docs/gates/gate-c/ROADMAP.md`

### 不包含
- 任何核心代码逻辑改造

---

## PR-2：contracts / core 执行入口收敛（进行中）

### 目标
- 统一执行应用服务
- 收敛 place / cancel / ack / reject / trade-report / query-confirm 入口
- 明确 core 与 adapter / scheduler / ledger 边界

### 涉及模块
- `nq-core`
- `nq-contracts`
- `nq-adapter-api`

### 不包含
- 风控规则实现
- Flyway 迁移
- 大规模恢复逻辑重写

---

## PR-3：pre-trade 风控规则链（已完成）

### 目标
- 从 `NoopRiskGate` 过渡到规则链
- 引入 rule registry、拒绝码、统一返回模型

### 涉及模块
- `nq-risk`
- `nq-core`
- 文档：`RISK_RULES.md`

### 不包含
- 多交易所深扩边
- 复杂组合风控

---

## PR-4：状态机、事件与执行回执收敛（进行中）

### 目标
- 冻结订单状态机
- 明确本地状态与外部事实状态
- 收敛重复回报、乱序回报、终态保护

### 涉及模块
- `nq-core`
- `nq-adapter-api`
- `nq-adapter-okx`
- `nq-adapter-binance`
- 文档：`STATE_MACHINE.md`

---

## PR-5：scheduler / recovery / reconcile / degrade 收敛（进行中）

### 目标
- scheduler 瘦身
- 明确 reconcile、recovery、query-confirm、degrade 的职责与调用关系
- 补充运行与排障手册

### 涉及模块
- `nq-scheduler`
- `nq-core`
- `nq-observability`
- 文档：`COMPENSATION_SYNC.md`、`RECOVERY_RUNBOOK.md`

---

## PR-6：ledger / projection / db schema（进行中）

### 目标
- fills 去重
- ledger posting 幂等
- position / account snapshot 持久化与投影增强
- 新增 GateD 迁移脚本

### 涉及模块
- `nq-ledger`
- `nq-infra`
- 文档：`DB_SCHEMA.md`、`NUMERIC_POLICY.md`

---

## PR-7：app / api 验收入口与查询视图（进行中）

### 目标
- 建立 GateD 最小验收入口
- 收敛阶段性接口与正式查询入口
- 完成最小本地验证闭环

### 涉及模块
- `nq-app`
- `nq-api`

### 当前进展
- `__gated` canonical 验收入口、order/trade/position/account 本地闭环已完成
- OKX 验收脚本已改为 canonical non-fallback 启动路径，并显式支持 `.env -> NQ_OKX_ENV=dome|real -> NQ_OKX_API_*` 统一运行时变量映射
- `dome / real` 两种模式均已推进到真实 OKX bootstrap；当前共同阻断点是 OKX 外网连接失败（`Permission denied: getsockopt`）
- 真实 `UC-D9: OKX LIMIT -> cancel` 尚未转绿，因此 PR-7 继续保持进行中

---

## PR-8：integration tests / freeze docs（未开始）

### 目标
- 跑通 GateD 用例
- 更新 `WORK.md`
- 形成冻结结论

### 涉及模块
- `nq-app`
- `nq-core`
- `nq-risk`
- `nq-scheduler`
- `nq-ledger`
- 文档：`TEST_CASES.md`、`GATE_D_CHECKLIST.md`、`WORK.md`

---

## PR 通用要求

每个 PR 都必须：

- 标注对应 checklist 条目
- 说明不包含的范围
- 写出验证方式
- 若改动契约 / 状态机 / DB / 恢复逻辑，必须同步更新文档
- 不接受“文档之后补”的口头承诺
