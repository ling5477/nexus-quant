# GateD ARCHITECTURE
# GateD 架构说明

## 1. 架构目标

GateD 架构目标是把当前仓库已有的多交易所适配、reconcile、recovery、ledger posting 能力，收敛为统一执行域，而不是继续让各个模块像野草一样各自生长。

---

## 2. 总体链路

```text
Order Request / Strategy Intent
  -> nq-core ExecutionApplicationService
  -> nq-risk PreTradeRiskService
  -> nq-adapter-api AdapterRouter
  -> Paper / OKX / Binance Adapter
  -> Adapter Ack / Reject / Trade Report / Snapshot
  -> nq-core OrderLifecycle / StateMachine
  -> nq-ledger TradePosting / Projection
  -> nq-scheduler Reconcile / Recovery / Degrade
  -> audit_logs / event_store / metrics / trace
```

---

## 3. 分层说明

### 3.1 接入层（nq-app / nq-api）
- 接收本地验收请求或正式 API 请求
- 负责 profile 限制、参数边界、controller 暴露
- 不承担执行域核心逻辑

### 3.2 执行域中心（nq-core）
- 统一执行入口
- 统一状态推进入口
- 执行 trace 协调
- adapter / risk / ledger / scheduler 的编排中心

### 3.3 风控层（nq-risk）
- 下单前硬风控
- 规则链与拒绝模型
- 风控审计与拒单事件

### 3.4 适配层（nq-adapter-api + nq-adapter-*）
- 外部请求签名与调用
- venue 响应归一映射
- 不写本地业务投影

### 3.5 账本与投影层（nq-ledger）
- fills 去重
- 账本入账
- 持仓与账户快照投影
- 失败可见与补偿支撑

### 3.6 调度与补偿层（nq-scheduler）
- 周期 reconcile
- 启动 recovery
- WS 断链 degrade 协调
- 只做触发与窗口管理，不做业务事实定义

### 3.7 可观测层（nq-observability）
- trace_id 贯穿
- audit / metrics / structured log
- 故障定位与阶段验收支撑

---

## 4. 关键原则

### 4.1 REST-first, WS-accelerated
- WS 只做加速与更快的 ACK / trade 通知
- REST reconcile 是长期兜底，不是临时补丁

### 4.2 State-machine-first
- 所有订单状态推进必须经过状态机
- 同一事件多次到达也必须幂等

### 4.3 Projection-rebuildable
- positions / account_snapshots / read-model 允许重建
- event_store / ledger_entries / orders / trades 不允许丢事实

### 4.4 Query-confirm before retry
- 网络超时、未知状态、启动恢复，都必须先查事实，再决定补偿动作

---

## 5. 现有代码对齐点

- `nq-core` 已有 `OrderCommandService` 与状态机基础，GateD 需要收敛成统一执行入口
- `nq-scheduler` 已有 reconcile / recovery / WS 协同能力，GateD 需要瘦身与边界澄清
- `nq-risk` 还停留在过渡态，是 GateD 最大缺口之一
- `nq-adapter-api` 已经是良好基座，GateD 主要做冻结与补齐
- `nq-ledger` 已有 posting 基础，GateD 需要和执行域明确咬合

