# docs/gates/gate-c/MODULES.md
# Gate C MODULES（模块职责与依赖）

## 1) 模块职责（GateC 版本）

- nq-adapter-api：统一下单/撤单/查单/扫描挂单/账户快照的接口（GateC-0：三分法）
    - TradingAdapter / MarketDataAdapter / AccountAdapter
- nq-adapter-okx：OKX 方言（signer/headers/params/errors/instruments/rest/ws）
- nq-adapter-binance：Binance 方言（GateC-2）
- nq-core：订单编排/状态机/幂等/审计/风险编排（不感知交易所）
    - AdapterRouter（可放 core.execution 子包或独立 nq-execution 模块）
- nq-scheduler：同步器（REST 轮询）+ 恢复扫描（非终态订单）+ 巡检
- nq-ledger：成交记账与校验（复用 GateB）
- nq-risk：风控（复用 GateB；GateC 至少启用最小规则集，不再默认全放行）
- nq-observability：trace/审计统一
- nq-infra：JDBC/HTTP 基础设施（含 event_store appender）

---

## 2) 禁止项（硬约束）

- adapter-* 不得直接写 ledger/positions（只能产出回执/成交数据，由 core/ledger 消费）
- core 不得依赖 okx/binance 具体实现（必须通过 adapter-api 注入）
- scheduler 不得实现“PAPER 专用链路”绕过 adapter；PAPER 必须是 TradingAdapter 的一种实现