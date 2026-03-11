# docs/gates/gate-c/GATE_C_CHECKLIST.md
# Gate C CHECKLIST（验收门禁）

> 当前冻结结论：**GateC 已通过最终总验收，可冻结**。
> 口径来源：以 `docs/current/GATE_CHECKLIST.md` 为准；本文件用于保留 GateC 冻结快照的勾选状态。

---

## 1. 构建与测试
- [x] `mvn -q -f backend/pom.xml test` 通过

---

## 2. GateC-0（前置改造，必须）
- [x] `nq-adapter-api` 完成三接口定义（Trading/MarketData/Account）
- [x] `AdapterRouter` 可按 `(accountId, venue)` 路由到具体 adapter（PAPER/OKX/BINANCE）
- [x] `orders.external_order_id` 已落库 + 索引 `(venue, external_order_id)`
- [x] `placeOrder/cancelOrder` 不再硬编码 PAPER 路径，必须调用 `TradingAdapter`
- [x] place/cancel 回执必须事件化并写 `event_store`
- [x] `OrderAck/OrderReject` 已覆盖
- [x] `CancelAck/CancelReject` 已覆盖

---

## 3. OKX Demo Trading（必须：REST-only / GateC-1）
- [x] instruments 缓存可用：`public/instruments` 拉取成功并落缓存
- [x] 下单成功（`orders=1`；状态推进到 `SENT -> ACCEPTED` via `OrderAck`）
- [x] 同步器推进订单状态（`query trade/order`；必要时 `orders-pending`）
- [x] fills 同步成交（`trades>=1`，`exchange=OKX`，`exchange_trade_id` 去重生效）
- [x] 每笔 fill 写入 `TradeExecuted`（`trade.event.v1`）到 `event_store`
- [x] 记账成功（`ledger_entries>=2`，`idempotency_key` 非空）
- [x] positions 更新（`positions>=1`）
- [x] `event_store` 证据链齐全（至少覆盖 `order.command / order.event / trade.event / ledger.event / risk.event / position.event`）
- [x] 审计与风险证据齐全（至少包含风控 PASS 记录）

---

## 4. 重启恢复（必须）
- [x] 制造非终态订单（`SENT/ACCEPTED`，`trades=0`）后重启
- [x] 重启后不重复下单、不重复成交、不重复记账
- [x] 重启后通过 REST reconcile 推进到终态（`FILLED` 或 `CANCELED/REJECTED`）
- [x] `trace_id` 全链路可追踪

---

## 5. OKX 私有 WS（GateC-1.1）
- [x] `orders/account/positions`（或 `balance_and_position`）订阅成功
- [x] WS 回报映射为标准事件写入 `event_store`（`order.event / audit.event / position.event`）
- [x] 断线重连与重订阅可用
- [x] WS 异常可降级到 REST reconcile（`orders-pending + fills`）
- [x] WS + REST 同时开启不产生重复 `trades/ledger`（依赖幂等兜底）
- [x] OKX Real 最小复验已通过：`clientOrderId=d1okx1u0311164501` 完成 `ACCEPTED -> CANCELLED`

---

## 6. Binance（GateC-2）
- [x] 复用 `adapter-api` 接口
- [x] `accounts.venue`（或订单携带 `venue`）路由正确
- [x] REST-only 已通过后再上私有 WS
- [x] Testnet/Demo 已通过后再上真实 key（`Trade+Read`，不开 `Withdraw`）
- [x] Binance Testnet 全量验收已通过（REST UseCase-A / UseCase-B / WS / 协同 / 降级）
- [x] Binance Real 最小复验已通过：`clientOrderId=d2bin0311163301` 完成 `ACCEPTED -> CANCELLED`
