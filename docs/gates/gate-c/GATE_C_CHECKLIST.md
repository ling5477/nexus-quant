# docs/gates/gate-c/GATE_C_CHECKLIST.md
# Gate C CHECKLIST（验收门禁）

> 先 GateC-1（OKX）通过，再做 GateC-2（Binance）。

---

## 1. 构建与测试
- [ ] `mvn -q -f backend/pom.xml test` 通过

---

## 2. GateC-0（前置改造，必须）
- [ ] `nq-adapter-api` 完成三接口定义（Trading/MarketData/Account）
- [ ] `AdapterRouter` 可按 (accountId, venue) 路由到具体 adapter（PAPER/OKX/BINANCE）
- [ ] `orders.external_order_id` 已落库 + 索引 (venue, external_order_id)
- [ ] placeOrder/cancelOrder 不再硬编码 PAPER 路径，必须调用 TradingAdapter
- [ ] place/cancel 回执必须事件化并写 event_store：
    - [ ] OrderAck/OrderReject
    - [ ] CancelAck/CancelReject

---

## 3. OKX Demo Trading（必须：REST-only / GateC-1）
- [ ] instruments 缓存可用：public/instruments 拉取成功并落缓存
- [ ] 下单成功（orders=1；状态推进到 SENT -> ACCEPTED via OrderAck）
- [ ] 同步器推进订单状态（query trade/order；必要时 orders-pending）
- [ ] fills 同步成交（trades>=1，exchange=OKX，exchange_trade_id 去重生效）
- [ ] 每笔 fill 写入 TradeExecuted（trade.event.v1）到 event_store
- [ ] 记账成功（ledger_entries>=2，idempotency_key 非空）
- [ ] positions 更新（positions>=1）
- [ ] event_store 证据链齐全（至少覆盖 order.command/order.event/trade.event/ledger.event/risk.event/position.event）
- [ ] 审计与风险：audit_logs>=N，risk_events>=1（至少包含风控 PASS 记录）

---

## 4. 重启恢复（必须）
- [ ] 制造非终态订单（SENT/ACCEPTED，trades=0）后重启
- [ ] 重启后不重复下单、不重复成交、不重复记账
- [ ] 重启后通过 REST reconcile 推进到终态（FILLED 或 CANCELED/REJECTED）
- [ ] trace_id 全链路可追踪

---

## 5. OKX 私有 WS（可选：GateC-1.1）
- [ ] orders/account/positions（或 balance_and_position）订阅成功
- [ ] WS 回报映射为标准事件写 event_store（order.event/trade.event/position.event）
- [ ] 断线重连与重订阅可用
- [ ] WS 异常可降级到 REST reconcile（orders-pending + fills）
- [ ] WS + REST 同时开启不产生重复 trades/ledger（依赖幂等兜底）

---

## 6. Binance（GateC-2）
- [ ] 复用 adapter-api 接口
- [ ] accounts.venue（或订单携带 venue）路由正确
- [ ] REST-only 通过后再上私有 WS
- [ ] Testnet/Demo 通过后再上真实 key（Trade+Read，不开 Withdraw）