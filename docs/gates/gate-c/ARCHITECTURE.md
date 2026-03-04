# docs/gates/gate-c/ARCHITECTURE.md
# Gate C ARCHITECTURE（CEX 接入：OKX -> Binance）

> Gate C 目标：在 Gate B 的“幂等/状态机/事实链(event_store)/账本(ledger)/审计(audit)/风控(risk)/可恢复”底座上，
> 接入真实 CEX（先 OKX 现货，后 Binance 现货），实现真实下单/撤单/成交同步/记账/持仓投影，且可回放可复盘。
>
> GateC 的关键原则：**实盘链路必须以 adapter 为中心**，PAPER/OKX/BINANCE 只是不同 venue 的 adapter 实现；core/ledger/risk 不出现 venue 分支。

---

## 0. GateC 分阶段（强约束）

- **GateC-0（前置改造，必须）**：adapter-api 三分法 + AdapterRouter；orders.external_order_id；订单回执事件化（Ack/Reject/Fill/CancelAck）。
- **GateC-1（OKX REST-only，必须）**：用 REST 跑通闭环（place/cancel/query/orders-pending/fills）+ 轮询同步器 + 记账与持仓投影。
- **GateC-1.1（OKX 私有 WS，推荐后置）**：WS 实时加速（orders/account/positions 或 balance_and_position），但必须保留 REST reconcile 兜底。
- **GateC-2（Binance）**：复用同样框架与验收口径，仅替换 adapter 实现。

---

## 1. 总体策略（强约束）

1) **REST 先跑通闭环（GateC-1）**
   - 下单/撤单/查单/挂单扫描/成交同步全部用 REST。
   - 好处：可复现、可验收、排障简单。

2) **WebSocket 后置优化（GateC-1.1）**
   - 私有 WS 做订单/成交/账户变化的实时推送。
   - 但必须保留 REST reconcile 兜底（断线/乱序/重复/漏推都会发生）。

3) **多交易所差异隔离**
   - core/ledger/risk 不出现 venue 分支；交易所方言仅存在于 adapter-okx / adapter-binance。

4) **执行链路以 adapter 为中心**
   - place/cancel 不得直连 PAPER 专用链路；PAPER 也必须是一个 TradingAdapter 实现（venue=PAPER）。
   - OMS 状态推进只能由“命令 + 回执事件 + 同步器确认”驱动（避免绕过审计/幂等/状态机）。

---

## 2. GateC-0 前置改造（必须先做）

### 2.1 adapter-api 三分法（必须）
在 `nq-adapter-api` 定义三类接口（Port）：
- TradingAdapter：placeOrder / cancelOrder / getOrder / listOpenOrders
- MarketDataAdapter：subscribeBars / subscribeTrades / subscribeOrderBook（GateC-1 可先最小 stub）
- AccountAdapter：getBalances / getPositions / getAccountSnapshot（GateC-1 可先 REST 拉取）

### 2.2 AdapterRouter（必须）
新增 AdapterRouter（建议 `nq-core` 的 execution 子包或新模块 `nq-execution`）：
- route(accountId, venue) -> {trading(), marketData(), account()}
- 限频/重试/超时/降级封装在 adapter 层或 router 层的“横切组件”中（core 不关心）。

### 2.3 orders.external_order_id（必须）
- orders 增加 `external_order_id`（交易所 ordId / orderId）
- 索引：`(venue, external_order_id)`（或 `(exchange, external_order_id)`，以现有字段名为准）
用途：
- WS 回报关联本地订单
- REST reconcile 兜底对账
- 重启恢复定位外部订单

### 2.4 回执事件化（必须）
place/cancel 的外部结果必须映射为内部事件，并写入 event_store：
- OrderAck / OrderReject
- CancelAck / CancelReject
- TradeExecuted（fills）

---

## 3. 端到端闭环（CEX）

最小闭环（必须达成）：

1) scheduler 或 HTTP 触发策略运行：写 strategy_runs
2) 产生 PlaceOrderCommand（EventEnvelope，topic=order.command.v1）
3) 风控判定：写 risk_events + audit_logs（并写入 event_store）
4) core 下单编排：
   - 幂等：orders(account_id, client_order_id) UNIQUE
   - 状态机：NEW -> RISK_PASSED -> SENT
5) AdapterRouter -> adapter-okx TradingAdapter REST 下单：
   - 映射 client_order_id -> OKX clOrdId
   - 成功回执：OrderAck（写 external_order_id）
   - 失败回执：OrderReject（含 reject_code/reason）
6) 同步器（REST 轮询）：
   - 查单推进订单状态
   - 拉 fills 写入 trades（去重：exchange+exchange_trade_id）
   - 每笔 fill 产出 TradeExecuted 事件并写 event_store
7) ledger：
   - trade -> ledger_entries/ledger_events（幂等+平衡校验）
8) positions/account_snapshots 投影更新（沿用 ledger 投影）
9) event_store 写入证据链：
   - 命令、订单事件、成交事件、记账事件、风险/审计事件（envelope 全量 JSON）
10) 重启恢复：
   - 扫描非终态 orders + orders-pending + fills 补偿，确保不重复下单/不重复成交/不重复记账

---

## 4. OKX GateC-1 REST 最小接口集（必须实现）

### 4.1 公共接口（无鉴权）
- 获取现货产品信息（精度/最小下单量等）：
  `GET /api/v5/public/instruments?instType=SPOT`
- 获取系统时间（用于排查时钟漂移/签名失败）：
  `GET /api/v5/public/time`

### 4.2 交易接口（需鉴权）
- 下单：`POST /api/v5/trade/order`
- 撤单：`POST /api/v5/trade/cancel-order`
- 查单（单笔）：`GET /api/v5/trade/order`
- 当前挂单（未完成订单）：`GET /api/v5/trade/orders-pending`
- 成交明细（近3天）：`GET /api/v5/trade/fills`
- 成交历史（近3个月，补偿对账可选）：`GET /api/v5/trade/fills-history`

> GateC-1 同步器强约束：
> - placeOrder 成功只代表“接收并分配 ordId”，不代表成交；
> - placeOrder 超时/网络异常：禁止盲重试；必须 query-confirm；
> - 同步器循环：query order + pull fills；并严格幂等写入 trades/ledger。

---

## 5. OKX GateC-1.1 私有 WS（推荐但后置）

私有 WS 的职责：
- 订单更新：orders channel
- 账户/余额更新：account channel
- 仓位/余额仓位：positions 或 balance_and_position（以 OKX 实际为准）

注意：
- WS 断线/重连/乱序/重复必须处理；
- **必须保留 REST reconcile**（orders-pending + fills）兜底；
- 启动恢复先用 orders-pending 获取 live orders，再用 WS 增量跟踪。

---

## 6. Binance（GateC-2）

Binance 的接入复用 GateC-1 的框架与验收口径：
- adapter-binance 仅实现签名/参数/响应/错误码/filters；
- syncer 同样提供 REST 轮询（先）、WS 后置；
- 按 accounts.venue 路由到不同 adapter；
- 复用 core/ledger/risk/audit/event_store。

---

## 7. 关键不变量（必须守住）

1) client_order_id 全链路贯穿：EventEnvelope.key、orders.client_order_id、交易所 clientId 字段（OKX clOrdId / Binance clientOrderId）
2) 超时重试：先 query-confirm，再决定补偿动作
3) trades 去重：优先 (exchange, exchange_trade_id) UNIQUE
4) ledger 幂等：ledger_entries.idempotency_key UNIQUE
5) trace_id 全链路可追踪：orders/trades/ledger/risk/audit/event_store
6) core/ledger/risk 禁止出现 venue 分支；方言只在 adapter-*

---

## 8. Failure Modes（GateC 必测）

1) 签名错误/时间戳错误导致全部失败（必须 audit+risk+连接指纹）
2) 下单超时但实际成功（query-confirm）
3) 部分成交/多笔成交（fills 去重+状态推进）
4) 撤单失败或撤单回报延迟
5) 限频/短期不可用（降级、熔断、审计）
6) 重启恢复：非终态订单继续推进，不产生重复副作用
7) 精度/filters 拒单（instruments 预校验+trim+reason）
8) WS（若启用）断线/乱序/重复（REST reconcile 兜底）

---

## 9. 参考依据（权威来源）
- GateC 全部“接口/WS 通道/约束/outbox/可观测性”的依据统一收敛在：
  - `docs/gates/gate-c/SOURCES.md`