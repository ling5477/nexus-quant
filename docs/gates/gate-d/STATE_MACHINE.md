# GateD STATE_MACHINE
# GateD 订单状态机说明

## 1. 目标

GateD 状态机要解决两个混乱源：
1. 本地命令状态与外部事实状态混在一起
2. reconcile / recovery / ws / rest 都可能推进状态，容易打架

GateD 的做法是：**统一状态推进入口 + 本地状态与外部事实状态分层**。

---

## 2. 状态分层

### 2.1 本地过程状态
用于描述本地执行过程：
- `NEW`
- `RISK_CHECKING`
- `RISK_REJECTED`
- `READY_TO_SUBMIT`
- `SUBMITTING`
- `CANCEL_SUBMITTING`
- `FAILED`

### 2.2 外部事实状态
用于描述交易所或 paper executor 的确认事实：
- `ACCEPTED`
- `PARTIALLY_FILLED`
- `FILLED`
- `CANCELLED`
- `REJECTED`
- `CANCEL_REJECTED`
- `EXPIRED`（如后续需要）

### 2.3 终态
终态包括：
- `RISK_REJECTED`
- `FILLED`
- `CANCELLED`
- `REJECTED`
- `FAILED`（仅用于本地不可恢复失败）

---

## 3. 基本流转

### 3.1 下单
`NEW -> RISK_CHECKING -> READY_TO_SUBMIT -> SUBMITTING -> ACCEPTED | REJECTED | FAILED`

### 3.2 成交
`ACCEPTED -> PARTIALLY_FILLED -> FILLED`

### 3.3 撤单
`ACCEPTED | PARTIALLY_FILLED -> CANCEL_SUBMITTING -> CANCELLED | CANCEL_REJECTED`

### 3.4 风控拒绝
`RISK_CHECKING -> RISK_REJECTED`

---

## 4. 推进来源

### 4.1 本地命令推进
- place command
- cancel command
- risk reject
- local validation fail

### 4.2 外部事实推进
- adapter ack / reject
- ws order event
- rest query order snapshot
- recovery snapshot

### 4.3 成交推进
- ws trade report
- rest pull fills

---

## 5. 硬规则

- 终态不允许回退到非终态
- `FILLED` 不允许再推进到 `CANCELLED`
- 同一 `exchange_trade_id` 只允许造成一次成交推进
- `external_order_id` 允许延迟绑定，但绑定后必须可追踪
- `CANCEL_REJECTED` 不表示订单终态结束，后续仍允许通过 reconcile 对齐到真实 `FILLED / CANCELLED`

---

## 6. GateD 必须修正的问题

- 清理过渡态中语义重复的状态命名
- 清理“某个状态只给某个 venue 用”的隐式约定
- 清理 scheduler / ws / rest 各自偷推进状态的路径

