# GateE PR_SPLIT_PLAN

> 原则：单个 PR 只解决一类边界问题，做到能 review、能回滚、能定位。

---

## 当前拆批建议

- [ ] GateE-0.1：Binance background reconcile 噪音治理
- [ ] GateE-0.2：schema / metadata 收口
- [ ] GateE-0.3：返回模型一致性收尾
- [ ] GateE-1：策略接入契约与注册
- [ ] GateE-2：调度编排主链

---

## GateE-0.1：Binance background reconcile 噪音治理

### 目标
- 收敛 `BINANCE_RECONCILE_ORDER_FAILED(credentials missing / -1021)` 这类高频噪音
- 统一 scheduler / reconcile 的 credential 与 timestamp 口径

### 边界
- 只做降噪与行为口径收敛
- 不改业务主状态机
- 不扩写成 Binance 深度齐平

### 不做项
- account sync
- snapshot 拉取增强
- observability 全量扩写

---

## GateE-0.2：schema / metadata 收口

### 目标
- 收敛当前 schema 与 metadata 的命名、文档和查询面口径
- 为 GateE 主体减少历史 schema 噪音

### 边界
- 只做必要收口
- 不为迎合历史占位文案制造空 migration

### 不做项
- 大规模 schema 扩边
- 重新定义 GateD 冻结基线

---

## GateE-0.3：返回模型一致性收尾

### 目标
- 收紧 `Paper / OKX / Binance` 在未成交、成交、恢复、对账场景下的响应口径

### 边界
- 只做返回模型和文档一致性收口
- 不做新的 venue 验收扩边

### 不做项
- UI 适配
- 新的接口层扩展

---

## GateE-1：策略接入契约与注册

### 目标
- 建立最小策略接入契约
- 建立策略注册与运行状态管理最小链路

### 边界
- 只做策略接入主体，不混调度主链细节

### 不做项
- 大规模编排与窗口控制
- 复杂策略生命周期

---

## GateE-2：调度编排主链

### 目标
- 建立调度编排主链
- 打通策略触发、运行窗口控制、状态衔接

### 边界
- 只做编排主链，不回头重做 GateD 执行闭环

### 不做项
- 回测/研究平台
- 大规模 observability 扩边
