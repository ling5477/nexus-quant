# docs/current/GATE_CHECKLIST.md

# Current Gate Checklist（Gate C：OKX -> Binance）

> 本文件是“当前 Gate 的唯一验收入口（Source of Truth）”。
> 切换 Gate 时只需要更新本文件内容；历史 Gate 文档固定在 `docs/gates/` 下。
>
> 当前阶段：**Gate C（CEX 接入：OKX -> Binance）**
> 验收优先级：GateC-0 -> GateC-1（OKX REST-only）-> GateC-1.1（OKX WS 可选）-> GateC-2（Binance）

---

## 0. 基础构建门禁（必须）

- [x] `mvn -q -f backend/pom.xml test` 全绿
- [x] 本地 Postgres 可启动（docker compose）
- [x] `nq-app` 可启动（profile=local），health 可用（若已有 actuator）
- [x] GateC 验收入口已就绪（local only）：`POST /__gatec/orders`、`POST /__gatec/orders/cancel`、
  `POST /__gatec/reconcile/runOnce`、`POST /__gatec/recovery/runOnce`
- [x] GateC 验收入口已硬化（`local + nq.gatec.verify.enabled=true` 双门禁；生产零暴露）

---

## 1. GateC-0（必须先做：前置改造门禁）

目标：把“执行链路”从 PAPER 专用路径升级为“adapter 为中心”的统一链路。

### 1.1 adapter-api 三分法（必须）

- [x] `nq-adapter-api` 定义三接口（Port）：
    - [x] TradingAdapter：placeOrder/cancelOrder/getOrder/listOpenOrders
    - [x] MarketDataAdapter：subscribeBars/subscribeTrades/subscribeOrderBook（可先 stub）
    - [x] AccountAdapter：getBalances/getPositions/getAccountSnapshot（可先 REST 拉取）

### 1.2 AdapterRouter（必须）

- [x] 存在 `AdapterRouter`（建议在 `nq-core.execution` 或 `nq-execution` 模块）
- [x] 可按 `(accountId, venue)` 路由到 PAPER/OKX/BINANCE 的 adapter
- [x] core 仅依赖 adapter-api，不依赖 okx/binance 实现类

### 1.3 orders.external_order_id（必须）

- [x] orders 表新增 `external_order_id`
- [x] 索引：`(venue, external_order_id)`（或字段名为 exchange 时对应 `(exchange, external_order_id)`）
- [x] placeOrder 成功回执后必须落库 `external_order_id`

### 1.4 回执事件化（必须）

- [x] placeOrder 的外部结果必须转为事件并写 event_store：
    - [x] OrderAck / OrderReject
- [x] cancelOrder 的外部结果必须转为事件并写 event_store：
    - [x] CancelAck / CancelReject
- [x] fills 必须转为 TradeExecuted 并写 event_store

### 1.5 禁止项（必须满足）

- [x] `OrderCommandService` 不允许出现 “SUBMITTED_TO_PAPER” 这类硬编码分支（或任何 paper-only 分支）
- [x] `nq-scheduler` 不允许存在绕过 adapter 的 PAPER 专用链路（PAPER 必须是 TradingAdapter 的一种实现）

---

## 2. GateC-1（必须：OKX Spot REST-only 闭环）

目标：仅靠 REST 跑通真实闭环（可复现、可验收、可恢复）。

### 2.1 OKX 基础能力（必须）

- [x] OKX signer/鉴权可用（失败可定位：timestamp/signature/apiKey 权限）
- [x] public instruments 可拉取并缓存（用于精度/最小下单量校验与 trim）
- [x] 下单前按 instruments 做 trim（tickSz/lotSz/minSz）

### 2.2 OKX REST 交易链路（必须）

- [x] REST 下单成功：本地订单状态推进到 SENT -> ACCEPTED（由 OrderAck 事件驱动）
- [x] REST 撤单成功：状态推进到 CANCELED（由 CancelAck 事件驱动）
- [x] placeOrder 超时/网络异常：禁止盲重试；必须 query-confirm（查单或挂单列表）

### 2.3 REST reconcile 同步器（必须）

> 同步器可以在 `nq-scheduler`，但必须只调用 adapter-api/adapter-okx，不能绕过 core/ledger 规则。

- [x] 同步器每 N 秒扫描非终态订单（SENT/ACCEPTED/PARTIALLY_FILLED 等）
- [x] 对每个订单执行：
    - [x] query order（推进订单终态）
    - [x] pull fills（写 trades，去重生效：UNIQUE(exchange, exchange_trade_id)）
    - [x] 每笔 fill 触发 ledger posting（ledger_entries.idempotency_key 幂等）
- [x] WS 未启用时，靠 reconcile 也能推进到终态（FILLED/CANCELED/REJECTED）

### 2.4 账本与持仓（必须）

- [x] trades >= 1（exchange=OKX；exchange_trade_id 去重生效）
- [x] ledger_entries >= 2（idempotency_key 非空）
- [x] positions 更新（positions>=1 或对应投影可见）
- [x] 记账不平衡会走失败路径（写 risk/audit + event_store）

### 2.5 事实链/审计（必须）

- [x] event_store 至少包含：
    - [x] order.command（Place/Cancel）
    - [x] order.event（Ack/Reject/CancelAck/CancelReject）
    - [x] trade.event（TradeExecuted）
    - [x] ledger.event（LedgerPosted/失败事件若有）
    - [x] risk/audit 关键节点
- [x] trace_id 贯穿：orders/trades/ledger/risk/audit/event_store 都可追踪

---

## 3. GateC-1.1（可选后置：OKX 私有 WS + REST 兜底）

目标：WS 做实时加速，但不改变事实来源。

- [x] PR-W1 已完成连接治理层（连接/login/订阅管理/心跳/重连/指标），且不落业务表/不推进状态机
- [x] orders/account/positions（或 balance_and_position）订阅成功
- [x] WS 回报映射为标准事件写入 event_store（order.event/audit.event/position.event）
- [x] WS 断线可自动重连并重订阅
- [x] WS 异常必须降级触发一次 REST reconcile（限定窗口/非终态订单集合）
- [x] WS + REST 同时开启不产生重复 trades/ledger（幂等兜底有效）
- [x] CancelReject 不再停留 `CANCEL_REQUESTED`：状态推进到 `CANCEL_REJECTED`，并可由 REST reconcile 对齐回实时事实

---

## 4. GateC-2（Binance Spot）

目标：复用 GateC 框架，仅替换 adapter 实现。

- [x] PR-C10（无 key 阶段）：REST signer + HTTP client + mock 单测已完成，不依赖真实网络/真实 key
- [x] PR-C11（无 key 阶段）：exchangeInfo/filters 缓存 + trim 规则已完成，不依赖真实网络/真实 key
- [x] PR-C12（无 key 阶段）：adapter-binance 已实现 TradingAdapter（place/cancel/get/listOpenOrders）且 mock 单测覆盖 request 组装/错误解析
- [x] PR-C12（无 key 阶段）：Binance REST reconcile 已实现 query order + myTrades -> trades 去重 -> ledger posting 幂等回归测试
- [x] PR-C12（运行态阶段）：REST-only UseCase-A（LIMIT 远离盘口 -> Cancel）通过并在 WORK.md 留存证据链
- [x] PR-C13（运行态阶段）：REST-only UseCase-B（MARKET 小额成交 -> reconcile -> trades/ledger/positions）通过并在 WORK.md 留存证据链
- [ ] 私有 WS 后置（同 GateC-1.1 口径）
- [x] testnet 通过后再上真实 key（Trade+Read；不启用提现权限）

---

## 5. 重启恢复（GateC 必测门禁）

- [x] 制造非终态订单（SENT/ACCEPTED，trades=0）后重启服务
- [x] 重启后不重复下单、不重复成交、不重复记账
- [x] 重启后通过 REST reconcile 推进到终态
- [x] 全程 trace_id 可追踪、audit/risk 有证据链
- [x] query-confirm 遇到 `OKX 51603 (Order does not exist)` 不阻断启动，且降级证据链完整（audit + event_store + 终态推进）

---

## 6. 文档对齐检查（必须）

- [x] `docs/gates/gate-c/ARCHITECTURE.md` 已冻结并与实现一致
- [x] `docs/gates/gate-c/CONTRACTS.md` 的事件字段与代码 DTO 一致
- [x] `docs/gates/gate-c/DB_SCHEMA.md` 的 DDL 与 Flyway 增量一致
- [x] `docs/gates/gate-c/RECOVERY_RUNBOOK.md` 与启动恢复行为一致
