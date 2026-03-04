# docs/gates/gate-c/DB_SCHEMA.md
# Gate C DB SCHEMA（增量与强约束）

> 基线：Gate A/B 已落地 V1__init.sql。GateC 原则：尽量少 DDL；必要时只加字段/索引并明确语义与约束。

---

## 1. 必须增量（GateC-0）

### 1.1 orders.external_order_id（必须）
- 新增字段：external_order_id（varchar）
- 语义：交易所订单 ID（OKX ordId / Binance orderId）
- 用途：WS 回报关联、REST reconcile 对账、重启恢复定位
- 索引：idx_orders_venue_external_order_id (venue, external_order_id)
  - 若实际字段为 exchange，则索引为 (exchange, external_order_id)
- 若当前基线 orders 尚未持久化 venue：
  - GateC-0 迁移必须一并新增 `orders.venue`
  - 并从 `accounts.venue` 回填历史数据后再创建索引，避免 `(venue, external_order_id)` 无法成立

---

## 2. 必须对齐（不改字段名）

### 2.1 orders
- 幂等：UNIQUE(account_id, client_order_id) 已存在
- status 只能由状态机迁移写入
- client_order_id 必须贯穿 event_store.key 与交易所 clientId 字段（OKX clOrdId）

### 2.2 trades
- 必填：exchange（OKX/BINANCE/PAPER）
- 去重：UNIQUE(exchange, exchange_trade_id) 已存在
- fee/fee_currency：GateC 要写入（交易所返回则必须落库）
- external_order_id：若现有 trades 表有对应字段则必须写入；若没有，可通过 orders 外键关系串联

### 2.3 ledger_entries / ledger_events
- ledger_entries.idempotency_key 必须用于幂等（GateB 已具备）
- 记账不平衡必须触发失败路径（写 risk/audit + event_store）

### 2.4 event_store
- payload_json 为 JSONB：JDBC 写入必须 `CAST(? AS jsonb)`（GateB 已标准化）
- schema_version 对齐 EventEnvelope.version
- event_type 对齐 EventEnvelope.type
- key_value 对齐 EventEnvelope.key

### 2.5 TIMESTAMPTZ
- 所有 TIMESTAMPTZ 入参统一 `Timestamp.from(Instant)`（GateB 已标准化）

---

## 3. GateB 已包含以下索引，无需 GateC 再新增迁移
- idx_orders_strategy_run_id (strategy_run_id)
- idx_trades_account_ts (account_id, ts desc)
- idx_ledger_entries_trace_id (trace_id)

---

## 4. GateC 数据语义约束（必须）

- trades.exchange 必须填 OKX/BINANCE/PAPER
- trades.exchange_trade_id：
  - 若交易所返回成交 ID：必须填，并依赖 UNIQUE 去重
  - 若缺失：必须定义替代去重策略，并写入 docs/gates/gate-c/DECISIONS.md
- orders.external_order_id：
  - placeOrder 成功后必须落库
  - allow null：仅在“外部调用失败/未知结果”且待 query-confirm 的窗口期允许为空

---

## 5. 建议 DDL（可选，但推荐在 GateC-0 一并做）
> 若你希望更强的完整性，可以在 trades 增加 (venue, external_order_id) 索引用于回溯；但不作为 GateC 硬门禁。