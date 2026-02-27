# Gate B ARCHITECTURE（最小交易闭环：模拟盘）

> Gate B 目标：在 Gate A 工程骨架基础上，实现“端到端最小交易闭环（模拟盘）”，并满足：
> - 严格状态机
> - client_order_id 幂等
> - 账本可平衡校验
> - 重启可恢复
> - traceId 全链路可观测
>
> 非目标（Gate B 不做）：
> - 不接真实交易所网络（OKX/Binance 不发真实 HTTP/WebSocket）
> - 不做复杂撮合/深度盘口/滑点建模（只做可复盘的极简撮合）
> - 不做复杂策略研究/因子挖掘（AlphaCFG 属于后续 Research Gate）

---

## 1. 端到端闭环概览

闭环链路（最小必达）：

1) Strategy Trigger（scheduler 或 HTTP）生成策略运行 `strategy_runs`
2) Strategy 输出下单意图（PlaceOrderCommand）
3) Risk Engine 判定（PASS/REJECT）并记录 `risk_events`
4) Order Orchestrator 创建订单 `orders`（幂等）并进入状态机
5) Paper Adapter 受理订单（不出网），进入 `SENT/ACCEPTED`
6) Paper Matching（定时任务）生成成交 `trades`
7) Ledger Posting（记账）写入 `ledger_events` + `ledger_entries`（平衡校验）
8) Position Update 更新 `positions` / `account_snapshots`
9) Audit/Observability 写入 `audit_logs`，traceId 串起全链路

---

## 2. 关键组件与职责

### 2.1 nq-scheduler

- 触发策略运行（按策略/账户/交易对）
- 触发撮合（paper match tick）
- 触发对账/校验（ledger reconcile）

### 2.2 nq-core（订单编排与状态机）

- 订单状态机：唯一的订单状态来源
- 幂等入口：以 `client_order_id` 为幂等键
- 恢复：重启后能重建“待处理订单/撮合任务”

### 2.3 nq-risk（风控）

- 最小规则集：kill switch、最大下单金额、最大持仓、频率限制
- 风控输出必须可审计：risk_events + audit_logs

### 2.4 nq-adapter-api + (Paper Adapter 逻辑)

- Gate B 不接真实 OKX/Binance 网络
- 可选两种实现方式（二选一）：
    1) 新增 `nq-adapter-paper`（推荐，最干净）
    2) 在 `nq-adapter-okx/binance` 提供 paper 模式实现（不推荐，容易污染）
- 适配器只负责“受理/取消/回报”，不得绕过 core 改 DB

### 2.5 nq-ledger（记账与校验）

- 成交驱动记账：trade -> ledger_events -> ledger_entries
- 平衡校验：同一 ledger_event 的 entries 借贷必须平衡（不平衡直接 fail + risk_event）

### 2.6 nq-observability

- traceId 必须贯穿：HTTP -> scheduler -> domain -> events -> DB -> logs
- 对外只展示必要信息，内部要全量可复盘

---

## 3. 状态机（最小口径）

### 3.1 Order 状态（建议最小集合）

- NEW（已创建，未风控）
- RISK_PASSED / RISK_REJECTED
- SENT（已提交给适配器）
- ACCEPTED（适配器已受理，可选）
- PARTIALLY_FILLED
- FILLED（终态）
- CANCELLED（终态）
- REJECTED（终态）

> 规则：任何状态变更必须通过状态机；禁止“直接改 DB 状态”绕过。

### 3.2 Trade 状态（最小）

- EXECUTED（生成即终态；后续扩展可加撤销/更正）

---

## 4. 幂等与一致性策略

### 4.1 client_order_id 幂等

- 入口（HTTP/策略）必须提供 client_order_id（或由系统生成但必须可重放）
- DB 层：orders(client_order_id) UNIQUE（建议含 tenant_id 维度）
- 服务层：重复请求返回同一订单结果，不产生副作用

### 4.2 exactly-once vs at-least-once

- 内部事件/调度按“at-least-once”设计
- 通过幂等键 + 状态机保证“有效副作用 exactly-once”

---

## 5. 记账模型（最小）

- 任何成交必须产出 ledger_event（关联 order_id / trade_id / trace_id）
- ledger_entries：
    - 资产维度（base/quote）
    - 借贷方向（DEBIT/CREDIT）
    - 金额（非负）
- 平衡校验：
    - 同一 ledger_event 下，DEBIT 总额 == CREDIT 总额（按币种分别校验）
    - 校验失败：拒绝落库/或落库为 FAILED 并触发 risk_event（实现时二选一，推荐“失败也落事件，便于复盘”）

---

## 6. Paper 撮合（极简策略）

- 价格来源：
    - 优先：最近一根 close/vwap（来自行情库/特征库）
    - 若无：使用固定 price provider（仅用于本地验证）
- 成交策略（最简）：
    - 市价单：按当前价全成
    - 限价单：若价格满足则成交，否则保持挂单（可选，Gate B 允许只做市价）

---

## 7. 可观测性与审计

- traceId 传播：
    - HTTP Filter / Scheduler 触发生成 traceId
    - 写入：orders/trades/ledger_events/audit_logs（至少这些表有 trace_id 列或可关联）
- audit_logs 必写点：
    - 策略运行开始/结束
    - 风控判定
    - 下单创建
    - 成交生成
    - 记账完成/失败

---

## 8. Failure Modes（Gate B 必须覆盖的常见失败）

1) 重复下单（相同 client_order_id）导致重复订单
2) 状态机非法迁移（比如 NEW -> FILLED）
3) 撮合任务重复执行导致重复 trades
4) 记账不平衡（费用/四舍五入导致）
5) 重启后“订单卡住”（无人撮合/无人对账）
6) traceId 丢失导致无法复盘
7) 风控绕过导致不该下的单下了
8) 数据缺失（无价格/无账户快照）导致撮合异常

> Gate B 验收要求：上述失败中至少 1/2/3/4/5 有单测或可复现脚本，并能在 audit_logs/risk_events 中定位。