# docs/gates/gate-c/EVOLUTION_RULES.md
# Gate C EVOLUTION RULES（强约束）

1) 不破坏 GateB 的幂等/状态机/账本/审计事实链。
2) 交易所差异只允许出现在 adapter-*；禁止 core/ledger/risk 出现 venue 分支。
3) placeOrder/cancelOrder 必须通过 AdapterRouter -> TradingAdapter；PAPER 也必须是一个 TradingAdapter 实现。
4) placeOrder 超时不得盲重试；必须 query-confirm。
5) trades 必须可去重：优先 (exchange, exchange_trade_id)；否则必须在 DECISIONS 记录替代策略。
6) orders 必须落 external_order_id（成功后），并具备 (venue, external_order_id) 索引。
7) event_store 必须记录命令与关键事件（推荐 envelope 全量 JSON），且回执必须事件化：
    - OrderAck/OrderReject/CancelAck/CancelReject/TradeExecuted
8) 外部调用失败必须写 audit_logs，必要时写 risk_events；包含 trace_id 与 reason。
9) JSONB 写入统一 CAST(? AS jsonb)；TIMESTAMPTZ 入参统一 Timestamp.from(Instant)。
10) GateC-1 先 REST；引入 WS 必须保留 REST reconcile 兜底；WS 仅加速不作为事实源。