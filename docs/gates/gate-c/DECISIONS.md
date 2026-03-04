# docs/gates/gate-c/DECISIONS.md
# Gate C DECISIONS（ADR）

## ADR-C01：GateC-1 先 REST 跑通闭环，WS 后置
- Decision：place/cancel/query/orders-pending/fills 全用 REST；WS 作为 GateC-1.1 优化。
- Consequences：验收稳定；轮询成本增加但可接受。

## ADR-C02：超时处理：禁止盲重试，必须 query-confirm
- Decision：placeOrder 超时/网络错误后，先用 trade/order 或 orders-pending 确认，再决定补偿动作。
- Consequences：避免重复下单与双开仓。

## ADR-C03：交易所差异隔离：方言仅存在于 adapter-*
- Decision：core/ledger/risk 不出现 venue 分支；按 accounts.venue（或订单携带 venue）路由 adapter。
- Consequences：第二家交易所接入成本线性，不指数爆炸。

## ADR-C04：instruments 元数据缓存 + 下单前 trim 为强制
- Decision：必须实现 public/instruments 缓存，按 tick/lot/min 校验与截断。
- Consequences：显著减少拒单与运行态噪声。

## ADR-C05：成交去重优先使用 (exchange, exchange_trade_id)
- Decision：依赖 trades uq_trades_exchange_trade 去重；若某场景无 trade id，另行定义策略并记录。
- Consequences：避免重复成交与重复记账。

## ADR-C06：GateC-0 必须引入 orders.external_order_id 与索引
- Decision：orders 增加 external_order_id，并建 (venue, external_order_id) 索引用于关联与对账。
- Consequences：WS/REST reconcile/恢复链路统一，避免靠“模糊匹配”导致误关联。

## ADR-C07：执行链路以 adapter 为中心；PAPER 也是一个 adapter 实现
- Decision：place/cancel 必须通过 AdapterRouter -> TradingAdapter；PAPER 作为 venue=PAPER 的 TradingAdapter。
- Consequences：避免 PAPER 专用链路分叉，确保 GateC 接入不推翻 GateB 事实链与状态机。

## ADR-C08：回执必须事件化并写 event_store
- Decision：place/cancel 的外部结果必须转为 OrderAck/OrderReject/CancelAck/CancelReject；fills 转为 TradeExecuted。
- Consequences：证据链完整、可回放可复盘；并能在恢复时重建“发生了什么”。

## ADR-C09：WS 推送仅加速，不作为最终事实源
- Decision：WS 所有推送必须可被 REST reconcile 覆盖校正；异常时必须降级 REST。
- Consequences：断线/乱序/漏推不再是线上 P0。