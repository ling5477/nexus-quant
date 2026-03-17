# GateE PR_SPLIT_PLAN

> 原则：单个 PR 只解决一类边界问题，做到能 review、能回滚、能定位。GateE 不搞一锅炖。

---

## 当前拆批建议

- [x] GateE-DOC-1：文档完善批
- [ ] GateE-0.1：Binance background reconcile 噪音治理
- [ ] GateE-0.2：schema / metadata 收口
- [ ] GateE-0.3：返回模型一致性收尾
- [ ] GateE-1.1：策略契约与注册模型
- [ ] GateE-1.2：策略运行状态与最小入口
- [ ] GateE-2.1：调度编排主链
- [ ] GateE-2.2：运行窗口 / 去重 / 验收样本

---

## GateE-DOC-1：文档完善批（已完成）

### 目标
- 基于当前项目文件，梳理 GateE 真实起点
- 补齐 GateE 的契约、schema、状态机、验收与依据索引
- 把 GateE 从“骨架文档”提升到“可开工文档”

### 边界
- 只改文档，不改业务代码
- 不创造假 migration
- 不假装 GateE 已经开工实现

---

## GateE-0.1：Binance background reconcile 噪音治理

### 目标
- 收敛 `credentials missing / -1021 / cooldown 内重复触发` 这类高频噪音
- 统一 scheduler / reconcile 的 credential 与 timestamp 口径

### 涉及模块
- `nq-scheduler`
- `nq-adapter-binance`
- `nq-app`

### 不做项
- account sync 扩展
- snapshot 拉取增强
- 把 Binance 适配层全面重写

---

## GateE-0.2：schema / metadata 收口

### 目标
- 收敛当前 schema 与 metadata 命名、文档和查询面口径
- 对齐 `strategyId / strategyRunId / source / requestId / idempotencyKey` 的边界

### 涉及模块
- `nq-infra`
- `nq-api`
- `nq-ledger`
- `nq-core`

### 不做项
- 大规模 schema 扩边
- 为迎合历史占位文案制造空 migration

---

## GateE-0.3：返回模型一致性收尾

### 目标
- 收紧 `Paper / OKX / Binance` 在未成交、成交、恢复、对账场景下的响应口径
- 让 GateE 上层不用再对 venue 响应写分支补丁

### 涉及模块
- `nq-adapter-api`
- `nq-adapter-okx`
- `nq-adapter-binance`
- `nq-api`

### 不做项
- UI 适配
- 新的 venue 扩接

---

## GateE-1.1：策略契约与注册模型

### 目标
- 冻结策略定义、注册、启停、人工触发契约
- 建立最小策略注册存储模型

### 涉及模块
- `nq-core`
- `nq-app`
- `nq-api`
- `nq-infra`
- `nq-contracts`

### 不做项
- 复杂多级调度
- 多租户权限体系扩边

---

## GateE-1.2：策略运行状态与最小入口

### 目标
- 建立策略运行状态最小模型
- 让策略运行与执行链路完成最小血缘关联

### 涉及模块
- `nq-core`
- `nq-scheduler`
- `nq-app`
- `nq-api`

### 不做项
- 复杂失败重试矩阵
- 任务优先级与分片调度

---

## GateE-2.1：调度编排主链

### 目标
- 建立调度编排主链
- 打通策略触发、运行窗口控制、状态衔接

### 涉及模块
- `nq-scheduler`
- `nq-core`
- `nq-app`

### 不做项
- 回测 / 研究平台
- 生产级分布式调度集群

---

## GateE-2.2：运行窗口 / 去重 / 验收样本

### 目标
- 建立去重、串行化与运行窗口规则
- 建立可回归的 GateE 最小验收样本

### 涉及模块
- `nq-scheduler`
- `nq-api`
- `nq-app`
- `docs/gates/gate-e/TEST_CASES.md`
